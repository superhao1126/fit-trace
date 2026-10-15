package com.fittrace.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF2F7D5B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE0F0E9),
        onPrimaryContainer = Color(0xFF225E46),
        onSurfaceVariant = Color(0xFF637067),
        outline = Color(0xFFD4DDD8),
        outlineVariant = Color(0xFFE1E6E3),
        background = Color(0xFFF1F3F5),
        surface = Color.White,
        onBackground = Color(0xFF202824),
        onSurface = Color(0xFF202824),
    )
private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF67CDA3),
        onPrimary = Color(0xFF11231A),
        primaryContainer = Color(0xFF203E31),
        onPrimaryContainer = Color(0xFFA5E5C7),
        onSurfaceVariant = Color(0xFFB6C0BC),
        outline = Color(0xFF65766D),
        outlineVariant = Color(0xFF36443D),
        background = Color(0xFF14181A),
        surface = Color(0xFF1C2124),
        onBackground = Color(0xFFE9EEEC),
        onSurface = Color(0xFFE9EEEC),
    )

@Composable
fun FitTraceTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
