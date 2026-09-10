package dev.ttshare

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/** Wraps yt-dlp for the one job this app has: fetch a single video as an MP4. */
object Downloader {

    private const val SHARED_DIR_NAME: String = "shared"
    private const val MP4_EXTENSION: String = "mp4"
    private const val OPTIMIZED_SUFFIX: String = "-ttshare"
    private const val TAG: String = "Downloader"
    private const val RETRIES: Int = 3
    private const val SOCKET_TIMEOUT_SECONDS: Int = 15
    private const val MAX_STDERR_LINES: Int = 6
    private val PRUNE_AGE_MILLIS: Long = TimeUnit.DAYS.toMillis(1)

    /** Same URL pattern Seal uses to pull a link out of arbitrary shared text. */
    private val URL_PATTERN: Pattern = Pattern.compile(
        "(http|https)://[\\w\\-_]+(\\.[\\w\\-_]+)+([\\w\\-.,@?^=%&:/~+#]*[\\w\\-@?^=%&/~+#])?"
    )

    /** Prefer a progressive H.264 MP4; otherwise merge best video + audio and remux to MP4. */
    private const val FORMAT_SELECTOR: String = "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b"
    private const val FORMAT_SORT: String = "vcodec:h264,res,br"
    private const val OUTPUT_TEMPLATE: String = "%(uploader,id)s-%(id)s.%(ext)s"

    fun sharedDir(context: Context): File = File(context.cacheDir, SHARED_DIR_NAME)

    fun extractUrl(text: String?): String? {
        var found: String? = null
        if (text != null) {
            val matcher = URL_PATTERN.matcher(text)
            if (matcher.find()) {
                found = matcher.group()
            }
        }
        return found
    }

    enum class Phase { DOWNLOADING, ANALYZING, COMPRESSING }

    /**
     * Downloads [url] into [outDir], then crops letterboxing and re-encodes to a smaller MP4.
     * Falls back to the raw download if the optimize step fails. Blocking; call from a background dispatcher.
     */
    fun downloadAndOptimize(
        url: String,
        outDir: File,
        taskId: String,
        ffmpeg: FfmpegRunner,
        onProgress: (Phase, Float, String) -> Unit
    ): File {
        val raw: File = download(url, outDir, taskId) { pct, line -> onProgress(Phase.DOWNLOADING, pct, line) }
        onProgress(Phase.ANALYZING, -1f, "")
        val optimizer = MediaOptimizer(ffmpeg)
        val optimized: File = File(outDir, raw.nameWithoutExtension + OPTIMIZED_SUFFIX + ".$MP4_EXTENSION")
        val outcome: MediaOptimizer.Outcome = runCatching {
            optimizer.optimize(raw, optimized) { pct -> onProgress(Phase.COMPRESSING, pct * 100f, "") }
        }.getOrElse { error -> MediaOptimizer.Outcome(raw, null, null, error.message ?: "optimize failed") }
        Log.i(TAG, "optimize: ${outcome.note}")

        if (outcome.file != raw) {
            raw.delete()
        }
        return outcome.file
    }

    /**
     * Downloads [url] into [outDir] and returns the resulting MP4.
     * Blocking; call from a background dispatcher.
     */
    fun download(
        url: String,
        outDir: File,
        taskId: String,
        onProgress: (Float, String) -> Unit
    ): File {
        outDir.deleteRecursively()
        outDir.mkdirs()

        val request = YoutubeDLRequest(url)
            .addOption("--no-playlist")
            .addOption("--no-mtime")
            .addOption("--restrict-filenames")
            .addOption("-R", RETRIES)
            .addOption("--socket-timeout", SOCKET_TIMEOUT_SECONDS)
            .addOption("-f", FORMAT_SELECTOR)
            .addOption("-S", FORMAT_SORT)
            .addOption("--merge-output-format", MP4_EXTENSION)
            .addOption("-P", outDir.absolutePath)
            .addOption("-o", OUTPUT_TEMPLATE)

        val response = YoutubeDL.getInstance().execute(request, taskId) { progress, _, line ->
            onProgress(progress, line)
        }

        val produced: File? = outDir.listFiles()
            ?.filter { it.isFile && it.extension.equals(MP4_EXTENSION, ignoreCase = true) }
            ?.maxByOrNull { it.length() }

        if (produced == null) {
            throw DownloadFailedException(summarize(response.err.ifBlank { response.out }))
        }
        return produced
    }

    fun cancel(taskId: String) {
        YoutubeDL.getInstance().destroyProcessById(taskId)
    }

    /** Pulls the newest stable yt-dlp from GitHub (network on the phone). Returns the new version. */
    fun updateEngine(context: Context): String {
        val ytdlp = YoutubeDL.getInstance()
        ytdlp.updateYoutubeDL(context)
        return ytdlp.version(context) ?: "unknown"
    }

    fun engineVersion(context: Context): String = YoutubeDL.getInstance().version(context) ?: "unknown"

    fun pruneOldFiles(dir: File) {
        val cutoff: Long = System.currentTimeMillis() - PRUNE_AGE_MILLIS
        dir.listFiles()
            ?.filter { it.isFile && it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    /** Keeps the tail of yt-dlp's stderr, which is where the actual error message lives. */
    fun summarize(output: String): String = output
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .takeLast(MAX_STDERR_LINES)
        .joinToString("\n")

    class DownloadFailedException(message: String) : RuntimeException(message)
}
