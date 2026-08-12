package com.lioisme.lide.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
 *   返回 / 清除按钮，展开态与收起态共用）；
 * - [PermissionGroupHeader] / [PermissionCheckRow]：分组标题与勾选行，供页级
 *   LazyColumn 直接消费（消除嵌套滚动容器）；
 * - [PermissionPickerList]：工程属性页弹窗专用（常驻搜索框 + 列表，高度由调用方约束）。
 *
 * 过滤全部经 [Dispatchers.Default] 后台执行（AGENTS.md 搜索红线：禁止 UI 线程
 * 对全量条目做 O(n) 过滤）。
 */
@Composable
internal fun PermissionPickerList(
    catalog: List<PermissionEntry>,
    unknownPermissions: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    // 预建小写检索索引（名称 + 中文名 + 别名）；池在组合期内不变，只算一次。
    val items = rememberPickerItems(catalog, unknownPermissions, context)

    val queryText = query.trim().lowercase()
    val filtered =
        remember(query, items) {
            if (queryText.isEmpty()) items else items.filter { it.searchKeys.contains(queryText) }
        }

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.permission_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.permission_search_clear))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Text(
                stringResource(R.string.permission_no_result),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                permissionGroupItems(filtered) { item ->
                    PermissionCheckRow(
                        label = item.label,
                        alias = item.aliasText,
                        checked = item.name in selected,
                        onToggle = { onToggle(item.name) },
                    )
                }
            }
        }
    }
}

/**
 * 检索索引构建（页面 / 弹窗共用）：池 = 目录条目 + 工程已声明但目录外的未知权限。
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

/** 分组序（常规 → 特殊 → 其他），空组跳过；页级 LazyColumn 与弹窗列表共用。 */
private val PICKER_GROUPS: List<Triple<PermissionGroup?, Int, (PickerItem) -> Boolean>> =
    listOf(
        Triple(PermissionGroup.REGULAR, R.string.perm_regular_section, { g: PickerItem -> g.group == PermissionGroup.REGULAR }),
        Triple(PermissionGroup.SPECIAL, R.string.perm_special_section, { g: PickerItem -> g.group == PermissionGroup.SPECIAL }),
        Triple(null, R.string.perm_other_section, { g: PickerItem -> g.group == null }),
    )

/** 分组渲染（LazyListScope 扩展）：标题 + 行，空组跳过（页面与弹窗共用渲染序）。 */
internal fun androidx.compose.foundation.lazy.LazyListScope.permissionGroupItems(
    items: List<PickerItem>,
    row: @Composable (PickerItem) -> Unit,
) {
    for ((_, headerRes, predicate) in PICKER_GROUPS) {
        val groupItems = items.filter(predicate)
        if (groupItems.isEmpty()) continue
        item(key = "header-$headerRes") {
            PermissionGroupHeader(headerRes)
        }
        items(groupItems, key = { it.name }) { item ->
            row(item)
        }
    }
}

@Composable
internal fun PermissionGroupHeader(headerRes: Int) {
    Text(
        stringResource(headerRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
internal fun PermissionCheckRow(
    label: String,
    alias: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
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
 * `onFocusChanged` 因长按文本选择/获得焦点误触发展开；leading 为搜索图标
 * （页面顶栏已承载返回），尾部清除按钮在查询非空时展示。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PermissionCollapsedSearchInput(
    query: String,
    onClear: () -> Unit,
    onTapToExpand: () -> Unit,
    modifier: Modifier = Modifier,
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
        Icon(
            Icons.Default.Search,
            contentDescription = stringResource(R.string.permission_search_hint),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp).size(24.dp),
        )
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
        Spacer(Modifier.width(4.dp))
    }
}

/** 全屏搜索输入条（ExpandedFullScreenSearchBar inputField 槽）：返回 / 清除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PermissionSearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onBack: () -> Unit,
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
            if (query.isNotEmpty()) {
                TipIconButton(tooltip = stringResource(R.string.permission_search_clear), onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.permission_search_clear),
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}