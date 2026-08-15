#ifndef RF_ENCODER_H
#define RF_ENCODER_H

#include "rf_protocol.h"
#include <Arduino.h>
#include <vector>

bool rf_encode_protocol(
    uint64_t data, unsigned int bits, int te, const RfProtocolDef *def, int repeat,
    std::vector<int> &outDurations
);

bool rf_tx_protocol(uint64_t data, unsigned int bits, int te, const RfProtocolDef *def, int repeat);
bool rf_tx_keeloq(uint64_t key, int repeat);
bool rf_tx_raw_bits(const String &bits, int te);

#endif // RF_ENCODER_H
