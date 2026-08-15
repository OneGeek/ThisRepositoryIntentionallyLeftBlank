"""TamaWatch art generator. Emits palette-limited RGBA PNG frame-strips for
every art ID in the Authoritative Generation Set (docs/04) and returns a
manifest fragment {id: {file, frameW, frameH, frames, tags}}.

Pixel art is authored at the listed cell sizes and shipped un-dpi-scaled
(drawable-nodpi); the app scales it nearest-neighbor for the chunky look."""

import os
from PIL import Image, ImageDraw
import palette as P

MANIFEST = {}


# ----------------------------------------------------------------------------- helpers
def cell(w, h):
    img = Image.new("RGBA", (w, h), P.TRANSPARENT)
    return img, ImageDraw.Draw(img)


def strip(frames, tags, id_, out_dir):
    """Lay frames horizontally into one PNG strip and record the manifest."""
    fw, fh = frames[0].size
    sheet = Image.new("RGBA", (fw * len(frames), fh), P.TRANSPARENT)
    for i, f in enumerate(frames):
        sheet.paste(f, (i * fw, 0))
    path = os.path.join(out_dir, id_ + ".png")
    sheet.save(path)
    MANIFEST[id_] = {"file": id_ + ".png", "frameW": fw, "frameH": fh,
                     "frames": len(frames), "tags": tags}
    return sheet


def ellipse(d, cx, cy, rx, ry, fill, outline=P.INK, ow=1):
    d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline, width=ow)


def dot(d, x, y, r, fill):
    d.ellipse([x - r, y - r, x + r, y + r], fill=fill)


# ----------------------------------------------------------------------------- creature
# Each species = dict of body color/accent/feature/shape. Poses are variations.
SPECIES = {
    # baby (universal)
    "baby":    dict(body=P.CREAM, accent=P.PINK_D, feat="antenna",  shape=(0.62, 0.66), cheeks=True),
    # children (care-branch)
    "child_a": dict(body=P.GREEN,  accent=P.LCD_D,  feat="ears_round", shape=(0.60, 0.60), cheeks=True),
    "child_b": dict(body=P.BLUE_L, accent=P.BLUE_D, feat="tuft",       shape=(0.60, 0.58)),
    "child_c": dict(body=P.GRAY_L, accent=P.GRAY_D, feat="spikes",     shape=(0.58, 0.56), grumpy=True),
    # teens
    "teen_a":  dict(body=P.YELLOW, accent=P.INK,    feat="ears_cat",  shape=(0.58, 0.62)),
    "teen_b":  dict(body=P.PINK_L, accent=P.PINK_D, feat="bow",       shape=(0.60, 0.60), cheeks=True),
    "teen_c":  dict(body=P.GREEN,  accent=P.LCD_D,  feat="horn",      shape=(0.56, 0.64)),
    "teen_d":  dict(body=P.PURPLE, accent=P.INK,    feat="spikes",    shape=(0.58, 0.60), grumpy=True),
    "teen_e":  dict(body=P.BLUE_D, accent=P.WHITE,  feat="wings",     shape=(0.56, 0.60)),
    # adults
    "adult_a": dict(body=P.YELLOW, accent=P.WHITE,  feat="halo",      shape=(0.60, 0.64), cheeks=True),
    "adult_b": dict(body=P.GREEN,  accent=P.LCD_D,  feat="ears_cat",  shape=(0.62, 0.62)),
    "adult_c": dict(body=P.PINK_L, accent=P.PINK_D, feat="wings",     shape=(0.60, 0.62), cheeks=True),
    "adult_d": dict(body=P.BLUE_L, accent=P.BLUE_D, feat="horn",      shape=(0.60, 0.64)),
    "adult_e": dict(body=P.SKIN,   accent=P.RED,    feat="ears_round", shape=(0.62, 0.60)),
    "adult_f": dict(body=P.PURPLE, accent=P.WHITE,  feat="spikes",    shape=(0.60, 0.62)),
    "adult_g": dict(body=P.RED,    accent=P.INK,    feat="wings",     shape=(0.58, 0.64), grumpy=True),
    "adult_h": dict(body=P.GRAY_D, accent=P.GRAY_L, feat="tuft",      shape=(0.58, 0.58), grumpy=True),
}


