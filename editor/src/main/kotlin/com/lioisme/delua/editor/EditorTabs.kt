package com.lioisme.delua.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 标签行（纯 Compose，零宿主依赖）。
 *
 * 展示打开文档列表：
 * - 每个已打开文档均有指示器 —— 选中：全宽主题色指示条；未选中：主题色圆点；
 * - 文本使用主题色（选中 = primary，未选中 = onSurfaceVariant）；
 * - 单击切换文档，长按（原生振动）调出关闭菜单（关闭 / 关闭其他 / 关闭全部）；
 * - 脏标记前缀、超出宽度横向滚动。数据（[EditorTab] 列表）由宿主注入，
 *   本组件不持有任何文件/会话状态；切换/关闭事件一律经回调上报宿主。
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
    onCloseOthers: (String) -> Unit = {},
    onCloseAll: () -> Unit = {},
    state: LazyListState = rememberLazyListState(),
) {
    // 仅在「追加」时滚动到新标签：数量不变的重排不重启本效应，关闭不触发。
    val previousCount = remember { mutableIntStateOf(tabs.size) }
    LaunchedEffect(tabs.size) {
        if (tabs.size > previousCount.intValue) {
            state.animateScrollToItem(tabs.size - 1)
        }
        previousCount.intValue = tabs.size
    }

    LazyRow(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
        // 产品规范：标签与标签栏之间无距离（垂直内边距为 0，标签贴满条带）。
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(tabs, key = { it.fileName }) { tab ->
            EditorTabItem(
                tab = tab,
                selected = tab.fileName == selectedFile,
                onClick = { onTabClick(tab.fileName) },
                onClose = { onTabClose(tab.fileName) },
                onCloseOthers = onCloseOthers,
                onCloseAll = onCloseAll,
                modifier = Modifier,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditorTabItem(
    tab: EditorTab,
    selected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onCloseOthers: (String) -> Unit,
    onCloseAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var menuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .height(36.dp)
            // 单击：切换打开文档（含已选中的标签，恒为切换语义）；
            // 长按：原生振动（与主界面工程列表同源，经 View 系统 LONG_PRESS）+ 关闭菜单。
            .combinedClickable(
                role = Role.Tab,
                onClick = { onClick() },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuExpanded = true
                },
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = if (tab.dirty) {
                    stringResource(R.string.dirty_marker) + tab.fileName
                } else {
                    tab.fileName
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 打开文档指示器：选中 = 全宽主题色指示条；未选中 = 主题色圆点。
        if (selected) {
            // 选中条：LazyRow 子项宽度约束为无限，fillMaxWidth 直接用在条上是空操作（宽为 0），
            // 且 align() 若位于 matchParentSize() 外层会破坏 Box 对它的特殊处理（仍得 0 宽）。
            // 正确写法：matchParentSize() 作为最外层修饰符，用内层 Box 的 contentAlignment 摆放。
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_close_tab)) },
                onClick = {
                    menuExpanded = false
                    onClose()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_close_others)) },
                onClick = {
                    menuExpanded = false
                    onCloseOthers(tab.fileName)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_close_all)) },
                onClick = {
                    menuExpanded = false
                    onCloseAll()
                },
            )
        }
    }
}
