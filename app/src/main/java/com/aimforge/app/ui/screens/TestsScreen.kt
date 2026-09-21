package com.aimforge.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimforge.app.domain.TestGroup
import com.aimforge.app.domain.TestMode
import com.aimforge.app.ui.AppViewModel
import com.aimforge.app.ui.components.ActiveSessionCard
import com.aimforge.app.ui.components.GlassCard
import com.aimforge.app.ui.components.SectionTitle
import com.aimforge.app.ui.theme.AF

@Composable
fun TestsScreen(vm: AppViewModel, onOpen: (TestMode) -> Unit, onOpenSession: (String) -> Unit) {
    val active by vm.activeSession.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp)
    ) {
        item {
            Text("Tests", style = MaterialTheme.typography.titleLarge, color = AF.TextPrimary)
            Spacer(Modifier.height(4.dp))
        }
        active?.let { s ->
            item(key = "active") { ActiveSessionCard(s, onOpen = { onOpenSession(s.sessionId) }) }
        }
        TestGroup.entries.forEach { group ->
            item(key = "h_${group.name}") { SectionTitle(group.title, Modifier.padding(top = 10.dp)) }
            items(TestMode.entries.filter { it.group == group }, key = { it.name }) { mode ->
                GlassCard(Modifier.fillMaxWidth(), onClick = { onOpen(mode) }) {
                    Text(mode.title, style = MaterialTheme.typography.titleMedium, color = AF.TextPrimary)
                    Text(
                        "${mode.distance} range, ${mode.target.lowercase()} target",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AF.TextSecondary
                    )
                }
            }
        }
    }
}