def draw_feature(d, cx, top, rx, feat, accent):
    if feat == "antenna":
        d.line([cx, top, cx, top - rx * 0.5], fill=P.INK, width=2)
        dot(d, cx, int(top - rx * 0.5), 3, accent)
    elif feat == "ears_round":
        dot(d, int(cx - rx * 0.7), top, 5, accent)
        dot(d, int(cx + rx * 0.7), top, 5, accent)
    elif feat == "ears_cat":
        d.polygon([(cx - rx, top), (cx - rx * 0.4, top - rx * 0.6), (cx - rx * 0.2, top)], fill=accent, outline=P.INK)
        d.polygon([(cx + rx, top), (cx + rx * 0.4, top - rx * 0.6), (cx + rx * 0.2, top)], fill=accent, outline=P.INK)
    elif feat == "tuft":
        for dx in (-4, 0, 4):
            d.line([cx + dx, top, cx + dx, top - 6], fill=accent, width=2)
    elif feat == "spikes":
        for dx in (-rx * 0.6, 0, rx * 0.6):
            d.polygon([(cx + dx - 3, top), (cx + dx, top - 7), (cx + dx + 3, top)], fill=accent, outline=P.INK)
    elif feat == "horn":
        d.polygon([(cx - 3, top), (cx, int(top - rx * 0.7)), (cx + 3, top)], fill=accent, outline=P.INK)
    elif feat == "wings":
        d.polygon([(cx - rx, top + rx * 0.3), (cx - rx * 1.5, top - 2), (cx - rx * 0.8, top + rx * 0.6)], fill=accent, outline=P.INK)
        d.polygon([(cx + rx, top + rx * 0.3), (cx + rx * 1.5, top - 2), (cx + rx * 0.8, top + rx * 0.6)], fill=accent, outline=P.INK)
    elif feat == "bow":
        d.polygon([(cx - 7, top + 2), (cx, top + 5), (cx - 7, top + 8)], fill=accent, outline=P.INK)
        d.polygon([(cx + 7, top + 2), (cx, top + 5), (cx + 7, top + 8)], fill=accent, outline=P.INK)
    elif feat == "halo":
        d.ellipse([cx - rx * 0.5, top - 8, cx + rx * 0.5, top - 3], outline=P.YELLOW, width=2)


def draw_creature(size, sp, pose):
    """size = cell px; sp = species dict; pose = str."""
    img, d = cell(size, size)
    cx = size // 2
    baseline = int(size * 0.82)
    sx, sy = sp["shape"]
    rx, ry = int(size * sx * 0.5), int(size * sy * 0.5)
    bob = 0
    if pose in ("idle1", "happy0", "call"):
        bob = -1
    if pose == "happy1":
        bob = -3
    cy = baseline - ry + bob

    body = sp["body"]
    outline = P.INK
    # feet
    foot_dx = int(rx * 0.55)
    fy = baseline
    lift = 2 if pose == "walk1" else 0
    ellipse(d, cx - foot_dx, fy - lift, 5, 4, P.darker(body, 0.8))
    ellipse(d, cx + foot_dx, fy - (2 - lift), 5, 4, P.darker(body, 0.8))

    # feature behind/top
    draw_feature(d, cx, cy - ry, rx, sp["feat"], sp.get("accent", P.INK))

    # body
    col = body
    if pose == "sick":
        col = P.lighter(P.GRAY_L, 0.2)
    ellipse(d, cx, cy, rx, ry, col, outline, ow=2)
    # belly
    ellipse(d, cx, cy + int(ry * 0.25), int(rx * 0.6), int(ry * 0.5), P.lighter(col, 0.45), outline=None)

    # arms
    arm_y = cy + int(ry * 0.15)
    if pose in ("happy0", "happy1"):
        d.line([cx - rx, arm_y, cx - rx - 4, arm_y - 6], fill=outline, width=3)
        d.line([cx + rx, arm_y, cx + rx + 4, arm_y - 6], fill=outline, width=3)
    else:
        dot(d, cx - rx, arm_y, 3, P.darker(col, 0.85))
        dot(d, cx + rx, arm_y, 3, P.darker(col, 0.85))

    # eyes
    ey = cy - int(ry * 0.15)
    ex = int(rx * 0.42)
    if pose in ("sleep0", "sleep1", "idle1") and pose != "idle1":
        pass
    if pose in ("sleep0", "sleep1"):
        d.line([cx - ex - 3, ey, cx - ex + 3, ey], fill=P.INK, width=2)
        d.line([cx + ex - 3, ey, cx + ex + 3, ey], fill=P.INK, width=2)
    elif pose == "happy1" or pose == "happy0":
        d.arc([cx - ex - 4, ey - 4, cx - ex + 4, ey + 4], 200, 340, fill=P.INK, width=2)
        d.arc([cx + ex - 4, ey - 4, cx + ex + 4, ey + 4], 200, 340, fill=P.INK, width=2)
    elif pose == "sick":
        d.line([cx - ex - 3, ey - 3, cx - ex + 3, ey + 3], fill=P.INK, width=2)
        d.line([cx - ex - 3, ey + 3, cx - ex + 3, ey - 3], fill=P.INK, width=2)
        d.line([cx + ex - 3, ey - 3, cx + ex + 3, ey + 3], fill=P.INK, width=2)
        d.line([cx + ex - 3, ey + 3, cx + ex + 3, ey - 3], fill=P.INK, width=2)
    else:
        r = 3 if size >= 56 else 2
        dot(d, cx - ex, ey, r, P.INK)
        dot(d, cx + ex, ey, r, P.INK)
        dot(d, cx - ex + 1, ey - 1, 1, P.WHITE)
        dot(d, cx + ex + 1, ey - 1, 1, P.WHITE)

    # cheeks
    if sp.get("cheeks") and pose not in ("sick", "sad"):
        dot(d, cx - int(rx * 0.7), ey + 4, 2, P.PINK_L)
        dot(d, cx + int(rx * 0.7), ey + 4, 2, P.PINK_L)

    # mouth
    my = cy + int(ry * 0.15)
    if pose in ("happy0", "happy1", "eat1"):
        d.chord([cx - 5, my - 3, cx + 5, my + 6], 0, 180, fill=P.RED, outline=P.INK)
    elif pose == "eat0":
        ellipse(d, cx, my + 1, 3, 4, P.RED, P.INK)
    elif pose in ("sad", "sick"):
        d.arc([cx - 5, my + 1, cx + 5, my + 7], 20, 160, fill=P.INK, width=2)
    elif sp.get("grumpy"):
        d.line([cx - 4, my + 2, cx + 4, my + 2], fill=P.INK, width=2)
    else:
        d.arc([cx - 4, my - 2, cx + 4, my + 4], 20, 160, fill=P.INK, width=2)

    return img


