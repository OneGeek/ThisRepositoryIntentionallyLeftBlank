package com.tamawatch.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamawatch.assets.PartRig
import com.tamawatch.core.model.*
import com.tamawatch.ui.common.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

private const val PET_MAX_STRETCH = 0.42f   // max elongation along the drag axis
private const val PET_DRAG_FOLLOW = 0.5f    // how far the pet translates toward the drag

/** Angle of the drag vector in degrees (0 when there's essentially no drag). */
private fun dragAngleDeg(x: Float, y: Float): Float {
    val m = kotlin.math.hypot(x, y)
    return if (m > 0.5f) Math.toDegrees(kotlin.math.atan2(y, x).toDouble()).toFloat() else 0f
}

/** Stretch amount (0..PET_MAX_STRETCH) from the drag magnitude. */
private fun dragStretch(x: Float, y: Float, maxPx: Float): Float {
    val m = kotlin.math.hypot(x, y)
    return (m / maxPx).coerceIn(0f, 1f) * PET_MAX_STRETCH
}

/** Clamp an offset's length to [maxLen], keeping direction. */
private fun clampLen(o: Offset, maxLen: Float): Offset {
    val m = o.getDistance()
    return if (m <= maxLen || m == 0f) o else o * (maxLen / m)
}

/** The pet's current sprite id + animation tag from its state. */
fun poseFor(pet: Pet): Pair<String, String> {
    val base = pet.species.spriteId
    val set = pet.species.stagesetId() ?: base
    return when {
        pet.stage == Stage.EGG -> base to "wiggle"
        !pet.alive -> set to "sleep"
        pet.asleep -> set to "sleep"
        pet.stats.sick -> set to "sick"
        pet.needsAttention() -> set to "call"
        pet.stats.happy >= 80 -> base to "happy"
        else -> base to "idle"
    }
}

/** Selectable floor rugs the pet/egg stands on. id 0 = no rug. */
data class RugStyle(val id: Int, val asset: String?, val name: String)

val Rugs = listOf(
    RugStyle(0, null, "None"),
    RugStyle(1, "rug_rose", "Rose"),
    RugStyle(2, "rug_sky", "Sky"),
    RugStyle(3, "rug_moss", "Moss"),
    RugStyle(4, "rug_cream", "Cream"),
    RugStyle(5, "rug_royal", "Royal"),
    RugStyle(6, "rug_night", "Night"),
)

fun rugAsset(id: Int): String? = Rugs.firstOrNull { it.id == id }?.asset
fun rugName(id: Int): String = Rugs.firstOrNull { it.id == id }?.name ?: "None"

/** A selected rug plus (optionally) a soft contact shadow, drawn under the feet.
 *  Creature sprites already carry their own baked grounding shadow, so pass
 *  shadow=false when a creature stands on the rug to avoid a doubled shadow. */
@Composable
fun Ground(rugId: Int, width: Dp, modifier: Modifier = Modifier, shadow: Boolean = true) {
    Box(modifier.width(width), contentAlignment = Alignment.BottomCenter) {
        rugAsset(rugId)?.let { PixelFrame(it, 0, Modifier.fillMaxWidth()) }
        if (shadow) PixelFrame("fx_shadow", 0, Modifier.fillMaxWidth(0.86f))
    }
}

