package com.moodcam.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark color scheme optimized for camera use
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFF5A623),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFFE09000),
    onPrimaryContainer = Color.White,
    
    secondary = Color(0xFF6B7280),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF374151),
    onSecondaryContainer = Color.White,
    
    tertiary = Color(0xFF9CA3AF),
    onTertiary = Color.Black,
    
    background = Color(0xFF0D0D0D),
    onBackground = Color.White,
    
    surface = Color(0xFF1A1A1A),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFFAAAAAA),
    
    error = Color(0xFFFF6B6B),
    onError = Color.White,
    
    outline = Color(0xFF404040),
    outlineVariant = Color(0xFF2A2A2A)
)

// Curve editor colors
object CurveColors {
    val luma = Color.White
    val red = Color(0xFFFF6B6B)
    val green = Color(0xFF69DB7C)
    val blue = Color(0xFF74C0FC)
}

@Composable
fun MoodCamTheme(
    darkTheme: Boolean = true, // Always dark for camera
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
