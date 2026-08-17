# TamaWatch — Game Design Document

**Version:** 1.0
**Target device:** Samsung Galaxy Watch Ultra (2025) — Wear OS 5 (Android 14), Exynos W1000, 1.5" circular AMOLED, 480×480 px, touch, haptics, step counter, microphone, heart-rate sensor.

> **Correction:** the Galaxy Watch Ultra has no rotating bezel, so navigation is **touch-first**. The care ring was replaced by a touch **peek-and-hold** menu: press-and-hold (or drag toward the center) a category hub to reveal its name and confirm; sub-actions are labeled lists. The "bezel / rotary" references below are superseded.
**Genre:** Virtual-pet / life-sim (real-time, always-on care loop).
**Reproduction target:** Tamagotchi Smart (2021) — chosen because it is itself a round-screen, wrist-worn, color-touchscreen virtual pet with a built-in pedometer, so its hardware and interaction model map 1:1 onto the Watch Ultra.
**IP note:** TamaWatch is an original homage. All characters, names, sprites, and sounds are original works created for this project. It reproduces the *mechanics and interaction model* of the Tamagotchi Smart, not its copyrighted assets or trademarks.

---

## 1. Design Pillars

1. **Glanceable care.** A single look at the watch tells you how your pet is doing. Deep menus are optional; the home screen is the game.
2. **Real time is the clock.** The pet lives on the wall clock. Stats decay whether the app is open or not. Bedtime is your bedtime.
3. **The watch is the toy.** Every sensor earns its place: steps feed the pet, the bezel is the menu dial, haptics are the pet tapping you on the wrist, the mic lets you "talk" to it.
4. **Faithful, not fiddly.** Reproduce the Tamagotchi Smart care loop — feed, play, clean, heal, discipline, sleep, evolve, generations — tuned for glance-length sessions (5–20 s), not lean-in play.
5. **It survives a reboot.** State is durable. Close the app, reboot the watch, come back tomorrow — the pet is exactly where the wall clock says it should be.

---

## 2. Core Fantasy & Loop

You hatch an egg on your wrist. A creature (a *Tamamon*) lives there. You keep it fed, happy, clean, and healthy across real days. How well you care for it — plus how much you walk — determines what it evolves into. A life ends; the next generation hatches. You are trying to raise a well-cared-for adult and discover the rarer evolutions.

**Second-to-second (glance):** look → is a care icon flashing? → tap to resolve → done.
**Minute-to-minute (session):** open → feed / play a mini-game / clean / shop → close.
**Hour-to-hour (ambient):** stats decay; the pet gets hungry/bored; poop appears; it may get sick; attention calls fire a haptic + notification.
**Day-to-day:** the pet sleeps at night, ages one life stage on schedule, banks Gotchi Points from steps, and eventually evolves.
**Generation-to-generation (days):** the adult reaches end of life → an egg from the next generation hatches, carrying a small legacy bonus.

---

## 3. Life Stages & Evolution

Real-time-gated stages (durations are tuned; see §11):

| Stage | Name | Real-time in stage | Notes |
|---|---|---|---|
| 0 | **Egg** | 0–3 min after hatch | Wiggles, then hatches automatically. |
| 1 | **Baby** (Babymon) | ~10 min | High needs, frequent calls, cannot play games. Universal sprite. |
| 2 | **Child** (Kidmon) | ~1 real day | Learns to play games. First branch by care. |
| 3 | **Teen** (Teenmon) | ~2 real days | Branches again; personality emerges. |
| 4 | **Adult** (Adultmon) | ~3–5 real days | Final form; branch determined by cumulative care + steps. |
| 5 | **Elder / Farewell** | end of adult span | Pet departs; next generation egg appears. |

**Evolution branching.** At each transition the game computes a **Care Score** since the last stage:

```
CareScore = w_neglect * (missed attention calls)      // lower is better
          + w_health  * (times sick / weight extremes)
          + w_disc    * (discipline mistakes)
          + w_play     * (games played, positive)
          + w_steps    * (avg daily steps, positive)
```

The score maps to a branch (e.g. Good / Average / Neglected), and each branch points at a different next-stage species. This reproduces the Tamagotchi "care mistakes shape evolution" rule. There are **3 child species, 5 teen species, and 8 adult species** in v1 (a compact but branching tree; see the sprite matrix in the asset list).

**Legacy.** When the next generation hatches it inherits a small starting bonus (a few Gotchi Points and a slightly slower first-day hunger decay) scaled by how well the previous pet was raised. This gives long-term progression.

---

## 4. Stats Model

All stats are `0..MAX` and decay in real time via a background tick (see §10). Displayed as heart rows / bars.

