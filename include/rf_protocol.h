#ifndef RF_PROTOCOL_H
#define RF_PROTOCOL_H

#include "rf_structs.h"
#include <stdint.h>

struct RfPreset {
    const char *name;
    uint8_t modulation; // CC1101: 0=2-FSK, 1=GFSK, 2=ASK/OOK, 4=MSK
    float deviation;    // kHz
    float rxBW;         // kHz
    float dataRate;     // kbps
    uint8_t legacyProto;
};

struct RfProtocolDef {
    const char *name;
    uint16_t te;   // base pulse length in microseconds
    HighLow sync;  // sync / pilot factor ({0,0} = none)
    HighLow zero;  // bit 0 encoding
    HighLow one;   // bit 1 encoding
    uint8_t bits;  // typical payload length in bits (0 = variable)
    bool inverted; // inverted signal level
    uint8_t flags; // bitmask
};

enum RfProtocolFlags : uint8_t {
    RF_PF_HAS_SYNC = 0x01,  // protocol uses a sync/pilot pulse
    RF_PF_FIXED_LEN = 0x02, // payload length is fixed (== bits)
};

#endif // RF_PROTOCOL_H
