package com.lioisme.lide.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import com.lioisme.lide.engine.RuntimePaths
import com.lioisme.lide.import.AlpImportCoordinator
import com.lioisme.lide.project.AlpManager
import com.lioisme.lide.ui.theme.LideTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 内置 .alp 示例：asset 文件名、导入后的工程目录名（须与 init.lua 的 appname 一致）、展示文案。 */
data class BuiltinExample(
    val asset: String,
    val dir: String,
    val nameRes: Int,
    val descRes: Int,
)

private val builtinExamples =
    listOf(
        BuiltinExample("demo.alp", "demo", R.string.example_name_demo, R.string.example_desc_demo),
        BuiltinExample("DemoProject.alp", "DemoProject", R.string.example_name_demoproject, R.string.example_desc_demoproject),
        BuiltinExample("hello.alp", "hello", R.string.example_name_hello, R.string.example_desc_hello),
        BuiltinExample("kleinbottle.alp", "kleinbottle", R.string.example_name_kleinbottle, R.string.example_desc_kleinbottle),
        BuiltinExample("listview.alp", "listview", R.string.example_name_listview, R.string.example_desc_listview),
        BuiltinExample("live2d.alp", "live2d", R.string.example_name_live2d, R.string.example_desc_live2d),
        BuiltinExample("TestJson.alp", "TestJson", R.string.example_name_testjson, R.string.example_desc_testjson),
    )

/**
 * 内置示例界面：列出 assets/examples 目录下的 .alp 示例，点击导入到工程列表。
 * 导入复用 [AlpManager.import]（与用户导入 .alp 同一路径）；同名冲突弹确认（WP-015），
 * 默认（无冲突）直接导入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamplesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var installed by remember { mutableStateOf(emptySet<String>()) }
    var importing by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    // WP-015 待确认导入：临时 .alp 路径 + 无副作用计划（冲突态已解析）。
    val alpCoordinator = remember(context) { AlpImportCoordinator(context) }
    var pendingImport by remember { mutableStateOf<AlpImportCoordinator.Pending?>(null) }
    val strImportFailed = stringResource(R.string.import_failed)
    val strImportSuccess = stringResource(R.string.examples_import_success)
    // 精简模式（2026-08-07）：内置 .alp 由 Gradle 属性 lide.examples 控制是否生成
    // （默认零内置）。按 assets/examples 实际存在的资产过滤展示，空时给出空态提示；
    // 元数据列表（含名称/描述字符串）保留，供后续恢复 1-2 个代表性示例直接复用。
    val availableExamples =
        remember(context) {
            val present =
                runCatching { context.assets.list("examples") }
                    .getOrNull()
                    ?.filter { it.endsWith(".alp") }
                    ?.toSet()
                    .orEmpty()
            builtinExamples.filter { it.asset in present }
        }

    fun refreshInstalled() {
        val projectDir = RuntimePaths.projectsDir()
        installed =
            availableExamples
                .filter { File(projectDir, it.dir).isDirectory }
                .map { it.dir }
                .toSet()
    }

    /** 冲突确认后按模式执行导入（临时文件用完即删）。 */
    fun executeImport(
        tempPath: String,
        mode: AlpManager.ImportMode,
    ) {
        scope.launch {
            val ok =
                withContext(Dispatchers.IO) {
                    alpCoordinator.import(tempPath, mode) != null
                }
            importing = false
            if (ok) {
                Toast.makeText(context, strImportSuccess, Toast.LENGTH_SHORT).show()
                refreshInstalled()
            } else {
                snackbarHostState.showSnackbar(strImportFailed)
            }
        }
    }

    // 下拉刷新：重查各示例的已安装状态（触发式，无需重列静态示例列表）。
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            refreshInstalled()
            isRefreshing = false
        }
    }

    fun importExample(example: BuiltinExample) {
        if (importing) return
        importing = true
        scope.launch {
            // WP-015：先无副作用解析导入计划（含同名冲突态），冲突时弹确认，
            // 选择后按模式执行；无冲突直接导入。
            val temp =
                withContext(Dispatchers.IO) {
                    try {
                        context.assets.open("examples/${example.asset}").use { input ->
                            val f =
                                File(
                                    context.cacheDir,
                                    "example_${System.currentTimeMillis()}_${example.asset}",
                                )
                            f.parentFile?.mkdirs()
                            input.copyTo(f.outputStream())
                            f
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            if (temp == null) {
                importing = false
                snackbarHostState.showSnackbar(strImportFailed)
                return@launch
            }
            val plan = withContext(Dispatchers.IO) { alpCoordinator.plan(temp.absolutePath) }
            if (plan == null) {
                importing = false
                withContext(Dispatchers.IO) { alpCoordinator.discard(temp.absolutePath) }
                snackbarHostState.showSnackbar(strImportFailed)
                return@launch
            }
            if (plan.conflict) {
                importing = false
                pendingImport = AlpImportCoordinator.Pending(temp.absolutePath, plan)
                return@launch
            }
            executeImport(temp.absolutePath, AlpManager.ImportMode.OVERWRITE)
        }
    }

    LaunchedEffect(Unit) { refreshInstalled() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LideTopBar(
                title = stringResource(R.string.examples),
                onBack = onBack,
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.examples_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (availableExamples.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.examples_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(availableExamples) { example ->
                    val isInstalled = example.dir in installed
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors =
                            CardDefaults.cardColors(
                                // E1（2026-08-12）：surfaceVariant 已废弃——内容卡用 surfaceContainerLow。
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(example.nameRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    stringResource(example.descRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (isInstalled) {
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = stringResource(R.string.examples_installed),
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            stringResource(R.string.examples_installed),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = { importExample(example) },
                                enabled = !importing,
                            ) {
                                Text(
                                    stringResource(
                                        if (isInstalled) R.string.examples_reimport else R.string.examples_import,
                                    ),
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    pendingImport?.let { pending ->
        AlpImportConflictDialog(
            name = pending.plan.name,
            onOverwrite = {
                pendingImport = null
                executeImport(pending.tempPath, AlpManager.ImportMode.OVERWRITE)
            },
            onRename = {
                pendingImport = null
                executeImport(pending.tempPath, AlpManager.ImportMode.RENAME)
            },
            onCancel = {
                pendingImport = null
                scope.launch { withContext(Dispatchers.IO) { alpCoordinator.discard(pending.tempPath) } }
            },
        )
    }
}
