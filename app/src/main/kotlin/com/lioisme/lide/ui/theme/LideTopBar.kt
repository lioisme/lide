package com.lioisme.lide.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lioisme.lide.R
import com.lioisme.lide.editor.TipIconButton

/**
 * 共享次级页顶栏（架构审计 B-2）：返回键 + 标题 + 标准配色。
 * 设计规则（2026-08）：禁止在标题前显示图标（icon 参数已移除）。
 * 消除 ~13 处「TopAppBar + 返回键 + colors 样板」的逐屏复制。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LideTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            TipIconButton(tooltip = stringResource(R.string.cd_back), onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
        },
        actions = { actions() },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
    )
}
