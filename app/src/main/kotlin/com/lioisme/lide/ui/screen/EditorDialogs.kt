package com.lioisme.lide.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import com.lioisme.lide.editor.pressAndHoldHapticFeedback
import com.lioisme.lide.engine.EngineResultParser

/** 关闭标签确认对话框（单元素待确认队列）。 */
@Composable
internal fun EditorCloseConfirmDialog(
    fileName: String,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.close_file_title)) },
        text = { Text(stringResource(R.string.close_file_message, fileName)) },
        confirmButton = {
            Row {
                TextButton(
                    onClick = onDiscard,
                    modifier = Modifier.pressAndHoldHapticFeedback(),
                ) {
                    Text(stringResource(R.string.discard))
                }
                TextButton(
                    onClick = onSave,
                    modifier = Modifier.pressAndHoldHapticFeedback(),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.pressAndHoldHapticFeedback(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * 引擎运行错误对话框：失败时展示错误文本 + 跳行 / import 建议。
 * [onJumpToLine] 仅在存在可定位错误位置时展示；[onInsertImport] 仅在识别出
 * nil global 时展示（由调用方以「是否提供回调」表达可用性）。
 */
@Composable
internal fun EditorRunErrorDialog(
    output: String,
    hasLocation: Boolean,
    onJumpToLine: () -> Unit,
    onInsertImport: (String) -> Unit,
    onDismiss: () -> Unit,
    /** 编辑能力（只读等态下插入 import 置灰，防直写绕过，审计 R1）。 */
    editable: Boolean = true,
) {
    val nilGlobal = EngineResultParser.findNilGlobal(output)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.run_error_title)) },
        text = {
            Column {
                Text(
                    output,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                )
                if (nilGlobal != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.run_import_suggestion, nilGlobal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            Row {
                if (hasLocation) {
                    TextButton(
                        onClick = onJumpToLine,
                        modifier = Modifier.pressAndHoldHapticFeedback(),
                    ) {
                        Text(stringResource(R.string.run_jump_to_line))
                    }
                }
                if (nilGlobal != null) {
                    TextButton(
                        onClick = { onInsertImport(nilGlobal) },
                        enabled = editable,
                        modifier = Modifier.pressAndHoldHapticFeedback(),
                    ) {
                        Text(stringResource(R.string.run_insert_import, nilGlobal))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.pressAndHoldHapticFeedback(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * 跳行对话框（P5.2，2026-08-11）：数字输入 + 范围校验（1..[maxLine]）。
 * 确认回调携带 **0 基**行号（对齐 sora `setSelection` 与运行错误跳行的既有语义）。
 * 与 [EditorScreen] 的 pendingJump 消费效应共用同一跳转管线（含编辑器绑定门控）。
 */
@Composable
internal fun GotoLineDialog(
    maxLine: Int,
    onConfirm: (line: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val max = maxLine.coerceAtLeast(1)
    val valid = input.toIntOrNull()?.let { it in 1..max } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goto_line_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { v ->
                    if (v.length <= 9 && v.all(Char::isDigit)) input = v
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.goto_line_hint, max)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { input.toIntOrNull()?.let { onConfirm(it - 1) } },
                modifier = Modifier.pressAndHoldHapticFeedback(),
            ) {
                Text(stringResource(R.string.goto_action))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.pressAndHoldHapticFeedback(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
