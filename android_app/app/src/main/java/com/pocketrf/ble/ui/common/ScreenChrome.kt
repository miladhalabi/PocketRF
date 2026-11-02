package com.pocketrf.ble.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pocketrf.ble.ble.ConnState
import com.pocketrf.ble.ui.theme.StatusAmber

/** A quiet dot + label instead of a loud colored pill; the dot carries the state color. */
@Composable
fun StatusIndicator(state: ConnState, modifier: Modifier = Modifier, compact: Boolean = false) {
    val (color, label) = when (state) {
        ConnState.READY -> MaterialTheme.colorScheme.primary to "Connected"
        ConnState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant to "Disconnected"
        else -> StatusAmber to "Connecting"
    }
    val animatedColor by animateColorAsState(color, tween(200), label = "statusDotColor")

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(animatedColor),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shared top bar for every screen below Home: back arrow, title, connection dot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketRfTopBar(title: String, connState: ConnState, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            StatusIndicator(connState, modifier = Modifier.padding(end = 16.dp), compact = true)
        },
    )
}
