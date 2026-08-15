package com.tamawatch.core.engine

import com.tamawatch.core.model.*

/**
 * Steps → Gotchi Points, daily goal bonus, mini-game rewards, and shop
 * spending. Steps arrive as the watch's cumulative TYPE_STEP_COUNTER value;
 * we track a per-day baseline and reward on each increment.
 */
object EconomyEngine {

    /**
     * @param rawTotal cumulative sensor steps since boot.
     * @param todayEpochDay days-since-epoch in local time, for the daily reset.
     */
    fun applySteps(pet: Pet, rawTotal: Long, todayEpochDay: Long, nowMs: Long): Sim {
        val events = mutableListOf<DomainEvent>()
        // New day, or first ever, or sensor discontinuity (reboot → smaller total): rebaseline.
        val baseline = pet.stepBaseline
        val newDay = todayEpochDay != pet.stepDayEpoch
        if (baseline == null || newDay || rawTotal < baseline) {
            return Sim(
                pet.copy(
                    stepBaseline = rawTotal,
                    stepsToday = 0,
                    stepDayEpoch = todayEpochDay,
                    lastUpdatedMs = nowMs,
                )
            )
        }

        val newToday = (rawTotal - baseline).coerceAtLeast(0)
        val old = pet.stepsToday
        if (newToday <= old) return Sim(pet.copy(lastUpdatedMs = nowMs))

        // GP from steps (integer, cumulative-correct across increments).
        val gpGain = (newToday / Tuning.STEPS_PER_GP - old / Tuning.STEPS_PER_GP).toInt()
        // Per-1000-step happiness / slimming.
        val crossed = (newToday / 1000 - old / 1000).toInt()
        var gp = pet.gp + gpGain
        if (gpGain > 0) events += DomainEvent.EarnedGp(gpGain)

        // Daily goal bonus (once).
        if (newToday >= Tuning.DAILY_STEP_GOAL && old < Tuning.DAILY_STEP_GOAL) {
            gp += Tuning.DAILY_GOAL_BONUS_GP
            events += DomainEvent.StepGoal
            events += DomainEvent.EarnedGp(Tuning.DAILY_GOAL_BONUS_GP)
        }

        val s = pet.stats
        val stats = s.copy(
            happy = (s.happy + crossed * Tuning.STEP_HAPPY_PER_1K).coerceIn(0, Stats.MAX),
            weightG = (s.weightG - crossed * Tuning.STEP_WEIGHT_PER_1K).coerceIn(Tuning.WEIGHT_MIN, Tuning.WEIGHT_MAX),
        )
        return Sim(
            pet.copy(
                gp = gp,
                stepsToday = newToday,
                stats = stats,
                care = pet.care.copy(stepSum = pet.care.stepSum + (newToday - old)),
                lastUpdatedMs = nowMs,
            ),
            events,
        )
    }

    fun awardGameGp(pet: Pet, score: Int, nowMs: Long): Sim {
        val reward = 5 + score
        return Sim(
            pet.copy(gp = pet.gp + reward, lastUpdatedMs = nowMs),
            listOf(DomainEvent.EarnedGp(reward)),
        )
    }

    /** Returns null if the pet can't afford [amount]. */
    fun trySpend(pet: Pet, amount: Int, nowMs: Long): Sim? =
        if (pet.gp >= amount)
            Sim(pet.copy(gp = pet.gp - amount, lastUpdatedMs = nowMs), listOf(DomainEvent.Bought))
        else null
}
