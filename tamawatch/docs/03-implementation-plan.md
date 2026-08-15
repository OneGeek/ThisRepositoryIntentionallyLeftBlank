# TamaWatch — Implementation Plan

Implements `01-game-design-doc.md` using the assets in `02-asset-list.md`. Target: an installable Wear OS APK for Galaxy Watch Ultra (Wear OS 5 / API 34, min API 33 for modern Wear OS).

---

## 1. Tech Stack & Build

| Concern | Choice | Why |
|---|---|---|
| Language | **Kotlin** | Standard for Wear OS. |
| UI | **Jetpack Compose for Wear OS** (`androidx.wear.compose`) | Round-aware components, rotary input, ambient support, least boilerplate. |
| Build | **Gradle (Kotlin DSL)** + Android Gradle Plugin | Standard; `./gradlew assembleDebug` → APK. |
| Min / Target SDK | min 33 / target 34 (compile 34) | Wear OS 5 is API 34; 33 covers the modern Wear OS install base. |
| Persistence | **Room** (pet + inventory + history) and **DataStore** (settings) | Durable, transactional, versioned schema. |
| Background | **WorkManager** periodic tick + **AlarmManager**/expedited work for attention calls | Reliable wall-clock advancement. |
| Sensors | `SensorManager` (`TYPE_STEP_COUNTER`), `RotaryInput`, `Vibrator`/`VibratorManager`, `AudioRecord` (mic amplitude, opt-in) | Native watch integration. |
| Surfaces | Wear **Tile** (`androidx.wear.tiles`), **Complication** (`androidx.wear.watchface.complications`), **Ongoing Notification** | Glanceable off-app care. |
| Ambient | `AmbientLifecycleObserver` | Always-on low-power pet/clock. |
| Audio | `SoundPool` for SFX (WAV/OGG in `res/raw`) | Low-latency, tiny clips. |
| Rendering | Compose `Canvas` + `drawImage` on nearest-neighbor-scaled bitmaps; frame-strip sprite sheets sliced at load | Chunky pixel-art look, cheap. |
| DI | Lightweight manual DI (a `AppContainer`) | Avoids Hilt weight for a small app. |

**Project layout**
```
tamawatch/
  settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/wrapper/…, gradlew, gradlew.bat
  app/
    build.gradle.kts
    proguard-rules.pro
    src/main/AndroidManifest.xml
    src/main/res/{drawable-nodpi, raw, mipmap-*, values, xml}
    src/main/assets/tamawatch/{sprites, sounds}   # generated assets copied here + res
    src/main/java/com/tamawatch/…
    src/test/…            # JVM unit tests for the engine (no device needed)
```

---

## 2. Package / Module Structure (`com.tamawatch`)

```
com.tamawatch
├─ TamaApp.kt                     # Application, AppContainer wiring
├─ core/
│   ├─ model/                     # Pet, Stats, Stage, Species, Item, Inventory, Settings, SaveState
│   ├─ engine/
│   │   ├─ CareEngine.kt          # timestamp-based decay integrator, actions (feed/clean/heal/scold/pet)
│   │   ├─ EvolutionEngine.kt     # CareScore → branch → next species (data-driven table)
│   │   ├─ EconomyEngine.kt       # steps→GP, game rewards, shop transactions
│   │   ├─ SleepClock.kt          # bedtime window, offline catch-up cap
│   │   └─ Tuning.kt              # all §11 constants in one object
│   └─ data/
│       ├─ PetDao/InventoryDao + TamaDatabase (Room)
│       ├─ SettingsStore (DataStore)
│       └─ Repository.kt          # single source of truth; stamps lastUpdated
├─ sensor/
│   ├─ StepSource.kt              # TYPE_STEP_COUNTER → daily delta
│   ├─ Haptics.kt                 # hap_* patterns
│   └─ MicPresence.kt             # opt-in amplitude only
├─ audio/SoundBank.kt             # SoundPool, sfx_* map, sound toggle
├─ assets/SpriteBank.kt           # loads frame-strip sheets, slices frames, nearest-neighbor scale
├─ background/
│   ├─ TickWorker.kt              # WorkManager 15-min tick → engine.advance(now)
│   ├─ AttentionNotifier.kt       # ongoing "needs you" notification
│   └─ BootReceiver.kt            # reschedule after reboot
├─ ui/
│   ├─ TamaActivity.kt            # single-activity, Compose host, rotary + ambient
│   ├─ nav/                       # screen state machine (Home ↔ menus ↔ games)
│   ├─ home/HomeScreen.kt         # room + pet + care ring
│   ├─ carering/CareRing.kt       # round menu, bezel-driven
│   ├─ feed/FeedScreen.kt
│   ├─ play/{PlayMenu, JumpGame, GuessGame, CatchGame}.kt
│   ├─ status/StatusScreen.kt
│   ├─ shop/ShopScreen.kt
│   ├─ steps/StepsScreen.kt
│   ├─ onboarding/{Onboarding, NamePet, HatchScene}.kt
│   ├─ cutscene/{EvolveScene, FarewellScene}.kt
│   └─ common/{PixelSprite, HeartRow, StatBar, RoundPanel, Toast}.kt
├─ tile/CareTileService.kt        # quick status + Feed action
└─ complication/CareComplicationService.kt
```

