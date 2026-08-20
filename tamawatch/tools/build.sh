#!/usr/bin/env bash
# One-shot TamaWatch build.
#
# Produces, under app/build/outputs/:
#   TamaWatch.apk    signed release APK (installable / sideloadable)
#   screenshots.png  5-up contact sheet of representative screens
#
# The screenshots PNG is ALWAYS rendered alongside the APK, so a build can be
# eyeballed without a device.
#
# Signing: if a keystore.properties exists at the project root it is used as-is;
# otherwise the shared, committed dev key (tools/dev.keystore) is used. Using one
# stable key across builds is what lets a sideloaded update install OVER a prior
# build (a fresh random key each time triggers Android's signature-mismatch and
# the update silently fails). The dev key is for sideloading/previews only — use
# your own keystore.properties for real Play releases.
#
# Requires: JDK, the Android SDK (ANDROID_HOME or local.properties), Python with
# Pillow (+ numpy for asset regen). Pass --gen to regenerate assets first.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="app/build/outputs"
mkdir -p "$OUT"

# Ensure Gradle can find the SDK.
if [ ! -f local.properties ] && [ -n "${ANDROID_HOME:-}" ]; then
  echo "sdk.dir=$ANDROID_HOME" > local.properties
fi

# Optional: regenerate all art/audio from the Python generators.
if [ "${1:-}" = "--gen" ]; then
  echo "==> Regenerating assets"
  ( cd assets-src && python3 generate_assets.py )
fi

# --- signed release APK ------------------------------------------------------
# Use the caller's keystore.properties if present; otherwise fall back to the
# committed, stable dev key so updates install over each other.
TEMP_KS=0
if [ ! -f keystore.properties ]; then
  echo "==> Using shared dev key (tools/dev.keystore)"
  printf 'storeFile=tools/dev.keystore\nstorePassword=tamadev\nkeyAlias=tamawatch\nkeyPassword=tamadev\n' \
    > keystore.properties
  TEMP_KS=1
fi

echo "==> Building signed release APK"
./gradlew :app:assembleRelease --console=plain
cp app/build/outputs/apk/release/app-release.apk "$OUT/TamaWatch.apk"

[ "$TEMP_KS" = 1 ] && rm -f keystore.properties

# --- screenshots (always) ----------------------------------------------------
# Render the REAL Compose screens to PNGs on the JVM (Robolectric + Roborazzi —
# no emulator), then composite them into one contact sheet. These are the actual
# shipping composables with the real assets, so the preview can't drift from the
# app the way a hand-drawn mock does.
echo "==> Rendering screenshots (real Compose render)"
rm -f app/build/screens/*.png          # drop stale frames from renamed/removed states
# --rerun-tasks so the render always regenerates (Gradle can't see that the rm
# above invalidated the Roborazzi outputs, and would otherwise skip as up-to-date).
./gradlew :app:recordRoborazziDebug --console=plain --rerun-tasks
python3 assets-src/contact_sheet.py "$OUT/screenshots.png"

echo
echo "APK:         $OUT/TamaWatch.apk"
echo "Screenshots: $OUT/screenshots.png"
