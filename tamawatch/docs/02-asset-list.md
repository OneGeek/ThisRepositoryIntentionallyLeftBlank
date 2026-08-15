# TamaWatch — Asset List (Art & Sound)

Derived from `01-game-design-doc.md`. This is the authoritative manifest the implementation and the asset generator must satisfy. Every row has a **stable ID** (used as the resource/file name) so the code, the generator, and the validation doc all agree.

## Conventions

- **Canvas:** round 480×480. The "stage" (pet play area) is a 240×240 logical grid scaled up; pixel-art sprites authored at low res (e.g. 48×48 or 64×64) and scaled with nearest-neighbor for the chunky Tamagotchi look.
- **Art format:** PNG-32 (RGBA), transparent background, palette-limited. Sprites are **frame sheets** (horizontal strips) — `name_wxh_Nf.png` where N = frame count. Naming in Android res is lowercase + underscores.
- **Sound format:** mono WAV, 16-bit PCM, 22 050 Hz (small, plenty for chiptune); the app may transcode to `.ogg` at build time but WAV is the source of truth. Each ≤ ~1.5 s unless noted.
- **Palette:** original 16-color "gotchi" palette (defined in `assets-src/palette.py`): ink, white, 2 grays, LCD green (2 shades), warm skin/cream (2), pink (2), red, blue (2), yellow, green, purple. Documented once, reused everywhere for cohesion.
- **Style:** faithful pixel-art homage — original characters. No copyrighted sprites.

---

## PART A — ART

### A1. Characters (life-stage sprites)

Each species has an idle animation (2 frames) at minimum; the **active pet on Home** also needs walk (2f), happy (2f), sad (1f), sick (1f), sleep (2f), eat (2f), and call/attention (1f) overlays. To keep v1 tractable, non-idle animation frames are **shared per stage silhouette** where reasonable and recolored/re-shaped per species only for idle+happy.

