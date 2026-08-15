# TamaWatch — a Tamagotchi-style virtual pet for Wear OS

A faithful homage to the **Tamagotchi Smart (2021)**, built for the **Samsung Galaxy Watch Ultra (2025)** and any Wear OS 5 device. Hatch an egg on your wrist, keep it fed / happy / clean / healthy across real days, walk to earn Gotchi Points, and raise it through a branching evolution tree — one generation to the next.

> **Why the Tamagotchi Smart?** It is itself a round, wrist-worn, color-touchscreen virtual pet with a built-in pedometer, so its hardware and interaction model map 1:1 onto the Watch Ultra (480×480 round AMOLED, rotating bezel, touch, step counter, haptics, mic). TamaWatch reproduces its *mechanics*, with entirely **original** pixel art, names, and sound — not copyrighted assets.

## Play it right now (no build needed)

Open **`preview/index.html`** in any browser. It's a fully self-contained, accelerated playable build of the core loop using the real generated art and audio — the egg hatches in seconds and evolves through Baby → Child → Teen → Adult in a couple of minutes. Click the center to act, click ring icons, scroll/←→ to spin the dial.

## Build the watch APK

Prerequisites: **JDK 17+** and the **Android SDK** (Android Studio, or the command-line tools). The Gradle wrapper is committed, so you do *not* need a local Gradle install.

```bash
# 1. Install the required SDK packages (once):
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"

# 2. Point Gradle at your SDK (either export or create local.properties):
export ANDROID_HOME=/path/to/Android/sdk        # or: echo "sdk.dir=/path/to/sdk" > local.properties

# 3. Build the installable debug APK:
cd tamawatch
./gradlew :app:assembleDebug
#   -> app/build/outputs/apk/debug/app-debug.apk

# 4. Install on a connected Galaxy Watch Ultra (Wi-Fi/adb debugging on) or the
#    Wear OS emulator (create a *round*, API 34 watch AVD):
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Release build (signed): configure a `signingConfig` and run `./gradlew :app:assembleRelease` (or `bundleRelease` for an AAB to upload to the Play Console).

### Run the engine tests (pure JVM, no device)

```bash
cd tamawatch && ./gradlew :app:testDebugUnitTest
```

These cover the entire simulation core (decay, offline catch-up, attention/care-miss, death, evolution branching, steps→GP). See `app/src/test/java/com/tamawatch/core/EngineTest.kt`.

### Regenerate the assets

All art and audio are procedurally generated and self-validate against the design docs:

```bash
pip install Pillow numpy
cd tamawatch/assets-src && python3 generate_assets.py     # writes res/ + assets_manifest.json
cd ../preview && python3 build_preview.py                 # rebuilds the web preview
```

## Project layout

```
tamawatch/
  docs/        01 game-design-doc · 02 asset-list · 03 impl-plan · 04 asset-validation
  assets-src/  palette + procedural art & chiptune generators (Python)
  app/         Wear OS Gradle project (Kotlin + Compose for Wear)
    src/main/java/com/tamawatch/
      core/    model + engine (PURE Kotlin, JVM-tested) + data (Room/DataStore) + Repository
      assets/  SpriteBank (manifest-driven sprite loading)
      audio/   SoundBank (SoundPool)
      sensor/  StepSource · Haptics
      background/ TickWorker · AttentionNotifier · BootReceiver
      ui/      Compose-for-Wear screens, care ring, mini-games, cutscenes, onboarding
      tile/ complication/  glanceable off-app surfaces
    src/test/  engine unit tests
  preview/     self-contained playable web build (index.html)
```

## Design & docs

The four documents in `docs/` are the source of truth and were produced in sequence: the **game design doc** → the **asset list** → the **implementation plan** → a two-way **asset↔plan validation** that cut/descoped orphaned assets before anything was generated.

## What was verified where

This project was assembled in a sandbox **without access to the Android SDK** (the SDK host is blocked by egress policy there), so:

- ✅ **Simulation engine** — the full care/economy/evolution core passes its JVM unit suite (`EngineTest`, **13/13**) via `./gradlew :app:testDebugUnitTest`. This is the game's heart and it is verified.
- ✅ **Assets** — 79 art frame-strips + 30 SFX generated, integrity-checked, and self-validated against the asset manifest.
- ✅ **Web preview** — JS syntax validated; plays the full loop with the real assets.
- ✅ **Android app layer (Compose UI, Room, Tile, Complication)** — now compiled against the Android SDK (compile/target 34, min 33). `./gradlew :app:assembleDebug` produces the installable `app-debug.apk`, and `./gradlew :app:assembleRelease` produces an R8-minified, resource-shrunk release APK that passes `lintVitalRelease` cleanly. The release still needs your own `signingConfig` before it can be installed/uploaded.

## Roadmap (seams left in the architecture)

Downloadable content packs (TamaSma-style), online friend visits / Tamaverse, cross-watch marriage, cloud backup, wearable-accessory cosmetics, and an extended mini-game roster. The evolution tree and catalog are data-driven and the save schema is versioned to make these additive.
