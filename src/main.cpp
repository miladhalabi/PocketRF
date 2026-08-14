#include "app_config.h"
#include "ble_server.h"
#include <Arduino.h>

// Callback handler for incoming BLE RX data
void handleBLERxData(const uint8_t *data, size_t length) {
    if (length == 0) return;

    uint8_t cmd = data[0];
    if (cmd == 0x01 || cmd == '1') {
        Serial.println("ON");
        bleServer.notifyText("ON");
    } else if (cmd == 0x00 || cmd == '0') {
        Serial.println("OFF");
        bleServer.notifyText("OFF");
    } else {
        Serial.printf("[BLE RECV] 0x%02X (Len: %u)\n", cmd, (unsigned int)length);
        bleServer.notifyText("UNKNOWN");
    }
    Serial.flush();
}

void setup() {
    Serial.begin(115200);
    delay(1000);
    Serial.println("==========================================");
    Serial.println(" Headless CC1101 BLE Firmware starting...");
    Serial.println(" Target MCU: Seeed Studio XIAO ESP32C3");
    Serial.println(" Device BLE Name: " BLE_DEVICE_NAME);
    Serial.println("==========================================");

    // Initialize BLE GATT Server
    bleServer.begin(handleBLERxData);
}

void loop() {
    static unsigned long lastLog = 0;
    if (millis() - lastLog > 5000) {
        lastLog = millis();
        Serial.printf(
            "[SYSTEM] BLE Status: %s | Peer MTU: %u\n",
            bleServer.isConnected() ? "Connected" : "Advertising",
            bleServer.getPeerMTU()
        );
        Serial.flush();
    }
    delay(10);
}
