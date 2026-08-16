package com.smarthome.iot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TealPrimary = Color(0xFF06B6D4) // Vibrant Cyan
private val TealOnPrimary = Color(0xFFFFFFFF)
private val SlateBg = Color(0xFFF8FAFC)
private val SlateSurface = Color(0xFFFFFFFF)
private val Ink = Color(0xFF0F172A)

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = TealOnPrimary,
    primaryContainer = Color(0xFFCFFAFE),
    onPrimaryContainer = Color(0xFF164E63),
    secondary = Color(0xFF6366F1), // Indigo
    secondaryContainer = Color(0xFFE0E7FF),
    background = SlateBg,
    surface = SlateSurface,
    surfaceVariant = Color(0xFFF1F5F9),
    onBackground = Ink,
    onSurface = Ink,
    error = Color(0xFFEF4444),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF10B981), // Emerald 500
    onPrimary = Color(0xFF020617), // Slate 950
    primaryContainer = Color(0xFF059669), // Emerald 600
    onPrimaryContainer = Color(0xFFD1FAE5), // Emerald 100
    secondary = Color(0xFF22D3EE), // Cyan 400
    secondaryContainer = Color(0xFF0891B2), // Cyan 600
    background = Color(0xFF020617), // Slate 950 (app background)
    surface = Color(0xFF0F172A), // Slate 900 (cards/modals)
    surfaceVariant = Color(0xFF1E293B), // Slate 800 (borders/inputs)
    onBackground = Color(0xFFF8FAFC), // Slate 50 (text)
    onSurface = Color(0xFFF8FAFC),
    error = Color(0xFFF43F5E), // Rose 500
)

@Composable
fun SmartHomeTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors, // Forced dark mode
        content = content,
    )
}
