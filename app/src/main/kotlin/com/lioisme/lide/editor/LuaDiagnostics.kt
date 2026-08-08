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
     * - 任何内部异常都降级为全量重扫，增量状态不会毒化后续检查。
     *
     * 线程模型：事件回调（主线程）调用 [onEdit] / [onNewText]，检查协程（后台线程）调用
     * [check]；全部方法同步，检查点一致性受锁保护。
     */
    class Incremental {
        /** 单行解析状态：模式 + 括号/块栈。 */
        private data class Frame(
            var kind: String,
            val line: Int,
        )

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

        /** 行内收集到的一个错误（[line] 为 1-based 行号）；[fix] 为可选的 quickfix 描述。 */
        private data class ScanError(
            val line: Int,
            val message: String,
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

        private var checkpoints: MutableList<Checkpoint> = ArrayList()

        /**
         * 新文本（文件切换 / setText）：清空全部增量状态。
         */
        @Synchronized
        fun onNewText() {
            checkpoints = ArrayList()
            minEditedLine = Int.MAX_VALUE
            maxEditedLine = Int.MAX_VALUE
            prevFirstErrorLine = -1
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
            val lineCount = countLines(source, n)
            val minEdited = minEditedLine
            val maxEdited = maxEditedLine
            minEditedLine = Int.MAX_VALUE
            maxEditedLine = Int.MAX_VALUE
            if (lineCount == 0) {
                checkpoints = ArrayList()
                prevFirstErrorLine = -1
                return emptyList()
            }
            if (checkpoints.isEmpty()) {
                prevFirstErrorLine = -1
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
                if (li >= maxEdited && checkpoint != null &&
                    checkpoint.mode == result.mode && checkpoint.stack == stack
                ) {
                    // 收敛：后续内容与检查点一致（行号逐值相同 ⇒ 行号未漂移），提前结束。
                    truncate(li + 1)
                    prevFirstErrorLine = if (errors.isEmpty()) -1 else errors.minOf { it.line }
                    return buildRegions(source, errors, quickfixSource)
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
                    is Mode.LongComment -> ScanError(mode.startLine, "未闭合的长注释", FixSpec.AppendAtFileEnd(closeOfLong(mode.level)))
                    is Mode.LongString -> ScanError(mode.startLine, "未闭合的长字符串", FixSpec.AppendAtFileEnd(closeOfLong(mode.level)))
                    is Mode.QuotedString -> ScanError(mode.startLine, "未闭合的字符串", FixSpec.AppendAtFileEnd(mode.quote.toString()))
                    else -> null
                }
            if (modeError != null) errors.add(modeError)
            for (frame in stack) {
                val fix =
                    if (frame.kind.startsWith("bracket:")) {
                        FixSpec.AppendText(closeOf(frame.kind.removePrefix("bracket:")))
                    } else {
                        FixSpec.AppendText("end")
                    }
                errors.add(ScanError(frame.line, unclosedMessage(frame.kind), fix))
            }
            prevFirstErrorLine = if (errors.isEmpty()) -1 else errors.minOf { it.line }
            return buildRegions(source, errors, quickfixSource)
        }

        private fun buildRegions(
            source: String,
            errors: List<ScanError>,
            quickfixSource: Content? = null,
        ): List<DiagnosticRegion> {
            if (errors.isEmpty()) return emptyList()
            // 按区域起始排序 + (起始, 结束, 文案) 去重：多错误聚合的稳定输出面。
            val mapped =
                errors.map { error ->
                    val region = errorAt(source, error.line, error.message)
                    val fix = error.fix
                    if (quickfixSource != null && fix != null) {
                        region.detail =
                            region.detail?.copy(
                                quickfixes = listOf(buildQuickfix(quickfixSource, region, fix)),
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
        private fun buildQuickfix(
            content: Content,
            region: DiagnosticRegion,
            spec: FixSpec,
        ): Quickfix {
            val title =
                when (spec) {
                    is FixSpec.DeleteToken -> "删除多余的 '${spec.expected}'"
                    is FixSpec.AppendText -> "在行尾补上 '${spec.text}'"
                    is FixSpec.AppendAtFileEnd -> "在文件末尾补上 '${spec.text}'"
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
            while (i < end) {
                when (mode) {
                    is Mode.LongComment -> {
                        val close = LONG_CLOSE_PREFIX + "=".repeat(mode.level) + "]"
                        if (source.startsWith(close, i)) {
                            mode = Mode.Normal
                            i += close.length
                        } else {
                            i++
                        }
                    }

                    is Mode.LongString -> {
                        val close = LONG_CLOSE_PREFIX + "=".repeat(mode.level) + "]"
                        if (source.startsWith(close, i)) {
                            mode = Mode.Normal
                            i += close.length
                        } else {
                            i++
                        }
                    }

                    is Mode.QuotedString -> {
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
                        val c = source[i]
                        when {
                            c == '-' && i + 1 < end && source[i + 1] == '-' -> {
                                i += 2
                                val level = longBracketLevel(source, i, end)
                                if (level >= 0) {
                                    // `--[=[`：同行长注释；跨行则进入 LongComment 模式。
                                    val close = LONG_CLOSE_PREFIX + "=".repeat(level) + "]"
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
                                    val close = LONG_CLOSE_PREFIX + "=".repeat(level) + "]"
                                    val found = indexOfWithin(source, close, i, end)
                                    if (found >= 0) {
                                        i = found + close.length
                                    } else {
                                        mode = Mode.LongString(level, lineNo)
                                        i = end
                                    }
                                } else {
                                    stack.addLast(Frame("bracket:[", lineNo))
                                    i++
                                }
                            }

                            c == '(' || c == '{' -> {
                                stack.addLast(Frame("bracket:$c", lineNo))
                                i++
                            }

                            c == ')' || c == ']' || c == '}' -> {
                                val want =
                                    when (c) {
                                        ')' -> '('
                                        ']' -> '['
                                        else -> '{'
                                    }
                                if (stack.isNotEmpty() && stack.last().kind == "bracket:$want") {
                                    stack.removeLast()
                                } else {
                                    errors.add(ScanError(lineNo, "不匹配的 '$c'", FixSpec.DeleteToken(i - start, i - start + 1, c.toString())))
                                }
                                i++
                            }

                            c.isLetter() || c == '_' -> {
                                val wordStart = i
                                while (i < end && source[i].isIdentifierChar()) i++
                                val len = i - wordStart
                                when {
                                    wordIs(source, wordStart, len, "if") -> {
                                        stack.addLast(Frame("if", lineNo))
                                    }

                                    wordIs(source, wordStart, len, "for") -> {
                                        stack.addLast(Frame("for", lineNo))
                                    }

                                    wordIs(source, wordStart, len, "while") -> {
                                        stack.addLast(Frame("while", lineNo))
                                    }

                                    wordIs(source, wordStart, len, "do") -> {
                                        // `for ... do` / `while ... do` 的 do 与 for/while 共享同一个 end，
                                        // 不重复压栈（`while x do ... end` 只有一个 end）；独立的 do 块压栈。
                                        val top = stack.lastOrNull()
                                        if (top?.kind != "for" && top?.kind != "while") {
                                            stack.addLast(Frame("do", lineNo))
                                        }
                                    }

                                    wordIs(source, wordStart, len, "function") -> {
                                        stack.addLast(Frame("function", lineNo))
                                    }

                                    wordIs(source, wordStart, len, "repeat") -> {
                                        stack.addLast(Frame("repeat", lineNo))
                                    }

                                    wordIs(source, wordStart, len, "then") -> {
                                        val top = stack.lastOrNull()
                                        if (top?.kind == "if") top.kind = "ifthen"
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
                                            setOf("repeat"),
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
                    top != null && top.kind in expected -> {
                        stack.removeLast()
                        break
                    }

                    top != null && top.kind.startsWith("bracket:") -> {
                        val ch = top.kind.removePrefix("bracket:")
                        errors.add(ScanError(top.line, "未闭合的 '$ch'", FixSpec.AppendText(closeOf(ch))))
                        stack.removeLast()
                    }

                    else -> {
                        errors.add(ScanError(lineNo, "多余的 '$excessToken'", FixSpec.DeleteToken(tokenRelStart, tokenRelEnd, excessToken)))
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
    }

    private fun countLines(
        s: String,
        n: Int,
    ): Int {
        var count = 1
        for (i in 0 until n) if (s[i] == '\n') count++
        return count
    }

    private fun unclosedMessage(kind: String): String {
        if (kind.startsWith("bracket:")) {
            return "未闭合的 '${kind.removePrefix("bracket:")}'"
        }
        return when (kind) {
            "if", "ifthen" -> "未闭合的 'if'"
            "for" -> "未闭合的 'for'"
            "while" -> "未闭合的 'while'"
            "do" -> "未闭合的 'do'"
            "function" -> "未闭合的 'function'"
            "repeat" -> "未闭合的 'repeat'"
            else -> "未闭合的 '$kind'"
        }
    }

    /** 左括号的对应右括号；未知输入原样返回。 */
    private fun closeOf(open: String): String =
        when (open) {
            "(" -> ")"
            "[" -> "]"
            "{" -> "}"
            else -> open
        }

    /** 长括号关闭序列：`]` + `=`×[level] + `]`。 */
    private fun closeOfLong(level: Int): String = LONG_CLOSE_PREFIX + "=".repeat(level) + "]"

    private fun errorAt(
        source: String,
        lineNumber: Int,
        message: String,
    ): DiagnosticRegion {
        val line = if (lineNumber < 1) 1 else lineNumber
        var start = 0
        var cur = 1
        while (cur < line) {
            val nl = source.indexOf('\n', start)
            if (nl < 0) break
            start = nl + 1
            cur++
        }
        var end = source.indexOf('\n', start)
        if (end < 0) end = source.length
        return DiagnosticRegion(
            start,
            end,
            DiagnosticRegion.SEVERITY_ERROR,
            0L,
            DiagnosticDetail(briefMessage = "$message（第 $line 行）"),
        )
    }

    private const val LONG_CLOSE_PREFIX = "]"
    private val BLOCK_KINDS = setOf("if", "ifthen", "for", "while", "do", "function")
}
