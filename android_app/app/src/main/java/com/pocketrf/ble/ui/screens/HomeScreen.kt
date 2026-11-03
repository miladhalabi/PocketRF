package com.pocketrf.ble.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SsidChart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pocketrf.ble.ble.ConnState
import com.pocketrf.ble.ble.RfStatus
import com.pocketrf.ble.library.SavedSignal
import com.pocketrf.ble.ui.Routes
import com.pocketrf.ble.ui.common.PressableFilledButton
import com.pocketrf.ble.ui.common.PressableOutlinedButton
import com.pocketrf.ble.ui.common.StatusIndicator
import com.pocketrf.ble.ui.common.StatusSection

private data class MenuEntry(val route: String, val label: String, val sublabel: String, val icon: ImageVector)

private val MENU = listOf(
    MenuEntry(Routes.SCAN, "Scan/copy", "Auto-sweep, lock on, decode", Icons.Outlined.Radar),
    MenuEntry(Routes.FIXED_RX, "Fixed Freq Scan/copy", "Decode on a frequency you pick", Icons.Outlined.GraphicEq),
    MenuEntry(Routes.CUSTOM_SUBGHZ, "Custom SubGhz", "Pick a protocol, enter a key, send", Icons.AutoMirrored.Outlined.Send),
    MenuEntry(Routes.SPECTRUM, "Spectrum", "Live RSSI + sweep across the band", Icons.Outlined.SsidChart),
    MenuEntry(Routes.LISTEN, "Listen", "Frequency / pulse counter", Icons.Outlined.Headphones),
    MenuEntry(Routes.BRUTEFORCE, "Bruteforce", "Enumerate a fixed-code keyspace", Icons.Outlined.VpnKey),
    MenuEntry(Routes.JAMMER, "Jammer", "Full / Intermittent / Noise / Sweep", Icons.Outlined.Block),
    MenuEntry(Routes.CONFIG, "Config", "Frequency, preset, scan threshold", Icons.Outlined.Tune),
    MenuEntry(Routes.LIBRARY, "Library", "Saved signals, replay, export", Icons.Outlined.FolderOpen),
)

/**
 * Two swipeable pages sharing one header: Keys (pinned quick-access) and the
 * full feature menu. A page-dot indicator under the header shows which one
 * you're on, matching the swipe affordance.
 */
@Composable
fun HomeScreen(
    connState: ConnState,
    status: RfStatus?,
    mtu: Int,
    pinnedSignals: List<SavedSignal>,
    onConnectToggle: () -> Unit,
    onRefreshStatus: () -> Unit,
    onNavigate: (String) -> Unit,
    onReplayPinned: (SavedSignal) -> Unit,
    onUnpin: (SavedSignal) -> Unit,
    onReorderPinned: (List<Long>) -> Unit,
) {
    val connected = connState == ConnState.READY
    val pagerState = rememberPagerState(pageCount = { 2 })

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "PocketRF",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            StatusIndicator(connState)
        }

        Spacer(Modifier.height(20.dp))
        PressableFilledButton(
            label = if (connected) "Disconnect" else "Connect",
            icon = if (connected) Icons.Outlined.BluetoothConnected else Icons.AutoMirrored.Outlined.BluetoothSearching,
            onClick = onConnectToggle,
        )
        Spacer(Modifier.height(10.dp))
        PressableOutlinedButton(
            label = "Status",
            icon = Icons.Outlined.Refresh,
            enabled = connected,
            modifier = Modifier.fillMaxWidth(),
            onClick = onRefreshStatus,
        )

        status?.let {
            Spacer(Modifier.height(20.dp))
            StatusSection(it, warningIcon = Icons.Outlined.WarningAmber, mtu = mtu)
        }

        Spacer(Modifier.height(16.dp))
        PageDots(pageCount = 2, currentPage = pagerState.currentPage)
        Spacer(Modifier.height(8.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top,
        ) { page ->
            when (page) {
                0 -> KeysScreen(
                    pinnedSignals = pinnedSignals,
                    replayEnabled = connected,
                    onNavigateScan = { onNavigate(Routes.SCAN) },
                    onNavigateFixedRx = { onNavigate(Routes.FIXED_RX) },
                    onReplay = onReplayPinned,
                    onUnpin = onUnpin,
                    onReorder = onReorderPinned,
                )
                else -> MenuPage(onNavigate)
            }
        }
    }
}

@Composable
private fun PageDots(pageCount: Int, currentPage: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(pageCount) { index ->
            val active = index == currentPage
            val size by animateDpAsState(if (active) 7.dp else 6.dp, tween(160), label = "pageDotSize")
            val color by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                tween(160),
                label = "pageDotColor",
            )
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun MenuPage(onNavigate: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(MENU) { entry ->
            MenuRow(entry) { onNavigate(entry.route) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        }
    }
}

@Composable
private fun MenuRow(entry: MenuEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(entry.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(entry.sublabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
