package com.lioisme.delua.editor

import androidx.compose.foundation.background
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
import com.lioisme.delua.editor.R
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.subscribeAlways

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
    var showReplace by remember { mutableStateOf(false) }
    var matchCount by remember { mutableStateOf(0) }
    var currentMatch by remember { mutableStateOf(0) }

    val editor = editorProvider()

    DisposableEffect(editor) {
        if (editor == null) {
            onDispose { }
        } else {
            val receipt = editor.subscribeAlways(PublishSearchResultEvent::class.java) { _ ->
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
            if (searcher.hasQuery()) {
                searcher.stopSearch()
            }
            matchCount = 0
            currentMatch = 0
            return
        }
        val searchType = if (isRegex) EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
                         else EditorSearcher.SearchOptions.TYPE_NORMAL
        val options = EditorSearcher.SearchOptions(searchType, !isCaseSensitive)
        try {
            searcher.search(query, options)
        } catch (_: Exception) {
            matchCount = 0
            currentMatch = 0
        }
    }

    LaunchedEffect(query, isCaseSensitive, isRegex) {
        performSearch()
    }

    Card(
        modifier = modifier.padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Search, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).padding(start = 4.dp))
                Spacer(Modifier.width(8.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Search,
                    ),
                )

                if (query.isNotEmpty()) {
                    Text(
                        "$currentMatch/$matchCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (matchCount > 0) MaterialTheme.colorScheme.onSurfaceVariant
                               else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )

                    IconButton(onClick = { editorProvider()?.getSearcher()?.gotoPrevious() },
                        modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp,
                            stringResource(R.string.cd_search_prev),
                            modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { editorProvider()?.getSearcher()?.gotoNext() },
                        modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown,
                            stringResource(R.string.cd_search_next),
                            modifier = Modifier.size(20.dp))
                    }
                }

                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close,
                        stringResource(R.string.cd_search_close),
                        modifier = Modifier.size(20.dp))
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
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
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
                    modifier = Modifier.height(32.dp),
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
                    modifier = Modifier.height(32.dp),
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
                    modifier = Modifier.height(28.dp),
                )
                FilterChip(
                    selected = isRegex,
                    onClick = { isRegex = !isRegex },
                    label = { Text(stringResource(R.string.regex), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp),
                )
            }
        }
    }
}
