#include "cmd_router.h"
#include "ble_server.h"

CmdRouter cmdRouter;

CmdRouter::CmdRouter() {}

uint8_t CmdRouter::calculateCrc8(const uint8_t *data, size_t len) {
    uint8_t crc = 0x00;
    for (size_t i = 0; i < len; i++) {
        crc ^= data[i];
        for (uint8_t b = 0; b < 8; b++) {
            if (crc & 0x80) crc = (crc << 1) ^ 0x31;
            else crc <<= 1;
        }
    }
    return crc;
}

void CmdRouter::processInput(const uint8_t *data, size_t length) {
    if (length == 0 || data == nullptr) return;

    if (data[0] == BINARY_FRAME_HEADER) {
        processBinaryFrame(data, length);
    } else {
        String cmdStr = "";
        for (size_t i = 0; i < length; i++) {
            if (data[i] == '\r' || data[i] == '\n') break;
            cmdStr += (char)data[i];
        }
        cmdStr.trim();
        processAsciiCommand(cmdStr);
    }
}

void CmdRouter::processBinaryFrame(const uint8_t *data, size_t length) {
    // Minimum binary frame: [0xAA] [CMD_ID] [PAYLOAD_LEN] [CRC8]
    if (length < 4) {
        Serial.println("[CMD ROUTER] Invalid binary frame length (<4)");
        return;
    }

    uint8_t cmdId = data[1];
    uint8_t payloadLen = data[2];

    if (length < (size_t)(3 + payloadLen + 1)) {
        Serial.println("[CMD ROUTER] Binary frame payload truncated");
        return;
    }

    uint8_t rxCrc = data[3 + payloadLen];
    uint8_t calcCrc = calculateCrc8(data, 3 + payloadLen);

    if (rxCrc != calcCrc) {
        Serial.printf("[CMD ROUTER] Binary CRC mismatch! Received: 0x%02X, Calc: 0x%02X\n", rxCrc, calcCrc);
        return;
    }

    const uint8_t *payload = &data[3];
    uint8_t respBuf[64];
    size_t respLen = 0;

    respBuf[0] = BINARY_FRAME_HEADER;
    respBuf[1] = cmdId | 0x80; // Response ID

    switch (cmdId) {
        case CMD_STATUS: {
            respBuf[2] = 11; // Payload size
            respBuf[3] = cc1101Driver.isDetected() ? 0x01 : 0x00;
            respBuf[4] = cc1101Driver.getVersion();
            respBuf[5] = cc1101Driver.getPartNum();
            respBuf[6] = cc1101Driver.getMarcState();

            uint32_t freqHz = (uint32_t)(cc1101Driver.getFrequency() * 1000000.0f);
            memcpy(&respBuf[7], &freqHz, 4);

            int8_t rssiVal = (int8_t)cc1101Driver.getRssi();
            respBuf[11] = (uint8_t)rssiVal;
            respBuf[12] = rfSignalEngine.isRxActive() ? 0x01 : 0x00;
            respLen = 13;
            break;
        }
        case CMD_SET_FREQ: {
            if (payloadLen >= 4) {
                uint32_t freqHz = 0;
                memcpy(&freqHz, payload, 4);
                float freqMhz = (float)freqHz / 1000000.0f;
                cc1101Driver.setFrequency(freqMhz, false);
            }
            respBuf[2] = 1;
            respBuf[3] = 0x00; // OK
            respLen = 4;
            break;
        }
        case CMD_START_RX: {
            bool ok = rfSignalEngine.startRx(cc1101Driver.getFrequency());
            respBuf[2] = 1;
            respBuf[3] = ok ? 0x00 : 0x01;
            respLen = 4;
            break;
        }
        case CMD_STOP_RX: {
            rfSignalEngine.stopRx();
            respBuf[2] = 1;
            respBuf[3] = 0x00;
            respLen = 4;
            break;
        }
        case CMD_TX_PROTO: {
            if (payloadLen >= 10) {
                uint8_t protoIdx = payload[0];
                uint8_t bits = payload[1];
                uint64_t key = 0;
                memcpy(&key, &payload[2], 8);

                const RfProtocolDef *def = rf_protocol_at(protoIdx);
                bool ok = false;
                if (def) { ok = rf_tx_protocol(key, bits ? bits : def->bits, def->te, def, 10); }
                respBuf[2] = 1;
                respBuf[3] = ok ? 0x00 : 0x01;
            } else {
                respBuf[2] = 1;
                respBuf[3] = 0xFF; // Invalid payload
            }
            respLen = 4;
            break;
        }
        case CMD_TX_KEELOQ: {
            if (payloadLen >= 8) {
                uint64_t key = 0;
                memcpy(&key, payload, 8);
                bool ok = rf_tx_keeloq(key, 10);
                respBuf[2] = 1;
                respBuf[3] = ok ? 0x00 : 0x01;
            } else {
                respBuf[2] = 1;
                respBuf[3] = 0xFF;
            }
            respLen = 4;
            break;
        }
        default:
            respBuf[2] = 1;
            respBuf[3] = 0xFE; // Unknown command
            respLen = 4;
            break;
    }

    uint8_t respCrc = calculateCrc8(respBuf, respLen);
    respBuf[respLen] = respCrc;
    respLen++;

    Serial.printf(
        "[CMD ROUTER] Sending binary response for CMD 0x%02X (%u bytes)\n", cmdId, (unsigned int)respLen
    );
    bleServer.notifyBinary(respBuf, respLen);
}

