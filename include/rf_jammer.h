#ifndef RF_JAMMER_H
#define RF_JAMMER_H

#include <Arduino.h>
#include <stdint.h>

// RF Jammer modes, ported from Bruce's src/modules/rf/rf_jammer.h.
// Same four attack patterns, same 30s safety cutoff (MAX_JAMMER_RUNTIME).
// Re-architected as a non-blocking tick() state machine (Bruce's version
// blocks in a while loop polling buttons; headless PocketRF has no buttons
// and must keep servicing BLE/CLI commands, so each tick() call does one
// bounded chunk of work and returns).
enum class RfJamMode : uint8_t {
    FULL = 0, // Continuous TX, max duty cycle
    ITMT,     // Intermittent bursts, varied pulse patterns
    NOISE,    // CC1101 PN9 hardware random TX
    SWEEP,    // +/-5MHz hop around target frequency
};

class RFJammer {
public:
    RFJammer();

    bool begin(RfJamMode mode, float freqMhz);
    void stop();

    // Runs one bounded chunk of the jam pattern. Returns false once the
    // safety timeout (MAX_JAMMER_RUNTIME) has elapsed (caller should stop()).
    bool tick();

    bool isActive() const { return m_active; }
    RfJamMode getMode() const { return m_mode; }
    unsigned long getElapsedMs() const { return m_active ? (millis() - m_startTime) : 0; }
    uint32_t getPulseCount() const { return m_pulseCount; }
    uint32_t getSweepCount() const { return m_sweepCount; }

private:
    bool m_active = false;
    RfJamMode m_mode = RfJamMode::FULL;
    int m_txPin = -1;
    uint32_t m_pulseCount = 0;
    uint32_t m_sweepCount = 0;
    unsigned long m_startTime = 0;
    unsigned long m_lastModCycleMs = 0;
    uint8_t m_modCycle = 0;

    // FULL
    uint8_t m_fullPhase = 0;

    // ITMT (intermittent sweep): forward 10..500us, reverse 500..10us, then noise burst
    enum class ItmtPhase : uint8_t { FORWARD, REVERSE, NOISE_BURST };
    ItmtPhase m_itmtPhase = ItmtPhase::FORWARD;
    int m_itmtSeq = 0;
    int m_itmtCycle = 0;

    // SWEEP
    float m_sweepBase = 433.92f;
    float m_sweepMin = 0.f;
    float m_sweepMax = 0.f;
    float m_sweepFreq = 0.f;
    bool m_sweepForward = true;

    void tickFull();
    void tickItmt();
    void tickNoise();
    void tickSweep();
    void sendOptimizedPulse(int width);
    void sendRandomBurst(int numPulses);
};

extern RFJammer rfJammer;

#endif // RF_JAMMER_H
