package com.pocketrf.ble.ble

import org.json.JSONObject

/**
 * Reassembles a chunked `raw_pulse`/`scan_raw` capture (BLE_PROTOCOL.md
 * §5.1): each notification carries a `"part"` of `full`/`begin`/`chunk`/
 * `end`. Only one capture is ever in flight — a fresh `"begin"` silently
 * replaces whatever was accumulating, matching the firmware's own
 * best-effort semantics (no guaranteed delivery, no abandonment signal for
 * the capture it replaces).
 */
class RawCaptureAssembler {
    private var totalCount = 0
    private var filled = 0
    private var buffer: IntArray? = null
    private var freq: Double? = null
    private var rssi: Int? = null
    private var scanLocked = false

    /**
     * Feeds one raw_pulse/scan_raw JSON payload. Returns a completed event
     * only once a capture is fully known — immediately for `"full"`, or
     * once `"end"` closes out a begin/chunk sequence. `null` otherwise
     * (still accumulating).
     */
    fun accept(json: JSONObject, isScanRaw: Boolean): PocketRfEvent.RawPulse? {
        return when (json.optString("part", "full")) {
            "full" -> {
                val arr = json.optJSONArray("durations")
                val durations = buildList {
                    if (arr != null) for (i in 0 until arr.length()) add(arr.optInt(i))
                }
                PocketRfEvent.RawPulse(
                    count = json.optInt("count", durations.size),
                    freq = json.optDoubleOrNull("freq"),
                    rssi = json.optIntOrNull("rssi"),
                    durations = durations,
                    truncated = false, // "full" means it fit in one notification, by definition complete
                    scanLocked = isScanRaw,
                )
            }

            "begin" -> {
                totalCount = json.optInt("total_count").coerceAtLeast(0)
                buffer = IntArray(totalCount)
                filled = 0
                freq = json.optDoubleOrNull("freq")
                rssi = json.optIntOrNull("rssi")
                scanLocked = isScanRaw
                null
            }

            "chunk" -> {
                val buf = buffer ?: return null // a chunk with no preceding begin has nowhere to go
                val offset = json.optInt("offset")
                val arr = json.optJSONArray("durations") ?: return null
                for (i in 0 until arr.length()) {
                    val idx = offset + i
                    if (idx in buf.indices) {
                        buf[idx] = arr.optInt(i)
                        filled++
                    }
                }
                null
            }

            "end" -> {
                val buf = buffer
                buffer = null
                if (buf == null) return null // stray end, nothing was in progress
                // A dropped chunk leaves gaps the firmware has no way to signal individually;
                // filled < totalCount is the only evidence, so that capture is marked truncated
                // the same as the old single-notification case — never saveable (see ViewModel.isSaveable).
                PocketRfEvent.RawPulse(
                    count = totalCount,
                    freq = freq,
                    rssi = rssi,
                    durations = buf.toList(),
                    truncated = filled < totalCount,
                    scanLocked = scanLocked,
                )
            }

            else -> null
        }
    }
}