| Stat | Range | Meaning | Restored by | Decays via |
|---|---|---|---|---|
| **Hunger** | 0–4 hearts | Fullness (4 = full) | Feeding a Meal | Time; faster when awake/active |
| **Happy** | 0–4 hearts | Mood | Feeding Snacks, mini-games, petting | Time; faster when bored |
| **Energy** | 0–100 | Stamina | Sleep | Activity, games, being awake |
| **Hygiene** | clean / dirty | Poop on screen | Cleaning (flush) | Random poop events on a timer |
| **Health** | healthy / sick | Illness (skull icon) | Medicine | Neglect (empty hearts), random |
| **Weight** | grams (g) | Body weight | — | +by snacks/meals, −by games/steps |
| **Discipline** | 0–100 | Behavior | Scold on false calls | — (only rises) |
| **Bond** | 0–100 | Affection | Petting, correct care, mic "talk", HR presence | Slow decay on neglect |
| **Age** | days | Lifetime | — | Wall clock |

**Derived / hidden:** *Care Score* (drives evolution), *Attention flag* (any need critical → the pet "calls").

**Fail states:** If Hunger **and** Happy sit at 0 for too long, or Health stays sick untreated, the pet's **life meter** drains; sustained neglect ends the life early (a "care miss" death → next generation). This mirrors the Tamagotchi consequence model without being punishing over a single missed glance.

---

## 5. Screens & Navigation

Everything is built for a **round 480×480** canvas. Primary navigation is the **rotating bezel** (menu dial) with touch as an equal alternative; swipe left/right pages, swipe down = system, long-press pet = pet/affection.

### 5.1 Home (the pet's room)
- The pet sprite animates in a room scene (idle, walk, blink, emote).
- Poop, sickness skull, sleep Z's, and "call" exclamation render here contextually.
- **Care ring:** an arc of action icons around the round edge — Feed, Light, Play, Bathroom, Medicine, Status, Discipline, Shop — reproducing the classic Tamagotchi icon row, bent around the circular display. Rotate bezel to highlight, tap/press to select. The highlighted icon shows a label.
- Top curve: tiny clock + battery-style Gotchi Point counter + today's steps.
- Tap the pet: pet it (small Happy/Bond bump, heart particle, chirp).

### 5.2 Feed menu
- Two tabs: **Meal** (reduces Hunger deficit, +weight) and **Snack** (+Happy, ++weight). Scrollable item list from inventory. Eating plays a chew animation + sound; refusing when full shakes "no".

### 5.3 Play (mini-games)
Three games in v1, each a ~15–30 s glanceable round that awards Gotchi Points and Happy, and burns Energy/Weight:
1. **Jump** — tap to hop the pet over obstacles (timing).
2. **Direction Guess** — the pet turns left/right; swipe the way it *won't* (the classic Tamagotchi game), best of 5.
3. **Catch** — falling treats; tilt/tap to move a basket, catch good, dodge bad.

### 5.4 Bathroom
- If poop present: tap flush → cleaning animation + flush sound → Hygiene restored, small Bond bump.

### 5.5 Medicine
- If sick (skull): tap syringe → heal animation; may take 1–2 doses. Otherwise "healthy" wobble.

### 5.6 Status / Health book
- Full read-out: name, species, stage, age, weight, all stat bars, discipline, bond, generation #, care log (recent misses).

### 5.7 Discipline
- Only meaningful when the pet is "calling for no reason" (false attention call). Scold to raise Discipline; scolding when a real need exists lowers Bond (a care mistake).

### 5.8 Shop (Gotchi Points)
- Buy meals, snacks, medicine refills, and **cosmetics** (hats/accessories, room backgrounds). Currency = Gotchi Points earned from steps + games.

### 5.9 Light (sleep)
- Toggles room light. At the pet's bedtime it wants the light off; leaving it on at night is a care mistake and drains Energy/Bond. Turning the light off during the day just dims.

### 5.10 Steps / Walk screen
- Reads the watch step counter. Shows today's steps, the Gotchi-Point conversion (e.g. per 100 steps), and a "walk streak". Steps also slim the pet and boost Happy — the pet cheers when you hit daily step goals.

### 5.11 Egg / Hatch & Generation transition
- Egg wiggle → crack → hatch cutscene. On end-of-life: a gentle farewell cutscene → new egg with legacy summary ("Generation 3 — raised a Well-Cared Adultmon").

### 5.12 Onboarding
- First launch: name the pet, pick an egg color, grant sensor/notification permissions, quick 3-card tutorial (bezel = menu, tap pet = pet, red icon = needs you).

---

## 6. Sensor & Platform Integration (why it's a *watch* game)

