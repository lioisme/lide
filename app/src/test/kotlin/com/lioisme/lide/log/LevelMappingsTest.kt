package com.lioisme.lide.log

import io.github.oshai.kotlinlogging.Level
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * LevelMappings 映射契约测试（2026-08-15 kotlin-logging 迁移）。
 *
 * 断言 logcat 优先级字符 ↔ kotlin-logging [Level] 双向映射与 fatal/未知回落语义，
 * 守护读取面级别语义与写入面（AndroidNativeLoggerFactory 实际输出）一致。
 */
class LevelMappingsTest {

    @Test
    fun `logcat 字符映射 kotlin-logging 级别`() {
        assertEquals(Level.TRACE, logcatSymbolToLevel('V'))
        assertEquals(Level.DEBUG, logcatSymbolToLevel('D'))
        assertEquals(Level.INFO, logcatSymbolToLevel('I'))
        assertEquals(Level.WARN, logcatSymbolToLevel('W'))
        assertEquals(Level.ERROR, logcatSymbolToLevel('E'))
    }

    @Test
    fun `fatal F 与未知字符回落 ERROR`() {
        assertEquals(Level.ERROR, logcatSymbolToLevel('F'))
        assertEquals(Level.ERROR, logcatSymbolToLevel('?'))
        assertEquals(Level.ERROR, logcatSymbolToLevel(' '))
    }

    @Test
    fun `kotlin-logging 级别回映 logcat 字符`() {
        assertEquals('V', Level.TRACE.symbolChar())
        assertEquals('D', Level.DEBUG.symbolChar())
        assertEquals('I', Level.INFO.symbolChar())
        assertEquals('W', Level.WARN.symbolChar())
        assertEquals('E', Level.ERROR.symbolChar())
    }

    @Test
    fun `level 数值序单调升序可比较`() {
        // 过滤语义经 Level.toInt() 比较（0/10/20/30/40/50），低于 TRACE 的级别不存在于 logcat 行。
        assert(Level.TRACE.toInt() < Level.DEBUG.toInt())
        assert(Level.DEBUG.toInt() < Level.INFO.toInt())
        assert(Level.INFO.toInt() < Level.WARN.toInt())
        assert(Level.WARN.toInt() < Level.ERROR.toInt())
    }

    @Test
    fun `symbolChar 与 formatted 一致`() {
        val entry =
            LogEntry(
                id = 1,
                timeText = "08-14 10:22:33.123",
                pid = 1,
                tid = 2,
                level = Level.WARN,
                tag = "lua",
                message = "boom",
            )
        assertEquals("08-14 10:22:33.123 1 2 W/lua: boom", entry.formatted())
    }
}