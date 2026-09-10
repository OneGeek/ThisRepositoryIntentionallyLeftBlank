---
name: android-sideload-apk
description: Set up an Android build environment from scratch in a headless Linux session (Claude Code on the web or any container with JDK and outbound HTTPS), build a signed release APK for sideloading, and deliver it to the user in chat, splitting it when it exceeds the upload cap. Use whenever the task is "build me an APK", "package this as a sideloadable app", "set up Android tooling here", or when an existing Android project needs a release build with no Android Studio and no CI. Also covers the keystore decision (commit a personal key or not) and how the user rejoins a split APK.
---

# Android sideload APK: environment, build, sign, deliver

This is the procedure that produced TTShare (`ttshare/` in this repo) on 2026-09-09.
Every command below was run and worked in a Claude Code web session. Adjust paths and
versions where noted.

## 0. Check what the box already has

```bash
java -version                 # JDK 17+ needed. This box had OpenJDK 21 at /usr/bin/java.
which gradle keytool unzip curl
nproc; free -g; df -h /       # 4 CPU / 15 GB RAM built a 55 MB APK in ~5.5 min.
```

Check network reachability before anything else. A blocked host found late costs a
full build cycle.

```bash
for u in https://dl.google.com/android/repository/repository2-3.xml \
         https://dl.google.com/dl/android/maven2/master-index.xml \
         https://repo1.maven.org/maven2/ \
         https://plugins.gradle.org/m2/ \
         https://services.gradle.org/distributions/ \
         https://jitpack.io https://github.com; do
  printf "%s -> " "$u"; curl -sS -o /dev/null -w "%{http_code}\n" --max-time 20 "$u" || echo FAIL
done
```

What this session saw: Google SDK repo, Google Maven, Maven Central, Gradle plugin
portal, and Gradle distributions all 200. JitPack and GitHub were 403 through the
proxy. Consequences:

- Any dependency only on JitPack must be replaced with a Maven Central equivalent
  (for yt-dlp on Android, `io.github.junkfood02.youtubedl-android` on Central replaces
  the `com.github.yausername` JitPack coordinates).
- Do not add the foojay toolchain resolver plugin. It fetches JDKs from GitHub.
  Pin `compileOptions` / `kotlinOptions.jvmTarget` to 17 and let the installed JDK 21
  compile it instead of asking Gradle for a toolchain.
- Go through the proxy as configured. Never unset `HTTPS_PROXY` or disable TLS checks.

## 1. Install the Android SDK (no Android Studio)

Command-line tools only. This took about a minute.

```bash
export ANDROID_HOME=/root/android-sdk     # or $HOME/android-sdk
mkdir -p $ANDROID_HOME/cmdline-tools && cd $ANDROID_HOME/cmdline-tools
curl -sS -o cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q cmdtools.zip && mv cmdline-tools latest && rm cmdtools.zip
yes | latest/bin/sdkmanager --licenses >/dev/null 2>&1
latest/bin/sdkmanager --install "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

The `mv cmdline-tools latest` step matters: sdkmanager refuses to run unless it lives
at `cmdline-tools/latest/bin`. Run this in the background (it is a few hundred MB) and
scaffold the project while it downloads.

Point the project at it: `echo "sdk.dir=$ANDROID_HOME" > <project>/local.properties`
and gitignore `local.properties`.

Useful binaries after install: `$ANDROID_HOME/build-tools/34.0.0/{apksigner,aapt2,zipalign}`
and `$ANDROID_HOME/platform-tools/adb`.

## 2. Gradle

Use the Gradle wrapper, not the system Gradle, so the build is reproducible. If you have
no wrapper jar, copy `gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar`
from any existing Android project (this session took them from the Seal 1.13.1 source
zip). Set `gradle-wrapper.properties` to a distribution that services.gradle.org serves:

```
distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
```

Known-good version set, taken from a project whose CI builds on JDK 21:

| Piece | Version |
|---|---|
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 1.9.23 |
| Gradle wrapper | 8.7 |
| compileSdk / targetSdk | 34 |
| build-tools | 34.0.0 |

Put versions in `gradle/libs.versions.toml` and keep `settings.gradle.kts` repositories
to `google()` and `mavenCentral()` only (plus `gradlePluginPortal()` in
`pluginManagement`). `RepositoriesMode.FAIL_ON_PROJECT_REPOS` stops a module from
sneaking JitPack back in.

`gradle.properties` minimum:

```
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
```

Run the build in the background with a long timeout; pipe through `tail` only if you
accept seeing nothing until it ends:

```bash
export ANDROID_HOME=/root/android-sdk
./gradlew --no-daemon assembleRelease
```

First build downloads Gradle, AGP, and every dependency. Budget 5 to 10 minutes.

## 3. Signing for sideloading

Android will not install an unsigned APK, and it will only install an update over an
existing app if the signature matches. So generate one key and keep it.

```bash
mkdir -p keystore && cd keystore
PW=$(openssl rand -hex 12)
keytool -genkeypair -v -keystore ttshare.jks -alias ttshare -keyalg RSA -keysize 2048 \
  -validity 10000 -storepass "$PW" -keypass "$PW" -dname "CN=TTShare, O=TTShare, C=US"
