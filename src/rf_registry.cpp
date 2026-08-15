#include "rf_registry.h"

#define SYNC RF_PF_HAS_SYNC
#define FIXED RF_PF_FIXED_LEN

static const RfProtocolDef rf_protocols[] = {
    // name           te    sync       zero      one       bits inv  flags
    {"Princeton",    350,  {1, 31},  {1, 3},  {3, 1},  24, false, SYNC        },
    {"NICE_FLO",     700,  {1, 36},  {2, 1},  {1, 2},  12, false, SYNC | FIXED},
    {"Nice_12bit",   700,  {36, 1},  {1, 2},  {2, 1},  12, true,  SYNC | FIXED},
    {"Linear",       500,  {3, 42},  {1, 3},  {3, 1},  10, false, SYNC | FIXED},
    {"Clemsa",       385,  {7, 50},  {1, 7},  {7, 1},  18, false, SYNC | FIXED},
    {"Mastercode",   1072, {2, 14},  {1, 2},  {2, 1},  36, false, SYNC | FIXED},
    {"CAME",         320,  {36, 1},  {2, 1},  {1, 2},  12, true,  SYNC | FIXED},
    {"Ansonic",      555,  {35, 1},  {1, 2},  {2, 1},  12, true,  SYNC | FIXED},
    {"GateTX",       350,  {49, 2},  {1, 2},  {2, 1},  24, true,  SYNC | FIXED},
    {"Holtek",       430,  {36, 1},  {1, 2},  {2, 1},  40, true,  SYNC | FIXED},
    {"Holtek_12bit", 430,  {36, 1},  {1, 2},  {2, 1},  12, true,  SYNC | FIXED},
    {"Holtek_HT12",  450,  {23, 1},  {1, 2},  {2, 1},  12, true,  SYNC | FIXED},
    {"PhoenixV2",    427,  {60, 6},  {1, 2},  {2, 1},  52, true,  SYNC | FIXED},

    // Generic numbered fallback entries
    {"RcSwitch_1",   350,  {1, 31},  {1, 3},  {3, 1},  0,  false, SYNC        },
    {"RcSwitch_2",   650,  {1, 10},  {1, 2},  {2, 1},  0,  false, SYNC        },
    {"RcSwitch_3",   100,  {30, 71}, {4, 11}, {9, 6},  0,  false, SYNC        },
    {"RcSwitch_4",   380,  {1, 6},   {1, 3},  {3, 1},  0,  false, SYNC        },
    {"RcSwitch_5",   500,  {6, 14},  {1, 2},  {2, 1},  0,  false, SYNC        },
    {"RcSwitch_6",   450,  {23, 1},  {1, 2},  {2, 1},  0,  true,  SYNC        },
    {"RcSwitch_7",   150,  {2, 62},  {1, 6},  {6, 1},  0,  false, SYNC        },
    {"RcSwitch_8",   200,  {3, 130}, {7, 16}, {3, 16}, 0,  false, SYNC        },
    {"RcSwitch_9",   200,  {130, 7}, {16, 7}, {16, 3}, 0,  true,  SYNC        },
    {"RcSwitch_10",  365,  {18, 1},  {3, 1},  {1, 3},  0,  true,  SYNC        },
    {"RcSwitch_11",  270,  {36, 1},  {1, 2},  {2, 1},  0,  true,  SYNC        },
    {"RcSwitch_12",  320,  {36, 1},  {1, 2},  {2, 1},  0,  true,  SYNC        },
};

#undef SYNC
#undef FIXED

static const int rf_protocols_count = sizeof(rf_protocols) / sizeof(rf_protocols[0]);

struct RfProtoAlias {
    const char *flipper;
    const char *canonical;
};

static const RfProtoAlias rf_proto_aliases[] = {
    {"Nice FLO",     "NICE_FLO"    },
    {"Nice 12bit",   "Nice_12bit"  },
    {"Holtek 12bit", "Holtek_12bit"},
    {"Holtec 12bit", "Holtek_12bit"},
    {"Holtek_HT12X", "Holtek_HT12" },
    {"Phoenix_V2",   "PhoenixV2"   },
};

const RfProtocolDef *rf_find_protocol(const String &name) {
    String wanted = name;
    for (const auto &a : rf_proto_aliases) {
        if (name == a.flipper) {
            wanted = a.canonical;
            break;
        }
    }
    for (const auto &p : rf_protocols) {
        if (wanted == p.name) return &p;
    }
    return nullptr;
}

String rf_flipper_protocol_name(const String &canonical) {
    for (const auto &a : rf_proto_aliases) {
        if (canonical == a.canonical) return a.flipper;
    }
    return canonical;
}

const RfProtocolDef *rf_protocol_for_number(int proto_no) {
    if (proto_no >= 1 && proto_no <= 12) {
        char buf[16];
        snprintf(buf, sizeof(buf), "RcSwitch_%d", proto_no);
        const RfProtocolDef *def = rf_find_protocol(buf);
        if (def) return def;
    } else if (proto_no == 20) {
        return rf_find_protocol("CAME");
    } else if (proto_no == 22) {
        return rf_find_protocol("NICE_FLO");
    }
    return &rf_protocols[13]; // Default to RcSwitch_1
}

const RfProtocolDef *rf_protocol_at(int index) {
    if (index < 0 || index >= rf_protocols_count) return nullptr;
    return &rf_protocols[index];
}

int rf_protocol_count() { return rf_protocols_count; }
