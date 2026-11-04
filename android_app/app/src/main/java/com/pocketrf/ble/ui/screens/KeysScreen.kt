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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.pocketrf.ble.library.SavedSignal
import com.pocketrf.ble.ui.common.SignalCardShape
import com.pocketrf.ble.ui.theme.EventDecoded
import com.pocketrf.ble.ui.theme.EventRaw
import kotlin.math.roundToInt

private val GridColumns = 2
// A compact, single-row card (icon + name) — must still fit the unpin "x" in
// the corner. A Card's fixed height clips overflow silently if this is ever
// too short for its content.
// Minimum height; cards grow (all together, see ReorderableKeyGrid) when a
// label wraps past what fits in this.
private val CardHeight = 78.dp
// KeyCard's horizontal layout, shared with the grid so it can measure each
// label at exactly the width the card will give it.
private val CardPaddingStart = 14.dp
private val CardPaddingEnd = 28.dp // keeps clear of the edit-mode unpin "x"
private val CardPaddingVertical = 14.dp
private val CardIconSize = 38.dp
private val CardIconGap = 12.dp
private const val CardLabelMaxLines = 3
private val QuickAccessCardHeight = 92.dp
private val GridSpacing = 10.dp

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

    val scrollState = rememberScrollState()
    // The grid's own gesture needs to know the scrollable viewport's bounds in
    // root/window coordinates to decide "is the finger near the top/bottom
    // edge" and to drive auto-scroll while dragging near them.
    var viewportTopInRoot by remember { mutableStateOf(0f) }
    var viewportHeightPx by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .onGloballyPositioned { coordinates ->
                viewportTopInRoot = coordinates.positionInRoot().y
                viewportHeightPx = coordinates.size.height.toFloat()
            }
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
                scrollState = scrollState,
                viewportTopInRoot = viewportTopInRoot,
                viewportHeightPx = viewportHeightPx,
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

