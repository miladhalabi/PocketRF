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
| **D10** | GPIO 10 | SCK | SPI Clock |
| **D2** | GPIO 4 | MISO | SPI Master In / Slave Out (Safe non-strapping pin) |
| **D3** | GPIO 5 | MOSI | SPI Master Out / Slave In |
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
- [x] **Feature 4: CC1101 Driver & Precise Frequency Calibration Extraction**
  * Created `include/rf_structs.h`, `include/cc1101_driver.h`, and `src/cc1101_driver.cpp`.
  * Implemented `CC1101Driver` class wrapping hardware SPI initialization on Seeed XIAO ESP32C3 pins (SCK: GPIO 10, MISO: GPIO 4, MOSI: GPIO 5, CS: GPIO 7, GDO0: GPIO 3).
  * Resolved ESP32-C3 single FSPI host initialization (`SPI.begin()` + `setSPIinstance(&SPI)`) fixing `spiStartBus(): SPI bus index 1 is out of range`.
  * Extracted `applyPreciseCalibration` (`FSCTRL0`, `FSCAL2`, `TEST0`, `FREND0/1`) across 315MHz, 433MHz, 868MHz, and 915MHz bands.
  * Added ASK/OOK preset configuration (`applyFixedFreqOokPreset`).
  * Integrated BLE commands (`STATUS`/`INIT`, `FREQ <mhz>`, `MODE <RX|TX|IDLE>`) returning JSON status payloads over BLE notifications.
- [x] **Feature 5: ESP32 RMT Microsecond Timing Core (`rf_signal_engine`)**
  * Created `include/rf_signal_engine.h` and `src/rf_signal_engine.cpp` leveraging native ESP-IDF 5.x RMT drivers (`driver/rmt_rx.h`, `driver/rmt_tx.h`).
  * Configured 1 MHz tick resolution ($1\,\mu\text{s}$) on GPIO 3 (GDO0 pin).
  * Implemented `durationsToRmtSymbols()` and `rmtSymbolsToDurations()` signed pulse converters.
  * Built non-blocking background RMT RX session (`startRx()`, `stopRx()`, `pollRxDurations()`) with FreeRTOS queue integration.
  * Built RMT TX pulse train transmitter (`transmitDurations()`).
  * Created `test_ble.py` automated test runner using Python `bleak`.
- [x] **Feature 6: Protocol Registry & KeeLoq Engine Integration**
  * Built `include/rf_protocol.h`, `include/rf_registry.h`, and `src/rf_registry.cpp` containing static OOK protocol definitions (Princeton, CAME, Nice, Holtek, Linear, Clemsa, Mastercode, GateTX, Ansonic, PhoenixV2, RcSwitch 1..12).
  * Built `include/rf_encoder.h` and `src/rf_encoder.cpp` (`rf_tx_protocol`, `rf_tx_keeloq`, `rf_tx_raw_bits`).
  * Built `include/rf_decoder.h` and `src/rf_decoder.cpp` (`rf_decode_ook`, `rf_decode_keeloq`, `rf_build_raw`).
  * Built `include/rf_keeloq.h` and `src/rf_keeloq.cpp` with 32-bit block cipher, KeeLoq learning schemes (Simple, Normal, Secure, Magic XOR), and frame identification.
  * Aligned CC1101 initialization, frequency synthesizer tuning (`setMHZ`), calibration (`applyPreciseCalibration`), and OOK presets (`applyFixedFreqOokPreset`) 100% 1-to-1 with original Bruce firmware.
  * Added explicit `IOCFG0 = 0x0D` GDO0 serial routing for RX mode in `setRxMode()` and expanded RMT RX idle gap window to `50 ms` for high-sensitivity capture.
  * Built interactive CLI menu `pocketrf_cli.py` for one-keypress multi-band testing.
