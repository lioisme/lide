package com.lioisme.delua.ui.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lioisme.delua.R
import com.lioisme.delua.androlua.LuaConfigParser
import com.lioisme.delua.androlua.PermissionResolver
import com.lioisme.delua.engine.EngineActivity
import com.lioisme.delua.engine.EngineResultParser
import com.lioisme.delua.engine.RunEngineLauncher
import com.lioisme.delua.editor.EditorConfig
import com.lioisme.delua.editor.EditorMoreAction
import com.lioisme.delua.editor.EditorTab
import com.lioisme.delua.editor.EditorTabs
import com.lioisme.delua.editor.EditorToolbar
import com.lioisme.delua.editor.FileNode
import com.lioisme.delua.editor.LuaEditor
import com.lioisme.delua.editor.ProjectFileTree
import com.lioisme.delua.editor.SearchBar
import com.lioisme.delua.model.Project
import com.lioisme.delua.project.ProjectManager
import com.lioisme.delua.session.EditorSession
import com.lioisme.delua.ui.theme.LocalIsDarkTheme
import com.lioisme.delua.ui.theme.SettingsManager
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
 * 布局：Scaffold（工具栏常驻顶栏）→ 抽屉内嵌内容区（经典侧边抽屉，位于顶栏之下，
 * 不覆盖状态栏/顶栏）→ 标签行 + 编辑器。窄屏 Modal 抽屉；宽屏（≥840dp）Permanent 常驻并排。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
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

    // 宽屏判定（官方 WindowSizeClass）：Expanded ≥840dp 启用常驻抽屉，窄屏经典 Modal。
    // 本工程未引入 activity 1.10+ 的 LocalActivity，经 Context 链解包宿主 Activity（确定性）。
    val windowSizeClass = calculateWindowSizeClass(LocalContext.current.findActivity())
    val isWide = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded

    val session = remember(project) { EditorSession(project, projectManager) }
    val tabs by session.tabs.collectAsState()
    val selectedFile by session.selectedFile.collectAsState()
    val content by session.selectedContent.collectAsState()
    android.util.Log.d("DeluaDebug", "EditorScreen project.name=${project.name} path=${project.path} main=${project.mainFile} infoV=$projectInfoVersion")

    val editorRef = remember { mutableStateOf<CodeEditor?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    // 非 saveable 抽屉状态：抽屉必须每次进入页面均为关闭态。
    // rememberDrawerState 是 rememberSaveable 实现（Saver 保存 currentValue），
    // 转屏/进程重建会把 Open 复活并产生可见「展开→收起」闪烁，故此处用官方裸构造。
    val drawerState = remember { DrawerState(DrawerValue.Closed) }

    var treeRoot by remember { mutableStateOf<FileNode?>(null) }
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
            val content = session.selectedContent.value ?: return@launch
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

    val requestRun: () -> Unit = {
        // 审计修正：工程声明权限先归一化（裸名 → android.permission. 全名），
        // 且只请求宿主 manifest 已声明的权限（宿主未声明的仅在打包时写入产物 manifest）。
        val declared = projectManager.loadUserPermissions(project)
            .map { PermissionResolver.normalize(it) }
        val hostDeclared = try {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_PERMISSIONS,
            )
            info.requestedPermissions?.toSet() ?: emptySet()
        } catch (_: Throwable) {
            emptySet()
        }
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
        treeRoot = withContext(Dispatchers.IO) { projectManager.listProjectTree(project) }
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
        // 抽屉关闭与文件打开互不依赖：关闭立即执行（杜绝「IO 期间敞开」窗口），打开保持原异步语义。
        scope.launch { drawerState.close() }
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
        // 抽屉策略：宽屏常驻并排（顶栏隐藏汉堡键）；窄屏经典 Modal 侧边抽屉。
        // 抽屉容器在 Scaffold 外层：Modal 抽屉打开时整屏（含顶栏）被 sheet+scrim 覆盖，
        // 否则 Scaffold 顶栏（z 序高于 content）会把抽屉顶部内容裁掉。
        NavigationDrawerPanel(
            isWide = isWide,
            drawerState = drawerState,
            drawerContent = {
                DrawerContent(
                    project = project,
                    projectInfoVersion = projectInfoVersion,
                    treeRoot = treeRoot,
                    selectedPath = selectedFile?.let { File(project.path, it).absolutePath },
                    onFileClick = { openFileFromTree(it) },
                    onPermissionsClick = {
                        scope.launch { drawerState.close() }
                        onPermissions()
                    },
                    onProjectInfoClick = {
                        scope.launch { drawerState.close() }
                        onProjectInfo()
                    },
                )
            },
        ) {
            Scaffold(
                topBar = {
                    EditorToolbar(
                        fileName = selectedFile.orEmpty(),
                        canUndo = selectedTab?.canUndo == true,
                        canRedo = selectedTab?.canRedo == true,
                        onBack = {
                            // 退出契约：先关抽屉再退出。close() 对已关闭状态立即返回（零延迟），
                            // 对开启状态先收起再 pop，杜绝「满屏抽屉随退出转场滑出」。
                            session.flushSelectedDirtyAsync()
                            scope.launch {
                                drawerState.close()
                                onBack()
                            }
                        },
                        onToggleDrawer = {
                            scope.launch {
                                if (drawerState.isOpen) drawerState.close() else drawerState.open()
                            }
                        },
                        onUndo = { editorRef.value?.undo() },
                        onRedo = { editorRef.value?.redo() },
                        onRun = {
                            requestRun()
                        },
                        onSearch = { isSearchVisible = true },
                        showDrawerToggle = !isWide,
                        // 工程级操作统一入口：右上角更多（⋮）菜单。
                        moreActions = listOf(
                            EditorMoreAction(stringResource(R.string.drawer_project_info)) {
                                onProjectInfo()
                            },
                            EditorMoreAction(stringResource(R.string.drawer_permissions)) {
                                onPermissions()
                            },
                            EditorMoreAction(stringResource(R.string.build_apk)) {
                                onBuildApk()
                            },
                        ),
                    )
                },
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState)
                },
            ) { padding ->
                EditorBody(
                    session = session,
                    contentPadding = padding,
                    tabs = tabs.map { it.tab },
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
                            }
                        ) {
                            Text(stringResource(R.string.discard))
                        }
                        TextButton(
                            onClick = {
                                session.saveAndClose(pendingFile)
                                closeQueue = closeQueue.drop(1)
                            }
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { closeQueue = emptyList() }) {
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
                            TextButton(onClick = { jumpToError() }) {
                                Text(stringResource(R.string.run_jump_to_line))
                            }
                        }
                        if (nilGlobal != null) {
                            TextButton(onClick = { insertImport(nilGlobal) }) {
                                Text(stringResource(R.string.run_insert_import, nilGlobal))
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { runResult = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}

private fun hasStoragePermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return Environment.isExternalStorageManager()
    }
    return true
}

