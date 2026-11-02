package com.pocketrf.ble.library

/**
 * A signal kept in the app's local library. PocketRF itself is headless and
 * storage-free by design (BLE_PROTOCOL.md intro) — the firmware's own dev
 * log is explicit that recent-codes history and a saved-signal library were
 * deliberately left off the device for a future mobile app to own. This is
 * that store.
 */
data class SavedSignal(
    val id: Long = 0,
    val label: String,
    val protocol: String,
    val bits: Int,
    val keyHex: String,
    val freqMhz: Double?,
    val teUs: Int?,
    val rssi: Int?,
    val mfName: String?,
    val btn: Int?,
    val serial: String?,
    val cnt: Int?,
    val favorite: Boolean,
    val source: String, // "rx" | "scan" | "manual"
    val createdAtMs: Long,
    /** Set only for an unrecognized capture (raw_pulse/scan_raw, BLE_PROTOCOL.md §5.1) — the actual pulse durations, replayable via `subghz rawtx`. Never saved when the capture arrived `truncated`. */
    val rawDurations: List<Int>? = null,
    /** Position in the Keys screen's pinned grid — only meaningful while [favorite] is true, user-reordered by drag. */
    val pinOrder: Int = 0,
) {
    /** KeeLoq frames replay via `subghz keeloqtx`; everything else via `subghz txp`. */
    val isKeeloq: Boolean get() = protocol.equals("KeeLoq", ignoreCase = true)
    val isRaw: Boolean get() = rawDurations != null
}
