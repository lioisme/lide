package com.lioisme.lide.editor

/**
 * 编辑器主题库单一事实源（2026-08-13 用户第三次拍板：恢复「适配全局外观系统」）。
 *
 * 决策链：
 * - 2026-08-12：配色方案可选 + 双模配对（Squircle / Terminus 同款方案选择器），
 *   token 对比度 ≥ 4.5:1 门禁。
 * - 2026-08-13（上午）：改为 Squircle CE 9 款单侧主题手选（T1-T6），
 *   「与 Squircle 源 JSON 完全一致」取代逐 token 对比度成为新门禁
 *   （[EditorPaletteContrastTest]）。
 * - 2026-08-13（下午，本轮拍板）：恢复「适配全局外观系统」——一套方案 = 亮/暗
 *   双模对（宿主明暗自动切换对应侧，替代单侧手选）；**语法高亮与 Squircle CE
 *   保持 100% 一致**（磁盘 9 款 Squircle JSON 全量保留、TextMate 加载白名单不变）。
 *
 * 双模配对（用户拍板方案）：3 对恢复 T19 选择器——
 * - Classic（默认）：IntelliJ 谱系 = 亮 intellij_light / 暗 darcula；
 * - Solarized：亮 solarized_light / 暗 obsidian；
 * - Eclipse：亮 eclipse / 暗 visual_studio。
 *
 * 激活机制：
 * - 主题 = 「方案索引 + 宿主明暗」（[EditorConfig.themeScheme] 0..2 方案索引，
 *   持久化键名 `editor_scheme` 不变；默认 [SCHEME_CLASSIC]）；
 * - [themeNameFor] 为激活装配唯一入口（越界安全回落，非法值永不崩溃）；
 * - 宿主明暗输入源 = 宿主外观系统（LideApp 系统明暗预热 + LuaEditor
 *   `LocalIsDarkTheme` 注入；R4：运行时不读宿主状态）。
 *
 * 消费方：
 * - [TextMateRuntime] 按 [allThemes] 加载全部主题 JSON（assets/textmate/<名>.json），
 *   [themeNameFor] 为激活装配唯一入口；
 * - [LuaEditor] / [LideApp] 经 [themeNameFor(scheme, isDark)] 装配当前主题；
 * - [EditorSettingsScreen] 方案选择面板展示 [schemeCount] 套方案 + [schemeBackgrounds]
 *   双模预览色条。
 */
object EditorPalette {
    /** 主题文件名常量（= assets/textmate/<名>.json；Squircle feature-themes 全量）。 */
    const val DARCULA = "darcula"
    const val ECLIPSE = "eclipse"
    const val INTELLIJ_LIGHT = "intellij_light"
    const val LADIES_NIGHT = "ladies_night"
    const val MONOKAI = "monokai"
    const val OBSIDIAN = "obsidian"
    const val SOLARIZED_LIGHT = "solarized_light"
    const val TOMORROW_NIGHT = "tomorrow_night"
    const val VISUAL_STUDIO = "visual_studio"

    /** 方案索引（EditorConfig.themeScheme 取值；索引 = [pairs] 下表）。 */
    const val SCHEME_CLASSIC = 0
    const val SCHEME_SOLARIZED = 1
    const val SCHEME_ECLIPSE = 2

    /** 默认激活方案索引（= Classic），EditorConfig 初值。 */
    const val THEME_DEFAULT_INDEX = SCHEME_CLASSIC

    /** 主题条目：名 + 明暗（Squircle 源声明；用于加载面与静态镜像表）。 */
    data class ThemeEntry(
        val name: String,
        val isDark: Boolean,
    )

    /** Squircle feature-themes 全量主题（顺序 = 转换脚本输入目录字母序，
     * 与 Squircle 上游一致；TextMate 加载白名单 = 本表，禁止旁路加主题）。
     * 实际文本区装配按 [pairs] 侧选择，此处仅承载 9 款 JSON 全量。 */
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

    /**
     * 双模方案配对表：每套方案 = (亮侧主题名, 暗侧主题名)，宿主明暗自动换侧
     * （2026-08-13 拍板；配对 = Classic IntelliJ 谱系 / Solarized / Eclipse）。
     */
    val pairs: List<Pair<String, String>> =
        listOf(
            INTELLIJ_LIGHT to DARCULA,
            SOLARIZED_LIGHT to OBSIDIAN,
            ECLIPSE to VISUAL_STUDIO,
        )

    /** 全部主题名（TextMate 加载白名单 = Squircle 源 JSON 全集，与
     * assets/textmate 目录 + 转换脚本输出集一一对应；EditorPaletteContrastTest 守护）。 */
    val allThemes: List<String> = themes.map { it.name }

    /** 兼容别名：旧调用方读主题列表（供 TextMateRuntime 全量加载）。 */
    val themeFiles: List<String>
        get() = allThemes

    /** 方案数（= 选择面板可选项数；pairs 与 scheme* 表必须同长，测试守护）。 */
    val schemeCount: Int = pairs.size

    /** 各主题背景色（ARGB，供 9 款静态镜像断言 + [schemeBackgrounds] 派生；
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

    /** 各主题行号文字色（ARGB，与 settings[0].settings.lineNumber 同源；行号随
     * 主题（Squircle 行为），替代旧「宿主 M3 行号」。一致性由
     * EditorPaletteContrastTest 守护）。 */
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

    /** 各方案亮/暗侧背景色（ARGB，设置页双模预览色条用；派生自
     * [themeBackgrounds] 按 [pairs] 名查找，同一静态镜像源）。 */
    val schemeBackgrounds: List<Pair<Int, Int>> =
        pairs.map { (light, dark) -> themeBackgrounds[indexOfName(light)] to themeBackgrounds[indexOfName(dark)] }

    /** 各方案亮/暗侧行号文字色（派生自 [themeLineNumbers] 按 [pairs] 名查找）。 */
    val schemeLineNumbers: List<Pair<Int, Int>> =
        pairs.map { (light, dark) -> themeLineNumbers[indexOfName(light)] to themeLineNumbers[indexOfName(dark)] }

    /** 各方案亮/暗侧当前行行号文字色（派生自 [themeLineNumbersActive]）。 */
    val schemeLineNumbersActive: List<Pair<Int, Int>> =
        pairs.map { (light, dark) -> themeLineNumbersActive[indexOfName(light)] to themeLineNumbersActive[indexOfName(dark)] }

    /** 按「方案索引 + 宿主明暗」取激活主题名（编辑器文本区装配唯一入口；
     * 索引越界安全回落，非法值永不崩溃）。 */
    fun themeNameFor(
        scheme: Int,
        isDark: Boolean,
    ): String {
        val pair = pairs[scheme.coerceIn(0, pairs.lastIndex)]
        return if (isDark) pair.second else pair.first
    }

    /** 按「方案索引 + 宿主明暗」取激活主题行号色对（行号随主题侧，
     * LuaEditor themedHostColors 用）。 */
    fun lineNumberPairFor(
        scheme: Int,
        isDark: Boolean,
    ): Pair<Int, Int> {
        val pair = schemeLineNumbers[scheme.coerceIn(0, schemeLineNumbers.lastIndex)]
        val active = schemeLineNumbersActive[scheme.coerceIn(0, schemeLineNumbersActive.lastIndex)]
        return if (isDark) {
            pair.second to active.second
        } else {
            pair.first to active.first
        }
    }

    private fun indexOfName(name: String): Int {
        val i = themes.indexOfFirst { it.name == name }
        check(i >= 0) { "EditorPalette: 主题名 $name 不在 themes 表（pairs 必须引用 9 款全量之一）" }
        return i
    }
}