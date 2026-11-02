package com.pocketrf.ble.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pocketrf.ble.ble.ConnState
import com.pocketrf.ble.ble.PocketRf
import com.pocketrf.ble.ble.PocketRfConnection
import com.pocketrf.ble.ble.PocketRfEvent
import com.pocketrf.ble.ble.RfProtocols
import com.pocketrf.ble.ble.RfStatus
import com.pocketrf.ble.library.SavedSignal
import com.pocketrf.ble.library.SignalLibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val MAX_LOG_LINES = 200

/** Mirrors the firmware's single-session `RfState` (BLE_PROTOCOL.md §6), plus BRUTEFORCE/JAM which the doc lists but doesn't name as a `RfState` enumerator yet. */
enum class RfMode { IDLE, RX, LISTEN, SPECTRUM_FIXED, SPECTRUM_SWEEP, SCAN, BRUTEFORCE, JAM }

class PocketRfViewModel(app: Application) : AndroidViewModel(app) {
    private val connection = PocketRfConnection(app)
    private val library = SignalLibraryRepository(app)
    val connState: StateFlow<ConnState> = connection.state
    val mtu: StateFlow<Int> = connection.mtu

    private val _savedSignals = MutableStateFlow<List<SavedSignal>>(emptyList())
    val savedSignals: StateFlow<List<SavedSignal>> = _savedSignals.asStateFlow()

    private val _status = MutableStateFlow<RfStatus?>(null)
    val status: StateFlow<RfStatus?> = _status.asStateFlow()

    private val _mode = MutableStateFlow(RfMode.IDLE)
    val mode: StateFlow<RfMode> = _mode.asStateFlow()

    // Discrete, meaningful events (decoded signals, scan locks, acks, jam/brute done).
    private val _log = MutableStateFlow<List<PocketRfEvent>>(emptyList())
    val log: StateFlow<List<PocketRfEvent>> = _log.asStateFlow()

    // High-frequency streaming events get their own "latest value" slot instead of
    // flooding the log (spectrum_fixed arrives ~20/sec, listen_pulse up to 5/sec).
    private val _listenLive = MutableStateFlow<PocketRfEvent.ListenPulse?>(null)
    val listenLive: StateFlow<PocketRfEvent.ListenPulse?> = _listenLive.asStateFlow()

    private val _spectrumFixedLive = MutableStateFlow<PocketRfEvent.SpectrumFixed?>(null)
    val spectrumFixedLive: StateFlow<PocketRfEvent.SpectrumFixed?> = _spectrumFixedLive.asStateFlow()

    private val _spectrumSweepLive = MutableStateFlow<PocketRfEvent.SpectrumSweep?>(null)
    val spectrumSweepLive: StateFlow<PocketRfEvent.SpectrumSweep?> = _spectrumSweepLive.asStateFlow()

    private val _scanLockedFreq = MutableStateFlow<Double?>(null)
    val scanLockedFreq: StateFlow<Double?> = _scanLockedFreq.asStateFlow()

    /** Latest ack/status response (txp/keeloqtx/brute/preset/...), for screens that want inline feedback without reading the whole log. */
    private val _lastCommandResult = MutableStateFlow<PocketRfEvent.CommandResponse?>(null)
    val lastCommandResult: StateFlow<PocketRfEvent.CommandResponse?> = _lastCommandResult.asStateFlow()

    private val _bruteforceDone = MutableStateFlow(false)
    val bruteforceDone: StateFlow<Boolean> = _bruteforceDone.asStateFlow()

    private val _jamDone = MutableStateFlow(false)
    val jamDone: StateFlow<Boolean> = _jamDone.asStateFlow()

    /** Config's saved default; Scan pre-fills its own field from this instead of a hardcoded guess. */
    private val _defaultScanThresholdDbm = MutableStateFlow(-65)
    val defaultScanThresholdDbm: StateFlow<Int> = _defaultScanThresholdDbm.asStateFlow()

    fun setDefaultScanThreshold(dbm: Int) {
        _defaultScanThresholdDbm.value = dbm
    }

    // What to re-issue to resume the session a TX had to pause (RX/Listen/Spectrum/Scan only —
    // see [pauseForTxThenResume]). Deliberately NOT set for Bruteforce/Jam: those screens have no
    // inline replay action, and re-firing a jam session automatically is not something to do
    // without the user explicitly asking for it each time.
    private var resumeMode: RfMode? = null
    private var resumeAction: (suspend () -> Unit)? = null

    private fun rememberResumable(mode: RfMode, action: suspend () -> Unit) {
        resumeMode = mode
        resumeAction = action
    }

    private fun forgetResumable() {
        resumeMode = null
        resumeAction = null
    }

