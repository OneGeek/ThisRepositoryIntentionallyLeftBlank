package dev.ttshare

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs the FFmpeg binary that ships inside the youtubedl-android ffmpeg module.
 * The library only drives FFmpeg through yt-dlp, so this reproduces its launch environment:
 * the executable is `libffmpeg.so` in the native library dir and its shared objects live under
 * `<files>/youtubedl-android/packages/ffmpeg/usr/lib` after FFmpeg.init() has unpacked them.
 */
class FfmpegRunner(private val context: Context) {

    class FfmpegResult(val exitCode: Int, val stderr: String, val stdout: String) {
        val succeeded: Boolean get() = exitCode == 0
    }

    private val binary: File = File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)

    @Volatile
    private var running: Process? = null

    fun isAvailable(): Boolean = binary.exists() && libraryDir() != null

    /**
     * Blocking. [onStdoutLine] receives each stdout line as it arrives (used for `-progress pipe:1`).
     * Stderr is collected and returned whole.
     */
    fun run(
        args: List<String>,
        timeoutSeconds: Long,
        onStdoutLine: ((String) -> Unit)? = null
    ): FfmpegResult {
        val libDir: File = libraryDir() ?: throw IllegalStateException("FFmpeg libraries not unpacked")
        val command: List<String> = listOf(binary.absolutePath, "-hide_banner", "-nostdin", "-y") + args

        val builder = ProcessBuilder(command)
        builder.environment().apply {
            put("LD_LIBRARY_PATH", libDir.absolutePath)
            put("HOME", context.filesDir.absolutePath)
            put("TMPDIR", context.cacheDir.absolutePath)
        }
        val process: Process = builder.start()
        running = process

        val stderr = StringBuffer()
        val stdout = StringBuffer()
        val errThread = Thread {
            process.errorStream.bufferedReader().forEachLine { line -> stderr.append(line).append('\n') }
        }
        val outThread = Thread {
            process.inputStream.bufferedReader().forEachLine { line ->
                stdout.append(line).append('\n')
                onStdoutLine?.invoke(line)
            }
        }
        errThread.start()
        outThread.start()

        val finished: Boolean = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
        }
        errThread.join()
        outThread.join()
        running = null

        val exitCode: Int = if (finished) process.exitValue() else TIMEOUT_EXIT_CODE
        return FfmpegResult(exitCode, stderr.toString(), stdout.toString())
    }

    fun cancel() {
        running?.destroyForcibly()
    }

    private fun libraryDir(): File? {
        val roots: List<File> = listOf(context.noBackupFilesDir, context.filesDir)
        return roots
            .map { root -> File(root, PACKAGES_RELATIVE_PATH) }
            .firstOrNull { it.isDirectory }
    }

    private companion object {
        const val BINARY_NAME: String = "libffmpeg.so"
        const val PACKAGES_RELATIVE_PATH: String = "youtubedl-android/packages/ffmpeg/usr/lib"
        const val TIMEOUT_EXIT_CODE: Int = -1
    }
}