@Composable
fun HomeScreen(vm: TamaViewModel, pet: Pet, ownsBeach: Boolean, ownsSpace: Boolean, rugId: Int, coatId: Int) {
    val bg = when {
        pet.stage == Stage.EGG -> "bg_egg"
        pet.asleep || !pet.lightOn -> "bg_room_night"
        ownsSpace -> "cos_bg_space"
        ownsBeach -> "cos_bg_beach"
        else -> "bg_room_day"
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PixelFrame(bg, 0, Modifier.fillMaxSize())

        if (pet.stage == Stage.EGG) {
            TopStatus(pet)
            Column(Modifier.offset(y = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.BottomCenter) {
                    Ground(rugId, 116.dp, Modifier.align(Alignment.BottomCenter).offset(y = 6.dp))
                    PixelSprite("spr_egg", "wiggle", 2, Modifier.size(104.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text("${pet.name}'s egg…", style = MaterialTheme.typography.caption1)
                Spacer(Modifier.height(4.dp))
                CompactChip(onClick = { vm.hatchNow() }, label = { Text("Hatch now") })
            }
            return@Box
        }

        // Centered pet, the touch-first radial menu around it, then the status ON
        // TOP so a jumping pet slips behind the meters instead of being clipped.
        PetCenter(vm, pet, rugId, coatId)
        RadialMenu(vm, pet)
        TopStatus(pet)
    }
}

/** The top status meters (Hunger shanks / Happy smileys / GP), drawn Minecraft-style. */
@Composable
private fun BoxScope.TopStatus(pet: Pet) {
    Column(
        Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconMeter("ic_hunger", "ic_hunger_empty", pet.stats.hungerHearts)
        Spacer(Modifier.height(2.dp))
        IconMeter("ic_mood", "ic_mood_empty", pet.stats.happyHearts)
        Spacer(Modifier.height(2.dp))
        GpBadge(pet.gp, pet.stepsToday)
    }
}

/**
 * The centered pet on its rug. Tap it to pet it — the pet gives a clear bounce +
 * a heart that pops up. Petting only lands a happiness gain once per cooldown; in
 * between, the pet wears a small "content" heart and taps give a gentler nudge, so
 * the throttle reads as a mood, never a timer.
 */
@Composable
private fun BoxScope.PetCenter(vm: TamaViewModel, pet: Pet, rugId: Int, coatId: Int) {
    val (id, tag) = poseFor(pet)
    // The interactive idle pose is drawn from its part rig (body deforms; face,
    // arms, feature merely shift; shadow slides horizontally). Other poses keep
    // their richer baked-frame animation and the whole-bitmap stretch.
    val rig = if (tag == "idle") LocalSprites.current.parts(id) else null
    val reduce = LocalReduceMotion.current
    val lastPetMs by vm.lastPetMs.collectAsStateWithLifecycle()
    val annoyedUntilMs by vm.annoyedUntilMs.collectAsStateWithLifecycle()

    // Wall-clock ticker: only runs while a cooldown is active so the "content"
    // state clears itself. Never shown as a number — it just gates the mood cue.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastPetMs) {
        while (System.currentTimeMillis() - lastPetMs < Tuning.PET_COOLDOWN_MS) {
            now = System.currentTimeMillis()
            delay(250)
        }
        now = System.currentTimeMillis()
    }
    // Over-petting mood takes precedence over the content heart while it lasts.
    val annoyed = pet.alive && !pet.asleep && now < annoyedUntilMs
    val content = pet.alive && !pet.asleep && !annoyed && now - lastPetMs < Tuning.PET_COOLDOWN_MS

    // One-shot tap reaction: a squash-and-stretch bounce, bigger when it counts.
    var reactKey by remember { mutableIntStateOf(0) }
    var reactEffective by remember { mutableStateOf(true) }
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(reactKey) {
        if (reactKey == 0) return@LaunchedEffect
        val peak = if (reactEffective) 1.18f else 1.07f
        bounce.snapTo(1f)
        bounce.animateTo(peak, tween(90))
        bounce.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f))
    }

    // Procedural grab-and-stretch: dragging the pet elongates it toward the drag
    // (with a perpendicular squash) and translates it a little toward the finger;
    // letting go springs it back with a wobble. An EXTRA layer over the frame
    // animation + the tap bounce. `live` is the drag offset (px); the release
    // spring animates it back to zero.
    var live by remember { mutableStateOf(Offset.Zero) }
    var springJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()
    val maxDragPx = with(LocalDensity.current) { 96.dp.toPx() } * 0.72f
    val feet = TransformOrigin(0.5f, 1f)

    // The rug/ground is its own element, pinned at the grounded spot near the
    // bottom. It is deliberately independent of the pet: raising the pet must
    // not lift the floor, so this stays put while the pet box sits higher.
    Box(
        Modifier.align(Alignment.Center).offset(y = 14.dp).size(96.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // shadow=false: the creature sprite already carries its own baked shadow,
        // so the rug here is just the mat (no second, floating shadow).
        Ground(rugId, 118.dp, Modifier.align(Alignment.BottomCenter).offset(y = 4.dp), shadow = false)
        // Poop belongs on the floor, so it lives with the rug, not the raised pet.
        if (pet.stats.dirty) PixelSprite("ov_poop", "idle", 2, Modifier.align(Alignment.BottomStart).size(22.dp))
    }

    Box(
        Modifier
            .align(Alignment.Center)
            .offset(y = (-14).dp)
            .size(96.dp)
            .pointerInput(pet.species, reduce) {
                // One gesture handles both: a drag stretches the pet (unless
                // reduce-motion); a press with no drag pets it (the old tap).
                val maxDrag = size.width * 0.72f
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    springJob?.cancel()          // grab cleanly, cutting any bounce
                    var raw = Offset.Zero
                    var dragging = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (ch.pressed) {
                            raw += ch.positionChange()
                            if (!reduce && !dragging && raw.getDistance() > viewConfiguration.touchSlop) {
                                dragging = true
                            }
                            if (dragging) {
                                live = clampLen(raw, maxDrag)
                                ch.consume()
                            }
                        } else {
                            if (dragging) {
                                springJob = scope.launch {
                                    val anim = Animatable(live, Offset.VectorConverter)
                                    anim.animateTo(Offset.Zero, spring(dampingRatio = 0.3f, stiffness = 340f)) {
                                        live = value
                                    }
                                }
                            }
                            when {
                                // Touching a sleeping pet wakes it (no fuss/happiness).
                                pet.asleep -> vm.wake()
                                // A drag is a fuss too — it makes the pet happy like a tap.
                                dragging -> vm.petIt()
                                else -> {
                                    reactEffective = System.currentTimeMillis() - lastPetMs >= Tuning.PET_COOLDOWN_MS
                                    reactKey++
                                    vm.petIt()
                                }
                            }
                            break
                        }
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Feet pinned to the bottom of this (raised) box; headroom overflows up.
        if (rig != null) {
            // Part-composited idle pet: per-part stretch + horizontal-only shadow.
            RiggedPet(rig, coatId, live, maxDragPx, if (reduce) 1f else bounce.value, reduce)
        } else {
            // Baked-frame path. Transform stack (outer→inner): grab-follow translation,
            // a directional stretch (rotate to the drag axis, scale, rotate back), the
            // tap bounce, then the coat clipped to the whole silhouette.
            PetSprite(
                id, tag, 3,
                Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        translationX = live.x * PET_DRAG_FOLLOW
                        translationY = live.y * PET_DRAG_FOLLOW
                    }
                    .graphicsLayer {
                        rotationZ = dragAngleDeg(live.x, live.y)
                        transformOrigin = feet
                    }
                    .graphicsLayer {
                        val amt = dragStretch(live.x, live.y, maxDragPx)
                        scaleX = 1f + amt
                        scaleY = 1f - amt * 0.55f
                        transformOrigin = feet
                    }
                    .graphicsLayer {
                        rotationZ = -dragAngleDeg(live.x, live.y)
                        transformOrigin = feet
                    }
                    .graphicsLayer {
                        val s = if (reduce) 1f else bounce.value
                        scaleX = s; scaleY = s
                        transformOrigin = feet
                    }
                    .drawWithContent {
                        if (coatId == 0) {
                            drawContent()
                        } else {
                            val pad = 64.dp.toPx()
                            drawContext.canvas.saveLayer(
                                Rect(-pad, -pad, size.width + pad, size.height + pad),
                                Paint(),
                            )
                            drawContent()
                            drawCoat(coatId)
                            drawContext.canvas.restore()
                        }
                    },
            )
        }
        if (pet.stats.sick) PixelSprite("ov_sick_skull", "blink", 3, Modifier.align(Alignment.TopEnd).size(18.dp))
        if (pet.asleep) PixelSprite("ov_zzz", "idle", 2, Modifier.align(Alignment.TopEnd).size(22.dp))
        if (pet.needsAttention() && !pet.asleep) PixelSprite("ov_call", "blink", 3, Modifier.align(Alignment.TopEnd).size(16.dp))
        // Pester the pet too much and it gets annoyed: angry brows knit over its
        // eyes and a 💢 pops on its forehead, outranking the content heart until the
        // mood passes.
        if (annoyed) {
            AngerOverlay(rig?.anchor("face")?.y ?: 0.627f)
        } else if (content) {
            // Cooldown mood: a small steady heart, so it's clear the pet is content
            // and more petting won't add happiness right now.
            PixelSprite(
                "ov_heart_particle", "rise", 2,
                Modifier.align(Alignment.TopCenter).offset(y = 2.dp).size(18.dp).alpha(0.7f),
            )
        }
        // The tap burst: a heart that springs up and fades on an effective pet.
        PetTapBurst(reactKey, reactEffective && !reduce)
    }
}

private const val PET_SHADOW_FOLLOW = 0.28f   // how far the cast shadow slides with a horizontal drag

/** Angle of the drag vector in radians (0 when there's essentially no drag). */
private fun dragAngleRad(x: Float, y: Float): Float {
    val m = kotlin.math.hypot(x, y)
    return if (m > 0.5f) kotlin.math.atan2(y, x) else 0f
}

/**
 * The displacement to translate a part by so it *rides* the body's directional
 * stretch while keeping its own shape: the body's R(θ)·S·R(−θ) about the feet
 * pivot, applied to the part's anchor, minus the anchor. All in px.
 */
private fun stretchDelta(anchor: Offset, pivot: Offset, angle: Float, amt: Float): Offset {
    val vx = anchor.x - pivot.x
    val vy = anchor.y - pivot.y
    val c = cos(-angle); val s = sin(-angle)
    val rx = vx * c - vy * s
    val ry = vx * s + vy * c
    val sxp = rx * (1f + amt)
    val syp = ry * (1f - amt * 0.55f)
    val c2 = cos(angle); val s2 = sin(angle)
    val bx = sxp * c2 - syp * s2
    val by = sxp * s2 + syp * c2
    return Offset(bx - vx, by - vy)
}

/** An accessory pinned to a rig slot anchor (e.g. "headTop", "handR"), riding the
 *  body's stretch shift. The building block for hats / held-item customization. */
data class RigAttachment(val anchor: String, val content: @Composable () -> Unit)

/**
 * The home pet, composited from its rig parts instead of a single flat frame, so a
 * drag can deform the body (and its coat) while merely *shifting* the face, arms and
 * feature — keeping their shape — and sliding the shadow horizontally only, like a
 * real cast shadow. Layers back→front: shadow · feature · body(+coat) · arms · face.
 * The container carries the whole-pet drag-follow + tap bounce; per-part transforms
 * ride on top. A slow breath + occasional blink keep it alive (unless reduce-motion).
 * Anchors come from the manifest, so no face geometry is hard-coded here.
 */
@Composable
internal fun BoxScope.RiggedPet(
    rig: PartRig,
    coatId: Int,
    live: Offset,
    maxDragPx: Float,
    bounce: Float,
    reduce: Boolean,
    attachments: List<RigAttachment> = emptyList(),
) {
    val feetA = rig.anchor("feet")
    val feetOrigin = TransformOrigin(feetA.x, feetA.y)

    val breath = remember { Animatable(0f) }
    var blink by remember { mutableStateOf(false) }
    LaunchedEffect(reduce) {
        if (reduce) { breath.snapTo(0f); blink = false; return@LaunchedEffect }
        launch { while (true) { breath.animateTo(1f, tween(1900)); breath.animateTo(0f, tween(1900)) } }
        while (true) { delay(3200); blink = true; delay(120); blink = false }
    }
    val breatheY = if (reduce) 0f else 0.02f * sin(breath.value * Math.PI.toFloat())

    BoxWithConstraints(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .graphicsLayer {                       // whole-pet: drag-follow + tap bounce
                translationX = live.x * PET_DRAG_FOLLOW
                translationY = live.y * PET_DRAG_FOLLOW
                scaleX = bounce; scaleY = bounce
                transformOrigin = feetOrigin
            },
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val pivot = Offset(feetA.x * w, feetA.y * h)
        val angle = if (reduce) 0f else dragAngleRad(live.x, live.y)
        val angleDeg = Math.toDegrees(angle.toDouble()).toFloat()
        val amt = if (reduce) 0f else dragStretch(live.x, live.y, maxDragPx)
        fun shift(name: String): Offset {
            val a = rig.anchor(name)
            return stretchDelta(Offset(a.x * w, a.y * h), pivot, angle, amt)
        }

        // shadow — slides horizontally only; never squashes with the body
        val shadowDx = if (reduce) 0f else live.x * PET_SHADOW_FOLLOW
        PixelFrame(rig.shadow, 0, Modifier.matchParentSize().graphicsLayer { translationX = shadowDx })

        // feature (behind the body) — shift, keep shape
        val fd = shift("feature")
        PixelFrame(rig.feature, 0, Modifier.matchParentSize().graphicsLayer { translationX = fd.x; translationY = fd.y })

        // body (+ coat) — the deformable mass
        PixelFrame(
            rig.body, 0,
            Modifier.matchParentSize()
                .graphicsLayer { rotationZ = angleDeg; transformOrigin = feetOrigin }
                .graphicsLayer {
                    scaleX = 1f + amt
                    scaleY = (1f - amt * 0.55f) + breatheY
                    transformOrigin = feetOrigin
                }
                .graphicsLayer { rotationZ = -angleDeg; transformOrigin = feetOrigin }
                .drawWithContent {
                    if (coatId == 0) {
                        drawContent()
                    } else {
                        val pad = 64.dp.toPx()
                        drawContext.canvas.saveLayer(Rect(-pad, -pad, size.width + pad, size.height + pad), Paint())
                        drawContent()
                        drawCoat(coatId)
                        drawContext.canvas.restore()
                    }
                },
        )

        // arms (hands) — shift, keep shape
        val ad = shift("arms")
        PixelFrame(rig.arms, 0, Modifier.matchParentSize().graphicsLayer { translationX = ad.x; translationY = ad.y })

        // face — shift, keep shape; swap to the blink face on the idle blink
        val fc = shift("face")
        PixelFrame(
            if (blink) rig.faceBlink else rig.face, 0,
            Modifier.matchParentSize().graphicsLayer { translationX = fc.x; translationY = fc.y },
        )

        // accessories parented to a slot anchor: centered on the (shifted) anchor
        attachments.forEach { at ->
            val a = rig.anchor(at.anchor)
            val d = shift(at.anchor)
            Box(
                Modifier
                    .align(BiasAlignment(a.x * 2f - 1f, a.y * 2f - 1f))
                    .graphicsLayer { translationX = d.x; translationY = d.y },
            ) { at.content() }
        }
    }
}

/**
 * The "annoyed" face: angry brows knitted over the eyes and a 💢 on the forehead.
 * Drawn in the pet sprite's own footprint (a 2:3 cell — every creature shares it),
 * so the marks land on the face by the sprite's known geometry (eyes at ~0.63 h,
 * ±0.13 w). Because it's an overlay it stays put through the eye-blink frames, so
 * the pet still reads as angry mid-blink.
 */
@Composable
private fun BoxScope.AngerOverlay(faceY: Float) {
    val pop = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 520f)) }
    val ink = Color(0xFF222034)   // matches the sprite's outline ink
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .graphicsLayer {
                scaleX = pop.value; scaleY = pop.value
                transformOrigin = TransformOrigin(0.5f, 0.55f)
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            val cx = w * 0.5f
            val eyeY = h * faceY            // eye line, from the rig's face anchor
            val exOff = w * 0.13f            // eye offset from centre
            val sw = w * 0.045f
            // Angry brows: each slants down toward the nose (inner end lower).
            drawLine(
                ink,
                Offset(cx - exOff - w * 0.055f, eyeY - h * 0.05f),
                Offset(cx - exOff + w * 0.05f, eyeY - h * 0.012f),
                strokeWidth = sw, cap = StrokeCap.Round,
            )
            drawLine(
                ink,
                Offset(cx + exOff + w * 0.055f, eyeY - h * 0.05f),
                Offset(cx + exOff - w * 0.05f, eyeY - h * 0.012f),
                strokeWidth = sw, cap = StrokeCap.Round,
            )
            // 💢 anger vein on the forehead — a four-pointed star with concave sides
            // (the puffy "vein pop"). Drawn (not an emoji) so it lands exactly on the
            // forehead and renders identically on-device and in previews.
            val red = Color(0xFFE8352A)
            val fx = cx; val fy = h * (faceY - 0.124f)   // forehead, just above the eyes
            val r = w * 0.085f              // point radius
            val inner = r * 0.30f           // how far the sides pinch inward
            val vein = Path()
            fun pt(rad: Float, ang: Double) = Offset(fx + rad * cos(ang).toFloat(), fy + rad * sin(ang).toFloat())
            for (k in 0 until 4) {
                val a0 = -Math.PI / 2 + k * (Math.PI / 2)       // this point (start at top)
                val a1 = a0 + Math.PI / 2                        // next point
                val ac = a0 + Math.PI / 4                        // control, between them
                val p0 = pt(r, a0); val c = pt(inner, ac); val p1 = pt(r, a1)
                if (k == 0) vein.moveTo(p0.x, p0.y)
                val steps = 6
                for (s in 1..steps) {                            // sample the concave edge
                    val t = s / steps.toFloat(); val mt = 1 - t
                    val x = mt * mt * p0.x + 2 * mt * t * c.x + t * t * p1.x
                    val y = mt * mt * p0.y + 2 * mt * t * c.y + t * t * p1.y
                    vein.lineTo(x, y)
                }
            }
            vein.close()
            drawPath(vein, red, style = Stroke(width = w * 0.028f, cap = StrokeCap.Round))
        }
    }
}

