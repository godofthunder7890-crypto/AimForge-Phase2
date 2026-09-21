package com.aimforge.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimforge.app.domain.AimErrorState
import com.aimforge.app.ui.AppViewModel
import com.aimforge.app.ui.components.GlassCard
import com.aimforge.app.ui.components.KeyValueRow
import com.aimforge.app.ui.formatStamp
import com.aimforge.app.ui.theme.AF

/** Shows the latest ANALYZED session only. Until an analysis engine exists this always shows the empty state. */
@Composable
fun ReportScreen(vm: AppViewModel, onBack: () -> Unit) {
    val sessions by vm.sessions.collectAsState()
    val s = sessions.firstOrNull { it.hasAnalysis }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AF.TextPrimary)
            }
            Text("Session Report", style = MaterialTheme.typography.titleLarge, color = AF.TextPrimary)
        }
        if (s == null) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("No analysis yet", style = MaterialTheme.typography.titleMedium, color = AF.TextPrimary)
                Text(
                    if (sessions.isEmpty()) "Run your first test."
                    else "Sessions exist, but none has been analyzed. Analysis needs captured gameplay.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AF.TextSecondary
                )
            }
        } else {
            val state = AimErrorState.entries.firstOrNull { it.name == s.errorState }
            GlassCard(Modifier.fillMaxWidth()) {
                Text(formatStamp(s.createdAt), style = MaterialTheme.typography.labelMedium, color = AF.TextSecondary)
                Text(testTitle(s), style = MaterialTheme.typography.titleMedium, color = AF.TextPrimary)
                KeyValueRow("Aim score", s.aimScore?.toString() ?: "--")
                KeyValueRow("Detected pattern", state?.label ?: "--")
                KeyValueRow("Confidence", s.confidence?.let { "$it%" } ?: "--")
                KeyValueRow("Aim error", s.aimErrorPx?.let { "${"%.1f".format(it)} px" } ?: "--")
                KeyValueRow("Stability", s.stability?.toString() ?: "--")
            }
            if ((s.confidence ?: 0) < 60) {
                Text("Data insufficient. Recommendation nahi di ja rahi.", style = MaterialTheme.typography.bodyMedium, color = AF.Signal)
            } else {
                s.recommendation?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = AF.Accent) }
            }
        }
    }
}
