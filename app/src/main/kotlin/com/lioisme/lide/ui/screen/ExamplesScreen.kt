package com.lioisme.lide.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.androlua.LuaApplication
import com.lioisme.lide.R
import com.lioisme.lide.androlua.AlpManager
import com.lioisme.lide.editor.TipIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 内置 .alp 示例：asset 文件名、导入后的工程目录名（须与 init.lua 的 appname 一致）、展示文案。 */
data class BuiltinExample(val asset: String, val dir: String, val nameRes: Int, val descRes: Int)

private val builtinExamples = listOf(
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
 * 导入复用 [AlpManager.import]（与用户导入 .alp 同一路径）；同名已存在时覆盖为出厂内容。
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
    val strImportFailed = stringResource(R.string.import_failed)
    val strImportSuccess = stringResource(R.string.examples_import_success)

    fun refreshInstalled() {
        val projectDir = File(LuaApplication.getInstance().luaExtDir, "project")
        installed = builtinExamples
            .filter { File(projectDir, it.dir).isDirectory }
            .map { it.dir }
            .toSet()
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
            val ok = withContext(Dispatchers.IO) {
                try {
                    val temp = File(context.cacheDir, "example_${System.currentTimeMillis()}_${example.asset}")
                    temp.parentFile?.mkdirs()
                    context.assets.open("examples/${example.asset}").use { input ->
                        input.copyTo(temp.outputStream())
                    }
                    val result = AlpManager(context).import(temp.absolutePath)
                    temp.delete()
                    result != null
                } catch (e: Exception) {
                    false
                }
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

    LaunchedEffect(Unit) { refreshInstalled() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.examples)) },
                navigationIcon = {
                    TipIconButton(tooltip = stringResource(R.string.cd_back), onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true },
            modifier = Modifier
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
            items(builtinExamples) { example ->
                val isInstalled = example.dir in installed
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
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
                                    if (isInstalled) R.string.examples_reimport else R.string.examples_import
                                )
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
