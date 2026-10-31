# PocketRF Mobile App — Implementation Plan

This is the implementation plan for the companion mobile app: the client
that drives every feature PocketRF's firmware exposes over BLE. It assumes
[`BLE_PROTOCOL.md`](BLE_PROTOCOL.md) as the single source of truth for the
wire protocol — this document is about what the app builds on top of that
protocol, not a restatement of it.

PocketRF is headless and storage-free by design: the firmware has no SD
card, no display, and keeps no history. **The app owns everything the
firmware doesn't** — persistence, a signal library, `.sub` file import/
export, UI, and all user-facing safety gates. That division of
responsibility drives most of the architecture below.

---

## 1. Goals & scope

Use **every** capability the firmware exposes — nothing in
`BLE_PROTOCOL.md` should be unreachable from the app:

- Device connection, status, and health (`CMD_STATUS`, MARCSTATE/RSSI
  diagnostics)
- Receive & decode (`subghz rx` → `decoded_rf`/`raw_pulse`), with KeeLoq
  manufacturer identification surfaced in the UI
- Frequency/pulse listener (`subghz listen` → `listen_pulse`)
- Spectrum tools: fixed RSSI stream and sweep (`subghz rssi`/`subghz sweep`
  → `spectrum_fixed`/`spectrum_sweep`)
- Transmit: registered protocols, KeeLoq, raw duration lists, and full
  `.sub` RAW replay (`subghz txp`/`keeloqtx`/`rawtx`/`RAWSUB `)
- Modulation presets (`subghz preset`)
- RF Jammer, all 4 modes, with the 30s safety cutoff respected and
  surfaced (`subghz jam`)
- Bruteforce keyspace enumeration (`subghz brute`)
- Auto Scan + Copy, including the tunable RSSI threshold
  (`subghz scan [threshold]`)
- A local signal library: save/load/organize captured and hand-entered
  codes, import/export Flipper-compatible `.sub` files — since the
  firmware has no SD card, this is entirely the app's job

Non-goals for v1: multi-device management (one connected PocketRF at a
time — the firmware itself only accepts one BLE central anyway), cloud
sync, anything that requires firmware changes.

---

## 2. Platform & stack

**Target: Android first.** BLE GATT central-mode tooling is more mature and
predictable on Android than iOS for a protocol like this (arbitrary binary
framing, long-running notification streams, background operation during a
scan/jam session), and it's the more common platform for this kind of
hobbyist/educational RF tool. iOS (CoreBluetooth) is a plausible follow-up
port once the protocol layer below is stable — the BLE command/response
layer (§4) is the only piece that's platform-specific; everything above it
(domain models, view models, UI structure) ports conceptually even if the
UI toolkit changes.

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin | Standard for modern Android |
| UI | Jetpack Compose | Declarative UI maps cleanly onto a stream-heavy, state-driven app (constant notification updates) |
| BLE | Android `BluetoothGatt` APIs, wrapped in a coroutine-based client (no third-party BLE library needed — the protocol is simple enough that a thin wrapper is less risk than a heavy dependency) | Full control over MTU negotiation, write queuing, and notification correlation, which this protocol needs (see §4.3) |
| Local persistence | Room (SQLite) | Signal library, scan history, saved presets |
| Async | Kotlin Coroutines + Flow | Natural fit for a BLE notification stream → UI state pipeline |
| DI | Manual or Hilt (either is fine; Hilt only pays off once the app has enough screens to justify it) | |

---

## 3. Architecture

```
┌─────────────────────────────────────────────────────┐
│ UI (Compose screens, §6)                             │
├─────────────────────────────────────────────────────┤
│ ViewModels (one per screen/feature area)              │
├─────────────────────────────────────────────────────┤
│ Domain / Repositories                                 │
│  - DeviceRepository    (connection, status)           │
│  - RfSessionRepository (RX/listen/spectrum/scan state) │
│  - TxRepository        (protocol/KeeLoq/raw/preset TX) │
│  - SignalLibraryRepository (Room-backed, §7)           │
├─────────────────────────────────────────────────────┤
│ BLE Protocol Layer (§4)                                │
│  - PocketRfConnection (GATT lifecycle, MTU, writes)    │
│  - Binary framing (encode/decode, CRC8)                │
│  - ASCII command builders                              │
│  - Notification parser → sealed NotificationEvent       │
├─────────────────────────────────────────────────────┤
│ Android BluetoothGatt                                 │
└─────────────────────────────────────────────────────┘
```

