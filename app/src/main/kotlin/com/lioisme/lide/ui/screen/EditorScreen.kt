package com.lioisme.lide.ui.screen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lioisme.lide.R
import com.lioisme.lide.editor.EditorContent
import com.lioisme.lide.editor.EditorController
import com.lioisme.lide.editor.EditorMoreAction
import com.lioisme.lide.editor.EditorToolbar
import com.lioisme.lide.editor.SearchMatchInfo
import com.lioisme.lide.editor.SearchMatchType
import com.lioisme.lide.engine.EngineContract
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

private const val AUTO_SAVE_DELAY_MS = 500L

/** 查找防抖（P5.1）：输入变化 → 250ms 后派发搜索（大文件/连续输入零压力）。 */
private const val SEARCH_DEBOUNCE_MS = 250L

/** 跳行等待编辑器绑定的超时上限（编辑器错误态/初始化失败时降级跳过定位）。 */
private const val EDITOR_BIND_TIMEOUT_MS = 5_000L

// 跳行定位的轮询步进：仅跳行文件刚切换的瞬态发生（常驻无此路径）。
private const val GOTO_BIND_POLL_MS = 8L

/**
 * 跳行目标（T4，2026-08-10）：携带目标 [EditorContent] 身份 + 0 基行号。
 *
 * `jumpToError` 先 openTab（挂起）再置标记；独立效应可能在 LuaEditor 的
 * `LaunchedEffect(content)` setText 生效前对**旧**文档执行 setSelection，
 * 随后文本替换导致定位丢失或错位。消费侧必须以
 * `EditorController.isShowing` 身份绑定为前置（见 [EditorScreen] 的 pendingJump 消费效应）。
 */
