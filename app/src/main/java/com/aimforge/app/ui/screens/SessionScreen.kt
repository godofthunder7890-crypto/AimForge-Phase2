package com.aimforge.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.aimforge.app.data.CaptureEntity
import com.aimforge.app.data.TestSessionEntity
import com.aimforge.app.domain.CaptureGrant
import com.aimforge.app.domain.CaptureRuntimeStatus
import com.aimforge.app.domain.CaptureSnapshot
import com.aimforge.app.domain.CaptureStatus
import com.aimforge.app.domain.ScopeType
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

fun scopeText(s: TestSessionEntity): String =
    ScopeType.entries.firstOrNull { it.name == s.scope }?.label ?: "Unknown"

/**
 * One screen for the session's life: READY -> starting/capturing -> saved summary.
 * It is derived from the stored session plus the live capture engine state, so it is correct after rotation or restart.
 * "Capturing" is shown only while the engine really reports a running MediaProjection for this session.
 */
@Composable
fun SessionScreen(
    vm: AppViewModel,
    sessionId: String,
    onClose: () -> Unit,
    onDetails: (String) -> Unit
) {
    val sessions by vm.sessions.collectAsState()
    val captures by vm.captures.collectAsState()
    val snap by vm.captureState.collectAsState()
    val session = sessions.firstOrNull { it.sessionId == sessionId }
    val capture = captures.lastOrNull { it.sessionId == sessionId }
    val context = LocalContext.current

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val ticking = session != null && session.isActive && session.startedAt != null && session.pausedAt == null
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(ticking) {
        now = System.currentTimeMillis()
        if (ticking) {
            // Real wall-clock time; ticks only while this screen is visible, so nothing runs in the background.
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    now = System.currentTimeMillis()
                    delay(250)
                }
            }
        }
    }

    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val handle: (SessionResult) -> Unit = { r ->
        busy = false
        message = when (r) {
            is SessionResult.Ok -> r.notice
            is SessionResult.Rejected -> r.reason
            SessionResult.NotFound -> "Session no longer exists."
        }
    }

    // Real Android permission flow: the system dialog is the only way to get a capture grant.
    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val data = res.data
        val grant = if (res.resultCode == Activity.RESULT_OK && data != null) CaptureGrant(res.resultCode, data) else null
        vm.startCapture(sessionId, grant, handle)
    }
    val launchProjection: () -> Unit = {
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }
    // Capture works without this permission; without it Android may hide the capture notification (and its Stop button).
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        launchProjection()
    }
    val onStartClicked: () -> Unit = {
        busy = true
        message = null
        val needsNotificationPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else launchProjection()
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
            SavedSummary(session, capture, vm.elapsedMs(session, now), onDetails = { onDetails(session.sessionId) }, onDone = onClose)
            return@Column
        }

        val isReady = session.sessionState == SessionState.READY
        val isCapturing = session.sessionState == SessionState.CAPTURING
        val liveHere = snap.sessionId == session.sessionId && snap.status == CaptureRuntimeStatus.RUNNING

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(testTitle(session), style = MaterialTheme.typography.titleLarge, color = AF.TextPrimary)
            StatusBadge(
                text = when {
                    isReady -> "Ready"
                    isCapturing && liveHere -> "Capturing"
                    isCapturing -> "Not running"
                    session.pausedAt != null -> "Paused"
                    else -> "Waiting"
                },
                color = when {
                    isCapturing && liveHere -> AF.Good
                    session.pausedAt != null || (isCapturing && !liveHere) -> AF.Signal
                    else -> AF.Accent
                }
            )
        }

        when {
            isReady -> {
                Text(
                    "Conditions are locked. START asks Android for screen-capture permission. Nothing is captured before you approve it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AF.TextSecondary
                )
                if (session.captureStatusEnum == CaptureStatus.PERMISSION_DENIED) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text("Screen-capture permission was denied.", style = MaterialTheme.typography.titleMedium, color = AF.Signal)
                        Text(
                            "Nothing was captured and the session did not start. Tap START to ask again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AF.TextSecondary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                ConditionsCard(session)
            }
            isCapturing -> {
                TimerCard(vm.elapsedMs(session, now))
                CapturePanel(session, snap, now)
                ConditionsCard(session)
            }
            else -> {
                TimerCard(vm.elapsedMs(session, now))
                GlassCard(Modifier.fillMaxWidth()) {
                    if (session.captureStatusEnum == CaptureStatus.NOT_AVAILABLE) {
                        Text("Screen capture engine is not available yet.", style = MaterialTheme.typography.titleMedium, color = AF.Signal)
                        Text(
                            "Nothing is being recorded. This timer only measures elapsed session time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AF.TextSecondary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    } else {
                        Text("Starting screen capture...", style = MaterialTheme.typography.titleMedium, color = AF.TextPrimary)
                        Text(
                            "Waiting for Android to confirm the capture is running.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AF.TextSecondary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                ConditionsCard(session)
            }
        }

        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AF.Signal) }

        when {
            isReady -> PrimaryButton(text = if (busy) "WAITING FOR ANDROID..." else "START SESSION", enabled = !busy, onClick = onStartClicked)
            isCapturing -> PrimaryButton(text = "END SESSION", enabled = !busy, onClick = {
                busy = true
                vm.endSession(session.sessionId, handle)
            })
            else -> {
                // Pause only exists for a session without a live capture (a live capture cannot be paused).
                if (session.captureStatusEnum == CaptureStatus.NOT_AVAILABLE) {
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
            }
        }
        TextButton(onClick = { confirmCancel = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel session", color = AF.Signal)
        }
    }

    if (confirmCancel && session != null) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel this session?") },
            text = { Text("Capture stops if it is running. The session stays in History as Cancelled. You can delete it from its detail screen.") },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false; busy = true; vm.cancelSession(session.sessionId, handle) }) {
                    Text("Cancel session", color = AF.Signal)
                }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep") } }
        )
    }
}