    init {
        viewModelScope.launch { refreshLibrary() }
        viewModelScope.launch {
            connection.events.collect { event ->
                when (event) {
                    is PocketRfEvent.ListenPulse -> _listenLive.value = event
                    is PocketRfEvent.SpectrumFixed -> _spectrumFixedLive.value = event
                    is PocketRfEvent.SpectrumSweep -> _spectrumSweepLive.value = event
                    is PocketRfEvent.ScanLocked -> {
                        _scanLockedFreq.value = event.freq
                        _log.update { (it + event).takeLast(MAX_LOG_LINES) }
                    }
                    is PocketRfEvent.DecodedRf, is PocketRfEvent.RawPulse -> {
                        // No auto-save: the user picks which captures are worth keeping and
                        // names them explicitly (see saveCapture()) rather than every decode
                        // silently landing in the library.
                        _log.update { (it + event).takeLast(MAX_LOG_LINES) }
                    }
                    is PocketRfEvent.CommandResponse -> {
                        _lastCommandResult.value = event
                        _log.update { (it + event).takeLast(MAX_LOG_LINES) }
                    }
                    PocketRfEvent.BruteforceDone -> {
                        _bruteforceDone.value = true
                        _log.update { (it + event).takeLast(MAX_LOG_LINES) }
                    }
                    // The device auto-stops on its own 30s safety cutoff regardless of what the
                    // client thinks is happening (BLE_PROTOCOL.md §3.1) — mirror that here so the
                    // UI doesn't keep showing "jamming" after the radio has already gone idle.
                    PocketRfEvent.JamDone -> {
                        _jamDone.value = true
                        _mode.value = RfMode.IDLE
                        _log.update { (it + event).takeLast(MAX_LOG_LINES) }
                    }
                    else -> _log.update { (it + event).takeLast(MAX_LOG_LINES) }
                }
            }
        }
    }

    /** True for a capture the user could actually save: a decoded signal, or a raw capture that wasn't cut short (BLE_PROTOCOL.md §5.1 — a truncated one is missing data, saving it would silently replay an incomplete signal). */
    fun isSaveable(event: PocketRfEvent): Boolean = when (event) {
        is PocketRfEvent.DecodedRf -> true
        is PocketRfEvent.RawPulse -> !event.truncated && event.durations.isNotEmpty()
        else -> false
    }

    /** Suggested name shown in the save dialog — the user can change it before confirming. */
    fun suggestedLabel(event: PocketRfEvent): String = when (event) {
        is PocketRfEvent.DecodedRf -> if (event.mfName != null && event.mfName != "Generic KeeLoq") event.mfName else event.protocol
        is PocketRfEvent.RawPulse -> "RAW capture (${event.count} pulses)"
        else -> ""
    }

    /** Explicit save, triggered by the user naming a capture in the UI — see [isSaveable]/[suggestedLabel]. */
    fun saveCapture(event: PocketRfEvent, label: String) {
        viewModelScope.launch {
            val signal = when (event) {
                is PocketRfEvent.DecodedRf -> SavedSignal(
                    label = label,
                    protocol = event.protocol,
                    bits = event.bits,
                    keyHex = event.keyHex,
                    freqMhz = event.freq,
                    teUs = event.te,
                    rssi = event.rssi,
                    mfName = event.mfName,
                    btn = event.btn,
                    serial = event.serial,
                    cnt = event.cnt,
                    favorite = false,
                    source = if (event.scanLocked) "scan" else "rx",
                    createdAtMs = System.currentTimeMillis(),
                )
                is PocketRfEvent.RawPulse -> SavedSignal(
                    label = label,
                    protocol = "RAW",
                    bits = 0,
                    keyHex = "",
                    freqMhz = event.freq,
                    teUs = null,
                    rssi = event.rssi,
                    mfName = null,
                    btn = null,
                    serial = null,
                    cnt = null,
                    favorite = false,
                    source = if (event.scanLocked) "scan" else "rx",
                    createdAtMs = System.currentTimeMillis(),
                    rawDurations = event.durations,
                )
                else -> return@launch
            }
            library.save(signal)
            refreshLibrary()
        }
    }

    private suspend fun refreshLibrary() {
        _savedSignals.value = library.loadAll()
    }

    fun renameSignal(signal: SavedSignal, newLabel: String) {
        viewModelScope.launch {
            library.rename(signal, newLabel)
            refreshLibrary()
        }
    }

    fun toggleFavorite(signal: SavedSignal) {
        viewModelScope.launch {
            library.setFavorite(signal, !signal.favorite)
            refreshLibrary()
        }
    }

