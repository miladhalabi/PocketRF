package com.pocketrf.ble.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketrf.ble.library.SavedSignal
import com.pocketrf.ble.ui.common.QuickReplayButton
import com.pocketrf.ble.ui.common.SignalCardShape
import com.pocketrf.ble.ui.theme.EventDecoded
import com.pocketrf.ble.ui.theme.EventRaw
import kotlin.math.roundToInt

private val GridColumns = 2
private val CardHeight = 130.dp
private val QuickAccessCardHeight = 92.dp
private val GridSpacing = 10.dp
// How far a long-press can drift and still count as "didn't move" -> unpin, not reorder.
private val TapSlop = 12.dp

@Composable
fun KeysScreen(
    pinnedSignals: List<SavedSignal>, // pre-sorted by pinOrder
    replayEnabled: Boolean,
    onNavigateScan: () -> Unit,
    onNavigateFixedRx: () -> Unit,
    onReplay: (SavedSignal) -> Unit,
    onUnpin: (SavedSignal) -> Unit,
    onLabelClick: (SavedSignal) -> Unit,
    onReorder: (List<Long>) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GridSpacing),
        ) {
            QuickAccessCard("Scan/copy", Icons.Outlined.Radar, Modifier.weight(1f), onNavigateScan)
            QuickAccessCard("Fixed Freq", Icons.Outlined.GraphicEq, Modifier.weight(1f), onNavigateFixedRx)
        }
        Spacer(Modifier.height(16.dp))

        if (pinnedSignals.isEmpty()) {
            KeysEmptyState()
        } else {
            ReorderableKeyGrid(
                signals = pinnedSignals,
                replayEnabled = replayEnabled,
                onReplay = onReplay,
                onUnpin = onUnpin,
                onLabelClick = onLabelClick,
                onReorder = onReorder,
            )
            if (pinnedSignals.size < 4) {
                Spacer(Modifier.height(GridSpacing))
                PinMoreHint()
            }
        }
    }
}

@Composable
private fun PinMoreHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SignalCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.AddCircleOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Pin more signals from the Library to fill this page",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuickAccessCard(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(160), label = "quickAccessPressScale")

    Card(
        shape = SignalCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .height(QuickAccessCardHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun KeysEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Pin a saved signal from the Library to see it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A 2-column, non-lazy grid (pinned-key counts are small — no virtualization
 * needed) that supports long-press drag reorder with haptic feedback, and
 * treats a long-press that never crosses [TapSlop] as "unpin" instead.
 * Reorder math works in grid-cell units (row/col deltas from measured
 * container width + the fixed card height), not exact pixel hit-testing —
 * simpler and good enough for a handful of cards.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReorderableKeyGrid(
    signals: List<SavedSignal>,
    replayEnabled: Boolean,
    onReplay: (SavedSignal) -> Unit,
    onUnpin: (SavedSignal) -> Unit,
    onLabelClick: (SavedSignal) -> Unit,
    onReorder: (List<Long>) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var order by remember(signals.map { it.id }) { mutableStateOf(signals.map { it.id }) }
    val byId = remember(signals) { signals.associateBy { it.id } }

    var draggedId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragDistance by remember { mutableStateOf(0f) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cellWidthPx = with(density) { (maxWidth / GridColumns).toPx() }
        val cellHeightPx = with(density) { (CardHeight + GridSpacing).toPx() }
        val tapSlopPx = with(density) { TapSlop.toPx() }

        Column(verticalArrangement = Arrangement.spacedBy(GridSpacing)) {
            order.chunked(GridColumns).forEachIndexed { rowIndex, rowIds ->
                Row(horizontalArrangement = Arrangement.spacedBy(GridSpacing)) {
                    rowIds.forEachIndexed { colIndex, id ->
                        val signal = byId[id] ?: return@forEachIndexed
                        val index = rowIndex * GridColumns + colIndex
                        val isDragged = draggedId == id

                        KeyCard(
                            signal = signal,
                            replayEnabled = replayEnabled,
                            onReplay = { onReplay(signal) },
                            onLabelClick = { onLabelClick(signal) },
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer {
                                    if (isDragged) {
                                        translationX = dragOffset.x
                                        translationY = dragOffset.y
                                        scaleX = 1.04f
                                        scaleY = 1.04f
                                        shadowElevation = 12f
                                    }
                                }
                                .pointerInput(id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedId = id
                                            dragOffset = Offset.Zero
                                            dragDistance = 0f
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount
                                            dragDistance += amount.getDistance()

                                            val colDelta = (dragOffset.x / cellWidthPx).roundToInt()
                                            val rowDelta = (dragOffset.y / cellHeightPx).roundToInt()
                                            if (colDelta != 0 || rowDelta != 0) {
                                                val from = order.indexOf(id)
                                                val to = (from + rowDelta * GridColumns + colDelta).coerceIn(0, order.lastIndex)
                                                if (to != from) {
                                                    order = order.toMutableList().apply { add(to, removeAt(from)) }
                                                    dragOffset -= Offset(colDelta * cellWidthPx, rowDelta * cellHeightPx)
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            if (dragDistance < tapSlopPx) {
                                                onUnpin(signal)
                                            } else {
                                                onReorder(order)
                                            }
                                            draggedId = null
                                            dragOffset = Offset.Zero
                                        },
                                        onDragCancel = {
                                            draggedId = null
                                            dragOffset = Offset.Zero
                                        },
                                    )
                                },
                        )
                    }
                    // Odd count on the last row: keep the lone card at half width instead of stretching.
                    if (rowIds.size < GridColumns) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun KeyCard(
    signal: SavedSignal,
    replayEnabled: Boolean,
    onReplay: () -> Unit,
    onLabelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (signal.isRaw) EventRaw else EventDecoded
    val icon = if (signal.isRaw) Icons.Outlined.GraphicEq else Icons.Outlined.Key
    Card(
        shape = SignalCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.height(CardHeight),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                signal.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onLabelClick),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (signal.isRaw) "RAW ${signal.rawDurations?.size ?: 0}p" else "${signal.protocol} ${signal.bits}b",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            QuickReplayButton(enabled = replayEnabled, onClick = onReplay, modifier = Modifier.fillMaxWidth())
        }
    }
}
