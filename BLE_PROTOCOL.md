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
| `0x0E` | `CMD_START_SCAN` | `rssiThresholdDbm`(i8, optional — omit for default `-65`) | `0x00`/`0x01` |
| `0x0F` | `CMD_TX_RAW_BEGIN` | `freqMhz`(f32) `totalCount`(u16) | `0x00`/`0xFF` |
| `0x10` | `CMD_TX_RAW_CHUNK` | `durations[N]`(i16 each, N = however many fit this write) | `status`(u8: `0x00` ok / `0x01` no BEGIN in progress / `0xFF` malformed) `accumulated`(u16) — 3 bytes |
| `0x11` | `CMD_TX_RAW_END` | *(none)* | `0x00`/`0x01` |
| *other* | — | — | `0xFE` = unknown command |

See §4.3.1 for why `CMD_TX_RAW_BEGIN`/`CHUNK`/`END` exist and how to use them.

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
| `subghz scan [rssi_threshold_dbm]` | Start Scan+Copy: sweep 300-928MHz, lock onto a signal above the threshold (default `-65`), decode continuously (§5). **Tune this to your hardware** — see note below |
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
in one write, and the firmware does **not** reassemble a `RAWSUB` payload
split across multiple writes — `pocketrf_cli.py` just warns about this and
sends it anyway. **For anything that might not fit in one write, don't use
`RAWSUB` — use the chunked binary transfer instead (§4.3.1).**

#### 4.3.1 Chunked RAW transfer (`CMD_TX_RAW_BEGIN`/`CHUNK`/`END`)

For a duration list too long for one write — whether from importing a large
`.sub` file or replaying a saved raw capture from a library — don't send the
raw `.sub` text at all. **Parse the `.sub` text client-side instead** (the
format is a handful of `Key: value` header lines plus one or more
`RAW_Data: <space-separated signed ints>` lines — trivial to parse in any
language) and send the resulting `(frequency, durations[])` pair through
this chunked binary transfer. This is also exactly the shape a saved RAW
capture from the signal library already has (see the `durations` field on
`raw_pulse`/`scan_raw`, §5.1), so **one mechanism covers both "import a
`.sub` file" and "replay a saved capture"** — there's no need for a second,
text-based chunking scheme just for `RAWSUB`.

```
1. CMD_TX_RAW_BEGIN  freqMhz(f32) + totalCount(u16)
   -> declares the frequency and total duration count, resets the
      accumulation buffer. totalCount is capped server-side at 2000
      (RAW_CHUNK_MAX_DURATIONS, matching the RMT capture buffer's own
      limit) - sending more than that just won't all fit regardless.

2. CMD_TX_RAW_CHUNK  durations[N](i16 each)  -- as many times as needed
   -> appends N durations to the buffer, where N is however many fit in
      this write (size it to the negotiated MTU, same as any other
      payload - up to roughly (peerMtu-3-4)/2 durations per chunk).
      Response includes `accumulated`: the running total so far - use it
      to confirm chunks aren't being dropped, there's no separate ack
      sequence number.

3. CMD_TX_RAW_END  (no payload)
   -> transmits whatever was accumulated (even if short of the declared
      totalCount - a dropped chunk still gets a real, if incomplete,
      transmission rather than silently refusing) and clears the buffer.
      Same 0x00/0x01 response as CMD_TX_RAW.
```

A fresh `CMD_TX_RAW_BEGIN` always resets the buffer, so an abandoned
transfer (client disconnected mid-sequence, forgot to send `END`) doesn't
need any explicit cleanup — the next `BEGIN` just starts clean.

ASCII equivalents exist for manual testing (`pocketrf_cli.py`-style), same
three-step shape: `subghz rawtx_begin <freq> <total_count>`, one or more
`subghz rawtx_chunk <d1> <d2> ...`, then `subghz rawtx_end`.

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

### 4.6 Tuning the Scan+Copy RSSI threshold

`subghz scan`'s default threshold (`-65dBm`, Bruce's own default) assumes
RSSI calibration roughly in line with a genuine CC1101. **RSSI calibration
varies noticeably between CC1101 clones and antennas** — on one tested board
here, even an actively-transmitting signal only ever read around
`-70`-`-80dBm`, so the scanner never locked onto anything at the default
threshold. If `subghz scan` never produces a `scan_locked` notification,
try loosening it:

```
subghz scan -80
```

**Caution:** loosen it too far and the scanner will lock onto pure noise
floor instead of a real signal (a `scan_locked` notification with no
`scan_decoded` ever following it is a sign of that — `subghz scan` with a
too-permissive threshold, or `subghz rssi` to watch the noise floor live,
are the quickest ways to find the right value for your specific board). A
reasonable approach: watch `subghz rssi` while idle to find your board's
noise floor, then set the threshold 5-10dB above that.

---

## 5. Unsolicited notifications

Sent on the TX characteristic while a session is active, distinguished by
`"type"`. None of these are responses to a specific command — they arrive
asynchronously, possibly many per second, for as long as the corresponding
session is running.

