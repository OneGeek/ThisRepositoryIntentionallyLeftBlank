package com.tamawatch.core.engine

import com.tamawatch.core.model.*
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The simulation core. Timestamp-based: [advance] integrates real elapsed time
 * forward minute-by-minute (capped at [Tuning.OFFLINE_CAP_MIN]) so closing the
 * app for hours does the right thing. Stage/age progression is decoupled from
 * the vitals cap and handled by [EvolutionEngine] on real elapsed time.
 *
 * No Android imports — fully JVM-unit-testable.
 */
object CareEngine {

    private fun Int.clamp() = coerceIn(0, Stats.MAX)

    /** Advance the world to [nowMs]. [hourAt] maps a timestamp to local hour-of-day. */
    fun advance(
        pet: Pet,
        nowMs: Long,
        window: SleepWindow = SleepWindow(),
        hourAt: (Long) -> Int,
        rng: Random = Random.Default,
    ): Sim {
        if (!pet.alive || pet.stage == Stage.FAREWELL) {
            return Sim(pet.copy(lastUpdatedMs = nowMs))
        }
        val events = mutableListOf<DomainEvent>()
        var p = pet

        // Egg has no vitals; only age matters — jump straight to stage check.
        if (p.stage != Stage.EGG) {
            val gapMin = ((nowMs - p.lastUpdatedMs) / 60_000L).coerceAtLeast(0)
            val steps = gapMin.coerceAtMost(Tuning.OFFLINE_CAP_MIN.toLong()).toInt()
            var cursor = nowMs - steps * 60_000L

            var hungerF = p.stats.hunger.toDouble()
            var happyF = p.stats.happy.toDouble()
            var energyF = p.stats.energy.toDouble()

            for (i in 0 until steps) {
                cursor += 60_000L
                val hour = hourAt(cursor)
                val asleepNow = window.contains(hour)
                if (asleepNow != p.asleep) {
                    p = p.copy(asleep = asleepNow)
                    events += if (asleepNow) DomainEvent.SleepStarted else DomainEvent.WokeUp
                }
                val disturbed = asleepNow && p.lightOn
                val factor = if (asleepNow) Tuning.ASLEEP_DECAY_FACTOR else 1.0
                val sickMult = if (p.stats.sick) 1.5 else 1.0

                hungerF = (hungerF - Tuning.HUNGER_DECAY_PER_MIN * factor * sickMult).coerceIn(0.0, 100.0)
                happyF = (happyF - Tuning.HAPPY_DECAY_PER_MIN * factor * sickMult).coerceIn(0.0, 100.0)
                energyF = if (asleepNow && !disturbed)
                    (energyF + Tuning.ENERGY_REGEN_PER_MIN).coerceAtMost(100.0)
                else
                    (energyF - Tuning.ENERGY_DECAY_PER_MIN).coerceAtLeast(0.0)

                var stats = p.stats.copy(
                    hunger = hungerF.roundToInt().clamp(),
                    happy = happyF.roundToInt().clamp(),
                    energy = energyF.roundToInt().clamp(),
                )
                var care = p.care.copy(careMinutes = p.care.careMinutes + 1)

                // Poop (awake only)
                if (!asleepNow && !stats.dirty && rng.nextDouble() < Tuning.POOP_CHANCE_PER_MIN) {
                    stats = stats.copy(dirty = true)
                    events += DomainEvent.Pooped
                }
                // Sickness
                if (!stats.sick) {
                    val neglected = stats.hunger <= 0 || stats.happy <= 0 || stats.dirty
                    val chance = Tuning.SICK_CHANCE_PER_MIN * (if (neglected) Tuning.SICK_NEGLECT_MULT else 1.0)
                    if (rng.nextDouble() < chance) {
                        stats = stats.copy(sick = true)
                        care = care.copy(sickCount = care.sickCount + 1)
                        events += DomainEvent.GotSick
                    }
                }
                // Disturbed sleep costs bond gradually
                if (disturbed && care.careMinutes % 30 == 0) {
                    stats = stats.copy(bond = (stats.bond - 1).clamp())
                    care = care.copy(disturbedNights = care.disturbedNights + 1)
                }
                // Death from sustained total neglect
                care = if (stats.hunger <= 0 && stats.happy <= 0)
                    care.copy(criticalMinutes = care.criticalMinutes + 1)
                else
                    care.copy(criticalMinutes = 0)

                p = p.copy(stats = stats, care = care)

                // Attention calls (awake only)
                p = updateAttention(p, cursor, events)

                if (p.care.criticalMinutes >= Tuning.DEATH_CRITICAL_MIN) {
                    p = p.copy(alive = false)
                    events += DomainEvent.Died
                    break
                }
            }
        }

        p = p.copy(lastUpdatedMs = nowMs)
        // Age / stage progression on real elapsed time (independent of vitals cap).
        if (p.alive) {
            val evo = EvolutionEngine.maybeAdvanceStage(p, nowMs)
            p = evo.pet
            events += evo.events
        }
        return Sim(p, events)
    }

