package com.lioisme.lide.ui.theme

import android.content.SharedPreferences
import com.lioisme.lide.editor.EditorConfig

/**
 * 编辑器设置声明式键表（架构审计 B-4）。
 *
 * 21 个键的「常量 + initEditor 读取 + updateEditor 写入」三元组收敛为单一数据表：
 * 每个键只声明一次（存储名 + 默认值 + 读写访问器），加载与持久化按表驱动，
 * 加减键只改这一处。
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
            BoolKey("use_tab", true, { it.useTab }, { c, v -> c.copy(useTab = v) }),
            BoolKey("auto_indent", true, { it.autoIndent }, { c, v -> c.copy(autoIndent = v) }),
            BoolKey("auto_close_brackets", true, { it.autoCloseBrackets }, { c, v -> c.copy(autoCloseBrackets = v) }),
            BoolKey("auto_completion", true, { it.autoCompletion }, { c, v -> c.copy(autoCompletion = v) }),
            BoolKey("identifier_completion", false, { it.identifierCompletion }, { c, v -> c.copy(identifierCompletion = v) }),
            BoolKey("lexical_diagnostics", true, { it.lexicalDiagnostics }, { c, v -> c.copy(lexicalDiagnostics = v) }),
            BoolKey("read_only", false, { it.readOnly }, { c, v -> c.copy(readOnly = v) }),
            BoolKey("word_wrap", false, { it.wordWrap }, { c, v -> c.copy(wordWrap = v) }),
            BoolKey("line_numbers", true, { it.lineNumbers }, { c, v -> c.copy(lineNumbers = v) }),
            BoolKey("highlight_current_line", true, { it.highlightCurrentLine }, { c, v -> c.copy(highlightCurrentLine = v) }),
            BoolKey("highlight_bracket_pair", true, { it.highlightBracketPair }, { c, v -> c.copy(highlightBracketPair = v) }),
            BoolKey("highlight_current_block", true, { it.highlightCurrentBlock }, { c, v -> c.copy(highlightCurrentBlock = v) }),
            BoolKey("minimap", false, { it.minimap }, { c, v -> c.copy(minimap = v) }),
            BoolKey("sticky_scroll", false, { it.stickyScroll }, { c, v -> c.copy(stickyScroll = v) }),
            BoolKey("ln_panel", false, { it.lnPanel }, { c, v -> c.copy(lnPanel = v) }),
            BoolKey("show_whitespace", false, { it.showWhitespace }, { c, v -> c.copy(showWhitespace = v) }),
            BoolKey("scalable", true, { it.scalable }, { c, v -> c.copy(scalable = v) }),
            BoolKey("cursor_animation", false, { it.cursorAnimation }, { c, v -> c.copy(cursorAnimation = v) }),
            BoolKey("disable_soft_kbd_if_hard_kbd", true, { it.disableSoftKbdIfHardKbd }, { c, v -> c.copy(disableSoftKbdIfHardKbd = v) }),
        )

    val intKeys: List<IntKey> =
        listOf(
            IntKey("font_size", 14, { it.fontSize }, { c, v -> c.copy(fontSize = v) }),
            IntKey("tab_size", 4, { it.tabSize }, { c, v -> c.copy(tabSize = v) }),
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
