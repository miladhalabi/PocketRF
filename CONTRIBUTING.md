# Contributing to PocketRF

Thanks for helping. PocketRF is a three-part project — ESP32-C3 firmware, an Android app, and a
Python CLI — so the first thing worth knowing is which part you are touching.

Read [`LEGAL.md`](LEGAL.md) before you start. It is short, and it applies to contributors too:
do not propose features whose purpose is to interfere with other people's radio or devices.

## Repository layout

```
src/            Firmware implementation (C++, PlatformIO)
include/        Firmware headers and embedded data
android_app/    Kotlin + Jetpack Compose companion app
pocketrf_cli.py Python BLE client / interactive test menu
BLE_PROTOCOL.md The BLE contract — the source of truth shared by all three parts
docs/           User-facing documentation
docs/dev/       Development log and internal plans
```

`BLE_PROTOCOL.md` is the contract. **Any change to a command, payload, notification, or
characteristic must update that file in the same pull request.** Firmware and clients are developed
separately and go out of sync otherwise.

## Building

**Firmware** (requires [PlatformIO](https://platformio.org/)):

```bash
pio run                        # compile
pio run -t upload              # flash the XIAO ESP32C3
pio device monitor             # 115200 baud
```

`platformio.ini` pins a specific pioarduino platform build. If the first build seems to hang, it is
downloading that toolchain — it is large.

**Android app** (JDK 17 + Android SDK, `compileSdk` 35):

```bash
cd android_app
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Python CLI:**

```bash
pip install bleak
python3 pocketrf_cli.py
```

## Pull requests

- **One concern per PR.** Protocol change, refactor, and UI restyle in one diff is very hard to
  review against a device you may not have.
- **Say what you tested on.** Real hardware, which band, which client — or "compile-only". A PR
  claiming RF behaviour works without a device is not verifiable, and reviewers will ask.
- **Match surrounding style.** The firmware is plain C++ with explicit types and `m_` members; the
  app uses Compose with state hoisted to view models. Follow the file you are editing.
- **Don't reformat unrelated code.** It hides the real diff.
- **Commit messages** in the imperative mood, describing intent: `Fix raw capture overwrite on
  chunk boundary`, not `fixed stuff`.

## Working on the protocol

When you change `BLE_PROTOCOL.md`:

1. Update the firmware handler in `src/cmd_router.cpp`.
2. Update the Android side in `android_app/.../ble/PocketRfProtocol.kt`.
3. Update `pocketrf_cli.py` if it exposes the command.
4. Note the change in `docs/dev/DEVELOPMENT_LOG.md`.

## What is most useful right now

- Reports from **real hardware** in different regions (band behaviour, sensitivity, antenna results).
- Known-good `.sub` files and captured vectors to use as regression fixtures — the codecs are pure
  logic and are currently untested.
- Android UI/accessibility polish from people running it on varied screen sizes.
- Documentation fixes, especially jurisdiction-specific notes for `LEGAL.md`.

## Reporting bugs

Include firmware commit, app version, phone model and Android version, and the device's serial log
around the failure. `pio device monitor` output is the single most useful thing you can attach.

Security issues go through [`SECURITY.md`](SECURITY.md), not the public tracker.

## License

By contributing you agree your work is licensed under AGPL-3.0, the same terms as the project. See
[`THIRD_PARTY.md`](THIRD_PARTY.md) for the upstream components whose licenses constrain that choice.
