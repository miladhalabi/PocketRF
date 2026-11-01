#ifndef RF_SPECTRUM_H
#define RF_SPECTRUM_H

#include "ble_server.h"
#include "cc1101_driver.h"
#include <Arduino.h>
#include <ArduinoJson.h>
#include <vector>

enum class SpectrumMode { OFF, FIXED_RSSI, SWEEP_SW };

class RfSpectrum {
public:
    RfSpectrum();

    bool startFixedRssi(float freqMhz, uint16_t sampleIntervalMs = 50);
    bool startSweep(
        float startFreqMhz = 300.0f, float endFreqMhz = 928.0f, float stepMhz = 1.0f,
        uint16_t stepIntervalMs = 15
    );
    void stop();

    bool isActive() const { return m_mode != SpectrumMode::OFF; }
    SpectrumMode getMode() const { return m_mode; }

    void loop();

private:
    SpectrumMode m_mode;
    float m_centerFreq;
    float m_startFreq;
    float m_endFreq;
    float m_stepFreq;
    float m_currSweepFreq;

    uint16_t m_intervalMs;
    unsigned long m_lastSampleTime;

    std::vector<int8_t> m_sweepRssiBuffer;

    void sendSweepResult();
};

extern RfSpectrum rfSpectrum;

#endif // RF_SPECTRUM_H
