# PocketRF BLE Protocol Reference

This document is the integration reference for anything talking to PocketRF
over Bluetooth Low Energy — primarily the future mobile companion app, but
also `pocketrf_cli.py` and any other client. It describes the GATT profile,
the two command formats the device accepts, every command currently
implemented, and every notification the device can send back.

PocketRF is **headless and storage-free by design**: it has no SD card, no
display, and keeps no history. A client is expected to own persistence
(saved codes, `.sub` file libraries, scan history) itself and talk to the
device purely as a live radio.

---

## 1. GATT Profile

| | |
|---|---|
| **Device name** | `XIAO-RF-BLE` |
| **Primary service UUID** | `0000cc11-0000-1000-8000-00805f9b34fb` |
| **RX characteristic** (commands, app → device) | `0000cc12-0000-1000-8000-00805f9b34fb` — `WRITE` / `WRITE_NO_RESPONSE` |
| **TX characteristic** (responses/notifications, device → app) | `0000cc13-0000-1000-8000-00805f9b34fb` — `READ` / `NOTIFY` |
| **Negotiated MTU** | up to 512 bytes (request it on connect for best throughput) |

Connect, subscribe to **NOTIFY** on the TX characteristic, then write
commands to the RX characteristic. Every command — binary or ASCII — produces
exactly one response notification on the TX characteristic. While an
RX/spectrum/scan session is active, the device also pushes unsolicited
notifications (captured signals, RSSI samples, etc.) on the same
characteristic; these are distinguished by their `"type"` field (see §5).

**Only one BLE central can be connected at a time** (standard NimBLE default).
The device stops advertising while connected and resumes advertising
immediately on disconnect — if a client crashes or is killed without a clean
disconnect, the OS/BlueZ side may still think it's connected for a while,
which blocks new connections until that stale link is dropped.

---

## 2. Two command formats

The device auto-detects which format you sent by inspecting the first byte(s)
of the write:

1. **Binary packets** — first byte `0xAA`. Compact, fixed-field, CRC-checked.
   Intended for the mobile app's normal operation.
2. **ASCII text** — anything else. Either Bruce's `subghz ...` CLI syntax, or
   PocketRF's small set of legacy standalone commands (`STATUS`, `FREQ ...`,
   `TX_PROTO ...`, etc.). Convenient for manual testing (serial monitor,
   `pocketrf_cli.py`, `nRF Connect`-style BLE apps).

There is also a third, special-cased raw-byte form for replaying a full
Flipper-style `.sub` file in one write — see §4.3.

Both ASCII and binary commands always produce a **single JSON or binary
response** on the TX characteristic, with `"status"` (`"ok"`/`"error"`) in
the ASCII/JSON case.

---

## 3. Binary command format

```
Request:  [0xAA] [CMD_ID]       [PAYLOAD_LEN] [...payload...] [CRC8]
Response: [0xAA] [CMD_ID|0x80]  [PAYLOAD_LEN] [...payload...] [CRC8]
```

- `CMD_ID` is one byte (see table below). The response echoes it with bit 7
  set (`CMD_ID | 0x80`).
- `PAYLOAD_LEN` is one byte: the number of payload bytes that follow.
- `CRC8` is computed over everything from `0xAA` through the last payload
  byte (i.e. `3 + PAYLOAD_LEN` bytes), **not including itself**.
- Multi-byte numeric fields are **little-endian** (native ESP32/`memcpy`
  layout — `float` is standard IEEE-754 32-bit).

### CRC8 algorithm

Polynomial `0x31`, initial value `0x00`, MSB-first, no reflection, no final
XOR (classic "Dallas/Maxim"-style bitwise CRC8):

```python
def crc8(data: bytes) -> int:
    crc = 0x00
    for byte in data:
        crc ^= byte
        for _ in range(8):
            if crc & 0x80:
                crc = ((crc << 1) ^ 0x31) & 0xFF
            else:
                crc = (crc << 1) & 0xFF
    return crc
```

A frame with a CRC mismatch is silently dropped (logged to serial, no BLE
response sent) — the client should apply its own write timeout.

### Binary command table

