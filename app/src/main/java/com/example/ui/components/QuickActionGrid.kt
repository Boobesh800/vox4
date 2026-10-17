package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CommandType
import com.example.model.DeviceState
import com.example.ui.theme.VoxCyanNeon
import com.example.ui.theme.VoxEmeraldLive
import com.example.ui.theme.VoxSurfaceVariant
import com.example.ui.theme.VoxVioletNeon

@Composable
fun QuickActionGrid(
    deviceState: DeviceState,
    onExecuteCommand: (CommandType, String, String?, String?, Int?, Int?, String?) -> Unit,
    onToggleMuteFeedback: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "HARDWARE & CONNECTIVITY CONTROLS",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF94A3B8),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Row 1: Torch & Internet
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Torch / Flashlight Card
            ActionCard(
                title = "Torch Light",
                voiceCue = "\"turn on torch light\"",
                isActive = deviceState.isTorchOn,
                activeColor = Color(0xFFFFB300),
                icon = if (deviceState.isTorchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                testTag = "quick_torch_card",
                modifier = Modifier.weight(1f),
                onClick = {
                    val next = !deviceState.isTorchOn
                    val type = if (next) CommandType.TORCH_ON else CommandType.TORCH_OFF
                    val phrase = if (next) "turn on torch light" else "turn off torch light"
                    onExecuteCommand(type, phrase, null, null, null, null, null)
                }
            )

            // Internet Connectivity Card
            ActionCard(
                title = "Internet / Data",
                voiceCue = "\"turn on internet\"",
                isActive = false,
                activeColor = VoxCyanNeon,
                icon = Icons.Default.Language,
                testTag = "quick_internet_card",
                modifier = Modifier.weight(1f),
                onClick = {
                    onExecuteCommand(CommandType.INTERNET_ON, "turn on internet", null, null, null, null, null)
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Row 2: Bluetooth & Wi-Fi
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Bluetooth Card
            ActionCard(
                title = "Bluetooth",
                voiceCue = "\"turn on bluetooth\"",
                isActive = deviceState.isBluetoothOn,
                activeColor = Color(0xFF3B82F6),
                icon = Icons.Default.Bluetooth,
                testTag = "quick_bluetooth_card",
                modifier = Modifier.weight(1f),
                onClick = {
                    val next = !deviceState.isBluetoothOn
                    val type = if (next) CommandType.BLUETOOTH_ON else CommandType.BLUETOOTH_OFF
                    val phrase = if (next) "turn on bluetooth" else "turn off bluetooth"
                    onExecuteCommand(type, phrase, null, null, null, null, null)
                }
            )

            // Wi-Fi Card
            ActionCard(
                title = "Wi-Fi",
                voiceCue = "\"turn on wifi\"",
                isActive = deviceState.isWifiOn,
                activeColor = VoxEmeraldLive,
                icon = Icons.Default.Wifi,
                testTag = "quick_wifi_card",
                modifier = Modifier.weight(1f),
                onClick = {
                    val next = !deviceState.isWifiOn
                    val type = if (next) CommandType.WIFI_ON else CommandType.WIFI_OFF
                    val phrase = if (next) "turn on wifi" else "turn off wifi"
                    onExecuteCommand(type, phrase, null, null, null, null, null)
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Row 3: Offline Alarms (Set & Turn Off)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Set Alarm Card
            ActionCard(
                title = "Set Alarm (7 AM)",
                voiceCue = "\"set alarm at 7 am\"",
                isActive = false,
                activeColor = Color(0xFFF59E0B),
                icon = Icons.Default.Alarm,
                testTag = "quick_set_alarm_card",
                modifier = Modifier.weight(1f),
                onClick = {
                    onExecuteCommand(CommandType.SET_ALARM, "set alarm at 7:00 am", null, null, 7, 0, "7:00 AM")
                }
            )

            // Turn Off Alarm Card
            ActionCard(
                title = "Turn Off Alarm",
                voiceCue = "\"turn off alarm\"",
                isActive = false,
                activeColor = Color(0xFFEF4444),
                icon = Icons.Default.AlarmOff,
                testTag = "quick_off_alarm_card",
                modifier = Modifier.weight(1f),
                onClick = {
                    onExecuteCommand(CommandType.DISMISS_ALARM, "turn off alarm", null, null, null, null, null)
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Row 4: Offline Call & Silent Mode
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Offline Call Card
            ActionCard(
                title = "Offline Call",
                voiceCue = "\"call mom\" / \"dial 123\"",
                isActive = false,
                activeColor = Color(0xFF10B981),
                icon = Icons.Default.Call,
                testTag = "quick_call_card",
                modifier = Modifier.weight(1f),
                onClick = {
                    onExecuteCommand(CommandType.CALL_CONTACT, "call phone", null, "dialer", null, null, null)
                }
            )

            // Silent Mode (No App Voice)
            ActionCard(
                title = if (deviceState.isVoiceFeedbackMuted) "App Voice: SILENT" else "App Voice: ON",
                voiceCue = if (deviceState.isVoiceFeedbackMuted) "No talking back" else "Voice response on",
                isActive = deviceState.isVoiceFeedbackMuted,
                activeColor = Color(0xFF8B5CF6),
                icon = if (deviceState.isVoiceFeedbackMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                testTag = "quick_silent_toggle",
                modifier = Modifier.weight(1f),
                onClick = {
                    onToggleMuteFeedback(!deviceState.isVoiceFeedbackMuted)
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Row 5: Quick App Testers (YouTube & WhatsApp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // YouTube
            ActionCard(
                title = "Open YouTube",
                voiceCue = "\"open youtube\"",
                isActive = false,
                activeColor = Color(0xFFFF0000),
                icon = Icons.Default.Apps,
                testTag = "quick_open_youtube",
                modifier = Modifier.weight(1f),
                onClick = {
                    onExecuteCommand(CommandType.OPEN_APP, "open youtube", "YouTube", null, null, null, null)
                }
            )

            // WhatsApp
            ActionCard(
                title = "Open WhatsApp",
                voiceCue = "\"open whatsapp\"",
                isActive = false,
                activeColor = Color(0xFF25D366),
                icon = Icons.Default.Apps,
                testTag = "quick_open_whatsapp",
                modifier = Modifier.weight(1f),
                onClick = {
                    onExecuteCommand(CommandType.OPEN_APP, "open whatsapp", "WhatsApp", null, null, null, null)
                }
            )
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    voiceCue: String,
    isActive: Boolean,
    activeColor: Color,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(VoxSurfaceVariant)
            .border(
                width = 1.dp,
                color = if (isActive) activeColor else Color(0xFF28344E),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag(testTag)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) activeColor.copy(alpha = 0.2f) else Color(0xFF1E283D)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isActive) activeColor else Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (isActive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(activeColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = voiceCue,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1
            )
        }
    }
}
