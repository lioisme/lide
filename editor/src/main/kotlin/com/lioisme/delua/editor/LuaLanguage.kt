package com.lioisme.delua.editor

import android.os.Bundle
import android.util.Log
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * Lua 语言支持：TextMate 高亮 + 关键字 / 片段补全。
 *
 * 补全回调运行在编辑器的分析线程上，任何异常都必须被吞掉并降级
 * （最多损失本次补全建议），不得让异常逃逸进 sora 主循环。
 */
class LuaLanguage(collectIdentifiers: Boolean) :
    TextMateLanguage(
        GrammarRegistry.getInstance().findGrammar("source.lua"),
        GrammarRegistry.getInstance().findLanguageConfiguration("source.lua"),
        GrammarRegistry.getInstance(),
        ThemeRegistry.getInstance(),
        collectIdentifiers,
    ) {

    private var editor: CodeEditor? = null

    fun attachEditor(editor: CodeEditor) {
        this.editor = editor
    }

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle,
    ) {
        try {
            super.requireAutoComplete(content, position, publisher, extraArguments)

            val editor = this.editor ?: return
            if (!isAutoCompleteEnabled) return

            val line = content.getLine(position.line)
            val prefix = if (position.column <= line.length) {
                line.substring(0, position.column).takeLastWhile { c ->
                    c.isLetterOrDigit() || c == '_'
                }
            } else ""

            if (prefix.length >= 1) {
                LuaSnippetManager.toCompletionItems()
                    .filter { it.label.startsWith(prefix, ignoreCase = true) }
                    .forEach { publisher.addItem(it) }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "requireAutoComplete degraded", t)
        }
    }

    private companion object {
        const val TAG = "LuaLanguage"
    }
}
