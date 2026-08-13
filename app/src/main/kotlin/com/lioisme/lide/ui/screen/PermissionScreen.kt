package com.lioisme.lide.ui.screen

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lioisme.lide.R
import com.lioisme.lide.install.InstallerServices
import com.lioisme.lide.ui.theme.LideTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 宿主权限管理页（与工程声明权限分离）。
 *
 * 仅展示宿主自身运行时权限：特殊权限（悬浮窗/系统设置/外部存储/电池优化/
 * 安装未知来源/使用情况访问）走各自系统检查与设置入口；常规权限按宿主
 * manifest 声明列表展示，未声明的不出现。工程声明权限（打包写入）不在此页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostPermissionScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // 列表构建（getPackageInfo + N×checkSelfPermission + 特殊权限 AppOps binder 调用）
    // 移出组合（P2#12）：以 refreshKey 驱动 IO 线程重建，UI 只读结果。
    // refreshKey 由下拉刷新与授权返回（两种 launcher 回调）自增。
    var refreshKey by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    var uiState by remember { mutableStateOf<HostPermUiState?>(null) }

    LaunchedEffect(refreshKey) {
        val built = withContext(Dispatchers.IO) { buildHostPermUiState(context) }
        uiState = built
        isRefreshing = false
    }

    val permLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { _: Map<String, Boolean> ->
            refreshKey++
        }

    val manageStorageLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { _: androidx.activity.result.ActivityResult ->
            refreshKey++
        }

    // 特殊权限系统页启动（带 ActivityNotFoundException 兜底：OEM 可能未实现
    // 对应 ACTION，此时回退到应用详情页，避免点击无响应/崩溃）。
    // 仍走 launcher：返回本页时经 refreshKey 回写授予状态（ACC-2）。
    fun launchSettingsIntent(intent: Intent) {
        try {
            manageStorageLauncher.launch(intent)
        } catch (_: Throwable) {
            try {
                manageStorageLauncher.launch(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            } catch (_: Throwable) {
                Toast
                    .makeText(
                        context,
                        context.getString(R.string.perm_settings_unavailable),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            LideTopBar(
                title = stringResource(R.string.permission_management),
                onBack = onBack,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                refreshKey++
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Spacer(Modifier.height(8.dp))
                }
                item {
                    Text(
                        stringResource(R.string.perm_special_section),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                }
                items(uiState?.specials ?: emptyList()) { (special, granted) ->
                    PermissionRow(
                        label = stringResource(special.labelRes),
                        granted = granted,
                        isSpecial = true,
                        onRequest = {
                            launchSettingsIntent(special.settingsIntent(context))
                        },
                    )
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.perm_general_section),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                }
                // 通用权限（2026-08-12 治理红线）：Root / Shizuku / Dhizuku 是宿主可
                // 申请的**通用系统级权限通道**（宿主用其实现静默安装等能力），行标题
                // 一律 `perm_*`，禁止以用途（安装方式）命名；安装功能（通道选择）在
                // 设置页「安装方式」与安装/构建对话框。仅标题 + 短状态词 + 「授权」
                // 按钮，无描述/指引长文本（T15 同款）。
                item {
                    InstallBackendStatusRow(
                        label = stringResource(R.string.perm_shizuku),
                        status =
                            InstallerServices.shizuku.status
                                .collectAsState()
                                .value,
                        onGrant = { InstallerServices.shizuku.requestPermission() },
                    )
                }
                item {
                    InstallBackendStatusRow(
                        label = stringResource(R.string.perm_dhizuku),
                        status =
                            InstallerServices.dhizuku.status
                                .collectAsState()
                                .value,
                        onGrant = { InstallerServices.dhizuku.requestPermission() },
                    )
                }
                item {
                    InstallBackendStatusRow(
                        label = stringResource(R.string.perm_root),
                        status =
                            InstallerServices.root.status
                                .collectAsState()
                                .value,
                        onGrant = { InstallerServices.root.requestPermission() },
                    )
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.perm_regular_section),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                }
                items(uiState?.regular ?: emptyList()) { perm ->
                    PermissionRow(
                        label = stringResource(perm.labelRes),
                        granted = perm.granted,
                        isSpecial = false,
                        onRequest = {
                            // P2#12：按行只请求该权限（原为点任意一行弹全部 PermissionCatalog 权限）。
                            permLauncher.launch(arrayOf(perm.name))
                        },
                    )
                }

                item {
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }
}

/**
 * 工程声明权限页（与宿主权限管理分离）。
 *
 * 勾选清单：池 = 权限目录（[PermissionCatalog.ENTRIES]，常规/特殊分组）+ 工程已声明
 * 但目录外的权限（原样透传，归「其他」组）；[PermissionPickerList] 提供搜索。
 * 勾选结果写回 init.lua 的 user_permission（与 pro projectinfo 行为一致），
 * 打包时按白名单（声明 ∩ 宿主 + 宿主自声明）写入产物 manifest。
 * 与宿主运行时权限无关，因此不提供请求按钮、不展示宿主授予状态。
 *
 * T2（2026-08-12）：勾选即生效，toggle 时立即触发 [persistSelected] 写盘，无需保存按钮。
 * T3（2026-08-12）：搜索内嵌 AppBar（CenterAlignedTopAppBar），对齐 API 浏览器同款。
 * T4（2026-08-12）：AppBar trailingIcon 含分类筛选按钮（PermissionFilterMenuButton）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectPermissionScreen(
    project: com.lioisme.lide.model.Project,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val projectManager =
        remember {
            com.lioisme.lide.project
                .ProjectManager()
        }
    var state by remember(project) { mutableStateOf<ProjectPermState?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(project) {
        state =
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val declared =
                    projectManager
                        .loadUserPermissions(project)
                        .map {
                            com.lioisme.lide.project.PermissionResolver
                                .normalize(it)
                        }.toSet()
                ProjectPermState(selected = declared)
            }
    }

    // U8（2026-08-12）：勾选即生效——toggle 直接触发异步写盘，无需保存按钮。
    // 写盘收敛到 [projectManager.writeInitFields] 域服务（B-1）。
    // T5（2026-08-13）：参数化快照集合（调用方传 toggle 后的新 Set），
    // 不依赖闭包读 state 的时序（写盘与重组解耦）。
    fun persistSelected(selected: Set<String>) {
        scope.launch {
            val ok =
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    projectManager.writeInitFields(project) { content ->
                        com.lioisme.lide.project.LuaConfigParser.setStringArrayField(
                            content,
                            "user_permission",
                            selected.sorted(),
                        )
                    }
                }
            if (!ok) {
                Toast.makeText(context, R.string.perm_project_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // U7/T3（2026-08-12）：搜索对齐 API 浏览器模式（AGENTS.md 搜索红线）——
    // 独立 searchActive 意图 + SearchBarState 状态机唯一驱动；rememberSearchBarState()
    // 必须无参调用（按引用比较 key）；展开输入框为官方 InputField（展开态无长按
    // 文本选择崩坏问题），折叠条为自绘（规避崩坏）。
    var query by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var selectedGroup by rememberSaveable { mutableStateOf<PermissionGroupFilter?>(null) }
    var filterMenuOpen by remember { mutableStateOf(false) }
    val searchBarState = rememberSearchBarState()
    LaunchedEffect(searchActive) {
        if (searchActive) {
            searchBarState.animateToExpanded()
        } else {
            searchBarState.animateToCollapsed()
        }
    }
    LaunchedEffect(Unit) { if (!searchActive) searchBarState.snapTo(0f) }
    val focusManager = LocalFocusManager.current

    // 检索池（目录 + 工程已声明未知权限）；remember 稳定 key，仅在 selected 内容变化时重建。
    val poolUnknown by remember(state?.selected) {
        derivedStateOf { (state?.selected ?: emptySet<String>()) - PermissionCatalog.knownNames }
    }
    val allItems = rememberPickerItems(PermissionCatalog.ENTRIES, poolUnknown.sorted(), context)

    // 分组计数（用于筛选菜单）；allItems 稳定时只算一次。
    val groupCounts by remember(allItems) {
        mutableStateOf(
            mapOf<PermissionGroupFilter?, Int>(
                null to allItems.size,
                PermissionGroupFilter.REGULAR to allItems.count { it.group == PermissionGroup.REGULAR },
                PermissionGroupFilter.SPECIAL to allItems.count { it.group == PermissionGroup.SPECIAL },
                PermissionGroupFilter.OTHER to allItems.count { it.group == null },
            ),
        )
    }

    // 搜索 + 分类过滤：后台线程（Dispatchers.Default）经预索引 contains，对齐红线。
    val filtered by produceState(
        initialValue = emptyList<PickerItem>(),
        query,
        allItems,
        selectedGroup,
    ) {
        value =
            withContext(Dispatchers.Default) {
                val q = query.trim().lowercase()
                val base = if (q.isEmpty()) allItems else allItems.filter { it.searchKeys.contains(q) }
                if (selectedGroup == null) {
                    base
                } else {
                    base.filter {
                        when (selectedGroup) {
                            PermissionGroupFilter.REGULAR -> it.group == PermissionGroup.REGULAR
                            PermissionGroupFilter.SPECIAL -> it.group == PermissionGroup.SPECIAL
                            PermissionGroupFilter.OTHER -> it.group == null
                            else -> true
                        }
                    }
                }
            }
    }

    Scaffold(
        topBar = {
            // T3（2026-08-12）：搜索内嵌 AppBar，与 API 浏览器同款 CenterAlignedTopAppBar。
            CenterAlignedTopAppBar(
                title = {
                    SearchBar(
                        state = searchBarState,
                        inputField = {
                            PermissionCollapsedSearchInput(
                                query = query,
                                onClear = { query = "" },
                                onTapToExpand = { searchActive = true },
                                onBack = onBack,
                                filterSlot = {
                                    PermissionFilterMenuButton(
                                        expanded = filterMenuOpen,
                                        onExpandedChange = { filterMenuOpen = it },
                                        selectedGroup = selectedGroup,
                                        onGroupSelect = { selectedGroup = it },
                                        groupCounts = groupCounts,
                                        totalCount = allItems.size,
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
    ) { padding ->
        val s = state
        if (s == null) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else {
            // 正文列表受分类筛选影响：selectedGroup != null 时仅展示对应分组。
            val displayItems =
                if (selectedGroup == null) {
                    allItems
                } else {
                    allItems.filter {
                        when (selectedGroup) {
                            PermissionGroupFilter.REGULAR -> it.group == PermissionGroup.REGULAR
                            PermissionGroupFilter.SPECIAL -> it.group == PermissionGroup.SPECIAL
                            PermissionGroupFilter.OTHER -> it.group == null
                            else -> true
                        }
                    }
                }
            // 页级单一 LazyColumn：勾选行平铺渲染（2026-08-13 用户拍板：无分组
            // 标题——分类区分与 AppBar 筛选按钮功能冲突；共享 searchActive/
            // 筛选菜单语义）；行全宽无外部边距，左右/顶底边距为行内内部边距
            // （PermissionCheckRow horizontal 16 / vertical 12，对齐主界面工程
            // 列表），行距由行内 padding 承担（无 spacedBy）。
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(displayItems, key = { it.name }) { item ->
                    PermissionCheckRow(
                        label = item.label,
                        alias = item.aliasText,
                        checked = item.name in s.selected,
                        onToggle = {
                            // T5（2026-08-13）：不可变 Set 重赋（快照写入 → 重组），
                            // 不再原地修改 MutableSet。
                            val next = togglePermission(s.selected, item.name)
                            state = s.copy(selected = next)
                            persistSelected(next)
                        },
                    )
                }
                item {
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }

    // 全屏展开搜索：Dialog 内独立 LazyColumn（与正文互不嵌套）；建议行点击 = 切换
    // 勾选（不收起，支持连续勾选）；返回键 / IME 搜索收起（收起路径置 searchActive
    // = false + 清焦，对齐红线）。
    if (searchActive) {
        ExpandedFullScreenSearchBar(
            state = searchBarState,
            inputField = {
                PermissionSearchInputField(
                    query = query,
                    onQueryChange = { query = it },
                    active = searchActive,
                    onActiveChange = { searchActive = it },
                    onBack = {
                        searchActive = false
                        focusManager.clearFocus()
                    },
                    filterSlot = {
                        PermissionFilterMenuButton(
                            expanded = filterMenuOpen,
                            onExpandedChange = { filterMenuOpen = it },
                            selectedGroup = selectedGroup,
                            onGroupSelect = { selectedGroup = it },
                            groupCounts = groupCounts,
                            totalCount = allItems.size,
                        )
                    },
                )
            },
            content = {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    BackHandler {
                        searchActive = false
                        focusManager.clearFocus()
                    }
                    if (filtered.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.permission_no_result),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(filtered, key = { it.name }) { item ->
                                val cur = state
                                PermissionCheckRow(
                                    label = item.label,
                                    alias = item.aliasText,
                                    checked = cur != null && item.name in cur.selected,
                                    onToggle = {
                                        // T5（2026-08-13）：同正文列表——不可变 Set
                                        // 重赋 + 参数化写盘。
                                        if (cur != null) {
                                            val next = togglePermission(cur.selected, item.name)
                                            state = cur.copy(selected = next)
                                            persistSelected(next)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

/** 工程权限编辑状态（勾选即生效，无保存基线）。 */
private data class ProjectPermState(
    val selected: Set<String>,
)

/**
 * 切换勾选（T5，2026-08-13）：返回**新不可变集合**，配合
 * `state = s.copy(selected = ...)` 重赋触发 Compose 快照写入 → 重组。
 * 禁止对 MutableSet 原地增删（快照系统检测不到变化 → 无重组 → 勾选框视觉不动，
 * 历史缺陷根因）。
 */
private fun togglePermission(
    selected: Set<String>,
    name: String,
): Set<String> = if (name in selected) selected - name else selected + name

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    isSpecial: Boolean,
    onRequest: () -> Unit,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                // E1（2026-08-12）：surfaceVariant 已废弃——内容卡用 surfaceContainerLow。
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                if (granted) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint =
                    if (granted) {
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
                Text(
                    if (granted) {
                        stringResource(R.string.perm_granted)
                    } else {
                        stringResource(R.string.perm_denied)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (granted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                )
            }
            if (!granted) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onRequest) {
                    Text(stringResource(R.string.perm_request))
                }
            }
        }
    }
}

