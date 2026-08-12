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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
 * 键盘同步（U5，2026-08-12）：焦点请求在 sheet 入场动画**完成**后发出——
 * `sheetState.show()` 挂起至动画结束。动画期间 Dialog 窗口焦点树未就绪，
 * 立即 `requestFocus()` 在部分设备/OEM 上落空，表现为「先弹框后弹键盘」。
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
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        sheetState.show()
        focusRequester.requestFocus()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
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
                        .focusRequester(focusRequester),
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
