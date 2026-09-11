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
3. Two FFmpeg passes shrink the file (`MediaOptimizer`, `FfmpegRunner`):
   - Analysis, about a second: decode keyframes only over the first two minutes and run
     `cropdetect` per frame. The "majority box" is a 20th/80th percentile over those
     per-frame boxes, so a brief full-frame transition cannot veto the crop. Frames whose
     content extends past the majority box get a second look: the bar strips on those
     frames are measured for blackness. Mostly black with something in it (a caption, a
     logo) is protected and the crop widens to keep it; a strip filled with picture is a
     transition and is cropped through. Only vertical letterboxing (black bars top and
     bottom) is acted on, and only when the bars total at least 32 px and the remaining
     picture is at least half the original height. Known gaps: content that appears only
     between keyframes is never seen; a caption whose own pixels cover more than 40% of the
     bar is treated as picture; bars that are not black are not detected at all.
   - Encode: crop the bars, cap the long side at 1280 px (TikTok's 1080x1920 becomes
     720x1280), H.264 at roughly 0.06 bits per pixel per frame (about 1.7 Mbps for
     720x1280 at 30 fps, clamped to 0.6 to 2.5 Mbps), AAC 96 kbps. The hardware encoder
     (`h264_mediacodec`) is tried first, `libx264 -preset veryfast -crf 26` is the
     fallback. If neither works, or the result is not smaller and nothing was cropped,
     the raw download is shared unchanged.
   FFmpeg is the copy bundled by the yt-dlp library; the app runs `libffmpeg.so` from
   the native library directory with `LD_LIBRARY_PATH` pointing at the unpacked
   `packages/ffmpeg/usr/lib`, the same way yt-dlp itself launches it.
4. On success the activity builds an `ACTION_SEND video/mp4` intent with a FileProvider
   URI and opens `Intent.createChooser`. Pick a messaging app and the MP4 attaches.
5. Opening the app from the launcher shows the bundled yt-dlp version and an
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
