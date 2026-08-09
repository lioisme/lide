package com.lioisme.lide.project

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Q9（审计 P1-2）工程管理器参数化测试：luaExtDir 经参数注入后，
 * 列表扫描 / 新建 / 删除 / 解析回落 全链路可在临时目录验证（无 LuaApplication 依赖）。
 */
class ProjectManagerTest {
    private lateinit var luaExtDir: File
    private val manager = ProjectManager()

    @Before
    fun setUp() {
        luaExtDir = File.createTempFile("lideman", "").apply { delete() }.also { it.mkdirs() }
        File(luaExtDir, "project").mkdirs()
    }

    @After
    fun tearDown() {
        luaExtDir.deleteRecursively()
    }

    private fun makeProject(name: String, withInit: Boolean = true) {
        val dir = File(luaExtDir, "project/$name").apply { mkdirs() }
        File(dir, "main.lua").writeText("-- main of $name")
        if (withInit) {
            File(dir, "init.lua").writeText("appname = \"$name\"\npackagename = \"com.lioisme.lide.$name\"")
        }
    }

    // ---- 列表扫描 ----

    @Test
    fun `empty dir scans to empty list`() {
        assertTrue(manager.listProjects(luaExtDir.absolutePath).isEmpty())
    }

    @Test
    fun `project with main and init is listed`() {
        makeProject("foo")
        val projects = manager.listProjects(luaExtDir.absolutePath)
        assertEquals(1, projects.size)
        val p = projects.first()
        assertEquals("foo", p.name)
        assertEquals(File(luaExtDir, "project/foo").absolutePath, p.path)
        assertEquals("com.lioisme.lide.foo", p.packageName)
    }

    @Test
    fun `dir without init lua is filtered`() {
        makeProject("hasInit")
        makeProject("noInit", withInit = false)
        val projects = manager.listProjects(luaExtDir.absolutePath)
        assertEquals(listOf("hasInit"), projects.map { it.name })
    }

    @Test
    fun `project with invalid init lua falls back to dir name`() {
        val dir = File(luaExtDir, "project/broken").apply { mkdirs() }
        File(dir, "main.lua").writeText("-- main")
        File(dir, "init.lua").writeText("this is { not valid lua =")
        val projects = manager.listProjects(luaExtDir.absolutePath)
        assertEquals(1, projects.size)
        assertEquals("broken", projects.first().name)
    }

    // ---- 新建 / 删除 ----

    @Test
    fun `create project writes files and is listable`() {
        val p = manager.createProject("hello", luaExtDir.absolutePath)
        assertNotNull(p)
        assertEquals("hello", p!!.name)
        assertTrue(File(p.path, "main.lua").exists())
        assertTrue(File(p.path, "init.lua").exists())
        val listed = manager.listProjects(luaExtDir.absolutePath)
        assertEquals(listOf("hello"), listed.map { it.name })
    }

    @Test
    fun `create project with path separator fails`() {
        assertNull(manager.createProject("a/b", luaExtDir.absolutePath))
        assertNull(manager.createProject("..", luaExtDir.absolutePath))
    }

    @Test
    fun `delete project removes dir and cache`() {
        val p = manager.createProject("gone", luaExtDir.absolutePath)!!
        assertTrue(File(p.path).exists())
        assertTrue(manager.deleteProject(p))
        assertFalse(File(p.path).exists())
        assertTrue(manager.listProjects(luaExtDir.absolutePath).isEmpty())
    }

    // ---- 读写 ----

    @Test
    fun `script read returns null instead of placeholder`() {
        val p = manager.createProject("rw", luaExtDir.absolutePath)!!
        assertEquals("-- rw\nprint(\"hello from rw!\")\n", manager.getProjectScript(p, "main.lua"))
        assertNull(manager.getProjectScript(p, "missing.lua"))
    }

    @Test
    fun `script save and reload roundtrip`() {
        val p = manager.createProject("rw2", luaExtDir.absolutePath)!!
        manager.saveProjectScript(p, "main.lua", "print(42)")
        assertEquals("print(42)", manager.getProjectScript(p, "main.lua"))
        assertTrue(File(p.path, "main.lua").readText().contains("print(42)"))
    }

    @Test
    fun `rename project updates dir and cache`() {
        val p = manager.createProject("oldName", luaExtDir.absolutePath)!!
        val renamed = manager.renameProject(p, "newName")
        assertNotNull(renamed)
        assertEquals("newName", renamed!!.name)
        val listed = manager.listProjects(luaExtDir.absolutePath)
        assertEquals(listOf("newName"), listed.map { it.name })
        assertTrue(File(luaExtDir, "project/newName").isDirectory)
    }
}
