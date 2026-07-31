package com.lioisme.delua.ui.screen

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Environment
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

private data class PermissionItem(
    val name: String,
    val label: String,
    val granted: Boolean,
    val isSpecial: Boolean = false
)

private val ALL_PERMISSIONS = PermissionCatalog.ALL

private val SPECIAL_PERMISSIONS = PermissionCatalog.SPECIAL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(
    onBack: () -> Unit,
    project: com.lioisme.delua.model.Project? = null,
) {
    val context = LocalContext.current
    var permissions: List<PermissionItem> by remember {
        mutableStateOf(buildPermissionList(context))
    }

    // 工程声明权限：后台 IO 读取 init.lua（大文件防护：小文件读一次，无阻塞风险）。
    val projectManager = remember { com.lioisme.delua.project.ProjectManager(context) }
    var declaredPermissions by remember(project) {
        mutableStateOf<List<String>?>(if (project == null) emptyList() else null)
    }
    LaunchedEffect(project) {
        val p = project ?: return@LaunchedEffect
        declaredPermissions = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            projectManager.loadUserPermissions(p)
        }
    }
    val declared = declaredPermissions ?: emptyList()
    val declaredLoaded = declaredPermissions != null

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _: Map<String, Boolean> ->
        permissions = buildPermissionList(context)
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _: androidx.activity.result.ActivityResult ->
        permissions = buildPermissionList(context)
    }

    // 单权限请求：权限页条目各自独立请求（区别于运行前的一次性批量请求）。
    val singlePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _: Map<String, Boolean> ->
        permissions = buildPermissionList(context)
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
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
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
                        val label = labelOf(name)
                        val isSpecial = SPECIAL_PERMISSIONS.any { (perm, _) -> perm == name }
                        val granted = if (isSpecial) {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                                Environment.isExternalStorageManager()
                        } else {
                            ContextCompat.checkSelfPermission(context, name) ==
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                        }
                        item(key = "declared_$name") {
                            PermissionRow(
                                label = label,
                                granted = granted,
                                isSpecial = isSpecial,
                                onRequest = {
                                    if (isSpecial) {
                                        manageStorageLauncher.launch(
                                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                data = android.net.Uri.parse("package:${context.packageName}")
                                            }
                                        )
                                    } else {
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
                    stringResource(R.string.perm_special_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
            }
            items(SPECIAL_PERMISSIONS) { (permName, label) ->
                val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    Environment.isExternalStorageManager() else true
                PermissionRow(
                    label = label,
                    granted = granted,
                    isSpecial = true,
                    onRequest = {
                        manageStorageLauncher.launch(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                        )
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
    onRequest: () -> Unit
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

private fun buildPermissionList(ctx: android.content.Context): List<PermissionItem> {
    return ALL_PERMISSIONS.map { (name, label) ->
        val granted = ContextCompat.checkSelfPermission(ctx, name) == android.content.pm.PackageManager.PERMISSION_GRANTED
        PermissionItem(name, label, granted)
    }
}

/** 权限名 → 可读标签；未知权限原样展示。 */
private fun labelOf(name: String): String {
    ALL_PERMISSIONS.forEach { (perm, label) -> if (perm == name) return label }
    SPECIAL_PERMISSIONS.forEach { (perm, label) -> if (perm == name) return label }
    return name
}