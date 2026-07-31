package com.lioisme.delua.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 标签行（纯 Compose，零宿主依赖）。
 *
 * 展示打开文档列表：扁平标签 + 底部选中指示条、脏标记前缀、关闭按钮、长按拖拽排序、
 * 点击选中标签弹关闭菜单，超出宽度可横向滚动。数据（[EditorTab] 列表）由宿主注入，
 * 本组件不持有任何文件/会话状态；排序/关闭事件一律经回调上报宿主。
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
    onTabMoved: (from: Int, to: Int) -> Unit = { _, _ -> },
    onCloseOthers: (String) -> Unit = {},
    onCloseAll: () -> Unit = {},
    state: LazyListState = rememberLazyListState(),
) {
    val haptics = LocalHapticFeedback.current
    val reorderableState = rememberReorderableLazyListState(state) { from, to ->
        onTabMoved(from.index, to.index)
    }

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
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(tabs, key = { it.fileName }) { tab ->
            ReorderableItem(reorderableState, tab.fileName) { isDragging ->
                EditorTabItem(
                    tab = tab,
                    selected = tab.fileName == selectedFile,
                    onClick = { onTabClick(tab.fileName) },
                    onClose = { onTabClose(tab.fileName) },
                    onCloseOthers = onCloseOthers,
                    onCloseAll = onCloseAll,
                    modifier = Modifier
                        .alpha(if (isDragging) 0.5f else 1f)
                        .longPressDraggableHandle(
                            onDragStarted = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragStopped = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                        ),
                )
            }
        }
    }
}

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
    val interactionSource = remember { MutableInteractionSource() }
    var menuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .height(36.dp)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Tab,
                onClick = {
                    if (selected) menuExpanded = true else onClick()
                },
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 12.dp, end = 2.dp),
        ) {
            Text(
                text = if (tab.dirty) {
                    stringResource(R.string.dirty_marker) + tab.fileName
                } else {
                    tab.fileName
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                    ),
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
