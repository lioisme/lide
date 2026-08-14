package com.lioisme.lide.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.oshai.kotlinlogging.Level

/**
 * LogFilter 语义表 + filterEntries 过滤契约测试（2026-08-14 LOGCAT L1，
 * 2026-08-14 LOGCAT_REVISION L1 修订：Lua/Test 拆分对齐 pro logcat.lua）。
 *
 * 语义对齐 pro logcat.lua 过滤器（All=*:* / Lua=lua:* / Test=test:* / Tcc=tcc:* /
 * Error/Warning/Info/Debug/Verbose=*:E/W/I/D/V，即级别 ≥ 阈值）。
 */
class LogFilterTest {

    private fun entry(
        tag: String = "lua",
        level: Level = Level.INFO,
        message: String = "msg",
        id: Long = 0,
    ): LogEntry =
        LogEntry(
            id = id,
            timeText = "08-14 10:22:33.123",
            pid = 1,
            tid = 2,
            level = level,
            tag = tag,
            message = message,
        )

    @Test
    fun `菜单顺序对齐 pro logcat lua`() {
        assertEquals(
            listOf(
                "ALL",
                "LUA",
                "TEST",
                "TCC",
                "ERROR",
                "WARNING",
                "INFO",
                "DEBUG",
                "VERBOSE",
            ),
            LogFilter.entries.map { it.name },
        )
    }

    @Test
    fun `ALL 匹配一切`() {
        assertTrue(LogFilter.ALL.matches(entry(tag = "lua", level = Level.TRACE)))
        assertTrue(LogFilter.ALL.matches(entry(tag = "crash", level = Level.ERROR)))
    }

    @Test
    fun `LUA 仅 tag lua 精确匹配`() {
        assertTrue(LogFilter.LUA.matches(entry(tag = "lua")))
        assertFalse(LogFilter.LUA.matches(entry(tag = "luajava")))
        assertFalse(LogFilter.LUA.matches(entry(tag = "luaX")))
        assertFalse(LogFilter.LUA.matches(entry(tag = "test")))
        assertFalse(LogFilter.LUA.matches(entry(tag = "crash")))
    }

    @Test
    fun `TEST 仅 tag test 精确匹配`() {
        assertTrue(LogFilter.TEST.matches(entry(tag = "test")))
        assertFalse(LogFilter.TEST.matches(entry(tag = "Test")))
        assertFalse(LogFilter.TEST.matches(entry(tag = "lua")))
        assertFalse(LogFilter.TEST.matches(entry(tag = "crash")))
    }

    @Test
    fun `TCC 仅 tag tcc`() {
        assertTrue(LogFilter.TCC.matches(entry(tag = "tcc")))
        assertFalse(LogFilter.TCC.matches(entry(tag = "lua")))
    }

    @Test
    fun `级别阈值语义`() {
        assertTrue(LogFilter.ERROR.matches(entry(level = Level.ERROR)))
        assertFalse(LogFilter.ERROR.matches(entry(level = Level.WARN)))

        assertTrue(LogFilter.WARNING.matches(entry(level = Level.WARN)))
        assertTrue(LogFilter.WARNING.matches(entry(level = Level.ERROR)))
        assertFalse(LogFilter.WARNING.matches(entry(level = Level.INFO)))

        assertTrue(LogFilter.INFO.matches(entry(level = Level.INFO)))
        assertTrue(LogFilter.INFO.matches(entry(level = Level.WARN)))
        assertFalse(LogFilter.INFO.matches(entry(level = Level.DEBUG)))

        assertTrue(LogFilter.DEBUG.matches(entry(level = Level.DEBUG)))
        assertTrue(LogFilter.DEBUG.matches(entry(level = Level.INFO)))
        assertFalse(LogFilter.DEBUG.matches(entry(level = Level.TRACE)))

        assertTrue(LogFilter.VERBOSE.matches(entry(level = Level.TRACE)))
        assertTrue(LogFilter.VERBOSE.matches(entry(level = Level.ERROR)))
    }

    @Test
    fun `ALL 空查询零拷贝返回原表`() {
        val entries = listOf(entry(), entry(id = 1))
        assertSame(entries, filterEntries(entries, LogFilter.ALL, ""))
    }

    @Test
    fun `查询词命中 tag 与消息忽略大小写`() {
        val entries =
            listOf(
                entry(id = 0, tag = "Lua", message = "print output"),
                entry(id = 1, tag = "crash", message = "Runtime error at line 42"),
            )
        assertEquals(listOf(0L), filterEntries(entries, LogFilter.ALL, "print").map { it.id })
        assertEquals(listOf(1L), filterEntries(entries, LogFilter.ALL, "LINE 42").map { it.id })
        // "LUA" 仅命中 tag（entry1 消息无 lua 子串）。
        assertEquals(listOf(0L), filterEntries(entries, LogFilter.ALL, "LUA").map { it.id })
    }

    @Test
    fun `类型与查询词组合过滤`() {
        val entries =
            listOf(
                entry(id = 0, tag = "lua", level = Level.INFO, message = "hello"),
                entry(id = 1, tag = "lua", level = Level.ERROR, message = "boom"),
                entry(id = 2, tag = "crash", level = Level.ERROR, message = "boom"),
            )
        assertEquals(listOf(1L), filterEntries(entries, LogFilter.LUA, "boom").map { it.id })
        assertEquals(listOf(1L, 2L), filterEntries(entries, LogFilter.ERROR, "").map { it.id })
        assertEquals(listOf(0L, 1L), filterEntries(entries, LogFilter.LUA, "").map { it.id })
        assertEquals(emptyList<Long>(), filterEntries(entries, LogFilter.TCC, "").map { it.id })
        assertEquals(emptyList<Long>(), filterEntries(entries, LogFilter.TEST, "").map { it.id })
    }

    @Test
    fun `查询词空白串视为无查询`() {
        val entries = listOf(entry(id = 0), entry(id = 1))
        assertEquals(2, filterEntries(entries, LogFilter.ALL, "   ").size)
    }
}