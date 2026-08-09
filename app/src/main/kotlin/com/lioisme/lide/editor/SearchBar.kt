package com.lioisme.lide.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.subscribeAlways
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SEARCH_DEBOUNCE_MS = 150L

/**
 * 自包含搜索面板：查询 / 替换 / 匹配计数等全部状态内聚在本组件内，
 * 打字与搜索结果只重组本子树（宿主整屏零重组）。
 *
 * 搜索执行经 [editorProvider] 获取当前编辑器实例；结果计数由 sora
 * `PublishSearchResultEvent` 驱动（与 sora 渲染器高亮同源，计数必然一致）。
 */
@Composable
fun SearchBar(
    editorProvider: () -> CodeEditor?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var isCaseSensitive by remember { mutableStateOf(false) }
    var isRegex by remember { mutableStateOf(false) }
    var isWholeWord by remember { mutableStateOf(false) }
    var showReplace by remember { mutableStateOf(false) }
    var matchCount by remember { mutableStateOf(0) }
    var currentMatch by remember { mutableStateOf(0) }

    val editor = editorProvider()

    // 搜索执行作用域：快照/匹配派发到 Default 线程（Q2，消除大文件主线程 O(n) 拷贝）。
    // 结果回发经 sora `postInLifecycle` 回到主线程，计数更新仍发生在主线程。
    val searchScope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(editor) {
        if (editor == null) {
            onDispose { }
        } else {
            val receipt =
                editor.subscribeAlways(PublishSearchResultEvent::class.java) { _ ->
                    try {
                        val searcher = editor.getSearcher()
                        if (searcher.hasQuery()) {
                            val index = searcher.getCurrentMatchedPositionIndex()
                            matchCount = searcher.getMatchedPositionCount()
                            currentMatch = if (index >= 0) index + 1 else 0
                        } else {
                            matchCount = 0
                            currentMatch = 0
                        }
                    } catch (t: Throwable) {
                        matchCount = 0
                        currentMatch = 0
                    }
                }
            onDispose { receipt.unsubscribe() }
        }
    }

    fun performSearch() {
        val searcher = editorProvider()?.getSearcher() ?: return
        if (query.isBlank()) {
            searchJob?.cancel()
            searchJob = null
            // stopSearch 在主线程调用：sora 会立即 dispatchEvent（订阅回调在主线程执行，
            // 更新 Compose 计数状态），移出主线程会破坏快照状态写线程约束。
            if (searcher.hasQuery()) {
                searcher.stopSearch()
            }
            matchCount = 0
            currentMatch = 0
            return
        }
        val searchType =
            when {
                isRegex -> EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
                isWholeWord -> EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
                else -> EditorSearcher.SearchOptions.TYPE_NORMAL
            }
        val options = EditorSearcher.SearchOptions(searchType, !isCaseSensitive)
        // 搜索提交（含 content.toStringBuilder 全文本快照 + 匹配线程启动）移出主线程；
        // 新查询取消旧协程（sora 侧 executeMatch 亦会打断旧匹配线程，双保险）。
        searchJob?.cancel()
        searchJob =
            searchScope.launch(Dispatchers.Default) {
                try {
                    searcher.search(query, options)
                } catch (_: Exception) {
                    // 非法正则/搜索失败：计数归零需回到主线程写 Compose 状态。
                    withContext(Dispatchers.Main.immediate) {
                        matchCount = 0
                        currentMatch = 0
                    }
                }
            }
    }

    // 防抖：连续键入只触发一次搜索。sora `EditorSearcher.search` 的全文快照
    // （content.toStringBuilder）已随 Q2 移入 Default 线程（大文件每键 O(n) 拷贝
    // 不再阻塞主线程）；防抖由 LaunchedEffect 重启语义天然提供取消。
    LaunchedEffect(query, isCaseSensitive, isRegex, isWholeWord) {
        delay(SEARCH_DEBOUNCE_MS)
        performSearch()
    }

    Card(
        modifier = modifier.padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.Search,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).padding(start = 4.dp),
                )
                Spacer(Modifier.width(8.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                        ),
                    keyboardOptions =
                        androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Search,
                        ),
                )

                if (query.isNotEmpty()) {
                    Text(
                        // Q14：匹配计数整句进资源（%1$d/%2$d）。
                        stringResource(R.string.cd_search_match_count, currentMatch, matchCount),
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (matchCount > 0) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )

                    TipIconButton(
                        tooltip = stringResource(R.string.cd_search_prev),
                        onClick = { editorProvider()?.getSearcher()?.gotoPrevious() },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            stringResource(R.string.cd_search_prev),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    TipIconButton(
                        tooltip = stringResource(R.string.cd_search_next),
                        onClick = { editorProvider()?.getSearcher()?.gotoNext() },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            stringResource(R.string.cd_search_next),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                TipIconButton(
                    tooltip = stringResource(R.string.cd_search_close),
                    onClick = onClose,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        stringResource(R.string.cd_search_close),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 4.dp),
            ) {
                OutlinedTextField(
                    value = replaceQuery,
                    onValueChange = { replaceQuery = it },
                    placeholder = { Text(stringResource(R.string.replace_hint)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                        ),
                    keyboardOptions =
                        androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done,
                        ),
                )

                Spacer(Modifier.width(4.dp))
                FilledTonalButton(
                    onClick = {
                        editorProvider()?.getSearcher()?.replaceCurrentMatch(replaceQuery)
                    },
                    enabled = query.isNotEmpty() && matchCount > 0,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier =
                        Modifier
                            .height(32.dp)
                            .pressAndHoldHapticFeedback(),
                ) {
                    Text(stringResource(R.string.replace), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(
                    onClick = {
                        editorProvider()?.getSearcher()?.replaceAll(replaceQuery)
                    },
                    enabled = query.isNotEmpty() && matchCount > 0,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier =
                        Modifier
                            .height(32.dp)
                            .pressAndHoldHapticFeedback(),
                ) {
                    Text(stringResource(R.string.replace_all), style = MaterialTheme.typography.labelSmall)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = isCaseSensitive,
                    onClick = { isCaseSensitive = !isCaseSensitive },
                    label = { Text(stringResource(R.string.case_sensitive), style = MaterialTheme.typography.labelSmall) },
                    modifier =
                        Modifier
                            .height(28.dp)
                            .pressAndHoldHapticFeedback(),
                )
                FilterChip(
                    selected = isRegex,
                    onClick = { isRegex = !isRegex },
                    label = { Text(stringResource(R.string.regex), style = MaterialTheme.typography.labelSmall) },
                    modifier =
                        Modifier
                            .height(28.dp)
                            .pressAndHoldHapticFeedback(),
                )
                FilterChip(
                    selected = isWholeWord,
                    onClick = { isWholeWord = !isWholeWord },
                    label = { Text(stringResource(R.string.whole_word), style = MaterialTheme.typography.labelSmall) },
                    modifier =
                        Modifier
                            .height(28.dp)
                            .pressAndHoldHapticFeedback(),
                )
            }
        }
    }
}
