package com.lioisme.lide.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import com.lioisme.lide.androlua.PermissionResolver
import com.lioisme.lide.engine.EngineActivity
import com.lioisme.lide.engine.EngineResultParser
import com.lioisme.lide.engine.RunEngineLauncher
import com.lioisme.lide.editor.EditorConfig
import com.lioisme.lide.editor.EditorMoreAction
import com.lioisme.lide.editor.EditorTab
import com.lioisme.lide.editor.EditorTabs
import com.lioisme.lide.editor.EditorToolbar
import com.lioisme.lide.editor.FileBrowser
import com.lioisme.lide.editor.FileNode
import com.lioisme.lide.editor.LuaEditor
import com.lioisme.lide.editor.SearchBar
import com.lioisme.lide.editor.TipIconButton
import com.lioisme.lide.editor.pressAndHoldHapticFeedback
import com.lioisme.lide.model.Project
import com.lioisme.lide.project.ProjectManager
import com.lioisme.lide.session.EditorSession
import com.lioisme.lide.ui.theme.LocalIsDarkTheme
import com.lioisme.lide.ui.theme.SettingsManager
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val AUTO_SAVE_DELAY_MS = 500L

/** WP-002 引擎运行结果（宿主展示用）。 */
private data class RunResult(
    val output: String,
    val location: EngineResultParser.ErrorLocation?,
)

