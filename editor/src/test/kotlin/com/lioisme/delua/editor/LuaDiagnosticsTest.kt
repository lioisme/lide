package com.lioisme.delua.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LuaDiagnostics 词法校验（WP-019 测试骨架）。
 */
class LuaDiagnosticsTest {

    private fun errors(source: String): List<String> =
        LuaDiagnostics.checkSyntax(source).map { it.detail.briefMessage.toString() }

    @Test
    fun `干净脚本零错误`() {
        val src = """
            local function greet(name)
                if name then
                    print("hello, " .. name)
                end
                for i = 1, 10 do
                    print(i)
                end
                while false do end
                repeat print(1) until true
            end
            greet("world")
        """.trimIndent()
        assertTrue(errors(src).isEmpty())
    }

    @Test
    fun `未闭合括号报错`() {
        assertTrue(errors("local x = (1 + 2").any { it.contains("未闭合的 '('") })
    }

    @Test
    fun `不匹配右括号报错`() {
        assertTrue(errors("local x = }").any { it.contains("不匹配的 '}'") })
    }

    @Test
    fun `多余 end 报错`() {
        assertTrue(errors("print(1)\nend").any { it.contains("多余的 'end'") })
    }

    @Test
    fun `未闭合 function 报错`() {
        assertTrue(errors("local function f()").any { it.contains("未闭合的 'function'") })
    }

    @Test
    fun `字符串内的括号不算结构符`() {
        assertTrue(errors("local s = \"a(b)c\"\nprint(s)").isEmpty())
        assertTrue(errors("local s = 'x}y'").isEmpty())
    }

    @Test
    fun `行注释内的括号与关键字被忽略`() {
        assertTrue(errors("-- if ( then end\nprint(1)").isEmpty())
    }

    @Test
    fun `长注释跨行不影响结构`() {
        val src = """
            --[[
            if ( broken
            end
            ]]
            print("ok")
        """.trimIndent()
        assertTrue(errors(src).isEmpty())
    }

    @Test
    fun `长字符串跨行被视为字符串`() {
        assertTrue(errors("local t = [[\nif then end\n]]\nprint(t)").isEmpty())
    }

    @Test
    fun `未闭合长注释报错并指向起始行`() {
        assertTrue(errors("print(1)\n--[[ oops\nprint(2)").any { it.contains("未闭合的长注释") })
    }

    @Test
    fun `错误行号定位正确`() {
        val source = "print(1)\nlocal x = ("
        val region = LuaDiagnostics.checkSyntax(source).first()
        // 错误位于第二行，region 起点应为该行的行首偏移。
        assertEquals("print(1)\n".length, region.startIndex)
    }

    @Test
    fun `增量编辑后错误收敛为正确`() {
        val inc = LuaDiagnostics.Incremental()
        val src = "local f = function()\nprint(1)\nend"
        assertTrue(inc.check(src).isEmpty())
        // 在 function 行插入括号导致未闭合
        val broken = "local f = function(\nprint(1)\nend"
        inc.onEdit(0, 0)
        assertTrue(inc.check(broken).any { it.detail.briefMessage.contains("未闭合的 '('") })
        // 删除括号恢复正常
        inc.onEdit(0, 0)
        assertTrue(inc.check(src).isEmpty())
    }

    @Test
    fun `增量编辑跨多行后仍正确`() {
        val inc = LuaDiagnostics.Incremental()
        val base = "a()\nb()\nc()\nd()"
        assertTrue(inc.check(base).isEmpty())
        val changed = "a(\nb()\nc()\nd()"
        inc.onEdit(0, 2)
        assertTrue(inc.check(changed).any { it.detail.briefMessage.contains("未闭合") })
    }

    @Test
    fun `注释中的 appname 不产生结构错误`() {
        assertTrue(errors("-- appname = \"demo\"\nprint(1)").isEmpty())
    }
}
