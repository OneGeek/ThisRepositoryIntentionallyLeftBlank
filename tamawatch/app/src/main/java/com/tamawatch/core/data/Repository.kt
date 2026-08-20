package com.tamawatch.core.data

import com.tamawatch.core.engine.CareEngine
import com.tamawatch.core.engine.EconomyEngine
import com.tamawatch.core.engine.EvolutionEngine
import com.tamawatch.core.model.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Single source of truth. Applies engine operations, persists atomically
 * (stamping lastUpdated), and broadcasts [DomainEvent]s for feedback layers.
 */
class Repository(
    private val dao: PetDao,
    val settings: SettingsStore,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    private val _pet = MutableStateFlow<Pet?>(null)
    val pet: StateFlow<Pet?> = _pet.asStateFlow()

    private val _inventory = MutableStateFlow<Map<String, Int>>(emptyMap())
    val inventory: StateFlow<Map<String, Int>> = _inventory.asStateFlow()

    private val _events = MutableSharedFlow<DomainEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<DomainEvent> = _events.asSharedFlow()

    // Wall-clock of the last *effective* pet, so the UI can show the cooldown
    // (pet looks content) without ever displaying a countdown. Not persisted —
    // a soft anti-spam throttle that simply resets when the app restarts.
    private val _lastPetMs = MutableStateFlow(0L)
    val lastPetMs: StateFlow<Long> = _lastPetMs.asStateFlow()

    private var sleep: SleepWindow = SleepWindow()
    fun setSleepWindow(w: SleepWindow) { sleep = w }

    private fun hourAt(ms: Long): Int =
        Instant.ofEpochMilli(ms).atZone(zone).hour
    private fun epochDay(ms: Long): Long =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()

    suspend fun load() = mutex.withLock {
        val entity = dao.get()
        _pet.value = entity?.toDomain()
        _inventory.value = dao.inventory().associate { it.itemId to it.count }
    }

    private suspend fun persist(p: Pet) {
        dao.upsert(PetEntity.from(p))
        _pet.value = p
    }

    private suspend fun emitAll(events: List<DomainEvent>) {
        events.forEach { _events.emit(it) }
    }

    /** Advance the world to now and persist. Safe to call on open, on tick, from the tile. */
    suspend fun tick(now: Long = clock()) = mutex.withLock {
        val p = _pet.value ?: return@withLock
        val sim = CareEngine.advance(p, now, sleep, ::hourAt)
        persist(sim.pet)
        emitAll(sim.events)
    }

    private suspend fun apply(op: (Pet, Long) -> Sim) = mutex.withLock {
        val now = clock()
        val cur = _pet.value ?: return@withLock
        // fold in elapsed time first so actions act on a fresh state
        val advanced = CareEngine.advance(cur, now, sleep, ::hourAt)
        val sim = op(advanced.pet, now)
        persist(sim.pet)
        emitAll(advanced.events + sim.events)
    }

    suspend fun feed(foodId: String) {
        val food = Catalog.food(foodId) ?: return
        if ((_inventory.value[foodId] ?: 0) <= 0) return
        apply { p, now -> CareEngine.feed(p, food, now) }
        consume(foodId)
    }

    suspend fun clean() = apply { p, now -> CareEngine.clean(p, now) }
    /**
     * Cure sickness. If a shop Medicine is in the bag it's a gentle cure (consumed,
     * no bond loss); otherwise the free home remedy is used (works, but costs bond).
     * Never a no-op while sick, so the pet can always be saved.
     */
    suspend fun heal() {
        val cur = _pet.value ?: return
        if (!cur.stats.sick) return
        val gentle = (_inventory.value["item_medicine"] ?: 0) > 0
        apply { p, now -> CareEngine.heal(p, now, gentle) }
        if (gentle) consume("item_medicine")
    }
    suspend fun petIt() = apply { p, now ->
        val effective = now - _lastPetMs.value >= Tuning.PET_COOLDOWN_MS
        if (effective) _lastPetMs.value = now
        CareEngine.pet(p, now, effective)
    }
    suspend fun scold() = apply { p, now -> CareEngine.scold(p, now) }
    suspend fun toggleLight() = apply { p, now -> CareEngine.toggleLight(p, now) }

    suspend fun finishGame(score: Int) = apply { p, now ->
        val vitals = CareEngine.applyGameResult(p, score, now)
        val econ = EconomyEngine.awardGameGp(vitals.pet, score, now)
        Sim(econ.pet, vitals.events + econ.events)
    }

    /** Called from the step sensor with the cumulative sensor total. */
    suspend fun onStepTotal(rawTotal: Long) = mutex.withLock {
        val now = clock()
        val cur = _pet.value ?: return@withLock
        val advanced = CareEngine.advance(cur, now, sleep, ::hourAt)
        val sim = EconomyEngine.applySteps(advanced.pet, rawTotal, epochDay(now), now)
        persist(sim.pet)
        emitAll(advanced.events + sim.events)
    }

    // ---- shop / inventory
    private suspend fun consume(itemId: String) = mutex.withLock {
        val n = (_inventory.value[itemId] ?: 0) - 1
        setItem(itemId, n.coerceAtLeast(0))
    }
    private suspend fun setItem(itemId: String, count: Int) {
        dao.setItem(InventoryEntity(itemId, count))
        _inventory.value = _inventory.value.toMutableMap().apply { put(itemId, count) }
    }
    suspend fun grantItem(itemId: String, count: Int = 1) = mutex.withLock {
        setItem(itemId, (_inventory.value[itemId] ?: 0) + count)
    }

    /** @return true if the purchase succeeded. */
    suspend fun buyFood(foodId: String): Boolean = buy(Catalog.food(foodId)?.price) { grantAfterSpend(foodId) }
    suspend fun buyMedicine(): Boolean = buy(Catalog.medicinePrice) { grantAfterSpend("item_medicine") }
    suspend fun buyCosmetic(id: String): Boolean = buy(Catalog.cosmetic(id)?.price) { grantAfterSpend(id) }

    private suspend fun grantAfterSpend(itemId: String) {
        setItem(itemId, (_inventory.value[itemId] ?: 0) + 1)
    }

    private suspend fun buy(price: Int?, onOk: suspend () -> Unit): Boolean {
        price ?: return false
        var ok = false
        mutex.withLock {
            val cur = _pet.value ?: return@withLock
            val sim = EconomyEngine.trySpend(cur, price, clock()) ?: return@withLock
            persist(sim.pet)
            onOk()
            emitAll(sim.events)
            ok = true
        }
        return ok
    }

    fun ownsCosmetic(id: String) = (_inventory.value[id] ?: 0) > 0

    // ---- lifecycle
    suspend fun startFreshPet(name: String) = mutex.withLock {
        val egg = EvolutionEngine.newEgg(name, clock())
        persist(egg)
        // starter kit
        setItem("meal_bowl", 3)
        setItem("snack_candy", 2)
        setItem("item_medicine", 1)
    }

    /** Skip the incubation wait: backdate the egg so it hatches right now. */
    suspend fun hatchNow() = mutex.withLock {
        val cur = _pet.value ?: return@withLock
        if (cur.stage != Stage.EGG) return@withLock
        val now = clock()
        val aged = cur.copy(stageStartMs = now - Tuning.EGG_MS - 1)
        val sim = CareEngine.advance(aged, now, sleep, ::hourAt)
        persist(sim.pet)
        emitAll(sim.events)
    }

    suspend fun startNextGeneration(name: String) = mutex.withLock {
        val prev = _pet.value ?: return@withLock
        val now = clock()
        dao.addHistory(
            HistoryEntity(
                generation = prev.generation, name = prev.name, species = prev.species,
                tier = EvolutionEngine.tierOf(prev.care), ageDays = prev.ageDays, endedAtMs = now,
            )
        )
        persist(EvolutionEngine.spawnNextGeneration(prev, name, now))
        setItem("meal_bowl", 3)
        setItem("item_medicine", 1)
    }

    suspend fun historyCount(): Int = dao.history().size
}