# per-species active sheet: idle,idle,walk,walk,happy,happy
SPECIES_POSES = ["idle0", "idle1", "walk0", "walk1", "happy0", "happy1"]
SPECIES_TAGS = {"idle": [0, 1], "walk": [2, 3], "happy": [4, 5]}
# baby gets the full set
BABY_POSES = ["idle0", "idle1", "walk0", "walk1", "happy0", "happy1",
              "sad", "sick", "sleep0", "sleep1", "eat0", "eat1", "call"]
BABY_TAGS = {"idle": [0, 1], "walk": [2, 3], "happy": [4, 5], "sad": [6],
             "sick": [7], "sleep": [8, 9], "eat": [10, 11], "call": [12]}
# shared stageset per stage silhouette: sad,sick,sleep,sleep,eat,eat,call
STAGESET_POSES = ["sad", "sick", "sleep0", "sleep1", "eat0", "eat1", "call"]
STAGESET_TAGS = {"sad": [0], "sick": [1], "sleep": [2, 3], "eat": [4, 5], "call": [6]}

SIZES = {"baby": 48, "child": 56, "teen": 64, "adult": 64}


def gen_characters(out):
    # baby (full set)
    frames = [draw_creature(SIZES["baby"], SPECIES["baby"], p) for p in BABY_POSES]
    strip(frames, BABY_TAGS, "spr_baby", out)
    # children/teens/adults active sheets
    for key, sp in SPECIES.items():
        if key == "baby":
            continue
        stage = key.split("_")[0]
        size = SIZES[stage]
        frames = [draw_creature(size, sp, p) for p in SPECIES_POSES]
        strip(frames, SPECIES_TAGS, "spr_" + key, out)
    # stagesets (use a neutral silhouette per stage: child_a/teen_a/adult_a body but gray)
    for stage in ("child", "teen", "adult"):
        neutral = dict(SPECIES[stage + "_a"]); neutral["body"] = P.GRAY_L; neutral["accent"] = P.GRAY_D
        frames = [draw_creature(SIZES[stage], neutral, p) for p in STAGESET_POSES]
        strip(frames, STAGESET_TAGS, "spr_stageset_" + stage, out)


