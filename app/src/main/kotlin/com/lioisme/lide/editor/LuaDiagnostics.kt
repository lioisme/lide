package com.lioisme.lide.editor

import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.Quickfix
import io.github.rosemoe.sora.text.Content

/**
 * Lua 标识符字符判定（字母/数字/下划线），补全前缀与词法扫描共用。
 */
internal fun Char.isIdentifierChar(): Boolean = isLetterOrDigit() || this == '_'

/**
 * 纯 Kotlin 词法校验器。
 *
 * 用途：编辑器进程内的行级语法诊断（括号配对、块闭合、字符串/注释完整性）。
 * 设计约束：零原生依赖（编辑器进程不加载任何 .so）、单遍 O(n)、无每词字符串分配。
 * 深层次语法（表达式、运算符优先级等）不属于本类职责，由运行时引擎在编译期兜底。
 *
 * 多错误聚合（任务单 G4）：单遍扫描收集全部错误（括号栈 / 块栈 / 字符串模式并行累计），
 * 错误后以「确定性恢复」继续扫描（见 [Incremental.scanLine]），按区域起始排序并去重返回；
 * 不再止步于首个错误。
 *
 * 增量语义（[Incremental]）：以「每行扫描后的解析状态检查点」为基础，编辑后只重扫
 * 受影响区间，状态收敛即停止——对齐 sora `AsyncIncrementalAnalyzeManager` 的 MSG_MOD
 * 思想（只 tokenize 到状态收敛）。
 */
object LuaDiagnostics {
    /**
     * 全量校验（等价于 [Incremental] 的首扫路径）。
     */
    fun checkSyntax(source: String): List<DiagnosticRegion> = Incremental().check(source)

