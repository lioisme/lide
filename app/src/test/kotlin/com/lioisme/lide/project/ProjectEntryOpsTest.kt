package com.lioisme.lide.project

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 工程文件系统防护逻辑测试（DEAD_CODE_CLEANUP T3 收缩，2026-08-12）：
 * 名称校验 / 去重 / 越界防护（临时目录，无 Android 依赖）。
 * createFile / createDirectory / renameEntry / deleteEntry 已随文件树占位
 * 代码删除（零生产调用），对应用例一并移除；重建见 backlog D8。
 */
class ProjectEntryOpsTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = File.createTempFile("lidetest", "").apply { delete() }.also { it.mkdirs() }
        File(root, "main.lua").writeText("-- main")
        File(root, "init.lua").writeText("appname = \"t\"")
        File(root, "sub").mkdirs()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    // ---- 名称校验 ----

    @Test
    fun `valid names accepted`() {
        assertTrue(ProjectEntryOps.isValidEntryName("script.lua"))
        assertTrue(ProjectEntryOps.isValidEntryName("my dir"))
        assertTrue(ProjectEntryOps.isValidEntryName("中文名.lua"))
        assertTrue(ProjectEntryOps.isValidEntryName("a-b_c.d"))
    }

    @Test
    fun `invalid names rejected`() {
        assertFalse(ProjectEntryOps.isValidEntryName(""))
        assertFalse(ProjectEntryOps.isValidEntryName("   "))
        assertFalse(ProjectEntryOps.isValidEntryName("."))
        assertFalse(ProjectEntryOps.isValidEntryName(".."))
        assertFalse(ProjectEntryOps.isValidEntryName("a/b"))
        assertFalse(ProjectEntryOps.isValidEntryName("a\\b"))
        assertFalse(ProjectEntryOps.isValidEntryName("a:b"))
        assertFalse(ProjectEntryOps.isValidEntryName("a*b"))
        assertFalse(ProjectEntryOps.isValidEntryName("a\"b"))
        assertFalse(ProjectEntryOps.isValidEntryName("a<b"))
        assertFalse(ProjectEntryOps.isValidEntryName("a>b"))
        assertFalse(ProjectEntryOps.isValidEntryName("a|b"))
        assertFalse(ProjectEntryOps.isValidEntryName("a?b"))
        assertFalse(ProjectEntryOps.isValidEntryName("a\u0000b"))
    }

    // ---- 去重 ----

    @Test
    fun `duplicate name gets -1 suffix`() {
        // 无冲突返回原名；冲突后追加 -1/-2（对齐 .alp 导入策略）。
        assertEquals("a", ProjectEntryOps.dedupeEntryName(root, "a"))
        File(root, "a").mkdirs()
        assertEquals("a-1", ProjectEntryOps.dedupeEntryName(root, "a"))
        File(root, "a-1").mkdirs()
        assertEquals("a-2", ProjectEntryOps.dedupeEntryName(root, "a"))
    }

    // ---- 越界防护 ----

    @Test
    fun `isInsideRoot rejects dotdot traversal`() {
        // S4（2026-08-12）：词法 `..` 段回退后越出根的路径必须判为越界。
        val parent = root.parentFile!!.absolutePath
        assertFalse(ProjectEntryOps.isInsideRoot(root, "$parent/../lidetest2"))
        assertFalse(ProjectEntryOps.isInsideRoot(root, "${root.absolutePath}/../escape"))
        // `..` 未越出根时归一化后仍属内部（如 a/../b → b）。
        assertTrue(ProjectEntryOps.isInsideRoot(root, "${root.absolutePath}/sub/.."))
        assertTrue(ProjectEntryOps.isInsideRoot(root, "${root.absolutePath}/sub/../sub"))
    }

    @Test
    fun `isInsideRoot rejects relative and foreign paths`() {
        assertFalse(ProjectEntryOps.isInsideRoot(root, "relative/path"))
        val other = File.createTempFile("other", "").apply { delete() }.also { it.mkdirs() }
        try {
            assertFalse(ProjectEntryOps.isInsideRoot(root, other.absolutePath))
        } finally {
            other.deleteRecursively()
        }
    }
}
