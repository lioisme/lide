package com.lioisme.delua.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lioisme.delua.editor.R

/**
 * 目录条目（绝对路径 + 名称 + 类型）。供宿主文件浏览与树渲染共用。
 */
data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val children: List<FileNode> = emptyList(),
)

/**
 * 工程文件浏览器（文件系统风格，纯 Compose，零宿主依赖）。
 *
 * 展示当前目录条目：目录在前、按名排序；目录点击回调上级（宿主负责导航），
 * 文件点击回调宿主打开。选中态按 [selectedPath] 高亮。
 *
 * [onRefresh] 非空时启用下拉刷新（触发宿主重扫当前目录）；为空则保持纯展示组件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowser(
    entries: List<FileNode>,
    selectedPath: String?,
    onOpenDirectory: (FileNode) -> Unit,
    onOpenFile: (FileNode) -> Unit,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    val sorted = remember(entries) {
        entries.sortedWith(
            compareByDescending<FileNode> { it.isDirectory }
                .thenBy { it.name.lowercase() }
        )
    }

    val content: @Composable () -> Unit = {
        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.file_tree_empty),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(min = 300.dp),
            ) {
                items(sorted, key = { it.path }) { entry ->
                    FileBrowserRow(
                        entry = entry,
                        selected = !entry.isDirectory && entry.path == selectedPath,
                        onClick = {
                            if (entry.isDirectory) onOpenDirectory(entry) else onOpenFile(entry)
                        },
                    )
                }
            }
        }
    }

    if (onRefresh != null) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier
                .fillMaxSize()
                .widthIn(min = 300.dp),
        ) { content() }
    } else {
        content()
    }
}

@Composable
private fun FileBrowserRow(
    entry: FileNode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Description,
            contentDescription = null,
            tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        )
    }
}