@Composable
private fun TimerCard(elapsedMs: Long) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(
            SessionFormat.duration(elapsedMs),
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
}

/** Live numbers from the capture engine. Every value was measured from frames Android really delivered. */
@Composable
private fun CapturePanel(session: TestSessionEntity, snap: CaptureSnapshot, now: Long) {
    val live = snap.sessionId == session.sessionId && snap.status == CaptureRuntimeStatus.RUNNING
    GlassCard(Modifier.fillMaxWidth()) {
        if (!live) {
            Text("Screen capture is not running", style = MaterialTheme.typography.titleMedium, color = AF.Signal)
            Text(
                "The session says capturing, but the capture engine reports nothing running. The state will be saved as failed.",
                style = MaterialTheme.typography.bodyMedium,
                color = AF.TextSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )
        } else {
            Text("Screen capture running", style = MaterialTheme.typography.titleMedium, color = AF.Good)
            KeyValueRow("Resolution", if (snap.width != null && snap.height != null) "${snap.width} x ${snap.height}" else "--")
            KeyValueRow("Frames received", snap.frameCount.toString())
            KeyValueRow("Measured FPS", snap.measuredFps?.let { "%.1f".format(it) } ?: "--")
            KeyValueRow("Last frame", snap.lastFrameAtMs?.let { "${(now - it).coerceAtLeast(0)} ms ago" } ?: "none yet")
            KeyValueRow(
                "Black-frame check",
                if (snap.sampledFrames == 0) "--" else "${snap.sampledFrames - snap.blankSampledFrames} of ${snap.sampledFrames} not black"
            )
            val waitedMs = snap.startedAtMs?.let { now - it } ?: 0L
            if (snap.frameCount == 0 && waitedMs > 3_000) {
                Text(
                    "No frames received yet. The screen may be static, or capture may be blocked.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AF.Signal,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (snap.sampledFrames >= 3 && snap.blankSampledFrames * 10 >= snap.sampledFrames * 8) {
                Text(
                    "Most sampled frames are black. The screen or app being captured may block screen capture.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AF.Signal,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
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
private fun SavedSummary(
    s: TestSessionEntity,
    capture: CaptureEntity?,
    elapsed: Long,
    onDetails: () -> Unit,
    onDone: () -> Unit
) {
    val cancelled = s.sessionState == SessionState.CANCELLED
    val failed = s.sessionState == SessionState.FAILED
    Text(
        when {
            cancelled -> "SESSION CANCELLED"
            failed -> "CAPTURE FAILED"
            else -> "SESSION SAVED"
        },
        style = MaterialTheme.typography.titleLarge,
        color = if (cancelled || failed) AF.Signal else AF.TextPrimary
    )
    GlassCard(Modifier.fillMaxWidth()) {
        KeyValueRow("Test", testTitle(s))
        KeyValueRow("Weapon", SessionFormat.orUnknown(s.weapon))
        KeyValueRow("Sensitivity", SessionFormat.sensitivity(s.cameraSensitivity, s.adsSensitivity, s.gyroSensitivity, s.adsGyroSensitivity))
        KeyValueRow("Duration", if (s.startedAt == null) "Not started" else SessionFormat.duration(elapsed))
        KeyValueRow("Capture", s.captureStatusEnum.label)
        capture?.frameCount?.let { KeyValueRow("Frames received", it.toString()) }
        if (capture?.widthPx != null && capture.heightPx != null) {
            KeyValueRow("Resolution", "${capture.widthPx} x ${capture.heightPx}")
        }
        KeyValueRow("Analysis", s.analysisStatusEnum.label)
        (s.failureReason ?: capture?.stopReason)?.let { KeyValueRow("Reason", it) }
    }
    Spacer(Modifier.height(4.dp))
    PrimaryButton(text = "VIEW DETAILS", onClick = onDetails)
    OutlinedButton(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(16.dp)
    ) { Text("DONE", color = AF.TextPrimary) }
}
