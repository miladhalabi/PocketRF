package com.pocketrf.ble.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pocketrf.ble.ble.RfStatus

@Composable
fun StatusSection(status: RfStatus, warningIcon: ImageVector, mtu: Int? = null) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            MetricColumn("Frequency", "%.3f MHz".format(status.freqHz / 1_000_000.0), Modifier.weight(1f))
            MetricColumn("RSSI", "${status.rssi} dBm", Modifier.weight(1f))
            MetricColumn("RX", if (status.rxActive) "active" else "idle", Modifier.weight(1f))
            if (mtu != null) MetricColumn("MTU", "$mtu B", Modifier.weight(1f))
        }
        // ~7 bytes budgeted per duration value (BLE_PROTOCOL.md §5.1): a small negotiated
        // MTU caps how many pulses fit in one raw_pulse/scan_raw notification before it's
        // marked truncated and can't be saved.
        if (mtu != null && mtu < 512) {
            Spacer(Modifier.height(10.dp))
            Text(
                "MTU negotiated at $mtu, not the full 512 — raw captures over ~${((mtu - 3 - 80) / 7).coerceAtLeast(0)} pulses will show as truncated and can't be saved.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!status.looksGenuine) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    warningIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.width(16.dp).height(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "CC1101 not detected — check wiring",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    }
}

@Composable
private fun MetricColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
