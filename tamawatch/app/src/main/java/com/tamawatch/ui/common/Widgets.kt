package com.tamawatch.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Text

private val HeartRed = Color(0xFFDF3E3E)

/** A row of up to 4 hearts (Hunger / Happy). Icon + shape, not color alone. */
@Composable
fun HeartRow(filled: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(4) { i ->
            PixelFrame(if (i < filled) "ui_heart_full" else "ui_heart_empty", 0, Modifier.size(14.dp))
        }
    }
}

/**
 * A Minecraft-style meter where each point IS its icon: `filled` colored icons
 * followed by dimmed "empty" ones (e.g. meat-shanks for Hunger, smileys for
 * Happy). No text label needed — the icon carries the meaning.
 */
@Composable
fun IconMeter(fullId: String, emptyId: String, filled: Int, count: Int = 4, size: Dp = 14.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(count) { i -> PixelFrame(if (i < filled) fullId else emptyId, 0, Modifier.size(size)) }
    }
}

/** A labeled 0..max bar (Energy / Bond / Discipline). Drawn in code per plan §4. */
@Composable
fun StatBar(label: String, value: Int, max: Int = 100, tint: Color = Color(0xFF60963C), modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = androidx.wear.compose.material.MaterialTheme.typography.caption3)
        Box(
            Modifier.width(64.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF)),
        ) {
            val frac = (value.toFloat() / max).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth(frac).height(8.dp).background(tint))
        }
    }
}

@Composable
fun RoundPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xCC1A1C2E))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun GpBadge(gp: Int, steps: Long, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        PixelSprite("ui_gp_coin", "spin", 4, Modifier.size(14.dp))
        Text("$gp", style = androidx.wear.compose.material.MaterialTheme.typography.caption2)
        Box(Modifier.width(6.dp))
        PixelFrame("ui_step_shoe", 0, Modifier.size(14.dp))
        Text("$steps", style = androidx.wear.compose.material.MaterialTheme.typography.caption2)
    }
}
