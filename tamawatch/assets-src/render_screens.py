"""Render representative TamaWatch screens into a single contact-sheet PNG, so a
build can be eyeballed without installing the APK.

It composites the REAL generated backgrounds / sprites / icons (read straight
from assets_manifest.json) using the same layout the Compose UI uses
(app/.../ui/Screens.kt), masks each screen to the round watch, and tiles five
screens with captions. These are asset-composited mockups — faithful to the
art and layout the APK ships, not literal device screencaps.

Usage: python3 render_screens.py [out.png]
Default out: ../app/build/outputs/screenshots.png (git-ignored with build/).
"""

import os
import sys
import json
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import palette as P

HERE = os.path.dirname(os.path.abspath(__file__))
MAIN = os.path.join(HERE, "..", "app", "src", "main")
DRAWABLE = os.path.join(MAIN, "res", "drawable-nodpi")
MANIFEST = os.path.join(MAIN, "assets", "tamawatch", "assets_manifest.json")
W = 480  # round watch canvas

_man = json.load(open(MANIFEST))["sprites"]
_sheets = {}


def _sheet(sid):
    if sid not in _sheets:
        _sheets[sid] = Image.open(os.path.join(DRAWABLE, _man[sid]["file"])).convert("RGBA")
    return _sheets[sid]


def frame(sid, index=0, tag=None):
    """Slice one frame from a strip, exactly like SpriteBank does at runtime."""
    info = _man[sid]
    if tag and tag in info.get("tags", {}):
        index = info["tags"][tag][0]
    fw, fh = info["frameW"], info["frameH"]
    return _sheet(sid).crop((index * fw, 0, index * fw + fw, fh))


def stamp(base, im, cx, cy, size=None, sid_tag=None):
    """Paste an RGBA image centered at (cx,cy), optionally scaled to fit `size`
    (nearest-neighbor to keep the pixel look). Clips at the canvas edge."""
    if size:
        s = size / max(im.width, im.height)
        im = im.resize((max(1, round(im.width * s)), max(1, round(im.height * s))), Image.LANCZOS)
    base.paste(im, (round(cx - im.width / 2), round(cy - im.height / 2)), im)


def sprite(base, sid, cx, cy, size, tag=None, index=0):
    stamp(base, frame(sid, index, tag), cx, cy, size)


def pet_bottom(base, sid, cx, bottom_y, width, tag=None, index=0):
    """Place a creature by width with its feet at bottom_y — the frame's top
    headroom overflows upward (matching the app's bottom-anchored pet)."""
    im = frame(sid, index, tag)
    im = im.resize((width, max(1, round(im.height * width / im.width))), Image.LANCZOS)
    base.paste(im, (round(cx - width / 2), round(bottom_y - im.height)), im)


def ground(base, cx, feet_y, width, rug="rug_rose"):
    """A rug + soft contact shadow centered at the feet, so the pet is grounded."""
    if rug:
        stamp(base, frame(rug), cx, feet_y, width)
    stamp(base, frame("fx_shadow"), cx, feet_y, int(width * 0.86))


def _font(sz, bold=True):
    names = (["DejaVuSans-Bold.ttf", "DejaVuSans.ttf"] if bold else ["DejaVuSans.ttf"])
    roots = ["/usr/share/fonts/truetype/dejavu", "/usr/share/fonts/dejavu",
             "/usr/share/fonts/truetype/liberation"]
    for r in roots:
        for n in names:
            p = os.path.join(r, n)
            if os.path.exists(p):
                return ImageFont.truetype(p, sz)
    return ImageFont.load_default()


def text(d, cx, y, s, size=22, color=P.WHITE, center=True, shadow=True):
    f = _font(size)
    if center:
        bb = d.textbbox((0, 0), s, font=f)
        x = cx - (bb[2] - bb[0]) / 2
    else:
        x = cx
    if shadow:
        d.text((x + 1, y + 1), s, font=f, fill=(0, 0, 0, 180))
    d.text((x, y), s, font=f, fill=color)


# ------------------------------------------------------------------ UI fragments
def hearts(base, x, y, filled, size=26, gap=4):
    for i in range(4):
        sprite(base, "ui_heart_full" if i < filled else "ui_heart_empty",
               x + i * (size + gap) + size / 2, y + size / 2, size)
    return x + 4 * (size + gap)


def icon_meter(base, x, y, full, empty, filled, count=4, size=28, gap=5):
    """Minecraft-style: each point in the meter is its icon (colored=filled,
    dimmed=empty)."""
    for i in range(count):
        sprite(base, full if i < filled else empty, x + i * (size + gap) + size / 2, y + size / 2, size)


