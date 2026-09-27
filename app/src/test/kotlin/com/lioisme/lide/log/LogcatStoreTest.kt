package com.lioisme.lide.log

import io.github.oshai.kotlinlogging.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

/**
 * LogcatStore 数据层契约测试（2026-08-14 LOGCAT L1；2026-08-15 LOGCAT_CORE C4/C6
 * 适配节流发布）。
 *
 * 单例状态经 [LogcatStore.resetForTest] 隔离 + 虚拟时钟注入（[LogcatStore.setClockForTest]）；
 * 采集器（LogcatReader）不经 ensureStarted 启动（JVM 无 logcat 进程），仅测行解析 →
 * 缓冲 → 节流快照发布链路。
 */
class LogcatStoreTest {

    /** 虚拟时钟（毫秒），节流窗口行为可控断言。 */
    private val clock = AtomicLong(0)

    @Before
    fun setUp() {
        LogcatStore.resetForTest()
        LogcatStore.setClockForTest { clock.get() }
    }

    /** 追加若干行并强制发布（窗口未到期时断言用）。 */
    private fun append(vararg lines: String) {
        lines.forEach { LogcatStore.appendRawLine(it) }
        LogcatStore.flushForTest()
    }

    @Test
    fun `标准行解析并发布到快照`() {
        append("08-14 10:22:33.123  1234  5678  I lua: hello")
        val entries = LogcatStore.entries.value
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals(0L, entry.id)
        assertEquals("lua", entry.tag)
        assertEquals("hello", entry.message)
        assertEquals(Level.INFO, entry.level)
        assertTrue(!entry.truncated)
    }

    @Test
    fun `id 单调递增`() {
        append(
            "08-14 10:22:33.123  1  2  I lua: a",
            "08-14 10:22:33.124  1  2  I lua: b",
            "08-14 10:22:33.125  1  2  I lua: c",
        )
        assertEquals(listOf(0L, 1L, 2L), LogcatStore.entries.value.map { it.id })
    }

    @Test
    fun `续行归并到上一条目`() {
        append("08-14 10:22:33.123  1  2  E lua: boom", "    at com.foo(bar.lua:42)")
        val entry = LogcatStore.entries.value.single()
        assertEquals("boom\n    at com.foo(bar.lua:42)", entry.message)
    }

    @Test
    fun `缓冲分节头与孤儿续行丢弃`() {
        append("----- beginning of main", "orphan continuation")
        assertTrue(LogcatStore.entries.value.isEmpty())
    }

    @Test
    fun `超长消息截断并标记 truncated`() {
        val long = "x".repeat(20_000)
        append("08-14 10:22:33.123  1  2  I lua: $long")
        val entry = LogcatStore.entries.value.single()
        assertEquals(16_384, entry.message.length)
        assertTrue(entry.truncated)
    }

    @Test
    fun `正常消息不标记 truncated`() {
        append("08-14 10:22:33.123  1  2  I lua: hello")
        assertTrue(!LogcatStore.entries.value.single().truncated)
    }

    @Test
    fun `clear 清空内存缓冲`() {
        append("08-14 10:22:33.123  1  2  I lua: a")
        LogcatStore.clear()
        assertTrue(LogcatStore.entries.value.isEmpty())
        assertNull(LogcatStore.entryById(0L))
    }

    @Test
    fun `entryById 命中`() {
        append("08-14 10:22:33.123  1  2  I lua: a")
        assertEquals("a", LogcatStore.entryById(0L)?.message)
        assertNull(LogcatStore.entryById(99L))
    }

    @Test
    fun `环形上限溢出丢最旧`() {
        // 上限 3000：写入 3001 条后首条被丢弃，最新一条仍在。
        for (i in 0..LogcatStore.DEFAULT_CAPACITY) {
            LogcatStore.appendRawLine("08-14 10:22:33.123  1  2  I lua: line$i")
        }
        LogcatStore.flushForTest()
        val entries = LogcatStore.entries.value
        assertEquals(LogcatStore.DEFAULT_CAPACITY, entries.size)
        assertEquals(1L, entries.first().id)
        assertEquals(LogcatStore.DEFAULT_CAPACITY.toLong(), entries.last().id)
    }

    @Test
    fun `首条立即发布`() {
        clock.set(0)
        LogcatStore.appendRawLine("08-14 10:22:33.123  1  2  I lua: a")
        assertEquals(listOf(0L), LogcatStore.entries.value.map { it.id })
    }

    @Test
    fun `节流窗口内不发布窗口到期发布`() {
        clock.set(0)
        LogcatStore.appendRawLine("08-14 10:22:33.123  1  2  I lua: a")
        assertEquals(listOf(0L), LogcatStore.entries.value.map { it.id })

        // 窗口内（+50ms）：入缓冲但不发布。
        clock.set(50)
        LogcatStore.appendRawLine("08-14 10:22:33.124  1  2  I lua: b")
        assertEquals(listOf(0L), LogcatStore.entries.value.map { it.id })

        // 窗口到期（+100ms）：合并发布 a+b+c。
        clock.set(100)
        LogcatStore.appendRawLine("08-14 10:22:33.125  1  2  I lua: c")
        assertEquals(listOf(0L, 1L, 2L), LogcatStore.entries.value.map { it.id })
    }
}