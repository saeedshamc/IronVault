package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = IronLightTeal,
    onPrimary = IronDarkTeal,
    primaryContainer = IronDarkTeal,
    onPrimaryContainer = IronLightTeal,
    secondary = IronLightTeal,
    background = IronDarkBackground,
    surface = IronDarkSurface,
    surfaceVariant = IronDarkSurfaceVariant,
    onBackground = IronDarkOnBackground,
    onSurface = IronDarkOnSurface
)

private val LightColorScheme = lightColorScheme(
    primary = IronTeal,
    onPrimary = IronLightSurface,
    primaryContainer = IronLightTeal,
    onPrimaryContainer = IronDarkTeal,
    secondary = IronTeal,
    background = IronLightBackground,
    surface = IronLightSurface,
    surfaceVariant = IronLightSurfaceVariant,
    onBackground = IronLightOnBackground,
    onSurface = IronLightOnSurface
)

@Composable
fun IronVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
