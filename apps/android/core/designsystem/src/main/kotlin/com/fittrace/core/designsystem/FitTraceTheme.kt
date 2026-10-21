package com.fittrace.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF24C867),
        secondary = Color(0xFF229D60),
        secondaryContainer = Color(0xFFE6F8EE),
        onSecondaryContainer = Color(0xFF229D60),
        tertiary = Color(0xFF229D60),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE6F8EE),
        onTertiaryContainer = Color(0xFF229D60),
        surfaceTint = Color(0xFF24C867),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color.White,
        surfaceContainer = Color(0xFFF7F8FA),
        surfaceContainerHigh = Color(0xFFF7F8FA),
        surfaceContainerHighest = Color(0xFFE6EAE8),
        onSecondary = Color.White,
        error = Color(0xFFB6473F),
        errorContainer = Color(0xFFFFF1EE),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE6F8EE),
        onPrimaryContainer = Color(0xFF229D60),
        onSurfaceVariant = Color(0xFF777777),
        outline = Color(0xFFE6EAE8),
        outlineVariant = Color(0xFFE6EAE8),
        background = Color(0xFFF7F8FA),
        surface = Color.White,
        onBackground = Color(0xFF222222),
        onSurface = Color(0xFF222222),
    )
private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF24C867),
        secondary = Color(0xFF62DEA0),
        secondaryContainer = Color(0xFF163E29),
        onSecondaryContainer = Color(0xFF62DEA0),
        tertiary = Color(0xFF62DEA0),
        onTertiary = Color(0xFF111713),
        tertiaryContainer = Color(0xFF163E29),
        onTertiaryContainer = Color(0xFF62DEA0),
        surfaceTint = Color(0xFF24C867),
        surfaceContainerLowest = Color(0xFF111713),
        surfaceContainerLow = Color(0xFF1D2620),
        surfaceContainer = Color(0xFF1D2620),
        surfaceContainerHigh = Color(0xFF1D2620),
        surfaceContainerHighest = Color(0xFF35483D),
        onSecondary = Color(0xFF111713),
        error = Color(0xFFFFB4AB),
        errorContainer = Color(0xFF432823),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF163E29),
        onPrimaryContainer = Color(0xFF62DEA0),
        onSurfaceVariant = Color(0xFFACB9B0),
        outline = Color(0xFFACB9B0),
        outlineVariant = Color(0xFF35483D),
        background = Color(0xFF111713),
        surface = Color(0xFF1D2620),
        onBackground = Color(0xFFE9F1EB),
        onSurface = Color(0xFFE9F1EB),
    )

@Composable
fun FitTraceTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}
