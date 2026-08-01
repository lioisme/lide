package com.lioisme.delua.ui.screen

import android.Manifest
import android.app.AppOpsManager
import android.widget.Toast
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lioisme.delua.R
import com.lioisme.delua.editor.TipIconButton
import kotlinx.coroutines.launch

/**
 * 宿主权限管理页（与工程声明权限分离）。
 *
 * 仅展示宿主自身运行时权限：特殊权限（悬浮窗/系统设置/外部存储/电池优化/
 * 安装未知来源/使用情况访问）走各自系统检查与设置入口；常规权限按宿主
 * manifest 声明列表展示，未声明的不出现。工程声明权限（打包写入）不在此页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostPermissionScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var permissions: List<PermissionItem> by remember {
        mutableStateOf(buildHostPermissionList(context))
    }
    var isRefreshing by remember { mutableStateOf(false) }

    // 下拉刷新：重查全部宿主权限授予状态（覆盖去系统设置授予后返回的陈旧展示）。
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            permissions = buildHostPermissionList(context)
            isRefreshing = false
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _: Map<String, Boolean> ->
        permissions = buildHostPermissionList(context)
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _: androidx.activity.result.ActivityResult ->
        permissions = buildHostPermissionList(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.permission_management)) },
                navigationIcon = {
                    TipIconButton(tooltip = stringResource(R.string.cd_back), onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.perm_host_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }
            item {
                Text(
                    stringResource(R.string.perm_special_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
            }
            items(SPECIAL_PERMISSIONS) { special ->
                PermissionRow(
                    label = stringResource(special.labelRes),
                    granted = special.isGranted(context),
                    isSpecial = true,
                    onRequest = {
                        manageStorageLauncher.launch(special.settingsIntent(context))
                    }
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.perm_regular_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
            }
            items(permissions) { perm ->
                PermissionRow(
                    label = stringResource(perm.labelRes),
                    granted = perm.granted,
                    isSpecial = false,
                    onRequest = {
                        permLauncher.launch(PermissionCatalog.ALL.map { it.first }.toTypedArray())
                    }
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.perm_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp)
                )
                Spacer(Modifier.height(80.dp))
            }
        }
        }
    }
}

/**
 * 工程声明权限页（与宿主权限管理分离）。
 *
 * 勾选清单：池 = 权限目录（PermissionCatalog）+ 工程已声明但目录外的权限。
 * 勾选结果写回 init.lua 的 user_permission（与 pro projectinfo 行为一致），
 * 打包时按白名单（声明 ∩ 宿主 + 宿主自声明）写入产物 manifest。
 * 与宿主运行时权限无关，因此不提供请求按钮、不展示宿主授予状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectPermissionScreen(
    project: com.lioisme.delua.model.Project,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val projectManager = remember { com.lioisme.delua.project.ProjectManager(context) }
    var state by remember(project) { mutableStateOf<ProjectPermState?>(null) }
    val scope = rememberCoroutineScope()
    val strSaved = stringResource(R.string.perm_project_saved)
    val strSaveFailed = stringResource(R.string.perm_project_save_failed)

    LaunchedEffect(project) {
        state = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val declared = projectManager.loadUserPermissions(project)
                .map { com.lioisme.delua.androlua.PermissionResolver.normalize(it) }
                .toSet()
            val known = PermissionCatalog.ALL.map { it.first }.toSet()
            ProjectPermState(
                pool = PermissionCatalog.ALL + (declared - known).sorted().map { it to null },
                selected = declared.toMutableSet(),
                original = declared,
            )
        }
    }

    fun save() {
        val s = state ?: return
        scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val file = java.io.File(project.path, "init.lua")
                    if (!file.exists()) return@withContext false
                    val checked = s.pool
                        .filter { (perm, _) -> perm in s.selected }
                        .map { (perm, _) -> perm }
                    file.writeText(
                        com.lioisme.delua.androlua.LuaConfigParser.setStringArrayField(
                            file.readText(), "user_permission", checked
                        )
                    )
                    true
                } catch (_: Throwable) {
                    false
                }
            }
            if (ok) {
                state = state?.let { it.copy(original = it.selected.toSet()) }
                Toast.makeText(context, strSaved, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, strSaveFailed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.perm_project_title)) },
                navigationIcon = {
                    TipIconButton(tooltip = stringResource(R.string.cd_back), onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    val s = state
                    TextButton(
                        onClick = { save() },
                        enabled = s != null && s.selected != s.original,
                    ) {
                        Text(stringResource(R.string.perm_project_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.perm_project_section, project.name),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
                Text(
                    stringResource(R.string.perm_project_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
                )
            }
            val s = state
            if (s == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else {
                items(s.pool) { (perm, labelRes) ->
                    ProjectPermissionRow(
                        label = labelRes?.let { stringResource(it) } ?: perm,
                        checked = perm in s.selected,
                        onToggle = {
                            state = s.copy(
                                selected = if (perm in s.selected) s.selected - perm else s.selected + perm
                            )
                        },
                    )
                }
            }
            item {
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

/** 工程权限编辑状态：勾选池 + 当前勾选 + 已保存基线。 */
private data class ProjectPermState(
    val pool: List<Pair<String, Int?>>,
    val selected: Set<String>,
    val original: Set<String>,
)