def meters(base, hunger=3, happy=4, gp=120, steps=3240):
    d = ImageDraw.Draw(base)
    size, gap = 30, 5
    row_w = 4 * (size + gap)
    x = (W - row_w) / 2
    y = 32
    icon_meter(base, x, y, "ic_hunger", "ic_hunger_empty", hunger, size=size, gap=gap)
    y2 = y + 40
    icon_meter(base, x, y2, "ic_mood", "ic_mood_empty", happy, size=size, gap=gap)
    # GP + steps badge
    by = y2 + 44
    sprite(base, "ui_gp_coin", W / 2 - 60, by, 24)
    text(d, W / 2 - 30, by - 10, str(gp), 22, center=False)
    sprite(base, "ui_step_shoe", W / 2 + 20, by, 22)
    text(d, W / 2 + 36, by - 10, str(steps), 22, center=False)


def care_ring(base, pressed=2, progress=0.55):
    """Five category hubs on a horseshoe; `pressed` shows a peek-in-progress with
    a partial gold confirm-ring (touch-first peek-hold menu)."""
    import math
    hubs = [("ic_settings", 216), ("ic_shop", 154), ("ic_feed", 90),
            ("ic_play", 26), ("ic_status", -36)]
    names = ["Settings", "Shop", "Care", "Play", "Stats"]
    r = 0.35 * W
    d = ImageDraw.Draw(base)
    for i, (ic, ang) in enumerate(hubs):
        a = math.radians(ang)
        cx, cy = W / 2 + r * math.cos(a), W / 2 + r * math.sin(a)
        sz = 56 if i == pressed else 42
        sprite(base, ic, cx, cy, sz)
        if i == pressed:
            m = sz / 2 + 5
            d.arc([cx - m, cy - m, cx + m, cy + m], -90, -90 + 360 * progress,
                  fill=(242, 193, 78, 255), width=6)
    return names[pressed]


