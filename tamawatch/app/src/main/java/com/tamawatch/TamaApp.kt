package com.tamawatch

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.tamawatch.assets.SpriteBank
import com.tamawatch.audio.SoundBank
import com.tamawatch.core.data.Repository
import com.tamawatch.core.data.SettingsStore
import com.tamawatch.core.data.TamaDatabase
import com.tamawatch.sensor.Haptics
import com.tamawatch.sensor.StepSource

/** Lightweight manual DI — a small app doesn't need Hilt. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val database: TamaDatabase = Room.databaseBuilder(app, TamaDatabase::class.java, "tamawatch.db")
        .fallbackToDestructiveMigration()
        .build()

    val settings = SettingsStore(app)
    val repository = Repository(database.dao(), settings)
    val spriteBank = SpriteBank(app)
    val soundBank = SoundBank(app)
    val haptics = Haptics(app)
    val stepSource = StepSource(app)
}

class TamaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.tama: AppContainer
    get() = (applicationContext as TamaApp).container
