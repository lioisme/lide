package com.lioisme.lide.androlua

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LuaCompilerTest {

    @Test
    fun `parseErrorLine extracts line from file-style chunk`() {
        assertEquals(12, LuaCompiler.parseErrorLine("main.lua:12: syntax error near 'end'"))
    }

    @Test
    fun `parseErrorLine extracts line from at-prefixed chunk`() {
        assertEquals(3, LuaCompiler.parseErrorLine("@main.lua:3: unexpected symbol"))
    }

    @Test
    fun `parseErrorLine extracts line from equals-prefixed chunk`() {
        assertEquals(5, LuaCompiler.parseErrorLine("=main.lua:5: 'then' expected"))
    }

    @Test
    fun `parseErrorLine returns null when no line`() {
        assertNull(LuaCompiler.parseErrorLine("stack overflow"))
        assertNull(LuaCompiler.parseErrorLine(""))
    }

    @Test
    fun `compile failure message strips chunk prefix`() {
        val e = LuaCompileException(12, "syntax error near 'end'")
        assertEquals(12, e.line)
        assertEquals("syntax error near 'end'", e.message)
    }
}
