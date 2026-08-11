package com.lioisme.lide.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExpandedFullScreenSearchBar
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
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import com.lioisme.lide.apibrowser.ApiCategory
import com.lioisme.lide.apibrowser.ApiIndex
import com.lioisme.lide.apibrowser.ApiIndex.ApiClassEntry
import com.lioisme.lide.apibrowser.ApiIndex.Snapshot
import com.lioisme.lide.engine.RuntimePaths
import com.lioisme.lide.project.ProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 单一日志 TAG（Q13 收口：历史字面量 Log.w("ApiBrowserScreen", ...) 统一为常量）。 */
private const val TAG = "ApiBrowserScreen"

/**
 * API 浏览器：AppBar 内嵌官方 SearchBar（SearchBarState 驱动）+ 全屏搜索视图
 * （ExpandedFullScreenSearchBar，Dialog 新窗口，建议列表与正文列表完全解耦）。
 * 交互语义：点击搜索条 -> 全屏展开（键盘自动弹出）-> 输入实时过滤 ->
 * 选择建议收起并进入详情；正文列表只受分类筛选影响。
 * 索引在 IO 线程构建，快照缓存于进程级单例 [ApiIndex]。
 *
 * 组件拆分（P3-2）：搜索输入条 / 筛选菜单 / 列表行与徽标 / 过滤与工具函数
 * 见 ApiBrowserComponents.kt；本文件只保留搜索状态机（动画唯一驱动 + pendingNavigation
 * 消费机制，见搜索红线文档）与页面组装。
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
    // 两阶段索引发布：buildBase 只做非反射阶段（静态解析 + dex 枚举，约 1s 级），发布后列表
    // 立即可交互；completeBuild 在同一 IO 协程内后台补全反射阶段（用途分类 + 分类细化，
    // 类解析走合并缓存无预算），完成后原子替换进程级快照并刷新本状态（徽章渐进完善，无闪烁）。
    var snapshot by remember { mutableStateOf<Snapshot?>(null) }
    LaunchedEffect(Unit) {
        // 降级约定（AGENTS.md）：两阶段构建全路径 try/catch(Throwable)（P2#13）。
        // buildBase 失败 → 回退进程级缓存；completeBuild 失败 → 保留基础快照；
        // 两阶全失败 → 空快照（列表空态），不崩溃、不无限转圈。
        var base: Snapshot? = null
        try {
            base =
                withContext(Dispatchers.IO) {
                    val projectManager = ProjectManager()
                    val roots =
                        projectManager
                            .listProjects(RuntimePaths.luaExtDir())
                            .map { File(it.path) }
                    ApiIndex.buildBase(context, roots)
                }
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "buildBase failed", t)
        }
        if (base != null) {
            snapshot = base
            try {
                withContext(Dispatchers.IO) { ApiIndex.completeBuild() }
                snapshot = ApiIndex.cached() ?: base
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "completeBuild failed", t)
            }
        } else {
            snapshot = ApiIndex.cached() ?: ApiIndex.Snapshot(
                classes = emptyList(),
                nativeModules = emptyList(),
                nameLower = emptyList(),
                categories = emptyMap(),
                categoryCounts = emptyMap(),
                dexNames = emptySet(),
                entryNames = emptySet(),
            )
        }
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
    val bodyList =
        remember(snapshot, selectedCategory) {
            snapshot?.let { filterSnapshot(it, selectedCategory, "") } ?: emptyList()
        }
    // 搜索建议列表：分类 + 关键词实时过滤，异步计算——每次 key 变化 produceState 重启并
    // 自动取消旧查询（协程取消即防抖，无 debounce 依赖），UI 线程零过滤负担；
    // block 完成前保留上一轮结果（不闪空）。纯 CPU 过滤走 Default，不占用 IO 反射池。
    val searchResults by produceState(emptyList<ApiClassEntry>(), snapshot, selectedCategory, query) {
        value =
            withContext(Dispatchers.Default) {
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
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.api_copied_format, entry.name, strCopied),
                                        )
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
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.api_copied_format, entry.name, strCopied),
                                        )
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
