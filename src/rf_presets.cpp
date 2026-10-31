#include "rf_presets.h"
#include <ELECHOUSE_CC1101_SRC_DRV.h>

// Canonical preset table, ported 1-to-1 from Bruce's rf_presets.cpp. A field
// at 0 means "keep module default" (not overridden).
static const RfPreset rf_presets[] = {
    // name                         mod  dev        rxBW   dataRate  legacyProto
    {"Ook270Async",                  2, 0.0f,       270.f, 0.0f,     1},
    {"Ook650Async",                  2, 0.0f,       650.f, 0.0f,     2},
    {"2FSKDev238Async",              0, 2.380371f,  238.f, 0.0f,     1},
    {"2FSKDev476Async",              0, 47.60742f,  476.f, 0.0f,     1},
    {"MSK99_97KbAsync",              4, 47.60742f,  0.0f,  99.97f,   1},
    {"GFSK9_99KbAsync",              1, 19.042969f, 0.0f,  9.996f,   1},
};
static const int rf_presets_count = sizeof(rf_presets) / sizeof(rf_presets[0]);

struct PresetAlias {
    const char *alias;
    const char *canonical;
};
static const PresetAlias rf_preset_aliases[] = {
    {"FuriHalSubGhzPresetOok270Async",     "Ook270Async"    },
    {"FuriHalSubGhzPresetOok650Async",     "Ook650Async"    },
    {"FuriHalSubGhzPreset2FSKDev238Async", "2FSKDev238Async"},
    {"FuriHalSubGhzPreset2FSKDev476Async", "2FSKDev476Async"},
    {"FuriHalSubGhzPresetMSK99_97KbAsync", "MSK99_97KbAsync"},
    {"FuriHalSubGhzPresetGFSK9_99KbAsync", "GFSK9_99KbAsync"},
};

const RfPreset *rf_find_preset(const String &name) {
    String wanted = name;
    for (const auto &a : rf_preset_aliases) {
        if (name == a.alias) {
            wanted = a.canonical;
            break;
        }
    }
    for (int i = 0; i < rf_presets_count; i++) {
        if (wanted == rf_presets[i].name) return &rf_presets[i];
    }
    return nullptr;
}

void rf_apply_preset(const RfPreset *preset) {
    if (preset == nullptr) return;
    ELECHOUSE_cc1101.setModulation(preset->modulation);
    if (preset->deviation > 0.0f) ELECHOUSE_cc1101.setDeviation(preset->deviation);
    if (preset->rxBW > 0.0f) ELECHOUSE_cc1101.setRxBW(preset->rxBW);
    if (preset->dataRate > 0.0f) ELECHOUSE_cc1101.setDRate(preset->dataRate);
}