def gen_egg(out):
    size = 64
    frames = []
    tags = {"idle": [0, 1], "wiggle": [2, 3], "crack": [4, 5, 6]}
    for pose in ("idle0", "idle1", "wig0", "wig1", "crack0", "crack1", "crack2"):
        img, d = cell(size, size)
        cx, cy = size // 2, int(size * 0.58)
        tilt = -3 if pose == "wig0" else (3 if pose == "wig1" else 0)
        ellipse(d, cx + tilt, cy, 20, 24, P.CREAM, P.INK, 2)
        # spots
        for (dx, dy) in [(-6, -4), (5, 2), (-3, 8), (7, -6)]:
            dot(d, cx + tilt + dx, cy + dy, 3, P.LCD_L)
        if pose.startswith("crack"):
            n = int(pose[-1])
            d.line([cx - 8, cy - 6, cx - 2, cy], fill=P.INK, width=2)
            if n >= 1:
                d.line([cx - 2, cy, cx + 4, cy - 6], fill=P.INK, width=2)
            if n >= 2:
                d.line([cx + 4, cy - 6, cx + 10, cy], fill=P.INK, width=2)
                d.line([cx - 12, cy + 4, cx + 12, cy + 4], fill=P.INK, width=1)
        frames.append(img)
    strip(frames, tags, "spr_egg", out)


# ----------------------------------------------------------------------------- overlays
def gen_overlays(out):
    # poop 24x24 2f
    fs = []
    for k in range(2):
        img, d = cell(24, 24)
        for i, (rx, y) in enumerate([(9, 20), (7, 15), (4, 10)]):
            ellipse(d, 12, y - k, rx, 4, P.darker((150, 100, 60, 255), 0.9 - i * 0.05), P.INK)
        dot(d, 9, 9 - k, 1, P.WHITE); dot(d, 15, 9 - k, 1, P.WHITE)
        fs.append(img)
    strip(fs, {"idle": [0, 1]}, "ov_poop", out)

    # sick skull 20x20 2f (blink)
    fs = []
    for k in range(2):
        img, d = cell(20, 20)
        ellipse(d, 10, 8, 6, 6, P.WHITE, P.INK)
        d.rectangle([7, 12, 13, 15], fill=P.WHITE, outline=P.INK)
        if k == 0:
            dot(d, 8, 8, 1, P.INK); dot(d, 12, 8, 1, P.INK)
        fs.append(img)
    strip(fs, {"blink": [0, 1]}, "ov_sick_skull", out)

    # zzz 24x24 2f
    fs = []
    for k in range(2):
        img, d = cell(24, 24)
        for i, s in enumerate([6, 8, 10]):
            x = 4 + i * 5; y = 16 - i * 5 - (k if i == 2 else 0)
            d.text((x, y), "Z", fill=P.BLUE_D)
        fs.append(img)
    strip(fs, {"idle": [0, 1]}, "ov_zzz", out)

    # call ! 20x20 2f blink
    fs = []
    for k in range(2):
        img, d = cell(20, 20)
        if k == 0:
            d.rectangle([8, 3, 12, 12], fill=P.RED, outline=P.INK)
            dot(d, 10, 16, 2, P.RED)
        fs.append(img)
    strip(fs, {"blink": [0, 1]}, "ov_call", out)

    # heart particle 16x16 3f rise/fade
    fs = []
    for k in range(3):
        img, d = cell(16, 16)
        y = 10 - k * 3
        col = P.PINK_D if k == 0 else (P.PINK_L if k == 1 else P.lighter(P.PINK_L, 0.5))
        d.polygon([(8, y + 5), (3, y), (5, y - 2), (8, y + 1), (11, y - 2), (13, y), (8, y + 5)], fill=col, outline=P.INK)
        fs.append(img)
    strip(fs, {"rise": [0, 1, 2]}, "ov_heart_particle", out)

    # sweat 12x12 1f
    img, d = cell(12, 12)
    d.polygon([(6, 2), (2, 8), (10, 8)], fill=P.BLUE_L, outline=P.BLUE_D)
    ellipse(d, 6, 8, 4, 3, P.BLUE_L, P.BLUE_D)
    strip([img], {"idle": [0]}, "ov_sweat", out)

    # note 16x16 2f
    fs = []
    for k in range(2):
        img, d = cell(16, 16)
        dot(d, 5, 12 - k, 3, P.PURPLE)
        d.line([8, 12 - k, 8, 3], fill=P.INK, width=2)
        d.line([8, 3, 12, 5], fill=P.INK, width=2)
        fs.append(img)
    strip(fs, {"idle": [0, 1]}, "ov_note", out)


