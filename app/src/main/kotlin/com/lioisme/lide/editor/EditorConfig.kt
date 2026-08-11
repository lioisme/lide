package com.lioisme.lide.editor

/**
 * 编辑器视图配置（由宿主注入的纯值对象）。
 *
 * 与宿主的持久化设置解耦：本模块只定义渲染与语言装配所需的纯值，
 * 持久化、默认值来源等属于 `:app` 的 [com.lioisme.lide.ui.theme.SettingsManager] 职责。
 *
 * 2026-08-11 设置瘦身（P4，对标 Squircle）：以下「最佳配置」不再暴露为设置，
 * 由 [LuaEditor] 以常量落实——undo 栈容量（500，大文件 cap 200）、粘性滚动保留行数（3）、
 * 行号面板（关）、固定行号（关）、行号分割线（恒 1dp + 主题色）、光标动画（关）、
 * 行高倍率（1.0）。Squircle 同款语义（view/CodeEditor.kt 硬编码常量）。
 *
 * 字段与 sora 原生 API 映射全表见任务单 EDITOR_FEATURE_COMPLETION_WORK_ORDER §7：
 * - 语言级设置（useTab / autoIndent / autoCloseBrackets / autoCompletion /
 *   identifierCompletion / tabSize）经 [EditorLanguage.configure] 一次应用；
 * - 视图级设置经 [LuaEditor] 守卫式 diff 同步（update 块，值不变不赋值）；
 * - 大文件（>200KB）降级矩阵由 [LuaEditor] 强制执行，与字段值无关。
 *
 * 性能红线：全部字段为纯值；读取不触发任何 sora 内部重排（赋值才触发）。
 */
data class EditorConfig(
    // === 通用 ===
    val fontSize: Int = 14,
    val tabSize: Int = 4,
    val useTab: Boolean = true,
    // T5：字体族（[FONT_FAMILY_*] 常量 id；等宽族用于代码文本与行号）。
    val fontFamily: Int = FONT_FAMILY_DEFAULT,
    // === 编辑 ===
    val autoIndent: Boolean = true,
    val autoCloseBrackets: Boolean = true,
    val autoCompletion: Boolean = true,
    val identifierCompletion: Boolean = false,
    val lexicalDiagnostics: Boolean = true,
    val readOnly: Boolean = false,
    // === 视图 ===
    val wordWrap: Boolean = false,
    val lineNumbers: Boolean = true,
    val highlightCurrentLine: Boolean = true,
    val highlightBracketPair: Boolean = true,
    val highlightCurrentBlock: Boolean = true,
    val minimap: Boolean = false,
    val stickyScroll: Boolean = false,
    // === 输入 ===
    val showWhitespace: Boolean = false,
    val scalable: Boolean = true,
    val disableSoftKbdIfHardKbd: Boolean = true,
) {
    companion object {
        /** 字体族：默认（2026-08-11 起 = Squircle CE 组合 [FONT_FAMILY_MONOSPACE]，
         * 见 LuaEditor.fontFamilyTypeface——Squircle `TypefaceProvider.DEFAULT` 语义）。 */
        const val FONT_FAMILY_DEFAULT = 0
        /** 字体族：等宽（monospace）。 */
        const val FONT_FAMILY_MONOSPACE = 1
        /** 字体族：无衬线（sans-serif）。 */
        const val FONT_FAMILY_SANS = 2
        /** 字体族：衬线（serif）。 */
        const val FONT_FAMILY_SERIF = 3
    }
}