/** 特殊权限描述：独立授予检查 + 系统设置入口。 */
private data class SpecialPerm(
    val name: String,
    val labelRes: Int,
    val isGranted: (Context) -> Boolean,
    val settingsIntent: (Context) -> Intent,
)

private val SPECIAL_PERMISSIONS: List<SpecialPerm> =
    listOf(
        SpecialPerm(
            name = Manifest.permission.SYSTEM_ALERT_WINDOW,
            labelRes = R.string.perm_special_overlay,
            isGranted = { Settings.canDrawOverlays(it) },
            settingsIntent = {
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${it.packageName}"))
            },
        ),
        SpecialPerm(
            name = Manifest.permission.WRITE_SETTINGS,
            labelRes = R.string.perm_special_write_settings,
            isGranted = { Settings.System.canWrite(it) },
            settingsIntent = {
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${it.packageName}"))
            },
        ),
        SpecialPerm(
            name = Manifest.permission.MANAGE_EXTERNAL_STORAGE,
            labelRes = R.string.perm_special_all_files,
            isGranted = { ctx ->
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    android.os.Environment.isExternalStorageManager()
            },
            settingsIntent = {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .setData(Uri.parse("package:${it.packageName}"))
            },
        ),
        SpecialPerm(
            name = Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            labelRes = R.string.perm_special_battery,
            isGranted = { ctx ->
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return@SpecialPerm false
                pm.isIgnoringBatteryOptimizations(ctx.packageName)
            },
            settingsIntent = {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:${it.packageName}"))
            },
        ),
        SpecialPerm(
            name = Manifest.permission.REQUEST_INSTALL_PACKAGES,
            labelRes = R.string.perm_special_install_unknown,
            isGranted = { ctx ->
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    ctx.packageManager.canRequestPackageInstalls()
            },
            settingsIntent = {
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${it.packageName}"))
            },
        ),
        SpecialPerm(
            name = Manifest.permission.PACKAGE_USAGE_STATS,
            labelRes = R.string.perm_special_usage_stats,
            isGranted = { ctx ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return@SpecialPerm true
                val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return@SpecialPerm false
                // API 29+ 用 unsafeCheckOpNoThrow；API 24-28 无该方法（直接调用会 NoSuchMethodError），
                // 回退自 API 19 起的公开 checkOpNoThrow（Q 起弃用，此处为旧分支语义正确性保留）。
                val mode =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        appOps.unsafeCheckOpNoThrow(
                            AppOpsManager.OPSTR_GET_USAGE_STATS,
                            android.os.Process.myUid(),
                            ctx.packageName,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        appOps.checkOpNoThrow(
                            AppOpsManager.OPSTR_GET_USAGE_STATS,
                            android.os.Process.myUid(),
                            ctx.packageName,
                        )
                    }
                mode == AppOpsManager.MODE_ALLOWED
            },
            settingsIntent = {
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            },
        ),
    )

private data class PermissionItem(
    val name: String,
    val labelRes: Int,
    val granted: Boolean,
)

/** 宿主权限页一次构建的全部展示状态（特殊权限 + 常规权限）。 */
private data class HostPermUiState(
    val specials: List<Pair<SpecialPerm, Boolean>>,
    val regular: List<PermissionItem>,
)

private fun checkGranted(
    ctx: Context,
    name: String,
): Boolean = ContextCompat.checkSelfPermission(ctx, name) == PackageManager.PERMISSION_GRANTED

private fun buildHostPermUiState(ctx: Context): HostPermUiState =
    HostPermUiState(
        specials = SPECIAL_PERMISSIONS.map { it to it.isGranted(ctx) },
        regular = buildHostPermissionList(ctx),
    )

private fun buildHostPermissionList(ctx: Context): List<PermissionItem> {
    val declared = PermissionCatalog.hostDeclaredPermissions(ctx)
    return PermissionCatalog.ALL
        .filter { (name, _) -> name in declared }
        .map { (name, labelRes) ->
            PermissionItem(name, labelRes, checkGranted(ctx, name))
        }
}
