package com.lioisme.lide.editor

/**
 * T13（2026-08-11）Lua 语法格式化器：缩进重排 + 空白清理档位（零语义风险）。
 *
 * 设计：
 * - 输入经 [LuaLexer.tokenize] 产出完整 token 流，字符串/注释内容为原子 token，
 *   内部关键字零误伤（保护语义，与诊断词法层单事实源）；
 * - 按「行首 token」判定缩进（与 pro AutoIndent 行首词判定同构）：
 *   `end`/`until`/`else`/`elseif` 行首提前减级（先减后写）；`else`/`elseif`
 *   行结束恢复块体深度；`if`/`for`/`while`/`function`/`repeat`/行首 `do`/
 *   `local function` 行结束加级；
 * - 空白全部重建：行首缩进（useTab/tabSize）、token 间单空格、行尾零空白、
 *   连续空行折叠（保留 1）、换行符归一（CRLF/CR → LF）；
 * - 纯函数无状态，可后台执行；大文件（>200KB）短路返回原文本（调用方负责后台化）。
 */
object LuaFormatter {
    /** 大文件短路阈值（D2：单一事实源 [EditorLimits.MAX_FORMAT_LENGTH]，与
     *  编辑器大文件降级口径一致——>200KB 不做重排）。 */
    private const val MAX_FORMAT_LENGTH = EditorLimits.MAX_FORMAT_LENGTH

    /** 行首开启块的词（[blockOpenAt] 已含 `local function` 组合）。 */
    private val BLOCK_OPEN = setOf("if", "for", "while", "function", "repeat", "do")

    /** 行首闭合/对齐词。 */
    private val BLOCK_CLOSE = setOf("end", "until", "else", "elseif")

    /**
     * 格式化 [source]；[useTab] 为真时缩进用 tab（tab 视作一个单元），否则用
     * [tabSize] 个空格。返回格式化后的文本（失败或大文件返回原文本）。
     */
    fun format(
        source: String,
        useTab: Boolean = false,
        tabSize: Int = 4,
    ): String {
        if (source.length > MAX_FORMAT_LENGTH || source.isEmpty()) return source
        return try {
            rebuild(LuaLexer.tokenize(source), source, useTab, tabSize)
        } catch (_: Throwable) {
            // 降级：格式化失败返回原文本，不破坏编辑内容。
            source
        }
    }

