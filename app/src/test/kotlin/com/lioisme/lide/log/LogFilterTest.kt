package com.lioisme.lide.log

import io.github.oshai.kotlinlogging.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 过滤契约测试（2026-08-15 LOGCAT_CORE C5/C6）。
 *
 * 覆盖：小写预索引正确性（构造期一次 lowercase）/ 查询词大小写不敏感
 * （tag + message 子串）/ 类型筛选 ∩ 查询词交集 / 空查询零拷贝 /
 * 查询词修剪。
 */
class LogFilterTest {

    private fun entry(
        id: Long,
        tag: String = "lua",
        message: String = "hello World",
    ): LogEntry =
        LogEntry(
            id = id,
            timeText = "08-14 10:22:33.123",
            pid = 1,
            tid = 2,
            level = Level.INFO,
            tag = tag,
            message = message,
        )

    @Test
    fun `小写预索引与手算一致`() {
        val entries =
            listOf(
                entry(0, tag = "Lua", message = "Hello World"),
                entry(1, tag = "System.out", message = "foo BAR"),
            )
        assertEquals("lua", entries[0].tagLower)
        assertEquals("hello world", entries[0].messageLower)
        assertEquals("system.out", entries[1].tagLower)
        assertEquals("foo bar", entries[1].messageLower)
    }

    @Test
    fun `查询词大小写不敏感命中 tag 与 message`() {
        val entries =
            listOf(
                entry(0, tag = "lua", message = "layout loaded"),
                entry(1, tag = "System.out", message = "GC start"),
            )
        assertEquals(listOf(0L), filterEntries(entries, LogFilter.ALL, "LAYOUT").map { it.id })
        assertEquals(listOf(1L), filterEntries(entries, LogFilter.ALL, "system").map { it.id })
    }

    @Test
    fun `类型筛选与查询词取交集`() {
        val entries =
            listOf(
                entry(0, tag = "lua", message = "boom"),
                entry(1, tag = "test", message = "boom"),
            )
        assertEquals(
            listOf(0L),
            filterEntries(entries, LogFilter.LUA, "boom").map { it.id },
        )
        assertEquals(emptyList<Long>(), filterEntries(entries, LogFilter.LUA, "zzz").map { it.id })
    }

    @Test
    fun `级别筛选阈值`() {
        val entries =
            listOf(
                entry(0).copy(level = Level.TRACE),
                entry(1).copy(level = Level.WARN),
                entry(2).copy(level = Level.ERROR),
            )
        assertEquals(
            listOf(1L, 2L),
            filterEntries(entries, LogFilter.WARNING, "").map { it.id },
        )
    }

    @Test
    fun `空查询与 ALL 直接返回原表`() {
        val entries = listOf(entry(0), entry(1))
        assertSame(entries, filterEntries(entries, LogFilter.ALL, ""))
    }

    @Test
    fun `查询词空格修剪`() {
        val entries = listOf(entry(0, message = "hello"))
        assertEquals(
            listOf(0L),
            filterEntries(entries, LogFilter.ALL, "  hello  ").map { it.id },
        )
    }
}