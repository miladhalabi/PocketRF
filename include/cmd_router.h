#ifndef CMD_ROUTER_H
#define CMD_ROUTER_H

#include "cc1101_driver.h"
#include "rf_bruteforce.h"
#include "rf_decoder.h"
#include "rf_encoder.h"
#include "rf_jammer.h"
#include "rf_keeloq.h"
#include "rf_presets.h"
#include "rf_raw.h"
#include "rf_registry.h"
#include "rf_service.h"
#include "rf_signal_engine.h"
#include <Arduino.h>
#include <ArduinoJson.h>
#include <vector>

#define BINARY_FRAME_HEADER 0xAA

enum BinaryCmdId : uint8_t {
    CMD_STATUS = 0x01,
    CMD_SET_FREQ = 0x02,
    CMD_START_RX = 0x03,
    CMD_STOP_RX = 0x04,
    CMD_TX_PROTO = 0x05,
    CMD_TX_KEELOQ = 0x06,
    CMD_TX_RAW = 0x07,
    CMD_START_LISTEN = 0x08,
    CMD_STOP_ALL = 0x09,
    CMD_START_SPECTRUM_FIXED = 0x0A,
    CMD_START_SPECTRUM_SWEEP = 0x0B,
    CMD_START_JAM = 0x0C,
    CMD_START_BRUTEFORCE = 0x0D,
    CMD_START_SCAN = 0x0E,
    // Chunked RAW TX: for a duration list too long to fit CMD_TX_RAW's
    // single-write payload. BEGIN declares the frequency and total count
    // and resets the accumulation buffer; one or more CHUNKs append
    // durations to it; END transmits the fully-accumulated list (same
    // semantics/response as CMD_TX_RAW) and clears the buffer. See
    // BLE_PROTOCOL.md's chunked-transfer section for the wire format.
    CMD_TX_RAW_BEGIN = 0x0F,
    CMD_TX_RAW_CHUNK = 0x10,
    CMD_TX_RAW_END = 0x11,
};

// RMT can capture/replay at most this many durations (matches
// rf_signal_engine's default 1000-symbol buffer = up to 2000 durations);
// caps a declared CMD_TX_RAW_BEGIN total so a malformed/malicious client
// can't make the firmware allocate an unbounded accumulation buffer.
#define RAW_CHUNK_MAX_DURATIONS 2000

class CmdRouter {
public:
    CmdRouter();

    void processInput(const uint8_t *data, size_t length);

private:
    void processBinaryFrame(const uint8_t *data, size_t length);
    void processAsciiCommand(const String &cmdStr);
    void processRawSubCommand(const String &subText);
    uint8_t calculateCrc8(const uint8_t *data, size_t len);

    // Chunked RAW TX accumulation state, shared by the binary
    // CMD_TX_RAW_BEGIN/CHUNK/END commands and their ASCII equivalents
    // (subghz rawtx_begin/rawtx_chunk/rawtx_end).
    std::vector<int> m_rawChunkBuffer;
    float m_rawChunkFreq = 0.0f;
    size_t m_rawChunkExpected = 0;
    bool m_rawChunkActive = false;

    void rawChunkBegin(float freqMhz, size_t totalCount);
    void rawChunkAppend(const std::vector<int> &durations);
    bool rawChunkFinish(); // transmits + clears state; returns success
};

extern CmdRouter cmdRouter;

#endif // CMD_ROUTER_H
