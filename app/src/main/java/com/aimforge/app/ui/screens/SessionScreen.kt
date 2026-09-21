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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.aimforge.app.data.TestSessionEntity
import com.aimforge.app.domain.CaptureStatus
import com.aimforge.app.domain.SessionFormat
import com.aimforge.app.domain.SessionResult
import com.aimforge.app.domain.SessionState
import com.aimforge.app.domain.TestMode
import com.aimforge.app.ui.AppViewModel
import com.aimforge.app.ui.components.GlassCard
import com.aimforge.app.ui.components.KeyValueRow
import com.aimforge.app.ui.components.PrimaryButton
import com.aimforge.app.ui.components.StatusBadge
import com.aimforge.app.ui.theme.AF
import kotlinx.coroutines.delay

fun testTitle(s: TestSessionEntity): String =
    TestMode.entries.firstOrNull { it.name == s.testType }?.title ?: s.testType

/**
 * One screen for the session's life: READY -> waiting/timer -> saved summary.
 * The screen is derived purely from the stored session, so it is correct after a restart or rotation.
 */
@Composable
fun SessionScreen(
    vm: AppViewModel,
    sessionId: String,
    onClose: () -> Unit,
    onDetails: (String) -> Unit
) {
    val sessions by vm.sessions.collectAsState()
    val session = sessions.firstOrNull { it.sessionId == sessionId }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val ticking = session != null && session.isActive && session.startedAt != null && session.pausedAt == null
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(ticking) {
        now = System.currentTimeMillis()
        if (ticking) {
            // Real wall-clock time; only ticks while the screen is visible, so nothing runs in the background.
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    now = System.currentTimeMillis()
                    delay(250)
                }
            }
        }
    }

    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    val handle: (SessionResult) -> Unit = { r ->
        error = when (r) {
            is SessionResult.Ok -> null
            is SessionResult.Rejected -> r.reason
            SessionResult.NotFound -> "Session no longer exists."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (session == null) {
            Text("Session not found", style = MaterialTheme.typography.titleLarge, color = AF.TextPrimary)
            Text("It may have been deleted.", style = MaterialTheme.typography.bodyMedium, color = AF.TextSecondary)
            PrimaryButton(text = "BACK", onClick = onClose)
            return@Column
        }

        val ended = session.endedAt != null || session.sessionState.isTerminal

        if (ended) {
            SavedSummary(session, vm.elapsedMs(session, now), onDetails = { onDetails(session.sessionId) }, onDone = onClose)
            return@Column
        }

        val isReady = session.sessionState == SessionState.READY
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(testTitle(session), style = MaterialTheme.typography.titleLarge, color = AF.TextPrimary)
            StatusBadge(
                text = when {
                    isReady -> "Ready"
                    session.pausedAt != null -> "Paused"
                    else -> "Waiting"
                },
                color = if (session.pausedAt != null) AF.Signal else AF.Accent
            )
        }

        if (isReady) {
            Text(
                "Conditions are locked. Start when you are in the practice range.",
                style = MaterialTheme.typography.bodyMedium,
                color = AF.TextSecondary
            )
            ConditionsCard(session)
        } else {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    SessionFormat.duration(vm.elapsedMs(session, now)),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Light,
                    color = AF.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Session time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AF.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (session.captureStatusEnum == CaptureStatus.NOT_AVAILABLE) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Screen capture engine is not available yet.", style = MaterialTheme.typography.titleMedium, color = AF.Signal)
                    Text(
                        "Nothing is being recorded and nothing will be analyzed. This timer only measures elapsed session time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AF.TextSecondary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            ConditionsCard(session)
        }

        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AF.Signal) }

        if (isReady) {
            PrimaryButton(text = "START SESSION", onClick = { vm.startSession(session.sessionId, handle) })
        } else {
            if (session.pausedAt != null) {
                PrimaryButton(text = "RESUME", onClick = { vm.resumeSession(session.sessionId, handle) })
            } else {
                OutlinedButton(
                    onClick = { vm.pauseSession(session.sessionId, handle) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("PAUSE", color = AF.TextPrimary) }
            }
            PrimaryButton(text = "END SESSION", onClick = { vm.endSession(session.sessionId, handle) })
        }
        TextButton(onClick = { confirmCancel = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel session", color = AF.Signal)
        }
    }

    if (confirmCancel && session != null) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel this session?") },
            text = { Text("It stays in History as Cancelled. You can delete it from its detail screen.") },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false; vm.cancelSession(session.sessionId, handle) }) {
                    Text("Cancel session", color = AF.Signal)
                }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep") } }
        )
    }
}

@Composable
private fun ConditionsCard(s: TestSessionEntity) {
    GlassCard(Modifier.fillMaxWidth()) {
        KeyValueRow("Scope", scopeText(s))
        KeyValueRow("Weapon", SessionFormat.orUnknown(s.weapon))
        KeyValueRow("Sensitivity", SessionFormat.sensitivity(s.cameraSensitivity, s.adsSensitivity, s.gyroSensitivity, s.adsGyroSensitivity))
    }
}

@Composable
private fun SavedSummary(s: TestSessionEntity, elapsed: Long, onDetails: () -> Unit, onDone: () -> Unit) {
    val cancelled = s.sessionState == SessionState.CANCELLED
    Text(
        if (cancelled) "SESSION CANCELLED" else "SESSION SAVED",
        style = MaterialTheme.typography.titleLarge,
        color = if (cancelled) AF.Signal else AF.TextPrimary
    )
    GlassCard(Modifier.fillMaxWidth()) {
        KeyValueRow("Test", testTitle(s))
        KeyValueRow("Weapon", SessionFormat.orUnknown(s.weapon))
        KeyValueRow("Sensitivity", SessionFormat.sensitivity(s.cameraSensitivity, s.adsSensitivity, s.gyroSensitivity, s.adsGyroSensitivity))
        KeyValueRow("Duration", if (s.startedAt == null) "Not started" else SessionFormat.duration(elapsed))
        KeyValueRow("Capture", s.captureStatusEnum.label)
        KeyValueRow("Analysis", s.analysisStatusEnum.label)
    }
    Spacer(Modifier.height(4.dp))
    PrimaryButton(text = "VIEW DETAILS", onClick = onDetails)
    OutlinedButton(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(16.dp)
    ) { Text("DONE", color = AF.TextPrimary) }
}

fun scopeText(s: TestSessionEntity): String =
    com.aimforge.app.domain.ScopeType.entries.firstOrNull { it.name == s.scope }?.label ?: "Unknown"