- [x] **Feature 7: Dual Command Parser (Binary Packets 0xAA + ASCII Text CLI subghz)**
  * Created `include/cmd_router.h` and `src/cmd_router.cpp` implementing smart input detection.
  * **Binary Packet Router (`0xAA` frames):** Parses compact binary commands (`CMD_STATUS`, `CMD_SET_FREQ`, `CMD_START_RX`, `CMD_STOP_RX`, `CMD_TX_PROTO`, `CMD_TX_KEELOQ`, `CMD_TX_RAW`) with CRC8 verification for Android/iOS mobile companion app.
  * **ASCII CLI Router:** Parses Bruce terminal syntax (`subghz freq <hz_or_mhz>`, `subghz rx`, `subghz rx stop`, `subghz txp <proto> <freq> <bits> <key_hex>`, `subghz keeloqtx <key_hex>`, `subghz status`).
  * Enabled USB CDC Serial input in `main.cpp` (`Serial.available()`), allowing terminal commands to be typed directly into Serial Monitor alongside BLE.

---

## 5. Planned Milestones Roadmap

- [x] **Feature 8: RF Service State Machine Manager (`rf_service`)**
  * Created `include/rf_service.h` and `src/rf_service.cpp` implementing central thread-safe state machine (`IDLE`, `RX_DECODED`, `RX_RAW`, `LISTEN`).
  * Implemented Frequency Counter / Signal Listener (`startListen`) using IRAM-safe GPIO interrupt handler (`onListenPulseISR`) on GPIO 3 (GDO0).
  * Streaming live pulse durations, signal center frequencies, and RSSI metrics as JSON payloads over BLE.
  * Added binary commands (`CMD_START_LISTEN = 0x08`, `CMD_STOP_ALL = 0x09`) and ASCII sub-commands (`subghz listen`, `subghz stop`).
  * Expanded interactive `pocketrf_cli.py` Python test utility with Option 14 Frequency Listener.
- [x] **Feature 9: Spectrum Analyzer & Waterfall RSSI Engine (`rf_spectrum`)**
  * Created `include/rf_spectrum.h` and `src/rf_spectrum.cpp` implementing fixed frequency continuous RSSI time-series sampling (`startFixedRssi`) and high-speed multi-band software frequency sweeping (`startSweep`).
  * Integrated sweep array streamer into `RfService` producing live JSON payloads over BLE (`spectrum_fixed` and `spectrum_sweep`).
  * Updated `cmd_router.h/.cpp` with binary commands (`CMD_START_SPECTRUM_FIXED = 0x0A`, `CMD_START_SPECTRUM_SWEEP = 0x0B`) and ASCII sub-commands (`subghz rssi <freq>`, `subghz sweep <start> <end> [step]`).
  * Updated interactive `pocketrf_cli.py` with Option 15 (Live RSSI Fixed Stream) and Option 16 (Sub-GHz Spectrum Waterfall Sweep).