---

## 3. Simulation Core (device-independent, fully unit-testable)

The engine is pure Kotlin with **no Android imports**, so it runs in `src/test` on the JVM (works even without an Android SDK — our CI/local check).

- `CareEngine.advance(state, nowMillis)`: integrates Hunger/Happy/Energy decay from `state.lastUpdated` to `now`, applies sleep regen inside the sleep window, rolls poop/sick events per elapsed ticks (seeded RNG for testability), clamps offline gap to `OFFLINE_CAP`, updates Age, and records care-misses when attention calls time out. Returns a new immutable state + a list of `DomainEvent`s (Pooped, GotSick, Called, Aged, Died…).
- Actions: `feed(meal|snack)`, `clean()`, `heal()`, `scold()`, `pet()`, `playResult(game, score)`, `buy(item)`, `toggleLight()`, `applySteps(delta)`. Each returns new state + events; UI/audio/haptics react to events.
- `EvolutionEngine.maybeEvolve(state)`: when a stage duration elapses, compute CareScore over the stage, pick branch, resolve species from the **Evolution Table** (below), emit `Evolved(species)`.

### Evolution Table (data — maps 1:1 to asset IDs)

```
Egg ─(3m)→ Baby(spr_baby)
Baby ─(10m, branch by CareScore)→
    Good/Avg  → Kidmon-A (spr_child_a)
    Low        → Kidmon-B (spr_child_b)
    Neglect    → Kidmon-C (spr_child_c)
Child ─(1d, branch)→ one of Teen A..E (spr_teen_a..e)
    A→{teen_a|teen_b}, B→{teen_b|teen_c|teen_d}, C→{teen_d|teen_e} by score
Teen ─(2d, branch)→ one of Adult A..H (spr_adult_a..h)
    weighted by teen species + cumulative CareScore + avg steps
Adult ─(3–5d)→ Farewell → next-gen Egg (+legacy bonus)
```
Every species token here (`spr_baby`, `spr_child_*`, `spr_teen_*`, `spr_adult_*`) is a required asset from the asset list — this is the join the validation doc checks.

---

## 4. Rendering & Assets Loading

- Generated PNG frame-strips ship in `res/drawable-nodpi/` (pixel art must not be dpi-scaled) referenced by their asset IDs; sounds in `res/raw/`.
- `SpriteBank` loads a strip once, slices into `Bitmap` frames by declared frame width, caches, and exposes `frame(id, index)`. `PixelSprite` composable draws the current frame with `FilterQuality.None` scaled to the round stage.
- Backgrounds are full 480×480 PNGs; ambient uses `bg_ambient`.
- A generated `assets_manifest.json` (frame counts + sizes per ID) is emitted by the generator and read by `SpriteBank` so frame slicing never hard-codes numbers — keeps art and code in lockstep.

---

## 5. Screen-by-Screen Build Order (and assets each needs)

