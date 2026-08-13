package com.lioisme.lide.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import com.lioisme.lide.editor.TipIconButton

/**
 * 工程权限搜索选择器组件集（2026-08-11 P4 建立；2026-08-12 U7 简约化重构）：
 *
 * - [PermissionCollapsedSearchInput]：折叠搜索条（与 API 浏览器折叠条同款交互——
 *   单击展开全屏搜索、长按无副作用），权限场景 leading 为搜索图标；
 * - [PermissionSearchInputField]：全屏搜索输入条（SearchBarDefaults.InputField，
 *   返回 / 清除 / 分类筛选按钮，展开态与收起态共用）；
 * - [PermissionFilterMenuButton]：分类筛选按钮 + DropdownMenu（ALL / REGULAR / SPECIAL / OTHER）；
 * - [PermissionCheckRow]：勾选行——行本身全宽（无相对外部边距），内部边距
 *   （horizontal 16dp / vertical 12dp，对齐主界面工程列表行）由行内 padding 承担，
 *   供页级 LazyColumn 与全屏搜索直接消费（消除嵌套滚动容器）。
 *
 * 2026-08-13 拍板：列表**平铺渲染**（不区分常规/特殊/其他分组标题——分类
 * 区分与筛选按钮功能冲突），分组语义仅保留于筛选菜单（[PermissionGroupFilter]）。
 *
 * 过滤全部经 [Dispatchers.Default] 后台执行（AGENTS.md 搜索红线：禁止 UI 线程
 * 对全量条目做 O(n) 过滤）。
 */
/**
 * 检索索引构建（页面与全屏搜索共用）：池 = 目录条目 + 工程已声明但目录外的未知权限。
 * KDoc 契约与 PermissionCatalog 同步（未知权限归「其他」组，名称原样）。
 */
@Composable
internal fun rememberPickerItems(
    catalog: List<PermissionEntry>,
    unknownPermissions: List<String>,
    context: android.content.Context,
): List<PickerItem> =
    androidx.compose.runtime.remember(catalog, unknownPermissions) {
        buildList {
            catalog.forEach { e ->
                val label = context.getString(e.labelRes)
                add(
                    PickerItem(
                        name = e.name,
                        label = label,
                        group = e.group,
                        aliasText = e.aliases.joinToString(" · "),
                        searchKeys = (listOf(e.name, label) + e.aliases).joinToString(" ").lowercase(),
                    ),
                )
            }
            unknownPermissions.forEach { name ->
                add(
                    PickerItem(
                        name = name,
                        label = name,
                        group = null,
                        aliasText = "",
                        searchKeys = name.lowercase(),
                    ),
                )
            }
        }
    }

/** 过滤（纯函数，后台线程调用；空查询零拷贝返回原表）。 */
internal fun filterPickerItems(
    items: List<PickerItem>,
    query: String,
): List<PickerItem> {
    val q = query.trim().lowercase()
    return if (q.isEmpty()) items else items.filter { it.searchKeys.contains(q) }
}

internal data class PickerItem(
    val name: String,
    val label: String,
    val group: PermissionGroup?,
    val aliasText: String,
    val searchKeys: String,
)