void CmdRouter::processAsciiCommand(const String &cmdStr) {
    Serial.printf("[CMD ROUTER] Processing ASCII command: '%s'\n", cmdStr.c_str());
    JsonDocument doc;

    String lowerStr = cmdStr;
    lowerStr.toLowerCase();

    // Check Bruce CLI syntax (e.g. subghz freq 433920000, subghz rx, subghz txp CAME ...)
    if (lowerStr.startsWith("subghz ")) {
        String subCmd = cmdStr.substring(7);
        subCmd.trim();
        String lowerSub = subCmd;
        lowerSub.toLowerCase();

        if (lowerSub.startsWith("freq ")) {
            String valStr = subCmd.substring(5);
            valStr.trim();
            float freqMhz = valStr.toFloat();
            if (freqMhz > 10000.0f) freqMhz /= 1000000.0f; // Input was in Hz (e.g. 433920000)

            cc1101Driver.setFrequency(freqMhz, false);
            doc["status"] = "ok";
            doc["cmd"] = "subghz freq";
            doc["freq"] = cc1101Driver.getFrequency();
        } else if (lowerSub == "rx") {
            bool ok = rfSignalEngine.startRx(cc1101Driver.getFrequency());
            doc["status"] = ok ? "ok" : "error";
            doc["cmd"] = "subghz rx";
            doc["active"] = rfSignalEngine.isRxActive();
        } else if (lowerSub == "rx stop") {
            rfSignalEngine.stopRx();
            doc["status"] = "ok";
            doc["cmd"] = "subghz rx stop";
            doc["active"] = rfSignalEngine.isRxActive();
        } else if (lowerSub.startsWith("txp ")) {
            // subghz txp <proto> <freq_hz> <bits> <key_hex> [te] [repeat]
            String args = subCmd.substring(4);
            args.trim();

            std::vector<String> tokens;
            int start = 0;
            while (start < (int)args.length()) {
                int nextSpace = args.indexOf(' ', start);
                if (nextSpace < 0) {
                    tokens.push_back(args.substring(start));
                    break;
                }
                tokens.push_back(args.substring(start, nextSpace));
                start = nextSpace + 1;
                while (start < (int)args.length() && args[start] == ' ') start++;
            }

            if (tokens.size() >= 4) {
                String protoName = tokens[0];
                float freqMhz = tokens[1].toFloat();
                if (freqMhz > 10000.0f) freqMhz /= 1000000.0f;

                unsigned int bits = (unsigned int)tokens[2].toInt();
                uint64_t key = strtoull(tokens[3].c_str(), NULL, 16);
                int te = (tokens.size() >= 5) ? tokens[4].toInt() : 0;
                int repeat = (tokens.size() >= 6) ? tokens[5].toInt() : 10;

                const RfProtocolDef *def = rf_find_protocol(protoName);
                if (def) {
                    cc1101Driver.setFrequency(freqMhz, true);
                    bool ok = rf_tx_protocol(key, bits ? bits : def->bits, te ? te : def->te, def, repeat);
                    doc["status"] = ok ? "ok" : "error";
                    doc["cmd"] = "subghz txp";
                    doc["proto"] = def->name;
                    doc["freq"] = cc1101Driver.getFrequency();
                } else {
                    doc["status"] = "error";
                    doc["msg"] = "Unknown protocol name";
                }
            } else {
                doc["status"] = "error";
                doc["msg"] = "Invalid txp parameters";
            }
        } else if (lowerSub.startsWith("keeloqtx ")) {
            String keyStr = subCmd.substring(9);
            keyStr.trim();
            uint64_t key = strtoull(keyStr.c_str(), NULL, 16);
            bool ok = rf_tx_keeloq(key, 10);
            doc["status"] = ok ? "ok" : "error";
            doc["cmd"] = "subghz keeloqtx";
            doc["key"] = keyStr;
        } else if (lowerSub == "status") {
            doc["status"] = "ok";
            doc["cmd"] = "subghz status";
            doc["detected"] = cc1101Driver.isDetected();
            doc["version"] = cc1101Driver.getVersion();
            doc["partnum"] = cc1101Driver.getPartNum();
            doc["marcstate"] = cc1101Driver.getMarcState();
            doc["freq"] = cc1101Driver.getFrequency();
            doc["rssi"] = cc1101Driver.getRssi();
            doc["rmt_rx"] = rfSignalEngine.isRxActive();
        } else {
            doc["status"] = "error";
            doc["msg"] = "Unknown subghz sub-command";
        }
    }
    // PocketRF standard JSON/CLI commands
    else if (lowerStr.equalsIgnoreCase("STATUS") || lowerStr.equalsIgnoreCase("INIT") || cmdStr[0] == 0x01) {
        doc["status"] = "ok";
        doc["cmd"] = "STATUS";
        doc["detected"] = cc1101Driver.isDetected();
        doc["version"] = cc1101Driver.getVersion();
        doc["partnum"] = cc1101Driver.getPartNum();
        doc["marcstate"] = cc1101Driver.getMarcState();
        doc["freq"] = cc1101Driver.getFrequency();
        doc["rssi"] = cc1101Driver.getRssi();
        doc["rmt_rx"] = rfSignalEngine.isRxActive();
    } else if (lowerStr.startsWith("freq ")) {
        float freq = cmdStr.substring(5).toFloat();
        if (freq > 10000.0f) freq /= 1000000.0f;
        if (freq > 0) {
            cc1101Driver.setFrequency(freq, false);
            doc["status"] = "ok";
            doc["cmd"] = "FREQ";
            doc["freq"] = cc1101Driver.getFrequency();
        } else {
            doc["status"] = "error";
            doc["msg"] = "Invalid frequency format";
        }
    } else if (lowerStr.equalsIgnoreCase("MODE RX")) {
        cc1101Driver.setRxMode(cc1101Driver.getFrequency());
        doc["status"] = "ok";
        doc["cmd"] = "MODE";
        doc["mode"] = "RX";
    } else if (lowerStr.equalsIgnoreCase("MODE TX")) {
        cc1101Driver.setTxMode(cc1101Driver.getFrequency());
        doc["status"] = "ok";
        doc["cmd"] = "MODE";
        doc["mode"] = "TX";
    } else if (lowerStr.equalsIgnoreCase("MODE IDLE")) {
        cc1101Driver.setIdle();
        doc["status"] = "ok";
        doc["cmd"] = "MODE";
        doc["mode"] = "IDLE";
    } else if (lowerStr.equalsIgnoreCase("RMT RX START")) {
        bool ok = rfSignalEngine.startRx(cc1101Driver.getFrequency());
        doc["status"] = ok ? "ok" : "error";
        doc["cmd"] = "RMT RX START";
        doc["active"] = rfSignalEngine.isRxActive();
    } else if (lowerStr.equalsIgnoreCase("RMT RX STOP")) {
        rfSignalEngine.stopRx();
        doc["status"] = "ok";
        doc["cmd"] = "RMT RX STOP";
        doc["active"] = rfSignalEngine.isRxActive();
    } else if (lowerStr.startsWith("tx_proto ")) {
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
                bool ok = rf_tx_protocol(key, def->bits ? def->bits : 24, def->te, def, 10);
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
    } else if (lowerStr.startsWith("tx_keeloq ")) {
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
