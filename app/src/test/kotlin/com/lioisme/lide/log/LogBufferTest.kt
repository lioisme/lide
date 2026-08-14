package com.lioisme.lide.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import io.github.oshai.kotlinlogging.Level

/**
 * LogBuffer 环形缓冲契约测试（2026-08-14 LOGCAT L1）。
 */
class LogBufferTest {

    private lateinit var buffer: LogBuffer

    private fun entry(id: Long, message: String = "m$id"): LogEntry =
        LogEntry(
            id = id,
            timeText = "08-14 10:22:33.123",
            pid = 1,
            tid = 2,
            level = Level.INFO,
            tag = "lua",
            message = message,
        )

    @Before
    fun setUp() {
        buffer = LogBuffer(capacity = 3)
    }

    @Test
    fun `追加保持顺序`() {
        buffer.append(entry(0))
        buffer.append(entry(1))
        assertEquals(listOf(0L, 1L), buffer.snapshot().map { it.id })
    }

    @Test
    fun `容量满丢弃最旧`() {
        buffer.append(entry(0))
        buffer.append(entry(1))
        buffer.append(entry(2))
        buffer.append(entry(3))
        assertEquals(listOf(1L, 2L, 3L), buffer.snapshot().map { it.id })
    }

    @Test
    fun `续行归并到最新条目`() {
        buffer.append(entry(0, "first"))
        assertTrue(buffer.appendContinuation("    at com.foo"))
        val last = buffer.snapshot().last()
        assertEquals("first\n    at com.foo", last.message)
    }

    @Test
    fun `空缓冲续行不生效`() {
        assertFalse(buffer.appendContinuation("orphan"))
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun `连续多条续行依次归并`() {
        buffer.append(entry(0, "a"))
        buffer.appendContinuation("b")
        buffer.appendContinuation("c")
        assertEquals("a\nb\nc", buffer.snapshot().last().message)
    }

    @Test
    fun `清空后为空`() {
        buffer.append(entry(0))
        buffer.clear()
        assertTrue(buffer.isEmpty())
        assertEquals(emptyList<LogEntry>(), buffer.snapshot())
    }

    @Test
    fun `按 id 查找命中与未命中`() {
        buffer.append(entry(7))
        assertEquals(7L, buffer.entryById(7)?.id)
        assertNull(buffer.entryById(8))
    }

    @Test
    fun `快照为独立副本`() {
        buffer.append(entry(0))
        val snapshot = buffer.snapshot()
        assertNotSame(snapshot, buffer.snapshot())
        buffer.clear()
        assertEquals(1, snapshot.size)
    }
}