package com.example.repository

import com.example.model.AppInfo
import com.example.model.DeviceState
import com.example.model.ExecutedCommand
import com.example.model.ListenerStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object VoiceControlRepository {

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _listenerStatus = MutableStateFlow(ListenerStatus.STOPPED)
    val listenerStatus: StateFlow<ListenerStatus> = _listenerStatus.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript.asStateFlow()

    private val _commandHistory = MutableStateFlow<List<ExecutedCommand>>(emptyList())
    val commandHistory: StateFlow<List<ExecutedCommand>> = _commandHistory.asStateFlow()

    private val _deviceState = MutableStateFlow(DeviceState())
    val deviceState: StateFlow<DeviceState> = _deviceState.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
        if (!running) {
            _listenerStatus.value = ListenerStatus.STOPPED
            _rmsDb.value = 0f
            _currentTranscript.value = ""
        }
    }

    fun setListenerStatus(status: ListenerStatus) {
        _listenerStatus.value = status
    }

    fun updateRms(rms: Float) {
        _rmsDb.value = rms
    }

    fun updateTranscript(transcript: String) {
        _currentTranscript.value = transcript
    }

    fun addExecutedCommand(command: ExecutedCommand) {
        _commandHistory.update { current ->
            // Keep most recent 50 commands
            listOf(command) + current.take(49)
        }
    }

    fun clearHistory() {
        _commandHistory.value = emptyList()
    }

    fun updateTorchState(on: Boolean) {
        _deviceState.update { it.copy(isTorchOn = on) }
    }

    fun updateBluetoothState(on: Boolean) {
        _deviceState.update { it.copy(isBluetoothOn = on) }
    }

    fun updateWifiState(on: Boolean) {
        _deviceState.update { it.copy(isWifiOn = on) }
    }

    fun updateBatteryPercent(percent: Int) {
        _deviceState.update { it.copy(batteryPercent = percent) }
    }

    fun updateVoiceFeedbackMuted(muted: Boolean) {
        _deviceState.update { it.copy(isVoiceFeedbackMuted = muted) }
    }

    fun setInstalledApps(apps: List<AppInfo>) {
        _installedApps.value = apps
    }
}
