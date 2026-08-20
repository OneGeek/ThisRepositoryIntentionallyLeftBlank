package com.tamawatch.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val HOLD_MS = 1050f       // hold-alone time to commit
private const val FADE_MS = 140f        // the name pops in this fast, independent of commit
private const val DRAG_FRAC = 0.5f      // or drag half the way toward center
private const val ROW_HOLD_MS = 720f

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
 * Touch-first radial menu: five category hubs on a horseshoe. Press-and-hold a
 * hub (or drag it toward the center) — its name pops into a capsule at the center
 * and a gold ring fills; releasing early cancels. Committing opens the hub's
 * labeled list (or navigates straight to Shop/Settings). No rotary, no memorizing.
 */
@Composable
fun RadialMenu(vm: TamaViewModel, pet: Pet) {
    val haptics = LocalContext.current.tama.haptics
    val inventory by vm.inventory.collectAsStateWithLifecycle()
    val medicineCount = inventory["item_medicine"] ?: 0
    val hubs = remember(pet.lightOn, pet.stats.sick, medicineCount) { hubsFor(vm, pet, medicineCount) }
    var openHub by remember { mutableStateOf<Hub?>(null) }

    var pressed by remember { mutableIntStateOf(-1) }
    var dragToward by remember { mutableFloatStateOf(0f) }
    var startDist by remember { mutableFloatStateOf(1f) }
    var progress by remember { mutableFloatStateOf(0f) }
    var labelAlpha by remember { mutableFloatStateOf(0f) }

    // Time-based progress while a hub is held. Drag can outrun the hold.
    LaunchedEffect(pressed) {
        if (pressed < 0) { progress = 0f; labelAlpha = 0f; return@LaunchedEffect }
        val idx = pressed
        var start = -1L
        while (true) {
            withFrameMillis { now ->
                if (start < 0L) start = now
                val el = (now - start).toFloat()
                val hold = el / HOLD_MS
                val drag = if (startDist > 0f) dragToward / (startDist * DRAG_FRAC) else 0f
                progress = min(1f, max(0f, max(hold, drag)))
                labelAlpha = min(1f, el / FADE_MS)
            }
            if (progress >= 1f) break
        }
        val hub = hubs.getOrNull(idx)
        haptics.confirm()
        if (hub != null) { if (hub.direct != null) hub.direct.invoke() else openHub = hub }
        pressed = -1
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
            val topLeft = Offset(hubCenter.x - hubHalf, hubCenter.y - hubHalf)
            val thisStart = (hubCenter - center).getDistance()

            Box(
                Modifier
                    .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                    .size(hubDp)
                    .graphicsLayer {
                        val s = if (i == pressed) 1f + 0.05f * labelAlpha + 0.11f * progress else 1f
                        scaleX = s; scaleY = s
                    }
                    .pointerInput(i, topLeft) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            if (openHub == null) {
                                startDist = thisStart; dragToward = 0f; pressed = i
                                haptics.tick()
                            }
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull() ?: break
                                if (ch.pressed) {
                                    val gx = topLeft.x + ch.position.x
                                    val gy = topLeft.y + ch.position.y
                                    val d = Offset(gx - center.x, gy - center.y).getDistance()
                                    dragToward = (startDist - d).coerceAtLeast(0f)
                                    ch.consume()
                                } else {
                                    if (pressed == i && progress < 1f) pressed = -1
                                    break
                                }
                            }
                        }
                    },
            ) {
                PixelFrame(hub.icon, 0, Modifier.fillMaxSize())
            }
        }

        // Center capsule with the pressed hub's name (fades in fast); the gold
        // progress ring encircles the capsule, not the hub under the finger.
        if (pressed in hubs.indices) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = (-4).dp)
                    .alpha(labelAlpha)
                    .graphicsLayer { val s = 0.92f + 0.08f * labelAlpha; scaleX = s; scaleY = s },
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
                        hubs[pressed].name,
                        style = MaterialTheme.typography.title2,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF6F2EA),
                    )
                }
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
                    pm.getSegment(0f, pm.length * progress, seg, true)
                    drawPath(seg, color = Gold, style = Stroke(width = sw, cap = StrokeCap.Round))
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
    var holding by remember { mutableStateOf(false) }
    var fill by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(holding) {
        if (!holding) { fill = 0f; return@LaunchedEffect }
        var start = -1L
        while (true) {
            withFrameMillis { now -> if (start < 0L) start = now; fill = min(1f, (now - start) / ROW_HOLD_MS) }
            if (fill >= 1f) break
        }
        haptics.confirm(); item.run(); onDone()
    }

    val base = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(Color(0x14FFFFFF))
    val gesture = if (item.confirm) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                holding = true; haptics.tick()
                while (true) {
                    val ev = awaitPointerEvent()
                    if (ev.changes.none { it.pressed }) break
                }
                holding = false
            }
        }
    } else {
        Modifier.clickable { haptics.confirm(); item.run(); onDone() }
    }

    Box(base.then(gesture)) {
        if (item.confirm && fill > 0f) {
            Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(Color(0x333CA0A5)))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PixelFrame(item.icon, 0, Modifier.size(26.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.button)
                item.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.caption3, color = Color(0xFFAAB1C6))
                }
            }
            Text(
                if (item.confirm) "hold" else "tap",
                style = MaterialTheme.typography.caption3,
                color = Color(0xFFAAB1C6),
                textAlign = TextAlign.End,
            )
        }
    }
}
