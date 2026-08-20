package com.tamawatch.ui

import androidx.compose.runtime.Composable
import com.tamawatch.core.model.Pet
import com.tamawatch.core.model.Species
import com.tamawatch.core.model.Stage
import com.tamawatch.core.model.Stats

/**
 * The canonical set of preview states. This is the SINGLE source of truth shared by
 * the JVM synthetic render (HomeSnapshotTest → screenshots.png) and the on-device
 * capture (Settings → "Capture render states"). Because both drive the exact same
 * composables with the exact same inputs, the two outputs can be compared
 * pixel-for-pixel — any divergence is a real render/config gap, not a state mismatch.
 *
 * Add or tweak a state here and both the synthetic sheet and the device capture pick
 * it up. Keep the ids stable — they name the output files.
 */
class PreviewShot(
    val id: String,
    val label: String,
    val content: @Composable (TamaViewModel) -> Unit,
)

private fun previewBaby(
    hunger: Int = 70,
    happy: Int = 80,
    sick: Boolean = false,
    dirty: Boolean = false,
    bond: Int = 30,
) = Pet(
    name = "Tama", species = Species.BABY, stage = Stage.BABY,
    bornAtMs = 0, stageStartMs = 0, lastUpdatedMs = 0,
    stats = Stats(hunger = hunger, happy = happy, energy = 90, sick = sick, dirty = dirty, bond = bond),
)

val PreviewShots: List<PreviewShot> = listOf(
    PreviewShot("01_egg", "Egg") { vm ->
        HomeScreen(vm, Pet(name = "Tama"), ownsBeach = false, ownsSpace = false, rugId = 4)
    },
    PreviewShot("02_home", "Home") { vm ->
        HomeScreen(vm, previewBaby(), ownsBeach = false, ownsSpace = false, rugId = 1)
    },
    PreviewShot("03_sleeping", "Sleeping") { vm ->
        HomeScreen(
            vm, previewBaby(hunger = 45, happy = 55).copy(asleep = true, lightOn = false),
            ownsBeach = false, ownsSpace = false, rugId = 2,
        )
    },
    PreviewShot("04_sick", "Sick") { vm ->
        HomeScreen(
            vm, previewBaby(hunger = 40, happy = 50, sick = true, dirty = true),
            ownsBeach = false, ownsSpace = false, rugId = 3,
        )
    },
    PreviewShot("05_status", "Status") { vm ->
        StatusScreen(vm, previewBaby(hunger = 60, happy = 80))
    },
    PreviewShot("06_shop", "Shop") { vm ->
        ShopScreen(vm, previewBaby().copy(gp = 120))
    },
    PreviewShot("07_help", "Help") { vm ->
        HelpScreen(vm)
    },
)