/** A heart that pops up from the pet's head and fades — the "I felt that" cue. */
@Composable
private fun BoxScope.PetTapBurst(key: Int, show: Boolean) {
    if (key == 0 || !show) return
    val rise = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { rise.snapTo(0f); rise.animateTo(1f, tween(620)) }
    val p = rise.value
    val frame = (p * 3f).toInt().coerceIn(0, 2)
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .offset(y = (6 - 30 * p).dp)
            .alpha((1f - p).coerceIn(0f, 1f))
            .size((26 + 6 * p).dp),
    ) {
        PixelFrame("ov_heart_particle", frame, Modifier.fillMaxSize())
    }
}

/**
 * A dark radial backdrop for the list/info screens that have no room art of their
 * own (they'd otherwise render black-on-black). Lighter in the middle, darker at
 * the bezel — reads as intentional on the round face and keeps text legible.
 */
@Composable
fun ScreenBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(listOf(Color(0xFF1C2136), Color(0xFF0A0C13))),
            ),
    )
}

/**
 * A solid "tray" panel that extends from the very top of the screen down over most
 * of the round face, with a rounded bottom. Info drawn on top of it reads with
 * consistent contrast regardless of the gradient/vignette behind, and it visually
 * anchors the content to the top of the display. Drawn as a BoxScope child so it
 * sits behind the screen's content.
 */
