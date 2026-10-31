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

                    JsonDocument pulseDoc;
                    if (isDecoded) {
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
                    } else {
                        pulseDoc["type"] = "raw_pulse";
                        pulseDoc["count"] = capturedPulses.size();
                        pulseDoc["freq"] = cc1101Driver.getFrequency();
                        pulseDoc["rssi"] = cc1101Driver.getRssi();
                    }

                    String pulseJson;
                    serializeJson(pulseDoc, pulseJson);
                    bleServer.notifyText(pulseJson);
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
                    }
                }
            }
            break;
        }
    }
}
