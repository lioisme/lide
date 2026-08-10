package com.lioisme.lide.editor

import com.lioisme.lide.R

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SelectionMovement

/**
 * 文本操作集合（任务单 G6 + T3）：一次性命令，全部 try/catch(Throwable) 降级。
 *
 * 宿主把本列表并入右上角「更多」多级菜单「编辑」分类（[EditorMoreAction]）；
 * [editorProvider] 惰性获取当前编辑器实例（无实例时操作静默忽略）。
 * 行操作基于 sora 原生 `setSelectionRegion` + `deleteText` / `duplicateLine`，
 * 剪贴板四件套走 sora 原生 `cutText` / `copyText` / `pasteText` / `selectAll`（T3，
 * 对齐 Squircle `EditMenu` 的 Cut/Copy/Paste/Select All 分类），
 * 大小写转换与词级移动对齐 squircle 行为。
 */
@Composable
fun editorTextOpsActions(
    editorProvider: () -> CodeEditor?,
    category: String?,
    /** 编辑能力（只读等态下置灰）：仅影响 剪切/粘贴/删除行/复制行/大小写 五项写操作。 */
    enabled: Boolean = true,
): List<EditorMoreAction> =
    listOf(
        EditorMoreAction(category, stringResource(R.string.menu_cut), enabled = enabled) {
            runEditorOp(editorProvider) { editor -> editor.cutText() }
        },
        EditorMoreAction(category, stringResource(R.string.menu_copy)) {
            runEditorOp(editorProvider) { editor -> editor.copyText() }
        },
        EditorMoreAction(category, stringResource(R.string.menu_paste), enabled = enabled) {
            runEditorOp(editorProvider) { editor -> editor.pasteText() }
        },
        EditorMoreAction(category, stringResource(R.string.menu_select_all)) {
            runEditorOp(editorProvider) { editor -> editor.selectAll() }
        },
        EditorMoreAction(category, stringResource(R.string.text_op_select_line)) {
            runEditorOp(editorProvider) { editor ->
                val line = editor.cursor.leftLine
                editor.setSelectionRegion(line, 0, line, editor.text.getColumnCount(line))
            }
        },
        EditorMoreAction(category, stringResource(R.string.text_op_delete_line), enabled = enabled) {
            runEditorOp(editorProvider) { editor ->
                deleteLine(editor)
            }
        },
        EditorMoreAction(category, stringResource(R.string.text_op_duplicate_line), enabled = enabled) {
            runEditorOp(editorProvider) { editor ->
                editor.duplicateLine()
            }
        },
        EditorMoreAction(category, stringResource(R.string.text_op_toggle_case), enabled = enabled) {
            runEditorOp(editorProvider) { editor ->
                toggleCase(editor)
            }
        },
        EditorMoreAction(category, stringResource(R.string.text_op_word_prev)) {
            runEditorOp(editorProvider) { editor ->
                editor.moveOrExtendSelection(SelectionMovement.PREVIOUS_WORD_BOUNDARY, false)
            }
        },
        EditorMoreAction(category, stringResource(R.string.text_op_word_next)) {
            runEditorOp(editorProvider) { editor ->
                editor.moveOrExtendSelection(SelectionMovement.NEXT_WORD_BOUNDARY, false)
            }
        },
    )

private inline fun runEditorOp(
    editorProvider: () -> CodeEditor?,
    op: (CodeEditor) -> Unit,
) {
    try {
        val editor = editorProvider() ?: return
        op(editor)
    } catch (_: Throwable) {
        // 降级：单次命令失败静默放弃，不逃逸进 UI 线程。
    }
}

/**
 * 删除光标所在整行（含行尾换行符）；末行为空行时无操作
 * （与 sora `cutLine` 的选择边界逻辑一致）。
 */
private fun deleteLine(editor: CodeEditor) {
    val line = editor.cursor.leftLine
    if (line + 1 == editor.lineCount) {
        val columnCount = editor.text.getColumnCount(line)
        if (columnCount == 0) return
        editor.setSelectionRegion(line, 0, line, columnCount)
    } else {
        editor.setSelectionRegion(line, 0, line + 1, 0)
    }
    editor.deleteText()
}

/**
 * 选中区大小写互转；无选中时对光标所在词生效。
 */
private fun toggleCase(editor: CodeEditor) {
    // 只读守卫（审计 R1）：content.replace 直改绕过 sora isEditable 门禁（sora 原生
    // cut/paste/delete/duplicate 均自带门禁，本函数是唯一 content 直写路径）。
    if (!editor.isEditable) return
    val cursor = editor.cursor
    val content = editor.text
    var left = cursor.left()
    var right = cursor.right()
    if (!cursor.isSelected()) {
        // 无选中：扩展为当前词后再转换（与 squircle toggleCase 一致）。
        val lineText = content.getLine(left.line)
        var start = left.column
        var end = left.column
        while (start > 0 && lineText[start - 1].isIdentifierChar()) start--
        while (end < lineText.length && lineText[end].isIdentifierChar()) end++
        left = left.fromThis().apply { column = start }
        right = right.fromThis().apply { column = end }
    }
    val sb = StringBuilder()
    if (left.line == right.line) {
        sb.append(content.getLine(left.line).substring(left.column, right.column))
    } else {
        sb.append(content.getLine(left.line).substring(left.column))
        for (ln in left.line + 1 until right.line) {
            sb.append('\n').append(content.getLine(ln))
        }
        sb.append('\n').append(content.getLine(right.line).substring(0, right.column))
    }
    val toggled = CharArray(sb.length)
    for ((idx, ch) in sb.withIndex()) {
        toggled[idx] =
            when {
                ch.isUpperCase() -> ch.lowercaseChar()
                ch.isLowerCase() -> ch.uppercaseChar()
                else -> ch
            }
    }
    content.replace(left.line, left.column, right.line, right.column, String(toggled))
}
