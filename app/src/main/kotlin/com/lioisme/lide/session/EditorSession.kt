package com.lioisme.lide.session

import com.lioisme.lide.editor.EditorTab
import com.lioisme.lide.model.Project
import com.lioisme.lide.project.ProjectManager
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 编辑器会话（宿主侧，:app）。
 *
 * 职责：标签列表、选中文件、每文件 [Content] 缓存、脏标记、undo/redo 能力、
 * 自动保存节流信号。对齐 Squircle `EditorViewModel` 的 needsUpdate 模式：
 *
 * - 每键编辑事件只更新脏标记 / undo 能力，且仅在值**实际变化**时 emit——
 *   稳态打字期间 Compose 零重组（首个字符置脏后不再发射）；
 * - 编辑内容实体（[Content]）留在此处缓存，切换标签零读盘、零 O(n) 重建
 *   （编辑器侧 `setText(Content)` 复用路径）；
 * - 高频信号（[editTick]）与低频状态（[tabs] / [selection]）
 *   分离，编辑节奏不触发任何页面重组。
 *
 * 线程模型：标签/选中/脏标记变更只发生在主线程（sora 事件回调与 UI 点击均在主线程）；
 * [Content] 缓存的读写经 [cacheLock] 同步（IO 线程构造/快照与主线程删除并发）。
 * 会话持有独立作用域，屏幕销毁后的兜底保存（onRelease / onBack）不受组合作用域取消影响。
 */
