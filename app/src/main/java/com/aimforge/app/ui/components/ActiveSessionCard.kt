package com.aimforge.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aimforge.app.data.TestSessionEntity
import com.aimforge.app.domain.SessionState
import com.aimforge.app.domain.TestMode
import com.aimforge.app.ui.theme.AF

/** Shown when a session is still open (also after an app restart). Tap opens it. */
@Composable
fun ActiveSessionCard(session: TestSessionEntity, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val title = TestMode.entries.firstOrNull { it.name == session.testType }?.title ?: session.testType
    GlassCard(modifier.fillMaxWidth(), onClick = onOpen) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Open session", style = MaterialTheme.typography.labelMedium, color = AF.TextSecondary)
            StatusBadge(
                text = if (session.sessionState == SessionState.READY) "Ready"
                else if (session.pausedAt != null) "Paused" else "Waiting"
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = AF.TextPrimary)
    }
}
