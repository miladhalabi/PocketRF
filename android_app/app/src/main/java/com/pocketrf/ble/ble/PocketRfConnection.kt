package com.pocketrf.ble.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "PocketRfConnection"
private const val WRITE_TIMEOUT_MS = 2_500L
private const val SCAN_TIMEOUT_MS = 10_000L
// TX commands repeat a code up to 10x firmware-side before replying, so they get more
// room than a plain status/control write before giving up on an ack.
private const val TX_ACK_TIMEOUT_MS = 6_000L

enum class ConnState { DISCONNECTED, SCANNING, CONNECTING, DISCOVERING, READY }

/**
 * Owns the whole BLE lifecycle for one PocketRF device: scan -> connect ->
 * discover -> subscribe -> MTU negotiation -> ready to send commands.
 *
 * Call sites interact with it entirely through coroutines/Flow; no raw
 * BluetoothGattCallback plumbing leaks out. One in-flight write at a time
 * (matches the device: one command -> one response).
 */
@SuppressLint("MissingPermission") // caller is responsible for the runtime permission dance
class PocketRfConnection(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var gatt: BluetoothGatt? = null
    private var rxChar: BluetoothGattCharacteristic? = null
    private var txChar: BluetoothGattCharacteristic? = null
    private var negotiatedMtu: Int = 23 // BLE default until onMtuChanged reports the real one

    private val _state = MutableStateFlow(ConnState.DISCONNECTED)
    val state: StateFlow<ConnState> = _state.asStateFlow()

    private val _mtu = MutableStateFlow(23)
    /** The actual negotiated MTU (BLE default 23 until a connection reports otherwise) — surfaced so the UI can explain truncated raw captures (BLE_PROTOCOL.md §5.1) instead of silently logging it. */
    val mtu: StateFlow<Int> = _mtu.asStateFlow()

    private val _events = MutableSharedFlow<PocketRfEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<PocketRfEvent> = _events.asSharedFlow()

    private val _rawResponses = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)
    private val rawCaptureAssembler = RawCaptureAssembler()

    private var pendingWriteAck: CompletableDeferred<Unit>? = null

    /** Scans for the device by name (falls back to service UUID filter), then connects. */
    suspend fun connect(): Boolean {
        val bt = adapter ?: return false
        if (!bt.isEnabled) return false

        _state.value = ConnState.SCANNING
        val device = withTimeoutOrNull(SCAN_TIMEOUT_MS) { scanForDevice(bt) }
        if (device == null) {
            Log.w(TAG, "scan timed out, no PocketRF device found")
            _state.value = ConnState.DISCONNECTED
            return false
        }

        _state.value = ConnState.CONNECTING
        val connected = CompletableDeferred<Boolean>()
        gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        _state.value = ConnState.DISCOVERING
                        g.requestMtu(PocketRf.PREFERRED_MTU)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        _state.value = ConnState.DISCONNECTED
                        rxChar = null
                        txChar = null
                        if (!connected.isCompleted) connected.complete(false)
                    }
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                negotiatedMtu = mtu
                _mtu.value = mtu
                g.discoverServices()
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    connected.complete(false)
                    return
                }
                val service = g.getService(PocketRf.SERVICE_UUID)
                val rx = service?.getCharacteristic(PocketRf.RX_CHAR_UUID)
                val tx = service?.getCharacteristic(PocketRf.TX_CHAR_UUID)
                if (rx == null || tx == null) {
                    Log.e(TAG, "PocketRF service/characteristics missing")
                    connected.complete(false)
                    return
                }
                rxChar = rx
                txChar = tx

                g.setCharacteristicNotification(tx, true)
                val cccd = tx.getDescriptor(PocketRf.CCCD_UUID)
                if (cccd != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        g.writeDescriptor(cccd)
                    }
                }
                _state.value = ConnState.READY
                connected.complete(true)
            }

            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                handleIncoming(value)
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                // Pre-API33 path; API33+ overload above is used instead when available.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    handleIncoming(characteristic.value ?: return)
                }
            }

            override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                pendingWriteAck?.complete(Unit)
            }
        })

        return connected.await()
    }

    private fun handleIncoming(value: ByteArray) {
        if (value.isEmpty()) return
        if (value[0] == PocketRf.FRAME_START) {
            _rawResponses.tryEmit(value)
            return
        }
        val text = value.toString(Charsets.UTF_8)

        // raw_pulse/scan_raw are a stateful begin/chunk/end sequence (BLE_PROTOCOL.md §5.1),
        // so they're reassembled here rather than by the stateless parsePocketRfEvent below.
        val json = runCatching { JSONObject(text) }.getOrNull()
        val type = json?.optString("type", "")
        if (json != null && (type == "raw_pulse" || type == "scan_raw")) {
            rawCaptureAssembler.accept(json, isScanRaw = type == "scan_raw")?.let { _events.tryEmit(it) }
            return
        }

        _events.tryEmit(parsePocketRfEvent(text))
    }

    private suspend fun scanForDevice(bt: BluetoothAdapter): BluetoothDevice? {
        val scanner = bt.bluetoothLeScanner ?: return null
        val found = CompletableDeferred<BluetoothDevice>()
        // No ScanFilter here deliberately: NimBLE's default advertisement is small and
        // commonly carries only flags + the device name, not the full 128-bit service
        // UUID (that costs 16 bytes of the ~31-byte legacy adv payload). Filtering on
        // the service UUID would silently match nothing against real firmware, so we
        // scan unfiltered and match on name in the callback instead.
        val filters = emptyList<ScanFilter>()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: result.scanRecord?.deviceName
                if (name == PocketRf.DEVICE_NAME && !found.isCompleted) {
                    found.complete(result.device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed: $errorCode")
                if (!found.isCompleted) found.completeExceptionally(IllegalStateException("scan failed: $errorCode"))
            }
        }
        scanner.startScan(filters, settings, callback)
        try {
            return found.await()
        } finally {
            scanner.stopScan(callback)
        }
    }

    /**
     * Sends a binary command and waits for its framed response (matched by
     * arrival order — the device replies to exactly one in-flight command).
     */
    suspend fun sendBinary(cmd: Byte, payload: ByteArray = ByteArray(0)): BinaryResponse? {
        val frame = buildBinaryFrame(cmd, payload)
        return sendRawAndAwaitBinaryResponse(frame)
    }

    /** Sends a raw ASCII/text command (e.g. `"subghz status"`); response arrives on [events]. */
    suspend fun sendAscii(command: String) {
        writeToRx(command.toByteArray(Charsets.UTF_8))
    }

    /**
     * Sends an ASCII command and waits for its JSON ack (unlike [sendAscii],
     * which only waits for the BLE write itself to land — not for the
     * device to finish acting on it). Needed wherever the caller has to
     * know the command actually completed before doing anything else, e.g.
     * not restarting an RX session until a TX has genuinely finished
     * transmitting, not just been handed to the radio.
     */
    suspend fun sendAsciiAwaitingAck(command: String): PocketRfEvent.CommandResponse? =
        writeAwaitingAck(command.toByteArray(Charsets.UTF_8))

    /** Sends a raw `.sub` file body prefixed with the special `RAWSUB ` marker. Only safe for a file small enough to fit one write — see [sendRawChunked] for anything that might not (BLE_PROTOCOL.md §4.3). */
    suspend fun sendRawSub(subFileContents: String) {
        writeToRx(("RAWSUB " + subFileContents).toByteArray(Charsets.UTF_8))
    }

    /** Same as [sendRawSub], but waits for the device's JSON ack — see [sendAsciiAwaitingAck]. */
    suspend fun sendRawSubAwaitingAck(subFileContents: String): PocketRfEvent.CommandResponse? =
        writeAwaitingAck(("RAWSUB " + subFileContents).toByteArray(Charsets.UTF_8))

    private suspend fun writeAwaitingAck(bytes: ByteArray): PocketRfEvent.CommandResponse? {
        var result: PocketRfEvent.CommandResponse? = null
        val got = CompletableDeferred<Unit>()
        val collectorJob = scope.launch {
            _events.collect { event ->
                if (event is PocketRfEvent.CommandResponse) {
                    result = event
                    if (!got.isCompleted) got.complete(Unit)
                }
            }
        }
        writeToRx(bytes)
        withTimeoutOrNull(TX_ACK_TIMEOUT_MS) { got.await() }
        collectorJob.cancel()
        return result
    }

    /**
     * Transmits a (possibly long) raw duration list via the chunked binary
     * transfer (`CMD_TX_RAW_BEGIN`/`CHUNK`/`END`, BLE_PROTOCOL.md §4.3.1),
     * sized to the negotiated MTU instead of one unbounded write — this is
     * what replaying a saved RAW capture, or a parsed `.sub` import, should
     * go through rather than `subghz rawtx`/`RAWSUB`. Returns true only if
     * the final END reports success.
     */
    suspend fun sendRawChunked(freqMhz: Float, durations: List<Int>): Boolean {
        val capped = durations.take(PocketRf.RAW_CHUNK_MAX_DURATIONS)

        val beginPayload = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(freqMhz)
            .putShort(capped.size.toShort())
            .array()
        val began = sendBinary(PocketRf.Cmd.TX_RAW_BEGIN, beginPayload)
        if (began?.payload?.firstOrNull()?.toInt() != 0x00) return false

        // Each i16 duration is 2 bytes; PAYLOAD_LEN is a single byte (max 255, §3), so a
        // chunk is capped at 127 durations regardless of how generous the MTU is.
        val chunkSize = (((negotiatedMtu - 3 - 4) / 2).coerceIn(1, 127))
        for (start in capped.indices step chunkSize) {
            val chunk = capped.subList(start, minOf(start + chunkSize, capped.size))
            val chunkPayload = ByteBuffer.allocate(chunk.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            chunk.forEach { chunkPayload.putShort(it.toShort()) }
            val resp = sendBinary(PocketRf.Cmd.TX_RAW_CHUNK, chunkPayload.array())
            if (resp?.payload?.firstOrNull()?.toInt() != 0x00) return false
        }

        val ended = sendBinary(PocketRf.Cmd.TX_RAW_END)
        return ended?.payload?.firstOrNull()?.toInt() == 0x00
    }

    private suspend fun sendRawAndAwaitBinaryResponse(frame: ByteArray): BinaryResponse? {
        var result: BinaryResponse? = null
        val got = CompletableDeferred<Unit>()
        val collectorJob = scope.launch {
            _rawResponses.collect { bytes ->
                parseBinaryFrame(bytes)?.let {
                    result = it
                    if (!got.isCompleted) got.complete(Unit)
                }
            }
        }
        writeToRx(frame)
        withTimeoutOrNull(WRITE_TIMEOUT_MS) { got.await() }
        collectorJob.cancel()
        return result
    }

    private suspend fun writeToRx(bytes: ByteArray) {
        val g = gatt ?: return
        val rx = rxChar ?: return
        val ack = CompletableDeferred<Unit>()
        pendingWriteAck = ack
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(rx, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            rx.value = bytes
            @Suppress("DEPRECATION")
            rx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            g.writeCharacteristic(rx)
        }
        withTimeoutOrNull(WRITE_TIMEOUT_MS) { ack.await() }
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _state.value = ConnState.DISCONNECTED
    }
}
