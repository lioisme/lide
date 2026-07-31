package com.lioisme.delua.ui.theme

import android.content.Context
import com.lioisme.delua.editor.EditorConfig
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

    // === Editor Settings ===
    private const val EDITOR_PREF = "delua_editor"
    private const val KEY_WORD_WRAP = "word_wrap"
    private const val KEY_SHOW_WHITESPACE = "show_whitespace"
    private const val KEY_FONT_SIZE = "font_size"
    private const val KEY_TAB_SIZE = "tab_size"

    private val _editorConfig = MutableStateFlow(EditorConfig())
    val editorConfig: StateFlow<EditorConfig> = _editorConfig.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _themeConfig.value = ThemeConfig(
            appearanceMode = try {
                AppearanceMode.valueOf(
                    prefs.getString(KEY_APPEARANCE, AppearanceMode.SYSTEM.name) ?: AppearanceMode.SYSTEM.name
                )
            } catch (_: IllegalArgumentException) {
                AppearanceMode.SYSTEM
            },
            useWallpaper = prefs.getBoolean(KEY_USE_WALLPAPER, true),
            customSeedColor = prefs.getLong(KEY_CUSTOM_SEED, 0xFF1976D2),
        )
        initEditor(context)
    }

    fun update(config: ThemeConfig, context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_APPEARANCE, config.appearanceMode.name)
            .putBoolean(KEY_USE_WALLPAPER, config.useWallpaper)
            .putLong(KEY_CUSTOM_SEED, config.customSeedColor)
            .apply()
        _themeConfig.value = config
    }

    fun initEditor(context: Context) {
        val prefs = context.getSharedPreferences(EDITOR_PREF, Context.MODE_PRIVATE)
        _editorConfig.value = EditorConfig(
            wordWrap = prefs.getBoolean(KEY_WORD_WRAP, false),
            showWhitespace = prefs.getBoolean(KEY_SHOW_WHITESPACE, false),
            fontSize = prefs.getInt(KEY_FONT_SIZE, 14),
            tabSize = prefs.getInt(KEY_TAB_SIZE, 4),
        )
    }

    fun updateEditor(config: EditorConfig, context: Context) {
        context.getSharedPreferences(EDITOR_PREF, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_WORD_WRAP, config.wordWrap)
            .putBoolean(KEY_SHOW_WHITESPACE, config.showWhitespace)
            .putInt(KEY_FONT_SIZE, config.fontSize)
            .putInt(KEY_TAB_SIZE, config.tabSize)
            .apply()
        _editorConfig.value = config
    }
}
