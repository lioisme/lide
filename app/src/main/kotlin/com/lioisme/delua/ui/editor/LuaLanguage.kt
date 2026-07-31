package com.lioisme.delua.ui.editor

import android.os.Bundle
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.widget.CodeEditor

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
    }
}
