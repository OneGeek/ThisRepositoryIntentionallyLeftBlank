package com.tamawatch.core.engine

import com.tamawatch.core.model.*

/**
 * Stage progression + care-driven branching (GDD §3). Aging runs on real
 * elapsed time so it is independent of the vitals catch-up cap; multiple
 * stages can chain in one call after a very long absence.
 */
object EvolutionEngine {

    private fun stageDurationMs(stage: Stage): Long? = when (stage) {
        Stage.EGG -> Tuning.EGG_MS
        Stage.BABY -> Tuning.BABY_MS
        Stage.CHILD -> Tuning.CHILD_MS
        Stage.TEEN -> Tuning.TEEN_MS
        Stage.ADULT -> Tuning.ADULT_MS
        Stage.FAREWELL -> null
    }

    /** Lower raw score = better care. Mapped to a tier. */
    fun careScore(care: CareLog): Double {
        val minutes = care.careMinutes.coerceAtLeast(1)
        val avgStepsPerDay = care.stepSum.toDouble() / (minutes / (24.0 * 60.0)).coerceAtLeast(1.0)
        return 3.0 * care.misses +
            2.5 * care.sickCount +
            2.0 * care.disciplineMistakes +
            1.5 * care.disturbedNights -
            0.5 * care.gamesPlayed -
            0.0005 * avgStepsPerDay
    }

    fun tierOf(care: CareLog): CareTier {
        val s = careScore(care)
        return when {
            s <= 2.0 -> CareTier.GOOD
            s <= 6.0 -> CareTier.AVERAGE
            else -> CareTier.NEGLECT
        }
    }

    private fun childFor(tier: CareTier) = when (tier) {
        CareTier.GOOD -> Species.CHILD_A
        CareTier.AVERAGE -> Species.CHILD_B
        CareTier.NEGLECT -> Species.CHILD_C
    }

    private fun teenFor(child: Species, tier: CareTier): Species = when (child) {
        Species.CHILD_A -> if (tier == CareTier.GOOD) Species.TEEN_A else Species.TEEN_B
        Species.CHILD_B -> when (tier) {
            CareTier.GOOD -> Species.TEEN_B
            CareTier.AVERAGE -> Species.TEEN_C
            CareTier.NEGLECT -> Species.TEEN_D
        }
        else -> if (tier == CareTier.NEGLECT) Species.TEEN_E else Species.TEEN_D
    }

    private fun adultFor(teen: Species, tier: CareTier): Species = when (teen) {
        Species.TEEN_A -> if (tier == CareTier.GOOD) Species.ADULT_A else Species.ADULT_B
        Species.TEEN_B -> if (tier == CareTier.GOOD) Species.ADULT_B else Species.ADULT_C
        Species.TEEN_C -> if (tier == CareTier.GOOD) Species.ADULT_D else Species.ADULT_E
        Species.TEEN_D -> if (tier == CareTier.NEGLECT) Species.ADULT_H else Species.ADULT_F
        Species.TEEN_E -> if (tier == CareTier.GOOD) Species.ADULT_G else Species.ADULT_H
        else -> Species.ADULT_F
    }

    fun maybeAdvanceStage(pet: Pet, nowMs: Long): Sim {
        var p = pet
        val events = mutableListOf<DomainEvent>()
        while (true) {
            val dur = stageDurationMs(p.stage) ?: break
            if (nowMs - p.stageStartMs < dur) break
            val tier = tierOf(p.care)
            val newStart = p.stageStartMs + dur
            p = when (p.stage) {
                Stage.EGG -> {
                    events += DomainEvent.Hatched
                    p.copy(stage = Stage.BABY, species = Species.BABY,
                        stageStartMs = newStart, bornAtMs = if (p.bornAtMs == 0L) newStart else p.bornAtMs,
                        care = p.care.resetForNewStage())
                }
                Stage.BABY -> {
                    val child = childFor(tier)
                    events += DomainEvent.Evolved(child, tier)
                    p.copy(stage = Stage.CHILD, species = child, stageStartMs = newStart, care = p.care.resetForNewStage())
                }
                Stage.CHILD -> {
                    val teen = teenFor(p.species, tier)
                    events += DomainEvent.Evolved(teen, tier)
                    p.copy(stage = Stage.TEEN, species = teen, stageStartMs = newStart, care = p.care.resetForNewStage())
                }
                Stage.TEEN -> {
                    val adult = adultFor(p.species, tier)
                    events += DomainEvent.Evolved(adult, tier)
                    p.copy(stage = Stage.ADULT, species = adult, stageStartMs = newStart, care = p.care.resetForNewStage())
                }
                Stage.ADULT -> {
                    events += DomainEvent.Farewell
                    p.copy(stage = Stage.FAREWELL, alive = false, stageStartMs = newStart)
                }
                Stage.FAREWELL -> break
            }
            if (p.stage == Stage.FAREWELL) break
        }
        return Sim(p, events)
    }

    /** Legacy carry-over: a well-raised predecessor gives the next egg a leg up. */
    fun spawnNextGeneration(previous: Pet, name: String, nowMs: Long): Pet {
        val tier = tierOf(previous.care)
        val legacyGp = when (tier) {
            CareTier.GOOD -> 300
            CareTier.AVERAGE -> 150
            CareTier.NEGLECT -> 50
        }
        return Pet(
            name = name,
            species = Species.EGG,
            stage = Stage.EGG,
            generation = previous.generation + 1,
            bornAtMs = 0,
            stageStartMs = nowMs,
            lastUpdatedMs = nowMs,
            lightOn = true,
            gp = previous.gp + legacyGp,
        )
    }

    fun newEgg(name: String, nowMs: Long): Pet =
        Pet(name = name, species = Species.EGG, stage = Stage.EGG, generation = 1,
            stageStartMs = nowMs, lastUpdatedMs = nowMs, gp = 50)
}
