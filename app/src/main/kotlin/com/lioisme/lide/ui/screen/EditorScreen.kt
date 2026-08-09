package com.lioisme.lide.ui.screen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lioisme.lide.R
import com.lioisme.lide.engine.EngineActivity
import com.lioisme.lide.editor.EditorMoreAction
import com.lioisme.lide.editor.EditorToolbar
import com.lioisme.lide.editor.editorTextOpsActions
import com.lioisme.lide.engine.EngineResultParser
import com.lioisme.lide.engine.RunEngineLauncher
import com.lioisme.lide.model.Project
import com.lioisme.lide.project.PermissionResolver
import com.lioisme.lide.project.ProjectManager
import com.lioisme.lide.session.EditorSession
import com.lioisme.lide.session.EditorSessionViewModel
import com.lioisme.lide.session.EditorSessionViewModelFactory
import com.lioisme.lide.ui.theme.LocalIsDarkTheme
import com.lioisme.lide.ui.theme.SettingsManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

private const val AUTO_SAVE_DELAY_MS = 500L

/** 跳行等待编辑器绑定的超时上限（编辑器错误态/初始化失败时降级跳过定位）。 */
private const val EDITOR_BIND_TIMEOUT_MS = 5_000L

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
 * - 文件切换走 [Content] 缓存复用，零读盘、零 O(n) 重建（除首次打开）。
 *
 * 布局：Scaffold（工具栏常驻顶栏）→ 标签行 + 编辑器；默认打开 main.lua，
 * 不再提供文件树/打开文件入口（2026-08-09 用户红线，文件选择一律走系统选择器）。
 * 组件拆分（P3-2）：编辑器主体/横幅见 [EditorBody]（EditorComponents.kt），
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
    onManageSigning: () -> Unit = {},
    onRun: (fileName: String) -> Unit,
    projectInfoVersion: Int = 0,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkTheme = LocalIsDarkTheme.current
    val appEditorConfig by SettingsManager.editorConfig.collectAsState()

    // 会话经 ViewModel 持有（Q8）：旋转/配置变更不销毁，打开标签/撤销历史/选中文件
    // 完整保留；返回栈条目出栈时 onCleared 委托会话兜底保存（脏标签落盘 + 缓存清空）。
    val sessionViewModel: EditorSessionViewModel =
        viewModel(factory = EditorSessionViewModelFactory(project, projectManager))
    val session = sessionViewModel.session
    val tabs by session.tabs.collectAsState()
    val selection by session.selection.collectAsState()
    val selectedFile = selection.file
    val content = selection.content

    // 进程级重建恢复（Q8）：打开的标签名单 + 选中文件（Bundle 可序列化）；
    // 配置变更路径由 ViewModel 保证会话存活，本恢复仅兜底进程重建（旋转时两者均为幂等空操作）。
    var savedTabNames by rememberSaveable(project.path) { mutableStateOf(arrayListOf<String>()) }
    var savedSelectedFile by rememberSaveable(project.path) { mutableStateOf("") }
    LaunchedEffect(tabs, selection) {
        savedTabNames = tabs.map { it.fileName }.toCollection(arrayListOf())
        savedSelectedFile = selection.file ?: ""
    }

    // 编辑器实例引用：普通原子容器（非 Compose state）——写入只发生在 AndroidView update
    // 阶段与 onDispose，组合期内无读取，无需快照跟踪（P3-5，脱离重组系统）。
    val editorRef = remember { AtomicReference<io.github.rosemoe.sora.widget.CodeEditor?>(null) }
    // 编辑器绑定门控（Q1，替代 60Hz 忙轮询）：LuaEditor update 阶段绑定后完成；
    // 跳行等待经 withTimeoutOrNull 挂起，编辑器错误态/未绑定超时后降级跳过。
    val editorReady = remember { CompletableDeferred<Unit>() }
    val snackbarHostState = remember { SnackbarHostState() }

    var showBuildDialog by remember { mutableStateOf(false) }
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
    // 拒绝不阻断脚本执行；2026-08-09 起静默继续，不再提示）。

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
    // 门控等待替代忙轮询：编辑器绑定信号（CompletableDeferred）完成即继续，
    // 超时（编辑器错误态/初始化失败）降级跳过定位，不阻塞不空转。
    LaunchedEffect(pendingGotoLine) {
        val line = pendingGotoLine ?: return@LaunchedEffect
        withTimeoutOrNull(EDITOR_BIND_TIMEOUT_MS) { editorReady.await() }
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
            // 经会话文本门面写入（Q7）：脏标记与自动保存节流由门面统一触发。
            session.insertText(0, 0, "import \"$globalName\"\n")
        }
    }

    val runPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { _: Map<String, Boolean> ->
            // 无论授予与否都静默继续运行（pro 行为：拒绝不阻断脚本执行；2026-08-09
            // 需求：不再弹「已拒绝 x 项权限」Toast，保持无打扰运行）。
            doRun()
        }

    // 宿主 manifest 声明的权限集合（P4b 去重：单一事实源 PermissionCatalog.hostDeclaredPermissions）；
    // getPackageInfo 经 produceState + IO 查询（P2#14），避免首帧组合内 binder 调用。
    val hostDeclared by produceState(emptySet<String>()) {
        value = withContext(Dispatchers.IO) { PermissionCatalog.hostDeclaredPermissions(context) }
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
        // 进程重建恢复：重新打开上次的标签并恢复选中（内容自磁盘重载，脏标记随自动保存语义重置）。
        val restored = savedTabNames.filter { it.isNotBlank() }
        if (restored.isNotEmpty()) {
            val current = session.tabs.value.map { it.fileName }
            restored.filterNot { it in current }.forEach { session.openTab(it) }
            val target = savedSelectedFile.takeIf { it in restored }
            if (target != null && target != session.selection.value.file) {
                session.select(target)
            }
        }
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

    val selectedTab = tabs.firstOrNull { it.fileName == selectedFile }

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
                    // 文件操作入口已收拢（2026-08-09 用户红线：编辑器仅自动打开 main.lua，
                    // 不再打开其他文件），工程级操作留在更多（⋮）分类菜单：
                    // Appbar 常驻按钮（含返回）恒为 5 个 —— 返回 / 运行 / 撤销 / 重做 / 更多。
                    moreActions =
                        listOf(
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
                            ) { showBuildDialog = true },
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
                onEditorBound = {
                    // 绑定信号幂等完成（主线程 update 阶段触发；已完成则跳过，防重复 complete 抛异常）。
                    if (!editorReady.isCompleted) editorReady.complete(Unit)
                },
                storagePermissionMissing = extPermissionMissing.value,
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
            )
        }

        // 构建打包对话框：唯一打包入口（需求：仅保留一个构建打包对话框）。
        if (showBuildDialog) {
            BuildPackagingDialog(
                project = project,
                projectManager = projectManager,
                onDismiss = { showBuildDialog = false },
                onManageSigning = onManageSigning,
            )
        }

        // 脏文件待确认队列：单标签关闭时为单元素列表；「关闭其他/全部」时为批量队列，
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
