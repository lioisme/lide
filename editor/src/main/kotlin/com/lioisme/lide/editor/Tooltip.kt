package com.lioisme.lide.editor

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 带长按提示（tooltip）的图标按钮。
 *
 * 纯 UI 组件，零宿主依赖（提示文本由调用方注入，不引用任何 R 资源），
 * `:app` 可直接复用（宿主 → 编辑器单向依赖，满足 R4）。
 * 长按按钮即显示 [tooltip] 文本，并触发一次原生长按振动
 * （与主界面工程列表同源：HapticFeedbackType.LongPress → 系统 LONG_PRESS，
 * 非自定义振动事件；M3 1.3.0 TooltipBox 自身不执行 haptic，此处补齐）。
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
    val haptics = LocalHapticFeedback.current
    val tooltipState = rememberTooltipState()
    // 长按展示 tooltip 的同时执行原生振动（isVisible 翻转一次触发一次）。
    LaunchedEffect(tooltipState.isVisible) {
        if (tooltipState.isVisible) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = tooltipState,
        modifier = modifier,
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            content()
        }
    }
}
