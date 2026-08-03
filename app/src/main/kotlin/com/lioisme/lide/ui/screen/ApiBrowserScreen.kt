package com.lioisme.lide.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import com.lioisme.lide.apibrowser.ApiCategory
import com.lioisme.lide.apibrowser.ApiIndex
import com.lioisme.lide.apibrowser.ApiIndex.ApiClassEntry
import com.lioisme.lide.apibrowser.ApiIndex.RowInfo
import com.lioisme.lide.apibrowser.ApiIndex.Snapshot
import com.lioisme.lide.apibrowser.ApiUsage
import com.lioisme.lide.editor.TipIconButton
import com.lioisme.lide.project.ProjectManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * API 浏览器：AppBar 内嵌官方 SearchBar（SearchBarState 驱动）+ 全屏搜索视图
 * （ExpandedFullScreenSearchBar，Dialog 新窗口，建议列表与正文列表完全解耦）。
 * 交互语义：点击搜索条 -> 全屏展开（键盘自动弹出）-> 输入实时过滤 ->
 * 选择建议收起并进入详情；正文列表只受分类筛选影响。
 * 索引在 IO 线程构建，快照缓存于进程级单例 [ApiIndex]。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ApiBrowserScreen(
    onBack: () -> Unit,
    onClassClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val strCopied = stringResource(R.string.api_copied)
    // 两阶段索引发布（性能审计 F1）：白屏不再等反射预算（曾达 3.5~5.6s 串行）——
    // buildBase 只做非反射阶段（静态解析 + dex 枚举，约 1s 级），发布后列表立即可交互；
    // completeBuild 在同一 IO 协程内后台补全（用途分类 + 行信息预热，预算上界 2s + 2.5s），
    // 完成后原子替换进程级快照并刷新本状态（徽章渐进完善，无闪烁）。
    var snapshot by remember { mutableStateOf<Snapshot?>(null) }
    LaunchedEffect(Unit) {
        val base = withContext(Dispatchers.IO) {
            val projectManager = ProjectManager(context)
            val roots = projectManager.listProjects().map { File(it.path) }
            ApiIndex.buildBase(context, roots)
        }
        snapshot = base
        withContext(Dispatchers.IO) { ApiIndex.completeBuild() }
        snapshot = ApiIndex.cached()
    }

    var selectedCategory by rememberSaveable { mutableStateOf<ApiCategory?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var filterMenuOpen by remember { mutableStateOf(false) }
    // 红线（冻结审计根因 3）：必须无参调用，禁止显式传动画 spec——
    // rememberSaveable 按引用比较 key，每次重组新建的 spring 实例会导致状态无限重建冻结。
    val searchBarState = rememberSearchBarState()
    // 搜索展开意图（独立 state）：InputField 的 expanded/onExpandedChange 只读写本状态，
    // 动画由下方 LaunchedEffect 幂等驱动。禁止把 InputField.expanded 绑定到
    // searchBarState.currentValue（动画派生值）——否则焦点回调会与动画互相激发形成死循环
    // （冻结审计根因：收起完成 → 焦点回落折叠输入条 → onExpandedChange(true) → 重新展开）。
    var searchActive by rememberSaveable { mutableStateOf(false) }
    // 待导航目标（点击建议后挂起）：SearchBarState 的动画基于 Animatable，
    // 并发 animateTo* 互斥（后启动者取消先前的动画协程）——若在点击回调里
    // scope.launch { animateToCollapsed(); onClassClick() }，协程会被下方
    // LaunchedEffect 的动画取消，onClassClick 永不执行（导航丢失）。
    // 因此导航统一由动画唯一驱动协程在收起动画完成后执行。
    var pendingNavigation by remember { mutableStateOf<String?>(null) }
    // 动画唯一驱动点：仅当意图翻转时执行一次；SearchBarState.animateTo* 内部无守卫，
    // 但本 effect 不会在焦点回调等高频路径重复启动。
    LaunchedEffect(searchActive) {
        if (searchActive) {
            searchBarState.animateToExpanded()
        } else {
            searchBarState.animateToCollapsed()
            // 收起动画完成（Dialog 已退出组合）后再导航，避免 Dialog 覆盖新目的地。
            pendingNavigation?.let { onClassClick(it) }
            pendingNavigation = null
        }
    }
    // 防御：rememberSaveable 可能恢复半程动画进度（progress ∈ (0,1)），
    // 此时 currentValue 恒为 Expanded，会导致进入界面即弹搜索窗；强制归零兜底。
    LaunchedEffect(Unit) {
        if (!searchActive) searchBarState.snapTo(0f)
    }

    // 正文列表：仅受分类筛选影响，与搜索关键词解耦。同步计算，命中 build 期分类预索引
    // （O(选中分类条目数)），单次亚毫秒级（性能审计 R3）。
    val bodyList = remember(snapshot, selectedCategory) {
        snapshot?.let { filterSnapshot(it, selectedCategory, "") } ?: emptyList()
    }
    // 搜索建议列表：分类 + 关键词实时过滤，异步计算——每次 key 变化 produceState 重启并
    // 自动取消旧查询（协程取消即防抖，无 debounce 依赖），UI 线程零过滤负担；
    // block 完成前保留上一轮结果（不闪空）。纯 CPU 过滤走 Default，不占用 IO 反射池。
    val searchResults by produceState(emptyList<ApiClassEntry>(), snapshot, selectedCategory, query) {
        value = withContext(Dispatchers.Default) {
            snapshot?.let { filterSnapshot(it, selectedCategory, query) } ?: emptyList()
        }
    }
    val categoryCounts = snapshot?.categoryCounts ?: emptyMap()

    Scaffold(
        topBar = {
            // 使用 CenterAlignedTopAppBar：标题相对整条 AppBar 水平居中。
            // 普通 TopAppBar 标题默认 CenterStart，且无导航图标时起始有 12dp inset、
            // 末端仅有 4dp padding，满宽搜索条左右边距不对称，视觉整体偏右。
            CenterAlignedTopAppBar(
                title = {
                    SearchBar(
                        state = searchBarState,
                        inputField = {
                            // 折叠态不挂可编辑输入条：SearchBarDefaults.InputField 内部
                            // onFocusChanged 会因长按文本选择/获得焦点直接置 onExpandedChange(true)，
                            // 导致长按搜索条也进入 SearchView。改用自绘折叠条，仅 detectTapGestures
                            // 单击展开，长按无副作用（展开后的输入与焦点由 ExpandedFullScreenSearchBar
                            // 内部 focusRequester 自动接管，键盘照常弹出）。
                            ApiCollapsedSearchInput(
                                query = query,
                                onBack = onBack,
                                onClear = { query = "" },
                                onTapToExpand = { searchActive = true },
                                filterSlot = {
                                    FilterMenuButton(
                                        expanded = filterMenuOpen,
                                        onExpandedChange = { filterMenuOpen = it },
                                        selectedCategory = selectedCategory,
                                        onCategorySelect = { selectedCategory = it },
                                        categoryCounts = categoryCounts,
                                        totalCount = snapshot?.classes?.size,
                                    )
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(0.92f),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when {
                    snapshot == null -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.api_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    bodyList.isEmpty() -> {
                        Text(
                            stringResource(R.string.api_no_result),
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            items(bodyList, key = { it.name }) { entry ->
                                ClassRow(
                                    entry = entry,
                                    onClick = { onClassClick(entry.name) },
                                    onLongClick = {
                                        copyToClipboard(context, entry.name)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("${entry.name} · $strCopied")
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

    // 全屏搜索视图：Dialog 新窗口承载（官方组件，返回键/缩放动画/键盘管理内置），
    // 建议列表在本 content 槽内组合，与正文 LazyColumn 分属不同组合树，完全解耦。
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
                    filterSlot = {
                        FilterMenuButton(
                            expanded = filterMenuOpen,
                            onExpandedChange = { filterMenuOpen = it },
                            selectedCategory = selectedCategory,
                            onCategorySelect = { selectedCategory = it },
                            categoryCounts = categoryCounts,
                            totalCount = snapshot?.classes?.size,
                        )
                    },
                )
            },
        ) {
            // Dialog 内返回键：收起 + 显式清焦点。
            // 若不清理焦点，Dialog 销毁后主窗口焦点会自动回落折叠输入条，
            // 触发其 onFocusChanged(true) → onExpandedChange(true) → 收起后立即重新展开（死循环）。
            BackHandler(enabled = true) {
                searchActive = false
                focusManager.clearFocus()
            }
            when {
                snapshot == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.api_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                searchResults.isEmpty() -> {
                    Text(
                        stringResource(R.string.api_no_result),
                        modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        items(searchResults, key = { it.name }) { entry ->
                            ClassRow(
                                entry = entry,
                                onClick = {
                                    // 先收起搜索态再导航，避免 Dialog 覆盖新目的地；
                                    // 实际导航由动画唯一驱动协程在收起完成后执行（见 pendingNavigation）。
                                    searchActive = false
                                    focusManager.clearFocus()
                                    pendingNavigation = entry.name
                                },
                                onLongClick = {
                                    copyToClipboard(context, entry.name)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("${entry.name} · $strCopied")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 搜索输入条（折叠态 SearchBar 与全屏 ExpandedFullScreenSearchBar 共用同一实现）。
 * 展开/收起意图经 [active]/[onActiveChange] 与外部独立 state 通信（官方语义）：
 * 本组件不直接触碰 SearchBarState 动画，动画由宿主 LaunchedEffect(active) 幂等驱动，
 * 避免焦点回调与动画互相激发（死循环红线）。
 * 返回按钮置于 leadingIcon、清除 + 分类筛选（[filterSlot]，含 DropdownMenu）置于
 * trailingIcon，与 SearchBar 共占顶栏一行。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiSearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    filterSlot: @Composable () -> Unit,
) {
    SearchBarDefaults.InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = { onSearch() },
        expanded = active,
        onExpandedChange = onActiveChange,
        placeholder = { Text(stringResource(R.string.api_search_hint)) },
        leadingIcon = {
            TipIconButton(tooltip = stringResource(R.string.cd_back), onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    TipIconButton(tooltip = stringResource(R.string.api_clear_search), onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.api_clear_search),
                        )
                    }
                }
                filterSlot()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 折叠态搜索条（AppBar 内）：仅单击进入 SearchView。
 *
 * 不挂载 SearchBarDefaults.InputField——其 BasicTextField 内部
 * `onFocusChanged { if (isFocused) onExpandedChange(true) }` 由“获得焦点”驱动展开，
 * 而长按（文本选择/光标）同样会请求焦点，导致长按也进入 SearchView。
 * 本组件用 [detectTapGestures] 区分单击（展开）与长按（无副作用），
 * 首尾图标按钮（返回 / 清除 / 分类筛选）各自保留单击语义与长按 tooltip。
 * 展开后的输入、焦点与键盘由 ExpandedFullScreenSearchBar 的 focusRequester 自动接管。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ApiCollapsedSearchInput(
    query: String,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onTapToExpand: () -> Unit,
    filterSlot: @Composable () -> Unit,
) {
    val placeholder = stringResource(R.string.api_search_hint)
    val container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(container)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TipIconButton(tooltip = stringResource(R.string.cd_back), onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTapToExpand() },
                        onLongPress = {},
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = if (query.isEmpty()) placeholder else query,
                style = MaterialTheme.typography.bodyLarge,
                color = if (query.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (query.isNotEmpty()) {
            TipIconButton(tooltip = stringResource(R.string.api_clear_search), onClick = onClear) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.api_clear_search),
                )
            }
        }
        filterSlot()
    }
}

/**
 * 分类筛选按钮 + DropdownMenu（SearchBar trailingIcon 槽内使用，锚点随输入条）。
 */
@Composable
private fun FilterMenuButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    selectedCategory: ApiCategory?,
    onCategorySelect: (ApiCategory?) -> Unit,
    categoryCounts: Map<ApiCategory, Int>,
    totalCount: Int?,
) {
    Box {
        TipIconButton(tooltip = stringResource(R.string.api_filter), onClick = { onExpandedChange(true) }) {
            Icon(
                Icons.Default.FilterList,
                contentDescription = stringResource(R.string.api_filter),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            FilterMenuItem(
                label = stringResource(R.string.api_category_all),
                count = totalCount,
                selected = selectedCategory == null,
                onClick = {
                    onCategorySelect(null)
                    onExpandedChange(false)
                },
            )
            ApiCategory.entries.forEach { category ->
                FilterMenuItem(
                    label = categoryLabel(category),
                    count = categoryCounts[category],
                    selected = selectedCategory == category,
                    onClick = {
                        onCategorySelect(category)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterMenuItem(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            RadioButton(selected = selected, onClick = null)
        },
        trailingIcon = {
            if (count != null) {
                Text(
                    text = "($count)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onClick = onClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClassRow(
    entry: ApiClassEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    // 行级反射信息（接口判定 + 可加载性）经 rowInfoAsync 获取：命中 build 期预热缓存时 O(1) 查表；
    // 未命中时单飞计算（NonCancellable，不随行生命周期取消，fling 不重启全 dex 扫描）。
    // 失败降级按不可用处理。
    val info by produceState<RowInfo?>(initialValue = null, entry.name) {
        value = try {
            ApiIndex.rowInfoAsync(entry.name)
        } catch (_: Throwable) {
            RowInfo(kind = null, loadable = false)
        }
    }
    val unavailable = info?.loadable == false
    // 固定行高（两行单行文本 + 固定间距 + divider），避免 LazyColumn 测量抖动。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = simpleNameOf(entry.name),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                    // 不可用 API：Compose 原生中划线（用户决策，替代原 error 圆点非文本指示；
                    // 禁止往文本内容里插入 '-' 字符等「伪中划线」技巧）。
                    textDecoration = if (unavailable) TextDecoration.LineThrough else null,
                )
                Spacer(Modifier.width(8.dp))
                TypeBadge(entry = entry, info = info)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.name,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (unavailable) TextDecoration.LineThrough else null,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

/**
 * 类型徽标：优先「接口」（反射判定），其次「用途分类」（build 期预计算，UI 零反射）；
 * 未命中用途的条目不显示徽标（原「类」兜底徽标信息量为零）。
 * 徽标颜色统一为单一色系（secondaryContainer），仅以文案区分类型（用户决策：颜色一致）。
 */
@Composable
private fun TypeBadge(entry: ApiClassEntry, info: RowInfo?) {
    val isInterface = info?.kind?.isInterface == true
    val label = when {
        isInterface -> stringResource(R.string.api_kind_interface)
        entry.usage != null -> usageLabel(entry.usage)
        else -> return
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** 类型徽章文案（用途分类 → 可翻译资源）。 */
@Composable
private fun usageLabel(usage: ApiUsage): String = stringResource(
    when (usage) {
        ApiUsage.ADAPTER -> R.string.api_usage_adapter
        ApiUsage.CONTAINER -> R.string.api_usage_container
        ApiUsage.INPUT -> R.string.api_usage_input
        ApiUsage.UTILITY -> R.string.api_usage_utility
        ApiUsage.BUTTON -> R.string.api_usage_button
        ApiUsage.STATE -> R.string.api_usage_state
        ApiUsage.VIEW -> R.string.api_usage_view
        ApiUsage.POPUP -> R.string.api_usage_popup
        ApiUsage.DROPDOWN -> R.string.api_usage_dropdown
    }
)

/** 简单名派生：内部类优先取最后一个 `$` 之后（MenuPresenter$Callback → Callback），否则取最后一个 `.` 之后。 */
private fun simpleNameOf(fqn: String): String {
    val dollar = fqn.lastIndexOf('$')
    val cut = if (dollar >= 0) dollar else fqn.lastIndexOf('.')
    return if (cut >= 0) fqn.substring(cut + 1) else fqn
}

private fun filterSnapshot(snapshot: Snapshot, category: ApiCategory?, query: String): List<ApiClassEntry> {
    val q = query.trim().lowercase()
    val indices = snapshot.categories[category]
    return if (indices == null) {
        // 全量（无分类筛选）：查询为空直接返回原表（零拷贝）。
        if (q.isEmpty()) {
            snapshot.classes
        } else {
            snapshot.classes.indices
                .asSequence()
                .filter { snapshot.nameLower[it].contains(q) }
                .map { snapshot.classes[it] }
                .toList()
        }
    } else {
        // 分类筛选：O(选中分类条目数)，命中 build 期预索引（性能审计 R3）。
        if (q.isEmpty()) {
            indices.map { snapshot.classes[it] }
        } else {
            indices.asSequence()
                .filter { snapshot.nameLower[it].contains(q) }
                .map { snapshot.classes[it] }
                .toList()
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("lide-api", text))
    } catch (_: Throwable) {
    }
}

@Composable
fun categoryLabel(category: ApiCategory): String = stringResource(
    when (category) {
        ApiCategory.COMMON_WIDGETS -> R.string.api_category_common
        ApiCategory.MATERIAL -> R.string.api_category_material
        ApiCategory.ANDROIDX -> R.string.api_category_androidx
        ApiCategory.R_RESOURCES -> R.string.api_category_r
        ApiCategory.ANDROLUA -> R.string.api_category_androlua
        ApiCategory.FRAMEWORK -> R.string.api_category_framework
        ApiCategory.JAVA -> R.string.api_category_java
        ApiCategory.PROJECT -> R.string.api_category_project
        ApiCategory.OTHER -> R.string.api_category_other
    }
)
