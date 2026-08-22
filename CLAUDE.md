# TamaWatch — Development Handoff & Workflow

This file is the pickup point for a new session. It captures how we build, test,
preview, and ship **TamaWatch**, a Wear OS (Galaxy Watch Ultra) Tamagotchi-style
virtual pet, plus the exact state we left off in.

- **Active branch: `tamawatchi`** (branched from the complete work at the tip of
  `claude/android-app-development-h0n422`). Do all new work here.
- All app code lives under **`tamawatch/`**. Run gradle from that directory.
- Target device: **Samsung Galaxy Watch Ultra** — 480×480 px, **226×226 dp @ 340 dpi
  (density 2.125)**, round, Wear OS 5 / **API 34**, touch-first (no rotating bezel).

---

## 1. Build tools / environment

The Android SDK is preinstalled in this remote environment at **`/opt/android-sdk`**.
Always pass it explicitly:

```bash
export ANDROID_HOME=/opt/android-sdk
```

- **JDK 21** is present; the project compiles at language level 17 (AGP 8.7.3,
  Gradle 8.11.1, Kotlin 2.0.21, KSP). The Gradle **wrapper is committed** — never
  install gradle.
- Required SDK packages (already installed here; listed for a fresh machine):
  `platforms;android-34`, `build-tools;34.0.0`, `platform-tools`.
- Outbound HTTPS goes through an agent proxy; the Gradle/Java truststore is wired
  automatically. Don't disable TLS.

### Gradle-invocation gotchas (important in this sandbox)

- The Bash tool's working directory resets between calls, and `cd` inside a compound
  command can prompt. Two reliable patterns:
  - `cd /home/user/ThisRepositoryIntentionallyLeftBlank/tamawatch && ANDROID_HOME=/opt/android-sdk ./gradlew …`
  - or the project-dir flag (no cd):
    `ANDROID_HOME=/opt/android-sdk /home/user/ThisRepositoryIntentionallyLeftBlank/tamawatch/gradlew -p /home/user/ThisRepositoryIntentionallyLeftBlank/tamawatch …`
- Long builds: run in background and poll the output file; don't block.

---

## 2. Signed, downloadable APK — the one-shot build

**`tamawatch/tools/build.sh`** builds a **signed release APK** and renders the
preview screenshots in one step:

```bash
ANDROID_HOME=/opt/android-sdk bash tamawatch/tools/build.sh
#   -> tamawatch/app/build/outputs/TamaWatch.apk       (signed, sideloadable, ~10 MB)
#   -> tamawatch/app/build/outputs/screenshots.png     (real-render contact sheet)
# add --gen to regenerate all art/audio assets first
```

### Signing / the dev keystore

