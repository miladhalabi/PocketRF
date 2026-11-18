# 3D Case — Source Files

The PocketRF case, designed in **FreeCAD**. Two variants are included:

- **`ESP32_CC1101_Enclosure`** — the plain enclosure.
- **`MagSafe_Enclosure`** — the MagSafe/phone-mounted variant shown in the README hero and
  `docs/images/pocketrf-on-phone.jpg`.

The **FreeCAD source is the authoritative model**. The STLs are exports for printing; if you edit
the design, edit the `.FCStd` and re-export.

---

## What fits inside

Per the design:

- **Seeed Studio XIAO ESP32-C3**
- **Ebyte E07-M1101D-SMA** (a CC1101 module) — or a similar CC1101 breakout, if the mounting
  holes line up
- **40 × 20 × 8 mm battery**
- SMA antenna connector, USB-C, and a slide switch exposed through the case

Check the internal dimensions against your own parts before printing. The model assumes these
specific modules.

---

## Files

| Path | What it is |
| :--- | :--- |
| `freecad/ESP32_CC1101_Enclosure.FCStd` | Plain enclosure, FreeCAD source |
| `freecad/MagSafe_Enclosure.FCStd` | MagSafe/phone variant, FreeCAD source |
| `stl/enclosure_base.stl` | Plain base, ready to print |
| `stl/enclosure_lid.stl` | Plain lid |
| `stl/magsafe_base.stl` | MagSafe base |
| `stl/magsafe_lid.stl` | MagSafe lid |
| `stl/magsafe_lid_text_black.stl` | Lid text as a separate part (multi-material / two-colour print) |
| `MagSafe_Enclosure_drawing.pdf` | Dimensioned drawing |
| `fonts/` | Fonts used for the "PocketRF" text — FreeCAD needs the font file to render the text |
| `renders/` | Reference views: closed, open, bottom, exploded |

The `.FCStd` files reference the fonts in `fonts/`. Keep that folder next to the models, or FreeCAD
will fall back to a default font for the text.

---

## Printing

No print profile is committed — the geometry is the source of truth. A few practical notes:

- The **base** and **lid** are separate parts; print the ones you need.
- The **text lid** (`magsafe_lid_text_black.stl`) is a separate text part for a two-colour or
  multi-material print.
- **Test-fit before gluing or screwing.** Internal clearances depend on your printer's accuracy.
- Use the **SMA connector** and **USB-C** cut-outs as alignment checks after the first layer or two.
- **Do not** print the case in a way that blocks the antenna connector — the antenna must be
  attached before transmitting (see [LEGAL.md §6](../../LEGAL.md#6-hardware-damage-and-rf-exposure)).

---

## Licence

The case design is part of PocketRF and is released under the same licence as the project —
**AGPL-3.0** (see [`LICENSE`](../../LICENSE)). If you redistribute printed parts or the source
files, keep the attribution and licence.

The fonts are third-party: **Black Ops One** and **Chakra Petch** are Google Fonts, distributed
under the **SIL Open Font License 1.1**. Keep their licence terms if you redistribute them.

> "MagSafe" is a trademark of Apple Inc. The name is used descriptively to indicate the mounting
> style; this project is not affiliated with or endorsed by Apple.

---

## Before you print

Read [`LEGAL.md`](../../LEGAL.md) and [`docs/hardware.md`](../../docs/hardware.md). The enclosure
protects the board — it does not make the transmitter legal, and it does not protect the CC1101
from being powered without an antenna.
