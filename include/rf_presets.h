#ifndef RF_PRESETS_H
#define RF_PRESETS_H

#include "rf_protocol.h"
#include <Arduino.h>

// Radio preset registry, ported from Bruce's src/modules/rf/protocols/rf_presets.cpp.
// Maps a `.sub` "Preset:" name (or its legacy Flipper alias) to concrete
// CC1101 transceiver parameters (modulation / deviation / RX bandwidth /
// data rate), so PocketRF isn't limited to the fixed OOK/ASK preset used by
// the rest of the firmware.
const RfPreset *rf_find_preset(const String &name);

// Applies a preset's modulation/deviation/BW/dataRate to the CC1101. A field
// left at 0 in the preset means "keep the current/default value".
void rf_apply_preset(const RfPreset *preset);

#endif // RF_PRESETS_H
