package com.lioisme.lide.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineResultParserTest {
    @Test
    fun `findErrorLocation extracts file and line`() {
        val output =
            """
            print ok
            Runtime error: /storage/emulated/0/AndroLua/project/demo/main.lua:12: attempt to index a nil value (global 'http')
            stack traceback:
            	.../main.lua:12: in main chunk
            """.trimIndent()
        assertEquals(
            EngineResultParser.ErrorLocation("main.lua", 12),
            EngineResultParser.findErrorLocation(output),
        )
    }

    @Test
    fun `findErrorLocation takes first location from error line`() {
        val output =
            """
            Runtime error: /p/main.lua:3: attempt to call a nil value
            stack traceback:
            	/p/helper.lua:8: in function 'inner'
            	/p/main.lua:5: in main chunk
            """.trimIndent()
        // 错误消息首行定位 = 真正报错点；traceback 帧为调用点，不取。
        assertEquals(
            EngineResultParser.ErrorLocation("main.lua", 3),
            EngineResultParser.findErrorLocation(output),
        )
    }

    @Test
    fun `findErrorLocation null on plain output`() {
        assertNull(EngineResultParser.findErrorLocation("hello\nworld"))
        assertNull(EngineResultParser.findErrorLocation(null))
        assertNull(EngineResultParser.findErrorLocation(""))
    }

    @Test
    fun `findErrorLocation anchors to error line ignoring leading timestamps`() {
        // B6 回归：stdout 先输出 `12:34:56` 时间戳行（NN:NN:NN 形态），
        // 错误定位必须锚定在 Runtime error 首行，不得被时间戳误导。
        val output =
            """
            12:34:56.789 starting demo
            download https://example.com/1:2:3
            Runtime error: /p/main.lua:7: attempt to call a nil value
            stack traceback:
            	/p/main.lua:7: in main chunk
            """.trimIndent()
        assertEquals(
            EngineResultParser.ErrorLocation("main.lua", 7),
            EngineResultParser.findErrorLocation(output, "/p"),
        )
    }

    @Test
    fun `findErrorLocation falls back to whole-output match when no error prefix`() {
        // B6 兜底语义：输出无错误前缀行时保持历史全文首匹配行为（此时无锚可依，
        // 首定位即最可能出错处——时间戳类误判仅存在于无错误前缀的兜底场景）。
        val output = "note line\n/p/main.lua:3: boom"
        assertEquals(
            EngineResultParser.ErrorLocation("main.lua", 3),
            EngineResultParser.findErrorLocation(output),
        )
    }

    @Test
    fun `findErrorLocation preserves project relative path for subdir files`() {
        val output =
            """
            Runtime error: /storage/emulated/0/AndroLua/project/demo/utils/helper.lua:8: attempt to call a nil value
            stack traceback:
            	/storage/emulated/0/AndroLua/project/demo/utils/helper.lua:8: in function 'inner'
            """.trimIndent()
        assertEquals(
            EngineResultParser.ErrorLocation("utils/helper.lua", 8),
            EngineResultParser.findErrorLocation(output, "/storage/emulated/0/AndroLua/project/demo"),
        )
    }

    @Test
    fun `findErrorLocation falls back to basename without project path or outside project`() {
        val output = "Runtime error: /p/utils/helper.lua:3: boom"
        assertEquals(EngineResultParser.ErrorLocation("helper.lua", 3), EngineResultParser.findErrorLocation(output))
        assertEquals(
            EngineResultParser.ErrorLocation("helper.lua", 3),
            EngineResultParser.findErrorLocation(output, "/unrelated/project"),
        )
    }

    @Test
    fun `findErrorLocation root file stays relative`() {
        val output = "Runtime error: /p/demo/main.lua:12: boom"
        assertEquals(
            EngineResultParser.ErrorLocation("main.lua", 12),
            EngineResultParser.findErrorLocation(output, "/p/demo"),
        )
    }

    @Test
    fun `findNilGlobal extracts quoted name`() {
        val output =
            """
            Runtime error: /p/main.lua:3: attempt to index a nil value (global 'http')
            """.trimIndent()
        assertEquals("http", EngineResultParser.findNilGlobal(output))
    }

    @Test
    fun `findNilGlobal null when absent`() {
        assertNull(EngineResultParser.findNilGlobal("attempt to index a nil value (field 'x')"))
        assertNull(EngineResultParser.findNilGlobal(null))
    }

    @Test
    fun `isErrorOutput matches error prefixes`() {
        assertTrue(EngineResultParser.isErrorOutput("Runtime error: /p/main.lua:1: boom"))
        assertTrue(EngineResultParser.isErrorOutput("Syntax error: /p/main.lua:1: 'end' expected"))
        assertFalse(EngineResultParser.isErrorOutput("hello from demo"))
        assertFalse(EngineResultParser.isErrorOutput(null))
    }
}
