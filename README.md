# PocketRF 📡⚡

> ⚠️ **Notice:** This project is currently **under active development**. Features are being implemented incrementally.

**PocketRF** is a compact, headless **Sub-GHz RF transceiver system** built on the **Seeed Studio XIAO ESP32C3** and **TI CC1101** transceiver, controlled entirely via **Bluetooth Low Energy 5.0 (BLE)**.

Designed to eliminate screen and button dependencies, PocketRF turns any smartphone or terminal into a full-featured Sub-GHz command center. The repo has three parts: the ESP32C3 **firmware**, a native **Android companion app**, and a **Python CLI** for quick interactive testing from a laptop.

---

## 🛠️ Hardware Pinout (Seeed Studio XIAO ESP32C3)

| Seeed XIAO ESP32C3 Pin | GPIO | CC1101 Pin | Function |
| :--- | :--- | :--- | :--- |
| **D10** | GPIO 10 | SCK | SPI Clock |
| **D2** | GPIO 4 | MISO | SPI Master In / Slave Out (Safe pin) |
| **D3** | GPIO 5 | MOSI | SPI Master Out / Slave In |
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
  * **KeeLoq Rolling Code Cipher:** 528-round NLF engine supporting Simple, Normal, Secure, Magic XOR, Pujol, and Erreka learning schemes, with manufacturer identification against ~30 real brands (Aprimatic, Centurion, NICE, FAAC, etc.) via an embedded, AES-encrypted keystore.
  * **Multi-Modulation Presets:** OOK/ASK, 2-FSK, MSK and GFSK presets, matching Flipper Zero's `.sub` preset names.
  * **Spectrum / Waterfall Sweeper:** Real-time RSSI channel scanning.
  * **RF Jammer:** Full-power, intermittent, CC1101 PN9 hardware noise, and ±5MHz frequency-sweep modes, with a 30s hard safety cutoff.
  * **Bruteforce Key Generator:** Enumerates the full keyspace of a fixed-code OOK protocol (CAME 12-bit, Princeton 24-bit, Nice FLO 12-bit, etc.).
  * **RAW Signal Replay:** Flipper-compatible `.sub` RAW file parser + RMT-precision replay, plus chunked RAW capture/replay over BLE for captures too long for one notification/write.
  * **Auto Scan + Copy:** Sweeps all 3 ISM bands for an active signal, locks on, and decodes automatically (KeeLoq identified by manufacturer when recognized).

---

## 📶 BLE GATT Profile Contract

* **Device BLE Name:** `XIAO-RF-BLE`
* **Primary Service UUID:** `0000cc11-0000-1000-8000-00805f9b34fb`
  * **RX Characteristic (Write / Write Without Response):** `0000cc12-0000-1000-8000-00805f9b34fb` (Commands from App)
  * **TX Characteristic (Notify / Read):** `0000cc13-0000-1000-8000-00805f9b34fb` (Data/Notifications to App)

📖 **Full command reference for app/client developers:** [BLE_PROTOCOL.md](BLE_PROTOCOL.md) — every binary and ASCII command, payload layouts, notification types, and worked examples.

📱 **Mobile app feature mapping:** [MOBILE_APP_PLAN.md](MOBILE_APP_PLAN.md) — screen-by-screen feature breakdown, data model, and what's left to build.

---

## 📱 Android Companion App

A native Kotlin + Jetpack Compose (Material3) app in [`android_app/`](android_app) — the primary client for a PocketRF device. Since the device itself is screen- and storage-free, the app owns the entire UI and a local SQLite signal library.

* **Home screen:** two swipeable pages — a **Keys** page of pinned signals (drag-to-reorder, long-press-to-unpin, quick-access tiles for Scan/copy and Fixed Freq) and the full feature **Menu**.
* **Screens for every RF mode:** Scan/copy, Fixed Freq Scan/copy, Custom SubGhz (protocol + KeeLoq key entry, `.sub` replay), Spectrum, Listen, Bruteforce, Jammer, and Config.
* **Library:** every captured or saved signal (decoded or RAW), searchable, with rename, pin-to-Keys, delete, and one-tap Replay — saving is explicit (name-and-save), never automatic.
* **BLE client:** GATT connection with MTU negotiation, chunked transfer in both directions (long RAW captures in from the device, long RAW/TX payloads out to it), and ack-aware TX so starting a replay reliably pauses any active RX/scan session and resumes it afterward.

### Build & install

```bash
cd android_app
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17 and the Android SDK (`compileSdk`/`targetSdk` 35, `minSdk` 26). On Android 12+, grant Bluetooth permissions on first connect.

---

## 🖥️ Python CLI (quick testing)

[`pocketrf_cli.py`](pocketrf_cli.py) is a [Bleak](https://github.com/hbldh/bleak)-based interactive BLE client for testing a device from a laptop without the Android app — run it with no arguments for a numbered menu:

```bash
python3 pocketrf_cli.py
```

---

## 🔧 Building & Flashing the Firmware

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
