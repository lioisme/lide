package com.lioisme.delua.ui.screen

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lioisme.delua.R
import com.lioisme.delua.androlua.PermissionResolver

/**
 * 权限页。审计修正（宿主权限 vs 工程权限混淆）：
 * - 区块明确区分「宿主权限」与「工程声明权限」（`user_permission`）；
 * - 每个权限独立的授予状态判断：特殊权限走各自系统检查
 *   （悬浮窗/系统设置/外部存储/电池优化/安装未知来源），不再统一套用
 *   `Environment.isExternalStorageManager()`；
 * - 工程声明权限统一归一化（裸名 → `android.permission.` 全名）后再检查/请求；
 *   宿主 manifest 未声明的权限视为「打包时写入」（工程权限的目标是打包产物
 *   manifest，见 WP-014），不尝试向宿主运行时请求。
 */
private data class PermissionItem(
    val name: String,
    val label: String,
    val granted: Boolean,
    val isSpecial: Boolean = false,
    /** 宿主 manifest 未声明的工程权限：仅在打包时写入产物 manifest。 */
    val packageOnly: Boolean = false,
)

/** 特殊权限描述：独立授予检查 + 系统设置入口。 */
private data class SpecialPerm(
    val name: String,
    val label: String,
    val isGranted: (Context) -> Boolean,
    val settingsIntent: (Context) -> Intent,
)

private val SPECIAL_PERMISSIONS: List<SpecialPerm> = listOf(
    SpecialPerm(
        name = Manifest.permission.SYSTEM_ALERT_WINDOW,
        label = "悬浮窗",
        isGranted = { Settings.canDrawOverlays(it) },
        settingsIntent = {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${it.packageName}"))
        },
    ),
    SpecialPerm(
        name = Manifest.permission.WRITE_SETTINGS,
        label = "修改系统设置",
        isGranted = { Settings.System.canWrite(it) },
        settingsIntent = {
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${it.packageName}"))
        },
    ),
    SpecialPerm(
        name = Manifest.permission.MANAGE_EXTERNAL_STORAGE,
        label = "管理所有文件",
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
        label = "忽略电池优化",
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
        label = "安装未知来源应用",
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
        label = "使用情况访问权限",
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

private val ALL_PERMISSIONS = PermissionCatalog.ALL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(
    onBack: () -> Unit,
    project: com.lioisme.delua.model.Project? = null,
) {
    val context = LocalContext.current
    var permissions: List<PermissionItem> by remember {
        mutableStateOf(buildHostPermissionList(context))
    }
    // 宿主 manifest 实际声明的权限集合（请求目标必须在此集合内，否则系统静默拒绝）。
    val hostDeclared = remember { hostDeclaredPermissions(context) }

    // 工程声明权限：后台 IO 读取 init.lua（大文件防护：小文件读一次，无阻塞风险）。
    val projectManager = remember { com.lioisme.delua.project.ProjectManager(context) }
    var declaredPermissions by remember(project) {
        mutableStateOf<List<String>?>(if (project == null) emptyList() else null)
    }
    LaunchedEffect(project) {
        val p = project ?: return@LaunchedEffect
        declaredPermissions = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            projectManager.loadUserPermissions(p).map { PermissionResolver.normalize(it) }
        }
    }
    val declared = declaredPermissions ?: emptyList()
    val declaredLoaded = declaredPermissions != null

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _: Map<String, Boolean> ->
        permissions = buildHostPermissionList(context)
    }

    // 单权限请求：权限页条目各自独立请求（区别于运行前的一次性批量请求）。
    val singlePermLauncher = rememberLauncherForActivityResult(
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
                    IconButton(onClick = onBack) {
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (project != null) {
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
                if (declared.isEmpty() && declaredLoaded) {
                    item {
                        Text(
                            stringResource(R.string.perm_project_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                } else if (!declaredLoaded) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                } else {
                    declared.forEach { name ->
                        val special = SPECIAL_PERMISSIONS.firstOrNull { it.name == name }
                        val packageOnly = name !in hostDeclared
                        item(key = "declared_$name") {
                            PermissionRow(
                                label = special?.label ?: labelOf(name),
                                granted = special != null || checkGranted(context, name),
                                isSpecial = special != null,
                                packageOnly = packageOnly,
                                onRequest = {
                                    if (special != null) {
                                        manageStorageLauncher.launch(special.settingsIntent(context))
                                    } else if (!packageOnly) {
                                        singlePermLauncher.launch(arrayOf(name))
                                    }
                                }
                            )
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(16.dp))
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.perm_host_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
                Text(
                    stringResource(R.string.perm_host_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
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
                    label = special.label,
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
                    label = perm.label,
                    granted = perm.granted,
                    isSpecial = false,
                    onRequest = {
                        permLauncher.launch(ALL_PERMISSIONS.map { it.first }.toTypedArray())
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

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    isSpecial: Boolean,
    onRequest: () -> Unit,
    packageOnly: Boolean = false,
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
                    when {
                        packageOnly -> stringResource(R.string.perm_package_only)
                        granted -> stringResource(R.string.perm_granted)
                        else -> stringResource(R.string.perm_denied)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (granted || packageOnly) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                )
            }
            if (!granted && !packageOnly) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onRequest) {
                    Text(stringResource(R.string.perm_request))
                }
            }
        }
    }
}

private fun checkGranted(ctx: Context, name: String): Boolean =
    ContextCompat.checkSelfPermission(ctx, name) == PackageManager.PERMISSION_GRANTED

private fun buildHostPermissionList(ctx: Context): List<PermissionItem> {
    val declared = hostDeclaredPermissions(ctx)
    return ALL_PERMISSIONS
        .filter { (name, _) -> name in declared }
        .map { (name, label) ->
            PermissionItem(name, label, checkGranted(ctx, name))
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

/** 权限名 → 可读标签；未知权限原样展示。 */
private fun labelOf(name: String): String {
    ALL_PERMISSIONS.forEach { (perm, label) -> if (perm == name) return label }
    SPECIAL_PERMISSIONS.forEach { it.let { sp -> if (sp.name == name) return sp.label } }
    return name
}
