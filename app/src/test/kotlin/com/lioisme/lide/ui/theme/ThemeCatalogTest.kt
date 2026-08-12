package com.lioisme.lide.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ThemeCatalog 纯函数测试（P2，2026-08-11）：别名映射与下拉选项装配。
 * 反射路径（[ThemeCatalog.reflectedThemeNames]，android.R.style）依赖 Android
 * 运行时，不在 JVM 单测覆盖；options 的回落路径（空表 → FALLBACK_NAMES）可测。
 *
 * 2026-08-12（DEAD_CODE_CLEANUP T5）：resolveDark / ThemeOption.dark 深色推断轴
 * 已删除（WP-014 反转后的测试残留，生产零消费），对应用例移除。
 */
class ThemeCatalogTest {

    @Test
    fun `options fallback list covers common aliases with labels`() {
        val options = ThemeCatalog.options(emptyList())
        assertTrue(options.size >= 10)
        // FALLBACK_NAMES 内的常见主题都应带别名文案（labelRes 非空）。
        val common = options.filter { it.labelRes != null }
        assertTrue(common.size == ThemeCatalog.COMMON_ALIASES.size)
    }

    @Test
    fun `options preserves unknown reflected names as raw`() {
        val options = ThemeCatalog.options(listOf("Theme_Weird_Future"))
        assertEquals(1, options.size)
        assertEquals("Theme_Weird_Future", options[0].value)
        assertNull(options[0].labelRes)
    }

    @Test
    fun `options maps common alias to label`() {
        val options = ThemeCatalog.options(listOf("Theme_DeviceDefault"))
        assertEquals(1, options.size)
        assertEquals("Theme_DeviceDefault", options[0].value)
        assertTrue(options[0].labelRes != null)
    }
}