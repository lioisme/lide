package com.lioisme.lide.util

/**
 * 帮助手册解析器（WP-018）：`assets/help.txt` 的 @章节@ 结构纯函数解析。
 *
 * 文件格式（对齐 pro help.lua 的章节分隔惯例）：
 * - 单独成行且以 `@` 开头、`@` 结尾的行 = 章节标题（首个匹配 `@` 起至行尾）；
 * - 其余非空行 = 章节正文（逐行保留，空白行作为段落分隔被丢弃）；
 * - 正文内嵌的 `@` 不构成标题（要求标题独占一行）。
 */
object HelpParser {
    data class Section(
        val title: String,
        val lines: List<String>,
    )

    private val HEADER_REGEX = Regex("^@(.+)@\\s*$")

    fun parse(raw: String): List<Section> {
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
        val sections = mutableListOf<Section>()
        var currentTitle: String? = null
        var currentLines = mutableListOf<String>()
        normalized.lineSequence().forEach { line ->
            val header =
                HEADER_REGEX
                    .matchEntire(line)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
            if (header != null) {
                finishSection(sections, currentTitle, currentLines)
                currentTitle = header
                currentLines = mutableListOf()
            } else if (line.isNotBlank()) {
                currentLines.add(line.trimEnd())
            }
        }
        finishSection(sections, currentTitle, currentLines)
        return sections
    }

    private fun finishSection(
        sections: MutableList<Section>,
        title: String?,
        lines: MutableList<String>,
    ) {
        val t = title ?: return
        if (lines.isNotEmpty()) {
            sections.add(Section(t, lines))
        }
    }
}
