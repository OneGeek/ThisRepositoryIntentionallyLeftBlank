# TamaWatch — Rendering Layers

Purpose: document how visual assets are **layered** in the two places layering happens — offline **asset generation** (Python) and on-watch **runtime composition** (Compose) — and the single seam that joins them. The companion visual is [`layer-atlas.html`](layer-atlas.html) (open in a browser; also published as the "TamaWatch Layer Atlas" artifact).

There are two independent layer stacks. Don't confuse them: generation flattens parts *into* a frame; runtime stacks *screens* on top of that frame.

---

## Phase ① — Asset generation (offline, Python)

`assets-src/` generates every pixel. `generate_assets.py` runs the art + sound generators, then **refuses to write** unless the output ids match the Authoritative Generation Set (`04-asset-validation.md`), so art and code never drift.

```
palette.py ──colors──▶ generate_art.py ─┐
                        generate_sound.py┴─▶ generate_assets.py ─┬─▶ res/drawable-nodpi/*.png   (frame-strip sheets)
                                              (validate + write)  ├─▶ assets/tamawatch/assets_manifest.json
                                                                  └─▶ res/raw/*.wav
```

### Inside `draw_creature` — the baked part order

One creature frame is composited **bottom → top**; each pass paints over the last. This is where a "part" concept exists today — but only at generation time.

| order | pass | notes |
|------:|------|-------|
| 1 | grounding shadow | flat translucent ellipse; shrinks as the body lifts |
| 2 | feet | planted; alternate lift on walk |
| 3 | feature | antenna / ears / horn / wings / halo (per species) |
| 4 | body + belly | the `shape` ellipse; species `body` color |
| 5 | arms | side dots or raised lines |
| 6 | eyes | open / blink(closed) / happy / x, per motion state |
| 7 | cheeks | optional (`cheeks` species flag) |
| 8 | mouth | smile / open / frown / grumpy line |

The pose is **baked in**: an idle frame and a blink frame are just different indices in the same strip. The full frame set (`frames` wide) with named `tags` (idle, idle2–4, happy, sick, sleep, call, eat…) is written to one PNG per creature, and the manifest records `frameW`, `frameH`, `frames`, and each tag's frame-index range.

What is **not** baked: the coat pattern and the annoyed face — those are added live in Phase ②.

---

## Phase ② — Runtime composition (on-watch, Compose)

`SpriteBank` slices frames out of the generated sheets on demand (`frame(id, i) → ImageBitmap`), using the manifest so no frame size is hard-coded. `HomeScreen` stacks eight layers back-to-front every frame; the pet's live state decides each layer's content.

### Home screen z-order (back → front)

| z | layer | source / driver |
|--:|-------|-----------------|
| 0 | background | `bg_room_day` / `_night` / `cos_bg_*` — from pet.asleep, lightOn, owned cosmetics |
| 1 | ground | `rug(rugId)` + poop |
| 2 | **pet** | `PetSprite` frame (`poseFor(pet)`) + coat (`SrcAtop`), through the modifier stack below |
| 3 | status overlays | sick skull · zzz · call (`TopEnd`) |
| 4 | **mood** | content heart · **or** the annoyed face (angry brows + 💢), gated by `annoyedUntilMs` |
| 5 | tap burst | heart pop on an effective pet |
| 6 | radial menu | hubs · capsule · submenu sheet |
| 7 | status meters (front) | hunger · happy · GP — pet sits at z2 so its head rises *behind* these |

### z2 · PetSprite modifier stack (outer → inner, all riding one bitmap)

```
drag-follow ▶ rotate(dragAngle) ▶ stretch(scaleX↑,scaleY↓) ▶ counter-rotate ▶ bounce ▶ coat (SrcAtop, saveLayer)
```

All operate on the **single flat frame** — so today body, face, hands and shadow deform together as one affine transform. (Per-part deformation would require Phase ① to stop flattening; see "Future: a runtime part rig" below.)

### What drives which layer

| ViewModel state | picks |
|-----------------|-------|
| `pet` (pose) | the sprite frame + which status overlays show |
| `settings.rugId` | z1 ground |
| `settings.coatId` | z2 coat pass |
| `lastPetMs` | z4 content heart (cooldown mood) |
| `annoyedUntilMs` | z4 annoyed face |

`DomainEvents` from the Repository never touch the screen — they drive the **non-visual** layer: `SoundBank` (chiptune SFX) + `Haptics` (wrist buzz).

---

## The seam

Phase ① writes exactly two things the runtime reads back — the **frame-strip PNGs** and the **manifest** that indexes them. `SpriteBank` is the only bridge across it:

- change the art and nothing in Phase ② moves;
- change the manifest's geometry and every sprite-drawing layer re-slices correctly, because no frame size is ever hard-coded.

Runtime overlays that need to land *on the pet's face* (the annoyed brows/vein) currently re-derive face geometry from the same normalized formula the generator uses (eyes at ~`0.627h`, ±`0.13w`). That works but is an **implicit** anchor system — the natural place to formalize it is the manifest (see below).

---

## The runtime part rig (implemented)

The interactive **idle** pose is no longer a single flat frame — it is composited from parts at runtime, so a drag can deform the body while merely *shifting* the face/arms/feature and sliding the shadow horizontally. This is the 2D-rig best practice: named slots with pivot **anchors**, a parent→child transform, and art (skin) kept separate from structure (rig) so accessories attach without editing baked art. Non-idle poses (happy/sick/sleep/call) keep their richer baked-frame animation and the whole-bitmap stretch.

**Generation** — `generate_parts.py` (run after `generate_assets.py`; also wired into `tools/build.sh --gen`) emits, per creature, the part PNGs at the neutral pose and their attach anchors, merged into a `parts` section of the manifest:

| role | id | runtime transform |
|------|----|-------------------|
| shadow | `spr_<c>_shadow` | horizontal translate only |
| feature | `spr_<c>_feature` | shift by `M(anchor)−anchor` |
| body | `spr_<c>_body` | the stretch `M` (+ coat, SrcAtop) + breath |
| arms | `spr_<c>_arms` | shift |
| face / faceBlink | `spr_<c>_face[_blink]` | shift; swap on blink |

It reuses draw_creature's exact neutral geometry, so a composited part stack equals the baked idle frame at rest (verified). Anchors: `feet` (stretch pivot), `face`, `feature`, `arms`, plus `headTop`/`handL`/`handR` for accessories.

**Runtime** — `SpriteBank.parts(id)` returns a `PartRig` (layer ids + anchors); `RiggedPet` (Screens.kt) composites the layers. The body carries the directional stretch `M = R(θ)·S·R(−θ)` about the feet; each shifting part is translated by `M(anchor) − anchor` with **no scale** ("keep shape, shift by the stretch amount"); the shadow takes horizontal translate only. `AngerOverlay` now reads its face position from the rig anchor instead of a hard-coded constant. `RigAttachment(anchor, content)` pins an accessory to a slot so it rides the stretch — the seam for hat/held-item customization.

**Tests** — `RigSnapshotTest` captures the rig under fixed drags (rest, right, up, diagonal+coat, +hat) so the per-part behavior is pinned without a device.

**Tradeoff** — the idle rig drives its own life (a slow breath + blink); the baked idle bob/sway is not reproduced on the interactive pose. Extending the rig to the other poses would mean moving their baked motion (blink, arm-up, squash) to per-part frames or runtime transforms.
