package dev.ttshare

import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Two FFmpeg passes over a downloaded video:
 *  1. Analysis (fast, keyframes only): read duration, frame size, frame rate, and the bounding box
 *     of non-black content via `cropdetect`. Only vertical letterboxing (bars top and bottom) is acted on.
 *  2. Encode: crop the bars, cap the long side at 1280 px, H.264 at a bitrate tuned to the pixel count,
 *     AAC audio. Hardware encoder first, libx264 fallback.
 * If anything fails, the caller keeps the original file, so this never turns a good download into no download.
 */
class MediaOptimizer(private val ffmpeg: FfmpegRunner) {

    class Probe(
        val durationSeconds: Double,
        val width: Int,
        val height: Int,
        val fps: Double,
        val cropWidth: Int,
        val cropHeight: Int,
        val cropX: Int,
        val cropY: Int
    )

    class Plan(val cropTop: Int, val cropHeight: Int, val outWidth: Int, val outHeight: Int, val hasCrop: Boolean)

    class Outcome(val file: File, val plan: Plan?, val encoder: String?, val note: String)

    fun optimize(input: File, output: File, onProgress: (Float) -> Unit): Outcome {
        var outcome = Outcome(input, null, null, "ffmpeg unavailable")
        if (ffmpeg.isAvailable()) {
            val probe: Probe? = analyze(input)
            if (probe == null) {
                outcome = Outcome(input, null, null, "analysis failed")
            } else {
                val plan: Plan = plan(probe)
                outcome = encode(input, output, probe, plan, onProgress)
            }
        }
        return outcome
    }

    fun analyze(input: File): Probe? {
        val args: List<String> = listOf(
            "-skip_frame", "nokey",
            "-t", ANALYSIS_WINDOW_SECONDS.toString(),
            "-i", input.absolutePath,
            "-vf", "cropdetect=limit=$CROP_BLACK_LIMIT:round=2:reset=0",
            "-an", "-f", "null", "-"
        )
        val result = ffmpeg.run(args, ANALYSIS_TIMEOUT_SECONDS)
        return parseProbe(result.stderr)
    }

    fun parseProbe(stderr: String): Probe? {
        var probe: Probe? = null
        val duration = DURATION_PATTERN.find(stderr)
        val size = SIZE_PATTERN.find(stderr)
        val crop = CROP_PATTERN.findAll(stderr).lastOrNull()
        if (duration != null && size != null) {
            val seconds: Double = duration.groupValues[1].toDouble() * 3600 +
                duration.groupValues[2].toDouble() * 60 +
                duration.groupValues[3].toDouble()
            val width: Int = size.groupValues[1].toInt()
            val height: Int = size.groupValues[2].toInt()
            val fps: Double = FPS_PATTERN.find(stderr)?.groupValues?.get(1)?.toDoubleOrNull() ?: DEFAULT_FPS
            probe = if (crop != null) {
                Probe(
                    seconds, width, height, fps,
                    crop.groupValues[1].toInt(), crop.groupValues[2].toInt(),
                    crop.groupValues[3].toInt(), crop.groupValues[4].toInt()
                )
            } else {
                Probe(seconds, width, height, fps, width, height, 0, 0)
            }
        }
        return probe
    }

    /** Decide the vertical crop and output size. Horizontal bars are ignored on purpose. */
    fun plan(probe: Probe): Plan {
        val barsTotal: Int = probe.height - probe.cropHeight
        val keepsEnough: Boolean = probe.cropHeight >= probe.height * MIN_KEEP_RATIO
        val hasCrop: Boolean = barsTotal >= MIN_BARS_TOTAL_PX && keepsEnough && probe.cropHeight > 0

        val cropTop: Int = if (hasCrop) even(probe.cropY) else 0
        val cropHeight: Int = if (hasCrop) even(probe.cropHeight) else even(probe.height)
        val srcWidth: Int = even(probe.width)

        val longSide: Int = max(srcWidth, cropHeight)
        val scale: Double = if (longSide > MAX_LONG_SIDE_PX) MAX_LONG_SIDE_PX.toDouble() / longSide else 1.0
        val outWidth: Int = even((srcWidth * scale).roundToInt())
        val outHeight: Int = even((cropHeight * scale).roundToInt())
        return Plan(cropTop, cropHeight, outWidth, outHeight, hasCrop)
    }

