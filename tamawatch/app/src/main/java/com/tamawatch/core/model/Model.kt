package com.tamawatch.core.model

/**
 * TamaWatch domain model. Pure Kotlin, no Android imports, so the whole
 * simulation is unit-testable on the JVM. Every mutating operation returns a
 * fresh immutable [Pet] plus a list of [DomainEvent]s that the UI/audio/haptic
 * layers react to.
 */

enum class Stage { EGG, BABY, CHILD, TEEN, ADULT, FAREWELL }

/** All 17 creatures + egg. spriteId joins 1:1 with the generated asset IDs. */
enum class Species(val stage: Stage, val spriteId: String, val display: String) {
    EGG(Stage.EGG, "spr_egg", "Egg"),
    BABY(Stage.BABY, "spr_baby", "Babymon"),
    CHILD_A(Stage.CHILD, "spr_child_a", "Kidmon"),
    CHILD_B(Stage.CHILD, "spr_child_b", "Pipmon"),
    CHILD_C(Stage.CHILD, "spr_child_c", "Grumon"),
    TEEN_A(Stage.TEEN, "spr_teen_a", "Sunmon"),
    TEEN_B(Stage.TEEN, "spr_teen_b", "Bowmon"),
    TEEN_C(Stage.TEEN, "spr_teen_c", "Hornmon"),
    TEEN_D(Stage.TEEN, "spr_teen_d", "Spikmon"),
    TEEN_E(Stage.TEEN, "spr_teen_e", "Wingmon"),
    ADULT_A(Stage.ADULT, "spr_adult_a", "Angelmon"),
    ADULT_B(Stage.ADULT, "spr_adult_b", "Leafmon"),
    ADULT_C(Stage.ADULT, "spr_adult_c", "Fairymon"),
    ADULT_D(Stage.ADULT, "spr_adult_d", "Aquamon"),
    ADULT_E(Stage.ADULT, "spr_adult_e", "Embermon"),
    ADULT_F(Stage.ADULT, "spr_adult_f", "Mystmon"),
    ADULT_G(Stage.ADULT, "spr_adult_g", "Dracomon"),
    ADULT_H(Stage.ADULT, "spr_adult_h", "Rockmon");

    /** Shared emote sheet for this stage (sad/sick/sleep/eat/call). */
    fun stagesetId(): String? = when (stage) {
        Stage.CHILD -> "spr_stageset_child"
        Stage.TEEN -> "spr_stageset_teen"
        Stage.ADULT -> "spr_stageset_adult"
        else -> null // baby has its own full sheet; egg has none
    }
}

enum class CareTier { GOOD, AVERAGE, NEGLECT }

/**
 * Optional decorative coat pattern painted over the pet sprite, clipped to its
 * silhouette. Purely cosmetic — it never touches the simulation. [id] is the
 * stable value persisted in settings; [display] is the Settings-screen label.
 */
enum class CoatStyle(val id: String, val display: String) {
    NONE("none", "None"),
    TIGER("tiger", "Tiger Stripes"),
    LEOPARD("leopard", "Leopard Spots"),
    TRIANGLE("triangle", "Triangle Spots");

    companion object {
        fun from(id: String?): CoatStyle = entries.firstOrNull { it.id == id } ?: NONE
        fun next(cur: CoatStyle): CoatStyle = entries[(cur.ordinal + 1) % entries.size]
    }
}

/** Live vitals. Hunger/Happy/Energy are 0..100 internally; UI shows hearts. */
data class Stats(
    val hunger: Int = 70,
    val happy: Int = 70,
    val energy: Int = 90,
    val weightG: Int = 20,
    val discipline: Int = 30,
    val bond: Int = 20,
    val dirty: Boolean = false,
    val sick: Boolean = false,
) {
    val hungerHearts get() = hearts(hunger)
    val happyHearts get() = hearts(happy)

    companion object {
        const val MAX = 100
        const val HEARTS = 4
        fun hearts(v: Int): Int = ((v.coerceIn(0, MAX) * HEARTS + MAX - 1) / MAX)
    }
}

/** Per-stage care accumulators that drive evolution and the fail state. */
data class CareLog(
    val misses: Int = 0,            // attention calls that timed out
    val sickCount: Int = 0,
    val disciplineMistakes: Int = 0,
    val gamesPlayed: Int = 0,
    val stepSum: Long = 0,
    val careMinutes: Int = 0,       // minutes simulated in this stage
    val criticalMinutes: Int = 0,   // consecutive minutes both hunger & happy empty
    val disturbedNights: Int = 0,
) {
    fun resetForNewStage() = CareLog()
}

data class Pet(
    val name: String = "Tama",
    val species: Species = Species.EGG,
    val stage: Stage = Stage.EGG,
    val generation: Int = 1,
    val bornAtMs: Long = 0,
    val stageStartMs: Long = 0,
    val lastUpdatedMs: Long = 0,
    val lightOn: Boolean = true,
    val asleep: Boolean = false,
    val stats: Stats = Stats(),
    val care: CareLog = CareLog(),
    val pendingCallSinceMs: Long? = null,
    val gp: Int = 0,
    val stepBaseline: Long? = null,   // raw sensor baseline for today
    val stepsToday: Long = 0,
    val stepDayEpoch: Long = 0,       // days since epoch, for daily reset
    val alive: Boolean = true,
) {
    val ageDays get() = if (bornAtMs == 0L) 0 else ((lastUpdatedMs - bornAtMs) / 86_400_000L).toInt()

    /** Any unmet need the pet is "calling" about right now. */
    fun needsAttention(): Boolean =
        alive && !asleep && (stats.hunger <= Tuning.CRIT || stats.happy <= Tuning.CRIT ||
            stats.dirty || stats.sick)
}

enum class FoodKind { MEAL, SNACK }

data class Food(
    val id: String,
    val kind: FoodKind,
    val display: String,
    val iconId: String,
    val hunger: Int,   // restores hunger
    val happy: Int,    // restores happy
    val weight: Int,   // grams added
    val price: Int,
)

data class Cosmetic(val id: String, val display: String, val bgId: String, val price: Int)

sealed interface DomainEvent {
    data object Hatched : DomainEvent
    data class Evolved(val to: Species, val tier: CareTier) : DomainEvent
    data object Pooped : DomainEvent
    data object GotSick : DomainEvent
    data object Recovered : DomainEvent
    data object Called : DomainEvent
    data class CareMiss(val total: Int) : DomainEvent
    data object SleepStarted : DomainEvent
    data object WokeUp : DomainEvent
    data class Fed(val kind: FoodKind, val refused: Boolean) : DomainEvent
    data object Cleaned : DomainEvent
    data object Healed : DomainEvent
    data object Petted : DomainEvent
    data class Scolded(val correct: Boolean) : DomainEvent
    data class EarnedGp(val amount: Int) : DomainEvent
    data object StepGoal : DomainEvent
    data object Bought : DomainEvent
    data object Died : DomainEvent
    data object Farewell : DomainEvent
}

/** Return type for every engine operation. */
data class Sim(val pet: Pet, val events: List<DomainEvent> = emptyList())

data class SleepWindow(val startHour: Int = 22, val endHour: Int = 8) {
    fun contains(hour: Int): Boolean =
        if (startHour < endHour) hour in startHour until endHour
        else hour >= startHour || hour < endHour
}
