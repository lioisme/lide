package com.lioisme.lide.editor

import io.github.rosemoe.sora.text.Content
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LuaDiagnostics 词法校验（WP-019 测试骨架）。
 */
class LuaDiagnosticsTest {
    private fun errors(source: String): List<String> = LuaDiagnostics.checkSyntax(source).map { it.detail.briefMessage.toString() }

    @Test
    fun `干净脚本零错误`() {
        val src =
            """
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
        val src =
            """
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

    /** 执行第一个错误的第一个 quickfix，返回执行后的文本。 */
    private fun applyFirstFix(source: String): String {
        val content = Content(source)
        val regions = LuaDiagnostics.Incremental().check(source, content)
        val fixes = regions.flatMap { it.detail.quickfixes.orEmpty() }
        assertTrue("期望存在 quickfix: $source", fixes.isNotEmpty())
        fixes.first().executeQuickfix()
        return content.toString()
    }

    @Test
    fun `多余 end 的 quickfix 删除该 token`() {
        assertEquals("print(1)\n", applyFirstFix("print(1)\nend"))
    }

    @Test
    fun `多余的 until 的 quickfix 删除该 token`() {
        assertEquals("print(1)\n", applyFirstFix("print(1)\nuntil"))
    }

    @Test
    fun `不匹配右括号的 quickfix 删除该字符`() {
        assertEquals("print(1", applyFirstFix("print(1]"))
    }

    @Test
    fun `未闭合字符串的 quickfix 在行尾补引号`() {
        assertEquals("local s = \"abc\"", applyFirstFix("local s = \"abc"))
    }

    @Test
    fun `未闭合括号的 quickfix 在起始行行尾补闭合符`() {
        assertEquals("print(1)", applyFirstFix("print(1"))
        assertEquals("local t = {1, 2}", applyFirstFix("local t = {1, 2"))
    }

    @Test
    fun `未闭合块的多错误聚合均附补 end quickfix`() {
        val source = "local function f()\nif x then"
        // 区域坐标基于检查时快照：两个 fix 各自在独立内容上执行（容器平移是宿主侧职责）。
        val c1 = Content(source)
        val fixes1 = LuaDiagnostics.Incremental().check(source, c1).flatMap { it.detail.quickfixes.orEmpty() }
        assertEquals(2, fixes1.size)
        fixes1[0].executeQuickfix()
        assertEquals("local function f()end\nif x then", c1.toString())
        val c2 = Content(source)
        val fixes2 = LuaDiagnostics.Incremental().check(source, c2).flatMap { it.detail.quickfixes.orEmpty() }
        fixes2[1].executeQuickfix()
        assertEquals("local function f()\nif x thenend", c2.toString())
    }

    @Test
    fun `未闭合长字符串的 quickfix 在文件末尾补关闭序列`() {
        assertEquals("local t = [[\nx]]", applyFirstFix("local t = [[\nx"))
        assertEquals("local t = [=[\nx]=]", applyFirstFix("local t = [=[\nx"))
    }

    @Test
    fun `未闭合 if 的 quickfix 在行尾补 end`() {
        assertEquals("if a thenend", applyFirstFix("if a then"))
    }

    @Test
    fun `stale quickfix 因预期文本不符而放弃修改`() {
        val source = "print(1)\nend"
        val content = Content(source)
        val regions = LuaDiagnostics.Incremental().check(source, content)
        val fixes = regions.flatMap { it.detail.quickfixes.orEmpty() }
        assertTrue(fixes.isNotEmpty())
        // 模拟行内编辑：token 前插入字符，未平移区域坐标 → 预期文本校验失败，静默放弃。
        content.insert(1, 0, "x")
        fixes.first().executeQuickfix()
        assertEquals("print(1)\nxend", content.toString())
    }

    @Test
    fun `无 Content 时不生成 quickfix`() {
        val regions = LuaDiagnostics.checkSyntax("print(1")
        assertTrue(regions.isNotEmpty())
        assertTrue(regions.all { it.detail.quickfixes.isNullOrEmpty() })
    }

    @Test
    fun `增量行索引在多次追加编辑后行号定位仍正确`() {
        val inc = LuaDiagnostics.Incremental()
        assertTrue(inc.check("a()\nb()").isEmpty())
        // 尾部追加两行并登记编辑（行 1..2）：行索引应从编辑行增量重扫。
        inc.onEdit(1, 2)
        val src = "a()\nb()\nlocal x = ("
        val region = inc.check(src).first()
        assertEquals("a()\nb()\n".length, region.startIndex)
        // 无编辑重查（轮询场景）：行索引复用路径不得破坏行号定位。
        assertEquals("a()\nb()\n".length, inc.check(src).first().startIndex)
    }

    @Test
    fun `自定义消息渲染器覆盖诊断文案`() {
        val provider =
            object : DiagnosticMessageProvider {
                override fun message(key: String, vararg args: Any): String =
                    when (key) {
                        MSG_UNMATCHED_CLOSE -> "bad close: ${args[0]}"
                        MSG_AT_LINE -> "${args[0]}@${args[1]}"
                        else -> key
                    }
            }
        val regions = LuaDiagnostics.Incremental(provider).check("local x = }")
        assertTrue(regions.any { it.detail.briefMessage == "bad close: }@1" })
        // 默认渲染器仍为中文模板（历史文案契约）。
        assertTrue(
            LuaDiagnostics.checkSyntax("local x = }")
                .any { it.detail.briefMessage.contains("不匹配的 '}'") },
        )
    }
}
