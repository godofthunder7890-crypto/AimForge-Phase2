package com.aimforge.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimforge.app.domain.ScopeType
import com.aimforge.app.ui.AppViewModel
import com.aimforge.app.ui.components.GlassCard
import com.aimforge.app.ui.theme.AF

@Composable
fun ProfileScreen(vm: AppViewModel) {
    val profile = vm.profileUi.collectAsState().value.profile
    val sessions by vm.sessions.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    // Only rank scopes with at least 3 scored tests. No claims from thin data.
    val analyzedSessions = sessions.filter { it.hasAnalysis }
    val scopeAvg = analyzedSessions
        .filter { it.scope != null }
        .groupBy { it.scope!! }
        .filter { it.value.size >= 3 }
        .mapValues { e -> e.value.map { it.aimScore!! }.average() }
    val strongest = if (scopeAvg.size >= 2) scopeAvg.maxByOrNull { it.value }?.key else null
    val weakest = if (scopeAvg.size >= 2) scopeAvg.minByOrNull { it.value }?.key else null

    val scored = analyzedSessions.map { it.aimScore!! } // newest first
    val trend = if (scored.size >= 6) {
        val diff = scored.take(3).average() - scored.drop(3).take(3).average()
        when {
            diff >= 3 -> "Improving"
            diff <= -3 -> "Dropping"
            else -> "Flat"
        }
    } else null

    fun scopeLabel(name: String?) = name?.let { n -> ScopeType.entries.firstOrNull { it.name == n }?.label }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Profile", style = MaterialTheme.typography.titleLarge, color = AF.TextPrimary)

        GlassCard(Modifier.fillMaxWidth()) {
            Row2("Device", profile?.deviceName ?: "--")
            Row2("Control", profile?.controlLayout ?: "--")
            Row2("Gyro", if (profile?.gyroEnabled == true) "On" else "Off")
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Row2("Strongest scope", scopeLabel(strongest) ?: "Enough data nahi")
            Row2("Weakest scope", scopeLabel(weakest) ?: "Enough data nahi")
            Row2("Overall trend", trend ?: "Enough data nahi")
            Text(
                "Ranking ke liye har scope ke kam se kam 3 scored tests chahiye.",
                style = MaterialTheme.typography.bodyMedium,
                color = AF.TextSecondary,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        OutlinedButton(
            onClick = { confirmDelete = true },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("DELETE ALL DATA", color = AF.Signal) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Sab data delete karein?") },
            text = { Text("Profile, sensitivity values aur saari test history phone se hamesha ke liye hat jayegi.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteAllData() }) {
                    Text("Delete", color = AF.Signal)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Row2(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AF.TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = AF.TextPrimary)
    }
}
