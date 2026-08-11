package com.lioisme.lide.editor

import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 编辑器实例命令门面（sora 门面契约，2026-08-11 解耦审计 B 级收口）。
 *
 * 宿主（ui）不得持有 [CodeEditor] 原始引用（此前经 `AtomicReference<CodeEditor?>`
 * 发布，sora 类型泄漏至界面层）；本门面由 [LuaEditor] 绑定/解绑，向宿主暴露最小
 * 命令集：undo / redo / 跳行 / 焦点 / 能力查询 / 文本实体身份判定。
 *
 * 线程契约（对齐 AGENTS.md 编辑器关键约定）：绑定与命令仅在主线程调用
 * （AndroidView update 阶段 / 点击回调）；全部命令经 [runOnEditor] 包裹，
 * `try/catch(Throwable)` 降级，异常不得逃逸进 UI 主循环。
 */
class EditorController {

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

    /** 绑定（[LuaEditor] AndroidView 装配期调用，仅主线程）。 */
    internal fun bind(e: CodeEditor) {
        editor = e
    }

    /** 解绑（[LuaEditor] onDispose 调用，仅主线程）。 */
    internal fun unbind() {
        editor = null
    }

    /** 命令执行壳：未绑定静默跳过；单次命令失败降级，不逃逸。 */
    private inline fun runOnEditor(op: (CodeEditor) -> Unit) {
        val e = editor ?: return
        try {
            op(e)
        } catch (_: Throwable) {
            // 降级：单次命令失败静默放弃（AGENTS.md 编辑器约定）。
        }
    }
}
