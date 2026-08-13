package com.lioisme.lide.editor

/**
 * 编辑器主题库单一事实源（2026-08-13 用户拍板：编辑器高亮与 Squircle CE 完全一致）。
 *
 * 决策链：
 * - 2026-08-12：配色方案可选 + 双模配对（Squircle / Terminus 同款方案选择器），
 *   token 对比度 ≥ 4.5:1 门禁。
 * - 2026-08-13（本轮）：语法文件、高亮配色全部对齐 Squircle CE 基线——主题层直接
 *   引入 Squircle 9 款配色（VS Code 格式 → TextMate settings 格式，一次性转换脚本
 *   `scripts/convert-squircle-themes.ps1`）；「与源 JSON 一致」取代逐 token 对比度
 *   断言成为新门禁（[EditorPaletteContrastTest]），宿主 chrome 槽门禁
 *   （[com.lioisme.lide.ui.theme.ThemeContrastTest]）保留。
 *
 * 激活机制（同步拍板反转）：
 * - 编辑器文本区主题 = **手选单侧主题**（9 款直接选择，Squircle 同款交互），
 *   不再「方案 + 宿主明暗」双模跟随；
 * - [EditorConfig.themeScheme] 语义 = 0..8 主题索引（持久化键名 `editor_scheme`
 *   不变；旧值 0..2 在新表下的语义迁移记录于 EditorConfig）；
 * - 默认激活 = 0（darcula，Squircle 默认主题）。
 *
 * 消费方：
 * - [TextMateRuntime] 按 [allThemes] 加载全部主题 JSON（assets/textmate/<名>.json），
 *   [themeNameFor] 为激活装配唯一入口；
 * - [LuaEditor] / [LideApp] 经 [themeNameFor] 装配当前主题；
 * - [EditorSettingsScreen] 主题选择面板展示 [themeCount] 款 + [themeBackgrounds]
 *   单背景预览；（[themeNameFor] 越界安全回落，非法值永不崩溃）。
 */
object EditorPalette {
    /** 主题文件名常量（= assets/textmate/<名>.json；索引 = [themes] 下表）。 */
    const val DARCULA = "darcula"
    const val ECLIPSE = "eclipse"
    const val INTELLIJ_LIGHT = "intellij_light"
    const val LADIES_NIGHT = "ladies_night"
    const val MONOKAI = "monokai"
    const val OBSIDIAN = "obsidian"
    const val SOLARIZED_LIGHT = "solarized_light"
    const val TOMORROW_NIGHT = "tomorrow_night"
    const val VISUAL_STUDIO = "visual_studio"

    /** 默认激活主题索引（= darcula，Squircle 默认），EditorConfig 初值。 */
    const val THEME_DEFAULT_INDEX = 0

    /** 主题条目：名 + 明暗（明暗仅用于设置页预览标识与宿主 chrome 组合校验；
     * 实际文本区装配直接按主题 JSON 自身值。）。 */
    data class ThemeEntry(
        val name: String,
        val isDark: Boolean,
    )

    /** 全部可选主题（Squircle feature-themes 全量，索引 = EditorConfig.themeScheme
     * 取值；[THEME_DEFAULT_INDEX] 为默认）。顺序 = 转换脚本输入目录字母序，
     * 与 Squircle 上游一致。 */
    val themes: List<ThemeEntry> =
        listOf(
            ThemeEntry(DARCULA, isDark = true),
            ThemeEntry(ECLIPSE, isDark = false),
            ThemeEntry(INTELLIJ_LIGHT, isDark = false),
            ThemeEntry(LADIES_NIGHT, isDark = true),
            ThemeEntry(MONOKAI, isDark = true),
            ThemeEntry(OBSIDIAN, isDark = true),
            ThemeEntry(SOLARIZED_LIGHT, isDark = false),
            ThemeEntry(TOMORROW_NIGHT, isDark = true),
            ThemeEntry(VISUAL_STUDIO, isDark = true),
        )

    /** 全部主题名（TextMate 加载白名单 = 本表，禁止旁路加主题；与
     * assets/textmate 目录 + 转换脚本输出集一一对应）。 */
    val allThemes: List<String> = themes.map { it.name }

    /** 主题数（= 选择面板可选项数）。 */
    val themeCount: Int = themes.size

    /** 兼容别名：旧调用方读主题列表（供 TextMateRuntime 全量加载）。 */
    val themeFiles: List<String>
        get() = allThemes

    /** 各主题背景色（ARGB，供设置页预览色条 + 宿主 chrome 对比度门禁组合；
     * 与 assets/textmate/<名>.json 的 settings[0].settings.background 同源，
     * 一致性由 EditorPaletteContrastTest 逐主题断言守护——本表是静态镜像，
     * JSON 是运行期事实源。）。 */
    val themeBackgrounds: List<Int> =
        listOf(
            0xFF1E1F22.toInt(), // darcula
            0xFFFFFFFF.toInt(), // eclipse
            0xFFFFFFFF.toInt(), // intellij_light
            0xFF21282D.toInt(), // ladies_night
            0xFF1D1E19.toInt(), // monokai
            0xFF23282A.toInt(), // obsidian
            0xFFFCF6E5.toInt(), // solarized_light
            0xFF1D1F21.toInt(), // tomorrow_night
            0xFF1E1E1E.toInt(), // visual_studio
        )

    /** 各主题行号文字色（ARGB，与 settings[0].settings.lineNumber 同源；2026-08-13
     * 起行号文字随主题（Squircle 行为），替代旧「宿主 M3 行号」——亮主题 × 暗宿主
     * 组合下行号不可读的集成缺陷的根治。一致性由 EditorPaletteContrastTest 守护。 */
    val themeLineNumbers: List<Int> =
        listOf(
            0xFF616366.toInt(), // darcula
            0xFFADADAD.toInt(), // eclipse
            0xFFADADAD.toInt(), // intellij_light
            0xFF859599.toInt(), // ladies_night
            0xFF575850.toInt(), // monokai
            0xFF859599.toInt(), // obsidian
            0xFFA5ADAB.toInt(), // solarized_light
            0xFF858585.toInt(), // tomorrow_night
            0xFF6F7680.toInt(), // visual_studio
        )

    /** 各主题当前行行号文字色（ARGB，与 settings[0].settings.lineNumberActiveForeground
     * 同源，EditorPaletteContrastTest 守护）。 */
    val themeLineNumbersActive: List<Int> =
        listOf(
            0xFFA4A3A3.toInt(), // darcula
            0xFF828282.toInt(), // eclipse
            0xFF828282.toInt(), // intellij_light
            0xFFE0E2E4.toInt(), // ladies_night
            0xFFC0C1B6.toInt(), // monokai
            0xFFC6C6C6.toInt(), // obsidian
            0xFF858A84.toInt(), // solarized_light
            0xFFFFFFFF.toInt(), // tomorrow_night
            0xFFCCCCCC.toInt(), // visual_studio
        )

    /** 按索引取激活主题名（编辑器文本区装配唯一入口；越界安全回落）。 */
    fun themeNameFor(index: Int): String {
        val i = index.coerceIn(0, themes.lastIndex)
        return themes[i].name
    }

    /** 按索引判定主题明暗（设置页展示 / 宿主 chrome 组合校验用）。 */
    fun isDarkTheme(index: Int): Boolean {
        val i = index.coerceIn(0, themes.lastIndex)
        return themes[i].isDark
    }
}