| ID | Name | Request payload | Response payload |
|----|------|------------------|-------------------|
| `0x01` | `CMD_STATUS` | *(none)* | `detected`(u8) `version`(u8) `partnum`(u8) `marcstate`(u8) `freqHz`(u32) `rssi`(i8) `rxActive`(u8) — 10 bytes |
| `0x02` | `CMD_SET_FREQ` | `freqHz`(u32) | `0x00` = ok |
| `0x03` | `CMD_START_RX` | *(none)* | `0x00`/`0x01` |
| `0x04` | `CMD_STOP_RX` | *(none)* | `0x00` |
| `0x05` | `CMD_TX_PROTO` | `protoIdx`(u8) `bits`(u8, `0`=use protocol default) `key`(u64) | `0x00`/`0x01`/`0xFF`(bad payload) |
| `0x06` | `CMD_TX_KEELOQ` | `key`(u64) | `0x00`/`0x01`/`0xFF` |
| `0x07` | `CMD_TX_RAW` | `freqMhz`(f32) `count`(u16) `durations[count]`(i16 each, signed µs, +=HIGH/-=LOW) | `0x00`/`0x01`/`0xFF` |
| `0x08` | `CMD_START_LISTEN` | *(none)* | `0x00`/`0x01` |
| `0x09` | `CMD_STOP_ALL` | *(none)* | `0x00` |
| `0x0A` | `CMD_START_SPECTRUM_FIXED` | *(none, uses current freq)* | `0x00`/`0x01` |
| `0x0B` | `CMD_START_SPECTRUM_SWEEP` | `startMhz`(f32) `endMhz`(f32) `stepMhz`(f32) *(optional — omit for default 300/928/1.0)* | `0x00`/`0x01` |
| `0x0C` | `CMD_START_JAM` | `mode`(u8, see §3.1) `freqMhz`(f32) | `0x00`/`0x01` |
| `0x0D` | `CMD_START_BRUTEFORCE` | `protoIdx`(u8) `bits`(u8, `0`=default) `freqMhz`(f32) `repeat`(u8) | `0x00`/`0x01` |
| `0x0E` | `CMD_START_SCAN` | *(none)* | `0x00`/`0x01` |
| *other* | — | — | `0xFE` = unknown command |

Response byte values for the simple `0x00`/`0x01` commands: `0x00` = success,
`0x01` = failed (e.g. invalid protocol index, CC1101 not responding),
`0xFF` = malformed/too-short payload.

#### 3.1 Jam mode byte (`CMD_START_JAM`, also used by `subghz jam`)

| Value | Mode |
|-------|------|
| `0` | `FULL` — continuous max-duty-cycle TX |
| `1` | `ITMT` — intermittent, varied pulse-width bursts |
| `2` | `NOISE` — CC1101 hardware PN9 random TX |
| `3` | `SWEEP` — ±5MHz hop around the target frequency |

Every jam session has a hard **30-second safety cutoff**, after which the
device auto-stops and sends a `jam_done` notification (§5) regardless of
whether the client asked it to stop.

#### 3.2 `CMD_STATUS` payload layout

```
offset  size  field
0       1     detected      (0/1 — see caveat below)
1       1     version       (CC1101 VERSION register, 0x31)
2       1     partnum       (CC1101 PARTNUM register, 0x30 — should be 0x00 on genuine hardware)
3       1     marcstate     (CC1101 MARCSTATE, lower 5 bits — see §3.3)
4       4     freqHz        (u32, current tuned frequency in Hz)
8       1     rssi          (i8, dBm)
9       1     rxActive      (0/1 — RMT RX session currently running)
```

> **Known caveat:** `detected` only checks `VERSION > 0`. A floating/
> disconnected SPI bus commonly reads `0xFF` for every register, which also
> satisfies `> 0` — so `detected: true` with `version: 255` and
> `partnum: 255` usually means *nothing is actually wired*, not that a chip
> was found. A genuine CC1101 always reports `partnum == 0`; treat a nonzero
> `partnum` as a red flag regardless of what `detected` says.

#### 3.3 MARCSTATE values worth knowing

| Value | Meaning |
|-------|---------|
| `0x01` | `IDLE` |
| `0x0D` | `RX` |
| `0x11` (and others `0x0E`-`0x16`) | transient TX-chain states |
| `0x1F` (all 1s) | **floating bus** (no chip responding), not a real CC1101 state |

---

## 4. ASCII commands

Two syntaxes are accepted. Both return the same single-line JSON response
shape (`{"status": "ok"|"error", "cmd": "...", ...extra fields}`).

### 4.1 `subghz ...` (preferred — Bruce-compatible)

