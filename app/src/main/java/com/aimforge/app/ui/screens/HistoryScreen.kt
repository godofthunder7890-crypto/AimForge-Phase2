package com.aimforge.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimforge.app.data.TestSessionEntity
import com.aimforge.app.domain.SessionFormat
import com.aimforge.app.ui.AppViewModel
import com.aimforge.app.ui.components.GlassCard
import com.aimforge.app.ui.components.KeyValueRow
import com.aimforge.app.ui.components.StatusBadge
import com.aimforge.app.ui.formatStamp
import com.aimforge.app.ui.startOfDay
import com.aimforge.app.ui.theme.AF

private enum class Range(val label: String) {
    TODAY("Today"), YESTERDAY("Yesterday"), WEEK("Last 7 days"), MONTH("Last 30 days");

    fun matches(ts: Long): Boolean = when (this) {
        TODAY -> ts >= startOfDay(0)
        YESTERDAY -> ts >= startOfDay(1) && ts < startOfDay(0)
        WEEK -> ts >= startOfDay(6)
        MONTH -> ts >= startOfDay(29)
    }
}

@Composable
fun HistoryScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val sessions by vm.sessions.collectAsState()
    var rangeName by rememberSaveable { mutableStateOf(Range.WEEK.name) }
    val range = Range.valueOf(rangeName)
    val shown: List<TestSessionEntity> = sessions.filter { range.matches(it.createdAt) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("History", style = MaterialTheme.typography.titleLarge, color = AF.TextPrimary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Range.entries.toList(), key = { it.name }) { r ->
                FilterChip(selected = range == r, onClick = { rangeName = r.name }, label = { Text(r.label) })
            }
        }
        if (shown.isEmpty()) {
            Text(
                if (sessions.isEmpty()) "No analysis yet" else "No tests in this range",
                style = MaterialTheme.typography.titleMedium,
                color = AF.TextPrimary
            )
            if (sessions.isEmpty()) {
                Text("Run your first test.", style = MaterialTheme.typography.bodyMedium, color = AF.TextSecondary)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(shown, key = { it.sessionId }) { s ->
                    GlassCard(Modifier.fillMaxWidth(), onClick = { onOpen(s.sessionId) }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(formatStamp(s.createdAt), style = MaterialTheme.typography.labelMedium, color = AF.TextSecondary)
                            StatusBadge(s.sessionState.label, color = if (s.sessionState.name == "CANCELLED") AF.Signal else AF.Accent)
                        }
                        Text(testTitle(s), style = MaterialTheme.typography.titleMedium, color = AF.TextPrimary)
                        KeyValueRow("Scope", scopeText(s))
                        KeyValueRow("Weapon", SessionFormat.orUnknown(s.weapon))
                        KeyValueRow("Duration", if (s.startedAt == null) "Not started" else SessionFormat.duration(vm.elapsedMs(s, System.currentTimeMillis())))
                        KeyValueRow("Capture", s.captureStatusEnum.label)
                        KeyValueRow("Analysis", s.analysisStatusEnum.label)
                        KeyValueRow("Score", if (s.hasAnalysis) s.aimScore.toString() else "Not analyzed")
                    }
                }
            }
        }
    }
}
