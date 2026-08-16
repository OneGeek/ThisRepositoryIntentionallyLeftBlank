package com.tamawatch.ui.common

import androidx.compose.foundation.Image
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

/**
 * Plays an idle animation but reshuffles between several variant tags, picking a
 * new one at random after each full loop — so the pet doesn't repeat one canned
 * idle forever. Honors reduce-motion (freezes on the first variant's frame 0).
 */
@Composable
fun PixelIdle(id: String, variants: List<String>, fps: Int = 3, modifier: Modifier = Modifier) {
    val bank = LocalSprites.current
    val reduce = LocalReduceMotion.current
    var tag by remember(id) { mutableStateOf(variants.first()) }
    var i by remember(id) { mutableIntStateOf(0) }
    LaunchedEffect(id, reduce) {
        if (reduce) { tag = variants.first(); i = 0; return@LaunchedEffect }
        while (true) {
            val frames = bank.tag(id, tag)
            for (f in frames.indices) { i = f; delay(1000L / fps) }
            tag = variants.random()          // next loop is a random alternate
            i = 0
        }
    }
    PixelFrame(id, bank.tag(id, tag).getOrElse(i) { 0 }, modifier)
}

@Composable
fun ProvidePixel(bank: SpriteBank, reduceMotion: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSprites provides bank,
        LocalReduceMotion provides reduceMotion,
        content = content,
    )
}
