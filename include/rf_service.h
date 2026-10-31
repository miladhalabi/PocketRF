#ifndef RF_SERVICE_H
#define RF_SERVICE_H

#include "app_config.h"
#include "ble_server.h"
#include "cc1101_driver.h"
#include "rf_bruteforce.h"
#include "rf_decoder.h"
#include "rf_jammer.h"
#include "rf_keeloq.h"
#include "rf_protocol.h"
#include "rf_signal_engine.h"
#include "rf_spectrum.h"
#include <Arduino.h>
#include <ArduinoJson.h>
#include <vector>

enum class RfState { IDLE, RX_DECODED, RX_RAW, LISTEN, SPECTRUM_FIXED, SPECTRUM_SWEEP, JAM, BRUTEFORCE };

class RfService {
public:
    RfService();

    void init();
    RfState getState() const { return m_state; }

    bool startRxDecoded(float freqMhz);
    bool startRxRaw(float freqMhz);
    bool startListen(float freqMhz);
    bool startSpectrumFixed(float freqMhz, uint16_t intervalMs = 50);
    bool startSpectrumSweep(float startFreq, float endFreq, float stepMhz = 1.0f, uint16_t intervalMs = 15);
    bool startJam(RfJamMode mode, float freqMhz);
    bool startBruteforce(const RfProtocolDef *def, int bits, float freqMhz, int repeat = 1);
    void stop();

    void loop();

private:
    RfState m_state;
    float m_currentFreq;
    unsigned long m_lastListenNotify;
};

extern RfService rfService;

#endif // RF_SERVICE_H
