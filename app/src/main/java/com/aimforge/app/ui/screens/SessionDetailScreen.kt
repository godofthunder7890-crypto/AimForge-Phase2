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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimforge.app.domain.AimErrorState
import com.aimforge.app.domain.AnalysisStatus
import com.aimforge.app.domain.CaptureStatus
import com.aimforge.app.domain.Distance
import com.aimforge.app.domain.SessionFormat
import com.aimforge.app.domain.TargetType
import com.aimforge.app.ui.AppViewModel
import com.aimforge.app.ui.components.GlassCard
import com.aimforge.app.ui.components.KeyValueRow
import com.aimforge.app.ui.components.PrimaryButton
import com.aimforge.app.ui.components.SectionTitle
import com.aimforge.app.ui.formatStamp
import com.aimforge.app.ui.theme.AF

@Composable
fun SessionDetailScreen(
    vm: AppViewModel,
    sessionId: String,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit
) {
    val sessions by vm.sessions.collectAsState()
    val captures by vm.captures.collectAsState()
    val s = sessions.firstOrNull { it.sessionId == sessionId }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

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
            Text("Session", style = MaterialTheme.typography.titleLarge, color = AF.TextPrimary)
        }

        if (s == null) {
            Text("Session not found. It may have been deleted.", style = MaterialTheme.typography.bodyLarge, color = AF.TextSecondary)
            return@Column
        }

        val elapsed = vm.elapsedMs(s, System.currentTimeMillis())
        val capture = captures.lastOrNull { it.sessionId == s.sessionId }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("TEST")
            KeyValueRow("Test", testTitle(s))
            KeyValueRow("Scope", scopeText(s))
            KeyValueRow("Weapon", SessionFormat.orUnknown(s.weapon))
            KeyValueRow("Muzzle", SessionFormat.orUnknown(s.muzzle))
            KeyValueRow("Grip", SessionFormat.orUnknown(s.grip))
            KeyValueRow("Magazine", SessionFormat.orUnknown(s.magazine))
            KeyValueRow("Stock", SessionFormat.orUnknown(s.stock))
            KeyValueRow("Other attachment", SessionFormat.orUnknown(s.otherAttachment))
            KeyValueRow("Created", formatStamp(s.createdAt))
            KeyValueRow("State", s.sessionState.label)
            KeyValueRow("Duration", if (s.startedAt == null) "Not started" else SessionFormat.duration(elapsed))
            s.notes?.let { KeyValueRow("Notes", it) }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("CONDITIONS")
            KeyValueRow("Distance", Distance.entries.firstOrNull { it.name == s.distance }?.label ?: "Unknown")
            KeyValueRow("Target", TargetType.entries.firstOrNull { it.name == s.targetType }?.label ?: "Unknown")
            KeyValueRow("FPS (entered)", SessionFormat.orUnknown(s.fps))
            KeyValueRow("Refresh rate (entered)", s.refreshRate?.let { "$it Hz" } ?: "Unknown")
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("SENSITIVITY SNAPSHOT")
            KeyValueRow("Camera", SessionFormat.orUnknown(s.cameraSensitivity))
            KeyValueRow("ADS", SessionFormat.orUnknown(s.adsSensitivity))
            KeyValueRow("Gyroscope", SessionFormat.orUnknown(s.gyroSensitivity))
            KeyValueRow("ADS Gyroscope", SessionFormat.orUnknown(s.adsGyroSensitivity))
            Text("User-entered values, not read from BGMI.", style = MaterialTheme.typography.bodyMedium, color = AF.TextSecondary)
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("CAPTURE")
            KeyValueRow("Status", s.captureStatusEnum.label)
            when (s.captureStatusEnum) {
                CaptureStatus.NOT_STARTED -> Text("Waiting for capture to start.", style = MaterialTheme.typography.bodyMedium, color = AF.TextSecondary)
                CaptureStatus.NOT_AVAILABLE -> Text("Screen capture engine was not available. No gameplay was recorded.", style = MaterialTheme.typography.bodyMedium, color = AF.TextSecondary)
                CaptureStatus.PERMISSION_DENIED -> Text("Screen-capture permission was denied. Nothing was captured.", style = MaterialTheme.typography.bodyMedium, color = AF.Signal)
                else -> Unit
            }
            capture?.let { c ->
                KeyValueRow("Permission granted", when (c.permissionGranted) { true -> "Yes"; false -> "No"; null -> "Unknown" })
                KeyValueRow("Frames received", c.frameCount?.toString() ?: "--")
                KeyValueRow("Resolution", if (c.widthPx != null && c.heightPx != null) "${c.widthPx} x ${c.heightPx}" else "--")
                KeyValueRow("Measured frame rate", c.fps?.let { "%.1f /s".format(it) } ?: "--")
                KeyValueRow(
                    "Non-black samples",
                    if (c.sampledFrameCount == null || c.sampledFrameCount == 0) "--"
                    else "${c.sampledFrameCount - (c.blankFrameCount ?: 0)} of ${c.sampledFrameCount}"
                )
                if (c.startTime != null && c.endTime != null) {
                    KeyValueRow("Capture duration", SessionFormat.duration((c.endTime - c.startTime).coerceAtLeast(0L)))
                }
                c.stopReason?.let { KeyValueRow("Ended by", it) }
                s.failureReason?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AF.Signal) }
                KeyValueRow("Frames stored", "No (counted live, not saved)")
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("ANALYSIS")
            KeyValueRow("Status", s.analysisStatusEnum.label)
            if (s.hasAnalysis) {
                KeyValueRow("Aim score", s.aimScore.toString())
                KeyValueRow("Pattern", AimErrorState.entries.firstOrNull { it.name == s.errorState }?.label ?: "--")
                KeyValueRow("Confidence", s.confidence?.let { "$it%" } ?: "--")
            } else {
                KeyValueRow("Score", "Not analyzed")
            }
        }

        if (s.isActive) {
            PrimaryButton(text = "OPEN SESSION", onClick = { onOpenSession(s.sessionId) })
        }
        OutlinedButton(
            onClick = { confirmDelete = true },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("DELETE SESSION", color = AF.Signal) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this session?") },
            text = { Text("The session record and any capture data linked to it are removed from this phone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteSession(sessionId) { onBack() } }) {
                    Text("Delete", color = AF.Signal)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
