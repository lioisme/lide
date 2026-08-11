package com.lioisme.lide.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import com.lioisme.lide.model.Project
import com.lioisme.lide.project.LuaConfigParser
import com.lioisme.lide.project.PermissionResolver
import com.lioisme.lide.project.ProjectManager
import com.lioisme.lide.ui.theme.LideTopBar
import com.lioisme.lide.ui.theme.ThemeCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 工程属性页（WP-004 / P1-P4，2026-08-11 重构）：
 *
 * - 分区展示：基本信息（appname/appver/appcode/packagename/developer/description）、
 *   系统（目标 SDK/最低 SDK/主题/屏幕方向）、高级（app_key/app_channel/debugmode）、
 *   权限（user_permission 搜索选择器）；
 * - 目标 SDK（pro `appsdk` 字段，打包写 manifest targetSdkVersion）与最低 SDK
 *   （`minsdk` 字段）为「保持宿主（字段缺失）/ 自定义」下拉；主题（`theme` 字段，
 *   pro 同名）为「跟随宿主 / 常见主题别名 / 反射全部 Theme_*」下拉；
 * - 保存为结构化文本改写（[LuaConfigParser]，保留注释与无关内容）；「跟随宿主」
 *   对应移除字段（[LuaConfigParser.removeField]）；运行时侧（LuaActivity theme /
 *   debugmode）与打包侧（appsdk→targetSdkVersion）均为既有消费路径，本页只持久化。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProjectInfoScreen(
    project: Project,
    projectManager: ProjectManager,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<ProjectInfoState?>(null) }
    var original by remember { mutableStateOf<ProjectInfoState?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val strSaveSuccess = stringResource(R.string.info_save_success)
    val strSaveFailed = stringResource(R.string.info_save_failed)
    val strLoadFailed = stringResource(R.string.info_load_failed)
    val strSdkInvalid = stringResource(R.string.info_sdk_invalid)

    LaunchedEffect(project) {
        val loadedState =
            withContext(Dispatchers.IO) {
                // B-1：init.lua 读写收敛到 ProjectManager 域服务，UI 不自摸文件。
                val content = projectManager.readInitContent(project) ?: return@withContext null
                ProjectInfoState(
                    values = TEXT_FIELDS.associate { f -> f.key to (LuaConfigParser.stringField(content, f.key) ?: "") },
                    debugMode =
                        LuaConfigParser.stringField(content, "debugmode")?.let {
                            it != "false" && it != "0"
                        } ?: true,
                    permissions = LuaConfigParser.stringArrayField(content, "user_permission"),
                    minSdk = intField(content, "minsdk") ?: intField(content, "minSdk"),
                    targetSdk = intField(content, "appsdk") ?: intField(content, "targetSdk"),
                    theme = LuaConfigParser.stringField(content, "theme"),
                )
            }
        if (loadedState == null) {
            Toast.makeText(context, strLoadFailed, Toast.LENGTH_SHORT).show()
            onBack()
            return@LaunchedEffect
        }
        state = loadedState
        original = loadedState
        loaded = true
    }

    fun save() {
        val s = state ?: return
        // 校验：自定义目标 SDK 不得低于最低 SDK（有效值 = 自定义值或宿主默认）。
        if (s.minSdk != null && s.targetSdk != null && s.minSdk > s.targetSdk) {
            Toast.makeText(context, strSdkInvalid, Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            val ok =
                withContext(Dispatchers.IO) {
                    projectManager.writeInitFields(project) { content ->
                        var updated = content
                        for (f in TEXT_FIELDS) {
                            updated =
                                LuaConfigParser.setField(
                                    updated,
                                    f.key,
                                    LuaConfigParser.quoteLuaString(s.values[f.key]?.trim().orEmpty()),
                                )
                        }
                        updated =
                            LuaConfigParser.setField(
                                updated,
                                "debugmode",
                                if (s.debugMode) "true" else "false",
                            )
                        updated = writeSdkField(updated, "appsdk", s.targetSdk)
                        updated = writeSdkField(updated, "minsdk", s.minSdk)
                        updated =
                            if (s.theme == null) {
                                LuaConfigParser.removeField(updated, "theme")
                            } else {
                                LuaConfigParser.setField(updated, "theme", LuaConfigParser.quoteLuaString(s.theme))
                            }
                        LuaConfigParser.setStringArrayField(
                            updated,
                            "user_permission",
                            s.permissions.map { PermissionResolver.normalize(it) },
                        )
                    }
                }
            if (ok) {
                original = s
                Toast.makeText(context, strSaveSuccess, Toast.LENGTH_SHORT).show()
                onSaved()
            } else {
                Toast.makeText(context, strSaveFailed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val dirty = state != original

    Scaffold(
        topBar = {
            LideTopBar(
                title = stringResource(R.string.project_properties),
                onBack = onBack,
            )
        },
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
            // ===== 基本信息 =====
            item { SectionHeader(R.string.info_section_basic) }
            items(BASIC_FIELDS) { f ->
                OutlinedTextField(
                    value = s.values[f.key].orEmpty(),
                    onValueChange = { v -> state = s.copy(values = s.values + (f.key to v)) },
                    label = { Text(stringResource(f.labelRes)) },
                    minLines = if (f.key == "description") 2 else 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ===== 系统 =====
            item { SectionHeader(R.string.info_section_system) }
            item {
                SdkDropdown(
                    label = stringResource(R.string.info_target_sdk),
                    hostValue = HOST_TARGET_SDK,
                    value = s.targetSdk,
                    customCandidates = (HOST_MIN_SDK..HOST_TARGET_SDK).toList().reversed(),
                    onSelect = { state = s.copy(targetSdk = it) },
                )
            }
            item {
                SdkDropdown(
                    label = stringResource(R.string.info_min_sdk),
                    hostValue = HOST_MIN_SDK,
                    value = s.minSdk,
                    customCandidates = (1..HOST_TARGET_SDK).toList().reversed(),
                    onSelect = { state = s.copy(minSdk = it) },
                )
            }
            item {
                ThemeDropdown(
                    current = s.theme,
                    onSelect = { state = s.copy(theme = it) },
                )
            }
            item {
                OutlinedTextField(
                    value = s.values["path_pattern"].orEmpty(),
                    onValueChange = { v -> state = s.copy(values = s.values + ("path_pattern" to v)) },
                    label = { Text(stringResource(R.string.info_path_pattern)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ===== 高级 =====
            item { SectionHeader(R.string.info_section_advanced) }
            items(ADVANCED_FIELDS) { f ->
                OutlinedTextField(
                    value = s.values[f.key].orEmpty(),
                    onValueChange = { v -> state = s.copy(values = s.values + (f.key to v)) },
                    label = { Text(stringResource(f.labelRes)) },
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

            // ===== 权限 =====
            item { SectionHeader(R.string.info_section_permissions) }
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
                    enabled = dirty,
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
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(stringResource(R.string.info_permissions_title)) },
            text = {
                PermissionPickerList(
                    catalog = PermissionCatalog.ENTRIES,
                    unknownPermissions = (s.permissions - PermissionCatalog.knownNames).sorted(),
                    selected = draft,
                    onToggle = { perm -> draft = if (perm in draft) draft - perm else draft + perm },
                    modifier = Modifier.heightIn(max = 480.dp),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        state = s.copy(permissions = draft.toList())
                    },
                ) { Text(stringResource(R.string.info_permissions_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** 文本字段元信息（字段名 → 标签资源）。 */
private data class InfoField(
    val key: String,
    val labelRes: Int,
)

/** 基本信息字段（pro projectinfo 同序）。 */
private val BASIC_FIELDS =
    listOf(
        InfoField("appname", R.string.info_appname),
        InfoField("appver", R.string.info_appver),
        InfoField("appcode", R.string.info_appcode),
        InfoField("packagename", R.string.info_packagename),
        InfoField("developer", R.string.info_developer),
        InfoField("description", R.string.info_description),
    )

/** 高级字段。 */
private val ADVANCED_FIELDS =
    listOf(
        InfoField("app_key", R.string.info_app_key),
        InfoField("app_channel", R.string.info_app_channel),
    )

/** 全部文本字段（保存循环用）。 */
private val TEXT_FIELDS = BASIC_FIELDS + ADVANCED_FIELDS

/** 宿主 compileSdk/minSdk（与 app/build.gradle.kts 对齐；仅用于下拉候选与校验展示）。 */
private const val HOST_TARGET_SDK = 36
private const val HOST_MIN_SDK = 24

/** 工程属性页状态（含「跟随宿主」= null 语义）。 */
private data class ProjectInfoState(
    val values: Map<String, String>,
    val debugMode: Boolean,
    val permissions: List<String>,
    val minSdk: Int?,
    val targetSdk: Int?,
    val theme: String?,
)

private fun intField(
    content: String,
    name: String,
): Int? = LuaConfigParser.field(content, name)?.trim()?.toIntOrNull()

/** 整数字段写回；null = 跟随宿主 → 移除字段（运行时/打包回落默认）。 */
private fun writeSdkField(
    content: String,
    name: String,
    value: Int?,
): String =
    if (value == null) {
        LuaConfigParser.removeField(content, name)
    } else {
        LuaConfigParser.setField(content, name, value.toString())
    }

@Composable
private fun SectionHeader(labelRes: Int) {
    Text(
        stringResource(labelRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * SDK 版本下拉（保持宿主 / 自定义数字两段式）：null = 保持宿主（不写 init.lua 字段）。
 * 候选数字按降序（高版本优先）。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SdkDropdown(
    label: String,
    hostValue: Int,
    value: Int?,
    customCandidates: List<Int>,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value =
                if (value == null) {
                    stringResource(R.string.info_sdk_keep_host, hostValue)
                } else {
                    stringResource(R.string.info_sdk_custom, value.toString())
                },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.info_sdk_keep_host, hostValue)) },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            HorizontalDivider()
            customCandidates.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(candidate.toString()) },
                    onClick = {
                        expanded = false
                        onSelect(candidate)
                    },
                )
            }
        }
    }
}

/**
 * 工程主题下拉（P2）：跟随宿主（null）+ 常见主题别名 + 反射 `android.R.style`
 * 全部 `Theme_*`（[ThemeCatalog]，pro `projectinfo/main.lua:63-70` 同款）。
 * 主题名仅持久化，运行时由 LuaActivity `android.R.style.class.getField(theme)`
 * 反射消费（见 app/vendor/androlua LuaActivity.java:1508-1513）。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ThemeDropdown(
    current: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options by
        produceState<List<ThemeCatalog.ThemeOption>>(initialValue = emptyList()) {
            value = withContext(Dispatchers.Default) { ThemeCatalog.options() }
        }
    val legacy = if (current == "dark" || current == "light") listOf(current) else emptyList()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = themeLabel(current, options),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.info_theme)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.info_theme_keep_host)) },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            HorizontalDivider()
            legacy.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        onSelect(name)
                    },
                )
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(themeLabel(option.value, options)) },
                    onClick = {
                        expanded = false
                        onSelect(option.value)
                    },
                )
            }
        }
    }
}

@Composable
private fun themeLabel(
    value: String?,
    options: List<ThemeCatalog.ThemeOption>,
): String {
    if (value == null) return stringResource(R.string.info_theme_keep_host)
    val option = options.firstOrNull { it.value == value }
    return if (option?.labelRes != null) stringResource(option.labelRes) else value
}
