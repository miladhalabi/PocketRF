package com.pocketrf.ble.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pocketrf.ble.ble.ConnState
import com.pocketrf.ble.ble.PocketRfEvent
import com.pocketrf.ble.ble.RfProtocols
import com.pocketrf.ble.ui.RfMode
import com.pocketrf.ble.ui.common.PocketRfTopBar
import com.pocketrf.ble.ui.common.PressableFilledButton

@Composable
fun BruteforceScreen(
    connState: ConnState,
    mode: RfMode,
    lastCommandResult: PocketRfEvent.CommandResponse?,
    bruteforceDone: Boolean,
    onClearLive: () -> Unit,
    onStart: (protocol: String, bits: Int?, freqMhz: Double?, repeat: Int?) -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    val connected = connState == ConnState.READY
    val running = mode == RfMode.BRUTEFORCE
    var protocol by remember { mutableStateOf(RfProtocols.FIXED_CODE.first().first) }
    var bitsText by remember { mutableStateOf(RfProtocols.defaultBitsFor(protocol)?.toString() ?: "") }
    var freqText by remember { mutableStateOf("433.92") }
    var repeatText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { onClearLive() }

    Scaffold(topBar = { PocketRfTopBar("Bruteforce", connState, onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Enumerates a fixed-code protocol's full keyspace. KeeLoq isn't listed: its 528-round cipher has no brute-forceable fixed code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            FixedCodeProtocolDropdown(
                selected = protocol,
                onSelect = {
                    protocol = it
                    bitsText = RfProtocols.defaultBitsFor(it)?.toString() ?: ""
                },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = bitsText,
                    onValueChange = { bitsText = it },
                    label = { Text("Bits (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = freqText,
                    onValueChange = { freqText = it },
                    label = { Text("Freq MHz (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = repeatText,
                onValueChange = { repeatText = it },
                label = { Text("Repeat per code (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            PressableFilledButton(
                label = if (running) "Stop" else "Start bruteforce",
                icon = if (running) Icons.Outlined.Stop else Icons.Outlined.VpnKey,
                enabled = connected,
                onClick = {
                    if (running) {
                        onStop()
                    } else {
                        onStart(protocol, bitsText.toIntOrNull(), freqText.toDoubleOrNull(), repeatText.toIntOrNull())
                    }
                },
            )

            if (running) {
                Spacer(Modifier.height(16.dp))
                Text("Running — this walks the full keyspace and can take a while.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (bruteforceDone) {
                Spacer(Modifier.height(10.dp))
                Text("Keyspace exhausted.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            lastCommandResult?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    "${it.cmd ?: "ack"} -> ${it.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.status == "ok") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FixedCodeProtocolDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Protocol") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RfProtocols.FIXED_CODE.forEach { (name, _) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(name); expanded = false })
            }
        }
    }
}
