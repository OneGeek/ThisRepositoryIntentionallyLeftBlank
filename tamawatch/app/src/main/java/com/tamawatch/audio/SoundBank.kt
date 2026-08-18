package com.tamawatch.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.tamawatch.core.model.DomainEvent

/**
 * Low-latency chiptune SFX via SoundPool. Sounds live in res/raw as the
 * generated WAVs. Respects a runtime sound toggle.
 */
class SoundBank(private val context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()

    private val ids = HashMap<String, Int>()
    var enabled: Boolean = true

    private val names = listOf(
        "sfx_select", "sfx_confirm", "sfx_cancel", "sfx_error",
        "sfx_eat", "sfx_drink", "sfx_refuse", "sfx_flush", "sfx_heal", "sfx_pet",
        "sfx_happy", "sfx_sad", "sfx_sick", "sfx_sleep", "sfx_call", "sfx_talk_react",
        "sfx_hatch", "sfx_evolve", "sfx_goal", "sfx_coin", "sfx_buy",
        "sfx_farewell", "sfx_levelup",
        "sfx_game_start", "sfx_jump", "sfx_score", "sfx_miss", "sfx_game_win", "sfx_game_lose",
    )

    fun preload() {
        for (n in names) {
            val resId = context.resources.getIdentifier(n, "raw", context.packageName)
            if (resId != 0) ids[n] = pool.load(context, resId, 1)
        }
    }

    fun play(name: String, volume: Float = 1f) {
        if (!enabled) return
        ids[name]?.let { pool.play(it, volume, volume, 1, 0, 1f) }
    }

    /** Map a domain event to its signature sound. */
    fun onEvent(e: DomainEvent) = when (e) {
        is DomainEvent.Hatched -> play("sfx_hatch")
        is DomainEvent.Evolved -> play("sfx_evolve")
        is DomainEvent.Pooped -> play("sfx_sad", 0.5f)
        is DomainEvent.GotSick -> play("sfx_sick")
        is DomainEvent.Recovered -> play("sfx_happy", 0.7f)
        is DomainEvent.Called -> play("sfx_call")
        is DomainEvent.Fed -> play(if (e.refused) "sfx_refuse" else if (e.kind.name == "MEAL") "sfx_eat" else "sfx_drink")
        is DomainEvent.Cleaned -> play("sfx_flush")
        is DomainEvent.Healed -> play("sfx_heal")
        is DomainEvent.Petted -> if (e.effective) play("sfx_pet") else play("sfx_pet", 0.35f)
        is DomainEvent.Scolded -> play(if (e.correct) "sfx_confirm" else "sfx_error")
        is DomainEvent.EarnedGp -> play("sfx_coin", 0.6f)
        is DomainEvent.StepGoal -> play("sfx_goal")
        is DomainEvent.Bought -> play("sfx_buy")
        is DomainEvent.Farewell -> play("sfx_farewell")
        is DomainEvent.Died -> play("sfx_sad")
        else -> Unit
    }

    fun release() = pool.release()
}