| Command | Effect |
|---|---|
| `subghz freq <mhz\|hz>` | Set frequency (value `>10000` is treated as Hz) |
| `subghz rx` | Start decode-RX (`decoded_rf`/`raw_pulse` notifications, see §5) |
| `subghz rx stop` / `subghz stop` | Stop whatever RF operation is active |
| `subghz listen` | Start the frequency/pulse listener (`listen_pulse` notifications) |
| `subghz rssi [mhz]` | Start fixed-frequency RSSI streaming (`spectrum_fixed` notifications) |
| `subghz sweep [start_mhz] [end_mhz] [step_mhz]` | Start a spectrum sweep (`spectrum_sweep` notifications); defaults `300 928 1.0` |
| `subghz txp <proto> <freq> <bits> <key_hex> [te] [repeat]` | Transmit a registered protocol (§4.4 for names); `freq` in MHz or Hz; `te`/`repeat` optional |
| `subghz keeloqtx <key_hex>` | Transmit a raw 64-bit KeeLoq frame, 10 repeats |
| `subghz rawtx <freq_mhz\|hz> <d1> <d2> ...` | Transmit a raw signed-µs duration list directly in the command line |
| `subghz jam <full\|itmt\|noise\|sweep> [freq]` | Start the jammer (§3.1); `freq` optional, defaults to current |
| `subghz brute <proto> [bits] [freq] [repeat]` | Start bruteforcing a protocol's full keyspace; `bits`/`freq`/`repeat` optional |
| `subghz scan` | Start Scan+Copy: sweep 300-928MHz, lock onto an active signal, decode continuously (§5) |
| `subghz preset <name>` | Apply a modulation preset ahead of a TX/RX (§4.5) |
| `subghz status` | One-shot device/radio status (same fields as §3.2, as JSON) |

### 4.2 Legacy standalone commands

Kept for backward compatibility / quick manual testing; `subghz ...` is
preferred for anything new.

| Command | Effect |
|---|---|
| `STATUS` / `INIT` | Device status |
| `FREQ <mhz\|hz>` | Set frequency |
| `MODE RX` / `MODE TX` / `MODE IDLE` | Direct CC1101 mode switch |
| `RMT RX START` / `RMT RX STOP` / `STOP` | Start/stop decode-RX |
| `LISTEN START` | Start the frequency listener |
| `TX_PROTO <proto> <key_hex>` | Transmit a protocol with its default bit count/te, 10 repeats |
| `TX_KEELOQ <key_hex>` | Transmit a raw 64-bit KeeLoq frame, 10 repeats |

Commands are **case-insensitive**. Anything unrecognized returns
`{"status": "unknown_command", "raw": "<what you sent>"}`.

### 4.3 `RAWSUB ` — full `.sub` file replay

For replaying a whole Flipper-style `.sub` RAW file in one shot (header +
`Frequency:` + one or more `RAW_Data:` lines), prefix the raw bytes with
`RAWSUB ` (note the trailing space) and send the entire file content —
embedded `\n` characters are fine; this path is **not** truncated at the
first newline the way the generic ASCII commands above are.

```
RAWSUB Filetype: Bruce SubGhz File
Version 1
Frequency: 433920000
Preset: 0
Protocol: RAW
RAW_Data: 320 -320 640 -640 320 -320 ...
```

Response: `{"status": "ok"|"error", "cmd": "RAWSUB", ...}`.

**MTU caution:** a single BLE write is capped by the negotiated MTU (up to
512 bytes). A `.sub` file with hundreds of `RAW_Data` values can exceed that
in one write — `pocketrf_cli.py` warns about this but does not chunk it. A
production client sending large RAW captures should either negotiate a large
enough MTU for the payload or split the write (the firmware does not
currently support multi-write reassembly for `RAWSUB`).

### 4.4 Protocol names (`rf_registry`)

Used by `subghz txp`, `subghz brute`, `tx_proto`, and `CMD_TX_PROTO`'s
`protoIdx` (0-based index into this same list).

| Idx | Name | Bits | Idx | Name | Bits |
|----:|------|-----:|----:|------|-----:|
| 0 | `Princeton` | 24 | 13 | `RcSwitch_1` | variable |
| 1 | `NICE_FLO` | 12 | 14 | `RcSwitch_2` | variable |
| 2 | `Nice_12bit` | 12 | 15 | `RcSwitch_3` | variable |
| 3 | `Linear` | 10 | 16 | `RcSwitch_4` | variable |
| 4 | `Clemsa` | 18 | 17 | `RcSwitch_5` | variable |
| 5 | `Mastercode` | 36 | 18 | `RcSwitch_6` | variable |
| 6 | `CAME` | 12 | 19 | `RcSwitch_7` | variable |
| 7 | `Ansonic` | 12 | 20 | `RcSwitch_8` | variable |
| 8 | `GateTX` | 24 | 21 | `RcSwitch_9` | variable |
| 9 | `Holtek` | 40 | 22 | `RcSwitch_10` | variable |
| 10 | `Holtek_12bit` | 12 | 23 | `RcSwitch_11` | variable |
| 11 | `Holtek_HT12` | 12 | 24 | `RcSwitch_12` | variable |
| 12 | `PhoenixV2` | 52 | | | |

Flipper-style aliases are also accepted by name (not by index) in
`subghz txp`/`subghz brute`/`tx_proto`: `"Nice FLO"` → `NICE_FLO`,
`"Nice 12bit"` → `Nice_12bit`, `"Holtek 12bit"`/`"Holtec 12bit"` →
`Holtek_12bit`, `"Holtek_HT12X"` → `Holtek_HT12`, `"Phoenix_V2"` →
`PhoenixV2`.

