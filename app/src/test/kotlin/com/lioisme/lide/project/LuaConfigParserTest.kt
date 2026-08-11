package com.lioisme.lide.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaConfigParserTest {

    // ---- P5b 测试迁移：以下用例从 :app 误置副本（app/src/test/.../project/ 下同包名）
    // ---- 迁入 :androlua 权威测试（被测类 LuaConfigParser 属 :androlua）。

    @Test
    fun `top level fields parsed`() {
        val fields = LuaConfigParser.parse(sample)
        val names = fields.map { it.name }
        assertTrue("appname" in names)
        assertTrue("appver" in names)
        assertTrue("user_permission" in names)
        assertTrue("icon" in names)
    }

    @Test
    fun `string field unquoted with null fallback`() {
        assertEquals("demo", LuaConfigParser.stringField(sample, "appname"))
        assertNull(LuaConfigParser.stringField(sample, "icon"))
        assertNull(LuaConfigParser.stringField(sample, "missing"))
    }

    @Test
    fun `array field parsed`() {
        assertEquals(
            listOf("main", "page_calc"),
            LuaConfigParser.stringArrayField(sample, "pages"),
        )
        assertTrue(LuaConfigParser.stringArrayField(sample, "missing").isEmpty())
    }

    @Test
    fun `appname inside line comment not matched`() {
        val content = "-- appname = \"comment\"\nprint(1)\nappname = \"real\"\n"
        assertEquals("real", LuaConfigParser.stringField(content, "appname"))
    }

    @Test
    fun `assignment inside string not matched`() {
        val content = "local s = \"appname = \\\"fake\\\"\"\nappname = \"ok\"\n"
        assertEquals("ok", LuaConfigParser.stringField(content, "appname"))
    }

    @Test
    fun `fields inside long comment and long string not matched`() {
        val content =
            """
            --[[ appname = "incomment" ]]
            local t = [[
                appname = "instring"
            ]]
            appname = "real"
            """.trimIndent()
        assertEquals("real", LuaConfigParser.stringField(content, "appname"))
    }

    @Test
    fun `fields in nested table not matched`() {
        val content = "layout = { appname = \"inner\" }\nappname = \"outer\"\n"
        assertEquals("outer", LuaConfigParser.stringField(content, "appname"))
    }

    @Test
    fun `multiline array field parsed`() {
        val content =
            """
            user_permission = {
                "CAMERA",
                "WRITE_SETTINGS",
                INSTALL_SHORTCUT,
            }
            """.trimIndent()
        assertEquals(
            listOf("CAMERA", "WRITE_SETTINGS", "INSTALL_SHORTCUT"),
            LuaConfigParser.stringArrayField(content, "user_permission"),
        )
    }

    @Test
    fun `setField keeps bytes outside replacement identical`() {
        val before = sample
        val after = LuaConfigParser.setField(before, "appver", "\"2.0\"")
        assertTrue(after.contains("appver = \"2.0\""))
        assertTrue(after.contains("-- lide project init"))
        assertTrue(after.contains("-- trailing comment stays"))
        val strippedAfter = after.replace("appver = \"2.0\"\n", "")
        val strippedBefore = before.replace("appver = \"1.0\"\n", "")
        assertEquals(strippedBefore, strippedAfter)
    }

    @Test
    fun `setStringArrayField keeps other fields`() {
        val after = LuaConfigParser.setStringArrayField(sample, "user_permission", listOf("CAMERA"))
        assertEquals(listOf("CAMERA"), LuaConfigParser.stringArrayField(after, "user_permission"))
        assertEquals("demo", LuaConfigParser.stringField(after, "appname"))
    }

    @Test
    fun `setStringArrayField empty array`() {
        val after = LuaConfigParser.setStringArrayField(sample, "user_permission", emptyList())
        assertTrue(LuaConfigParser.stringArrayField(after, "user_permission").isEmpty())
    }

    @Test
    fun `bare identifier array items parsed`() {
        assertEquals(
            listOf("CAMERA"),
            LuaConfigParser.stringArrayField("user_permission = { CAMERA }\n", "user_permission"),
        )
    }

    @Test
    fun `scalar with function call not truncated`() {
        val content = "debugmode = checkVersion(3)\nappname = \"x\"\n"
        assertEquals("checkVersion(3)", LuaConfigParser.field(content, "debugmode"))
    }

    private val sample =
        """
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

    @Test
    fun `top level index expression does not poison subsequent fields`() {
        val content =
            "t[1] = \"a\"\n" +
                "t[2] = \"b\"\n" +
                "appname = \"demo\"\n" +
                "appver = \"1.0\"\n" +
                "user_permission = { \"INTERNET\", \"WRITE_EXTERNAL_STORAGE\" }\n"
        val appname = LuaConfigParser.stringField(content, "appname")
        val appver = LuaConfigParser.stringField(content, "appver")
        val perms = LuaConfigParser.stringArrayField(content, "user_permission")
        assertEquals("demo", appname)
        assertEquals("1.0", appver)
        assertEquals(listOf("INTERNET", "WRITE_EXTERNAL_STORAGE"), perms)
    }

    @Test
    fun `table bracket keys do not leak into depth counting`() {
        val content =
            "theme = { [1] = \"a\", [2] = \"b\" }\n" +
                "appname = \"demo\"\n"
        assertEquals("demo", LuaConfigParser.stringField(content, "appname"))
    }

    @Test
    fun `long string values and comments still parsed after index expressions`() {
        val content =
            "local t = {}\n" +
                "t[1] = 'x'\n" +
                "--[[\n" +
                "appname = \"blocked\"\n" +
                "]]\n" +
                "appdesc = [[hello]]\n" +
                "packagename = \"com.demo\"\n"
        assertEquals("[[hello]]", LuaConfigParser.field(content, "appdesc"))
        assertEquals("com.demo", LuaConfigParser.stringField(content, "packagename"))
    }

    // ---- P1（2026-08-11）：removeField（「跟随宿主」语义 = 字段不存在）----

    @Test
    fun `removeField removes whole line`() {
        val content = "appname = \"demo\"\nappsdk = 23\ntheme = \"Theme_Light\"\n"
        val out = LuaConfigParser.removeField(content, "appsdk")
        assertEquals("appname = \"demo\"\ntheme = \"Theme_Light\"\n", out)
        assertNull(LuaConfigParser.field(out, "appsdk"))
    }

    @Test
    fun `removeField removes last field without trailing newline`() {
        val content = "appname = \"demo\"\ntheme = \"Theme_Black\""
        val out = LuaConfigParser.removeField(content, "theme")
        assertEquals("appname = \"demo\"\n", out)
    }

    @Test
    fun `removeField keeps bytes outside target line identical`() {
        val content = "-- keep me\nappname = \"demo\"\nappsdk = 23 -- sdk note\nuser_permission = { }\n"
        val out = LuaConfigParser.removeField(content, "appsdk")
        assertEquals("-- keep me\nappname = \"demo\"\nuser_permission = { }\n", out)
    }

    @Test
    fun `removeField missing field returns content unchanged`() {
        val content = "appname = \"demo\"\n"
        assertEquals(content, LuaConfigParser.removeField(content, "appsdk"))
    }

    @Test
    fun `removeField does not match inside comments or tables`() {
        val content = "-- appsdk = 99\nlayout = { appsdk = 21 }\nappname = \"x\"\n"
        assertEquals(content, LuaConfigParser.removeField(content, "appsdk"))
    }
}
