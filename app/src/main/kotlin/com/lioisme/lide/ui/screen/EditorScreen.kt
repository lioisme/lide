package com.lioisme.lide.ui.screen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.lioisme.lide.R
import com.lioisme.lide.androlua.PermissionResolver
import com.lioisme.lide.editor.EditorMoreAction
import com.lioisme.lide.editor.EditorToolbar
import com.lioisme.lide.editor.FileNode
import com.lioisme.lide.editor.editorTextOpsActions
import com.lioisme.lide.engine.EngineActivity
import com.lioisme.lide.engine.EngineResultParser
import com.lioisme.lide.engine.RunEngineLauncher
import com.lioisme.lide.model.Project
import com.lioisme.lide.project.ProjectManager
import com.lioisme.lide.session.EditorSession
import com.lioisme.lide.ui.theme.LocalIsDarkTheme
import com.lioisme.lide.ui.theme.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

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
 * 组件拆分（P3-2）：编辑器主体/文件树/横幅见 [EditorBody]（EditorComponents.kt），
 * 关闭确认/运行错误对话框见 EditorDialogs.kt，本文件只留会话编排与状态接线。
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

    // 编辑器实例引用：普通原子容器（非 Compose state）——写入只发生在 AndroidView update
    // 阶段与 onDispose，组合期内无读取，无需快照跟踪（P3-5，脱离重组系统）。
    val editorRef = remember { AtomicReference<io.github.rosemoe.sora.widget.CodeEditor?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    var isFileTreeVisible by remember { mutableStateOf(false) }
    var isSearchVisible by remember { mutableStateOf(false) }
    // 脏文件待确认队列：单标签关闭时为单元素列表；「关闭其他/全部」时为批量队列，
    // 逐个确认后经 saveAndClose/discardAndClose 出队，取消即放弃剩余批次。
    var closeQueue by remember { mutableStateOf<List<String>>(emptyList()) }

    val extPermissionMissing =
        remember {
            mutableStateOf(!hasStoragePermission(context))
        }

    val manageStorageLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
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

    val engineResultLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val data = result.data
            val output = data?.getStringExtra(EngineActivity.EXTRA_OUTPUT) ?: ""
            val isError =
                data?.getBooleanExtra(EngineActivity.EXTRA_ERROR, false) == true ||
                    EngineResultParser.isErrorOutput(output)
            if (isError) {
                runResult = RunResult(output, EngineResultParser.findErrorLocation(output, project.path))
            } else {
                snackbarMsg = context.getString(R.string.run_finished)
            }
        }

    val doRun: () -> Unit = {
        scope.launch {
            session.flushSelectedDirty()
            // WP-013：运行入口跟随当前标签——当前标签非 main.lua 时运行该文件，
            // main.lua（含单标签默认）保持运行工程入口语义。EngineActivity 的 luaDir
            // 始终为工程根，init.lua/import 上下文不受影响（pro 语义对齐）。
            val target = selection.file ?: project.mainFile
            engineResultLauncher.launch(
                RunEngineLauncher(context).createIntent(project.path, target),
            )
        }
    }

    // 跳行：等待编辑器绑定到目标文件后定位（sora setSelection 0 基行号，自动滚动）。
    LaunchedEffect(pendingGotoLine) {
        val line = pendingGotoLine ?: return@LaunchedEffect
        while (editorRef.get() == null) {
            delay(16)
        }
        val editor = editorRef.get()
        if (editor != null) {
            val last = (editor.text.lineCount - 1).coerceAtLeast(0)
            editor.setSelection(line.coerceIn(0, last), 0)
            editor.requestFocus()
        }
        pendingGotoLine = null
    }

    fun jumpToError() {
        val loc =
            runResult?.location ?: run {
                runResult = null
                return
            }
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

    val runPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { granted: Map<String, Boolean> ->
            val denied = granted.filterValues { !it }.keys
            if (denied.isNotEmpty()) {
                android.widget.Toast
                    .makeText(
                        context,
                        context.getString(R.string.run_permission_denied_hint, denied.size),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
            }
            doRun()
        }

    // 宿主 manifest 声明的权限集合：安装周期内不变，只查询一次；
    // getPackageInfo 经 produceState + IO 查询（P2#14），避免首帧组合内 binder 调用。
    val hostDeclared by produceState(emptySet<String>()) {
        value =
            withContext(Dispatchers.IO) {
                try {
                    context.packageManager
                        .getPackageInfo(
                            context.packageName,
                            android.content.pm.PackageManager.GET_PERMISSIONS,
                        ).requestedPermissions
                        ?.toSet() ?: emptySet()
                } catch (_: Throwable) {
                    emptySet()
                }
            }
    }

    val requestRun: () -> Unit = {
        // 审计修正：工程声明权限先归一化（裸名 → android.permission. 全名），
        // 且只请求宿主 manifest 已声明的权限（宿主未声明的仅在打包时写入产物 manifest）。
        // init.lua 读取与授予检查在 IO 线程执行（P2#11），结果经主线程 launcher 弹窗。
        scope.launch {
            val ungranted =
                withContext(Dispatchers.IO) {
                    val declared =
                        projectManager
                            .loadUserPermissions(project)
                            .map { PermissionResolver.normalize(it) }
                    PermissionResolver
                        .runtimeRequestable(declared)
                        .filter { it in hostDeclared }
                        .filter {
                            androidx.core.content.ContextCompat
                                .checkSelfPermission(context, it) !=
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                        }
                }
            if (ungranted.isEmpty()) {
                doRun()
            } else {
                runPermissionLauncher.launch(ungranted.toTypedArray())
            }
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

    fun getSearcher(): io.github.rosemoe.sora.widget.EditorSearcher? = editorRef.get()?.getSearcher()

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
        modifier =
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.F) {
                        isSearchVisible = !isSearchVisible
                        if (!isSearchVisible) {
                            getSearcher()?.stopSearch()
                        }
                        true
                    } else {
                        false
                    }
                },
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
                    onUndo = { editorRef.get()?.undo() },
                    onRedo = { editorRef.get()?.redo() },
                    onRun = {
                        requestRun()
                    },
                    // 文件树 / 搜索与工程级操作统一收进右上角更多（⋮）分类菜单：
                    // Appbar 常驻按钮（含返回）恒为 5 个 —— 返回 / 运行 / 撤销 / 重做 / 更多。
                    moreActions =
                        listOf(
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
                        ) +
                            editorTextOpsActions(
                                editorProvider = { editorRef.get() },
                                category = stringResource(R.string.menu_category_text),
                            ),
                )
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
        ) { padding ->
            // 标签视图列表：状态类已合并（B-5），tabs 直接是 EditorTab，无需逐帧适配。
            EditorBody(
                session = session,
                contentPadding = padding,
                tabs = tabs,
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
                        },
                    )
                },
                onSearchClose = {
                    isSearchVisible = false
                    getSearcher()?.stopSearch()
                },
            )
        }

        // 工程文件目录对话框（文件系统风格）：顶栏文件夹键打开，选中文件即打开并关闭。
        // WP-005：文件操作后经会话回调同步标签（重命名跟随 / 删除即关，脏内容不丢）。
        if (isFileTreeVisible) {
            ProjectFileBrowserDialog(
                project = project,
                onFileOpen = { openFileFromTree(it) },
                onFileRenamed = { oldName, newName -> session.renameTab(oldName, newName) },
                onFileDeleted = { fileName -> session.removeTab(fileName) },
                onDismiss = { isFileTreeVisible = false },
            )
        }

        val pendingFile = closeQueue.firstOrNull()
        if (pendingFile != null) {
            EditorCloseConfirmDialog(
                fileName = pendingFile,
                onSave = {
                    session.saveAndClose(pendingFile)
                    closeQueue = closeQueue.drop(1)
                },
                onDiscard = {
                    session.discardAndClose(pendingFile)
                    closeQueue = closeQueue.drop(1)
                },
                onCancel = { closeQueue = emptyList() },
            )
        }

        // WP-002 运行结果对话框：失败时展示错误文本 + 跳行 / import 建议。
        val result = runResult
        if (result != null) {
            EditorRunErrorDialog(
                output = result.output,
                hasLocation = result.location != null,
                onJumpToLine = { jumpToError() },
                onInsertImport = { insertImport(it) },
                onDismiss = { runResult = null },
            )
        }
    }
}
