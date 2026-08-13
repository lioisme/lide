package com.lioisme.lide.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 编辑器主题一致性门禁（2026-08-13 Squircle 对齐拍板：编辑器高亮与 Squircle CE 完全一致）。
 *
 * 旧「逐 token WCAG 4.5:1」门禁已随 2026-08-13 拍板替换（Squircle 源 token 色
 * 有弱对比项，用户明确「高亮与 Squircle 完全一致」，对比度要求让位于一致性；
 * 宿主 chrome 槽门禁保留在 [com.lioisme.lide.ui.theme.ThemeContrastTest]）。
 *
 * 本门禁断言（硬性契约）：
 * 1. 白名单：[EditorPalette.allThemes] 与 assets/textmate/ 目录 JSON 集一致；
 * 2. **与 Squircle 源完全一致（双向）**：9 款主题的
 *    - colors 段全键 → settings[0].settings 同值（映射表见下方）；
 *    - tokenColors → settings[1..] 逐项 scope + foreground + fontStyle 配平
 *      （Squircle 无 scope 的全局首项 = settings[0] 承载，跳过）；
 *    源基线 = `app/src/test/resources/squircle-themes/`（转换脚本导入，CI 无
 *    reference/ 也可跑）；
 * 3. 背景预览：[EditorPalette.themeBackgrounds] 与各主题 JSON 背景同源；
 * 4. 明暗声明：[EditorPalette.isDarkTheme] 与主题背景亮度一致。
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

    private val squircleBaselineRoot =
        listOf(
            File("src/test/resources/squircle-themes"),
            File("editor/src/test/resources/squircle-themes"),
        ).firstOrNull { it.isDirectory }
            ?: throw IllegalStateException("squircle-themes baseline not found (cwd=${File("").absolutePath})")

    /** Squircle colors 键 → TextMate settings[0] 键（与 scripts/convert-squircle-themes.ps1 同一映射表）。 */
    private val colorParity: List<Pair<String, List<String>>> =
        listOf(
            "editor.background" to listOf("background"),
            "editor.foreground" to listOf("foreground"),
            "editor.cursor" to listOf("caret"),
            "editor.selection.background" to listOf("selection"),
            "editor.currentLine.background" to listOf("lineHighlight"),
            "editor.whitespace" to listOf("invisibles"),
            "editor.lineNumber.foreground" to listOf("lineNumber"),
            "editor.lineNumber.activeForeground" to listOf("lineNumberActiveForeground"),
            "editor.lineNumber.divider" to listOf("lineNumberDivider"),
            "editor.indentGuide.background" to listOf("indentGuideBackground"),
            "editor.indentGuide.activeBackground" to listOf("indentGuideActiveBackground"),
            "editor.highlightedDelimiters.foreground" to listOf("highlightedDelimitersForeground"),
            "editor.highlightedDelimiters.background" to listOf("highlightedDelimitersBackground"),
            "editor.matchHighlight.background" to listOf("findMatchBackground"),
            "editor.popupWindow.background" to listOf("completionWindowBackground", "tooltipBackground"),
            "editor.popupWindow.activeBackground" to listOf("completionWindowBackgroundCurrent"),
        )

    private class ParsedTheme(
        val name: String,
        val background: String?,
        /** 文本承载槽：槽名 → 前景色（parseVscTheme / parseTmTheme 产出的同结构集合）。 */
        val globalColors: Map<String, String>,
        /** token 规则（保持源顺序）：scope 串 → settings。 */
        val tokenRules: List<Pair<String, Map<String, String>>>,
    )

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
    fun `主题背景预览与主题 JSON 同源`() {
        assertEquals(
            "themeCount 与 themeBackgrounds 必须同长",
            EditorPalette.themeCount,
            EditorPalette.themeBackgrounds.size,
        )
        EditorPalette.themes.forEachIndexed { index, entry ->
            val bg = loadTheme(entry.name).background ?: error("${entry.name} 缺少背景色")
            assertEquals(
                "主题[$index] ${entry.name} 背景必须与 JSON 背景一致",
                parseArgb(bg),
                EditorPalette.themeBackgrounds[index],
            )
        }
    }

    @Test
    fun `行号文字预览与主题 JSON 同源`() {
        assertEquals("themeCount 与 themeLineNumbers 必须同长", EditorPalette.themeCount, EditorPalette.themeLineNumbers.size)
        assertEquals(
            "themeCount 与 themeLineNumbersActive 必须同长",
            EditorPalette.themeCount,
            EditorPalette.themeLineNumbersActive.size,
        )
        EditorPalette.themes.forEachIndexed { index, entry ->
            val theme = loadTheme(entry.name)
            val lineNumber = theme.globalColors["lineNumber"]
                ?: error("${entry.name} 缺少 lineNumber 键（行号随主题契约）")
            val lineNumberActive = theme.globalColors["lineNumberActiveForeground"]
                ?: error("${entry.name} 缺少 lineNumberActiveForeground 键")
            assertEquals(
                "主题[$index] ${entry.name} 行号色必须与 JSON lineNumber 一致",
                parseArgb(lineNumber),
                EditorPalette.themeLineNumbers[index],
            )
            assertEquals(
                "主题[$index] ${entry.name} 当前行号色必须与 JSON lineNumberActiveForeground 一致",
                parseArgb(lineNumberActive),
                EditorPalette.themeLineNumbersActive[index],
            )
        }
    }

    @Test
    fun `主题明暗声明与背景亮度一致`() {
        EditorPalette.themes.forEachIndexed { index, entry ->
            val bg = loadTheme(entry.name).background ?: error("${entry.name} 缺少背景色")
            val lum = relativeLuminance(bg)
            assertTrue(
                "主题[$index] ${entry.name}: isDark=${entry.isDark} 与背景亮度 $lum 矛盾",
                if (entry.isDark) lum <= 0.5 else lum > 0.5,
            )
        }
    }

    @Test
    fun `全部主题与 Squircle 源完全一致`() {
        val failures = mutableListOf<String>()
        for (name in EditorPalette.allThemes) {
            val lide = loadTheme(name)
            val squircle = loadSquircleSource(name)

            // colors 段全键配对
            for ((srcKey, dstKeys) in colorParity) {
                val srcValue = squircle.globalColors[srcKey]
                for (dstKey in dstKeys) {
                    val dstValue = lide.globalColors[dstKey]
                    if (srcValue == null) {
                        if (dstValue != null) {
                            failures += "$name: 源缺 $srcKey 但 LIDE 有 $dstKey=$dstValue"
                        }
                        continue
                    }
                    if (srcValue != dstValue) {
                        failures += "$name: $srcKey($srcValue) != settings[$dstKey]($dstValue)"
                    }
                }
            }

            // tokenColors 配平（LIDE settings[1..] ↔ Squircle tokenColors 跳过无 scope 首项）
            val lideRules = lide.tokenRules
            val srcRules = squircle.tokenRules
            if (lideRules.size != srcRules.size) {
                failures += "$name: token 规则数不一致 LIDE=${lideRules.size} Squircle=${srcRules.size}"
            }
            val common = minOf(lideRules.size, srcRules.size)
            for (i in 0 until common) {
                val (lideScope, lideSettings) = lideRules[i]
                val (srcScope, srcSettings) = srcRules[i]
                if (lideScope != srcScope) {
                    failures += "$name: token[$i] scope 不一致 LIDE=[$lideScope] Squircle=[$srcScope]"
                }
                val allKeys = (lideSettings.keys + srcSettings.keys).distinct().sorted()
                for (key in allKeys) {
                    val lv = lideSettings[key]
                    val sv = srcSettings[key]
                    if (lv != sv) {
                        failures += "$name: token[$i] [$lideScope] $key 不一致 LIDE=$lv Squircle=$sv"
                    }
                }
            }
        }
        assertTrue(
            "与 Squircle 源一致性门禁失败（改动主题 JSON 必须过门禁）：\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    /** #RRGGBB → ARGB Int（与 themeBackgrounds 常量同形式）。 */
    private fun parseArgb(hex: String): Int {
        val clean = hex.removePrefix("#")
        return 0xFF000000.toInt() or clean.substring(0, 6).toInt(16)
    }

    // ---------- 主题模型与解析 ----------

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

    private fun loadSquircleSource(name: String): ParsedTheme {
        val file = File(squircleBaselineRoot, "$name.json")
        assertTrue("Squircle 源基线缺失: ${file.path}", file.isFile)
        val root = JsonParser(file.readText()).parse() as Map<*, *>
        return parseVscTheme(name, root)
    }

    private fun parseVscTheme(
        name: String,
        root: Map<*, *>,
    ): ParsedTheme {
        val colors = (root["colors"] as? Map<*, *>) ?: error("$name 缺少 colors 段")
        val bg = colors["editor.background"] as? String
        val globalColors =
            colors.entries
                .filter { it.key as? String in colorParity.map { p -> p.first } }
                .associate { it.key.toString() to it.value.toString() }
        val tokenRules = mutableListOf<Pair<String, Map<String, String>>>()
        val tokenColors = (root["tokenColors"] as? List<*>) ?: emptyList<Any>()
        tokenColors.forEach { raw ->
            val entry = raw as? Map<*, *> ?: return@forEach
            val scope = entry["scope"] as? String ?: return@forEach // 无 scope 全局首项跳过
            val settings = entry["settings"] as? Map<*, *> ?: return@forEach
            val rule = linkedMapOf<String, String>()
            (settings["foreground"] as? String)?.let { rule["foreground"] = it }
            (settings["fontStyle"] as? String)?.let { rule["fontStyle"] = it }
            tokenRules += scope to rule
        }
        return ParsedTheme(name, bg, globalColors, tokenRules)
    }

    private fun parseTmTheme(
        name: String,
        root: Map<*, *>,
    ): ParsedTheme {
        val settings = (root["settings"] as? List<*>) ?: error("$name 缺少 settings 数组")
        val global = (settings.firstOrNull() as? Map<*, *>)?.get("settings") as? Map<*, *>
            ?: error("$name 缺少全局 settings 段")
        val bg = global["background"] as? String
        val globalColors = linkedMapOf<String, String>()
        global.forEach { (k, v) -> globalColors[k.toString()] = v.toString() }
        val tokenRules = mutableListOf<Pair<String, Map<String, String>>>()
        settings.drop(1).forEach { raw ->
            val entry = raw as? Map<*, *> ?: return@forEach
            val scope = entry["scope"] as? String ?: return@forEach
            val entrySettings = entry["settings"] as? Map<*, *> ?: return@forEach
            val rule = linkedMapOf<String, String>()
            (entrySettings["foreground"] as? String)?.let { rule["foreground"] = it }
            (entrySettings["fontStyle"] as? String)?.let { rule["fontStyle"] = it }
            tokenRules += scope to rule
        }
        return ParsedTheme(name, bg, globalColors, tokenRules)
    }

    // ---------- WCAG 相对亮度（明暗判定用） ----------

    private fun relativeLuminance(hex: String): Double {
        val (r, g, b) = parseRgb(hex)
        fun linear(c: Double): Double =
            if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }

    /** 解析 #RRGGBB 或 #RRGGBBAA（alpha 不参与亮度）。 */
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