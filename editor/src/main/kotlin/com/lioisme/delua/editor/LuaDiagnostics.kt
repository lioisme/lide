package com.lioisme.delua.editor

import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion

/**
 * 纯 Kotlin 词法校验器。
 *
 * 用途：编辑器进程内的行级语法诊断（括号配对、块闭合、字符串/注释完整性）。
 * 设计约束：零原生依赖（编辑器进程不加载任何 .so）、单遍 O(n)、无每词字符串分配。
 * 深层次语法（表达式、运算符优先级等）不属于本类职责，由运行时引擎在编译期兜底。
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
     * - 上一轮报告过错误且错误行早于本次扫描起点时强制全量重扫，前缀错误不会凭空消失；
     * - 任何内部异常都降级为全量重扫，增量状态不会毒化后续检查。
     *
     * 线程模型：事件回调（主线程）调用 [onEdit] / [onNewText]，检查协程（后台线程）调用
     * [check]；全部方法同步，检查点一致性受锁保护。
     */
    class Incremental {

        /** 单行解析状态：模式 + 括号/块栈。 */
        private data class Frame(var kind: String, val line: Int)

        private sealed interface Mode {
            /** 普通代码。 */
            data object Normal : Mode

            /** 长注释 `--[=[ ... ]=]` 中（跨行）。 */
            data class LongComment(val level: Int, val startLine: Int) : Mode

            /** 长字符串 `[=[ ... ]=]` 中（跨行）。 */
            data class LongString(val level: Int, val startLine: Int) : Mode

            /** 引号字符串中（跨行，与全量版语义一致）。 */
            data class QuotedString(val quote: Char, val startLine: Int) : Mode
        }

        /** 第 [line] 行处理完成后的检查点；[nextLineStart] 为下一行的起始偏移。 */
        private class Checkpoint(
            val nextLineStart: Int,
            val mode: Mode,
            val stack: List<Frame>,
        )

        private class ScanResult(
            val mode: Mode,
            val errorLine: Int = -1,
            val errorMessage: String? = null,
        )

        @Volatile
        private var minEditedLine = Int.MAX_VALUE

        @Volatile
        private var maxEditedLine = Int.MAX_VALUE

        @Volatile
        private var prevErrorLine = -1

        private var checkpoints: MutableList<Checkpoint> = ArrayList()

        /**
         * 新文本（文件切换 / setText）：清空全部增量状态。
         */
        @Synchronized
        fun onNewText() {
            checkpoints = ArrayList()
            minEditedLine = Int.MAX_VALUE
            maxEditedLine = Int.MAX_VALUE
            prevErrorLine = -1
        }

        /**
         * 记录一次编辑影响的行区间（0-based，闭区间）。
         */
        @Synchronized
        fun onEdit(startLine: Int, endLine: Int) {
            if (startLine < minEditedLine) minEditedLine = startLine
            if (endLine > maxEditedLine) maxEditedLine = endLine
        }

        /**
         * 对 [source] 执行（增量）校验，返回首个错误（无错误返回空表）。
         */
        @Synchronized
        fun check(source: String): List<DiagnosticRegion> {
            return try {
                checkInternal(source)
            } catch (t: Throwable) {
                // 降级：清空增量状态后全量重扫，保证诊断永不因状态损坏而缺失。
                checkpoints = ArrayList()
                prevErrorLine = -1
                try {
                    checkInternal(source)
                } catch (_: Throwable) {
                    emptyList()
                }
            }
        }

        private fun checkInternal(source: String): List<DiagnosticRegion> {
            val n = source.length
            val lineCount = countLines(source, n)
            val minEdited = minEditedLine
            val maxEdited = maxEditedLine
            minEditedLine = Int.MAX_VALUE
            maxEditedLine = Int.MAX_VALUE
            if (lineCount == 0) {
                checkpoints = ArrayList()
                prevErrorLine = -1
                return emptyList()
            }
            if (checkpoints.isEmpty()) {
                prevErrorLine = -1
            }

            // 上一次检查报告的错误若在本轮扫描起点之前，强制全量重扫以维持该错误的可见性。
            val requestedStart = if (minEdited == Int.MAX_VALUE) 0 else minEdited.coerceAtMost(lineCount - 1)
            var start = if (prevErrorLine in 0 until requestedStart) 0 else requestedStart
            if (start > 0 && checkpoints.getOrNull(start - 1) == null) start = 0

            var li = start
            var lineStart = if (start == 0) 0 else checkpoints[start - 1].nextLineStart
            var mode: Mode = if (start == 0) Mode.Normal else checkpoints[start - 1].mode
            var stack: MutableList<Frame> =
                if (start == 0) mutableListOf() else checkpoints[start - 1].stack.toMutableList()

            while (li < lineCount) {
                val nl = source.indexOf('\n', lineStart)
                val lineEnd = if (nl < 0) n else nl
                val nextLineStart = if (nl < 0) n else nl + 1

                val result = scanLine(source, lineStart, lineEnd, li + 1, mode, stack)
                if (result.errorMessage != null) {
                    // 错误行不提交检查点（其后状态未知），截断到本行之前。
                    truncate(li)
                    prevErrorLine = result.errorLine
                    return errorAt(source, result.errorLine, result.errorMessage)
                }

                val checkpoint = checkpoints.getOrNull(li)
                if (li >= maxEdited && checkpoint != null &&
                    checkpoint.mode == result.mode && checkpoint.stack == stack
                ) {
                    // 收敛：后续内容与检查点一致（行号逐值相同 ⇒ 行号未漂移），提前结束。
                    truncate(li + 1)
                    prevErrorLine = -1
                    return emptyList()
                }

                val committed = Checkpoint(nextLineStart, result.mode, stack.toList())
                if (li < checkpoints.size) checkpoints[li] = committed else checkpoints.add(committed)
                mode = result.mode
                lineStart = nextLineStart
                li++
            }

            truncate(lineCount)
            return when (mode) {
                is Mode.LongComment -> errorAt(source, mode.startLine, "未闭合的长注释").also {
                    prevErrorLine = mode.startLine
                }
                is Mode.LongString -> errorAt(source, mode.startLine, "未闭合的长字符串").also {
                    prevErrorLine = mode.startLine
                }
                is Mode.QuotedString -> errorAt(source, mode.startLine, "未闭合的字符串").also {
                    prevErrorLine = mode.startLine
                }
                else -> {
                    val top = stack.lastOrNull()
                    if (top != null) {
                        errorAt(source, top.line, unclosedMessage(top.kind)).also {
                            prevErrorLine = top.line
                        }
                    } else {
                        prevErrorLine = -1
                        emptyList()
                    }
                }
            }
        }

        private fun truncate(count: Int) {
            while (checkpoints.size > count) checkpoints.removeAt(checkpoints.size - 1)
        }

        /**
         * 扫描 [start, end) 单行（[lineNo] 为 1-based 行号），就地更新 [stack]，
         * 返回行末模式；行内发现错误时返回错误信息。
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
                            '\\' -> i += 2
                            mode.quote -> {
                                mode = Mode.Normal
                                i++
                            }
                            else -> i++
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
                                        '\\' -> i += 2
                                        quote -> {
                                            i++
                                            closed = true
                                            break
                                        }
                                        else -> i++
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
                                val want = when (c) {
                                    ')' -> '('
                                    ']' -> '['
                                    else -> '{'
                                }
                                if (stack.isNotEmpty() && stack.last().kind == "bracket:$want") {
                                    stack.removeLast()
                                } else {
                                    return ScanResult(mode, lineNo, "不匹配的 '$c'")
                                }
                                i++
                            }

                            c.isLetter() || c == '_' -> {
                                val wordStart = i
                                while (i < end && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                                val len = i - wordStart
                                when {
                                    wordIs(source, wordStart, len, "if") -> stack.addLast(Frame("if", lineNo))
                                    wordIs(source, wordStart, len, "for") -> stack.addLast(Frame("for", lineNo))
                                    wordIs(source, wordStart, len, "while") -> stack.addLast(Frame("while", lineNo))
                                    wordIs(source, wordStart, len, "do") -> stack.addLast(Frame("do", lineNo))
                                    wordIs(source, wordStart, len, "function") -> stack.addLast(Frame("function", lineNo))
                                    wordIs(source, wordStart, len, "repeat") -> stack.addLast(Frame("repeat", lineNo))
                                    wordIs(source, wordStart, len, "then") -> {
                                        val top = stack.lastOrNull()
                                        if (top?.kind == "if") top.kind = "ifthen"
                                    }
                                    wordIs(source, wordStart, len, "end") -> {
                                        val top = stack.lastOrNull()
                                        if (top != null && top.kind in BLOCK_KINDS) {
                                            stack.removeLast()
                                        } else {
                                            return ScanResult(mode, lineNo, "多余的 'end'")
                                        }
                                    }
                                    wordIs(source, wordStart, len, "until") -> {
                                        val top = stack.lastOrNull()
                                        if (top?.kind == "repeat") {
                                            stack.removeLast()
                                        } else {
                                            return ScanResult(mode, lineNo, "多余的 'until'")
                                        }
                                    }
                                }
                            }

                            else -> i++
                        }
                    }
                }
            }
            return ScanResult(mode)
        }

        private fun wordIs(s: String, offset: Int, len: Int, word: String): Boolean {
            return len == word.length && s.regionMatches(offset, word, 0, word.length)
        }

        /**
         * 返回 [i] 处开始的长括号层级；要求整个 `[=[` 都在 [end) 行内（与 Lua 语义一致，
         * 同时避免跨行扫描把下一行的 `[=[` 误并入上一行注释）。
         */
        private fun longBracketLevel(s: String, i: Int, end: Int): Int {
            if (i >= end || s[i] != '[') return -1
            var j = i + 1
            while (j < end && s[j] == '=') j++
            return if (j < end && s[j] == '[') j - i - 1 else -1
        }

        private fun indexOfWithin(s: String, target: String, from: Int, to: Int): Int {
            var k = from
            while (k < to) {
                if (s.startsWith(target, k)) return k
                k++
            }
            return -1
        }
    }

    private fun countLines(s: String, n: Int): Int {
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

    private fun errorAt(source: String, lineNumber: Int, message: String): List<DiagnosticRegion> {
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
        return listOf(
            DiagnosticRegion(
                start, end, DiagnosticRegion.SEVERITY_ERROR, 0L,
                DiagnosticDetail(briefMessage = "$message（第 $line 行）"),
            )
        )
    }

    private const val LONG_CLOSE_PREFIX = "]"
    private val BLOCK_KINDS = setOf("if", "ifthen", "for", "while", "do", "function")
}