    /**
     * 增量校验器。
     *
     * 算法：每行扫描结束后缓存「模式 + 括号/块栈」检查点。编辑发生时记录受影响的行区间
     * （来自 sora `ContentChangeEvent` 的 changeStart/changeEnd）。下次检查从最早受影响行
     * 复用上一行的检查点续扫；当某行扫描后的状态与该行旧检查点逐值一致（含 Frame 行号），
     * 且已越过本次编辑的最大影响行时，后续内容可判定未变，提前收敛。
     *
     * 正确性保证：
     * - 收敛条件要求逐值一致，插入/删除行导致的行号漂移必然破坏一致性，不会误收敛；
     * - 错误行以确定性恢复态提交检查点（恢复规则纯函数，重扫必得同态），不破坏收敛；
     * - 上一轮报告过错误且错误行早于本次扫描起点时强制全量重扫，前缀错误不会凭空消失；
     * - 上次结果错误行晚于本次编辑终点（[lastErrorMaxLine] 收敛门）时禁止提前收敛，
     *   尾部行局部错误（不匹配右括号 / 多余 end·until）不会因栈值一致而被整批丢弃；
     * - 任何内部异常都降级为全量重扫，增量状态不会毒化后续检查。
     *
     * 线程模型：事件回调（主线程）调用 [onEdit] / [onNewText]，检查协程（后台线程）调用
     * [check]；全部方法同步，检查点一致性受锁保护。
     */
    class Incremental(
        /** 消息渲染器（Q15）：默认中文模板；宿主 UI 注入资源本地化实现（LuaEditor 组合期 remember 构造）。 */
        private val messageProvider: DiagnosticMessageProvider = LuaDiagnostics.DEFAULT_PROVIDER,
    ) {
        /** 栈帧（Q11）：类型化区分括号/块，括号帧直接存字符，免 `"bracket:$c"` 拼接字符串分配。 */
        private sealed interface Frame {
            val line: Int
        }

        /** 括号帧（`(`/`[`/`{`）。 */
        private data class BracketFrame(
            val ch: Char,
            override val line: Int,
        ) : Frame

        /** 块帧（if/for/while/do/function/repeat/ifthen）。 */
        private data class BlockFrame(
            var kind: String,
            override val line: Int,
        ) : Frame

        private sealed interface Mode {
            /** 普通代码。 */
            data object Normal : Mode

            /** 长注释 `--[=[ ... ]=]` 中（跨行）。 */
            data class LongComment(
                val level: Int,
                val startLine: Int,
            ) : Mode

            /** 长字符串 `[=[ ... ]=]` 中（跨行）。 */
            data class LongString(
                val level: Int,
                val startLine: Int,
            ) : Mode

            /** 引号字符串中（跨行，与全量版语义一致）。 */
            data class QuotedString(
                val quote: Char,
                val startLine: Int,
            ) : Mode
        }

        /**
         * 行内收集到的一个错误（[line] 为 1-based 行号）；[fix] 为可选的 quickfix 描述。
         *
         * Q15：错误只携带「消息键 + 参数」（[key]/[args]），文案本地化统一由
         * [DiagnosticMessageProvider] 在渲染层完成（诊断器零资源依赖，可单测）。
         */
        private data class ScanError(
            val line: Int,
            val key: String,
            val args: List<String> = emptyList(),
            val fix: FixSpec? = null,
        )

        /**
         * Quickfix 描述（仅在宿主传入 [Content] 时生成 [Quickfix]，保证诊断纯逻辑零依赖）。
         * 两种形态均相对「错误所在行区域」定位：区域内偏移在行内编辑后仍有较高命中率，
         * 执行时再做预期文本校验兜底，失败则静默放弃（try/catch 降级）。
         */
        private sealed interface FixSpec {
            /** 删除区域起始后 [relStart, relEnd) 区间的 [expected] 文本（删除多余 token）。 */
            data class DeleteToken(
                val relStart: Int,
                val relEnd: Int,
                val expected: String,
            ) : FixSpec

            /** 在区域末尾（行尾）追加 [text]（补闭合符 / end / 引号）。 */
            data class AppendText(
                val text: String,
            ) : FixSpec

            /**
             * 在文件末尾追加 [text]（闭合跨行字符串 / 长注释模式）。
             * 区域定位于起始行，但闭合点应为构造末尾；文件末即构造末尾，
             * 且实时 [Content.length] 恒为最新坐标，无平移失效风险。
             */
            data class AppendAtFileEnd(
                val text: String,
            ) : FixSpec
        }

        /** 第 [line] 行处理完成后的检查点；[nextLineStart] 为下一行的起始偏移。 */
        private class Checkpoint(
            val nextLineStart: Int,
            val mode: Mode,
            val stack: List<Frame>,
        )

        private class ScanResult(
            val mode: Mode,
            val errors: List<ScanError> = emptyList(),
        )

        @Volatile
        private var minEditedLine = Int.MAX_VALUE

        @Volatile
        private var maxEditedLine = Int.MAX_VALUE

        @Volatile
        private var prevFirstErrorLine = -1

        /**
         * 上次结果错误的最大行号（1-based；无错误 = -1）（T1 收敛门）。
         * 收敛条件依赖它：编辑点之后仍存在旧错误时禁止提前收敛（行局部错误——不匹配右括号 /
         * 多余 end·until——不改变解析栈，收敛会发生在编辑点行并把 `[收敛行, EOF]` 的旧错误
         * 整批丢弃），强制扫描至 EOF 重新收集后再收敛。
         */
        @Volatile
        private var lastErrorMaxLine = -1

        private var checkpoints: MutableList<Checkpoint> = ArrayList()

        /**
         * Q11 行起始偏移索引：`lineStarts[i]` = 行 i 的起始偏移（行数 = size）。
         * 增量维护：编辑只影响 `startLine` 及之后的行，前缀 0..startLine 直接复用，
         * 从 startLine 行起始重扫至末尾（全量重扫场景 keep=0 全重建）。
         */
        private var lineStarts: MutableList<Int> = ArrayList()

        /** 上次检查的文本长度：用于发现「未登记编辑的文本变更」（onEdit 未触发时全量重建索引）。 */
        private var lastCheckedLength = -1

        /**
         * 新文本（文件切换 / setText）：清空全部增量状态。
         * T2（2026-08-10）：[lastCheckedLength] 必须一并复位——否则新文本与旧文本字节长度
         * 相同时，[checkInternal] 的索引复用门命中（lineStarts 已清空 → 行数 0 → 提前返回空），
         * 诊断永久缺失直至下一次长度变化的编辑。
         */
        @Synchronized
        fun onNewText() {
            checkpoints = ArrayList()
            lineStarts = ArrayList()
            minEditedLine = Int.MAX_VALUE
            maxEditedLine = Int.MAX_VALUE
            prevFirstErrorLine = -1
            lastErrorMaxLine = -1
            lastCheckedLength = -1
        }

        /**
         * 记录一次编辑影响的行区间（0-based，闭区间）。
         */
        @Synchronized
        fun onEdit(
            startLine: Int,
            endLine: Int,
        ) {
            if (startLine < minEditedLine) minEditedLine = startLine
            if (endLine > maxEditedLine) maxEditedLine = endLine
        }

        /**
         * 对 [source] 执行（增量）校验，返回全部错误（无错误返回空表）。
         * [quickfixSource] 非空时错误附带可执行的 quickfix（内部持有该 [Content] 引用，
         * 点击时按区域实时坐标执行，全部 try/catch 降级）；为 null 时保持纯文本语义
         * （测试与文本级诊断路径），不产生任何 [Quickfix]。
         */
        @Synchronized
        fun check(
            source: String,
            quickfixSource: Content? = null,
        ): List<DiagnosticRegion> =
            try {
                checkInternal(source, quickfixSource)
            } catch (t: Throwable) {
                // 降级：清空增量状态后全量重扫，保证诊断永不因状态损坏而缺失。
                checkpoints = ArrayList()
                prevFirstErrorLine = -1
                lastErrorMaxLine = -1
                try {
                    checkInternal(source, quickfixSource)
                } catch (_: Throwable) {
                    emptyList()
                }
            }

        private fun checkInternal(
            source: String,
            quickfixSource: Content? = null,
        ): List<DiagnosticRegion> {
            val n = source.length
            val minEdited = minEditedLine
            val maxEdited = maxEditedLine
            minEditedLine = Int.MAX_VALUE
            maxEditedLine = Int.MAX_VALUE
            // 行索引增量维护（Q11）：
            // - 已登记编辑：sora 事件保证 startLine 之前的行不变，从编辑起始行重扫至末尾；
            // - 未登记编辑（文本被外部替换但未调 onEdit/onNewText）且长度变化：全量重建；
            // - 无编辑且长度不变（轮询/防抖重查）：完整复用，O(1) 拿到行数。
            val lineCount =
                if (minEdited == Int.MAX_VALUE && n == lastCheckedLength) {
                    lineStarts.size
                } else {
                    buildLineStarts(source, n, if (minEdited == Int.MAX_VALUE) 0 else minEdited)
                }
            lastCheckedLength = n
            if (lineCount == 0) {
                checkpoints = ArrayList()
                prevFirstErrorLine = -1
                lastErrorMaxLine = -1
                return emptyList()
            }
            if (checkpoints.isEmpty()) {
                prevFirstErrorLine = -1
                lastErrorMaxLine = -1
            }

            // 上一次检查报告的错误若在本轮扫描起点之前，强制全量重扫以维持该错误的可见性。
            val requestedStart = if (minEdited == Int.MAX_VALUE) 0 else minEdited.coerceAtMost(lineCount - 1)
            var start = if (prevFirstErrorLine in 0 until requestedStart) 0 else requestedStart
            if (start > 0 && checkpoints.getOrNull(start - 1) == null) start = 0

            var li = start
            var lineStart = if (start == 0) 0 else checkpoints[start - 1].nextLineStart
            var mode: Mode = if (start == 0) Mode.Normal else checkpoints[start - 1].mode
            var stack: MutableList<Frame> =
                if (start == 0) mutableListOf() else checkpoints[start - 1].stack.toMutableList()

            val errors = ArrayList<ScanError>()
            while (li < lineCount) {
                val nl = source.indexOf('\n', lineStart)
                val lineEnd = if (nl < 0) n else nl
                val nextLineStart = if (nl < 0) n else nl + 1

                val result = scanLine(source, lineStart, lineEnd, li + 1, mode, stack)
                if (result.errors.isNotEmpty()) {
                    // 多错误聚合：记录全部行内错误后继续扫描（恢复态确定性，检查点照常提交）。
                    errors.addAll(result.errors)
                }

                val checkpoint = checkpoints.getOrNull(li)
                if (li >= maxEdited && lastErrorMaxLine <= maxEdited && checkpoint != null &&
                    checkpoint.mode == result.mode && checkpoint.stack == stack
                ) {
                    // 收敛：后续内容与检查点一致（行号逐值相同 ⇒ 行号未漂移），提前结束。
                    // T1 门：上次结果在编辑点之后仍有错误时禁止提前收敛——该区间旧错误未被本轮
                    // 重扫，提前返回会把它们整批丢弃（行局部错误不改变解析栈，栈值一致无法辨识）。
                    truncate(li + 1)
                    prevFirstErrorLine = if (errors.isEmpty()) -1 else errors.minOf { it.line }
                    lastErrorMaxLine = if (errors.isEmpty()) -1 else errors.maxOf { it.line }
                    return buildRegions(source, errors, quickfixSource, lineStarts, n)
                }

                val committed = Checkpoint(nextLineStart, result.mode, stack.toList())
                if (li < checkpoints.size) checkpoints[li] = committed else checkpoints.add(committed)
                mode = result.mode
                lineStart = nextLineStart
                li++
            }

            truncate(lineCount)

            // 文件尾部：模式未闭合 + 块栈未闭合全部上报（多错误聚合），
            // 模式（跨行字符串/注释）附「文件末尾补闭合符」fix，块/括号附「行尾补 end/闭合符」fix。
            val modeError: ScanError? =
                when (mode) {
                    is Mode.LongComment -> ScanError(mode.startLine, MSG_UNCLOSED_LONG_COMMENT, fix = FixSpec.AppendAtFileEnd(closeOfLong(mode.level)))
                    is Mode.LongString -> ScanError(mode.startLine, MSG_UNCLOSED_LONG_STRING, fix = FixSpec.AppendAtFileEnd(closeOfLong(mode.level)))
                    is Mode.QuotedString -> ScanError(mode.startLine, MSG_UNCLOSED_QUOTED_STRING, fix = FixSpec.AppendAtFileEnd(mode.quote.toString()))
                    else -> null
                }
            if (modeError != null) errors.add(modeError)
            for (frame in stack) {
                val fix =
                    when (frame) {
                        is BracketFrame -> FixSpec.AppendText(closeOf(frame.ch))
                        is BlockFrame -> FixSpec.AppendText("end")
                    }
                errors.add(ScanError(frame.line, MSG_UNCLOSED_TOKEN, unclosedArgs(frame), fix))
            }
            prevFirstErrorLine = if (errors.isEmpty()) -1 else errors.minOf { it.line }
            lastErrorMaxLine = if (errors.isEmpty()) -1 else errors.maxOf { it.line }
            return buildRegions(source, errors, quickfixSource, lineStarts, n)
        }

        private fun buildRegions(
            source: String,
            errors: List<ScanError>,
            quickfixSource: Content? = null,
            lineStarts: List<Int>,
            sourceLength: Int,
        ): List<DiagnosticRegion> {
            if (errors.isEmpty()) return emptyList()
            // 按区域起始排序 + (起始, 结束, 文案) 去重：多错误聚合的稳定输出面。
            val mapped =
                errors.map { error ->
                    val region = errorAt(lineStarts, sourceLength, error, messageProvider)
                    val fix = error.fix
                    if (quickfixSource != null && fix != null) {
                        region.detail =
                            region.detail?.copy(
                                quickfixes = listOf(buildQuickfix(quickfixSource, region, fix, messageProvider)),
                            )
                    }
                    region
                }
            return mapped
                .sortedBy { it.startIndex }
                .distinctBy { Triple(it.startIndex, it.endIndex, it.detail?.briefMessage) }
        }

        /**
         * 构造 [Quickfix]：捕获实时 [Content] 与 [DiagnosticRegion] 引用（区域坐标由
         * DiagnosticsContainer 在文本变化时原位平移，点击时读取的是当前坐标）。
         * 执行全部 try/catch(Throwable) 降级；[FixSpec.DeleteToken] 先校验预期文本
         * 再删除，避免行内编辑后的错位删除。
         */
        /** 未闭合栈帧的展示参数（[MSG_UNCLOSED_TOKEN] 的 %1$s）。 */
    private fun unclosedArgs(frame: Frame): List<String> =
        when (frame) {
            is BracketFrame -> listOf(frame.ch.toString())
            is BlockFrame -> listOf(frame.kind)
        }

    private fun buildQuickfix(
            content: Content,
            region: DiagnosticRegion,
            spec: FixSpec,
            messageProvider: DiagnosticMessageProvider,
        ): Quickfix {
            val title =
                when (spec) {
                    is FixSpec.DeleteToken -> messageProvider.message(MSG_FIX_DELETE_TOKEN, spec.expected)
                    is FixSpec.AppendText -> messageProvider.message(MSG_FIX_APPEND_LINE_END, spec.text)
                    is FixSpec.AppendAtFileEnd -> messageProvider.message(MSG_FIX_APPEND_FILE_END, spec.text)
                }
            return Quickfix(
                title,
                fixAction =
                    Runnable {
                        try {
                            when (spec) {
                                is FixSpec.DeleteToken -> {
                                    val start = region.startIndex + spec.relStart
                                    val end = region.startIndex + spec.relEnd
                                    if (start in 0..end && end <= content.length &&
                                        content.substring(start, end) == spec.expected
                                    ) {
                                        content.delete(start, end)
                                    }
                                }

                                is FixSpec.AppendText -> {
                                    val at = region.endIndex
                                    if (at in 0..content.length) {
                                        content.replace(at, at, spec.text)
                                    }
                                }

                                is FixSpec.AppendAtFileEnd -> {
                                    val at = content.length
                                    if (at in 0..content.length) {
                                        content.replace(at, at, spec.text)
                                    }
                                }
                            }
                        } catch (_: Throwable) {
                            // 降级：quickfix 失败静默放弃，不逃逸进 UI 线程。
                        }
                    },
            )
        }

        private fun truncate(count: Int) {
            while (checkpoints.size > count) checkpoints.removeAt(checkpoints.size - 1)
        }

        /**
         * 扫描 [start, end) 单行（[lineNo] 为 1-based 行号），就地更新 [stack]，
         * 返回行末模式与行内发现的全部错误。
         *
         * 错误恢复策略（确定性，保证检查点可复现）：
         * - 不匹配的右括号：记录错误，跳过该字符继续；
         * - 多余的 end/until：记录错误，跳过该词继续；
         * - end/until 撞上未闭合括号：记录「未闭合」错误并弹出括号，继续尝试匹配块；
         * - 其余 token 处理不中断，行末状态总是可提交的检查点。
         */
        private fun scanLine(
            source: String,
            start: Int,
            end: Int,
            lineNo: Int,
            initialMode: Mode,
            stack: MutableList<Frame>,
        ): ScanResult {
            var i = start
            var mode = initialMode
            val errors = ArrayList<ScanError>(0)
            // Q11：长模式闭合序列缓存（模式内逐字符迭代复用，免每字符拼接字符串分配）。
            var pendingClose: String? = null
            while (i < end) {
                when (mode) {
                    is Mode.LongComment -> {
                        val close = pendingClose ?: closeOfLong(mode.level).also { pendingClose = it }
                        if (source.startsWith(close, i)) {
                            mode = Mode.Normal
                            pendingClose = null
                            i += close.length
                        } else {
                            i++
                        }
                    }

                    is Mode.LongString -> {
                        val close = pendingClose ?: closeOfLong(mode.level).also { pendingClose = it }
                        if (source.startsWith(close, i)) {
                            mode = Mode.Normal
                            pendingClose = null
                            i += close.length
                        } else {
                            i++
                        }
                    }

                    is Mode.QuotedString -> {
                        pendingClose = null
                        when (source[i]) {
                            '\\' -> {
                                i += 2
                            }

                            mode.quote -> {
                                mode = Mode.Normal
                                i++
                            }

                            else -> {
                                i++
                            }
                        }
                    }

                    Mode.Normal -> {
                        pendingClose = null
                        val c = source[i]
                        when {
                            c == '-' && i + 1 < end && source[i + 1] == '-' -> {
                                i += 2
                                val level = longBracketLevel(source, i, end)
                                if (level >= 0) {
                                    // `--[=[`：同行长注释；跨行则进入 LongComment 模式。
                                    val close = closeOfLong(level)
                                    val found = indexOfWithin(source, close, i, end)
                                    if (found >= 0) {
                                        i = found + close.length
                                    } else {
                                        mode = Mode.LongComment(level, lineNo)
                                        i = end
                                    }
                                } else {
                                    // 行注释：跳过至行尾。
                                    i = end
                                }
                            }

                            c == '\'' || c == '"' -> {
                                val quote = c
                                i++
                                var closed = false
                                while (i < end) {
                                    when (source[i]) {
                                        '\\' -> {
                                            i += 2
                                        }

                                        quote -> {
                                            i++
                                            closed = true
                                            break
                                        }

                                        else -> {
                                            i++
                                        }
                                    }
                                }
                                if (!closed) {
                                    // 跨行字符串：进入 QuotedString 模式，行尾不报错。
                                    mode = Mode.QuotedString(quote, lineNo)
                                    i = end
                                }
                            }

                            c == '[' -> {
                                val level = longBracketLevel(source, i, end)
                                if (level >= 0) {
                                    i += level + 2
                                    val close = closeOfLong(level)
                                    val found = indexOfWithin(source, close, i, end)
                                    if (found >= 0) {
                                        i = found + close.length
                                    } else {
                                        mode = Mode.LongString(level, lineNo)
                                        i = end
                                    }
                                } else {
                                    stack.addLast(BracketFrame('[', lineNo))
                                    i++
                                }
                            }

                            c == '(' || c == '{' -> {
                                stack.addLast(BracketFrame(c, lineNo))
                                i++
                            }

                            c == ')' || c == ']' || c == '}' -> {
                                val want =
                                    when (c) {
                                        ')' -> '('
                                        ']' -> '['
                                        else -> '{'
                                    }
                                val top = stack.lastOrNull()
                                if (top is BracketFrame && top.ch == want) {
                                    stack.removeLast()
                                } else {
                                    val s = c.toString()
                                    errors.add(
                                        ScanError(
                                            lineNo,
                                            MSG_UNMATCHED_CLOSE,
                                            listOf(s),
                                            FixSpec.DeleteToken(i - start, i - start + 1, s),
                                        ),
                                    )
                                }
                                i++
                            }

                            c.isLetter() || c == '_' -> {
                                val wordStart = i
                                while (i < end && source[i].isIdentifierChar()) i++
                                val len = i - wordStart
                                when {
                                    wordIs(source, wordStart, len, "if") -> {
                                        stack.addLast(BlockFrame("if", lineNo))
                                    }

                                    wordIs(source, wordStart, len, "for") -> {
                                        stack.addLast(BlockFrame("for", lineNo))
                                    }

                                    wordIs(source, wordStart, len, "while") -> {
                                        stack.addLast(BlockFrame("while", lineNo))
                                    }

                                    wordIs(source, wordStart, len, "do") -> {
                                        // `for ... do` / `while ... do` 的 do 与 for/while 共享同一个 end，
                                        // 不重复压栈（`while x do ... end` 只有一个 end）；独立的 do 块压栈。
                                        val top = stack.lastOrNull()
                                        if (top !is BlockFrame || (top.kind != "for" && top.kind != "while")) {
                                            stack.addLast(BlockFrame("do", lineNo))
                                        }
                                    }

                                    wordIs(source, wordStart, len, "function") -> {
                                        stack.addLast(BlockFrame("function", lineNo))
                                    }

                                    wordIs(source, wordStart, len, "repeat") -> {
                                        stack.addLast(BlockFrame("repeat", lineNo))
                                    }

                                    wordIs(source, wordStart, len, "then") -> {
                                        val top = stack.lastOrNull()
                                        if (top is BlockFrame && top.kind == "if") top.kind = "ifthen"
                                    }

                                    wordIs(source, wordStart, len, "end") -> {
                                        consumeEnd(
                                            lineNo,
                                            stack,
                                            errors,
                                            BLOCK_KINDS,
                                            "end",
                                            wordStart - start,
                                            i - start,
                                        )
                                    }

                                    wordIs(source, wordStart, len, "until") -> {
                                        consumeEnd(
                                            lineNo,
                                            stack,
                                            errors,
                                            REPEAT_KINDS,
                                            "until",
                                            wordStart - start,
                                            i - start,
                                        )
                                    }
                                }
                            }

                            else -> {
                                i++
                            }
                        }
                    }
                }
            }
            return ScanResult(mode, errors)
        }

        /**
         * 块闭合词（end / until）的确定性恢复：
         * 栈顶为块则弹出；栈顶为未闭合括号则上报（附补闭合符 fix）并弹出（循环），
         * 直到匹配块或栈空；栈空时上报「多余的」并附删除该词的 fix。
         * [expected] 为可闭合该词的块种类集合；[tokenRelStart]/[tokenRelEnd] 为该词
         * 相对行首的偏移（供删除型 fix 使用）。
         */
        private fun consumeEnd(
            lineNo: Int,
            stack: MutableList<Frame>,
            errors: MutableList<ScanError>,
            expected: Set<String>,
            excessToken: String,
            tokenRelStart: Int,
            tokenRelEnd: Int,
        ) {
            while (true) {
                val top = stack.lastOrNull()
                when {
                    top is BlockFrame && top.kind in expected -> {
                        stack.removeLast()
                        break
                    }

                    top is BracketFrame -> {
                        val s = top.ch.toString()
                        errors.add(
                            ScanError(top.line, MSG_UNCLOSED_TOKEN, listOf(s), FixSpec.AppendText(closeOf(top.ch))),
                        )
                        stack.removeLast()
                    }

                    else -> {
                        errors.add(
                            ScanError(
                                lineNo,
                                MSG_EXCESS_TOKEN,
                                listOf(excessToken),
                                FixSpec.DeleteToken(tokenRelStart, tokenRelEnd, excessToken),
                            ),
                        )
                        break
                    }
                }
            }
        }

        private fun wordIs(
            s: String,
            offset: Int,
            len: Int,
            word: String,
        ): Boolean = len == word.length && s.regionMatches(offset, word, 0, word.length)

        /**
         * 返回 [i] 处开始的长括号层级；要求整个 `[=[` 都在 [end) 行内（与 Lua 语义一致，
         * 同时避免跨行扫描把下一行的 `[=[` 误并入上一行注释）。
         */
        private fun longBracketLevel(
            s: String,
            i: Int,
            end: Int,
        ): Int {
            if (i >= end || s[i] != '[') return -1
            var j = i + 1
            while (j < end && s[j] == '=') j++
            return if (j < end && s[j] == '[') j - i - 1 else -1
        }

        private fun indexOfWithin(
            s: String,
            target: String,
            from: Int,
            to: Int,
        ): Int {
            var k = from
            while (k < to) {
                if (s.startsWith(target, k)) return k
                k++
            }
            return -1
        }

        /**
         * 行起始索引增量构建（Q11）：
         * - 编辑不影响 [editedStartLine] 之前的行（sora 事件语义），其行起始偏移直接复用；
         * - 从 editedStartLine 行起始重扫至文本末尾（该行及之后内容可能已变），O(剩余文本)；
         * - 无编辑且长度不变的复用场景由调用方直接取 [lineStarts].size，不经本方法。
         * 返回行数（= lineStarts.size）。
         */
        private fun buildLineStarts(
            s: String,
            n: Int,
            editedStartLine: Int,
        ): Int {
            val cached = lineStarts
            val keep = editedStartLine.coerceAtMost(cached.size)
            while (cached.size > keep + 1) cached.removeAt(cached.size - 1)
            // 保证 cached 含行 keep 起始（keep 之前的行未变，其起始仍有效；keep 行起始也不变）。
            if (cached.isEmpty()) cached.add(0)
            if (cached.size <= keep) {
                val from = cached.last()
                val nl = s.indexOf('\n', from)
                if (nl >= 0) cached.add(nl + 1)
            }
            val scanFrom = cached.getOrNull(keep) ?: return cached.size
            var scan = scanFrom
            while (scan < n) {
                val nl = s.indexOf('\n', scan)
                if (nl < 0) break
                cached.add(nl + 1)
                scan = nl + 1
            }
            return cached.size
        }

        /**
         * 错误区域定位（Q11 优化）：经 [lineStarts] O(1) 查表（原实现每错误从头
         * indexOf 扫行号 = O(E×L)）；文案 = 消息键渲染 + 「第 N 行」附注（[MSG_AT_LINE]）。
         */
        private fun errorAt(
            lineStarts: List<Int>,
            sourceLength: Int,
            error: ScanError,
            messageProvider: DiagnosticMessageProvider,
        ): DiagnosticRegion {
            val line = error.line.coerceAtLeast(1)
            val start = lineStarts.getOrNull(line - 1) ?: 0
            // 行尾 = 下一行起始 - 1（原实现取首个 '\n' 位置）；末行回落文本末尾。
            val end = lineStarts.getOrNull(line)?.minus(1) ?: sourceLength
            val message = messageProvider.message(error.key, *error.args.toTypedArray())
            return DiagnosticRegion(
                start,
                end,
                DiagnosticRegion.SEVERITY_ERROR,
                0L,
                DiagnosticDetail(briefMessage = messageProvider.message(MSG_AT_LINE, message, line)),
            )
        }
    }