| Watch capability | Game use |
|---|---|
| **Step counter** (`TYPE_STEP_COUNTER`) | Earns Gotchi Points, slims weight, boosts Happy, powers daily goals & streaks. The signature Smart feature. |
| **Rotating bezel / crown** (`RotaryScrollEvent`) | Primary menu dial around the care ring; scroll lists in shop/feed. |
| **Touch** | Tap icons, tap-to-pet, swipe pages, mini-game input. |
| **Haptics** (`Vibrator`) | Attention "wrist tap" patterns, feed/clean confirmations, game feedback, evolution fanfare. |
| **Microphone** (optional, permissioned) | "Talk to your Tama" — amplitude-triggered happy reaction & Bond bump. Pure on-device amplitude, no recording stored. |
| **Heart-rate** (optional) | Passive "presence"/bond trickle while worn; a calm-HR moment can soothe a stressed pet. Stretch. |
| **Ambient / Always-on display** | Low-power sleeping-pet or clock face; wakes to full color on tilt. |
| **Tile** | Quick-glance care status + one-tap Feed, without opening the app. |
| **Complication** | Hunger/Happy hearts on a watch face; tap opens the app. |
| **Ongoing notification** | "Your Tama needs you!" when a need goes critical while the app is closed. |
| **WorkManager background tick** | Advances stats/age on the wall clock so care is real even when the app is closed. |

---

## 7. Time, Sleep & Offline Catch-up

- The simulation is **timestamp-based**, not frame-based. Every stat stores `lastUpdated`. On any wake (app open, tick, tile refresh) the engine computes elapsed real time and integrates decay forward — so closing the app for 6 hours does the right thing.
- **Sleep window:** default 22:00–08:00 (editable, and biased toward the wearer's own inactivity). While asleep the pet regenerates Energy, decays Hunger/Happy slowly, does not poop, and cannot be disturbed except by leaving the light on (a care mistake).
- **Catch-up cap:** offline decay is capped so a weekend away doesn't instantly kill the pet, but neglect still registers for Care Score. This keeps the game humane on a device you take off at night.

---

## 8. Economy

- **Gotchi Points (GP):** earned from steps (e.g. 1 GP / 20 steps, daily-goal bonus), mini-games (per score), and daily login. Spent in Shop on food, medicine, and cosmetics.
- **Inventory:** meals, snacks, medicine are consumable stacks; cosmetics are permanent unlocks.
- **Balance goal:** a normally active wearer (~6–8k steps) plus a couple of game rounds earns enough GP/day to comfortably feed and occasionally treat the pet, with cosmetics as aspirational sinks.

---

## 9. Audio Design

8-bit / chiptune homage palette, all original. Short, quiet, watch-appropriate; every SFX ≤ ~1.5 s, respects the ringer/DND and a per-app **Sound** toggle. Categories: UI (select/confirm/cancel), care (eat, drink, flush, heal, refuse), emotion (happy chirp, sad, sick, sleep, attention-call jingle), events (hatch fanfare, evolution fanfare, level/goal chime, farewell theme), and a couple of tiny mini-game loops/stings. Full list in the asset doc.

## 10. Accessibility & Comfort

- Content-descriptions on every actionable icon (TalkBack).
- Large-target mode; color-blind-safe stat cues use **icon + shape**, not color alone (e.g. skull for sick, not just a red tint).
- Reduce-motion setting trims animations to a single frame + fade.
- Battery-aware: ambient sleeping-pet, throttled tick, no continuous sensors; mic/HR strictly opt-in.
- Nothing time-critical is punitive within a single sleep window (see catch-up cap).

---

## 11. Tuning Constants (initial, all data-driven)

These live in one config object so they can be balanced without code changes.

```
HUNGER_DECAY      = 1 heart / 3 h awake
HAPPY_DECAY       = 1 heart / 2.5 h awake
ENERGY_DECAY      = ~100 / 16 h awake ; regen ~100 / 8 h asleep
POOP_INTERVAL     = every 2–5 h awake (randomized)
SICK_CHANCE       = base 2%/tick, ×4 if any heart empty or dirty
ATTENTION_TIMEOUT = 60 min ignored call = 1 care miss
STEP_TO_GP        = 1 GP / 20 steps ; daily goal 6000 → +200 GP bonus
STAGE_DURATIONS   = Egg 3m, Baby 10m, Child 1d, Teen 2d, Adult 3–5d
OFFLINE_CAP       = decay integrated up to max 12 h per gap
TICK_INTERVAL     = 15 min (WorkManager) ; foreground 1 s sim step
```

## 12. Persistence

- **Room** database for the pet (single active pet + generation history) and inventory; **DataStore** for settings (sound, sleep window, reduce-motion, permissions granted). All writes are transactional so a mid-write crash can't corrupt the save. Every meaningful mutation stamps `lastUpdated`.

## 13. Out of Scope for v1 (roadmap)

TamaSma-style downloadable content packs; online friend visits / Tamaverse; multiplayer marriage between two watches; cloud backup; extended mini-game roster; costume layering. The architecture leaves seams for these (content packs are data, evolution tree is data-driven, save schema is versioned).

## 14. Success Criteria for v1

- Installs and runs on a Galaxy Watch Ultra (Wear OS 5) and the Wear OS emulator (round, 480×480).
- Full care loop playable: hatch → care → decay over real time → evolve at least Baby→Child→Teen with branching → generation rollover.
- Steps drive GP; at least the three mini-games and the shop function.
- Survives app close, device reboot, and 12 h offline with correct catch-up.
- Tile + ongoing attention notification + ambient mode work.
- All art and audio are the project's own generated assets.
