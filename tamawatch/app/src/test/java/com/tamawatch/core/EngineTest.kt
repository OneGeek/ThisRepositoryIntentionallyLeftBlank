package com.tamawatch.core

import com.tamawatch.core.engine.CareEngine
import com.tamawatch.core.engine.EconomyEngine
import com.tamawatch.core.engine.EvolutionEngine
import com.tamawatch.core.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Pure-JVM tests for the simulation core — no Android, no device needed. */
class EngineTest {

    private val awake: (Long) -> Int = { 12 }        // always daytime
    private val night: (Long) -> Int = { 3 }         // always sleep window
    private fun seed() = Random(42)
    private val min = 60_000L

    @Test fun eggHatchesAfterEggDuration() {
        val egg = EvolutionEngine.newEgg("Tama", 0)
        val sim = CareEngine.advance(egg, Tuning.EGG_MS + min, hourAt = awake, rng = seed())
        assertEquals(Stage.BABY, sim.pet.stage)
        assertTrue(sim.events.any { it is DomainEvent.Hatched })
    }

    @Test fun feedingRaisesHungerAndRefusesWhenFull() {
        val baby = baby().copy(stats = Stats(hunger = 20))
        val meal = Catalog.food("meal_fish")!!
        val fed = CareEngine.feed(baby, meal, 0).pet
        assertTrue(fed.stats.hunger > 20)

        val full = baby().copy(stats = Stats(hunger = 99))
        val refused = CareEngine.feed(full, meal, 0)
        assertTrue((refused.events.first() as DomainEvent.Fed).refused)
        assertEquals(99, refused.pet.stats.hunger)
    }

    @Test fun hungerDecaysOverTimeWhenAwake() {
        val p = baby().copy(stats = Stats(hunger = 100), lastUpdatedMs = 0)
        val after = CareEngine.advance(p, 3 * 60 * min, hourAt = awake, rng = seed()).pet
        assertTrue("hunger should drop ~1 heart over 3h", after.stats.hunger in 50..75)
    }

    @Test fun offlineDecayIsCappedButAgingIsNot() {
        // Away 48h: vitals integrate at most 12h of decay, but the baby still ages to child.
        val p = baby().copy(stats = Stats(hunger = 100, happy = 100), stageStartMs = 0, lastUpdatedMs = 0)
        val sim = CareEngine.advance(p, 48L * 60 * min, hourAt = awake, rng = seed())
        assertNotEquals(Stage.BABY, sim.pet.stage)        // aged on real time
        assertTrue("not starved to nothing by the cap", sim.pet.alive)
    }

    @Test fun neglectTriggersCallThenCareMiss() {
        // Observe a full call -> timeout -> care-miss cycle (needs > ATTENTION_TIMEOUT_MIN).
        // Keep the pet inside one stage across the 90-min window: the baby stage lasts only
        // BABY_MS (10 min), so evolving mid-window would reset the per-stage care log (misses).
        val p = baby().copy(
            stats = Stats(hunger = 5, happy = 5),
            lastUpdatedMs = 0, stageStartMs = 90 * min, pendingCallSinceMs = null,
        )
        val sim = CareEngine.advance(p, 90 * min, hourAt = awake, rng = seed())
        assertTrue(sim.events.any { it is DomainEvent.Called })
        assertTrue(sim.events.any { it is DomainEvent.CareMiss })
        assertTrue(sim.pet.care.misses >= 1)
    }

    @Test fun sustainedTotalNeglectKillsPet() {
        val p = baby().copy(stats = Stats(hunger = 0, happy = 0), lastUpdatedMs = 0)
        val sim = CareEngine.advance(p, (Tuning.DEATH_CRITICAL_MIN + 5) * min, hourAt = awake, rng = seed())
        assertFalse(sim.pet.alive)
        assertTrue(sim.events.any { it is DomainEvent.Died })
    }

    @Test fun goodCareEvolvesToBranchA_neglectToBranchC() {
        val good = baby().copy(stageStartMs = 0, care = CareLog(misses = 0, gamesPlayed = 4, careMinutes = 600))
        val g = EvolutionEngine.maybeAdvanceStage(good, Tuning.BABY_MS + 1)
        assertEquals(Species.CHILD_A, g.pet.species)

        val bad = baby().copy(stageStartMs = 0, care = CareLog(misses = 8, sickCount = 3, careMinutes = 600))
        val b = EvolutionEngine.maybeAdvanceStage(bad, Tuning.BABY_MS + 1)
        assertEquals(Species.CHILD_C, b.pet.species)
    }

