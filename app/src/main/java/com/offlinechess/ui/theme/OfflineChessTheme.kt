package com.offlinechess.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF315C46),
    onPrimary = Color.White,
    secondary = Color(0xFF826044),
    background = Color(0xFFF7F7F2),
    surface = Color(0xFFF7F7F2),
    onBackground = Color(0xFF1A1C19),
    onSurfaceVariant = Color(0xFF5D625C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA3D0B2),
    secondary = Color(0xFFE0BE9E),
    background = Color(0xFF171A17),
    surface = Color(0xFF171A17),
    onBackground = Color(0xFFE2E3DD),
    onSurfaceVariant = Color(0xFFC1C8BF),
)

@Composable
fun OfflineChessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