    private fun rebuild(
        tokens: List<LuaToken>,
        source: String,
        useTab: Boolean,
        tabSize: Int,
    ): String {
        val n = source.length
        val lineStarts = buildLineStarts(source, n)
        val out = StringBuilder(n + n / 4)
        val unit: String = if (useTab) "\t" else " ".repeat(tabSize.coerceAtLeast(1))

        var level = 0
        var outLine = 0
        var pendingBlankLines = 0
        var lineHasToken = false
        var lineBlockOpen = false
        var lineHasEnd = false
        var lineHasOpenBrace = false
        var lineHasCloseBrace = false

        fun finishLine() {
            if (lineHasToken) {
                out.append('\n')
            } else {
                pendingBlankLines++
            }
            // else/elseif 行首已减级对齐 if，行末恢复块体深度；
            // 行内自闭合（`while false do end` / `{ 1, 2 },`）不加减。
            if (lineBlockOpen && !lineHasEnd) level++
            // 表块（2026-08-11 T17.4 设备实测补丁）：行内有 `{` 无 `}` 时加级，
            // `}` 行首已减级（对齐现有 end/until 语义），自闭合表不加减。
            if (lineHasOpenBrace && !lineHasCloseBrace) level++
            lineHasToken = false
            lineBlockOpen = false
            lineHasEnd = false
            lineHasOpenBrace = false
            lineHasCloseBrace = false
        }

        fun writeIndent() {
            if (level < 0) level = 0
            repeat(level) { out.append(unit) }
        }

        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.type == LuaTokenType.WHITESPACE) {
                i++
                continue
            }
            val srcLine = lineIndexFor(t.start, lineStarts)
            if (srcLine > outLine) {
                // 推进输出行：跨行 token 内部换行已原样写入，只补不足的行数。
                val skip = (srcLine - outLine).coerceAtLeast(1)
                repeat(skip) { finishLine() }
                outLine = srcLine
                if (pendingBlankLines > 0) {
                    // 连续空行折叠：仅输出一个空行。
                    out.append('\n')
                    pendingBlankLines = 0
                }
            }
            if (!lineHasToken) {
                // 行首 token：先应用减级再写缩进。
                if (t.type == LuaTokenType.CODE) {
                    val text = t.text
                    // 「行首词」判定：`end,` 命中；`while false do end`（行首词
                    // 为 while）不命中，由行内自闭合标记兜住。
                    if (firstWordIsAny(text, BLOCK_CLOSE)) {
                        level--
                    } else if (text.startsWith("}")) {
                        level--
                        // 表闭合：行首 `}`（如 `},`）与 end/until 同语义先减级。
                    }
                }
                writeIndent()
            } else {
                // token 间单空格：`,`/`)`/`]`/`;` 开头不插前导空格
                // （`text = "hi",` 不变成 `text = "hi" ,`；`}` 保留前导空格
                // 以维持 `{ 1, 2 }` 惯例，2026-08-11 T17.4 实测修正）。
                val noLeadingSpace =
                    t.type == LuaTokenType.CODE &&
                        (
                            t.text.startsWith(",") ||
                                t.text.startsWith(")") ||
                                t.text.startsWith("]") ||
                                t.text.startsWith(";")
                        )
                if (out.isNotEmpty() && out.last() != '\n' && out.last() != ' ' && !noLeadingSpace) {
                    out.append(' ')
                }
            }
            // 行内累计判定（跨 token，如 `onClick = function()` 的 function 是
            // 独立 token）：词边界匹配，`doSomething`/`endless` 等标识符不误伤。
            if (t.type == LuaTokenType.CODE) {
                val text = t.text
                if (
                    !lineBlockOpen &&
                    (
                        containsBlockWord(text, BLOCK_OPEN) ||
                            containsBlockWord(text, "else") ||
                            containsBlockWord(text, "elseif") ||
                            (text == "local" && nextCodeWordIs(tokens, i, "function"))
                    )
                ) {
                    lineBlockOpen = true
                }
                // 行内自闭合标记：本行出现过 end/until 则整行视为自闭合（`while x do end`）。
                if (containsBlockWord(text, "end") || containsBlockWord(text, "until")) {
                    lineHasEnd = true
                }
                // 表定界标记（CODE token 不含字符串/注释内容，安全）：
                // `layout = {` 行加级；`},` 行首已减级；`{ 1, 2 },` 自闭合不加减。
                if (text.contains('{')) lineHasOpenBrace = true
                if (text.contains('}')) lineHasCloseBrace = true
            }
            out.append(t.text)
            lineHasToken = true
            val newlines = countNewlines(t.text)
            if (newlines > 0) outLine += newlines
            i++
        }
        finishLine()
        // 原文本末尾无换行时去掉补上的末尾换行，保留用户尾部形态。
        if (source.isNotEmpty() && source.last() != '\n' && out.isNotEmpty() && out.last() == '\n') {
            out.setLength(out.length - 1)
        }
        return out.toString()
    }

    /** [tokens] 中 [index] 之后（跳过空白）首个 CODE token 是否等于 [word]。 */
    private fun nextCodeWordIs(
        tokens: List<LuaToken>,
        index: Int,
        word: String,
    ): Boolean {
        var j = index + 1
        while (j < tokens.size) {
            val next = tokens[j]
            if (next.type == LuaTokenType.WHITESPACE) {
                j++
                continue
            }
            return next.type == LuaTokenType.CODE && next.text == word
        }
        return false
    }

    /**
     * [text]（单行 CODE token，不含字符串/注释）中是否出现 [word] 且前后均为
     * 标识符边界。`onClick = function()` 命中 function；`doSomething`/`endless`
     * 不命中 do/end（前后邻接标识符字符）。
     */
    private fun containsBlockWord(
        text: String,
        word: String,
    ): Boolean {
        var from = 0
        while (true) {
            val idx = text.indexOf(word, from)
            if (idx < 0) return false
            val beforeOk = idx == 0 || !text[idx - 1].isIdentifierChar()
            val afterOk =
                idx + word.length >= text.length || !text[idx + word.length].isIdentifierChar()
            if (beforeOk && afterOk) return true
            from = idx + 1
        }
    }

    /** [words] 中任一在 [text] 内按词边界出现。 */
    private fun containsBlockWord(
        text: String,
        words: Set<String>,
    ): Boolean = words.any { containsBlockWord(text, it) }

    /** [text] 开头（词边界）是否为 [word]（`end,` 命中；`endless` 不命中）。 */
    private fun firstWordIs(
        text: String,
        word: String,
    ): Boolean {
        if (!text.startsWith(word)) return false
        return text.length == word.length || !text[word.length].isIdentifierChar()
    }

    /** [words] 中任一在 [text] 开头按词边界匹配。 */
    private fun firstWordIsAny(
        text: String,
        words: Set<String>,
    ): Boolean = words.any { firstWordIs(text, it) }

    private fun countNewlines(s: String): Int {
        var count = 0
        for (c in s) {
            if (c == '\n') count++
        }
        return count
    }

    private fun buildLineStarts(
        s: String,
        n: Int,
    ): IntArray {
        val starts = ArrayList<Int>(n / 16 + 1)
        starts.add(0)
        var scan = 0
        while (scan < n) {
            val nl = s.indexOf('\n', scan)
            if (nl < 0) break
            starts.add(nl + 1)
            scan = nl + 1
        }
        return starts.toIntArray()
    }

    private fun lineIndexFor(
        offset: Int,
        lineStarts: IntArray,
    ): Int {
        var lo = 0
        var hi = lineStarts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (lineStarts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }
}