/**
 * 编辑器页面（会话驱动）。
 *
 * 重组模型（对齐 Squircle `EditorViewModel`）：
 * - 每键编辑只触发会话内部 [EditorSession.editTick]（StateFlow，不重组）；
 *   脏标记 / undo 能力仅在值实际变化时经 [EditorSession.tabs] 发射——稳态打字零重组；
 * - 搜索面板自持全部状态，打字与搜索结果只重组 SearchBar 子树；
 * - 文件切换走 [Content] 缓存复用，零读盘、零 O(n) 重建（除首次打开）。
 *
 * 布局：Scaffold（工具栏常驻顶栏）→ 标签行 + 编辑器；工程文件树经顶栏文件夹键打开对话框展示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    project: Project,
    projectManager: ProjectManager,
    onBack: () -> Unit,
    onPermissions: () -> Unit = {},
    onProjectInfo: () -> Unit = {},
    onBuildApk: () -> Unit = {},
    onRun: (fileName: String) -> Unit,
    projectInfoVersion: Int = 0,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkTheme = LocalIsDarkTheme.current
    val appEditorConfig by SettingsManager.editorConfig.collectAsState()

    val session = remember(project) { EditorSession(project, projectManager) }
    // 页面销毁时释放会话：兜底保存脏标签后取消会话作用域并清空 Content 缓存。
    DisposableEffect(project) {
        onDispose { session.release() }
    }
    val tabs by session.tabs.collectAsState()
    val selection by session.selection.collectAsState()
    val selectedFile = selection.file
    val content = selection.content

    val editorRef = remember { mutableStateOf<CodeEditor?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    var isFileTreeVisible by remember { mutableStateOf(false) }
    var isSearchVisible by remember { mutableStateOf(false) }
    // 脏文件待确认队列：单标签关闭时为单元素列表；「关闭其他/全部」时为批量队列，
    // 逐个确认后经 saveAndClose/discardAndClose 出队，取消即放弃剩余批次。
    var closeQueue by remember { mutableStateOf<List<String>>(emptyList()) }

    val extPermissionMissing = remember {
        mutableStateOf(!hasStoragePermission(context))
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        extPermissionMissing.value = !hasStoragePermission(context)
    }

    // WP-003 运行前权限门：工程 init.lua 声明而尚未授予的运行时权限，
    // 经系统对话框一次性请求；回调里无论授予与否都继续运行（pro 行为：
    // 拒绝仅 Toast 提示，不阻断脚本执行）。

    // WP-002 运行诊断闭环状态：引擎返回结果（成功 Snackbar / 失败对话框）。
    var runResult by remember { mutableStateOf<RunResult?>(null) }
    var snackbarMsg by remember { mutableStateOf<String?>(null) }
    // 跳行/插入 import 的待执行标记：编辑器绑定目标文件后消费。
    var pendingGotoLine by remember { mutableStateOf<Int?>(null) }
    var pendingImportGlobal by remember { mutableStateOf<String?>(null) }

    val engineResultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val output = data?.getStringExtra(EngineActivity.EXTRA_OUTPUT) ?: ""
        val isError = data?.getBooleanExtra(EngineActivity.EXTRA_ERROR, false) == true ||
            EngineResultParser.isErrorOutput(output)
        if (isError) {
            runResult = RunResult(output, EngineResultParser.findErrorLocation(output))
        } else {
            snackbarMsg = context.getString(R.string.run_finished)
        }
    }

    val doRun: () -> Unit = {
        scope.launch {
            session.flushSelectedDirty()
            engineResultLauncher.launch(
                RunEngineLauncher(context).createIntent(project.path, project.mainFile)
            )
        }
    }

    // 跳行：等待编辑器绑定到目标文件后定位（sora setSelection 0 基行号，自动滚动）。
    LaunchedEffect(pendingGotoLine) {
        val line = pendingGotoLine ?: return@LaunchedEffect
        while (editorRef.value == null) {
            delay(16)
        }
        val editor = editorRef.value
        if (editor != null) {
            val last = (editor.text.lineCount - 1).coerceAtLeast(0)
            editor.setSelection(line.coerceIn(0, last), 0)
            editor.requestFocus()
        }
        pendingGotoLine = null
    }

    fun jumpToError() {
        val loc = runResult?.location ?: run { runResult = null; return }
        runResult = null
        scope.launch {
            session.openTab(loc.file)
            pendingGotoLine = loc.line - 1
        }
    }

    fun insertImport(globalName: String) {
        val target = runResult?.location?.file ?: project.mainFile
        runResult = null
        scope.launch {
            session.openTab(target)
            val content = session.selection.value.content ?: return@launch
            content.insert(0, 0, "import \"$globalName\"\n")
            session.onContentModified()
        }
    }

    val runPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted: Map<String, Boolean> ->
        val denied = granted.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.run_permission_denied_hint, denied.size),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
        doRun()
    }

    // 宿主 manifest 声明的权限集合：安装周期内不变，只查询一次。
    val hostDeclared = remember {
        try {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_PERMISSIONS,
            ).requestedPermissions?.toSet() ?: emptySet()
        } catch (_: Throwable) {
            emptySet()
        }
    }

    val requestRun: () -> Unit = {
        // 审计修正：工程声明权限先归一化（裸名 → android.permission. 全名），
        // 且只请求宿主 manifest 已声明的权限（宿主未声明的仅在打包时写入产物 manifest）。
        val declared = projectManager.loadUserPermissions(project)
            .map { PermissionResolver.normalize(it) }
        val ungranted = PermissionResolver.runtimeRequestable(declared).filter { it in hostDeclared }
            .filter {
                androidx.core.content.ContextCompat.checkSelfPermission(context, it) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        if (ungranted.isEmpty()) {
            doRun()
        } else {
            runPermissionLauncher.launch(ungranted.toTypedArray())
        }
    }

    LaunchedEffect(Unit) {
        session.initialize()
    }

    // 自动保存：500ms 防抖。editTick 为 StateFlow，收集过程不触发重组。
    LaunchedEffect(Unit) {
        session.editTick.collectLatest {
            delay(AUTO_SAVE_DELAY_MS)
            if (!extPermissionMissing.value) session.flushSelectedDirty()
        }
    }

    // 运行结束提示（成功路径）：Snackbar 一次性消费。
    LaunchedEffect(snackbarMsg) {
        val msg = snackbarMsg ?: return@LaunchedEffect
        snackbarMsg = null
        snackbarHostState.showSnackbar(msg)
    }

    fun getSearcher(): EditorSearcher? = editorRef.value?.getSearcher()

    fun openFileFromTree(node: FileNode) {
        if (node.isDirectory) return
        val prefix = project.path + File.separator
        val relative = if (node.path.startsWith(prefix)) node.path.removePrefix(prefix) else node.name
        // 文件打开后关闭对话框（对话框与打开保持原异步语义）。
        isFileTreeVisible = false
        scope.launch { session.openTab(relative) }
    }

    val selectedTab = tabs.firstOrNull { it.fileName == selectedFile }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.F) {
                    isSearchVisible = !isSearchVisible
                    if (!isSearchVisible) {
                        getSearcher()?.stopSearch()
                    }
                    true
                } else false
            }
    ) {
        Scaffold(
            topBar = {
                EditorToolbar(
                    // 标题显示工程名，不显示当前打开文件的路径（文件身份由标签行承载）。
                    title = project.name,
                    canUndo = selectedTab?.canUndo == true,
                    canRedo = selectedTab?.canRedo == true,
                    onBack = {
                        session.flushSelectedDirtyAsync()
                        onBack()
                    },
                    onUndo = { editorRef.value?.undo() },
                    onRedo = { editorRef.value?.redo() },
                    onRun = {
                        requestRun()
                    },
                    // 文件树 / 搜索与工程级操作统一收进右上角更多（⋮）分类菜单：
                    // Appbar 常驻按钮（含返回）恒为 5 个 —— 返回 / 运行 / 撤销 / 重做 / 更多。
                    moreActions = listOf(
                        EditorMoreAction(
                            category = stringResource(R.string.menu_category_file),
                            label = stringResource(R.string.cd_open_file_tree),
                        ) { isFileTreeVisible = true },
                        EditorMoreAction(
                            category = stringResource(R.string.menu_category_file),
                            label = stringResource(R.string.cd_search),
                        ) { isSearchVisible = true },
                        EditorMoreAction(
                            category = stringResource(R.string.menu_category_project),
                            label = stringResource(R.string.project_info),
                        ) { onProjectInfo() },
                        EditorMoreAction(
                            category = stringResource(R.string.menu_category_project),
                            label = stringResource(R.string.project_permissions),
                        ) { onPermissions() },
                        EditorMoreAction(
                            category = stringResource(R.string.menu_category_project),
                            label = stringResource(R.string.build_apk),
                        ) { onBuildApk() },
                    ),
                )
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
        ) { padding ->
            // 标签视图列表：仅在标签数据实际变化时重建（避免每次重组分配新列表）。
            val tabViews = remember(tabs) { tabs.map { it.tab } }
            EditorBody(
                session = session,
                contentPadding = padding,
                tabs = tabViews,
                selectedFile = selectedFile,
                content = content,
                isDarkTheme = isDarkTheme,
                editorConfig = appEditorConfig,
                editorRef = editorRef,
                storagePermissionMissing = extPermissionMissing.value,
                isSearchVisible = isSearchVisible,
                onTabClose = { file ->
                    if (session.requestClose(file)) closeQueue = listOf(file)
                },
                onTabMoved = { from, to -> session.moveTab(from, to) },
                onCloseOthers = { file ->
                    val dirty = session.closeOthers(file)
                    if (dirty.isNotEmpty()) closeQueue = dirty
                },
                onCloseAll = {
                    val dirty = session.closeAll()
                    if (dirty.isNotEmpty()) closeQueue = dirty
                },
                onGrantStorage = {
                    manageStorageLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    )
                },
                onSearchClose = {
                    isSearchVisible = false
                    getSearcher()?.stopSearch()
                },
            )
        }

        // 工程文件目录对话框（文件系统风格）：顶栏文件夹键打开，选中文件即打开并关闭。
        if (isFileTreeVisible) {
            ProjectFileBrowserDialog(
                project = project,
                onFileOpen = { openFileFromTree(it) },
                onDismiss = { isFileTreeVisible = false },
            )
        }

        val pendingFile = closeQueue.firstOrNull()
        if (pendingFile != null) {
            AlertDialog(
                onDismissRequest = { closeQueue = emptyList() },
                title = { Text(stringResource(R.string.close_file_title)) },
                text = { Text(stringResource(R.string.close_file_message, pendingFile)) },
                confirmButton = {
                    Row {
                        TextButton(
                            onClick = {
                                session.discardAndClose(pendingFile)
                                closeQueue = closeQueue.drop(1)
                            },
                            modifier = Modifier.pressAndHoldHapticFeedback(),
                        ) {
                            Text(stringResource(R.string.discard))
                        }
                        TextButton(
                            onClick = {
                                session.saveAndClose(pendingFile)
                                closeQueue = closeQueue.drop(1)
                            },
                            modifier = Modifier.pressAndHoldHapticFeedback(),
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { closeQueue = emptyList() },
                        modifier = Modifier.pressAndHoldHapticFeedback(),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        // WP-002 运行结果对话框：失败时展示错误文本 + 跳行 / import 建议。
        val result = runResult
        if (result != null) {
            val nilGlobal = EngineResultParser.findNilGlobal(result.output)
            AlertDialog(
                onDismissRequest = { runResult = null },
                title = { Text(stringResource(R.string.run_error_title)) },
                text = {
                    Column {
                        Text(
                            result.output,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                        if (nilGlobal != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.run_import_suggestion, nilGlobal),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                confirmButton = {
                    Row {
                        if (result.location != null) {
                            TextButton(
                                onClick = { jumpToError() },
                                modifier = Modifier.pressAndHoldHapticFeedback(),
                            ) {
                                Text(stringResource(R.string.run_jump_to_line))
                            }
                        }
                        if (nilGlobal != null) {
                            TextButton(
                                onClick = { insertImport(nilGlobal) },
                                modifier = Modifier.pressAndHoldHapticFeedback(),
                            ) {
                                Text(stringResource(R.string.run_insert_import, nilGlobal))
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { runResult = null },
                        modifier = Modifier.pressAndHoldHapticFeedback(),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}

/** 宿主存储权限门：Android 11+ 需要「所有文件访问」授权。 */
private fun hasStoragePermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return Environment.isExternalStorageManager()
    }
    return true
}

