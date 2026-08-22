package com.tamawatch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Top-level router: onboarding → cutscenes → the current screen. */
@Composable
fun WearApp(vm: TamaViewModel) {
    val pet by vm.pet.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val inventory by vm.inventory.collectAsStateWithLifecycle()
    val cutscene = vm.cutscene
    val current = pet

    when {
        !settings.onboarded || current == null -> Onboarding(onStart = vm::startFreshPet)
        cutscene is Cutscene.Hatch -> HatchOverlay(onDone = vm::dismissCutscene)
        cutscene is Cutscene.Evolve -> EvolveOverlay(cutscene.to, onDone = vm::dismissCutscene)
        cutscene is Cutscene.Farewell -> FarewellOverlay(onNext = { name ->
            vm.startNextGeneration(name); vm.dismissCutscene()
        })
        else -> {
            val ownsBeach = (inventory["cos_beach"] ?: 0) > 0
            val ownsSpace = (inventory["cos_space"] ?: 0) > 0
            when (vm.screen) {
                Screen.Home -> HomeScreen(vm, current, ownsBeach, ownsSpace, settings.coat)
                Screen.Feed -> FeedScreen(vm, inventory)
                Screen.PlayMenu -> PlayMenu(vm)
                Screen.Jump -> JumpGame(vm, current)
                Screen.Guess -> GuessGame(vm, current)
                Screen.Catch -> CatchGame(vm, current)
                Screen.Status -> StatusScreen(vm, current)
                Screen.Shop -> ShopScreen(vm, current)
                Screen.Steps -> StepsScreen(vm, current)
                Screen.Settings -> SettingsScreen(vm, settings)
            }
        }
    }
}
