package com.tamawatch.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import com.tamawatch.assets.SpriteBank
import kotlinx.coroutines.delay

val LocalSprites = staticCompositionLocalOf<SpriteBank> { error("SpriteBank not provided") }
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Draws an art frame with smooth (bilinear) scaling for clean, anti-aliased lines. */
@Composable
fun PixelFrame(id: String, frameIndex: Int = 0, modifier: Modifier = Modifier) {
    val bank = LocalSprites.current
    val img = bank.frame(id, frameIndex) ?: return
    Image(
        painter = BitmapPainter(img, filterQuality = FilterQuality.High),
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

/** The four idle loops; the home pet plays a random one each cycle. */
val PetIdleVariants = listOf("idle", "idle2", "idle3", "idle4")

/**
 * Renders the home pet. Creature frames carry transparent headroom (a 2:3 cell)
 * so jumps/antennae never clip: this fills the width and lets the extra height
 * overflow upward (align it to the bottom so the feet stay put and the head can
 * rise behind the status). For "idle" it reshuffles among the four idle variants
 * at random after each loop; any other tag plays straight. Honors reduce-motion.
 */
@Composable
fun PetSprite(id: String, tag: String, fps: Int = 3, modifier: Modifier = Modifier) {
    val bank = LocalSprites.current
    val reduce = LocalReduceMotion.current
    val info = bank.info(id)
    val ar = if (info != null && info.frameH != 0) info.frameW.toFloat() / info.frameH else 1f
    val variants = if (tag == "idle") PetIdleVariants else listOf(tag)
    var curTag by remember(id, tag) { mutableStateOf(variants.first()) }
    var i by remember(id, tag) { mutableIntStateOf(0) }
    LaunchedEffect(id, tag, reduce) {
        if (reduce) { curTag = variants.first(); i = 0; return@LaunchedEffect }
        while (true) {
            val frames = bank.tag(id, curTag)
            for (f in frames.indices) { i = f; delay(1000L / fps) }
            curTag = variants.random()
            i = 0
        }
    }
    val idx = bank.tag(id, curTag).getOrElse(i) { 0 }
    val img = bank.frame(id, idx) ?: return
    Image(
        painter = BitmapPainter(img, filterQuality = FilterQuality.High),
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = modifier.fillMaxWidth().aspectRatio(ar),
    )
}

@Composable
fun ProvidePixel(bank: SpriteBank, reduceMotion: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSprites provides bank,
        LocalReduceMotion provides reduceMotion,
        content = content,
    )
}
