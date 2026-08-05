package com.lioisme.lide.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 标签行（纯 Compose，零宿主依赖）。
 *
 * 展示打开文档列表：
 * - 每个已打开文档均有指示器 —— 选中：全宽主题色指示条；未选中：主题色圆点；
 * - 文本使用主题色（选中 = primary，未选中 = onSurfaceVariant）；
 * - 手势契约：单击切换文档；长按拖动排序（拖起 alpha 0.5，原生长按振动）；
 *   关闭菜单为每个标签常驻的 ⋮ 下拉入口（关闭 / 关闭其他 / 关闭全部）；
 * - 标签标题仅显示文件名（去路径），过长省略号截断；
 * - 脏标记前缀、超出宽度横向滚动。数据（[EditorTab] 列表）由宿主注入，
 *   本组件不持有任何文件/会话状态；切换/关闭/排序事件一律经回调上报宿主。
 *
 * 交互简化（C-3）：删除「位移观察协程 + 长按松手弹菜单」的逆向手势实现，
 * 关闭菜单改为常驻 DropdownMenu 入口；长按手势仅保留拖拽排序语义，
 * 不再需要区分「长按松手」与「长按拖动」。
 */
/**
 * 打开的标签状态（单一状态类，B-5：合并宿主 `TabState` 双份同构类）。
 * 显示层只用 [fileName]/[dirty]；undo/redo 能力供宿主工具栏消费。
 */
data class EditorTab(
    val fileName: String,
    val dirty: Boolean,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

/** 标签最大显示宽度：LazyRow 子项宽度约束无上限，不封顶则省略号永不生效。 */
private val MaxTabWidth = 160.dp

/** 拖拽中条目透明度（对齐 Squircle DragAlpha 常量）。 */
private const val DRAG_ALPHA = 0.5f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorTabs(
    tabs: List<EditorTab>,
    selectedFile: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCloseOthers: (String) -> Unit = {},
    onCloseAll: () -> Unit = {},
    // 拖拽排序回调（from/to 为移动前索引 → 移动后索引），默认 no-op 保持参数兼容。
    onTabMoved: (from: Int, to: Int) -> Unit = { _, _ -> },
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

    val reorderableState =
        rememberReorderableLazyListState(state) { from, to ->
            onTabMoved(from.index, to.index)
        }

    LazyRow(
        state = state,
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
        // 产品规范：标签与标签栏之间无距离（垂直内边距为 0，标签贴满条带）。
        contentPadding = PaddingValues(horizontal = 8.dp),
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
                    isDragging = isDragging,
                    dragScope = this,
                    modifier =
                        Modifier
                            .alpha(if (isDragging) DRAG_ALPHA else 1f),
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
    isDragging: Boolean,
    dragScope: ReorderableCollectionItemScope,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var menuExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember(tab.fileName) { MutableInteractionSource() }
    // 标签标题：仅显示文件名（去目录路径），过长省略号截断。
    val displayName =
        remember(tab.fileName) {
            tab.fileName.substringAfterLast('/').substringAfterLast('\\')
        }

    Box(
        modifier =
            modifier
                .height(36.dp)
                // 单击（短按松手）= 切换标签（clickable；selected 语义供无障碍/自动化识别）。
                .clickable(
                    role = Role.Tab,
                    onClick = onClick,
                ).semantics { this.selected = selected }
                // reorderable 3.0.0 原生手势（库内为 detectDragGesturesAfterLongPress）：
                // 长按 = 拖拽排序启动（onDragStarted，原生长按振动）。
                // 关闭菜单不再依赖长按松手判定（C-3），由常驻 ⋮ 入口打开。
                .then(
                    with(dragScope) {
                        Modifier.longPressDraggableHandle(
                            enabled = true,
                            interactionSource = interactionSource,
                            onDragStarted = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                        )
                    },
                ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(start = 12.dp)
                    // 过长标签省略号截断（LazyRow 子项宽度无上限，必须显式封顶）。
                    .widthIn(max = MaxTabWidth),
        ) {
            Text(
                text =
                    if (tab.dirty) {
                        stringResource(R.string.dirty_marker) + displayName
                    } else {
                        displayName
                    },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 常驻关闭菜单入口（C-3）：⋮ 打开关闭/关闭其他/关闭全部；点击独立于标签切换。
        IconButton(
            onClick = { menuExpanded = true },
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(24.dp),
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.menu_close_tab),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(MaterialTheme.colorScheme.primary),
                )
            }
        } else {
            Box(
                modifier =
                    Modifier
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