@Composable
fun BoxScope.TopTray(heightFraction: Float = 0.88f) {
    val shape = RoundedCornerShape(bottomStart = 48.dp, bottomEnd = 48.dp)
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .fillMaxHeight(heightFraction)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF222A40), Color(0xFF1A2032))))
            .border(1.dp, Color(0x33FFFFFF), shape),
    )
}

/**
 * A ScalingLazyColumn with a DETERMINISTIC initial layout: pinned scroll state and
 * fixed content padding instead of auto-centering (whose padding depends on the
 * viewport, so it settles a few px differently between Robolectric and the device).
 * Pinning it lets the synthetic render and the on-device capture line up 1:1, so the
 * visual-diff check is clean on list screens too. Use this for any list screen that
 * appears in PreviewShots.
 */
@Composable
fun PinnedScalingColumn(
    modifier: Modifier = Modifier,
    content: androidx.wear.compose.foundation.lazy.ScalingLazyListScope.() -> Unit,
) {
    ScalingLazyColumn(
        modifier = modifier.fillMaxSize(),
        state = rememberScalingLazyListState(initialCenterItemIndex = 0, initialCenterItemScrollOffset = 0),
        autoCentering = null,
        contentPadding = PaddingValues(top = 40.dp, bottom = 44.dp, start = 8.dp, end = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

// ---------------------------------------------------------------- Feed
@Composable
fun FeedScreen(vm: TamaViewModel, inventory: Map<String, Int>) {
    val owned = Catalog.allFood.filter { (inventory[it.id] ?: 0) > 0 }
    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text("Feed", style = MaterialTheme.typography.title3) }
        if (owned.isEmpty()) item { Text("No food. Visit the Shop!", style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center) }
        items(owned) { food ->
            Button(onClick = { vm.feed(food.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelFrame(food.iconId, 0, Modifier.size(24.dp))
                    Text("${food.display}  x${inventory[food.id] ?: 0}", style = MaterialTheme.typography.button)
                }
            }
        }
        item { BackButton(vm) }
    }
}

// ---------------------------------------------------------------- Status
@Composable
fun StatusScreen(vm: TamaViewModel, pet: Pet) {
    Box(Modifier.fillMaxSize()) {
        ScreenBackdrop()
        TopTray()
    PinnedScalingColumn {
        item { Text(pet.name, style = MaterialTheme.typography.title3) }
        item { Text("${pet.species.display} · Gen ${pet.generation}", style = MaterialTheme.typography.caption1) }
        item { Text("${pet.stage} · ${pet.ageDays}d · ${pet.stats.weightG}g", style = MaterialTheme.typography.caption2) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("Hunger"); IconMeter("ic_hunger", "ic_hunger_empty", pet.stats.hungerHearts) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("Happy "); IconMeter("ic_mood", "ic_mood_empty", pet.stats.happyHearts) } }
        item { StatBar("Energy", pet.stats.energy, tint = Color(0xFF46C0E6)) }
        item { StatBar("Bond  ", pet.stats.bond, tint = Color(0xFFE96FA0)) }
        item { StatBar("Disc. ", pet.stats.discipline, tint = Color(0xFFFADC5A)) }
        item { Text("Care misses: ${pet.care.misses}", style = MaterialTheme.typography.caption3) }
        item { BackButton(vm) }
    }
    }
}

// ---------------------------------------------------------------- Shop
@Composable
fun ShopScreen(vm: TamaViewModel, pet: Pet) {
    var toast by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        PixelFrame("bg_shop", 0, Modifier.fillMaxSize())
        PinnedScalingColumn {
            item { Text("Shop · ${pet.gp} GP", style = MaterialTheme.typography.title3) }
            items(Catalog.allFood) { f ->
                ShopRow(f.iconId, "${f.display}", f.price) { vm.buyFood(f.id) { ok -> toast = if (ok) "Bought ${f.display}" else "Need more GP" } }
            }
            item { ShopRow("item_medicine", "Medicine · gentle cure", Catalog.medicinePrice) { vm.buyMedicine { ok -> toast = if (ok) "Bought Medicine" else "Need more GP" } } }
            items(Catalog.cosmetics) { c ->
                ShopRow(c.bgId, c.display, c.price) { vm.buyCosmetic(c.id) { ok -> toast = if (ok) "Unlocked ${c.display}" else "Need more GP" } }
            }
            item { BackButton(vm) }
        }
        toast?.let {
            RoundPanel(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) { Text(it, style = MaterialTheme.typography.caption2) }
        }
    }
}

