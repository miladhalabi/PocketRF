#include "rf_service.h"

RfService rfService;

volatile unsigned long g_lastEdgeMicros = 0;
volatile unsigned long g_pulseDuration = 0;
volatile bool g_newPulse = false;

void IRAM_ATTR onListenPulseISR() {
    unsigned long now = micros();
    unsigned long period = now - g_lastEdgeMicros;
    g_lastEdgeMicros = now;

    if (period >= 20 && period <= 1000000UL) {
        g_pulseDuration = period;
        g_newPulse = true;
    }
}

// Confirmed empirically (not a timing/delay issue - tested delays up to
// 500ms between calls, all failed the same way): two or more
// setValue()+notify() calls made within the SAME synchronous call stack
// silently lose everything but the last one, regardless of how long a
// delay() separates them. A single notification sent in isolation (nothing
// else queued in that call chain) is delivered reliably every time - as is
// a notification that is each a direct response to its own separate
// incoming BLE write (confirmed working for CMD_TX_RAW_BEGIN/CHUNK/END,
// each one a response to a distinct write from the client). The common
// thread: what's reliable is one notification per top-level call into the
// BLE stack (one write-triggered callback, one RfService::loop() tick) -
// not how much time is spent waiting inside that same call.
//
// So a capture too large for one notification can't be sent as a tight
// begin+chunk+...+end burst from a single function call, however it's
// paced internally. Instead, `rf_queue_raw_capture()` only *prepares* the
// chunked send; `rf_pending_capture_tick()` sends exactly one message per
// call and must be invoked once per RfService::loop() iteration (a
// separate call stack each time, like the proven-reliable patterns above).
struct PendingCaptureState {
    bool active = false;
    String type;
    std::vector<int> pulses;
    float freq = 0;
    int rssi = 0;
    size_t maxPerMsg = 0;
    size_t nextChunk = 0; // 0 = send "begin" next, 1..numChunks = that chunk, numChunks+1 = send "end"
};
static PendingCaptureState g_pendingCapture;

// Reports a capture that didn't match any known protocol, including the
// actual pulse durations (not just a count) so a client can save/replay it
// as a RAW .sub-style signal - previously only `count` was sent, so a RAW
// capture's real data never reached BLE at all.
//
// A capture can have hundreds of pulses (RMT buffers up to 1000 symbols =
// up to 2000 durations), more than fits in one BLE notification. One that
// fits is sent immediately as a single message, `"part": "full"` (the
// reliable single-notification case, see above). One that doesn't is
// queued for `rf_pending_capture_tick()` to drain: `"part": "begin"`
// (declares the true total count up front), one or more `"part": "chunk"`
// (durations + their starting offset), then `"part": "end"` - mirroring
// the CMD_TX_RAW_BEGIN/CHUNK/END shape already used for the opposite
// (app->device) direction, so a client reassembles the complete capture
// instead of ever receiving a silently-truncated partial one.
//
// Only one chunked send can be in flight at a time; a new capture that
// needs chunking while an earlier one is still draining replaces it (the
// earlier, incomplete one is dropped) - consistent with this being a
// best-effort live stream, same as every other RfService notification.
static void rf_queue_raw_capture(const char *type, const std::vector<int> &pulses, float freq, int rssi) {
    size_t total = pulses.size();

    uint16_t peerMtu = bleServer.getPeerMTU();
    // ATT payload is MTU-3; reserve ~80 bytes for the other fields plus the
    // "durations":[...] wrapper and JSON punctuation.
    int budget = (int)peerMtu - 3 - 80;
    // Worst case a signed duration serializes as 7 bytes ("-32767,").
    size_t maxPerMsg = (budget > 0) ? (size_t)budget / 7 : 0;

    if (maxPerMsg > 0 && total <= maxPerMsg) {
        // Fits in one notification - send immediately, no chunking state needed.
        JsonDocument doc;
        doc["type"] = type;
        doc["part"] = "full";
        doc["count"] = total;
        doc["freq"] = freq;
        doc["rssi"] = rssi;
        JsonArray arr = doc["durations"].to<JsonArray>();
        for (int d : pulses) { arr.add(d); }
        String json;
        serializeJson(doc, json);
        bleServer.notifyText(json);
        return;
    }

    // Too large (or MTU too small for even one duration): queue a chunked
    // send for rf_pending_capture_tick() to drain one message per call.
    g_pendingCapture.active = true;
    g_pendingCapture.type = type;
    g_pendingCapture.pulses = pulses;
    g_pendingCapture.freq = freq;
    g_pendingCapture.rssi = rssi;
    g_pendingCapture.maxPerMsg = maxPerMsg; // may be 0 - begin/end still convey total_count
    g_pendingCapture.nextChunk = 0;
}