@Composable
private fun ProjectPermissionRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.perm_package_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
            )
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    isSpecial: Boolean,
    onRequest: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (granted) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (granted) stringResource(R.string.perm_granted)
                    else stringResource(R.string.perm_denied),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (granted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                )
            }
            if (!granted) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onRequest) {
                    Text(stringResource(R.string.perm_request))
                }
            }
        }
    }
}

/** 特殊权限描述：独立授予检查 + 系统设置入口。 */
private data class SpecialPerm(
    val name: String,
    val labelRes: Int,
    val isGranted: (Context) -> Boolean,
    val settingsIntent: (Context) -> Intent,
)

private val SPECIAL_PERMISSIONS: List<SpecialPerm> = listOf(
    SpecialPerm(
        name = Manifest.permission.SYSTEM_ALERT_WINDOW,
        labelRes = R.string.perm_special_overlay,
        isGranted = { Settings.canDrawOverlays(it) },
        settingsIntent = {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${it.packageName}"))
        },
    ),
    SpecialPerm(
        name = Manifest.permission.WRITE_SETTINGS,
        labelRes = R.string.perm_special_write_settings,
        isGranted = { Settings.System.canWrite(it) },
        settingsIntent = {
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${it.packageName}"))
        },
    ),
    SpecialPerm(
        name = Manifest.permission.MANAGE_EXTERNAL_STORAGE,
        labelRes = R.string.perm_special_all_files,
        isGranted = { ctx ->
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                android.os.Environment.isExternalStorageManager()
        },
        settingsIntent = {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                .setData(Uri.parse("package:${it.packageName}"))
        },
    ),
    SpecialPerm(
        name = Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        labelRes = R.string.perm_special_battery,
        isGranted = { ctx ->
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return@SpecialPerm false
            pm.isIgnoringBatteryOptimizations(ctx.packageName)
        },
        settingsIntent = {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${it.packageName}"))
        },
    ),
    SpecialPerm(
        name = Manifest.permission.REQUEST_INSTALL_PACKAGES,
        labelRes = R.string.perm_special_install_unknown,
        isGranted = { ctx ->
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                ctx.packageManager.canRequestPackageInstalls()
        },
        settingsIntent = {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${it.packageName}"))
        },
    ),
    SpecialPerm(
        name = Manifest.permission.PACKAGE_USAGE_STATS,
        labelRes = R.string.perm_special_usage_stats,
        isGranted = { ctx ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return@SpecialPerm true
            val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return@SpecialPerm false
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                ctx.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        },
        settingsIntent = {
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        },
    ),
)

private data class PermissionItem(
    val name: String,
    val labelRes: Int,
    val granted: Boolean,
)

private fun checkGranted(ctx: Context, name: String): Boolean =
    ContextCompat.checkSelfPermission(ctx, name) == PackageManager.PERMISSION_GRANTED

private fun buildHostPermissionList(ctx: Context): List<PermissionItem> {
    val declared = hostDeclaredPermissions(ctx)
    return PermissionCatalog.ALL
        .filter { (name, _) -> name in declared }
        .map { (name, labelRes) ->
            PermissionItem(name, labelRes, checkGranted(ctx, name))
        }
}

/** 宿主 manifest 声明的权限集合（含特殊权限）。 */
private fun hostDeclaredPermissions(ctx: Context): Set<String> {
    return try {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
        info.requestedPermissions?.toSet() ?: emptySet()
    } catch (_: Throwable) {
        emptySet()
    }
}