@Composable
private fun ShopRow(icon: String, label: String, price: Int, onBuy: () -> Unit) {
    Button(onClick = onBuy, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PixelFrame(icon, 0, Modifier.size(22.dp))
            Text("$label · $price", style = MaterialTheme.typography.caption1)
        }
    }
}

// ---------------------------------------------------------------- Steps
@Composable
fun StepsScreen(vm: TamaViewModel, pet: Pet) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        PixelFrame("ic_steps", 0, Modifier.size(36.dp))
        Text("${pet.stepsToday}", style = MaterialTheme.typography.display3)
        Text("steps today", style = MaterialTheme.typography.caption1)
        val goal = Tuning.DAILY_STEP_GOAL
        Text(if (pet.stepsToday >= goal) "Goal reached! 🎉" else "${(goal - pet.stepsToday).coerceAtLeast(0)} to goal",
            style = MaterialTheme.typography.caption2)
        Text("${pet.stepsToday / Tuning.STEPS_PER_GP} GP earned", style = MaterialTheme.typography.caption3)
        Spacer(Modifier.height(6.dp)); BackButton(vm)
    }
}

// ---------------------------------------------------------------- Settings
@Composable
fun SettingsScreen(vm: TamaViewModel, s: com.tamawatch.core.data.Settings) {
    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text("Settings", style = MaterialTheme.typography.title3) }
        item { Button(onClick = { vm.setSound(!s.soundOn) }, modifier = Modifier.fillMaxWidth()) { Text("Sound: ${if (s.soundOn) "On" else "Off"}") } }
        item { Button(onClick = { vm.setReduceMotion(!s.reduceMotion) }, modifier = Modifier.fillMaxWidth()) { Text("Reduce motion: ${if (s.reduceMotion) "On" else "Off"}") } }
        item { Button(onClick = { vm.setMic(!s.micEnabled) }, modifier = Modifier.fillMaxWidth()) { Text("Mic talk: ${if (s.micEnabled) "On" else "Off"}") } }
        item { Text("Sleep ${s.sleep.startHour}:00–${s.sleep.endHour}:00", style = MaterialTheme.typography.caption2) }
        item { Button(onClick = { vm.go(Screen.Rugs) }, modifier = Modifier.fillMaxWidth()) { Text("Rug: ${rugName(s.rugId)}") } }
        item { Button(onClick = { vm.go(Screen.Coats) }, modifier = Modifier.fillMaxWidth()) { Text("Coat: ${coatName(s.coatId)}") } }
        item { Button(onClick = { vm.go(Screen.Help) }, modifier = Modifier.fillMaxWidth()) { Text("Help: what the icons mean") } }
        item {
            Button(onClick = { vm.startCapture() }, modifier = Modifier.fillMaxWidth()) {
                Text("Capture render states → Gallery", style = MaterialTheme.typography.caption2)
            }
        }
        item { BackButton(vm) }
    }
}

