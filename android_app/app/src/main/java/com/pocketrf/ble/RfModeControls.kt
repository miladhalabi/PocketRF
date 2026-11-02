package com.pocketrf.ble

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pocketrf.ble.ble.PocketRfEvent
import com.pocketrf.ble.ui.theme.EventSpectrum

/** Typical CC1101 RSSI range for the gauge/chart scale; readings are clamped into it. */
private const val RSSI_FLOOR = -110
private const val RSSI_CEIL = 0

/**
 * Scan's default -65dBm lock threshold rarely works on real hardware (BLE_PROTOCOL.md §4.6:
 * RSSI calibration varies a lot between CC1101 clones/antennas — the firmware's own test board
 * needed -80 before Scan ever locked onto anything). Exposed here instead of hardcoding a guess.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanThresholdControl(thresholdText: String, onThresholdTextChange: (String) -> Unit, enabled: Boolean) {
    Column {
        OutlinedTextField(
            value = thresholdText,
            onValueChange = onThresholdTextChange,
            label = { Text("Scan RSSI threshold (dBm)") },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "No lock? Try a looser (more negative) value. Watch Spectrum idle to find your board's noise floor first.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrequencyControl(freqText: String, onFreqTextChange: (String) -> Unit, enabled: Boolean, onSetFrequency: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = freqText,
            onValueChange = onFreqTextChange,
            label = { Text("Frequency (MHz)") },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        if (onSetFrequency != null) {
            Spacer(Modifier.width(10.dp))
            Button(onClick = onSetFrequency, enabled = enabled) {
                Text("Set")
            }
        }
    }
}

@Composable
fun ListenLivePanel(pulse: PocketRfEvent.ListenPulse?) {
    Text(
        "freq ${pulse?.detectedFreqHz ?: "-"} Hz   pulse ${pulse?.pulseDurUs ?: "-"} µs   ${pulse?.rssi ?: "-"} dBm",
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
fun ScanLivePanel(lockedFreq: Double?) {
    val transition = rememberInfiniteTransition(label = "scanPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scanPulseAlpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(if (lockedFreq == null) alpha else 1f)
                .clip(CircleShape)
                .background(if (lockedFreq != null) MaterialTheme.colorScheme.primary else EventSpectrum),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            if (lockedFreq != null) "Locked @ $lockedFreq MHz" else "Scanning 300-928 MHz...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** A quick 150ms ease-out smoothing of a noisy live RSSI reading, not decoration: it keeps the gauge readable. */
@Composable
fun SpectrumGauge(rssi: Int?) {
    val fraction = ((rssi ?: RSSI_FLOOR).coerceIn(RSSI_FLOOR, RSSI_CEIL) - RSSI_FLOOR) / (RSSI_CEIL - RSSI_FLOOR).toFloat()
    val animatedFraction by animateFloatAsState(fraction, tween(150, easing = FastOutSlowInEasing), label = "rssiGauge")

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("RSSI", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (rssi != null) "$rssi dBm" else "-",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction.coerceIn(0f, 1f))
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
fun SweepChart(rssiValues: List<Int>) {
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Column {
        Text(
            if (rssiValues.isEmpty()) "Waiting for first sweep pass..." else "${rssiValues.size} steps, last pass",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(top = 4.dp),
        ) {
            // baseline
            drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            if (rssiValues.isEmpty()) return@Canvas
            val barWidth = size.width / rssiValues.size
            rssiValues.forEachIndexed { i, rssi ->
                val fraction = (rssi.coerceIn(RSSI_FLOOR, RSSI_CEIL) - RSSI_FLOOR) / (RSSI_CEIL - RSSI_FLOOR).toFloat()
                val barHeight = size.height * fraction
                drawRect(
                    color = barColor,
                    topLeft = Offset(i * barWidth, size.height - barHeight),
                    size = Size(barWidth * 0.8f, barHeight),
                )
            }
        }
    }
}
