#ifndef RF_SERVICE_H
#define RF_SERVICE_H

#include "app_config.h"
#include "ble_server.h"
#include "cc1101_driver.h"
#include "rf_decoder.h"
#include "rf_keeloq.h"
#include "rf_signal_engine.h"
#include <Arduino.h>
#include <ArduinoJson.h>
#include <vector>

enum class RfState { IDLE, RX_DECODED, RX_RAW, LISTEN };

class RfService {
public:
    RfService();

    void init();
    RfState getState() const { return m_state; }

    bool startRxDecoded(float freqMhz);
    bool startRxRaw(float freqMhz);
    bool startListen(float freqMhz);
    void stop();

    void loop();

private:
    RfState m_state;
    float m_currentFreq;
    unsigned long m_lastListenNotify;
};

extern RfService rfService;

#endif // RF_SERVICE_H
