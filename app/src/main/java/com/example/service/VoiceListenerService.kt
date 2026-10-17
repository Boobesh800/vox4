package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.VoxApplication
import com.example.controller.DeviceActionExecutor
import com.example.model.CommandType
import com.example.model.ListenerStatus
import com.example.repository.VoiceControlRepository
import com.example.util.CommandParser
import java.util.Locale

class VoiceListenerService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val EXTRA_AUTO_START = "com.example.service.EXTRA_AUTO_START"
        private const val RESTART_NOTIFICATION_ID = 1002
        private const val TAG = "VoiceListenerService"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var actionExecutor: DeviceActionExecutor? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var audioManager: AudioManager? = null

    private var isListeningLoopActive = false
    private var lastExecutedUtterance: String = ""
    private var lastExecutionTime: Long = 0L

    private var consecutiveErrors = 0

    private var savedSystemVolume: Int = -1
    private var savedNotifVolume: Int = -1

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        acquireWakeLock()
        actionExecutor = DeviceActionExecutor(this)
        actionExecutor?.queryInstalledLaunchableApps()
        muteSystemSounds()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopListeningLoop()
            restoreSystemSounds()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!startInForeground()) {
            // Android refused to (re)start a microphone service from the background.
            // Do not crash: leave a one-tap "restart" notification instead.
            postRestartNotification()
            stopSelf()
            return START_NOT_STICKY
        }
        startListeningLoop()
        return START_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "VoxControl:ScreenOffVoiceWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire() // held until the service stops
            }
            Log.d(TAG, "WakeLock acquired for screen-off microphone listening")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
            Log.d(TAG, "WakeLock released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock", e)
        }
    }

    /**
     * Mutes the system stream to eliminate the irritating "ting ting"
     * SpeechRecognizer start/stop beeps during background listening.
     */
    private fun muteSystemSounds() {
        try {
            val am = audioManager ?: return
            if (savedSystemVolume == -1) {
                savedSystemVolume = am.getStreamVolume(AudioManager.STREAM_SYSTEM)
            }
            if (savedNotifVolume == -1) {
                savedNotifVolume = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            }
            am.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0)
            @Suppress("DEPRECATION")
            am.setStreamMute(AudioManager.STREAM_SYSTEM, true)
            @Suppress("DEPRECATION")
            am.setStreamMute(AudioManager.STREAM_NOTIFICATION, true)
        } catch (e: Exception) {
            Log.w(TAG, "Could not silence recognizer chime", e)
        }
    }

    private fun restoreSystemSounds() {
        try {
            val am = audioManager ?: return
            @Suppress("DEPRECATION")
            am.setStreamMute(AudioManager.STREAM_SYSTEM, false)
            @Suppress("DEPRECATION")
            am.setStreamMute(AudioManager.STREAM_NOTIFICATION, false)
            if (savedSystemVolume != -1) {
                am.setStreamVolume(AudioManager.STREAM_SYSTEM, savedSystemVolume, 0)
                savedSystemVolume = -1
            }
            if (savedNotifVolume != -1) {
                am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, savedNotifVolume, 0)
                savedNotifVolume = -1
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore system audio", e)
        }
    }

    private fun startInForeground(): Boolean {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleTorchIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_TOGGLE_TORCH
        }
        val pendingToggleTorch = PendingIntent.getBroadcast(
            this,
            1,
            toggleTorchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopServiceIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_STOP_SERVICE
        }
        val pendingStopService = PendingIntent.getBroadcast(
            this,
            2,
            stopServiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, VoxApplication.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("VoxControl Active (Silent)")
            .setContentText("Listening on-device; requires an installed offline language pack")
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_dialog_alert, "Toggle Torch", pendingToggleTorch)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStopService)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground refused", e)
            return false
        }

        VoiceControlRepository.setServiceRunning(true)
        return true
    }

    private fun postRestartNotification() {
        try {
            val restartIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_AUTO_START, true)
            }
            val pi = PendingIntent.getActivity(
                this, 3, restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(this, VoxApplication.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("VoxControl stopped")
                .setContentText("Tap to start listening again")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(RESTART_NOTIFICATION_ID, n)
        } catch (e: Exception) {
            Log.e(TAG, "Could not post restart notification", e)
        }
    }

    private fun startListeningLoop() {
        isListeningLoopActive = true
        VoiceControlRepository.setListenerStatus(ListenerStatus.INITIALIZING)
        mainHandler.post {
            initSpeechRecognizer()
            startListening()
        }
    }

    private fun initSpeechRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
            ) {
                val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
                speechRecognizer = recognizer
                Log.d(TAG, "Using On-Device SpeechRecognizer")
                ensureOfflineLanguagePack(recognizer)
            } else {
                // Strict offline mode: never fall back to the system recognizer because it may
                // send audio to an online service. Android/device must support an installed
                // on-device recognition model for voice commands to work without internet.
                Log.e(TAG, "On-device speech recognition/model is unavailable; offline mode cannot start")
                VoiceControlRepository.setListenerStatus(ListenerStatus.ERROR)
                return
            }

            speechRecognizer?.setRecognitionListener(createRecognitionListener())
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing SpeechRecognizer", e)
            VoiceControlRepository.setListenerStatus(ListenerStatus.ERROR)
        }
    }

    /**
     * Offline mode only works if the on-device language pack is installed. On Android 13+ we can
     * check that and ask the system to download it (needs internet once; offline forever after).
     */
    private fun ensureOfflineLanguagePack(recognizer: SpeechRecognizer) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        try {
            val languageTag = Locale.getDefault().toLanguageTag()
            val checkIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            }
            recognizer.checkRecognitionSupport(
                checkIntent,
                ContextCompat.getMainExecutor(this),
                object : RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                        val prefix = languageTag.substringBefore('-')
                        val installed = recognitionSupport.installedOnDeviceLanguages
                        val hasPack = installed.any { it.startsWith(prefix, ignoreCase = true) }
                        Log.d(TAG, "Offline packs installed: $installed (need $languageTag)")
                        if (!hasPack) {
                            try {
                                recognizer.triggerModelDownload(checkIntent)
                                Log.d(TAG, "Triggered offline speech model download for $languageTag")
                            } catch (e: Exception) {
                                Log.w(TAG, "Could not trigger offline model download", e)
                            }
                        }
                    }

                    override fun onError(error: Int) {
                        Log.w(TAG, "checkRecognitionSupport error: $error")
                    }
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Offline language pack check failed", e)
        }
    }

    private fun startListening() {
        if (!isListeningLoopActive) return

        try {
            muteSystemSounds()

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // Force offline on-device processing
                putExtra("android.speech.extra.PREFER_OFFLINE", true)
                // Dictation mode to disable start/stop prompt chimes
                putExtra("android.speech.extra.DICTATION_MODE", true)
                putExtra("android.speech.extra.SUPPRESS_START_STOP_SOUNDS", true)
            }

            speechRecognizer?.startListening(intent)
            VoiceControlRepository.setListenerStatus(ListenerStatus.LISTENING)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech listener", e)
            scheduleRestart(600)
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                VoiceControlRepository.setListenerStatus(ListenerStatus.LISTENING)
            }

            override fun onBeginningOfSpeech() {
                consecutiveErrors = 0
                VoiceControlRepository.setListenerStatus(ListenerStatus.LISTENING)
            }

            override fun onRmsChanged(rmsdB: Float) {
                VoiceControlRepository.updateRms(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                VoiceControlRepository.setListenerStatus(ListenerStatus.PROCESSING)
                VoiceControlRepository.updateRms(0f)
            }

            override fun onError(error: Int) {
                VoiceControlRepository.updateRms(0f)
                if (!isListeningLoopActive) return
                Log.d(TAG, "SpeechRecognizer error: $error")

                when (error) {
                    // Normal while nobody is talking: just listen again.
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        consecutiveErrors = 0
                        scheduleRestart(300)
                    }

                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        mainHandler.postDelayed({
                            if (isListeningLoopActive) {
                                initSpeechRecognizer()
                                startListening()
                            }
                        }, 800)
                    }

                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        Log.e(TAG, "Microphone permission missing")
                        VoiceControlRepository.setListenerStatus(ListenerStatus.ERROR)
                        postRestartNotification()
                        stopListeningLoop()
                        stopSelf()
                    }

                    // Language unsupported / offline model unavailable. Never switch to the
                    // standard recognizer: it may send audio to a network service. Stop the
                    // retry loop and tell the user to install a supported offline language pack.
                    12, 13 -> {
                        Log.e(TAG, "Offline language/model unavailable (error=$error); refusing online fallback")
                        VoiceControlRepository.setListenerStatus(ListenerStatus.ERROR)
                        postRestartNotification()
                        stopListeningLoop()
                    }

                    // Client/server/network/audio problems: rebuild the recognizer, back off.
                    else -> {
                        consecutiveErrors++
                        if (consecutiveErrors >= 3) {
                            VoiceControlRepository.setListenerStatus(ListenerStatus.ERROR)
                        }
                        mainHandler.postDelayed({
                            if (isListeningLoopActive) {
                                initSpeechRecognizer()
                                startListening()
                            }
                        }, backoffMs())
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                consecutiveErrors = 0
                VoiceControlRepository.setListenerStatus(ListenerStatus.PROCESSING)
                VoiceControlRepository.updateRms(0f)

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spokenText = matches[0]
                    VoiceControlRepository.updateTranscript(spokenText)
                    processRecognizedSpeech(spokenText)
                }

                scheduleRestart(350)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Display only. Executing on partial text ("open you...") launched the wrong app
                // and then ran the real command a second time.
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    VoiceControlRepository.updateTranscript(matches[0])
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun processRecognizedSpeech(spokenText: String) {
        if (!shouldExecuteCommand(spokenText)) return

        val installedApps = VoiceControlRepository.installedApps.value
        val parsed = CommandParser.parse(spokenText, installedApps)
        executeCommand(parsed)
    }

    private fun shouldExecuteCommand(text: String): Boolean {
        val now = System.currentTimeMillis()
        // Prevent duplicate execution within 1.5 seconds if utterance is identical
        if (now - lastExecutionTime < 1500 && text.equals(lastExecutedUtterance, ignoreCase = true)) {
            return false
        }
        return true
    }

    private fun executeCommand(parsed: com.example.model.ParsedCommand) {
        lastExecutionTime = System.currentTimeMillis()
        lastExecutedUtterance = parsed.rawText
        actionExecutor?.execute(parsed)
    }

    private fun backoffMs(): Long {
        val step = minOf(consecutiveErrors, 5)
        return minOf(500L * (1L shl step), 10_000L)
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!isListeningLoopActive) return
        mainHandler.postDelayed({
            if (isListeningLoopActive) {
                startListening()
            }
        }, delayMs)
    }

    private fun stopListeningLoop() {
        isListeningLoopActive = false
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        restoreSystemSounds()
        VoiceControlRepository.setServiceRunning(false)
        VoiceControlRepository.setListenerStatus(ListenerStatus.STOPPED)
    }

    override fun onDestroy() {
        stopListeningLoop()
        restoreSystemSounds()
        releaseWakeLock()
        actionExecutor?.destroy()
        actionExecutor = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
