# TTShare

A single-purpose Android app. Share a TikTok video to it, it downloads the video with
yt-dlp, then opens the system share sheet with the resulting MP4 so you can send it on
as a plain video file.

It is a stripped-down take on [Seal](https://github.com/JunkFood02/Seal): the same yt-dlp
Android library, the same share-intent handling, the same FileProvider trick for handing
the file to other apps. Everything else in Seal (settings, history, playlists, Compose UI,
cookies, aria2c) is gone.

## How it works

1. `ShareActivity` is registered for `ACTION_SEND text/plain` and for `ACTION_VIEW` on
   `tiktok.com` links. TikTok's share sheet sends text like
   `Check out this video! https://vm.tiktok.com/ZM.../`. The URL is pulled out with the
   same regex Seal uses.
2. `Downloader` runs yt-dlp (bundled, with FFmpeg) asking for an H.264 MP4, remuxing if
   TikTok only offers split streams or another container. Output lands in the app's
   private cache under `shared/`. No storage permission is needed.
3. On success the activity builds an `ACTION_SEND video/mp4` intent with a FileProvider
   URI and opens `Intent.createChooser`. Pick a messaging app and the MP4 attaches.
4. Opening the app from the launcher shows the bundled yt-dlp version and an
   "Update yt-dlp" button. TikTok changes often; if downloads start failing, tap that.
   The failure dialog also offers "Update yt-dlp & retry".

Files in the cache older than a day are deleted on app start.

## Build

Requirements: JDK 17 or 21, network access to `dl.google.com`, `repo1.maven.org`,
`services.gradle.org`, `plugins.gradle.org`.

```bash
./build-apk.sh
```

The script installs Android command-line tools into `$ANDROID_HOME` (default
`~/android-sdk`) if missing, pulls platform 34 and build-tools 34.0.0, runs
`./gradlew assembleRelease`, and verifies the signature. The APK lands at
`app/build/outputs/apk/release/TTShare-<version>-release.apk`. arm64-v8a only.

Signing uses `keystore/ttshare.jks` with credentials in `keystore.properties`. Both are
committed on purpose so rebuilds can install over the existing app. Anyone with this
repo can sign as this app, which is acceptable for a private sideload.

## Install (sideload)

1. Copy the APK to the phone.
2. Open it. Allow "install unknown apps" for whatever app you opened it from.
3. In TikTok, tap Share on a video, then choose TTShare (may be under "More").
4. Wait for the progress dialog, then pick where to send the MP4.

Minimum Android version: 8.0 (API 26).

## Renaming the branch

This was developed on `claude/ttshare-tiktok-downloader-7jf5fq`. To rename it locally
and on the remote:

```bash
git branch -m claude/ttshare-tiktok-downloader-7jf5fq ttshare
git push -u origin ttshare
git push origin --delete claude/ttshare-tiktok-downloader-7jf5fq
```
