package com.lioisme.lide.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.androlua.LuaApplication
import com.lioisme.lide.R
import com.lioisme.lide.androlua.AlpManager
import com.lioisme.lide.androlua.PluginInfo
import com.lioisme.lide.androlua.PluginManager
import com.lioisme.lide.editor.TipIconButton
import com.lioisme.lide.import.AlpImportCoordinator
import com.lioisme.lide.ui.theme.LideTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginScreen(
    onRun: (PluginInfo) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val pluginManager = remember { PluginManager() }
    val alpCoordinator = remember(context) { AlpImportCoordinator(context) }
    var plugins by remember { mutableStateOf(listOf<PluginInfo>()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var pendingUninstall by remember { mutableStateOf<PluginInfo?>(null) }
    // WP-015 同名冲突挂起（与工程/示例导入同一状态机，B-1）。
    var pendingPluginConflict by remember { mutableStateOf<AlpImportCoordinator.Pending?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val strCannotReadFile = stringResource(R.string.cannot_read_file)
    val strPluginInvalid = stringResource(R.string.plugin_invalid)

    fun refresh() {
        scope.launch {
            isRefreshing = true
            try {
                plugins =
                    withContext(Dispatchers.IO) {
                        val extDir = LuaApplication.getInstance().luaExtDir
                        pluginManager.scan("$extDir/plugin")
                    }
            } finally {
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val alpPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                try {
                    // 统一经 AlpImportCoordinator 走 AlpManager 导入（§4.5：installFromAlp 与之
                    // 重复且无条件覆盖，此处收敛为 OVERWRITE 语义 + 冲突走确认弹窗）。
                    val tempPath = withContext(Dispatchers.IO) { alpCoordinator.copyUriToTemp(uri) }
                    if (tempPath == null) {
                        snackbarHostState.showSnackbar(strCannotReadFile)
                        return@launch
                    }
                    val plan = withContext(Dispatchers.IO) { alpCoordinator.plan(tempPath) }
                    if (plan == null) {
                        withContext(Dispatchers.IO) { alpCoordinator.discard(tempPath) }
                        snackbarHostState.showSnackbar(strPluginInvalid)
                        return@launch
                    }
                    if (plan.conflict) {
                        pendingPluginConflict = AlpImportCoordinator.Pending(tempPath, plan)
                        return@launch
                    }
                    val result =
                        withContext(Dispatchers.IO) {
                            alpCoordinator.import(tempPath, AlpManager.ImportMode.OVERWRITE)
                        }
                    if (result != null) {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.plugin_installed, plan.name),
                                Toast.LENGTH_SHORT,
                            ).show()
                        refresh()
                    } else {
                        snackbarHostState.showSnackbar(strPluginInvalid)
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.error_prefix, e.message),
                    )
                }
            }
        }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LideTopBar(
                title = stringResource(R.string.plugins),
                onBack = onBack,
                actions = {
                    TipIconButton(
                        tooltip = stringResource(R.string.plugin_install_alp),
                        onClick = { alpPickerLauncher.launch("*/*") },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.plugin_install_alp))
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refresh() },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            if (plugins.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.plugin_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.plugin_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    itemsIndexed(plugins) { _, plugin ->
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clickable { onRun(plugin) },
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                ),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Extension,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        plugin.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        if (plugin.author.isNotBlank()) {
                                            context.getString(R.string.plugin_version_author, plugin.version, plugin.author)
                                        } else {
                                            context.getString(R.string.plugin_version, plugin.version)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (plugin.description.isNotBlank()) {
                                        Text(
                                            plugin.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = stringResource(R.string.plugin_run),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = { pendingUninstall = plugin }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.plugin_uninstall),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingUninstall?.let { plugin ->
        AlertDialog(
            onDismissRequest = { pendingUninstall = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.plugin_uninstall_title)) },
            text = { Text(stringResource(R.string.plugin_uninstall_message, plugin.name)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingUninstall = null
                    scope.launch {
                        val deleted =
                            withContext(Dispatchers.IO) {
                                File(plugin.dir).deleteRecursively()
                            }
                        if (deleted) {
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.plugin_uninstall_done, plugin.name),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            refresh()
                        } else {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.plugin_uninstall_failed, plugin.name),
                            )
                        }
                    }
                }) { Text(stringResource(R.string.plugin_uninstall_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingUninstall = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    pendingPluginConflict?.let { pending ->
        AlpImportConflictDialog(
            name = pending.plan.name,
            onOverwrite = {
                pendingPluginConflict = null
                scope.launch {
                    val result =
                        withContext(Dispatchers.IO) {
                            alpCoordinator.import(pending.tempPath, AlpManager.ImportMode.OVERWRITE)
                        }
                    if (result != null) {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.plugin_installed, pending.plan.name),
                                Toast.LENGTH_SHORT,
                            ).show()
                        refresh()
                    } else {
                        snackbarHostState.showSnackbar(strPluginInvalid)
                    }
                }
            },
            onRename = {
                pendingPluginConflict = null
                scope.launch {
                    val result =
                        withContext(Dispatchers.IO) {
                            alpCoordinator.import(pending.tempPath, AlpManager.ImportMode.RENAME)
                        }
                    if (result != null) {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.plugin_installed, pending.plan.name),
                                Toast.LENGTH_SHORT,
                            ).show()
                        refresh()
                    } else {
                        snackbarHostState.showSnackbar(strPluginInvalid)
                    }
                }
            },
            onCancel = {
                pendingPluginConflict = null
                scope.launch { withContext(Dispatchers.IO) { alpCoordinator.discard(pending.tempPath) } }
            },
        )
    }
}
