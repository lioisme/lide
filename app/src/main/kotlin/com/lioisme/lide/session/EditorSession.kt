package com.lioisme.lide.session

import com.lioisme.lide.editor.EditorContent
import com.lioisme.lide.editor.EditorTab
import com.lioisme.lide.model.Project
import com.lioisme.lide.project.ProjectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** 打开的标签 [EditorContent] 缓存上限：超出后按 LRU 逐出「未选中且非脏」标签并释放其 undo 栈。 */
private const val CONTENT_CACHE_CAP = 12

/**
 * 编辑器会话（宿主侧，:app）。
 *
 * 职责：标签列表、选中文件、每文件 [EditorContent] 缓存、脏标记、undo/redo 能力、
 * 自动保存节流信号。对齐 Squircle `EditorViewModel` 的 needsUpdate 模式：
 *
 * - 每键编辑事件只更新脏标记 / undo 能力，且仅在值**实际变化**时 emit——
 *   稳态打字期间 Compose 零重组（首个字符置脏后不再发射）；
 * - 编辑内容实体（[EditorContent]）留在此处缓存，切换标签零读盘、零 O(n) 重建
 *   （编辑器侧 `setText(Content)` 复用路径）；
 * - 高频信号（[editTick]）与低频状态（[tabs] / [selection]）
 *   分离，编辑节奏不触发任何页面重组。
 *
 * 线程模型：标签/选中/脏标记变更只发生在主线程（sora 事件回调与 UI 点击均在主线程）；
 * [EditorContent] 缓存的读写经 [cacheLock] 同步（IO 线程构造/快照与主线程删除并发）。
 * 会话持有独立作用域，屏幕销毁后的兜底保存（onRelease / onBack）不受组合作用域取消影响。
 */
