package com.lioisme.lide.log

import io.github.oshai.kotlinlogging.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LogBuffer 契约测试（2026-08-15 LOGCAT_CORE C3/C6）。
 *
 * 覆盖：容量硬上限 / 字节预算淘汰 / 归并分片（builder）正确性 /
 * 续行超限截断 + truncated 标记 / 孤儿续行 / 快照与按 id 查找的固化语义。
 */
class LogBufferTest {

    private fun entry(
        id: Long,
        message: String = "m$id",
        tag: String = "t",
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
    fun `容量硬上限丢最旧`() {
        val buffer = LogBuffer(capacity = 3)
        repeat(5) { buffer.append(entry(it.toLong())) }
        assertEquals(listOf(2L, 3L, 4L), buffer.snapshot().map { it.id })
    }

    @Test
    fun `字节预算淘汰丢最旧`() {
        // 单条成本 = message(60) + tag(1) + timeText(17) = 78；预算 100 → 至多 1 条。
        val buffer = LogBuffer(capacity = 100, charBudget = 100)
        repeat(10) { buffer.append(entry(it.toLong(), message = "x".repeat(60))) }
        val snapshot = buffer.snapshot()
        assertEquals(1, snapshot.size)
        assertEquals(9L, snapshot.single().id)
    }

    @Test
    fun `归并续行正确且索引同步重建`() {
        val buffer = LogBuffer(10)
        buffer.append(entry(0, message = "boom"))
        assertTrue(buffer.appendContinuation("    at com.foo(bar.lua:42)"))
        val e = buffer.snapshot().single()
        assertEquals("boom\n    at com.foo(bar.lua:42)", e.message)
        assertEquals("boom\n    at com.foo(bar.lua:42)", e.messageLower)
        assertFalse(e.truncated)
    }

    @Test
    fun `续行超限丢弃并标记截断`() {
        val buffer = LogBuffer(10)
        buffer.append(entry(0, message = "boom"))
        val longLine = "y".repeat(LogcatStore.MAX_MESSAGE_LENGTH)
        assertTrue(buffer.appendContinuation(longLine))
        val e = buffer.snapshot().single()
        assertEquals("boom", e.message)
        assertTrue(e.truncated)
    }

    @Test
    fun `孤儿续行不生效`() {
        val buffer = LogBuffer(10)
        assertFalse(buffer.appendContinuation("orphan"))
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun `快照固化未完成归并`() {
        val buffer = LogBuffer(10)
        buffer.append(entry(0, message = "a"))
        buffer.appendContinuation("b")
        buffer.appendContinuation("c")
        assertEquals("a\nb\nc", buffer.snapshot().single().message)
    }

    @Test
    fun `entryById 固化未完成归并`() {
        val buffer = LogBuffer(10)
        buffer.append(entry(0, message = "a"))
        buffer.appendContinuation("b")
        assertEquals("a\nb", buffer.entryById(0L)?.message)
    }

    @Test
    fun `clear 清空缓冲与记账`() {
        val buffer = LogBuffer(10)
        buffer.append(entry(0, message = "a"))
        buffer.appendContinuation("b")
        buffer.clear()
        assertTrue(buffer.isEmpty())
        assertEquals(emptyList<LogEntry>(), buffer.snapshot())
    }
}