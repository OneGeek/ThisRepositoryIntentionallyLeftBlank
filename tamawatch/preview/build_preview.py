"""Builds a single self-contained tamawatch/preview/index.html that embeds the
generated manifest, sprite PNGs and SFX as data URIs plus game.js, so the
preview plays offline in any browser (and as a shareable artifact)."""

import base64
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
MAIN = os.path.join(HERE, "..", "app", "src", "main")
DRAWABLE = os.path.join(MAIN, "res", "drawable-nodpi")
RAW = os.path.join(MAIN, "res", "raw")
MANIFEST = os.path.join(MAIN, "assets", "tamawatch", "assets_manifest.json")


def datauri(path, mime):
    with open(path, "rb") as f:
        return f"data:{mime};base64," + base64.b64encode(f.read()).decode()


def main():
    manifest = json.load(open(MANIFEST))
    sprites = {}
    for sid, info in manifest["sprites"].items():
        p = os.path.join(DRAWABLE, info["file"])
        if os.path.exists(p):
            sprites[sid] = datauri(p, "image/png")
    sounds = {}
    for name in os.listdir(RAW):
        if name.endswith(".wav"):
            sounds[name[:-4]] = datauri(os.path.join(RAW, name), "audio/wav")

    game_js = open(os.path.join(HERE, "game.js")).read()

    html = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>TamaWatch</title>
<style>
  :root{color-scheme:dark;}
  html,body{margin:0;height:100%;background:#0b0d16;color:#dfe6f2;font-family:system-ui,sans-serif;}
  .wrap{min-height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:14px;padding:16px;box-sizing:border-box;}
  h1{font-size:18px;letter-spacing:2px;margin:0;font-weight:600;color:#aadc78;}
  .bezel{width:min(88vw,480px);aspect-ratio:1;border-radius:50%;
    background:linear-gradient(145deg,#3a3f52,#14161f);padding:14px;
    box-shadow:0 18px 50px rgba(0,0,0,.6), inset 0 2px 6px rgba(255,255,255,.08);position:relative;}
  .bezel::after{content:"";position:absolute;inset:14px;border-radius:50%;box-shadow:inset 0 0 0 3px rgba(0,0,0,.5);pointer-events:none;}
  canvas{width:100%;height:100%;border-radius:50%;display:block;image-rendering:auto;background:#111;cursor:pointer;}
  .hint{font-size:12px;color:#8b93a7;max-width:480px;text-align:center;line-height:1.5;}
  .bar{display:flex;gap:8px;align-items:center;}
  button{background:#232838;color:#dfe6f2;border:1px solid #3a4055;border-radius:20px;padding:6px 14px;font-size:13px;cursor:pointer;}
  button:hover{background:#2d3345;}
  kbd{background:#232838;border:1px solid #3a4055;border-radius:4px;padding:1px 5px;font-size:11px;}
</style></head>
<body><div class="wrap">
  <h1>TAMAWATCH</h1>
  <div class="bezel"><canvas id="screen" width="480" height="480"></canvas></div>
  <div class="bar">
    <span class="hint">Click the <b>center</b> to do the highlighted action · click a <b>ring icon</b> to use it · scroll / <kbd>←</kbd><kbd>→</kbd> to spin the dial</span>
    <button id="mute">🔊</button>
  </div>
  <div class="hint">Time is accelerated for the demo — the egg hatches in seconds and evolves through Baby → Child → Teen → Adult in a couple of minutes. Care well (feed, clean, heal, play, walk) to reach the rarer forms.</div>
</div>
<script>
const MANIFEST=__MANIFEST__;
const SPRITES=__SPRITES__;
const SOUNDS=__SOUNDS__;
</script>
<script>
__GAME__
</script>
</body></html>
"""
    html = (html
            .replace("__MANIFEST__", json.dumps(manifest))
            .replace("__SPRITES__", json.dumps(sprites))
            .replace("__SOUNDS__", json.dumps(sounds))
            .replace("__GAME__", game_js))

    out = os.path.join(HERE, "index.html")
    with open(out, "w") as f:
        f.write(html)
    size = os.path.getsize(out) / 1024
    print(f"wrote {out}  ({size:.0f} KB)  sprites={len(sprites)} sounds={len(sounds)}")


if __name__ == "__main__":
    main()
