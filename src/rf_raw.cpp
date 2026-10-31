#include "rf_raw.h"
#include "cc1101_driver.h"
#include "rf_signal_engine.h"

bool rf_parse_sub_raw(const String &subText, float &freqMhzOut, std::vector<int> &durationsOut) {
    durationsOut.clear();
    bool found = false;

    int start = 0;
    const int len = subText.length();
    while (start < len) {
        int nl = subText.indexOf('\n', start);
        String line = (nl < 0) ? subText.substring(start) : subText.substring(start, nl);
        start = (nl < 0) ? len : nl + 1;
        line.trim(); // also drops a trailing '\r'
        if (line.length() == 0) continue;

        if (line.startsWith("Frequency:")) {
            String val = line.substring(10);
            val.trim();
            long hz = val.toInt();
            if (hz > 0) freqMhzOut = (float)hz / 1000000.0f;
            continue;
        }

        if (line.startsWith("RAW_Data:")) {
            String vals = line.substring(9);
            vals.trim();
            int tokStart = 0;
            const int vlen = vals.length();
            while (tokStart < vlen) {
                int space = vals.indexOf(' ', tokStart);
                String tok = (space < 0) ? vals.substring(tokStart) : vals.substring(tokStart, space);
                tokStart = (space < 0) ? vlen : space + 1;
                if (tok.length() == 0) continue;
                durationsOut.push_back(tok.toInt());
                found = true;
            }
        }
    }

    return found;
}

bool rf_raw_tx_sub(const String &subText) {
    float freqMhz = cc1101Driver.getFrequency();
    std::vector<int> durations;
    if (!rf_parse_sub_raw(subText, freqMhz, durations)) {
        Serial.println("[RAW] No RAW_Data found in .sub text.");
        return false;
    }
    return rf_raw_tx_durations(durations, freqMhz);
}

bool rf_raw_tx_durations(const std::vector<int> &durations, float freqMhz) {
    if (durations.empty()) return false;
    Serial.printf("[RAW] Replaying %u durations at %.2f MHz\n", (unsigned int)durations.size(), freqMhz);
    return rfSignalEngine.transmitDurations(durations, freqMhz);
}
