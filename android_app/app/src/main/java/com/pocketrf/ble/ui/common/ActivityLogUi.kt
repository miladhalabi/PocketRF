package com.pocketrf.ble.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pocketrf.ble.ble.PocketRfEvent
import com.pocketrf.ble.ui.theme.EventDecoded
import com.pocketrf.ble.ui.theme.EventRaw
import com.pocketrf.ble.ui.theme.EventSpectrum
import com.pocketrf.ble.ui.theme.EventSystem

/**
 * Discrete-event log list shared by every RX-ish screen (Scan, Fixed-Freq
 * RX), newest first. An actual captured signal (decoded or complete raw)
 * renders as a card with a Replay button — fire it straight from here
 * without saving — plus a Save bookmark; everything else (acks, RSSI
 * ticks, lock notices) stays a plain compact row.
 */
@Composable
fun ActivityLogList(
    log: List<PocketRfEvent>,
    connected: Boolean,
    emptyHint: String,
    isSaveable: (PocketRfEvent) -> Boolean = { false },
    onSaveClick: (PocketRfEvent) -> Unit = {},
    onReplayClick: (PocketRfEvent) -> Unit = {},
) {
    if (log.isEmpty()) {
        EmptyLogState(connected = connected, hint = emptyHint)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(log.asReversed()) { event ->
                if (isSignal(event)) {
                    SignalLogCard(
                        event = event,
                        replayEnabled = connected,
                        saveable = isSaveable(event),
                        onSave = { onSaveClick(event) },
                        onReplay = { onReplayClick(event) },
                    )
                } else {
                    PlainLogRow(event)
                }
            }
        }
    }
}

private fun isSignal(event: PocketRfEvent): Boolean =
    event is PocketRfEvent.DecodedRf || event is PocketRfEvent.RawPulse

@Composable
private fun EmptyLogState(connected: Boolean, hint: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (connected) Icons.Outlined.GraphicEq else Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (connected) hint else "Connect to PocketRF to begin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SignalLogCard(
    event: PocketRfEvent,
    replayEnabled: Boolean,
    saveable: Boolean,
    onSave: () -> Unit,
    onReplay: () -> Unit,
) {
    val (accent, scanLocked, description) = when (event) {
        is PocketRfEvent.DecodedRf -> Triple(
            EventDecoded,
            event.scanLocked,
            "${event.protocol} ${event.bits}b  key=${event.keyHex}  ${event.rssi ?: "?"} dBm",
        )
        is PocketRfEvent.RawPulse -> Triple(
            EventRaw,
            event.scanLocked,
            "raw ${event.count} pulses" + (if (event.truncated) " (truncated)" else "") + "  ${event.rssi ?: "?"} dBm",
        )
        else -> return // unreachable: isSignal() already filtered to the two cases above
    }
    // A truncated raw capture has no complete duration list to send (same reason it can't be saved).
    val replayable = saveable

    Card(
        shape = SignalCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    (if (scanLocked) "scan · " else "") + description,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                QuickReplayButton(
                    enabled = replayEnabled && replayable,
                    onClick = onReplay,
                    modifier = Modifier.weight(1f),
                )
                if (saveable) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onSave, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.BookmarkAdd, contentDescription = "Save with a name", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

private data class LogRowStyle(val color: Color, val text: String)

/** Non-signal log lines (acks, RSSI ticks, lock/done notices) — compact, no card, no actions. */
@Composable
private fun PlainLogRow(event: PocketRfEvent) {
    val style = when (event) {
        is PocketRfEvent.ListenPulse -> LogRowStyle(EventRaw, "listen  freq=${event.detectedFreqHz ?: "?"} Hz  ${event.rssi ?: "?"} dBm")
        is PocketRfEvent.SpectrumFixed -> LogRowStyle(EventSpectrum, "rssi @ ${event.freq ?: "?"}  ${event.rssi ?: "?"} dBm")
        is PocketRfEvent.SpectrumSweep -> LogRowStyle(EventSpectrum, "sweep ${event.startFreq}-${event.endFreq}  ${event.rssi.size} pts")
        PocketRfEvent.JamDone -> LogRowStyle(EventSystem, "jam finished (30s cutoff)")
        PocketRfEvent.BruteforceDone -> LogRowStyle(EventSystem, "bruteforce exhausted")
        is PocketRfEvent.ScanLocked -> LogRowStyle(EventSpectrum, "scan locked @ ${event.freq}")
        is PocketRfEvent.CommandResponse -> LogRowStyle(EventSystem, "${event.cmd ?: "ack"} -> ${event.status}")
        is PocketRfEvent.Unknown -> LogRowStyle(EventSystem, event.raw)
        else -> return // DecodedRf/RawPulse never reach here, see isSignal()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = style.color,
            modifier = Modifier.size(8.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            style.text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
