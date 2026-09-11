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
        val cropY: Int,
        val sampleCount: Int = 0,
        val protectedOutliers: Int = 0,
        val ignoredOutliers: Int = 0
    )

    class Plan(val cropTop: Int, val cropHeight: Int, val outWidth: Int, val outHeight: Int, val hasCrop: Boolean)

    class Outcome(val file: File, val plan: Plan?, val encoder: String?, val note: String, val probe: Probe? = null)

    /** Stream facts from the first analysis pass. */
    class Header(val durationSeconds: Double, val width: Int, val height: Int, val fps: Double)

    /** Per-keyframe content box, vertical extent only. */
    class Sample(val top: Int, val bottom: Int, val time: Double)

    class Box(val top: Int, val bottom: Int)

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

    /**
     * Pass 1: keyframes only, one cropdetect box per frame. The majority box is a percentile over
     * those boxes, so a few frames of full-frame transition cannot veto the crop.
     * Pass 2 (only if some frames extend past the majority box): measure how black the bar strips
     * are on those frames. A caption on a black bar leaves the strip mostly black and is protected;
     * a picture filling the strip is a transition and is cropped through.
     */
    fun analyze(input: File): Probe? {
        var probe: Probe? = null
        val first = ffmpeg.run(cropDetectArgs(input), ANALYSIS_TIMEOUT_SECONDS)
        val header: Header? = parseHeader(first.stderr)
        if (header != null) {
            val samples: List<Sample> = parseSamples(first.stderr)
            val majority: Box = majorityBox(samples, header.height)
            val outliers: List<Sample> = samples.filter { isOutlier(it, majority) }
            var finalBox: Box = majority
            var protectedCount = 0
            if (outliers.isNotEmpty()) {
                val refined: Pair<Box, Int> = refineWithBlackness(input, header, majority, outliers)
                finalBox = refined.first
                protectedCount = refined.second
            }
            probe = Probe(
                durationSeconds = header.durationSeconds,
                width = header.width,
                height = header.height,
                fps = header.fps,
                cropWidth = header.width,
                cropHeight = finalBox.bottom - finalBox.top,
                cropX = 0,
                cropY = finalBox.top,
                sampleCount = samples.size,
                protectedOutliers = protectedCount,
                ignoredOutliers = outliers.size - protectedCount
            )
        }
        return probe
    }

    private fun cropDetectArgs(input: File): List<String> = listOf(
        "-nostats",
        "-skip_frame", "nokey",
        "-t", ANALYSIS_WINDOW_SECONDS.toString(),
        "-i", input.absolutePath,
        "-vf", "cropdetect=limit=$CROP_BLACK_LIMIT:round=2:reset=1",
        "-an", "-f", "null", "-"
    )

    fun parseHeader(stderr: String): Header? {
        var header: Header? = null
        val duration = DURATION_PATTERN.find(stderr)
        val size = SIZE_PATTERN.find(stderr)
        if (duration != null && size != null) {
            val seconds: Double = duration.groupValues[1].toDouble() * 3600 +
                duration.groupValues[2].toDouble() * 60 +
                duration.groupValues[3].toDouble()
            val fps: Double = FPS_PATTERN.find(stderr)?.groupValues?.get(1)?.toDoubleOrNull() ?: DEFAULT_FPS
            header = Header(seconds, size.groupValues[1].toInt(), size.groupValues[2].toInt(), fps)
        }
        return header
    }

    /** Near-black frames make cropdetect report a negative width; those carry no information and are dropped. */
    fun parseSamples(stderr: String): List<Sample> = CROP_LINE_PATTERN.findAll(stderr)
        .map { m ->
            val time: Double = m.groupValues[1].toDouble()
            val width: Int = m.groupValues[2].toInt()
            val height: Int = m.groupValues[3].toInt()
            val y: Int = m.groupValues[5].toInt()
            Sample(top = y, bottom = y + height, time = time).takeIf { width > 0 && height > 0 }
        }
        .filterNotNull()
        .toList()

    fun majorityBox(samples: List<Sample>, frameHeight: Int): Box {
        var box = Box(0, frameHeight)
        if (samples.size >= MIN_CROP_SAMPLES) {
            val tops: List<Int> = samples.map { it.top }.sorted()
            val bottoms: List<Int> = samples.map { it.bottom }.sorted()
            box = Box(percentile(tops, CROP_KEEP_PERCENTILE), percentile(bottoms, 1.0 - CROP_KEEP_PERCENTILE))
        } else if (samples.isNotEmpty()) {
            box = Box(samples.minOf { it.top }, samples.maxOf { it.bottom })
        }
        return box
    }

    private fun percentile(sorted: List<Int>, q: Double): Int {
        val index: Int = (q * sorted.size).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    private fun isOutlier(sample: Sample, box: Box): Boolean =
        sample.top < box.top - OUTLIER_TOLERANCE_PX || sample.bottom > box.bottom + OUTLIER_TOLERANCE_PX

    /** Returns the widened box and how many outliers were protected as captions or graphics. */
    private fun refineWithBlackness(input: File, header: Header, box: Box, outliers: List<Sample>): Pair<Box, Int> {
        val filters = mutableListOf<String>()
        val maps = mutableListOf<String>()
        val hasTopStrip: Boolean = box.top > 0
        val hasBottomStrip: Boolean = box.bottom < header.height
        if (hasTopStrip) {
            filters.add("[a]crop=${header.width}:${box.top}:0:0,blackframe=amount=0:threshold=$CROP_BLACK_LIMIT[top]")
            maps.addAll(listOf("-map", "[top]", "-f", "null", "-"))
        }
        if (hasBottomStrip) {
            val stripHeight: Int = header.height - box.bottom
            filters.add("[b]crop=${header.width}:$stripHeight:0:${box.bottom},blackframe=amount=0:threshold=$CROP_BLACK_LIMIT[bot]")
            maps.addAll(listOf("-map", "[bot]", "-f", "null", "-"))
        }
        val graph: String = "[0:v]split=2[a][b];" + filters.joinToString(";")
        val args: List<String> = listOf(
            "-nostats",
            "-skip_frame", "nokey",
            "-t", ANALYSIS_WINDOW_SECONDS.toString(),
            "-i", input.absolutePath,
            "-filter_complex", graph
        ) + maps
        val result = ffmpeg.run(args, ANALYSIS_TIMEOUT_SECONDS)

        val byInstance: Map<Int, List<Pair<Int, Double>>> = parseBlackness(result.stderr)
        val instances: List<Int> = byInstance.keys.sorted()
        val topStats: List<Pair<Int, Double>>? = if (hasTopStrip) byInstance[instances.firstOrNull()] else null
        val bottomStats: List<Pair<Int, Double>>? = if (hasBottomStrip) byInstance[instances.lastOrNull()] else null

        var top: Int = box.top
        var bottom: Int = box.bottom
        var protectedCount = 0
        for (sample in outliers) {
            var protectedHere = false
            if (sample.top < box.top - OUTLIER_TOLERANCE_PX && isMostlyBlack(topStats, sample.time)) {
                top = minOf(top, sample.top)
                protectedHere = true
            }
            if (sample.bottom > box.bottom + OUTLIER_TOLERANCE_PX && isMostlyBlack(bottomStats, sample.time)) {
                bottom = maxOf(bottom, sample.bottom)
                protectedHere = true
            }
            if (protectedHere) {
                protectedCount++
            }
        }
        return Pair(Box(top, bottom), protectedCount)
    }

    /** blackframe lines: `[Parsed_blackframe_N @ ...] frame:.. pblack:NN pts:.. t:T ...`, one filter instance per strip. */
    fun parseBlackness(stderr: String): Map<Int, List<Pair<Int, Double>>> = BLACKFRAME_PATTERN.findAll(stderr)
        .groupBy(
            keySelector = { it.groupValues[1].toInt() },
            valueTransform = { Pair(it.groupValues[2].toInt(), it.groupValues[3].toDouble()) }
        )

    private fun isMostlyBlack(stats: List<Pair<Int, Double>>?, time: Double): Boolean {
        var mostlyBlack = false
        val match: Pair<Int, Double>? = stats?.firstOrNull { kotlin.math.abs(it.second - time) < TIME_MATCH_TOLERANCE_SECONDS }
        if (match != null) {
            mostlyBlack = match.first >= BAR_BLACK_KEEP_PERCENT
        }
        return mostlyBlack
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

        var outcome = Outcome(input, plan, null, "all encoders failed", probe)
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
                outcome = chooseSmaller(input, output, probe, plan, name)
            }
            index++
        }
        return outcome
    }

    private fun chooseSmaller(input: File, output: File, probe: Probe, plan: Plan, encoder: String): Outcome {
        val encodedIsUseful: Boolean = plan.hasCrop || output.length() < input.length()
        return if (encodedIsUseful) {
            Outcome(output, plan, encoder, "encoded with $encoder", probe)
        } else {
            output.delete()
            Outcome(input, plan, encoder, "encoded file was not smaller; kept original", probe)
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
                String.format(
                    Locale.US, "cropped %dpx of bars, %dx%d (%d samples, %d protected, %d ignored)",
                    probe.height - plan.cropHeight, plan.outWidth, plan.outHeight,
                    probe.sampleCount, probe.protectedOutliers, probe.ignoredOutliers
                )
            } else {
                String.format(Locale.US, "%dx%d (%d samples)", plan.outWidth, plan.outHeight, probe.sampleCount)
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
        const val CROP_KEEP_PERCENTILE: Double = 0.20
        const val MIN_CROP_SAMPLES: Int = 4
        const val OUTLIER_TOLERANCE_PX: Int = 2
        const val BAR_BLACK_KEEP_PERCENT: Int = 60
        const val TIME_MATCH_TOLERANCE_SECONDS: Double = 0.02
        const val ANALYSIS_WINDOW_SECONDS: Int = 120
        const val ANALYSIS_TIMEOUT_SECONDS: Long = 20
        const val ENCODE_TIMEOUT_FACTOR: Double = 4.0
        const val MIN_ENCODE_TIMEOUT_SECONDS: Long = 120

        val DURATION_PATTERN = Regex("""Duration: (\d+):(\d+):(\d+(?:\.\d+)?)""")
        val SIZE_PATTERN = Regex("""Video: .*?, (\d{2,5})x(\d{2,5})""")
        val FPS_PATTERN = Regex("""(\d+(?:\.\d+)?) fps""")
        val CROP_LINE_PATTERN = Regex("""t:(\d+(?:\.\d+)?) .*?crop=(-?\d+):(-?\d+):(-?\d+):(-?\d+)""")
        val BLACKFRAME_PATTERN = Regex("""Parsed_blackframe_(\d+) .*?pblack:(\d+) .*?t:(\d+(?:\.\d+)?)""")
        val OUT_TIME_PATTERN = Regex("""out_time_us=(\d+)""")
    }
}
