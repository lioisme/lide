package com.lioisme.lide.project

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * WP-005 文件树操作纯逻辑测试：名称校验 / 去重 / 锚点保护 / 越界防护 /
 * 新建 / 重命名 / 删除（临时目录，无 Android 依赖）。
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

    // ---- 新建 ----

    @Test
    fun `create file and directory`() {
        assertTrue(ProjectEntryOps.createFile(root, root.absolutePath, "new.lua"))
        assertTrue(File(root, "new.lua").isFile)
        assertTrue(ProjectEntryOps.createDirectory(root, root.absolutePath, "assets"))
        assertTrue(File(root, "assets").isDirectory)
    }

    @Test
    fun `create refuses duplicate name`() {
        assertTrue(ProjectEntryOps.createFile(root, root.absolutePath, "main.lua"))
        // 同名已存在 → 去重为 -1（不覆盖原文件）
        val created = File(root, "main.lua-1")
        assertTrue(created.isFile)
        assertTrue(File(root, "main.lua").readText().contains("-- main"))
    }

    @Test
    fun `create rejects invalid name`() {
        assertFalse(ProjectEntryOps.createFile(root, root.absolutePath, "bad/name"))
        assertFalse(ProjectEntryOps.createDirectory(root, root.absolutePath, ".."))
        assertFalse(File(root, "bad").exists())
    }

    @Test
    fun `create rejects path outside root`() {
        val outside = File.createTempFile("outside", "").apply { delete() }.also { it.mkdirs() }
        try {
            assertFalse(ProjectEntryOps.createFile(root, outside.absolutePath, "x.lua"))
        } finally {
            outside.deleteRecursively()
        }
    }

    // ---- 重命名 ----

    @Test
    fun `rename file updates path`() {
        File(root, "b.lua").writeText("x")
        val finalName = ProjectEntryOps.renameEntry(root, File(root, "b.lua").absolutePath, "c.lua")
        assertEquals("c.lua", finalName)
        assertFalse(File(root, "b.lua").exists())
        assertTrue(File(root, "c.lua").isFile)
    }

    @Test
    fun `rename to existing name dedupes`() {
        File(root, "b.lua").writeText("x")
        File(root, "c.lua").writeText("y")
        val finalName = ProjectEntryOps.renameEntry(root, File(root, "b.lua").absolutePath, "c.lua")
        assertEquals("c.lua-1", finalName)
        assertTrue(File(root, "c.lua").readText() == "y")
        assertTrue(File(root, "c.lua-1").readText() == "x")
    }

    @Test
    fun `rename rejects anchor`() {
        assertNull(ProjectEntryOps.renameEntry(root, File(root, "main.lua").absolutePath, "m2.lua"))
        assertTrue(File(root, "main.lua").exists())
    }

    @Test
    fun `rename rejects root dir itself`() {
        assertNull(ProjectEntryOps.renameEntry(root, root.absolutePath, "other"))
        assertTrue(root.isDirectory)
    }

    // ---- 删除 ----

    @Test
    fun `delete file and empty dir`() {
        File(root, "d.lua").writeText("x")
        assertTrue(ProjectEntryOps.deleteEntry(root, File(root, "d.lua").absolutePath))
        assertFalse(File(root, "d.lua").exists())
        assertTrue(ProjectEntryOps.deleteEntry(root, File(root, "sub").absolutePath))
        assertFalse(File(root, "sub").exists())
    }

    @Test
    fun `delete refuses anchor file`() {
        assertFalse(ProjectEntryOps.deleteEntry(root, File(root, "main.lua").absolutePath))
        assertTrue(File(root, "main.lua").exists())
    }

    @Test
    fun `delete refuses dir containing anchors`() {
        val nested = File(root, "nested").apply { mkdirs() }
        File(nested, "main.lua").writeText("-- m")
        assertFalse(ProjectEntryOps.deleteEntry(root, nested.absolutePath))
        assertTrue(nested.exists())
        assertTrue(File(nested, "main.lua").exists())
    }

    @Test
    fun `delete dir without anchors succeeds`() {
        val nested = File(root, "nested").apply { mkdirs() }
        File(nested, "a.lua").writeText("-- a")
        assertTrue(ProjectEntryOps.deleteEntry(root, nested.absolutePath))
        assertFalse(nested.exists())
    }

    @Test
    fun `delete rejects outside root`() {
        val outside = File.createTempFile("outside2", "").apply { delete() }.also { it.mkdirs() }
        try {
            assertFalse(ProjectEntryOps.deleteEntry(root, outside.absolutePath))
            assertTrue(outside.exists())
        } finally {
            outside.deleteRecursively()
        }
    }
}
