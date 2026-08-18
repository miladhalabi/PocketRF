#!/usr/bin/env python3
"""
PocketRF Quick Interactive Test & Control Menu
----------------------------------------------
Run without arguments for an instant numbered menu:
  python3 pocketrf_cli.py
"""

import argparse
import asyncio
import json
import sys
from bleak import BleakScanner, BleakClient

SERVICE_UUID = "0000cc11-0000-1000-8000-00805f9b34fb"
RX_CHAR_UUID = "0000cc12-0000-1000-8000-00805f9b34fb"
TX_CHAR_UUID = "0000cc13-0000-1000-8000-00805f9b34fb"
TARGET_NAME = "XIAO-RF-BLE"


class PocketRFClient:
    def __init__(self, device_name=TARGET_NAME):
        self.device_name = device_name
        self.client = None
        self.response_event = asyncio.Event()
        self.last_response = None

    async def connect(self):
        print(f"🔍 Scanning for BLE device '{self.device_name}'...")

        def match_target(device, adv):
            if device.name == self.device_name or adv.local_name == self.device_name:
                return True
            return SERVICE_UUID in adv.service_uuids

        device = await BleakScanner.find_device_by_filter(match_target, timeout=8.0)
        if not device:
            print(f"❌ Device '{self.device_name}' not found!")
            print("   Ensure Seeed XIAO ESP32C3 is powered on and advertising.")
            return False

        print(f"✓ Found '{self.device_name}' [{device.address}]. Connecting...")
        self.client = BleakClient(device)
        await self.client.connect()

        if self.client.is_connected:
            print("✓ Connected to PocketRF Hardware over BLE!\n")
            await self.client.start_notify(TX_CHAR_UUID, self._on_notification)
            return True
        return False

    async def disconnect(self):
        if self.client and self.client.is_connected:
            await self.client.stop_notify(TX_CHAR_UUID)
            await self.client.disconnect()
            print("✓ Disconnected safely.")

    def _on_notification(self, sender, data: bytearray):
        payload = data.decode("utf-8", errors="replace")
        try:
            parsed = json.loads(payload)
        except json.JSONDecodeError:
            parsed = {"raw": payload}

        if parsed.get("type") in ("decoded_rf", "raw_pulse"):
            print(f"\n📡 [RF CAPTURE EVENT] {json.dumps(parsed, indent=2)}")
        else:
            print(f"\n📥 [RESPONSE] {json.dumps(parsed)}")
            self.last_response = parsed
            self.response_event.set()

    async def send_cmd(self, cmd_text: str, wait=True):
        if not self.client or not self.client.is_connected:
            print("❌ Error: BLE Client is not connected.")
            return None

        self.response_event.clear()
        self.last_response = None
        print(f"---> Command: '{cmd_text}'")
        await self.client.write_gatt_char(
            RX_CHAR_UUID, cmd_text.encode("utf-8"), response=True
        )

        if wait:
            try:
                await asyncio.wait_for(self.response_event.wait(), timeout=3.0)
                return self.last_response
            except asyncio.TimeoutError:
                print("⚠️ Notice: Request submitted.")
                return None
        return None


async def start_rx_session(rf_client, freq_mhz):
    print(f"\n==================================================")
    print(f" 🎧 LISTENING ON {freq_mhz} MHz")
    print(f" Press Enter or Ctrl+C to return to main menu.")
    print(f"==================================================")

    await rf_client.send_cmd(f"FREQ {freq_mhz}")
    await rf_client.send_cmd("RMT RX START")

    loop = asyncio.get_event_loop()
    try:
        await loop.run_in_executor(None, sys.stdin.readline)
    except (KeyboardInterrupt, asyncio.CancelledError):
        pass

    print("\nStopping Receiver...")
    await rf_client.send_cmd("RMT RX STOP")


