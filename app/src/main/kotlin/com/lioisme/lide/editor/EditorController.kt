package com.lioisme.lide.editor

import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

/** 查找匹配类型（P5.1，2026-08-11）：映射 sora `EditorSearcher.SearchOptions.TYPE_*`。 */
enum class SearchMatchType {
    NORMAL,
    WHOLE_WORD,
    REGEX,
}

/** 查找结果快照（主线程读取，后台搜索完成经回调刷新）。 */
data class SearchMatchInfo(
    val count: Int,
    val index: Int,
) {
    companion object {
        val NONE = SearchMatchInfo(0, 0)
    }
}

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
    fun setSelection(
        line: Int,
        column: Int,
    ) {
        runOnEditor { e ->
            val last = (e.text.lineCount - 1).coerceAtLeast(0)
            e.setSelection(line.coerceIn(0, last), column.coerceAtLeast(0))
            e.requestFocus()
        }
    }

    /**
     * 释放编辑器 View 焦点（U2，2026-08-12）：离开编辑器页面前调用，防止 sora
     * 编辑器持有的 View 焦点在导航 pop 销毁时被系统转移、落到新页面首个可聚焦
     * 组件（历史缺陷：焦点落「新建项目」按钮）。仅清焦点不关输入法；未绑定/异常
     * 经 [runOnEditor] 降级为无操作。
     */
    fun releaseFocus() {
        runOnEditor { it.clearFocus() }
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
     *
     * U6（2026-08-12）：**视口恢复**——替换前快照 `getFirstVisibleLine()`，
     * 替换后 `setSelection(..., makeItVisible=false)` 不强制滚动到光标行（否则
     * 光标在首/末行时等价「跳顶/跳底」），再 `ensurePositionVisible(firstVisible,
     * 0)` 使格式化前的浏览位置重新可见；行结构变化后夹取到新行数边界。
     */
    fun format(
        useTab: Boolean,
        tabSize: Int,
        onResult: (changed: Boolean) -> Unit = {},
    ) {
        val e = editor ?: return
        val text =
            try {
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
                    // U6（2026-08-12）：视口快照——格式化后恢复浏览位置（不跳顶/不跳底/不跳光标行）。
                    val firstVisible = ed.getFirstVisibleLine()
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
                    // makeItVisible=false：不得强制滚动到光标行（格式化前光标在视口外
                    // 时，若强制滚动等于把用户浏览位置拽走）；随后视口按快照行恢复。
                    ed.setSelection(restoredLine, restoredCol, false)
                    // 视口恢复：使格式化前的视口首行重新可见（行级近似，行结构变化后
                    // 夹取到新边界）。光标若原本在视口外，保持用户浏览位置优先。
                    val restoredFirst = firstVisible.coerceIn(0, (ed.text.lineCount - 1).coerceAtLeast(0))
                    ed.ensurePositionVisible(restoredFirst, 0, true)
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
     * 页面级作用域释放（T5E，2026-08-12）：仅由 EditorScreen 离场（onDispose / 返回）
     * 调用，取消仍在途的格式化任务。**不得**与 [unbind] 绑定——LuaEditor 随标签切换
     * dispose/recreate，若随解绑取消，格式化功能将永久瘫痪；也不得由 LuaEditor
     * 持有调用。
     */
    fun disposeScope() {
        scope.cancel()
    }

    // ---- 查找/替换（P5.1，2026-08-11）----
    // 搜索结果 sora 在后台线程计算，完成时经 PublishSearchResultEvent 通知
    // （LuaEditor 订阅 → onSearchResultPublished → 宿主刷新 SearchMatchInfo）。

    /** 发起搜索（空/未绑定返回 false；正则非法在 sora 侧预检抛异常 → 降级 false）。 */
    fun search(
        pattern: String,
        type: SearchMatchType,
        caseInsensitive: Boolean,
    ): Boolean {
        val e = editor ?: return false
        return try {
            val soraType =
                when (type) {
                    SearchMatchType.NORMAL -> EditorSearcher.SearchOptions.TYPE_NORMAL
                    SearchMatchType.WHOLE_WORD -> EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
                    SearchMatchType.REGEX -> EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
                }
            e.getSearcher().search(pattern, EditorSearcher.SearchOptions(soraType, caseInsensitive))
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** 停止搜索（清高亮与结果；未绑定/异常降级）。 */
    fun stopSearch() {
        runOnEditor { it.getSearcher().stopSearch() }
    }

    /** 结果快照（count / 当前命中序号，0 基；无查询/未就绪 = NONE）。 */
    fun searchMatchInfo(): SearchMatchInfo {
        val e = editor ?: return SearchMatchInfo.NONE
        return try {
            val searcher = e.getSearcher()
            SearchMatchInfo(searcher.getMatchedPositionCount(), searcher.getCurrentMatchedPositionIndex())
        } catch (_: Throwable) {
            SearchMatchInfo.NONE
        }
    }

    /** 下一个命中（未绑定/无结果 false）。 */
    fun searchNext(): Boolean {
        val e = editor ?: return false
        return try {
            e.getSearcher().gotoNext()
        } catch (_: Throwable) {
            false
        }
    }

    /** 上一个命中（语义同 [searchNext]）。 */
    fun searchPrevious(): Boolean {
        val e = editor ?: return false
        return try {
            e.getSearcher().gotoPrevious()
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 替换当前命中（sora 内置 isEditable 门禁，只读下静默无操作；UI 层另按
     * [EditorConfig.readOnly] 置灰按钮，双保险）。
     */
    fun replaceCurrent(replacement: String) {
        runOnEditor { it.getSearcher().replaceCurrentMatch(replacement) }
    }

    /**
     * 全部替换（P5.1，2026-08-11 收口）：sora 自带 [EditorSearcher.replaceAll] 内部
     * 弹 `ProgressDialog`，在 Compose AndroidView 上下文（`ContextThemeWrapper`，无
     * window token）下抛 `BadTokenException`——故弃用，自实现同语义替换（对齐 sora
     * 结果语义：NORMAL = 字面 / WHOLE_WORD = `\b` 包裹 / REGEX = 正则，flags 含
     * CASE_INSENSITIVE + MULTILINE），从后往前改写区间后单次 [Content.replace] 提交
     * （单条 undo 记录）。只读门禁 sora 同款（[CodeEditor.isEditable]）。
     * H1（2026-08-12）：sora `Cursor.afterInsert` 语义下，在 0 处整文替换光标必然
     * 右移到全文末尾——替换前记录光标、替换后按 format 同款行列双夹取恢复
     * （`Cursor.afterInsert` 的 index ≥ 插入点整体右移行为见 sora Cursor.java）。
     * 完成后回调经主线程执行。
     */
    fun replaceAll(
        pattern: String,
        replacement: String,
        type: SearchMatchType,
        caseInsensitive: Boolean,
        whenSucceeded: () -> Unit = {},
    ) {
        val e = editor ?: return
        try {
            if (!e.isEditable) return
            if (pattern.isEmpty()) return
            val text = e.text.toString()
            val flags = (if (caseInsensitive) Pattern.CASE_INSENSITIVE else 0) or Pattern.MULTILINE
            val javaPattern =
                when (type) {
                    SearchMatchType.NORMAL -> Pattern.quote(pattern)
                    SearchMatchType.WHOLE_WORD -> "\\b" + Pattern.quote(pattern) + "\\b"
                    SearchMatchType.REGEX -> pattern
                }
            val matcher = Pattern.compile(javaPattern, flags).matcher(text)
            val ranges = ArrayList<Pair<Int, Int>>()
            while (matcher.find()) {
                ranges.add(matcher.start() to matcher.end())
            }
            if (ranges.isEmpty()) return
            val sb = StringBuilder(text)
            for (i in ranges.indices.reversed()) {
                sb.replace(ranges[i].first, ranges[i].second, replacement)
            }
            val newText = sb.toString()
            if (newText == text) return
            val curLine = e.cursor.getLeftLine()
            val curColumn = e.cursor.getLeftColumn()
            // U6（2026-08-12）：视口快照——替换后恢复浏览位置（与 format 同款）。
            val firstVisible = e.getFirstVisibleLine()
            val lastLine = e.lineCount - 1
            e.text.replace(0, 0, lastLine, e.text.getColumnCount(lastLine), newText)
            // 光标恢复：行列双夹取（对齐 format 的 L3 模式），越过界回落到有效边界。
            val restoredLine = curLine.coerceIn(0, (e.lineCount - 1).coerceAtLeast(0))
            val restoredCol = curColumn.coerceIn(0, e.text.getColumnCount(restoredLine))
            // makeItVisible=false + 视口按快照行恢复（同 format U6 语义）。
            e.setSelection(restoredLine, restoredCol, false)
            val restoredFirst = firstVisible.coerceIn(0, (e.lineCount - 1).coerceAtLeast(0))
            e.ensurePositionVisible(restoredFirst, 0, true)
            whenSucceeded()
        } catch (_: Throwable) {
            // 降级：单次命令失败静默放弃（正则非法等）。
        }
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
