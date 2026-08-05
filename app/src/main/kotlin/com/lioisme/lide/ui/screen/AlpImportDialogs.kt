package com.lioisme.lide.ui.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lioisme.lide.R

/**
 * .alp 导入同名冲突确认（WP-015）：覆盖 / 改名 / 取消 三选。
 * 展示由调用方持有（plan 无副作用解析结果），选择后由调用方执行对应模式导入。
 */
@Composable
internal fun AlpImportConflictDialog(
    name: String,
    onOverwrite: () -> Unit,
    onRename: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.alp_conflict_title)) },
        text = { Text(stringResource(R.string.alp_conflict_message, name)) },
        confirmButton = {
            Row {
                TextButton(onClick = onRename) {
                    Text(stringResource(R.string.alp_conflict_rename))
                }
                TextButton(onClick = onOverwrite) {
                    Text(stringResource(R.string.alp_conflict_overwrite))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
