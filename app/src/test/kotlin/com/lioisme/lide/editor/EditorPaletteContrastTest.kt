package com.lioisme.lide.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 编辑器配色对比度门禁（2026-08-12 治理红线 C4）。
 *
 * 硬性契约（WCAG AA 严格档）：
 * - 全部激活主题（[EditorPalette.allThemes]）的**文本承载色** vs 该主题背景
 *   对比度 ≥ 4.5:1——含注释、行号、次要元素，无豁免；
 * - 前景色不得等于背景色（背景与代码文本禁止混淆）；
 * - 双模配对结构：每对亮/暗主题的 `editor.background` 亮度必须分居
 *   0.5/0.1 两侧（亮侧为浅底、暗侧为深底），且每对亮/暗侧主题 JSON 文件必须存在；
 * - 新增主题必须登记进 [EditorPalette.pairs]，测试从该表读取——白名单单一事实源。
 *
 * 文本承载色来源（与 LideHostColorScheme / sora 读取一致）：
 * - VSC 格式（tokenColors 段）：`tokenColors[].settings.foreground` +
 *   `colors.editor.foreground` / `editorLineNumber.foreground` /
 *   `editorLineNumber.activeForeground` / `editorWhitespace.foreground` /
 *   `editor.highlightedDelimiters.foreground`；
 * - TM 格式（settings 数组，darcula.json）：`settings[0].settings` 的
 *   `foreground` / `lineNumber` / `lineNumberActiveForeground` / `invisibles` /
 *   `highlightedDelimitersForeground` + 其余条目的 `settings.foreground`。
 * 装饰性槽位（背景/边框/选区底层/滚动条/minimap 等）不承载文本，免检。
 *
 * 注意：本测试从源码树读取资产（Gradle 单测工作目录 = 模块目录），不依赖 Android 运行时。
 */
class EditorPaletteContrastTest {

    private val assetsRoot =
        listOf(
            File("src/main/assets"),
            File("editor/src/main/assets"),
        ).firstOrNull { it.isDirectory }
            ?: throw IllegalStateException("assets root not found (cwd=${File("").absolutePath})")

    /** WCAG 对比度下限（严格档，含注释/行号）。 */
    private val MIN_CONTRAST = 4.5

    @Test
    fun `双模配对结构完整且亮暗分居`() {
        assertTrue("激活主题库必须至少一套双模配对", EditorPalette.pairs.isNotEmpty())
        for ((light, dark) in EditorPalette.pairs) {
            val lightTheme = loadTheme(light)
            val darkTheme = loadTheme(dark)
            val lightBg = lightTheme.background ?: error("$light 缺少背景色")
            val darkBg = darkTheme.background ?: error("$dark 缺少背景色")
            assertTrue(
                "亮侧主题 $light 背景应为浅色（luminance=${"%.3f".format(relativeLuminance(lightBg))}）",
                relativeLuminance(lightBg) > 0.5,
            )
            assertTrue(
                "暗侧主题 $dark 背景应为深色（luminance=${"%.3f".format(relativeLuminance(darkBg))}）",
                relativeLuminance(darkBg) < 0.1,
            )
        }
    }

    @Test
    fun `主题白名单与文件树一致`() {
        val dir = File(assetsRoot, "textmate")
        val themeFiles =
            dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
                ?.map { it.name.removeSuffix(".json") }
                ?.filter { it != "languages" }
                ?.sorted()
                ?: emptyList()
        assertEquals(
            "EditorPalette.allThemes 与 textmate/ 目录必须一致（新增/废弃主题必须同步白名单）",
            themeFiles,
            EditorPalette.allThemes.sorted(),
        )
    }

