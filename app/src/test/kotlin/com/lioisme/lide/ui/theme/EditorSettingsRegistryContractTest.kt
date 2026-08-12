package com.lioisme.lide.ui.theme

import com.lioisme.lide.editor.EditorConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L1（2026-08-12）编辑器设置键契约锁（单一数据表驱动，加减键只改 SettingsRegistry）：
 * - `fixed_line_numbers` 键存在、默认 true、读写映射 EditorConfig.fixedLineNumbers
 *   （固定行号，sora 原生 setPinLineNumber）；
 * - `use_tab` 键默认 false（L3 用户拍板：默认空格缩进）；
 * - **不复用** P4 已删除的旧键 `pin_line_number`（历史引用不得回退——
 *   旧键若复活即与固定行号双键并存，违反治理红线）。
 */
class EditorSettingsRegistryContractTest {
    @Test
    fun `固定行号键存在且默认开启`() {
        val key =
            SettingsRegistry.boolKeys.find { it.name == "fixed_line_numbers" }
                ?: error("缺少 fixed_line_numbers 键（SettingsRegistry.boolKeys）")
        assertTrue("fixed_line_numbers 默认必须为 true", key.default)
        assertTrue("get 必须映射 fixedLineNumbers", key.get(EditorConfig()))
        val updated = key.with(EditorConfig(), false)
        assertFalse("with 必须写回 fixedLineNumbers", updated.fixedLineNumbers)
    }

    @Test
    fun `use_tab 键默认空格缩进`() {
        val key =
            SettingsRegistry.boolKeys.find { it.name == "use_tab" }
                ?: error("缺少 use_tab 键")
        assertFalse("use_tab 默认必须为 false（空格缩进）", key.default)
    }

    @Test
    fun `默认缩进键与固定行号键共存于表驱动`() {
        val names = SettingsRegistry.boolKeys.map { it.name }
        assertTrue(names.contains("use_tab"))
        assertTrue(names.contains("fixed_line_numbers"))
    }

    @Test
    fun `旧键 pin_line_number 不得回退`() {
        assertNull(
            "P4 已删除的 pin_line_number 键不得复活（历史引用不得回退）",
            SettingsRegistry.boolKeys.find { it.name == "pin_line_number" },
        )
    }

    @Test
    fun `行号键默认开启且与固定行号解耦存储`() {
        val lineNumbers =
            SettingsRegistry.boolKeys.find { it.name == "line_numbers" }
                ?: error("缺少 line_numbers 键")
        assertTrue("line_numbers 默认必须为 true", lineNumbers.default)
        // 固定行号默认值独立于行号开关（联动在设置页 UI 层，存储解耦）。
        val fixed = SettingsRegistry.boolKeys.find { it.name == "fixed_line_numbers" }!!
        assertEquals(true, fixed.default)
    }
}