printf 'storeFile=keystore/ttshare.jks\nstorePassword=%s\nkeyAlias=ttshare\nkeyPassword=%s\n' "$PW" "$PW" > ../keystore.properties
```

Wire it in `app/build.gradle.kts`:

```kotlin
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").inputStream().use { load(it) }
}
android {
    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProperties["storeFile"].toString())
            storePassword = keystoreProperties["storePassword"].toString()
            keyAlias = keystoreProperties["keyAlias"].toString()
            keyPassword = keystoreProperties["keyPassword"].toString()
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug { signingConfig = signingConfigs.getByName("release") }
    }
}
```

Signing debug with the same key means a debug build installs over a release build
without an uninstall.

### The decision: commit the keystore or not

Ask the user. Both are defensible; the default for a personal sideload app is to commit.

- **Commit keystore and `keystore.properties`.** Any future session can rebuild and
  the phone accepts it as an update. Anyone with repo access can sign as this app. For
  a private repo and an app that only the owner installs, that is an acceptable trade.
  This is what TTShare does.
- **Keep it out of git.** Send the `.jks` and password to the user in chat, gitignore
  both files. The user must supply them for every future rebuild, and if they are lost
  the app must be uninstalled before the next version installs. Pick this for anything
  that will be distributed to other people or published to a store.

Never generate a new key silently for a rebuild of an app that already exists on the
user's phone. That forces an uninstall.

### Other release-build details that bit or almost bit

- `minSdk`: an adaptive icon under `mipmap-anydpi-v26` only exists on API 26+. Either
  set `minSdk = 26` or supply PNG launcher icons for lower levels. Lint does not fail
  the build for this; the app crashes at launch on the old device instead.
- Native libraries: `ndk { abiFilters += "arm64-v8a" }` to ship one architecture.
  Libraries that carry Python or FFmpeg payloads need
  `packaging { jniLibs.useLegacyPackaging = true }` and
  `android:extractNativeLibs="true"` in the manifest, plus
  `<uses-sdk tools:overrideLibrary="..."/>` if their minSdk is higher than yours.
- Resource shrinking keeps raw resources that code references. Verify afterwards with
  `aapt2 dump resources` (step 4) rather than trusting it.
- Proguard for reflective libraries: `-keep class com.yausername.** { *; }` style
  rules, and `-dontobfuscate` when stack traces from users matter more than size.
- Name the output so the version is in the filename:

```kotlin
applicationVariants.all {
    outputs.all {
        (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
            "TTShare-${defaultConfig.versionName}-${name}.apk"
    }
}
```

## 4. Verify before delivering

There is no emulator in these sessions (no KVM), so static checks are all you get.
Run all of them.

```bash
BT=$ANDROID_HOME/build-tools/34.0.0
APK=$(ls app/build/outputs/apk/release/*.apk)

$BT/apksigner verify --verbose --print-certs "$APK"     # expect "Verifies", v2 true, your DN
$BT/aapt2 dump badging "$APK" | grep -E "^package|sdkVersion|native-code|launchable|uses-permission"
$BT/aapt2 dump xmltree --file AndroidManifest.xml "$APK" | grep -E 'E: (activity|intent-filter|provider)|android:(name|mimeType|scheme|host)'
unzip -l "$APK" | awk '$1 > 1000000'                  # big payloads present? (native libs, raw assets)
$BT/aapt2 dump resources "$APK" | grep -A2 'type raw'  # shrinker kept the raw resources?
sha256sum "$APK"
```

Tell the user plainly which behaviour was verified statically and which needs a real
phone. Do not describe on-device behaviour as confirmed.

## 5. Deliver the APK in chat

Chat delivery avoids committing binaries. The upload cap is **30 MiB per file**.
An APK with FFmpeg and Python payloads for one ABI came out at 52.8 MiB and was
rejected; a lean app without native payloads fits in one file.

Try the single file first with `SendUserFile` (`display: attach`). If it is refused
for size, split it:

```bash
cd app/build/outputs/apk/release
split -b 28m -d -a 1 TTShare-0.1.0-release.apk TTShare-0.1.0-release.apk.part
ls -la TTShare-0.1.0-release.apk.part*
cat TTShare-0.1.0-release.apk.part0 TTShare-0.1.0-release.apk.part1 | sha256sum   # must equal the original
```

`-b 28m` leaves headroom under 30 MiB. `-d -a 1` gives numeric suffixes `part0`,
`part1`, ... Send all parts in one `SendUserFile` call and put the rejoin command and
the SHA-256 in the caption so it travels with the files.

Alternatives considered and why they were not used: uploading through the Google Drive
MCP tool requires base64 inside a tool parameter, which is impractical above a few MB;
GitHub Releases has no create tool in the MCP set available here; committing the APK
to git was declined by the user (and costs 50 MB of repo history per rebuild). Building
a second, FFmpeg-free variant to fit under the cap is an option if the user prefers one
file over remux support.

### Rejoin procedure for the user

On macOS or Linux, in the download folder:

```bash
cat TTShare-0.1.0-release.apk.part0 TTShare-0.1.0-release.apk.part1 > TTShare-0.1.0-release.apk
sha256sum TTShare-0.1.0-release.apk      # macOS: shasum -a 256
```

On Windows (PowerShell):

```powershell
cmd /c copy /b TTShare-0.1.0-release.apk.part0+TTShare-0.1.0-release.apk.part1 TTShare-0.1.0-release.apk
Get-FileHash TTShare-0.1.0-release.apk -Algorithm SHA256
```

The hash must match the one in the caption. Order matters: part0 first. Then install:

```bash
adb devices
adb install -r TTShare-0.1.0-release.apk
```

`INSTALL_FAILED_UPDATE_INCOMPATIBLE` means a copy signed with a different key is on the
phone: `adb uninstall <applicationId>` first. Without adb, copy the APK to the phone and
open it; Android asks to allow installs from that source.

## 6. Leave the repo reproducible

- Commit a `build-apk.sh` that does steps 1, 2 and 4 end to end (install SDK if
  missing, write `local.properties`, `./gradlew --no-daemon assembleRelease`,
  `apksigner verify`, print path and SHA-256). See `ttshare/build-apk.sh`.
- Gitignore `.gradle/`, `**/build/`, `local.properties`, `.kotlin/`.
- README: what the app does, how to build, how to sideload, and the keystore decision.
- Do not put model identifiers in commits or code.

## Reference: the Seal-derived TikTok downloader built with this procedure

`ttshare/` is the worked example: three Kotlin files, `dev.ttshare`, minSdk 26,
arm64-v8a, yt-dlp + FFmpeg via `io.github.junkfood02.youtubedl-android:{library,ffmpeg}:0.18.1`
from Maven Central. Share-target activity, download to `cacheDir/shared`, FileProvider
`ACTION_SEND video/mp4` chooser. 55 MB APK, delivered as two parts.
