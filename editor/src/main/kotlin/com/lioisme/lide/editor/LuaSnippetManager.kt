package com.lioisme.lide.editor

import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.lang.completion.snippet.CodeSnippet
import io.github.rosemoe.sora.lang.completion.snippet.parser.CodeSnippetParser
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor

private data class LuaSnippet(
    val prefix: String,
    val label: String,
    val body: String,
    val description: String,
)

/**
 * Lua 片段补全（任务单 G2：原生 SnippetController）。
 *
 * 片段体为 VS Code 语法（`${1:name}` tab-stop / `${0}` 结尾 / 同名 tab-stop 镜像），
 * 由 sora 原生 [CodeSnippetParser] 解析，经 [SnippetController.startSnippet] 启动
 * tab-stop 逐位导航（Tab/Shift+Tab 由 sora 原生按键处理），不再手写占位符剥离文本。
 *
 * 性能：解析惰性执行一次（首次补全时），打字热路径零开销；执行均 try/catch 降级。
 */
object LuaSnippetManager {
    private val snippets: List<LuaSnippet> =
        listOf(
            LuaSnippet(
                "fun",
                "function",
                """function ${'$'}{1:name}(${'$'}{2:args})
    ${'$'}{3:-- body}
end${'$'}{0}""",
                "Define a function",
            ),
            LuaSnippet(
                "for",
                "for i",
                """for ${'$'}{1:i}=${'$'}{2:1}, ${'$'}{3:10} do
    ${'$'}{4:-- body}
end${'$'}{0}""",
                "Numeric for loop",
            ),
            LuaSnippet(
                "fori",
                "for k,v",
                """for ${'$'}{1:k}, ${'$'}{2:v} in ${'$'}{3:pairs}(${'$'}{4:t}) do
    ${'$'}{5:-- body}
end${'$'}{0}""",
                "Generic for loop",
            ),
            LuaSnippet(
                "if",
                "if",
                """if ${'$'}{1:condition} then
    ${'$'}{2:-- body}
end${'$'}{0}""",
                "If statement",
            ),
            LuaSnippet(
                "ifel",
                "if else",
                """if ${'$'}{1:condition} then
    ${'$'}{2:-- body}
else
    ${'$'}{3:-- body}
end${'$'}{0}""",
                "If-else statement",
            ),
            LuaSnippet(
                "while",
                "while",
                """while ${'$'}{1:condition} do
    ${'$'}{2:-- body}
end${'$'}{0}""",
                "While loop",
            ),
            LuaSnippet(
                "repeat",
                "repeat",
                """repeat
    ${'$'}{1:-- body}
until ${'$'}{2:condition}${'$'}{0}""",
                "Repeat-until loop",
            ),
            LuaSnippet(
                "localfun",
                "local function",
                """local function ${'$'}{1:name}(${'$'}{2:args})
    ${'$'}{3:-- body}
end${'$'}{0}""",
                "Local function definition",
            ),
            LuaSnippet(
                "require",
                "require",
                """local ${'$'}{1:name} = require("${'$'}{2:module}")${'$'}{0}""",
                "Module require",
            ),
            LuaSnippet(
                "class",
                "class (OOP)",
                """${'$'}{1:ClassName} = {${'$'}{2:-- fields}}
${'$'}{1:ClassName}.__index = ${'$'}{1:ClassName}

function ${'$'}{1:ClassName}.new(${'$'}{3:args})
    local self = setmetatable({}, ${'$'}{1:ClassName})
    ${'$'}{4:-- init}
    return self
end${'$'}{0}""",
                "Table-based OOP pattern",
            ),
        )

    private val completionItems: List<CompletionItem> by lazy {
        // 惰性解析：首次补全时执行一次（分析线程），失败降级为空片段，不阻断补全窗。
        val parsedByLabel = HashMap<String, CodeSnippet>(snippets.size)
        snippets.map { snippet ->
            LuaSnippetCompletionItem(
                snippet = snippet,
                parsed = parsedByLabel.getOrPut(snippet.label) { parseSafely(snippet.body) },
            )
        }
    }

    fun toCompletionItems(): List<CompletionItem> = completionItems

    private fun parseSafely(body: String): CodeSnippet =
        try {
            CodeSnippetParser.parse(body)
        } catch (t: Throwable) {
            CodeSnippetParser.parse("")
        }
}

/**
 * 原生 snippet 补全项（任务单 G2）。
 *
 * 与 [SimpleSnippetCompletionItem] 的差异：prefix 删除长度在点击时按实际词首计算
 * （`SimpleSnippetCompletionItem` 用固定 [SnippetDescription.selectedLength]，
 * 键入「fun」选「function」时会误删 4 个多余字符）。
 */
private class LuaSnippetCompletionItem(
    private val snippet: LuaSnippet,
    private val parsed: CodeSnippet,
) : CompletionItem(snippet.label, snippet.description, null) {
    init {
        kind(CompletionItemKind.Snippet)
    }

    override fun performCompletion(
        editor: CodeEditor,
        content: Content,
        position: CharPosition,
    ) {
        try {
            val lineText = content.getLine(position.line)
            val caretIndex = position.index
            val wordStart =
                if (position.column <= lineText.length) {
                    val from = lineText.take(position.column).indexOfLast { it == ' ' || it == '\t' }
                    if (from < 0) {
                        content.getCharIndex(position.line, 0)
                    } else {
                        content.getCharIndex(position.line, from + 1)
                    }
                } else {
                    caretIndex
                }
            if (wordStart < caretIndex) {
                content.delete(wordStart, caretIndex)
            }
            editor.snippetController.startSnippet(wordStart, parsed, "")
        } catch (t: Throwable) {
            // 降级：snippet 启动失败不影响编辑器状态，异常不逃逸进 sora 主循环。
        }
    }

    override fun performCompletion(
        editor: CodeEditor,
        content: Content,
        line: Int,
        column: Int,
    ) {
        // EditorAutoCompletion 走 CharPosition 重载；此重载无需实现。
    }
}
