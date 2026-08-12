package com.lioisme.lide.project

/**
 * init.lua 结构化解析器（纯 Kotlin，宿主侧：工程元信息解析与打包共用）。
 *
 * 用途：工程/插件元信息（appname/appver/packagename/user_permission/pages…）的可靠读写。
 * 设计约束：单遍 O(n) 词法扫描（跳过行注释 / 长注释 / 字符串 / 长字符串），
 * 只认**顶层**（不在任何 `{}` 表内）的 `field = value` 赋值，注释内容永不误命中；
 * 写入器为字段级替换（其余字节原样保留，含注释与格式）。
 *
 * 本类只做文本级配置读写，不涉运行时语义（R2 边界）。
 */
object LuaConfigParser {

    /** E3（2026-08-12）：扫描收敛守卫——每字符最多推进的扫描步数。 */
    private const val GUARD_STEPS_PER_CHAR = 8L

    /** E3（2026-08-12）：扫描收敛守卫——固定步数基数（覆盖空输入等边界）。 */
    private const val GUARD_STEPS_BASE = 1024L

    /** 解析出的顶层字段：原始值文本 + 可替换区间（行首 → 值结束）。 */
    data class Field(
        val name: String,
        /** 值原文：字符串含引号；数组含 `{...}`；无值则为 nil/空。 */
        val rawValue: String,
        /** 替换区间起点（字段所在行的行首偏移，含前导空白）。 */
        val startOffset: Int,
        /** 替换区间终点（值结束后的偏移，不含行尾换行）。 */
        val endOffset: Int,
    )

    /** 提取全部顶层字段。 */
    fun parse(content: String): List<Field> {
        val fields = ArrayList<Field>()
        val n = content.length
        var i = 0
        var depth = 0
        // 防死循环：任何单遍扫描都必须在有限步内推进（防御异常输入）。
        var guard = 0L
        // E3（2026-08-12）：扫描步数上限 = 输入长度 × GUARD_STEPS_PER_CHAR + GUARD_STEPS_BASE。
        val guardLimit = n.toLong() * GUARD_STEPS_PER_CHAR + GUARD_STEPS_BASE
        // 行首扫描态：所在行尚未产出字段时，从行首第一个非空白开始尝试匹配 `name =`。
        var lineStart = 0
        var atLineStart = true
        while (i < n) {
            if (++guard > guardLimit) {
                throw IllegalStateException("LuaConfigParser.parse scan did not converge (abnormal input)")
            }
            val c = content[i]
            when {
                atLineStart && depth == 0 && (c.isLetter() || c == '_') -> {
                    val tokenStart = i
                    while (i < n && (content[i].isLetterOrDigit() || content[i] == '_')) i++
                    // 仅当标识符后（跳过空白）紧跟 `=` 才视为字段。
                    var j = i
                    while (j < n && (content[j] == ' ' || content[j] == '\t')) j++
                    if (j < n && content[j] == '=' && j + 1 < n) {
                        val name = content.substring(tokenStart, i)
                        val valueStart = skipWhitespace(content, j + 1, n)
                        val valueEnd = scanValueEnd(content, valueStart, n)
                        fields.add(
                            Field(
                                name = name,
                                rawValue = content.substring(valueStart, valueEnd).trim(),
                                startOffset = lineStart,
                                endOffset = valueEnd,
                            )
                        )
                        i = valueEnd
                        atLineStart = false
                        lineStart = skipToNextLine(content, i, n)
                        continue
                    }
                    // 非字段赋值（函数调用、表达式等）：按普通文本推进，防止原地踏步。
                    i = j + 1
                }

                c == '-' && i + 1 < n && content[i + 1] == '-' -> {
                    val level = longBracketLevel(content, i + 2, n)
                    if (level >= 0) {
                        // 长注释：跨行扫描。
                        i += 2 + level + 2
                        val close = "]${"=".repeat(level)}]"
                        val end = content.indexOf(close, i)
                        i = if (end < 0) n else end + close.length
                        lineStart = skipToNextLine(content, i, n)
                    } else {
                        // 行注释：跳到行尾。
                        i = skipToNextLine(content, i, n)
                        lineStart = i
                    }
                    atLineStart = true
                }

                c == '"' || c == '\'' -> {
                    val quote = c
                    i++
                    while (i < n) {
                        when (content[i]) {
                            '\\' -> i += 2
                            quote -> { i++; break }
                            else -> i++
                        }
                    }
                    if (i >= n) return fields
                    lineStart = skipToNextLine(content, i, n)
                    atLineStart = true
                }

                c == '[' -> {
                    val level = longBracketLevel(content, i, n)
                    if (level >= 0) {
                        i += level + 2
                        val close = "]${"=".repeat(level)}]"
                        val end = content.indexOf(close, i)
                        i = if (end < 0) n else end + close.length
                        lineStart = skipToNextLine(content, i, n)
                    } else {
                        // 索引括号（t[1]、[1] = "x"）不参与表嵌套计数（depth 只跟踪 `{}`）：
                        // `[` 无配对递减，计入会使顶层 `t[1] = x` 之后所有字段静默丢失（P0-5）。
                        i++
                    }
                    atLineStart = false
                }

                c == '{' -> {
                    depth++
                    i++
                    atLineStart = false
                }

                c == '}' -> {
                    if (depth > 0) depth--
                    i++
                    atLineStart = false
                }

                c == '\n' -> {
                    i++
                    lineStart = i
                    atLineStart = true
                }

                c == ' ' || c == '\t' || c == '\r' -> i++

                else -> {
                    i++
                    atLineStart = false
                }
            }
        }
        return fields
    }

