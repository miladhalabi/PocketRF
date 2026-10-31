# PocketRF Mobile App — Feature Plan

Same feature set as [Bruce](https://github.com/pr3y/Bruce)'s RF module,
listed the same simple way Bruce lists its own features — the app is just
the screen/controls for every feature the firmware already has. Full wire
details for each one are in [`BLE_PROTOCOL.md`](BLE_PROTOCOL.md).

- [ ] Scan/Copy (auto-sweeps for a signal, locks on, decodes — RAW capture too when a signal doesn't match a known protocol)
- [ ] Fixed Freq Scan/Copy (same decode/RAW capture, but on a frequency you pick instead of auto-sweeping)
- [ ] Custom SubGhz (pick a protocol, enter a key, send it)
- [ ] KeeLoq (send a rolling-code key; manufacturer identified automatically on receive)
- [ ] Spectrum (live RSSI + sweep across the band)
- [ ] Jammer Full
- [ ] Jammer Intermittent
- [ ] Jammer Noise (CC1101 hardware PN9)
- [ ] Jammer Sweep (±5MHz hop)
- [ ] Bruteforce (enumerate a fixed-code protocol's full keyspace)
- [ ] Replay (send a saved/raw signal back out, Flipper `.sub`-compatible)
- [ ] Listen (frequency/pulse counter)
- [ ] Config
  - [ ] Frequency
  - [ ] Modulation preset (OOK/2-FSK/MSK/GFSK)
  - [ ] Scan RSSI threshold
