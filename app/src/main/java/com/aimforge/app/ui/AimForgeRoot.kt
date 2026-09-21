package com.aimforge.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aimforge.app.domain.TestMode
import com.aimforge.app.ui.screens.DashboardScreen
import com.aimforge.app.ui.screens.HistoryScreen
import com.aimforge.app.ui.screens.LabScreen
import com.aimforge.app.ui.screens.OnboardingScreen
import com.aimforge.app.ui.screens.ProfileScreen
import com.aimforge.app.ui.screens.ReportScreen
import com.aimforge.app.ui.screens.SessionDetailScreen
import com.aimforge.app.ui.screens.SessionScreen
import com.aimforge.app.ui.screens.SetupScreen
import com.aimforge.app.ui.screens.TestsScreen
import com.aimforge.app.ui.theme.AF

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Default.Home),
    TESTS("tests", "Tests", Icons.Default.PlayArrow),
    LAB("lab", "Lab", Icons.Default.Build),
    HISTORY("history", "History", Icons.AutoMirrored.Filled.List),
    PROFILE("profile", "Profile", Icons.Default.Person)
}

private const val ROUTE_TEST = "test/{mode}"
private const val ROUTE_REPORT = "report"
private const val ROUTE_SESSION = "session/{id}"
private const val ROUTE_DETAIL = "detail/{id}"

@Composable
fun AimForgeRoot(vm: AppViewModel) {
    val profileUi by vm.profileUi.collectAsState()
    Surface(modifier = Modifier.fillMaxSize(), color = AF.Bg) {
        when {
            !profileUi.loaded -> Box(Modifier.fillMaxSize())
            profileUi.profile?.onboardingDone != true -> OnboardingScreen(vm)
            else -> MainShell(vm)
        }
    }
}

@Composable
private fun MainShell(vm: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    fun goTab(tab: Tab) {
        nav.navigate(tab.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = AF.Bg,
        bottomBar = {
            NavigationBar(containerColor = AF.Surface) {
                Tab.entries.forEach { tab ->
                    val selected = route == tab.route || (tab == Tab.TESTS && (route == ROUTE_TEST || route == ROUTE_SESSION)) ||
                        (tab == Tab.HISTORY && route == ROUTE_DETAIL)
                    NavigationBarItem(
                        selected = selected,
                        onClick = { goTab(tab) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AF.Bg,
                            selectedTextColor = AF.Accent,
                            indicatorColor = AF.Accent,
                            unselectedIconColor = AF.TextSecondary,
                            unselectedTextColor = AF.TextSecondary
                        )
                    )
                }
            }
        }
    ) { inner ->
        NavHost(navController = nav, startDestination = Tab.HOME.route, modifier = Modifier.padding(inner)) {
            composable(Tab.HOME.route) {
                DashboardScreen(
                    vm = vm,
                    onStart = { goTab(Tab.TESTS) },
                    onQuickTest = { nav.navigate("test/${it.name}") },
                    onReport = { nav.navigate(ROUTE_REPORT) },
                    onOpenSession = { nav.navigate("session/$it") }
                )
            }
            composable(Tab.TESTS.route) {
                TestsScreen(
                    vm = vm,
                    onOpen = { nav.navigate("test/${it.name}") },
                    onOpenSession = { nav.navigate("session/$it") }
                )
            }
            composable(ROUTE_TEST) { entry ->
                val name = entry.arguments?.getString("mode")
                val mode = TestMode.entries.firstOrNull { it.name == name } ?: TestMode.QUICK_AIM_CHECK
                SetupScreen(
                    vm = vm,
                    mode = mode,
                    onBack = { nav.popBackStack() },
                    // Replace the setup screen so Back from the session does not return to a stale form.
                    onOpenSession = { id ->
                        nav.navigate("session/$id") { popUpTo(Tab.TESTS.route) }
                    }
                )
            }
            composable(ROUTE_SESSION) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                SessionScreen(
                    vm = vm,
                    sessionId = id,
                    onClose = { goTab(Tab.TESTS) },
                    onDetails = { nav.navigate("detail/$it") }
                )
            }
            composable(ROUTE_DETAIL) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                SessionDetailScreen(
                    vm = vm,
                    sessionId = id,
                    onBack = { nav.popBackStack() },
                    onOpenSession = { nav.navigate("session/$it") }
                )
            }
            composable(Tab.LAB.route) { LabScreen(vm) }
            composable(Tab.HISTORY.route) { HistoryScreen(vm, onOpen = { nav.navigate("detail/$it") }) }
            composable(ROUTE_REPORT) { ReportScreen(vm, onBack = { nav.popBackStack() }) }
            composable(Tab.PROFILE.route) { ProfileScreen(vm) }
        }
    }
}
