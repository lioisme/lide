package com.lioisme.delua.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lioisme.delua.editor.R

/**
 * 右上角更多菜单项（纯 UI 数据：分类 + 标签 + 点击回调）。
 *
 * [category] 为菜单分类标题：相邻项分类不同（或首个带分类）时，菜单内
 * 插入一个分类标题；null 表示并入上一分类。
 */
data class EditorMoreAction(
    val category: String?,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * 编辑器顶栏。
 *
 * 布局约束（产品规范）：含返回键在内最多 5 个按钮 —— 返回 / 运行 / 撤销 /
 * 重做 / 更多；文件树、搜索等其余功能一律收进「更多」分类菜单。
 * 更多菜单经 Box 包裹锚定在 ⋮ 按钮正下方（菜单不得移位）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorToolbar(
    fileName: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRun: () -> Unit,
    // 右上角更多（⋮）分类菜单项：文件树 / 搜索 / 工程信息 / 工程权限 / 打包 APK 等。
    moreActions: List<EditorMoreAction> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Text(
                text = fileName,
                maxLines = 1,
                fontWeight = FontWeight.Medium,
            )
        },
        navigationIcon = {
            TipIconButton(
                tooltip = stringResource(R.string.cd_back),
                onClick = onBack,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
        },
        actions = {
            TipIconButton(
                tooltip = stringResource(R.string.cd_run),
                onClick = onRun,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.cd_run),
                )
            }
            TipIconButton(
                tooltip = stringResource(R.string.cd_undo),
                onClick = onUndo,
                enabled = canUndo,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = stringResource(R.string.cd_undo),
                )
            }
            TipIconButton(
                tooltip = stringResource(R.string.cd_redo),
                onClick = onRedo,
                enabled = canRedo,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = stringResource(R.string.cd_redo),
                )
            }
            // ⋮ 按钮与菜单必须同处一个 Box：DropdownMenu 的锚点 = 其直接父节点，
            // 直接写在 actions Row 内会锚定到整行起点导致菜单移位。
            Box {
                TipIconButton(
                    tooltip = stringResource(R.string.cd_more),
                    onClick = { menuExpanded = true },
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.cd_more),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    moreActions.forEachIndexed { index, action ->
                        if (action.category != null &&
                            (index == 0 || moreActions[index - 1].category != action.category)
                        ) {
                            MoreMenuCategoryHeader(action.category)
                        }
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            onClick = {
                                menuExpanded = false
                                action.onClick()
                            },
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier,
    )
}

/** 更多菜单分类标题（M3 1.3.0 无 DropdownMenuHeader，按 1.4 视觉对齐手写）。 */
@Composable
private fun MoreMenuCategoryHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}
