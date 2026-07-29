package com.delua.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SettingsManager {
    private const val PREF_NAME = "delua_theme"
    private const val KEY_APPEARANCE = "appearance"
    private const val KEY_USE_WALLPAPER = "use_wallpaper"
    private const val KEY_CUSTOM_SEED = "custom_seed"

    private val _themeConfig = MutableStateFlow(ThemeConfig())
    val themeConfig: StateFlow<ThemeConfig> = _themeConfig.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _themeConfig.value = ThemeConfig(
            appearanceMode = AppearanceMode.entries[prefs.getInt(KEY_APPEARANCE, 0)],
            useWallpaper = prefs.getBoolean(KEY_USE_WALLPAPER, true),
            customSeedColor = prefs.getLong(KEY_CUSTOM_SEED, 0xFF1976D2),
        )
    }

    fun update(config: ThemeConfig, context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_APPEARANCE, config.appearanceMode.ordinal)
            .putBoolean(KEY_USE_WALLPAPER, config.useWallpaper)
            .putLong(KEY_CUSTOM_SEED, config.customSeedColor)
            .apply()
        _themeConfig.value = config
    }
}
