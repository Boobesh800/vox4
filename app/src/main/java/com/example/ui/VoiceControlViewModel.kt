package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.controller.DeviceActionExecutor
import com.example.model.AppInfo
import com.example.model.CommandType
import com.example.model.DeviceState
import com.example.model.ExecutedCommand
import com.example.model.ListenerStatus
import com.example.model.ParsedCommand
import com.example.repository.VoiceControlRepository
import com.example.service.VoiceListenerService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VoiceUiState(
    val isServiceRunning: Boolean = false,
    val listenerStatus: ListenerStatus = ListenerStatus.STOPPED,
    val rmsDb: Float = 0f,
    val currentTranscript: String = "",
    val commandHistory: List<ExecutedCommand> = emptyList(),
    val deviceState: DeviceState = DeviceState(),
    val installedApps: List<AppInfo> = emptyList(),
    val isBatteryOptimizedIgnored: Boolean = false,
    val canDrawOverlays: Boolean = false
)

class VoiceControlViewModel : ViewModel() {

    val uiState: StateFlow<VoiceUiState> = combine(
        combine(
            VoiceControlRepository.isServiceRunning,
            VoiceControlRepository.listenerStatus,
            VoiceControlRepository.rmsDb,
            ::Triple
        ),
        combine(
            VoiceControlRepository.currentTranscript,
            VoiceControlRepository.commandHistory,
            ::Pair
        ),
        combine(
            VoiceControlRepository.deviceState,
            VoiceControlRepository.installedApps,
            ::Pair
        )
    ) { (isRunning, status, rms), (transcript, history), (devState, apps) ->
        VoiceUiState(
            isServiceRunning = isRunning,
            listenerStatus = status,
            rmsDb = rms,
            currentTranscript = transcript,
            commandHistory = history,
            deviceState = devState,
            installedApps = apps,
            isBatteryOptimizedIgnored = false,
            canDrawOverlays = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VoiceUiState()
    )

    fun startService(context: Context) {
        val intent = Intent(context, VoiceListenerService::class.java).apply {
            action = VoiceListenerService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopService(context: Context) {
        val intent = Intent(context, VoiceListenerService::class.java).apply {
            action = VoiceListenerService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun toggleService(context: Context) {
        if (uiState.value.isServiceRunning) {
            stopService(context)
        } else {
            startService(context)
        }
    }

    fun executeManualCommand(
        context: Context,
        type: CommandType,
        rawText: String,
        targetApp: String? = null,
        targetContactOrNumber: String? = null,
        alarmHour: Int? = null,
        alarmMinute: Int? = null,
        alarmTimeDisplay: String? = null
    ) {
        viewModelScope.launch {
            val executor = DeviceActionExecutor(context)
            val parsed = ParsedCommand(
                type = type,
                rawText = rawText,
                targetApp = targetApp,
                targetContactOrNumber = targetContactOrNumber,
                alarmHour = alarmHour,
                alarmMinute = alarmMinute,
                alarmTimeDisplay = alarmTimeDisplay
            )
            executor.execute(parsed)
            executor.destroy()
        }
    }

    fun toggleVoiceFeedback(muted: Boolean) {
        VoiceControlRepository.updateVoiceFeedbackMuted(muted)
    }

    fun clearHistory() {
        VoiceControlRepository.clearHistory()
    }

    fun refreshInstalledApps(context: Context) {
        viewModelScope.launch {
            val executor = DeviceActionExecutor(context)
            executor.queryInstalledLaunchableApps()
            executor.destroy()
        }
    }

    fun requestIgnoreBatteryOptimization(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val packageName = context.packageName
                if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (_: Exception) {}
        }
    }

    /**
     * Opens the next missing special-access screen (one per app launch, so the user is never
     * thrown through several settings pages at once): overlay first (needed to open apps from
     * the background), then battery optimization (keeps listening alive with the screen off).
     */
    fun promptNextMissingAccess(context: Context) {
        val overlayOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
        if (!overlayOk) {
            requestOverlayPermission(context)
            return
        }
        requestIgnoreBatteryOptimization(context)
    }

    fun requestOverlayPermission(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(context)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    fun openVoiceSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_LOCALE_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (_: Exception) {}
        }
    }
}
