package com.lioisme.lide.androlua

import org.junit.Assert.assertEquals
import org.junit.Test

class LuaConfigParserTest {

    @Test
    fun `quote escapes quotes backslashes and newlines`() {
        assertEquals("\"a\\\"b\"", LuaConfigParser.quoteLuaString("a\"b"))
        assertEquals("\"a\\\\b\"", LuaConfigParser.quoteLuaString("a\\b"))
        assertEquals("\"a\\nb\"", LuaConfigParser.quoteLuaString("a\nb"))
        assertEquals("\"a\\rb\"", LuaConfigParser.quoteLuaString("a\rb"))
        assertEquals("\"a\\tb\"", LuaConfigParser.quoteLuaString("a\tb"))
    }

    @Test
    fun `quote keeps utf8 bytes intact`() {
        assertEquals("\"中文\"", LuaConfigParser.quoteLuaString("中文"))
        assertEquals("\"a€b\"", LuaConfigParser.quoteLuaString("a€b"))
    }

    @Test
    fun `quote escapes control chars`() {
        assertEquals("\"\\u{00}\"", LuaConfigParser.quoteLuaString("\u0000"))
        assertEquals("\"\\u{1f}\"", LuaConfigParser.quoteLuaString("\u001f"))
    }

    @Test
    fun `setStringArrayField writes pro style multiline`() {
        val content = "appname = \"demo\"\n"
        val out = LuaConfigParser.setStringArrayField(
            content, "user_permission", listOf("INTERNET", "WRITE_EXTERNAL_STORAGE")
        )
        assertEquals(
            "appname = \"demo\"\nuser_permission = { \"INTERNET\", \"WRITE_EXTERNAL_STORAGE\" }\n",
            out,
        )
    }

    @Test
    fun `setField preserves unrelated content and comments`() {
        val content = """
            --[[
            appname = "old-blocked"
            ]]
            -- appname comment
            appname = "old"
            appver = "1.0"
            -- tail comment
        """.trimIndent() + "\n"
        val out = LuaConfigParser.setField(content, "appname", "\"new\"")
        assertEquals(
            """
            --[[
            appname = "old-blocked"
            ]]
            -- appname comment
            appname = "new"
            appver = "1.0"
            -- tail comment
            """.trimIndent() + "\n",
            out,
        )
    }

    @Test
    fun `setField appends missing field at end`() {
        val content = "appname = \"demo\"\n"
        val out = LuaConfigParser.setField(content, "debugmode", "true")
        assertEquals("appname = \"demo\"\ndebugmode = true\n", out)
    }

    @Test
    fun `repro sequential save of full field set`() {
        val content = "appname=\"demo\"\nappver=\"1.0\"\npackagename=\"com.androlua.demo\"\n" +
            "user_permission={\n  \"INTERNET\",\n  \"WRITE_EXTERNAL_STORAGE\",\n}\n"
        val values = listOf(
            "appname" to "testapp",
            "appver" to "2.5",
            "appcode" to "",
            "appsdk" to "",
            "path_pattern" to "",
            "packagename" to "com.androlua.testapp",
            "theme" to "",
            "app_key" to "",
            "app_channel" to "",
            "developer" to "",
            "description" to "hello",
        )
        var c = content
        for ((k, v) in values) {
            c = LuaConfigParser.setField(c, k, LuaConfigParser.quoteLuaString(v))
        }
        val out = c
        val appname = LuaConfigParser.stringField(out, "appname")
        val packagename = LuaConfigParser.stringField(out, "packagename")
        val appver = LuaConfigParser.stringField(out, "appver")
        assertEquals("testapp", appname)
        assertEquals("com.androlua.testapp", packagename)
        assertEquals("2.5", appver)
    }
}
