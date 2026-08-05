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

    private val _themeConfig = MutableStateFlow(ThemeConfig())
    val themeConfig: StateFlow<ThemeConfig> = _themeConfig.asStateFlow()

    // === Editor Settings (D1：21 项全集，键名 ↔ EditorConfig 字段) ===
    private const val EDITOR_PREF = "lide_editor"
    private const val KEY_FONT_SIZE = "font_size"
    private const val KEY_TAB_SIZE = "tab_size"
    private const val KEY_USE_TAB = "use_tab"
    private const val KEY_AUTO_INDENT = "auto_indent"
    private const val KEY_AUTO_CLOSE_BRACKETS = "auto_close_brackets"
    private const val KEY_AUTO_COMPLETION = "auto_completion"
    private const val KEY_IDENTIFIER_COMPLETION = "identifier_completion"
    private const val KEY_LEXICAL_DIAGNOSTICS = "lexical_diagnostics"
    private const val KEY_READ_ONLY = "read_only"
    private const val KEY_WORD_WRAP = "word_wrap"
    private const val KEY_LINE_NUMBERS = "line_numbers"
    private const val KEY_HIGHLIGHT_CURRENT_LINE = "highlight_current_line"
    private const val KEY_HIGHLIGHT_BRACKET_PAIR = "highlight_bracket_pair"
    private const val KEY_HIGHLIGHT_CURRENT_BLOCK = "highlight_current_block"
    private const val KEY_MINIMAP = "minimap"
    private const val KEY_STICKY_SCROLL = "sticky_scroll"
    private const val KEY_LN_PANEL = "ln_panel"
    private const val KEY_SHOW_WHITESPACE = "show_whitespace"
    private const val KEY_SCALABLE = "scalable"
    private const val KEY_CURSOR_ANIMATION = "cursor_animation"
    private const val KEY_DISABLE_SOFT_KBD_IF_HARD = "disable_soft_kbd_if_hard_kbd"

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

    fun initEditor(context: Context) {
        val prefs = context.getSharedPreferences(EDITOR_PREF, Context.MODE_PRIVATE)
        _editorConfig.value =
            EditorConfig(
                fontSize = prefs.getInt(KEY_FONT_SIZE, 14),
                tabSize = prefs.getInt(KEY_TAB_SIZE, 4),
                useTab = prefs.getBoolean(KEY_USE_TAB, true),
                autoIndent = prefs.getBoolean(KEY_AUTO_INDENT, true),
                autoCloseBrackets = prefs.getBoolean(KEY_AUTO_CLOSE_BRACKETS, true),
                autoCompletion = prefs.getBoolean(KEY_AUTO_COMPLETION, true),
                identifierCompletion = prefs.getBoolean(KEY_IDENTIFIER_COMPLETION, false),
                lexicalDiagnostics = prefs.getBoolean(KEY_LEXICAL_DIAGNOSTICS, true),
                readOnly = prefs.getBoolean(KEY_READ_ONLY, false),
                wordWrap = prefs.getBoolean(KEY_WORD_WRAP, false),
                lineNumbers = prefs.getBoolean(KEY_LINE_NUMBERS, true),
                highlightCurrentLine = prefs.getBoolean(KEY_HIGHLIGHT_CURRENT_LINE, true),
                highlightBracketPair = prefs.getBoolean(KEY_HIGHLIGHT_BRACKET_PAIR, true),
                highlightCurrentBlock = prefs.getBoolean(KEY_HIGHLIGHT_CURRENT_BLOCK, true),
                minimap = prefs.getBoolean(KEY_MINIMAP, false),
                stickyScroll = prefs.getBoolean(KEY_STICKY_SCROLL, false),
                lnPanel = prefs.getBoolean(KEY_LN_PANEL, false),
                showWhitespace = prefs.getBoolean(KEY_SHOW_WHITESPACE, false),
                scalable = prefs.getBoolean(KEY_SCALABLE, true),
                cursorAnimation = prefs.getBoolean(KEY_CURSOR_ANIMATION, false),
                disableSoftKbdIfHardKbd = prefs.getBoolean(KEY_DISABLE_SOFT_KBD_IF_HARD, true),
            )
    }

    fun updateEditor(
        config: EditorConfig,
        context: Context,
    ) {
        context
            .getSharedPreferences(EDITOR_PREF, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_FONT_SIZE, config.fontSize)
            .putInt(KEY_TAB_SIZE, config.tabSize)
            .putBoolean(KEY_USE_TAB, config.useTab)
            .putBoolean(KEY_AUTO_INDENT, config.autoIndent)
            .putBoolean(KEY_AUTO_CLOSE_BRACKETS, config.autoCloseBrackets)
            .putBoolean(KEY_AUTO_COMPLETION, config.autoCompletion)
            .putBoolean(KEY_IDENTIFIER_COMPLETION, config.identifierCompletion)
            .putBoolean(KEY_LEXICAL_DIAGNOSTICS, config.lexicalDiagnostics)
            .putBoolean(KEY_READ_ONLY, config.readOnly)
            .putBoolean(KEY_WORD_WRAP, config.wordWrap)
            .putBoolean(KEY_LINE_NUMBERS, config.lineNumbers)
            .putBoolean(KEY_HIGHLIGHT_CURRENT_LINE, config.highlightCurrentLine)
            .putBoolean(KEY_HIGHLIGHT_BRACKET_PAIR, config.highlightBracketPair)
            .putBoolean(KEY_HIGHLIGHT_CURRENT_BLOCK, config.highlightCurrentBlock)
            .putBoolean(KEY_MINIMAP, config.minimap)
            .putBoolean(KEY_STICKY_SCROLL, config.stickyScroll)
            .putBoolean(KEY_LN_PANEL, config.lnPanel)
            .putBoolean(KEY_SHOW_WHITESPACE, config.showWhitespace)
            .putBoolean(KEY_SCALABLE, config.scalable)
            .putBoolean(KEY_CURSOR_ANIMATION, config.cursorAnimation)
            .putBoolean(KEY_DISABLE_SOFT_KBD_IF_HARD, config.disableSoftKbdIfHardKbd)
            .apply()
        _editorConfig.value = config
    }
}
