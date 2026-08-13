package com.lioisme.lide.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lioisme.lide.R

/**
 * 底部输入对话框（共享组件，2026-08-12 U3/U5）：新建项目 / 工程重命名 /
 * 跳转到行等确认类输入场景共用。
 *
 * 相对居中 [androidx.compose.material3.AlertDialog] 的差异：自动聚焦输入框
 * （键盘随 sheet 弹出）、IME 动作 [ImeAction.Done] 直接提交（回车 = 确认）、
 * 底部 padding 与导航栏安全区由 [ModalBottomSheet] 自带。状态全部由调用方
 * 持有（无内部 state），旋转由调用方 `rememberSaveable` 保证。
 *
 * 键盘同步（2026-08-12 初版 + T9/T10 2026-08-13 最终收口，SQUIRCLE_FOUR_FIX 需求④）：
 * [ModalBottomSheet]（material3 1.4.0）的弹层是**独立 Dialog 窗口**——主窗口组合的
 * `LocalSoftwareKeyboardController` 对子窗口内的焦点 TextField 无挂靠点（跨窗口 show()
 * 落空），且 M3 的 `ModalBottomSheetDialogWrapper.updateParameters` 强制 Dialog 窗口
 * `SOFT_INPUT_STATE_ALWAYS_HIDDEN`（SDK>=30 时 `setSoftInputMode(48)`），Compose
 * show()（SHOW_IMPLICIT）被静默拒绝（IME 窗口 0 高、键盘永不展开）。
 * 修订：键盘控制器在 sheet content（Dialog 窗口）组合内重新获取，焦点请求轮询重试
 * （`onFocusChanged` 观察 isFocused 真正落定后再触发键盘），随后
 * `InputMethodManager.showSoftInput(SHOW_FORCED)` 无视 ALWAYS_HIDDEN 强制弹出
 * （连试两拍 + Compose show + WindowInsetsControllerCompat 兜底）。实测通过：
 * IME 窗口全高展开、数字键直接上屏、跳转执行正常。
 *
 * @param label 输入框标签（null 不展示；与 [placeholder] 可同时存在）
 * @param hintText 标题下方的可选说明文字（如重命名指引），null 不展示
 * @param placeholder 输入框占位提示（如跳行范围），null 不展示
 * @param confirmEnabled 确认按钮可用性（空值/非法输入禁用）
 * @param keyboardType 输入法键盘类型（默认文本；跳行等数字场景传 Number）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BottomInputSheet(
    title: String,
    label: String? = null,
    hintText: String? = null,
    placeholder: String? = null,
    value: String,
    confirmEnabled: Boolean,
    confirmText: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        // T6（2026-08-13，SQUIRCLE_FOUR_FIX 需求④复核）：焦点/键盘逻辑必须整体
        // 位于 sheet content（Dialog 独立窗口 = 独立 FocusOwner）——
        // 主窗口组合创建的 FocusRequester 对 Dialog 窗口内的 TextField 跨
        // FocusOwner requestFocus() 失效（实测 isFocused 恒 false → 键盘不弹）；
        // 键盘控制器同理须取 Dialog 窗口自己的实例。
        val innerRequest = remember { FocusRequester() }
        val dialogKeyboard = LocalSoftwareKeyboardController.current
        var inputFocused by remember { mutableStateOf(false) }
        var keyboardShown by remember { mutableStateOf(false) }
        // 焦点请求带重试：Dialog 窗口 window focus 异步就绪，首帧 requestFocus
        // 可能被丢弃——轮询直到焦点真正落到输入框（onFocusChanged 回写）。
        LaunchedEffect(Unit) {
            // 不调 sheetState.show()：ModalBottomSheet 组合时内部自驱动入场动画
            // （mutator 互斥），content 内重复 show() 与内部驱动力竞争导致
            // 永久挂起（实测 logcat 卡在 before/after 之间）。
            // content 组合发生即窗口已创建，直接重试焦点请求。
            repeat(10) {
                if (inputFocused) return@LaunchedEffect
                innerRequest.requestFocus()
                kotlinx.coroutines.delay(100)
            }
        }
        // 焦点落定（isFocused=true）后再调出 IME：IME session（Dialog 窗口
        // input connection）异步建立，延迟一拍再 show 才挂靠成功。
        val sheetView = LocalView.current
        LaunchedEffect(keyboardShown) {
            if (!keyboardShown) return@LaunchedEffect
            kotlinx.coroutines.delay(80)
            // 2026-08-13 收口（SQUIRCLE_FOUR_FIX 需求④）：M3 1.4.0
            // ModalBottomSheetDialogWrapper.updateParameters 对 Dialog 窗口强制
            // SOFTWARE_INPUT_STATE_ALWAYS_HIDDEN（SDK>=30 时 setSoftInputMode(48)）
            // → Compose show()（SHOW_IMPLICIT）被窗口属性静默拒绝（实测 IME
            // 窗口 0 高、键盘永不展开）。SHOW_FORCED 语义上无视该隐藏策略，
            // 连试两拍（首拍可能早于窗口焦点完全稳定）+ Compose show 兜底。
            val imm = sheetView.context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            repeat(2) {
                imm?.showSoftInput(sheetView, android.view.inputmethod.InputMethodManager.SHOW_FORCED)
                kotlinx.coroutines.delay(120)
            }
            dialogKeyboard?.show()
            if (context is androidx.activity.ComponentActivity) {
                WindowInsetsControllerCompat(context.window, context.window.decorView)
                    .show(WindowInsetsCompat.Type.ime())
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (hintText != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    hintText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = label?.let { { Text(it) } },
                placeholder = placeholder?.let { { Text(it) } },
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .focusRequester(innerRequest)
                        .onFocusChanged {
                            inputFocused = it.isFocused
                            if (it.isFocused) keyboardShown = true
                        },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm() }),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                ) {
                    Text(confirmText)
                }
            }
        }
    }
}
