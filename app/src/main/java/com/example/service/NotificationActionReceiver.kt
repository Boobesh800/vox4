package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.controller.DeviceActionExecutor
import com.example.repository.VoiceControlRepository

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TOGGLE_TORCH = "com.example.ACTION_TOGGLE_TORCH"
        const val ACTION_STOP_SERVICE = "com.example.ACTION_STOP_SERVICE"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_TOGGLE_TORCH -> {
                val current = VoiceControlRepository.deviceState.value.isTorchOn
                val executor = DeviceActionExecutor(context)
                executor.toggleTorch(!current, "Notification Quick Toggle")
                executor.destroy()
            }
            ACTION_STOP_SERVICE -> {
                val serviceIntent = Intent(context, VoiceListenerService::class.java).apply {
                    action = VoiceListenerService.ACTION_STOP
                }
                context.startService(serviceIntent)
            }
        }
    }
}