/** 沿 ContextWrapper 链解包出宿主 Activity（Compose 环境恒可命中）。 */
private tailrec fun Context.findActivity(): Activity {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> error("无法解析宿主 Activity：context=$this")
    }
}

/**
 * 抽屉面板（工程名 + 文件树）。纯展示组件：树数据与选中态由宿主注入，
 * 点击回调上报宿主（宿主负责关闭抽屉并打开文件）。
 */
@Composable
private fun DrawerContent(
    project: Project,
    projectInfoVersion: Int,
    treeRoot: FileNode?,
    selectedPath: String?,
    onFileClick: (FileNode) -> Unit,
    onPermissionsClick: () -> Unit,
    onProjectInfoClick: () -> Unit,
) {
    // WP-004：工程属性保存后（版本自增）重读 init.lua appname，抽屉工程名即时更新。
    var displayName by remember(project, projectInfoVersion) { mutableStateOf(project.name) }
    LaunchedEffect(project, projectInfoVersion) {
        if (projectInfoVersion > 0) {
            displayName = withContext(Dispatchers.IO) {
                val file = File(project.path, "init.lua")
                if (file.exists()) {
                    val appname = LuaConfigParser.stringField(file.readText(), "appname")
                    appname?.takeIf { it.isNotBlank() } ?: project.name
                } else {
                    project.name
                }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                project.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Row(Modifier.padding(start = 4.dp)) {
            TextButton(
                onClick = onPermissionsClick,
            ) {
                Text(stringResource(R.string.drawer_permissions))
            }
            TextButton(
                onClick = onProjectInfoClick,
            ) {
                Text(stringResource(R.string.drawer_project_info))
            }
        }
        HorizontalDivider()
        val root = treeRoot
        if (root != null) {
            ProjectFileTree(
                root = root,
                selectedPath = selectedPath,
                onFileClick = onFileClick,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
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
            TextButton(onClick = onGrant) {
                Text(stringResource(R.string.editor_storage_permission_grant))
            }
        }
    }
}

/**
 * 抽屉容器策略（官方组件二选一）：
 * - 窄屏（默认）：[ModalNavigationDrawer] 经典侧边抽屉。抽屉容器位于 Scaffold 外层，
 *   打开时整屏（含顶栏与状态栏）被 sheet + scrim 覆盖——若放在 Scaffold content 槽内，
 *   顶栏 z 序高于 content，会裁掉抽屉顶部内容（实测 topBar 遮挡 sheet 顶部 256px）。
 *   `ModalDrawerSheet(drawerState)` 内置官方返回处理（PredictiveBackHandler，
 *   enabled 仅在抽屉打开时生效）——抽屉关闭时不注册，返回事件自然归 NavHost；
 *   Android 14+ 带预测性返回动画，与 navigation-compose 2.8.5 的 pop 转场无竞争，
 *   无需手写 BackHandler。打开状态可拖拽关闭；不启用边缘滑开（避免与编辑器触摸交互冲突）。
 * - 宽屏（≥840dp）：[PermanentNavigationDrawer] 常驻并排，抽屉恒可见。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationDrawerPanel(
    isWide: Boolean,
    drawerState: DrawerState,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    if (isWide) {
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet {
                    drawerContent()
                }
            },
        ) {
            content()
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                ModalDrawerSheet(drawerState) {
                    drawerContent()
                }
            },
        ) {
            content()
        }
    }
}

/**
 * 编辑器主体（标签行 + 编辑器 + 权限横幅 + 搜索面板）。与抽屉容器解耦：
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
    onCloseOthers: (String) -> Unit,
    onCloseAll: () -> Unit,
    onGrantStorage: () -> Unit,
    onSearchClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        if (tabs.isNotEmpty()) {
            EditorTabs(
                tabs = tabs,
                selectedFile = selectedFile,
                onTabClick = { session.select(it) },
                onTabClose = onTabClose,
                onTabMoved = { from, to -> session.moveTab(from, to) },
                onCloseOthers = onCloseOthers,
                onCloseAll = onCloseAll,
                modifier = Modifier.fillMaxWidth(),
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
                        // 兜底保存：编辑内容最后一次快照交回会话（会话作用域存活，安全）。
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
