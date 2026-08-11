package com.lioisme.lide.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.lioisme.lide.R
import com.lioisme.lide.install.BackendStatus
import com.lioisme.lide.install.InstallerBackend
import com.lioisme.lide.install.InstallerCoordinator
import com.lioisme.lide.install.InstallerServices
import com.lioisme.lide.ui.theme.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 通用安装方式选择对话框（2026-08-10 T16）：
 * 系统「用 LIDE 打开 APK」→ 展示安装方式选择（默认 = 设置页持久化）→
 * 所选后端 READY 时静默安装，否则回落系统安装器。
 * 与构建对话框（BuildApkDialog）共用同一安装后端状态口径（InstallBackendUi）。
 */
@Composable
fun InstallMethodDialog(
    apkFile: File,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installing by remember { mutableStateOf(false) }
    // 初始值 = 设置页持久化默认（D-4 语义：本次安装选择不回写持久化）。
    var method by remember { mutableStateOf(SettingsManager.buildOpts.value.installBackend) }

    val backendStatuses =
        listOf(
            Triple(InstallerBackend.DEFAULT, stringResource(R.string.install_method_default), null),
            Triple(InstallerBackend.SHIZUKU, stringResource(R.string.install_method_shizuku), InstallerServices.shizuku.status.collectAsState().value),
            Triple(InstallerBackend.DHIZUKU, stringResource(R.string.install_method_dhizuku), InstallerServices.dhizuku.status.collectAsState().value),
            Triple(InstallerBackend.ROOT, stringResource(R.string.install_method_root), InstallerServices.root.status.collectAsState().value),
        )

    fun installNow() {
        installing = true
        scope.launch {
            val silent =
                method != InstallerBackend.DEFAULT &&
                    withContext(Dispatchers.IO) { InstallerCoordinator.installSilently(apkFile, method) }
            if (silent) {
                Toast.makeText(context, R.string.install_success, Toast.LENGTH_SHORT).show()
            } else {
                installApkViaSystem(context, apkFile)
            }
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!installing) onDismiss() },
        title = { Text(stringResource(R.string.install_apk_title)) },
        text = {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    apkFile.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                backendStatuses.forEach { (backend, label, status) ->
                    // 仅后端已就绪（READY）可选；未获对应权限/未运行/不可用一律置灰。
                    val available = backend == InstallerBackend.DEFAULT || status == BackendStatus.READY
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = available && !installing) { method = backend }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = method == backend,
                            onCheckedChange = { method = backend },
                            enabled = available && !installing,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                if (available) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                },
                        )
                        if (backend != InstallerBackend.DEFAULT && status != null && status != BackendStatus.READY) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                installBackendStatusLabel(status),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { installNow() }, enabled = !installing) {
                Text(stringResource(R.string.install_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !installing) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
