package com.tamawatch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.tamawatch.core.model.Pet
import com.tamawatch.tama
import com.tamawatch.ui.common.PixelFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val FADE_MS = 120         // capsule name fades in this fast (ms)
private const val DRAG_FRAC = 0.5f      // drag this fraction toward the center to commit
private const val LINGER_MS = 550L      // the chosen name lingers this long after a commit
private const val HINT_PULL = 0.24f     // release bounce: hub pulls this fraction toward center

private val Gold = Color(0xFFF2C14E)

private class MenuItem(
    val name: String,
    val icon: String,
    val confirm: Boolean = false,
    val subtitle: String? = null,
    val run: () -> Unit,
)
private class Hub(
    val name: String,
    val icon: String,
    val angleDeg: Double,
    val direct: (() -> Unit)? = null,
    val items: List<MenuItem> = emptyList(),
)

private fun hubsFor(vm: TamaViewModel, pet: Pet, medicineCount: Int): List<Hub> {
    // Teach the two-tier cure right where you'd use it: with a shop Medicine in the
    // bag it's a gentle cure (no bond loss); without one, the free home remedy still
    // cures but the pet takes a small bond hit.
    val medSubtitle = when {
        !pet.stats.sick -> "Not sick"
        medicineCount > 0 -> "Gentle cure · ×$medicineCount"
        else -> "Free home remedy · small bond hit"
    }
    return listOf(
    Hub("Settings", "ic_settings", 216.0, direct = { vm.go(Screen.Settings) }),
    Hub("Shop", "ic_shop", 154.0, direct = { vm.go(Screen.Shop) }),
    Hub("Care", "ic_feed", 90.0, items = listOf(
        MenuItem("Feed", "ic_feed") { vm.go(Screen.Feed) },
        MenuItem("Clean", "ic_bathroom") { vm.clean() },
        MenuItem("Medicine", "ic_medicine", confirm = true, subtitle = medSubtitle) { vm.heal() },
        MenuItem(if (pet.lightOn) "Lights off" else "Lights on", "ic_light") { vm.toggleLight() },
        MenuItem("Scold", "ic_discipline") { vm.scold() },
    )),
    Hub("Play", "ic_play", 26.0, items = listOf(
        MenuItem("Jump", "ic_play") { vm.go(Screen.Jump) },
        MenuItem("Guess", "ic_play") { vm.go(Screen.Guess) },
        MenuItem("Catch", "ic_play") { vm.go(Screen.Catch) },
    )),
    Hub("Stats", "ic_status", -36.0, items = listOf(
        MenuItem("Status", "ic_status") { vm.go(Screen.Status) },
        MenuItem("Steps", "ic_steps") { vm.go(Screen.Steps) },
    )),
    )
}

/**
 * Touch-first radial menu: five category hubs on a horseshoe. Press a hub and DRAG
 * it toward the center to trigger — the name pops into a capsule that grows as you
 * near the center, and a gold ring fills. There is no hold-to-trigger. Let go short
 * of the center and the hub springs a little toward the center (an affordance for
 * "drag me in"), the capsule pulsing in time. Committing opens the hub's labeled
 * list or navigates straight to Shop/Settings; the name lingers a beat after.
 */
