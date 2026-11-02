package com.pocketrf.ble

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pocketrf.ble.library.SavedSignal
import com.pocketrf.ble.ui.common.QuickReplayButton
import com.pocketrf.ble.ui.common.RenameDialog
import com.pocketrf.ble.ui.common.SignalCardShape
import com.pocketrf.ble.ui.theme.EventDecoded
import com.pocketrf.ble.ui.theme.EventRaw

@Composable
fun LibraryScreen(
    signals: List<SavedSignal>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    connected: Boolean,
    onReplay: (SavedSignal) -> Unit,
    onToggleFavorite: (SavedSignal) -> Unit,
    onRename: (SavedSignal, String) -> Unit,
    onDelete: (SavedSignal) -> Unit,
) {
    val filtered = remember(signals, searchQuery) {
        if (searchQuery.isBlank()) {
            signals
        } else {
            signals.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                    it.protocol.contains(searchQuery, ignoreCase = true) ||
                    (it.mfName?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }
    var renameTarget by remember { mutableStateOf<SavedSignal?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text("Search label, protocol, manufacturer") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            LibraryEmptyState(hasAnySignals = signals.isNotEmpty())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(filtered, key = { it.id }) { signal ->
                    SignalCard(
                        signal = signal,
                        replayEnabled = connected,
                        onReplay = { onReplay(signal) },
                        onToggleFavorite = { onToggleFavorite(signal) },
                        onLabelClick = { renameTarget = signal },
                        onDelete = { onDelete(signal) },
                    )
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            initialLabel = target.label,
            onConfirm = { newLabel ->
                onRename(target, newLabel)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
}

@Composable
private fun SignalCard(
    signal: SavedSignal,
    replayEnabled: Boolean,
    onReplay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLabelClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = if (signal.isRaw) EventRaw else EventDecoded

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
                    signal.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f).clickable(onClick = onLabelClick),
                )
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (signal.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (signal.favorite) "Unpin from Keys" else "Pin to Keys",
                        tint = if (signal.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (signal.isRaw) {
                    "RAW  ${signal.rawDurations?.size ?: 0} pulses" + (signal.freqMhz?.let { "  $it MHz" } ?: "")
                } else {
                    "${signal.protocol} ${signal.bits}b  key=${signal.keyHex}" +
                        (signal.freqMhz?.let { "  $it MHz" } ?: "")
                },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                QuickReplayButton(
                    enabled = replayEnabled,
                    onClick = onReplay,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun LibraryEmptyState(hasAnySignals: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (hasAnySignals) "No signals match your search." else "Nothing saved yet. Tap the bookmark on a captured signal to name and save it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