| ID | Stage | Species | Frames needed | Size |
|---|---|---|---|---|
| `spr_egg` | Egg | (color-tinted at runtime) | idle 2f, wiggle 2f, crack 3f | 64×64 |
| `spr_baby` | Baby | Babymon (universal) | idle 2, walk 2, happy 2, sad 1, sick 1, sleep 2, eat 2, call 1 | 48×48 |
| `spr_child_a` | Child | Kidmon-A (good care) | idle 2, walk 2, happy 2 (+ shares baby's sad/sick/sleep/eat via stage set) | 56×56 |
| `spr_child_b` | Child | Kidmon-B (avg) | idle 2, walk 2, happy 2 | 56×56 |
| `spr_child_c` | Child | Kidmon-C (neglect) | idle 2, walk 2, happy 2 | 56×56 |
| `spr_teen_a`..`spr_teen_e` | Teen | 5 Teenmon | each: idle 2, walk 2, happy 2 | 64×64 |
| `spr_adult_a`..`spr_adult_h` | Adult | 8 Adultmon | each: idle 2, walk 2, happy 2 | 64×64 |
| `spr_stageset_child` | Child | shared emotes (sad/sick/sleep/eat/call) | 5×(1–2f) | 56×56 |
| `spr_stageset_teen` | Teen | shared emotes | 5×(1–2f) | 64×64 |
| `spr_stageset_adult` | Adult | shared emotes | 5×(1–2f) | 64×64 |

**Species count:** 1 baby + 3 child + 5 teen + 8 adult = **17 species**, plus egg + 3 shared stage-emote sets. Evolution tree (Care Score branch → species) is defined in the implementation plan's data table.

### A2. Emote / status overlays (drawn on top of any pet)

| ID | Meaning | Frames | Size |
|---|---|---|---|
| `ov_poop` | Poop pile | 2 (fresh/settled) | 24×24 |
| `ov_sick_skull` | Sickness marker | 2 (blink) | 20×20 |
| `ov_zzz` | Sleeping | 2 | 24×24 |
| `ov_call` | Attention "!" bubble | 2 (blink) | 20×20 |
| `ov_heart_particle` | Pet/affection | 3 (rise/fade) | 16×16 |
| `ov_sweat` | Refuse/stress | 1 | 12×12 |
| `ov_note` | Music (mic talk / happy) | 2 | 16×16 |

### A3. Care-ring icons (the round menu)

12 icons, authored at 40×40, monochrome-on-transparent so they can tint (idle gray / highlighted white / alert red):

`ic_feed`, `ic_light`, `ic_play`, `ic_bathroom`, `ic_medicine`, `ic_status`, `ic_discipline`, `ic_shop`, `ic_steps`, `ic_settings`, `ic_back`, `ic_confirm`.

### A4. UI chrome & meters

| ID | Purpose | Size / notes |
|---|---|---|
| `ui_heart_full` / `ui_heart_empty` | Hunger/Happy hearts | 20×20 |
| `ui_bar_frame` / `ui_bar_fill` | Energy/Bond/Discipline bars | 9-patch-ish, drawn in code from these tiles |
| `ui_gp_coin` | Gotchi Point coin | 18×18, 2f spin |
| `ui_step_shoe` | Steps glyph | 18×18 |
| `ui_ring_bg` | Care-ring arc backdrop | vector/drawn in code |
| `ui_selector` | Highlighted-icon cursor | 44×44, 2f pulse |
| `ui_panel` | Menu/dialog panel (round-safe) | drawn in code + corner tile |
| `ui_toast` | Small confirmation chip | drawn in code |
| `ic_app_launcher` | Adaptive launcher icon (fg+bg) | 108×108 fg on 108×108 bg; + 48×48 legacy |
| `ic_notification` | Status-bar/notify small icon | 24×24 monochrome |
| `img_splash` | Boot/hatch splash | 480×480 |

### A5. Backgrounds / rooms

| ID | Use | Size |
|---|---|---|
| `bg_room_day` | Home room, lights on | 480×480 |
| `bg_room_night` | Home room, lights off/sleep (dim, stars) | 480×480 |
| `bg_egg` | Pre-hatch scene | 480×480 |
| `bg_shop` | Shop backdrop | 480×480 |
| `bg_game_jump` | Jump mini-game | 480×480 |
| `bg_game_guess` | Direction-guess mini-game | 480×480 |
| `bg_game_catch` | Catch mini-game | 480×480 |
| `bg_ambient` | Always-on/ambient (near-black, low-lit pet) | 480×480 |

### A6. Food / shop items (icons, 32×32)

Meals: `item_meal_bread`, `item_meal_bowl`, `item_meal_fish`, `item_meal_veg`.
Snacks: `item_snack_cake`, `item_snack_candy`, `item_snack_juice`, `item_snack_icecream`.
Care: `item_medicine`, `item_soap`.
Cosmetics: `cos_hat_party`, `cos_hat_crown`, `cos_bg_beach` (room reskin), `cos_bg_space`.

### A7. Mini-game props

| Game | Props |
|---|---|
| Jump | `game_obstacle` (24×24, 2f), reuse room floor |
| Guess | arrows `game_arrow_l`/`game_arrow_r` (32×32) |
| Catch | `game_basket` (40×24), `game_treat_good` (20×20), `game_treat_bad` (20×20) |

### A8. Cutscene / transition frames

| ID | Use | Frames |
|---|---|---|
| `cut_hatch` | Egg → baby | 4 |
| `cut_evolve` | Stage-up flash/star burst | 4 |
| `cut_farewell` | End of life | 3 |
| `fx_star` | Reusable sparkle | 3, 16×16 |
| `fx_goal` | Step-goal celebration | 3, 24×24 |

**Art total (distinct source PNGs):** ~ character sheets 20 + overlays 7 + ring icons 12 + UI 15 + backgrounds 8 + items 16 + game props 6 + cutscene/fx 5 = **≈ 89 source art files** (many are multi-frame sheets).

---

## PART B — SOUND

All original chiptune, mono 16-bit 22.05 kHz WAV.

### B1. UI

| ID | Description | Len |
|---|---|---|
| `sfx_select` | Menu move (bezel tick) | 0.08 s |
| `sfx_confirm` | Confirm/select | 0.15 s |
| `sfx_cancel` | Back/cancel | 0.15 s |
| `sfx_error` | Invalid / can't | 0.25 s |

### B2. Care

| ID | Description | Len |
|---|---|---|
| `sfx_eat` | Chew (meal) | 0.5 s |
| `sfx_drink` | Snack/juice | 0.4 s |
| `sfx_refuse` | Too full "no" | 0.4 s |
| `sfx_flush` | Bathroom clean | 0.6 s |
| `sfx_heal` | Medicine | 0.6 s |
| `sfx_pet` | Tap-to-pet chirp | 0.3 s |

### B3. Emotion / state

| ID | Description | Len |
|---|---|---|
| `sfx_happy` | Happy chirp | 0.5 s |
| `sfx_sad` | Sad blip | 0.4 s |
| `sfx_sick` | Ill wobble | 0.6 s |
| `sfx_sleep` | Yawn → snore looplet | 0.8 s |
| `sfx_call` | **Attention call jingle** (paired with haptic) | 0.8 s |
| `sfx_talk_react` | Mic "talk" happy reaction | 0.5 s |

### B4. Events

| ID | Description | Len |
|---|---|---|
| `sfx_hatch` | Hatch fanfare | 1.2 s |
| `sfx_evolve` | Evolution fanfare | 1.5 s |
| `sfx_goal` | Step-goal chime | 0.6 s |
| `sfx_coin` | Earn Gotchi Points | 0.3 s |
| `sfx_buy` | Shop purchase | 0.4 s |
| `sfx_farewell` | End-of-life theme | 1.5 s |
| `sfx_levelup` | Stage-up (non-evolve) ding | 0.5 s |

### B5. Mini-games

| ID | Description | Len |
|---|---|---|
| `sfx_game_start` | Round start | 0.4 s |
| `sfx_jump` | Hop | 0.2 s |
| `sfx_score` | Point scored | 0.2 s |
| `sfx_miss` | Missed / bump | 0.3 s |
| `sfx_game_win` | Round win | 0.8 s |
| `sfx_game_lose` | Round lose | 0.6 s |
| `mus_game_loop` | Optional tiny loop bed | 4 s (looping) |

**Sound total:** UI 4 + care 6 + emotion 6 + events 7 + game 7 = **30 sound files.**

### B6. Haptic patterns (not files — defined in code, listed for completeness)

`hap_tick` (bezel), `hap_confirm`, `hap_call` (double-buzz attention), `hap_evolve` (celebratory), `hap_error`. Enumerated so the impl plan and validation can map them.

---

## Asset ID Index (for validation cross-check)

- **Characters:** egg + baby + child_a/b/c + teen_a..e + adult_a..h + 3 stagesets = 20 groups
- **Overlays:** 7 · **Ring icons:** 12 · **UI:** 15 · **Backgrounds:** 8 · **Items:** 16 · **Game props:** 6 · **Cutscene/FX:** 5
- **Sound:** 30 · **Haptics:** 5 patterns

Every ID above must be either (a) produced by the asset generator, or (b) explicitly marked "drawn in code" (ui_ring_bg, ui_bar_*, ui_panel, ui_toast). The validation doc reconciles this index against the implementation plan's screen-by-screen usage.
