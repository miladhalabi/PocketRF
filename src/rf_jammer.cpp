#include "rf_jammer.h"
#include "app_config.h"
#include "cc1101_driver.h"
#include <ELECHOUSE_CC1101_SRC_DRV.h>

RFJammer rfJammer;

// Same 30s hard safety cutoff as Bruce's rf_jammer.cpp (MAX_JAMMER_RUNTIME).
#define MAX_JAMMER_RUNTIME 30000UL

static const char *JAM_MODE_NAMES[] = {"FULL", "ITMT", "NOISE", "SWEEP"};

const char *rf_jam_mode_name(RfJamMode m) { return JAM_MODE_NAMES[(uint8_t)m]; }

RFJammer::RFJammer() {}

bool RFJammer::begin(RfJamMode mode, float freqMhz) {
    stop();
    m_mode = mode;
    m_txPin = CC1101_GDO0_PIN;
    m_pulseCount = 0;
    m_sweepCount = 0;
    m_fullPhase = 0;
    m_itmtPhase = ItmtPhase::FORWARD;
    m_itmtSeq = 0;
    m_itmtCycle = 0;
    m_modCycle = 0;

    if (!cc1101Driver.setTxMode(freqMhz)) {
        Serial.println("[JAMMER] Failed to configure CC1101 TX mode!");
        return false;
    }

    if (mode == RfJamMode::NOISE) {
        // CC1101 PN9 hardware random TX, matching Bruce's run_noise_jammer().
        ELECHOUSE_cc1101.setSidle();
        ELECHOUSE_cc1101.setPktFormat(2); // PN9 random TX mode
        ELECHOUSE_cc1101.setDRate(800);
        ELECHOUSE_cc1101.setModulation(2); // start ASK/OOK
        ELECHOUSE_cc1101.setDeviation(47.6);
        ELECHOUSE_cc1101.setRxBW(812);
        ELECHOUSE_cc1101.setPA(12);
        ELECHOUSE_cc1101.SetTx();
    } else if (mode == RfJamMode::SWEEP) {
        m_sweepBase = freqMhz;
        m_sweepMin = freqMhz - 5.0f;
        m_sweepMax = freqMhz + 5.0f;
        m_sweepFreq = m_sweepMin;
        m_sweepForward = true;
        ELECHOUSE_cc1101.setPA(12);
        cc1101Driver.quickTune(m_sweepFreq);
        pinMode(m_txPin, OUTPUT);
        digitalWrite(m_txPin, HIGH);
    } else {
        pinMode(m_txPin, OUTPUT);
    }

    m_active = true;
    m_startTime = millis();
    m_lastModCycleMs = m_startTime;
    Serial.printf(
        "[JAMMER] Started mode=%s freq=%.2f MHz (safety cutoff %lus)\n",
        JAM_MODE_NAMES[(uint8_t)mode],
        freqMhz,
        MAX_JAMMER_RUNTIME / 1000UL
    );
    return true;
}

void RFJammer::stop() {
    if (!m_active) return;
    if (m_txPin >= 0) digitalWrite(m_txPin, LOW);
    if (m_mode == RfJamMode::NOISE) ELECHOUSE_cc1101.setPktFormat(3); // restore async serial mode
    if (m_mode == RfJamMode::SWEEP) cc1101Driver.quickTune(m_sweepBase);
    cc1101Driver.setIdle();
    m_active = false;
    Serial.println("[JAMMER] Stopped.");
}

bool RFJammer::tick() {
    if (!m_active) return false;

    unsigned long elapsed = millis() - m_startTime;
    if (elapsed > MAX_JAMMER_RUNTIME) {
        Serial.println("[JAMMER] Safety cutoff reached (30s max runtime).");
        return false;
    }

    switch (m_mode) {
        case RfJamMode::FULL: tickFull(); break;
        case RfJamMode::ITMT: tickItmt(); break;
        case RfJamMode::NOISE: tickNoise(); break;
        case RfJamMode::SWEEP: tickSweep(); break;
    }
    return true;
}

// ---------------------------------------------------------------------------
// FULL: ported from Bruce's run_full_jammer(). Phase rotates every 100ms
// across 3 attack patterns. One call = one phase's worth of pulses (bounded
// to roughly 0.1-1ms), so this is safe to call once per main-loop tick.
// ---------------------------------------------------------------------------
void RFJammer::tickFull() {
    uint8_t phase = ((millis() - m_startTime) / 100) % 3;
    m_fullPhase = phase;

    switch (phase) {
        case 0: // Ultra-rapid micro-glitches (3us HIGH / 1us LOW)
            for (int i = 0; i < 100; i++) {
                digitalWrite(m_txPin, HIGH);
                delayMicroseconds(3);
                digitalWrite(m_txPin, LOW);
                delayMicroseconds(1);
                m_pulseCount++;
            }
            break;
        case 1: // Variable-width burst disruption (2-20us pulses)
            for (int i = 0; i < 50; i++) {
                uint32_t w = 2 + (micros() % 18);
                digitalWrite(m_txPin, HIGH);
                delayMicroseconds(w);
                digitalWrite(m_txPin, LOW);
                delayMicroseconds(1);
                m_pulseCount++;
            }
            break;
        case 2: // Sustained carrier with periodic hard cuts
            digitalWrite(m_txPin, HIGH);
            delayMicroseconds(80);
            digitalWrite(m_txPin, LOW);
            delayMicroseconds(2);
            digitalWrite(m_txPin, HIGH);
            delayMicroseconds(80);
            m_pulseCount += 2;
            break;
    }
}