@Composable
fun RadialMenu(vm: TamaViewModel, pet: Pet) {
    val haptics = LocalContext.current.tama.haptics
    val inventory by vm.inventory.collectAsStateWithLifecycle()
    val medicineCount = inventory["item_medicine"] ?: 0
    val hubs = remember(pet.lightOn, pet.stats.sick, medicineCount) { hubsFor(vm, pet, medicineCount) }
    var openHub by remember { mutableStateOf<Hub?>(null) }
    val scope = rememberCoroutineScope()

    var pressed by remember { mutableIntStateOf(-1) }         // hub under an active drag
    var dragProgress by remember { mutableFloatStateOf(0f) }  // 0..1 toward the center
    var startDist by remember { mutableFloatStateOf(1f) }
    val pressAlpha by animateFloatAsState(if (pressed >= 0) 1f else 0f, tween(FADE_MS), label = "capAlpha")

    // Release affordance: an un-committed hub springs toward the center, name pulses.
    var hintHub by remember { mutableIntStateOf(-1) }
    var hintName by remember { mutableStateOf("") }
    val hint = remember { Animatable(0f) }

    // After a commit the chosen name lingers a beat before fading.
    var lingerName by remember { mutableStateOf<String?>(null) }
    val linger = remember { Animatable(0f) }

    fun commit(i: Int) {
        val hub = hubs.getOrNull(i) ?: return
        haptics.confirm()
        lingerName = hub.name
        scope.launch {
            linger.snapTo(1f)
            delay(LINGER_MS)
            linger.animateTo(0f, tween(200))
            if (lingerName == hub.name) lingerName = null
        }
        if (hub.direct != null) hub.direct.invoke() else openHub = hub
    }

    fun bounceHint(i: Int, from: Float) {
        val hub = hubs.getOrNull(i) ?: return
        hintHub = i; hintName = hub.name
        haptics.tick()
        scope.launch {
            hint.snapTo(from.coerceIn(0f, 0.5f))
            hint.animateTo(0.62f, tween(120))                                    // pull toward center
            hint.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 360f))   // spring back
            if (hintHub == i) hintHub = -1
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val center = Offset(wPx / 2f, hPx / 2f)
        val ringR = min(wPx, hPx) * 0.35f
        val hubDp = 42.dp
        val hubHalf = with(density) { hubDp.toPx() } / 2f

        hubs.forEachIndexed { i, hub ->
            val a = Math.toRadians(hub.angleDeg)
            val hubCenter = Offset(center.x + ringR * cos(a).toFloat(), center.y + ringR * sin(a).toFloat())
            val baseTopLeft = Offset(hubCenter.x - hubHalf, hubCenter.y - hubHalf)
            val thisStart = (hubCenter - center).getDistance()

            // Draw-time pull toward the center during the release bounce (finger up).
            val pull = if (i == hintHub) hint.value * HINT_PULL else 0f
            val drawTL = Offset(
                baseTopLeft.x + (center.x - hubCenter.x) * pull,
                baseTopLeft.y + (center.y - hubCenter.y) * pull,
            )
            val hubScale = when {
                i == pressed -> 1f + 0.16f * dragProgress
                i == hintHub -> 1f + 0.12f * hint.value
                else -> 1f
            }

            Box(
                Modifier
                    .offset { IntOffset(drawTL.x.roundToInt(), drawTL.y.roundToInt()) }
                    .size(hubDp)
                    .graphicsLayer { scaleX = hubScale; scaleY = hubScale }
                    .pointerInput(i, baseTopLeft) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            if (openHub != null) return@awaitEachGesture
                            pressed = i; startDist = thisStart; dragProgress = 0f
                            haptics.tick()
                            var committed = false
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull() ?: break
                                if (ch.pressed) {
                                    val gx = baseTopLeft.x + ch.position.x
                                    val gy = baseTopLeft.y + ch.position.y
                                    val d = Offset(gx - center.x, gy - center.y).getDistance()
                                    val toward = (startDist - d).coerceAtLeast(0f)
                                    dragProgress = if (startDist > 0f)
                                        (toward / (startDist * DRAG_FRAC)).coerceIn(0f, 1f) else 0f
                                    ch.consume()
                                    if (dragProgress >= 1f) {
                                        committed = true
                                        pressed = -1
                                        commit(i)
                                        break
                                    }
                                } else {
                                    val from = dragProgress
                                    pressed = -1
                                    if (!committed) bounceHint(i, from)
                                    break
                                }
                            }
                        }
                    },
            ) {
                PixelFrame(hub.icon, 0, Modifier.fillMaxSize())
            }
        }

        // Center capsule: the active hub's name. It GROWS toward the center (closer
        // = bigger) and pulses with the release bounce. Gold ring only while dragging.
        val activeName: String?
        val closeness: Float
        val capAlpha: Float
        when {
            pressed in hubs.indices -> { activeName = hubs[pressed].name; closeness = dragProgress; capAlpha = pressAlpha }
            hintHub in hubs.indices -> { activeName = hintName; closeness = hint.value; capAlpha = (0.4f + hint.value).coerceAtMost(1f) }
            lingerName != null -> { activeName = lingerName; closeness = 1f; capAlpha = linger.value }
            else -> { activeName = null; closeness = 0f; capAlpha = 0f }
        }

        if (activeName != null && capAlpha > 0.01f) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = (-4).dp)
                    .alpha(capAlpha)
                    .graphicsLayer { val s = 0.9f + 0.22f * closeness; scaleX = s; scaleY = s },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .padding(7.dp)
                        .shadow(12.dp, RoundedCornerShape(50), clip = false)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF171A28))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(50))
                        .padding(horizontal = 22.dp, vertical = 9.dp),
                ) {
                    Text(
                        activeName,
                        style = MaterialTheme.typography.title2,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF6F2EA),
                    )
                }
                if (pressed in hubs.indices) {
                    // Gold progress ring hugging the capsule's pill outline.
                    Canvas(Modifier.matchParentSize()) {
                        val sw = 4.dp.toPx()
                        val half = sw / 2f
                        val rad = (size.height - sw) / 2f
                        val rr = RoundRect(
                            left = half, top = half,
                            right = size.width - half, bottom = size.height - half,
                            cornerRadius = CornerRadius(rad, rad),
                        )
                        val path = Path().apply { addRoundRect(rr) }
                        val pm = PathMeasure().apply { setPath(path, false) }
                        val seg = Path()
                        pm.getSegment(0f, pm.length * dragProgress, seg, true)
                        drawPath(seg, color = Gold, style = Stroke(width = sw, cap = StrokeCap.Round))
                    }
                }
            }
        }

        // Submenu sheet
        openHub?.let { hub ->
            SubmenuSheet(hub, onClose = { openHub = null })
        }
    }
}

