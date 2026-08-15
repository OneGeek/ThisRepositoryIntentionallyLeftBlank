package com.tamawatch.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tamawatch.core.model.SleepWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val soundOn: Boolean = true,
    val reduceMotion: Boolean = false,
    val micEnabled: Boolean = false,
    val sleep: SleepWindow = SleepWindow(),
    val onboarded: Boolean = false,
)

private val Context.dataStore by preferencesDataStore(name = "tama_settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val sound = booleanPreferencesKey("sound_on")
        val reduce = booleanPreferencesKey("reduce_motion")
        val mic = booleanPreferencesKey("mic_enabled")
        val sleepStart = intPreferencesKey("sleep_start")
        val sleepEnd = intPreferencesKey("sleep_end")
        val onboarded = booleanPreferencesKey("onboarded")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            soundOn = p[Keys.sound] ?: true,
            reduceMotion = p[Keys.reduce] ?: false,
            micEnabled = p[Keys.mic] ?: false,
            sleep = SleepWindow(p[Keys.sleepStart] ?: 22, p[Keys.sleepEnd] ?: 8),
            onboarded = p[Keys.onboarded] ?: false,
        )
    }

    suspend fun update(block: (Settings) -> Settings) {
        context.dataStore.edit { prefs ->
            val cur = Settings(
                soundOn = prefs[Keys.sound] ?: true,
                reduceMotion = prefs[Keys.reduce] ?: false,
                micEnabled = prefs[Keys.mic] ?: false,
                sleep = SleepWindow(prefs[Keys.sleepStart] ?: 22, prefs[Keys.sleepEnd] ?: 8),
                onboarded = prefs[Keys.onboarded] ?: false,
            )
            val next = block(cur)
            prefs[Keys.sound] = next.soundOn
            prefs[Keys.reduce] = next.reduceMotion
            prefs[Keys.mic] = next.micEnabled
            prefs[Keys.sleepStart] = next.sleep.startHour
            prefs[Keys.sleepEnd] = next.sleep.endHour
            prefs[Keys.onboarded] = next.onboarded
        }
    }
}
