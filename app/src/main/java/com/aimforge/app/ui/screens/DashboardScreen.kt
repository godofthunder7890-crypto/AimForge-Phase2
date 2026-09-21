package com.aimforge.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimforge.app.domain.AimErrorState
import com.aimforge.app.domain.TestMode
import com.aimforge.app.ui.AppViewModel
import com.aimforge.app.ui.components.ActiveSessionCard
import com.aimforge.app.ui.components.GlassCard
import com.aimforge.app.ui.components.KeyValueRow
import com.aimforge.app.ui.components.PrimaryButton
import com.aimforge.app.ui.components.ReticleScore
import com.aimforge.app.ui.components.SectionTitle
import com.aimforge.app.ui.startOfDay
import com.aimforge.app.ui.theme.AF

@Composable
fun DashboardScreen(
    vm: AppViewModel,
    onStart: () -> Unit,
    onQuickTest: (TestMode) -> Unit,
    onReport: () -> Unit,
    onOpenSession: (String) -> Unit
) {
    val sessions by vm.sessions.collectAsState()
    val active by vm.activeSession.collectAsState()
    val todayStart = startOfDay(0)

    // Scores and patterns exist only for sessions a real analysis engine completed.
    val analyzed = sessions.filter { it.hasAnalysis }
    val todayScore = analyzed.firstOrNull { it.createdAt >= todayStart }?.aimScore
    val latest = sessions.firstOrNull()
    val latestAnalyzed = analyzed.firstOrNull()

    val latestState = latestAnalyzed?.let { s -> AimErrorState.entries.firstOrNull { it.name == s.errorState } }
    val lowConfidence = latestAnalyzed != null && (latestAnalyzed.confidence ?: 0) < 60
    val problem: String? =
        if (latestAnalyzed == null || lowConfidence) null
        else if (latestState == null || latestState == AimErrorState.GOOD || latestState == AimErrorState.INSUFFICIENT_DATA) null
        else latestState.label

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text("AIMFORGE", style = MaterialTheme.typography.headlineLarge, color = AF.TextPrimary)
            Text("Personal Aim Laboratory", style = MaterialTheme.typography.bodyMedium, color = AF.TextSecondary)
        }

        active?.let { a -> ActiveSessionCard(a, onOpen = { onOpenSession(a.sessionId) }) }

        PrimaryButton(text = "START ANALYSIS", onClick = onStart)

        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Today's Aim Score")
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ReticleScore(score = todayScore)
                if (todayScore == null) {
                    if (sessions.isEmpty()) {
                        Text("No analysis yet", style = MaterialTheme.typography.titleMedium, color = AF.TextPrimary)
                        Text("Run your first test.", style = MaterialTheme.typography.bodyMedium, color = AF.TextSecondary, textAlign = TextAlign.Center)
                    } else {
                        Text("No analyzed test today", style = MaterialTheme.typography.bodyMedium, color = AF.TextSecondary, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Latest detected pattern")
            when {
                latestAnalyzed == null -> {
                    Text("No analysis yet", style = MaterialTheme.typography.titleMedium, color = AF.TextPrimary)
                    Text(
                        if (sessions.isEmpty()) "Run your first test." else "No session has been analyzed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AF.TextSecondary
                    )
                }
                lowConfidence -> Text("Not enough visual evidence. Retest.", style = MaterialTheme.typography.bodyLarge, color = AF.TextSecondary)
                problem == null -> Text("No problem pattern detected in the latest analyzed test.", style = MaterialTheme.typography.bodyLarge, color = AF.TextSecondary)
                else -> {
                    Text(testTitle(latestAnalyzed), style = MaterialTheme.typography.bodyMedium, color = AF.TextSecondary)
                    Text(problem, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = AF.Signal)
                    Text("Confidence ${latestAnalyzed.confidence}%", style = MaterialTheme.typography.bodyMedium, color = AF.TextSecondary)
                }
            }
        }

        Column {
            SectionTitle("Quick Tests")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                TestMode.quick.forEach { mode ->
                    OutlinedButton(
                        onClick = { onQuickTest(mode) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            when (mode) {
                                TestMode.RED_DOT_SPRAY -> "Red Dot"
                                TestMode.SPRAY_3X -> "3x"
                                else -> "4x"
                            },
                            color = AF.TextPrimary
                        )
                    }
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Recent Result")
            if (latest == null) {
                Text("No analysis yet", style = MaterialTheme.typography.titleMedium, color = AF.TextPrimary)
                Text("Run your first test.", style = MaterialTheme.typography.bodyMedium, color = AF.TextSecondary)
            } else {
                Text(testTitle(latest), style = MaterialTheme.typography.titleMedium, color = AF.TextPrimary)
                KeyValueRow("Capture", latest.captureStatusEnum.label)
                KeyValueRow("Analysis", latest.analysisStatusEnum.label)
            }
        }

        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = onReport,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("VIEW FULL REPORT", color = AF.TextPrimary) }
    }
}
