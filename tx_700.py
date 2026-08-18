#!/usr/bin/env python3
import subprocess, sys

cmd = [
    "python3",
    "/home/admin/Lab/firmware/headless_cc1101_ble/pocketrf_cli.py",
    "--freq",
    "700.00",
    "tx",
    "--proto",
    "Princeton",
    "--key",
    "123456",
]
sys.exit(subprocess.call(cmd))
