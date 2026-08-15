package com.tamawatch.core.model

/**
 * Every balance constant in one place (GDD §11) so tuning never touches logic.
 * Rates are expressed per real minute; the engine steps minute-by-minute.
 */
object Tuning {
    // Vital decay (points per awake minute; a heart = 25 points on the 0..100 scale)
    const val HEART_POINTS = 100.0 / Stats.HEARTS           // 25
    const val HUNGER_DECAY_PER_MIN = HEART_POINTS / (3.0 * 60.0)   // 1 heart / ~3 h
    const val HAPPY_DECAY_PER_MIN = HEART_POINTS / (2.5 * 60.0)    // 1 heart / ~2.5 h
    const val ENERGY_DECAY_PER_MIN = 100.0 / (16.0 * 60.0)  // full / 16 h awake
    const val ENERGY_REGEN_PER_MIN = 100.0 / (8.0 * 60.0)   // full / 8 h asleep
    const val ASLEEP_DECAY_FACTOR = 0.35                     // vitals decay slower asleep

    const val CRIT = 15            // a vital at/below this triggers a call
    const val WEIGHT_MIN = 5
    const val WEIGHT_MAX = 99

    // Random events (probability per awake minute)
    const val POOP_CHANCE_PER_MIN = 1.0 / 180.0   // ~ every 3 h on average
    const val SICK_CHANCE_PER_MIN = 0.0008
    const val SICK_NEGLECT_MULT = 6.0             // when a need is empty or dirty

    // Attention / fail state
    const val ATTENTION_TIMEOUT_MIN = 60          // ignored this long = 1 care miss
    const val DEATH_CRITICAL_MIN = 6 * 60         // both hunger & happy empty this long = death

    // Steps → economy
    const val STEPS_PER_GP = 20
    const val DAILY_STEP_GOAL = 6000
    const val DAILY_GOAL_BONUS_GP = 200
    const val STEP_HAPPY_PER_1K = 3               // +happy per 1000 steps
    const val STEP_WEIGHT_PER_1K = 1              // -weight per 1000 steps

    // Offline catch-up
    const val OFFLINE_CAP_MIN = 12 * 60           // never integrate more than 12 h per gap

    // Stage durations (ms). Short early stages for quick delight, long adult.
    const val EGG_MS = 3 * 60_000L
    const val BABY_MS = 10 * 60_000L
    const val CHILD_MS = 24 * 60 * 60_000L
    const val TEEN_MS = 2 * 24 * 60 * 60_000L
    const val ADULT_MS = 4 * 24 * 60 * 60_000L

    // Background tick
    const val TICK_MINUTES = 15L

    // Feeding
    const val OVERFEED_HUNGER = 95   // refuse meals above this
    const val PET_HAPPY = 4
    const val PET_BOND = 2
}