    /** Persists a drag-reorder on the Keys screen's pinned grid. */
    fun reorderPinnedKeys(orderedIds: List<Long>) {
        viewModelScope.launch {
            library.reorderPinned(orderedIds, _savedSignals.value)
            refreshLibrary()
        }
    }

    fun deleteSignal(signal: SavedSignal) {
        viewModelScope.launch {
            library.delete(signal)
            refreshLibrary()
        }
    }

    /**
     * Pauses whatever RX/scan/listen/spectrum session is running (if any), runs [tx] and waits
     * for it to genuinely finish, then restarts that same session with the same parameters.
     *
     * Two problems this solves together:
     *  - Sending a TX command does NOT implicitly stop an active RX session the way starting a
     *    new *session* mode does — the radio goes back to listening right after, so a
     *    still-running Scan/RX hears the app's own replay and logs it as a "new" signal.
     *  - A plain `sendAscii` only waits for the BLE write to land, not for the device to finish
     *    acting on it — resuming immediately after that would race the actual transmission.
     *    [tx] must use `sendAsciiAwaitingAck`/`sendBinary`/`sendRawChunked` (all of which wait
     *    for the device's real response) for the "finished" half of this to be true.
     */
    private suspend fun pauseForTxThenResume(tx: suspend () -> Unit) {
        val pausedMode = resumeMode
        val pausedAction = resumeAction
        if (_mode.value != RfMode.IDLE) {
            connection.sendAsciiAwaitingAck("subghz stop")
            _mode.value = RfMode.IDLE
            clearLiveState()
        }

        tx()

        if (pausedMode != null && pausedAction != null) {
            pausedAction()
            _mode.value = pausedMode
        }
    }

    /** Re-transmits a saved signal: raw captures go out through the chunked binary transfer (any length, BLE_PROTOCOL.md §4.3.1), KeeLoq frames raw (`keeloqtx`), everything else via the named-protocol TX command. */
    fun replaySignal(signal: SavedSignal) {
        viewModelScope.launch {
            pauseForTxThenResume {
                val durations = signal.rawDurations
                if (durations != null) {
                    val freq = signal.freqMhz ?: return@pauseForTxThenResume
                    connection.sendRawChunked(freq.toFloat(), durations)
                } else if (signal.isKeeloq) {
                    connection.sendAsciiAwaitingAck("subghz keeloqtx ${signal.keyHex}")
                } else {
                    val freq = signal.freqMhz ?: return@pauseForTxThenResume
                    connection.sendAsciiAwaitingAck("subghz txp ${signal.protocol} $freq ${signal.bits} ${signal.keyHex}")
                }
            }
        }
    }

    /** Replays a just-captured signal straight from the live log, without saving it first — same TX paths as [replaySignal], just fed from a [PocketRfEvent] instead of a [SavedSignal]. Silently no-ops for anything [isSaveable] would also reject. */
    fun replayEvent(event: PocketRfEvent) {
        viewModelScope.launch {
            pauseForTxThenResume {
                when (event) {
                    is PocketRfEvent.DecodedRf -> {
                        val freq = event.freq ?: return@pauseForTxThenResume
                        if (event.protocol.equals(RfProtocols.KEELOQ, ignoreCase = true)) {
                            connection.sendAsciiAwaitingAck("subghz keeloqtx ${event.keyHex}")
                        } else {
                            connection.sendAsciiAwaitingAck("subghz txp ${event.protocol} $freq ${event.bits} ${event.keyHex}")
                        }
                    }
                    is PocketRfEvent.RawPulse -> {
                        if (event.truncated || event.durations.isEmpty()) return@pauseForTxThenResume
                        val freq = event.freq ?: return@pauseForTxThenResume
                        connection.sendRawChunked(freq.toFloat(), event.durations)
                    }
                    else -> Unit
                }
            }
        }
    }

    fun connect() {
        viewModelScope.launch { connection.connect() }
    }

    fun disconnect() {
        connection.disconnect()
        _status.value = null
        _mode.value = RfMode.IDLE
        clearLiveState()
        forgetResumable()
    }

    fun refreshStatus() {
        viewModelScope.launch {
            val resp = connection.sendBinary(PocketRf.Cmd.STATUS) ?: return@launch
            _status.value = RfStatus.fromPayload(resp.payload)
        }
    }

