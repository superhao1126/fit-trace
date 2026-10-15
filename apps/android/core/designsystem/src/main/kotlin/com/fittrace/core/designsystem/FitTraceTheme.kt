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
        background = Color(0xFFF1F3F5),
        surface = Color.White,
        onBackground = Color(0xFF202824),
        onSurface = Color(0xFF202824),
    )
private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF67CDA3),
        onPrimary = Color(0xFF11231A),
        background = Color(0xFF14181A),
        surface = Color(0xFF1C2124),
        onBackground = Color(0xFFE9EEEC),
        onSurface = Color(0xFFE9EEEC),
    )

@Composable
fun FitTraceTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
