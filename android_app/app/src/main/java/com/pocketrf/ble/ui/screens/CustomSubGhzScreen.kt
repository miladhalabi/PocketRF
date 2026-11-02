package com.pocketrf.ble.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pocketrf.ble.ble.ConnState
import com.pocketrf.ble.ble.PocketRfEvent
import com.pocketrf.ble.ble.RfProtocols
import com.pocketrf.ble.ui.common.PocketRfTopBar
import com.pocketrf.ble.ui.common.PressableFilledButton
import com.pocketrf.ble.ui.common.PressableOutlinedButton
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun CustomSubGhzScreen(
    connState: ConnState,
    lastCommandResult: PocketRfEvent.CommandResponse?,
    onTransmit: (protocol: String, freqMhz: Double, bits: Int, keyHex: String) -> Unit,
    onReplaySubFile: (contents: String) -> Unit,
    onBack: () -> Unit,
) {
    val connected = connState == ConnState.READY
    val context = LocalContext.current

    var protocol by remember { mutableStateOf(RfProtocols.FIXED_CODE.first().first) }
    var freqText by remember { mutableStateOf("433.92") }
    var bitsText by remember { mutableStateOf(RfProtocols.defaultBitsFor(protocol)?.toString() ?: "") }
    var keyText by remember { mutableStateOf("") }
    var subImportStatus by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val contents = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            }
        }.getOrNull()
        if (contents != null) {
            onReplaySubFile(contents)
            subImportStatus = "Sent ${contents.lineCount()} lines from .sub file"
        } else {
            subImportStatus = "Couldn't read that file"
        }
    }

    Scaffold(topBar = { PocketRfTopBar("Custom SubGhz", connState, onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Pick a protocol, enter a key, and transmit it. KeeLoq sends the raw 64-bit frame (10 repeats); everything else goes through the named-protocol TX command.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            ProtocolDropdown(
                selected = protocol,
                onSelect = {
                    protocol = it
                    bitsText = RfProtocols.defaultBitsFor(it)?.toString() ?: ""
                },
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = freqText,
                    onValueChange = { freqText = it },
                    label = { Text("Freq (MHz)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = bitsText,
                    onValueChange = { bitsText = it },
                    label = { Text("Bits") },
                    singleLine = true,
                    enabled = !protocol.equals(RfProtocols.KEELOQ, ignoreCase = true),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = keyText,
                onValueChange = { keyText = it },
                label = { Text("Key (hex)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            PressableFilledButton(
                label = "Send",
                icon = Icons.AutoMirrored.Outlined.Send,
                enabled = connected && keyText.isNotBlank() && (protocol.equals(RfProtocols.KEELOQ, true) || bitsText.toIntOrNull() != null) && freqText.toDoubleOrNull() != null,
                onClick = {
                    val freq = freqText.toDoubleOrNull() ?: return@PressableFilledButton
                    val bits = bitsText.toIntOrNull() ?: 0
                    onTransmit(protocol, freq, bits, keyText.trim())
                },
            )

            lastCommandResult?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    "${it.cmd ?: "ack"} -> ${it.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.status == "ok") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(28.dp))
            Text("Replay a .sub file", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(6.dp))
            Text(
                "Flipper-style RAW .sub file, sent whole via the device's special RAWSUB path. Large files may exceed one BLE write (MTU-dependent) and fail to send in one shot.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            PressableOutlinedButton(
                label = "Import .sub file",
                icon = Icons.Outlined.FileUpload,
                enabled = connected,
                modifier = Modifier.fillMaxWidth(),
                onClick = { filePicker.launch(arrayOf("*/*")) },
            )
            subImportStatus?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun String.lineCount() = this.count { it == '\n' } + 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtocolDropdown(selected: String, onSelect: (String) -> Unit) {
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
            RfProtocols.ALL_NAMES.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(name); expanded = false })
            }
        }
    }
}
