package com.tamawatch.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.tamawatch.core.model.Species
import com.tamawatch.ui.common.PixelFrame
import com.tamawatch.ui.common.PixelSprite
import kotlinx.coroutines.delay
import kotlin.random.Random

private val NAMES = listOf("Tama", "Momo", "Pip", "Kuro", "Yuki", "Bibi", "Nana", "Taro")

@Composable
fun Onboarding(onStart: (String) -> Unit) {
    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { PixelSprite("spr_egg", "idle", 2, Modifier.size(80.dp)) }
        item { Text("Name your Tama", style = MaterialTheme.typography.title3, textAlign = TextAlign.Center) }
        items(NAMES) { n ->
            Button(onClick = { onStart(n) }, modifier = Modifier.fillMaxWidth(0.8f)) { Text(n) }
        }
        item { Button(onClick = { onStart(NAMES[Random.nextInt(NAMES.size)]) }, modifier = Modifier.fillMaxWidth(0.8f)) { Text("Surprise me") } }
    }
}

@Composable
fun HatchOverlay(onDone: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PixelFrame("bg_egg", 0, Modifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PixelSprite("cut_hatch", "play", 4, Modifier.size(140.dp))
            Text("It hatched!", style = MaterialTheme.typography.title3)
        }
    }
    LaunchedEffect(Unit) { delay(2200); onDone() }
}

@Composable
fun EvolveOverlay(to: Species, onDone: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                PixelSprite("cut_evolve", "play", 6, Modifier.size(180.dp))
                PixelSprite(to.spriteId, "happy", 4, Modifier.size(72.dp))
            }
            Text("Evolved into ${to.display}!", style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)
        }
    }
    LaunchedEffect(Unit) { delay(2600); onDone() }
}

@Composable
fun FarewellOverlay(onNext: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(2600); show = true }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PixelFrame("bg_room_night", 0, Modifier.fillMaxSize())
        if (!show) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PixelSprite("cut_farewell", "play", 3, Modifier.size(120.dp))
                Text("Farewell…", style = MaterialTheme.typography.title3)
            }
        } else {
            Onboarding(onStart = onNext)
        }
    }
}
