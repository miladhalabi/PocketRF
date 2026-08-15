#!/usr/bin/env python3
"""
PocketRF BLE Integration Test Script (Multi-Band Sub-GHz Test)
Connects to 'XIAO-RF-BLE' and tests:
- STATUS command
- Multi-band frequency tuning (433.92 MHz, 779.00 MHz, 868.35 MHz)
- Protocol transmission at 779 MHz / 868 MHz band
- KeeLoq transmission
- RMT RX START & STOP
"""

import asyncio
import json
import sys
from bleak import BleakScanner, BleakClient

SERVICE_UUID = "0000cc11-0000-1000-8000-00805f9b34fb"
RX_CHAR_UUID = "0000cc12-0000-1000-8000-00805f9b34fb"
TX_CHAR_UUID = "0000cc13-0000-1000-8000-00805f9b34fb"
TARGET_NAME = "XIAO-RF-BLE"


def notification_handler(sender, data: bytearray):
    payload = data.decode("utf-8", errors="replace")
    print(f"\n[BLE NOTIFY] Received ({len(data)} bytes): {payload}")
    try:
        parsed = json.loads(payload)
        print(f"            Parsed JSON: {parsed}")
    except json.JSONDecodeError:
        pass


async def main():
    print("==================================================")
    print(" PocketRF Multi-Band (433 / 779 / 868 MHz) Test ")
    print(" Target Device: " + TARGET_NAME)
    print("==================================================")
    print("[1] Scanning for BLE device...")

    def match_target(device, advertisement_data):
        if device.name == TARGET_NAME:
            return True
        if advertisement_data.local_name == TARGET_NAME:
            return True
        if SERVICE_UUID in advertisement_data.service_uuids:
            return True
        return False

    device = await BleakScanner.find_device_by_filter(match_target, timeout=10.0)
    if not device:
        print(f"❌ Device '{TARGET_NAME}' not found during scan!")
        print("   Make sure the Seeed XIAO ESP32C3 is powered on and advertising.")
        sys.exit(1)

    print(f"✓ Found '{TARGET_NAME}' [Address: {device.address}]")
    print("\n[2] Connecting to BLE server...")

    async with BleakClient(device) as client:
        if not client.is_connected:
            print("❌ Failed to connect to device.")
            sys.exit(1)

        print(f"✓ Connected to {device.address}!")
        mtu = getattr(client, "mtu_size", "default")
        print(f"   Negotiated MTU Size: {mtu}")

        print("\n[3] Subscribing to TX Notifications...")
        await client.start_notify(TX_CHAR_UUID, notification_handler)
        await asyncio.sleep(1.0)

        # Test sequence helper
        async def send_cmd(cmd_text: str, wait_sec: float = 2.0):
            print(f"\n---> Sending Command: '{cmd_text}'")
            await client.write_gatt_char(
                RX_CHAR_UUID, cmd_text.encode("utf-8"), response=True
            )
            await asyncio.sleep(wait_sec)

        # Execute test sequence
        await send_cmd("STATUS")

        print("\n[4] Testing Multi-Band Tuning & Protocol Transmission...")
        # Test 433.92 MHz
        await send_cmd("FREQ 433.92")
        await send_cmd("TX_PROTO CAME A")

        # Test 779.00 MHz Band (CC1101 779-928 MHz band)
        await send_cmd("FREQ 779.00")
        await send_cmd("TX_PROTO Princeton 123456")

        # Test 868.35 MHz Band
        await send_cmd("FREQ 868.35")
        await send_cmd("TX_KEELOQ 123456789ABCDEF0")

        # Restore default frequency 433.92 MHz
        await send_cmd("FREQ 433.92")

        print("\n[5] Testing Background Receiver & Decoder Engine...")
        await send_cmd("RMT RX START")
        print("    Listening for sub-GHz remote signals for 5 seconds...")
        await asyncio.sleep(5.0)
        await send_cmd("RMT RX STOP")
        await send_cmd("STATUS")

        print("\n[6] Stopping notifications and disconnecting...")
        await client.stop_notify(TX_CHAR_UUID)
        print("✓ Multi-Band BLE Test Script Executed Successfully!")


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nTest interrupted by user.")
    except Exception as e:
        print(f"\n❌ Error during test execution: {e}")