1. **Skeleton + engine + tests** — model, CareEngine, EvolutionEngine, Tuning, Repository, Room/DataStore, JVM unit tests. *(no art)*
2. **App shell** — TamaActivity, nav state machine, SpriteBank/SoundBank, permissions. *(app icon `ic_app_launcher`, `img_splash`)*
3. **Home + Care Ring** — `bg_room_day/night`, active pet sprite set, `ov_*` overlays, ring icons `ic_feed/light/play/bathroom/medicine/status/discipline/shop/steps/settings`, `ui_selector`, hearts/bars, `ui_gp_coin`, `ui_step_shoe`, `sfx_select/confirm/cancel`, `hap_tick`.
4. **Care actions** — Feed (`bg`? no; item icons `item_meal_*`/`item_snack_*`, `sfx_eat/drink/refuse`, eat frames), Bathroom (`ov_poop`, `sfx_flush`), Medicine (`ov_sick_skull`, `sfx_heal`), Pet (`ov_heart_particle`, `sfx_pet`), Discipline (`sfx_confirm/error`).
5. **Steps + Economy + Shop** — StepSource, `ic_steps`/`ui_step_shoe`, `fx_goal`/`sfx_goal`, `sfx_coin`; Shop `bg_shop`, item icons + cosmetics `cos_*`, `sfx_buy`.
6. **Mini-games** — Jump (`bg_game_jump`, `game_obstacle`, `sfx_jump/score/miss`), Guess (`bg_game_guess`, `game_arrow_l/r`), Catch (`bg_game_catch`, `game_basket`, `game_treat_good/bad`), shared `sfx_game_start/win/lose`, optional `mus_game_loop`.
7. **Lifecycle scenes** — onboarding + name + egg (`bg_egg`, `spr_egg`, `sfx_hatch`, `cut_hatch`), evolve (`cut_evolve`, `fx_star`, `sfx_evolve`/`sfx_levelup`, `hap_evolve`), farewell (`cut_farewell`, `sfx_farewell`).
8. **Background/off-app** — TickWorker, AttentionNotifier (`ic_notification`, `sfx_call`, `hap_call`), BootReceiver, Tile (`CareTileService`), Complication.
9. **Ambient + accessibility + settings** — `bg_ambient`, reduce-motion, sound toggle, TalkBack descriptions, sleep-window editor.
10. **Polish + package** — proguard, launcher/tile icons check, `assembleDebug`/`assembleRelease`.

---

## 6. Background & Off-App Behavior

- `TickWorker` (periodic 15 min, plus run-on-open) calls `engine.advance(now)`, persists, and if a need went critical, posts/updates the **ongoing attention notification** and fires `hap_call`. Coalesces so it never spams.
- `BootReceiver` reschedules the worker after reboot (state is timestamp-based, so a missed window is caught up on next advance).
- **Tile** shows Hunger/Happy hearts + GP + a Feed button (feeds the cheapest meal, advances state).
- **Complication** exposes Hunger as `RANGED_VALUE` + icon; tap deep-links Home.

---

## 7. Persistence & Save Schema

- Room entities: `PetEntity` (id, name, species, stage, stats…, weight, discipline, bond, careScore accumulators, generation, bornAt, lastUpdated), `InventoryEntity`, `HistoryEntity` (past generations). Schema `version = 1`, exported schema JSON checked in; migrations planned for roadmap features.
- All engine outputs written in a single transaction; `lastUpdated` stamped every write.
- Corruption guard: writes are atomic; on load failure the app offers a fresh egg rather than crashing.

---

## 8. Testing Strategy (runs without Android SDK)

- **JVM unit tests** (`src/test`, JUnit) on the pure engine: decay math, sleep regen, offline-cap catch-up, poop/sick RNG with fixed seed, CareScore→branch mapping for all species, economy math, save round-trip (in-memory).
- These are runnable here with Gradle *if* AGP is skipped — so the engine is also mirrored as a plain Kotlin/Gradle `:engine-jvm` check where practical, or verified via a standalone Kotlin script. (Device/instrumented tests are documented but require the SDK.)

---

## 9. Packaging (the deliverable "package")

- `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` (installable via `adb install` or Play Console internal testing).
- `./gradlew :app:assembleRelease` with a signing config → signed APK/AAB for sideload / Play.
- A **Gradle wrapper** is committed so no local Gradle is needed; **Android SDK is the only external prerequisite** (documented in README with the exact `sdkmanager` package list: `platforms;android-34`, `build-tools;34.0.0`, `platform-tools`).
- **Constraint in this environment:** the Android SDK host is blocked by egress policy, so the APK is built by the user/CI, not in-session. Everything needed to build is committed. A **browser playable preview** (`tamawatch/preview/index.html`) reproduces the core loop for immediate hands-on evaluation and to prove out game feel + assets before a device build.

---

## 10. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| No SDK in-session | Ship complete buildable project + wrapper + docs; verify engine via JVM tests; web preview for game feel. |
| Step counter differs per device/reboot | Track deltas against a stored baseline; reset baseline on sensor discontinuity. |
| Background limits on Wear OS | 15-min WorkManager (respects Doze); timestamp catch-up covers skipped ticks. |
| Battery | Ambient mode, throttled tick, opt-in mic/HR, SoundPool tiny clips. |
| Asset/code drift | `assets_manifest.json` single source for frame counts; validation doc reconciles IDs. |
```
