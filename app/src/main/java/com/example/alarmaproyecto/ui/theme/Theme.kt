package com.example.alarmaproyecto.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = IoTPrimary,
    onPrimary = IOTOnPrimary,
    primaryContainer = IoTPrimaryVariant,
    secondary = IoTSecondary,
    onSecondary = IOTOnSecondary,
    secondaryContainer = IoTSecondaryVariant,
    background = IoTBackground,
    onBackground = IOTOnBackground,
    surface = IOTSurface,
    onSurface = IOTOnSurface,
    error = IoTError,
)

private val DarkColorScheme = darkColorScheme(
    primary = IoTPrimary,
    onPrimary = IOTOnPrimary,
    primaryContainer = IoTPrimaryVariant,
    secondary = IoTSecondary,
    onSecondary = IOTOnSecondary,
    secondaryContainer = IoTSecondaryVariant,
    background = IoTBackground,
    onBackground = IOTOnBackground,
    surface = IOTSurface,
    onSurface = IOTOnSurface,
    error = IoTError,
)

@Composable
fun AlarmaProyectoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