@Composable
internal fun PermissionCheckRow(
    label: String,
    alias: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    // 行主体整行 toggleable（T5，2026-08-13）：点击消费收敛到外层单一 toggleable，
    // Checkbox 仅作视觉（onCheckedChange = null）——避免「Row.clickable +
    // Checkbox.onCheckedChange」同区位双触发（先 add 后 remove 状态不变），
    // 同时整行（含文字区）可点，扩展点击热区。
    // 边距契约（2026-08-13 二次拍板，三修）：修饰符顺序对齐主界面工程列表
    // （ProjectListScreen `fillMaxWidth → combinedClickable → padding`）——
    // toggleable 在 padding **外层**：点击热区 / 波纹全宽（无相对外部边距，
    // 长按波纹铺满整行），padding 在内层只缩进内容（左右 16dp / 顶底 12dp）。
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            // T-01（2026-08-12）：显式声明 onSurface，确保任意 seed 色下对比度达标。
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (alias.isNotEmpty()) {
                Text(
                    alias,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 折叠搜索条（U7，2026-08-12）：与 API 浏览器折叠条同款交互——`detectTapGestures`
 * 区分单击（展开）与长按（无副作用），规避 SearchBarDefaults.InputField 内部
 * `onFocusChanged` 因长按文本选择/获得焦点误触发展开；leading 为返回按钮
 * （与 [ApiCollapsedSearchInput] 同款，点击收起搜索态），尾部清除按钮在查询非空时展示。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PermissionCollapsedSearchInput(
    query: String,
    onClear: () -> Unit,
    onTapToExpand: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    filterSlot: @Composable () -> Unit = {},
) {
    val placeholder = stringResource(R.string.permission_search_hint)
    // E1（2026-08-12）：搜索条浮于表面之上——surfaceContainerHigh（M3 search bar 容器）。
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(container)
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // B1（2026-08-12）：leading 与 API 浏览器同款——返回箭头（收起搜索态），
        // 而非搜索图标；返回语义由调用方传入 [onBack]。
        TipIconButton(tooltip = stringResource(R.string.cd_back), onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
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
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        if (query.isNotEmpty()) {
            TipIconButton(tooltip = stringResource(R.string.permission_search_clear), onClick = onClear) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.permission_search_clear),
                )
            }
        }
        filterSlot()
        Spacer(Modifier.width(4.dp))
    }
}

/** 全屏搜索输入条（ExpandedFullScreenSearchBar inputField 槽）：返回 / 清除 / 分类筛选。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PermissionSearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    filterSlot: @Composable () -> Unit = {},
) {
    SearchBarDefaults.InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = { onActiveChange(false) },
        expanded = active,
        onExpandedChange = onActiveChange,
        placeholder = { Text(stringResource(R.string.permission_search_hint)) },
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
                    TipIconButton(tooltip = stringResource(R.string.permission_search_clear), onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.permission_search_clear),
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
 * 权限分类筛选按钮 + DropdownMenu（PermissionScreen AppBar trailingIcon 槽内使用）。
 * 选项：全部 / 常规 / 特殊 / 其他；计数来自 [groupCounts]（由调用方预计算）。
 */
@Composable
internal fun PermissionFilterMenuButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    selectedGroup: PermissionGroupFilter?,
    onGroupSelect: (PermissionGroupFilter?) -> Unit,
    groupCounts: Map<PermissionGroupFilter?, Int>,
    totalCount: Int?,
) {
    Box {
        TipIconButton(tooltip = stringResource(R.string.perm_filter), onClick = { onExpandedChange(true) }) {
            Icon(
                Icons.Default.FilterList,
                contentDescription = stringResource(R.string.perm_filter),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            FilterOption(
                label = stringResource(R.string.perm_filter_all),
                count = totalCount,
                selected = selectedGroup == null,
                onClick = {
                    onGroupSelect(null)
                    onExpandedChange(false)
                },
            )
            PermissionGroupFilter.entries.forEach { group ->
                FilterOption(
                    label =
                        when (group) {
                            PermissionGroupFilter.REGULAR -> stringResource(R.string.perm_regular_section)
                            PermissionGroupFilter.SPECIAL -> stringResource(R.string.perm_special_section)
                            PermissionGroupFilter.OTHER -> stringResource(R.string.perm_other_section)
                        },
                    count = groupCounts[group],
                    selected = selectedGroup == group,
                    onClick = {
                        onGroupSelect(group)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterOption(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { RadioButton(selected = selected, onClick = null) },
        trailingIcon = {
            if (count != null) {
                Text(
                    text = stringResource(R.string.perm_filter_count, count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onClick = onClick,
    )
}

/** 权限分类筛选器选项（全部 / 常规 / 特殊 / 其他）。 */
internal enum class PermissionGroupFilter {
    REGULAR,
    SPECIAL,
    OTHER,
}