// ---------------------------------------------------------------------------
// ITMT: ported from Bruce's run_itmt_jammer(). Forward sweep 10->500us,
// reverse sweep 500->10us, then a 200-pulse random noise burst, repeating.
// Each tick() call advances exactly one step of whichever phase is active.
// ---------------------------------------------------------------------------
void RFJammer::tickItmt() {
    static const uint32_t MAX_SEQUENCE = 50;
    static const uint32_t DURATION_CYCLES = 3;

    switch (m_itmtPhase) {
        case ItmtPhase::FORWARD: {
            uint32_t pulseWidth = 10 * (m_itmtSeq + 1);
            sendOptimizedPulse(pulseWidth);
            m_pulseCount++;
            m_itmtCycle++;
            if (m_itmtCycle >= (int)DURATION_CYCLES) {
                m_itmtCycle = 0;
                m_itmtSeq++;
                if (m_itmtSeq >= (int)MAX_SEQUENCE) {
                    m_itmtSeq = (int)MAX_SEQUENCE - 1;
                    m_itmtPhase = ItmtPhase::REVERSE;
                }
            }
            break;
        }
        case ItmtPhase::REVERSE: {
            uint32_t pulseWidth = 10 * (m_itmtSeq + 1);
            sendOptimizedPulse(pulseWidth);
            m_pulseCount++;
            m_itmtSeq--;
            if (m_itmtSeq < 0) {
                m_itmtSeq = 0;
                m_itmtPhase = ItmtPhase::NOISE_BURST;
            }
            break;
        }
        case ItmtPhase::NOISE_BURST: {
            sendRandomBurst(200);
            m_pulseCount += 200;
            m_itmtPhase = ItmtPhase::FORWARD;
            m_itmtSeq = 0;
            m_itmtCycle = 0;
            break;
        }
    }
}

// ---------------------------------------------------------------------------
// NOISE: ported from Bruce's run_noise_jammer(). The CC1101 PN9 generator
// does the actual TX in hardware; tick() just cycles ASK->2FSK->MSK every 3s
// for wideband spectral diversity, matching Bruce exactly.
// ---------------------------------------------------------------------------
void RFJammer::tickNoise() {
    static const uint8_t modSchemes[] = {2, 0, 1}; // ASK, 2FSK, MSK
    unsigned long elapsed = millis() - m_startTime;
    uint8_t newMod = (elapsed / 3000) % 3;
    if (newMod != m_modCycle) {
        m_modCycle = newMod;
        ELECHOUSE_cc1101.setSidle();
        ELECHOUSE_cc1101.setModulation(modSchemes[m_modCycle]);
        ELECHOUSE_cc1101.SetTx();
    }
    m_pulseCount = elapsed / 1000;
}

// ---------------------------------------------------------------------------
// SWEEP: ported from Bruce's run_sweep_jammer(). +/-5MHz around the target
// in 50kHz steps, bidirectional, 40-pulse burst per step.
// ---------------------------------------------------------------------------
void RFJammer::tickSweep() {
    static const float SWEEP_STEP = 0.05f;

    cc1101Driver.quickTune(m_sweepFreq);

    for (int burst = 0; burst < 40; burst++) {
        digitalWrite(m_txPin, HIGH);
        delayMicroseconds(30);
        digitalWrite(m_txPin, LOW);
        delayMicroseconds(5);
        m_pulseCount++;
    }

    if (m_sweepForward) {
        m_sweepFreq += SWEEP_STEP;
        if (m_sweepFreq > m_sweepMax) {
            m_sweepFreq = m_sweepMax;
            m_sweepForward = false;
            m_sweepCount++;
        }
    } else {
        m_sweepFreq -= SWEEP_STEP;
        if (m_sweepFreq < m_sweepMin) {
            m_sweepFreq = m_sweepMin;
            m_sweepForward = true;
            m_sweepCount++;
        }
    }
}

void RFJammer::sendOptimizedPulse(int width) {
    for (uint32_t i = 0; i < (uint32_t)width; i += 6) {
        digitalWrite(m_txPin, HIGH);
        delayMicroseconds(5);
        digitalWrite(m_txPin, LOW);
        delayMicroseconds(1);
    }
    digitalWrite(m_txPin, HIGH);
    delayMicroseconds(width / 3);
    digitalWrite(m_txPin, LOW);

    uint32_t lowPeriod = width / 4;
    if (lowPeriod > 0) delayMicroseconds(lowPeriod);
}

void RFJammer::sendRandomBurst(int numPulses) {
    unsigned long startTime = millis();
    for (int i = 0; i < numPulses; i++) {
        uint32_t pulseWidth = 1 + (micros() % 60);
        digitalWrite(m_txPin, HIGH);
        delayMicroseconds(pulseWidth);
        digitalWrite(m_txPin, LOW);
        uint32_t spaceWidth = 1 + (micros() % 8);
        delayMicroseconds(spaceWidth);
        if (millis() - startTime > 250) break;
    }
}