# ----------------------------------------------------------------------------- ring icons
def _icon(sizepx=40):
    return cell(sizepx, sizepx)


def gen_icons(out):
    ic = P.WHITE  # authored white; app tints
    defs = {}

    def add(name, drawer):
        img, d = _icon()
        drawer(d)
        strip([img], {"idle": [0]}, name, out)

    add("ic_feed", lambda d: (d.ellipse([10, 14, 30, 32], outline=ic, width=3),
                              d.line([14, 8, 14, 16], fill=ic, width=2),
                              d.line([18, 8, 18, 16], fill=ic, width=2),
                              d.line([22, 8, 22, 16], fill=ic, width=2)))
    add("ic_light", lambda d: (d.ellipse([13, 8, 27, 22], outline=ic, width=3),
                               d.rectangle([16, 22, 24, 28], outline=ic, width=2),
                               [d.line([20, 4, 20, 1], fill=ic, width=2)]))
    add("ic_play", lambda d: d.polygon([(14, 10), (14, 30), (30, 20)], outline=ic, width=1, fill=ic))
    add("ic_bathroom", lambda d: (d.arc([10, 12, 30, 30], 0, 180, fill=ic, width=3),
                                  d.line([10, 20, 30, 20], fill=ic, width=3),
                                  d.line([20, 20, 20, 8], fill=ic, width=3)))
    add("ic_medicine", lambda d: (d.line([10, 30, 30, 10], fill=ic, width=6),
                                  d.ellipse([24, 6, 34, 16], outline=ic, width=2)))
    add("ic_status", lambda d: (d.rectangle([10, 8, 30, 32], outline=ic, width=3),
                                d.line([14, 14, 26, 14], fill=ic, width=2),
                                d.line([14, 20, 26, 20], fill=ic, width=2),
                                d.line([14, 26, 22, 26], fill=ic, width=2)))
    add("ic_discipline", lambda d: (d.line([8, 32, 20, 12], fill=ic, width=3),
                                    d.polygon([(18, 8), (28, 14), (22, 20), (14, 14)], outline=ic, width=2)))
    add("ic_shop", lambda d: (d.polygon([(8, 14), (32, 14), (28, 32), (12, 32)], outline=ic, width=3),
                              d.arc([13, 6, 27, 20], 180, 360, fill=ic, width=2)))
    add("ic_steps", lambda d: (d.ellipse([10, 8, 20, 20], outline=ic, width=2),
                               d.ellipse([20, 20, 30, 32], outline=ic, width=2)))
    add("ic_settings", lambda d: (d.ellipse([13, 13, 27, 27], outline=ic, width=3),
                                  [d.line([20, 6, 20, 12], fill=ic, width=2),
                                   d.line([20, 28, 20, 34], fill=ic, width=2),
                                   d.line([6, 20, 12, 20], fill=ic, width=2),
                                   d.line([28, 20, 34, 20], fill=ic, width=2)]))
    add("ic_back", lambda d: (d.line([12, 20, 28, 20], fill=ic, width=3),
                              d.line([12, 20, 20, 12], fill=ic, width=3),
                              d.line([12, 20, 20, 28], fill=ic, width=3)))
    add("ic_confirm", lambda d: (d.line([10, 20, 18, 28], fill=ic, width=4),
                                 d.line([18, 28, 32, 10], fill=ic, width=4)))