### 4.5 Preset names (`rf_presets`)

Used by `subghz preset <name>`. A preset changes modulation/deviation/RX
bandwidth/data rate; it does **not** change frequency.

| Name | Modulation |
|------|------------|
| `Ook270Async` | OOK/ASK, 270kHz BW *(device default)* |
| `Ook650Async` | OOK/ASK, 650kHz BW |
| `2FSKDev238Async` | 2-FSK, 2.38kHz deviation |
| `2FSKDev476Async` | 2-FSK, 47.6kHz deviation |
| `MSK99_97KbAsync` | MSK, 99.97kb/s |
| `GFSK9_99KbAsync` | GFSK, 9.996kb/s |

Flipper's `FuriHalSubGhzPreset...` names are also accepted as aliases (e.g.
`FuriHalSubGhzPresetOok270Async`).

---

## 5. Unsolicited notifications

Sent on the TX characteristic while a session is active, distinguished by
`"type"`. None of these are responses to a specific command — they arrive
asynchronously, possibly many per second, for as long as the corresponding
session is running.

| `type` | Sent while... | Fields |
|---|---|---|
| `decoded_rf` | `subghz rx` / `RMT RX START`, signal recognized | `protocol`, `bits`, `key`(hex string), `te`, `freq`, `rssi`; if `protocol=="KeeLoq"`: `mf_name`, `btn`, `serial`, and `cnt` (only once a keystore match sets `mf_name` to something other than `"Generic KeeLoq"`) |
| `raw_pulse` | `subghz rx` / `RMT RX START`, signal captured but not recognized | `count` (pulse count), `freq`, `rssi` |
| `listen_pulse` | `subghz listen` / `LISTEN START` | `center_freq`, `pulse_dur_us`, `detected_freq_hz`, `rssi` — throttled to ≤5/sec |
| `spectrum_fixed` | `subghz rssi` / `CMD_START_SPECTRUM_FIXED` | `freq`, `rssi` — one every ~50ms |
| `spectrum_sweep` | `subghz sweep` / `CMD_START_SPECTRUM_SWEEP`, one full pass completed | `start_freq`, `end_freq`, `step_mhz`, `count`, `rssi` (array of i8, one per step) |
| `jam_done` | A jam session hit its 30s safety cutoff | `reason: "timeout"` |
| `bruteforce_done` | A bruteforce run exhausted its keyspace | *(none)* |
| `scan_locked` | `subghz scan`, once an active frequency is found | `freq` |
| `scan_decoded` | `subghz scan`, after locking, per captured+decoded signal | Same shape as `decoded_rf` (protocol/bits/key/te/freq/rssi/mf_name/btn/serial/cnt) |

A client should parse every TX-characteristic notification, branch on
`"type"` (treating a missing `"type"` as a command response instead), and
keep listening — a long-running session (RX, listen, spectrum, scan) can
produce many notifications before the client sends a `stop`.

---

## 6. State model

The firmware is a single-session state machine (`RfState` in
`rf_service.h`): starting any new operation implicitly stops whatever was
running before it (no need to send `stop` first). The states are: `IDLE`,
`RX_DECODED`, `RX_RAW` *(reserved, not currently exposed over BLE)`*,
`LISTEN`, `SPECTRUM_FIXED`, `SPECTRUM_SWEEP`, `JAM`, `BRUTEFORCE`, `SCAN`.

`subghz stop` / `STOP` / `CMD_STOP_ALL` (`0x09`) stops whatever is currently
active and returns to `IDLE`, regardless of which state it was in.

---

## 7. Worked examples

**Python / `bleak`, binary `CMD_STATUS`:**

```python
import struct

def crc8(data: bytes) -> int:
    crc = 0
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = ((crc << 1) ^ 0x31) & 0xFF if crc & 0x80 else (crc << 1) & 0xFF
    return crc

frame = bytes([0xAA, 0x01, 0x00])       # CMD_STATUS, no payload
frame += bytes([crc8(frame)])
await client.write_gatt_char(RX_CHAR_UUID, frame, response=True)
# response on TX_CHAR_UUID: 0xAA 0x81 0x0A <10 payload bytes> <crc8>
```

**ASCII, start RX and transmit a CAME code:**

```python
await send("subghz freq 433.92")
await send("subghz rx")
# ... notifications arrive as decoded_rf / raw_pulse ...
await send("subghz stop")

await send("subghz txp CAME 433.92 12 A5")   # 12-bit CAME, key=0xA5
```

See [`pocketrf_cli.py`](pocketrf_cli.py) for a full working reference client
(scan/jam/bruteforce/RAW replay included) and
[`DEVELOPMENT_LOG.md`](DEVELOPMENT_LOG.md) for how each feature was ported
from [Bruce](https://github.com/pr3y/Bruce)'s RF module.
