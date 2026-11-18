# Legal & Responsible Use

PocketRF is a **Sub-GHz radio transceiver**. The hardware you build with it is a radio
transmitter, and radio transmission is regulated everywhere. This document is a plain-English
summary of what that means in practice. **It is not legal advice.** Rules differ by country and
change over time — verify your local regulations before transmitting.

If you are not sure whether something is permitted where you live, assume it is not.

---

## Summary

| Activity | Status |
| :--- | :--- |
| Building the hardware and reading this repo | Fine |
| Receiving / capturing Sub-GHz signals **you own** | Legal in most jurisdictions, see [§4](#4-receiving-and-capturing) |
| Replaying a signal from a remote **you own** | Generally fine, subject to [§3](#3-transmitting) |
| Decoding and archiving your own remotes | Fine |
| **Jamming** (any mode, any duration) | **Illegal in the US, UK, EU, and most other countries** |
| Replaying or bruteforcing **someone else's** device | Likely illegal — see [§5](#5-rolling-codes-and-third-party-devices) |
| Transmitting at all without an antenna attached | Damages your hardware — see [§6](#6-hardware-damage-and-rf-exposure) |

---

## 1. Jamming

PocketRF ships a jammer (`CMD_START_JAM`, and the Jammer screen in the Android app).
**Intentional radio interference is a criminal offence in effectively every jurisdiction.** The
30-second automatic cutoff is a safety limit, not a legal one.

Examples, not an exhaustive list:

- **United States** — 47 U.S.C. § 333 (wilful or malicious interference) and 47 U.S.C. § 301
  (unlicensed transmission). The FCC also treats the **marketing, manufacture, import, or sale**
  of jamming devices as illegal, independently of use.
- **United Kingdom** — Wireless Telegraphy Act 2006, § 68 (deliberate interference).
- **European Union** — Radio Equipment Directive 2014/53/EU, plus national criminal law in
  most member states.

Operating the jammer can interfere with emergency services, aviation, and mobile networks. Penalties
include substantial fines, equipment seizure, and imprisonment.

**Building the hardware does not make it legal to use. There is no "just testing" exemption.**

## 2. Duty cycle and band rules

Even when transmitting is legal, it is usually *constrained*. The CC1101 bands PocketRF uses are
shared with alarms, sensors, garage doors, and utility meters.

- **EU/UK (ERC/REC 70-03)** — the common 868 MHz allocations are limited to **1%** duty cycle
  (and 0.1% for some sub-bands). Continuously replaying a signal can breach this in minutes.
- **US (FCC Part 15)** — 315 MHz and 433.92 MHz are unlicensed only within strict field-strength,
  bandwidth, and duty-cycle limits. Part 15 devices must accept interference and may not cause it.
- **Licensed bands** — PocketRF can tune outside the ISM bands. Doing so is transmitting without a
  licence unless you hold one, and a ham licence does **not** authorise unlicensed commercial gear
  or encrypted/obfuscated content.

## 3. Transmitting

Transmitting a signal is only appropriate when **you own or are authorised to operate** the device
being emulated, or when you are testing equipment you control in a shielded or lawfully licensed
environment.

You are responsible for every transmission your hardware makes, including one triggered by a client
app, a script, or an automated scan.

## 4. Receiving and capturing

Receiving is generally less restricted than transmitting, but it is not unregulated:

- **Interception and privacy laws** — many countries restrict capturing communications not intended
  for you (e.g. the UK's Investigatory Powers / interception provisions, various EU privacy statutes,
  US wiretap law at the state and federal level).
- **Storing what you capture** — a signal library containing neighbours' remotes is a liability.
  Log only your own devices; delete captures you no longer need.

## 5. Rolling codes and third-party devices

PocketRF includes a KeeLoq engine and a bruteforce key generator. These exist for analysis of
**your own** hardware. Using them against devices you do not own may constitute unauthorised access
or theft of service:

- **US** — Computer Fraud and Abuse Act (18 U.S.C. § 1030) and state computer-crime statutes.
- **UK** — Computer Misuse Act 1990.
- **EU** — national equivalents plus Directive 2013/40/EU.

Capturing a rolling code also does not let you predict future codes; if a device's security model
assumes you cannot do this, breaking it is the definition of an attack, not a feature.

## 6. Hardware damage and RF exposure

- **Never transmit without an antenna connected.** A CC1101 transmitting into an open or mismatched
  load can be permanently damaged.
- **The CC1101 is a 3.3 V part.** Do not power it from 5 V. Levels above 3.3 V on its I/O will
  destroy it.
- **RF exposure** — keep antennas away from your body during sustained transmission and follow your
  country's exposure guidelines. This matters most for the jammer's full-power continuous modes.
- **Power** — the PA can draw peaks the XIAO's regulator may not enjoy. Use a stable supply; see
  [`docs/hardware.md`](docs/hardware.md).

## 7. No warranty, no indemnity

This project is provided under the GNU Affero General Public License v3.0 (see [`LICENSE`](LICENSE)),
which includes an explicit disclaimer of warranty. The authors and contributors accept no liability
for how you use this hardware, software, or documentation, nor for any regulatory, civil, or criminal
consequences arising from that use.

## 8. If you think something here is wrong

Open an issue. Corrections to this document are welcome — especially jurisdiction-specific detail
from people who know their local law.