| `type` | Sent while... | Fields |
|---|---|---|
| `decoded_rf` | `subghz rx` / `RMT RX START`, signal recognized | `protocol`, `bits`, `key`(hex string), `te`, `freq`, `rssi`; if `protocol=="KeeLoq"`: `mf_name`, `btn`, `serial`, and `cnt` (only once a keystore match sets `mf_name` to something other than `"Generic KeeLoq"`) |
| `raw_pulse` | `subghz rx` / `RMT RX START`, signal captured but not recognized | `part` (`full`/`begin`/`chunk`/`end`) + part-specific fields — see §5.1. `durations` are signed ints, µs, same sign convention as `CMD_TX_RAW`/`rawtx` |
| `listen_pulse` | `subghz listen` / `LISTEN START` | `center_freq`, `pulse_dur_us`, `detected_freq_hz`, `rssi` — throttled to ≤5/sec |
| `spectrum_fixed` | `subghz rssi` / `CMD_START_SPECTRUM_FIXED` | `freq`, `rssi` — one every ~50ms |
| `spectrum_sweep` | `subghz sweep` / `CMD_START_SPECTRUM_SWEEP`, one full pass completed | `start_freq`, `end_freq`, `step_mhz`, `count`, `decimated` (bool), `rssi` (array of i8) — see §5.2 |
| `jam_done` | A jam session hit its 30s safety cutoff | `reason: "timeout"` |
| `bruteforce_done` | A bruteforce run exhausted its keyspace | *(none)* |
| `scan_locked` | `subghz scan`, once an active frequency is found | `freq` |
| `scan_decoded` | `subghz scan`, after locking, per captured+decoded signal | Same shape as `decoded_rf` (protocol/bits/key/te/freq/rssi/mf_name/btn/serial/cnt) |
| `scan_raw` | `subghz scan`, after locking, per captured-but-unrecognized signal | Same shape as `raw_pulse` — see §5.1 |

A client should parse every TX-characteristic notification, branch on
`"type"` (treating a missing `"type"` as a command response instead), and
keep listening — a long-running session (RX, listen, spectrum, scan) can
produce many notifications before the client sends a `stop`.

### 5.1 Chunked delivery (`raw_pulse`/`scan_raw`)

A capture can have hundreds of pulses (the RMT buffer holds up to 1000
symbols = up to 2000 durations), far more than fits in one BLE
notification. Every `raw_pulse`/`scan_raw` message carries a `"part"`
field:

- **`"part": "full"`** — the whole capture fit in one notification. Fields:
  `count`, `freq`, `rssi`, `durations` (the complete array). This is the
  common case.
- **`"part": "begin"`** — too large for one notification; this declares the
  true total before any data. Fields: `total_count`, `freq`, `rssi`.
- **`"part": "chunk"`** (one or more) — Fields: `offset`, `durations` (this
  chunk's slice). Reassemble by placing `durations` at `offset` in a buffer
  of length `total_count`.
- **`"part": "end"`** — Fields: `total_count`. Signals the capture is
  complete; by this point `offset + len(durations)` across all chunks
  should sum to `total_count`.

A chunk's size is budgeted against the *negotiated MTU*
(`peerMtu - 3 - 80`, worst-case 7 bytes/value) — **negotiate the full
512-byte MTU on connect** (§1) for fewer, larger chunks. Only one chunked
capture can be "in flight" at a time: a new capture that needs chunking
while an earlier one is still sending replaces it (the earlier, incomplete
one is abandoned, no `"end"` for it) — this is a best-effort live stream,
same as every other unsolicited notification in this protocol, not a
guaranteed-delivery channel. A client should track `total_count` vs. bytes
actually reassembled and treat a capture it never saw `"end"` for as
incomplete/abandoned rather than silently trusting a partial result.

(This mirrors `CMD_TX_RAW_BEGIN`/`CHUNK`/`END`, §4.3.1, for the opposite
direction — same `begin`/`chunk(s)`/`end` shape, just unsolicited
notifications instead of commands, since the firmware is the one sending a
large payload here rather than receiving one.)

### 5.2 `rssi` decimation (`spectrum_sweep`)

Same problem as §5.1, different fix. A full sweep pass at the default
1MHz step produces 280+ samples — also too many to fit in one notification
— but unlike a RAW capture, cutting off the tail would mean never seeing
most of the band at all, which defeats the point of a spectrum overview.
Instead the firmware **downsamples**: when the real pass is longer than
what fits the negotiated MTU, it keeps every Nth sample (spanning the full
requested range at reduced resolution) and reports the adjusted effective
spacing in `step_mhz` (e.g. a requested `1.0` becomes `4.0` if 4x
downsampling was needed) along with `"decimated": true`. A client
reconstructing "frequency at sample index i" should use the *returned*
`step_mhz`, not whatever value it originally requested in `subghz sweep`.

Also worth knowing: the frequency-per-sample mapping is only exactly
linear within a band. `rf_skip_invalid_gap()` makes the sweep jump straight
over the CC1101's two hardware-unsupported gaps (350-387MHz, 468-779MHz)
rather than sampling through them, so a naive `start_freq + i * step_mhz`
calculation is wrong for any sample index after a gap-jump.

---

## 6. State model

The firmware is a single-session state machine (`RfState` in
`rf_service.h`): starting any new operation implicitly stops whatever was
running before it (no need to send `stop` first). The states are: `IDLE`,
`RX_DECODED`, `RX_RAW` *(reserved, not currently exposed over BLE — there's
no dedicated "RAW-only" mode; RAW capture instead comes through as the
`raw_pulse`/`scan_raw` fallback within `RX_DECODED`/`SCAN` whenever a
signal doesn't match a known protocol, §5.1)*, `LISTEN`, `SPECTRUM_FIXED`,
`SPECTRUM_SWEEP`, `JAM`, `BRUTEFORCE`, `SCAN`.

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