/** Rug picker: tap a rug to stand your pet on it. */
@Composable
fun RugScreen(vm: TamaViewModel, current: Int) {
    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text("Choose a rug", style = MaterialTheme.typography.title3) }
        Rugs.forEach { r ->
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (r.id == current) Color(0x33FFFFFF) else Color(0x22000000))
                        .clickable { vm.setRug(r.id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (r.asset != null) PixelFrame(r.asset, 0, Modifier.width(46.dp).height(20.dp))
                    else Box(Modifier.width(46.dp).height(20.dp))
                    Text(r.name, style = MaterialTheme.typography.button, modifier = Modifier.weight(1f))
                    if (r.id == current) Text("✓", style = MaterialTheme.typography.title3)
                }
            }
        }
        item { BackButton(vm) }
    }
}

// ---------------------------------------------------------------- Coats
/** Selectable coat patterns painted over the pet's silhouette. id 0 = plain. */
data class CoatStyle(val id: Int, val name: String)

val Coats = listOf(
    CoatStyle(0, "None"),
    CoatStyle(1, "Tiger"),
    CoatStyle(2, "Leopard"),
    CoatStyle(3, "Triangles"),
)

fun coatName(id: Int): String = Coats.firstOrNull { it.id == id }?.name ?: "None"

/** Coat picker: tap a pattern to paint it onto your pet. */
@Composable
fun CoatScreen(vm: TamaViewModel, current: Int) {
    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text("Choose a coat", style = MaterialTheme.typography.title3) }
        Coats.forEach { c ->
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (c.id == current) Color(0x33FFFFFF) else Color(0x22000000))
                        .clickable { vm.setCoat(c.id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CoatSwatch(c.id, Modifier.width(46.dp).height(24.dp))
                    Text(c.name, style = MaterialTheme.typography.button, modifier = Modifier.weight(1f))
                    if (c.id == current) Text("✓", style = MaterialTheme.typography.title3)
                }
            }
        }
        item { BackButton(vm) }
    }
}