/**
 * A 2-column grid, manually laid out: every card is absolute-positioned
 * inside one `Box` via `Modifier.offset`, animated toward its slot with
 * `animateFloatAsState` whenever `order` changes. (An earlier version used
 * `LazyVerticalGrid` for its free `animateItem()` position animation, but
 * fast drags that triggered several swaps in quick succession could retarget
 * its internal animations faster than they settled, leaving multiple cards
 * visually stacked mid-flight. Driving the animation ourselves avoids that.)
 *
 * A long-press alone (no movement required) enters jiggle/edit mode,
 * matching the iPhone home screen; dragging while in that mode reorders.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReorderableKeyGrid(
    signals: List<SavedSignal>,
    replayEnabled: Boolean,
    editMode: Boolean,
    onEnterEditMode: () -> Unit,
    scrollState: ScrollState,
    viewportTopInRoot: Float,
    viewportHeightPx: Float,
    onReplay: (SavedSignal) -> Unit,
    onUnpin: (SavedSignal) -> Unit,
    onReorder: (List<Long>) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    // One MutableState for the grid's whole lifetime. Keying the remember on
    // the ids would swap in a *new* state object after every persisted
    // reorder, while the long-lived pointerInput/LaunchedEffect lambdas below
    // keep writing the old one — so every drag after the first would operate
    // on a list nothing renders. Instead, sync it from `signals` when they change.
    val signalIds = signals.map { it.id }
    var order by remember { mutableStateOf(signalIds) }
    val byId = remember(signals) { signals.associateBy { it.id } }

    var draggedId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(signalIds) {
        if (draggedId == null) order = signalIds
    }
    // Total finger travel since drag start (incl. auto-scroll), and the dragged
    // card's original slot origin; card position = dragStart + dragOffset.
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    // Position of this grid in root/window coordinates — needed to compare the
    // drag's touch position against the scrollable viewport's edges below.
    var gridTopInRoot by remember { mutableStateOf(0f) }
    // Signed px/frame; 0 means not auto-scrolling. Set from the drag's touch
    // position, consumed by the LaunchedEffect loop further down.
    var autoScrollSpeed by remember { mutableStateOf(0f) }

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

    val rows = (order.size + GridColumns - 1) / GridColumns
    val labelStyle = keyLabelStyle()
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth = (maxWidth - GridSpacing * (GridColumns - 1)) / GridColumns
        // Every card shares one height: tall enough for the longest label.
        // Per-row heights would move row boundaries whenever a drag swaps a
        // long-named card between rows, which can flip the swap straight back.
        val cardHeight = remember(signals, cardWidth, labelStyle, density) {
            val labelWidthPx = with(density) {
                (cardWidth - CardPaddingStart - CardIconSize - CardIconGap - CardPaddingEnd).roundToPx()
            }.coerceAtLeast(1)
            val tallestLabelPx = signals.maxOfOrNull {
                textMeasurer.measure(
                    it.label,
                    style = labelStyle,
                    maxLines = CardLabelMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = labelWidthPx),
                ).size.height
            } ?: 0
            maxOf(CardHeight, with(density) { tallestLabelPx.toDp() } + CardPaddingVertical * 2)
        }
        val gridHeight = cardHeight * rows + GridSpacing * (rows - 1).coerceAtLeast(0)
        val cellWidthPx = with(density) { (cardWidth + GridSpacing).toPx() }
        val cellHeightPx = with(density) { (cardHeight + GridSpacing).toPx() }
        val edgeZonePx = with(density) { 72.dp.toPx() }
        val maxAutoScrollPx = with(density) { 16.dp.toPx() }
        // The pointerInput / LaunchedEffect lambdas below live for the grid's
        // whole lifetime, so anything that can change after first composition
        // must be read through State, not captured by value.
        val cellW = rememberUpdatedState(cellWidthPx)
        val cellH = rememberUpdatedState(cellHeightPx)
        val viewportTop = rememberUpdatedState(viewportTopInRoot)
        val viewportHeight = rememberUpdatedState(viewportHeightPx)
        val currentOnEnterEditMode = rememberUpdatedState(onEnterEditMode)
        val currentOnReorder = rememberUpdatedState(onReorder)

        // Moves the dragged card to wherever the finger (plus however far
        // auto-scroll has shifted the page under it) now puts it — shared by
        // the direct-touch path and the auto-scroll loop below, since both
        // change dragOffset and need the same "did we cross into a new cell"
        // check.
        //
        // A diagonal drag changes row AND column at once. Combining both into
        // one list-index jump (`from + rowDelta*GridColumns + colDelta`) looks
        // right but isn't: that formula doesn't always land on the card's
        // real 2D neighbor, so the very next check could "correct" it again,
        // and again — an oscillation that looked like several cards stuck
        // mid-flight in a pile rather than settling. Decomposing into single
        // row-or-column steps (however many are needed, one at a time) is
        // always well-defined and ripples every card in between correctly.
        fun maybeSwap(id: Long) {
            val cw = cellW.value
            val ch = cellH.value
            val from = order.indexOf(id)
            if (from < 0 || cw <= 0f || ch <= 0f) return
            // Stateless: the cell under the dragged card's centre IS the target
            // index. The drag position is never "consumed", so there is no
            // bookkeeping to drift out of sync with the layout.
            val cx = dragStart.x + dragOffset.x + cw / 2
            val cy = dragStart.y + dragOffset.y + ch / 2
            val lastRow = (order.lastIndex / GridColumns)
            val col = (cx / cw).toInt().coerceIn(0, GridColumns - 1)
            val row = (cy / ch).toInt().coerceIn(0, lastRow)
            val to = (row * GridColumns + col).coerceIn(0, order.lastIndex)
            if (to == from) return
            order = order.toMutableList().apply { add(to, removeAt(from)) }
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }

        // Runs for the grid's whole lifetime but only does anything while
        // autoScrollSpeed is non-zero (set by the drag gesture below) — keeps
        // scrolling smoothly frame-by-frame even if the finger holds still
        // near an edge instead of only reacting to discrete move events.
        LaunchedEffect(scrollState) {
            while (true) {
                withFrameMillis { }
                val id = draggedId
                if (autoScrollSpeed != 0f && id != null) {
                    val consumed = scrollState.scrollBy(autoScrollSpeed)
                    if (consumed != 0f) {
                        dragOffset += Offset(0f, consumed)
                        maybeSwap(id)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight)
                .onGloballyPositioned { gridTopInRoot = it.positionInRoot().y }
                // The drag gesture lives here, on the grid container, instead of on
                // each card. A card that hosts the pointerInput tracking its OWN
                // drag, while also being moved by that same drag via offset/
                // graphicsLayer, gets its own touch coordinates re-resolved against
                // its new (shifted) position on every event — the card ends up
                // chasing a moving target and drifts off the finger. The grid
                // container never moves, so the gesture's coordinate frame stays
                // stable for the whole drag; which card started it is worked out
                // from the long-press's start position using the known cell grid.
                // Keyed by Unit, not `order` — `order` mutates on every swap *during*
                // this same gesture, and a pointerInput's key changing restarts (and
                // so cancels) it. Keying on the swapping state meant the drag died
                // the instant the first swap happened. Reading `order` inside the
                // lambdas below already sees each swap's latest value regardless.
                .pointerInput(Unit) {
                    // `detectDragGesturesAfterLongPress` returns once a single gesture
                    // (down-through-up) finishes; without this loop the second drag of
                    // the session would land on an already-finished coroutine that's no
                    // longer listening — tracking and swapping would both silently stop.
                    while (true) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { startOffset ->
                                val cw = cellW.value
                                val ch = cellH.value
                                val col = (startOffset.x / cw).toInt().coerceIn(0, GridColumns - 1)
                                val row = (startOffset.y / ch).toInt()
                                val index = (row * GridColumns + col).coerceIn(0, order.lastIndex)
                                draggedId = order[index]
                                dragStart = Offset((index % GridColumns) * cw, (index / GridColumns) * ch)
                                dragOffset = Offset.Zero
                                currentOnEnterEditMode.value()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, amount ->
                                val id = draggedId ?: return@detectDragGesturesAfterLongPress
                                change.consume()
                                dragOffset += amount
                                maybeSwap(id)

                                // Edge proximity is measured in window coordinates so it
                                // lines up with the *visible* scrollable viewport, not
                                // this grid's own (possibly off-screen-above) bounds.
                                val touchYInWindow = gridTopInRoot + change.position.y
                                val relativeY = touchYInWindow - viewportTop.value
                                val vh = viewportHeight.value
                                autoScrollSpeed = when {
                                    vh <= 0f -> 0f
                                    relativeY < edgeZonePx ->
                                        -maxAutoScrollPx * (((edgeZonePx - relativeY) / edgeZonePx).coerceIn(0f, 1f))
                                    relativeY > vh - edgeZonePx ->
                                        maxAutoScrollPx * (((relativeY - (vh - edgeZonePx)) / edgeZonePx).coerceIn(0f, 1f))
                                    else -> 0f
                                }
                            },
                            onDragEnd = {
                                currentOnReorder.value(order)
                                draggedId = null
                                dragOffset = Offset.Zero
                                autoScrollSpeed = 0f
                            },
                            onDragCancel = {
                                draggedId = null
                                dragOffset = Offset.Zero
                                autoScrollSpeed = 0f
                            },
                        )
                    }
                },
        ) {
            order.forEachIndexed { index, id ->
                val signal = byId[id] ?: return@forEachIndexed
                val isDragged = draggedId == id
                val targetX = (index % GridColumns) * cellWidthPx
                val targetY = (index / GridColumns) * cellHeightPx

                // Keyed by id so each card's animation state follows the card
                // itself across reorders, not whatever index happens to land there.
                key(id) {
                    val animatedX by animateFloatAsState(targetX, label = "cardX")
                    val animatedY by animateFloatAsState(targetY, label = "cardY")

                    KeyCard(
                        signal = signal,
                        replayEnabled = replayEnabled,
                        onReplay = { onReplay(signal) },
                        onUnpin = { onUnpin(signal) },
                        showUnpin = editMode,
                        // Edit mode is driven purely by long-press, never by a tap, so
                        // tap-to-replay only needs to stay out of the way while
                        // jiggling (no shared state/race with the drag gesture above).
                        tapEnabled = replayEnabled && !editMode,
                        modifier = Modifier
                            .width(cardWidth)
                            .height(cardHeight)
                            .zIndex(if (isDragged) 1f else 0f)
                            // The dragged card tracks the finger 1:1 (its own slot
                            // target plus the raw drag delta, no animation lag);
                            // every other card glides toward its new slot instead.
                            .offset {
                                val x = if (isDragged) dragStart.x + dragOffset.x else animatedX
                                val y = if (isDragged) dragStart.y + dragOffset.y else animatedY
                                IntOffset(x.roundToInt(), y.roundToInt())
                            }
                            .graphicsLayer {
                                if (isDragged) {
                                    scaleX = 1.08f
                                    scaleY = 1.08f
                                    shadowElevation = 18f
                                } else if (editMode) {
                                    rotationZ = jiggleAngle * (if (index % 2 == 0) 1f else -1f)
                                }
                            },
                    )
                }
            }
        }
    }
}

// The grid measures labels with this same style to size the cards.
@Composable
private fun keyLabelStyle(): TextStyle =
    MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 22.sp)

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
    tapEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = if (signal.isRaw) EventRaw else EventDecoded
    val icon = if (signal.isRaw) Icons.Outlined.GraphicEq else Icons.Outlined.Key

    Box(
        modifier = modifier
            .clip(SignalCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = tapEnabled, onClick = onReplay),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = CardPaddingStart, end = CardPaddingEnd)
                .alpha(if (replayEnabled) 1f else 0.5f),
        ) {
            Box(
                modifier = Modifier
                    .size(CardIconSize)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(CardIconGap))
            Text(
                signal.label,
                style = keyLabelStyle(),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = CardLabelMaxLines,
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
