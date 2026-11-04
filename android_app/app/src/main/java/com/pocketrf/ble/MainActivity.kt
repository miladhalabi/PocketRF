package com.pocketrf.ble

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pocketrf.ble.ble.ConnState
import com.pocketrf.ble.ui.PocketRfViewModel
import com.pocketrf.ble.ui.RfMode
import com.pocketrf.ble.ui.Routes
import com.pocketrf.ble.ui.common.PocketRfTopBar
import com.pocketrf.ble.ui.screens.BruteforceScreen
import com.pocketrf.ble.ui.screens.ConfigScreen
import com.pocketrf.ble.ui.screens.CustomSubGhzScreen
import com.pocketrf.ble.ui.screens.FixedRxScreen
import com.pocketrf.ble.ui.screens.HomeScreen
import com.pocketrf.ble.ui.screens.JammerScreen
import com.pocketrf.ble.ui.screens.ListenScreen
import com.pocketrf.ble.ui.screens.ScanScreen
import com.pocketrf.ble.ui.screens.SpectrumScreen
import com.pocketrf.ble.ui.theme.PocketRfTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PocketRfViewModel by viewModels()

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.all { it }) viewModel.connect()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PocketRfTheme {
                PocketRfApp(
                    viewModel = viewModel,
                    onConnectRequested = { requestBlePermissionsThenConnect() },
                )
            }
        }
    }

    private fun requestBlePermissionsThenConnect() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.toTypedArray()
        requestPermissions.launch(needed)
    }
}

private const val NavDurationMs = 300
// The outgoing screen is gone within the first ~third of the transition, and
// only then does the incoming one fade in, so the two never visibly overlap.
private const val NavFadeOutMs = 90
private val NavSlideDistance = 30.dp

private fun sharedAxisEnter(fromOffsetPx: Int): EnterTransition =
    slideInHorizontally(tween(NavDurationMs, easing = FastOutSlowInEasing)) { fromOffsetPx } +
        fadeIn(tween(NavDurationMs - NavFadeOutMs, delayMillis = NavFadeOutMs, easing = LinearOutSlowInEasing))

private fun sharedAxisExit(toOffsetPx: Int): ExitTransition =
    slideOutHorizontally(tween(NavDurationMs, easing = FastOutSlowInEasing)) { toOffsetPx } +
        fadeOut(tween(NavFadeOutMs, easing = FastOutLinearInEasing))

