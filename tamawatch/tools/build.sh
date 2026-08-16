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
# otherwise a throwaway dev key is generated just for this build (fine for
# sideloading / previews — use your own keystore.properties for real releases).
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
THROWAWAY=0
if [ ! -f keystore.properties ]; then
  echo "==> No keystore.properties; generating a throwaway dev signing key"
  keytool -genkeypair -v -keystore "$OUT/dev.jks" -alias tamawatch \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass tamadev -keypass tamadev \
    -dname "CN=TamaWatch Dev, O=TamaWatch, C=US" >/dev/null 2>&1
  printf 'storeFile=%s\nstorePassword=tamadev\nkeyAlias=tamawatch\nkeyPassword=tamadev\n' \
    "$OUT/dev.jks" > keystore.properties
  THROWAWAY=1
fi

echo "==> Building signed release APK"
./gradlew :app:assembleRelease --console=plain
cp app/build/outputs/apk/release/app-release.apk "$OUT/TamaWatch.apk"

if [ "$THROWAWAY" = 1 ]; then
  rm -f keystore.properties "$OUT/dev.jks"
fi

# --- screenshots (always) ----------------------------------------------------
echo "==> Rendering screenshots"
python3 assets-src/render_screens.py "$OUT/screenshots.png"

echo
echo "APK:         $OUT/TamaWatch.apk"
echo "Screenshots: $OUT/screenshots.png"
