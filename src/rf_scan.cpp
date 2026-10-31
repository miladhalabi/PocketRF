#include "rf_scan.h"
#include "cc1101_driver.h"
#include "rf_signal_engine.h"

RfScanner rfScanner;

// Common ISM-band frequencies across all 3 CC1101 ranges, ported verbatim
// from Bruce's rf_utils.cpp subghz_frequency_list[]. PocketRF has no
// range-selection menu, so the scanner always sweeps the full list ("All
// ranges" in Bruce's terms), rather than letting the user narrow it first.
static const float subghz_frequency_list[] = {
    /* 300 - 348 MHz */
    300.000f, 302.757f, 303.875f, 303.900f, 304.250f, 307.000f, 307.500f, 307.800f, 309.000f, 310.000f,
    312.000f, 312.100f, 312.200f, 313.000f, 313.850f, 314.000f, 314.350f, 314.980f, 315.000f, 318.000f,
    330.000f, 345.000f, 348.000f, 350.000f,
    /* 387 - 464 MHz */
    387.000f, 390.000f, 418.000f, 430.000f, 430.500f, 431.000f, 431.500f, 433.075f, 433.220f, 433.420f,
    433.657f, 433.889f, 433.920f, 434.075f, 434.177f, 434.190f, 434.390f, 434.420f, 434.620f, 434.775f,
    438.900f, 440.175f, 464.000f, 467.750f,
    /* 779 - 928 MHz */
    779.000f, 868.350f, 868.400f, 868.800f, 868.950f, 906.400f, 915.000f, 925.000f, 928.000f,
};
static const int subghz_frequency_count = sizeof(subghz_frequency_list) / sizeof(subghz_frequency_list[0]);

RfScanner::RfScanner() {}

bool RfScanner::begin(int rssiThresholdDbm) {
    stop();
    m_idx = 0;
    m_try = 0;
    m_locked = false;
    m_lockedFreq = 0.f;
    m_rssiThreshold = rssiThresholdDbm;
    for (auto &f : m_freqs) {
        f.freq = 433.92f;
        f.rssi = -75;
    }

    if (!cc1101Driver.setRxMode(subghz_frequency_list[0])) {
        Serial.println("[RF SCAN] Failed to configure CC1101 RX mode!");
        return false;
    }

    m_active = true;
    Serial.printf(
        "[RF SCAN] Started: sweeping %d frequencies (300-928 MHz) for RSSI > %d dBm\n",
        subghz_frequency_count,
        m_rssiThreshold
    );
    return true;
}

void RfScanner::stop() {
    if (!m_active) return;
    if (m_locked) rfSignalEngine.stopRx();
    cc1101Driver.setIdle();
    m_active = false;
    m_locked = false;
    Serial.println("[RF SCAN] Stopped.");
}

void RfScanner::lockOnto(float freqMhz) {
    m_locked = true;
    m_lockedFreq = freqMhz;
    Serial.printf("[RF SCAN] Frequency found: %.3f MHz. Locking on and starting RX...\n", freqMhz);
    rfSignalEngine.startRx(freqMhz);
}

bool RfScanner::fastScanStep() {
    if (m_idx < 0 || m_idx >= subghz_frequency_count) m_idx = 0;
    float checkFreq = subghz_frequency_list[m_idx];

    cc1101Driver.quickTune(checkFreq);
    delay(5); // let RSSI settle, matches Bruce's fast_scan() vTaskDelay(5)
    int rssi = cc1101Driver.getRssi();

    if (rssi > m_rssiThreshold) {
        m_freqs[m_try].freq = checkFreq;
        m_freqs[m_try].rssi = rssi;
        m_try++;
        if (m_try >= (int)(sizeof(m_freqs) / sizeof(m_freqs[0]))) {
            int maxIdx = 0;
            for (int i = 1; i < m_try; i++) {
                if (m_freqs[i].rssi > m_freqs[maxIdx].rssi) maxIdx = i;
            }
            lockOnto(m_freqs[maxIdx].freq);
            return true;
        }
    }
    m_idx++;
    return false;
}

bool RfScanner::tick() {
    if (!m_active) return false;
    if (m_locked) return true; // RfService handles capture polling once locked
    fastScanStep();
    return true;
}
