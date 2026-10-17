package com.example.controller

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.alarm.AlarmScheduler
import com.example.model.AppInfo
import com.example.model.CommandType
import com.example.model.ExecutedCommand
import com.example.model.ParsedCommand
import com.example.repository.VoiceControlRepository
import com.example.util.CommandParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceActionExecutor(private val context: Context) : TextToSpeech.OnInitListener {

    private val tag = "DeviceActionExecutor"

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private var torchCameraId: String? = null
    private var isTorchActive = false

    init {
        initTts()
        detectTorchCamera()
        registerTorchCallback()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize TTS", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsReady = true
        }
    }

    private fun detectTorchCamera() {
        try {
            cameraManager?.let { cm ->
                for (id in cm.cameraIdList) {
                    val characteristics = cm.getCameraCharacteristics(id)
                    val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        torchCameraId = id
                        break
                    }
                }
                if (torchCameraId == null && cm.cameraIdList.isNotEmpty()) {
                    torchCameraId = cm.cameraIdList[0]
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error detecting torch camera", e)
        }
    }

    private fun registerTorchCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                cameraManager?.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        if (cameraId == torchCameraId) {
                            isTorchActive = enabled
                            VoiceControlRepository.updateTorchState(enabled)
                        }
                    }
                }, null)
            } catch (e: Exception) {
                Log.e(tag, "Error registering torch callback", e)
            }
        }
    }

    fun execute(command: ParsedCommand): ExecutedCommand {
        vibrate(80)
        val result = when (command.type) {
            CommandType.TORCH_ON -> toggleTorch(true, command.rawText)
            CommandType.TORCH_OFF -> toggleTorch(false, command.rawText)
            CommandType.BLUETOOTH_ON -> toggleBluetooth(true, command.rawText)
            CommandType.BLUETOOTH_OFF -> toggleBluetooth(false, command.rawText)
            CommandType.WIFI_ON -> toggleWifi(true, command.rawText)
            CommandType.WIFI_OFF -> toggleWifi(false, command.rawText)
            CommandType.INTERNET_ON -> toggleInternet(true, command.rawText)
            CommandType.INTERNET_OFF -> toggleInternet(false, command.rawText)
            CommandType.OPEN_APP -> launchApp(command)
            CommandType.CALL_CONTACT -> makeCall(command.targetContactOrNumber, command.rawText)
            CommandType.SET_ALARM -> setAlarm(command.alarmHour, command.alarmMinute, command.alarmTimeDisplay, command.rawText)
            CommandType.DISMISS_ALARM -> turnOffAlarm(command.alarmHour, command.alarmMinute, command.alarmTimeDisplay, command.rawText)
            CommandType.VOLUME_UP -> adjustVolume(true, command.rawText)
            CommandType.VOLUME_DOWN -> adjustVolume(false, command.rawText)
            CommandType.VOLUME_MUTE -> muteVolume(command.rawText)
            CommandType.BATTERY_CHECK -> checkBattery(command.rawText)
            CommandType.TIME_CHECK -> checkTime(command.rawText)
            CommandType.UNKNOWN -> {
                speak("Command not recognized")
                ExecutedCommand(
                    rawUtterance = command.rawText,
                    commandType = CommandType.UNKNOWN,
                    summary = "Unknown Command",
                    success = false,
                    feedbackMessage = "Could not parse action from: \"${command.rawText}\""
                )
            }
        }

        VoiceControlRepository.addExecutedCommand(result)
        return result
    }

    fun toggleTorch(enable: Boolean, rawUtterance: String): ExecutedCommand {
        val targetId = torchCameraId ?: "0"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager?.setTorchMode(targetId, enable)
                isTorchActive = enable
                VoiceControlRepository.updateTorchState(enable)
                val msg = if (enable) "Torch turned on" else "Torch turned off"
                speak(msg)
                ExecutedCommand(
                    rawUtterance = rawUtterance,
                    commandType = if (enable) CommandType.TORCH_ON else CommandType.TORCH_OFF,
                    summary = if (enable) "Torch Turned On" else "Torch Turned Off",
                    success = true,
                    feedbackMessage = msg
                )
            } else {
                val msg = "Torch not supported on this Android version"
                speak(msg)
                ExecutedCommand(
                    rawUtterance = rawUtterance,
                    commandType = if (enable) CommandType.TORCH_ON else CommandType.TORCH_OFF,
                    summary = "Torch Error",
                    success = false,
                    feedbackMessage = msg
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Error setting torch mode", e)
            val msg = "Failed to switch torch: ${e.localizedMessage}"
            speak("Could not toggle torch")
            ExecutedCommand(
                rawUtterance = rawUtterance,
                commandType = if (enable) CommandType.TORCH_ON else CommandType.TORCH_OFF,
                summary = "Torch Failed",
                success = false,
                feedbackMessage = msg
            )
        }
    }

    fun toggleBluetooth(enable: Boolean, rawUtterance: String): ExecutedCommand {
        return try {
            val adapter = bluetoothAdapter
            if (adapter == null) {
                val msg = "Bluetooth hardware not detected"
                speak(msg)
                return ExecutedCommand(
                    rawUtterance = rawUtterance,
                    commandType = if (enable) CommandType.BLUETOOTH_ON else CommandType.BLUETOOTH_OFF,
                    summary = "No Bluetooth Hardware",
                    success = false,
                    feedbackMessage = msg
                )
            }

            var directSuccess = false
            try {
                directSuccess = if (enable) adapter.enable() else adapter.disable()
            } catch (sec: SecurityException) {
                Log.w(tag, "Bluetooth toggle requires permission, falling back to panel", sec)
            }

            if (!directSuccess) {
                openBluetoothSettings()
            }

            VoiceControlRepository.updateBluetoothState(enable)
            val msg = if (enable) "Bluetooth turned on" else "Bluetooth turned off"
            speak(msg)
            ExecutedCommand(
                rawUtterance = rawUtterance,
                commandType = if (enable) CommandType.BLUETOOTH_ON else CommandType.BLUETOOTH_OFF,
                summary = if (enable) "Bluetooth Enabled" else "Bluetooth Disabled",
                success = true,
                feedbackMessage = msg
            )
        } catch (e: Exception) {
            Log.e(tag, "Error setting bluetooth", e)
            ExecutedCommand(
                rawUtterance = rawUtterance,
                commandType = if (enable) CommandType.BLUETOOTH_ON else CommandType.BLUETOOTH_OFF,
                summary = "Bluetooth Error",
                success = false,
                feedbackMessage = e.localizedMessage ?: "Bluetooth operation failed"
            )
        }
    }

    private fun openBluetoothSettings() {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.applicationContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "Failed to open bluetooth settings", e)
        }
    }

    fun toggleWifi(enable: Boolean, rawUtterance: String): ExecutedCommand {
        return try {
            var directSuccess = false
            try {
                if (wifiManager != null) {
                    @Suppress("DEPRECATION")
                    directSuccess = wifiManager.setWifiEnabled(enable)
                }
            } catch (e: Exception) {
                Log.w(tag, "Direct WiFi toggle restricted by Android, opening panel", e)
            }

            if (!directSuccess) {
                openWifiSettings()
            }

            VoiceControlRepository.updateWifiState(enable)
            val msg = if (enable) "Wi-Fi turned on" else "Wi-Fi turned off"
            speak(msg)
            ExecutedCommand(
                rawUtterance = rawUtterance,
                commandType = if (enable) CommandType.WIFI_ON else CommandType.WIFI_OFF,
                summary = if (enable) "Wi-Fi Turned On" else "Wi-Fi Turned Off",
                success = true,
                feedbackMessage = msg
            )
        } catch (e: Exception) {
            Log.e(tag, "Error setting WiFi", e)
            ExecutedCommand(
                rawUtterance = rawUtterance,
                commandType = if (enable) CommandType.WIFI_ON else CommandType.WIFI_OFF,
                summary = "Wi-Fi Error",
                success = false,
                feedbackMessage = e.localizedMessage ?: "WiFi operation failed"
            )
        }
    }

    private fun openWifiSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_WIFI)
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.applicationContext.startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.applicationContext.startActivity(fallback)
        }
    }

    fun toggleInternet(enable: Boolean, rawUtterance: String): ExecutedCommand {
        return try {
            try {
                @Suppress("DEPRECATION")
                wifiManager?.setWifiEnabled(enable)
            } catch (_: Exception) {}

            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            } else {
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.applicationContext.startActivity(intent)

            val msg = if (enable) "Internet connectivity opened" else "Internet controls opened"
            speak(if (enable) "Turning on internet" else "Turning off internet")

            ExecutedCommand(
                rawUtterance = rawUtterance,
                commandType = if (enable) CommandType.INTERNET_ON else CommandType.INTERNET_OFF,
                summary = if (enable) "Internet Turned On" else "Internet Turned Off",
                success = true,
                feedbackMessage = msg
            )
        } catch (e: Exception) {
            Log.e(tag, "Error opening internet panel", e)
            ExecutedCommand(
                rawUtterance = rawUtterance,
                commandType = if (enable) CommandType.INTERNET_ON else CommandType.INTERNET_OFF,
                summary = "Internet Control Error",
                success = false,
                feedbackMessage = e.localizedMessage ?: "Could not open internet settings"
            )
        }
    }

    fun launchApp(command: ParsedCommand): ExecutedCommand {
        val appName = command.targetApp ?: "App"
        val packageName = command.targetPackage
        val appContext = context.applicationContext
        val pm = appContext.packageManager

        return try {
            wakeScreenUp()

            var launchIntent: Intent? = null
            var label = appName

            // 1. Exact package (from the parser: installed-app match or well-known alias)
            if (!packageName.isNullOrBlank()) {
                launchIntent = pm.getLaunchIntentForPackage(packageName)
            }

            // 2. Special system shortcuts
            if (launchIntent == null) {
                val lower = appName.lowercase(Locale.ROOT)
                launchIntent = when {
                    lower.contains("camera") -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    lower.contains("settings") -> Intent(Settings.ACTION_SETTINGS)
                    lower.contains("clock") || lower.contains("alarm") -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
                    lower.contains("gallery") || lower.contains("photos") -> Intent(Intent.ACTION_VIEW).apply {
                        type = "image/*"
                    }
                    else -> null
                }
            }

            // 3. Search installed apps by label
            if (launchIntent == null) {
                val installed = queryInstalledLaunchableApps()
                val match = CommandParser.matchApp(installed, appName)
                if (match != null) {
                    launchIntent = pm.getLaunchIntentForPackage(match.packageName)
                    label = match.label
                }
            }

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                val outcome = BackgroundLauncher.launch(appContext, launchIntent, label)
                when (outcome) {
                    BackgroundLauncher.Outcome.DIRECT, BackgroundLauncher.Outcome.OVERLAY -> {
                        speak("Opening $label")
                        ExecutedCommand(
                            rawUtterance = command.rawText,
                            commandType = CommandType.OPEN_APP,
                            summary = "Opened $label",
                            success = true,
                            feedbackMessage = "Launched $label"
                        )
                    }
                    BackgroundLauncher.Outcome.NOTIFICATION -> {
                        speak("Tap the notification to open $label")
                        ExecutedCommand(
                            rawUtterance = command.rawText,
                            commandType = CommandType.OPEN_APP,
                            summary = "Tap to open $label",
                            success = false,
                            feedbackMessage = "Android blocked the automatic launch. Allow \"Display over other apps\" for VoxControl so $label opens by itself."
                        )
                    }
                    BackgroundLauncher.Outcome.FAILED -> {
                        ExecutedCommand(
                            rawUtterance = command.rawText,
                            commandType = CommandType.OPEN_APP,
                            summary = "Launch Error",
                            success = false,
                            feedbackMessage = "Could not launch $label"
                        )
                    }
                }
            } else {
                speak("App $appName not found")
                ExecutedCommand(
                    rawUtterance = command.rawText,
                    commandType = CommandType.OPEN_APP,
                    summary = "App Not Found",
                    success = false,
                    feedbackMessage = "Could not find \"$appName\" on this device"
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Error launching app: $appName", e)
            ExecutedCommand(
                rawUtterance = command.rawText,
                commandType = CommandType.OPEN_APP,
                summary = "Launch Error",
                success = false,
                feedbackMessage = e.localizedMessage ?: "Failed to open app"
            )
        }
    }

    fun setAlarm(hour: Int?, minute: Int?, displayTime: String?, rawUtterance: String): ExecutedCommand {
        val h = hour ?: 7
        val m = minute ?: 0
        val timeLabel = AlarmScheduler.formatTime(h, m)

        return try {
            val result = AlarmScheduler.schedule(context, h, m)
            val whenWord = AlarmScheduler.dayWord(result.triggerAtMillis)
            speak("Alarm set for $timeLabel $whenWord")
            val note = if (result.exact) "" else " (may ring a little late: allow Alarms & reminders for VoxControl)"
            ExecutedCommand(
                rawUtterance = rawUtterance,
                commandType = CommandType.SET_ALARM,
                summary = "Alarm Set: $timeLabel",
                success = true,
                feedbackMessage = "Alarm set for $timeLabel ($whenWord)$note"
            )
        } catch (e: Exception) {
            Log.e(tag, "Error setting alarm", e)
            ExecutedCommand(
                rawUtterance = rawUtterance,
                commandType = CommandType.SET_ALARM,
                summary = "Alarm Error",
                success = false,
                feedbackMessage = e.localizedMessage ?: "Failed to set alarm"
            )
        }
    }

    fun turnOffAlarm(hour: Int?, minute: Int?, displayTime: String?, rawUtterance: String): ExecutedCommand {
        try {
            // 1. An alarm is ringing right now -> stop it.
            if (AlarmScheduler.stopRinging(context)) {
                speak("Alarm stopped")
                return ExecutedCommand(
                    rawUtterance = rawUtterance,
                    commandType = CommandType.DISMISS_ALARM,
                    summary = "Alarm Stopped",
                    success = true,
                    feedbackMessage = "Stopped the ringing alarm"
                )
            }

            // 2. Otherwise cancel a scheduled alarm (a specific time, or the next one).
            val cancelled: Pair<Int, Int>? = if (hour != null && minute != null) {
                if (AlarmScheduler.cancel(context, hour, minute)) Pair(hour, minute) else null
            } else {
                AlarmScheduler.cancelNext(context)
            }

            return if (cancelled != null) {
                val t = AlarmScheduler.formatTime(cancelled.first, cancelled.second)
                speak("Alarm at $t turned off")
                ExecutedCommand(
                    rawUtterance = rawUtterance,
                    commandType = CommandType.DISMISS_ALARM,
                    summary = "Alarm Turned Off",
                    success = true,
                    feedbackMessage = "Turned off alarm at $t"
                )
            } else {
                speak("No alarm found")
                ExecutedCommand(
                    rawUtterance = rawUtterance,
                    commandType = CommandType.DISMISS_ALARM,
                    summary = "No Alarm Found",
                    success = false,
                    feedbackMessage = "There is no matching VoxControl alarm to turn off"
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Error turning off alarm", e)
            return ExecutedCommand(
                rawUtterance = rawUtterance,
                commandType = CommandType.DISMISS_ALARM,
                summary = "Alarm Off Error",
                success = false,
                feedbackMessage = e.localizedMessage ?: "Failed to turn off alarm"
            )
        }
    }

    @SuppressLint("Range")
    fun makeCall(target: String?, rawUtterance: String): ExecutedCommand {
        if (target.isNullOrBlank()) {
            return ExecutedCommand(
                rawUtterance = rawUtterance,
                commandType = CommandType.CALL_CONTACT,
                summary = "Call Failed",
                success = false,
                feedbackMessage = "Please specify a contact name or phone number"
            )
        }

        wakeScreenUp()

        return try {
            val isNumber = target.matches(Regex("^[+0-9\\s\\-()]+$"))
            var phoneNumber = if (isNumber) target.replace(Regex("[^0-9+]"), "") else null
            var contactDisplayName = target

            // If not a pure number, lookup in contacts offline!
            if (phoneNumber.isNullOrEmpty()) {
                val resolver = context.contentResolver
                val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )

                var cursor: Cursor? = null
                try {
                    cursor = resolver.query(
                        uri,
                        projection,
                        null,
                        null,
                        null
                    )

                    if (cursor != null && cursor.moveToFirst()) {
                        val cleanTarget = target.lowercase(Locale.ROOT).trim()
                        var bestNumber: String? = null
                        var bestName: String? = null

                        do {
                            val name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: ""
                            val number = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""

                            val lowerName = name.lowercase(Locale.ROOT)
                            if (lowerName == cleanTarget) {
                                bestNumber = number
                                bestName = name
                                break
                            } else if (lowerName.contains(cleanTarget) && bestNumber == null) {
                                bestNumber = number
                                bestName = name
                            }
                        } while (cursor.moveToNext())

                        if (bestNumber != null) {
                            phoneNumber = bestNumber
                            contactDisplayName = bestName ?: target
                        }
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Contacts query failed, falling back to dialer", e)
                } finally {
                    cursor?.close()
                }
            }

            if (!phoneNumber.isNullOrEmpty()) {
                val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

                val hasCallPermission = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.CALL_PHONE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasCallPermission) {
                    context.applicationContext.startActivity(callIntent)
                } else {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.applicationContext.startActivity(dialIntent)
                }

                speak("Calling $contactDisplayName")
                ExecutedCommand(
                    rawUtterance = rawUtterance,
                    commandType = CommandType.CALL_CONTACT,
                    summary = "Calling $contactDisplayName",
                    success = true,
                    feedbackMessage = "Initiated call to $contactDisplayName ($phoneNumber)"
                )
            } else {
                val dialerIntent = Intent(Intent.ACTION_DIAL).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.applicationContext.startActivity(dialerIntent)

                speak("Contact $target not found. Opened phone dialer")
                ExecutedCommand(
                    rawUtterance = rawUtterance,
                    commandType = CommandType.CALL_CONTACT,
                    summary = "Dialer Opened",
                    success = true,
                    feedbackMessage = "Could not find \"$target\" in local contacts. Opened dialer."
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Error making call", e)
            ExecutedCommand(
                rawUtterance = rawUtterance,
                commandType = CommandType.CALL_CONTACT,
                summary = "Call Error",
                success = false,
                feedbackMessage = e.localizedMessage ?: "Failed to initiate call"
            )
        }
    }

    private fun adjustVolume(raise: Boolean, rawUtterance: String): ExecutedCommand {
        return try {
            val direction = if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            val msg = if (raise) "Volume increased" else "Volume decreased"
            speak(msg)
            ExecutedCommand(
                rawUtterance = rawUtterance,
                commandType = if (raise) CommandType.VOLUME_UP else CommandType.VOLUME_DOWN,
                summary = msg,
                success = true,
                feedbackMessage = msg
            )
        } catch (e: Exception) {
            ExecutedCommand(
                rawUtterance = rawUtterance,
                commandType = if (raise) CommandType.VOLUME_UP else CommandType.VOLUME_DOWN,
                summary = "Volume Error",
                success = false,
                feedbackMessage = e.localizedMessage ?: "Failed to change volume"
            )
        }
    }

    private fun muteVolume(rawUtterance: String): ExecutedCommand {
        return try {
            audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            val msg = "Audio muted"
            speak(msg)
            ExecutedCommand(
                rawUtterance = rawUtterance,
                commandType = CommandType.VOLUME_MUTE,
                summary = "Audio Muted",
                success = true,
                feedbackMessage = msg
            )
        } catch (e: Exception) {
            ExecutedCommand(
                rawUtterance = rawUtterance,
                commandType = CommandType.VOLUME_MUTE,
                summary = "Mute Error",
                success = false,
                feedbackMessage = e.localizedMessage ?: "Failed to mute audio"
            )
        }
    }

    private fun checkBattery(rawUtterance: String): ExecutedCommand {
        return try {
            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, batteryFilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
            VoiceControlRepository.updateBatteryPercent(percent)
            val msg = "Battery is at $percent percent"
            speak(msg)
            ExecutedCommand(
                rawUtterance = rawUtterance,
                commandType = CommandType.BATTERY_CHECK,
                summary = "Battery: $percent%",
                success = true,
                feedbackMessage = msg
            )
        } catch (e: Exception) {
            ExecutedCommand(
                rawUtterance = rawUtterance,
                commandType = CommandType.BATTERY_CHECK,
                summary = "Battery Check",
                success = false,
                feedbackMessage = "Could not check battery"
            )
        }
    }

    private fun checkTime(rawUtterance: String): ExecutedCommand {
        val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        val timeStr = formatter.format(Date())
        val msg = "The time is $timeStr"
        speak(msg)
        return ExecutedCommand(
            rawUtterance = rawUtterance,
            commandType = CommandType.TIME_CHECK,
            summary = "Current Time: $timeStr",
            success = true,
            feedbackMessage = msg
        )
    }

    private fun wakeScreenUp() {
        try {
            @Suppress("DEPRECATION")
            val screenWakeLock = powerManager?.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "VoxControl:WakeScreenForAction"
            )
            screenWakeLock?.acquire(3000L)
        } catch (_: Exception) {}
    }

    fun speak(text: String) {
        val isMuted = VoiceControlRepository.deviceState.value.isVoiceFeedbackMuted
        if (isMuted) return

        try {
            if (isTtsReady) {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vox_tts_utterance")
            }
        } catch (e: Exception) {
            Log.e(tag, "TTS speak failed", e)
        }
    }

    fun vibrate(durationMs: Long = 80) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
    }

    fun queryInstalledLaunchableApps(): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val list = resolveInfos.mapNotNull { ri ->
            val pkg = ri.activityInfo.packageName
            val label = ri.loadLabel(pm).toString()
            if (pkg.isNotEmpty() && label.isNotEmpty()) {
                AppInfo(
                    packageName = pkg,
                    label = label,
                    normalizedLabel = label.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
                )
            } else null
        }
        VoiceControlRepository.setInstalledApps(list)
        return list
    }

    fun destroy() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
    }
}
