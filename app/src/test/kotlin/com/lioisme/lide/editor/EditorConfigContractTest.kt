package com.lioisme.lide.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L1/L3（2026-08-12）EditorConfig 默认值契约锁：
 * - 固定行号默认开（fixedLineNumbers=true，sora 原生 setPinLineNumber 接线依据）；
 * - 默认缩进 = 空格（useTab=false，L3 用户拍板：格式化后退格逐格删除）；
 * - tabSize 默认 4（格式化空格缩进宽度 = 4）。
 * 改动默认值必须同步本测试与 SettingsRegistry 键默认。
 */
class EditorConfigContractTest {

    @Test
    fun `固定行号默认开启`() {
        assertTrue("fixedLineNumbers 默认必须为 true（对齐现代 IDE 体验）", EditorConfig().fixedLineNumbers)
    }

    @Test
    fun `默认缩进为空格且宽度 4`() {
        val config = EditorConfig()
        assertFalse("useTab 默认必须为 false（空格缩进，L3 用户拍板）", config.useTab)
        assertEquals(4, config.tabSize)
    }

    @Test
    fun `默认字体为内置 JetBrains Mono`() {
        assertEquals(EditorConfig.FONT_FAMILY_DEFAULT, EditorConfig().fontFamily)
        // DEFAULT 与 JETBRAINS_MONO 为同一资产别名（见 LuaFontsFallbackTest）
        assertTrue(
            "DEFAULT/4 必须为内置族（非系统族、非外部域）",
            EditorConfig.FONT_FAMILY_DEFAULT < 4 &&
                EditorConfig.FONT_FAMILY_JETBRAINS_MONO < EditorConfig.FONT_FAMILY_EXTERNAL_BASE,
        )
    }

    @Test
    fun `字体 id 拓扑无冲突`() {
        val builtin = setOf(
            EditorConfig.FONT_FAMILY_DEFAULT,
            EditorConfig.FONT_FAMILY_JETBRAINS_MONO,
            EditorConfig.FONT_FAMILY_DROID_SANS_MONO,
            EditorConfig.FONT_FAMILY_FIRA_CODE,
            EditorConfig.FONT_FAMILY_SOURCE_CODE_PRO,
            EditorConfig.FONT_FAMILY_ANONYMOUS_PRO,
            EditorConfig.FONT_FAMILY_DEJAVU_SANS_MONO,
        )
        val system = setOf(EditorConfig.FONT_FAMILY_MONOSPACE, EditorConfig.FONT_FAMILY_SANS, EditorConfig.FONT_FAMILY_SERIF)
        assertTrue("内置 id 与系统族 id 必须互斥", builtin.intersect(system).isEmpty())
        assertTrue("外部基址必须高于内置域", EditorConfig.FONT_FAMILY_EXTERNAL_BASE > builtin.max())
    }
}