Repositories expose Kotlin `Flow`s of domain state (e.g.
`RfSessionRepository.state: StateFlow<RfSessionState>`), not raw BLE
notifications — the protocol layer's job is entirely translation between
wire format and typed domain objects, so nothing above it ever touches a
`ByteArray` or a raw JSON string.

---

## 4. BLE protocol layer

This is the highest-risk part of the app — get it right once, and every
feature screen above it is comparatively straightforward.

### 4.1 Connection lifecycle

- Scan for `XIAO-RF-BLE` (filter by name or by the service UUID
  `0000cc11-...`).
- Connect, discover services, **negotiate MTU to 512** before doing
  anything else (BLE_PROTOCOL.md §1) — a failed/low MTU negotiation should
  be visible in the UI, not silently degrade.
- Subscribe to NOTIFY on the TX characteristic (`0000cc13-...`) before
  sending any commands.
- The firmware accepts **only one BLE central at a time** and does not
  resume advertising until a clean disconnect (BLE_PROTOCOL.md §1) — the
  app should disconnect explicitly and promptly on backgrounding/screen
  exit rather than relying on the OS to tear down the GATT link eventually,
  since a lingering connection (crash, force-kill) locks other clients out
  until it times out or is dropped at the OS/BlueZ level. Handle
  `onConnectionStateChange` disconnect codes and surface "still holding a
  stale connection" scenarios with a clear retry path.
- Reconnection: exponential backoff, capped retries, clear UI state
  (`Disconnected` / `Connecting` / `Connected` / `Reconnecting`).

### 4.2 Command encoding

- **Binary frames** (`0xAA`-framed, CRC8 per BLE_PROTOCOL.md §3) for every
  command that has a binary form — prefer these over ASCII for anything the
  UI triggers programmatically (status polling, starting/stopping
  sessions, TX), since they're compact, typed, and CRC-verified.
- **ASCII (`subghz ...`)** only where the binary protocol doesn't cover it
  or where a raw/debug text entry field is explicitly useful (e.g. an
  "advanced" raw-command console for power users, mirroring
  `pocketrf_cli.py`).
