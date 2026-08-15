package com.tamawatch.core.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Dao
interface PetDao {
    @Query("SELECT * FROM pet WHERE id = 1")
    suspend fun get(): PetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pet: PetEntity)

    @Query("SELECT * FROM inventory")
    suspend fun inventory(): List<InventoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setItem(item: InventoryEntity)

    @Query("SELECT * FROM inventory WHERE itemId = :id")
    suspend fun item(id: String): InventoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addHistory(h: HistoryEntity)

    @Query("SELECT * FROM history ORDER BY generation DESC LIMIT 20")
    suspend fun history(): List<HistoryEntity>
}

@Database(
    entities = [PetEntity::class, InventoryEntity::class, HistoryEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(EnumConverters::class)
abstract class TamaDatabase : RoomDatabase() {
    abstract fun dao(): PetDao
}