def capsule(base, cx, cy, s):
    """The menu-name capsule that fades in at the center during a peek."""
    d = ImageDraw.Draw(base)
    f = _font(30)
    bb = d.textbbox((0, 0), s, font=f)
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    px, py = 26, 13
    x0, y0, x1, y1 = cx - tw / 2 - px, cy - th / 2 - py, cx + tw / 2 + px, cy + th / 2 + py
    rad = (y1 - y0) / 2
    sh = Image.new("RGBA", base.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle([x0, y0 + 7, x1, y1 + 7], radius=rad, fill=(0, 0, 0, 150))
    base.alpha_composite(sh.filter(ImageFilter.GaussianBlur(7)))
    d.rounded_rectangle([x0, y0, x1, y1], radius=rad, fill=(23, 26, 40, 255), outline=(255, 255, 255, 42), width=2)
    text(d, cx, y0 + py - bb[1], s, 30)


def panel(base, x0, y0, x1, y1, fill=(26, 28, 46, 232)):
    ov = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    ImageDraw.Draw(ov).rounded_rectangle([x0, y0, x1, y1], radius=26, fill=fill)
    base.alpha_composite(ov)


def bar(base, label, x, y, frac, tint):
    d = ImageDraw.Draw(base)
    text(d, x, y, label, 20, center=False, shadow=False)
    bx, bw, bh = x + 118, 150, 14
    d.rounded_rectangle([bx, y, bx + bw, y + bh], radius=7, fill=(255, 255, 255, 40))
    d.rounded_rectangle([bx, y, bx + int(bw * frac), y + bh], radius=7, fill=tint)


# ------------------------------------------------------------------ screens
def screen_bg(bg_id):
    img = Image.new("RGBA", (W, W), (0, 0, 0, 255))
    img.paste(frame(bg_id, 0), (0, 0))
    return img


def s_egg():
    img = screen_bg("bg_egg")
    ground(img, W / 2, 300, 140, rug="rug_cream")
    sprite(img, "spr_egg", W / 2, 232, 128, tag="wiggle")
    d = ImageDraw.Draw(img)
    text(d, W / 2, 326, "Tama's egg...", 22)
    cw, ch, cy0 = 156, 40, 356
    d.rounded_rectangle([(W - cw) / 2, cy0, (W + cw) / 2, cy0 + ch], radius=20,
                        fill=(64, 116, 86, 255), outline=(255, 255, 255, 70), width=2)
    text(d, W / 2, cy0 + 8, "Hatch now", 22)
    return img, "Egg (grounded, Cream rug)"


def s_home_day():
    img = screen_bg("bg_room_day")
    care_ring(img, pressed=2, progress=0.55)      # peeking the "Care" hub
    ground(img, W / 2, 300, 150, rug="rug_rose")
    pet_bottom(img, "spr_baby", W / 2, 302, 108, tag="idle")
    capsule(img, W / 2, W * 0.43, "Care")         # name fades in at center
    meters(img, hunger=3, happy=4)                # drawn last -> in front
    return img, "Home - peek-hold menu"


def s_sleeping():
    img = screen_bg("bg_room_night")
    ground(img, W / 2, 304, 150, rug="rug_sky")
    pet_bottom(img, "spr_baby", W / 2, 306, 112, tag="sleep")
    sprite(img, "ov_zzz", W / 2 + 70, 214, 40)
    meters(img, hunger=2, happy=3)
    return img, "Sleeping (Sky rug)"


def s_status():
    img = screen_bg("bg_room_day")
    panel(img, 74, 96, 406, 384)
    d = ImageDraw.Draw(img)
    text(d, W / 2, 108, "Status", 24)
    text(d, 118, 152, "Hunger", 20, center=False, shadow=False)
    icon_meter(img, 236, 146, "ic_hunger", "ic_hunger_empty", 3, size=26, gap=4)
    text(d, 118, 190, "Happy", 20, center=False, shadow=False)
    icon_meter(img, 236, 184, "ic_mood", "ic_mood_empty", 4, size=26, gap=4)
    bar(img, "Energy", 118, 236, 0.72, (70, 192, 230, 255))
    bar(img, "Bond", 118, 280, 0.55, (233, 111, 160, 255))
    bar(img, "Disc.", 118, 324, 0.40, (250, 220, 90, 255))
    return img, "Status screen"


def s_shop():
    img = screen_bg("bg_shop")
    panel(img, 70, 140, 410, 400)
    d = ImageDraw.Draw(img)
    rows = [("item_meal_fish", "Fish", 30), ("item_snack_cake", "Cake", 15),
            ("item_medicine", "Medicine", 40)]
    for i, (ic, name, price) in enumerate(rows):
        ry = 168 + i * 74
        sprite(img, ic, 118, ry + 22, 40)
        text(d, 154, ry + 10, name, 22, center=False, shadow=False)
        sprite(img, "ui_gp_coin", 322, ry + 22, 22)
        text(d, 340, ry + 10, str(price), 22, center=False, shadow=False)
    text(d, W / 2, 150, "Shop", 22)
    return img, "Shop"


SCREENS = [s_egg, s_home_day, s_sleeping, s_status, s_shop]


# ------------------------------------------------------------------ contact sheet
def roundify(img):
    mask = Image.new("L", (W, W), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, W - 1, W - 1], fill=255)
    out = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    ImageDraw.Draw(out).ellipse([2, 2, W - 3, W - 3], outline=(18, 18, 26, 255), width=8)
    return out


def contact_sheet(out_path):
    rendered = [(roundify(img), cap) for img, cap in (fn() for fn in SCREENS)]
    tile = 360
    cols, gap, cap_h, pad = 3, 28, 42, 32
    rows = (len(rendered) + cols - 1) // cols
    sw = pad * 2 + cols * tile + (cols - 1) * gap
    sh = pad * 2 + 56 + rows * (tile + cap_h) + (rows - 1) * gap
    sheet = Image.new("RGBA", (sw, sh), (14, 15, 22, 255))
    d = ImageDraw.Draw(sheet)
    text(d, sw / 2, pad, "TamaWatch — build preview", 34)
    for idx, (img, cap) in enumerate(rendered):
        r, c = divmod(idx, cols)
        x = pad + c * (tile + gap)
        y = pad + 56 + r * (tile + cap_h + gap)
        sheet.paste(img.resize((tile, tile), Image.LANCZOS), (x, y), img.resize((tile, tile), Image.LANCZOS))
        text(d, x + tile / 2, y + tile + 8, cap, 22)
    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    sheet.convert("RGB").save(out_path)
    return sheet.size


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(MAIN, "..", "build", "outputs", "screenshots.png")
    out = os.path.abspath(out)
    size = contact_sheet(out)
    print(f"Screenshots -> {out}  ({size[0]}x{size[1]}, {len(SCREENS)} screens)")


if __name__ == "__main__":
    main()
