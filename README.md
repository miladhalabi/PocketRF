# PocketRF 📡⚡

> ⚠️ **Notice:** This project is currently **under active development**. Features are being implemented incrementally.

**PocketRF** is a compact, headless **Sub-GHz RF transceiver system** built on the **Seeed Studio XIAO ESP32C3** and **TI CC1101** transceiver, controlled entirely via **Bluetooth Low Energy 5.0 (BLE)**.

Designed to eliminate screen and button dependencies, PocketRF turns any smartphone or terminal into a full-featured Sub-GHz command center.

---

## 🛠️ Hardware Pinout (Seeed Studio XIAO ESP32C3)

| Seeed XIAO ESP32C3 Pin | GPIO | CC1101 Pin | Function |
| :--- | :--- | :--- | :--- |
| **D6** | GPIO 8 | SCK | SPI Clock |
| **D7** | GPIO 9 | MISO | SPI Master In / Slave Out |
| **D8** | GPIO 10 | MOSI | SPI Master Out / Slave In |
| **D5** | GPIO 7 | CSN | SPI Chip Select |
| **D1** | GPIO 3 | GDO0 | Microsecond Rx/Tx Data Stream (RMT) |
| **3.3V / GND** | 3.3V / GND | VCC / GND | Power Supply |

---

## 🚀 Key Features

* **Headless Architecture:** Zero physical display or button dependencies; ultra-compact form factor.
* **BLE 5.0 GATT Interface:** High-throughput streaming (up to 512-byte MTU) with dual binary packet and text CLI interfaces.
* **Sub-GHz Capabilities:**
  * **Wideband Transceiver:** Supports 300–348 MHz, 387–464 MHz, and 779–928 MHz.
  * **Precise Calibration Engine:** Automated `FSCTRL0`, `FSCAL2`, `TEST0`, `FREND0/1` tuning for peak sensitivity.
  * **Microsecond Timing Core:** Uses ESP32 native RMT hardware peripherals (`rmt_rx` / `rmt_tx`) for microsecond pulse precision.
  * **Static OOK Protocol Engine:** Princeton, CAME, NICE_FLO, Linear, Clemsa, Mastercode, Ansonic, GateTX, Holtek/HT12, PhoenixV2, and RcSwitch families.
  * **KeeLoq Rolling Code Cipher:** 528-round NLF engine supporting Simple, Normal, Secure, Magic XOR, Pujol, and Erreka learning schemes.
  * **Spectrum / Waterfall Sweeper:** Real-time RSSI channel scanning.
  * **RF Jammer & Raw Signal Replay:** Continuous/Intermittent TX, hardware PN9 noise generator, and raw timing replay.

---

## 📶 BLE GATT Profile Contract

* **Device BLE Name:** `XIAO-RF-BLE`
* **Primary Service UUID:** `0000cc11-0000-1000-8000-00805f9b34fb`
  * **RX Characteristic (Write / Write Without Response):** `0000cc12-0000-1000-8000-00805f9b34fb` (Commands from App)
  * **TX Characteristic (Notify / Read):** `0000cc13-0000-1000-8000-00805f9b34fb` (Data/Notifications to App)

---

## 🔧 Building & Flashing

PocketRF uses **PlatformIO**.

```bash
# Compile firmware
~/.platformio/penv/bin/pio run

# Flash to Seeed Studio XIAO ESP32C3
~/.platformio/penv/bin/pio run -t upload

# Monitor Serial output
~/.platformio/penv/bin/pio device monitor
```

---

## 📋 License & Credits

* **Core RF Engines:** Extracted and adapted from the [Bruce Firmware](https://github.com/pr3y/Bruce).
* **CC1101 Driver:** Modified SmartRC CC1101 Driver Library.
* **BLE Stack:** NimBLE-Arduino.
