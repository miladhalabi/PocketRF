#include "rf_spectrum.h"

RfSpectrum rfSpectrum;

// The CC1101 can only tune within 3 discrete sub-bands (roughly 280-350,
// 387-468, 779-928 MHz - the exact bounds CC1101Driver::setFrequency()
// validates against); it physically cannot cover 350-387 or 468-779. A
// linear sweep that doesn't account for this lands on those gap
// frequencies, where setFrequency() silently falls back to 433.92 MHz -
// corrupting the sweep with repeated bogus 433.92 MHz readings instead of
// real data for a large fraction of a 300-928 MHz pass. This jumps straight
// to the next valid band instead.
static float rf_skip_invalid_gap(float freq) {
    if (freq < 280.0f) return 280.0f;
    if (freq > 350.0f && freq < 387.0f) return 387.0f;
    if (freq > 468.0f && freq < 779.0f) return 779.0f;
    return freq;
}

// CC1101Driver::getRssi() can legitimately return down to -138dBm (the raw
// RSSI register's weakest-signal floor, routinely hit while sweeping over
// empty spectrum - not just on a disconnected chip). int8_t's range is only
// -128..127, and a plain `(int8_t)rssi` cast does NOT clamp out-of-range
// values - it wraps via truncating reinterpretation, so -138 silently
// became +118 in the sweep buffer. Clamping instead of wrapping loses no
// meaningful information (anything beyond -128 is already "very weak/no
// signal" territory) while fixing the wraparound entirely.
static int8_t rf_clamp_rssi_i8(int rssi) {
    if (rssi > 127) return 127;
    if (rssi < -128) return -128;
    return (int8_t)rssi;
}

// Explicit settle window between "radio just entered RX at the new
// frequency" and "read RSSI", instead of relying on incidental timing from
// the main-loop cadence and an interval gate that calibration's own
// (much longer, ~tens of ms) blocking time had usually already consumed by
// the time the next step ran.
static const uint32_t RF_SWEEP_SETTLE_US = 2000;

// With quickRetuneForRx() instead of the full setRxMode(), one sweep step
// only costs ~2-3ms - but doing exactly one step per loop() call still left
// the *outer* scheduling (main.cpp's own delay(10) cadence, plus the old
// per-step interval gate) as the real bottleneck: a ~280-step pass still
// took several seconds, just from how often loop() itself gets called,
// not from the RF work anymore. A real transmission that only lasts a
// second or two could easily be missed entirely in that gap. Processing
// several steps per call (bounded by a time budget, not a step count, so
// it adapts if a step is ever slower than expected) amortizes that outer
// overhead across many steps instead of paying it once per step.
static const uint32_t RF_SWEEP_BUDGET_MS = 20;

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
    m_currSweepFreq = rf_skip_invalid_gap(m_startFreq);
    m_intervalMs = stepIntervalMs;

    if (!cc1101Driver.isDetected()) {
        Serial.println("[SPECTRUM] Failed to start sweep: CC1101 not detected.");
        return false;
    }

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

// Builds and sends one completed sweep pass, keeping the result inside the
// negotiated BLE MTU. A full pass can have ~280+ samples at the default
// 1MHz step (after the 2 band gaps are skipped) - at up to 5 JSON chars
// each ("-128,") that's well over even a fully-negotiated 512-byte MTU, and
// the old code sent it as one unconditional notifyText() call regardless,
// which the BLE stack would truncate/drop rather than deliver intact.
// Downsampling (keep every Nth sample, spanning the full requested range at
// reduced resolution) is used instead of cutting off the tail, since a
// partial-band result is far less useful for a spectrum overview than a
// full-band one at lower resolution - and the reported step_mhz is adjusted
// to match, so a client reconstructing frequency-per-sample stays correct.
void RfSpectrum::sendSweepResult() {
    JsonDocument doc;
    doc["type"] = "spectrum_sweep";
    doc["start_freq"] = m_startFreq;
    doc["end_freq"] = m_endFreq;

    size_t total = m_sweepRssiBuffer.size();
    uint16_t peerMtu = bleServer.getPeerMTU();
    int budget = (int)peerMtu - 3 - 80; // reserve ~80 bytes for the other fields/wrapper

    size_t maxSamples = 0;
    if (budget > 0) {
        // Worst case a signed int8 serializes as 5 bytes ("-128,").
        maxSamples = (size_t)budget / 5;
    }

    bool decimated = false;
    float effectiveStep = m_stepFreq;
    JsonArray rssiArray = doc["rssi"].to<JsonArray>();

    if (maxSamples == 0) {
        // MTU too small to carry any samples at all.
        decimated = (total > 0);
    } else if (total <= maxSamples) {
        for (int8_t v : m_sweepRssiBuffer) { rssiArray.add(v); }
    } else {
        decimated = true;
        size_t factor = (total + maxSamples - 1) / maxSamples; // ceil
        effectiveStep = m_stepFreq * (float)factor;
        for (size_t i = 0; i < total; i += factor) { rssiArray.add(m_sweepRssiBuffer[i]); }
    }

    doc["step_mhz"] = effectiveStep;
    doc["decimated"] = decimated;
    doc["count"] = rssiArray.size();

    String json;
    serializeJson(doc, json);
    bleServer.notifyText(json);
}

void RfSpectrum::loop() {
    if (m_mode == SpectrumMode::OFF) return;

    if (m_mode == SpectrumMode::FIXED_RSSI) {
        unsigned long now = millis();
        if (now - m_lastSampleTime < m_intervalMs) return;
        m_lastSampleTime = now;

        int rssi = cc1101Driver.getRssi();
        JsonDocument doc;
        doc["type"] = "spectrum_fixed";
        doc["freq"] = m_centerFreq;
        doc["rssi"] = rssi;

        String json;
        serializeJson(doc, json);
        bleServer.notifyText(json);
    } else if (m_mode == SpectrumMode::SWEEP_SW) {
        // Process steps in a bounded burst instead of one per call (see
        // RF_SWEEP_BUDGET_MS) - no interval gate here, the per-step RF work
        // itself is the pacing now.
        unsigned long budgetStart = millis();
        while (millis() - budgetStart < RF_SWEEP_BUDGET_MS) {
            // Tune, settle, and read all within this one step - instead of
            // reading RSSI based on whatever tuning a *previous* step's
            // call happened to leave behind, with no guaranteed minimum
            // settle time between "entered RX" and "read RSSI". Uses
            // quickRetuneForRx() (SIDLE -> setMHZ -> SetRx(), relying on
            // MCSM0's hardware auto-calibration) rather than the full
            // setRxMode() - a sweep is hundreds of RSSI-only probes, not a
            // decode session, and paying setRxMode()'s full
            // manual-calibration + warm-up-pulse cost on every single step
            // made a full pass take seconds.
            cc1101Driver.quickRetuneForRx(m_currSweepFreq);
            delayMicroseconds(RF_SWEEP_SETTLE_US);
            int8_t rssi = rf_clamp_rssi_i8(cc1101Driver.getRssi());
            m_sweepRssiBuffer.push_back(rssi);

            m_currSweepFreq += m_stepFreq;
            m_currSweepFreq = rf_skip_invalid_gap(m_currSweepFreq);
            if (m_currSweepFreq > m_endFreq + 0.001f) {
                // Completed one sweep pass: send it and yield back to the
                // main loop immediately rather than starting the next pass
                // in the same burst, so a stop/other command in flight gets
                // serviced promptly between passes.
                sendSweepResult();
                m_sweepRssiBuffer.clear();
                m_currSweepFreq = rf_skip_invalid_gap(m_startFreq);
                break;
            }
        }
    }
}