@Composable
fun PocketRfApp(viewModel: PocketRfViewModel, onConnectRequested: () -> Unit) {
    val navController = rememberNavController()
    val connState by viewModel.connState.collectAsState()
    val status by viewModel.status.collectAsState()
    val mtu by viewModel.mtu.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val listenLive by viewModel.listenLive.collectAsState()
    val spectrumFixedLive by viewModel.spectrumFixedLive.collectAsState()
    val spectrumSweepLive by viewModel.spectrumSweepLive.collectAsState()
    val scanLockedFreq by viewModel.scanLockedFreq.collectAsState()
    val lastCommandResult by viewModel.lastCommandResult.collectAsState()
    val bruteforceDone by viewModel.bruteforceDone.collectAsState()
    val jamDone by viewModel.jamDone.collectAsState()
    val defaultScanThreshold by viewModel.defaultScanThresholdDbm.collectAsState()
    val log by viewModel.log.collectAsState()
    val savedSignals by viewModel.savedSignals.collectAsState()
    val connected = connState == ConnState.READY

    fun back() = navController.popBackStack()

    // Material "shared axis X": forward slides the new screen in from the
    // right as the old one fades out fast; back is the mirror. Replaces the
    // NavHost default (a slow 700ms crossfade where both screens overlap).
    // Pop transitions also drive predictive back (enabled in the manifest).
    val slidePx = with(LocalDensity.current) { NavSlideDistance.roundToPx() }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { sharedAxisEnter(slidePx) },
        exitTransition = { sharedAxisExit(-slidePx) },
        popEnterTransition = { sharedAxisEnter(-slidePx) },
        popExitTransition = { sharedAxisExit(slidePx) },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                connState = connState,
                status = status,
                mtu = mtu,
                pinnedSignals = savedSignals.filter { it.favorite },
                onConnectToggle = { if (connected) viewModel.disconnect() else onConnectRequested() },
                onRefreshStatus = viewModel::refreshStatus,
                onNavigate = { navController.navigate(it) },
                onReplayPinned = viewModel::replaySignal,
                onUnpin = viewModel::toggleFavorite,
                onReorderPinned = viewModel::reorderPinnedKeys,
            )
        }
        composable(Routes.SCAN) {
            ScanScreen(
                connState = connState,
                mode = mode,
                scanLockedFreq = scanLockedFreq,
                log = log,
                defaultThresholdDbm = defaultScanThreshold,
                isSaveable = viewModel::isSaveable,
                suggestedLabel = viewModel::suggestedLabel,
                onClearLog = viewModel::clearLog,
                onSaveCapture = viewModel::saveCapture,
                onReplayCapture = viewModel::replayEvent,
                onStart = { threshold -> viewModel.selectMode(RfMode.SCAN, scanThresholdDbm = threshold) },
                onStop = viewModel::stopAll,
                onBack = ::back,
            )
        }
        composable(Routes.FIXED_RX) {
            FixedRxScreen(
                connState = connState,
                mode = mode,
                log = log,
                isSaveable = viewModel::isSaveable,
                suggestedLabel = viewModel::suggestedLabel,
                onClearLog = viewModel::clearLog,
                onSaveCapture = viewModel::saveCapture,
                onReplayCapture = viewModel::replayEvent,
                onSetFrequency = viewModel::setFrequencyHz,
                onStart = { viewModel.selectMode(RfMode.RX) },
                onStop = viewModel::stopAll,
                onBack = ::back,
            )
        }
        composable(Routes.CUSTOM_SUBGHZ) {
            CustomSubGhzScreen(
                connState = connState,
                lastCommandResult = lastCommandResult,
                onTransmit = viewModel::transmitProtocol,
                onReplaySubFile = viewModel::replaySubFile,
                onBack = ::back,
            )
        }
        composable(Routes.SPECTRUM) {
            SpectrumScreen(
                connState = connState,
                mode = mode,
                spectrumFixedLive = spectrumFixedLive,
                spectrumSweepLive = spectrumSweepLive,
                onClearLive = {},
                onStartFixed = { freq -> viewModel.selectMode(RfMode.SPECTRUM_FIXED, spectrumFreqMhz = freq) },
                onStartSweep = { viewModel.selectMode(RfMode.SPECTRUM_SWEEP) },
                onStop = viewModel::stopAll,
                onBack = ::back,
            )
        }
        composable(Routes.LISTEN) {
            ListenScreen(
                connState = connState,
                mode = mode,
                listenLive = listenLive,
                onClearLive = {},
                onStart = { viewModel.selectMode(RfMode.LISTEN) },
                onStop = viewModel::stopAll,
                onBack = ::back,
            )
        }
        composable(Routes.BRUTEFORCE) {
            BruteforceScreen(
                connState = connState,
                mode = mode,
                lastCommandResult = lastCommandResult,
                bruteforceDone = bruteforceDone,
                onClearLive = {},
                onStart = viewModel::startBruteforce,
                onStop = viewModel::stopAll,
                onBack = ::back,
            )
        }
        composable(Routes.JAMMER) {
            JammerScreen(
                connState = connState,
                mode = mode,
                jamDone = jamDone,
                onClearLive = {},
                onStart = viewModel::startJam,
                onStop = viewModel::stopAll,
                onBack = ::back,
            )
        }
        composable(Routes.CONFIG) {
            ConfigScreen(
                connState = connState,
                status = status,
                mtu = mtu,
                lastCommandResult = lastCommandResult,
                defaultScanThresholdDbm = defaultScanThreshold,
                onSetFrequency = viewModel::setFrequencyHz,
                onApplyPreset = viewModel::applyPreset,
                onSetDefaultScanThreshold = viewModel::setDefaultScanThreshold,
                onBack = ::back,
            )
        }
        composable(Routes.LIBRARY) {
            var librarySearch by remember { mutableStateOf("") }
            Scaffold(topBar = { PocketRfTopBar("Library", connState, ::back) }) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
                    LibraryScreen(
                        signals = savedSignals,
                        searchQuery = librarySearch,
                        onSearchQueryChange = { librarySearch = it },
                        connected = connected,
                        onReplay = viewModel::replaySignal,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onRename = viewModel::renameSignal,
                        onDelete = viewModel::deleteSignal,
                    )
                }
            }
        }
    }
}
