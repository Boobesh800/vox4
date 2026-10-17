package com.example.model

enum class CommandType(val displayName: String, val category: String) {
    TORCH_ON("Torch On", "Device"),
    TORCH_OFF("Torch Off", "Device"),
    BLUETOOTH_ON("Bluetooth On", "Connectivity"),
    BLUETOOTH_OFF("Bluetooth Off", "Connectivity"),
    WIFI_ON("WiFi On", "Connectivity"),
    WIFI_OFF("WiFi Off", "Connectivity"),
    INTERNET_ON("Internet On", "Connectivity"),
    INTERNET_OFF("Internet Off", "Connectivity"),
    OPEN_APP("Open App", "Application"),
    CALL_CONTACT("Make Call", "Phone"),
    SET_ALARM("Set Alarm", "Clock"),
    DISMISS_ALARM("Turn Off Alarm", "Clock"),
    VOLUME_UP("Volume Up", "Media"),
    VOLUME_DOWN("Volume Down", "Media"),
    VOLUME_MUTE("Mute Audio", "Media"),
    BATTERY_CHECK("Battery Check", "System"),
    TIME_CHECK("Time Check", "System"),
    UNKNOWN("Unknown", "Other")
}

data class ParsedCommand(
    val type: CommandType,
    val rawText: String,
    val targetApp: String? = null,
    val targetPackage: String? = null,
    val targetContactOrNumber: String? = null,
    val alarmHour: Int? = null,
    val alarmMinute: Int? = null,
    val alarmTimeDisplay: String? = null
)

data class ExecutedCommand(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val rawUtterance: String,
    val commandType: CommandType,
    val summary: String,
    val success: Boolean,
    val feedbackMessage: String
)

data class AppInfo(
    val packageName: String,
    val label: String,
    val normalizedLabel: String
)

data class DeviceState(
    val isTorchOn: Boolean = false,
    val isBluetoothOn: Boolean = false,
    val isWifiOn: Boolean = false,
    val batteryPercent: Int = 100,
    val isVoiceFeedbackMuted: Boolean = true // Mute app speech by default per user request
)

enum class ListenerStatus {
    STOPPED,
    INITIALIZING,
    LISTENING,
    PROCESSING,
    SCREEN_OFF_LISTENING,
    ERROR
}