class EditorSession(
    private val project: Project,
    private val projectManager: ProjectManager,
) {

    data class TabState(
        val fileName: String,
        val dirty: Boolean,
        val canUndo: Boolean,
        val canRedo: Boolean,
    ) {
        val tab: EditorTab get() = EditorTab(fileName, dirty)
    }

    /** 当前选中：文件与内容原子发射，文件切换只触发一次重组。 */
    data class Selection(
        val file: String? = null,
        val content: Content? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cacheLock = Any()
    private val contents = LinkedHashMap<String, Content>(16)

    private val _tabs = MutableStateFlow<List<TabState>>(emptyList())
    val tabs: StateFlow<List<TabState>> = _tabs.asStateFlow()

    private val _selection = MutableStateFlow(Selection())
    val selection: StateFlow<Selection> = _selection.asStateFlow()

    /** 自动保存节流信号：每键 +1，不触发重组；宿主经 collectLatest 防抖消费。 */
    private val _editTick = MutableStateFlow(0L)
    val editTick: StateFlow<Long> = _editTick.asStateFlow()

    /** 启动：读文件列表、打开主文件并预载 Content。 */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        val files = projectManager.listProjectFiles(project)
        if (files.isEmpty()) return@withContext
        val main = project.mainFile.takeIf { it in files } ?: files.first()
        val content = loadContent(main)
        _tabs.value = listOf(TabState(main, dirty = false, canUndo = false, canRedo = false))
        _selection.value = Selection(main, content)
    }

    /** 打开（或选中）标签：IO 读盘 + IO 线程构造 Content 后入缓存，再切换。 */
    suspend fun openTab(fileName: String) {
        val content = withContext(Dispatchers.IO) { loadContent(fileName) }
        if (_tabs.value.none { it.fileName == fileName }) {
            _tabs.value = _tabs.value + TabState(fileName, dirty = false, canUndo = false, canRedo = false)
        }
        select(fileName, content)
    }

    /** 切换选中（内容必须已在缓存——所有打开过的标签都满足）。 */
    fun select(fileName: String) {
        val content = synchronized(cacheLock) { contents[fileName] } ?: return
        select(fileName, content)
    }

    /**
     * 拖拽排序（WP-003）：仅重排标签列表；选中按 fileName 语义自动跟随，
     * Content 缓存按文件名键控，重排不触碰。仅主线程调用。
     */
    fun moveTab(from: Int, to: Int) {
        val current = _tabs.value
        if (from !in current.indices || to !in current.indices || from == to) return
        val mutable = current.toMutableList()
        val moved = mutable.removeAt(from)
        mutable.add(to, moved)
        _tabs.value = mutable
    }

    private fun select(fileName: String, content: Content) {
        _selection.value = Selection(fileName, content)
    }

    /**
     * 编辑事件（每键）：脏标记仅在「首次变脏」时发射，其余仅递增节流信号。
     */
    fun onContentModified() {
        val selected = _selection.value.file ?: return
        val current = _tabs.value
        val index = current.indexOfFirst { it.fileName == selected }
        if (index < 0) return
        _editTick.value++
        if (current[index].dirty) return
        _tabs.value = current.toMutableList().apply {
            this[index] = current[index].copy(dirty = true)
        }
    }

    /** undo/redo 能力变化：仅在值变化时发射（needsUpdate 判定）。 */
    fun onUndoRedoChanged(canUndo: Boolean, canRedo: Boolean) {
        val selected = _selection.value.file ?: return
        val current = _tabs.value
        val index = current.indexOfFirst { it.fileName == selected }
        if (index < 0) return
        val tab = current[index]
        if (tab.canUndo == canUndo && tab.canRedo == canRedo) return
        _tabs.value = current.toMutableList().apply {
            this[index] = tab.copy(canUndo = canUndo, canRedo = canRedo)
        }
    }

    /**
     * 请求关闭标签。未脏直接关闭并返回 false；已脏返回 true（由宿主弹确认框）。
     */
    fun requestClose(fileName: String): Boolean {
        val tab = _tabs.value.firstOrNull { it.fileName == fileName } ?: return false
        if (tab.dirty) return true
        close(fileName)
        return false
    }

    /**
     * 关闭除 [anchor] 外的所有标签。非脏标签立即关闭；脏标签不关闭，
     * 作为待确认队列返回，由宿主逐个确认后经 [saveAndClose] / [discardAndClose] 关闭。
     */
    fun closeOthers(anchor: String): List<String> = closeBatch(_tabs.value.filterNot { it.fileName == anchor })

    /** 关闭全部标签（含当前选中）。脏标签返回待确认队列（语义同 [closeOthers]）。 */
    fun closeAll(): List<String> = closeBatch(_tabs.value)

    private fun closeBatch(targets: List<TabState>): List<String> {
        val dirty = targets.filter { it.dirty }.map { it.fileName }
        targets.filterNot { it.dirty }.forEach { close(it.fileName) }
        return dirty
    }

    /** 保存并关闭（宿主确认「保存」）。 */
    fun saveAndClose(fileName: String) {
        scope.launch {
            flushDirty(fileName)
            close(fileName)
        }
    }

    /** 放弃修改并关闭：缓存中的未保存内容必须一并丢弃，防止自动保存复活脏数据。 */
    fun discardAndClose(fileName: String) {
        synchronized(cacheLock) { contents.remove(fileName) }
        close(fileName)
    }

    /** 兜底保存（onRelease / onBack）：在会话作用域上异步执行，不随页面销毁取消。 */
    fun flushSelectedDirtyAsync() {
        flushDirtyAsync(_selection.value.file)
    }

    /** 自动保存 / 运行前落盘：刷新当前选中标签的脏内容。 */
    suspend fun flushSelectedDirty() {
        flushDirty(_selection.value.file ?: return)
    }

    fun flushDirtyAsync(fileName: String?) {
        if (fileName == null) return
        scope.launch { flushDirty(fileName) }
    }

    /** 保存指定标签的脏内容（快照在 IO 线程获取，Content.toString 持有 ReadWriteLock）。 */
    suspend fun flushDirty(fileName: String) {
        val tab = _tabs.value.firstOrNull { it.fileName == fileName } ?: return
        if (!tab.dirty) return
        val content = synchronized(cacheLock) { contents[fileName] } ?: return
        val saved = withContext(Dispatchers.IO) {
            projectManager.saveProjectScript(project, fileName, content.toString())
        }
        if (saved) {
            val current = _tabs.value
            val index = current.indexOfFirst { it.fileName == fileName }
            if (index >= 0 && current[index].dirty) {
                _tabs.value = current.toMutableList().apply {
                    this[index] = current[index].copy(dirty = false)
                }
            }
        }
    }

    /** 关闭标签；关闭的是选中标签时自动切换到相邻标签。 */
    private fun close(fileName: String) {
        val current = _tabs.value
        val index = current.indexOfFirst { it.fileName == fileName }
        if (index < 0) return
        val nextTabs = current.filterNot { it.fileName == fileName }
        _tabs.value = nextTabs
        synchronized(cacheLock) { contents.remove(fileName) }
        if (_selection.value.file == fileName) {
            val next = nextTabs.getOrNull(index.coerceAtMost(nextTabs.size - 1))
            if (next != null) {
                select(next.fileName)
            } else {
                _selection.value = Selection()
            }
        }
    }

    private fun loadContent(fileName: String): Content {
        synchronized(cacheLock) {
            contents[fileName]?.let { return it }
        }
        val text = projectManager.getProjectScript(project, fileName)
        val content = Content(text)
        synchronized(cacheLock) {
            contents[fileName] = content
        }
        return content
    }
}
