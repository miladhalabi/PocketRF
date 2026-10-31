#ifndef RF_BRUTEFORCE_H
#define RF_BRUTEFORCE_H

#include "rf_protocol.h"
#include <Arduino.h>
#include <stdint.h>

// Automated key generator for fixed-code OOK receivers, ported from Bruce's
// src/modules/rf/rf_bruteforce.cpp. Reuses PocketRF's rf_registry protocol
// table (which already carries CAME 12-bit, Princeton 24-bit, Nice FLO
// 12-bit, etc.) instead of duplicating a second protocol table, and reuses
// rf_tx_protocol() for the actual transmit.
//
// Non-blocking: tick() transmits exactly one code (its `repeat` copies) and
// advances to the next, so a BLE "stop" command can interrupt it between
// codes exactly like Bruce's EscPress check between codes.
class RfBruteforce {
public:
    RfBruteforce();

    // def: protocol to brute force. bits: override bit count (0 = use def->bits).
    bool begin(const RfProtocolDef *def, int bits, float freqMhz, int repeat = 1);
    void stop();

    // Sends the next code. Returns false once the keyspace is exhausted.
    bool tick();

    bool isActive() const { return m_active; }
    uint64_t getCurrentCode() const { return m_code; }
    uint64_t getTotalCodes() const { return m_total; }

private:
    bool m_active = false;
    const RfProtocolDef *m_def = nullptr;
    int m_bits = 0;
    int m_repeat = 1;
    uint64_t m_code = 0;
    uint64_t m_total = 0;
};

extern RfBruteforce rfBruteforce;

#endif // RF_BRUTEFORCE_H
