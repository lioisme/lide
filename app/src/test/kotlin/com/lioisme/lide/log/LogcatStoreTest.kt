package com.lioisme.lide.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import io.github.oshai.kotlinlogging.Level

/**
 * LogcatStore 数据层契约测试（2026-08-14 LOGCAT L1）。
 *
 * 单例状态经 [LogcatStore.resetForTest] 隔离；采集器（LogcatReader）不经
 * ensureStarted 启动（JVM 无 logcat 进程），仅测行解析 → 缓冲 → 快照发布链路。
 */
class LogcatStoreTest {

    @Before
    fun setUp() {
        LogcatStore.resetForTest()
    }

    @Test
    fun `标准行解析并发布到快照`() {
        LogcatStore.appendRawLine("08-14 10:22:33.123  1234  5678  I lua: hello")
        val entries = LogcatStore.entries.value
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals(0L, entry.id)
        assertEquals("lua", entry.tag)
        assertEquals("hello", entry.message)
        assertEquals(Level.INFO, entry.level)
    }

    @Test
    fun `id 单调递增`() {
        LogcatStore.appendRawLine("08-14 10:22:33.123  1  2  I lua: a")
        LogcatStore.appendRawLine("08-14 10:22:33.124  1  2  I lua: b")
        LogcatStore.appendRawLine("08-14 10:22:33.125  1  2  I lua: c")
        assertEquals(listOf(0L, 1L, 2L), LogcatStore.entries.value.map { it.id })
    }

    @Test
    fun `续行归并到上一条目`() {
        LogcatStore.appendRawLine("08-14 10:22:33.123  1  2  E lua: boom")
        LogcatStore.appendRawLine("    at com.foo(bar.lua:42)")
        val entry = LogcatStore.entries.value.single()
        assertEquals("boom\n    at com.foo(bar.lua:42)", entry.message)
    }

    @Test
    fun `缓冲分节头与孤儿续行丢弃`() {
        LogcatStore.appendRawLine("----- beginning of main")
        LogcatStore.appendRawLine("orphan continuation")
        assertTrue(LogcatStore.entries.value.isEmpty())
    }

    @Test
    fun `超长消息截断`() {
        val long = "x".repeat(20_000)
        LogcatStore.appendRawLine("08-14 10:22:33.123  1  2  I lua: $long")
        assertEquals(16_384, LogcatStore.entries.value.single().message.length)
    }

    @Test
    fun `clear 清空内存缓冲`() {
        LogcatStore.appendRawLine("08-14 10:22:33.123  1  2  I lua: a")
        LogcatStore.clear()
        assertTrue(LogcatStore.entries.value.isEmpty())
        assertNull(LogcatStore.entryById(0L))
    }

    @Test
    fun `entryById 命中`() {
        LogcatStore.appendRawLine("08-14 10:22:33.123  1  2  I lua: a")
        assertEquals("a", LogcatStore.entryById(0L)?.message)
        assertNull(LogcatStore.entryById(99L))
    }

    @Test
    fun `环形上限溢出丢最旧`() {
        // 上限 3000：写入 3001 条后首条被丢弃，最新一条仍在。
        for (i in 0..LogcatStore.DEFAULT_CAPACITY) {
            LogcatStore.appendRawLine("08-14 10:22:33.123  1  2  I lua: line$i")
        }
        val entries = LogcatStore.entries.value
        assertEquals(LogcatStore.DEFAULT_CAPACITY, entries.size)
        assertEquals(1L, entries.first().id)
        assertEquals(LogcatStore.DEFAULT_CAPACITY.toLong(), entries.last().id)
    }
}
