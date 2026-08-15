#include "rf_encoder.h"
#include "cc1101_driver.h"
#include "rf_registry.h"
#include "rf_signal_engine.h"

bool rf_encode_protocol(
    uint64_t data, unsigned int bits, int te, const RfProtocolDef *def, int repeat, std::vector<int> &out
) {
    out.clear();
    if (def == nullptr || bits == 0) return false;
    int base = (te > 0) ? te : def->te;
    if (base <= 0) return false;
    if (repeat < 1) repeat = 1;

    const bool inv = def->inverted;
    const bool hasSync = (def->sync.high != 0 || def->sync.low != 0);

    out.reserve((size_t)repeat * (bits + 1) * 2);

    auto emitPulse = [&](const HighLow &p) {
        long first = (long)p.high * base;
        long second = (long)p.low * base;
        out.push_back(inv ? -(int)first : (int)first);
        out.push_back(inv ? (int)second : -(int)second);
    };

    for (int r = 0; r < repeat; r++) {
        for (int i = (int)bits - 1; i >= 0; i--) {
            const HighLow &p = ((data >> i) & 1ULL) ? def->one : def->zero;
            emitPulse(p);
        }
        if (hasSync) emitPulse(def->sync);
    }
    return true;
}

bool rf_tx_protocol(uint64_t data, unsigned int bits, int te, const RfProtocolDef *def, int repeat) {
    std::vector<int> durs;
    if (!rf_encode_protocol(data, bits, te, def, repeat, durs)) return false;
    return rfSignalEngine.transmitDurations(durs, cc1101Driver.getFrequency());
}

#define RF_KL_SHORT 400
#define RF_KL_LONG 800

static bool rf_keeloq_durations(uint64_t key, std::vector<int> &out) {
    out.clear();
    out.reserve(11 * 2 + 2 + 64 * 2 + 4);

    for (int i = 0; i < 11; i++) {
        out.push_back(RF_KL_SHORT);
        out.push_back(-RF_KL_SHORT);
    }
    out.push_back(RF_KL_SHORT);
    out.push_back(-RF_KL_SHORT * 10);

    for (int i = 63; i >= 0; i--) {
        if ((key >> i) & 1ULL) {
            out.push_back(RF_KL_SHORT);
            out.push_back(-RF_KL_LONG);
        } else {
            out.push_back(RF_KL_LONG);
            out.push_back(-RF_KL_SHORT);
        }
    }
    out.push_back(RF_KL_SHORT);
    out.push_back(-RF_KL_LONG);
    out.push_back(RF_KL_SHORT);
    out.push_back(-RF_KL_SHORT * 40);
    return true;
}

bool rf_tx_keeloq(uint64_t key, int repeat) {
    if (repeat < 1) repeat = 1;
    std::vector<int> frame;
    if (!rf_keeloq_durations(key, frame)) return false;

    std::vector<int> durs;
    durs.reserve(frame.size() * repeat);
    for (int r = 0; r < repeat; r++) durs.insert(durs.end(), frame.begin(), frame.end());

    return rfSignalEngine.transmitDurations(durs, cc1101Driver.getFrequency());
}

bool rf_tx_raw_bits(const String &bits, int te) {
    if (bits.length() == 0 || te <= 0) return false;
    std::vector<int> durs;
    durs.reserve(bits.length());
    for (int i = bits.length() - 1; i >= 0; i--) {
        char c = bits[i];
        if (c == '1') durs.push_back(te);
        else if (c == '0') durs.push_back(-te);
    }
    return rfSignalEngine.transmitDurations(durs, cc1101Driver.getFrequency());
}