/** A little preview tile: the pattern painted over a fur-toned rounded rect. */
@Composable
private fun CoatSwatch(coatId: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(6.dp))) {
        drawRect(Color(0xFFD8B486))                 // fur-toned base
        if (coatId != 0) drawCoat(coatId)
    }
}

/** Paint the chosen coat pattern. Every mark uses BlendMode.SrcAtop, so on the pet
 *  it clips to the sprite silhouette, and on the opaque swatch it shows in full.
 *  Placement is deterministic (golden-ratio fractions), so it never shimmers. */
private fun DrawScope.drawCoat(coatId: Int) {
    when (coatId) {
        1 -> tigerCoat()
        2 -> leopardCoat()
        3 -> triangleCoat()
    }
}

private fun frac(x: Float): Float = x - floor(x)
private const val GOLD = 0.6180339887f

/** Curved vertical flank stripes. */
private fun DrawScope.tigerCoat() {
    val w = size.width; val h = size.height
    val ink = Color(0xFF241708)
    val count = 7
    for (i in 0 until count) {
        val t = i / (count - 1f)
        val cx = w * (0.13f + 0.74f * t)
        val bow = (frac(i * GOLD) - 0.5f) * w * 0.16f
        val yTop = h * (0.26f + 0.06f * frac(i * 0.34f))
        val yBot = h * (0.97f - 0.05f * frac(i * 0.78f))
        val path = Path()
        val steps = 8
        for (s in 0..steps) {
            val f = s / steps.toFloat()
            val y = yTop + (yBot - yTop) * f
            val x = cx + bow * sin(f * Math.PI).toFloat()
            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val sw = w * (0.055f + 0.028f * frac(i * GOLD))
        drawPath(
            path, ink, alpha = 0.6f,
            style = Stroke(width = sw, cap = StrokeCap.Round),
            blendMode = BlendMode.SrcAtop,
        )
    }
}

/** Scattered rosettes: a dark ring around a small center dot. */
private fun DrawScope.leopardCoat() {
    val w = size.width; val h = size.height
    val ink = Color(0xFF2E1B08)
    val n = 16
    for (i in 0 until n) {
        val cx = w * (0.14f + 0.72f * frac(i * GOLD))
        val cy = h * (0.30f + 0.66f * frac(i * 0.4142136f))
        val r = w * (0.05f + 0.03f * frac(i * 0.7548777f))
        drawCircle(ink, r, Offset(cx, cy), alpha = 0.6f,
            style = Stroke(width = w * 0.022f), blendMode = BlendMode.SrcAtop)
        drawCircle(ink, r * 0.3f, Offset(cx, cy), alpha = 0.55f, blendMode = BlendMode.SrcAtop)
    }
}

/** Scattered filled triangles at varied sizes and rotations. */
private fun DrawScope.triangleCoat() {
    val w = size.width; val h = size.height
    val ink = Color(0xFF26170A)
    val n = 18
    for (i in 0 until n) {
        val cx = w * (0.14f + 0.72f * frac(i * GOLD))
        val cy = h * (0.30f + 0.66f * frac(i * 0.3183099f))
        val s = w * (0.06f + 0.045f * frac(i * 0.7548777f))
        val rot = frac(i * 0.9f) * 6.2831855f
        val path = Path()
        for (k in 0..2) {
            val a = rot + k * 2.0943952f          // 120° apart
            val x = cx + s * cos(a)
            val y = cy + s * sin(a)
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, ink, alpha = 0.6f, blendMode = BlendMode.SrcAtop)
    }
}

/** Help: a legend explaining every icon the player sees. */
private val HelpEntries = listOf(
    Triple("ic_hunger", "Hunger", "Meat shanks show how fed your pet is — refill it by feeding."),
    Triple("ic_mood", "Happy", "Smileys show your pet's mood — raise it by playing and petting."),
    Triple("ov_heart_particle", "Pet", "Tap or drag your pet to fuss it — it perks up and gains a little happiness. A small heart means it's content for a bit; pester it too much and it gets annoyed (💢). Tap it while it's asleep to wake it."),
    Triple("ic_feed", "Feed", "Open the food menu to feed a meal or snack."),
    Triple("ic_play", "Play", "Play a mini-game to raise happiness and earn Gotchi Points."),
    Triple("ic_bathroom", "Clean", "Flush away poop so your pet doesn't get sick."),
    Triple("ic_medicine", "Medicine", "Cures sickness. The free home remedy always works but your pet dislikes it (small bond hit). A Medicine from the Shop is a gentle cure — no bond loss."),
    Triple("ic_light", "Lights", "Toggle the room light. Turn it off so a sleepy pet can rest; the same button turns it back on."),
    Triple("ic_status", "Status", "See detailed hunger, happy, energy, bond and discipline."),
    Triple("ic_shop", "Shop", "Spend Gotchi Points on food, medicine and backgrounds."),
    Triple("ic_steps", "Steps", "Your real steps become Gotchi Points each day."),
    Triple("ic_discipline", "Scold", "Discipline your pet when it calls for no real reason."),
    Triple("ic_settings", "Settings", "Sound, reduce-motion, and this help."),
)

@Composable
fun HelpScreen(vm: TamaViewModel) {
    Box(Modifier.fillMaxSize()) {
        ScreenBackdrop()
    PinnedScalingColumn {
        item { Text("Help", style = MaterialTheme.typography.title3) }
        item { Text("What the icons mean", style = MaterialTheme.typography.caption2) }
        HelpEntries.forEach { (icon, name, desc) ->
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PixelFrame(icon, 0, Modifier.size(30.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.button)
                        Text(desc, style = MaterialTheme.typography.caption3)
                    }
                }
            }
        }
        item { BackButton(vm) }
    }
    }
}

@Composable
fun BackButton(vm: TamaViewModel) {
    Button(onClick = { vm.home() }, modifier = Modifier.padding(top = 4.dp)) {
        PixelFrame("ic_back", 0, Modifier.size(20.dp))
    }
}
