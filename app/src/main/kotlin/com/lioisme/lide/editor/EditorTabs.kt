package com.lioisme.lide.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
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
 *   长按后未超过系统触摸阈值位移、直接松手 = 调出关闭菜单（关闭 / 关闭其他 / 关闭全部）；
 * - 标签标题仅显示文件名（去路径），过长省略号截断；
 * - 脏标记前缀、超出宽度横向滚动。数据（[EditorTab] 列表）由宿主注入，
 *   本组件不持有任何文件/会话状态；切换/关闭/排序事件一律经回调上报宿主。
 *
 * 手势实现（reorderable 3.0.0，已反编译二进制校验 API）：
 * - 排序/长按检测走库原生 `longPressDraggableHandle`（内部为
 *   detectDragGesturesAfterLongPress）；「长按松手是否弹菜单」用「本手势内是否出现
 *   超过系统触摸阈值位移」判定：无位移 = 菜单，有位移 = 拖动排序。
 *   位移由只读观察协程累计（不消费事件），且在任何 UP 事件送达前即已置位，
 *   与 onDragStopped 回调（由 UP 触发）不存在时序竞态。
 * - 标签卡不再常驻 ⋮ 菜单按钮（功能重复，用户决策），菜单唯一入口为长按松手。
 *
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
                // T19（2026-08-12）：标签行回归宿主 Material3 surface，
                // 与 AppBar/主界面一致；编辑器画布（文本区）由主题系统独立着色。
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
    val viewConfiguration = LocalViewConfiguration.current
    var menuExpanded by remember { mutableStateOf(false) }
    // 「本手势是否出现超过系统触摸阈值的位移」：区分「长按松手弹菜单」与「拖动排序」。
    // 只读观察协程在 MOVE 事件期间置位（早于 UP 事件），onDragStopped 读取时已无竞态。
    val dragMoved = remember(tab.fileName) { mutableStateOf(false) }
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
                // 只读位移观察：不消费任何事件，仅累计记录本手势是否有超过触摸阈值的位移
                // （阈值来自系统 ViewConfiguration.touchSlop，非自定义距离判定）。
                .pointerInput(tab.fileName) {
                    awaitEachGesture {
                        dragMoved.value = false
                        var accumulated = Offset.Zero
                        val down = awaitFirstDown(requireUnconsumed = false) ?: return@awaitEachGesture
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                            if (!change.pressed) break
                            accumulated += change.position - change.previousPosition
                            if (!dragMoved.value &&
                                accumulated.getDistance() >= viewConfiguration.touchSlop
                            ) {
                                dragMoved.value = true
                            }
                        }
                    }
                }
                // 单击（短按松手）= 切换标签（clickable；selected 语义供无障碍/自动化识别）。
                .clickable(
                    role = Role.Tab,
                    onClick = onClick,
                ).semantics { this.selected = selected }
                // reorderable 3.0.0 原生手势（库内为 detectDragGesturesAfterLongPress）：
                // 长按 = 拖拽排序启动（onDragStarted，原生长按振动）；
                // 长按后未超过触摸阈值位移直接松手 = onDragStopped → 关闭菜单。
                .then(
                    with(dragScope) {
                        Modifier.longPressDraggableHandle(
                            enabled = true,
                            interactionSource = interactionSource,
                            onDragStarted = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragStopped = {
                                if (!dragMoved.value) menuExpanded = true
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
                    .padding(horizontal = 12.dp)
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
