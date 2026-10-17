package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** AlarmManager forgets everything on reboot, so put the saved alarms back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> AlarmScheduler.rescheduleAll(context)
        }
    }
}
