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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import com.lioisme.lide.editor.TipIconButton
import com.lioisme.lide.log.LogEntry
import com.lioisme.lide.log.LogFilter
import com.lioisme.lide.log.symbolChar
import com.lioisme.lide.ui.theme.LocalIsDarkTheme
import io.github.oshai.kotlinlogging.Level

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
internal fun levelColor(level: Level): Color {
    val isDark = LocalIsDarkTheme.current
    return if (isDark) DARK_COLORS[level]!! else LIGHT_COLORS[level]!!
}

private val LIGHT_COLORS =
    mapOf(
        Level.TRACE to Color(0xFF757575),
        Level.DEBUG to Color(0xFF1565C0),
        Level.INFO to Color(0xFF2E7D32),
        Level.WARN to Color(0xFFE65100),
        Level.ERROR to Color(0xFFC62828),
    )

private val DARK_COLORS =
    mapOf(
        Level.TRACE to Color(0xFF9E9E9E),
        Level.DEBUG to Color(0xFF64B5F6),
        Level.INFO to Color(0xFF81C784),
        Level.WARN to Color(0xFFFFB74D),
        Level.ERROR to Color(0xFFE57373),
    )

/**
 * 日志行（LOGCAT_LIST_REVISION8 2026-08-15 重构定稿：徽章固定列锚点，LogFox 同款）。
 *
 * 用户拍板：REVISION7 前缀式验收失败（消息列随 tag 长度逐行漂移成锯齿、前缀与
 * 消息混排一行）；重构为**固定列锚点**结构——每行 = `[级别徽章固定宽列] + 固定
 * 12dp 间距 + [右侧内容列]`，右侧消息/元数据左缘全列表恒定 x 对齐（行业列式规范：
 * perfetto Android Logs / MAUI.Sherpa / Android Studio Logcat 同构；移动端落法 =
 * LogFox）：
 *
 * - 左列（固定 LEVEL_BADGE_WIDTH 28dp）：级别徽章——圆角 6dp、级别色 15% alpha
 *   淡底 + 级别色字母（labelMedium 等宽居中，top 对齐消息首行）；级别一眼扫描
 *   且列锚点恒定；
 * - 主体 Column（weight 1f，左缘 = 徽章右缘 + 12dp 恒定）：
 *   行1 消息（bodySmall Monospace onSurface `maxLines = 3`，**独占整行区零混排**，
 *   续行自然对齐消息列，超长省略 + 详情页全文）；行2 元数据小字一行
 *   （labelSmall onSurfaceVariant）：`MM-DD HH:MM:SS（tnum）· PID-TID（等宽）·
 *   tag（等宽 widthIn(max = 120dp) 省略）`（毫秒降级详情页，详情页展示完整
 *   timeText；PID-TID 为 AS Standard View 同款 `12345-12345` 形态）；
 * - 行高 minHeight 56dp（消息 1 行时行高一致，滚动整齐）+ padding(h16, v10)；
 * - 行末细分隔线（outlineVariant 0.4 alpha，ClassRow/MemberRow 同款惯例）；
 * - 单击进详情；长按复制标准整行文本（`entry.formatted()`，
 *   `MM-DD HH:MM:SS.mmm PID TID I/tag: message`，Android Studio 同款）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LogRow(
    entry: LogEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .heightIn(min = LOG_ROW_MIN_HEIGHT)
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        // 左列：级别徽章（固定宽淡底圆角，级别一眼扫描；右侧内容左缘恒定锚点）。
        LevelBadge(level = entry.level)
        Spacer(Modifier.width(BADGE_TO_CONTENT_GAP))
        // 主体：消息独占整行区（零混排）+ 元数据小字一行，左缘恒定。
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = MAX_MESSAGE_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            // 元数据：时间 · PID-TID · tag，弱色一行（与消息分离）。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeForList(entry.timeText),
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontFeatureSettings = "tnum",
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = " · ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = "${entry.pid}-${entry.tid}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = " · ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = entry.tag,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = TAG_MAX_WIDTH),
                )
            }
        }
    }
    // 行末细分隔线：日志密度大时扫描清晰（ClassRow/MemberRow 同款惯例）。
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

/** 级别徽章：固定宽淡底圆角（级别色 15% alpha 底 + 级别色字母居中），列锚点恒定。 */
@Composable
private fun LevelBadge(level: Level) {
    Box(
        modifier =
            Modifier
                .width(LEVEL_BADGE_WIDTH)
                .background(levelColor(level).copy(alpha = LEVEL_BADGE_ALPHA), RoundedCornerShape(LEVEL_BADGE_CORNER))
                .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = level.symbolChar().toString(),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = levelColor(level),
            maxLines = 1,
        )
    }
}

/** 级别徽章固定列宽（右侧内容左缘全列表恒定锚点）。 */
private val LEVEL_BADGE_WIDTH = 28.dp

/** 徽章与内容间距（固定，与徽章宽共同构成列锚点）。 */
private val BADGE_TO_CONTENT_GAP = 12.dp

/** 徽章圆角。 */
private val LEVEL_BADGE_CORNER = 6.dp

/** 徽章淡底透明度（级别色 alpha，淡色底 + 级别色字母）。 */
private const val LEVEL_BADGE_ALPHA = 0.15f

/** 行最小高度（消息 1 行时行高一致，列表滚动整齐）。 */
private val LOG_ROW_MIN_HEIGHT = 56.dp

/** tag 最大宽度（元数据行中克制省略；120dp 覆盖常见全名）。 */
private val TAG_MAX_WIDTH = 120.dp

/** 消息最大行数（列表多行可读，超长省略 + 详情页全文）。 */
private const val MAX_MESSAGE_LINES = 3

/** 列表时间文本长度（`MM-DD HH:MM:SS` = 14 字符；毫秒降级详情页——详情页展示完整 `timeText`）。 */
private const val TIME_TEXT_LENGTH = 14

/** 列表时间文本：取 `MM-DD HH:MM:SS`（毫秒截断，详情页仍展示完整时间）。 */
internal fun timeForList(timeText: String): String = timeText.take(TIME_TEXT_LENGTH)
