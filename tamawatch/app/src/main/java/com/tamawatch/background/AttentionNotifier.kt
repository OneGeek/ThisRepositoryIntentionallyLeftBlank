package com.tamawatch.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tamawatch.R
import com.tamawatch.core.model.Pet
import com.tamawatch.ui.TamaActivity

/** Ongoing "your Tama needs you!" notification, raised/cleared with the need. */
class AttentionNotifier(private val context: Context) {
    private val nm = NotificationManagerCompat.from(context)

    private fun ensureChannel() {
        val ch = NotificationChannel(
            CHANNEL, context.getString(R.string.notif_channel_care),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { setShowBadge(true) }
        nm.createNotificationChannel(ch)
    }

    fun refresh(pet: Pet) {
        if (!pet.needsAttention()) {
            nm.cancel(ID)
            return
        }
        ensureChannel()
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, TamaActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val reason = when {
            pet.stats.sick -> "feels sick"
            pet.stats.dirty -> "needs cleaning"
            pet.stats.hunger <= 15 -> "is hungry"
            else -> "wants to play"
        }
        val notif = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_needs_you))
            .setContentText("${pet.name} $reason")
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            nm.notify(ID, notif)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — silently skip.
        }
    }

    companion object {
        private const val CHANNEL = "care"
        private const val ID = 1001
    }
}
