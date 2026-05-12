package com.patrollink.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import com.patrollink.domain.DisplayThemeMode
import com.patrollink.domain.FontSizeMode

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

private val PatrolDarkScheme = darkColorScheme(
    primary = TechBlue,
    onPrimary = Color.White,
    secondary = Warning,
    error = Danger,
    background = Navy,
    surface = Color(0xFF131B2E),
    onSurface = Color(0xFFE5ECFF),
    outline = Color(0xFF26334D)
)

data class PatrolDisplayConfig(
    val fontSizeMode: FontSizeMode = FontSizeMode.Standard,
    val displayThemeMode: DisplayThemeMode = DisplayThemeMode.System,
    val dark: Boolean = false
)

data class PatrolColors(
    val dark: Boolean,
    val page: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val topBar: Color,
    val bottomBar: Color,
    val text: Color,
    val textMuted: Color,
    val textSubtle: Color,
    val border: Color,
    val control: Color,
    val controlSelected: Color
)

private val LightPatrolColors = PatrolColors(
    dark = false,
    page = Color(0xFFF5F7FB),
    surface = Color.White,
    surfaceHigh = Color(0xFFF8FAFC),
    topBar = Color(0xFFF5F7FB),
    bottomBar = Color.White,
    text = Color(0xFF111827),
    textMuted = Color(0xFF64748B),
    textSubtle = Color(0xFF94A3B8),
    border = Color(0xFFE2E8F0),
    control = Color(0xFFE9EEF6),
    controlSelected = Color.White
)

private val DarkPatrolColors = PatrolColors(
    dark = true,
    page = Color(0xFF071120),
    surface = Color(0xFF121C2E),
    surfaceHigh = Color(0xFF202B42),
    topBar = Color(0xFF071120),
    bottomBar = Color(0xFF0B1326),
    text = Color(0xFFEAF0FF),
    textMuted = Color(0xFFB4C0D6),
    textSubtle = Color(0xFF748199),
    border = Color(0xFF26334D),
    control = Color(0xFF0E1728),
    controlSelected = Color(0xFF26334D)
)

val LocalPatrolDisplayConfig = compositionLocalOf { PatrolDisplayConfig() }
val LocalPatrolColors = compositionLocalOf { LightPatrolColors }

val FontSizeMode.scale: Float
    get() = when (this) {
        FontSizeMode.Compact -> 0.92f
        FontSizeMode.Standard -> 1f
        FontSizeMode.Large -> 1.12f
    }

@Composable
fun PatrolTheme(
    fontSizeMode: FontSizeMode = FontSizeMode.Standard,
    displayThemeMode: DisplayThemeMode = DisplayThemeMode.System,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (displayThemeMode) {
        DisplayThemeMode.System -> systemDark
        DisplayThemeMode.Light -> false
        DisplayThemeMode.Dark -> true
    }
    val density = LocalDensity.current
    val scaledDensity = Density(density = density.density, fontScale = density.fontScale * fontSizeMode.scale)

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalPatrolDisplayConfig provides PatrolDisplayConfig(
            fontSizeMode = fontSizeMode,
            displayThemeMode = displayThemeMode,
            dark = dark
        ),
        LocalPatrolColors provides if (dark) DarkPatrolColors else LightPatrolColors
    ) {
        MaterialTheme(
            colorScheme = if (dark) PatrolDarkScheme else PatrolLightScheme,
            typography = PatrolTypography,
            content = content
        )
    }
}

object PatrolDisplay {
    val config: PatrolDisplayConfig
        @Composable
        @ReadOnlyComposable
        get() = LocalPatrolDisplayConfig.current

    val colors: PatrolColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPatrolColors.current
}
