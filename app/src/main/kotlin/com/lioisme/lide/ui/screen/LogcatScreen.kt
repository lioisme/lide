package com.lioisme.lide.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import com.lioisme.lide.log.LogEntry
import com.lioisme.lide.log.LogFilter
import com.lioisme.lide.log.LogcatStatus
import com.lioisme.lide.log.LogcatStore
import com.lioisme.lide.log.filterEntries
import com.lioisme.lide.log.formatted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 单一日志 TAG（Q13 收口：历史字面量统一为常量）。 */
private const val TAG = "LogcatScreen"

/**
 * 日志猫列表页：全局日志查看器（LIDE 进程日志，宿主 + 引擎 + Lua print）。
 *
 * 与 API 浏览器同构（P3-2 组件拆分 + 搜索状态机红线）：
 * - 顶栏：折叠搜索条（[ApiCollapsedSearchInput] 复用）+ 类型筛选菜单
 *   （菜单底部含「清空日志」项）；
 * - 全屏 SearchView：查询词实时过滤（与正文同一过滤管线：类型 ∩ 查询词）；
 * - 搜索状态机红线全守（rememberSearchBarState 无参 / searchActive 独立 state /
 *   LaunchedEffect 唯一动画驱动 / 收起路径三件套）；
 * - 过滤在 Dispatchers.Default（produceState 协程取消即防抖）；
 * - 自动滚动：用户停留在底部时新日志跟随，上滑即暂停。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LogcatScreen(
    onBack: () -> Unit,
    onEntryClick: (Long) -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val strCopied = stringResource(R.string.log_copied)
    // 进程级缓冲快照（采集器线程原子发布，UI 主线程消费）。
    val entries by LogcatStore.entries.collectAsState()
    val status by LogcatStore.status.collectAsState()

    // 红线（冻结审计根因 3）：必须无参调用，禁止显式传动画 spec。
    val searchBarState = rememberSearchBarState()
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var selectedFilter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var filterMenuOpen by remember { mutableStateOf(false) }
    // 待导航目标（点击行后挂起）：导航统一由动画唯一驱动协程在收起动画完成后执行。
    var pendingNavigation by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(searchActive) {
        if (searchActive) {
            searchBarState.animateToExpanded()
        } else {
            searchBarState.animateToCollapsed()
            pendingNavigation?.let { onEntryClick(it) }
            pendingNavigation = null
        }
    }
    // 防御：rememberSaveable 可能恢复半程动画进度，强制归零兜底。
    LaunchedEffect(Unit) {
        if (!searchActive) searchBarState.snapTo(0f)
    }

    // 过滤（类型 ∩ 查询词）：每次快照/筛选/查询变化重启并取消旧查询（协程取消即防抖）；
    // 纯 CPU 过滤走 Default，UI 线程零负担。
    val filtered by produceState(emptyList<LogEntry>(), entries, selectedFilter, query) {
        value =
            withContext(Dispatchers.Default) {
                filterEntries(entries, selectedFilter, query)
            }
    }

    val listState = rememberLazyListState()
    // 自动滚动：用户停留在列表底部（或初始空列表）时新日志跟随；上滑即暂停。
    LaunchedEffect(filtered.size) {
        val lastIndex = filtered.lastIndex
        if (lastIndex < 0 || listState.isScrollInProgress) return@LaunchedEffect
        val lastVisible =
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index ?: -1
        if (lastVisible >= lastIndex - 1) {
            listState.scrollToItem(lastIndex)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    SearchBar(
                        state = searchBarState,
                        inputField = {
                            ApiCollapsedSearchInput(
                                query = query,
                                onBack = onBack,
                                onClear = { query = "" },
                                onTapToExpand = { searchActive = true },
                                placeholder = stringResource(R.string.logcat_search_hint),
                                filterSlot = {
                                    LogFilterMenuButton(
                                        expanded = filterMenuOpen,
                                        onExpandedChange = { filterMenuOpen = it },
                                        selectedFilter = selectedFilter,
                                        onFilterSelect = { selectedFilter = it },
                                        onClearClick = { LogcatStore.clear() },
                                    )
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(0.92f),
                    )
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LogListContent(
                entries = filtered,
                emptyMessage = emptyMessage(status, entries.isEmpty()),
                onEntryClick = onEntryClick,
                onEntryLongClick = { entry ->
                    copyToClipboard(context, entry.formatted())
                    scope.launch {
                        snackbarHostState.showSnackbar(strCopied)
                    }
                },
                listState = listState,
            )
        }
    }

    // 全屏搜索视图：Dialog 新窗口承载（官方组件，返回键/缩放动画/键盘管理内置）。
    val searchListState = rememberLazyListState()
    if (searchBarState.currentValue == SearchBarValue.Expanded) {
        ExpandedFullScreenSearchBar(
            state = searchBarState,
            inputField = {
                ApiSearchInputField(
                    query = query,
                    onQueryChange = { query = it },
                    active = searchActive,
                    onActiveChange = { searchActive = it },
                    onSearch = {
                        searchActive = false
                        focusManager.clearFocus()
                    },
                    onBack = {
                        searchActive = false
                        focusManager.clearFocus()
                    },
                    placeholder = stringResource(R.string.logcat_search_hint),
                    filterSlot = {
                        LogFilterMenuButton(
                            expanded = filterMenuOpen,
                            onExpandedChange = { filterMenuOpen = it },
                            selectedFilter = selectedFilter,
                            onFilterSelect = { selectedFilter = it },
                            onClearClick = { LogcatStore.clear() },
                        )
                    },
                )
            },
        ) {
            // Dialog 内返回键：收起 + 显式清焦点（防焦点回落折叠条重新展开死循环）。
            BackHandler(enabled = true) {
                searchActive = false
                focusManager.clearFocus()
            }
            LogListContent(
                entries = filtered,
                emptyMessage = emptyMessage(status, entries.isEmpty()),
                onEntryClick = { id ->
                    // 先收起搜索态再导航，实际导航由动画唯一驱动协程执行（见 pendingNavigation）。
                    searchActive = false
                    focusManager.clearFocus()
                    pendingNavigation = id
                },
                onEntryLongClick = { entry ->
                    copyToClipboard(context, entry.formatted())
                    scope.launch {
                        snackbarHostState.showSnackbar(strCopied)
                    }
                },
                listState = searchListState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

/** 空态文案：无日志（采集不可用 / 尚未产生）与筛选无结果区分。 */
@Composable
private fun emptyMessage(
    status: LogcatStatus,
    storeEmpty: Boolean,
): String =
    when {
        status == LogcatStatus.Unavailable && storeEmpty -> {
            stringResource(R.string.logcat_unavailable)
        }

        storeEmpty -> {
            stringResource(R.string.logcat_empty)
        }

        else -> {
            stringResource(R.string.logcat_no_result)
        }
    }

/**
 * 日志列表主体（正文与全屏搜索共用）：空态 / LazyColumn + 自动滚动状态挂接。
 */
@Composable
private fun LogListContent(
    entries: List<LogEntry>,
    emptyMessage: String,
    onEntryClick: (Long) -> Unit,
    onEntryLongClick: (LogEntry) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    Box(modifier = modifier) {
        if (entries.isEmpty()) {
            // 规范空态：图标 + 文案居中（M3 空态惯例），替代裸文本。
            Column(
                modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // 列表全宽：无相对外部间距（行内 padding(horizontal = 16, vertical = 10)
                // 承担边距，对齐 LIDE 主界面列表写法）。
                contentPadding = PaddingValues(0.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    LogRow(
                        entry = entry,
                        onClick = { onEntryClick(entry.id) },
                        onLongClick = { onEntryLongClick(entry) },
                    )
                }
            }
        }
    }
}
