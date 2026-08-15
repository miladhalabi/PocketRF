#include "ble_server.h"

BLEServerManager bleServer;

BLEServerManager::BLEServerManager() : m_serverCallbacks(this), m_charCallbacks(this) {}

BLEServerManager::~BLEServerManager() { end(); }

void BLEServerManager::begin(BLEDataCallback rxCallback) {
    m_dataCallback = rxCallback;

    Serial.println("[BLE] Initializing NimBLE Device: " BLE_DEVICE_NAME);
    NimBLEDevice::init(BLE_DEVICE_NAME);

    // Set maximum MTU size (512 bytes for high-throughput pulse data)
    NimBLEDevice::setMTU(512);

    m_pServer = NimBLEDevice::createServer();
    m_pServer->setCallbacks(&m_serverCallbacks);

    m_pService = m_pServer->createService(BLE_SERVICE_UUID);

    // TX Characteristic: Notify data to Phone/App
    m_pTxChar = m_pService->createCharacteristic(
        BLE_CHAR_TX_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY | NIMBLE_PROPERTY::WRITE
    );
    m_pTxChar->setCallbacks(&m_charCallbacks);

    // RX Characteristic: Receive commands from Phone/App
    m_pRxChar = m_pService->createCharacteristic(
        BLE_CHAR_RX_UUID, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR
    );
    m_pRxChar->setCallbacks(&m_charCallbacks);

    // Start NimBLE Server to commit GATT services and characteristics to stack
    m_pServer->start();

    // Start BLE Advertising
    NimBLEAdvertising *pAdvertising = NimBLEDevice::getAdvertising();
    pAdvertising->setName(BLE_DEVICE_NAME);
    pAdvertising->addServiceUUID(BLE_SERVICE_UUID);
    pAdvertising->enableScanResponse(true);
    pAdvertising->start();

    Serial.println("[BLE] GATT Server active and advertising.");
    Serial.flush();
}

void BLEServerManager::end() {
    if (m_pServer) {
        NimBLEDevice::deinit(true);
        m_pServer = nullptr;
        m_pService = nullptr;
        m_pTxChar = nullptr;
        m_pRxChar = nullptr;
        m_connected = false;
        Serial.println("[BLE] GATT Server stopped.");
        Serial.flush();
    }
}

void BLEServerManager::handleConnect(NimBLEServer *pServer, NimBLEConnInfo &connInfo) {
    m_connected = true;
    m_peerMTU = connInfo.getMTU();
    Serial.printf(
        "[BLE] Client connected! Peer address: %s, MTU: %u\n",
        connInfo.getAddress().toString().c_str(),
        m_peerMTU
    );
    Serial.flush();
}

void BLEServerManager::handleDisconnect(NimBLEServer *pServer, NimBLEConnInfo &connInfo, int reason) {
    m_connected = false;
    Serial.printf("[BLE] Client disconnected (reason: %d). Restarting advertising...\n", reason);
    Serial.flush();
    NimBLEDevice::startAdvertising();
}

void BLEServerManager::handleMTUChange(uint16_t MTU, NimBLEConnInfo &connInfo) {
    m_peerMTU = MTU;
    Serial.printf("[BLE] Negotiated MTU updated: %u bytes\n", m_peerMTU);
    Serial.flush();
}

void BLEServerManager::handleWrite(NimBLECharacteristic *pCharacteristic, NimBLEConnInfo &connInfo) {
    NimBLEAttValue value = pCharacteristic->getValue();
    size_t len = value.size();
    const uint8_t *data = value.data();

    Serial.printf(
        "[BLE GATT WRITE] Char UUID: %s, Len: %u\n",
        pCharacteristic->getUUID().toString().c_str(),
        (unsigned int)len
    );
    Serial.flush();

    if (pCharacteristic == m_pRxChar || pCharacteristic->getUUID() == NimBLEUUID(BLE_CHAR_RX_UUID)) {
        if (len > 0 && m_dataCallback != nullptr) { m_dataCallback(data, len); }
    }
}

bool BLEServerManager::notifyText(const String &text) {
    return notifyBinary(reinterpret_cast<const uint8_t *>(text.c_str()), text.length());
}

bool BLEServerManager::notifyBinary(const uint8_t *data, size_t length) {
    if (!m_connected || m_pTxChar == nullptr) { return false; }
    m_pTxChar->setValue(data, length);
    return m_pTxChar->notify();
}
