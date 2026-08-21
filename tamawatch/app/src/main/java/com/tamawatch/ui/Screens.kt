package com.tamawatch.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
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
import com.tamawatch.core.model.*
import com.tamawatch.ui.common.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
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
fun HomeScreen(vm: TamaViewModel, pet: Pet, ownsBeach: Boolean, ownsSpace: Boolean, rugId: Int) {
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
        PetCenter(vm, pet, rugId)
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
private fun BoxScope.PetCenter(vm: TamaViewModel, pet: Pet, rugId: Int) {
    val (id, tag) = poseFor(pet)
    val reduce = LocalReduceMotion.current
    val lastPetMs by vm.lastPetMs.collectAsStateWithLifecycle()

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
    val content = pet.alive && !pet.asleep && now - lastPetMs < Tuning.PET_COOLDOWN_MS

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
                            } else {
                                reactEffective = System.currentTimeMillis() - lastPetMs >= Tuning.PET_COOLDOWN_MS
                                reactKey++
                                vm.petIt()
                            }
                            break
                        }
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Feet pinned to the bottom of this (raised) box; headroom overflows up.
        // Transform stack (outer→inner): grab-follow translation, then a directional
        // stretch (rotate to the drag axis, scale, rotate back), then the tap bounce.
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
                },
        )
        if (pet.stats.sick) PixelSprite("ov_sick_skull", "blink", 3, Modifier.align(Alignment.TopEnd).size(18.dp))
        if (pet.asleep) PixelSprite("ov_zzz", "idle", 2, Modifier.align(Alignment.TopEnd).size(22.dp))
        if (pet.needsAttention() && !pet.asleep) PixelSprite("ov_call", "blink", 3, Modifier.align(Alignment.TopEnd).size(16.dp))
        // Cooldown mood: a small steady heart, so it's clear the pet is content and
        // more petting won't add happiness right now.
        if (content) {
            PixelSprite(
                "ov_heart_particle", "rise", 2,
                Modifier.align(Alignment.TopCenter).offset(y = 2.dp).size(18.dp).alpha(0.7f),
            )
        }
        // The tap burst: a heart that springs up and fades on an effective pet.
        PetTapBurst(reactKey, reactEffective && !reduce)
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

/** Help: a legend explaining every icon the player sees. */
private val HelpEntries = listOf(
    Triple("ic_hunger", "Hunger", "Meat shanks show how fed your pet is — refill it by feeding."),
    Triple("ic_mood", "Happy", "Smileys show your pet's mood — raise it by playing and petting."),
    Triple("ov_heart_particle", "Pet", "Tap your pet to give it a fuss — it perks up and gains a little happiness. A small heart means it's content for a bit."),
    Triple("ic_feed", "Feed", "Open the food menu to feed a meal or snack."),
    Triple("ic_play", "Play", "Play a mini-game to raise happiness and earn Gotchi Points."),
    Triple("ic_bathroom", "Clean", "Flush away poop so your pet doesn't get sick."),
    Triple("ic_medicine", "Medicine", "Cures sickness. The free home remedy always works but your pet dislikes it (small bond hit). A Medicine from the Shop is a gentle cure — no bond loss."),
    Triple("ic_light", "Lights", "Turn the room light off so a sleepy pet can rest."),
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