/**
 * 工程文件目录对话框（对齐工程信息「用户权限」对话框风格）：AlertDialog 标准骨架
 * （标题 = 当前目录，正文 = 仅文件列表，按钮 = 返回上一目录 / 取消）。
 * 目录点击进入，文档文件点击打开（宿主负责关闭对话框），非文档文件点击提示「不支持打开」。
 * 列表展示全部文件（不按扩展名过滤）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectFileBrowserDialog(
    project: Project,
    onFileOpen: (FileNode) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val strNotSupported = stringResource(R.string.file_not_supported_open)
    val rootDir = remember(project) { File(project.path) }
    var currentDir by remember(project) { mutableStateOf(rootDir) }
    var entries by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }

    suspend fun loadEntries(dir: File): List<FileNode> = withContext(Dispatchers.IO) {
        // 全量展示：目录 + 所有文件（非文档文件在点击时提示，不在列表层过滤）。
        dir.listFiles()
            ?.filter { it.isDirectory || it.isFile }
            ?.map { FileNode(it.name, it.absolutePath, it.isDirectory) }
            ?: emptyList()
    }

    // 目录内容按需读取（IO 线程）。
    LaunchedEffect(currentDir) {
        entries = loadEntries(currentDir)
    }

    // 下拉刷新：重扫当前目录（覆盖外部改动，如 adb push 新文件）。
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            entries = loadEntries(currentDir)
            isRefreshing = false
        }
    }

    // 标题 = 当前目录相对工程的路径（根目录显示工程名），辅助定位。
    val title = remember(currentDir, rootDir) {
        val rel = try {
            currentDir.relativeTo(rootDir).invariantSeparatorsPath
        } catch (_: Throwable) {
            currentDir.name
        }
        rel.ifBlank { project.name }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            FileBrowser(
                entries = entries,
                onOpenDirectory = { currentDir = File(it.path) },
                onOpenFile = { node ->
                    if (isDocumentFile(node.name)) {
                        onFileOpen(node)
                    } else {
                        Toast.makeText(context, strNotSupported, Toast.LENGTH_SHORT).show()
                    }
                },
                isRefreshing = isRefreshing,
                onRefresh = { isRefreshing = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { currentDir = currentDir.parentFile ?: rootDir },
                enabled = currentDir != rootDir,
                modifier = Modifier.pressAndHoldHapticFeedback(),
            ) {
                Text(stringResource(R.string.back_to_parent))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.pressAndHoldHapticFeedback(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * 可打开编辑的文档扩展名白名单（不含扩展名的文件视为文档，如 Makefile / LICENSE）。
 * 白名单之外的文件（二进制、归档、图片等）点击提示「不支持打开」。
 */
