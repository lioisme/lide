package com.lioisme.lide.androlua

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LuaConfigParser：init.lua 结构化读写（WP-017 底座；WP-003/004 依赖）。
 */
class LuaConfigParserTest {

    private val sample = """
        -- lide project init
        appname = "demo"
        appver = "1.0"
        packagename = "com.lioisme.lide.demo"
        icon = nil
        pages = {"main", "page_calc"}
        user_permission = { "CAMERA", "RECORD_AUDIO" }
        -- trailing comment stays
    """.trimIndent()

    @Test
    fun `解析顶层字段`() {
        val fields = LuaConfigParser.parse(sample)
        val names = fields.map { it.name }
        assertTrue("appname" in names)
        assertTrue("appver" in names)
        assertTrue("user_permission" in names)
        assertTrue("icon" in names)
    }

    @Test
    fun `字符串字段去引号`() {
        assertEquals("demo", LuaConfigParser.stringField(sample, "appname"))
        assertEquals("1.0", LuaConfigParser.stringField(sample, "appver"))
        assertNull(LuaConfigParser.stringField(sample, "icon"))
        assertNull(LuaConfigParser.stringField(sample, "missing"))
    }

    @Test
    fun `数组字段解析`() {
        assertEquals(
            listOf("main", "page_calc"),
            LuaConfigParser.stringArrayField(sample, "pages")
        )
        assertEquals(
            listOf("CAMERA", "RECORD_AUDIO"),
            LuaConfigParser.stringArrayField(sample, "user_permission")
        )
        assertTrue(LuaConfigParser.stringArrayField(sample, "missing").isEmpty())
    }

    @Test
    fun `注释中的 appname 不命中`() {
        val content = "-- appname = \"comment\"\nprint(1)\nappname = \"real\"\n"
        assertEquals("real", LuaConfigParser.stringField(content, "appname"))
    }

    @Test
    fun `字符串内的赋值不命中`() {
        val content = "local s = \"appname = \\\"fake\\\"\"\nappname = \"ok\"\n"
        assertEquals("ok", LuaConfigParser.stringField(content, "appname"))
    }

    @Test
    fun `长注释与长字符串内的字段不命中`() {
        val content = """
            --[[ appname = "incomment" ]]
            local t = [[
                appname = "instring"
            ]]
            appname = "real"
        """.trimIndent()
        assertEquals("real", LuaConfigParser.stringField(content, "appname"))
    }

    @Test
    fun `嵌套表中的字段不命中`() {
        val content = "layout = { appname = \"inner\" }\nappname = \"outer\"\n"
        assertEquals("outer", LuaConfigParser.stringField(content, "appname"))
    }

    @Test
    fun `多行数组字段解析`() {
        val content = """
            user_permission = {
                "CAMERA",
                "WRITE_SETTINGS",
                INSTALL_SHORTCUT,
            }
        """.trimIndent()
        assertEquals(
            listOf("CAMERA", "WRITE_SETTINGS", "INSTALL_SHORTCUT"),
            LuaConfigParser.stringArrayField(content, "user_permission")
        )
    }

    @Test
    fun `写回单字段且其余字节级不变`() {
        val before = sample
        val after = LuaConfigParser.setField(before, "appver", "\"2.0\"")
        assertTrue(after.contains("appver = \"2.0\""))
        assertTrue(after.contains("-- lide project init"))
        assertTrue(after.contains("-- trailing comment stays"))
        // 替换区间外的字节完全一致：删除 appver 行后应与原文删除行后相同。
        val strippedAfter = after.replace("appver = \"2.0\"\n", "")
        val strippedBefore = before.replace("appver = \"1.0\"\n", "")
        assertEquals(strippedBefore, strippedAfter)
    }

    @Test
    fun `写回数组字段`() {
        val after = LuaConfigParser.setStringArrayField(sample, "user_permission", listOf("CAMERA"))
        assertEquals(
            listOf("CAMERA"),
            LuaConfigParser.stringArrayField(after, "user_permission")
        )
        // 其余字段不受影响
        assertEquals("demo", LuaConfigParser.stringField(after, "appname"))
    }

    @Test
    fun `写回空数组`() {
        val after = LuaConfigParser.setStringArrayField(sample, "user_permission", emptyList())
        assertTrue(LuaConfigParser.stringArrayField(after, "user_permission").isEmpty())
    }

    @Test
    fun `写回缺失字段时追加到文件末尾`() {
        val after = LuaConfigParser.setField("appname = \"a\"\n", "appver", "\"1.1\"")
        assertEquals("appname = \"a\"\nappver = \"1.1\"\n", after)
    }

    @Test
    fun `裸标识符数组项`() {
        assertEquals(
            listOf("CAMERA"),
            LuaConfigParser.stringArrayField("user_permission = { CAMERA }\n", "user_permission")
        )
    }

    @Test
    fun `字段值跨行标量（函数调用）不被截断`() {
        // 标量含括号：按行尾截断即可（值本身单行）。
        val content = "debugmode = checkVersion(3)\nappname = \"x\"\n"
        assertEquals("checkVersion(3)", LuaConfigParser.field(content, "debugmode"))
    }
}
