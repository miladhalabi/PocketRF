package com.pocketrf.ble.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketrf.ble.FrequencyControl
import com.pocketrf.ble.ble.ConnState
import com.pocketrf.ble.ui.RfMode
import com.pocketrf.ble.ui.common.PocketRfTopBar
import com.pocketrf.ble.ui.common.PressableFilledButton

private data class JamModeOption(val arg: String, val label: String)

private val JAM_MODES = listOf(
    JamModeOption("full", "Full"),
    JamModeOption("itmt", "Intermittent"),
    JamModeOption("noise", "Noise"),
    JamModeOption("sweep", "Sweep"),
)

@Composable
fun JammerScreen(
    connState: ConnState,
    mode: RfMode,
    jamDone: Boolean,
    onClearLive: () -> Unit,
    onStart: (jamMode: String, freqMhz: Double?) -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    val connected = connState == ConnState.READY
    val running = mode == RfMode.JAM
    var selectedJamMode by remember { mutableStateOf(JAM_MODES.first().arg) }
    var freqText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { onClearLive() }

    Scaffold(topBar = { PocketRfTopBar("Jammer", connState, onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Every session hard-stops after 30 seconds regardless of this screen " +
                    "(a firmware safety cutoff, not a suggestion).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            JamModeChips(selected = selectedJamMode, enabled = connected && !running, onSelect = { selectedJamMode = it })
            Spacer(Modifier.height(16.dp))

            FrequencyControl(
                freqText = freqText,
                onFreqTextChange = { freqText = it },
                enabled = connected && !running,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Leave blank to use the frequency already set in Config.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            PressableFilledButton(
                label = if (running) "Stop jam" else "Start jam",
                icon = if (running) Icons.Outlined.Stop else Icons.Outlined.Block,
                enabled = connected,
                onClick = {
                    if (running) {
                        onStop()
                    } else {
                        onStart(selectedJamMode, freqText.toDoubleOrNull())
                    }
                },
            )

            if (running) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Jamming — will stop automatically within 30s.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (jamDone) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Jam session ended (30s safety cutoff).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JamModeChips(selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        JAM_MODES.forEach { option ->
            FilterChip(
                selected = selected == option.arg,
                enabled = enabled,
                onClick = { onSelect(option.arg) },
                label = { Text(option.label) },
            )
        }
    }
}
