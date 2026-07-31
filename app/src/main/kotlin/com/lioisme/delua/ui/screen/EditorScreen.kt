package com.lioisme.delua.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lioisme.delua.R
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
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val AUTO_SAVE_DELAY_MS = 500L

/**
 * 编辑器页面（会话驱动）。
 *
 * 重组模型（对齐 Squircle `EditorViewModel`）：
 * - 每键编辑只触发会话内部 [EditorSession.editTick]（StateFlow，不重组）；
 *   脏标记 / undo 能力仅在值实际变化时经 [EditorSession.tabs] 发射——稳态打字零重组；
 * - 搜索面板自持全部状态，打字与搜索结果只重组 SearchBar 子树；
 * - 文件切换走 [Content] 缓存复用，零读盘、零 O(n) 重建（除首次打开）。
 *
 * 布局：ModalNavigationDrawer（侧边栏工程树）→ Scaffold（工具栏 + 标签行 + 编辑器）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    project: Project,
    projectManager: ProjectManager,
    onBack: () -> Unit,
    onRun: (fileName: String) -> Unit,
    onDependencies: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkTheme = LocalIsDarkTheme.current
    val appEditorConfig by SettingsManager.editorConfig.collectAsState()

    val session = remember(project) { EditorSession(project, projectManager) }
    val tabs by session.tabs.collectAsState()
    val selectedFile by session.selectedFile.collectAsState()
    val content by session.selectedContent.collectAsState()

    val editorRef = remember { mutableStateOf<io.github.rosemoe.sora.widget.CodeEditor?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var treeRoot by remember { mutableStateOf<FileNode?>(null) }
    var isSearchVisible by remember { mutableStateOf(false) }
    var pendingCloseFile by remember { mutableStateOf<String?>(null) }

    val extPermissionMissing = remember {
        mutableStateOf(!hasStoragePermission(context))
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        extPermissionMissing.value = !hasStoragePermission(context)
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

    fun getSearcher(): EditorSearcher? = editorRef.value?.getSearcher()

    fun openFileFromTree(node: FileNode) {
        if (node.isDirectory) return
        val prefix = project.path + File.separator
        val relative = if (node.path.startsWith(prefix)) node.path.removePrefix(prefix) else node.name
        scope.launch {
            session.openTab(relative)
            drawerState.close()
        }
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
        ModalNavigationDrawer(
            drawerState = drawerState,
            // 关闭手势，避免与编辑器触摸交互冲突（仅工具栏汉堡键开合）。
            gesturesEnabled = false,
            drawerContent = {
                ModalDrawerSheet {
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                project.name,
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
                        HorizontalDivider()
                        val root = treeRoot
                        if (root != null) {
                            ProjectFileTree(
                                root = root,
                                selectedPath = selectedFile?.let { File(project.path, it).absolutePath },
                                onFileClick = ::openFileFromTree,
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            },
        ) {
            Scaffold(
                topBar = {
                    EditorToolbar(
                        fileName = selectedFile.orEmpty(),
                        canUndo = selectedTab?.canUndo == true,
                        canRedo = selectedTab?.canRedo == true,
                        onBack = {
                            session.flushSelectedDirtyAsync()
                            onBack()
                        },
                        onToggleDrawer = { scope.launch { drawerState.open() } },
                        onUndo = { editorRef.value?.undo() },
                        onRedo = { editorRef.value?.redo() },
                        onRun = {
                            scope.launch {
                                session.flushSelectedDirty()
                                onRun(project.mainFile)
                            }
                        },
                        onSearch = { isSearchVisible = true },
                        onDependencies = onDependencies,
                    )
                }
            ) { padding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    if (tabs.isNotEmpty()) {
                        EditorTabs(
                            tabs = tabs.map { it.tab },
                            selectedFile = selectedFile,
                            onTabClick = { session.select(it) },
                            onTabClose = { file ->
                                if (session.requestClose(file)) pendingCloseFile = file
                            },
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
                                config = appEditorConfig,
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
                            if (extPermissionMissing.value) {
                                StoragePermissionBanner(
                                    onGrant = {
                                        manageStorageLauncher.launch(
                                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (isSearchVisible) {
                                SearchBar(
                                    editorProvider = { editorRef.value },
                                    onClose = {
                                        isSearchVisible = false
                                        getSearcher()?.stopSearch()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }

        val pendingFile = pendingCloseFile
        if (pendingFile != null) {
            AlertDialog(
                onDismissRequest = { pendingCloseFile = null },
                title = { Text(stringResource(R.string.close_file_title)) },
                text = { Text(stringResource(R.string.close_file_message, pendingFile)) },
                confirmButton = {
                    Row {
                        TextButton(
                            onClick = {
                                session.discardAndClose(pendingFile)
                                pendingCloseFile = null
                            }
                        ) {
                            Text(stringResource(R.string.discard))
                        }
                        TextButton(
                            onClick = {
                                session.saveAndClose(pendingFile)
                                pendingCloseFile = null
                            }
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingCloseFile = null }) {
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