    @Test fun fullTreeReachesAnAdult() {
        var p = baby().copy(stageStartMs = 0, bornAtMs = 0, care = CareLog(careMinutes = 600))
        // advance three stage lengths worth of real time in one shot
        val now = Tuning.BABY_MS + Tuning.CHILD_MS + Tuning.TEEN_MS + 1
        p = EvolutionEngine.maybeAdvanceStage(p, now).pet
        assertEquals(Stage.ADULT, p.stage)
        assertTrue(p.species.stage == Stage.ADULT)
    }

    @Test fun stepsEarnGpAndDailyGoalBonus() {
        val p = baby().copy(gp = 0, stepBaseline = 1000, stepsToday = 0, stepDayEpoch = 100)
        // walk 6100 steps today
        val sim = EconomyEngine.applySteps(p, rawTotal = 1000 + 6100, todayEpochDay = 100, nowMs = 0)
        assertEquals(6100 / Tuning.STEPS_PER_GP + Tuning.DAILY_GOAL_BONUS_GP, sim.pet.gp)
        assertTrue(sim.events.any { it is DomainEvent.StepGoal })
    }

    @Test fun stepBaselineResetsOnNewDayAndReboot() {
        val p = baby().copy(stepBaseline = 5000, stepsToday = 3000, stepDayEpoch = 100)
        val newDay = EconomyEngine.applySteps(p, rawTotal = 5200, todayEpochDay = 101, nowMs = 0).pet
        assertEquals(0, newDay.stepsToday)
        assertEquals(5200L, newDay.stepBaseline)

        val reboot = EconomyEngine.applySteps(p, rawTotal = 10, todayEpochDay = 100, nowMs = 0).pet
        assertEquals(10L, reboot.stepBaseline)
    }

    @Test fun shopSpendingRespectsBalance() {
        val rich = baby().copy(gp = 100)
        assertNotNull(EconomyEngine.trySpend(rich, 60, 0))
        val poor = baby().copy(gp = 10)
        assertNull(EconomyEngine.trySpend(poor, 60, 0))
    }

    @Test fun sleepRegeneratesEnergyWhenLightOff() {
        val p = baby().copy(stats = Stats(energy = 20, hunger = 80, happy = 80), lightOn = false, lastUpdatedMs = 0)
        val after = CareEngine.advance(p, 4 * 60 * min, hourAt = night, rng = seed()).pet
        assertTrue("energy should recover during sleep", after.stats.energy > 20)
        assertTrue(after.asleep)
    }

    @Test fun cleaningAndHealingClearState() {
        val dirty = baby().copy(stats = Stats(dirty = true))
        assertFalse(CareEngine.clean(dirty, 0).pet.stats.dirty)
        val sick = baby().copy(stats = Stats(sick = true))
        assertFalse(CareEngine.heal(sick, 0).pet.stats.sick)
    }

    @Test fun pettingRaisesHappyOnlyWhenEffective() {
        val p = baby().copy(stats = Stats(hunger = 80, happy = 50, energy = 90))
        val landed = CareEngine.pet(p, 0, effective = true)
        assertEquals(50 + Tuning.PET_HAPPY, landed.pet.stats.happy)
        assertTrue((landed.events.first() as DomainEvent.Petted).effective)

        val throttled = CareEngine.pet(p, 0, effective = false)
        assertEquals("cooldown pet must not change happy", 50, throttled.pet.stats.happy)
        assertFalse((throttled.events.first() as DomainEvent.Petted).effective)
    }

    @Test fun healingIsGentleWithMedicineButCostsBondAsHomeRemedy() {
        val sick = baby().copy(stats = Stats(sick = true, bond = 40))
        val gentle = CareEngine.heal(sick, 0, gentle = true)
        assertFalse(gentle.pet.stats.sick)
        assertEquals(40 + Tuning.MEDICINE_BOND_BONUS, gentle.pet.stats.bond)
        assertTrue((gentle.events.first() as DomainEvent.Healed).gentle)

        val remedy = CareEngine.heal(sick, 0, gentle = false)
        assertFalse("home remedy still cures", remedy.pet.stats.sick)
        assertEquals(40 - Tuning.HOME_REMEDY_BOND_PENALTY, remedy.pet.stats.bond)
        assertFalse((remedy.events.first() as DomainEvent.Healed).gentle)
    }

    private fun baby() = Pet(
        name = "Tama", species = Species.BABY, stage = Stage.BABY,
        generation = 1, bornAtMs = 0, stageStartMs = 0, lastUpdatedMs = 0,
        stats = Stats(hunger = 80, happy = 80, energy = 90),
    )
}
