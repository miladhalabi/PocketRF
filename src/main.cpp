#include "app_config.h"
#include "ble_server.h"
#include "cc1101_driver.h"
#include "cmd_router.h"
#include "rf_decoder.h"
#include "rf_encoder.h"
#include "rf_keeloq.h"
#include "rf_registry.h"
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

    // Initialize BLE GATT Server
    bleServer.begin(handleBLERxData);
}

void loop() {
    static unsigned long lastLog = 0;
    if (millis() - lastLog > 5000) {
        lastLog = millis();
        Serial.printf(
            "[SYSTEM] BLE: %s | Peer MTU: %u | CC1101 State: 0x%02X | Freq: %.2f MHz | RSSI: %d dBm | RMT "
            "RX: %s\n",
            bleServer.isConnected() ? "Connected" : "Advertising",
            bleServer.getPeerMTU(),
            cc1101Driver.getMarcState(),
            cc1101Driver.getFrequency(),
            cc1101Driver.getRssi(),
            rfSignalEngine.isRxActive() ? "ACTIVE" : "OFF"
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

    // Poll RMT RX pulse stream if active
    if (rfSignalEngine.isRxActive()) {
        std::vector<int> capturedPulses;
        if (rfSignalEngine.pollRxDurations(capturedPulses, 0)) {
            RfCodes decoded;
            bool isDecoded = false;

            if (rf_decode_ook(capturedPulses, decoded)) {
                isDecoded = true;
            } else if (rf_decode_keeloq(capturedPulses, decoded)) {
                keeloq_identify(decoded);
                isDecoded = true;
            }

            JsonDocument pulseDoc;
            if (isDecoded) {
                pulseDoc["type"] = "decoded_rf";
                pulseDoc["protocol"] = decoded.protocol;
                pulseDoc["bits"] = decoded.Bit;
                pulseDoc["key"] = String((unsigned long long)decoded.key, HEX);
                pulseDoc["te"] = decoded.te;
                pulseDoc["freq"] = cc1101Driver.getFrequency();
                pulseDoc["rssi"] = cc1101Driver.getRssi();
                if (decoded.protocol == "KeeLoq") { pulseDoc["mf_name"] = decoded.mf_name; }
            } else {
                pulseDoc["type"] = "raw_pulse";
                pulseDoc["count"] = capturedPulses.size();
                pulseDoc["freq"] = cc1101Driver.getFrequency();
                pulseDoc["rssi"] = cc1101Driver.getRssi();
            }

            String pulseJson;
            serializeJson(pulseDoc, pulseJson);
            bleServer.notifyText(pulseJson);
        }
    }

    delay(10);
}
