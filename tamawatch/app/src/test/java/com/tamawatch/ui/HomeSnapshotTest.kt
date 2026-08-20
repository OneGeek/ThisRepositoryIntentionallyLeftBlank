package com.tamawatch.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.tamawatch.core.model.Pet
import com.tamawatch.core.model.Species
import com.tamawatch.core.model.Stage
import com.tamawatch.core.model.Stats
import com.tamawatch.tama
import com.tamawatch.ui.common.ProvidePixel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the REAL Compose screens to PNGs on the JVM — the same composables that
 * ship, the real generated assets — so visual changes can be eyeballed before
 * deploy without an emulator. Each frame is clipped to the round watch shape on a
 * black backdrop, so anything the bezel would cut off shows up here too.
 *
 * Record all frames: `./gradlew :app:recordRoborazziDebug` (or run via tools/build.sh).
 * Reduce-motion is on so animated sprites settle on a stable frame.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w480dp-h480dp-round-xhdpi")
class HomeSnapshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private fun vm() = TamaViewModel(ctx.tama.repository, ctx.tama.settings)

    private fun capture(name: String, content: @Composable () -> Unit) {
        val bank = ctx.tama.spriteBank
        compose.setContent {
            ProvidePixel(bank, reduceMotion = true) {
                Box(Modifier.size(480.dp).background(Color.Black)) {
                    Box(Modifier.size(480.dp).clip(CircleShape)) { content() }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screens/$name.png")
    }

    private fun baby(hunger: Int = 70, happy: Int = 80) = Pet(
        name = "Tama", species = Species.BABY, stage = Stage.BABY,
        bornAtMs = 0, stageStartMs = 0, lastUpdatedMs = 0,
        stats = Stats(hunger = hunger, happy = happy, energy = 90),
    )

    @Test fun egg() = capture("01_egg") {
        HomeScreen(vm(), Pet(name = "Tama"), ownsBeach = false, ownsSpace = false, rugId = 4)
    }

    @Test fun home() = capture("02_home") {
        HomeScreen(vm(), baby(), ownsBeach = false, ownsSpace = false, rugId = 1)
    }

    @Test fun sleeping() = capture("03_sleeping") {
        HomeScreen(vm(), baby(hunger = 45, happy = 55).copy(asleep = true, lightOn = false),
            ownsBeach = false, ownsSpace = false, rugId = 2)
    }

    @Test fun status() = capture("04_status") {
        StatusScreen(vm(), baby(hunger = 60, happy = 80))
    }

    @Test fun shop() = capture("05_shop") {
        ShopScreen(vm(), baby().copy(gp = 120))
    }

    @Test fun help() = capture("06_help") {
        HelpScreen(vm())
    }
}