    @Test
    fun `全部文本承载色对比度大于等于 4 5 比 1`() {
        val failures = mutableListOf<String>()
        for (name in EditorPalette.allThemes) {
            val theme = loadTheme(name)
            val bg = theme.background ?: error("$name 缺少背景色")
            for ((slot, fg) in theme.textColors) {
                if (fg == bg) {
                    failures += "$name [$slot]: 前景 $fg 与背景 $bg 相同（禁止混淆）"
                    continue
                }
                val ratio = contrast(fg, bg)
                if (ratio < MIN_CONTRAST) {
                    failures += "$name [$slot]: $fg 对比 $bg = ${"%.2f".format(ratio)} < $MIN_CONTRAST"
                }
            }
        }
        assertTrue(
            "对比度门禁失败（全部 token ≥ 4.5:1，注释/行号不豁免）：\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    // ---------- 主题模型与解析 ----------

    private class ParsedTheme(
        val name: String,
        val background: String?,
        /** 文本承载槽：槽名 → 前景色。 */
        val textColors: List<Pair<String, String>>,
    )

    private fun loadTheme(name: String): ParsedTheme {
        val file = File(assetsRoot, "textmate/$name.json")
        assertTrue("主题资产缺失: ${file.path}", file.isFile)
        val root = JsonParser(file.readText()).parse() as Map<*, *>
        return if (root.containsKey("tokenColors")) {
            parseVscTheme(name, root)
        } else {
            parseTmTheme(name, root)
        }
    }

    private fun parseVscTheme(
        name: String,
        root: Map<*, *>,
    ): ParsedTheme {
        val colors = (root["colors"] as? Map<*, *>) ?: error("$name 缺少 colors 段")
        val bg = colors["editor.background"] as? String
        val textColors = mutableListOf<Pair<String, String>>()
        listOf(
            "editor.foreground",
            "editorLineNumber.foreground",
            "editorLineNumber.activeForeground",
            "editorWhitespace.foreground",
            "editor.highlightedDelimiters.foreground",
        ).forEach { key ->
            (colors[key] as? String)?.let { textColors += key to it }
        }
        val tokenColors = (root["tokenColors"] as? List<*>) ?: emptyList<Any>()
        tokenColors.forEachIndexed { index, raw ->
            val entry = raw as? Map<*, *> ?: return@forEachIndexed
            val settings = entry["settings"] as? Map<*, *> ?: return@forEachIndexed
            val fg = settings["foreground"] as? String ?: return@forEachIndexed
            textColors += "tokenColors[$index]" to fg
        }
        return ParsedTheme(name, bg, textColors)
    }

    private fun parseTmTheme(
        name: String,
        root: Map<*, *>,
    ): ParsedTheme {
        val settings = (root["settings"] as? List<*>) ?: error("$name 缺少 settings 数组")
        val global = (settings.firstOrNull() as? Map<*, *>)?.get("settings") as? Map<*, *>
            ?: error("$name 缺少全局 settings 段")
        val bg = global["background"] as? String
        val textColors = mutableListOf<Pair<String, String>>()
        listOf(
            "foreground",
            "lineNumber",
            "lineNumberActiveForeground",
            "invisibles",
            "highlightedDelimitersForeground",
        ).forEach { key ->
            (global[key] as? String)?.let { textColors += key to it }
        }
        settings.drop(1).forEachIndexed { index, raw ->
            val entry = raw as? Map<*, *> ?: return@forEachIndexed
            val entrySettings = entry["settings"] as? Map<*, *> ?: return@forEachIndexed
            val fg = entrySettings["foreground"] as? String ?: return@forEachIndexed
            textColors += "settings[${index + 1}]" to fg
        }
        return ParsedTheme(name, bg, textColors)
    }

    // ---------- WCAG 相对亮度与对比度 ----------

    private fun relativeLuminance(hex: String): Double {
        val (r, g, b) = parseRgb(hex)
        fun linear(c: Double): Double =
            if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }

    private fun contrast(
        fg: String,
        bg: String,
    ): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** 解析 #RRGGBB 或 #RRGGBBAA（alpha 键不参与对比度，仅校验存在）。 */
    private fun parseRgb(hex: String): Triple<Double, Double, Double> {
        val clean = hex.removePrefix("#")
        require(clean.length == 6 || clean.length == 8) { "非法颜色值: $hex" }
        fun channel(offset: Int): Double = clean.substring(offset, offset + 2).toInt(16) / 255.0
        return Triple(channel(0), channel(2), channel(4))
    }

    // ---------- 极简 JSON 解析器（测试专用：仅覆盖主题 JSON 结构） ----------

    private class JsonParser(text: String) {
        private val text = if (text.isNotEmpty() && text[0] == '\uFEFF') text.substring(1) else text
        private var pos = 0

        fun parse(): Any {
            skipWs()
            return parseValue()
        }

        private fun parseValue(): Any =
            when {
                peek() == '{' -> parseObject()
                peek() == '[' -> parseArray()
                peek() == '"' -> parseString()
                text.startsWith("true", pos) -> { pos += 4; true }
                text.startsWith("false", pos) -> { pos += 5; false }
                text.startsWith("null", pos) -> { pos += 4; "null" }
                else -> parseNumber()
            }

        private fun parseObject(): Map<String, Any> {
            expect('{')
            val map = linkedMapOf<String, Any>()
            skipWs()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                expect(':')
                skipWs()
                map[key] = parseValue()
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> error("JSON 对象解析失败（第 $pos 字符）: 期望 , 或 }")
                }
            }
        }

        private fun parseArray(): List<Any> {
            expect('[')
            val list = mutableListOf<Any>()
            skipWs()
            if (peek() == ']') {
                pos++
                return list
            }
            while (true) {
                skipWs()
                list += parseValue()
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> error("JSON 数组解析失败（第 $pos 字符）: 期望 , 或 ]")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < text.length) {
                val c = text[pos++]
                if (c == '"') return sb.toString()
                if (c == '\\') {
                    val esc = text[pos++]
                    sb.append(
                        when (esc) {
                            'n' -> '\n'
                            't' -> '\t'
                            'r' -> '\r'
                            'b' -> '\b'
                            'f' -> '\u000C'
                            'u' -> {
                                val hex = text.substring(pos, pos + 4)
                                pos += 4
                                hex.toInt(16).toChar()
                            }
                            else -> esc
                        },
                    )
                } else {
                    sb.append(c)
                }
            }
            error("JSON 字符串未闭合")
        }

        private fun parseNumber(): Double {
            val start = pos
            while (pos < text.length && text[pos] in "0123456789+-.eE") pos++
            return text.substring(start, pos).toDouble()
        }

        private fun skipWs() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        private fun peek(): Char = text[pos]

        private fun expect(c: Char) {
            if (pos >= text.length || text[pos] != c) error("JSON 解析失败（第 $pos 字符）: 期望 $c")
            pos++
        }
    }
}