# ----------------------------------------------------------------------------- UI chrome
def gen_ui(out):
    # hearts 20x20
    for name, filled in (("ui_heart_full", True), ("ui_heart_empty", False)):
        img, d = cell(20, 20)
        pts = [(10, 17), (3, 9), (5, 5), (10, 8), (15, 5), (17, 9), (10, 17)]
        d.polygon(pts, fill=(P.RED if filled else None), outline=P.INK)
        strip([img], {"idle": [0]}, name, out)

    # gp coin 18x18 2f spin
    fs = []
    for k in range(2):
        img, d = cell(18, 18)
        w = 8 if k == 0 else 4
        ellipse(d, 9, 9, w, 8, P.YELLOW, P.darker(P.YELLOW, 0.7), 2)
        if k == 0:
            d.text((6, 4), "G", fill=P.darker(P.YELLOW, 0.6))
        fs.append(img)
    strip(fs, {"spin": [0, 1]}, "ui_gp_coin", out)

    # step shoe 18x18
    img, d = cell(18, 18)
    d.polygon([(3, 12), (12, 12), (16, 9), (12, 6), (3, 6)], fill=P.BLUE_L, outline=P.INK)
    d.line([3, 13, 16, 13], fill=P.INK, width=1)
    strip([img], {"idle": [0]}, "ui_step_shoe", out)

    # selector 44x44 2f pulse (ring)
    fs = []
    for k in range(2):
        img, d = cell(44, 44)
        m = 2 + k
        d.ellipse([m, m, 44 - m, 44 - m], outline=P.YELLOW, width=3)
        fs.append(img)
    strip(fs, {"pulse": [0, 1]}, "ui_selector", out)

    # launcher fg 108x108 (creature) + bg 108x108
    img, d = cell(108, 108)
    baby = draw_creature(72, SPECIES["baby"], "happy0").resize((84, 84), Image.NEAREST)
    img.paste(baby, (12, 14), baby)
    strip([img], {"idle": [0]}, "ic_app_launcher_fg", out)
    img, d = cell(108, 108)
    for y in range(108):
        c = P.lighter(P.LCD_L, y / 300)
        d.line([0, y, 108, y], fill=c)
    strip([img], {"idle": [0]}, "ic_app_launcher_bg", out)

    # notification 24x24 monochrome (heart)
    img, d = cell(24, 24)
    d.polygon([(12, 20), (3, 10), (6, 5), (12, 9), (18, 5), (21, 10), (12, 20)], fill=P.WHITE)
    strip([img], {"idle": [0]}, "ic_notification", out)

    # splash 480x480
    img, d = cell(480, 480)
    for y in range(480):
        d.line([0, y, 480, y], fill=P.lighter(P.LCD_L, y / 700))
    big = draw_creature(160, SPECIES["baby"], "happy0").resize((240, 240), Image.NEAREST)
    img.paste(big, (120, 120), big)
    d.text((196, 380), "TamaWatch", fill=P.INK)
    strip([img], {"idle": [0]}, "img_splash", out)


# ----------------------------------------------------------------------------- backgrounds
def _round_bg(topcol, botcol, floorcol=None):
    img, d = cell(480, 480)
    for y in range(480):
        t = y / 480
        c = (int(topcol[0] + (botcol[0] - topcol[0]) * t),
             int(topcol[1] + (botcol[1] - topcol[1]) * t),
             int(topcol[2] + (botcol[2] - topcol[2]) * t), 255)
        d.line([0, y, 480, y], fill=c)
    if floorcol:
        d.rectangle([0, 360, 480, 480], fill=floorcol)
        d.line([0, 360, 480, 360], fill=P.darker(floorcol, 0.7), width=3)
    return img, d


def gen_backgrounds(out):
    def one(name, top, bot, floor=None, extra=None):
        img, d = _round_bg(top, bot, floor)
        if extra:
            extra(d)
        strip([img], {"idle": [0]}, name, out)

    one("bg_room_day", P.lighter(P.BLUE_L, 0.4), P.BLUE_L, P.lighter(P.CREAM, 0.1),
        lambda d: d.rectangle([300, 120, 400, 220], outline=P.WHITE, width=4))  # window
    one("bg_room_night", (30, 34, 70, 255), (18, 20, 44, 255), (40, 42, 70, 255),
        lambda d: [dot(d, 80 + i * 60, 80 + (i % 3) * 40, 2, P.WHITE) for i in range(6)])
    one("bg_egg", P.lighter(P.LCD_L, 0.3), P.LCD_L, P.lighter(P.CREAM, 0.2))
    one("bg_shop", P.lighter(P.YELLOW, 0.4), P.YELLOW, P.lighter(P.CREAM, 0.1),
        lambda d: [d.line([0, 90, 480, 90], fill=P.RED, width=8),
                   [d.rectangle([40 + i * 70, 60, 90 + i * 70, 90], fill=(P.RED if i % 2 else P.WHITE)) for i in range(6)]])
    one("bg_game_jump", P.lighter(P.BLUE_L, 0.5), P.BLUE_L, P.GREEN)
    one("bg_game_guess", P.lighter(P.PURPLE, 0.5), P.PURPLE, P.lighter(P.CREAM, 0.1))
    one("bg_game_catch", P.lighter(P.LCD_L, 0.4), P.LCD_L, P.lighter(P.CREAM, 0.1))
    one("bg_ambient", (0, 0, 0, 255), (6, 8, 16, 255))
    # cosmetics room reskins
    one("cos_bg_beach", P.lighter(P.BLUE_L, 0.5), (250, 230, 160, 255), (240, 220, 150, 255),
        lambda d: ellipse(d, 400, 90, 40, 40, P.YELLOW, P.darker(P.YELLOW, 0.8)))
    one("cos_bg_space", (20, 10, 40, 255), (5, 2, 15, 255), None,
        lambda d: [dot(d, (i * 53) % 480, (i * 91) % 360, 2, P.WHITE) for i in range(40)] + [ellipse(d, 120, 120, 34, 34, P.PURPLE, P.WHITE)])


