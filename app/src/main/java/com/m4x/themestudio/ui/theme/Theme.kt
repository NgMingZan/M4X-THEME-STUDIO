package com.m4x.themestudio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = lightColorScheme(
    primary = Color(0xFF7050C4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF2A155B),
    secondary = Color(0xFF2FAAA5),
    background = Color(0xFFFFF8FF),
    surface = Color(0xFFFFF8FF),
    surfaceVariant = Color(0xFFEDE7EE),
    onSurface = Color(0xFF1F1A21),
    outline = Color(0xFF817A84)
)

@Composable
fun M4XTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
