package com.lioisme.lide.editor

/**
 * 编辑器双模配色库单一事实源（2026-08-12 治理红线）。
 *
 * 需求：编辑器配色支持多套科学、热门、合理的方案；每套主题必须同时适配
 * 夜间/日间（亮暗双模配对）；全部 token 对比度 ≥ 4.5:1（WCAG AA 严格档，
 * 由 EditorPaletteContrastTest 门禁守护）。
 *
 * 消费方：
 * - [TextMateRuntime] 按 [allThemes] 加载全部主题 JSON（assets/textmate/<名>.json）；
 * - [LuaEditor] 经 [themeNameFor] 取当前明暗的主题名装配；
 * - 未来扩展（系统高对比度无障碍联动等）只改激活配对表，编辑器侧零改动。
 */
object EditorPalette {

    /** LIDE Classic：VS Code Quiet Light / IntelliJ Darcula 谱系（默认激活配对）。 */
    const val CLASSIC_LIGHT = "quietlight"
    const val CLASSIC_DARK = "darcula"

    /** Solarized Dual：Ethan Schoonover Solarized 官方色板（科学配色典范），
     * 注释/次要色按 4.5:1 门禁提亮/加深（门禁适配）。 */
    const val SOLARIZED_LIGHT = "solarized_light"
    const val SOLARIZED_DARK = "solarized_dark"

    /** Tomorrow Dual：Chris Kempson Tomorrow 谱系（热门），明侧绿/橙按门禁加深。 */
    const val TOMORROW_LIGHT = "tomorrow"
    const val TOMORROW_DARK = "tomorrow_night"

    /** 激活主题配对：亮侧名 / 暗侧名（配对数 = 支持方案数；首对 = 默认激活）。
     * 2026-08-12 治理红线：每套主题必须同时适配日间/夜间（双模配对），
     * 禁止单侧主题入库。 */
    val pairs: List<Pair<String, String>> =
        listOf(
            CLASSIC_LIGHT to CLASSIC_DARK,
            SOLARIZED_LIGHT to SOLARIZED_DARK,
            TOMORROW_LIGHT to TOMORROW_DARK,
        )

    /** 全部激活主题名（TextMate 加载白名单 = 本表，禁止旁路加主题）。 */
    val allThemes: List<String> = pairs.flatMap { listOf(it.first, it.second) }

    /** 按宿主明暗选择激活配对的对应侧主题名（编辑器装配唯一入口）。 */
    fun themeNameFor(isDark: Boolean): String {
        val (light, dark) = pairs.first()
        return if (isDark) dark else light
    }
}
