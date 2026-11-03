package com.pocketrf.ble.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketrf.ble.library.SavedSignal
import com.pocketrf.ble.ui.common.SignalCardShape
import com.pocketrf.ble.ui.theme.EventDecoded
import com.pocketrf.ble.ui.theme.EventRaw
import kotlin.math.roundToInt

private val GridColumns = 2
// A compact, single-row card (icon + name) — must still fit the unpin "x" in
// the corner. A Card's fixed height clips overflow silently if this is ever
// too short for its content.
private val CardHeight = 78.dp
private val QuickAccessCardHeight = 92.dp
private val GridSpacing = 10.dp
// How far a long-press-then-drag must travel before it counts as a reorder
// rather than a cancelled long-press (which does nothing — a plain tap
// already replays, see KeyCard).
private val TapSlop = 12.dp

@Composable
fun KeysScreen(
    pinnedSignals: List<SavedSignal>, // pre-sorted by pinOrder
    replayEnabled: Boolean,
    onNavigateScan: () -> Unit,
    onNavigateFixedRx: () -> Unit,
    onReplay: (SavedSignal) -> Unit,
    onUnpin: (SavedSignal) -> Unit,
    onReorder: (List<Long>) -> Unit,
) {
    // "Jiggle mode", like the iPhone home screen: a drag on any pinned card
    // enters it (every card starts wiggling and shows its unpin "x"); it
    // persists across drags until Done is tapped or the page's empty space is.
    var editMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .let {
                if (editMode) {
                    it.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { editMode = false },
                    )
                } else {
                    it
                }
            }
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
            AnimatedVisibility(visible = editMode) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        "Done",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { editMode = false },
                            )
                            .padding(bottom = 10.dp),
                    )
                }
            }
            ReorderableKeyGrid(
                signals = pinnedSignals,
                replayEnabled = replayEnabled,
                editMode = editMode,
                onEnterEditMode = { editMode = true },
                onReplay = onReplay,
                onUnpin = onUnpin,
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

// How far a drag must travel before it flips jiggle mode on — deliberately
// smaller than TapSlop, so jiggle starts the moment real movement begins
// rather than waiting for a full reorder-committing drag. Keeping this
// decoupled from the clickable's own tap-vs-drag slop check is what avoids a
// long-press-then-release-without-moving from *also* firing a replay: if the
// finger never moves this far, editMode never flips and the plain `clickable`
// on KeyCard is the only thing that reacts, exactly like an ordinary tap.
private val JiggleStartSlop = 4.dp

/**
 * A 2-column, non-lazy grid (pinned-key counts are small — no virtualization
 * needed) that supports long-press drag reorder with haptic feedback.
 * Reorder math works in grid-cell units (row/col deltas from measured
 * container width + the fixed card height), not exact pixel hit-testing —
 * simpler and good enough for a handful of cards.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReorderableKeyGrid(
    signals: List<SavedSignal>,
    replayEnabled: Boolean,
    editMode: Boolean,
    onEnterEditMode: () -> Unit,
    onReplay: (SavedSignal) -> Unit,
    onUnpin: (SavedSignal) -> Unit,
    onReorder: (List<Long>) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var order by remember(signals.map { it.id }) { mutableStateOf(signals.map { it.id }) }
    val byId = remember(signals) { signals.associateBy { it.id } }

    var draggedId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragDistance by remember { mutableStateOf(0f) }

    val jiggleAngle: Float = if (editMode) {
        val infiniteTransition = rememberInfiniteTransition(label = "jiggle")
        val angle by infiniteTransition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(130, easing = LinearEasing), RepeatMode.Reverse),
            label = "jiggleAngle",
        )
        angle
    } else {
        0f
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cellWidthPx = with(density) { (maxWidth / GridColumns).toPx() }
        val cellHeightPx = with(density) { (CardHeight + GridSpacing).toPx() }
        val tapSlopPx = with(density) { TapSlop.toPx() }
        val jiggleStartSlopPx = with(density) { JiggleStartSlop.toPx() }

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
                            onUnpin = { onUnpin(signal) },
                            showUnpin = editMode,
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer {
                                    if (isDragged) {
                                        translationX = dragOffset.x
                                        translationY = dragOffset.y
                                        scaleX = 1.04f
                                        scaleY = 1.04f
                                        shadowElevation = 12f
                                    } else if (editMode) {
                                        rotationZ = jiggleAngle * (if (index % 2 == 0) 1f else -1f)
                                    }
                                }
                                .pointerInput(id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedId = id
                                            dragOffset = Offset.Zero
                                            dragDistance = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount
                                            dragDistance += amount.getDistance()

                                            if (!editMode && dragDistance >= jiggleStartSlopPx) {
                                                onEnterEditMode()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }

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
                                            if (dragDistance >= tapSlopPx) {
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

/**
 * A plain, flat list-style card — icon and name in one row, native ripple on
 * tap. Tapping anywhere on the card replays it; there's no separate button.
 * The unpin "x" only appears in jiggle/edit mode (see [ReorderableKeyGrid]),
 * fading and scaling in like the iPhone home screen's delete badges.
 */
@Composable
private fun KeyCard(
    signal: SavedSignal,
    replayEnabled: Boolean,
    onReplay: () -> Unit,
    onUnpin: () -> Unit,
    showUnpin: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = if (signal.isRaw) EventRaw else EventDecoded
    val icon = if (signal.isRaw) Icons.Outlined.GraphicEq else Icons.Outlined.Key

    Box(
        modifier = modifier
            .height(CardHeight)
            .clip(SignalCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = replayEnabled, onClick = onReplay),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 14.dp, end = 36.dp)
                .alpha(if (replayEnabled) 1f else 0.5f),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                signal.label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AnimatedVisibility(
            visible = showUnpin,
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.6f, animationSpec = tween(160)),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.6f, animationSpec = tween(120)),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            IconButton(onClick = onUnpin, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Unpin from Keys",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
