"""TamaWatch parts generator — the runtime rig layer.

Splits each creature's NEUTRAL pose into separately-composited part images
(shadow, feature, body, arms, face + a blink face) and records their attach
anchors, so the runtime can deform the body while merely *shifting* the face,
arms and feature by the stretch amount, and slide the shadow horizontally only.

This is additive: it reuses generate_art's primitives and neutral geometry (so
the parts composite to the same rest pose as the baked idle frame), writes the
new PNGs into res/drawable-nodpi, and merges a `parts` section into the existing
assets_manifest.json WITHOUT touching any existing sprite.

Usage: python3 generate_parts.py [app_main_dir]   (default ../app/src/main)
"""

import os
import sys
import json
import generate_art as ga
import palette as P

HERE = os.path.dirname(os.path.abspath(__file__))
MAIN = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "app", "src", "main")
ART_DIR = os.path.join(MAIN, "res", "drawable-nodpi")
MANIFEST_PATH = os.path.join(MAIN, "assets", "tamawatch", "assets_manifest.json")


def _geom(size, sp, pad):
    """Neutral-pose geometry — copied verbatim from draw_creature's rest values
    (bob=0, lean=0, sqx=sqy=1, arms side, eyes open, mouth smile). Kept in lockstep
    with draw_creature so a composited part stack equals the baked idle frame."""
    cx = size // 2
    baseline = int(pad + size * 0.82)
    sx, sy = sp["shape"]
    rx = max(4, int(int(size * sx * 0.5)))
    ry = max(4, int(int(size * sy * 0.5)))
    cy = int(baseline - ry)
    ucx = cx
    return dict(cx=cx, baseline=baseline, rx=rx, ry=ry, cy=cy, ucx=ucx,
                foot_dx=int(rx * 0.55), arm_y=cy + int(ry * 0.15),
                ey=cy - int(ry * 0.15), ex=int(rx * 0.42), my=cy + int(ry * 0.15))


def _shadow(size, sp, pad, g):
    img, d = ga.cell(size, size + pad)
    sh_rx = max(4, int(g["rx"] * 0.92))
    sh_ry = max(2, g["rx"] // 5)
    d.ellipse([g["cx"] - sh_rx, g["baseline"] + 3 - sh_ry, g["cx"] + sh_rx, g["baseline"] + 3 + sh_ry],
              fill=(0, 0, 0, 70), outline=None)
    return img


def _feature(size, sp, pad, g):
    img, d = ga.cell(size, size + pad)
    ga.draw_feature(d, g["ucx"], g["cy"] - g["ry"], g["rx"], sp["feat"], sp.get("accent", P.INK), 0)
    return img


def _body(size, sp, pad, g):
    """The deformable mass: feet + body + belly (feet ride the body's stretch)."""
    img, d = ga.cell(size, size + pad)
    body = sp["body"]
    ga.ellipse(d, g["cx"] - g["foot_dx"], g["baseline"], 5, 4, P.darker(body, 0.8))
    ga.ellipse(d, g["cx"] + g["foot_dx"], g["baseline"], 5, 4, P.darker(body, 0.8))
    ga.ellipse(d, g["ucx"], g["cy"], g["rx"], g["ry"], body, P.INK, ow=2)
    ga.ellipse(d, g["ucx"], g["cy"] + int(g["ry"] * 0.25), int(g["rx"] * 0.6), int(g["ry"] * 0.5),
               P.lighter(body, 0.45), outline=None)
    return img


def _arms(size, sp, pad, g):
    img, d = ga.cell(size, size + pad)
    ga.dot(d, g["ucx"] - g["rx"], g["arm_y"], 3, P.darker(sp["body"], 0.85))
    ga.dot(d, g["ucx"] + g["rx"], g["arm_y"], 3, P.darker(sp["body"], 0.85))
    return img


def _face(size, sp, pad, g, eyes="open"):
    img, d = ga.cell(size, size + pad)
    ga._draw_eyes(d, g["ucx"], g["ey"], g["ex"], size, eyes)
    if sp.get("cheeks"):
        ga.dot(d, g["ucx"] - int(g["rx"] * 0.7), g["ey"] + 4, 2, P.PINK_L)
        ga.dot(d, g["ucx"] + int(g["rx"] * 0.7), g["ey"] + 4, 2, P.PINK_L)
    my = g["my"]
    if sp.get("grumpy"):
        d.line([g["ucx"] - 4, my + 2, g["ucx"] + 4, my + 2], fill=P.INK, width=2)
    else:
        d.arc([g["ucx"] - 4, my - 2, g["ucx"] + 4, my + 4], 20, 160, fill=P.INK, width=2)
    return img


def _anchors(size, sp, pad, g):
    """Attach points, normalized to the cell (w=size, h=size+pad)."""
    w = float(size)
    h = float(size + pad)
    return {
        "feet":    [g["cx"] / w, g["baseline"] / h],           # stretch pivot
        "face":    [g["ucx"] / w, g["ey"] / h],
        "feature": [g["ucx"] / w, (g["cy"] - g["ry"]) / h],
        "arms":    [g["ucx"] / w, g["arm_y"] / h],
        "headTop": [g["ucx"] / w, (g["cy"] - g["ry"] - g["rx"] * 0.5) / h],
        "handL":   [(g["ucx"] - g["rx"]) / w, g["arm_y"] / h],
        "handR":   [(g["ucx"] + g["rx"]) / w, g["arm_y"] / h],
    }


def generate():
    parts = {}
    for key, sp in ga.SPECIES.items():
        stage = key.split("_")[0]
        size = ga.SIZES[stage]
        pad = size // 2
        g = _geom(size, sp, pad)
        base = "spr_" + key
        pieces = {
            "shadow": (base + "_shadow", _shadow(size, sp, pad, g)),
            "feature": (base + "_feature", _feature(size, sp, pad, g)),
            "body": (base + "_body", _body(size, sp, pad, g)),
            "arms": (base + "_arms", _arms(size, sp, pad, g)),
            "face": (base + "_face", _face(size, sp, pad, g, "open")),
            "faceBlink": (base + "_face_blink", _face(size, sp, pad, g, "closed")),
        }
        role_ids = {}
        for role, (pid, img) in pieces.items():
            ga.strip([img], {"idle": [0]}, pid, ART_DIR)   # downsample + write + record in ga.MANIFEST
            role_ids[role] = pid
        role_ids["anchors"] = _anchors(size, sp, pad, g)
        parts[base] = role_ids
    return parts


def main():
    parts = generate()
    with open(MANIFEST_PATH) as f:
        man = json.load(f)
    # merge the new part sprites (geometry/frames) into `sprites`
    for pid, meta in ga.MANIFEST.items():
        man["sprites"][pid] = meta
    man["parts"] = parts
    with open(MANIFEST_PATH, "w") as f:
        json.dump(man, f, indent=1)
    print("parts: %d creatures, %d images -> %s" %
          (len(parts), len(ga.MANIFEST), os.path.relpath(MANIFEST_PATH, HERE)))


if __name__ == "__main__":
    main()
