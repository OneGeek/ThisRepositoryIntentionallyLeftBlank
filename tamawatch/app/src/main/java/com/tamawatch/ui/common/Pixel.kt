package com.tamawatch.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.tamawatch.assets.SpriteBank
import com.tamawatch.core.model.CoatStyle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

val LocalSprites = staticCompositionLocalOf<SpriteBank> { error("SpriteBank not provided") }
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Draws a pixel-art frame with nearest-neighbor scaling. */
@Composable
fun PixelFrame(id: String, frameIndex: Int = 0, modifier: Modifier = Modifier) {
    val bank = LocalSprites.current
    val img = bank.frame(id, frameIndex) ?: return
    Image(
        painter = BitmapPainter(img, filterQuality = FilterQuality.None),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

/** Animates through a tag's frames at [fps]; honors reduce-motion. */
@Composable
fun PixelSprite(id: String, tag: String = "idle", fps: Int = 3, modifier: Modifier = Modifier) {
    val bank = LocalSprites.current
    val reduce = LocalReduceMotion.current
    val frames = remember(id, tag) { bank.tag(id, tag) }
    var i by remember(id, tag) { mutableIntStateOf(0) }
    LaunchedEffect(id, tag, reduce) {
        if (reduce || frames.size <= 1) { i = 0; return@LaunchedEffect }
        while (true) {
            delay(1000L / fps)
            i = (i + 1) % frames.size
        }
    }
    PixelFrame(id, frames.getOrElse(i) { 0 }, modifier)
}

/**
 * The pet, animated like [PixelSprite] but with an optional [coat] pattern
 * painted over it. The base frame and the pattern are drawn into one offscreen
 * layer; the pattern uses [BlendMode.SrcAtop] so it only lands on opaque pixels
 * — i.e. it's clipped to the creature's silhouette for every species and pose.
 */
@Composable
fun PixelPetSprite(
    id: String,
    tag: String = "idle",
    coat: CoatStyle = CoatStyle.NONE,
    fps: Int = 3,
    modifier: Modifier = Modifier,
) {
    if (coat == CoatStyle.NONE) {
        PixelSprite(id, tag, fps, modifier)
        return
    }
    val bank = LocalSprites.current
    val reduce = LocalReduceMotion.current
    val frames = remember(id, tag) { bank.tag(id, tag) }
    var i by remember(id, tag) { mutableIntStateOf(0) }
    LaunchedEffect(id, tag, reduce) {
        if (reduce || frames.size <= 1) { i = 0; return@LaunchedEffect }
        while (true) {
            delay(1000L / fps)
            i = (i + 1) % frames.size
        }
    }
    val img = bank.frame(id, frames.getOrElse(i) { 0 }) ?: return
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val side = size.minDimension
        val dst = IntSize(side.roundToInt(), side.roundToInt())
        val left = ((size.width - side) / 2f).roundToInt()
        val top = ((size.height - side) / 2f).roundToInt()
        drawImage(
            image = img,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset(left, top),
            dstSize = dst,
            filterQuality = FilterQuality.None,
        )
        drawCoat(coat, left.toFloat(), top.toFloat(), side)
    }
}

/** Paints [coat] markings within the square [side] box at ([ox],[oy]). */
private fun DrawScope.drawCoat(coat: CoatStyle, ox: Float, oy: Float, side: Float) {
    fun px(fx: Float, fy: Float) = Offset(ox + fx * side, oy + fy * side)
    when (coat) {
        CoatStyle.TIGER -> {
            val ink = Color(0xCC2A1A0C)
            val w = side * 0.05f
            // near-vertical stripes bowing outward from the spine
            val xs = floatArrayOf(0.20f, 0.34f, 0.50f, 0.66f, 0.80f)
            xs.forEachIndexed { k, fx ->
                val bow = (fx - 0.5f) * side * 0.35f
                val p = Path().apply {
                    moveTo(px(fx, 0.14f).x, px(fx, 0.14f).y)
                    quadraticBezierTo(
                        px(fx, 0.5f).x + bow, px(fx, 0.5f).y,
                        px(fx, 0.9f).x, px(fx, 0.9f).y,
                    )
                }
                drawPath(p, ink, style = Stroke(width = w), blendMode = BlendMode.SrcAtop)
                if (k < xs.size - 1) {
                    // shorter half-stripe between the full ones
                    val mid = (fx + xs[k + 1]) / 2f
                    val hp = Path().apply {
                        moveTo(px(mid, 0.30f).x, px(mid, 0.30f).y)
                        quadraticBezierTo(
                            px(mid, 0.55f).x + (mid - 0.5f) * side * 0.25f, px(mid, 0.55f).y,
                            px(mid, 0.78f).x, px(mid, 0.78f).y,
                        )
                    }
                    drawPath(hp, ink, style = Stroke(width = w * 0.8f), blendMode = BlendMode.SrcAtop)
                }
            }
        }
        CoatStyle.LEOPARD -> {
            val ink = Color(0xD23A2410)
            val spots = listOf(
                Triple(0.28f, 0.30f, 0.11f), Triple(0.58f, 0.24f, 0.10f),
                Triple(0.74f, 0.44f, 0.10f), Triple(0.40f, 0.50f, 0.12f),
                Triple(0.22f, 0.62f, 0.10f), Triple(0.60f, 0.66f, 0.11f),
                Triple(0.44f, 0.80f, 0.09f),
            )
            for ((fx, fy, fr) in spots) {
                val c = px(fx, fy)
                val r = fr * side
                val ring = Stroke(width = side * 0.028f)
                // broken rosette ring (two arcs) + centre speck → body shows through
                drawArc(ink, 20f, 150f, false, Offset(c.x - r, c.y - r),
                    androidx.compose.ui.geometry.Size(r * 2, r * 2), style = ring, blendMode = BlendMode.SrcAtop)
                drawArc(ink, 200f, 120f, false, Offset(c.x - r, c.y - r),
                    androidx.compose.ui.geometry.Size(r * 2, r * 2), style = ring, blendMode = BlendMode.SrcAtop)
                drawCircle(ink, r * 0.22f, c, blendMode = BlendMode.SrcAtop)
            }
        }
        CoatStyle.TRIANGLE -> {
            val ink = Color(0xCC1E2030)
            val spots = listOf(
                Triple(0.30f, 0.28f, true), Triple(0.56f, 0.26f, false),
                Triple(0.72f, 0.46f, true), Triple(0.40f, 0.48f, false),
                Triple(0.24f, 0.60f, true), Triple(0.60f, 0.64f, false),
                Triple(0.46f, 0.80f, true),
            )
            val t = side * 0.11f
            for ((fx, fy, up) in spots) {
                val c = px(fx, fy)
                val dir = if (up) -1f else 1f
                val p = Path().apply {
                    moveTo(c.x, c.y + dir * t)
                    lineTo(c.x - t, c.y - dir * t)
                    lineTo(c.x + t, c.y - dir * t)
                    close()
                }
                drawPath(p, ink, blendMode = BlendMode.SrcAtop)
            }
        }
        CoatStyle.NONE -> Unit
    }
}

@Composable
fun ProvidePixel(bank: SpriteBank, reduceMotion: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSprites provides bank,
        LocalReduceMotion provides reduceMotion,
        content = content,
    )
}
