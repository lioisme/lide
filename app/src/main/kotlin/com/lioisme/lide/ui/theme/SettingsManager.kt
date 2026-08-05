package com.lioisme.lide.ui.theme

import android.content.Context
import com.lioisme.lide.editor.EditorConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SettingsManager {
    private const val PREF_NAME = "lide_theme"
    private const val KEY_APPEARANCE = "appearance"
    private const val KEY_USE_WALLPAPER = "use_wallpaper"
    private const val KEY_CUSTOM_SEED = "custom_seed"

    private const val EDITOR_PREF = "lide_editor"

    private val _themeConfig = MutableStateFlow(ThemeConfig())
    val themeConfig: StateFlow<ThemeConfig> = _themeConfig.asStateFlow()

    private val _editorConfig = MutableStateFlow(EditorConfig())
    val editorConfig: StateFlow<EditorConfig> = _editorConfig.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _themeConfig.value =
            ThemeConfig(
                appearanceMode =
                    try {
                        AppearanceMode.valueOf(
                            prefs.getString(KEY_APPEARANCE, AppearanceMode.SYSTEM.name) ?: AppearanceMode.SYSTEM.name,
                        )
                    } catch (_: IllegalArgumentException) {
                        AppearanceMode.SYSTEM
                    },
                useWallpaper = prefs.getBoolean(KEY_USE_WALLPAPER, true),
                customSeedColor = prefs.getLong(KEY_CUSTOM_SEED, 0xFF1976D2),
            )
        initEditor(context)
    }

    fun update(
        config: ThemeConfig,
        context: Context,
    ) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs
            .edit()
            .putString(KEY_APPEARANCE, config.appearanceMode.name)
            .putBoolean(KEY_USE_WALLPAPER, config.useWallpaper)
            .putLong(KEY_CUSTOM_SEED, config.customSeedColor)
            .apply()
        _themeConfig.value = config
    }

    /** 编辑器配置按 [SettingsRegistry] 声明式键表驱动读写（B-4）。 */
    fun initEditor(context: Context) {
        val prefs = context.getSharedPreferences(EDITOR_PREF, Context.MODE_PRIVATE)
        _editorConfig.value = SettingsRegistry.load(prefs)
    }

    fun updateEditor(
        config: EditorConfig,
        context: Context,
    ) {
        context
            .getSharedPreferences(EDITOR_PREF, Context.MODE_PRIVATE)
            .edit()
            .apply { SettingsRegistry.applyTo(this, config) }
            .apply()
        _editorConfig.value = config
    }
}
