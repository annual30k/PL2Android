package com.patrollink.data.local

import android.content.Context
import com.patrollink.domain.DisplayThemeMode
import com.patrollink.domain.FontSizeMode

class UiSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("patrol_ui_settings", Context.MODE_PRIVATE)

    fun readFontSizeMode(): FontSizeMode =
        prefs.getString(KEY_FONT, null)?.let { runCatching { FontSizeMode.valueOf(it) }.getOrNull() }
            ?: FontSizeMode.Standard

    fun readDisplayThemeMode(): DisplayThemeMode =
        prefs.getString(KEY_THEME, null)?.let { runCatching { DisplayThemeMode.valueOf(it) }.getOrNull() }
            ?: DisplayThemeMode.System

    fun saveFontSizeMode(mode: FontSizeMode) {
        prefs.edit().putString(KEY_FONT, mode.name).apply()
    }

    fun saveDisplayThemeMode(mode: DisplayThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    private companion object {
        const val KEY_FONT = "font_size_mode"
        const val KEY_THEME = "display_theme_mode"
    }
}
