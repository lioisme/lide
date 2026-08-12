package com.lioisme.lide.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import com.lioisme.lide.editor.SearchMatchInfo

/**
 * 查找/替换面板（P5.1，2026-08-11）：COMPOSE + MD3，宿主 Material3 浮层色板
 * （对齐浮层契约：补全窗/tooltip = surfaceContainerHighest 同族）。
 *
 * 状态全部由调用方持有（可组合最小化）；[onSearchResultPublished] 由 sora
 * PublishSearchResultEvent 驱动刷新 [matchInfo]。只读下替换按钮置灰
 * （sora 侧另有 isEditable 门禁，双保险）。
 */
@Composable
internal fun EditorSearchPanel(
    findQuery: String,
    onFindQueryChange: (String) -> Unit,
    replaceQuery: String,
    onReplaceQueryChange: (String) -> Unit,
    showReplace: Boolean,
    onToggleReplace: () -> Unit,
    isRegex: Boolean,
    onIsRegexChange: (Boolean) -> Unit,
    isCaseSensitive: Boolean,
    onIsCaseSensitiveChange: (Boolean) -> Unit,
    isWholeWord: Boolean,
    onIsWholeWordChange: (Boolean) -> Unit,
    matchInfo: SearchMatchInfo,
    replaceEnabled: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 面板开启即拦截返回键：先收面板，不进导航回退（对齐 IME 搜索收起语义）。
    BackHandler(onBack = onClose)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = findQuery,
                    onValueChange = onFindQueryChange,
                    placeholder = { Text(stringResource(R.string.search_find)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                // 计数：n/m（sora 结果未就绪/无查询时空白）。
                Text(
                    text =
                        when {
                            findQuery.isEmpty() -> {
                                ""
                            }

                            matchInfo.count > 0 -> {
                                stringResource(R.string.search_match_position, matchInfo.index + 1, matchInfo.count)
                            }

                            else -> {
                                stringResource(R.string.search_no_results)
                            }
                        },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_search_close),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilterChip(
                    selected = isRegex,
                    onClick = { onIsRegexChange(!isRegex) },
                    label = { Text(stringResource(R.string.search_regex)) },
                )
                FilterChip(
                    selected = isCaseSensitive,
                    onClick = { onIsCaseSensitiveChange(!isCaseSensitive) },
                    label = { Text(stringResource(R.string.search_case_sensitive)) },
                )
                FilterChip(
                    selected = isWholeWord,
                    onClick = { onIsWholeWordChange(!isWholeWord) },
                    label = { Text(stringResource(R.string.search_whole_word)) },
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onToggleReplace) {
                    Icon(
                        if (showReplace) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                    )
                }
                IconButton(onClick = onPrevious) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.cd_search_previous),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.cd_search_next),
                    )
                }
            }
            if (showReplace) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = replaceQuery,
                        onValueChange = onReplaceQueryChange,
                        placeholder = { Text(stringResource(R.string.search_replace)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onReplace,
                        enabled = replaceEnabled,
                    ) {
                        Text(stringResource(R.string.search_replace))
                    }
                    TextButton(
                        onClick = onReplaceAll,
                        enabled = replaceEnabled,
                    ) {
                        Text(stringResource(R.string.search_replace_all))
                    }
                }
            }
        }
    }
}
