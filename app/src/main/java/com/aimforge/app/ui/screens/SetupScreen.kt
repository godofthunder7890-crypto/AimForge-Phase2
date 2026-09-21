package com.aimforge.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aimforge.app.domain.Distance
import com.aimforge.app.domain.DiagnosticPlan
import com.aimforge.app.domain.ScopeType
import com.aimforge.app.domain.SensType
import com.aimforge.app.domain.SessionDraft
import com.aimforge.app.domain.SessionOptions
import com.aimforge.app.domain.SessionResult
import com.aimforge.app.domain.TargetType
import com.aimforge.app.domain.TestMode
import com.aimforge.app.ui.AppViewModel
import com.aimforge.app.ui.components.ActiveSessionCard
import com.aimforge.app.ui.components.GlassCard
import com.aimforge.app.ui.components.KeyValueRow
import com.aimforge.app.ui.components.PrimaryButton
import com.aimforge.app.ui.components.SectionTitle
import com.aimforge.app.ui.components.afFieldColors
import com.aimforge.app.ui.theme.AF
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
    vm: AppViewModel,
    mode: TestMode,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit
) {
    val active by vm.activeSession.collectAsState()

    // Form state survives rotation. null / blank = Unknown.
    var scopeName by rememberSaveable(mode) { mutableStateOf(mode.scope?.name) }
    var weapon by rememberSaveable { mutableStateOf<String?>(null) }
    var weaponOther by rememberSaveable { mutableStateOf("") }
    var muzzle by rememberSaveable { mutableStateOf<String?>(null) }
    var grip by rememberSaveable { mutableStateOf<String?>(null) }
    var magazine by rememberSaveable { mutableStateOf<String?>(null) }
    var stock by rememberSaveable { mutableStateOf<String?>(null) }
    var otherAttachment by rememberSaveable { mutableStateOf("") }
    var showAttachments by rememberSaveable { mutableStateOf(false) }
    var distance by rememberSaveable { mutableStateOf<String?>(null) }
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    var fps by rememberSaveable { mutableStateOf<String?>(null) }
    var fpsManual by rememberSaveable { mutableStateOf("") }
    var refresh by rememberSaveable { mutableStateOf<String?>(null) }
    var refreshManual by rememberSaveable { mutableStateOf("") }
    var cam by rememberSaveable { mutableStateOf("") }
    var ads by rememberSaveable { mutableStateOf("") }
    var gyro by rememberSaveable { mutableStateOf("") }
    var adsGyro by rememberSaveable { mutableStateOf("") }
    var prefilledFor by rememberSaveable { mutableStateOf<String?>("__none__") }
    var notes by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    // Prefill the snapshot from the values the user typed in the Lab. Only once per scope so edits survive rotation.
    LaunchedEffect(scopeName) {
        if (prefilledFor != scopeName) {
            val saved = vm.sensitivities.value
            fun v(t: SensType) = scopeName?.let { saved[it to t.name]?.toString() } ?: ""
            cam = v(SensType.CAMERA); ads = v(SensType.ADS); gyro = v(SensType.GYRO); adsGyro = v(SensType.ADS_GYRO)
            prefilledFor = scopeName
        }
    }

    // Real device API value. Shown as information; the user decides what to store.
    val displayHz = LocalView.current.display?.refreshRate?.roundToInt()

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
            Text(mode.title, style = MaterialTheme.typography.titleLarge, color = AF.TextPrimary)
        }

        GlassCard(Modifier.fillMaxWidth()) {
            KeyValueRow("Recommended distance", mode.distance)
            KeyValueRow("Target", mode.target)
            KeyValueRow("Do", mode.doText)
            KeyValueRow("Keep", mode.keepText)
        }

        if (mode == TestMode.FULL_DIAGNOSTIC) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Planned sequence")
                DiagnosticPlan.steps.forEachIndexed { i, step ->
                    Text("${i + 1}. ${step.mode.title}", style = MaterialTheme.typography.bodyLarge, color = AF.TextPrimary, modifier = Modifier.padding(vertical = 2.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Not available yet. Each step must become its own real session, and that needs the capture engine (Phase 3). For now run these tests one by one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AF.TextSecondary
                )
            }
            return@Column
        }

        active?.let { a ->
            ActiveSessionCard(a, onOpen = { onOpenSession(a.sessionId) })
            Text(
                "Finish or cancel the open session before creating a new one.",
                style = MaterialTheme.typography.bodyMedium,
                color = AF.Signal
            )
        }

        // ---- Scope ----
        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Scope")
            ChoiceRow(ScopeType.entries.map { it.label }, ScopeType.entries.firstOrNull { it.name == scopeName }?.label) { picked ->
                scopeName = ScopeType.entries.firstOrNull { it.label == picked }?.name
            }
            HintText("Not selected = Unknown.")
        }

        // ---- Weapon ----
        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Weapon")
            ChoiceRow(SessionOptions.weapons, weapon) { weapon = it }
            if (weapon == SessionOptions.OTHER) {
                OutlinedTextField(
                    value = weaponOther,
                    onValueChange = { weaponOther = it.take(30) },
                    label = { Text("Weapon name") },
                    singleLine = true,
                    colors = afFieldColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            HintText("Not selected = Unknown. Weapon and attachment changes make two tests non-comparable.")
        }

        // ---- Attachments (optional) ----
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                SectionTitle("Attachments (optional)")
                TextButton(onClick = { showAttachments = !showAttachments }) {
                    Text(if (showAttachments) "Hide" else "Add", color = AF.Accent)
                }
            }
            if (showAttachments) {
                AttachmentSlot("Muzzle", SessionOptions.muzzle, muzzle) { muzzle = it }
                AttachmentSlot("Grip", SessionOptions.grip, grip) { grip = it }
                AttachmentSlot("Magazine", SessionOptions.magazine, magazine) { magazine = it }
                AttachmentSlot("Stock", SessionOptions.stock, stock) { stock = it }
                OutlinedTextField(
                    value = otherAttachment,
                    onValueChange = { otherAttachment = it.take(40) },
                    label = { Text("Other attachment") },
                    singleLine = true,
                    colors = afFieldColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                HintText("Not selected = Unknown. None = you confirmed nothing is attached.")
            }
        }

        // ---- Conditions ----
        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Distance")
            ChoiceRow(Distance.entries.map { it.label }, Distance.entries.firstOrNull { it.name == distance }?.label) { picked ->
                distance = Distance.entries.firstOrNull { it.label == picked }?.name
            }
            Spacer(Modifier.height(12.dp))
            SectionTitle("Target")
            ChoiceRow(TargetType.entries.map { it.label }, TargetType.entries.firstOrNull { it.name == target }?.label) { picked ->
                target = TargetType.entries.firstOrNull { it.label == picked }?.name
            }
            Spacer(Modifier.height(12.dp))
            SectionTitle("FPS (in game)")
            ChoiceRow(SessionOptions.fps.map { it.toString() }, fps) { fps = it; if (it != null) fpsManual = "" }
            NumberField("Other FPS", fpsManual) { fpsManual = it; if (it.isNotEmpty()) fps = null }
            Spacer(Modifier.height(12.dp))
            SectionTitle("Refresh rate (Hz)")
            ChoiceRow(SessionOptions.refreshRates.map { it.toString() }, refresh) { refresh = it; if (it != null) refreshManual = "" }
            NumberField("Other refresh rate", refreshManual) { refreshManual = it; if (it.isNotEmpty()) refresh = null }
            if (displayHz != null) {
                TextButton(onClick = { refresh = null; refreshManual = displayHz.toString() }) {
                    Text("Screen reports $displayHz Hz right now. Use it", color = AF.Accent)
                }
            }
            HintText("Nothing is measured automatically. Leave blank if you are not sure.")
        }

        // ---- Sensitivity snapshot ----
        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Sensitivity snapshot")
            Text(
                "Values you enter. AimForge cannot read BGMI settings. Blank = Unknown.",
                style = MaterialTheme.typography.bodyMedium,
                color = AF.TextSecondary
            )
            NumberField("Camera", cam) { cam = it }
            NumberField("ADS", ads) { ads = it }
            NumberField("Gyroscope", gyro) { gyro = it }
            NumberField("ADS Gyroscope", adsGyro) { adsGyro = it }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it.take(300) },
            label = { Text("Notes (optional)") },
            colors = afFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AF.Signal) }

        Text(
            "Locking saves these conditions with the session. They cannot be edited afterwards.",
            style = MaterialTheme.typography.bodyMedium,
            color = AF.TextSecondary
        )
        PrimaryButton(
            text = "LOCK CONDITIONS",
            enabled = active == null,
            onClick = {
                val draft = SessionDraft(
                    testType = mode,
                    scope = ScopeType.entries.firstOrNull { it.name == scopeName },
                    weapon = if (weapon == SessionOptions.OTHER) weaponOther.ifBlank { SessionOptions.OTHER } else weapon,
                    muzzle = muzzle,
                    grip = grip,
                    magazine = magazine,
                    stock = stock,
                    otherAttachment = otherAttachment,
                    distance = Distance.entries.firstOrNull { it.name == distance },
                    targetType = TargetType.entries.firstOrNull { it.name == target },
                    cameraSensitivity = cam.toIntOrNull(),
                    adsSensitivity = ads.toIntOrNull(),
                    gyroSensitivity = gyro.toIntOrNull(),
                    adsGyroSensitivity = adsGyro.toIntOrNull(),
                    fps = fpsManual.toIntOrNull() ?: fps?.toIntOrNull(),
                    refreshRate = refreshManual.toIntOrNull() ?: refresh?.toIntOrNull(),
                    notes = notes
                )
                vm.createSession(draft) { result ->
                    when (result) {
                        is SessionResult.Ok -> onOpenSession(result.session.sessionId)
                        is SessionResult.Rejected -> error = result.reason
                        SessionResult.NotFound -> error = "Session could not be created."
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(options: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(
                selected = selected == o,
                onClick = { onSelect(if (selected == o) null else o) },
                label = { Text(o) }
            )
        }
    }
}

@Composable
private fun AttachmentSlot(label: String, options: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = AF.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
        ChoiceRow(listOf(SessionOptions.NONE) + options, selected, onSelect)
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(3)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = afFieldColors(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
}

@Composable
private fun HintText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = AF.TextSecondary, modifier = Modifier.padding(top = 8.dp))
}
