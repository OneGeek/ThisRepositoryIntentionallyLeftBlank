package com.tamawatch.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.tamawatch.tama
import com.tamawatch.ui.common.ProvidePixel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Synthetic render of the REAL Compose screens to PNGs on the JVM (no emulator).
 * Drives the SAME [PreviewShots] the on-device capture uses (Settings → capture),
 * clipped to the round watch shape on a black backdrop, so the two can be compared
 * pixel-for-pixel. Record: `./gradlew :app:recordRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w480dp-h480dp-round-xhdpi")
class HomeSnapshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun renderAllStates() {
        val c = ctx.tama
        val vm = TamaViewModel(c.repository, c.settings)
        var index by mutableIntStateOf(0)
        compose.setContent {
            ProvidePixel(c.spriteBank, reduceMotion = true) {
                Box(Modifier.size(480.dp).background(Color.Black)) {
                    Box(Modifier.size(480.dp).clip(CircleShape)) { PreviewShots[index].content(vm) }
                }
            }
        }
        PreviewShots.indices.forEach { i ->
            compose.runOnUiThread { index = i }
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/screens/${PreviewShots[i].id}.png")
        }
    }
}
