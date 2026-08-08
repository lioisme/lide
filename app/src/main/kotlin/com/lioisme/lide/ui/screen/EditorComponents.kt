package com.lioisme.lide.ui.screen

import android.content.Context
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import com.lioisme.lide.editor.EditorConfig
import com.lioisme.lide.editor.EditorTab
import com.lioisme.lide.editor.EditorTabs
import com.lioisme.lide.editor.FileBrowser
import com.lioisme.lide.editor.FileNode
import com.lioisme.lide.editor.LanguageDetector
import com.lioisme.lide.editor.LuaEditor
import com.lioisme.lide.editor.SearchBar
import com.lioisme.lide.editor.pressAndHoldHapticFeedback
import com.lioisme.lide.model.Project
import com.lioisme.lide.project.ProjectEntryOps
import com.lioisme.lide.project.ProjectManager
import com.lioisme.lide.session.EditorSession
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** 宿主存储权限门：Android 11+ 需要「所有文件访问」授权。 */
internal fun hasStoragePermission(context: Context): Boolean {
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
 *
 * WP-005 文件操作：长按条目弹动作表（新建 .lua / 新建目录 / 重命名 / 删除，锚点
 * main.lua、init.lua 受保护）；右上 ＋ 在根级/当前目录新建。所有文件系统操作在
 * IO 线程执行，成功后回写列表并经回调同步编辑器会话（重命名/删除打开中的文件）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectFileBrowserDialog(
    project: Project,
    onFileOpen: (FileNode) -> Unit,
    onFileRenamed: (oldName: String, newName: String) -> Unit = { _, _ -> },
    onFileDeleted: (fileName: String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val projectManager = remember { ProjectManager() }
    val strNotSupported = stringResource(R.string.file_not_supported_open)
    val strOpFailed = stringResource(R.string.filetree_op_failed)
    val rootDir = remember(project) { File(project.path) }
    var currentDir by remember(project) { mutableStateOf(rootDir) }
    var entries by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }

    // WP-005 操作状态：动作 + 目标目录 + 长按目标（rename/delete 用）。
    var actionState by remember { mutableStateOf<BrowserActionState?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var addMenuExpanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    suspend fun loadEntries(dir: File): List<FileNode> =
        withContext(Dispatchers.IO) {
            // 全量展示：目录 + 所有文件（非文档文件在点击时提示，不在列表层过滤）。
            dir
                .listFiles()
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

    /** 工程相对路径（编辑器标签键，/ 分隔）。 */
    fun relOf(path: String): String =
        try {
            File(path).relativeTo(rootDir).invariantSeparatorsPath
        } catch (_: Throwable) {
            File(path).name
        }

    /** 执行文件操作（IO 线程）；成功后回写列表 + 同步会话回调。 */
    fun executeAction(state: BrowserActionState) {
        if (busy) return
        busy = true
        scope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    when (state.action) {
                        BrowserAction.NEW_FILE -> {
                            projectManager.createProjectFile(project, state.dir.absolutePath, nameInput)
                        }

                        BrowserAction.NEW_DIR -> {
                            projectManager.createProjectDirectory(project, state.dir.absolutePath, nameInput)
                        }

                        BrowserAction.RENAME -> {
                            val target = state.target ?: return@withContext false
                            val finalName =
                                projectManager.renameProjectEntry(project, target.path, nameInput)
                            if (finalName != null) {
                                val oldRel = relOf(target.path)
                                val newRel = relOf(File(state.dir, finalName).absolutePath)
                                onFileRenamed(oldRel, newRel)
                                true
                            } else {
                                false
                            }
                        }

                        BrowserAction.DELETE -> {
                            val target = state.target ?: return@withContext false
                            if (projectManager.deleteProjectEntry(project, target.path)) {
                                onFileDeleted(relOf(target.path))
                                true
                            } else {
                                false
                            }
                        }

                        BrowserAction.MENU -> {
                            false
                        }
                    }
                }
            busy = false
            actionState = null
            if (result) {
                entries = loadEntries(currentDir)
            } else {
                Toast.makeText(context, strOpFailed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 标题 = 当前目录相对工程的路径（根目录显示工程名），辅助定位。
    val title =
        remember(currentDir, rootDir) {
            val rel =
                try {
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
            Column {
                // 新建入口：当前目录右上角 ＋（下拉：新建 Lua 文件 / 新建目录）。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.filetree_current_dir_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        IconButton(onClick = { addMenuExpanded = true }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.filetree_new),
                            )
                        }
                        DropdownMenu(
                            expanded = addMenuExpanded,
                            onDismissRequest = { addMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.filetree_new_file)) },
                                onClick = {
                                    addMenuExpanded = false
                                    actionState = BrowserActionState(BrowserAction.NEW_FILE, currentDir, null)
                                    nameInput = ""
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.filetree_new_dir)) },
                                onClick = {
                                    addMenuExpanded = false
                                    actionState = BrowserActionState(BrowserAction.NEW_DIR, currentDir, null)
                                    nameInput = ""
                                },
                            )
                        }
                    }
                }
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
                    // WP-005：长按条目弹动作表（锚点文件仅提示受保护）。
                    onNodeLongClick = { node ->
                        if (ProjectEntryOps.isProjectAnchor(node.name)) {
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.filetree_anchor_protected),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        } else {
                            actionState =
                                BrowserActionState(
                                    BrowserAction.MENU,
                                    File(node.path).parentFile ?: currentDir,
                                    node,
                                )
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp),
                )
            }
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

    // ---- WP-005 动作对话框 ----

    val action = actionState
    if (action != null) {
        when (action.action) {
            BrowserAction.MENU -> {
                val target = action.target ?: return
                AlertDialog(
                    onDismissRequest = { actionState = null },
                    title = {
                        Text(
                            text = target.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    text = {
                        Column {
                            if (target.isDirectory) {
                                ActionSheetRow(
                                    icon = {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    },
                                    label = stringResource(R.string.filetree_new_file),
                                ) {
                                    actionState = BrowserActionState(BrowserAction.NEW_FILE, File(target.path), null)
                                    nameInput = ""
                                }
                                ActionSheetRow(
                                    icon = {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    },
                                    label = stringResource(R.string.filetree_new_dir),
                                ) {
                                    actionState = BrowserActionState(BrowserAction.NEW_DIR, File(target.path), null)
                                    nameInput = ""
                                }
                            }
                            ActionSheetRow(
                                icon = {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp),
                                    )
                                },
                                label = stringResource(R.string.filetree_rename),
                            ) {
                                nameInput = target.name
                                actionState = BrowserActionState(BrowserAction.RENAME, File(target.path).parentFile ?: currentDir, target)
                            }
                            ActionSheetRow(
                                icon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(22.dp),
                                    )
                                },
                                label = stringResource(R.string.filetree_delete),
                                labelColor = MaterialTheme.colorScheme.error,
                            ) {
                                actionState = BrowserActionState(BrowserAction.DELETE, File(target.path).parentFile ?: currentDir, target)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { actionState = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }

            BrowserAction.NEW_FILE, BrowserAction.NEW_DIR -> {
                val isFile = action.action == BrowserAction.NEW_FILE
                AlertDialog(
                    onDismissRequest = { actionState = null },
                    title = {
                        Text(stringResource(if (isFile) R.string.filetree_new_file else R.string.filetree_new_dir))
                    },
                    text = {
                        Column {
                            Text(
                                stringResource(
                                    if (isFile) R.string.filetree_new_file_hint else R.string.filetree_new_dir_hint,
                                    relOf(action.dir.absolutePath),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                singleLine = true,
                                placeholder = {
                                    Text(stringResource(if (isFile) R.string.filetree_new_file_placeholder else R.string.filetree_new_dir_placeholder))
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { executeAction(action) },
                            enabled = projectManager.isValidEntryName(nameInput) && !busy,
                        ) {
                            Text(stringResource(R.string.confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { actionState = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }

            BrowserAction.RENAME -> {
                AlertDialog(
                    onDismissRequest = { actionState = null },
                    title = { Text(stringResource(R.string.filetree_rename)) },
                    text = {
                        Column {
                            Text(
                                stringResource(R.string.filetree_rename_hint, action.target?.name.orEmpty()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { executeAction(action) },
                            enabled = projectManager.isValidEntryName(nameInput) && !busy,
                        ) {
                            Text(stringResource(R.string.confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { actionState = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }

            BrowserAction.DELETE -> {
                val target = action.target ?: return
                AlertDialog(
                    onDismissRequest = { actionState = null },
                    icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    title = { Text(stringResource(R.string.filetree_delete_title)) },
                    text = {
                        Text(
                            stringResource(
                                if (target.isDirectory) R.string.filetree_delete_dir_message else R.string.filetree_delete_message,
                                target.name,
                            ),
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { executeAction(action) },
                            enabled = !busy,
                        ) {
                            Text(stringResource(R.string.filetree_delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { actionState = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }
        }
    }
}

/** 文件树动作（WP-005）：长按菜单 / 新建文件 / 新建目录 / 重命名 / 删除。 */
private enum class BrowserAction { MENU, NEW_FILE, NEW_DIR, RENAME, DELETE }

/** 文件树操作状态：动作 + 执行目录（新建/重命名/删除的父目录）+ 目标条目。 */
private data class BrowserActionState(
    val action: BrowserAction,
    val dir: File,
    val target: FileNode?,
)

/** 动作表行：图标 + 标签，整行可点（对齐工程列表底部对话框风格）。 */
@Composable
private fun ActionSheetRow(
    icon: @Composable () -> Unit,
    label: String,
    labelColor: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .pressAndHoldHapticFeedback()
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 可打开编辑的文档判定：语言文件消费 :editor 唯一事实源 [LanguageDetector]
 * （P0-3，扩展名 → scope 由 :editor 维护，app 不再维护语言双表）；
 * 纯文本附加扩展名（:editor 以 `text.plain` 打开）单独列出。
 * 不含扩展名的文件视为文档（如 Makefile / LICENSE）。
 * 其余文件（二进制、归档、图片等）点击提示「不支持打开」。
 */
private val PLAIN_TEXT_EXTENSIONS =
    setOf(
        "txt",
        "csv",
        "log",
        "ps1",
        "svg",
    )

private fun isDocumentFile(name: String): Boolean {
    if (LanguageDetector.isKnownFile(name)) return true
    val ext = name.substringAfterLast('.', "")
    return ext.isEmpty() || ext.lowercase() in PLAIN_TEXT_EXTENSIONS
}

@Composable
internal fun StoragePermissionBanner(
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
internal fun EditorBody(
    session: EditorSession,
    contentPadding: PaddingValues,
    tabs: List<EditorTab>,
    selectedFile: String?,
    content: Content?,
    isDarkTheme: Boolean,
    editorConfig: EditorConfig,
    editorRef: AtomicReference<CodeEditor?>,
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
            .padding(contentPadding),
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
                .weight(1f),
        ) {
            val currentContent = content
            if (currentContent != null && selectedFile != null) {
                LuaEditor(
                    content = currentContent,
                    isDarkTheme = isDarkTheme,
                    config = editorConfig,
                    fileName = selectedFile,
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
                        editorProvider = { editorRef.get() },
                        onClose = onSearchClose,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
