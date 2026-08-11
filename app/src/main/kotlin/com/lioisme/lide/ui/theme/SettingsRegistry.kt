package com.lioisme.lide.ui.theme

import android.content.SharedPreferences
import com.lioisme.lide.editor.EditorConfig
import com.lioisme.lide.editor.EditorPalette

/**
 * 编辑器设置声明式键表（架构审计 B-4）。
 *
 * 14 个键的「常量 + initEditor 读取 + updateEditor 写入」三元组收敛为单一数据表：
 * 每个键只声明一次（存储名 + 默认值 + 读写访问器），加载与持久化按表驱动，
 * 加减键只改这一处。
 *
 * 2026-08-11（P4 设置瘦身，对标 Squircle）：移除 7 项「最佳配置」——
 * undo_stack_size / sticky_scroll_max_lines / ln_panel / pin_line_number /
 * line_number_divider_width / cursor_animation / line_spacing_multiplier，
 * 由 LuaEditor 常量落实（见 EditorConfig KDoc）。
 * 2026-08-12（EDITOR_LINENUM_FONT_FORMAT_FIX L1）：固定行号以新键
 * `fixed_line_numbers` 重新落地为设置项（sora 原生 setPinLineNumber）；
 * 不复用已删除的旧键 `pin_line_number`（历史引用不得回退）。
 */
internal object SettingsRegistry {
    internal data class BoolKey(
        val name: String,
        val default: Boolean,
        val get: (EditorConfig) -> Boolean,
        val with: (EditorConfig, Boolean) -> EditorConfig,
    )

    internal data class IntKey(
        val name: String,
        val default: Int,
        val get: (EditorConfig) -> Int,
        val with: (EditorConfig, Int) -> EditorConfig,
    )

    /** 与 [EditorConfig] 字段一一对应；顺序无关（按表驱动读写）。 */
    val boolKeys: List<BoolKey> =
        listOf(
            BoolKey("use_tab", false, { it.useTab }, { c, v -> c.copy(useTab = v) }),
            BoolKey("auto_indent", true, { it.autoIndent }, { c, v -> c.copy(autoIndent = v) }),
            BoolKey("auto_close_brackets", true, { it.autoCloseBrackets }, { c, v -> c.copy(autoCloseBrackets = v) }),
            BoolKey("auto_completion", true, { it.autoCompletion }, { c, v -> c.copy(autoCompletion = v) }),
            BoolKey("identifier_completion", false, { it.identifierCompletion }, { c, v -> c.copy(identifierCompletion = v) }),
            BoolKey("lexical_diagnostics", true, { it.lexicalDiagnostics }, { c, v -> c.copy(lexicalDiagnostics = v) }),
            BoolKey("read_only", false, { it.readOnly }, { c, v -> c.copy(readOnly = v) }),
            BoolKey("word_wrap", false, { it.wordWrap }, { c, v -> c.copy(wordWrap = v) }),
            BoolKey("line_numbers", true, { it.lineNumbers }, { c, v -> c.copy(lineNumbers = v) }),
            // L1（2026-08-12）：固定行号（sora 原生 setPinLineNumber）；默认开。
            BoolKey("fixed_line_numbers", true, { it.fixedLineNumbers }, { c, v -> c.copy(fixedLineNumbers = v) }),
            BoolKey("highlight_current_line", true, { it.highlightCurrentLine }, { c, v -> c.copy(highlightCurrentLine = v) }),
            BoolKey("highlight_bracket_pair", true, { it.highlightBracketPair }, { c, v -> c.copy(highlightBracketPair = v) }),
            BoolKey("highlight_current_block", true, { it.highlightCurrentBlock }, { c, v -> c.copy(highlightCurrentBlock = v) }),
            BoolKey("minimap", false, { it.minimap }, { c, v -> c.copy(minimap = v) }),
            BoolKey("sticky_scroll", false, { it.stickyScroll }, { c, v -> c.copy(stickyScroll = v) }),
            BoolKey("show_whitespace", false, { it.showWhitespace }, { c, v -> c.copy(showWhitespace = v) }),
            BoolKey("scalable", true, { it.scalable }, { c, v -> c.copy(scalable = v) }),
            BoolKey("disable_soft_kbd_if_hard_kbd", true, { it.disableSoftKbdIfHardKbd }, { c, v -> c.copy(disableSoftKbdIfHardKbd = v) }),
        )

    val intKeys: List<IntKey> =
        listOf(
            IntKey("font_size", 14, { it.fontSize }, { c, v -> c.copy(fontSize = v) }),
            IntKey("tab_size", 4, { it.tabSize }, { c, v -> c.copy(tabSize = v) }),
            // T5（2026-08-10）：sora 上限扩展
            IntKey("font_family", EditorConfig.FONT_FAMILY_DEFAULT, { it.fontFamily }, { c, v -> c.copy(fontFamily = v) }),
            // T8（2026-08-12 方案可选反转）：配色方案索引；新键名 editor_scheme，
            // 不复用已废弃的 editor_theme（历史引用不得回退）。
            IntKey("editor_scheme", EditorPalette.SCHEME_CLASSIC, { it.themeScheme }, { c, v -> c.copy(themeScheme = v) }),
        )

    /** 从 [prefs] 加载 [EditorConfig]（缺失键回落默认值）。 */
    fun load(prefs: SharedPreferences): EditorConfig {
        var config = EditorConfig()
        for (key in boolKeys) {
            val v = prefs.getBoolean(key.name, key.default)
            config = key.with(config, v)
        }
        for (key in intKeys) {
            val v = prefs.getInt(key.name, key.default)
            config = key.with(config, v)
        }
        return config
    }

    /** 将 [config] 全部键写入 [editor]（apply 提交由调用方执行）。 */
    fun applyTo(
        editor: SharedPreferences.Editor,
        config: EditorConfig,
    ) {
        for (key in boolKeys) {
            editor.putBoolean(key.name, key.get(config))
        }
        for (key in intKeys) {
            editor.putInt(key.name, key.get(config))
        }
    }
}
