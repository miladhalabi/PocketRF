#include "rf_decoder.h"
#include "rf_registry.h"

#define RF_SEPARATION_LIMIT 4000
#define RF_MAX_CHANGES 256
#define RF_RECEIVE_TOLERANCE 60
#define RF_NOMINAL_TE_TOLERANCE 50

static inline unsigned int rf_udiff(unsigned int a, unsigned int b) { return (a > b) ? (a - b) : (b - a); }

static inline unsigned int rf_udiff_u(unsigned int a, unsigned int b) { return (a > b) ? (a - b) : (b - a); }

static bool rf_match_protocol(
    const RfProtocolDef *pro, unsigned int changeCount, const unsigned int *timings, RfCodes &out
) {
    if (pro == nullptr) return false;
    uint64_t code = 0;
    unsigned int syncLen = (pro->sync.low > pro->sync.high) ? pro->sync.low : pro->sync.high;
    if (syncLen == 0) return false;
    unsigned int delay = timings[0] / syncLen;
    if (delay == 0) return false;
    unsigned int teTol = pro->te * RF_NOMINAL_TE_TOLERANCE / 100;
    if (teTol < 80) teTol = 80;
    if (rf_udiff_u(delay, pro->te) > teTol) return false;

    unsigned int tol = delay * RF_RECEIVE_TOLERANCE / 100;
    unsigned int first = pro->inverted ? 2 : 1;

    for (unsigned int i = first; i + 1 < changeCount; i += 2) {
        code <<= 1;
        if (rf_udiff(timings[i], delay * pro->zero.high) < tol &&
            rf_udiff(timings[i + 1], delay * pro->zero.low) < tol) {
        } else if (
            rf_udiff(timings[i], delay * pro->one.high) < tol &&
            rf_udiff(timings[i + 1], delay * pro->one.low) < tol
        ) {
            code |= 1;
        } else {
            return false;
        }
    }

    if (changeCount > 7) {
        int nbits = (changeCount - 1) / 2;
        if ((pro->flags & RF_PF_FIXED_LEN) && nbits != pro->bits) return false;
        out.key = code;
        out.Bit = nbits;
        out.te = delay;
        out.protocol = pro->name;
        return true;
    }
    return false;
}

bool rf_decode_ook(const std::vector<int> &durations, RfCodes &out) {
    if (durations.size() < 10) return false;

    unsigned int timings[RF_MAX_CHANGES];
    unsigned int changeCount = 0;
    unsigned int repeatCount = 0;
    const int protoCount = rf_protocol_count();

    for (int d : durations) {
        unsigned int dur = (d < 0) ? (unsigned int)(-d) : (unsigned int)d;

        if (dur > RF_SEPARATION_LIMIT) {
            if (repeatCount == 0 || rf_udiff(dur, timings[0]) < 200) {
                repeatCount++;
                if (repeatCount == 2) {
                    for (int p = 0; p < protoCount; p++) {
                        if (rf_match_protocol(rf_protocol_at(p), changeCount, timings, out)) {
                            out.preset = "Ook270Async";
                            return true;
                        }
                    }
                    repeatCount = 0;
                }
            }
            changeCount = 0;
        }

        if (changeCount >= RF_MAX_CHANGES) {
            changeCount = 0;
            repeatCount = 0;
        }
        timings[changeCount++] = dur;
    }
    return false;
}

bool rf_decode_keeloq(const std::vector<int> &durations, RfCodes &out) {
    if (durations.size() < 130) return false;
    const unsigned int shortTol = 200;

    for (size_t i = 0; i + 130 <= durations.size(); i++) {
        bool matchHeader = true;
        for (size_t h = 0; h < 22; h += 2) {
            int high = durations[i + h];
            int low = durations[i + h + 1];
            if (high < 200 || high > 600 || low > -200 || low < -600) {
                matchHeader = false;
                break;
            }
        }
        if (!matchHeader) continue;

        int syncHigh = durations[i + 22];
        int syncLow = durations[i + 23];
        if (syncHigh < 200 || syncHigh > 600 || syncLow > -2500 || syncLow < -5500) continue;

        uint64_t code = 0;
        bool dataOk = true;
        for (size_t b = 0; b < 64; b++) {
            int high = durations[i + 24 + b * 2];
            int low = durations[i + 24 + b * 2 + 1];
            code <<= 1;

            if (high >= 200 && high <= 600 && low <= -600 && low >= -1000) {
                code |= 1; // bit 1
            } else if (high >= 600 && high <= 1000 && low <= -200 && low >= -600) {
                // bit 0
            } else {
                dataOk = false;
                break;
            }
        }

        if (dataOk) {
            out.key = code;
            out.Bit = 64;
            out.te = 400;
            out.protocol = "KeeLoq";
            out.preset = "Ook270Async";
            return true;
        }
    }
    return false;
}

int rf_build_raw(
    const std::vector<int> &durations, String &dataOut, bool &hasCrc, uint64_t &crcOut,
    std::vector<int> &indexedOut, int &bitsOut, int &teOut
) {
    dataOut = "";
    hasCrc = false;
    crcOut = 0;
    bitsOut = 0;
    teOut = 0;
    indexedOut.clear();

    if (durations.empty()) return 0;

    int count = 0;
    for (int d : durations) {
        if (count > 0) dataOut += " ";
        dataOut += (d > 0) ? ("+" + String(d)) : String(d);
        count++;
        if (teOut == 0 && d > 0) teOut = d;
    }
    bitsOut = count;
    return count;
}
