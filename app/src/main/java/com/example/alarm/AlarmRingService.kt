package com.example.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat

/** Plays the alarm sound, shows a full-screen alarm over the lock screen, wakes the screen. */
class AlarmRingService : Service() {

    companion object {
        const val ACTION_RING = "com.example.alarm.ACTION_RING"
        const val ACTION_DISMISS = "com.example.alarm.ACTION_DISMISS"
        const val ACTION_SNOOZE = "com.example.alarm.ACTION_SNOOZE"
        const val CHANNEL_ID = "vox_alarm_channel"
        const val NOTIFICATION_ID = 2001
        const val SNOOZE_MINUTES = 10
        private const val MAX_RING_MS = 5 * 60 * 1000L
        private const val TAG = "AlarmRingService"

        @Volatile
        var isRinging: Boolean = false

        /** Set by AlarmRingActivity so it closes when the alarm is stopped by voice. */
        @Volatile
        var onStopped: (() -> Unit)? = null
    }

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var hour = -1
    private var minute = -1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> stopRinging()
            ACTION_SNOOZE -> {
                try {
                    AlarmScheduler.snooze(this, SNOOZE_MINUTES)
                } catch (e: Exception) {
                    Log.e(TAG, "Snooze failed", e)
                }
                stopRinging()
            }
            else -> {
                hour = intent?.getIntExtra(AlarmScheduler.EXTRA_HOUR, -1) ?: -1
                minute = intent?.getIntExtra(AlarmScheduler.EXTRA_MINUTE, -1) ?: -1
                startRinging()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRinging() {
        if (isRinging) return
        isRinging = true
        createChannel()

        val fullScreenIntent = Intent(this, AlarmRingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmScheduler.EXTRA_HOUR, hour)
            putExtra(AlarmScheduler.EXTRA_MINUTE, minute)
        }
        val fullScreenPi = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissPi = PendingIntent.getService(
            this, 1,
            Intent(this, AlarmRingService::class.java).setAction(ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozePi = PendingIntent.getService(
            this, 2,
            Intent(this, AlarmRingService::class.java).setAction(ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeText = if (hour >= 0 && minute >= 0) AlarmScheduler.formatTime(hour, minute) else "Alarm"
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm")
            .setContentText(timeText)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(fullScreenPi)
            .setFullScreenIntent(fullScreenPi, true)
            .addAction(0, "Snooze", snoozePi)
            .addAction(0, "Dismiss", dismissPi)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }

        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoxControl:AlarmRing")?.apply {
                setReferenceCounted(false)
                acquire(MAX_RING_MS + 10_000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Alarm wake lock failed", e)
        }

        playSound()
        startVibration()
        handler.postDelayed({ stopRinging() }, MAX_RING_MS)
    }

    private fun playSound() {
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.prepare()
            mp.start()
            player = mp
        } catch (e: Exception) {
            Log.e(TAG, "Could not play alarm sound", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        try {
            val pattern = longArrayOf(0, 800, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator()?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                vibrator()?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Alarm vibration failed", e)
        }
    }

    private fun stopRinging() {
        handler.removeCallbacksAndMessages(null)
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        try {
            vibrator()?.cancel()
        } catch (_: Exception) {
        }
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        wakeLock = null
        val wasRinging = isRinging
        isRinging = false
        if (wasRinging) onStopped?.invoke()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rings when a VoxControl alarm goes off"
                setSound(null, null) // sound is played by the service on the alarm stream
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        if (isRinging) {
            stopRinging()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