class EditorSession(
    private val project: Project,
    private val projectManager: ProjectManager,
    // 线程上下文可注入：默认走「主线程状态写 + IO 线程文件读写」的标准契约，
    // 单测传入虚拟调度器获得确定性时序（无真实 IO 线程竞态）。
    private val mainDispatcher: CoroutineContext = Dispatchers.Main.immediate,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) {
    data class Selection(
        val file: String? = null,
        val content: EditorContent? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val cacheLock = Any()

    // 访问序 LRU：最近使用的排尾部，逐出时从头扫描。
    private val contents = LinkedHashMap<String, EditorContent>(16, 0.75f, true)

    private val _tabs = MutableStateFlow<List<EditorTab>>(emptyList())
    val tabs: StateFlow<List<EditorTab>> = _tabs.asStateFlow()

    private val _selection = MutableStateFlow(Selection())
    val selection: StateFlow<Selection> = _selection.asStateFlow()

    /** 自动保存节流信号：每键 +1，不触发重组；宿主经 collectLatest 防抖消费。 */
    private val _editTick = MutableStateFlow(0L)
    val editTick: StateFlow<Long> = _editTick.asStateFlow()

    /**
     * 启动：读文件列表、打开主文件并预载 Content。
     *
     * 线程契约（Q6 修复）：文件读取在 IO 线程完成；状态写（[_tabs] / [_selection]）
     * 全部发生在主线程，与类文档「标签/选中/脏标记变更只发生在主线程」一致——
     * 消除初始化期间 IO 线程写 StateFlow 与主线程点击的交错窗口。
     *
     * 幂等（Q8）：会话随 ViewModel 跨配置变更存活，重组后宿主仍会调用本方法；
     * 已初始化则直接返回，绝不重复写状态（防旋转后 tab 列表被重置）。
     */
    suspend fun initialize() {
        if (initialized) return
        initialized = true
        val files = withContext(ioDispatcher) { projectManager.listProjectFiles(project) }
        if (files.isEmpty()) return
        val main = project.mainFile.takeIf { it in files } ?: files.first()
        val content = withContext(ioDispatcher) { loadContent(main) }
        _tabs.value = listOf(EditorTab(main, dirty = false))
        _selection.value = Selection(main, content)
    }

    private var initialized = false

    private var released = false

    /**
     * 会话出栈时调用（Q8：经 [EditorSessionViewModel.onCleared] 委托，替代早期
     * DisposableEffect onDispose 路径）：兜底保存全部脏标签，
     * 随后取消会话作用域并清空 [Content] 缓存，防止会话与缓存跨页面滞留。
     *
     * 保存与取消在同一协程内串行，保证不丢盘；与 LuaEditor onRelease 的
     * [flushSelectedDirtyAsync] 双路径对同一脏标签为幂等写，无竞态冲突。
     * 作用域取消经 flush 协程的 invokeOnCompletion 挂接：先完成全部脏标签落盘，
     * 再取消 scope（不在自身协程内 self-cancel，P3-2 卫生）。
     */
    fun release() {
        if (released) return
        released = true
        val flushJob =
            scope.launch {
                val dirty = _tabs.value.filter { it.dirty }.map { it.fileName }
                dirty.forEach { flushDirty(it) }
                synchronized(cacheLock) { contents.clear() }
            }
        flushJob.invokeOnCompletion { scope.cancel() }
    }

    /** 打开（或选中）标签：IO 读盘 + IO 线程构造 Content 后入缓存，再切换。 */
    suspend fun openTab(fileName: String) {
        val content = withContext(ioDispatcher) { loadContent(fileName) }
        if (_tabs.value.none { it.fileName == fileName }) {
            _tabs.value = _tabs.value + EditorTab(fileName, dirty = false)
        }
        select(fileName, content)
    }

    /**
     * 切换选中：缓存命中直接切换；被 LRU 逐出的标签先置空选中（编辑器区转圈）
     * 再后台重载，重载期间标签被关闭或用户切到其他标签则丢弃结果（最后点击生效）。
     */
    fun select(fileName: String) {
        val cached = synchronized(cacheLock) { contents[fileName] }
        if (cached != null) {
            select(fileName, cached)
            return
        }
        _selection.value = Selection(fileName, null)
        scope.launch {
            val reloaded = withContext(ioDispatcher) { loadContent(fileName) }
            val current = _selection.value.file
            if ((current == null || current == fileName) && _tabs.value.any { it.fileName == fileName }) {
                _selection.value = Selection(fileName, reloaded)
            } else {
                synchronized(cacheLock) { contents.remove(fileName) }
            }
        }
    }

    /**
     * 拖拽排序（WP-003）：仅重排标签列表；选中按 fileName 语义自动跟随，
     * Content 缓存按文件名键控，重排不触碰。仅主线程调用。
     */
    fun moveTab(
        from: Int,
        to: Int,
    ) {
        val current = _tabs.value
        if (from !in current.indices || to !in current.indices || from == to) return
        val mutable = current.toMutableList()
        val moved = mutable.removeAt(from)
        mutable.add(to, moved)
        _tabs.value = mutable
    }

    private fun select(
        fileName: String,
        content: EditorContent,
    ) {
        _selection.value = Selection(fileName, content)
    }

    /**
     * 文本修改门面（Q7）：宿主对选中标签的文本写入统一经本入口（sora Content API，
     * 行列 0 基）。内部同步触发脏标记与自动保存节流信号——宿主禁止绕过本门面直接改
     * `selection.value.content`（会绕过脏标记/自动保存/undo 语义）。仅主线程调用。
     */
    fun insertText(
        line: Int,
        column: Int,
        text: String,
    ) {
        val selected = _selection.value.content ?: return
        selected.raw.insert(line, column, text)
        onContentModified()
    }

    /** 文本替换门面（语义见 [insertText]）；[start]/[end] 为 (line, column) 二元组。 */
    fun replaceText(
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
        text: String,
    ) {
        val selected = _selection.value.content ?: return
        selected.raw.replace(startLine, startColumn, endLine, endColumn, text)
        onContentModified()
    }

    /**
     * 全文档替换门面（T14，2026-08-11）：以 [text] 整体替换选中标签的全部内容，
     * 内部计算行坐标，统一经 [onContentModified] 置脏并触发自动保存节流信号。
     * 仅主线程调用。
     */
    fun replaceAllText(text: String) {
        val selected = _selection.value.content ?: return
        val raw = selected.raw
        val lastLine = (raw.lineCount - 1).coerceAtLeast(0)
        raw.replace(0, 0, lastLine, raw.getColumnCount(lastLine), text)
        onContentModified()
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
        _tabs.value =
            current.toMutableList().apply {
                this[index] = current[index].copy(dirty = true)
            }
    }

    /**
     * 重新加载标签内容（「文件 → 重新加载」，T3，2026-08-10）：
     * 先把脏内容落盘（防静默丢修改），再丢弃缓存中的 [Content]（undo 栈随内容释放、
     * 撤销历史重置），走缓存未命中重载路径重新读盘。已关闭标签忽略。
     * 仅主线程调用；状态写遵循会话线程契约（scope 为主线程调度器）。
     */
    fun reload(fileName: String) {
        if (_tabs.value.none { it.fileName == fileName }) return
        scope.launch {
            flushDirty(fileName)
            synchronized(cacheLock) { contents.remove(fileName) }
            select(fileName)
        }
    }

    /** 撤销/重做能力变化：仅在值变化时发射（needsUpdate 判定）。 */
    fun onUndoRedoChanged(
        canUndo: Boolean,
        canRedo: Boolean,
    ) {
        val selected = _selection.value.file ?: return
        val current = _tabs.value
        val index = current.indexOfFirst { it.fileName == selected }
        if (index < 0) return
        val tab = current[index]
        if (tab.canUndo == canUndo && tab.canRedo == canRedo) return
        _tabs.value =
            current.toMutableList().apply {
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

    private fun closeBatch(targets: List<EditorTab>): List<String> {
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

    /**
     * 磁盘文件被删除时同步标签（WP-005）：用户已在确认框确认删除，
     * 丢弃该标签的缓存并关闭（不落盘），防自动保存复活已删文件。
     */
    fun removeTab(fileName: String) {
        synchronized(cacheLock) { contents.remove(fileName) }
        close(fileName)
    }

    /**
     * 磁盘文件被重命名时同步标签（WP-005）：先落盘脏内容（旧路径），
     * 再将 Content 缓存换键、更新标签名与选中状态（脏标记随落盘清除）。
     * 仅主线程调用；[oldName]/[newName] 为工程相对路径。
     */
    fun renameTab(
        oldName: String,
        newName: String,
    ) {
        scope.launch {
            flushDirty(oldName)
            val current = _tabs.value
            val index = current.indexOfFirst { it.fileName == oldName }
            if (index < 0) return@launch
            val content = synchronized(cacheLock) { contents.remove(oldName) }
            if (content != null) {
                synchronized(cacheLock) { contents[newName] = content }
            }
            _tabs.value =
                current.toMutableList().apply {
                    this[index] = current[index].copy(fileName = newName)
                }
            if (_selection.value.file == oldName) {
                _selection.value = Selection(newName, content ?: return@launch)
            }
        }
    }

    /** 兜底保存（onRelease / onBack）：在会话作用域上异步执行，不随页面销毁取消。 */
    fun flushSelectedDirtyAsync() {
        flushDirtyAsync(_selection.value.file)
    }

    /** 自动保存 / 运行前落盘：刷新当前选中标签的脏内容。 */
    suspend fun flushSelectedDirty() {
        flushDirty(_selection.value.file ?: return)
    }

    /**
     * 自动保存防抖落盘：刷新**全部**脏标签（T3，2026-08-10）。
     *
     * 历史实现只落盘「当前选中」标签：编辑标签 A → 500ms 防抖内切到 B → 进程被系统杀死
     * （无 onCleared 兜底）时，A 的修改永久丢失。防抖消费侧无「编辑对象」记录，
     * 全量遍历是唯一不漏的语义；成本仅限实际脏标签（[flushDirty] 内部按 dirty 过滤）。
     */
    suspend fun flushAllDirty() {
        _tabs.value.filter { it.dirty }.forEach { flushDirty(it.fileName) }
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
        val saved =
            withContext(ioDispatcher) {
                projectManager.saveProjectScript(project, fileName, content.snapshot())
            }
        if (saved) {
            val current = _tabs.value
            val index = current.indexOfFirst { it.fileName == fileName }
            if (index >= 0 && current[index].dirty) {
                _tabs.value =
                    current.toMutableList().apply {
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

    private fun loadContent(fileName: String): EditorContent {
        synchronized(cacheLock) {
            contents[fileName]?.let { return it }
        }
        val text = projectManager.getProjectScript(project, fileName) ?: ""
        val content = EditorContent.of(text)
        synchronized(cacheLock) {
            contents[fileName] = content
            trimCache()
        }
        return content
    }

    /**
     * LRU 逐出：仅逐出「未选中且非脏」的标签内容。选中与脏标签绝不逐出，
     * 保证无未保存内容丢失；被逐出标签的 undo 历史随内容一并释放（再次选中时
     * 从磁盘重载、undo 栈重置）——仅在打开标签数超过 [CONTENT_CACHE_CAP] 时发生。
     * 单遍扫描即终止：每轮要么前进迭代器，要么因上限达成退出。
     */
    private fun trimCache() {
        synchronized(cacheLock) {
            if (contents.size <= CONTENT_CACHE_CAP) return
            val selected = _selection.value.file
            val iterator = contents.entries.iterator()
            while (iterator.hasNext() && contents.size > CONTENT_CACHE_CAP) {
                val (fileName, _) = iterator.next()
                if (fileName == selected) continue
                val tab = _tabs.value.firstOrNull { it.fileName == fileName }
                if (tab == null || tab.dirty) continue
                iterator.remove()
            }
        }
    }
}
