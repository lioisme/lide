package com.lioisme.delua.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lioisme.delua.editor.R

/**
 * 工程文件树（纯 Compose，零宿主依赖）。
 *
 * [root] 与展开状态由宿主注入；本组件仅负责展开/折叠与点击回调。
 * 节点路径一律使用绝对路径，选中态按 [selectedPath] 高亮。
 */
data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val children: List<FileNode> = emptyList(),
)

private data class TreeRow(
    val node: FileNode,
    val depth: Int,
)

@Composable
fun ProjectFileTree(
    root: FileNode,
    selectedPath: String?,
    onFileClick: (FileNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    // 目录默认展开；显式折叠过的不再自动展开。节点少，直接遍历生成可见行。
    val rows = buildList {
        fun walk(node: FileNode, depth: Int) {
            val isExpanded = node.isDirectory && expanded.getOrDefault(node.path, true)
            add(TreeRow(node, depth))
            if (isExpanded) {
                node.children.forEach { walk(it, depth + 1) }
            }
        }
        walk(root, 0)
    }

    if (rows.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.file_tree_empty),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState()),
    ) {
        items(rows, key = { it.node.path }) { row ->
            TreeRowItem(
                row = row,
                selected = row.node.path == selectedPath,
                isExpanded = row.node.isDirectory && expanded.getOrDefault(row.node.path, true),
                onClick = {
                    if (row.node.isDirectory) {
                        val current = expanded.getOrDefault(row.node.path, true)
                        expanded[row.node.path] = !current
                    } else {
                        onFileClick(row.node)
                    }
                },
            )
        }
    }
}

@Composable
private fun TreeRowItem(
    row: TreeRow,
    selected: Boolean,
    isExpanded: Boolean,
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
            .widthIn(min = 248.dp)
            .background(background)
            .clickable(onClick = onClick)
            .padding(start = (12 + row.depth * 14).dp, end = 12.dp)
            .padding(vertical = 6.dp),
    ) {
        if (row.node.isDirectory) {
            Icon(
                if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = if (selected) contentColor else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp).padding(start = 2.dp),
            )
        } else {
            Box(modifier = Modifier.size(18.dp))
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp).padding(start = 2.dp),
            )
        }
        Text(
            text = row.node.name,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
        )
    }
}
