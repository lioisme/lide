package com.lioisme.delua.ui.screen

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lioisme.delua.R
import com.lioisme.delua.androlua.LuaConfigParser
import com.lioisme.delua.editor.TipIconButton
import com.lioisme.delua.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 工程属性页字段元信息（字段名 → 标签资源）。顺序与 pro projectinfo 页面一致。 */
private data class InfoField(val key: String, val labelRes: Int)

private val STRING_FIELDS = listOf(
    InfoField("appname", R.string.info_appname),
    InfoField("appver", R.string.info_appver),
    InfoField("appcode", R.string.info_appcode),
    InfoField("appsdk", R.string.info_appsdk),
    InfoField("path_pattern", R.string.info_path_pattern),
    InfoField("packagename", R.string.info_packagename),
    InfoField("theme", R.string.info_theme),
    InfoField("app_key", R.string.info_app_key),
    InfoField("app_channel", R.string.info_app_channel),
    InfoField("developer", R.string.info_developer),
    InfoField("description", R.string.info_description),
)

private data class InfoState(
    val values: Map<String, String>,
    val debugMode: Boolean,
    val permissions: List<String>,
)

/**
 * 工程属性页（WP-004，pro projectinfo 平移）：读取/改写工程 init.lua 的
 * appname/appver/appcode/appsdk/path_pattern/packagename/theme/app_key/
 * app_channel/developer/description/debugmode/user_permission 字段。
 * 保存为结构化文本改写（LuaConfigParser），保留注释与无关内容；theme/debugmode
 * 仅持久化（运行时生效属 WP-014）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectInfoScreen(
    project: Project,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<InfoState?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val strSaveSuccess = stringResource(R.string.info_save_success)
    val strSaveFailed = stringResource(R.string.info_save_failed)
    val strLoadFailed = stringResource(R.string.info_load_failed)

    LaunchedEffect(project) {
        state = withContext(Dispatchers.IO) {
            val file = File(project.path, "init.lua")
            if (!file.exists()) {
                null
            } else {
                val content = file.readText()
                val values = STRING_FIELDS.associate { f ->
                    f.key to (LuaConfigParser.stringField(content, f.key) ?: "")
                }
                InfoState(
                    values = values,
                    debugMode = LuaConfigParser.stringField(content, "debugmode")?.let {
                        it != "false" && it != "0"
                    } ?: true,
                    permissions = LuaConfigParser.stringArrayField(content, "user_permission"),
                )
            }
        }
        if (state == null) {
            Toast.makeText(context, strLoadFailed, Toast.LENGTH_SHORT).show()
            onBack()
            return@LaunchedEffect
        }
        loaded = true
    }

    fun save() {
        val s = state ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val file = File(project.path, "init.lua")
                    if (!file.exists()) return@withContext false
                    var content = file.readText()
                    for (f in STRING_FIELDS) {
                        val v = s.values[f.key]?.trim().orEmpty()
                        content = LuaConfigParser.setField(content, f.key, LuaConfigParser.quoteLuaString(v))
                    }
                    content = LuaConfigParser.setField(
                        content, "debugmode", if (s.debugMode) "true" else "false"
                    )
                    content = LuaConfigParser.setStringArrayField(
                        content, "user_permission", s.permissions.map { com.lioisme.delua.androlua.PermissionResolver.normalize(it) }
                    )
                    file.writeText(content)
                    true
                } catch (t: Throwable) {
                    false
                }
            }
            if (ok) {
                Toast.makeText(context, strSaveSuccess, Toast.LENGTH_SHORT).show()
                onSaved()
            } else {
                Toast.makeText(context, strSaveFailed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.info_title)) },
                navigationIcon = {
                    TipIconButton(tooltip = stringResource(R.string.cd_back), onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        }
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.info_basic_group), fontWeight = FontWeight.Medium) }
            items(STRING_FIELDS) { f ->
                OutlinedTextField(
                    value = s.values[f.key].orEmpty(),
                    onValueChange = { v ->
                        state = s.copy(values = s.values + (f.key to v))
                    },
                    label = { Text(stringResource(f.labelRes)) },
                    minLines = if (f.key == "description") 2 else 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.info_debugmode),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = s.debugMode,
                        onCheckedChange = { state = s.copy(debugMode = it) },
                    )
                }
            }
            item { HorizontalDivider() }
            item {
                OutlinedButton(
                    onClick = { showPermissionDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.info_permissions_btn, s.permissions.size))
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Button(
                    onClick = { save() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.info_save))
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showPermissionDialog) {
        val s = state ?: return
        var draft by remember { mutableStateOf(s.permissions.toSet()) }
        val known = PermissionCatalog.ALL.map { it.first }.toSet()
        val allOptions = (PermissionCatalog.ALL + (s.permissions - known).map { it to it })
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(stringResource(R.string.info_permissions_title)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(allOptions) { (perm, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { draft = if (perm in draft) draft - perm else draft + perm }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = perm in draft,
                                onCheckedChange = { c ->
                                    draft = if (c) draft + perm else draft - perm
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    state = s.copy(permissions = draft.toList())
                }) { Text(stringResource(R.string.info_permissions_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

