package com.tamawatch.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.tamawatch.tama
import com.tamawatch.ui.common.LocalSprites
import com.tamawatch.ui.common.ProvidePixel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Synthetic frames of the part rig under a FIXED drag, so the per-part behavior is
 * pinned down without a device: at a stretched position the body (and coat) deform,
 * while the face / arms / feature keep their shape and merely shift, and the shadow
 * slides horizontally only. Also proves accessory parenting (a hat pinned to the
 * headTop anchor rides the stretch). reduce-motion = true so the capture is static.
 * Record: `./gradlew :app:recordRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w226dp-h300dp-340dpi")
class RigSnapshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private data class Shot(val id: String, val coat: Int, val live: Offset, val hat: Boolean)

    @Test
    fun rigStretchPoses() {
        val shots = listOf(
            Shot("rig_0_rest", 0, Offset.Zero, false),
            Shot("rig_1_right", 0, Offset(120f, 0f), false),
            Shot("rig_2_up", 0, Offset(0f, -120f), false),
            Shot("rig_3_diag_coat", 1, Offset(95f, -70f), false),
            Shot("rig_4_hat", 0, Offset(80f, -55f), true),
        )
        var i by mutableIntStateOf(0)
        compose.setContent {
            val bank = ctx.tama.spriteBank
            ProvidePixel(bank, reduceMotion = true) {
                val s = shots[i]
                Box(
                    Modifier.size(226.dp).background(Color(0xFF9CC7E0)).testTag("shot"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(226.dp).clip(CircleShape)) {}
                    Box(
                        Modifier.size(96.dp).align(Alignment.Center),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        val rig = LocalSprites.current.parts("spr_baby")!!
                        val maxDragPx = with(LocalDensity.current) { 96.dp.toPx() } * 0.72f
                        val attach = if (s.hat) listOf(RigAttachment("headTop") { Hat() }) else emptyList()
                        RiggedPet(rig, s.coat, s.live, maxDragPx, 1f, reduce = true, attachments = attach)
                    }
                }
            }
        }
        shots.indices.forEach { idx ->
            compose.runOnUiThread { i = idx }
            compose.waitForIdle()
            compose.onNodeWithTag("shot").captureRoboImage("build/screens/${shots[idx].id}.png")
        }
    }
}

/** A stand-in accessory: a little cap, to show a slot attachment rides the stretch. */
@androidx.compose.runtime.Composable
private fun Hat() {
    Box(Modifier.size(30.dp)) {
        Box(
            Modifier.align(Alignment.BottomCenter).size(width = 30.dp, height = 8.dp)
                .clip(RoundedCornerShape(4.dp)).background(Color(0xFF2E4A8C)),
        )
        Box(
            Modifier.align(Alignment.TopCenter).size(width = 20.dp, height = 16.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(Color(0xFF3A5EB0)),
        )
    }
}