# ----------------------------------------------------------------------------- items
def gen_items(out):
    def item(name, drawer, sizepx=32):
        img, d = cell(sizepx, sizepx)
        drawer(d)
        strip([img], {"idle": [0]}, name, out)

    item("item_meal_bread", lambda d: (ellipse(d, 16, 18, 12, 8, (210, 160, 90, 255), P.INK), d.line([8, 18, 24, 18], fill=P.darker((210, 160, 90, 255), 0.7), width=1)))
    item("item_meal_bowl", lambda d: (d.arc([6, 12, 26, 30], 0, 180, fill=P.INK, width=3), d.line([6, 14, 26, 14], fill=P.WHITE, width=3), ellipse(d, 16, 12, 8, 3, P.LCD_L)))
    item("item_meal_fish", lambda d: (ellipse(d, 14, 16, 9, 6, P.BLUE_L, P.INK), d.polygon([(23, 16), (30, 10), (30, 22)], fill=P.BLUE_L, outline=P.INK), dot(d, 10, 14, 1, P.INK)))
    item("item_meal_veg", lambda d: (ellipse(d, 16, 20, 8, 9, P.RED, P.INK), d.line([16, 11, 16, 6], fill=P.GREEN, width=2), ellipse(d, 16, 7, 4, 2, P.GREEN)))
    item("item_snack_cake", lambda d: (d.rectangle([8, 16, 24, 28], fill=P.CREAM, outline=P.INK), d.rectangle([8, 12, 24, 16], fill=P.PINK_L, outline=P.INK), dot(d, 16, 8, 2, P.RED)))
    item("item_snack_candy", lambda d: (ellipse(d, 16, 16, 7, 7, P.PINK_D, P.INK), d.polygon([(9, 16), (4, 12), (4, 20)], fill=P.PINK_L, outline=P.INK), d.polygon([(23, 16), (28, 12), (28, 20)], fill=P.PINK_L, outline=P.INK)))
    item("item_snack_juice", lambda d: (d.polygon([(11, 10), (21, 10), (19, 28), (13, 28)], fill=P.YELLOW, outline=P.INK), d.line([16, 10, 20, 3], fill=P.RED, width=2)))
    item("item_snack_icecream", lambda d: (d.polygon([(10, 16), (22, 16), (16, 30)], fill=(210, 170, 110, 255), outline=P.INK), ellipse(d, 16, 13, 7, 6, P.PINK_L, P.INK), dot(d, 16, 7, 2, P.RED)))
    item("item_medicine", lambda d: (d.rectangle([10, 8, 22, 28], fill=P.WHITE, outline=P.INK), d.rectangle([12, 14, 20, 22], fill=P.RED), d.rectangle([15, 12, 17, 24], fill=P.WHITE), d.rectangle([12, 17, 20, 19], fill=P.WHITE)))


# ----------------------------------------------------------------------------- game props
def gen_gameprops(out):
    # obstacle 24x24 2f
    fs = []
    for k in range(2):
        img, d = cell(24, 24)
        d.rectangle([8, 6 + k, 16, 22], fill=P.GREEN, outline=P.INK)
        d.polygon([(6, 12), (8, 10), (8, 16)], fill=P.GREEN, outline=P.INK)
        fs.append(img)
    strip(fs, {"idle": [0, 1]}, "game_obstacle", out)
    # arrows 32x32
    img, d = cell(32, 32)
    d.polygon([(22, 6), (6, 16), (22, 26)], fill=P.YELLOW, outline=P.INK)
    strip([img], {"idle": [0]}, "game_arrow_l", out)
    img, d = cell(32, 32)
    d.polygon([(10, 6), (26, 16), (10, 26)], fill=P.YELLOW, outline=P.INK)
    strip([img], {"idle": [0]}, "game_arrow_r", out)
    # basket 40x24
    img, d = cell(40, 24)
    d.polygon([(4, 6), (36, 6), (32, 22), (8, 22)], fill=(180, 130, 80, 255), outline=P.INK)
    for x in range(10, 34, 6):
        d.line([x, 6, x - 2, 22], fill=P.INK, width=1)
    strip([img], {"idle": [0]}, "game_basket", out)
    # treats 20x20
    img, d = cell(20, 20)
    ellipse(d, 10, 10, 7, 7, P.PINK_L, P.INK); dot(d, 10, 10, 2, P.RED)
    strip([img], {"idle": [0]}, "game_treat_good", out)
    img, d = cell(20, 20)
    ellipse(d, 10, 10, 7, 7, P.GRAY_D, P.INK)
    d.line([6, 6, 14, 14], fill=P.RED, width=2); d.line([14, 6, 6, 14], fill=P.RED, width=2)
    strip([img], {"idle": [0]}, "game_treat_bad", out)


