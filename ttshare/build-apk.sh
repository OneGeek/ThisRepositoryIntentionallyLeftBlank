#!/usr/bin/env bash
# Builds the signed release APK from scratch, installing the Android SDK pieces if they are missing.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
BUILD_TOOLS_VERSION="34.0.0"

if [ ! -x "$SDKMANAGER" ]; then
  echo "Installing Android command-line tools into $ANDROID_HOME"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  tmp_zip="$(mktemp)"
  curl -sSL -o "$tmp_zip" "$CMDLINE_TOOLS_URL"
  unzip -q "$tmp_zip" -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -f "$tmp_zip"
fi

yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" --install "platforms;android-34" "build-tools;$BUILD_TOOLS_VERSION" "platform-tools" >/dev/null

echo "sdk.dir=$ANDROID_HOME" > "$HERE/local.properties"

cd "$HERE"
./gradlew --no-daemon assembleRelease

APK="$(ls "$HERE"/app/build/outputs/apk/release/TTShare-*-release.apk | head -n 1)"
"$ANDROID_HOME/build-tools/$BUILD_TOOLS_VERSION/apksigner" verify --print-certs "$APK"
echo
echo "Signed APK: $APK"
sha256sum "$APK"
