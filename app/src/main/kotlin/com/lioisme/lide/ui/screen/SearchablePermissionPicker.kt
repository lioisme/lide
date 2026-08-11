package com.lioisme.lide.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R

/**
 * 工程权限搜索选择列表（2026-08-11，P4）：工程属性页权限弹窗与工程权限页共用。
 *
 * - 搜索按 权限全名 / 中文名 / 别名 三路小写子串匹配（[PermissionCatalog] 别名关键词）；
 * - 池 = 目录条目（常规/特殊分组展示）＋ 工程已声明但目录外的未知权限（原样透传，归「其他」组）；
 * - 仅工程相关两处消费；宿主运行时权限页（HostPermissionScreen）不适用本组件。
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
    val items =
        remember(catalog, unknownPermissions) {
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
        Text(
            stringResource(R.string.permission_selected_count, selected.size, items.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (filtered.isEmpty()) {
            Text(
                stringResource(R.string.permission_no_result),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                val groups =
                    listOf(
                        Triple(PermissionGroup.REGULAR, R.string.perm_regular_section, { g: PickerItem -> g.group == PermissionGroup.REGULAR }),
                        Triple(PermissionGroup.SPECIAL, R.string.perm_special_section, { g: PickerItem -> g.group == PermissionGroup.SPECIAL }),
                        Triple(null, R.string.perm_other_section, { g: PickerItem -> g.group == null }),
                    )
                for ((_, headerRes, predicate) in groups) {
                    val groupItems = filtered.filter(predicate)
                    if (groupItems.isEmpty()) continue
                    item(key = "header-$headerRes") {
                        Text(
                            stringResource(headerRes),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp),
                        )
                    }
                    items(groupItems, key = { it.name }) { item ->
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
}

private data class PickerItem(
    val name: String,
    val label: String,
    val group: PermissionGroup?,
    val aliasText: String,
    val searchKeys: String,
)

@Composable
private fun PermissionCheckRow(
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
