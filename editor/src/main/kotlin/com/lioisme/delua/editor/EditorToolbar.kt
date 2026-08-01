package com.lioisme.delua.editor

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.lioisme.delua.editor.R

/** 右上角更多菜单项（纯 UI 数据：标签 + 点击回调）。 */
data class EditorMoreAction(
    val label: String,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorToolbar(
    fileName: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onOpenFileTree: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRun: () -> Unit,
    onSearch: () -> Unit = {},
    // 右上角更多（⋮）菜单：工程属性/工程权限/打包 APK 等宿主动作入口。
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
            Row {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                    )
                }
                IconButton(onClick = onOpenFileTree) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = stringResource(R.string.cd_open_file_tree),
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.cd_undo))
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.cd_redo))
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search))
            }
            IconButton(onClick = onRun) {
                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.cd_run))
            }
            if (moreActions.isNotEmpty()) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    moreActions.forEach { action ->
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
