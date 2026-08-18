#ifndef CMD_ROUTER_H
#define CMD_ROUTER_H

#include "cc1101_driver.h"
#include "rf_decoder.h"
#include "rf_encoder.h"
#include "rf_keeloq.h"
#include "rf_registry.h"
#include "rf_service.h"
#include "rf_signal_engine.h"
#include <Arduino.h>
#include <ArduinoJson.h>

#define BINARY_FRAME_HEADER 0xAA

enum BinaryCmdId : uint8_t {
    CMD_STATUS = 0x01,
    CMD_SET_FREQ = 0x02,
    CMD_START_RX = 0x03,
    CMD_STOP_RX = 0x04,
    CMD_TX_PROTO = 0x05,
    CMD_TX_KEELOQ = 0x06,
    CMD_TX_RAW = 0x07,
    CMD_START_LISTEN = 0x08,
    CMD_STOP_ALL = 0x09,
};

class CmdRouter {
public:
    CmdRouter();

    void processInput(const uint8_t *data, size_t length);

private:
    void processBinaryFrame(const uint8_t *data, size_t length);
    void processAsciiCommand(const String &cmdStr);
    uint8_t calculateCrc8(const uint8_t *data, size_t len);
};

extern CmdRouter cmdRouter;

#endif // CMD_ROUTER_H
