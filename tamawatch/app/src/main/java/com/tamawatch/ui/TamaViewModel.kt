package com.tamawatch.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tamawatch.core.data.Repository
import com.tamawatch.core.data.Settings
import com.tamawatch.core.data.SettingsStore
import com.tamawatch.core.model.DomainEvent
import com.tamawatch.core.model.Species
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Screen { Home, Feed, PlayMenu, Jump, Guess, Catch, Status, Shop, Steps, Settings, Help }

sealed interface Cutscene {
    data object Hatch : Cutscene
    data class Evolve(val to: Species) : Cutscene
    data object Farewell : Cutscene
}

class TamaViewModel(
    val repository: Repository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val pet = repository.pet
    val inventory = repository.inventory
    val settings: StateFlow<Settings> = settingsStore.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    var screen by mutableStateOf(Screen.Home)
        private set
    var ringIndex by mutableIntStateOf(0)
    var cutscene by mutableStateOf<Cutscene?>(null)
        private set

    init {
        viewModelScope.launch { repository.load() }
        viewModelScope.launch {
            repository.events.collect { e ->
                when (e) {
                    is DomainEvent.Hatched -> cutscene = Cutscene.Hatch
                    is DomainEvent.Evolved -> cutscene = Cutscene.Evolve(e.to)
                    is DomainEvent.Farewell, is DomainEvent.Died -> cutscene = Cutscene.Farewell
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            settingsStore.flow.collect { repository.setSleepWindow(it.sleep) }
        }
    }

    fun go(s: Screen) { screen = s }
    fun home() { screen = Screen.Home }
    fun dismissCutscene() { cutscene = null }

    fun tick() = viewModelScope.launch { repository.tick() }
    fun onStepTotal(total: Long) = viewModelScope.launch { repository.onStepTotal(total) }

    fun feed(foodId: String) = viewModelScope.launch { repository.feed(foodId); home() }
    fun clean() = viewModelScope.launch { repository.clean() }
    fun heal() = viewModelScope.launch { repository.heal() }
    fun petIt() = viewModelScope.launch { repository.petIt() }
    fun scold() = viewModelScope.launch { repository.scold() }
    fun toggleLight() = viewModelScope.launch { repository.toggleLight() }
    fun finishGame(score: Int) = viewModelScope.launch { repository.finishGame(score); home() }

    fun buyFood(id: String, done: (Boolean) -> Unit = {}) =
        viewModelScope.launch { done(repository.buyFood(id)) }
    fun buyMedicine(done: (Boolean) -> Unit = {}) =
        viewModelScope.launch { done(repository.buyMedicine()) }
    fun buyCosmetic(id: String, done: (Boolean) -> Unit = {}) =
        viewModelScope.launch { done(repository.buyCosmetic(id)) }

    fun startFreshPet(name: String) = viewModelScope.launch {
        repository.startFreshPet(name)
        settingsStore.update { it.copy(onboarded = true) }
        home()
    }
    fun startNextGeneration(name: String) = viewModelScope.launch { repository.startNextGeneration(name); home() }

    fun hatchNow() = viewModelScope.launch { repository.hatchNow() }

    fun setSound(on: Boolean) = viewModelScope.launch { settingsStore.update { it.copy(soundOn = on) } }
    fun setReduceMotion(on: Boolean) = viewModelScope.launch { settingsStore.update { it.copy(reduceMotion = on) } }
    fun setMic(on: Boolean) = viewModelScope.launch { settingsStore.update { it.copy(micEnabled = on) } }

    class Factory(
        private val repository: Repository,
        private val settingsStore: SettingsStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TamaViewModel(repository, settingsStore) as T
    }
}