- Signing uses a **committed, throwaway dev key**: `tamawatch/tools/dev.keystore`
  (storepass/keypass **`tamadev`**, alias `tamawatch`, CN=TamaWatch Dev). It is
  **not** a real release key — its only purpose is that every build is signed with
  the *same* key so a sideloaded update installs **over** the previous one (a fresh
  random key each build triggers Android's signature-mismatch and the update fails).
- `build.sh` auto-creates a temporary `keystore.properties` pointing at the dev key
  if none exists, and deletes it after. To use your own key, create
  `keystore.properties` (git-ignored) from `keystore.properties.template`.
- Plain `./gradlew :app:assembleRelease` also works; without `keystore.properties`
  it emits the usual `*-unsigned.apk`.

### Getting the APK onto the watch

The user sideloads (they chat from the paired phone). Release APK is ~10 MB
(well under the 30 MB file-send limit; the **debug** APK is ~34 MB — send release).
Install order once: uninstall any prior copy signed with a different key, then
install. Updates thereafter install over each other (stable dev key).

---

## 3. Screenshot generator (real Compose render, no emulator)

There is **no emulator** here (no KVM). Previews are the **real shipping composables**
rendered to PNG on the JVM via **Robolectric + Roborazzi**, so they can't drift from
the app.

```bash
# render every preview state to app/build/screens/NN_name.png:
ANDROID_HOME=/opt/android-sdk ./gradlew :app:recordRoborazziDebug --rerun-tasks
# composite them into a labeled contact sheet:
python3 tamawatch/assets-src/contact_sheet.py [out.png]
```

- The preview states are the **single source of truth** in
  `tamawatch/app/src/main/java/com/tamawatch/ui/PreviewScenarios.kt` (`PreviewShots`).
  Add a `@Composable` shot there and both the synthetic render **and** the on-device
  capture pick it up. Add list screens with **`PinnedScalingColumn`** (deterministic
  scroll) or they diff noisily.
- The renderer (`HomeSnapshotTest.kt`) is pinned to the watch's real config
  `w226dp-h300dp-340dpi` and captures a `testTag("shot")` node → **480×480 px**, 1:1
  with the device.
- **Robolectric offline setup** (in `app/build.gradle.kts`): Maven Central rate-limits
  Robolectric's runtime jar download, so Gradle resolves the `android-all-instrumented`
  jars (API 34 **and** 35, via two `robolectricSdk34/35` configs so version-conflict
  resolution can't drop one), a `Sync` task stages them, and tests run with
  `robolectric.offline=true`. Don't "fix" this by removing it.
- `build.sh` clears `app/build/screens/*.png` and force-reruns so renamed states leave
  no orphans in the sheet.

### On-device capture + pixel diff (validating the render)

- In the app: **Settings → "Capture render states → Gallery"** renders the same
  `PreviewShots` through the real on-watch pipeline and writes a composite + each
  frame (with the device's DisplayMetrics in the header) to `Pictures/TamaWatch` and
  the app's external files dir. Pull them: `adb pull /sdcard/Pictures/TamaWatch`.
  (Samsung's watch Gallery may not index them, but the files are on disk.)
- Diff synthetic vs device with the **anti-aliasing-tolerant** tool:
  ```bash
  python3 tamawatch/assets-src/visual_diff.py app/build/screens <device-dir> --out build/diff
  #   --color N (per-channel threshold, def 32)  --radius R (AA/shift px, def 1)
  #   --fail-pct P (nonzero exit over P%, def 1) → CI-friendly. Emits synthetic|device|diff triptychs.
  ```
  Validated: identical/1px-shift → ~0%; AA blur → ~0.25%; real change → flagged.

---

## 4. Tests & assets

```bash
# pure-JVM engine tests (decay, evolution, economy, care) + the snapshot render:
ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest
# regenerate all procedural art/audio (Pillow + numpy):
cd tamawatch/assets-src && python3 generate_assets.py
```

Engine is pure Kotlin under `core/` (no Android), fully unit-tested. Art/audio are
procedurally generated in `assets-src/*.py` (supersampled clean-line pipeline).

---

## 5. Every-time working rhythm (do this each change)

1. Make the change under `tamawatch/`.
2. **Compile fast** to catch errors: `./gradlew :app:compileDebugKotlin`.
3. For anything visual, **render** and eyeball it (`recordRoborazziDebug` + view the
   PNG) — but note **gesture/animation feel is device-only**; the static render can't
   show drags, bounces, or springs. Say so and let the user test on-wrist.
4. Build the signed APK with `tools/build.sh`.
5. **Commit, then push immediately** (see git note below).
6. Send the user the fresh `TamaWatch.apk` (and the screenshot when relevant).

### Commit conventions

End every commit body with these trailers:

```
Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_<id>
```

Never put a model identifier anywhere in commits/PRs/artifacts beyond that trailer.
Only open a PR if the user explicitly asks.

### ⚠️ Git / container-reset gotcha (bit us repeatedly)

This remote container has **silently rolled the local checkout back to an old commit**
mid-session several times (local `HEAD` + working tree revert; local objects for newer
commits vanish until re-fetched). **Origin is the source of truth and was never
corrupted.** Recovery when you notice files look older than expected:

```bash
git fetch origin '+refs/heads/*:refs/remotes/origin/*'
git log --oneline -3 origin/tamawatchi          # confirm the real tip
git reset --hard origin/tamawatchi              # restore local to it
```

Because of this: **push right after every commit** (`git push -u origin tamawatchi`);
if a commit ever lands on the wrong (old) base, the push is rejected — reset to
origin and `git cherry-pick <your-commit>` back on top.

---

## 6. Where we are — current feature state (tip = `b8f5d25` + this doc)

Done and shipping:

- Touch-first **radial menu** (5 category hubs; no rotary). Interaction: **drag a hub
  toward center to trigger** (no hold); the center name **capsule grows** as you near
  center; releasing short of center **springs the hub toward center** with the capsule
  pulsing (affordance); chosen name **lingers** briefly. Submenus scroll; the confirm
  ring hugs the capsule. Constants in `Menu.kt`: `DRAG_FRAC`, `HINT_PULL`, `FADE_MS`,
  `LINGER_MS`.
- **Pet interactions:** tap to pet (bounce + rising heart, distinct haptic/sound);
  petting has a **30 s happiness cooldown** shown as a "content" heart (never a timer).
  **Procedural grab-and-stretch:** dragging the pet elongates it toward the drag
  (perpendicular squash) + follows the finger, springs back on release — an extra
  transform layer over the frame animation + tap bounce. Constants in `Screens.kt`
  `PetCenter`: `PET_MAX_STRETCH`, `PET_DRAG_FOLLOW`, squash ratio, release spring.
- **Medicine = design A:** free **home remedy** always cures (−4 bond); shop
  **Medicine** is the gentle cure (+1 bond). Care-menu row shows the bond-dip subtitle
  (discoverability) and uses a **two-tap confirm** (not hold, so a scroll-drag can't
  fire it).
- **Grounding:** pet raised, rug independent + selectable (Settings → Rug); single
  baked grounding shadow (fx_shadow suppressed under the pet to avoid a doubled shadow;
  egg still gets it).
- **Status screen:** top-anchored **tray** behind the info for consistent readability;
  list screens use `PinnedScalingColumn`.
- **Notifications:** the "needs you" attention notification **auto-clears the instant a
  need is met** (state-driven via `AttentionNotifier` in the container + a pet-flow
  collector in `TamaActivity`), not just on the 15-min background tick.
- System **swipe-to-dismiss disabled** (`Theme.TamaWatch`, `windowSwipeToDismiss=false`);
  physical back button routes to in-app Home via a `BackHandler`.
- Minecraft-style meat-shank / smiley meters; clean anti-aliased art; four random idle
  animations; "Hatch now"; sound off by default; Help menu.
- **Tooling:** `tools/build.sh` (signed APK + real-render screenshots), Roborazzi
  synthetic renders, on-device capture, `visual_diff.py`.

### Stats reference (for future tuning)

- **Energy** — drains awake (~16 h), refills asleep with light off (~8 h), −10 per
  mini-game. Rest meter; no hard gate.
- **Bond** — up: pet (+2), clean (+1), shop Medicine (+1). down: wrong scold (−5), home
  remedy (−4), disturbed sleep (−1/min).
- **Discipline** — up: correct scold (+8, i.e. scolding a false-alarm call). Wrong scold
  logs a mistake (hurts evolution, costs bond).
- **Evolution** branch is a hidden care score from misses, sick count, discipline
  *mistakes*, disturbed nights, minus games played & steps. Bond/Discipline bars are
  readouts; the behaviors that move them are what steer evolution.

### Open / suggested next

- **Mini-games:** current roster is Jump (timing), Catch (reflex), Guess (luck). Best
  additions for a haptic touchscreen: a **memory/Simon (flag) game** (the classic
  favorite; recommended first) and a **rhythm/tap-to-beat** game. Both feed a score
  into happy/GP like the existing ones.
- **On-wrist tuning:** after device testing, dial the menu drag/spring, the pet
  grab-stretch, the 30 s pet cooldown, and the tray — all one-liners.
- Optionally make Bond/Discipline mechanically matter (e.g. low bond → pickier eater),
  and give the **sick** pose a distinct look (currently X-eyes reads as "dead").

---

## 7. Docs map

- `tamawatch/README.md` — build/preview/diff instructions (user-facing).
- `tamawatch/docs/01-04` — game design doc, asset list, impl plan, asset validation.
- This file — session handoff + workflow (the pickup point).
