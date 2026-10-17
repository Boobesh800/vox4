package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = VoxCyanNeon,
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF004D59),
    onPrimaryContainer = Color(0xFFBCEBFF),
    secondary = VoxVioletNeon,
    onSecondary = Color(0xFF2C0B5E),
    secondaryContainer = Color(0xFF451E84),
    onSecondaryContainer = Color(0xFFEADBFF),
    tertiary = VoxEmeraldLive,
    onTertiary = Color(0xFF003822),
    background = VoxBackground,
    onBackground = TextPrimary,
    surface = VoxSurface,
    onSurface = TextPrimary,
    surfaceVariant = VoxSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = VoxRoseError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Always use our styled cyber dark scheme to deliver the polished dark voice assistant aesthetic
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
