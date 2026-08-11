package com.lioisme.lide.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import com.lioisme.lide.project.AlpManager
import com.lioisme.lide.editor.TipIconButton
import com.lioisme.lide.engine.RuntimePaths
import com.lioisme.lide.import.AlpImportCoordinator
import com.lioisme.lide.model.Project
import com.lioisme.lide.project.ProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 批量导入 .alp 的单批上限（串行队列逐个处理，防单批无线排队）。 */
private const val IMPORT_BATCH_LIMIT = 50

/** 导入队列条目：缓存临时 .alp 文件路径（系统选择器产物，导入完成后删除）。 */
private data class ImportSource(
    val path: String,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectListScreen(
    projectManager: ProjectManager,
    projects: List<Project>,
    refresh: suspend () -> Unit,
    onProjectClick: (Project) -> Unit,
    onProjectInfoClick: (Project) -> Unit = {},
    onSettingsClick: () -> Unit,
    refreshKey: Int = 0,
    pendingImportPaths: List<String> = emptyList(),
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var pendingExportProject by remember { mutableStateOf<Project?>(null) }
    var pendingDeleteProject by remember { mutableStateOf<Project?>(null) }
    // 长按 item 弹出的底部对话框（工程信息 / 导出 .alp / 删除统一收口于此）。
    var sheetProject by remember { mutableStateOf<Project?>(null) }
    // 主界面精简（用户决策）：示例 / API 浏览器迁入设置，导入收进 ⋮ 溢出菜单。
    var menuExpanded by remember { mutableStateOf(false) }
    // WP-005 工程重命名：挂起对象 + 名称输入。
    var pendingRenameProject by remember { mutableStateOf<Project?>(null) }
    var renameInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val strImportSuccess = stringResource(R.string.import_success)
    val strImportFailed = stringResource(R.string.import_failed)
    val strExportSuccess = stringResource(R.string.export_success)
    val strExportFailed = stringResource(R.string.export_failed)
    val strRenameDone = stringResource(R.string.project_renamed)
    val strRenameFailed = stringResource(R.string.project_rename_failed)

    // 单一刷新入口：首次进入 / 工程属性保存（refreshKey 自增）/ 导入完成后各触发一次；
    // 列表数据为 NavHost 层缓存（导航往返不销毁），刷新期间先展示缓存，无空态闪烁；
    // 键值组合天然去重，避免并发重复读盘（同一列表反复重建）。
    LaunchedEffect(Unit, refreshKey, pendingImportPaths.size) {
        refresh()
    }

    // 下拉刷新：键控效应替代组合期条件副作用。
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            refresh()
            isRefreshing = false
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // WP-015 批量导入队列 + 同名冲突挂起：逐个处理，遇冲突暂停等确认（覆盖/改名/取消）。
    val alpCoordinator = remember(context) { AlpImportCoordinator(context) }
    var importQueue by remember { mutableStateOf<List<ImportSource>>(emptyList()) }
    var conflictPending by remember { mutableStateOf<AlpImportCoordinator.Pending?>(null) }
    var importedProjectCount by remember { mutableStateOf(0) }

    fun tallyImport(result: AlpManager.AlpImportResult?) {
        if (result != null) {
            importedProjectCount++
        }
    }

    /** 队列结束汇总：Toast 结果 + 刷新列表 + 清计数（T15：复数资源 one/other）。 */
    fun finishImportBatch() {
        if (importedProjectCount > 0) {
            Toast.makeText(
                context,
                context.resources.getQuantityString(R.plurals.import_projects_success, importedProjectCount, importedProjectCount),
                Toast.LENGTH_SHORT,
            ).show()
            scope.launch { refresh() }
        } else {
            errorMsg = strImportFailed
        }
        importedProjectCount = 0
    }

    /** 队列推进：当前项完成（或失败/取消）后调用，直到队列空后汇总。 */
    fun advanceImportQueue() {
        val next =
            importQueue.firstOrNull() ?: run {
                finishImportBatch()
                return
            }
        importQueue = importQueue.drop(1)
        scope.launch {
            val sourcePath = next.path
            val plan = withContext(Dispatchers.IO) { alpCoordinator.plan(sourcePath) }
            if (plan == null) {
                withContext(Dispatchers.IO) { alpCoordinator.discard(sourcePath) }
                advanceImportQueue()
                return@launch
            }
            if (plan.conflict) {
                conflictPending = AlpImportCoordinator.Pending(sourcePath, plan)
                return@launch
            }
            val result =
                withContext(Dispatchers.IO) {
                    alpCoordinator.import(sourcePath, AlpManager.ImportMode.OVERWRITE)
                }
            tallyImport(result)
            advanceImportQueue()
        }
    }

    /** 冲突确认后的执行入口：以所选模式导入挂起项并继续队列。 */
    fun resolveConflict(mode: AlpManager.ImportMode) {
        val pending = conflictPending ?: return
        conflictPending = null
        scope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    alpCoordinator.import(pending.tempPath, mode, deleteAfter = pending.isTemp)
                }
            tallyImport(result)
            advanceImportQueue()
        }
    }

    LaunchedEffect(errorMsg) {
        if (errorMsg != null) {
            snackbarHostState.showSnackbar(errorMsg!!)
            delay(100)
            errorMsg = null
        }
    }

    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/zip"),
        ) { uri ->
            val project = pendingExportProject ?: return@rememberLauncherForActivityResult
            pendingExportProject = null
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val ok =
                    withContext(Dispatchers.IO) {
                        AlpManager(context).export(project.path, uri)
                    }
                if (ok) {
                    Toast.makeText(context, strExportSuccess, Toast.LENGTH_SHORT).show()
                } else {
                    errorMsg = strExportFailed
                }
            }
        }

    // 批量导入 .alp：一律走系统文件选择器（SAF 多选，MIME 限 zip/octet-stream），
    // 选中即复制为缓存临时文件进入队列（导入/放弃后删除）。
    val importLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            val sources =
                uris
                    .mapNotNull { alpCoordinator.copyUriToTemp(it) }
                    .take(IMPORT_BATCH_LIMIT)
            if (sources.isEmpty()) {
                errorMsg = strImportFailed
                return@rememberLauncherForActivityResult
            }
            importQueue = sources.map { ImportSource(it) }
            advanceImportQueue()
        }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Medium) },
                actions = {
                    TipIconButton(
                        tooltip = stringResource(R.string.cd_new_project),
                        onClick = { showCreateDialog = true },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_new_project))
                    }
                    TipIconButton(
                        tooltip = stringResource(R.string.cd_settings),
                        onClick = onSettingsClick,
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                    Box {
                        TipIconButton(
                            tooltip = stringResource(R.string.cd_more_options),
                            onClick = { menuExpanded = true },
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                        }
                        // 导入 .alp 收进溢出菜单（主界面精简）。
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_alp)) },
                                leadingIcon = { Icon(Icons.Default.FileDownload, stringResource(R.string.import_alp)) },
                                onClick = {
                                    menuExpanded = false
                                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                                },
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            if (projects.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.no_projects),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.tap_to_create),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item { Spacer(Modifier.height(4.dp)) }
                    itemsIndexed(projects, key = { _, project -> project.path }) { index, project ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onProjectClick(project) },
                                        onLongClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            sheetProject = project
                                        },
                                    ).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = project.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (project.packageName.isNotBlank()) {
                                    Text(
                                        text = project.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (index < projects.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.new_project)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.project_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newName.trim()
                        if (name.isNotBlank()) {
                            showCreateDialog = false
                            newName = ""
                            scope.launch {
                                val created =
                                    withContext(Dispatchers.IO) {
                                        projectManager.createProject(
                                            name,
                                            RuntimePaths.luaExtDir(),
                                        )
                                    }
                                if (created != null) {
                                    scope.launch { refresh() }
                                } else {
                                    errorMsg = context.getString(R.string.project_create_failed, name)
                                }
                            }
                        }
                    },
                ) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // 长按工程 item 的底部对话框：工程信息 / 重命名 / 导出 .alp / 删除。
    sheetProject?.let { project ->
        ModalBottomSheet(
            onDismissRequest = { sheetProject = null },
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                if (project.packageName.isNotBlank()) {
                    Text(
                        text = project.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                sheetProject = null
                                onProjectInfoClick(project)
                            }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // P2#19：图标 contentDescription 与相邻行文本一致（无障碍朗读）。
                    Icon(Icons.Default.Info, stringResource(R.string.project_properties), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.project_properties),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                sheetProject = null
                                pendingRenameProject = project
                                renameInput = project.name
                            }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Edit, stringResource(R.string.project_rename), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.project_rename),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                sheetProject = null
                                pendingExportProject = project
                                exportLauncher.launch("${project.name}.alp")
                            }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.FileUpload, stringResource(R.string.export_alp), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.export_alp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                sheetProject = null
                                pendingDeleteProject = project
                            }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Delete, stringResource(R.string.project_delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.project_delete),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    pendingDeleteProject?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingDeleteProject = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.delete_project_title, project.name)) },
            text = { Text(stringResource(R.string.delete_project_message, project.name)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteProject = null
                    scope.launch {
                        val deleted =
                            withContext(Dispatchers.IO) {
                                projectManager.deleteProject(project)
                            }
                        if (deleted) {
                            scope.launch { refresh() }
                        } else {
                            errorMsg = context.getString(R.string.project_delete_failed, project.name)
                        }
                    }
                }) { Text(stringResource(R.string.project_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteProject = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // WP-005 工程重命名：目录改名 + init.lua appname 同步（重名自动去重 -1/-2）。
    pendingRenameProject?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingRenameProject = null },
            title = { Text(stringResource(R.string.project_rename_title, project.name)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.project_rename_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = pendingRenameProject ?: return@TextButton
                        pendingRenameProject = null
                        scope.launch {
                            val renamed =
                                withContext(Dispatchers.IO) {
                                    projectManager.renameProject(target, renameInput)
                                }
                            if (renamed != null) {
                                Toast.makeText(context, strRenameDone, Toast.LENGTH_SHORT).show()
                                scope.launch { refresh() }
                            } else {
                                errorMsg = strRenameFailed
                            }
                        }
                    },
                    enabled = projectManager.isValidEntryName(renameInput),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRenameProject = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    conflictPending?.let { pending ->
        AlpImportConflictDialog(
            name = pending.plan.name,
            onOverwrite = { resolveConflict(AlpManager.ImportMode.OVERWRITE) },
            onRename = { resolveConflict(AlpManager.ImportMode.RENAME) },
            onCancel = {
                conflictPending = null
                scope.launch { withContext(Dispatchers.IO) { alpCoordinator.discard(pending.tempPath) } }
                advanceImportQueue()
            },
        )
    }
}
