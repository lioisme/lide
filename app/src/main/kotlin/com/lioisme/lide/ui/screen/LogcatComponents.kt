package com.lioisme.lide.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import com.lioisme.lide.editor.TipIconButton
import com.lioisme.lide.log.LogEntry
import com.lioisme.lide.log.LogFilter
import com.lioisme.lide.log.LogLevel
import com.lioisme.lide.ui.theme.LocalIsDarkTheme

/*
 * 日志猫界面组件集（与 LogcatScreen 状态机解耦，API 浏览器 P3-2 同款拆分）：
 * 类型筛选菜单 / 日志行与级别指示色 / 筛选函数调用。
 * 线程契约同 ApiBrowserScreen：过滤走 Dispatchers.Default（UI 仅经 produceState 消费）。
 */

/**
 * 类型筛选按钮 + DropdownMenu（SearchBar trailingIcon 槽内使用，9 项单选，
 * 对齐 pro logcat.lua 菜单 All/Lua/Test/Tcc/Error/Warning/Info/Debug/Verbose）；
 * 菜单底部横向分隔线后为「清空日志」项（Delete 图标 + error 色，危险操作惯例，
 * 点击直接清除不二次确认）。
 */
@Composable
internal fun LogFilterMenuButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    selectedFilter: LogFilter,
    onFilterSelect: (LogFilter) -> Unit,
    onClearClick: () -> Unit,
) {
    Box {
        TipIconButton(tooltip = stringResource(R.string.logcat_filter), onClick = { onExpandedChange(true) }) {
            Icon(
                Icons.Default.FilterList,
                contentDescription = stringResource(R.string.logcat_filter),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            LogFilter.entries.forEach { filter ->
                DropdownMenuItem(
                    text = { Text(filterLabel(filter), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { RadioButton(selected = selectedFilter == filter, onClick = null) },
                    onClick = {
                        onFilterSelect(filter)
                        onExpandedChange(false)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.logcat_clear), color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.logcat_clear),
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    onClearClick()
                    onExpandedChange(false)
                },
            )
        }
    }
}

/** 类型筛选文案（可翻译资源）。 */
@Composable
internal fun filterLabel(filter: LogFilter): String =
    stringResource(
        when (filter) {
            LogFilter.ALL -> R.string.logcat_filter_all
            LogFilter.LUA -> R.string.logcat_filter_lua
            LogFilter.TEST -> R.string.logcat_filter_test
            LogFilter.TCC -> R.string.logcat_filter_tcc
            LogFilter.ERROR -> R.string.logcat_filter_error
            LogFilter.WARNING -> R.string.logcat_filter_warning
            LogFilter.INFO -> R.string.logcat_filter_info
            LogFilter.DEBUG -> R.string.logcat_filter_debug
            LogFilter.VERBOSE -> R.string.logcat_filter_verbose
        },
    )

/**
 * 日志级别指示色（经典 logcat 语义色，双模 Material 300/800 配对：
 * V 灰 / D 蓝 / I 绿 / W 琥珀 / E 红；纯指示色，不设 WCAG 文本对比度门禁）。
 */
@Composable
internal fun levelColor(level: LogLevel): Color {
    val isDark = LocalIsDarkTheme.current
    return if (isDark) DARK_COLORS[level]!! else LIGHT_COLORS[level]!!
}

private val LIGHT_COLORS =
    mapOf(
        LogLevel.VERBOSE to Color(0xFF757575),
        LogLevel.DEBUG to Color(0xFF1565C0),
        LogLevel.INFO to Color(0xFF2E7D32),
        LogLevel.WARN to Color(0xFFE65100),
        LogLevel.ERROR to Color(0xFFC62828),
    )

private val DARK_COLORS =
    mapOf(
        LogLevel.VERBOSE to Color(0xFF9E9E9E),
        LogLevel.DEBUG to Color(0xFF64B5F6),
        LogLevel.INFO to Color(0xFF81C784),
        LogLevel.WARN to Color(0xFFFFB74D),
        LogLevel.ERROR to Color(0xFFE57373),
    )

/**
 * 日志行（LOGCAT_LIST_REDESIGN 2026-08-14：两行式应用列表，对齐 LIDE 主界面范式）：
 *
 * 布局：[级别色点 垂直居中] + [行1：tag 主文字 · 行尾时间 tnum 右缘对齐]
 *        [行2：消息副行 单行省略] + 行末细分隔线。
 *
 * - 行1 tag 为主文字（labelMedium Monospace Medium onSurface），超宽省略
 *   （widthIn(max = 160dp)）；时间经 weight 占位推至行尾右缘（labelSmall tnum
 *   onSurfaceVariant）——右缘天然列位恒定，不再占用行首固定列；
 * - 行2 消息为副行（bodySmall Monospace onSurfaceVariant 单行省略），与 tag
 *   垂直对齐——废除 108dp 缩进，消除与 tag 徽章的错位阶梯；
 * - 级别色点保留语义（V/D/I/W/E 经典 logcat 色）且垂直居中；
 * - 行末细分隔线（outlineVariant 0.4 alpha，ClassRow/MemberRow 同款惯例）；
 * - 列表时间显示 `MM-DD HH:MM:SS`（毫秒降级详情页，详情页展示完整 timeText）；
 * - 单击进详情，长按复制完整消息（对齐 ClassRow 交互契约）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LogRow(
    entry: LogEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(levelColor(entry.level)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // tag 主文字：weight(fill = false) 按内容宽占位并把时间推至行尾右缘。
                    Text(
                        text = entry.tag,
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .weight(1f, fill = false)
                                .widthIn(max = TAG_MAX_WIDTH),
                    )
                    Text(
                        text = timeForList(entry.timeText),
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontFeatureSettings = "tnum",
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = entry.message,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 行末细分隔线：日志密度大时扫描清晰（ClassRow/MemberRow 同款惯例）。
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

/** tag 最大宽度（超长省略，防止挤压行尾时间列）。 */
private val TAG_MAX_WIDTH = 160.dp

/** 列表时间文本长度（`MM-DD HH:MM:SS` = 14 字符；毫秒降级详情页——详情页展示完整 `timeText`）。 */
private const val TIME_TEXT_LENGTH = 14

/** 列表时间文本：取 `MM-DD HH:MM:SS`（毫秒截断，详情页仍展示完整时间）。 */
internal fun timeForList(timeText: String): String = timeText.take(TIME_TEXT_LENGTH)
