package com.pocketrf.ble.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Corner radius locked for every signal card app-wide (Library, live capture log) — one shape rule, not a per-screen choice. */
val SignalCardShape = RoundedCornerShape(16.dp)

/** Filled primary button with explicit press feedback: scale(0.97), 160ms ease-out. */
@Composable
fun PressableFilledButton(
    label: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, tween(160), label = "pressScale")
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
    }
}

@Composable
fun PressableOutlinedButton(
    label: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, tween(160), label = "pressScale")
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.outlinedButtonColors(),
        modifier = modifier
            .height(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label)
    }
}

/**
 * A signal card's one prominent action: a filled pill, not a small icon, so
 * replaying is a single obvious tap. Used by the Library, the live capture
 * log, and the Keys grid.
 *
 * Built from primitives rather than Material3's `Button` — a hardcoded
 * parent height elsewhere silently clipped Button's content on one test
 * device, and these primitives make that failure mode impossible to hit by
 * accident (no surface/shadow stack hiding a clipped child).
 *
 * The play icon sits in its own nested circular chip flush with the pill's
 * trailing edge, rather than inline with the text — a "button-in-button"
 * that reads as one more deliberate control instead of an icon glued on.
 */
@Composable
fun QuickReplayButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, tween(160), label = "replayPressScale")

    val containerColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val chipColor = if (enabled) contentColor.copy(alpha = 0.18f) else contentColor.copy(alpha = 0.12f)

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text("Replay", style = MaterialTheme.typography.labelLarge, color = contentColor)
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(chipColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
        }
    }
}
