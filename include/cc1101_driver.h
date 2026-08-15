#ifndef CC1101_DRIVER_H
#define CC1101_DRIVER_H

#include "app_config.h"
#include <Arduino.h>
#include <ELECHOUSE_CC1101_SRC_DRV.h>

class CC1101Driver {
public:
    CC1101Driver();

    // Initialization & status
    bool begin();
    bool isDetected();
    uint8_t getMarcState();
    uint8_t getVersion();
    uint8_t getPartNum();
    int getRssi();

    // Frequency & Modulation Configuration
    bool setFrequency(float frequency, bool isTx = false);
    void applyPreciseCalibration(float frequency, bool isTx = false);
    void applyFixedFreqOokPreset(bool isTx = false);
    void waitForIdle();

    // Operational Modes
    bool setRxMode(float frequency = DEFAULT_RF_FREQ);
    bool setTxMode(float frequency = DEFAULT_RF_FREQ);
    void setIdle();

    // Frequency getter
    float getFrequency() const { return m_currentFreq; }

private:
    float m_currentFreq = DEFAULT_RF_FREQ;
    bool m_initialized = false;
    uint8_t
    interpolateFsctrl0(float frequency, float minFreq, float maxFreq, uint8_t minValue, uint8_t maxValue);
};

extern CC1101Driver cc1101Driver;

#endif // CC1101_DRIVER_H
