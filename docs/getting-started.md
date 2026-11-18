# Getting Started

From zero to a working PocketRF in about 20 minutes. This assumes you have already built the
hardware — if not, see [hardware.md](hardware.md) first.

> 🚨 **Read [LEGAL.md](../LEGAL.md) before you transmit.** This is a radio transmitter. Jamming is
> illegal in most jurisdictions, and even normal operation has duty-cycle and band rules.

---

## 1. Build the hardware

Wire the XIAO ESP32C3 to the CC1101 as shown in [hardware.md](hardware.md). Connect the antenna
**before** applying power. Do not skip this.

---

## 2. Flash the firmware

### Option A — Web flasher (easiest)

Open **https://miladhalabi.github.io/PocketRF/** in desktop Chrome, Edge, or Opera, plug the board
in over USB, and click **Connect**. No toolchain needed. First install erases the flash.

### Option B — PlatformIO

PocketRF uses **PlatformIO**. Install it first if you do not have it:

```bash
pip install platformio
```

Then, from the repository root:

```bash
# Compile
pio run

# Flash to the XIAO ESP32C3 over USB
pio run -t upload

# Watch the serial log (115200 baud)
pio device monitor
```

The board advertises over BLE as **`XIAO-RF-BLE`**. If you do not see it, check the serial log for a
boot error and confirm the board is in the right mode.

> The `platformio.ini` pins a specific pioarduino platform release. If the download URL ever moves,
> update that line — but keep the version pinned so builds stay reproducible.

---

## 3. Install the Android app

The app is in [`android_app/`](../android_app). It needs **JDK 17** and the Android SDK
(`compileSdk`/`targetSdk` 35, `minSdk` 26).

```bash
cd android_app
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On first connect, grant Bluetooth permissions. On Android 12+ you may also need location permission
for BLE scanning on some devices.

> Prebuilt APKs are attached to GitHub Releases once a release is tagged. Building from source is the
> supported path until then. Note that the debug build is signed with the debug key — it is not a
> production-signed app.

---

## 4. First connection

1. Open the app and scan for **`XIAO-RF-BLE`**.
2. Connect. The app negotiates a larger MTU when the phone supports it.
3. Try **Scan / copy** first: it sweeps the ISM bands, locks onto an active signal, and decodes it.
4. Save a capture explicitly with **name-and-save** — nothing is saved automatically.

If nothing appears, press one of your own remotes near the antenna and try again. Test with your own
hardware only.

---

## 5. Python CLI (optional)

For testing from a laptop without the app, use the Bleak-based CLI:

```bash
pip install bleak
python3 pocketrf_cli.py
```

Run it with no arguments for a numbered menu. The loose `rx_*.py` / `tx_*.py` scripts in the repo
root are one-off experiments; the CLI is the supported client.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
| :--- | :--- | :--- |
| Device not found over BLE | Missing permissions, or out of range | Grant Bluetooth/location permission; move closer; power-cycle the board |
| Upload fails | Board not in bootloader mode | Hold **BOOT**, tap **RESET**, release **BOOT**, retry |
| No serial output | Wrong port or baud | Select the USB CDC port; set 115200 baud |
| Very short range | Wrong antenna band, or no antenna | Fit the antenna matching your module's band |
| Garbled captures | Long/loose SPI wires, or noisy supply | Shorten wires; use a stable 3.3 V supply |

Still stuck? Open an issue with your board, firmware version, and the serial log.
