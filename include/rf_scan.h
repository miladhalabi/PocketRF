#ifndef RF_SCAN_H
#define RF_SCAN_H

#include "rf_structs.h"
#include <Arduino.h>

// Automatic frequency-range scan ("Scan + Copy"), ported from Bruce's
// src/modules/rf/rf_scan.cpp fast_scan()/decode_signal() core (SD-card
// saving, the file browser, and all display/menu code are dropped — headless
// PocketRF reports captures over BLE instead, and storage is the future
// mobile app's job, not the firmware's).
//
// Two phases, each driven one bounded step at a time via tick() so it never
// blocks the main loop/BLE stack:
//  - FAST_SCAN: hops across a list of common ISM frequencies, sampling RSSI
//    at each, picking the strongest of 5 candidates above a threshold
//    (same heuristic and threshold as Bruce).
//  - LOCKED: once a frequency is found, stays tuned there and keeps trying
//    to decode (KeeLoq first, then the OOK registry) every capture, exactly
//    like Bruce's behavior of locking onto the found frequency rather than
//    re-scanning after every capture.
class RfScanner {
public:
    RfScanner();

    // rssiThresholdDbm: minimum RSSI to consider a frequency "active" (Bruce
    // hardcodes -65; PocketRF makes it a parameter since RSSI calibration
    // varies noticeably between CC1101 clones/antennas - a board whose real
    // signals never read above ~-70dBm will never lock on at the default).
    bool begin(int rssiThresholdDbm = -65);
    void stop();
    bool tick(); // returns false once the scanner is no longer active

    bool isActive() const { return m_active; }
    bool isLocked() const { return m_locked; }
    float getLockedFreq() const { return m_lockedFreq; }

private:
    bool m_active = false;
    bool m_locked = false;
    float m_lockedFreq = 0.f;
    int m_rssiThreshold = -65;

    // FAST_SCAN state
    int m_idx = 0;
    FreqFound m_freqs[5];
    int m_try = 0;

    bool fastScanStep();
    void lockOnto(float freqMhz);
};

extern RfScanner rfScanner;

#endif // RF_SCAN_H
