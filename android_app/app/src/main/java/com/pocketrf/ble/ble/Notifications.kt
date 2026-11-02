package com.pocketrf.ble.ble

import org.json.JSONException
import org.json.JSONObject

/**
 * Everything the device can push on the TX characteristic unsolicited,
 * from BLE_PROTOCOL.md §5. A missing/absent "type" means it's a plain
 * command response (JSON ack or binary frame), not one of these.
 */
sealed class PocketRfEvent {
    data class DecodedRf(
        val protocol: String,
        val bits: Int,
        val keyHex: String,
        val te: Int?,
        val freq: Double?,
        val rssi: Int?,
        val mfName: String?,
        val btn: Int?,
        val serial: String?,
        val cnt: Int?,
        val scanLocked: Boolean = false, // true if this came from scan_decoded
    ) : PocketRfEvent()

    data class RawPulse(
        val count: Int,
        val freq: Double?,
        val rssi: Int?,
        val durations: List<Int>,
        val truncated: Boolean,
        val scanLocked: Boolean = false, // true if this came from scan_raw
    ) : PocketRfEvent()

    data class ListenPulse(
        val centerFreq: Double?,
        val pulseDurUs: Int?,
        val detectedFreqHz: Long?,
        val rssi: Int?,
    ) : PocketRfEvent()

    data class SpectrumFixed(val freq: Double?, val rssi: Int?) : PocketRfEvent()

    data class SpectrumSweep(
        val startFreq: Double?,
        val endFreq: Double?,
        val stepMhz: Double?,
        val count: Int?,
        val rssi: List<Int>,
    ) : PocketRfEvent()

    data object JamDone : PocketRfEvent()
    data object BruteforceDone : PocketRfEvent()
    data class ScanLocked(val freq: Double?) : PocketRfEvent()

    /** Any ack/status/error response that is NOT a `"type"`-tagged push. */
    data class CommandResponse(val status: String?, val cmd: String?, val raw: JSONObject) :
        PocketRfEvent()

    /** JSON parsed but didn't match a known shape, or wasn't JSON at all. */
    data class Unknown(val raw: String) : PocketRfEvent()
}

/** Parses one TX-characteristic notification payload (UTF-8 JSON text) into a [PocketRfEvent]. */
fun parsePocketRfEvent(text: String): PocketRfEvent {
    val json = try {
        JSONObject(text)
    } catch (e: JSONException) {
        return PocketRfEvent.Unknown(text)
    }

    return when (json.optString("type", "")) {
        "decoded_rf", "scan_decoded" -> PocketRfEvent.DecodedRf(
            protocol = json.optString("protocol", "?"),
            bits = json.optInt("bits"),
            keyHex = json.optString("key", ""),
            te = json.optIntOrNull("te"),
            freq = json.optDoubleOrNull("freq"),
            rssi = json.optIntOrNull("rssi"),
            mfName = json.optStringOrNull("mf_name"),
            btn = json.optIntOrNull("btn"),
            serial = json.optStringOrNull("serial"),
            cnt = json.optIntOrNull("cnt"),
            scanLocked = json.optString("type") == "scan_decoded",
        )

        // raw_pulse/scan_raw are NOT handled here: BLE_PROTOCOL.md §5.1 makes them a stateful
        // begin/chunk/end sequence now, which this stateless per-message parser can't reassemble.
        // PocketRfConnection intercepts both types before they reach this function and routes
        // them through RawCaptureAssembler instead.

        "listen_pulse" -> PocketRfEvent.ListenPulse(
            centerFreq = json.optDoubleOrNull("center_freq"),
            pulseDurUs = json.optIntOrNull("pulse_dur_us"),
            detectedFreqHz = json.optLongOrNull("detected_freq_hz"),
            rssi = json.optIntOrNull("rssi"),
        )

        "spectrum_fixed" -> PocketRfEvent.SpectrumFixed(
            freq = json.optDoubleOrNull("freq"),
            rssi = json.optIntOrNull("rssi"),
        )

        "spectrum_sweep" -> {
            val arr = json.optJSONArray("rssi")
            val rssiList = buildList {
                if (arr != null) for (i in 0 until arr.length()) add(arr.optInt(i))
            }
            PocketRfEvent.SpectrumSweep(
                startFreq = json.optDoubleOrNull("start_freq"),
                endFreq = json.optDoubleOrNull("end_freq"),
                stepMhz = json.optDoubleOrNull("step_mhz"),
                count = json.optIntOrNull("count"),
                rssi = rssiList,
            )
        }

        "jam_done" -> PocketRfEvent.JamDone
        "bruteforce_done" -> PocketRfEvent.BruteforceDone
        "scan_locked" -> PocketRfEvent.ScanLocked(freq = json.optDoubleOrNull("freq"))

        else -> PocketRfEvent.CommandResponse(
            status = json.optStringOrNull("status"),
            cmd = json.optStringOrNull("cmd"),
            raw = json,
        )
    }
}

// internal (not private): RawCaptureAssembler, in the same package, reuses these.
internal fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
internal fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) optLong(key) else null
internal fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key) else null
internal fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) optString(key) else null
