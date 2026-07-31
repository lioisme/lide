package com.lioisme.delua.editor

import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion

/**
 * 纯 Kotlin 单遍词法校验器。
 *
 * 用途：编辑器进程内的行级语法诊断（括号配对、块闭合、字符串/注释完整性）。
 * 设计约束：零原生依赖（编辑器进程不加载任何 .so）、单遍 O(n)、无每词字符串分配。
 * 深层次语法（表达式、运算符优先级等）不属于本类职责，由运行时引擎在编译期兜底。
 */
object LuaDiagnostics {

    private class Frame(var kind: String, val line: Int)

    fun checkSyntax(source: String): List<DiagnosticRegion> {
        val stack = ArrayDeque<Frame>()
        var line = 1
        var i = 0
        val n = source.length
        while (i < n) {
            val c = source[i]
            when {
                c == '\n' -> { line++; i++ }

                c == '-' && i + 1 < n && source[i + 1] == '-' -> {
                    i += 2
                    val level = longBracketLevel(source, i)
                    if (level >= 0) {
                        i += level + 2
                        val close = "]" + "=".repeat(level) + "]"
                        val end = source.indexOf(close, i)
                        if (end < 0) return errorAt(source, line, "未闭合的长注释")
                        line += countNewlines(source, i, end)
                        i = end + close.length
                    } else {
                        val nl = source.indexOf('\n', i)
                        if (nl < 0) return emptyList()
                        line++
                        i = nl + 1
                    }
                }

                c == '\'' || c == '"' -> {
                    val startLine = line
                    val quote = c
                    i++
                    var closed = false
                    while (i < n) {
                        when (source[i]) {
                            '\\' -> i += 2
                            quote -> { i++; closed = true; break }
                            '\n' -> { line++; i++ }
                            else -> i++
                        }
                    }
                    if (!closed) return errorAt(source, startLine, "未闭合的字符串")
                }

                c == '[' -> {
                    val level = longBracketLevel(source, i)
                    if (level >= 0) {
                        i += level + 2
                        val close = "]" + "=".repeat(level) + "]"
                        val end = source.indexOf(close, i)
                        if (end < 0) return errorAt(source, line, "未闭合的长字符串")
                        line += countNewlines(source, i, end)
                        i = end + close.length
                    } else {
                        stack.addLast(Frame("bracket:[", line))
                        i++
                    }
                }

                c == '(' || c == '{' -> {
                    stack.addLast(Frame("bracket:$c", line))
                    i++
                }

                c == ')' || c == ']' || c == '}' -> {
                    val want = when (c) { ')' -> '('; ']' -> '['; else -> '{' }
                    if (stack.isNotEmpty() && stack.last().kind == "bracket:$want") {
                        stack.removeLast()
                    } else {
                        return errorAt(source, line, "不匹配的 '$c'")
                    }
                    i++
                }

                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < n && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                    val len = i - start
                    when {
                        wordIs(source, start, len, "if") -> stack.addLast(Frame("if", line))
                        wordIs(source, start, len, "for") -> stack.addLast(Frame("for", line))
                        wordIs(source, start, len, "while") -> stack.addLast(Frame("while", line))
                        wordIs(source, start, len, "do") -> stack.addLast(Frame("do", line))
                        wordIs(source, start, len, "function") -> stack.addLast(Frame("function", line))
                        wordIs(source, start, len, "repeat") -> stack.addLast(Frame("repeat", line))
                        wordIs(source, start, len, "then") -> {
                            val top = stack.lastOrNull()
                            if (top?.kind == "if") top.kind = "ifthen"
                        }
                        wordIs(source, start, len, "end") -> {
                            val top = stack.lastOrNull()
                            if (top != null && top.kind in BLOCK_KINDS) {
                                stack.removeLast()
                            } else {
                                return errorAt(source, line, "多余的 'end'")
                            }
                        }
                        wordIs(source, start, len, "until") -> {
                            val top = stack.lastOrNull()
                            if (top?.kind == "repeat") {
                                stack.removeLast()
                            } else {
                                return errorAt(source, line, "多余的 'until'")
                            }
                        }
                    }
                }

                else -> i++
            }
        }
        val top = stack.lastOrNull() ?: return emptyList()
        return errorAt(source, top.line, unclosedMessage(top.kind))
    }

    private fun wordIs(s: String, offset: Int, len: Int, word: String): Boolean {
        return len == word.length && s.regionMatches(offset, word, 0, word.length)
    }

    private fun longBracketLevel(s: String, i: Int): Int {
        if (i >= s.length || s[i] != '[') return -1
        var j = i + 1
        while (j < s.length && s[j] == '=') j++
        return if (j < s.length && s[j] == '[') j - i - 1 else -1
    }

    private fun countNewlines(s: String, from: Int, to: Int): Int {
        var cnt = 0
        for (k in from until to) if (s[k] == '\n') cnt++
        return cnt
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

    private val BLOCK_KINDS = setOf("if", "ifthen", "for", "while", "do", "function")
}
