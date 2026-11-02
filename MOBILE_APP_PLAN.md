# PocketRF Mobile App — Feature & Menu Plan

Same feature set as [Bruce](https://github.com/pr3y/Bruce)'s RF module,
structured the same way Bruce structures it for its own users — mirrored
from Bruce's actual on-device RF menu
(`src/core/menu_items/RFMenu.cpp`). Full wire details for each one are in
[`BLE_PROTOCOL.md`](BLE_PROTOCOL.md).

Bruce's RF menu is a flat list opened from its main menu. PocketRF only
ever does RF, so that list **is** the app — no outer menu needed.

## Menu

- [x] **Scan/copy** — auto-sweeps for a signal, locks on, decodes, and now
      saves+replays a complete RAW capture too when a signal doesn't match
      a known protocol (covers what Bruce splits out as "Record RAW") —
      closed once `raw_pulse`/`scan_raw` started carrying real
      `durations`/`truncated` (`BLE_PROTOCOL.md` §5/§5.1)
- [x] **Fixed Freq Scan/copy** — same decode + RAW capture, on a frequency
      you pick instead of auto-sweeping
- [x]/[ ] **Custom SubGhz** — pick a protocol, enter a key, send it; same
      screen covers KeeLoq and `.sub` replay. Built. Not yet covered: a
      manual raw-duration-list entry form (raw replay currently only
      happens via a Scan/Fixed-Freq capture saved to the Library, not
      typed in by hand here)
- [x] **Spectrum** — live RSSI + sweep across the band (covers what Bruce
      splits into "Spectrum" / "RSSI Spectrum" / "SquareWave Spec" /
      "Spectogram" — those are different on-device visualizations of the
      same RSSI data, not different firmware features)
- [x] **Listen** — frequency/pulse counter
- [x] **Bruteforce** — enumerate a fixed-code protocol's full keyspace
- [x] **Jammer** — Full / Intermittent / Noise / Sweep, 30s safety cutoff
      surfaced in the UI (auto-returns to idle when `jam_done` fires).
      Built and verified compiling/rendering; not yet fired against real
      hardware
- [x] **Config**
  - [x] Frequency
  - [x] Modulation preset (OOK/2-FSK/MSK/GFSK)
  - [x] Scan RSSI threshold
  - [x] Back (the screen's own back arrow)

Not carried over from Bruce's Config: RF TX/RX Pin and RF Module — those
exist because Bruce supports both raw-GPIO RF modules and CC1101; PocketRF
is CC1101-only, fixed hardware, so there's nothing to pick.