    /** 左括号的对应右括号；未知输入原样返回。 */
    private fun closeOf(open: Char): String =
        when (open) {
            '(' -> ")"
            '[' -> "]"
            '{' -> "}"
            else -> open.toString()
        }

    /** 长括号关闭序列：`]` + `=`×[level] + `]`。 */
    private fun closeOfLong(level: Int): String = LONG_CLOSE_PREFIX + "=".repeat(level) + "]"

    private const val LONG_CLOSE_PREFIX = "]"
    private val BLOCK_KINDS = setOf("if", "ifthen", "for", "while", "do", "function")
    private val REPEAT_KINDS = setOf("repeat")

    /**
     * 默认消息渲染器（中文模板，与历史文案一致）：Incremental 缺省注入，
     * 保证测试与纯文本诊断路径无需构造 provider；宿主 UI 经资源驱动覆盖。
     */
    val DEFAULT_PROVIDER =
        object : DiagnosticMessageProvider {
            override fun message(key: String, vararg args: Any): String =
                when (key) {
                    MSG_UNCLOSED_LONG_COMMENT -> "未闭合的长注释"
                    MSG_UNCLOSED_LONG_STRING -> "未闭合的长字符串"
                    MSG_UNCLOSED_QUOTED_STRING -> "未闭合的字符串"
                    MSG_UNMATCHED_CLOSE -> "不匹配的 '${args[0]}'"
                    MSG_UNCLOSED_TOKEN -> "未闭合的 '${args[0]}'"
                    MSG_EXCESS_TOKEN -> "多余的 '${args[0]}'"
                    MSG_AT_LINE -> "${args[0]}（第 ${args[1]} 行）"
                    MSG_FIX_DELETE_TOKEN -> "删除多余的 '${args[0]}'"
                    MSG_FIX_APPEND_LINE_END -> "在行尾补上 '${args[0]}'"
                    MSG_FIX_APPEND_FILE_END -> "在文件末尾补上 '${args[0]}'"
                    else -> key
                }
        }
}

