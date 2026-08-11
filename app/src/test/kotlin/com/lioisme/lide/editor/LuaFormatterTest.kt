package com.lioisme.lide.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T16（2026-08-11）LuaFormatter 行为锁：缩进重排 / 注释·字符串·长括号保护 /
 * 换行符归一 / 空行折叠 / 大文件短路 / useTab-tabSize 矩阵。
 */
class LuaFormatterTest {

    private fun fmt(src: String): String = LuaFormatter.format(src)

    @Test
    fun `if 块缩进重排`() {
        val src = "if x then\nprint(1)\nend"
        assertEquals("if x then\n    print(1)\nend", fmt(src))
    }

    @Test
    fun `嵌套块深度正确`() {
        val src = "if a then\nif b then\nprint(1)\nend\nend"
        assertEquals(
            "if a then\n    if b then\n        print(1)\n    end\nend",
            fmt(src),
        )
    }

    @Test
    fun `elseif else 与 if 同级`() {
        val src = "if a then\nx()\nelseif b then\ny()\nelse\nz()\nend"
        assertEquals(
            "if a then\n    x()\nelseif b then\n    y()\nelse\n    z()\nend",
            fmt(src),
        )
    }

    @Test
    fun `for while repeat 开启块`() {
        val src = "for i = 1, 10 do\nprint(i)\nend\nwhile true do\nprint(1)\nend\nrepeat\nprint(2)\nuntil false"
        assertEquals(
            "for i = 1, 10 do\n    print(i)\nend\nwhile true do\n    print(1)\nend\nrepeat\n    print(2)\nuntil false",
            fmt(src),
        )
    }

    @Test
    fun `行内 do while end 不误伤`() {
        // 行内 while/do/end 只按行首词判定，同一行内的块词不改变深度。
        val src = "while false do end\nprint(1)"
        assertEquals("while false do end\nprint(1)", fmt(src))
    }

    @Test
    fun `function 与 local function 开启块`() {
        val src = "function f()\nreturn 1\nend\nlocal function g()\nreturn 2\nend"
        assertEquals(
            "function f()\n    return 1\nend\nlocal function g()\n    return 2\nend",
            fmt(src),
        )
    }

    @Test
    fun `local 变量声明不加级`() {
        val src = "local x = 1\nprint(x)"
        assertEquals("local x = 1\nprint(x)", fmt(src))
    }

    @Test
    fun `字符串内关键字零改动`() {
        val src = "local s = \"if end then while\"\nprint(s)"
        assertEquals("local s = \"if end then while\"\nprint(s)", fmt(src))
    }

    @Test
    fun `注释内容保护且前导空格保证`() {
        val src = "local x = 1-- if end then\nprint(x)"
        assertEquals("local x = 1 -- if end then\nprint(x)", fmt(src))
    }

    @Test
    fun `行注释行首原样保留`() {
        val src = "-- top comment\nlocal x = 1"
        assertEquals("-- top comment\nlocal x = 1", fmt(src))
    }

    @Test
    fun `长括号字符串保护`() {
        val src = "local s = [==[\nif end then\n]==]\nprint(s)"
        assertEquals("local s = [==[\nif end then\n]==]\nprint(s)", fmt(src))
    }

    @Test
    fun `长注释保护且不重排内部`() {
        val src = "local x = 1\n--[[\nif end\n]]\nprint(x)"
        assertEquals("local x = 1\n--[[\nif end\n]]\nprint(x)", fmt(src))
    }

    @Test
    fun `行尾空格清理`() {
        val src = "local x = 1   \nprint(x)   "
        assertEquals("local x = 1\nprint(x)", fmt(src))
    }

    @Test
    fun `token 间空白归一单空格`() {
        val src = "local    x   =   1\nprint   (x)"
        assertEquals("local x = 1\nprint (x)", fmt(src))
    }

    @Test
    fun `连续空行折叠保留一`() {
        val src = "a()\n\n\n\nb()"
        assertEquals("a()\n\nb()", fmt(src))
    }

    @Test
    fun `换行符归一`() {
        val src = "local x = 1\r\nprint(x)\r\n"
        assertEquals("local x = 1\nprint(x)\n", fmt(src))
    }

    @Test
    fun `末尾无换行保持无换行`() {
        val src = "print(1)"
        assertEquals("print(1)", fmt(src))
    }

    @Test
    fun `useTab 缩进`() {
        val src = "if x then\nprint(1)\nend"
        assertEquals("if x then\n\tprint(1)\nend", LuaFormatter.format(src, useTab = true))
    }

    @Test
    fun `tabSize 矩阵`() {
        val src = "if x then\nprint(1)\nend"
        assertEquals(
            "if x then\n  print(1)\nend",
            LuaFormatter.format(src, useTab = false, tabSize = 2),
        )
        assertEquals(
            "if x then\n      print(1)\nend",
            LuaFormatter.format(src, useTab = false, tabSize = 6),
        )
    }

    @Test
    fun `大文件短路返回原文本`() {
        val big = "local x = 1\n" + "y = y + 1\n".repeat(200_000) + "end\n"
        val out = LuaFormatter.format(big)
        assertEquals(big, out)
    }

    @Test
    fun `空输入返回原文本`() {
        assertEquals("", fmt(""))
    }

    @Test
    fun `多行引号字符串保护`() {
        val src = "local s = \"line1\nif end then\nline3\"\nprint(s)"
        assertEquals("local s = \"line1\nif end then\nline3\"\nprint(s)", fmt(src))
    }

    @Test
    fun `表结构缩进重排`() {
        val src = "layout = {\nLinearLayout,\n{\nTextView,\ntext = \"hi\",\n},\n}"
        assertEquals(
            "layout = {\n    LinearLayout,\n    {\n        TextView,\n        text = \"hi\",\n    },\n}",
            fmt(src),
        )
    }

    @Test
    fun `行内自闭合表不加减级`() {
        val src = "local t = { 1, 2 }\nprint(t[1])"
        assertEquals("local t = { 1, 2 }\nprint(t[1])", fmt(src))
    }

    @Test
    fun `表内函数块与表定界共存`() {
        val src = "layout = {\nonClick = function()\nprint(1)\nend,\n}"
        assertEquals(
            "layout = {\n    onClick = function()\n        print(1)\n    end,\n}",
            fmt(src),
        )
    }

    @Test
    fun `tab 缩进输入重排为空格缩进`() {
        // L3（2026-08-12 用户拍板）：默认 useTab=false——tab 缩进输入自动重排为
        // 空格 × tabSize(4)；格式化器按**语义深度**重写缩进（输入缩进不保留），
        // 故两 tab 行与单 tab 行同为 if 块内深度 1 → 均 4 空格。
        val src = "if x then\n\tprint(1)\n\t\tprint(2)\nend"
        assertEquals("if x then\n    print(1)\n    print(2)\nend", fmt(src))
    }

    @Test
    fun `默认参数即空格缩进`() {
        // L3 语义锁：format(src) 不带参数时缩进 = 空格（非 tab）。
        val src = "function f()\nreturn 1\nend"
        assertEquals("function f()\n    return 1\nend", LuaFormatter.format(src))
    }
}
