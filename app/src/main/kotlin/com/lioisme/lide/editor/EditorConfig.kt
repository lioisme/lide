package com.lioisme.lide.editor

/**
 * 编辑器视图配置（由宿主注入的纯值对象）。
 *
 * 与宿主的持久化设置解耦：本模块只定义渲染与语言装配所需的纯值，
 * 持久化、默认值来源等属于 `:app` 的 [com.lioisme.lide.ui.theme.SettingsManager] 职责。
 *
 * 2026-08-11 设置瘦身（P4，对标 Squircle）：以下「最佳配置」不再暴露为设置，
 * 由 [LuaEditor] 以常量落实——undo 栈容量（500，大文件 cap 200）、粘性滚动保留行数（3）、
 * 行号分割线（恒 1dp + 主题色）、光标动画（关）、行高倍率（1.0）。
 * Squircle 同款语义（view/CodeEditor.kt 硬编码常量）。
 * 2026-08-12（EDITOR_LINENUM_FONT_FORMAT_FIX L1）：固定行号以新设置项恢复
 * （`fixedLineNumbers` + `fixed_line_numbers` 键，sora 原生 `setPinLineNumber`）；
 * 不等同 P4 已删的旧键 `pin_line_number`（历史引用不得回退）。
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
    // L3（2026-08-12 user 拍板）：默认缩进改空格（false），对齐 Squircle CE
    // `useSpacesInsteadOfTabs=true` 默认——格式化后缩进 = tabSize 个空格，
    // 退格**逐格**删除（视觉与字符级一致，消除「一次删 4 空格」困惑）；
    // 旧用户持久化 true 值不受影响（键已落盘优先）。
    val useTab: Boolean = false,
    // T5：字体族（[FONT_FAMILY_*] 常量 id；等宽族用于代码文本与行号）。
    val fontFamily: Int = FONT_FAMILY_DEFAULT,
    // T8（2026-08-12 方案可选反转）：配色方案索引（EditorPalette.SCHEME_*，
    // 亮/暗双模配对、明暗跟随宿主；默认 LIDE Classic）。
    val themeScheme: Int = EditorPalette.SCHEME_CLASSIC,
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
    // L1（2026-08-12）：固定行号（sora 原生 setPinLineNumber，水平滚动时行号栏
    // 固定于左侧）；默认开启对齐现代 IDE（VSCode/Android Studio）体验；
    // 行号关闭时无效（sora 语义），设置页联动置灰。
    val fixedLineNumbers: Boolean = true,
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
        /** 字体族：默认（2026-08-11 T10 起 = 内置 JetBrains Mono，Squircle CE 组合；
         * 经 [LuaFonts] 加载 assets/fonts/jetbrains_mono.ttf，失败回落 MONOSPACE）。 */
        const val FONT_FAMILY_DEFAULT = 0
        /** 字体族：系统等宽（L2 2026-08-12 起不再作为面板可选项，仅终极兜底
         * 与历史持久化兼容；面板 = 内置 + 导入，见 LuaFonts KDoc）。 */
        const val FONT_FAMILY_MONOSPACE = 1
        /** 字体族：系统无衬线（同 [FONT_FAMILY_MONOSPACE] 兜底语义）。 */
        const val FONT_FAMILY_SANS = 2
        /** 字体族：系统衬线（同 [FONT_FAMILY_MONOSPACE] 兜底语义）。 */
        const val FONT_FAMILY_SERIF = 3
        // T10（2026-08-11）：内置 6 款等宽（OFL，assets/fonts/，白名单见 AGENTS.md R5）。
        const val FONT_FAMILY_JETBRAINS_MONO = 4
        const val FONT_FAMILY_DROID_SANS_MONO = 5
        const val FONT_FAMILY_FIRA_CODE = 6
        const val FONT_FAMILY_SOURCE_CODE_PRO = 7
        const val FONT_FAMILY_ANONYMOUS_PRO = 8
        const val FONT_FAMILY_DEJAVU_SANS_MONO = 9
        /** 外部导入字体 id 基址（T11）：id = BASE + files/fonts 扫描序（按文件名排序）。 */
        const val FONT_FAMILY_EXTERNAL_BASE = 100
    }
}