// Sends at most one message of a queued chunked capture report. Call once
// per RfService::loop() iteration regardless of RfState - this is
// independent of whatever RF operation is currently active.
static void rf_pending_capture_tick() {
    if (!g_pendingCapture.active) return;

    size_t total = g_pendingCapture.pulses.size();
    size_t numChunks = (g_pendingCapture.maxPerMsg > 0)
                            ? (total + g_pendingCapture.maxPerMsg - 1) / g_pendingCapture.maxPerMsg
                            : 0;

    JsonDocument doc;
    doc["type"] = g_pendingCapture.type;

    if (g_pendingCapture.nextChunk == 0) {
        doc["part"] = "begin";
        doc["total_count"] = total;
        doc["freq"] = g_pendingCapture.freq;
        doc["rssi"] = g_pendingCapture.rssi;
    } else if (g_pendingCapture.nextChunk <= numChunks) {
        size_t chunkIdx = g_pendingCapture.nextChunk - 1;
        size_t offset = chunkIdx * g_pendingCapture.maxPerMsg;
        size_t n = (total - offset < g_pendingCapture.maxPerMsg) ? (total - offset)
                                                                  : g_pendingCapture.maxPerMsg;
        doc["part"] = "chunk";
        doc["offset"] = offset;
        JsonArray arr = doc["durations"].to<JsonArray>();
        for (size_t i = 0; i < n; i++) { arr.add(g_pendingCapture.pulses[offset + i]); }
    } else {
        doc["part"] = "end";
        doc["total_count"] = total;
        g_pendingCapture.active = false; // this is the last message
    }

    String json;
    serializeJson(doc, json);
    bleServer.notifyText(json);
    g_pendingCapture.nextChunk++;
}

RfService::RfService() : m_state(RfState::IDLE), m_currentFreq(433.92f), m_lastListenNotify(0) {}

void RfService::init() { m_state = RfState::IDLE; }

void RfService::stop() {
    if (m_state == RfState::LISTEN) { detachInterrupt(digitalPinToInterrupt(CC1101_GDO0_PIN)); }

    if (rfSignalEngine.isRxActive()) { rfSignalEngine.stopRx(); }

    if (rfSpectrum.isActive()) { rfSpectrum.stop(); }

    if (rfJammer.isActive()) { rfJammer.stop(); }

    if (rfBruteforce.isActive()) { rfBruteforce.stop(); }

    if (rfScanner.isActive()) { rfScanner.stop(); }

    cc1101Driver.setIdle();
    m_state = RfState::IDLE;
    Serial.println("[RF SERVICE] Stopped all active RF operations.");
}

bool RfService::startRxDecoded(float freqMhz) {
    stop();
    m_currentFreq = freqMhz;
    bool ok = rfSignalEngine.startRx(freqMhz);
    if (ok) {
        m_state = RfState::RX_DECODED;
        Serial.printf("[RF SERVICE] Started RX_DECODED at %.2f MHz\n", freqMhz);
    }
    return ok;
}

bool RfService::startRxRaw(float freqMhz) {
    stop();
    m_currentFreq = freqMhz;
    bool ok = rfSignalEngine.startRx(freqMhz);
    if (ok) {
        m_state = RfState::RX_RAW;
        Serial.printf("[RF SERVICE] Started RX_RAW at %.2f MHz\n", freqMhz);
    }
    return ok;
}

