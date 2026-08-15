package com.tamawatch.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arm the periodic tick after a reboot. State is timestamp-based, so the
 *  missed offline window is caught up on the first tick. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            TickWorker.schedule(context)
        }
    }
}
