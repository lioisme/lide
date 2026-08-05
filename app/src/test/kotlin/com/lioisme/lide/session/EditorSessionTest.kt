package com.lioisme.lide.session

import com.lioisme.lide.model.Project
import com.lioisme.lide.project.ProjectManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * EditorSession 缓存契约测试（LRU 上限 / 逐出保护 / 缓存未命中重载）：
 * 经构造参数注入虚拟调度器，主线程状态写与文件 IO 走同一虚拟调度器，
 * 无真实线程竞态，时序由 [advanceUntilIdle] 精确控制。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorSessionTest {
    private val dispatcher = StandardTestDispatcher()

    private lateinit var root: File
    private lateinit var project: Project
    private lateinit var session: EditorSession

    @Before
    fun setUp() {
        root = File.createTempFile("lidesession", "").apply { delete() }.also { it.mkdirs() }
        File(root, "main.lua").writeText("-- main.lua")
        project = Project(name = "t", path = root.absolutePath)
        session =
            EditorSession(
                project = project,
                projectManager = ProjectManager(),
                mainDispatcher = dispatcher,
                ioDispatcher = dispatcher,
            )
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun writeFiles(count: Int) {
        repeat(count) { i ->
            val name = "f%02d.lua".format(i + 1)
            File(root, name).writeText("-- $name")
        }
    }

    private fun openFirst(count: Int) =
        runTest(dispatcher.scheduler) {
            writeFiles(count)
            session.initialize()
            repeat(count) { i ->
                session.openTab("f%02d.lua".format(i + 1))
            }
            advanceUntilIdle()
        }

    /** 打开 13 个标签（超上限 12）：最久未用且未选中未脏的标签被逐出。 */
    private fun openBeyondCap() = openFirst(13)

    @Test
    fun `initialize loads main file and caches content`() =
        runTest(dispatcher.scheduler) {
            writeFiles(2)
            session.initialize()

            assertEquals("main.lua", session.selection.value.file)
            val content = session.selection.value.content
            assertNotNull(content)
            assertEquals("-- main.lua", content?.toString())
            assertEquals(listOf("main.lua"), session.tabs.value.map { it.fileName })
        }

    @Test
    fun `cache miss reloads evicted tab`() =
        runTest(dispatcher.scheduler) {
            openBeyondCap()

            // f01 最早打开且此后未选中 → 逐出后缓存未命中：先置空，重载完成再落位。
            session.select("f01.lua")
            assertEquals("f01.lua", session.selection.value.file)
            assertNull(session.selection.value.content)

            advanceUntilIdle()
            val reloaded = session.selection.value.content
            assertEquals("-- f01.lua", reloaded?.toString())

            // f13 最近打开 → 缓存命中：同步切换，无需推进。
            session.select("f13.lua")
            assertNotNull(session.selection.value.content)
        }

    @Test
    fun `dirty tab is never evicted`() =
        runTest(dispatcher.scheduler) {
            openBeyondCap()

            // 把 f03 置脏（选中 + 编辑事件），再开第 14 个标签触发新一轮逐出。
            session.select("f03.lua")
            session.onContentModified()
            session.openTab("f14.lua")
            advanceUntilIdle()

            // 脏标签 f03 必须仍在缓存：同步命中。
            session.select("f03.lua")
            assertNotNull(session.selection.value.content)
        }

    @Test
    fun `stale reload result is discarded when selection moved on`() =
        runTest(dispatcher.scheduler) {
            openBeyondCap()

            // 触发已逐出 f01 的重载，但在其完成前切到缓存命中的 f13。
            session.select("f01.lua")
            session.select("f13.lua")
            advanceUntilIdle()

            assertEquals("f13.lua", session.selection.value.file)
            assertNotNull(session.selection.value.content)

            // f01 的迟到重载已丢弃且未滞留缓存：再次选中仍走重载路径（先置空）。
            session.select("f01.lua")
            assertNull(session.selection.value.content)
        }
}
