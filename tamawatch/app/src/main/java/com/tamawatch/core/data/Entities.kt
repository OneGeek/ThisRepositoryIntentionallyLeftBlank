package com.tamawatch.core.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.tamawatch.core.model.*

/**
 * Room storage. The domain [Pet] stays Android-free (JVM-testable), so we
 * persist a parallel [PetEntity] and map between the two.
 */
@Entity(tableName = "pet")
@TypeConverters(EnumConverters::class)
data class PetEntity(
    @PrimaryKey val id: Int = 1,          // single active pet
    val name: String,
    val species: Species,
    val stage: Stage,
    val generation: Int,
    val bornAtMs: Long,
    val stageStartMs: Long,
    val lastUpdatedMs: Long,
    val lightOn: Boolean,
    val asleep: Boolean,
    @Embedded(prefix = "st_") val stats: Stats,
    @Embedded(prefix = "cl_") val care: CareLog,
    val pendingCallSinceMs: Long?,
    val gp: Int,
    val stepBaseline: Long?,
    val stepsToday: Long,
    val stepDayEpoch: Long,
    val alive: Boolean,
) {
    fun toDomain() = Pet(name, species, stage, generation, bornAtMs, stageStartMs,
        lastUpdatedMs, lightOn, asleep, stats, care, pendingCallSinceMs, gp,
        stepBaseline, stepsToday, stepDayEpoch, alive)

    companion object {
        fun from(p: Pet) = PetEntity(1, p.name, p.species, p.stage, p.generation,
            p.bornAtMs, p.stageStartMs, p.lastUpdatedMs, p.lightOn, p.asleep, p.stats,
            p.care, p.pendingCallSinceMs, p.gp, p.stepBaseline, p.stepsToday,
            p.stepDayEpoch, p.alive)
    }
}

@Entity(tableName = "inventory")
data class InventoryEntity(
    @PrimaryKey val itemId: String,
    val count: Int,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val generation: Int,
    val name: String,
    val species: Species,
    val tier: CareTier,
    val ageDays: Int,
    val endedAtMs: Long,
)

class EnumConverters {
    @TypeConverter fun species(v: Species) = v.name
    @TypeConverter fun toSpecies(v: String) = Species.valueOf(v)
    @TypeConverter fun stage(v: Stage) = v.name
    @TypeConverter fun toStage(v: String) = Stage.valueOf(v)
    @TypeConverter fun tier(v: CareTier) = v.name
    @TypeConverter fun toTier(v: String) = CareTier.valueOf(v)
}