@Composable
private fun BoxScope.SubmenuSheet(hub: Hub, onClose: () -> Unit) {
    // scrim
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
    )
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(0.72f)
            .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
            .background(Color(0xF2141826))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(hub.name, style = MaterialTheme.typography.title3, fontWeight = FontWeight.Bold)
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            hub.items.forEach { item -> MenuRow(item, onClose) }
        }
    }
}

@Composable
private fun MenuRow(item: MenuItem, onDone: () -> Unit) {
    val haptics = LocalContext.current.tama.haptics
    // Two-step confirm for `confirm` rows: first tap arms (turns teal, "confirm?"),
    // second tap runs it. Taps (not holds) so a scroll-drag never triggers it.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) { delay(3500); armed = false }   // auto-disarm a stray first tap
    }

    val bg = if (armed) Color(0x333CA0A5) else Color(0x14FFFFFF)
    val onClick: () -> Unit = if (item.confirm) {
        {
            if (armed) { armed = false; haptics.confirm(); item.run(); onDone() }
            else { armed = true; haptics.tick() }
        }
    } else {
        { haptics.confirm(); item.run(); onDone() }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable { onClick() },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PixelFrame(item.icon, 0, Modifier.size(26.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (item.confirm && armed) "${item.name}?" else item.name,
                    style = MaterialTheme.typography.button,
                )
                item.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.caption3, color = Color(0xFFAAB1C6))
                }
            }
            Text(
                if (!item.confirm) "tap" else if (armed) "confirm" else "tap ×2",
                style = MaterialTheme.typography.caption3,
                color = if (armed) Color(0xFF8FE3C0) else Color(0xFFAAB1C6),
                textAlign = TextAlign.End,
            )
        }
    }
}
