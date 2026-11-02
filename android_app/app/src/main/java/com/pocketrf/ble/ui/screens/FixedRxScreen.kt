package com.pocketrf.ble.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Stop
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
import com.pocketrf.ble.ble.PocketRfEvent
import com.pocketrf.ble.ui.RfMode
import com.pocketrf.ble.ui.common.ActivityLogList
import com.pocketrf.ble.ui.common.PocketRfTopBar
import com.pocketrf.ble.ui.common.PressableFilledButton
import com.pocketrf.ble.ui.common.SaveCaptureDialog

@Composable
fun FixedRxScreen(
    connState: ConnState,
    mode: RfMode,
    log: List<PocketRfEvent>,
    isSaveable: (PocketRfEvent) -> Boolean,
    suggestedLabel: (PocketRfEvent) -> String,
    onClearLog: () -> Unit,
    onSaveCapture: (PocketRfEvent, String) -> Unit,
    onReplayCapture: (PocketRfEvent) -> Unit,
    onSetFrequency: (freqHz: Long) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    val connected = connState == ConnState.READY
    val running = mode == RfMode.RX
    var freqText by remember { mutableStateOf("433.92") }
    var captureToSave by remember { mutableStateOf<PocketRfEvent?>(null) }

    LaunchedEffect(Unit) { onClearLog() }

    Scaffold(topBar = { PocketRfTopBar("Fixed Freq Scan/copy", connState, onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Decodes known protocols on a frequency you set, instead of auto-sweeping.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            FrequencyControl(
                freqText = freqText,
                onFreqTextChange = { freqText = it },
                enabled = connected && !running,
                onSetFrequency = { freqText.toDoubleOrNull()?.let { mhz -> onSetFrequency((mhz * 1_000_000).toLong()) } },
            )
            Spacer(Modifier.height(16.dp))
            PressableFilledButton(
                label = if (running) "Stop RX" else "Start RX",
                icon = if (running) Icons.Outlined.Stop else Icons.Outlined.GraphicEq,
                enabled = connected,
                onClick = { if (running) onStop() else onStart() },
            )

            Spacer(Modifier.height(24.dp))
            Text("Decoded & raw signals", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(10.dp))
            Text(
                "Replay a signal straight from here, or bookmark it to save with a name.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ActivityLogList(
                log = log,
                connected = connected,
                emptyHint = "Start RX to see decoded and raw signals here.",
                isSaveable = isSaveable,
                onSaveClick = { captureToSave = it },
                onReplayClick = onReplayCapture,
            )
        }
    }

    captureToSave?.let { event ->
        SaveCaptureDialog(
            suggestedLabel = suggestedLabel(event),
            onConfirm = { label -> onSaveCapture(event, label); captureToSave = null },
            onDismiss = { captureToSave = null },
        )
    }
}
