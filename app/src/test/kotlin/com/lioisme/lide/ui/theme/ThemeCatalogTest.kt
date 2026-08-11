package com.lioisme.lide.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ThemeCatalog 纯函数测试（P2，2026-08-11）：工程主题深色推断与别名映射。
 * 反射路径（[ThemeCatalog.reflectedThemeNames]，android.R.style）依赖 Android
 * 运行时，不在 JVM 单测覆盖；options 的回落路径（空表 → FALLBACK_NAMES）可测。
 */
class ThemeCatalogTest {

    @Test
    fun `legacy dark and light values mapped directly`() {
        assertEquals(true, ThemeCatalog.resolveDark("dark"))
        assertEquals(false, ThemeCatalog.resolveDark("light"))
    }

    @Test
    fun `blank and unknown theme follow host`() {
        assertNull(ThemeCatalog.resolveDark(null))
        assertNull(ThemeCatalog.resolveDark(""))
        assertNull(ThemeCatalog.resolveDark("   "))
        assertNull(ThemeCatalog.resolveDark("SomeUnknown"))
    }

    @Test
    fun `common alias dark verdicts consistent with dropdown options`() {
        val options = ThemeCatalog.options(emptyList())
        for (option in options) {
            val alias = option.dark
            assertEquals(
                "alias[$option.value] 与 resolveDark 不一致",
                alias,
                ThemeCatalog.resolveDark(option.value),
            )
        }
        // 已知方向的抽验。
        assertEquals(true, ThemeCatalog.resolveDark("Theme_DeviceDefault"))
        assertEquals(false, ThemeCatalog.resolveDark("Theme_DeviceDefault_Light"))
        assertEquals(true, ThemeCatalog.resolveDark("Theme_Black"))
        assertEquals(null, ThemeCatalog.resolveDark("Theme_DeviceDefault_DayNight"))
    }

    @Test
    fun `heuristic handles unknown Theme names`() {
        assertFalse(ThemeCatalog.resolveDark("Theme_Custom_Light")!!)
        assertTrue(ThemeCatalog.resolveDark("Theme_Custom_Dark")!!)
        assertTrue(ThemeCatalog.resolveDark("Theme_Custom_Black")!!)
        assertTrue(ThemeCatalog.resolveDark("Theme_Custom_Night")!!)
        assertNull(ThemeCatalog.resolveDark("Theme_Custom_Plain"))
    }

    @Test
    fun `options fallback list covers common aliases with labels`() {
        val options = ThemeCatalog.options(emptyList())
        assertTrue(options.size >= 10)
        // FALLBACK_NAMES 内的常见主题都应带别名文案（labelRes 非空）。
        val common = options.filter { it.labelRes != null }
        assertTrue(common.size == ThemeCatalog.COMMON_ALIASES.size)
        assertTrue(options.any { it.value == "Theme_DeviceDefault_DayNight" && it.dark == null })
    }

    @Test
    fun `options preserves unknown reflected names as raw`() {
        val options = ThemeCatalog.options(listOf("Theme_Weird_Future"))
        assertEquals(1, options.size)
        assertEquals("Theme_Weird_Future", options[0].value)
        assertNull(options[0].labelRes)
        assertNull(options[0].dark)
    }
}
