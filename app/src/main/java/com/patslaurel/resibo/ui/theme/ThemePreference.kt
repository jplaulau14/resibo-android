package com.patslaurel.resibo.ui.theme

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

@Singleton
class ThemePreference
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("resibo_appearance", Context.MODE_PRIVATE)

        private val _themeMode = MutableStateFlow(loadThemeMode())
        val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

        fun setThemeMode(mode: ThemeMode) {
            prefs.edit().putString(KEY_THEME, mode.name).apply()
            _themeMode.value = mode
        }

        private fun loadThemeMode(): ThemeMode =
            runCatching {
                ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM)

        companion object {
            private const val KEY_THEME = "theme_mode"
        }
    }
