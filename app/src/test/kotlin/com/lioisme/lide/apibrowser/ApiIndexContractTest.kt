package com.lioisme.lide.apibrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ApiIndex 可加载性契约测试（技术债审计 D1 落地，2026-08-09）。
 *
 * 契约（AGENTS.md「API 浏览器反射线程契约」）：
 * - 索引构建期把枚举出的全部类名存为快照（dexNames + entryNames），
 *   「可加载性」判定为 O(1) 查表，只有已知类才发生 Class.forName——
 *   不存在 Class.forName 全 dex 负查找；
 * - 反射加载统一经 loadClassRef（单张合并缓存 + 负缓存）。
 *
 * 本测试覆盖：
 * 1. [ApiIndex.Snapshot.isKnown] 纯逻辑：entryNames / dexNames 双向命中、
 *    未知类名与内部类名判定正确；
 * 2. [ApiIndex.loadClassRef]：已知 JVM 类加载成功（索引未建时不设门控），
 *    未知类返回 null 且幂等（负缓存生效，同名二次调用不再解析）。
 */
class ApiIndexContractTest {
    private fun snapshot(
        entryNames: Set<String> = emptySet(),
        dexNames: Set<String> = emptySet(),
    ) = ApiIndex.Snapshot(
        classes =
            entryNames.map {
                ApiIndex.ApiClassEntry(it, ApiCategory.ANDROLUA, fromProject = false)
            },
        nativeModules = emptyList(),
        nameLower = entryNames.toList(),
        categories = emptyMap(),
        categoryCounts = emptyMap(),
        dexNames = dexNames,
        entryNames = entryNames,
    )

    @Test
    fun `isKnown 命中静态表条目与 dex 枚举类名`() {
        val s =
            snapshot(
                entryNames = setOf("android.widget.ListView", "com.androlua.LuaActivity"),
                dexNames = setOf("com.google.android.material.button.MaterialButton", "android.R"),
            )
        assertTrue("静态表条目应命中 entryNames", s.isKnown("android.widget.ListView"))
        assertTrue("dex 枚举类名应命中 dexNames", s.isKnown("com.google.android.material.button.MaterialButton"))
        assertTrue("dexNames 与 entryNames 交集类名应命中", s.isKnown("android.R"))
    }

    @Test
    fun `isKnown 未知类名与内部类判定`() {
        val s =
            snapshot(
                entryNames = setOf("com.lioisme.lide.apibrowser.ApiIndex"),
                dexNames = setOf("com.lioisme.lide.ui.MainActivity", "com.lioisme.lide.ui.MainActivity\$Inner"),
            )
        assertFalse("未知类名必须判定不可加载", s.isKnown("com.lioisme.lide.UnknownT3Class"))
        assertFalse("未登记的内部类名不可加载", s.isKnown("android.widget.ListView\$1"))
        assertTrue("已登记的内部类名可加载", s.isKnown("com.lioisme.lide.ui.MainActivity\$Inner"))
    }

    @Test
    fun `loadClassRef 对已知 JVM 类加载成功`() {
        assertNotNull("索引未建时已知 JVM 类可加载", ApiIndex.loadClassRef("java.lang.String"))
    }

    @Test
    fun `loadClassRef 对未知类返回 null 且幂等`() {
        val missing = "com.lioisme.lide.UnknownT3Ref${System.nanoTime()}"
        assertNull("未知类必须返回 null", ApiIndex.loadClassRef(missing))
        assertNull("负缓存生效：同名二次调用必须仍返回 null", ApiIndex.loadClassRef(missing))
    }

    @Test
    fun `isKnown 与 loadClassRef 语义一致`() {
        val s = snapshot(entryNames = setOf("java.lang.String"))
        assertTrue(s.isKnown("java.lang.String"))
        assertNotNull(ApiIndex.loadClassRef("java.lang.String"))
        assertEquals("String", ApiIndex.loadClassRef("java.lang.String")?.simpleName)
    }

    @Test
    fun `快照分类索引与条目列表对齐`() {
        val names = listOf("com.androlua.LuaActivity", "android.widget.ListView", "java.lang.String")
        val categories = mapOf(ApiCategory.ANDROLUA to listOf(0), ApiCategory.FRAMEWORK to listOf(1), ApiCategory.JAVA to listOf(2))
        val s =
            ApiIndex.Snapshot(
                classes = names.map { ApiIndex.ApiClassEntry(it, ApiCategory.OTHER, fromProject = false) },
                nativeModules = emptyList(),
                nameLower = names.map { it.lowercase() },
                categories = categories,
                categoryCounts = categories.mapValues { it.value.size },
                dexNames = emptySet(),
                entryNames = names.toSet(),
            )
        val allIndices = categories.values.flatten()
        assertTrue("分类索引必须全部落在 classes 范围内", allIndices.all { it in names.indices })
        assertEquals("nameLower 与 classes 同序", names.map { it.lowercase() }, s.nameLower)
    }
}
