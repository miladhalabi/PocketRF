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

// Prefix for a full, possibly multi-line, Flipper-style .sub RAW payload.
// Checked against the raw bytes (not newline-truncated) so embedded '\n'
// characters inside the .sub text survive, unlike the generic single-line
// ASCII command path below.
#define RAWSUB_PREFIX "RAWSUB "

void CmdRouter::processInput(const uint8_t *data, size_t length) {
    if (length == 0 || data == nullptr) return;

    if (data[0] == BINARY_FRAME_HEADER) {
        processBinaryFrame(data, length);
        return;
    }

    const size_t prefixLen = sizeof(RAWSUB_PREFIX) - 1;
    if (length > prefixLen && memcmp(data, RAWSUB_PREFIX, prefixLen) == 0) {
        String subText((const char *)(data + prefixLen), length - prefixLen);
        processRawSubCommand(subText);
        return;
    }

    String cmdStr = "";
    for (size_t i = 0; i < length; i++) {
        if (data[i] == '\r' || data[i] == '\n') break;
        cmdStr += (char)data[i];
    }
    cmdStr.trim();
    processAsciiCommand(cmdStr);
}

void CmdRouter::processRawSubCommand(const String &subText) {
    JsonDocument doc;
    bool ok = rf_raw_tx_sub(subText);
    doc["status"] = ok ? "ok" : "error";
    doc["cmd"] = "RAWSUB";
    if (!ok) doc["msg"] = "No RAW_Data found or transmit failed";
    String responseStr;
    serializeJson(doc, responseStr);
    bleServer.notifyText(responseStr);
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
            bool ok = rfService.startRxDecoded(cc1101Driver.getFrequency());
            respBuf[2] = 1;
            respBuf[3] = ok ? 0x00 : 0x01;
            respLen = 4;
            break;
        }
        case CMD_STOP_RX: {
            rfService.stop();
            respBuf[2] = 1;
            respBuf[3] = 0x00;
            respLen = 4;
            break;
        }
        case CMD_START_LISTEN: {
            bool ok = rfService.startListen(cc1101Driver.getFrequency());
            respBuf[2] = 1;
            respBuf[3] = ok ? 0x00 : 0x01;
            respLen = 4;
            break;
        }
        case CMD_STOP_ALL: {
            rfService.stop();
            respBuf[2] = 1;
            respBuf[3] = 0x00;
            respLen = 4;
            break;
        }
        case CMD_START_SPECTRUM_FIXED: {
            bool ok = rfService.startSpectrumFixed(cc1101Driver.getFrequency());
            respBuf[2] = 1;
            respBuf[3] = ok ? 0x00 : 0x01;
            respLen = 4;
            break;
        }
        case CMD_START_SPECTRUM_SWEEP: {
            float startF = 300.0f;
            float endF = 928.0f;
            float stepF = 1.0f;
            if (payloadLen >= 12) {
                memcpy(&startF, payload, 4);
                memcpy(&endF, &payload[4], 4);
                memcpy(&stepF, &payload[8], 4);
            }
            bool ok = rfService.startSpectrumSweep(startF, endF, stepF);
            respBuf[2] = 1;
            respBuf[3] = ok ? 0x00 : 0x01;
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
        case CMD_TX_RAW: {
            // Payload: freqMhz(float32) + count(uint16) + durations[count] (int16 each)
            if (payloadLen >= 6) {
                float freqMhz = 0;
                memcpy(&freqMhz, payload, 4);
                uint16_t count = 0;
                memcpy(&count, &payload[4], 2);
                bool ok = false;
                if (payloadLen >= (size_t)(6 + count * 2)) {
                    std::vector<int> durations;
                    durations.reserve(count);
                    for (uint16_t i = 0; i < count; i++) {
                        int16_t d = 0;
                        memcpy(&d, &payload[6 + i * 2], 2);
                        durations.push_back((int)d);
                    }
                    ok = rf_raw_tx_durations(durations, freqMhz);
                }
                respBuf[2] = 1;
                respBuf[3] = ok ? 0x00 : 0x01;
            } else {
                respBuf[2] = 1;
                respBuf[3] = 0xFF;
            }
            respLen = 4;
            break;
        }
        case CMD_START_JAM: {
            // Payload: mode(uint8) + freqMhz(float32)
            bool ok = false;
            if (payloadLen >= 5) {
                uint8_t mode = payload[0];
                float freqMhz = 0;
                memcpy(&freqMhz, &payload[1], 4);
                if (mode <= (uint8_t)RfJamMode::SWEEP) { ok = rfService.startJam((RfJamMode)mode, freqMhz); }
            }
            respBuf[2] = 1;
            respBuf[3] = ok ? 0x00 : 0x01;
            respLen = 4;
            break;
        }
        case CMD_START_BRUTEFORCE: {
            // Payload: protoIdx(uint8) + bits(uint8, 0=default) + freqMhz(float32) + repeat(uint8)
            bool ok = false;
            if (payloadLen >= 7) {
                uint8_t protoIdx = payload[0];
                uint8_t bits = payload[1];
                float freqMhz = 0;
                memcpy(&freqMhz, &payload[2], 4);
                uint8_t repeat = payload[6];
                const RfProtocolDef *def = rf_protocol_at(protoIdx);
                if (def) { ok = rfService.startBruteforce(def, bits, freqMhz, repeat ? repeat : 1); }
            }
            respBuf[2] = 1;
            respBuf[3] = ok ? 0x00 : 0x01;
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
            bool ok = rfService.startRxDecoded(cc1101Driver.getFrequency());
            doc["status"] = ok ? "ok" : "error";
            doc["cmd"] = "subghz rx";
            doc["active"] = (rfService.getState() == RfState::RX_DECODED);
        } else if (lowerSub == "rx stop" || lowerSub == "stop") {
            rfService.stop();
            doc["status"] = "ok";
            doc["cmd"] = "subghz stop";
            doc["active"] = false;
        } else if (lowerSub == "listen") {
            bool ok = rfService.startListen(cc1101Driver.getFrequency());
            doc["status"] = ok ? "ok" : "error";
            doc["cmd"] = "subghz listen";
            doc["active"] = (rfService.getState() == RfState::LISTEN);
        } else if (lowerSub.startsWith("sweep")) {
            // subghz sweep [start_mhz] [end_mhz] [step_mhz]
            String args = subCmd.substring(5);
            args.trim();
            float startF = 300.0f, endF = 928.0f, stepF = 1.0f;

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

            if (tokens.size() >= 1) startF = tokens[0].toFloat();
            if (tokens.size() >= 2) endF = tokens[1].toFloat();
            if (tokens.size() >= 3) stepF = tokens[2].toFloat();

            bool ok = rfService.startSpectrumSweep(startF, endF, stepF);
            doc["status"] = ok ? "ok" : "error";
            doc["cmd"] = "subghz sweep";
            doc["start_freq"] = startF;
            doc["end_freq"] = endF;
            doc["step_mhz"] = stepF;
        } else if (lowerSub.startsWith("rssi")) {
            String valStr = subCmd.substring(4);
            valStr.trim();
            float freqMhz = valStr.length() > 0 ? valStr.toFloat() : cc1101Driver.getFrequency();
            bool ok = rfService.startSpectrumFixed(freqMhz);
            doc["status"] = ok ? "ok" : "error";
            doc["cmd"] = "subghz rssi";
            doc["freq"] = freqMhz;
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
        } else if (lowerSub.startsWith("jam ") || lowerSub == "jam") {
            // subghz jam <full|itmt|noise|sweep> [freq_mhz_or_hz]
            String args = (lowerSub == "jam") ? "" : subCmd.substring(4);
            args.trim();
            int sp = args.indexOf(' ');
            String modeStr = (sp < 0) ? args : args.substring(0, sp);
            String freqStr = (sp < 0) ? "" : args.substring(sp + 1);
            freqStr.trim();
            modeStr.toLowerCase();

            RfJamMode mode = RfJamMode::FULL;
            bool validMode = true;
            if (modeStr == "full") mode = RfJamMode::FULL;
            else if (modeStr == "itmt") mode = RfJamMode::ITMT;
            else if (modeStr == "noise") mode = RfJamMode::NOISE;
            else if (modeStr == "sweep") mode = RfJamMode::SWEEP;
            else validMode = false;

            if (validMode) {
                float freqMhz = freqStr.length() > 0 ? freqStr.toFloat() : cc1101Driver.getFrequency();
                if (freqMhz > 10000.0f) freqMhz /= 1000000.0f;
                bool ok = rfService.startJam(mode, freqMhz);
                doc["status"] = ok ? "ok" : "error";
                doc["cmd"] = "subghz jam";
                doc["mode"] = modeStr;
                doc["freq"] = cc1101Driver.getFrequency();
            } else {
                doc["status"] = "error";
                doc["msg"] = "Unknown jam mode (full|itmt|noise|sweep)";
            }
        } else if (lowerSub.startsWith("brute ")) {
            // subghz brute <proto> [bits] [freq_mhz_or_hz] [repeat]
            String args = subCmd.substring(6);
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

            if (tokens.size() >= 1) {
                const RfProtocolDef *def = rf_find_protocol(tokens[0]);
                int bits = (tokens.size() >= 2) ? tokens[1].toInt() : 0;
                float freqMhz = (tokens.size() >= 3) ? tokens[2].toFloat() : cc1101Driver.getFrequency();
                if (freqMhz > 10000.0f) freqMhz /= 1000000.0f;
                int repeat = (tokens.size() >= 4) ? tokens[3].toInt() : 1;

                if (def) {
                    bool ok = rfService.startBruteforce(def, bits, freqMhz, repeat);
                    doc["status"] = ok ? "ok" : "error";
                    doc["cmd"] = "subghz brute";
                    doc["proto"] = def->name;
                    doc["bits"] = bits ? bits : def->bits;
                    doc["freq"] = cc1101Driver.getFrequency();
                } else {
                    doc["status"] = "error";
                    doc["msg"] = "Unknown protocol name";
                }
            } else {
                doc["status"] = "error";
                doc["msg"] = "Invalid brute parameters";
            }
        } else if (lowerSub.startsWith("rawtx ")) {
            // subghz rawtx <freq_mhz_or_hz> <d1> <d2> <d3> ...
            String args = subCmd.substring(6);
            args.trim();
            int sp = args.indexOf(' ');
            if (sp > 0) {
                float freqMhz = args.substring(0, sp).toFloat();
                if (freqMhz > 10000.0f) freqMhz /= 1000000.0f;
                String rest = args.substring(sp + 1);
                rest.trim();

                std::vector<int> durations;
                int start = 0;
                while (start < (int)rest.length()) {
                    int nextSpace = rest.indexOf(' ', start);
                    String tok =
                        (nextSpace < 0) ? rest.substring(start) : rest.substring(start, nextSpace);
                    start = (nextSpace < 0) ? rest.length() : nextSpace + 1;
                    if (tok.length() > 0) durations.push_back(tok.toInt());
                }

                bool ok = rf_raw_tx_durations(durations, freqMhz);
                doc["status"] = ok ? "ok" : "error";
                doc["cmd"] = "subghz rawtx";
                doc["count"] = (int)durations.size();
                doc["freq"] = freqMhz;
            } else {
                doc["status"] = "error";
                doc["msg"] = "Invalid rawtx parameters";
            }
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
        bool ok = rfService.startRxDecoded(cc1101Driver.getFrequency());
        doc["status"] = ok ? "ok" : "error";
        doc["cmd"] = "RMT RX START";
        doc["active"] = (rfService.getState() == RfState::RX_DECODED);
    } else if (lowerStr.equalsIgnoreCase("RMT RX STOP") || lowerStr.equalsIgnoreCase("STOP")) {
        rfService.stop();
        doc["status"] = "ok";
        doc["cmd"] = "STOP";
        doc["active"] = false;
    } else if (lowerStr.equalsIgnoreCase("LISTEN START")) {
        bool ok = rfService.startListen(cc1101Driver.getFrequency());
        doc["status"] = ok ? "ok" : "error";
        doc["cmd"] = "LISTEN START";
        doc["active"] = (rfService.getState() == RfState::LISTEN);
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
