#ifndef RF_DECODER_H
#define RF_DECODER_H

#include "rf_protocol.h"
#include "rf_structs.h"
#include <Arduino.h>
#include <vector>

bool rf_decode_ook(const std::vector<int> &durations, RfCodes &out);
bool rf_decode_keeloq(const std::vector<int> &durations, RfCodes &out);
int rf_build_raw(
    const std::vector<int> &durations, String &dataOut, bool &hasCrc, uint64_t &crcOut,
    std::vector<int> &indexedOut, int &bitsOut, int &teOut
);

#endif // RF_DECODER_H
