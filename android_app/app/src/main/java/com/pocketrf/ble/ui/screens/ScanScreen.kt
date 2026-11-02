package com.pocketrf.ble.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Radar
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
import com.pocketrf.ble.ScanLivePanel
import com.pocketrf.ble.ScanThresholdControl
import com.pocketrf.ble.ble.ConnState
import com.pocketrf.ble.ble.PocketRfEvent
import com.pocketrf.ble.ui.RfMode
import com.pocketrf.ble.ui.common.ActivityLogList
import com.pocketrf.ble.ui.common.PocketRfTopBar
import com.pocketrf.ble.ui.common.PressableFilledButton
import com.pocketrf.ble.ui.common.SaveCaptureDialog

@Composable
fun ScanScreen(
    connState: ConnState,
    mode: RfMode,
    scanLockedFreq: Double?,
    log: List<PocketRfEvent>,
    defaultThresholdDbm: Int,
    isSaveable: (PocketRfEvent) -> Boolean,
    suggestedLabel: (PocketRfEvent) -> String,
    onClearLog: () -> Unit,
    onSaveCapture: (PocketRfEvent, String) -> Unit,
    onReplayCapture: (PocketRfEvent) -> Unit,
    onStart: (thresholdDbm: Int?) -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    val connected = connState == ConnState.READY
    val running = mode == RfMode.SCAN
    var thresholdText by remember(defaultThresholdDbm) { mutableStateOf(defaultThresholdDbm.toString()) }
    var captureToSave by remember { mutableStateOf<PocketRfEvent?>(null) }

    LaunchedEffect(Unit) { onClearLog() }

    Scaffold(topBar = { PocketRfTopBar("Scan/copy", connState, onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Sweeps 300-928 MHz, locks onto the strongest signal above the threshold, then decodes continuously.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            ScanThresholdControl(
                thresholdText = thresholdText,
                onThresholdTextChange = { thresholdText = it },
                enabled = connected && !running,
            )
            Spacer(Modifier.height(16.dp))
            PressableFilledButton(
                label = if (running) "Stop scan" else "Start scan",
                icon = if (running) Icons.Outlined.Stop else Icons.Outlined.Radar,
                enabled = connected,
                onClick = { if (running) onStop() else onStart(thresholdText.toIntOrNull()) },
            )

            if (running) {
                Spacer(Modifier.height(20.dp))
                ScanLivePanel(scanLockedFreq)
            }

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
                emptyHint = "Start a scan to see locked, decoded, and raw signals here.",
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
