package com.example.controller

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.VoxApplication

/**
 * Starts another app's activity even when VoxControl itself is in the background / closed /
 * screen-off. Android 10+ silently blocks plain startActivity() from background services, so:
 *
 *  1. VoxControl on screen            -> normal startActivity
 *  2. "Display over other apps" given -> show a 1px invisible overlay, then startActivity
 *                                        (apps with a visible overlay are allowed to launch)
 *  3. otherwise                       -> full-screen/heads-up notification (one tap)
 */
object BackgroundLauncher {

    private const val TAG = "BackgroundLauncher"
    private const val CHANNEL_ID = "vox_launch_channel"
    private const val NOTIFICATION_ID = 3001
    private val handler = Handler(Looper.getMainLooper())

    enum class Outcome { DIRECT, OVERLAY, NOTIFICATION, FAILED }

    fun canDrawOverlays(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    fun launch(context: Context, intent: Intent, label: String): Outcome {
        val app = context.applicationContext
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (VoxApplication.isUiVisible) {
            return try {
                app.startActivity(intent)
                Outcome.DIRECT
            } catch (e: Exception) {
                Log.e(TAG, "Direct launch failed", e)
                Outcome.FAILED
            }
        }

        if (canDrawOverlays(app) && launchWithOverlay(app, intent)) {
            return Outcome.OVERLAY
        }

        return if (postLaunchNotification(app, intent, label)) Outcome.NOTIFICATION else Outcome.FAILED
    }

    @Suppress("DEPRECATION")
    private fun launchWithOverlay(app: Context, intent: Intent): Boolean {
        return try {
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = View(app)
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val params = WindowManager.LayoutParams(
                1,
                1,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            wm.addView(view, params)

            // Give the window a moment to become visible, then launch.
            handler.postDelayed({
                try {
                    app.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Overlay-assisted launch failed; Android may block background activity launches", e)
                    // Android can still block background starts on some OS/device builds.
                    // Give the user a reliable tap-to-open fallback instead of silently failing.
                    postLaunchNotification(app, intent, intent.resolveActivity(app.packageManager)
                        ?.loadLabel(app.packageManager)?.toString() ?: "app")
                }
            }, 120)
            handler.postDelayed({
                try {
                    wm.removeView(view)
                } catch (_: Exception) {
                }
            }, 2500)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not show overlay", e)
            false
        }
    }

    private fun postLaunchNotification(app: Context, intent: Intent, label: String): Boolean {
        return try {
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "VoxControl app launch", NotificationManager.IMPORTANCE_HIGH)
                )
            }
            val pi = PendingIntent.getActivity(
                app,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Open $label")
                .setContentText("Tap to open. Allow \"Display over other apps\" for automatic launch.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .build()
            nm.notify(NOTIFICATION_ID, notification)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not post launch notification", e)
            false
        }
    }
}
