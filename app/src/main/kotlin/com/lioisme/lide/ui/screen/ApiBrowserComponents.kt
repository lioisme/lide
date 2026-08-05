package com.lioisme.lide.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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

/*
 * API 浏览器界面组件集（与 ApiBrowserScreen 状态机解耦，P3-2 拆分）：
 * 搜索输入条 / 分类筛选菜单 / 列表行与徽标 / 纯函数助手。
 * 线程契约同 ApiBrowserScreen：行级反射信息仅经 ApiIndex.rowInfoAsync（IO 单飞）。
 */

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
internal fun ApiSearchInputField(
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
 * `onFocusChanged { if (isFocused) onExpandedChange(true) }` 由"获得焦点"驱动展开，
 * 而长按（文本选择/光标）同样会请求焦点，导致长按也进入 SearchView。
 * 本组件用 [detectTapGestures] 区分单击（展开）与长按（无副作用），
 * 首尾图标按钮（返回 / 清除 / 分类筛选）各自保留单击语义与长按 tooltip。
 * 展开后的输入、焦点与键盘由 ExpandedFullScreenSearchBar 的 focusRequester 自动接管。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ApiCollapsedSearchInput(
    query: String,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onTapToExpand: () -> Unit,
    filterSlot: @Composable () -> Unit,
) {
    val placeholder = stringResource(R.string.api_search_hint)
    val container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    Row(
        modifier =
            Modifier
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
            modifier =
                Modifier
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
                color =
                    if (query.isEmpty()) {
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

/** 分类筛选按钮 + DropdownMenu（SearchBar trailingIcon 槽内使用，锚点随输入条）。 */
@Composable
internal fun FilterMenuButton(
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
internal fun ClassRow(
    entry: ApiClassEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    // 行级反射信息（接口判定 + 可加载性）经 rowInfoAsync 获取：命中 build 期预热缓存时 O(1) 查表；
    // 未命中时单飞计算（NonCancellable，不随行生命周期取消，fling 不重启全 dex 扫描）。
    // 失败降级按不可用处理。
    val info by produceState<RowInfo?>(initialValue = null, entry.name) {
        value =
            try {
                ApiIndex.rowInfoAsync(entry.name)
            } catch (_: Throwable) {
                RowInfo(kind = null, loadable = false)
            }
    }
    val unavailable = info?.loadable == false
    // 固定行高（两行单行文本 + 固定间距 + divider），避免 LazyColumn 测量抖动。
    Row(
        modifier =
            Modifier
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
private fun TypeBadge(
    entry: ApiClassEntry,
    info: RowInfo?,
) {
    val isInterface = info?.kind?.isInterface == true
    val label =
        when {
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
private fun usageLabel(usage: ApiUsage): String =
    stringResource(
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
        },
    )

/** 简单名派生：内部类优先取最后一个 `$` 之后（MenuPresenter$Callback → Callback），否则取最后一个 `.` 之后。 */
internal fun simpleNameOf(fqn: String): String {
    val dollar = fqn.lastIndexOf('$')
    val cut = if (dollar >= 0) dollar else fqn.lastIndexOf('.')
    return if (cut >= 0) fqn.substring(cut + 1) else fqn
}

/** 快照过滤：命中 build 期分类预索引（O(选中子集)）；后台线程调用（UI 只经 remember/produceState）。 */
internal fun filterSnapshot(
    snapshot: Snapshot,
    category: ApiCategory?,
    query: String,
): List<ApiClassEntry> {
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
            indices
                .asSequence()
                .filter { snapshot.nameLower[it].contains(q) }
                .map { snapshot.classes[it] }
                .toList()
        }
    }
}

internal fun copyToClipboard(
    context: Context,
    text: String,
) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("lide-api", text))
    } catch (_: Throwable) {
    }
}

@Composable
internal fun categoryLabel(category: ApiCategory): String =
    stringResource(
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
        },
    )
