package com.lioisme.lide.editor

/**
 * T12（2026-08-11）Lua 词法层（单事实源）：标识符 / 长括号 / 长字符串 / 长注释 /
 * 行注释 / 引号字符串原语与 token 流，供行级诊断（[LuaDiagnostics]）与格式化
 * （[LuaFormatter]）共享。纯 Kotlin，零原生依赖（R1 合规），单遍 O(n)。
 *
 * Lua 标识符字符判定（字母/数字/下划线），补全前缀与词法扫描共用。
 */
internal fun Char.isIdentifierChar(): Boolean = isLetterOrDigit() || this == '_'

/** 左括号的对应右括号；未知输入原样返回。 */
internal fun closeOf(open: Char): String =
    when (open) {
        '(' -> ")"
        '[' -> "]"
        '{' -> "}"
        else -> open.toString()
    }

/** 长括号关闭序列：`]` + `=`×[level] + `]`。 */
internal fun closeOfLong(level: Int): String = LONG_CLOSE_PREFIX + "=".repeat(level) + "]"

private const val LONG_CLOSE_PREFIX = "]"

/**
 * 返回 [i] 处开始的长括号层级；要求整个 `[=[` 都在 [end) 内（与 Lua 语义一致，
 * 避免跨行扫描把下一行的 `[=[` 误并入上一行注释）。
 */
internal fun longBracketLevel(
    s: String,
    i: Int,
    end: Int,
): Int {
    if (i >= end || s[i] != '[') return -1
    var j = i + 1
    while (j < end && s[j] == '=') j++
    return if (j < end && s[j] == '[') j - i - 1 else -1
}

/** [from, to) 区间内查找 [target]；未找到返回 -1。 */
internal fun indexOfWithin(
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

/** 词精确匹配（不 regionMatches 到更长词前缀）。 */
internal fun wordIs(
    s: String,
    offset: Int,
    len: Int,
    word: String,
): Boolean = len == word.length && s.regionMatches(offset, word, 0, word.length)

/** 块闭合语义共享词表（诊断 / 格式化共用，单事实源）：[BLOCK_KINDS] 为 `end` 可闭合的块种类。 */
internal val BLOCK_KINDS = setOf("if", "ifthen", "for", "while", "do", "function")

/** [REPEAT_KINDS] 为 `until` 可闭合的块种类。 */
internal val REPEAT_KINDS = setOf("repeat")

/** Token 类型：格式化重排只需区分「代码 / 字面量 / 注释 / 空白」四类语义。 */
enum class LuaTokenType {
    /** 普通代码（标识符/关键字/符号/数字等），格式化时可能重排其周围空白。 */
    CODE,

    /** 引号字符串 / 长字符串内容（含引号/长括号定界符）：文本原样保留。 */
    STRING,

    /** 行注释 / 长注释内容（含 `--`/长括号定界符）：文本原样保留。 */
    COMMENT,

    /** 空白（空格/tab/换行），格式化时重建（缩进重写 + 行尾清理 + 空行折叠）。 */
    WHITESPACE,
}

/** 单个词法 token：[start, end) 为源文本偏移。 */
data class LuaToken(
    val type: LuaTokenType,
    val text: String,
    val start: Int,
    val end: Int,
)

/**
 * Lua 词法器：单遍产出完整 token 流（含空白，零丢失），字符串/注释内容不解析
 * 内部关键字（保护语义）。跨行结构（长注释/长字符串/引号字符串）在 token 流中
 * 合并为单一 token，与 [LuaDiagnostics] 的模式语义一致。
 */
object LuaLexer {
    fun tokenize(source: String): List<LuaToken> {
        val tokens = ArrayList<LuaToken>(source.length / 8)
        val n = source.length
        var i = 0
        while (i < n) {
            val c = source[i]
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                val start = i
                while (i < n && (source[i] == ' ' || source[i] == '\t' || source[i] == '\r' || source[i] == '\n')) i++
                tokens.add(LuaToken(LuaTokenType.WHITESPACE, source.substring(start, i), start, i))
            } else if (c == '-' && i + 1 < n && source[i + 1] == '-') {
                val start = i
                i += 2
                val level = longBracketLevel(source, i, n)
                if (level >= 0) {
                    // 长注释：扫描至闭合序列（可跨行），未闭合则吞到文件尾。
                    val close = closeOfLong(level)
                    i += level + 2
                    val found = indexOfWithin(source, close, i, n)
                    if (found >= 0) {
                        i = found + close.length
                    } else {
                        i = n
                    }
                } else {
                    while (i < n && source[i] != '\n') i++
                }
                tokens.add(LuaToken(LuaTokenType.COMMENT, source.substring(start, i), start, i))
            } else if (c == '\'' || c == '"') {
                val start = i
                val quote = c
                i++
                while (i < n) {
                    when (source[i]) {
                        '\\' -> {
                            i += 2
                        }

                        quote -> {
                            i++
                            break
                        }

                        else -> {
                            i++
                        }
                    }
                }
                tokens.add(LuaToken(LuaTokenType.STRING, source.substring(start, i), start, i))
            } else if (c == '[') {
                val level = longBracketLevel(source, i, n)
                if (level >= 0) {
                    val start = i
                    i += level + 2
                    val close = closeOfLong(level)
                    val found = indexOfWithin(source, close, i, n)
                    if (found >= 0) {
                        i = found + close.length
                    } else {
                        i = n
                    }
                    tokens.add(LuaToken(LuaTokenType.STRING, source.substring(start, i), start, i))
                } else {
                    val start = i
                    i++
                    tokens.add(LuaToken(LuaTokenType.CODE, source.substring(start, i), start, i))
                }
            } else {
                // 普通代码字符：合并连续代码字符为一个 token（标识符/数字/符号序列），
                // 供格式化器按「词」间距重排；遇到字符串/注释/长括号定界即停。
                val start = i
                while (i < n && isCodeChar(source, i, n)) i++
                tokens.add(LuaToken(LuaTokenType.CODE, source.substring(start, i), start, i))
            }
        }
        return tokens
    }

    /** 代码字符判定（当前 [i] 处；[n] 为文本长度）：非空白、非引号、非注释/长括号定界。 */
    private fun isCodeChar(
        s: String,
        i: Int,
        n: Int,
    ): Boolean {
        val c = s[i]
        if (c == ' ' || c == '\t' || c == '\r' || c == '\n') return false
        if (c == '\'' || c == '"') return false
        if (c == '-' && i + 1 < n && s[i + 1] == '-') return false
        if (c == '[' && longBracketLevel(s, i, n) >= 0) return false
        return true
    }
}
