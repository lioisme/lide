package com.delua.ui.screen

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
import com.delua.R

private data class PermissionItem(
    val name: String,
    val label: String,
    val granted: Boolean,
    val isSpecial: Boolean = false
)

private val ALL_PERMISSIONS: List<Pair<String, String>> = listOf(
    Manifest.permission.READ_EXTERNAL_STORAGE to "读取外部存储",
    Manifest.permission.WRITE_EXTERNAL_STORAGE to "写入外部存储",
    Manifest.permission.INTERNET to "网络访问",
    Manifest.permission.ACCESS_NETWORK_STATE to "网络状态",
    Manifest.permission.ACCESS_WIFI_STATE to "WiFi状态",
    Manifest.permission.CAMERA to "相机",
    Manifest.permission.RECORD_AUDIO to "录音",
    Manifest.permission.READ_CONTACTS to "读取联系人",
    Manifest.permission.WRITE_CONTACTS to "写入联系人",
    Manifest.permission.READ_CALENDAR to "读取日历",
    Manifest.permission.WRITE_CALENDAR to "写入日历",
    Manifest.permission.ACCESS_FINE_LOCATION to "精确定位",
    Manifest.permission.ACCESS_COARSE_LOCATION to "粗略定位",
    Manifest.permission.READ_PHONE_STATE to "读取设备信息",
    Manifest.permission.CALL_PHONE to "拨打电话",
    Manifest.permission.SEND_SMS to "发送短信",
    Manifest.permission.READ_SMS to "读取短信",
    Manifest.permission.RECEIVE_SMS to "接收短信",
    Manifest.permission.BLUETOOTH to "蓝牙",
    Manifest.permission.BLUETOOTH_ADMIN to "蓝牙管理",
    Manifest.permission.NFC to "NFC",
    Manifest.permission.POST_NOTIFICATIONS to "通知",
    Manifest.permission.FOREGROUND_SERVICE to "前台服务",
    Manifest.permission.VIBRATE to "振动",
    Manifest.permission.WAKE_LOCK to "唤醒锁",
)

private val SPECIAL_PERMISSIONS: List<Pair<String, String>> = listOf(
    "android.permission.MANAGE_EXTERNAL_STORAGE" to "管理所有文件"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var permissions: List<PermissionItem> by remember {
        mutableStateOf(buildPermissionList(context))
    }

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