bool RfService::startListen(float freqMhz) {
    stop();
    m_currentFreq = freqMhz;
    if (!cc1101Driver.setRxMode(freqMhz)) { return false; }

    g_lastEdgeMicros = micros();
    g_newPulse = false;
    pinMode(CC1101_GDO0_PIN, INPUT);
    attachInterrupt(digitalPinToInterrupt(CC1101_GDO0_PIN), onListenPulseISR, RISING);

    m_state = RfState::LISTEN;
    m_lastListenNotify = millis();
    Serial.printf("[RF SERVICE] Started Frequency Listener at %.2f MHz\n", freqMhz);
    return true;
}

bool RfService::startSpectrumFixed(float freqMhz, uint16_t intervalMs) {
    stop();
    m_currentFreq = freqMhz;
    bool ok = rfSpectrum.startFixedRssi(freqMhz, intervalMs);
    if (ok) { m_state = RfState::SPECTRUM_FIXED; }
    return ok;
}

bool RfService::startSpectrumSweep(float startFreq, float endFreq, float stepMhz, uint16_t intervalMs) {
    stop();
    bool ok = rfSpectrum.startSweep(startFreq, endFreq, stepMhz, intervalMs);
    if (ok) { m_state = RfState::SPECTRUM_SWEEP; }
    return ok;
}

bool RfService::startJam(RfJamMode mode, float freqMhz) {
    stop();
    bool ok = rfJammer.begin(mode, freqMhz);
    if (ok) { m_state = RfState::JAM; }
    return ok;
}

bool RfService::startBruteforce(const RfProtocolDef *def, int bits, float freqMhz, int repeat) {
    stop();
    bool ok = rfBruteforce.begin(def, bits, freqMhz, repeat);
    if (ok) { m_state = RfState::BRUTEFORCE; }
    return ok;
}

bool RfService::startScan(int rssiThresholdDbm) {
    stop();
    bool ok = rfScanner.begin(rssiThresholdDbm);
    if (ok) { m_state = RfState::SCAN; }
    return ok;
}