async def interactive_menu(rf_client):
    loop = asyncio.get_event_loop()

    while True:
        print("\n==================================================")
        print("       PocketRF Quick Hardware Test Menu          ")
        print("==================================================")
        print(" [1]  Receive on 433.92 MHz  (Default Sniffer)")
        print(" [2]  Receive on 315.00 MHz")
        print(" [3]  Receive on 700.00 MHz")
        print(" [4]  Receive on 779.00 MHz")
        print(" [5]  Receive on 868.35 MHz")
        print(" [6]  Receive on Custom Frequency...")
        print("--------------------------------------------------")
        print(" [7]  Transmit CAME (Key: A) @ 433.92 MHz")
        print(" [8]  Transmit Princeton (Key: 123456) @ 433.92 MHz")
        print(" [9]  Transmit Princeton (Key: 123456) @ 700.00 MHz")
        print(" [10] Transmit Princeton (Key: 123456) @ 779.00 MHz")
        print(" [11] Transmit Princeton (Key: 123456) @ 868.35 MHz")
        print(" [12] Transmit KeeLoq (Key: 123456789ABCDEF0) @ 868.35 MHz")
        print(" [13] Transmit Custom Protocol / Key...")
        print("--------------------------------------------------")
        print(" [14] Frequency Counter / Listener @ 433.92 MHz")
        print(" [15] Query Device Hardware Status")
        print(" [0]  Exit")
        print("==================================================")

        try:
            choice = await loop.run_in_executor(None, input, "Select choice (0-14): ")
            choice = choice.strip()

            if choice == "0":
                break
            elif choice == "1":
                await start_rx_session(rf_client, 433.92)
            elif choice == "2":
                await start_rx_session(rf_client, 315.00)
            elif choice == "3":
                await start_rx_session(rf_client, 700.00)
            elif choice == "4":
                await start_rx_session(rf_client, 779.00)
            elif choice == "5":
                await start_rx_session(rf_client, 868.35)
            elif choice == "6":
                custom_f = await loop.run_in_executor(
                    None, input, "Enter Frequency in MHz (e.g. 700.0): "
                )
                try:
                    freq_val = float(custom_f.strip())
                    await start_rx_session(rf_client, freq_val)
                except ValueError:
                    print("❌ Invalid frequency number.")
            elif choice == "7":
                await rf_client.send_cmd("FREQ 433.92")
                await rf_client.send_cmd("TX_PROTO CAME A")
            elif choice == "8":
                await rf_client.send_cmd("FREQ 433.92")
                await rf_client.send_cmd("TX_PROTO Princeton 123456")
            elif choice == "9":
                await rf_client.send_cmd("FREQ 700.00")
                await rf_client.send_cmd("TX_PROTO Princeton 123456")
            elif choice == "10":
                await rf_client.send_cmd("FREQ 779.00")
                await rf_client.send_cmd("TX_PROTO Princeton 123456")
            elif choice == "11":
                await rf_client.send_cmd("FREQ 868.35")
                await rf_client.send_cmd("TX_PROTO Princeton 123456")
            elif choice == "12":
                await rf_client.send_cmd("FREQ 868.35")
                await rf_client.send_cmd("TX_KEELOQ 123456789ABCDEF0")
            elif choice == "13":
                custom_f = await loop.run_in_executor(
                    None, input, "Frequency MHz [433.92]: "
                )
                freq_val = custom_f.strip() if custom_f.strip() else "433.92"
                proto = await loop.run_in_executor(
                    None, input, "Protocol Name (CAME/Princeton/NICE_FLO): "
                )
                key_hex = await loop.run_in_executor(
                    None, input, "Hex Key (e.g. A, 123456): "
                )
                await rf_client.send_cmd(f"FREQ {freq_val}")
                await rf_client.send_cmd(f"TX_PROTO {proto.strip()} {key_hex.strip()}")
            elif choice == "14":
                print(f"\n==================================================")
                print(f" 🎛️ FREQUENCY COUNTER / LISTENER ON 433.92 MHz")
                print(f" Press Enter or Ctrl+C to return to main menu.")
                print(f"==================================================")
                await rf_client.send_cmd("FREQ 433.92")
                await rf_client.send_cmd("LISTEN START")
                try:
                    await loop.run_in_executor(None, sys.stdin.readline)
                except (KeyboardInterrupt, asyncio.CancelledError):
                    pass
                print("\nStopping Listener...")
                await rf_client.send_cmd("STOP")
            elif choice == "15":
                await rf_client.send_cmd("STATUS")
            else:
                print("❌ Invalid selection. Please enter a number between 0 and 15.")
        except (KeyboardInterrupt, EOFError):
            break


async def main():
    parser = argparse.ArgumentParser(description="PocketRF Quick Test CLI Menu")
    parser.add_argument(
        "mode",
        nargs="?",
        default="menu",
        choices=["menu", "rx", "tx", "status"],
        help="Mode of operation (defaults to interactive menu)",
    )
    parser.add_argument(
        "--freq",
        type=float,
        default=433.92,
        help="RF Frequency in MHz",
    )
    parser.add_argument("--proto", type=str, help="Protocol name for TX")
    parser.add_argument("--key", type=str, help="Hex key for TX protocol")
    parser.add_argument("--keeloq", type=str, help="64-bit hex key for KeeLoq TX")

    args = parser.parse_args()

    rf_client = PocketRFClient()
    if not await rf_client.connect():
        sys.exit(1)

    try:
        if args.mode == "menu":
            await interactive_menu(rf_client)
        elif args.mode == "status":
            await rf_client.send_cmd("STATUS")
        elif args.mode == "rx":
            await start_rx_session(rf_client, args.freq)
        elif args.mode == "tx":
            await rf_client.send_cmd(f"FREQ {args.freq}")
            if args.keeloq:
                await rf_client.send_cmd(f"TX_KEELOQ {args.keeloq}")
            elif args.proto and args.key:
                await rf_client.send_cmd(f"TX_PROTO {args.proto} {args.key}")
            else:
                print("❌ Specify --proto and --key OR --keeloq for TX mode.")
    finally:
        await rf_client.disconnect()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nExiting...")
