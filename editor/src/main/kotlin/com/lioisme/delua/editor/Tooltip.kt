package com.lioisme.delua.editor

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 带长按提示（tooltip）的图标按钮。
 *
 * 纯 UI 组件，零宿主依赖（提示文本由调用方注入，不引用任何 R 资源），
 * `:app` 可直接复用（宿主 → 编辑器单向依赖，满足 R4）。
 * 长按按钮即显示 [tooltip] 文本。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipIconButton(
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            content()
        }
    }
}
