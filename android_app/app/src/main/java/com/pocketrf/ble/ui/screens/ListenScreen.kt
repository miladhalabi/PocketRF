package com.pocketrf.ble.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketrf.ble.ListenLivePanel
import com.pocketrf.ble.ble.ConnState
import com.pocketrf.ble.ble.PocketRfEvent
import com.pocketrf.ble.ui.RfMode
import com.pocketrf.ble.ui.common.PocketRfTopBar
import com.pocketrf.ble.ui.common.PressableFilledButton

@Composable
fun ListenScreen(
    connState: ConnState,
    mode: RfMode,
    listenLive: PocketRfEvent.ListenPulse?,
    onClearLive: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    val connected = connState == ConnState.READY
    val running = mode == RfMode.LISTEN

    LaunchedEffect(Unit) { onClearLive() }

    Scaffold(topBar = { PocketRfTopBar("Listen", connState, onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Live frequency/pulse-width counter — throttled to ≤5 readings/sec.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            PressableFilledButton(
                label = if (running) "Stop" else "Start listening",
                icon = if (running) Icons.Outlined.Stop else Icons.Outlined.Headphones,
                enabled = connected,
                onClick = { if (running) onStop() else onStart() },
            )
            Spacer(Modifier.height(24.dp))
            if (running) ListenLivePanel(listenLive)
        }
    }
}
