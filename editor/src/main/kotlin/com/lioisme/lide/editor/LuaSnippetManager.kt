package com.lioisme.lide.editor

import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor

private data class LuaSnippet(
    val prefix: String,
    val label: String,
    val body: String,
    val description: String,
)

object LuaSnippetManager {

    private val PLACEHOLDER_WITH_DEFAULT = Regex("""\$\{\d+:([^}]*)\}""")
    private val PLACEHOLDER = Regex("""\$\{\d+\}""")

    private val snippets: List<LuaSnippet> = listOf(
        LuaSnippet("fun", "function", """function ${'$'}{1:name}(${'$'}{2:args})
    ${'$'}{3:-- body}
end${'$'}{0}""", "Define a function"),

        LuaSnippet("for", "for i", """for ${'$'}{1:i}=${'$'}{2:1}, ${'$'}{3:10} do
    ${'$'}{4:-- body}
end${'$'}{0}""", "Numeric for loop"),

        LuaSnippet("fori", "for k,v", """for ${'$'}{1:k}, ${'$'}{2:v} in ${'$'}{3:pairs}(${'$'}{4:t}) do
    ${'$'}{5:-- body}
end${'$'}{0}""", "Generic for loop"),

        LuaSnippet("if", "if", """if ${'$'}{1:condition} then
    ${'$'}{2:-- body}
end${'$'}{0}""", "If statement"),

        LuaSnippet("ifel", "if else", """if ${'$'}{1:condition} then
    ${'$'}{2:-- body}
else
    ${'$'}{3:-- body}
end${'$'}{0}""", "If-else statement"),

        LuaSnippet("while", "while", """while ${'$'}{1:condition} do
    ${'$'}{2:-- body}
end${'$'}{0}""", "While loop"),

        LuaSnippet("repeat", "repeat", """repeat
    ${'$'}{1:-- body}
until ${'$'}{2:condition}${'$'}{0}""", "Repeat-until loop"),

        LuaSnippet("localfun", "local function", """local function ${'$'}{1:name}(${'$'}{2:args})
    ${'$'}{3:-- body}
end${'$'}{0}""", "Local function definition"),

        LuaSnippet("require", "require", """local ${'$'}{1:name} = require("${'$'}{2:module}")${'$'}{0}""", "Module require"),

        LuaSnippet("class", "class (OOP)", """${'$'}{1:ClassName} = {${'$'}{2:-- fields}}
${'$'}{1:ClassName}.__index = ${'$'}{1:ClassName}

function ${'$'}{1:ClassName}.new(${'$'}{3:args})
    local self = setmetatable({}, ${'$'}{1:ClassName})
    ${'$'}{4:-- init}
    return self
end${'$'}{0}""", "Table-based OOP pattern"),
    )

    fun clearSnippetSyntax(body: String): String {
        return body.replace(PLACEHOLDER_WITH_DEFAULT) { match ->
            match.groupValues[1]
        }.replace(PLACEHOLDER, "")
    }

    private val completionItems: List<CompletionItem> by lazy {
        snippets.map { snippet ->
            object : SimpleCompletionItem(
                snippet.label,
                snippet.description,
                null,
                snippet.prefix.length,
                clearSnippetSyntax(snippet.body),
            ) {
                override fun performCompletion(
                    editor: CodeEditor,
                    content: Content,
                    line: Int,
                    column: Int,
                ) {
                    val lineText = content.getLine(line)
                    val lineStart = content.getCharIndex(line, 0)
                    val caretIndex = content.getCharIndex(line, column)

                    val wordStart = lineText.take(column).indexOfLast { it == ' ' || it == '\t' }
                    val replaceFrom = if (wordStart < 0) lineStart
                    else content.getCharIndex(line, wordStart + 1)

                    val indent = lineText.takeWhile { it == ' ' || it == '\t' }
                    val cleanBody = clearSnippetSyntax(snippet.body)
                    val indentedBody = cleanBody.lines().joinToString("\n") { lineContent ->
                        if (lineContent.isNotEmpty() && (lineContent[0] == ' ' || lineContent[0] == '\t')) {
                            "$indent$lineContent"
                        } else {
                            lineContent
                        }
                    }

                    content.replace(replaceFrom, caretIndex, indentedBody)
                }
            }
        }
    }

    fun toCompletionItems(): List<CompletionItem> {
        return completionItems
    }
}
