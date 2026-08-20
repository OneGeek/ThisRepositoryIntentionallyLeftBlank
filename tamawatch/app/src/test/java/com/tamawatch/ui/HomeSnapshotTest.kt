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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
// Galaxy Watch Ultra, from the on-device capture's DisplayMetrics header:
// 480x480 px at 340dpi (density 2.125) => 226x226 dp. Matching that here makes the
// synthetic render 480x480 px, 1:1 with the device, so dp-sized elements (hubs,
// text) are the right size relative to the screen, not just positioned right.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w226dp-h300dp-340dpi")   // h tall enough to hold the 480px shot node
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
                // 226dp @ 340dpi => 480px, matching the Galaxy Watch Ultra 1:1.
                Box(Modifier.size(226.dp).background(Color.Black).testTag("shot")) {
                    Box(Modifier.size(226.dp).clip(CircleShape)) { PreviewShots[index].content(vm) }
                }
            }
        }
        PreviewShots.indices.forEach { i ->
            compose.runOnUiThread { index = i }
            compose.waitForIdle()
            compose.onNodeWithTag("shot").captureRoboImage("build/screens/${PreviewShots[i].id}.png")
        }
    }
}
