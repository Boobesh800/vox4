package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CommandType
import com.example.ui.theme.VoxCyanNeon
import com.example.ui.theme.VoxSurfaceVariant
import com.example.ui.theme.VoxVioletNeon

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommandCheatsheetCard(
    onTriggerVoiceCue: (CommandType, String, String?, String?, Int?, Int?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val commandGroups = listOf(
        CommandGroup(
            category = "OFFLINE ALARM & CLOCK",
            color = Color(0xFFF59E0B),
            items = listOf(
                CommandItem("set alarm at 7 am", CommandType.SET_ALARM, hour = 7, min = 0, display = "7:00 AM"),
                CommandItem("set alarm at 6:30 pm", CommandType.SET_ALARM, hour = 18, min = 30, display = "6:30 PM"),
                CommandItem("turn off alarm at 7 am", CommandType.DISMISS_ALARM, hour = 7, min = 0, display = "7:00 AM"),
                CommandItem("turn off alarm", CommandType.DISMISS_ALARM),
                CommandItem("cancel alarm", CommandType.DISMISS_ALARM)
            )
        ),
        CommandGroup(
            category = "OFFLINE CALLS & DIALER",
            color = Color(0xFF10B981),
            items = listOf(
                CommandItem("call mom", CommandType.CALL_CONTACT, targetContactOrNumber = "mom"),
                CommandItem("call dad", CommandType.CALL_CONTACT, targetContactOrNumber = "dad"),
                CommandItem("call 123", CommandType.CALL_CONTACT, targetContactOrNumber = "123"),
                CommandItem("make a call to john", CommandType.CALL_CONTACT, targetContactOrNumber = "john"),
                CommandItem("dial emergency", CommandType.CALL_CONTACT, targetContactOrNumber = "emergency")
            )
        ),
        CommandGroup(
            category = "OPEN APPS (FROM ANYWHERE & SCREEN OFF)",
            color = VoxVioletNeon,
            items = listOf(
                CommandItem("open youtube", CommandType.OPEN_APP, targetApp = "YouTube"),
                CommandItem("open whatsapp", CommandType.OPEN_APP, targetApp = "WhatsApp"),
                CommandItem("open camera", CommandType.OPEN_APP, targetApp = "Camera"),
                CommandItem("open settings", CommandType.OPEN_APP, targetApp = "Settings"),
                CommandItem("open calculator", CommandType.OPEN_APP, targetApp = "Calculator")
            )
        ),
        CommandGroup(
            category = "TORCH LIGHT",
            color = Color(0xFFFFB300),
            items = listOf(
                CommandItem("turn on torch light", CommandType.TORCH_ON),
                CommandItem("turn off torch light", CommandType.TORCH_OFF),
                CommandItem("torch on", CommandType.TORCH_ON),
                CommandItem("torch off", CommandType.TORCH_OFF)
            )
        ),
        CommandGroup(
            category = "INTERNET & DATA",
            color = VoxCyanNeon,
            items = listOf(
                CommandItem("turn on internet", CommandType.INTERNET_ON),
                CommandItem("turn off internet", CommandType.INTERNET_OFF),
                CommandItem("turn on mobile data", CommandType.INTERNET_ON),
                CommandItem("turn off mobile data", CommandType.INTERNET_OFF)
            )
        ),
        CommandGroup(
            category = "BLUETOOTH & WI-FI",
            color = Color(0xFF38BDF8),
            items = listOf(
                CommandItem("turn on bluetooth", CommandType.BLUETOOTH_ON),
                CommandItem("turn off bluetooth", CommandType.BLUETOOTH_OFF),
                CommandItem("turn on wifi", CommandType.WIFI_ON),
                CommandItem("turn off wifi", CommandType.WIFI_OFF)
            )
        ),
        CommandGroup(
            category = "VOLUME & SYSTEM",
            color = Color(0xFFA78BFA),
            items = listOf(
                CommandItem("volume up", CommandType.VOLUME_UP),
                CommandItem("volume down", CommandType.VOLUME_DOWN),
                CommandItem("mute", CommandType.VOLUME_MUTE),
                CommandItem("battery level", CommandType.BATTERY_CHECK),
                CommandItem("what time is it", CommandType.TIME_CHECK)
            )
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(VoxSurfaceVariant)
            .border(1.dp, Color(0xFF28344E), RoundedCornerShape(20.dp))
            .padding(18.dp)
            .testTag("command_cheatsheet_card")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2E1B59)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = "Voice commands",
                    tint = VoxVioletNeon,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "SUPPORTED OFFLINE VOICE PHRASES",
                    style = MaterialTheme.typography.labelSmall,
                    color = VoxVioletNeon,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Works offline, from home screen & inside other apps. Tap any chip to test.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        commandGroups.forEach { group ->
            Text(
                text = group.category,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = group.color,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                group.items.forEach { item ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF141C2E))
                            .border(1.dp, group.color.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .clickable {
                                onTriggerVoiceCue(
                                    item.type,
                                    item.phrase,
                                    item.targetApp,
                                    item.targetContactOrNumber,
                                    item.hour,
                                    item.min,
                                    item.display
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "\"${item.phrase}\"",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

private data class CommandGroup(
    val category: String,
    val color: Color,
    val items: List<CommandItem>
)

private data class CommandItem(
    val phrase: String,
    val type: CommandType,
    val targetApp: String? = null,
    val targetContactOrNumber: String? = null,
    val hour: Int? = null,
    val min: Int? = null,
    val display: String? = null
)
