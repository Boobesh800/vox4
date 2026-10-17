package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.MainActivity
import java.util.Calendar
import java.util.Locale

/**
 * Self-managed alarms. We do NOT depend on the phone's Clock app (that needs an activity launch,
 * which Android blocks from the background). AlarmManager.setAlarmClock fires even with the app
 * closed, offline, in Doze and with the screen off.
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    private const val PREFS = "vox_alarms"
    private const val KEY_ALARMS = "alarms" // Set<String> of "hour:minute"

    const val ACTION_FIRE = "com.example.alarm.ACTION_FIRE"
    const val EXTRA_HOUR = "extra_hour"
    const val EXTRA_MINUTE = "extra_minute"

    data class ScheduleResult(val triggerAtMillis: Long, val exact: Boolean)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun nextTrigger(hour: Int, minute: Int, now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun formatTime(hour: Int, minute: Int): String {
        val ampm = if (hour >= 12) "PM" else "AM"
        val h12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format(Locale.getDefault(), "%d:%02d %s", h12, minute, ampm)
    }

    fun dayWord(triggerAtMillis: Long): String {
        val now = Calendar.getInstance()
        val t = Calendar.getInstance().apply { timeInMillis = triggerAtMillis }
        val sameDay = now.get(Calendar.YEAR) == t.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) "today" else "tomorrow"
    }

    private fun firePendingIntent(context: Context, hour: Int, minute: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_HOUR, hour)
            putExtra(EXTRA_MINUTE, minute)
        }
        return PendingIntent.getBroadcast(
            context,
            hour * 60 + minute,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, hour: Int, minute: Int): ScheduleResult {
        require(hour in 0..23) { "Hour must be between 0 and 23" }
        require(minute in 0..59) { "Minute must be between 0 and 59" }
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val trigger = nextTrigger(hour, minute)
        val firePi = firePendingIntent(app, hour, minute)
        val showPi = PendingIntent.getActivity(
            app,
            0,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        var exact = true
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, showPi), firePi)
        } catch (e: SecurityException) {
            // Exact-alarm permission missing: still ring, but Android may delay it a little.
            Log.w(TAG, "Exact alarm not permitted, falling back to inexact", e)
            exact = false
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, firePi)
        }
        store(app, hour, minute, add = true)
        return ScheduleResult(trigger, exact)
    }

    fun cancel(context: Context, hour: Int, minute: Int): Boolean {
        if (hour !in 0..23 || minute !in 0..59) return false
        val app = context.applicationContext
        val existed = all(app).any { it.first == hour && it.second == minute }
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(firePendingIntent(app, hour, minute))
        store(app, hour, minute, add = false)
        return existed
    }

    /** Cancels whichever stored alarm will ring first. Returns its time, or null if none. */
    fun cancelNext(context: Context): Pair<Int, Int>? {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val next = all(app).minByOrNull { nextTrigger(it.first, it.second, now) } ?: return null
        cancel(app, next.first, next.second)
        return next
    }

    /** Called when an alarm fires, so one-shot alarms do not stay in the list. */
    fun forget(context: Context, hour: Int, minute: Int) {
        store(context.applicationContext, hour, minute, add = false)
    }

    fun snooze(context: Context, minutes: Int): ScheduleResult {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, minutes)
        return schedule(context, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    /** Stops an alarm that is ringing right now. Returns true if one was ringing. */
    fun stopRinging(context: Context): Boolean {
        if (!AlarmRingService.isRinging) return false
        val intent = Intent(context.applicationContext, AlarmRingService::class.java).apply {
            action = AlarmRingService.ACTION_DISMISS
        }
        context.applicationContext.startService(intent)
        return true
    }

    /** Re-registers every stored alarm (after reboot, app update, clock or timezone change). */
    fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        for ((h, m) in all(app)) {
            try {
                schedule(app, h, m)
            } catch (e: Exception) {
                Log.e(TAG, "Could not reschedule $h:$m", e)
            }
        }
    }

    fun all(context: Context): List<Pair<Int, Int>> {
        val set = prefs(context).getStringSet(KEY_ALARMS, emptySet()) ?: emptySet()
        return set.mapNotNull { entry ->
            val parts = entry.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull()
            val m = parts.getOrNull(1)?.toIntOrNull()
            if (h != null && m != null) Pair(h, m) else null
        }
    }

    private fun store(context: Context, hour: Int, minute: Int, add: Boolean) {
        val p = prefs(context)
        val set = HashSet<String>(p.getStringSet(KEY_ALARMS, emptySet()) ?: emptySet())
        val key = "$hour:$minute"
        if (add) set.add(key) else set.remove(key)
        p.edit().putStringSet(KEY_ALARMS, set).apply()
    }
}
