#include "rf_spectrum.h"

RfSpectrum rfSpectrum;

RfSpectrum::RfSpectrum()
    : m_mode(SpectrumMode::OFF), m_centerFreq(433.92f), m_startFreq(300.0f), m_endFreq(928.0f),
      m_stepFreq(1.0f), m_currSweepFreq(300.0f), m_intervalMs(50), m_lastSampleTime(0) {}

bool RfSpectrum::startFixedRssi(float freqMhz, uint16_t sampleIntervalMs) {
    stop();
    m_centerFreq = freqMhz;
    m_intervalMs = sampleIntervalMs;

    if (!cc1101Driver.setRxMode(freqMhz)) { return false; }

    m_mode = SpectrumMode::FIXED_RSSI;
    m_lastSampleTime = millis();
    Serial.printf(
        "[SPECTRUM] Started Fixed RSSI stream at %.2f MHz (Interval: %u ms)\n", freqMhz, sampleIntervalMs
    );
    return true;
}

bool RfSpectrum::startSweep(float startFreqMhz, float endFreqMhz, float stepMhz, uint16_t stepIntervalMs) {
    stop();
    m_startFreq = startFreqMhz;
    m_endFreq = endFreqMhz;
    m_stepFreq = (stepMhz > 0.01f) ? stepMhz : 0.5f;
    m_currSweepFreq = m_startFreq;
    m_intervalMs = stepIntervalMs;

    if (!cc1101Driver.setRxMode(m_currSweepFreq)) { return false; }

    m_sweepRssiBuffer.clear();
    m_mode = SpectrumMode::SWEEP_SW;
    m_lastSampleTime = millis();
    Serial.printf(
        "[SPECTRUM] Started SW Sweep %.2f - %.2f MHz (Step: %.2f MHz)\n", startFreqMhz, endFreqMhz, m_stepFreq
    );
    return true;
}

void RfSpectrum::stop() {
    if (m_mode != SpectrumMode::OFF) {
        cc1101Driver.setIdle();
        m_mode = SpectrumMode::OFF;
        m_sweepRssiBuffer.clear();
        Serial.println("[SPECTRUM] Stopped spectrum analyzer session.");
    }
}

void RfSpectrum::loop() {
    if (m_mode == SpectrumMode::OFF) return;

    unsigned long now = millis();
    if (now - m_lastSampleTime < m_intervalMs) return;
    m_lastSampleTime = now;

    if (m_mode == SpectrumMode::FIXED_RSSI) {
        int rssi = cc1101Driver.getRssi();
        JsonDocument doc;
        doc["type"] = "spectrum_fixed";
        doc["freq"] = m_centerFreq;
        doc["rssi"] = rssi;

        String json;
        serializeJson(doc, json);
        bleServer.notifyText(json);
    } else if (m_mode == SpectrumMode::SWEEP_SW) {
        int rssi = cc1101Driver.getRssi();
        m_sweepRssiBuffer.push_back((int8_t)rssi);

        m_currSweepFreq += m_stepFreq;
        if (m_currSweepFreq > m_endFreq + 0.001f) {
            // Completed one sweep pass across frequency range
            JsonDocument doc;
            doc["type"] = "spectrum_sweep";
            doc["start_freq"] = m_startFreq;
            doc["end_freq"] = m_endFreq;
            doc["step_mhz"] = m_stepFreq;
            doc["count"] = m_sweepRssiBuffer.size();

            JsonArray rssiArray = doc["rssi"].to<JsonArray>();
            for (int8_t val : m_sweepRssiBuffer) { rssiArray.add(val); }

            String json;
            serializeJson(doc, json);
            bleServer.notifyText(json);

            // Reset sweep for next pass
            m_sweepRssiBuffer.clear();
            m_currSweepFreq = m_startFreq;
        }

        // Tune CC1101 to next channel
        cc1101Driver.setFrequency(m_currSweepFreq, false);
    }
}