    private fun encode(input: File, output: File, probe: Probe, plan: Plan, onProgress: (Float) -> Unit): Outcome {
        val filters: String = buildFilter(probe, plan)
        val kbps: Int = targetKbps(plan.outWidth, plan.outHeight, probe.fps)
        val encoders: List<Pair<String, List<String>>> = listOf(
            HW_ENCODER to listOf("-c:v", HW_ENCODER, "-b:v", "${kbps}k", "-g", GOP_FRAMES.toString()),
            SW_ENCODER to listOf(
                "-c:v", SW_ENCODER, "-preset", X264_PRESET, "-crf", X264_CRF.toString(),
                "-maxrate", "${kbps * 3 / 2}k", "-bufsize", "${kbps * 3}k", "-pix_fmt", "yuv420p"
            )
        )

        var outcome = Outcome(input, plan, null, "all encoders failed")
        var index = 0
        while (index < encoders.size && outcome.encoder == null) {
            val (name, videoArgs) = encoders[index]
            output.delete()
            val args: List<String> = listOf("-i", input.absolutePath, "-vf", filters) +
                videoArgs +
                listOf(
                    "-c:a", "aac", "-b:a", "${AUDIO_KBPS}k", "-ac", "2",
                    "-movflags", "+faststart",
                    "-progress", "pipe:1", "-nostats",
                    output.absolutePath
                )
            val timeout: Long = encodeTimeoutSeconds(probe.durationSeconds)
            val result = ffmpeg.run(args, timeout) { line -> reportProgress(line, probe.durationSeconds, onProgress) }
            if (result.succeeded && output.length() > 0) {
                outcome = chooseSmaller(input, output, plan, name)
            }
            index++
        }
        return outcome
    }

    private fun chooseSmaller(input: File, output: File, plan: Plan, encoder: String): Outcome {
        val encodedIsUseful: Boolean = plan.hasCrop || output.length() < input.length()
        return if (encodedIsUseful) {
            Outcome(output, plan, encoder, "encoded with $encoder")
        } else {
            output.delete()
            Outcome(input, plan, encoder, "encoded file was not smaller; kept original")
        }
    }

    private fun buildFilter(probe: Probe, plan: Plan): String {
        val parts = mutableListOf<String>()
        if (plan.hasCrop) {
            parts.add("crop=${even(probe.width)}:${plan.cropHeight}:0:${plan.cropTop}")
        }
        parts.add("scale=${plan.outWidth}:${plan.outHeight}")
        parts.add("format=yuv420p")
        return parts.joinToString(",")
    }

    private fun reportProgress(line: String, durationSeconds: Double, onProgress: (Float) -> Unit) {
        val match = OUT_TIME_PATTERN.find(line)
        if (match != null && durationSeconds > 0) {
            val micros: Double = match.groupValues[1].toDoubleOrNull() ?: 0.0
            val fraction: Float = (micros / 1_000_000.0 / durationSeconds).toFloat().coerceIn(0f, 1f)
            onProgress(fraction)
        }
    }

    private fun targetKbps(width: Int, height: Int, fps: Double): Int {
        val bitsPerSecond: Double = width * height * fps * BITS_PER_PIXEL
        return (bitsPerSecond / 1000).roundToInt().coerceIn(MIN_KBPS, MAX_KBPS)
    }

    private fun encodeTimeoutSeconds(durationSeconds: Double): Long =
        (durationSeconds * ENCODE_TIMEOUT_FACTOR).toLong().coerceAtLeast(MIN_ENCODE_TIMEOUT_SECONDS)

    private fun even(value: Int): Int = value - (value % 2)

    fun describe(plan: Plan?, probe: Probe?): String {
        var text = ""
        if (plan != null && probe != null) {
            text = if (plan.hasCrop) {
                String.format(Locale.US, "cropped %dpx of bars, %dx%d", probe.height - plan.cropHeight, plan.outWidth, plan.outHeight)
            } else {
                String.format(Locale.US, "%dx%d", plan.outWidth, plan.outHeight)
            }
        }
        return text
    }

    private companion object {
        const val HW_ENCODER: String = "h264_mediacodec"
        const val SW_ENCODER: String = "libx264"
        const val X264_PRESET: String = "veryfast"
        const val X264_CRF: Int = 26
        const val AUDIO_KBPS: Int = 96
        const val GOP_FRAMES: Int = 60
        const val MAX_LONG_SIDE_PX: Int = 1280
        const val BITS_PER_PIXEL: Double = 0.06
        const val MIN_KBPS: Int = 600
        const val MAX_KBPS: Int = 2500
        const val DEFAULT_FPS: Double = 30.0
        const val CROP_BLACK_LIMIT: Int = 24
        const val MIN_BARS_TOTAL_PX: Int = 32
        const val MIN_KEEP_RATIO: Double = 0.5
        const val ANALYSIS_WINDOW_SECONDS: Int = 120
        const val ANALYSIS_TIMEOUT_SECONDS: Long = 20
        const val ENCODE_TIMEOUT_FACTOR: Double = 4.0
        const val MIN_ENCODE_TIMEOUT_SECONDS: Long = 120

        val DURATION_PATTERN = Regex("""Duration: (\d+):(\d+):(\d+(?:\.\d+)?)""")
        val SIZE_PATTERN = Regex("""Video: .*?, (\d{2,5})x(\d{2,5})""")
        val FPS_PATTERN = Regex("""(\d+(?:\.\d+)?) fps""")
        val CROP_PATTERN = Regex("""crop=(\d+):(\d+):(\d+):(\d+)""")
        val OUT_TIME_PATTERN = Regex("""out_time_us=(\d+)""")
    }
}