private val DOCUMENT_EXTENSIONS = setOf(
    "lua", "txt", "md", "markdown", "json", "xml", "yml", "yaml",
    "html", "htm", "css", "js", "mjs", "ts", "java", "kt", "kts",
    "c", "h", "cc", "cpp", "hpp", "cs", "go", "py", "rb", "php",
    "sh", "bat", "ps1", "ini", "cfg", "conf", "properties", "csv", "log",
    "gradle", "toml", "sql", "env", "svg",
)

private fun isDocumentFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "")
    return ext.isEmpty() || ext.lowercase() in DOCUMENT_EXTENSIONS
}

@Composable
private fun StoragePermissionBanner(
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.editor_storage_permission_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onGrant,
                modifier = Modifier.pressAndHoldHapticFeedback(),
            ) {
                Text(stringResource(R.string.editor_storage_permission_grant))
            }
        }
    }
}

/**
 * 编辑器主体（标签行 + 编辑器 + 权限横幅 + 搜索面板）。与文件目录对话框解耦：
 * 状态全部经参数注入，仅依赖会话回调上报宿主。
 */
@Composable
private fun EditorBody(
    session: EditorSession,
    contentPadding: PaddingValues,
    tabs: List<EditorTab>,
    selectedFile: String?,
    content: Content?,
    isDarkTheme: Boolean,
    editorConfig: EditorConfig,
    editorRef: MutableState<CodeEditor?>,
    storagePermissionMissing: Boolean,
    isSearchVisible: Boolean,
    onTabClose: (String) -> Unit,
    onTabMoved: (from: Int, to: Int) -> Unit,
    onCloseOthers: (String) -> Unit,
    onCloseAll: () -> Unit,
    onGrantStorage: () -> Unit,
    onSearchClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            // 输入法遮挡处理：IME 弹出时收缩编辑区（M3 Scaffold 已消费 contentWindowInsets，
            // imePadding = ime.union(systemBars) − consumed，键盘收起时不产生额外间距）。
            .imePadding()
            .padding(contentPadding)
    ) {
        if (tabs.isNotEmpty()) {
            EditorTabs(
                tabs = tabs,
                selectedFile = selectedFile,
                onTabClick = { session.select(it) },
                onTabClose = onTabClose,
                onTabMoved = onTabMoved,
                onCloseOthers = onCloseOthers,
                onCloseAll = onCloseAll,
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val currentContent = content
            if (currentContent != null && selectedFile != null) {
                LuaEditor(
                    content = currentContent,
                    isDarkTheme = isDarkTheme,
                    config = editorConfig,
                    onContentModified = { session.onContentModified() },
                    onUndoRedoStateChanged = { u, r -> session.onUndoRedoChanged(u, r) },
                    onRelease = {
                        // 兜底保存：编辑内容由会话缓存持有，宿主经会话异步落盘。
                        session.flushSelectedDirtyAsync()
                    },
                    editorRef = editorRef,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (selectedFile == null) {
                        Text(
                            stringResource(R.string.editor_no_file),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }

            Column(Modifier.align(Alignment.TopCenter)) {
                if (storagePermissionMissing) {
                    StoragePermissionBanner(
                        onGrant = onGrantStorage,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (isSearchVisible) {
                    SearchBar(
                        editorProvider = { editorRef.value },
                        onClose = onSearchClose,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
