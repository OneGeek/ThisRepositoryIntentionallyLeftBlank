package com.tamawatch.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.tamawatch.core.model.Pet
import com.tamawatch.ui.common.PixelFrame
import com.tamawatch.ui.common.PixelSprite
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun PlayMenu(vm: TamaViewModel) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Play", style = MaterialTheme.typography.title3)
        Spacer(Modifier.height(6.dp))
        Button(onClick = { vm.go(Screen.Jump) }, Modifier.fillMaxWidth(0.7f)) { Text("Jump") }
        Button(onClick = { vm.go(Screen.Guess) }, Modifier.fillMaxWidth(0.7f)) { Text("Guess") }
        Button(onClick = { vm.go(Screen.Catch) }, Modifier.fillMaxWidth(0.7f)) { Text("Catch") }
        Spacer(Modifier.height(4.dp)); BackButton(vm)
    }
}

/** Tap to hop over approaching obstacles. 8 obstacles; score = clean jumps. */
@Composable
fun JumpGame(vm: TamaViewModel, pet: Pet) {
    var score by remember { mutableIntStateOf(0) }
    var count by remember { mutableIntStateOf(0) }
    var obsX by remember { mutableFloatStateOf(1f) }
    var jumping by remember { mutableStateOf(false) }
    var scoredThisObstacle by remember { mutableStateOf(false) }
    val jumpY by animateFloatAsState(if (jumping) -1f else 0f, label = "jump")

    LaunchedEffect(count) {
        if (count >= 8) { vm.finishGame(score); return@LaunchedEffect }
        obsX = 1f; scoredThisObstacle = false
        while (obsX > -0.1f) {
            delay(16)
            obsX -= 0.018f
            if (!scoredThisObstacle && obsX in 0.12f..0.28f) {
                scoredThisObstacle = true
                if (jumping) score++
            }
        }
        count++
    }

    GameFrame("bg_game_jump", "Jump!  $score") { w, h ->
        // w,h are pixels (from onSizeChanged); offset lambdas are in pixels too.
        Box(Modifier.offset { IntOffset((w * 0.2f).toInt(), (h * (0.7f + jumpY * 0.3f)).toInt()) }) {
            PixelSprite(pet.species.spriteId, if (jumping) "happy" else "walk", 6, Modifier.size(48.dp))
        }
        Box(Modifier.offset { IntOffset((w * obsX).toInt(), (h * 0.72f).toInt()) }) {
            PixelSprite("game_obstacle", "idle", 4, Modifier.size(36.dp))
        }
        Box(Modifier.fillMaxSize().clickable(enabled = !jumping) {
            jumping = true
        })
    }
    LaunchedEffect(jumping) { if (jumping) { delay(420); jumping = false } }
}

/** The pet turns left or right — pick the opposite side. Best of 5. */
@Composable
fun GuessGame(vm: TamaViewModel, pet: Pet) {
    var round by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf(Random.nextBoolean()) } // true = pet goes right
    var reveal by remember { mutableStateOf(false) }

    if (round >= 5) { LaunchedEffect(Unit) { vm.finishGame(score) } }

    GameFrame("bg_game_guess", "Guess  $score/5") { _, _ ->
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            PixelSprite(pet.species.spriteId, if (reveal) "walk" else "idle", 4, Modifier.size(56.dp))
            if (reveal) PixelFrame(if (target) "game_arrow_r" else "game_arrow_l", 0, Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { guess(false, target) { correct -> if (correct) score++; reveal = true } }) {
                    PixelFrame("game_arrow_l", 0, Modifier.size(24.dp))
                }
                Button(onClick = { guess(true, target) { correct -> if (correct) score++; reveal = true } }) {
                    PixelFrame("game_arrow_r", 0, Modifier.size(24.dp))
                }
            }
        }
    }
    LaunchedEffect(reveal) {
        if (reveal) { delay(700); round++; target = Random.nextBoolean(); reveal = false }
    }
}

private inline fun guess(picked: Boolean, target: Boolean, result: (Boolean) -> Unit) {
    // you win by picking the side the pet does NOT go
    result(picked != target)
}

/** Falling treats — tap the left/right half to move the basket. Catch good, dodge bad. */
@Composable
fun CatchGame(vm: TamaViewModel, pet: Pet) {
    var score by remember { mutableIntStateOf(0) }
    var ticks by remember { mutableIntStateOf(0) }
    var basketRight by remember { mutableStateOf(false) }
    var treatX by remember { mutableStateOf(Random.nextBoolean()) } // true = right lane
    var treatY by remember { mutableFloatStateOf(0f) }
    var good by remember { mutableStateOf(true) }

    LaunchedEffect(ticks) {
        if (ticks >= 12) { vm.finishGame(score); return@LaunchedEffect }
        treatY = 0f; treatX = Random.nextBoolean(); good = Random.nextInt(100) < 70
        while (treatY < 1f) { delay(20); treatY += 0.02f }
        val caught = (treatX == basketRight)
        if (caught) score += if (good) 1 else -1
        else if (!good) score += 0
        if (score < 0) score = 0
        ticks++
    }

    GameFrame("bg_game_catch", "Catch  $score") { w, h ->
        Box(Modifier.offset { IntOffset((w * if (treatX) 0.62f else 0.28f).toInt(), (h * (0.15f + treatY * 0.55f)).toInt()) }) {
            PixelFrame(if (good) "game_treat_good" else "game_treat_bad", 0, Modifier.size(22.dp))
        }
        Box(Modifier.offset { IntOffset((w * if (basketRight) 0.6f else 0.26f).toInt(), (h * 0.74f).toInt()) }) {
            PixelFrame("game_basket", 0, Modifier.size(44.dp))
        }
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().clickable { basketRight = false })
            Box(Modifier.weight(1f).fillMaxHeight().clickable { basketRight = true })
        }
    }
}

@Composable
private fun GameFrame(bg: String, title: String, content: @Composable BoxScope.(Float, Float) -> Unit) {
    var size by remember { mutableStateOf(Pair(0f, 0f)) }
    Box(Modifier.fillMaxSize().onSizeChanged { size = it.width.toFloat() to it.height.toFloat() }) {
        PixelFrame(bg, 0, Modifier.fillMaxSize())
        content(size.first, size.second)
        Text(title, style = MaterialTheme.typography.caption1,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp), textAlign = TextAlign.Center)
    }
}
