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
import androidx.compose.material.icons.outlined.SsidChart
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
import com.pocketrf.ble.SpectrumGauge
import com.pocketrf.ble.SweepChart
import com.pocketrf.ble.ble.ConnState
import com.pocketrf.ble.ble.PocketRfEvent
import com.pocketrf.ble.ui.RfMode
import com.pocketrf.ble.ui.common.PocketRfTopBar
import com.pocketrf.ble.ui.common.PressableFilledButton

private enum class SpectrumKind { FIXED, SWEEP }

@Composable
fun SpectrumScreen(
    connState: ConnState,
    mode: RfMode,
    spectrumFixedLive: PocketRfEvent.SpectrumFixed?,
    spectrumSweepLive: PocketRfEvent.SpectrumSweep?,
    onClearLive: () -> Unit,
    onStartFixed: (freqMhz: Double?) -> Unit,
    onStartSweep: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    val connected = connState == ConnState.READY
    var kind by remember { mutableStateOf(SpectrumKind.FIXED) }
    var freqText by remember { mutableStateOf("433.92") }
    val running = mode == RfMode.SPECTRUM_FIXED || mode == RfMode.SPECTRUM_SWEEP

    LaunchedEffect(Unit) { onClearLive() }

    Scaffold(topBar = { PocketRfTopBar("Spectrum", connState, onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            SpectrumKindChips(kind = kind, enabled = !running, onSelect = { kind = it })
            Spacer(Modifier.height(16.dp))

            if (kind == SpectrumKind.FIXED) {
                FrequencyControl(freqText = freqText, onFreqTextChange = { freqText = it }, enabled = connected && !running)
                Spacer(Modifier.height(16.dp))
            }

            PressableFilledButton(
                label = if (running) "Stop" else "Start",
                icon = if (running) Icons.Outlined.Stop else Icons.Outlined.SsidChart,
                enabled = connected,
                onClick = {
                    if (running) {
                        onStop()
                    } else if (kind == SpectrumKind.FIXED) {
                        onStartFixed(freqText.toDoubleOrNull())
                    } else {
                        onStartSweep()
                    }
                },
            )

            Spacer(Modifier.height(24.dp))
            if (mode == RfMode.SPECTRUM_FIXED) {
                SpectrumGauge(spectrumFixedLive?.rssi)
            } else if (mode == RfMode.SPECTRUM_SWEEP) {
                SweepChart(spectrumSweepLive?.rssi ?: emptyList())
            } else {
                Text(
                    "Pick fixed-frequency RSSI or a full-band sweep, then Start.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpectrumKindChips(kind: SpectrumKind, enabled: Boolean, onSelect: (SpectrumKind) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FilterChip(selected = kind == SpectrumKind.FIXED, enabled = enabled, onClick = { onSelect(SpectrumKind.FIXED) }, label = { Text("Fixed RSSI") })
        FilterChip(selected = kind == SpectrumKind.SWEEP, enabled = enabled, onClick = { onSelect(SpectrumKind.SWEEP) }, label = { Text("Sweep") })
    }
}