/**
 * 诊断消息渲染器（Q15）：诊断器只产出「消息键 + 参数」，文案本地化在渲染层。
 * 默认实现为中文模板（与历史诊断文案一致，测试依赖）；宿主 UI 注入资源驱动实现。
 */
fun interface DiagnosticMessageProvider {
    /** [key] 为 [LuaDiagnostics] 的消息键常量；[args] 为按序参数（%1$s/%1$d 等）。 */
    fun message(key: String, vararg args: Any): String
}

/** 诊断消息键（Q15）：文案唯一事实源为 strings.xml `diag_*` / `fix_*`，此处键与资源名对应。 */
const val MSG_UNCLOSED_LONG_COMMENT = "unclosed_long_comment"
const val MSG_UNCLOSED_LONG_STRING = "unclosed_long_string"
const val MSG_UNCLOSED_QUOTED_STRING = "unclosed_quoted_string"
const val MSG_UNMATCHED_CLOSE = "unmatched_close"
const val MSG_UNCLOSED_TOKEN = "unclosed_token"
const val MSG_EXCESS_TOKEN = "excess_token"
const val MSG_AT_LINE = "at_line"
const val MSG_FIX_DELETE_TOKEN = "fix_delete_token"
const val MSG_FIX_APPEND_LINE_END = "fix_append_line_end"
const val MSG_FIX_APPEND_FILE_END = "fix_append_file_end"
