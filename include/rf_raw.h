#ifndef RF_RAW_H
#define RF_RAW_H

#include <Arduino.h>
#include <vector>

// Flipper-compatible .sub RAW file parser + replay emitter, ported from
// Bruce's src/modules/rf/save.cpp (writer) / emit.cpp (player) format:
//
//   Filetype: Bruce SubGhz File
//   Version 1
//   Frequency: <Hz>
//   Preset: 0
//   Protocol: RAW
//   RAW_Data: <signed d1> <signed d2> ... (may repeat "RAW_Data: " across
//             multiple lines, up to 512 values per line)
//
// PocketRF is headless (no SD card), so the .sub text is supplied directly
// as a BLE/serial payload rather than read from a file, and replay goes
// through the existing RMT signal engine instead of Bruce's bit-banged
// digitalWrite loop (same durations, just transmitted precisely).

// Parses a full .sub text blob. Returns true if at least one duration was
// found. freqMhzOut is left unchanged if no "Frequency:" line is present.
bool rf_parse_sub_raw(const String &subText, float &freqMhzOut, std::vector<int> &durationsOut);

// Parses + transmits a full .sub text blob in one call.
bool rf_raw_tx_sub(const String &subText);

// Transmits a raw duration list directly (already-parsed, e.g. from a
// compact binary BLE command) at the given frequency.
bool rf_raw_tx_durations(const std::vector<int> &durations, float freqMhz);

#endif // RF_RAW_H
