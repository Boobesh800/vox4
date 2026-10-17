package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ListenerStatus
import com.example.ui.theme.VoxCyanGlow
import com.example.ui.theme.VoxCyanNeon
import com.example.ui.theme.VoxEmeraldLive
import com.example.ui.theme.VoxRoseError
import com.example.ui.theme.VoxSurfaceVariant
import com.example.ui.theme.VoxVioletNeon
import kotlin.math.sin

@Composable
fun VoicePulseVisualizer(
    isServiceRunning: Boolean,
    status: ListenerStatus,
    rmsDb: Float,
    onToggleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_animation")

    // Breathing pulse for idle listening
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Animated RMS level smoothed for visualizer bars
    val normalizedRms = (rmsDb.coerceIn(0f, 15f) / 15f).coerceIn(0.1f, 1f)
    val animatedRms by animateFloatAsState(
        targetValue = if (isServiceRunning) normalizedRms else 0.05f,
        animationSpec = tween(80),
        label = "rms_anim"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF131B2F),
                        Color(0xFF0D1322)
                    )
                )
            )
            .border(1.dp, Color(0xFF233150), RoundedCornerShape(24.dp))
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Status Tag
        StatusBadge(isServiceRunning = isServiceRunning, status = status)

        Spacer(modifier = Modifier.height(20.dp))

        // Center Pulsing Circle & Glowing Mic Button
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            // Concentric Glowing Rings (Active only when running)
            if (isServiceRunning) {
                // Outer ring
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    VoxCyanNeon.copy(alpha = 0.18f * animatedRms),
                                    Color.Transparent
                                )
                            )
                        )
                )
                // Mid ring
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(1f + (pulseScale - 1f) * 0.5f)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    VoxVioletNeon.copy(alpha = 0.25f * animatedRms),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            // Main Action Button
            Surface(
                onClick = onToggleClick,
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .testTag("toggle_voice_service_button"),
                shape = CircleShape,
                color = if (isServiceRunning) VoxSurfaceVariant else Color(0xFF1A1F30),
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            if (isServiceRunning) {
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF0052CC),
                                        Color(0xFF00E5FF)
                                    )
                                )
                            } else {
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF334155),
                                        Color(0xFF1E293B)
                                    )
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isServiceRunning) Icons.Default.Mic else Icons.Default.PowerSettingsNew,
                        contentDescription = if (isServiceRunning) "Stop Voice Service" else "Start Voice Service",
                        tint = if (isServiceRunning) Color.White else Color(0xFF94A3B8),
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Soundwave Frequency Bar Graph
        SoundwaveBarCanvas(
            isLive = isServiceRunning,
            amplitude = animatedRms,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(38.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Instruction Text
        Text(
            text = if (isServiceRunning) {
                "Say \"Turn on torch\", \"Turn on internet\", \"Open YouTube\""
            } else {
                "Tap to activate continuous background & screen-off voice control"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (isServiceRunning) VoxCyanNeon else Color(0xFF94A3B8),
            fontWeight = if (isServiceRunning) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun StatusBadge(
    isServiceRunning: Boolean,
    status: ListenerStatus
) {
    val (bgColor, dotColor, labelText) = when {
        !isServiceRunning -> Triple(
            Color(0xFF261E28),
            Color(0xFF94A3B8),
            "OFFLINE SERVICE IDLE"
        )
        status == ListenerStatus.PROCESSING -> Triple(
            Color(0xFF3B2A10),
            Color(0xFFF59E0B),
            "PROCESSING COMMAND..."
        )
        status == ListenerStatus.ERROR -> Triple(
            Color(0xFF3B1218),
            VoxRoseError,
            "MICROPHONE RECONNECTING"
        )
        else -> Triple(
            Color(0xFF072A22),
            VoxEmeraldLive,
            "LISTENING (SCREEN-OFF READY)"
        )
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .border(1.dp, dotColor.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = labelText,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun SoundwaveBarCanvas(
    isLive: Boolean,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_oscillation")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset_anim"
    )

    Canvas(modifier = modifier) {
        val barCount = 27
        val barWidth = size.width / (barCount * 1.6f)
        val space = (size.width - (barCount * barWidth)) / (barCount - 1)
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val x = i * (barWidth + space)
            val factor = sin(i.toFloat() * 0.45f + waveOffset) * 0.5f + 0.5f
            val dynamicHeight = if (isLive) {
                val base = size.height * 0.15f
                val active = size.height * 0.85f * (amplitude * (0.4f + factor * 0.6f))
                (base + active).coerceIn(4f, size.height)
            } else {
                size.height * 0.12f
            }

            val top = centerY - (dynamicHeight / 2f)
            val barColor = if (isLive) {
                // Gradient from violet to cyan
                val ratio = i / barCount.toFloat()
                if (ratio < 0.5f) VoxCyanNeon else VoxVioletNeon
            } else {
                Color(0xFF334155)
            }

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, top),
                size = Size(barWidth, dynamicHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
