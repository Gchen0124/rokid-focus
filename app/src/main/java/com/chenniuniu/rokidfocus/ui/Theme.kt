package com.chenniuniu.rokidfocus.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF1B7F4E)
private val Ink = Color(0xFF111111)

private val Light = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    background = Color(0xFFF4F1EA),
    surface = Color.White,
    onSurface = Ink,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF6DDBA4),
    onPrimary = Ink,
    background = Color(0xFF101010),
    surface = Color(0xFF1A1A1A),
    onSurface = Color(0xFFF2F2F2),
)

@Composable
fun FocusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
