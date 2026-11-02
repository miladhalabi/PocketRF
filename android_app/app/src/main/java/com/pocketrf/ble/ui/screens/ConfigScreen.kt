package com.pocketrf.ble.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pocketrf.ble.ble.ConnState
import com.pocketrf.ble.ble.PocketRfEvent
import com.pocketrf.ble.ble.RfPresets
import com.pocketrf.ble.ble.RfStatus
import com.pocketrf.ble.ui.common.PocketRfTopBar
import com.pocketrf.ble.ui.common.PressableFilledButton
import com.pocketrf.ble.ui.common.StatusSection

@Composable
fun ConfigScreen(
    connState: ConnState,
    status: RfStatus?,
    mtu: Int,
    lastCommandResult: PocketRfEvent.CommandResponse?,
    defaultScanThresholdDbm: Int,
    onSetFrequency: (freqHz: Long) -> Unit,
    onApplyPreset: (name: String) -> Unit,
    onSetDefaultScanThreshold: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val connected = connState == ConnState.READY
    var freqText by remember { mutableStateOf("433.92") }
    var thresholdText by remember(defaultScanThresholdDbm) { mutableStateOf(defaultScanThresholdDbm.toString()) }

    Scaffold(topBar = { PocketRfTopBar("Config", connState, onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            Text("Frequency", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = freqText,
                    onValueChange = { freqText = it },
                    label = { Text("MHz") },
                    singleLine = true,
                    enabled = connected,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                PressableFilledButton(
                    label = "Set",
                    enabled = connected,
                    modifier = Modifier.width(100.dp),
                    onClick = { freqText.toDoubleOrNull()?.let { mhz -> onSetFrequency((mhz * 1_000_000).toLong()) } },
                )
            }

            Spacer(Modifier.height(28.dp))
            Text("Modulation preset", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(4.dp))
            Text(
                "Changes modulation/deviation/RX bandwidth/data rate ahead of a TX or RX. Doesn't change frequency.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            PresetDropdown(enabled = connected, onApply = onApplyPreset)

            Spacer(Modifier.height(28.dp))
            Text("Default Scan RSSI threshold", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(4.dp))
            Text(
                "Pre-fills Scan/copy's threshold field. RSSI calibration varies between CC1101 clones, so tune this to your board (BLE_PROTOCOL.md §4.6).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = thresholdText,
                onValueChange = {
                    thresholdText = it
                    it.toIntOrNull()?.let(onSetDefaultScanThreshold)
                },
                label = { Text("dBm") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            lastCommandResult?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    "${it.cmd ?: "ack"} -> ${it.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.status == "ok") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }

            status?.let {
                Spacer(Modifier.height(24.dp))
                StatusSection(it, warningIcon = Icons.Outlined.WarningAmber, mtu = mtu)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetDropdown(enabled: Boolean, onApply: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(RfPresets.NAMES.first()) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Preset") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RfPresets.NAMES.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { selected = name; expanded = false; onApply(name) })
            }
        }
    }
}
