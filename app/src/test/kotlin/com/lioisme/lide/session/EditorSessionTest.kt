package com.lioisme.lide.session

import com.lioisme.lide.model.Project
import com.lioisme.lide.project.ProjectManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
            assertEquals("-- main.lua", content?.snapshot())
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
            assertEquals("-- f01.lua", reloaded?.snapshot())

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

    @Test
    fun `flushAllDirty 落盘全部脏标签而非仅当前选中`() =
        runTest(dispatcher.scheduler) {
            // T3 回归（2026-08-10）：自动保存防抖只落盘选中标签时，
            // 「编辑 A → 切到 B → 进程被杀」路径会丢 A 的修改；
            // flushAllDirty 必须把 A/B 全部落盘。
            writeFiles(2)
            session.initialize()
            session.openTab("f02.lua")

            // 等价于「编辑 A(main.lua) 后切到 B(f02)」：A 先置脏，再切换并编辑 B。
            session.select("main.lua")
            session.onContentModified()
            session.select("f02.lua")
            session.insertText(0, 0, "-- edited f02\n")
            advanceUntilIdle()

            assertEquals(
                "两个标签都应处于脏状态",
                2,
                session.tabs.value.count { it.dirty },
            )

            session.flushAllDirty()
            advanceUntilIdle()

            assertEquals("落盘后全部脏标记清除", 0, session.tabs.value.count { it.dirty })
            assertEquals("-- edited f02\n-- f02.lua", File(root, "f02.lua").readText())
        }

    @Test
    fun `onUndoRedoChanged 值未变化时抑制发射`() =
        runTest(dispatcher.scheduler) {
            // T2 回归（2026-08-10）：undo/redo 能力仅在值实际变化时发射
            // （needsUpdate 判定），重复上报不触发重组。
            writeFiles(1)
            session.initialize()
            var changes = 0
            val job =
                launch(dispatcher) {
                    session.tabs.collect { tabs ->
                        val tab = tabs.firstOrNull { it.fileName == "main.lua" } ?: return@collect
                        if (tab.canUndo || tab.canRedo) changes++
                    }
                }
            // 先让 collector 完成订阅（收到初始 canUndo=false 不计数），
            // 再逐次提交并 runCurrent() 冲刷，保证 StateFlow 每次发射确定性送达
            // （同步连续 set 会与订阅竞态，晚订阅者只能收到最新值）。
            runCurrent()
            session.onUndoRedoChanged(true, false)
            runCurrent()
            session.onUndoRedoChanged(true, false)
            runCurrent()
            session.onUndoRedoChanged(false, true)
            runCurrent()
            job.cancel()

            assertEquals("两次真实变化发射，重复值被抑制", 2, changes)
        }
}
