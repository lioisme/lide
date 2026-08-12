package com.lioisme.lide.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
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
import com.lioisme.lide.R
import me.saket.cascade.CascadeDropdownMenu

/**
 * 右上角更多菜单项（纯 UI 数据：分类 + 标签 + 点击回调）。
 *
 * [category] 为菜单分类标题：分类相同的相邻项归入同一组，在「更多」菜单中
 * 渲染为一级分类项（带展开箭头），其下挂二级子菜单展示各功能项；
 * null 表示并入上一分类。
 */
data class EditorMoreAction(
    val category: String?,
    val label: String,
    /** 是否可点击（置灰项不触发 [onClick]）。 */
    val enabled: Boolean = true,
    /** 勾选态（如「只读」等开关项显示 MD3 check 图标）；null = 不显示。 */
    val checked: Boolean? = null,
    val onClick: () -> Unit,
)

/**
 * 编辑器顶栏。
 *
 * 布局约束（产品规范）：含返回键在内最多 5 个按钮 —— 返回 / 运行 / 撤销 /
 * 重做 / 更多；文件树等其余功能一律收进「更多」多级菜单
 * （一级 = 分类，二级 = 具体功能，禁止平铺）。
 * 标题显示工程名（[title]，由宿主注入），不显示当前文件路径；
 * 文件身份信息由标签行（[EditorTabs]）承载。
 * 更多菜单经 Box 包裹锚定在 ⋮ 按钮正下方（菜单不得移位）。
 *
 * 多级菜单用成熟第三方 [me.saket.cascade.CascadeDropdownMenu]：
 * M3 官方 DropdownMenu 无嵌套子菜单 API（手写嵌套 Popup 会锚定错位）；
 * cascade 自带级联定位（屏幕边缘避让/键盘避让）与条目右箭头、子菜单返回头。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorToolbar(
    title: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRun: () -> Unit,
    // 右上角更多（⋮）多级菜单项：文件树 / 工程信息 / 工程权限 / 打包 APK 等。
    moreActions: List<EditorMoreAction> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    // T19（2026-08-12）：AppBar 与主界面/设置界面实现方式一致
    // （ProjectListScreen 同款），恒用宿主 Material3 surface/onSurface，
    // 不随编辑器主题模式（LIGHT/DARK/FOLLOW）变化；编辑器文本区底色
    // 由主题系统独立提供，二者保持可区分的层次。
    TopAppBar(
        title = {
            Text(
                text = title,
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
            // ⋮ 按钮与菜单必须同处一个 Box：cascade 与 M3 DropdownMenu 同为 Popup，
            // 根菜单锚点 = 其直接父节点；直接写在 actions Row 内会锚定到整行起点导致菜单移位。
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
                CascadeDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    // 多级菜单：按分类分组，一级渲染分类项（cascade 自动带右箭头，
                    // 点按进入二级子菜单，子菜单头显示分类名 + 返回箭头）。
                    // 分类为 null 的项并入上一组（与历史「分类标题插入」语义一致）。
                    val groups = groupByCategory(moreActions)
                    groups.forEach { (category, actions) ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            children = {
                                actions.forEach { action ->
                                    DropdownMenuItem(
                                        text = { Text(action.label) },
                                        enabled = action.enabled,
                                        trailingIcon =
                                            action.checked?.let { checked ->
                                                {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            },
                                        onClick = {
                                            menuExpanded = false
                                            action.onClick()
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        modifier = modifier,
    )
}

/** 按分类把 [moreActions] 分组（保持出现顺序，null 并入上一组）。 */
private fun groupByCategory(actions: List<EditorMoreAction>): List<Pair<String, List<EditorMoreAction>>> {
    if (actions.isEmpty()) return emptyList()
    val result = mutableListOf<Pair<String, MutableList<EditorMoreAction>>>()
    for (action in actions) {
        val category = action.category ?: result.lastOrNull()?.first ?: ""
        if (result.isEmpty() || result.last().first != category) {
            result.add(category to mutableListOf(action))
        } else {
            result.last().second.add(action)
        }
    }
    return result
}
