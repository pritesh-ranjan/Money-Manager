package com.moneymanager.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

object ThemePreferenceManager {
    private const val PREFS_NAME = "money_manager_theme_prefs"
    private const val KEY_THEME_MODE = "key_theme_mode"

    private lateinit var prefs: SharedPreferences

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun init(context: Context) {
        init(context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    }

    fun init(sharedPreferences: SharedPreferences) {
        prefs = sharedPreferences
        val saved = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        _themeMode.value = try {
            ThemeMode.valueOf(saved)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        }
    }
}