    /** 取单个字段的原始值（无该字段返回 null）。 */
    fun field(content: String, name: String): String? =
        parse(content).firstOrNull { it.name == name }?.rawValue

    /**
     * D3（2026-08-12）：单遍解析全部顶层**字符串**字段（去引号），供多字段并发
     * 读取的调用方复用一次 [parse]——loadProject 原对 appname/packagename/icon
     * 各做一次全量扫描。非字符串字段（表/裸标识符）不进入结果。
     */
    fun stringFields(content: String): Map<String, String> {
        val result = HashMap<String, String>()
        for (f in parse(content)) {
            if (result.containsKey(f.name)) continue
            val v = f.rawValue.trim()
            if (v.length >= 2 && (v.first() == '"' || v.first() == '\'') && v.last() == v.first()) {
                result[f.name] = v.substring(1, v.length - 1)
            }
        }
        return result
    }

    /** 取字符串字段（去引号；非字符串/不存在返回 null）。 */
    fun stringField(content: String, name: String): String? {
        val raw = field(content, name) ?: return null
        val v = raw.trim()
        if (v.length >= 2 && (v.first() == '"' || v.first() == '\'') && v.last() == v.first()) {
            return v.substring(1, v.length - 1)
        }
        return null
    }

    /** 解析字符串数组字段（`{ "a", b }` 或裸标识符）；空表/缺省返回空表。 */
    fun stringArrayField(content: String, name: String): List<String> {
        val raw = field(content, name) ?: return emptyList()
        val v = raw.trim()
        if (!v.startsWith("{")) return emptyList()
        return parseArrayItems(v)
    }

    /**
     * 字段级写入：将 [name] 字段替换为 `name = [valueText]`。
     * 未找到字段时追加到文件末尾（以换行分隔）。其余字节与注释原样保留。
     */
    fun setField(content: String, name: String, valueText: String): String {
        val fields = parse(content)
        val existing = fields.firstOrNull { it.name == name }
        val replacement = "$name = $valueText"
        return if (existing != null) {
            content.substring(0, existing.startOffset) +
                replacement +
                content.substring(existing.endOffset)
        } else {
            val separator = if (content.isNotEmpty() && !content.endsWith("\n")) "\n" else ""
            content + separator + replacement + "\n"
        }
    }

    /** 将字符串数组字段写回（`{ "a", "b" }` 格式，与 pro 模板一致）。 */
    fun setStringArrayField(content: String, name: String, items: List<String>): String {
        val formatted = items.joinToString(", ") { "\"$it\"" }
        return setField(content, name, if (items.isEmpty()) "{ }" else "{ $formatted }")
    }

