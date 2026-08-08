package com.lioisme.lide.editor

import android.view.ViewConfiguration
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 原生长按振动修饰符（纯编辑器模块，零宿主依赖）。
 *
 * 与主界面工程列表同源：经 [HapticFeedbackType.LongPress] 走 View 系统
 * `performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)`（原生振动），
 * 禁止自定义 Vibrator 事件。本修饰符**不消费**任何输入事件，可与按钮自身的
 * clickable / combinedClickable 叠加；按住所时长 ≥ 系统长按超时（与原生长按
 * 判定同源）时触发一次振动，短按 / 提前取消不触发。
 */
fun Modifier.pressAndHoldHapticFeedback(): Modifier =
    composed {
        val haptics = LocalHapticFeedback.current
        val view = LocalView.current
        val longPressTimeout =
            remember(view) {
                ViewConfiguration.getLongPressTimeout().toLong()
            }
        pointerInput(longPressTimeout) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val releasedInTime =
                    try {
                        withTimeoutOrNull(longPressTimeout) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                            }
                        } != null
                    } catch (_: CancellationException) {
                        true
                    }
                if (!releasedInTime) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }
    }
