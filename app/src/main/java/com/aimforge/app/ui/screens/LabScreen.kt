package com.aimforge.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aimforge.app.domain.ScopeType
import com.aimforge.app.domain.SensType
import com.aimforge.app.ui.AppViewModel
import com.aimforge.app.ui.components.GlassCard
import com.aimforge.app.ui.components.PrimaryButton
import com.aimforge.app.ui.components.afFieldColors
import com.aimforge.app.ui.theme.AF

@Composable
fun LabScreen(vm: AppViewModel) {
    val saved by vm.sensitivities.collectAsState()
    var scope by remember { mutableStateOf(ScopeType.RED_DOT) }
    var savedTick by remember { mutableStateOf(false) }
    val fields = remember { mutableStateMapOf<SensType, String>() }

    LaunchedEffect(scope, saved) {
        SensType.entries.forEach { t -> fields[t] = saved[scope.name to t.name]?.toString() ?: "" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Sensitivity Lab", style = MaterialTheme.typography.titleLarge, color = AF.TextPrimary)
        Text(
            "BGMI ke settings se apni current values yahan manually daalo. App BGMI ki values khud nahi padh sakta.",
            style = MaterialTheme.typography.bodyMedium,
            color = AF.TextSecondary
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ScopeType.entries.toList(), key = { it.name }) { s ->
                FilterChip(
                    selected = scope == s,
                    onClick = { scope = s; savedTick = false },
                    label = { Text(s.label) }
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Text(scope.label, style = MaterialTheme.typography.titleMedium, color = AF.TextPrimary)
            SensType.entries.forEach { t ->
                OutlinedTextField(
                    value = fields[t] ?: "",
                    onValueChange = { v ->
                        fields[t] = v.filter { it.isDigit() }.take(3)
                        savedTick = false
                    },
                    label = { Text(t.label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = afFieldColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }
        }

        PrimaryButton(
            text = "SAVE",
            onClick = {
                vm.saveSensitivities(scope, SensType.entries.associateWith { fields[it]?.toIntOrNull() }) { savedTick = true }
            }
        )
        if (savedTick) {
            Text("${scope.label} values save ho gayi.", style = MaterialTheme.typography.bodyMedium, color = AF.Good)
        }
    }
}