    private fun updateAttention(pet: Pet, nowMs: Long, events: MutableList<DomainEvent>): Pet {
        if (!pet.needsAttention()) return if (pet.pendingCallSinceMs == null) pet else pet.copy(pendingCallSinceMs = null)
        return when (val since = pet.pendingCallSinceMs) {
            null -> {
                events += DomainEvent.Called
                pet.copy(pendingCallSinceMs = nowMs)
            }
            else -> if (nowMs - since >= Tuning.ATTENTION_TIMEOUT_MIN * 60_000L) {
                val care = pet.care.copy(misses = pet.care.misses + 1)
                events += DomainEvent.CareMiss(care.misses)
                pet.copy(care = care, pendingCallSinceMs = nowMs)
            } else pet
        }
    }

    private fun clearCallIfResolved(pet: Pet): Pet =
        if (!pet.needsAttention()) pet.copy(pendingCallSinceMs = null) else pet

    // -------------------------------------------------------------- actions
    fun feed(pet: Pet, food: Food, nowMs: Long): Sim {
        if (food.kind == FoodKind.MEAL && pet.stats.hunger >= Tuning.OVERFEED_HUNGER) {
            return Sim(pet.copy(lastUpdatedMs = nowMs), listOf(DomainEvent.Fed(food.kind, refused = true)))
        }
        val s = pet.stats
        var p = pet.copy(
            stats = s.copy(
                hunger = (s.hunger + food.hunger).clamp(),
                happy = (s.happy + food.happy).clamp(),
                weightG = (s.weightG + food.weight).coerceIn(Tuning.WEIGHT_MIN, Tuning.WEIGHT_MAX),
            ),
            lastUpdatedMs = nowMs,
        )
        p = clearCallIfResolved(p)
        return Sim(p, listOf(DomainEvent.Fed(food.kind, refused = false)))
    }

    fun clean(pet: Pet, nowMs: Long): Sim {
        if (!pet.stats.dirty) return Sim(pet.copy(lastUpdatedMs = nowMs))
        var p = pet.copy(
            stats = pet.stats.copy(dirty = false, bond = (pet.stats.bond + 1).clamp()),
            lastUpdatedMs = nowMs,
        )
        p = clearCallIfResolved(p)
        return Sim(p, listOf(DomainEvent.Cleaned))
    }

    fun heal(pet: Pet, nowMs: Long): Sim {
        if (!pet.stats.sick) return Sim(pet.copy(lastUpdatedMs = nowMs))
        var p = pet.copy(
            stats = pet.stats.copy(sick = false, bond = (pet.stats.bond + 1).clamp()),
            lastUpdatedMs = nowMs,
        )
        p = clearCallIfResolved(p)
        return Sim(p, listOf(DomainEvent.Healed, DomainEvent.Recovered))
    }

    fun pet(pet: Pet, nowMs: Long): Sim {
        val s = pet.stats
        return Sim(
            pet.copy(
                stats = s.copy(happy = (s.happy + Tuning.PET_HAPPY).clamp(), bond = (s.bond + Tuning.PET_BOND).clamp()),
                lastUpdatedMs = nowMs,
            ),
            listOf(DomainEvent.Petted),
        )
    }

    /** Discipline is "correct" only when the pet has no real unmet need. */
    fun scold(pet: Pet, nowMs: Long): Sim {
        val correct = !pet.needsAttention()
        val s = pet.stats
        val care = if (correct) pet.care else pet.care.copy(disciplineMistakes = pet.care.disciplineMistakes + 1)
        val stats = if (correct)
            s.copy(discipline = (s.discipline + 8).clamp())
        else
            s.copy(bond = (s.bond - 5).clamp())
        return Sim(pet.copy(stats = stats, care = care, lastUpdatedMs = nowMs), listOf(DomainEvent.Scolded(correct)))
    }

    fun toggleLight(pet: Pet, nowMs: Long): Sim =
        Sim(pet.copy(lightOn = !pet.lightOn, lastUpdatedMs = nowMs))

    /** Apply the outcome of a mini-game round to vitals (GP handled by EconomyEngine). */
    fun applyGameResult(pet: Pet, score: Int, nowMs: Long): Sim {
        val s = pet.stats
        val happyGain = (6 + score / 2).coerceAtMost(30)
        val energyCost = 10
        return Sim(
            pet.copy(
                stats = s.copy(
                    happy = (s.happy + happyGain).clamp(),
                    energy = (s.energy - energyCost).clamp(),
                    weightG = (s.weightG - 1).coerceIn(Tuning.WEIGHT_MIN, Tuning.WEIGHT_MAX),
                ),
                care = pet.care.copy(gamesPlayed = pet.care.gamesPlayed + 1),
                lastUpdatedMs = nowMs,
            ),
            listOf(DomainEvent.Petted), // small positive; happy chirp
        )
    }
}
