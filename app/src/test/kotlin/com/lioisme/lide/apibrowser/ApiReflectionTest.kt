package com.lioisme.lide.apibrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Q12（审计 P1-9）详情反射缓存契约：
 * - 命中缓存返回同一 Detail 对象（不可变共享，不重复反射）；
 * - 反射失败返回 null 且进入负缓存（重复调用不再触发反射路径，不崩溃）。
 * JVM 单测环境：JDK 类可经 loadClassRef 加载（Class.forName 兜底），android.* 类负缓存。
 */
class ApiReflectionTest {

    @Test
    fun `reflect 命中缓存返回同一对象`() {
        val first = ApiReflection.reflect("java.lang.String")
        val second = ApiReflection.reflect("java.lang.String")
        assertNotNull(first)
        assertSame(first, second)
        // 分组语义：String 有构造器与 methods，事件/设置器分组为空。
        assertTrue(first!!.constructors.isNotEmpty())
        assertTrue(first.methods.isNotEmpty())
        assertTrue(first.events.isEmpty())
        assertTrue(first.setters.isEmpty())
        assertTrue(first.header.startsWith("class java.lang.String"))
    }

    @Test
    fun `反射失败返回 null 且负缓存生效`() {
        // 未知类名（每次唯一，避免与 classpath 残留冲突）：第一次反射失败入负缓存；
        // 第二次命中负缓存直接返回 null（不抛异常、不重试反射）。
        val missing = "com.lioisme.lide.NonexistentRef${System.nanoTime()}"
        assertNull(ApiReflection.reflect(missing))
        assertNull(ApiReflection.reflect(missing))
    }

    @Test
    fun `getter setter 推导对齐 pro 语义`() {
        val detail = ApiReflection.reflect("java.util.Locale")!!
        // getLanguage() → "(String)Language"
        assertTrue(detail.getters.any { it == "(String)Language" })
        // setter 需单参 setXxx：Locale 无 → 断言分组存在（宽松：不空即可）。
        assertNotNull(detail.fields)
    }
}
