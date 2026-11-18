# Third-Party Components & Provenance

PocketRF is licensed **AGPL-3.0** (see [`LICENSE`](LICENSE)). That choice is not arbitrary: the RF
engines are adapted from Bruce, which is AGPL-3.0, so the combined work must carry the same license.

This file records what came from where, so downstream users and distributors can verify it.

## Vendored / adapted code

| Component | Used for | License | Source |
| :--- | :--- | :--- | :--- |
| **Bruce** | OOK protocol engines, KeeLoq NLF implementation, RMT timing patterns, RAW replay, jammer modes, spectrum sweep approach | **AGPL-3.0** | <https://github.com/pr3y/Bruce> |
| **SmartRC-CC1101-Driver-Lib** (`ELECHOUSE_CC1101_SRC_DRV.*`) | CC1101 register-level driver, config calculators | Custom permissive: *"You can freely use, edit or distribute it with reference to the source."* Upstream notes that portions derive from Elechouse's 2010 library (© Michael) and Little Satan's additions. | <https://github.com/bmorcelli/SmartRC-CC1101-Driver-Lib> |

The SmartRC license is not an OSI-approved identifier (GitHub reports it as `NOASSERTION`). Its
terms permit use and redistribution **provided the source is credited**, which is why this table
exists. If you redistribute PocketRF binaries, keep this attribution and the upstream repository
reference intact.

## Build dependencies

| Component | Version | License | Source |
| :--- | :--- | :--- | :--- |
| NimBLE-Arduino | `^2.5.0` | Apache-2.0 | <https://github.com/h2zero/NimBLE-Arduino> |
| ArduinoJson | `^7.0.0` | MIT | <https://github.com/bblanchon/ArduinoJson> |
| pioarduino `platform-espressif32` | 55.03.39 | Apache-2.0 platform definition, with Espressif binary blobs under their own terms | <https://github.com/pioarduino/platform-espressif32> |
| Bluedroid / NimBLE stack | via ESP-IDF | Apache-2.0 | Espressif |

Apache-2.0 and MIT are compatible with inclusion in an AGPL-3.0 work. The pioarduino platform is a
**build-time** dependency; it is not redistributed as part of PocketRF.

## Android app

| Component | License |
| :--- | :--- |
| AndroidX (core, lifecycle, activity, navigation) | Apache-2.0 |
| Jetpack Compose (UI, Material3, BOM 2024.10.01) | Apache-2.0 |
| Kotlin / kotlinx-coroutines | Apache-2.0 |

## Python CLI

| Component | License |
| :--- | :--- |
| [Bleak](https://github.com/hbldh/bleak) | MIT |

## Data files

| Asset | Notes |
| :--- | :--- |
| `include/rf_keeloq_mfcodes_data.h` | AES-256-CBC encrypted KeeLoq manufacturer keystore, taken from Bruce (the same blob Bruce ships), with its decryption key compiled into `src/rf_keeloq.cpp`. Distributed as part of an AGPL-3.0 work. |

> ⚠️ **Before any commercial use or paid distribution:** the ultimate provenance of the KeeLoq
> manufacturer database itself (as distinct from the encryption applied to it) is not documented
> upstream. It is a compiled identification table, not creative source code, but if you intend to
> sell hardware with it, confirm its origin independently.

## Case design & fonts

The case in `hardware/case/` is the project's own design (FreeCAD). It is not third-party code.

| Component | License | Source |
| :--- | :--- | :--- |
| Black Ops One | SIL Open Font License 1.1 | Google Fonts |
| Chakra Petch | SIL Open Font License 1.1 | Google Fonts |

The fonts are used to render the "PocketRF" text in the case model. They are redistributed with the
case files; keep their licence terms if you redistribute them. OFL 1.1 is compatible with an
AGPL-3.0 work.

## Adding a dependency

If you add a library, add a row to the relevant table above with its license and source URL. AGPL
compatibility is a requirement, not a preference.
