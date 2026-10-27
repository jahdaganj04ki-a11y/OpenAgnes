package com.openagnes.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF3B3B98)
private val IndigoLight = Color(0xFF6C6CD0)

private val LightColors = lightColorScheme(
    primary = Indigo,
    secondary = Color(0xFF5B5BA6),
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    secondary = Color(0xFFB0B0E8),
)

@Composable
fun OpenAgnesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