- [x] **Code Review: Features 1-9 Audit (corrected against live Bruce source)**
  * Full read-through of all `src/`/`include/` files plus a clean PlatformIO build (47.6% flash, 7.3% RAM).
  * Initially flagged `rf_tx_raw_bits()`'s reversed bit-string iteration as a bug and "fixed" it to iterate left-to-right. **Reverted** after diffing against Bruce's live `src/modules/rf/protocols/rf_encoder.cpp`: the reversed walk is intentional there too (explicit comment: "Matches the legacy bit sender, which walked the string from the end toward the start"). PocketRF's version is correct as originally written.
  * **Confirmed byte-for-byte match with Bruce** on: CC1101 calibration (`rf_utils.cpp` `cc1101ApplyPreciseCalibration`/`cc1101ApplyFixedFreqOokPreset`), the OOK protocol registry table + aliases (`rf_registry.cpp`), `rf_encode_protocol`/`rf_tx_protocol`/KeeLoq TX framing (`rf_encoder.cpp`).
  * **Fixed bug:** `rf_keeloq.cpp`'s `keeloq_encrypt`/`keeloq_decrypt` used a *different* NLF tap-to-weight ordering than Bruce's (`g5(x,31,26,20,9,1)`/`g5(x,30,25,19,8,0)` vs Bruce's `g5(x,1,9,20,26,31)`/`g5(x,0,8,19,25,30)`). Each side was internally self-consistent (own encrypt/decrypt round-tripped), so it wasn't caught by local testing — but it would NOT have produced the same hopcodes as Bruce, the real KeeLoq reference cipher, or genuine remotes for the same key. Fixed by matching Bruce's tap order exactly (the key-bit scheduling, `r & 63` ascending for encrypt / descending for decrypt, was already mathematically equivalent to Bruce's `(15-r)&63` ascending form, so that didn't need to change). Verified with a standalone host-side test comparing both ciphers bit-for-bit across 20 key/data combinations — now matches Bruce exactly.
  * **Found scope gaps vs Bruce's decoder** (`rf_decoder.cpp`): Bruce's OOK decoder additionally has a Chamberlain 9-bit matcher, M5-module glitch filtering (not applicable to PocketRF's CC1101-only hardware), a noise-rejection floor (`RF_RX_MIN_TRANSITIONS`), and uses `RF_SEPARATION_LIMIT=4300`/`RF_NOMINAL_TE_TOLERANCE=20` (PocketRF: `4000`/`50`, i.e. looser matching, more false-positive risk). Bruce's KeeLoq decoder is a different state-machine walk (not a sliding-window brute search) with its own tolerances. Not bugs, but intentional parity gaps to close later for decode reliability.
  * Minor nits (not fixed): duplicate `rf_udiff`/`rf_udiff_u` helper in `rf_decoder.cpp`; unused `WRITE` property on the BLE TX characteristic.
- [x] **Feature 10: Sub-GHz RF Jammer, Bruteforce Engine & RAW (.sub) Replay**
  * Created `include/rf_jammer.h`/`src/rf_jammer.cpp` (`RFJammer`): ported Bruce's 4 jam modes (`rf_jammer.cpp` FULL/ITMT/NOISE/SWEEP) 1-to-1 in pulse timing, but re-architected as a non-blocking `tick()` state machine instead of Bruce's blocking `while` loop polling buttons — headless PocketRF has no buttons and must keep servicing BLE/Serial between pulses. Same 30s hard safety cutoff (`MAX_JAMMER_RUNTIME`) as Bruce. NOISE mode drives the CC1101 PN9 hardware random-TX generator exactly as Bruce does (ASK->2FSK->MSK cycling every 3s); SWEEP uses a new `CC1101Driver::quickTune()` (no recalibration pass) for fast ±5MHz hops, matching Bruce's `setMHZ`-only sweep retune.
  * Created `include/rf_bruteforce.h`/`src/rf_bruteforce.cpp` (`RfBruteforce`): reuses PocketRF's existing `rf_registry` protocol table (already has CAME 12-bit, Princeton 24-bit, Nice FLO 12-bit, etc.) and `rf_tx_protocol()` instead of duplicating Bruce's separate `BruteProtocol` table — one code transmitted per `tick()` call so a `stop` command can interrupt between codes, same granularity as Bruce's EscPress check.
  * Created `include/rf_raw.h`/`src/rf_raw.cpp`: Flipper-compatible `.sub` RAW text parser (`Frequency:` + multi-line `RAW_Data:`, matching Bruce's `save.cpp` writer format), replaying through the existing RMT `rfSignalEngine` instead of Bruce's bit-banged `digitalWrite` emitter (`emit.cpp`) for more precise timing. PocketRF is headless (no SD card), so the `.sub` text is supplied directly as a payload rather than read from a file.
  * Extended `RfService` with `JAM`/`BRUTEFORCE` states (`startJam`, `startBruteforce`), each ticked once per main-loop iteration and auto-stopped with a `jam_done`/`bruteforce_done` BLE notification when done.
  * Extended `cmd_router`: binary commands `CMD_TX_RAW` (now implemented; was a stub), `CMD_START_JAM = 0x0C`, `CMD_START_BRUTEFORCE = 0x0D`; ASCII `subghz jam <full|itmt|noise|sweep> [freq]`, `subghz brute <proto> [bits] [freq] [repeat]`, `subghz rawtx <freq> <d1> <d2> ...`. Added a `RAWSUB ` raw-byte prefix (checked before the generic single-line ASCII router, which otherwise truncates at the first `\r`/`\n`) so a full multi-line `.sub` blob can be sent as one BLE write.
  * Updated `pocketrf_cli.py` with Option 18 (Jammer), Option 19 (Bruteforce), Option 20 (RAW `.sub` file replay, read locally and sent as a `RAWSUB` payload; CLI warns if it would exceed one BLE MTU write).
  * Verified with a clean PlatformIO build (48.3% flash, 7.3% RAM — plenty of headroom left).
- [x] **Feature 11: Full RF Parity — Multi-Modulation Presets, KeeLoq Manufacturer ID, RF Scan+Copy**
  * Closed the 3 real gaps vs Bruce's full RF module identified in the code review after Feature 10 (the original Feature 1-10 roadmap was always a *subset* of Bruce's RF module, not full parity). SD-card-backed features (recent-codes history, `.sub` file browser, legacy migration) were deliberately **not** ported — PocketRF stays headless/storage-free; a future mobile app owns persistence instead.
  * **Multi-modulation presets** — created `include/rf_presets.h`/`src/rf_presets.cpp`, porting Bruce's `rf_presets.cpp` preset table (`Ook270Async`, `Ook650Async`, `2FSKDev238Async`, `2FSKDev476Async`, `MSK99_97KbAsync`, `GFSK9_99KbAsync`) and Flipper alias map verbatim, plus a new `rf_apply_preset()` that drives `ELECHOUSE_cc1101` modulation/deviation/RX-BW/data-rate. PocketRF was OOK-only before this; FSK/MSK/GFSK protocols are now reachable via the new `subghz preset <name>` command ahead of a TX/RX.
  * **KeeLoq manufacturer identification** — copied Bruce's encrypted keystore blob unmodified (`include/rf_keeloq_mfcodes_data.h`, AES-256-CBC via `mbedtls`), and ported `KeeloqKeystore`, the embedded-plaintext decrypt, keystore parsing, and the real `keeloq_identify()` (tries every manufacturer key, including the `Centurion` special case) into `rf_keeloq.cpp`/`.h`. Headless PocketRF has no SD/LittleFS, so it always uses the embedded copy (Bruce's SD/LittleFS override path was skipped, not ported). Also ported the full per-manufacturer `keeloq_build_hop()` routing (Aprimatic/Dea_Mio/Centurion/etc. framing) and `RfCodes::keeloq_check_decrypt[_centurion]` (added to `rf_structs.h`). The previous `keeloq_identify()` was a stub that always reported `"Generic KeeLoq"`; it's now a real match against ~30 manufacturers.
  * Added `rf_reverse_bits()` + `rf_try_keeloq()` (`rf_keeloq.cpp`) to correctly un-reverse a captured frame's bit order before identification — real KeeLoq transmits each 32-bit half LSB-first, so a direct MSB-first RMT capture needs un-reversing first. **Verified independently**, since this is an easy direction to get backwards: a standalone host-side round-trip test (TX-frame encode → sliding-window decode → `rf_reverse_bits`) exactly recovered the original `fix`/`encrypted` values.
  * `RfService`'s `RX_DECODED` path now calls `rf_try_keeloq()` first (matches Bruce's `rf_scan.cpp` try-order: KeeLoq's strict 64-bit structure is cheap to check before the looser OOK registry match) and reports `mf_name`/`btn`/`serial`/`cnt` in the `decoded_rf` notification instead of just the raw key.
  * **RF Scan+Copy** — created `include/rf_scan.h`/`src/rf_scan.cpp` (`RfScanner`), porting the core of Bruce's `rf_scan.cpp` `fast_scan()` (923 lines in Bruce, including all display/menu/SD code which was dropped): sweeps the same 57-frequency ISM list (`subghz_frequency_list`, ported verbatim from `rf_utils.cpp`) across all 3 bands, picks the strongest RSSI of 5 candidates above `-65dBm`, then locks onto that frequency and keeps decoding (KeeLoq first, then OOK) — matching Bruce's "lock on and keep receiving" behavior rather than re-scanning after every capture. Re-architected as the same non-blocking `tick()` pattern as the rest of PocketRF (one RSSI probe per tick while searching) instead of Bruce's blocking loop.
  * Wired into `RfService` as a new `SCAN` state, `cmd_router` (`CMD_START_SCAN = 0x0E`, ASCII `subghz scan`), and `pocketrf_cli.py` (Option 21). Notifies `scan_locked` once a frequency is found, then `scan_decoded` per captured signal.
  * **Verified on real hardware**: flashed, boot self-test passes, `subghz scan` runs for 10s hopping cleanly across all 57 frequencies with no crash/watchdog reset, correctly never false-locks with nothing transmitting nearby, and `STOP` cleans up correctly.
  * Clean PlatformIO build: 49.1% flash, 7.4% RAM.
- [x] **Hardware debugging: BLE_PROTOCOL.md, CMD_STATUS fix, RX-listen quirk, sweep/scan bugs**
  * Added `BLE_PROTOCOL.md`: full command/notification reference for app developers, written by cross-checking the actual source (not from memory).
  * **Fixed:** `CMD_STATUS`'s binary response declared `PAYLOAD_LEN=11` but only wrote 10 payload bytes (`cmd_router.cpp`). Found while writing the protocol doc; flashed and verified the frame is still correctly 14 bytes total.
  * **Investigated real hardware symptom:** listening for signals sometimes produced nothing until a TX had happened once, after which RX reliably worked — and a plain stop-then-listen-again retry (confirmed with the user) did **not** fix it, ruling out simple AGC/calibration settling on the first attempt. Ruled out an IOCFG0 misconfiguration theory via driver source inspection (the SmartRC driver's `Init()` already configures GDO0 for async-serial RX correctly via `ccmode=0`, and nothing resets it). Added `CC1101Driver::warmUpBeforeRx()`: a brief internal dummy-TX pulse immediately before arming RX, replicating whatever "priming" effect a real TX was having, plus an `SFRX` RX-FIFO flush for good measure. Flashed; **not yet confirmed fixed** by the user as of this entry.
  * **Fixed real bug:** `subghz sweep`/spectrum-sweep stepping linearly across 300-928MHz walked straight through the CC1101's two hardware-unsupported frequency gaps (350-387MHz, 468-779MHz, per `CC1101Driver::setFrequency()`'s own validity check), where the chip can't physically tune — `setFrequency()` was silently substituting 433.92MHz instead, corrupting a large fraction of every sweep pass with bogus duplicate readings. Added `rf_skip_invalid_gap()` (`rf_spectrum.cpp`) to jump straight to the next valid band instead. **Verified on hardware**: re-ran the exact failing sweep before/after — 17 "Invalid frequency" fallbacks before the fix, 0 after, with the frequency sequence now correctly jumping `350→387` and `467→779`.
  * **Fixed real bug:** `subghz scan`'s RSSI threshold was hardcoded to Bruce's `-65dBm` default, but this board's actual signal readings never exceeded `-70`..`-80dBm` even for a presumed-active signal — meaning scan could never lock onto anything. Made the threshold a parameter (`RfScanner::begin(rssiThresholdDbm)`, `RfService::startScan(rssiThresholdDbm)`, ASCII `subghz scan [threshold]`, binary `CMD_START_SCAN`'s now-optional payload byte). **Verified on hardware**: with `-85`, the scanner locked on almost immediately (previously never locked at `-65`). Documented the tuning tradeoff (too loose = locks onto noise) in `BLE_PROTOCOL.md` §4.6.
