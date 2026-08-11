package com.lioisme.lide.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * L2（2026-08-12）字体收敛契约锁：内置/导入字体表结构 + assets/fonts 白名单 +
 * 系统字体「仅终极兜底」语义。
 *
 * 约束（与 AGENTS.md R5 例外联动）：
 * - assets/fonts/ 恰好 6 款内置等宽 ttf（OFL，Squircle CE 组合），白名单文件必须存在；
 * - `FONT_FAMILY_DEFAULT`（0）与 `FONT_FAMILY_JETBRAINS_MONO`（4）为同一资产别名
 *   （默认字体 = 内置 JetBrains Mono 红线）；
 * - 内置 id 绝不与系统族 id（1/2/3）重叠——系统字体不再作为可选项，仅兜底与
 *   历史持久化兼容；
 * - 外部导入字体 id 基址（100）大于全部内置 id，无冲突区间。
 *
 * 注意：本测试只触碰纯表结构与文件树（Gradle 单测工作目录 = 模块目录），
 * 不触碰 [android.graphics.Typeface]（JVM 下为 stub，兜底链行为在设备验收
 * 覆盖——删内置字体后回落系统等宽，见工作单 L2 验收）。
 */
class LuaFontsFallbackTest {

    private val assetsRoot =
        listOf(
            File("src/main/assets"),
            File("editor/src/main/assets"),
        ).firstOrNull { it.isDirectory }
            ?: throw IllegalStateException("assets root not found (cwd=${File("").absolutePath})")

    /** R5 白名单（AGENTS.md）：恰 6 款内置等宽，新增必须同步登记。 */
    private val WHITELIST =
        setOf(
            "jetbrains_mono.ttf",
            "droid_sans_mono.ttf",
            "fira_code.ttf",
            "source_code_pro.ttf",
            "anonymous_pro.ttf",
            "dejavu_sans_mono.ttf",
        )

    @Test
    fun `内置字体资产与 R5 白名单一致`() {
        val fontDir = File(assetsRoot, "fonts")
        assertTrue("assets/fonts 目录必须存在（cwd=${File("").absolutePath}）", fontDir.isDirectory)
        val present =
            fontDir.listFiles { f -> f.isFile && f.extension.equals("ttf", ignoreCase = true) }
                ?.map { it.name }
                ?.toSet()
                ?: emptySet()
        assertEquals("assets/fonts 必须与 R5 白名单完全一致（禁止多余 ttf）", WHITELIST, present)
    }

    @Test
    fun `内置表值全部指向存在的白名单资产`() {
        assertEquals("builtinAssets 必须恰好映射 6 个唯一资产", 6, LuaFonts.builtinAssets.values.toSet().size)
        for ((id, asset) in LuaFonts.builtinAssets) {
            assertTrue("id=$id → asset=$asset 必须在白名单内", asset in WHITELIST)
            assertTrue("id=$id 的资产文件必须存在: fonts/$asset", File(assetsRoot, "fonts/$asset").isFile)
        }
    }

    @Test
    fun `默认字体为内置 JetBrains Mono 别名`() {
        assertEquals(
            "FONT_FAMILY_DEFAULT 必须与 FONT_FAMILY_JETBRAINS_MONO 同一资产",
            LuaFonts.builtinAssets[EditorConfig.FONT_FAMILY_DEFAULT],
            LuaFonts.builtinAssets[EditorConfig.FONT_FAMILY_JETBRAINS_MONO],
        )
        assertEquals("jetbrains_mono.ttf", LuaFonts.builtinAssets[EditorConfig.FONT_FAMILY_DEFAULT])
    }

    @Test
    fun `内置 id 与系统族 id 无重叠`() {
        // 系统族（1/2/3）仅终极兜底语义，不得出现在内置选项中。
        val systemIds = setOf(EditorConfig.FONT_FAMILY_MONOSPACE, EditorConfig.FONT_FAMILY_SANS, EditorConfig.FONT_FAMILY_SERIF)
        for (id in LuaFonts.builtinAssets.keys) {
            assertFalse("内置 id=$id 不得重叠系统族 id", id in systemIds)
            assertTrue("内置 id=$id 必须在 [0, EXTERNAL_BASE) 区间", id in 0 until EditorConfig.FONT_FAMILY_EXTERNAL_BASE)
        }
    }

    @Test
    fun `外部导入 id 基址高于全部内置 id`() {
        val maxBuiltin = LuaFonts.builtinAssets.keys.maxOrNull() ?: -1
        assertTrue(
            "外部基址 ${EditorConfig.FONT_FAMILY_EXTERNAL_BASE} 必须高于全部内置 id（最大 $maxBuiltin）",
            EditorConfig.FONT_FAMILY_EXTERNAL_BASE > maxBuiltin,
        )
    }
}