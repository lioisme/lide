package com.lioisme.lide.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * LogcatReader 纯函数契约测试（2026-08-15 LOGCAT_CORE C2/C6）。
 *
 * 覆盖：`logcatCommand` 首启/续启两态（`-T` 续读防重复）/ `backoffDelayMs`
 * 指数递增（区间不重叠）/ 封顶上限 / 抖动范围。
 */
class ReaderCommandAndBackoffTest {

    @Test
    fun `首次启动不传 -T`() {
        assertEquals(listOf("logcat", "-v", "threadtime"), logcatCommand(null))
    }

    @Test
    fun `续启传最后时间戳`() {
        assertEquals(
            listOf("logcat", "-v", "threadtime", "-T", "08-14 10:22:33.123"),
            logcatCommand("08-14 10:22:33.123"),
        )
    }

    @Test
    fun `退避指数递增且区间不重叠`() {
        val random = Random(42)
        var prev = 0L
        for (attempt in 0..4) {
            val d = backoffDelayMs(attempt, random)
            assertTrue("attempt=$attempt d=$d", d > prev)
            prev = d
        }
    }

    @Test
    fun `退避封顶 30s`() {
        val random = Random(7)
        repeat(5) {
            assertEquals(30_000L, backoffDelayMs(20, random))
        }
    }

    @Test
    fun `退避落在基数区间内`() {
        val expected = mapOf(0 to 2_000L, 1 to 4_000L, 2 to 8_000L, 3 to 16_000L)
        for ((attempt, base) in expected) {
            val d = backoffDelayMs(attempt, Random(attempt))
            assertTrue(
                "attempt=$attempt d=$d base=$base",
                d in base..(base + 1_000L),
            )
        }
    }
}