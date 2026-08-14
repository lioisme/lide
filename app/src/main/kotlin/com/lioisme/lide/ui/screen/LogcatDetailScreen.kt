package com.lioisme.lide.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import com.lioisme.lide.editor.TipIconButton
import com.lioisme.lide.log.LogEntry
import com.lioisme.lide.log.LogLevel
import com.lioisme.lide.log.LogcatStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 日志详情页：展示完整日志（多行归并全文，可选中复制）+ 元数据
 * （时间/级别/tag/pid/tid）+ 一键复制。
 *
 * 三态契约（对齐 API 详情契约）：条目被环形缓冲溢出清除（id 查找失败）
 * 或参数非法 → Failed 文案（logcat_detail_missing），禁止误用「无结果」或无限转圈。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogcatDetailScreen(
    entryId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val id = entryId.toLongOrNull()
    val strCopied = stringResource(R.string.log_copied)
    // 详情三态：Loading / Loaded / Failed。retryTick 递增触发 produceState 重启重试。
    var retryTick by remember { mutableIntStateOf(0) }
    val detailState =
        produceState<LogDetailUiState>(
            initialValue = LogDetailUiState.Loading,
            entryId,
            retryTick,
        ) {
            value =
                try {
                    withContext(Dispatchers.IO) {
                        id?.let { LogcatStore.entryById(it) }
                    }?.let { LogDetailUiState.Loaded(it) } ?: LogDetailUiState.Failed
                } catch (_: Throwable) {
                    LogDetailUiState.Failed
                }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text =
                            (detailState.value as? LogDetailUiState.Loaded)?.entry?.tag
                                ?: stringResource(R.string.logcat),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    TipIconButton(tooltip = stringResource(R.string.cd_back), onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val state = detailState.value) {
            LogDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.logcat_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            LogDetailUiState.Failed -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.logcat_detail_missing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { retryTick++ }) {
                            Text(stringResource(R.string.api_retry))
                        }
                    }
                }
            }

            is LogDetailUiState.Loaded -> {
                val entry = state.entry
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.height(8.dp))
                    MetadataRow(stringResource(R.string.log_meta_time), entry.timeText)
                    MetadataRow(
                        stringResource(R.string.log_meta_level),
                        "${entry.level.symbol} · ${levelLabel(entry)}",
                        levelColor(entry.level),
                    )
                    MetadataRow(stringResource(R.string.log_meta_tag), entry.tag)
                    MetadataRow(stringResource(R.string.log_meta_pid), entry.pid.toString())
                    MetadataRow(stringResource(R.string.log_meta_tid), entry.tid.toString())
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.log_message),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = {
                                copyToClipboard(context, entry.message)
                                scope.launch {
                                    snackbarHostState.showSnackbar(strCopied)
                                }
                            },
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.log_copy))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // 完整日志（多行归并全文），可选中复制。
                    SelectionContainer {
                        Text(
                            text = entry.message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** 详情三态（对齐 API 详情契约）：Loading 转圈 / Loaded 内容 / Failed 错误 + 重试。 */
private sealed interface LogDetailUiState {
    data object Loading : LogDetailUiState

    data class Loaded(
        val entry: LogEntry,
    ) : LogDetailUiState

    data object Failed : LogDetailUiState
}

/** 元数据行：label（onSurfaceVariant）+ 值（Monospace onSurface，级别行带语义色）。 */
@Composable
private fun MetadataRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = valueColor,
        )
    }
}

/** 级别名（可翻译资源）。 */
@Composable
private fun levelLabel(entry: LogEntry): String =
    stringResource(
        when (entry.level) {
            LogLevel.VERBOSE -> R.string.log_level_verbose
            LogLevel.DEBUG -> R.string.log_level_debug
            LogLevel.INFO -> R.string.log_level_info
            LogLevel.WARN -> R.string.log_level_warning
            LogLevel.ERROR -> R.string.log_level_error
        },
    )
