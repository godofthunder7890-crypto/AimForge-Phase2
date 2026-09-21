package com.aimforge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimforge.app.ui.AimForgeRoot
import com.aimforge.app.ui.AppViewModel
import com.aimforge.app.ui.theme.AimForgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AimForgeApp
        val repo = app.repository
        val manager = app.sessionManager
        setContent {
            AimForgeTheme {
                val vm: AppViewModel = viewModel(factory = AppViewModel.Factory(repo, manager))
                AimForgeRoot(vm)
            }
        }
    }
}