    /**
     * 移除字段（P1，2026-08-11：「跟随宿主」语义 = 字段不存在，运行时/打包回落默认）。
     * 整行删除（字段值之后到行尾的内容——含行注释等尾随文本——一并删除）；
     * 字段不存在时原样返回。仅删除字段所在行，不触碰其他内容。
     */
    fun removeField(content: String, name: String): String {
        val fields = parse(content)
        val existing = fields.firstOrNull { it.name == name } ?: return content
        // 字段值结束可能早于行尾（值后跟行注释/逗号等），删除到行尾换行处。
        val nl = content.indexOf('\n', existing.endOffset)
        val end = if (nl < 0) content.length else nl + 1
        return content.substring(0, existing.startOffset) + content.substring(end)
    }

    /**
     * Lua `%q` 等价转义（带引号字符串）。仅转义双引号/反斜杠/换行/回车/制表符与
     * 控制字符（\0-\x1F、\x7F，Lua 5.3 `\u{XX}` 形式），其余字节原样（UTF-8 安全）。
     */
    fun quoteLuaString(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' || c == '\u007F' -> sb.append("\\u{%02x}".format(c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    // ---- 内部工具 ----

    private fun skipWhitespace(s: String, from: Int, n: Int): Int {
        var i = from
        while (i < n && (s[i] == ' ' || s[i] == '\t' || s[i] == '\r')) i++
        return i
    }

    private fun skipToNextLine(s: String, from: Int, n: Int): Int {
        val nl = s.indexOf('\n', from)
        return if (nl < 0) n else nl + 1
    }

    /** 值结束偏移：`{...}` 配平（跳过字符串）；标量取行尾（剔除行注释与尾随逗号）。 */
    private fun scanValueEnd(s: String, from: Int, n: Int): Int {
        var i = from
        if (i < n && s[i] == '{') {
            var d = 0
            while (i < n) {
                when (s[i]) {
                    '{' -> {
                        d++
                        i++
                    }
                    '}' -> {
                        d--
                        if (d == 0) { i++; break }
                    }
                    '"', '\'' -> {
                        val q = s[i]
                        i++
                        while (i < n) {
                            when (s[i]) {
                                '\\' -> i += 2
                                q -> { i++; break }
                                else -> i++
                            }
                        }
                    }
                    else -> i++
                }
            }
            return i
        }
        // 标量：到行尾；剔除行注释与尾随逗号。
        val nl = s.indexOf('\n', i)
        var end = if (nl < 0) n else nl
        var j = end
        while (j > i && (s[j - 1] == ' ' || s[j - 1] == '\t' || s[j - 1] == '\r')) j--
        if (j > i && s[j - 1] == ',') j--
        // 剔除 `--` 行注释。
        var k = i
        while (k < j - 1) {
            if (s[k] == '-' && s[k + 1] == '-') { j = k; break }
            k++
        }
        while (j > i && (s[j - 1] == ' ' || s[j - 1] == '\t')) j--
        return j
    }

    private fun longBracketLevel(s: String, i: Int, n: Int): Int {
        if (i >= n || s[i] != '[') return -1
        var j = i + 1
        while (j < n && s[j] == '=') j++
        return if (j < n && s[j] == '[') j - i - 1 else -1
    }

    private fun parseArrayItems(v: String): List<String> {
        val items = ArrayList<String>()
        var i = 0
        val n = v.length
        while (i < n) {
            when {
                v[i] == '"' || v[i] == '\'' -> {
                    val q = v[i]
                    val start = i
                    i++
                    while (i < n) {
                        when (v[i]) {
                            '\\' -> i += 2
                            q -> { i++; break }
                            else -> i++
                        }
                    }
                    items.add(v.substring(start + 1, i - 1))
                }

                v[i].isLetter() || v[i] == '_' -> {
                    val start = i
                    while (i < n && (v[i].isLetterOrDigit() || v[i] == '_')) i++
                    items.add(v.substring(start, i))
                }

                else -> i++
            }
        }
        return items
    }
}
