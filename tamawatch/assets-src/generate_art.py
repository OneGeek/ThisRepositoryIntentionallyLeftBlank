"""TamaWatch art generator. Emits RGBA PNG frame-strips for every art ID in the
Authoritative Generation Set (docs/04) and returns a manifest fragment
{id: {file, frameW, frameH, frames, tags}}.

Art is drawn on a supersampled canvas through a coordinate-scaling proxy and
downsampled with LANCZOS, so shapes come out as clean anti-aliased lines; the
app then scales the result smoothly (bilinear) instead of nearest-neighbor."""

import os
import math
import functools
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import palette as P

MANIFEST = {}

# --- clean-line rendering ----------------------------------------------------
# Draw at DRAW x the logical cell size, then downsample by SS -> stored at UP x.
# The SS-factor downsample is what anti-aliases the edges (clean lines).
SS = 3            # antialias supersample (draw big, downsample smooth)
UP = 4            # stored resolution multiplier over the logical cell size
DRAW = SS * UP    # coordinate scale applied while drawing cell-based sprites


@functools.lru_cache(maxsize=None)
def _sfont(px):
    px = max(6, int(px))
    for p in ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
              "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf"):
        if os.path.exists(p):
            return ImageFont.truetype(p, px)
    return ImageFont.load_default()


class _SDraw:
    """Wraps an ImageDraw and multiplies every coordinate + stroke width by a
    scale, so drawing code written in 'logical' pixels renders supersampled.
    joint='curve' + rounded stroke ends keep lines clean, not blocky."""

    def __init__(self, draw, scale):
        self._d = draw
        self._s = scale

    def _pts(self, xy):
        s = self._s
        if xy and isinstance(xy[0], (tuple, list)):
            return [tuple(v * s for v in p) for p in xy]
        return [v * s for v in xy]

    def _w(self, width):
        return max(1, int(round(width * self._s)))

    def line(self, xy, fill=None, width=1, joint="curve"):
        self._d.line(self._pts(xy), fill=fill, width=self._w(width), joint=joint)

    def ellipse(self, xy, fill=None, outline=None, width=1):
        self._d.ellipse(self._pts(xy), fill=fill, outline=outline, width=self._w(width))

    def rectangle(self, xy, fill=None, outline=None, width=1):
        self._d.rectangle(self._pts(xy), fill=fill, outline=outline, width=self._w(width))

    def rounded_rectangle(self, xy, radius=0, fill=None, outline=None, width=1):
        self._d.rounded_rectangle(self._pts(xy), radius=radius * self._s,
                                  fill=fill, outline=outline, width=self._w(width))

    def polygon(self, xy, fill=None, outline=None, width=1):
        try:
            self._d.polygon(self._pts(xy), fill=fill, outline=outline, width=self._w(width))
        except TypeError:
            self._d.polygon(self._pts(xy), fill=fill, outline=outline)

    def arc(self, xy, start, end, fill=None, width=1):
        self._d.arc(self._pts(xy), start, end, fill=fill, width=self._w(width))

    def chord(self, xy, start, end, fill=None, outline=None, width=1):
        self._d.chord(self._pts(xy), start, end, fill=fill, outline=outline, width=self._w(width))

    def point(self, xy, fill=None):
        self._d.point(self._pts(xy), fill=fill)

    def text(self, xy, s, fill=None, font=None, **kw):
        f = font or _sfont(round(11 * self._s))
        self._d.text((xy[0] * self._s, xy[1] * self._s), s, fill=fill, font=f, **kw)


def cell(w, h, scale=DRAW):
    """A supersampled transparent canvas + a scaling draw proxy."""
    img = Image.new("RGBA", (w * scale, h * scale), P.TRANSPARENT)
    return img, _SDraw(ImageDraw.Draw(img), scale)


def _paste(base, sub, x, y, scale=DRAW):
    """alpha-composite a supersampled sub-image at a logical (x, y)."""
    base.alpha_composite(sub, (int(x * scale), int(y * scale)))


