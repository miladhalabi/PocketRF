package com.pocketrf.ble.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * GATT profile + binary framing for PocketRF, mirrored from
 * headless_cc1101_ble/BLE_PROTOCOL.md. Keep this file in sync with that doc.
 */
object PocketRf {
    const val DEVICE_NAME = "XIAO-RF-BLE"

    val SERVICE_UUID: UUID = UUID.fromString("0000cc11-0000-1000-8000-00805f9b34fb")
    val RX_CHAR_UUID: UUID = UUID.fromString("0000cc12-0000-1000-8000-00805f9b34fb") // app -> device
    val TX_CHAR_UUID: UUID = UUID.fromString("0000cc13-0000-1000-8000-00805f9b34fb") // device -> app
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val PREFERRED_MTU = 512
    const val FRAME_START: Byte = 0xAA.toByte()

    object Cmd {
        const val STATUS: Byte = 0x01
        const val SET_FREQ: Byte = 0x02
        const val START_RX: Byte = 0x03
        const val STOP_RX: Byte = 0x04
        const val TX_PROTO: Byte = 0x05
        const val TX_KEELOQ: Byte = 0x06
        const val TX_RAW: Byte = 0x07
        const val START_LISTEN: Byte = 0x08
        const val STOP_ALL: Byte = 0x09
        const val START_SPECTRUM_FIXED: Byte = 0x0A
        const val START_SPECTRUM_SWEEP: Byte = 0x0B
        const val START_JAM: Byte = 0x0C
        const val START_BRUTEFORCE: Byte = 0x0D
        const val START_SCAN: Byte = 0x0E
        const val TX_RAW_BEGIN: Byte = 0x0F
        const val TX_RAW_CHUNK: Byte = 0x10
        const val TX_RAW_END: Byte = 0x11
    }

    /** Server-side cap on a chunked RAW transfer, matching the RMT capture buffer's own limit (BLE_PROTOCOL.md §4.3.1). */
    const val RAW_CHUNK_MAX_DURATIONS = 2000

    /** Response byte for the simple ack-style commands. */
    object Ack {
        const val OK: Int = 0x00
        const val FAILED: Int = 0x01
        const val BAD_PAYLOAD: Int = 0xFF
        const val UNKNOWN_COMMAND: Int = 0xFE
    }

    object JamMode {
        const val FULL: Byte = 0
        const val ITMT: Byte = 1
        const val NOISE: Byte = 2
        const val SWEEP: Byte = 3
    }
}

/** Dallas/Maxim-style bitwise CRC8: poly 0x31, init 0x00, MSB-first, no reflection, no final XOR. */
fun crc8(data: ByteArray): Byte {
    var crc = 0
    for (b in data) {
        crc = crc xor (b.toInt() and 0xFF)
        repeat(8) {
            crc = if (crc and 0x80 != 0) {
                ((crc shl 1) xor 0x31) and 0xFF
            } else {
                (crc shl 1) and 0xFF
            }
        }
    }
    return crc.toByte()
}

/**
 * Builds a binary request frame: [0xAA][cmd][len][payload...][crc8].
 * CRC8 covers everything from 0xAA through the last payload byte.
 */
fun buildBinaryFrame(cmd: Byte, payload: ByteArray = ByteArray(0)): ByteArray {
    require(payload.size <= 0xFF) { "payload too large for a single-byte length field" }
    val header = ByteArray(3 + payload.size)
    header[0] = PocketRf.FRAME_START
    header[1] = cmd
    header[2] = payload.size.toByte()
    payload.copyInto(header, destinationOffset = 3)
    val crc = crc8(header)
    return header + crc
}

/** A decoded binary response frame: [0xAA][cmd|0x80][len][payload...][crc8]. */
data class BinaryResponse(
    val cmdEcho: Int,
    val payload: ByteArray,
    val crcOk: Boolean,
)

/** Parses a binary response, verifying length and CRC8. Returns null if the frame is malformed. */
fun parseBinaryFrame(bytes: ByteArray): BinaryResponse? {
    if (bytes.size < 4) return null
    if (bytes[0] != PocketRf.FRAME_START) return null
    val cmdEcho = bytes[1].toInt() and 0xFF
    val len = bytes[2].toInt() and 0xFF
    if (bytes.size != 3 + len + 1) return null
    val payload = bytes.copyOfRange(3, 3 + len)
    val expectedCrc = crc8(bytes.copyOfRange(0, 3 + len))
    val crcOk = expectedCrc == bytes[3 + len]
    return BinaryResponse(cmdEcho, payload, crcOk)
}

/** Decoded `CMD_STATUS` (0x01) response payload — 10 bytes, little-endian. */
data class RfStatus(
    val detected: Boolean,
    val version: Int,
    val partnum: Int,
    val marcstate: Int,
    val freqHz: Long,
    val rssi: Int,
    val rxActive: Boolean,
) {
    /** See BLE_PROTOCOL.md §3.2 caveat: version>0 alone doesn't prove a real chip is wired. */
    val looksGenuine: Boolean get() = partnum == 0 && version != 0xFF

    companion object {
        fun fromPayload(payload: ByteArray): RfStatus? {
            if (payload.size != 10) return null
            val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
            val detected = buf.get().toInt() and 0xFF
            val version = buf.get().toInt() and 0xFF
            val partnum = buf.get().toInt() and 0xFF
            val marcstate = buf.get().toInt() and 0xFF
            val freqHz = buf.int.toLong() and 0xFFFFFFFFL
            val rssi = buf.get().toInt() // signed i8
            val rxActive = buf.get().toInt() and 0xFF
            return RfStatus(
                detected = detected != 0,
                version = version,
                partnum = partnum,
                marcstate = marcstate,
                freqHz = freqHz,
                rssi = rssi,
                rxActive = rxActive != 0,
            )
        }
    }
}
