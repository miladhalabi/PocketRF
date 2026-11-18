# Security Policy

## Scope

PocketRF is a local radio transceiver project. It has no server, no accounts, and no cloud
component. The attack surface is:

- **The firmware** — BLE GATT service, command parser, and the RF/decoder code paths.
- **The Android app** — BLE client, local signal library, and any file parsing (`.sub` replay).
- **The Python CLI** — local BLE client only.

Reports that matter most: memory-safety issues in the firmware's packet parsing, malformed BLE
payloads that crash or brick the device, and path/format issues in `.sub` parsing.

Out of scope: the fact that the project can transmit on regulated frequencies, and the jammer's
existence. Those are documented in [LEGAL.md](LEGAL.md) and are by design.

## Reporting a vulnerability

**Do not open a public issue for a security problem.** Instead:

1. Email the maintainer at the address listed in the repository's GitHub profile, or use GitHub's
   **private vulnerability reporting** (Security → Report a vulnerability) if enabled.
2. Include: firmware/app version, board, steps to reproduce, and impact.
3. Allow reasonable time for a fix before disclosure. This is a hobby-scale project — expect a
   best-effort response, not a guaranteed SLA.

## Supported versions

There are no stable releases yet. Only the latest commit on `main` is supported. Fixes land on
`main` and are noted in [CHANGELOG.md](CHANGELOG.md) when one exists.

## Known limitations

- **BLE is unauthenticated.** The GATT service has no pairing or encryption; anything in range can
  connect and send commands. Do not treat a PocketRF device as a trusted or private device.
- **The release APK is debug-signed.** See [docs/getting-started.md](docs/getting-started.md).
- **The KeeLoq keystore is compiled in.** See [THIRD_PARTY.md](THIRD_PARTY.md).
