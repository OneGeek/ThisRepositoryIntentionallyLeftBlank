#!/usr/bin/env python3
"""
Anti-aliasing-tolerant visual diff for TamaWatch renders.

Compares the JVM synthetic renders (app/build/screens/NN_name.png) against the
on-device captures pulled off the watch (tamawatch_NN_name.png), and flags only
*real* differences — not the sub-pixel/anti-aliasing noise you always get between
two rendering pipelines.

How the tolerance works (two knobs):
  --color N   a pixel must differ by more than N (0-255, max channel) to count.
              Absorbs gamma/blend and faint AA gradients. Default 32.
  --radius R  a pixel is a match if it matches ANY pixel within R px in the other
              image (a min-over-neighborhood, pixelmatch-style). Absorbs 1-2px
              edge shifts — exactly how AA and hinting differ. Default 1.
Only pixels that clear BOTH survive as a real diff.

Usage:
  # a whole set (dirs), matched by shot id (01_egg, 02_home, ...):
  python3 assets-src/visual_diff.py app/build/screens <pulled-device-dir> --out build/diff
  # a single pair:
  python3 assets-src/visual_diff.py a.png b.png --out diff.png

Each result is a triptych: synthetic | device | diff heatmap (real diffs in red).
Exit code is 0 when every pair is under --fail-pct, 1 otherwise (CI-friendly).
"""
import argparse
import os
import re
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont


def _load_rgb(path, size=None):
    im = Image.open(path).convert("RGB")
    if size is not None and im.size != size:
        im = im.resize(size, Image.LANCZOS)
    return np.asarray(im).astype(np.int16), im.size


def _shift(b, dy, dx):
    """b shifted by (dy, dx) with the overlapping region moved, edges left as-is."""
    h, w = b.shape[:2]
    out = b.copy()
    ys_src = slice(max(0, -dy), h - max(0, dy))
    ys_dst = slice(max(0, dy), h - max(0, -dy))
    xs_src = slice(max(0, -dx), w - max(0, dx))
    xs_dst = slice(max(0, dx), w - max(0, -dx))
    out[ys_dst, xs_dst] = b[ys_src, xs_src]
    return out


def _neighborhood_min_diff(a, b, radius):
    """Per-pixel best-match diff magnitude (max channel) within +/- radius px."""
    best = np.abs(a - b).max(axis=2)
    for dy in range(-radius, radius + 1):
        for dx in range(-radius, radius + 1):
            if dy == 0 and dx == 0:
                continue
            d = np.abs(a - _shift(b, dy, dx)).max(axis=2)
            best = np.minimum(best, d)
    return best


def _font(size):
    for name in ("DejaVuSans-Bold.ttf",
                 "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except Exception:
            continue
    return ImageFont.load_default()


def diff_pair(path_a, path_b, color, radius):
    """Return (triptych Image, pct_real_diff, max_diff)."""
    a, size = _load_rgb(path_a)
    b, _ = _load_rgb(path_b, size=size)          # device resized to synthetic if needed
    mag = _neighborhood_min_diff(a, b, radius)   # symmetric enough for our bordered shots
    mag = np.minimum(mag, _neighborhood_min_diff(b, a, radius))
    real = mag > color
    pct = 100.0 * real.sum() / real.size
    max_diff = int(mag.max())

    # Heatmap: dimmed grayscale of synthetic, real diffs painted hot by magnitude.
    gray = (a.mean(axis=2) * 0.35).astype(np.uint8)
    heat = np.stack([gray, gray, gray], axis=2)
    inten = np.clip((mag.astype(np.float32) - color) / max(1, 255 - color), 0, 1)
    heat[real, 0] = (120 + 135 * inten[real]).astype(np.uint8)
    heat[real, 1] = (40 * (1 - inten[real])).astype(np.uint8)
    heat[real, 2] = (40 * (1 - inten[real])).astype(np.uint8)

    w, h = size
    pad, labelh = 12, 26
    sheet = Image.new("RGB", (w * 3 + pad * 4, h + labelh + pad * 2), (12, 14, 22))
    d = ImageDraw.Draw(sheet)
    f = _font(18)
    for i, (img, label) in enumerate((
        (Image.fromarray(a.astype(np.uint8)), "synthetic"),
        (Image.fromarray(b.astype(np.uint8)), "device"),
        (Image.fromarray(heat), f"diff {pct:.2f}%"),
    )):
        x = pad + i * (w + pad)
        sheet.paste(img, (x, pad))
        d.text((x + 4, h + pad + 4), label, font=f, fill=(240, 240, 245))
    return sheet, pct, max_diff


def _key(name):
    """Normalize a filename to a shot key: tamawatch_02_home.png -> 02_home."""
    stem = os.path.splitext(os.path.basename(name))[0]
    return re.sub(r"^tamawatch_", "", stem)


def _collect(path):
    if os.path.isdir(path):
        out = {}
        for n in os.listdir(path):
            if n.lower().endswith(".png"):
                out[_key(n)] = os.path.join(path, n)
        return out
    return {_key(path): path}


def main():
    ap = argparse.ArgumentParser(description="AA-tolerant visual diff.")
    ap.add_argument("a", help="synthetic image or directory (app/build/screens)")
    ap.add_argument("b", help="device image or directory (pulled tamawatch_*.png)")
    ap.add_argument("--out", default="build/diff", help="output file (single) or dir")
    ap.add_argument("--color", type=int, default=32, help="per-channel color threshold 0-255")
    ap.add_argument("--radius", type=int, default=1, help="neighborhood radius (px) for AA/shift")
    ap.add_argument("--fail-pct", type=float, default=1.0, help="fail if any pair exceeds this %% diff")
    args = ap.parse_args()

    single = os.path.isfile(args.a) and os.path.isfile(args.b)
    if single:
        pairs = [(_key(args.a), args.a, args.b)]
    else:
        amap, bmap = _collect(args.a), _collect(args.b)
        keys = sorted(k for k in amap if k in bmap)
        skipped = sorted(set(amap) ^ set(bmap))
        if not keys:
            sys.exit(f"no matching image keys between {args.a} and {args.b}\n"
                     f"  synthetic: {sorted(amap)}\n  device: {sorted(bmap)}")
        pairs = [(k, amap[k], bmap[k]) for k in keys]
        os.makedirs(args.out, exist_ok=True)

    worst = 0.0
    print(f"AA-tolerant diff · color>{args.color} · radius={args.radius}px")
    for k, pa, pb in pairs:
        sheet, pct, mx = diff_pair(pa, pb, args.color, args.radius)
        out = args.out if single else os.path.join(args.out, f"diff_{k}.png")
        sheet.save(out)
        worst = max(worst, pct)
        flag = "  OK" if pct <= args.fail_pct else "  DIFF"
        print(f"  {k:<12} {pct:6.2f}% real diff  (max {mx:3d})  -> {out}{flag}")
    if not single and skipped:
        print(f"  (unmatched, skipped: {', '.join(skipped)})")
    print(f"worst: {worst:.2f}%  (threshold {args.fail_pct:.2f}%)")
    sys.exit(0 if worst <= args.fail_pct else 1)


if __name__ == "__main__":
    main()