- **`RAWSUB `** for `.sub` file replay: build the exact `Filetype: Bruce
  SubGhz File / Version 1 / Frequency: ... / RAW_Data: ...` text Bruce's
  own format uses (the firmware's parser expects this verbatim), prefixed
  with `RAWSUB ` and no intervening newline before the content.
  - **MTU chunking:** BLE_PROTOCOL.md flags that the firmware does **not**
    reassemble a `RAWSUB` payload split across multiple writes. For files
    that fit under the negotiated MTU, send as one write. For files that
    don't, the app needs one of: (a) warn and refuse (simplest, matches
    `pocketrf_cli.py`'s current behavior), or (b) split the `RAW_Data`
    values into multiple separate `RAWSUB` sends, each a self-contained
    valid `.sub` text with its own `Frequency:` line, played back as
    sequential TX calls client-side. Start with (a) for v1; revisit (b) if
    large captures turn out to matter in practice. A client-side-only fix
    (b) needs no firmware change; true multi-write reassembly would.

### 4.3 Response/notification correlation

This is the protocol's trickiest property for a client: **every**
notification arrives on the same TX characteristic, and the firmware
sends:
1. Exactly one response to each command you send (JSON with `"status"`
   for ASCII, a binary frame for binary commands), and
2. An unbounded stream of unsolicited event notifications while a session
   is active (`decoded_rf`, `raw_pulse`, `listen_pulse`, `spectrum_fixed`,
   `spectrum_sweep`, `jam_done`, `bruteforce_done`, `scan_locked`,
   `scan_decoded`), interleaved with those responses in no particular
   order relative to when you sent the command.

Design the notification handler as a single dispatcher that:
- Parses every incoming write as either **binary** (starts with `0xAA`) or
  **JSON text**.
- For JSON: branches on the presence of `"type"` — present means an
  unsolicited event (route to the relevant session's event stream);
  absent means a command response (route to whichever pending
  command is waiting, matched by `"cmd"` field + a client-side timeout, not
  by any sequence number — **the protocol has no request ID**, so a client
  issuing overlapping commands before the first one's response arrives
  cannot reliably tell them apart. Serialize command writes: never have
  more than one in flight, enforced by the connection layer, not by each
  caller remembering to do so).
- For binary: match by `cmdEcho` (`cmd | 0x80`) against the pending
  binary command.
- A command with no matching response within a timeout (~3s) surfaces as a
  client-side timeout error, distinct from a firmware-reported error.

### 4.4 State model mirroring

Mirror the firmware's single-session state machine (BLE_PROTOCOL.md §6)
client-side: starting any new RF operation implicitly stops whatever was
running. The app's `RfSessionRepository` should model this as one
`sealed class RfSession` (`Idle`, `Receiving`, `Listening`,
`SpectrumFixed`, `SpectrumSweep`, `Jamming`, `Bruteforcing`, `Scanning`)
rather than independent booleans per feature — so the UI can never show two
"active" feature screens at once, matching what the firmware actually
allows.

---

## 5. Data model (app-owned, since the firmware has none)

```kotlin
// A captured or hand-entered signal, decoded or raw.
data class SavedSignal(
    val id: Long,
    val label: String,                  // user-editable name
    val protocol: String,                // "CAME", "KeeLoq", "RAW", ...
    val freqMhz: Float,
    val bits: Int,
    val key: String,                     // hex string
    val te: Int?,                        // base pulse length, null for RAW
    val rawDurations: List<Int>?,        // null unless protocol == "RAW"
    val mfName: String?,                 // KeeLoq manufacturer, null otherwise
    val btn: Int?, val serial: Long?, val cnt: Int?,  // KeeLoq rolling-code fields
    val capturedAt: Instant,
    val source: SignalSource,            // Captured / Imported / HandEntered
)

enum class SignalSource { CAPTURED, IMPORTED_SUB, HAND_ENTERED }

// A completed spectrum sweep pass, for history/comparison.
data class SweepResult(
    val id: Long,
    val startMhz: Float, val endMhz: Float, val stepMhz: Float,
    val rssiSamples: List<Int>,
    val recordedAt: Instant,
)

// Bruteforce run bookkeeping (resumable if the app restarts mid-run).
data class BruteforceRun(
    val id: Long,
    val protocol: String, val bits: Int, val freqMhz: Float, val repeat: Int,
    val startedAt: Instant, val completedAt: Instant?,
)
```

`SavedSignal` is the central entity: a capture from `subghz rx`/`subghz
scan`, a hand-entered code, and an imported `.sub` file all become one,
feeding the same TX flow (§6.4) and the same library screen (§6.8).

---

## 6. Screens (mapped to firmware features)

### 6.1 Connect / Device

- Scan, connect, show connection state + negotiated MTU.
- `CMD_STATUS` polled periodically while on this screen: detected,
  version, partnum, MARCSTATE, frequency, RSSI, RX-active.
- Surface the known hardware caveats from BLE_PROTOCOL.md directly in the
  UI rather than leaving them as developer-only knowledge: a `partnum != 0`
  or `version == 0xFF` should show a visible "hardware may not be
  responding correctly" warning, not just `detected: true`.

### 6.2 Receive & Decode

- `subghz rx` / `CMD_START_RX`, stop via the shared stop action.
- Live feed of `decoded_rf` (protocol, bits, key, frequency, RSSI; for
  KeeLoq: manufacturer name, button, serial, and counter once identified)
  and `raw_pulse` (count, frequency, RSSI) events.
- One-tap "save to library" on any captured signal (§6.8).
- Frequency picker (common presets from BLE_PROTOCOL.md's frequency list,
  plus manual entry).

### 6.3 Listen (frequency counter)

- `subghz listen` / `CMD_START_LISTEN`.
- Live `listen_pulse` display: detected frequency (Hz), pulse duration,
  RSSI. Useful as a quick "is anything transmitting near here" tool,
  distinct from full decode.

### 6.4 Transmit

- Three TX modes in one screen (tabs or a mode switch):
  1. **Registered protocol** — pick from the protocol table
     (BLE_PROTOCOL.md §4.4), enter/paste a hex key, bits, optional te/repeat.
  2. **KeeLoq** — raw 64-bit hex key entry, or built from a saved
     `SavedSignal`.
  3. **Raw/`.sub`** — pick a saved/imported `SavedSignal` with
     `rawDurations`, replay via `RAWSUB` (or `CMD_TX_RAW` for anything
     under the binary payload's practical size).
- Preset selector (`subghz preset`) available as an advanced option before
  any TX, with the 6 presets from BLE_PROTOCOL.md §4.5.
- **Every TX action needs an explicit confirm step** — this is the one
  category of action in the whole app with real-world legal/interference
  consequences (see §8).

### 6.5 Spectrum

- Fixed RSSI stream (`subghz rssi`) — live numeric + simple strip-chart
  view, useful on its own and as the recommended way to find a board's
  noise floor before tuning the scan threshold (§6.7).
- Sweep (`subghz sweep`) — bar/line chart of the `spectrum_sweep` RSSI
  array per pass, with start/end/step controls. Note from
  BLE_PROTOCOL.md/DEVELOPMENT_LOG.md: the firmware already skips the
  CC1101's two hardware-unsupported frequency gaps (350-387MHz,
  468-779MHz) during a sweep — the app doesn't need to special-case them,
  but shouldn't assume uniform frequency spacing across the full result
  array near those boundaries.

### 6.6 Jammer

- Mode picker (FULL/ITMT/NOISE/SWEEP), frequency, start/stop.
- Visible countdown reflecting the firmware's **hard 30-second safety
  cutoff** — the app should not imply jamming can run indefinitely, and
  should surface the `jam_done` notification's `reason: "timeout"` clearly
  rather than silently returning to idle.
- Gated behind the same legal-use acknowledgment as the rest of the
  jammer/bruteforce features (§8) — consider a distinct, more deliberate
  confirmation here given jamming is the single most legally sensitive
  feature in scope.

### 6.7 Auto Scan + Copy

- `subghz scan [threshold]`, with the RSSI threshold exposed as a visible,
  adjustable setting — not buried. Default `-65`, but surface guidance
  from BLE_PROTOCOL.md §4.6 directly: suggest checking the live RSSI
  screen (§6.5) first to find the board's actual noise floor, and warn
  that too-loose a threshold means it'll lock onto noise, not a real
  signal (`scan_locked` with no `scan_decoded` following is the sign of
  that).
- Live state: searching → locked (show frequency) → decoding
  (`scan_decoded` events, same display as §6.2's capture feed, saveable
  the same way).

### 6.8 Bruteforce

- Protocol/bits/frequency/repeat selection, same protocol table as §6.4.
- **Set real expectations in the UI**: show the computed keyspace size
  (`2^bits`) and an estimated runtime before starting — a 24-bit protocol
  at even a fast repeat rate is hours, not seconds, and the app should say
  so rather than let the user discover it by watching a progress bar that
  isn't moving.
- Progress display (there's no built-in progress notification from the
  firmware beyond `bruteforce_done` — the app estimates progress from
  elapsed time vs. the estimated total, or periodically polls `CMD_STATUS`
  while running if a code counter turns out to be worth adding there later).
- Stoppable at any time via the shared stop action; `bruteforce_done`
  signals natural completion (keyspace exhausted).

### 6.9 Signal Library

- Browse/search/rename/delete `SavedSignal` entries.
- Import a `.sub` file from device storage, parse its header (`Frequency:`,
  `Protocol:`, `RAW_Data:` or a registered protocol's data) into a
  `SavedSignal`.
- Export a `SavedSignal` back to a `.sub` file (for RAW entries, regenerate
  the same text format the firmware's `rf_parse_sub_raw()` reads).
- One-tap "transmit this" into §6.4.

### 6.10 Settings / Advanced

- RSSI threshold defaults (used to pre-fill §6.7).
- Raw command console (free-text ASCII entry + response log) for anything
  not yet wrapped in a dedicated screen — useful during development and as
  an escape hatch, mirroring `pocketrf_cli.py`'s role for this project.
- About/diagnostics: firmware version info if/when the firmware exposes
  one (it currently doesn't — note as a possible future firmware addition,
  out of scope for the app alone).

---

## 7. Signal library persistence

Room database, one table per entity in §5. Keep the schema additive-only
once the app ships (migrations are cheap now, costly once real libraries
exist). `SavedSignal` is the one entity worth getting right early, since
every other screen either writes to it (capture, import) or reads from it
(transmit, library browse).

---

## 8. Safety, legal, and scope framing

This app controls real RF transmission hardware, including a deliberate
jammer and a bruteforce transmitter. Consistent with this project's stated
educational purpose:

- A **one-time acknowledgment gate** on first launch (or first use of
  TX/jam/bruteforce specifically) stating the tool is for authorized
  testing/educational use only, and that RF transmission — especially
  jamming — is regulated and often illegal without authorization in most
  jurisdictions. Don't make this a dead-end legal-disclaimer screen nobody
  reads; keep it short and specific.
- No feature gating beyond that acknowledgment — this is an educational
  tool for a university project, not a product deciding what its user is
  allowed to do. The acknowledgment is about informed use, not permission-
  seeking from the app.
- Every TX-class action (txp, KeeLoq, raw, jam, bruteforce) requires an
  explicit confirm tap; nothing transmits from a single accidental tap.

---

## 9. Phased roadmap

**Phase 1 — Connection + core RX (the foundation everything else needs)**
BLE protocol layer (§4) end-to-end: connect, MTU negotiation, binary
framing, notification dispatch/correlation. Connect screen (§6.1). Receive
& Decode screen (§6.2). This phase is disproportionately the hard part —
once command/response correlation and the notification dispatcher are
solid, every other screen is mostly "new command builder + new event
type."

**Phase 2 — Transmit + Signal Library**
Transmit screen (§6.4), Room-backed Signal Library (§6.9, §7), `.sub`
import/export. This is the pair that makes the app actually useful
end-to-end (capture something in Phase 1, save it, send it back).

**Phase 3 — Spectrum + Listen**
Spectrum screen (§6.5), Listen screen (§6.3). Lower complexity than Phase
1/2, mostly visualization work once the protocol layer exists.

**Phase 4 — Scan + Copy**
Auto Scan screen (§6.7), including the threshold-tuning UX — do this after
Phase 1-3 land since it reuses the same decode-display logic as §6.2 and
benefits from the Spectrum screen already existing (to find a sensible
threshold).

**Phase 5 — Jammer + Bruteforce**
Both gated behind §8's acknowledgment. Jammer (§6.6) is UI-simple but
needs the safety-cutoff UX done carefully. Bruteforce (§6.8) needs the
keyspace/runtime-estimate UX to avoid a confusing "nothing is happening"
experience on large keyspaces.

**Phase 6 — Polish**
Raw command console (§6.10), presets wired into Transmit (§6.4), app-wide
error/reconnection UX hardening, any firmware-version/diagnostics surfacing
once useful.

---

## 10. Open questions for firmware/app co-design

Flagging rather than resolving, since these cross the firmware/app
boundary and are worth deciding deliberately rather than defaulting into:

- **RAWSUB chunking** (§4.2): does it matter in practice, or do captured
  RAW signals stay small enough that refuse-if-too-large is fine
  permanently?
- **Bruteforce progress**: worth adding a lightweight progress field to
  `CMD_STATUS` or a periodic progress notification, so the app isn't
  purely estimating from elapsed time?
- **Multi-connection**: if a second client (e.g. a desktop tool) ever needs
  to talk to the same device, the firmware's single-connection limit
  becomes a real product constraint, not just a current limitation — worth
  knowing now whether that's ever a requirement.

---

See [`BLE_PROTOCOL.md`](BLE_PROTOCOL.md) for the complete, authoritative
wire protocol this plan builds on, and
[`DEVELOPMENT_LOG.md`](DEVELOPMENT_LOG.md) for how each firmware feature
referenced here was built and verified.
