package com.patrollink.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TechBlue = Color(0xFF0057FF)
val PageBg = Color(0xFFF4F7FA)
val SurfaceWhite = Color(0xFFFFFFFF)
val Danger = Color(0xFFFF4D4F)
val Warning = Color(0xFFFAAD14)
val Success = Color(0xFF52C41A)
val Ink = Color(0xFF1D2129)
val Muted = Color(0xFF86909C)
val Navy = Color(0xFF0B1326)
val Border = Color(0xFFE5EAF0)

private val PatrolLightScheme = lightColorScheme(
    primary = TechBlue,
    onPrimary = Color.White,
    secondary = Warning,
    error = Danger,
    background = PageBg,
    surface = SurfaceWhite,
    onSurface = Ink,
    outline = Border
)

@Composable
fun PatrolTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PatrolLightScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
