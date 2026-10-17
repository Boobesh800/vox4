package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action != AlarmScheduler.ACTION_FIRE) return
        val hour = intent.getIntExtra(AlarmScheduler.EXTRA_HOUR, -1)
        val minute = intent.getIntExtra(AlarmScheduler.EXTRA_MINUTE, -1)
        if (hour >= 0 && minute >= 0) AlarmScheduler.forget(context, hour, minute)

        val ring = Intent(context, AlarmRingService::class.java).apply {
            action = AlarmRingService.ACTION_RING
            putExtra(AlarmScheduler.EXTRA_HOUR, hour)
            putExtra(AlarmScheduler.EXTRA_MINUTE, minute)
        }
        try {
            // Allowed from an exact-alarm broadcast even when the app is closed.
            ContextCompat.startForegroundService(context, ring)
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Could not start alarm service", e)
        }
    }
}
