package com.tamawatch.assets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONObject

/** One sprite sheet's geometry, read from assets_manifest.json. */
data class SpriteInfo(
    val file: String,
    val frameW: Int,
    val frameH: Int,
    val frames: Int,
    val tags: Map<String, List<Int>>,
)

/**
 * Loads generated pixel-art frame-strips using the manifest so frame geometry
 * is never hard-coded (closes the art/code drift risk, impl-plan §4/§10).
 * Frames are sliced lazily and cached.
 */
class SpriteBank(private val context: Context) {
    private val info: Map<String, SpriteInfo> = loadManifest()
    private val sheetCache = HashMap<String, Bitmap>()
    private val frameCache = HashMap<String, ImageBitmap>()

    private fun loadManifest(): Map<String, SpriteInfo> {
        val json = context.assets.open("tamawatch/assets_manifest.json")
            .bufferedReader().use { it.readText() }
        val sprites = JSONObject(json).getJSONObject("sprites")
        val out = HashMap<String, SpriteInfo>()
        for (id in sprites.keys()) {
            val o = sprites.getJSONObject(id)
            val tags = HashMap<String, List<Int>>()
            o.optJSONObject("tags")?.let { t ->
                for (k in t.keys()) {
                    val arr = t.getJSONArray(k)
                    tags[k] = (0 until arr.length()).map { arr.getInt(it) }
                }
            }
            out[id] = SpriteInfo(o.getString("file"), o.getInt("frameW"),
                o.getInt("frameH"), o.getInt("frames"), tags)
        }
        return out
    }

    fun info(id: String): SpriteInfo? = info[id]

    fun frameCount(id: String): Int = info[id]?.frames ?: 1

    /** Frame indices for a named tag (e.g. "idle", "walk"), or [0] if unknown. */
    fun tag(id: String, name: String): List<Int> =
        info[id]?.tags?.get(name) ?: listOf(0)

    private fun sheet(id: String): Bitmap? {
        sheetCache[id]?.let { return it }
        val resId = context.resources.getIdentifier(id, "drawable", context.packageName)
        if (resId == 0) return null
        val opts = BitmapFactory.Options().apply { inScaled = false }  // keep pixel art crisp
        val bmp = BitmapFactory.decodeResource(context.resources, resId, opts) ?: return null
        sheetCache[id] = bmp
        return bmp
    }

    fun frame(id: String, index: Int): ImageBitmap? {
        val meta = info[id] ?: return sheet(id)?.asImageBitmap()
        val i = if (meta.frames > 0) index % meta.frames else 0
        val key = "$id#$i"
        frameCache[key]?.let { return it }
        val sheet = sheet(id) ?: return null
        val x = i * meta.frameW
        if (x + meta.frameW > sheet.width) return sheet.asImageBitmap()
        val sub = Bitmap.createBitmap(sheet, x, 0, meta.frameW, meta.frameH)
        val img = sub.asImageBitmap()
        frameCache[key] = img
        return img
    }
}
