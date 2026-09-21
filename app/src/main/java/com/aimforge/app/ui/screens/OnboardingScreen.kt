package com.aimforge.app.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aimforge.app.ui.AppViewModel
import com.aimforge.app.ui.components.GlassCard
import com.aimforge.app.ui.components.PrimaryButton
import com.aimforge.app.ui.components.afFieldColors
import com.aimforge.app.ui.theme.AF

private val controlOptions = listOf("2-finger", "3-finger", "4-finger claw", "5-finger+", "Thumbs")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(vm: AppViewModel) {
    var step by remember { mutableIntStateOf(0) }
    var device by remember { mutableStateOf("${Build.MANUFACTURER} ${Build.MODEL}".trim()) }
    var control by remember { mutableStateOf<String?>(null) }
    var gyro by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier
                            .size(width = if (i == step) 28.dp else 8.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(if (i <= step) AF.Accent else AF.Track)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            when (step) {
                0 -> {
                    Text("WELCOME TO AIMFORGE", style = MaterialTheme.typography.titleLarge, color = AF.TextPrimary)
                    Text(
                        "Ye app tumhari gameplay ko analyze karega aur tumhari personal sensitivity tune karne mein help karega.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AF.TextSecondary
                    )
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(
                            "Ye app BGMI ko touch nahi karta, koi file modify nahi karta, aur tumhare taps simulate nahi karta. " +
                                "Sirf jo gameplay tum share karoge use dekhta hai. Sab kuch phone ke andar hi rehta hai.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AF.TextPrimary
                        )
                    }
                }
                1 -> {
                    Text("Device aur control", style = MaterialTheme.typography.titleLarge, color = AF.TextPrimary)
                    OutlinedTextField(
                        value = device,
                        onValueChange = { device = it },
                        label = { Text("Device (auto-detected, edit if wrong)") },
                        singleLine = true,
                        colors = afFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Control layout", style = MaterialTheme.typography.labelMedium, color = AF.TextSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        controlOptions.forEach { opt ->
                            FilterChip(selected = control == opt, onClick = { control = opt }, label = { Text(opt) })
                        }
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Gyro use karte ho?", style = MaterialTheme.typography.bodyLarge, color = AF.TextPrimary)
                            Text("Optional. Lab mein gyro values baad mein bhi daal sakte ho.", style = MaterialTheme.typography.bodyMedium, color = AF.TextSecondary)
                        }
                        Switch(
                            checked = gyro,
                            onCheckedChange = { gyro = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = AF.Accent, checkedThumbColor = AF.Bg)
                        )
                    }
                }
                else -> {
                    Text("Ready", style = MaterialTheme.typography.titleLarge, color = AF.TextPrimary)
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text("Aage ka plan", style = MaterialTheme.typography.titleMedium, color = AF.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "1. Lab tab mein apni current BGMI sensitivity manually daalo. App BGMI ki values khud nahi padh sakta.\n" +
                                "2. Screen capture permission aur pehla diagnostic test agle phases mein aayega.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AF.TextSecondary
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (step > 0) {
                TextButton(onClick = { step -= 1 }) { Text("Back", color = AF.TextSecondary) }
            }
            PrimaryButton(
                text = if (step < 2) "Next" else "Shuru karo",
                onClick = { if (step < 2) step += 1 else vm.completeOnboarding(device, control ?: "", gyro) },
                enabled = step != 1 || (control != null && device.isNotBlank()),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
