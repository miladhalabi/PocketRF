#include "app_config.h"
#include "ble_server.h"
#include "cc1101_driver.h"
#include "cmd_router.h"
#include "rf_decoder.h"
#include "rf_encoder.h"
#include "rf_keeloq.h"
#include "rf_registry.h"
#include "rf_service.h"
#include "rf_signal_engine.h"
#include <Arduino.h>
#include <ArduinoJson.h>

// Handle incoming BLE RX commands
void handleBLERxData(const uint8_t *data, size_t length) {
    if (length == 0) return;
    cmdRouter.processInput(data, length);
}

void setup() {
    Serial.begin(115200);
    unsigned long startWait = millis();
    while (!Serial && (millis() - startWait < 3000)) { delay(10); }
    delay(500);
    Serial.println("==========================================");
    Serial.println(" PocketRF - Headless CC1101 BLE Firmware ");
    Serial.println(" Target MCU: Seeed Studio XIAO ESP32C3");
    Serial.println(" Device BLE Name: " BLE_DEVICE_NAME);
    Serial.println("==========================================");

    // Initialize CC1101 Radio Module
    bool radioOk = cc1101Driver.begin();
    if (radioOk) {
        Serial.println("[SYSTEM] CC1101 hardware self-test PASSED!");
    } else {
        Serial.println("[SYSTEM] WARNING: CC1101 hardware self-test FAILED! Check wiring.");
    }

    // Initialize RF Service
    rfService.init();

    // Initialize BLE GATT Server
    bleServer.begin(handleBLERxData);
}

void loop() {
    static unsigned long lastLog = 0;
    if (millis() - lastLog > 5000) {
        lastLog = millis();
        Serial.printf(
            "[SYSTEM] BLE: %s | Peer MTU: %u | CC1101 State: 0x%02X | Freq: %.2f MHz | RSSI: %d dBm | RF "
            "State: %d\n",
            bleServer.isConnected() ? "Connected" : "Advertising",
            bleServer.getPeerMTU(),
            cc1101Driver.getMarcState(),
            cc1101Driver.getFrequency(),
            cc1101Driver.getRssi(),
            (int)rfService.getState()
        );
        Serial.flush();
    }

    // Check USB CDC Serial for incoming terminal commands
    if (Serial.available()) {
        String serialLine = Serial.readStringUntil('\n');
        serialLine.trim();
        if (serialLine.length() > 0) {
            cmdRouter.processInput((const uint8_t *)serialLine.c_str(), serialLine.length());
        }
    }

    // Service active RF tasks
    rfService.loop();

    delay(10);
}
