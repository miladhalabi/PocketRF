# PocketRF Mobile App — Feature & Menu Plan

Same feature set as [Bruce](https://github.com/pr3y/Bruce)'s RF module,
structured the same way Bruce structures it for its own users — mirrored
from Bruce's actual on-device RF menu
(`src/core/menu_items/RFMenu.cpp`). Full wire details for each one are in
[`BLE_PROTOCOL.md`](BLE_PROTOCOL.md).

Bruce's RF menu is a flat list opened from its main menu. PocketRF only
ever does RF, so that list **is** the app — no outer menu needed.

## Menu

- [ ] **Scan/copy** — auto-sweeps for a signal, locks on, decodes (RAW
      capture too when a signal doesn't match a known protocol — covers
      what Bruce splits out as "Record RAW")
- [ ] **Fixed Freq Scan/copy** — same decode/RAW capture, on a frequency
      you pick instead of auto-sweeping
- [ ] **Custom SubGhz** — pick a protocol, enter a key, send it; same
      screen covers KeeLoq (manufacturer identified automatically on
      receive) and raw/`.sub` replay
- [ ] **Spectrum** — live RSSI + sweep across the band (covers what Bruce
      splits into "Spectrum" / "RSSI Spectrum" / "SquareWave Spec" /
      "Spectogram" — those are different on-device visualizations of the
      same RSSI data, not different firmware features)
- [ ] **Listen** — frequency/pulse counter
- [ ] **Bruteforce** — enumerate a fixed-code protocol's full keyspace
- [ ] **Jammer** — Full / Intermittent / Noise / Sweep
- [ ] **Config**
  - [ ] Frequency
  - [ ] Modulation preset (OOK/2-FSK/MSK/GFSK)
  - [ ] Scan RSSI threshold
  - [ ] Back

Not carried over from Bruce's Config: RF TX/RX Pin and RF Module — those
exist because Bruce supports both raw-GPIO RF modules and CC1101; PocketRF
is CC1101-only, fixed hardware, so there's nothing to pick.
