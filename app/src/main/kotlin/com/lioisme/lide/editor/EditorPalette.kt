package com.lioisme.lide.editor

/**
 * 编辑器双模配色库单一事实源（2026-08-12 用户拍板反转：方案可选、明暗双模跟随）。
 *
 * 需求：编辑器配色支持多套科学、热门、合理的方案；每套方案为**亮/暗双模配对**
 * （Squircle CE / Terminus 同款方案选择器）；明暗随宿主自动切换对应侧
 * （延续 pairs 结构）；全部 token 对比度 ≥ 4.5:1（WCAG AA 严格档，
 * 由 EditorPaletteContrastTest 门禁守护）。
 *
 * 消费方：
 * - [TextMateRuntime] 按 [allThemes] 加载全部主题 JSON（assets/textmate/<名>.json）；
 * - [LuaEditor] 经 [themeNameFor] 按「方案索引 + 宿主明暗」取主题名装配；
 * - [EditorSettingsScreen] 方案选择面板展示 [schemeCount] 套方案 + [schemeBackgrounds]
 *   双模预览色条；
 * - 未来扩展（系统高对比度无障碍联动等）只改激活配对表，编辑器侧零改动。
 */
object EditorPalette {
    /** LIDE Classic：VS Code Quiet Light / IntelliJ Darcula 谱系（默认激活方案）。 */
    const val CLASSIC_LIGHT = "quietlight"
    const val CLASSIC_DARK = "darcula"

    /** Solarized Dual：Ethan Schoonover Solarized 官方色板（科学配色典范），
     * 注释/次要色按 4.5:1 门禁提亮/加深（门禁适配）。 */
    const val SOLARIZED_LIGHT = "solarized_light"
    const val SOLARIZED_DARK = "solarized_dark"

    /** Tomorrow Dual：Chris Kempson Tomorrow 谱系（热门），明侧绿/橙按门禁加深。 */
    const val TOMORROW_LIGHT = "tomorrow"
    const val TOMORROW_DARK = "tomorrow_night"

    /** 方案索引（EditorConfig.themeScheme 取值；索引 = [pairs] 下表）。 */
    const val SCHEME_CLASSIC = 0
    const val SCHEME_SOLARIZED = 1
    const val SCHEME_TOMORROW = 2

    /** 激活主题配对：亮侧名 / 暗侧名（配对数 = 支持方案数；首对 = 默认激活）。
     * 2026-08-12 治理红线（用户拍板反转）：每套方案必须同时适配日间/夜间
     * （双模配对），禁止单侧主题入库。 */
    val pairs: List<Pair<String, String>> =
        listOf(
            CLASSIC_LIGHT to CLASSIC_DARK,
            SOLARIZED_LIGHT to SOLARIZED_DARK,
            TOMORROW_LIGHT to TOMORROW_DARK,
        )

    /** 全部激活主题名（TextMate 加载白名单 = 本表，禁止旁路加主题）。 */
    val allThemes: List<String> = pairs.flatMap { listOf(it.first, it.second) }

    /** 各方案亮/暗侧背景色（ARGB，与 assets/textmate/<名>.json 背景同源；
     * 方案选择面板双模预览色条用。一致性由 EditorPaletteContrastTest 守护）。 */
    val schemeBackgrounds: List<Pair<Int, Int>> =
        listOf(
            0xFFF5F5F5.toInt() to 0xFF242424.toInt(),
            0xFFFDF6E3.toInt() to 0xFF002B36.toInt(),
            0xFFFFFFFF.toInt() to 0xFF1D1F21.toInt(),
        )

    /** 方案数（pairs 与 schemeBackgrounds 必须同长，测试守护）。 */
    val schemeCount: Int = pairs.size

    /** 按「方案索引 + 宿主明暗」选择激活配对的对应侧主题名（编辑器装配唯一入口）。
     * 索引越界安全回落（coerce 到有效范围），非法值永不崩溃。 */
    fun themeNameFor(
        scheme: Int,
        isDark: Boolean,
    ): String {
        val index = scheme.coerceIn(0, pairs.lastIndex)
        val (light, dark) = pairs[index]
        return if (isDark) dark else light
    }
}
