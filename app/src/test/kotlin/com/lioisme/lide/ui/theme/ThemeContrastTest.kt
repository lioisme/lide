package com.lioisme.lide.ui.theme

import com.lioisme.lide.editor.EditorPalette
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.utils.ColorUtils
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主题对比度门禁（2026-08-12 T-2 整改配套，G4 红线的宿主侧扩展）。
 *
 * 生产实现 [Theme.kt] 的 [SchemeTonalSpot]（Google material-color-utilities
 * Kotlin 移植，Compose dynamicColorScheme 同款算法）为确定性纯计算、无平台
 * 差异，测试直接以同款调用构造 scheme，断言全部「文本承载」token 对满足
 * WCAG 2.x 相对亮度 AA 严格档 ≥ 4.5:1。
 *
 * 覆盖：
 * - 标准 M3 token 对（onPrimary/primary 等，含 container 系、inverse 系）；
 * - 编辑器 chrome 槽组合（EditorTheme.rememberHostEditorColors 映射镜像）：
 *   行号文字 vs 文本区背景（TextMate 亮/暗背景，EditorPaletteContrastTest
 *   守护 JSON 一致性）、浮层文字 vs surfaceContainerHighest（补全窗/tooltip
 *   三层递进契约）。
 *
 * seed 集单一事实源 = [ThemeSeeds.OPTIONS]（与 SettingsScreen 色板同源）。
 */
class ThemeContrastTest {

    private fun schemeFor(
        seed: Long,
        isDark: Boolean,
    ): SchemeTonalSpot =
        SchemeTonalSpot(
            sourceColorHct = Hct.fromInt(seed.toInt()),
            isDark = isDark,
            contrastLevel = 0.0,
        )

    private fun contrastRatio(fg: Int, bg: Int): Double {
        val l1 = ColorUtils.calculateLuminance(fg)
        val l2 = ColorUtils.calculateLuminance(bg)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** 与 Theme.kt generateScheme 的 token 槽位一一对应（文本承载对）。 */
    private fun textTokenPairs(s: SchemeTonalSpot): List<Pair<String, Pair<Int, Int>>> =
        listOf(
            "onPrimary/primary" to (s.onPrimary to s.primary),
            "onSecondary/secondary" to (s.onSecondary to s.secondary),
            "onTertiary/tertiary" to (s.onTertiary to s.tertiary),
            "onError/error" to (s.onError to s.error),
            "onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
            "onSecondaryContainer/secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
            "onTertiaryContainer/tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
            "onErrorContainer/errorContainer" to (s.onErrorContainer to s.errorContainer),
            "onBackground/background" to (s.onBackground to s.background),
            "onSurface/surface" to (s.onSurface to s.surface),
            "onSurfaceVariant/surfaceVariant" to (s.onSurfaceVariant to s.surfaceVariant),
            "onSurfaceVariant/surface" to (s.onSurfaceVariant to s.surface),
            "onSurfaceVariant/surfaceContainerHighest" to (s.onSurfaceVariant to s.surfaceContainerHighest),
            "inverseOnSurface/inverseSurface" to (s.inverseOnSurface to s.inverseSurface),
            "inversePrimary/inverseSurface" to (s.inversePrimary to s.inverseSurface),
            "onSurface/surfaceContainerLowest" to (s.onSurface to s.surfaceContainerLowest),
            "onSurface/surfaceContainerHighest" to (s.onSurface to s.surfaceContainerHighest),
        )

    @Test
    fun themeTokensMeetAaForAllPresets() {
        for (seed in ThemeSeeds.OPTIONS) {
            for (isDark in listOf(false, true)) {
                val s = schemeFor(seed, isDark)
                for ((name, pair) in textTokenPairs(s)) {
                    val ratio = contrastRatio(pair.first, pair.second)
                    assertTrue(
                        "seed=0x%08X dark=$isDark $name ratio=$ratio 低于 AA 严格档 4.5:1".format(seed),
                        ratio >= 4.5,
                    )
                }
            }
        }
    }

    @Test
    fun chromeSlotsMeetAaForAllPresets() {
        // 文本区背景 = TextMate 主题背景（EditorPalette.schemeBackgrounds，
        // 与 assets/textmate JSON 同源，一致性由 EditorPaletteContrastTest 守护）——
        // 行号等 chrome 文字槽随可选配色方案（T10）组合全部校验。
        for ((lightBg, darkBg) in EditorPalette.schemeBackgrounds) {
            for (seed in ThemeSeeds.OPTIONS) {
                for (isDark in listOf(false, true)) {
                    val s = schemeFor(seed, isDark)
                    val lineBg = if (isDark) darkBg else lightBg
                    val float = s.surfaceContainerHighest
                    val pairs =
                        listOf(
                            "lineNumber/editorBg" to (s.onSurfaceVariant to lineBg),
                            "completionText/float" to (s.onSurface to float),
                            "completionMatched/float" to (s.primary to float),
                            "tooltipBrief/float" to (s.onSurface to float),
                            "tooltipDetailed/float" to (s.onSurfaceVariant to float),
                            "tooltipAction/float" to (s.primary to float),
                        )
                    for ((name, pair) in pairs) {
                        val ratio = contrastRatio(pair.first, pair.second)
                        assertTrue(
                            "chrome seed=0x%08X dark=$isDark $name ratio=$ratio 低于 AA 严格档 4.5:1".format(seed),
                            ratio >= 4.5,
                        )
                    }
                }
            }
        }
    }
}