    /** Starting any mode implicitly stops whatever was running (firmware state model); picking the active mode again stops it explicitly. */
    fun selectMode(target: RfMode, spectrumFreqMhz: Double? = null, scanThresholdDbm: Int? = null) {
        viewModelScope.launch {
            if (_mode.value == target) {
                stopAll()
                return@launch
            }
            clearLiveState()
            when (target) {
                RfMode.RX -> {
                    connection.sendBinary(PocketRf.Cmd.START_RX)
                    rememberResumable(target) { connection.sendBinary(PocketRf.Cmd.START_RX) }
                }
                RfMode.LISTEN -> {
                    connection.sendAscii("subghz listen")
                    rememberResumable(target) { connection.sendAscii("subghz listen") }
                }
                RfMode.SPECTRUM_FIXED -> {
                    val cmd = if (spectrumFreqMhz != null) "subghz rssi $spectrumFreqMhz" else "subghz rssi"
                    connection.sendAscii(cmd)
                    rememberResumable(target) { connection.sendAscii(cmd) }
                }
                RfMode.SPECTRUM_SWEEP -> {
                    connection.sendAscii("subghz sweep")
                    rememberResumable(target) { connection.sendAscii("subghz sweep") }
                }
                // Default -65dBm rarely locks on real hardware (BLE_PROTOCOL.md §4.6: RSSI
                // calibration varies a lot between CC1101 clones/antennas) so this is user-tunable.
                RfMode.SCAN -> {
                    val cmd = if (scanThresholdDbm != null) "subghz scan $scanThresholdDbm" else "subghz scan"
                    connection.sendAscii(cmd)
                    rememberResumable(target) { connection.sendAscii(cmd) }
                }
                RfMode.IDLE -> {
                    connection.sendAscii("subghz stop")
                    forgetResumable()
                }
                // BRUTEFORCE and JAM have their own dedicated start*() functions (each needs
                // params — protocol/bits/repeat, or jam mode/freq — this generic entry point
                // doesn't take).
                RfMode.BRUTEFORCE, RfMode.JAM -> return@launch
            }
            _mode.value = target
        }
    }

    fun stopAll() {
        viewModelScope.launch {
            connection.sendAscii("subghz stop")
            _mode.value = RfMode.IDLE
            clearLiveState()
            forgetResumable()
        }
    }

    /** Custom SubGhz: transmits a protocol by name with an explicit key. KeeLoq goes out raw (10 repeats, firmware-side), everything else via `subghz txp`. */
    fun transmitProtocol(protocol: String, freqMhz: Double, bits: Int, keyHex: String) {
        viewModelScope.launch {
            pauseForTxThenResume {
                if (protocol.equals(RfProtocols.KEELOQ, ignoreCase = true)) {
                    connection.sendAsciiAwaitingAck("subghz keeloqtx $keyHex")
                } else {
                    connection.sendAsciiAwaitingAck("subghz txp $protocol $freqMhz $bits $keyHex")
                }
            }
        }
    }

    /** Custom SubGhz: replays a Flipper-style `.sub` RAW file's full contents via the special `RAWSUB ` path. */
    fun replaySubFile(fileContents: String) {
        viewModelScope.launch {
            pauseForTxThenResume {
                connection.sendRawSubAwaitingAck(fileContents)
            }
        }
    }

    fun applyPreset(name: String) {
        viewModelScope.launch { connection.sendAscii("subghz preset $name") }
    }

    fun startBruteforce(protocol: String, bits: Int?, freqMhz: Double?, repeat: Int?) {
        viewModelScope.launch {
            clearLiveState()
            forgetResumable()
            val cmd = buildString {
                append("subghz brute $protocol")
                if (bits != null) append(" $bits")
                if (freqMhz != null) append(" $freqMhz")
                if (repeat != null) append(" $repeat")
            }
            connection.sendAscii(cmd)
            _mode.value = RfMode.BRUTEFORCE
        }
    }

    /**
     * Starts a jam session. `mode` is one of "full"/"itmt"/"noise"/"sweep"
     * (BLE_PROTOCOL.md §3.1/§4.1). The device enforces its own hard 30s
     * cutoff regardless of what this call does — see the `JamDone` handling
     * above, which brings the UI back to IDLE when that fires.
     */
    fun startJam(mode: String, freqMhz: Double?) {
        viewModelScope.launch {
            clearLiveState()
            forgetResumable()
            connection.sendAscii(buildString {
                append("subghz jam $mode")
                if (freqMhz != null) append(" $freqMhz")
            })
            _mode.value = RfMode.JAM
        }
    }

    fun setFrequencyHz(freqHz: Long) {
        viewModelScope.launch {
            val payload = ByteBuffer
                .allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(freqHz.toInt())
                .array()
            connection.sendBinary(PocketRf.Cmd.SET_FREQ, payload)
            refreshStatus()
        }
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    private fun clearLiveState() {
        _listenLive.value = null
        _spectrumFixedLive.value = null
        _spectrumSweepLive.value = null
        _scanLockedFreq.value = null
        _bruteforceDone.value = false
        _jamDone.value = false
    }

    override fun onCleared() {
        connection.disconnect()
    }
}