def _fill_vgrad(img, top, bot):
    """Fill an existing RGBA image with a smooth vertical gradient (numpy)."""
    w, h = img.size
    ys = np.linspace(0.0, 1.0, h, dtype=np.float32)
    col = _rgb(top)[None, :] + (_rgb(bot) - _rgb(top))[None, :] * ys[:, None]
    arr = np.clip(np.repeat(col[:, None, :], w, axis=1), 0, 255).astype(np.uint8)
    a = np.full((h, w, 1), 255, np.uint8)
    img.paste(Image.fromarray(np.concatenate([arr, a], axis=2), "RGBA"), (0, 0))


def strip(frames, tags, id_, out_dir, ss=SS):
    """Downsample each supersampled frame (LANCZOS) then lay them into one PNG."""
    if ss > 1:
        frames = [f.resize((max(1, f.width // ss), max(1, f.height // ss)), Image.LANCZOS) for f in frames]
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


def _draw_eyes(d, cx, ey, ex, size, state):
    """state: open | wide | closed | happy | x"""
    if state == "closed":
        d.line([cx - ex - 3, ey, cx - ex + 3, ey], fill=P.INK, width=2)
        d.line([cx + ex - 3, ey, cx + ex + 3, ey], fill=P.INK, width=2)
    elif state == "happy":
        d.arc([cx - ex - 4, ey - 4, cx - ex + 4, ey + 4], 200, 340, fill=P.INK, width=2)
        d.arc([cx + ex - 4, ey - 4, cx + ex + 4, ey + 4], 200, 340, fill=P.INK, width=2)
    elif state == "x":
        for sgn in (-1, 1):
            bx = cx + sgn * ex
            d.line([bx - 3, ey - 3, bx + 3, ey + 3], fill=P.INK, width=2)
            d.line([bx - 3, ey + 3, bx + 3, ey - 3], fill=P.INK, width=2)
    else:  # open / wide
        r = (3 if size >= 56 else 2) + (1 if state == "wide" else 0)
        dot(d, cx - ex, ey, r, P.INK)
        dot(d, cx + ex, ey, r, P.INK)
        dot(d, cx - ex + 1, ey - 1, 1, P.WHITE)
        dot(d, cx + ex + 1, ey - 1, 1, P.WHITE)


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


def draw_feature(d, cx, top, rx, feat, accent, dy=0):
    top = int(top + dy)   # dy = secondary-motion lag, so features jiggle after the body
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


def _motion(anim, t, sp):
    """Map (anim, phase t in [0,1)) -> a motion state dict. This is where the
    life comes from: squash/stretch, bob, lean, blink, secondary-motion lag."""
    ph = t * 2 * math.pi
    m = dict(bob=0.0, lean=0.0, sqx=1.0, sqy=1.0, feat_dy=0.0,
             lift_l=0, lift_r=0, arms="side", eyes="open", mouth="smile",
             recolor=None)
    if anim == "idle":
        b = math.sin(ph)
        m.update(sqy=1 + 0.05 * b, sqx=1 - 0.035 * b, bob=-1 - 0.6 * (b + 1),
                 feat_dy=-0.8 * b, eyes=("closed" if 0.70 <= t < 0.86 else "open"))
    elif anim == "walk":
        s = math.sin(ph)
        m.update(bob=-abs(s) * 2, lean=1.2 * s, feat_dy=-0.6 * s,
                 lift_l=(3 if s > 0 else 0), lift_r=(3 if s < 0 else 0))
    elif anim == "happy":
        jump = math.sin(math.pi * t) ** 1.3            # 0 -> 1 -> 0 arc
        crouch = max(0.0, -math.sin(2 * math.pi * t))  # squash at take-off/landing
        m.update(bob=-11 * jump, sqy=1 + 0.10 * jump - 0.12 * crouch,
                 sqx=1 - 0.06 * jump + 0.10 * crouch, feat_dy=-3.0 * jump,
                 arms="up", eyes="happy", mouth="open")
    elif anim == "sad":
        b = math.sin(ph)
        m.update(sqy=1 + 0.02 * b, bob=1, mouth="frown")
    elif anim == "sick":
        m.update(lean=1.6 * math.sin(ph), recolor=P.lighter(P.GRAY_L, 0.2),
                 eyes="x", mouth="frown")
    elif anim == "sleep":
        b = math.sin(ph)
        m.update(sqy=1 + 0.04 * b, sqx=1 - 0.03 * b, bob=1 - 0.5 * (b + 1),
                 eyes="closed", mouth="smile")
    elif anim == "eat":
        chew = math.sin(ph)
        m.update(bob=-1, mouth=("open" if chew > 0 else "smile"))
    elif anim == "call":
        shake = math.sin(ph * 2)
        m.update(lean=2.6 * shake, feat_dy=-1.5 * shake, eyes="wide",
                 mouth=("open" if math.sin(ph) > 0 else "smile"))
    return m


def draw_creature(size, sp, anim="idle", t=0.0):
    """Render one animation frame. anim in idle|walk|happy|sad|sick|sleep|eat|call;
    t is the phase within that anim's loop, so frames tween smoothly."""
    img, d = cell(size, size)
    cx = size // 2
    baseline = int(size * 0.82)
    sx, sy = sp["shape"]
    rx0, ry0 = int(size * sx * 0.5), int(size * sy * 0.5)
    m = _motion(anim, t, sp)

    rx = max(4, int(rx0 * m["sqx"]))
    ry = max(4, int(ry0 * m["sqy"]))
    cy = int(baseline - ry + m["bob"])
    ucx = int(cx + m["lean"])              # upper body leans; feet stay planted
    body = m["recolor"] or sp["body"]
    outline = P.INK

    # soft grounding shadow — a flat translucent ellipse (smooth once downsampled);
    # shrinks as the body lifts off (jumps) so motion stays grounded
    lift = max(0.0, -m["bob"])
    sh_rx = max(4, int(rx * (0.92 - 0.03 * lift)))
    sh_ry = max(2, rx // 5)
    d.ellipse([cx - sh_rx, baseline + 3 - sh_ry, cx + sh_rx, baseline + 3 + sh_ry],
              fill=(0, 0, 0, 70), outline=None)

    # feet (planted; alternate lift on walk)
    foot_dx = int(rx * 0.55)
    ellipse(d, cx - foot_dx, baseline - m["lift_l"], 5, 4, P.darker(body, 0.8))
    ellipse(d, cx + foot_dx, baseline - m["lift_r"], 5, 4, P.darker(body, 0.8))

    # feature (top, lags the body for secondary motion)
    draw_feature(d, ucx, cy - ry, rx, sp["feat"], sp.get("accent", P.INK), m["feat_dy"])

    # body + belly
    ellipse(d, ucx, cy, rx, ry, body, outline, ow=2)
    ellipse(d, ucx, cy + int(ry * 0.25), int(rx * 0.6), int(ry * 0.5), P.lighter(body, 0.45), outline=None)

    # arms
    arm_y = cy + int(ry * 0.15)
    if m["arms"] == "up":
        d.line([ucx - rx, arm_y, ucx - rx - 4, arm_y - 6], fill=outline, width=3)
        d.line([ucx + rx, arm_y, ucx + rx + 4, arm_y - 6], fill=outline, width=3)
    else:
        dot(d, ucx - rx, arm_y, 3, P.darker(body, 0.85))
        dot(d, ucx + rx, arm_y, 3, P.darker(body, 0.85))

    # eyes
    ey = cy - int(ry * 0.15)
    ex = int(rx * 0.42)
    _draw_eyes(d, ucx, ey, ex, size, m["eyes"])

    # cheeks
    if sp.get("cheeks") and m["eyes"] != "x" and anim not in ("sad", "sick"):
        dot(d, ucx - int(rx * 0.7), ey + 4, 2, P.PINK_L)
        dot(d, ucx + int(rx * 0.7), ey + 4, 2, P.PINK_L)

    # mouth
    my = cy + int(ry * 0.15)
    if m["mouth"] == "open" and anim == "eat":
        ellipse(d, ucx, my + 1, 3, 4, P.RED, P.INK)
    elif m["mouth"] == "open":
        d.chord([ucx - 5, my - 3, ucx + 5, my + 6], 0, 180, fill=P.RED, outline=P.INK)
    elif m["mouth"] == "frown":
        d.arc([ucx - 5, my + 1, ucx + 5, my + 7], 20, 160, fill=P.INK, width=2)
    elif sp.get("grumpy") and anim not in ("happy", "eat"):
        d.line([ucx - 4, my + 2, ucx + 4, my + 2], fill=P.INK, width=2)
    else:
        d.arc([ucx - 4, my - 2, ucx + 4, my + 4], 20, 160, fill=P.INK, width=2)

    return img


# Frames per animation loop. More frames -> smoother motion; the renderer
# cycles any tag's frame list, so counts are free (validation checks IDs only).
ANIM_FRAMES = {"idle": 6, "walk": 6, "happy": 6, "sad": 2, "sick": 2,
               "sleep": 4, "eat": 4, "call": 4}
ACTIVE_ANIMS = ["idle", "walk", "happy"]                                  # child/teen/adult sheets
STAGESET_ANIMS = ["sad", "sick", "sleep", "eat", "call"]                  # shared per-stage states
BABY_ANIMS = ["idle", "walk", "happy", "sad", "sick", "sleep", "eat", "call"]

SIZES = {"baby": 48, "child": 56, "teen": 64, "adult": 64}


def _build_sheet(size, sp, anims, id_, out):
    """Concatenate several animation loops into one strip, recording each anim's
    frame indices as a manifest tag."""
    frames, tags = [], {}
    for a in anims:
        n = ANIM_FRAMES[a]
        start = len(frames)
        frames += [draw_creature(size, sp, a, i / n) for i in range(n)]
        tags[a] = list(range(start, start + n))
    strip(frames, tags, id_, out)


def gen_characters(out):
    _build_sheet(SIZES["baby"], SPECIES["baby"], BABY_ANIMS, "spr_baby", out)
    for key, sp in SPECIES.items():
        if key == "baby":
            continue
        stage = key.split("_")[0]
        _build_sheet(SIZES[stage], sp, ACTIVE_ANIMS, "spr_" + key, out)
    # stagesets (neutral gray silhouette per stage)
    for stage in ("child", "teen", "adult"):
        neutral = dict(SPECIES[stage + "_a"]); neutral["body"] = P.GRAY_L; neutral["accent"] = P.GRAY_D
        _build_sheet(SIZES[stage], neutral, STAGESET_ANIMS, "spr_stageset_" + stage, out)


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
    w = P.WHITE

    def add(name, drawer):                          # plain transparent-cell icon (meter points)
        img, d = _icon()
        drawer(d)
        strip([img], {"idle": [0]}, name, out)

    def chip_icon(name, color, glyph):
        """A colored circular 'button' with a clean white glyph — legible on any
        background (light room, dark night, shop) without relying on a tint."""
        img, d = _icon()
        d.ellipse([3, 3, 37, 37], fill=color, outline=P.INK, width=2)
        glyph(d)
        strip([img], {"idle": [0]}, name, out)

    def g_feed(d):
        d.chord([9, 15, 31, 32], 0, 180, fill=w)                      # bowl
        d.rectangle([7, 15, 33, 18], fill=w)                          # rim
        for sx in (14, 20, 26):
            d.line([sx, 7, sx, 12], fill=w, width=2)                  # steam

    def g_light(d):
        d.ellipse([13, 8, 27, 22], fill=w)                            # bulb
        d.rectangle([17, 21, 23, 28], fill=w)                         # base
        d.line([20, 2, 20, 6], fill=w, width=2)
        d.line([8, 12, 11, 14], fill=w, width=2)
        d.line([29, 12, 32, 14], fill=w, width=2)

    def g_play(d):
        d.polygon([(15, 11), (15, 29), (31, 20)], fill=w)

    def g_clean(d):                                                   # water drop + sparkle
        d.polygon([(20, 8), (12, 20), (28, 20)], fill=w)
        d.ellipse([12, 16, 28, 30], fill=w)
        d.line([29, 8, 29, 13], fill=w, width=2)
        d.line([27, 10, 31, 10], fill=w, width=2)

    def g_medicine(d):                                               # medical cross
        d.rounded_rectangle([17, 8, 23, 32], radius=2, fill=w)
        d.rounded_rectangle([8, 17, 32, 23], radius=2, fill=w)

    def g_status(d):                                                 # clipboard / list
        d.rounded_rectangle([11, 9, 29, 32], radius=3, fill=w, outline=P.INK, width=1)
        d.rounded_rectangle([16, 6, 24, 11], radius=2, fill=w, outline=P.INK, width=1)
        for yy in (16, 21, 26):
            d.line([15, yy, 25, yy], fill=P.INK, width=2)

    def g_disc(d):                                                   # exclamation (scold)
        d.rounded_rectangle([17, 7, 23, 24], radius=3, fill=w)
        d.ellipse([17, 27, 23, 33], fill=w)

    def g_shop(d):                                                   # shopping bag
        d.polygon([(11, 15), (29, 15), (31, 32), (9, 32)], fill=w)
        d.arc([14, 7, 26, 20], 180, 360, fill=w, width=3)

    def g_steps(d):                                                  # side-view sneaker
        d.polygon([(9, 26), (9, 18), (16, 16), (24, 20), (31, 22), (31, 26)], fill=w, outline=P.INK, width=1)
        d.line([9, 28, 31, 28], fill=w, width=3)

    def g_settings(d):                                              # three sliders
        for i, yy in enumerate((13, 20, 27)):
            d.line([9, yy, 31, yy], fill=w, width=3)
            kx = (14, 25, 18)[i]
            d.ellipse([kx - 3, yy - 3, kx + 3, yy + 3], fill=w, outline=P.INK, width=1)

    def g_back(d):
        d.line([25, 10, 14, 20, 25, 30], fill=w, width=4)

    def g_confirm(d):
        d.line([11, 21, 18, 28, 30, 11], fill=w, width=4)

    chip_icon("ic_feed", (232, 120, 72, 255), g_feed)
    chip_icon("ic_light", (230, 170, 50, 255), g_light)
    chip_icon("ic_play", (90, 175, 100, 255), g_play)
    chip_icon("ic_bathroom", (70, 140, 205, 255), g_clean)
    chip_icon("ic_medicine", (223, 62, 62, 255), g_medicine)
    chip_icon("ic_status", (150, 110, 195, 255), g_status)
    chip_icon("ic_discipline", (210, 120, 70, 255), g_disc)
    chip_icon("ic_shop", (60, 160, 165, 255), g_shop)
    chip_icon("ic_steps", (80, 150, 215, 255), g_steps)
    chip_icon("ic_settings", (120, 120, 132, 255), g_settings)
    chip_icon("ic_back", (96, 102, 116, 255), g_back)
    chip_icon("ic_confirm", (80, 180, 110, 255), g_confirm)
    # Meter "points", Minecraft-style: each unit in a meter IS its icon, full or
    # spent. Hunger = meat-shank, Happy = smiley; the *_empty variants are dimmed
    # so a row reads as filled/unfilled points with no text labels.
    def _shank(dd, meat, bone, shine=None):
        dd.line([22, 21, 30, 30], fill=bone, width=5)
        dd.ellipse([27, 24, 37, 34], fill=bone, outline=P.INK, width=2)
        dd.ellipse([26, 30, 36, 40], fill=bone, outline=P.INK, width=2)
        dd.ellipse([4, 3, 26, 30], fill=meat, outline=P.INK, width=2)
        if shine:
            dd.ellipse([9, 8, 16, 15], fill=shine)

    def _smiley(dd, face, feat=P.INK):
        dd.ellipse([5, 5, 35, 35], fill=face, outline=P.INK, width=2)
        dot(dd, 16, 18, 2, feat)
        dot(dd, 25, 18, 2, feat)
        dd.arc([14, 16, 27, 30], 20, 160, fill=feat, width=3)

    add("ic_hunger", lambda d: _shank(d, (178, 84, 60, 255), P.CREAM, (216, 140, 112, 255)))
    add("ic_hunger_empty", lambda d: _shank(d, (72, 68, 78, 255), (116, 112, 118, 255)))
    add("ic_mood", lambda d: _smiley(d, P.YELLOW))
    add("ic_mood_empty", lambda d: _smiley(d, (98, 94, 100, 255), feat=(56, 54, 60, 255)))


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
    _paste(img, draw_creature(84, SPECIES["baby"], "happy", 0.5), 12, 14)
    strip([img], {"idle": [0]}, "ic_app_launcher_fg", out)
    img, d = cell(108, 108)
    _fill_vgrad(img, P.LCD_L, P.lighter(P.LCD_L, 0.4))
    strip([img], {"idle": [0]}, "ic_app_launcher_bg", out)

    # notification 24x24 monochrome (heart)
    img, d = cell(24, 24)
    d.polygon([(12, 20), (3, 10), (6, 5), (12, 9), (18, 5), (21, 10), (12, 20)], fill=P.WHITE)
    strip([img], {"idle": [0]}, "ic_notification", out)

    # splash 480x480 (native store res; smooth gradient + composited creature + title)
    spl = Image.new("RGBA", (480, 480), (0, 0, 0, 255))
    _fill_vgrad(spl, P.LCD_L, P.lighter(P.LCD_L, 0.6))
    big = draw_creature(240, SPECIES["baby"], "happy", 0.5).resize((240, 240), Image.LANCZOS)
    spl.alpha_composite(big, (120, 108))
    sd = ImageDraw.Draw(spl)
    f = _sfont(40)
    tw = sd.textlength("TamaWatch", font=f)
    sd.text(((480 - tw) / 2, 384), "TamaWatch", font=f, fill=P.INK)
    strip([spl], {"idle": [0]}, "img_splash", out, ss=1)


# ----------------------------------------------------------------------------- backgrounds
BG = 480
WOOD = (196, 150, 96, 255)


def _rgb(c):
    return np.array(c[:3], dtype=np.float32)


def _scene(sky_top, sky_bot, floor_top=None, floor_bot=None, horizon=None, vignette=0.45):
    """Smooth (anti-aliased) RGBA background with an optional floor plane, built
    supersampled at BG*SS and returned with a scaling draw proxy so props drawn
    on top are anti-aliased too. strip() downsamples it back to BG."""
    S = BG * SS

    def vgrad(top, bot, y0, y1):
        ys = np.clip((np.arange(S, dtype=np.float32) - y0) / max(1.0, y1 - y0), 0.0, 1.0)
        return _rgb(top)[None, :] + (_rgb(bot) - _rgb(top))[None, :] * ys[:, None]

    hz = int(horizon * SS) if (floor_top is not None and horizon) else S
    arr = np.repeat(vgrad(sky_top, sky_bot, 0, hz)[:, None, :], S, axis=1)
    if floor_top is not None and horizon:
        fcol = vgrad(floor_top, floor_bot or P.darker(floor_top, 0.72), hz, S)
        arr[hz:] = np.repeat(fcol[:, None, :], S, axis=1)[hz:]
    if vignette > 0:                 # frame the round display, pop the pet
        yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
        c = (S - 1) / 2.0
        r = np.sqrt((xx - c) ** 2 + (yy - c) ** 2) / (S / 2.0)
        t = np.clip((r - 0.55) / 0.5, 0.0, 1.0)
        arr = arr * (1.0 - vignette * (t * t * (3 - 2 * t)))[:, :, None]
    arr = np.clip(arr, 0, 255).astype(np.uint8)
    rgba = np.concatenate([arr, np.full((S, S, 1), 255, np.uint8)], axis=2)
    img = Image.fromarray(rgba, "RGBA")
    return img, _SDraw(ImageDraw.Draw(img), SS)


def _stars(d, n, ymax=BG, bright=P.WHITE):
    for i in range(n):
        x, y = (i * 97 + 13) % BG, (i * 61 + 29) % ymax
        dot(d, x, y, 2 if i % 5 == 0 else 1, bright)
        if i % 7 == 0:
            d.line([x - 3, y, x + 3, y], fill=bright, width=1)
            d.line([x, y - 3, x, y + 3], fill=bright, width=1)


def _cloud(d, cx, cy, s=1.0):
    for (dx, dy, rr) in [(-18, 4, 16), (0, 0, 22), (20, 6, 15), (0, 10, 26)]:
        ellipse(d, int(cx + dx * s), int(cy + dy * s), int(rr * s), int(rr * 0.7 * s), P.WHITE, outline=None)


def _window(d, x0, y0, x1, y1):
    d.rectangle([x0, y0, x1, y1], fill=P.lighter(P.BLUE_L, 0.35), outline=P.WHITE, width=6)
    _cloud(d, (x0 + x1) // 2 - 10, y0 + 34, 0.45)
    mx, my = (x0 + x1) // 2, (y0 + y1) // 2
    d.line([mx, y0, mx, y1], fill=P.WHITE, width=5)
    d.line([x0, my, x1, my], fill=P.WHITE, width=5)


def _plant(d, x, base):
    d.polygon([(x - 12, base), (x + 12, base), (x + 9, base - 22), (x - 9, base - 22)],
              fill=P.darker(P.RED, 0.8), outline=P.INK)
    for (dx, dy) in [(-11, -30), (0, -42), (11, -30), (-5, -38), (6, -38)]:
        d.polygon([(x, base - 20), (x + dx, base - 20 + dy), (x + dx + 6, base - 16)],
                  fill=P.GREEN, outline=P.darker(P.GREEN, 0.7))


def _rug(d, cx, cy, rx, ry, col):
    ellipse(d, cx, cy, rx, ry, col, outline=P.darker(col, 0.7), ow=3)
    ellipse(d, cx, cy, int(rx * 0.6), int(ry * 0.6), P.lighter(col, 0.3), outline=None)


def _moon(d, cx, cy, r):
    ellipse(d, cx, cy, r, r, P.lighter(P.YELLOW, 0.4), outline=None)
    for (dx, dy, rr) in [(-4, -3, 3), (5, 2, 2), (-2, 6, 2)]:
        dot(d, cx + dx, cy + dy, rr, P.darker(P.YELLOW, 0.8))


def gen_backgrounds(out):
    def finish(name, img, d, props=None):
        if props:
            props(d)
        strip([img], {"idle": [0]}, name, out)

    # Cozy room, day — blue wall, warm wood floor, window, plant, rug
    img, d = _scene(P.lighter(P.BLUE_L, 0.6), P.BLUE_L,
                    floor_top=P.lighter(WOOD, 0.2), floor_bot=P.darker(WOOD, 0.8), horizon=330)
    finish("bg_room_day", img, d, lambda d: (
        d.line([0, 330, 480, 330], fill=P.darker(WOOD, 0.6), width=4),
        _window(d, 296, 88, 412, 210), _plant(d, 78, 330),
        _rug(d, 240, 408, 150, 30, P.PINK_L)))

    # Room, night — deep indigo, moon, stars, dim floor
    img, d = _scene((44, 48, 92, 255), (22, 24, 52, 255),
                    floor_top=(52, 54, 82, 255), floor_bot=(30, 32, 56, 255),
                    horizon=330, vignette=0.5)
    finish("bg_room_night", img, d, lambda d: (
        _stars(d, 26, ymax=320), _moon(d, 380, 96, 34),
        d.line([0, 330, 480, 330], fill=(22, 24, 52, 255), width=4)))

    # Egg incubator — soft LCD green, warm floor
    img, d = _scene(P.lighter(P.LCD_L, 0.5), P.LCD_L,
                    floor_top=P.lighter(P.CREAM, 0.2), floor_bot=P.darker(P.CREAM, 0.85),
                    horizon=340, vignette=0.4)
    finish("bg_egg", img, d, lambda d: _rug(d, 240, 410, 120, 26, P.lighter(P.LCD_L, 0.2)))

    # Shop — warm yellow, scalloped awning, stocked shelves
    img, d = _scene(P.lighter(P.YELLOW, 0.5), P.YELLOW,
                    floor_top=P.lighter(P.CREAM, 0.15), floor_bot=P.darker(P.CREAM, 0.8), horizon=340)
    def shop(d):
        for i in range(8):
            c = P.RED if i % 2 == 0 else P.WHITE
            d.rectangle([i * 60, 56, i * 60 + 60, 94], fill=c, outline=P.darker(P.RED, 0.7))
            d.polygon([(i * 60, 94), (i * 60 + 60, 94), (i * 60 + 30, 114)], fill=c, outline=P.darker(P.RED, 0.7))
        for sy in (180, 258):
            d.line([40, sy, 440, sy], fill=P.darker(P.CREAM, 0.5), width=6)
            for j, col in enumerate([P.PINK_D, P.BLUE_D, P.GREEN, P.PURPLE, P.RED]):
                bx = 62 + j * 76
                d.rectangle([bx, sy - 26, bx + 34, sy - 2], fill=col, outline=P.INK)
    finish("bg_shop", img, d, shop)

    # Mini-game stages
    img, d = _scene(P.lighter(P.BLUE_L, 0.6), P.BLUE_L, floor_top=P.GREEN,
                    floor_bot=P.darker(P.GREEN, 0.7), horizon=350)
    finish("bg_game_jump", img, d, lambda d: (_cloud(d, 120, 120, 0.7), _cloud(d, 330, 90, 0.5)))
    img, d = _scene(P.lighter(P.PURPLE, 0.6), P.PURPLE,
                    floor_top=P.lighter(P.PURPLE, 0.1), floor_bot=P.darker(P.PURPLE, 0.7), horizon=350)
    finish("bg_game_guess", img, d, lambda d: _stars(d, 14, ymax=330, bright=P.lighter(P.PURPLE, 0.6)))
    img, d = _scene(P.lighter(P.LCD_L, 0.5), P.LCD_L,
                    floor_top=P.lighter(P.CREAM, 0.1), floor_bot=P.darker(P.CREAM, 0.8), horizon=350)
    finish("bg_game_catch", img, d, None)

    # Always-on / ambient — near-black, flat, low power (no vignette)
    img, d = _scene((0, 0, 0, 255), (8, 10, 20, 255), vignette=0.0)
    finish("bg_ambient", img, d, lambda d: _stars(d, 10, bright=(60, 64, 96, 255)))

    # Beach cosmetic — sky, sea band, sand, sun, cloud
    img, d = _scene(P.lighter(P.BLUE_L, 0.6), P.lighter(P.BLUE_L, 0.2),
                    floor_top=(240, 220, 150, 255), floor_bot=(214, 190, 120, 255), horizon=320)
    def beach(d):
        d.rectangle([0, 268, 480, 320], fill=P.BLUE_D)
        d.rectangle([0, 262, 480, 272], fill=P.lighter(P.BLUE_L, 0.3))
        ellipse(d, 96, 92, 30, 30, P.YELLOW, outline=P.darker(P.YELLOW, 0.8), ow=2)
        ellipse(d, 96, 92, 20, 20, P.lighter(P.YELLOW, 0.4), outline=None)
        _cloud(d, 340, 88, 0.6)
    finish("cos_bg_beach", img, d, beach)

    # Space cosmetic — starfield, ringed planet, moon
    img, d = _scene((26, 14, 48, 255), (6, 3, 16, 255), vignette=0.55)
    def space(d):
        _stars(d, 60, ymax=BG)
        d.line([100, 152, 200, 148], fill=P.lighter(P.PURPLE, 0.4), width=4)   # ring behind
        ellipse(d, 150, 150, 44, 44, P.PURPLE, outline=P.WHITE, ow=2)
        _moon(d, 360, 118, 22)
    finish("cos_bg_space", img, d, space)


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
            b = draw_creature(48, SPECIES["baby"], "happy", 0.5)
            _paste(img, b, 8, 8)
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
        b = draw_creature(48, SPECIES["adult_a"], "sleep", 0.25)
        b.putalpha(b.getchannel("A").point(lambda a: min(a, alpha)))
        _paste(img, b, 8, 8 - k * 3)
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
