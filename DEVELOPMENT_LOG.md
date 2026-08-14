# PocketRF 📡⚡ - Comprehensive Log & Specification

> ⚠️ **Status:** This project is currently **under active development**.

## 1. Core Development Guidelines
- **Incremental Development:** Work part by part, feature by feature.
- **Halt and Wait:** After completing a feature, STOP and wait for testing.
- **Explicit Permission Required:** Do NOT proceed until the User explicitly asks.
- **Continuous Documentation:** Update this log after every feature.
- **No Mess:** Prioritize clean, working code.

---

## 2. Project Overview & System Architecture

### **Project Goal**
Extract the complete Sub-GHz RF capability (OOK Protocol Decoding/Encoding, KeeLoq Cipher Engine, ESP32 RMT Microsecond Timing, RSSI Spectrum/Waterfall Sweeper, RF Jammer, and RAW Signal Replay) from the Bruce firmware into a standalone **headless hardware device** (MCU + CC1101). All display, button, and GUI dependencies are stripped, exposing a low-latency **Bluetooth Low Energy 5.0 (BLE) GATT interface** for remote control via mobile apps or CLI tools.

### **Hardware Pinout Matrix (Seeed Studio XIAO ESP32C3)**

| Seeed XIAO ESP32C3 Pin | GPIO | CC1101 Transceiver Pin | Function |
| :--- | :--- | :--- | :--- |
| **D6** | GPIO 8 | SCK | SPI Clock |
| **D7** | GPIO 9 | MISO | SPI Master In / Slave Out |
| **D8** | GPIO 10 | MOSI | SPI Master Out / Slave In |
| **D5** | GPIO 7 | CSN | SPI Chip Select |
| **D1** | GPIO 3 | GDO0 | Microsecond Rx/Tx Data Stream (RMT) |
| **3.3V / GND** | 3.3V / GND | VCC / GND | Power Supply |

---

## 3. BLE Profile & Protocol Contract

* **Device BLE Name:** `XIAO-RF-BLE`
* **Primary Service UUID:** `0000cc11-0000-1000-8000-00805f9b34fb`
  * **RX Characteristic (Write / Write Without Response):** `0000cc12-0000-1000-8000-00805f9b34fb` (Commands in)
  * **TX Characteristic (Notify / Read):** `0000cc13-0000-1000-8000-00805f9b34fb` (Data & status out)
* **Negotiated MTU:** Up to 512 bytes.

---

## 4. Completed Milestones & Progress Log

- [x] **Feature 1: Directory Setup & Incremental Guidelines**
  * Created `/home/admin/Lab/firmware/headless_cc1101_ble/` workspace.
  * Established 5 core rules: *Incremental Development, Halt & Wait, Explicit Permission Required, Continuous Documentation, No Mess*.
- [x] **Feature 2: PlatformIO Setup & Hardware Configuration**
  * Configured `platformio.ini` for `seeed_xiao_esp32c3` with dependencies (`NimBLE-Arduino@2.5.1`, `SmartRC-CC1101-Driver-Lib`, `ArduinoJson@7.4.3`).
  * Created `include/app_config.h` defining SPI pins, GDO0 RMT pin, and BLE UUIDs.
  * Built clean initial skeleton (`src/main.cpp`).
- [x] **Feature 3: BLE GATT NimBLE Server Implementation**
  * Built `include/ble_server.h` and `src/ble_server.cpp` using NimBLE-Arduino.
  * Implemented 512-byte MTU negotiation, `notifyText()`, and `notifyBinary()`.
  * Refactored callback architecture with decoupled single-inheritance inner classes (`ServerCallbacks`, `CharCallbacks`) to guarantee virtual function dispatch.
  * Added explicit `m_pServer->start()` GATT database registration.
  * Added `Serial.flush()` for USB CDC logging.
  * **Verified 100% working:** Tested via Linux Python `bleak` script and two-way BLE notifications.

---

## 5. Planned Milestones Roadmap

- [ ] **Feature 4: CC1101 Driver & Precise Frequency Calibration Extraction**
  * Extract `structs.h`, `cc1101_driver.h/.cpp`, and `rf_utils.h/.cpp`.
  * Implement `cc1101ApplyPreciseCalibration` (`FSCTRL0`, `FSCAL2`, `TEST0`, `FREND0/1`) for 315MHz, 433MHz, 868MHz, 915MHz bands.
  * Add BLE self-test command (`0x01` / `INIT`) verifying MARCSTATE and version byte.
- [ ] **Feature 5: ESP32 RMT Microsecond Timing Core (`rf_signal_engine`)**
  * Implement `rmt_rx` channel (1 µs tick precision, non-blocking ring buffer).
  * Implement `rmt_tx` channel (sub-microsecond signed pulse timing sequence transmitter on GPIO 3).
- [ ] **Feature 6: Protocol Registry & KeeLoq Engine Integration**
  * Integrate static OOK protocol database (`rf_registry`: Princeton, CAME, Nice, Holtek, Linear, Clemsa, Mastercode, Ansonic, GateTX, PhoenixV2, RcSwitch).
  * Integrate generic OOK decoder (`rf_decoder`) and encoder (`rf_encoder`).
  * Integrate KeeLoq block cipher, manufacturer keystore `/mfcodes`, and learning schemes (`rf_keeloq`).
  * Add RAW pulse sequence generator & CRC-64 signal deduplication.
- [ ] **Feature 7: Dual Command Parser (Binary Packets + ASCII Text CLI)**
  * **Binary Mode:** Structured 0xAA frames for Android mobile app (`SET_FREQ`, `START_RX`, `STOP_RX`, `TX_PROTO`, `TX_KEELOQ`, `TX_RAW`, `START_SWEEP`, `JAMMER_CTRL`).
  * **ASCII Text Mode:** Terminal commands (`subghz freq`, `subghz rx`, `subghz txp`, `subghz keeloqtx`, `subghz sweep`, `subghz jam`).
- [ ] **Feature 8: RF Service State Machine Manager (`rf_service`)**
  * Manage active device states (`IDLE`, `RX_DECODED`, `RX_RAW`, `TX_ACTIVE`, `SWEEP_ACTIVE`, `JAMMER_ACTIVE`).
  * Real-time RSSI sweep streamer over BLE notifications for Waterfall UI.
  * Jammer controller with safety auto-cutoff timers.
