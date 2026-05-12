package com.patrollink.presentation.component

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun SystemBars(
    statusBarColor: Color,
    navigationBarColor: Color = statusBarColor,
    lightStatusBar: Boolean,
    lightNavigationBar: Boolean = lightStatusBar
) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.decorView.setBackgroundColor(navigationBarColor.toArgb())
        @Suppress("DEPRECATION")
        window.statusBarColor = statusBarColor.toArgb()
        @Suppress("DEPRECATION")
        window.navigationBarColor = navigationBarColor.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightStatusBar
            isAppearanceLightNavigationBars = lightNavigationBar
        }
    }
}
