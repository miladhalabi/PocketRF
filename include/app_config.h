#ifndef APP_CONFIG_H
#define APP_CONFIG_H

#include <Arduino.h>

// ---------------------------------------------------------------------------
// Hardware Pin Mapping (Seeed Studio XIAO ESP32C3)
// ---------------------------------------------------------------------------
// SCK  : D6 (GPIO 8)
// MISO : D7 (GPIO 9)
// MOSI : D8 (GPIO 10)
// CS   : D5 (GPIO 7)
// GDO0 : D1 (GPIO 3) - RMT Microsecond Rx/Tx Data Pin
// ---------------------------------------------------------------------------
#define CC1101_SCK_PIN 8
#define CC1101_MISO_PIN 9
#define CC1101_MOSI_PIN 10
#define CC1101_CS_PIN 7
#define CC1101_GDO0_PIN 3

// ---------------------------------------------------------------------------
// BLE Service & Characteristic UUIDs
// ---------------------------------------------------------------------------
#define BLE_DEVICE_NAME "XIAO-RF-BLE"
#define BLE_SERVICE_UUID "0000cc11-0000-1000-8000-00805f9b34fb"
#define BLE_CHAR_RX_UUID "0000cc12-0000-1000-8000-00805f9b34fb" // Write (App -> Device)
#define BLE_CHAR_TX_UUID "0000cc13-0000-1000-8000-00805f9b34fb" // Notify (Device -> App)

// ---------------------------------------------------------------------------
// Default RF Configuration Constants
// ---------------------------------------------------------------------------
#define DEFAULT_RF_FREQ 433.92f

#endif // APP_CONFIG_H
