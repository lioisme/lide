package com.lioisme.delua.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 标签行（纯 Compose，零宿主依赖）。
 *
 * 展示打开文档列表：选中态、脏标记圆点、关闭按钮，超出宽度可横向滚动。
 * 数据（[EditorTab] 列表）由宿主注入，本组件不持有任何文件/会话状态。
 */
data class EditorTab(
    val fileName: String,
    val dirty: Boolean,
)

@Composable
fun EditorTabs(
    tabs: List<EditorTab>,
    selectedFile: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(tabs, key = { it.fileName }) { tab ->
            EditorTabItem(
                tab = tab,
                selected = tab.fileName == selectedFile,
                onClick = { onTabClick(tab.fileName) },
                onClose = { onTabClose(tab.fileName) },
            )
        }
    }
}

@Composable
private fun EditorTabItem(
    tab: EditorTab,
    selected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        onClick = onClick,
        modifier = Modifier.height(34.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 2.dp),
        ) {
            Text(
                text = tab.fileName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (tab.dirty) {
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(6.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                )
            } else {
                Box(modifier = Modifier.padding(start = 6.dp).size(6.dp))
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(26.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_close_tab),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
