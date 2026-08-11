package com.lioisme.lide.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.lioisme.lide.R
import com.lioisme.lide.install.BackendStatus
import com.lioisme.lide.install.InstallerBackend
import java.io.File

/**
 * 静默安装后端 UI 共享件（SILENT_INSTALL，2026-08-10）：
 * 设置页「安装方式」组与权限管理页「通用权限」区共用——
 * 禁止两处各自实现状态文案与行样式（单一事实源）。
 * 2026-08-12 治理红线：权限页行标题经 perm_* 传参（用途与权限分离）。
 */

/** 后端状态 → 文案资源。 */
@Composable
fun installBackendStatusLabel(status: BackendStatus): String =
    stringResource(
        when (status) {
            BackendStatus.READY -> R.string.install_backend_status_ready
            BackendStatus.PERMISSION_NEEDED -> R.string.install_backend_status_permission_needed
            BackendStatus.NOT_RUNNING -> R.string.install_backend_status_not_running
            BackendStatus.UNAVAILABLE -> R.string.install_backend_status_unavailable
        },
    )

/**
 * 权限页「通用权限」行（2026-08-10 统一，2026-08-12 改名）：与权限页其余行
 * （PermissionRow）同款卡片 + 图标 + 标题 + 状态副标题布局；
 * PERMISSION_NEEDED 且可授权时提供「授权」按钮。
 * 不展示描述与指引长文本（界面禁止大量描述性文本）。
 */
@Composable
fun InstallBackendStatusRow(
    label: String,
    status: BackendStatus?,
    onGrant: (() -> Unit)?,
) {
    val ready = status == BackendStatus.READY
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (ready) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint =
                    if (ready) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (status != null) {
                    Text(
                        installBackendStatusLabel(status),
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (ready) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                    )
                }
            }
            if (status == BackendStatus.PERMISSION_NEEDED && onGrant != null) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onGrant) {
                    Text(stringResource(R.string.install_backend_grant))
                }
            }
        }
    }
}

/**
 * 设置页「安装方式」单选行（2026-08-10 统一）：与设置页其余行同款布局
 * （图标 + 标题 + 状态副标题），尾部 RadioButton 单选。
 * 仅 [enabled]（系统安装器或后端 READY）可选；PERMISSION_NEEDED 提供「授权」动作。
 */
@Composable
fun InstallBackendRadioRow(
    label: String,
    status: BackendStatus?,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onGrant: (() -> Unit)?,
) {
    val ready = status == BackendStatus.READY
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onSelect)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (ready) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint =
                if (ready) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
            )
            if (status != null) {
                Text(
                    installBackendStatusLabel(status),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (ready) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                )
            }
        }
        if (status == BackendStatus.PERMISSION_NEEDED && onGrant != null) {
            FilledTonalButton(onClick = onGrant) {
                Text(stringResource(R.string.install_backend_grant))
            }
            Spacer(Modifier.width(4.dp))
        }
        RadioButton(
            selected = selected,
            onClick = onSelect,
            enabled = enabled,
        )
    }
}

/**
 * 系统安装器入口：ACTION_INSTALL_PACKAGE 直连（InstallStart）。
 * 不用 ACTION_VIEW——宿主自身的 .alp/.lua 导入过滤会匹配任何 content:// 文件，
 * 会弹出"打开方式"选择器而非直接安装。
 * 未授予"安装未知来源应用"时系统会先弹授权页，授予后直达安装确认。
 * （T16 从 BuildApkDialog 提升为共享件：打包自动安装与通用 APK 安装对话框共用。）
 */
internal fun installApkViaSystem(
    context: android.content.Context,
    apkFile: File,
) {
    try {
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName, apkFile)
        val intent =
            Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(intent)
    } catch (e: Exception) {
        // T17：安装启动失败消息资源兜底（异常细节仅日志，用户可见文案本地化）。
        android.util.Log.w("InstallBackendUi", "installApkViaSystem failed", e)
        Toast.makeText(context, context.getString(R.string.install_failed), Toast.LENGTH_LONG).show()
    }
}