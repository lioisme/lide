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
