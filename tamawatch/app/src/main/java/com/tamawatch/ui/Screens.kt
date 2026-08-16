package com.tamawatch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import com.tamawatch.core.model.*
import com.tamawatch.ui.common.*
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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

private data class RingAction(val icon: String, val label: String, val onGo: () -> Unit, val alert: Boolean = false)

@Composable
fun HomeScreen(vm: TamaViewModel, pet: Pet, ownsBeach: Boolean, ownsSpace: Boolean) {
    val bg = when {
        pet.stage == Stage.EGG -> "bg_egg"
        pet.asleep || !pet.lightOn -> "bg_room_night"
        ownsSpace -> "cos_bg_space"
        ownsBeach -> "cos_bg_beach"
        else -> "bg_room_day"
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PixelFrame(bg, 0, Modifier.fillMaxSize())

        // Top status
        Column(Modifier.align(Alignment.TopCenter).padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Minecraft-style meters: each point in the meter IS its icon — a row
            // of meat-shanks for Hunger, smileys for Happy (no labels needed).
            IconMeter("ic_hunger", "ic_hunger_empty", pet.stats.hungerHearts)
            Spacer(Modifier.height(2.dp))
            IconMeter("ic_mood", "ic_mood_empty", pet.stats.happyHearts)
            Spacer(Modifier.height(2.dp))
            GpBadge(pet.gp, pet.stepsToday)
        }

        if (pet.stage == Stage.EGG) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PixelSprite("spr_egg", "wiggle", 2, Modifier.size(120.dp))
                Text("${pet.name}'s egg…", style = MaterialTheme.typography.caption1)
            }
            return@Box
        }

        // Care ring + centered pet
        CareRing(vm, pet)
    }
}

@Composable
private fun CareRing(vm: TamaViewModel, pet: Pet) {
    val actions = listOf(
        RingAction("ic_feed", "Feed", { vm.go(Screen.Feed) }, alert = pet.stats.hunger <= Tuning.CRIT),
        RingAction("ic_play", "Play", { vm.go(Screen.PlayMenu) }),
        RingAction("ic_bathroom", "Clean", { vm.clean() }, alert = pet.stats.dirty),
        RingAction("ic_medicine", "Medicine", { vm.heal() }, alert = pet.stats.sick),
        RingAction("ic_light", if (pet.lightOn) "Lights off" else "Lights on", { vm.toggleLight() }),
        RingAction("ic_status", "Status", { vm.go(Screen.Status) }),
        RingAction("ic_shop", "Shop", { vm.go(Screen.Shop) }),
        RingAction("ic_steps", "Steps", { vm.go(Screen.Steps) }),
        RingAction("ic_discipline", "Scold", { vm.scold() }),
        RingAction("ic_settings", "Settings", { vm.go(Screen.Settings) }),
    )
    val sel = vm.ringIndex.mod(actions.size)

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val radiusPx = with(density) { (maxWidth * 0.40f).toPx() }

        actions.forEachIndexed { i, a ->
            val ang = Math.toRadians((-90 + i * (360.0 / actions.size)))
            val x = (radiusPx * cos(ang)).roundToInt()
            val y = (radiusPx * sin(ang)).roundToInt()
            Box(
                Modifier
                    .offset { IntOffset(x, y) }
                    .size(if (i == sel) 40.dp else 32.dp)
                    .clickable { a.onGo() },
                contentAlignment = Alignment.Center,
            ) {
                if (i == sel) PixelSprite("ui_selector", "pulse", 3, Modifier.fillMaxSize())
                // Icons are self-contained colored chips, so they read on any background.
                PixelFrame(a.icon, 0, Modifier.size(if (i == sel) 34.dp else 27.dp))
                // Attention badge: a small red dot (an opaque chip would hide a wash).
                if (a.alert) Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDF3E3E))
                        .border(1.5.dp, Color.White, CircleShape),
                )
            }
        }

        // Center: pet + highlighted label. Tap = activate highlighted; long-press = pet.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val (id, tag) = poseFor(pet)
            Box(
                Modifier
                    .size(96.dp)
                    .pointerInput(sel, pet.species) {
                        detectTapGestures(
                            onTap = { actions[sel].onGo() },
                            onLongPress = { vm.petIt() },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                PixelSprite(id, tag, 3, Modifier.fillMaxSize())
                if (pet.stats.dirty) PixelSprite("ov_poop", "idle", 2, Modifier.align(Alignment.BottomStart).size(22.dp))
                if (pet.stats.sick) PixelSprite("ov_sick_skull", "blink", 3, Modifier.align(Alignment.TopEnd).size(18.dp))
                if (pet.asleep) PixelSprite("ov_zzz", "idle", 2, Modifier.align(Alignment.TopEnd).size(22.dp))
                if (pet.needsAttention() && !pet.asleep) PixelSprite("ov_call", "blink", 3, Modifier.align(Alignment.TopEnd).size(16.dp))
            }
            Text(actions[sel].label, style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center)
        }
    }
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
    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
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

// ---------------------------------------------------------------- Shop
@Composable
fun ShopScreen(vm: TamaViewModel, pet: Pet) {
    var toast by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        PixelFrame("bg_shop", 0, Modifier.fillMaxSize())
        ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Text("Shop · ${pet.gp} GP", style = MaterialTheme.typography.title3) }
            items(Catalog.allFood) { f ->
                ShopRow(f.iconId, "${f.display}", f.price) { vm.buyFood(f.id) { ok -> toast = if (ok) "Bought ${f.display}" else "Need more GP" } }
            }
            item { ShopRow("item_medicine", "Medicine", Catalog.medicinePrice) { vm.buyMedicine { ok -> toast = if (ok) "Bought Medicine" else "Need more GP" } } }
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
        item { BackButton(vm) }
    }
}

@Composable
fun BackButton(vm: TamaViewModel) {
    Button(onClick = { vm.home() }, modifier = Modifier.padding(top = 4.dp)) {
        PixelFrame("ic_back", 0, Modifier.size(20.dp))
    }
}