private data class PendingJump(
    val content: EditorContent,
    val line: Int,
)

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
 * - 文件切换走 [EditorContent] 缓存复用，零读盘、零 O(n) 重建（除首次打开）。
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
    // T3（2026-08-10）：菜单直达入口——编辑 → 编辑器设置；工程 → AndroLua 设置。
    onEditorSettings: () -> Unit = {},
    onAndroLuaSettings: () -> Unit = {},
    onRun: (fileName: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 编辑器文本区明暗唯一输入源 = 宿主外观（2026-08-12 方案可选反转：配色方案
    // 可在编辑器设置页选择，明暗双模随宿主自动切换；工程文件（init.lua theme 等）
    // 不允许影响宿主 UI）。
    val isDarkTheme = LocalIsDarkTheme.current
    val appEditorConfig by SettingsManager.editorConfig.collectAsState()
    // T19（2026-08-12）：编辑器周边 UI（AppBar/标签行/分隔线/空态）统一宿主
    // Material3 色板（与主界面一致），色带机制退役；编辑器文本区明暗仅由宿主明暗
    // 驱动（亮 = 当前方案亮侧 / 暗 = 当前方案暗侧）。

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

    // 编辑器实例门面（B 级收口，2026-08-11）：宿主不持 CodeEditor 原始引用，
    // 一律经 EditorController 命令访问（LuaEditor 绑定/解绑，仅主线程）。
    val editorController = remember { EditorController() }
    // U2（2026-08-12）：离场焦点释放兜底——任何离开编辑器页面的路径（返回/导航
    // 到权限/设置/工程信息页）都会销毁 AndroidView 内嵌的 sora 编辑器，若其持有
    // View 焦点，系统会把焦点转移到新页面首个可聚焦组件（历史缺陷：主界面
    // 「新建项目」按钮被聚焦）。onDispose 阶段编辑器实例仍在（LuaEditor 的解绑
    // 挂在本 body 组合之后），此处先清焦点再让 View 树销毁。
    DisposableEffect(Unit) {
        onDispose {
            // T5E（2026-08-12）：页面级作用域释放——取消在途格式化任务；顺序在
            // releaseFocus 后（view 销毁前一刻，格式化若正在主线程替换会自行降级）。
            editorController.disposeScope()
            editorController.releaseFocus()
        }
    }
    // 编辑器绑定门控（Q1，替代 60Hz 忙轮询）：LuaEditor update 阶段绑定后完成；
    // 跳行等待经 withTimeoutOrNull 挂起，编辑器错误态/未绑定超时后降级跳过。
    val editorReady = remember { CompletableDeferred<Unit>() }
    val snackbarHostState = remember { SnackbarHostState() }

    var showBuildDialog by remember { mutableStateOf(false) }
    // 脏文件待确认队列：单标签关闭时为单元素列表；「关闭其他/全部」时为批量队列，
    // 逐个确认后经 saveAndClose/discardAndClose 出队，取消即放弃剩余批次。
    var closeQueue by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }

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
    // 跳行目标（T4）：编辑器绑定到目标 Content 身份后消费（防对旧文档 setSelection）。
    var pendingJump by remember { mutableStateOf<PendingJump?>(null) }
    // P5.2（2026-08-11）：跳行对话框开关（⋮ 编辑 → 跳转到行）。
    var showGotoDialog by remember { mutableStateOf(false) }

    // P5.1（2026-08-11）查找/替换面板状态（全部宿主持有；搜索经防抖效应派发）。
    var searchActive by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var showReplace by remember { mutableStateOf(false) }
    var isRegex by remember { mutableStateOf(false) }
    var isCaseSensitive by remember { mutableStateOf(false) }
    var isWholeWord by remember { mutableStateOf(false) }
    var searchInfo by remember { mutableStateOf(SearchMatchInfo.NONE) }
    // 手动重搜触发器（替换动作后结果集失效，+1 强制防抖效应重跑）。
    var searchTick by remember { mutableIntStateOf(0) }

    fun closeSearchPanel() {
        editorController.stopSearch()
        searchActive = false
        searchInfo = SearchMatchInfo.NONE
    }

    fun runSearchNow() {
        searchTick++
    }

    val engineResultLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val data = result.data
            val output = data?.getStringExtra(EngineContract.EXTRA_OUTPUT) ?: ""
            val isError =
                data?.getBooleanExtra(EngineContract.EXTRA_ERROR, false) == true ||
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

    // 跳行：等编辑器**绑定到目标 Content**后定位（sora setSelection 0 基行号，自动滚动）。
    // T4（2026-08-10）：openTab 完成至 LuaEditor setText 生效之间存在窗口——
    // 先对旧文档 setSelection 会被随后的文本替换覆盖（定位丢失/错位）。
    // 以 EditorController.isShowing 身份判定为前置；身份比较免误判（同文件再次跳行零等待）。
    // 仅跳行文件切换的瞬态轮询（8ms 步进，非 60Hz 常驻）；编辑器错误态/初始化失败
    // 超时降级跳过定位，不阻塞不空转。
    LaunchedEffect(pendingJump) {
        val jump = pendingJump ?: return@LaunchedEffect
        val bound =
            withTimeoutOrNull(EDITOR_BIND_TIMEOUT_MS) {
                editorReady.await()
                while (!editorController.isShowing(jump.content)) {
                    delay(GOTO_BIND_POLL_MS)
                }
                true
            } ?: false
        if (bound) {
            editorController.setSelection(jump.line, 0)
        }
        pendingJump = null
    }

    // P5.1 查找防抖派发：面板开启且查询非空 → 250ms 后搜索；空查询 → stopSearch。
    // 手动重搜（searchTick）与查询/选项变化共用同一效应（替换后结果集失效用）。
    LaunchedEffect(searchActive, findQuery, isRegex, isCaseSensitive, isWholeWord, searchTick) {
        if (!searchActive) return@LaunchedEffect
        if (findQuery.isEmpty()) {
            editorController.stopSearch()
            searchInfo = SearchMatchInfo.NONE
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        val type =
            when {
                isRegex -> SearchMatchType.REGEX
                isWholeWord -> SearchMatchType.WHOLE_WORD
                else -> SearchMatchType.NORMAL
            }
        editorController.search(findQuery, type, !isCaseSensitive)
    }

    // P5.1 切标签守卫：面板开启时换文件 → 自动收起（sora 搜索绑定原编辑器实例，
    // 新实例无结果集，继续显示计数无意义；防对旧文档替换）。
    LaunchedEffect(selection.file) {
        if (searchActive) closeSearchPanel()
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
            // 携带目标 Content 身份：openTab 完成即选中该内容，后续仅等编辑器绑定。
            val content = session.selection.value.content ?: return@launch
            pendingJump = PendingJump(content, loc.line - 1)
        }
    }

    fun insertImport(globalName: String) {
        // 只读守卫（审计 R1）：insertText 直写会绕过 sora isEditable 门禁，只读下拒绝写入。
        if (appEditorConfig.readOnly) return
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
    // T3（2026-08-10）：全量脏标签落盘（非仅当前选中）——防抖窗口内切走标签后
    // 进程被杀不再丢失该标签修改（flushAllDirty 只写实际脏文件，成本同现状）。
    LaunchedEffect(Unit) {
        session.editTick.collectLatest {
            delay(AUTO_SAVE_DELAY_MS)
            if (!extPermissionMissing.value) session.flushAllDirty()
        }
    }

    // 运行结束提示（成功路径）：Snackbar 一次性消费。
    LaunchedEffect(snackbarMsg) {
        val msg = snackbarMsg ?: return@LaunchedEffect
        snackbarMsg = null
        snackbarHostState.showSnackbar(msg)
    }

    val selectedTab = tabs.firstOrNull { it.fileName == selectedFile }

    // T2（2026-08-10）：undo/redo 统一入口——执行后显式重算并提交会话
    // （对齐 Squircle EditorViewModel：点击处理内 content.undo() 后立即读 canUndo/canRedo）。
    // P1（2026-08-11 真只读）：按钮灰态只随 canUndo/canRedo（不随只读置灰，对齐 Squircle）；
    // 只读下的写命令经本守卫静默放弃（sora undo()/redo() 无 isEditable 门禁，需 VM 层拦截）。
    fun runUndoRedo(op: (EditorController) -> Unit) {
        if (appEditorConfig.readOnly) return
        op(editorController)
        session.onUndoRedoChanged(editorController.canUndo(), editorController.canRedo())
    }

    // T15（2026-08-11）：格式化统一入口（⋮「编辑→格式化」+ Ctrl+L 均经此）。
    // 只读守卫前置；后台计算 + 主线程批量替换由 EditorController.format 完成，
    // 完成后刷新 undo/redo 能力并展示「格式化完成」Snackbar。
    fun runFormat() {
        if (appEditorConfig.readOnly) return
        editorController.format(appEditorConfig.useTab, appEditorConfig.tabSize) { changed ->
            session.onUndoRedoChanged(editorController.canUndo(), editorController.canRedo())
            if (changed) snackbarMsg = context.getString(R.string.format_done)
        }
    }

    Scaffold(
        topBar = {
            EditorToolbar(
                // 标题显示工程名，不显示当前打开文件的路径（文件身份由标签行承载）。
                title = project.name,
                canUndo = selectedTab?.canUndo == true,
                canRedo = selectedTab?.canRedo == true,
                onBack = {
                    // U2：先释放编辑器 View 焦点再弹栈（防焦点转移到主界面首个可聚焦组件）。
                    editorController.releaseFocus()
                    session.flushSelectedDirtyAsync()
                    onBack()
                },
                onUndo = { runUndoRedo { it.undo() } },
                onRedo = { runUndoRedo { it.redo() } },
                onRun = {
                    requestRun()
                },
                moreActions =
                    buildList {
                        // 文件分类：文件树（占位，2026-08-11 用户拍板「可以先只做按钮」；
                        // 功能未接，点击提示占位文案）。
                        add(
                            EditorMoreAction(
                                category = stringResource(R.string.menu_category_file),
                                label = stringResource(R.string.menu_file_tree),
                            ) {
                                android.widget.Toast
                                    .makeText(
                                        context,
                                        context.getString(R.string.file_tree_coming_soon),
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                            },
                        )
                        // 编辑分类：查找（P5.1）+ 只读切换（P1.4）+ 格式化 + 跳行 + 设置直达。
                        add(
                            EditorMoreAction(
                                category = stringResource(R.string.menu_category_edit),
                                label = stringResource(R.string.menu_find),
                                enabled = selectedFile != null,
                            ) {
                                searchActive = true
                            },
                        )
                        // 格式化（Ctrl+L）+ 跳行 + 编辑器设置直达入口。
                        add(
                            EditorMoreAction(
                                category = stringResource(R.string.menu_category_edit),
                                label = stringResource(R.string.menu_goto_line),
                                enabled = selectedFile != null,
                            ) {
                                showGotoDialog = true
                            },
                        )
                        add(
                            EditorMoreAction(
                                category = stringResource(R.string.menu_category_edit),
                                label = stringResource(R.string.menu_format),
                                enabled = selectedTab != null && !appEditorConfig.readOnly,
                            ) {
                                runFormat()
                            },
                        )
                        add(
                            EditorMoreAction(
                                category = stringResource(R.string.menu_category_edit),
                                label = stringResource(R.string.menu_editor_settings),
                            ) {
                                onEditorSettings()
                            },
                        )
                        // 工程分类：信息 / 权限 / 打包 + AndroLua 设置直达入口。
                        addAll(
                            listOf(
                                EditorMoreAction(
                                    category = stringResource(R.string.menu_category_project),
                                    label = stringResource(R.string.project_properties),
                                ) {
                                    onProjectInfo()
                                },
                                EditorMoreAction(
                                    category = stringResource(R.string.menu_category_project),
                                    label = stringResource(R.string.project_permissions),
                                ) {
                                    onPermissions()
                                },
                                EditorMoreAction(
                                    category = stringResource(R.string.menu_category_project),
                                    label = stringResource(R.string.build_apk),
                                ) {
                                    showBuildDialog = true
                                },
                                EditorMoreAction(
                                    category = stringResource(R.string.menu_category_project),
                                    label = stringResource(R.string.menu_androlua_settings),
                                ) {
                                    onAndroLuaSettings()
                                },
                            ),
                        )
                    },
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // P5.1 查找/替换面板：位于编辑区上方，展开时编辑区自然收缩；关闭即释放。
            if (searchActive) {
                EditorSearchPanel(
                    findQuery = findQuery,
                    onFindQueryChange = { findQuery = it },
                    replaceQuery = replaceQuery,
                    onReplaceQueryChange = { replaceQuery = it },
                    showReplace = showReplace,
                    onToggleReplace = { showReplace = !showReplace },
                    isRegex = isRegex,
                    onIsRegexChange = { isRegex = it },
                    isCaseSensitive = isCaseSensitive,
                    onIsCaseSensitiveChange = { isCaseSensitive = it },
                    isWholeWord = isWholeWord,
                    onIsWholeWordChange = { isWholeWord = it },
                    matchInfo = searchInfo,
                    // 只读下替换置灰（sora 另有 isEditable 门禁，双保险）。
                    replaceEnabled = !appEditorConfig.readOnly,
                    // sora gotoNext/gotoPrevious 选中命中但不发 PublishSearchResultEvent，主动重读。
                    onNext = {
                        editorController.searchNext()
                        searchInfo = editorController.searchMatchInfo()
                    },
                    onPrevious = {
                        editorController.searchPrevious()
                        searchInfo = editorController.searchMatchInfo()
                    },
                    onReplace = {
                        // 替换后 sora 结果集失效：手动重搜刷新计数。
                        editorController.replaceCurrent(replaceQuery)
                        if (findQuery.isNotEmpty()) runSearchNow() else closeSearchPanel()
                    },
                    onReplaceAll = {
                        // 自实现全量替换（sora 版本带 ProgressDialog，AndroidView 上下文无
                        // window token 会崩），完成后重搜刷新计数。
                        val type =
                            when {
                                isRegex -> SearchMatchType.REGEX
                                isWholeWord -> SearchMatchType.WHOLE_WORD
                                else -> SearchMatchType.NORMAL
                            }
                        editorController.replaceAll(findQuery, replaceQuery, type, isCaseSensitive) {
                            if (findQuery.isNotEmpty()) runSearchNow() else closeSearchPanel()
                        }
                    },
                    onClose = { closeSearchPanel() },
                )
            }
            // 标签视图列表：状态类已合并（B-5），tabs 直接是 EditorTab，无需逐帧适配。
            EditorBody(
                session = session,
                contentPadding = PaddingValues(0.dp),
                tabs = tabs,
                selectedFile = selectedFile,
                content = content,
                editorConfig = appEditorConfig,
                controller = editorController,
                onEditorBound = {
                    // 绑定信号幂等完成（主线程 update 阶段触发；已完成则跳过，防重复 complete 抛异常）。
                    if (!editorReady.isCompleted) editorReady.complete(Unit)
                },
                onFormatRequest = { runFormat() },
                // P5.1：sora 后台搜索完成 → 主线程回调 → 刷新计数（面板展示 n/m）。
                onSearchResultPublished = { searchInfo = editorController.searchMatchInfo() },
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
    }

    // 构建 APK 对话框：唯一打包入口（P5，2026-08-11：打开即构建，无配置 UI）。
    if (showBuildDialog) {
        BuildApkDialog(
            project = project,
            projectManager = projectManager,
            onDismiss = { showBuildDialog = false },
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
            // 只读下「插入 import」直写 Content 会被门面拒绝：按钮置灰提示不可用
            // （insertImport 入口另有读守卫，双保险，见本文件 insertImport）。
            editable = !appEditorConfig.readOnly,
        )
    }

    // P5.2 跳行对话框：确认后走 pendingJump 消费效应（含编辑器绑定门控）。
    if (showGotoDialog) {
        GotoLineDialog(
            maxLine = editorController.lineCount(),
            onConfirm = { line ->
                val content =
                    selection.content ?: run {
                        showGotoDialog = false
                        return@GotoLineDialog
                    }
                pendingJump = PendingJump(content, line)
                showGotoDialog = false
            },
            onDismiss = { showGotoDialog = false },
        )
    }
}
