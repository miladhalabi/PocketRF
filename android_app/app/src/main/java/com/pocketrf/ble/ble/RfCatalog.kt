package com.pocketrf.ble.ble

/**
 * Mirrors `rf_registry` (BLE_PROTOCOL.md §4.4) — order matters, it's also the binary `protoIdx`.
 * A null bit count means "variable" per the doc's own table (the RcSwitch family): the app must
 * ask for it rather than guessing a default.
 */
object RfProtocols {
    val FIXED_CODE: List<Pair<String, Int?>> = listOf(
        "Princeton" to 24,
        "NICE_FLO" to 12,
        "Nice_12bit" to 12,
        "Linear" to 10,
        "Clemsa" to 18,
        "Mastercode" to 36,
        "CAME" to 12,
        "Ansonic" to 12,
        "GateTX" to 24,
        "Holtek" to 40,
        "Holtek_12bit" to 12,
        "Holtek_HT12" to 12,
        "PhoenixV2" to 52,
        "RcSwitch_1" to null,
        "RcSwitch_2" to null,
        "RcSwitch_3" to null,
        "RcSwitch_4" to null,
        "RcSwitch_5" to null,
        "RcSwitch_6" to null,
        "RcSwitch_7" to null,
        "RcSwitch_8" to null,
        "RcSwitch_9" to null,
        "RcSwitch_10" to null,
        "RcSwitch_11" to null,
        "RcSwitch_12" to null,
    )

    /** `txp`/`brute` also accept the plain string "KeeLoq", routed to `CMD_TX_KEELOQ`/`subghz keeloqtx` instead of the registry. */
    const val KEELOQ = "KeeLoq"

    val ALL_NAMES = FIXED_CODE.map { it.first } + KEELOQ

    fun defaultBitsFor(protocol: String): Int? = FIXED_CODE.firstOrNull { it.first == protocol }?.second
}

/** Mirrors `rf_presets` (BLE_PROTOCOL.md §4.5). Changes modulation/deviation/RX-BW/data-rate, not frequency. */
object RfPresets {
    val NAMES = listOf(
        "Ook270Async",
        "Ook650Async",
        "2FSKDev238Async",
        "2FSKDev476Async",
        "MSK99_97KbAsync",
        "GFSK9_99KbAsync",
    )
}
