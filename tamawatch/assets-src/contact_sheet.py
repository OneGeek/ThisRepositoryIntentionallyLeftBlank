#!/usr/bin/env python3
"""Composite the real Roborazzi screen renders (app/build/screens/NN_name.png)
into one labeled contact sheet. These are the ACTUAL Compose UI rendered on the
JVM — not a hand-drawn approximation — so the sheet tracks the shipping app.

Usage: python3 assets-src/contact_sheet.py [out.png]
"""
import os
import re
import sys
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SRC = os.path.join(ROOT, "app", "build", "screens")
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "app", "build", "outputs", "screenshots.png")

BG = (12, 14, 22, 255)
FG = (240, 240, 245, 255)
COLS = 3
PAD = 28
GAP = 26
LABEL_H = 34
TITLE_H = 64
CELL = 300  # each watch render is drawn at this px size


def _font(size, bold=True):
    for name in (
        "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ):
        try:
            return ImageFont.truetype(name, size)
        except Exception:
            continue
    return ImageFont.load_default()


def _label(fname):
    # "02_home.png" -> "Home"
    stem = re.sub(r"^\d+_", "", os.path.splitext(fname)[0])
    return stem.replace("_", " ").title()


def main():
    if not os.path.isdir(SRC):
        sys.exit(f"no renders at {SRC} — run ./gradlew :app:recordRoborazziDebug first")
    files = sorted(f for f in os.listdir(SRC) if re.match(r"^\d+_.*\.png$", f))
    if not files:
        sys.exit(f"no NN_name.png renders in {SRC}")

    rows = (len(files) + COLS - 1) // COLS
    cols = min(COLS, len(files))
    W = PAD * 2 + cols * CELL + (cols - 1) * GAP
    H = PAD + TITLE_H + rows * (CELL + LABEL_H) + (rows - 1) * GAP + PAD

    sheet = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(sheet)
    tf = _font(38)
    d.text((PAD, PAD + 6), "TamaWatch — build preview (real render)", font=tf, fill=FG)

    lf = _font(24)
    for i, fname in enumerate(files):
        r, c = divmod(i, cols)
        x = PAD + c * (CELL + GAP)
        y = PAD + TITLE_H + r * (CELL + LABEL_H + GAP)
        img = Image.open(os.path.join(SRC, fname)).convert("RGBA").resize((CELL, CELL), Image.LANCZOS)
        sheet.alpha_composite(img, (x, y))
        label = _label(fname)
        bb = d.textbbox((0, 0), label, font=lf)
        d.text((x + (CELL - (bb[2] - bb[0])) / 2, y + CELL + 6), label, font=lf, fill=FG)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    sheet.convert("RGB").save(OUT)
    print(f"Contact sheet -> {OUT}  ({W}x{H}, {len(files)} real renders)")


if __name__ == "__main__":
    main()