void RfService::loop() {
    // Drains at most one message of a queued chunked capture report per
    // call, independent of the current RfState - see rf_pending_capture_tick().
    rf_pending_capture_tick();

    switch (m_state) {
        case RfState::IDLE: break;

        case RfState::RX_DECODED:
        case RfState::RX_RAW: {
            if (rfSignalEngine.isRxActive()) {
                std::vector<int> capturedPulses;
                if (rfSignalEngine.pollRxDurations(capturedPulses, 0)) {
                    RfCodes decoded;
                    bool isDecoded = false;

                    if (m_state == RfState::RX_DECODED) {
                        // Try KeeLoq first (matches Bruce's rf_scan.cpp order): its 64-bit
                        // frame structure is strict enough to check cheaply before falling
                        // back to the looser OOK registry match.
                        if (rf_try_keeloq(capturedPulses, decoded)) {
                            isDecoded = true;
                        } else if (rf_decode_ook(capturedPulses, decoded)) {
                            isDecoded = true;
                        }
                    }

                    if (isDecoded) {
                        JsonDocument pulseDoc;
                        pulseDoc["type"] = "decoded_rf";
                        pulseDoc["protocol"] = decoded.protocol;
                        pulseDoc["bits"] = decoded.Bit;
                        pulseDoc["key"] = String((unsigned long long)decoded.key, HEX);
                        pulseDoc["te"] = decoded.te;
                        pulseDoc["freq"] = cc1101Driver.getFrequency();
                        pulseDoc["rssi"] = cc1101Driver.getRssi();
                        if (decoded.protocol == "KeeLoq") {
                            pulseDoc["mf_name"] = decoded.mf_name;
                            pulseDoc["btn"] = decoded.btn;
                            pulseDoc["serial"] = decoded.serial;
                            if (decoded.mf_name != "Generic KeeLoq") pulseDoc["cnt"] = decoded.cnt;
                        }
                        String pulseJson;
                        serializeJson(pulseDoc, pulseJson);
                        bleServer.notifyText(pulseJson);
                    } else {
                        rf_queue_raw_capture(
                            "raw_pulse", capturedPulses, cc1101Driver.getFrequency(), cc1101Driver.getRssi()
                        );
                    }
                }
            }
            break;
        }

        case RfState::LISTEN: {
            if (g_newPulse) {
                g_newPulse = false;
                unsigned long dur = g_pulseDuration;
                float freqHz = dur ? (1000000.0f / dur) : 0.0f;

                if (millis() - m_lastListenNotify > 200) {
                    m_lastListenNotify = millis();

                    JsonDocument doc;
                    doc["type"] = "listen_pulse";
                    doc["center_freq"] = cc1101Driver.getFrequency();
                    doc["pulse_dur_us"] = dur;
                    doc["detected_freq_hz"] = freqHz;
                    doc["rssi"] = cc1101Driver.getRssi();

                    String json;
                    serializeJson(doc, json);
                    bleServer.notifyText(json);
                }
            }
            break;
        }

        case RfState::SPECTRUM_FIXED:
        case RfState::SPECTRUM_SWEEP: {
            rfSpectrum.loop();
            break;
        }

        case RfState::JAM: {
            if (!rfJammer.tick()) {
                // Safety cutoff reached or jammer inactive: stop and notify.
                JsonDocument doc;
                doc["type"] = "jam_done";
                doc["reason"] = "timeout";
                String json;
                serializeJson(doc, json);
                bleServer.notifyText(json);
                stop();
            }
            break;
        }

        case RfState::BRUTEFORCE: {
            if (!rfBruteforce.tick()) {
                JsonDocument doc;
                doc["type"] = "bruteforce_done";
                String json;
                serializeJson(doc, json);
                bleServer.notifyText(json);
                stop();
            }
            break;
        }

        case RfState::SCAN: {
            if (!rfScanner.isActive()) {
                stop();
                break;
            }
            if (!rfScanner.isLocked()) {
                rfScanner.tick();
                if (rfScanner.isLocked()) {
                    JsonDocument doc;
                    doc["type"] = "scan_locked";
                    doc["freq"] = rfScanner.getLockedFreq();
                    String json;
                    serializeJson(doc, json);
                    bleServer.notifyText(json);
                }
                break;
            }

            // Locked: behave like RX_DECODED (KeeLoq first, then OOK registry),
            // but stay in SCAN rather than stopping after one capture - matches
            // Bruce's "lock onto the found frequency and keep receiving" model.
            if (rfSignalEngine.isRxActive()) {
                std::vector<int> capturedPulses;
                if (rfSignalEngine.pollRxDurations(capturedPulses, 0)) {
                    RfCodes decoded;
                    bool isDecoded =
                        rf_try_keeloq(capturedPulses, decoded) || rf_decode_ook(capturedPulses, decoded);

                    if (isDecoded) {
                        JsonDocument doc;
                        doc["type"] = "scan_decoded";
                        doc["protocol"] = decoded.protocol;
                        doc["bits"] = decoded.Bit;
                        doc["key"] = String((unsigned long long)decoded.key, HEX);
                        doc["te"] = decoded.te;
                        doc["freq"] = rfScanner.getLockedFreq();
                        doc["rssi"] = cc1101Driver.getRssi();
                        if (decoded.protocol == "KeeLoq") {
                            doc["mf_name"] = decoded.mf_name;
                            doc["btn"] = decoded.btn;
                            doc["serial"] = decoded.serial;
                            if (decoded.mf_name != "Generic KeeLoq") doc["cnt"] = decoded.cnt;
                        }
                        String json;
                        serializeJson(doc, json);
                        bleServer.notifyText(json);
                    } else {
                        // Previously dropped silently - Scan/Copy's RAW fallback (a
                        // locked-on signal that doesn't match a known protocol) now
                        // reports the same way RX_DECODED's raw_pulse does.
                        rf_queue_raw_capture(
                            "scan_raw", capturedPulses, rfScanner.getLockedFreq(), cc1101Driver.getRssi()
                        );
                    }
                }
            }
            break;
        }
    }
}
