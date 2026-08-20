package com.tamawatch.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.tamawatch.tama
import com.tamawatch.ui.common.ProvidePixel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Dev tool: render each [PreviewShots] state through the real on-device Compose /
 * Skia pipeline (identical to what's on screen), capture it to a bitmap, composite
 * the set at native resolution, and save it — plus each individual frame — to the
 * watch's gallery (Pictures/TamaWatch via MediaStore). Because these are the SAME
 * states the JVM synthetic render uses, the two can be overlaid pixel-for-pixel.
 *
 * Reduce-motion is forced so animated sprites land on the same frame as the
 * synthetic. Captured full-window, so the output is at the watch's true resolution
 * and density — which is itself part of what a comparison against the synthetic
 * (rendered at a fixed qualifier) reveals.
 */
@Composable
fun RenderCaptureScreen(vm: TamaViewModel) {
    val context = LocalContext.current
    val bank = remember { context.tama.spriteBank }
    val shots = PreviewShots
    val layer = rememberGraphicsLayer()
    var index by remember { mutableIntStateOf(0) }
    val frames = remember { mutableStateListOf<Bitmap>() }
    var msg by remember { mutableStateOf("Rendering states…") }

    if (index < shots.size) {
        ProvidePixel(bank, reduceMotion = true) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
            ) {
                Box(Modifier.fillMaxSize().clip(CircleShape)) { shots[index].content(vm) }
            }
        }
    } else {
        Box(Modifier.fillMaxSize().background(Color.Black))
    }

    // Progress text — a sibling of the recorded Box, so it never lands in a capture.
    Box(Modifier.fillMaxSize().padding(bottom = 20.dp), contentAlignment = Alignment.BottomCenter) {
        Text(msg, style = MaterialTheme.typography.caption1, color = Color.White)
    }

    LaunchedEffect(index) {
        if (index < shots.size) {
            withFrameNanos { }            // let the new state lay out
            withFrameNanos { }            // and draw into the layer
            delay(60)
            val bmp = layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
            frames.add(bmp)
            msg = "Captured ${index + 1}/${shots.size}"
            index++
        } else {
            msg = "Saving to gallery…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val sheet = buildContactSheet(shots.map { it.label }, frames)
                    val sheetUri = saveBitmapToGallery(context, "tamawatch_states.png", sheet)
                        ?: error("MediaStore insert returned null")
                    frames.forEachIndexed { i, b ->
                        saveBitmapToGallery(context, "tamawatch_${shots[i].id}.png", b)
                    }
                    sheetUri
                }
            }
            msg = if (result.isSuccess) {
                "Saved ${frames.size} states + sheet to Gallery"
            } else {
                "Capture failed: ${result.exceptionOrNull()?.message}"
            }
            delay(2800)
            vm.endCapture()
        }
    }
}

/** Lay the captured frames out as a 2-column labeled sheet at native resolution. */
private fun buildContactSheet(labels: List<String>, bmps: List<Bitmap>): Bitmap {
    val cell = bmps.firstOrNull()?.width ?: 480
    val cols = 2
    val labelH = 48
    val pad = 24
    val gap = 20
    val rows = (bmps.size + cols - 1) / cols
    val w = pad * 2 + cols * cell + (cols - 1) * gap
    val h = pad * 2 + rows * (cell + labelH) + (rows - 1) * gap
    val sheet = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(sheet)
    canvas.drawColor(0xFF0C0E16.toInt())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF0F0F5.toInt()
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }
    bmps.forEachIndexed { i, b ->
        val col = i % cols
        val row = i / cols
        val x = pad + col * (cell + gap)
        val y = pad + row * (cell + labelH + gap)
        canvas.drawBitmap(b, x.toFloat(), y.toFloat(), null)
        canvas.drawText(labels.getOrElse(i) { "" }, (x + cell / 2).toFloat(), (y + cell + 34).toFloat(), paint)
    }
    return sheet
}

/** Save a bitmap as a PNG into Pictures/TamaWatch so it shows up in the gallery. */
private fun saveBitmapToGallery(context: Context, displayName: String, bmp: Bitmap): Uri? {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TamaWatch")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    values.clear()
    values.put(MediaStore.Images.Media.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    return uri
}
