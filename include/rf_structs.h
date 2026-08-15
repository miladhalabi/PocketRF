#ifndef RF_STRUCTS_H
#define RF_STRUCTS_H

#include <Arduino.h>
#include <vector>

struct HighLow {
    uint8_t high; // 1
    uint8_t low;  // 31
};

struct Protocol {
    uint16_t pulseLength; // base pulse length in microseconds, e.g. 350
    HighLow syncFactor;
    HighLow zero;
    HighLow one;
    bool invertedSignal;
};

struct RfCodes {
    uint32_t frequency = 0;
    uint32_t serial = 0;
    uint64_t key = 0;
    uint16_t cnt = 0;
    uint32_t fix = 0;
    uint32_t hop = 0;
    uint32_t encrypted = 0;
    uint32_t seed = 0;
    uint8_t btn = 0;
    String mf_name = "Unknown";
    String protocol = "";
    String preset = "";
    String data = "";
    int te = 0;
    std::vector<int> indexed_durations;
    String filepath = "";
    int Bit = 0;
    int BitRAW = 0;
};

struct FreqFound {
    float freq;
    int rssi;
};

struct RawRecordingStatus {
    float frequency = 0.f;
    int rssiCount = 0;
    int latestRssi = 0;
    bool recordingStarted = false;
    bool recordingFinished = false;
    unsigned long firstSignalTime = 0;
    unsigned long lastSignalTime = 0;
    unsigned long lastRssiUpdate = 0;
};

#endif // RF_STRUCTS_H
