package com.tamawatch.assets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
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
 * A creature's runtime rig: the ids of its separately-composited part layers plus
 * their attach anchors (normalized 0..1 of the sprite cell). Lets the renderer
 * deform the body while merely shifting the face/arms/feature, and slide the
 * shadow horizontally. Anchors are authored once by generate_parts.py, so the
 * runtime never hard-codes face geometry.
 */
data class PartRig(
    val shadow: String,
    val feature: String,
    val body: String,
    val arms: String,
    val face: String,
    val faceBlink: String,
    val anchors: Map<String, Offset>,
) {
    fun anchor(name: String): Offset = anchors[name] ?: Offset(0.5f, 0.5f)
}

/**
 * Loads generated pixel-art frame-strips using the manifest so frame geometry
 * is never hard-coded (closes the art/code drift risk, impl-plan §4/§10).
 * Frames are sliced lazily and cached.
 */
class SpriteBank(private val context: Context) {
    private val root: JSONObject = JSONObject(
        context.assets.open("tamawatch/assets_manifest.json").bufferedReader().use { it.readText() }
    )
    private val info: Map<String, SpriteInfo> = loadManifest()
    private val rigs: Map<String, PartRig> = loadParts()
    private val sheetCache = HashMap<String, Bitmap>()
    private val frameCache = HashMap<String, ImageBitmap>()

    private fun loadManifest(): Map<String, SpriteInfo> {
        val sprites = root.getJSONObject("sprites")
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

    private fun loadParts(): Map<String, PartRig> {
        val parts = root.optJSONObject("parts") ?: return emptyMap()
        val out = HashMap<String, PartRig>()
        for (id in parts.keys()) {
            val o = parts.getJSONObject(id)
            val anchorsObj = o.getJSONObject("anchors")
            val anchors = HashMap<String, Offset>()
            for (k in anchorsObj.keys()) {
                val a = anchorsObj.getJSONArray(k)
                anchors[k] = Offset(a.getDouble(0).toFloat(), a.getDouble(1).toFloat())
            }
            out[id] = PartRig(
                shadow = o.getString("shadow"), feature = o.getString("feature"),
                body = o.getString("body"), arms = o.getString("arms"),
                face = o.getString("face"), faceBlink = o.getString("faceBlink"),
                anchors = anchors,
            )
        }
        return out
    }

    /** The part rig for a creature sprite id (e.g. "spr_baby"), or null if unrigged. */
    fun parts(id: String): PartRig? = rigs[id]

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
