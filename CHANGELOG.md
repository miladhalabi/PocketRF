# Changelog

All notable changes to PocketRF are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

There is no stable release yet. Until `1.0.0`, minor versions may change the BLE protocol.

## [Unreleased]

### Added

- Repository documentation: `LEGAL.md`, `THIRD_PARTY.md`, `CONTRIBUTING.md`, `SECURITY.md`,
  `docs/hardware.md`, `docs/getting-started.md`, and this changelog.
- GitHub Actions workflow building the firmware and the Android app, with a draft release on `v*`
  tags.
- `LICENSE` — GNU Affero General Public License v3.0.

### Changed

- Development notes moved to `docs/dev/` (`DEVELOPMENT_LOG.md`, `MOBILE_APP_PLAN.md`).
- README now leads with legal and licensing information and links to the new docs.

### Notes

- The release APK is signed with the debug key. Replace with a real signing key before any
  distribution beyond direct installs.
- The RF jammer remains included in the default build. See [LEGAL.md](LEGAL.md) before using it.

## [0.1.0] — 2026-11-18

### Added

- Initial firmware for the Seeed Studio XIAO ESP32C3 + CC1101: BLE GATT interface, OOK protocol
  engine, KeeLoq decoding, modulation presets, spectrum sweep, RAW capture/replay, auto scan+copy,
  bruteforce, and jammer modes.
- Android companion app (Kotlin, Jetpack Compose) with a local signal library.
- Python CLI for laptop testing.
