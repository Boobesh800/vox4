package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.VoiceListenerService
import com.example.ui.VoiceControlViewModel
import com.example.ui.components.CommandCheatsheetCard
import com.example.ui.components.CommandHistoryList
import com.example.ui.components.OfflineOptimizationBanner
import com.example.ui.components.QuickActionGrid
import com.example.ui.components.VoicePulseVisualizer
import com.example.ui.components.VoiceTranscriptCard
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.VoxBackground
import com.example.ui.theme.VoxCyanNeon
import com.example.ui.theme.VoxEmeraldLive

class MainActivity : ComponentActivity() {

    private val viewModel: VoiceControlViewModel by viewModels()

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleAutoStart(intent)
    }

    /** Tapping the "VoxControl stopped" notification restarts listening in one tap. */
    private fun handleAutoStart(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(VoiceListenerService.EXTRA_AUTO_START, false) != true) return
        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) viewModel.startService(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAutoStart(intent)

        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                // Permission Launcher for Microphone, Camera, Calls, Contacts, Notifications & Bluetooth
                val requiredPermissions = mutableListOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_CONTACTS
                ).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                }.toTypedArray()

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
                    if (micGranted) {
                        viewModel.startService(context)
                        Toast.makeText(context, "Offline Voice Service Started", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Microphone permission is required for voice control", Toast.LENGTH_LONG).show()
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.refreshInstalledApps(context)
                }

                // Overlay + battery access are what let open-app / listening work with the app
                // closed and the screen off. Asked one at a time, only while still missing.
                LaunchedEffect(Unit) {
                    viewModel.promptNextMissingAccess(context)
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(VoxBackground),
                    containerColor = VoxBackground
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Header bar
                            item {
                                VoxTopBar(
                                    isServiceRunning = uiState.isServiceRunning,
                                    isVoiceMuted = uiState.deviceState.isVoiceFeedbackMuted
                                )
                            }

                            // Main Voice Pulse Visualizer & Master Switch
                            item {
                                VoicePulseVisualizer(
                                    isServiceRunning = uiState.isServiceRunning,
                                    status = uiState.listenerStatus,
                                    rmsDb = uiState.rmsDb,
                                    onToggleClick = {
                                        val hasMic = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED

                                        if (uiState.isServiceRunning) {
                                            viewModel.stopService(context)
                                        } else {
                                            if (hasMic) {
                                                viewModel.startService(context)
                                            } else {
                                                permissionLauncher.launch(requiredPermissions)
                                            }
                                        }
                                    }
                                )
                            }

                            // Live Speech Transcript
                            item {
                                VoiceTranscriptCard(
                                    currentTranscript = uiState.currentTranscript,
                                    isServiceRunning = uiState.isServiceRunning
                                )
                            }

                            // Hardware & Connectivity Quick Actions (Torch, Internet, BT, WiFi, Call, Alarm, Apps)
                            item {
                                QuickActionGrid(
                                    deviceState = uiState.deviceState,
                                    onExecuteCommand = { type, raw, app, contactOrNum, hour, min, display ->
                                        viewModel.executeManualCommand(context, type, raw, app, contactOrNum, hour, min, display)
                                    },
                                    onToggleMuteFeedback = { muted ->
                                        viewModel.toggleVoiceFeedback(muted)
                                    }
                                )
                            }

                            // Interactive Supported Voice Phrases Cheatsheet (Including Calls & Alarms)
                            item {
                                CommandCheatsheetCard(
                                    onTriggerVoiceCue = { type, raw, app, contactOrNum, hour, min, display ->
                                        viewModel.executeManualCommand(context, type, raw, app, contactOrNum, hour, min, display)
                                    }
                                )
                            }

                            // Background & Screen-Off Battery Optimization Banner
                            item {
                                OfflineOptimizationBanner(
                                    onRequestBatteryOptimization = {
                                        viewModel.requestIgnoreBatteryOptimization(context)
                                    },
                                    onRequestOverlayPermission = {
                                        viewModel.requestOverlayPermission(context)
                                    }
                                )
                            }

                            // Activity Log of executed commands
                            item {
                                CommandHistoryList(
                                    history = uiState.commandHistory,
                                    onClearHistory = {
                                        viewModel.clearHistory()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoxTopBar(isServiceRunning: Boolean, isVoiceMuted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.ic_vox_icon),
                contentDescription = "VoxControl Logo",
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "VoxControl",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isVoiceMuted) "Silent Tactile • 100% Offline" else "Voice Enabled • 100% Offline",
                    fontSize = 11.sp,
                    color = VoxCyanNeon,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Active indicator badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (isServiceRunning) Color(0xFF063A2F) else Color(0xFF1E283D))
                .border(
                    1.dp,
                    if (isServiceRunning) VoxEmeraldLive.copy(alpha = 0.5f) else Color(0xFF334155),
                    RoundedCornerShape(50)
                )
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isServiceRunning) VoxEmeraldLive else Color(0xFF94A3B8))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isServiceRunning) "LISTENING" else "STANDBY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isServiceRunning) VoxEmeraldLive else Color(0xFF94A3B8)
                )
            }
        }
    }
}
