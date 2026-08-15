package com.tamawatch.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tamawatch.core.model.Tuning
import com.tamawatch.tama
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Wall-clock heartbeat. Advances the sim and, if a need is critical, raises the
 * ongoing attention notification. Timestamp-based catch-up means a skipped tick
 * is corrected on the next run.
 */
class TickWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.tama
        val settings = c.settings.flow.first()
        c.repository.setSleepWindow(settings.sleep)
        c.repository.load()
        c.repository.tick()
        c.repository.pet.value?.let { AttentionNotifier(applicationContext).refresh(it) }
        return Result.success()
    }

    companion object {
        private const val NAME = "tama_tick"
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<TickWorker>(Tuning.TICK_MINUTES, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
