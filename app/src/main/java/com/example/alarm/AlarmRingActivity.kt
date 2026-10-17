package com.example.alarm

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Full-screen alarm UI shown over the lock screen. */
class AlarmRingActivity : Activity() {

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val hour = intent.getIntExtra(AlarmScheduler.EXTRA_HOUR, -1)
        val minute = intent.getIntExtra(AlarmScheduler.EXTRA_MINUTE, -1)
        val timeText = if (hour >= 0 && minute >= 0) AlarmScheduler.formatTime(hour, minute) else ""

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0B1220"))
            setPadding(48, 48, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "ALARM"
            textSize = 20f
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = timeText
            textSize = 56f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 48)
        })
        root.addView(Button(this).apply {
            text = "Snooze ${AlarmRingService.SNOOZE_MINUTES} min"
            setOnClickListener { sendToService(AlarmRingService.ACTION_SNOOZE) }
        })
        root.addView(Button(this).apply {
            text = "Dismiss"
            setOnClickListener { sendToService(AlarmRingService.ACTION_DISMISS) }
        })
        setContentView(root)

        AlarmRingService.onStopped = { runOnUiThread { finish() } }
        if (!AlarmRingService.isRinging) finish()
    }

    private fun sendToService(action: String) {
        startService(Intent(this, AlarmRingService::class.java).setAction(action))
        finish()
    }

    override fun onDestroy() {
        AlarmRingService.onStopped = null
        super.onDestroy()
    }
}
