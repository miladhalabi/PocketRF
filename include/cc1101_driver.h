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

    // Returns false if the chip never reached IDLE within the timeout (SCAL
    // calibration took too long / never completed) - previously this was
    // silently ignored by every caller, so a step whose calibration had not
    // actually finished still went on to be used as if it had.
    bool waitForIdle();

    // Quick re-tune with no recalibration pass (ELECHOUSE_cc1101.setMHZ only).
    // Matches Bruce's jammer sweep (rf_jammer.cpp run_sweep_jammer): fast hops
    // across many frequencies don't need per-step precise calibration.
    void quickTune(float frequency);

    // Fast RX retune for RSSI-only probing (spectrum sweep, scan's fast-scan
    // phase): SIDLE -> setMHZ -> SetRx(), with none of setRxMode()'s manual
    // SCAL+waitForIdle calibration or warm-up TX pulse. MCSM0=0x18 already
    // enables hardware auto-calibration on every IDLE->RX transition (see
    // cc1101_driver.cpp), so the manual software calibration is largely
    // redundant for a quick presence check - it's there in setRxMode() for
    // the precision a real decode session needs, not a probe. Unlike
    // quickTune(), this forces SIDLE first (the datasheet requires the radio
    // be in IDLE when frequency registers are reprogrammed) and explicitly
    // re-arms RX afterward, so RSSI reflects the new frequency.
    void quickRetuneForRx(float frequency);

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

    // Brief dummy TX pulse immediately before arming RX. Workaround for an
    // observed hardware quirk on this board: listening sometimes produces
    // nothing until a TX has happened at least once, then subsequent RX
    // attempts work reliably. A short self-transmitted pulse reproduces
    // that "prime the receiver" effect without needing a real TX from the
    // user. See cc1101_driver.cpp for the fuller explanation.
    void warmUpBeforeRx();
};

extern CC1101Driver cc1101Driver;

#endif // CC1101_DRIVER_H
