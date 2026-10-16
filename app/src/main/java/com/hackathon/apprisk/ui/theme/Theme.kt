package com.hackathon.apprisk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF172522)
private val Teal = Color(0xFF0B8F77)

private val LightScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = Color(0xFF45645C),
    background = Color(0xFFF5F8F7),
    surface = Color(0xFFFFFFFF),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE8EFEC),
    error = Color(0xFFBA1A1A),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF70D7BE),
    secondary = Color(0xFFA4CFC2),
    background = Color(0xFF101715),
    surface = Color(0xFF18211E),
    onSurface = Color(0xFFE0E9E5),
    surfaceVariant = Color(0xFF26332F),
)

@Composable
fun AppRiskTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