# ----------------------------------------------------------------------------- cutscene / fx
def gen_cutscene(out):
    # hatch 4f (egg crack -> baby) 64x64
    fs = []
    for k in range(4):
        img, d = cell(64, 64)
        if k < 2:
            ellipse(d, 32, 36, 20, 24, P.CREAM, P.INK, 2)
            for n in range(k + 1):
                d.line([24 + n * 6, 24, 30 + n * 6, 34], fill=P.INK, width=2)
        else:
            b = draw_creature(48, SPECIES["baby"], "happy0")
            img.paste(b, (8, 8), b)
            if k == 3:
                for a in range(0, 360, 45):
                    import math
                    x = 32 + int(26 * math.cos(math.radians(a)))
                    y = 32 + int(26 * math.sin(math.radians(a)))
                    dot(d, x, y, 2, P.YELLOW)
        fs.append(img)
    strip(fs, {"play": [0, 1, 2, 3]}, "cut_hatch", out)

    # evolve 4f starburst 96x96
    import math
    fs = []
    for k in range(4):
        img, d = cell(96, 96)
        r = 10 + k * 12
        for a in range(0, 360, 30):
            x = 48 + int(r * math.cos(math.radians(a)))
            y = 48 + int(r * math.sin(math.radians(a)))
            dot(d, x, y, 3 - (k // 2), P.YELLOW)
        ellipse(d, 48, 48, 8 + k * 3, 8 + k * 3, P.WHITE, None)
        fs.append(img)
    strip(fs, {"play": [0, 1, 2, 3]}, "cut_evolve", out)

    # farewell 3f (fade to star) 64x64
    fs = []
    for k in range(3):
        img, d = cell(64, 64)
        alpha = [200, 120, 60][k]
        b = draw_creature(48, SPECIES["adult_a"], "sleep0")
        b.putalpha(b.getchannel("A").point(lambda a: min(a, alpha)))
        img.paste(b, (8, 8 - k * 3), b)
        dot(d, 32, 10 - k * 2, 2 + k, P.YELLOW)
        fs.append(img)
    strip(fs, {"play": [0, 1, 2]}, "cut_farewell", out)

    # fx_star 16x16 3f
    fs = []
    for k in range(3):
        img, d = cell(16, 16)
        r = [5, 7, 4][k]
        pts = []
        for i in range(10):
            ang = math.radians(i * 36 - 90)
            rr = r if i % 2 == 0 else r / 2
            pts.append((8 + rr * math.cos(ang), 8 + rr * math.sin(ang)))
        d.polygon(pts, fill=P.YELLOW, outline=P.INK)
        fs.append(img)
    strip(fs, {"twinkle": [0, 1, 2]}, "fx_star", out)

    # fx_goal 24x24 3f (checkmark burst)
    fs = []
    for k in range(3):
        img, d = cell(24, 24)
        ellipse(d, 12, 12, 8 + k, 8 + k, P.GREEN, P.INK)
        d.line([7, 12, 11, 16], fill=P.WHITE, width=2)
        d.line([11, 16, 17, 8], fill=P.WHITE, width=2)
        fs.append(img)
    strip(fs, {"play": [0, 1, 2]}, "fx_goal", out)


def generate(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    gen_egg(out_dir)
    gen_characters(out_dir)
    gen_overlays(out_dir)
    gen_icons(out_dir)
    gen_ui(out_dir)
    gen_backgrounds(out_dir)
    gen_items(out_dir)
    gen_gameprops(out_dir)
    gen_cutscene(out_dir)
    return MANIFEST
