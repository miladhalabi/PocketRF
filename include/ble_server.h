#ifndef BLE_SERVER_H
#define BLE_SERVER_H

#include "app_config.h"
#include <Arduino.h>
#include <NimBLEDevice.h>

// Function pointer type for handling incoming BLE RX data
typedef void (*BLEDataCallback)(const uint8_t *data, size_t length);

class BLEServerManager {
public:
    BLEServerManager();
    ~BLEServerManager();

    // Life cycle
    void begin(BLEDataCallback rxCallback = nullptr);
    void end();

    // Status
    bool isConnected() const { return m_connected; }
    uint16_t getPeerMTU() const { return m_peerMTU; }

    // Notifications (Device -> Phone/App)
    bool notifyText(const String &text);
    bool notifyBinary(const uint8_t *data, size_t length);

    // Internal Callback Handlers
    void handleConnect(NimBLEServer *pServer, NimBLEConnInfo &connInfo);
    void handleDisconnect(NimBLEServer *pServer, NimBLEConnInfo &connInfo, int reason);
    void handleMTUChange(uint16_t MTU, NimBLEConnInfo &connInfo);
    void handleWrite(NimBLECharacteristic *pCharacteristic, NimBLEConnInfo &connInfo);

private:
    NimBLEServer *m_pServer = nullptr;
    NimBLEService *m_pService = nullptr;
    NimBLECharacteristic *m_pTxChar = nullptr;
    NimBLECharacteristic *m_pRxChar = nullptr;

    bool m_connected = false;
    uint16_t m_peerMTU = 23;
    BLEDataCallback m_dataCallback = nullptr;

    class ServerCallbacks : public NimBLEServerCallbacks {
        BLEServerManager *m_mgr;

    public:
        ServerCallbacks(BLEServerManager *mgr) : m_mgr(mgr) {}
        void onConnect(NimBLEServer *pServer, NimBLEConnInfo &connInfo) override {
            m_mgr->handleConnect(pServer, connInfo);
        }
        void onDisconnect(NimBLEServer *pServer, NimBLEConnInfo &connInfo, int reason) override {
            m_mgr->handleDisconnect(pServer, connInfo, reason);
        }
        void onMTUChange(uint16_t MTU, NimBLEConnInfo &connInfo) override {
            m_mgr->handleMTUChange(MTU, connInfo);
        }
    } m_serverCallbacks;

    class CharCallbacks : public NimBLECharacteristicCallbacks {
        BLEServerManager *m_mgr;

    public:
        CharCallbacks(BLEServerManager *mgr) : m_mgr(mgr) {}
        void onWrite(NimBLECharacteristic *pCharacteristic, NimBLEConnInfo &connInfo) override {
            m_mgr->handleWrite(pCharacteristic, connInfo);
        }
    } m_charCallbacks;
};

extern BLEServerManager bleServer;

#endif // BLE_SERVER_H
