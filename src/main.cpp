#include "app_config.h"
#include "ble_server.h"
#include "cc1101_driver.h"
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

    // Build String from command
    String cmdStr = "";
    for (size_t i = 0; i < length; i++) {
        if (data[i] == '\r' || data[i] == '\n') break;
        cmdStr += (char)data[i];
    }
    cmdStr.trim();

    Serial.printf("[BLE CMD] Received: '%s' (Len: %u)\n", cmdStr.c_str(), (unsigned int)length);

    JsonDocument doc;

    if (cmdStr.equalsIgnoreCase("STATUS") || cmdStr.equalsIgnoreCase("INIT") || data[0] == 0x01) {
        doc["status"] = "ok";
        doc["cmd"] = "STATUS";
        doc["detected"] = cc1101Driver.isDetected();
        doc["version"] = cc1101Driver.getVersion();
        doc["partnum"] = cc1101Driver.getPartNum();
        doc["marcstate"] = cc1101Driver.getMarcState();
        doc["freq"] = cc1101Driver.getFrequency();
        doc["rssi"] = cc1101Driver.getRssi();
        doc["rmt_rx"] = rfSignalEngine.isRxActive();
    } else if (cmdStr.startsWith("FREQ ") || cmdStr.startsWith("freq ")) {
        float freq = cmdStr.substring(5).toFloat();
        if (freq > 0) {
            cc1101Driver.setFrequency(freq, false);
            doc["status"] = "ok";
            doc["cmd"] = "FREQ";
            doc["freq"] = cc1101Driver.getFrequency();
        } else {
            doc["status"] = "error";
            doc["msg"] = "Invalid frequency format";
        }
    } else if (cmdStr.equalsIgnoreCase("MODE RX")) {
        cc1101Driver.setRxMode(cc1101Driver.getFrequency());
        doc["status"] = "ok";
        doc["cmd"] = "MODE";
        doc["mode"] = "RX";
    } else if (cmdStr.equalsIgnoreCase("MODE TX")) {
        cc1101Driver.setTxMode(cc1101Driver.getFrequency());
        doc["status"] = "ok";
        doc["cmd"] = "MODE";
        doc["mode"] = "TX";
    } else if (cmdStr.equalsIgnoreCase("MODE IDLE")) {
        cc1101Driver.setIdle();
        doc["status"] = "ok";
        doc["cmd"] = "MODE";
        doc["mode"] = "IDLE";
    } else if (cmdStr.equalsIgnoreCase("RMT RX START")) {
        bool ok = rfSignalEngine.startRx(cc1101Driver.getFrequency());
        doc["status"] = ok ? "ok" : "error";
        doc["cmd"] = "RMT RX START";
        doc["active"] = rfSignalEngine.isRxActive();
    } else if (cmdStr.equalsIgnoreCase("RMT RX STOP")) {
        rfSignalEngine.stopRx();
        doc["status"] = "ok";
        doc["cmd"] = "RMT RX STOP";
        doc["active"] = rfSignalEngine.isRxActive();
    } else if (cmdStr.startsWith("TX_PROTO ")) {
        // Format: TX_PROTO <proto_name> <key_hex> [bits] [te] [repeat]
        String args = cmdStr.substring(9);
        args.trim();

        int firstSpace = args.indexOf(' ');
        if (firstSpace > 0) {
            String protoName = args.substring(0, firstSpace);
            String rest = args.substring(firstSpace + 1);
            rest.trim();

            int secondSpace = rest.indexOf(' ');
            String keyHexStr = (secondSpace > 0) ? rest.substring(0, secondSpace) : rest;

            uint64_t key = strtoull(keyHexStr.c_str(), NULL, 16);
            const RfProtocolDef *def = rf_find_protocol(protoName);

            if (def != nullptr) {
                unsigned int bits = def->bits ? def->bits : 24;
                int te = def->te;
                int repeat = 10;

                bool ok = rf_tx_protocol(key, bits, te, def, repeat);
                doc["status"] = ok ? "ok" : "error";
                doc["cmd"] = "TX_PROTO";
                doc["proto"] = def->name;
                doc["key"] = keyHexStr;
            } else {
                doc["status"] = "error";
                doc["msg"] = "Unknown protocol name";
            }
        } else {
            doc["status"] = "error";
            doc["msg"] = "Invalid TX_PROTO format";
        }
    } else if (cmdStr.startsWith("TX_KEELOQ ")) {
        String keyHexStr = cmdStr.substring(10);
        keyHexStr.trim();
        uint64_t key = strtoull(keyHexStr.c_str(), NULL, 16);

        bool ok = rf_tx_keeloq(key, 10);
        doc["status"] = ok ? "ok" : "error";
        doc["cmd"] = "TX_KEELOQ";
        doc["key"] = keyHexStr;
    } else {
        doc["status"] = "unknown_command";
        doc["raw"] = cmdStr;
    }

    String responseStr;
    serializeJson(doc, responseStr);
    Serial.printf("[BLE RESP] %s\n", responseStr.c_str());
    Serial.flush();
    bleServer.notifyText(responseStr);
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
