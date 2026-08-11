package com.lioisme.lide.editor

import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 编辑器实例命令门面（sora 门面契约，2026-08-11 解耦审计 B 级收口）。
 *
 * 宿主（ui）不得持有 [CodeEditor] 原始引用（此前经 `AtomicReference<CodeEditor?>`
 * 发布，sora 类型泄漏至界面层）；本门面由 [LuaEditor] 绑定/解绑，向宿主暴露最小
 * 命令集：undo / redo / 跳行 / 焦点 / 能力查询 / 文本实体身份判定 / 格式化。
 *
 * 线程契约（对齐 AGENTS.md 编辑器关键约定）：绑定与命令仅在主线程调用
 * （AndroidView update 阶段 / 点击回调）；全部命令经 [runOnEditor] 包裹，
 * `try/catch(Throwable)` 降级，异常不得逃逸进 UI 主循环。
 */
class EditorController {

    /** 格式化协程作用域：后台计算 + 主线程替换（无格式化任务时零开销）。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 当前绑定的编辑器实例（仅 [LuaEditor] 装配/释放期写入，主线程）。 */
    internal var editor: CodeEditor? = null
        private set

    /** 是否已绑定编辑器实例。 */
    fun isBound(): Boolean = editor != null

    /** 绑定的编辑器当前是否正在展示 [content]（引用身份判定，跳行等绑定前置）。 */
    fun isShowing(content: EditorContent): Boolean {
        val e = editor ?: return false
        return e.text === content.raw
    }

    /** 绑定的编辑器文本行数（跳行夹取上限；未绑定为 0）。 */
    fun lineCount(): Int {
        val e = editor ?: return 0
        return e.text.lineCount
    }

    /** 定位到指定行（0 基，内部夹取到有效区间）并请求焦点。 */
    fun setSelection(line: Int, column: Int) {
        runOnEditor { e ->
            val last = (e.text.lineCount - 1).coerceAtLeast(0)
            e.setSelection(line.coerceIn(0, last), column.coerceAtLeast(0))
            e.requestFocus()
        }
    }

    /** 撤销（只读守卫由调用方按 [EditorConfig.readOnly] 前置拦截）。 */
    fun undo() {
        runOnEditor { it.undo() }
    }

    /** 重做（守卫语义同 [undo]）。 */
    fun redo() {
        runOnEditor { it.redo() }
    }

    /** 撤销可用性（未绑定回落 false）。 */
    fun canUndo(): Boolean {
        val e = editor ?: return false
        return e.canUndo()
    }

    /** 重做可用性（未绑定回落 false）。 */
    fun canRedo(): Boolean {
        val e = editor ?: return false
        return e.canRedo()
    }

    /**
     * 全文档格式化（T14，2026-08-11）：后台线程经 [LuaFormatter] 计算（大文件短路），
     * 主线程 `beginBatchEdit` + 全量 [Content.replace] 一次应用（单组撤销），
     * try/catch 降级。文本变化经 sora 事件管线驱动会话脏标记/自动保存节流，
     * 宿主无需额外同步。[onResult] 主线程回调：true = 已替换；false = 未绑定 /
     * 计算失败 / 无文本变化 / 文本一致性守卫未过（宿主据此展示「格式化完成」
     * Snackbar——`changed=false` 时不展示）。
     *
     * L3（2026-08-12）双修复：
     * - **光标恢复**：sora `Cursor.afterInsert` 对「光标位于插入点之后或重合」
     *   的语义是右移 `insertedContent.length()`（Cursor.java:338-341），全量
     *   `replace(0,0,..末行末列)` 后光标必然落在全文末尾——替换前记录光标、
     *   替换后 `setSelection` 行列双夹取恢复（对齐 sora 官方
     *   `CodeEditor.onFormatSucceed` 的 `setSelectionAround` 语义）；
     * - **一致性守卫**：后台计算期间用户可能继续输入，替换前校验
     *   `text.toString() == 快照`，不一致即放弃（防止覆盖计算期间的新输入）。
     */
    fun format(
        useTab: Boolean,
        tabSize: Int,
        onResult: (changed: Boolean) -> Unit = {},
    ) {
        val e = editor ?: return
        val text = try {
            e.text.toString()
        } catch (_: Throwable) {
            return
        }
        scope.launch {
            val formatted =
                withContext(Dispatchers.Default) {
                    LuaFormatter.format(text, useTab, tabSize)
                }
            if (formatted == text) {
                onResult(false)
                return@launch
            }
            val applied =
                runOnEditor { ed ->
                    // 一致性守卫：快照后文本已被用户编辑 → 放弃本格式化（防覆盖）。
                    if (ed.text.toString() != text) {
                        return@runOnEditor false
                    }
                    val curLine = ed.cursor.getLeftLine()
                    val curColumn = ed.cursor.getLeftColumn()
                    val lastLine = (ed.text.lineCount - 1).coerceAtLeast(0)
                    val lastCol = ed.text.getColumnCount(lastLine)
                    ed.text.beginBatchEdit()
                    try {
                        ed.text.replace(0, 0, lastLine, lastCol, formatted)
                    } finally {
                        ed.text.endBatchEdit()
                    }
                    // 光标恢复：行列双夹取（行 → 新 lineCount-1 内；列 → 新行
                    // 列宽内），越过界回落到有效边界而不抛。
                    val restoredLine = curLine.coerceIn(0, (ed.text.lineCount - 1).coerceAtLeast(0))
                    val restoredCol = curColumn.coerceIn(0, ed.text.getColumnCount(restoredLine))
                    ed.setSelection(restoredLine, restoredCol, true)
                    true
                }
            onResult(applied == true)
        }
    }

    /** 绑定（[LuaEditor] AndroidView 装配期调用，仅主线程）。 */
    internal fun bind(e: CodeEditor) {
        editor = e
    }

    /** 解绑（[LuaEditor] onDispose 调用，仅主线程）。 */
    internal fun unbind() {
        editor = null
    }

    /**
     * 命令执行壳：未绑定返回 null；单次命令失败降级（返回 null），不逃逸。
     * 泛型返回值供 [format] 上报「是否实际替换」（守卫未过 / 异常 = false）。
     * 既有命令调用忽略返回值（Kotlin 表达式语句语义）。
     */
    private inline fun <T> runOnEditor(op: (CodeEditor) -> T): T? {
        val e = editor ?: return null
        return try {
            op(e)
        } catch (_: Throwable) {
            // 降级：单次命令失败静默放弃（AGENTS.md 编辑器约定）。
            null
        }
    }
}
