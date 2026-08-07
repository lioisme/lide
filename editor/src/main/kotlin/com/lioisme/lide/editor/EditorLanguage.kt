package com.lioisme.lide.editor

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
 * 通用 TextMate 语言支持（多语言化：按 scope 装配文法、语言配置、关键字）。
 *
 * 补全回调运行在编辑器的分析线程上，任何异常都必须被吞掉并降级
 * （最多损失本次补全建议），不得让异常逃逸进 sora 主循环。
 *
 * [collectIdentifiers] 决定是否启用「文档内标识符补全」（sora
 * [IdentifierAutoComplete]）：开启后分析器在后台收集全部标识符，
 * 大文件（>200KB）下成本显著，由宿主设置默认关闭并在大文件下强制关闭。
 *
 * Lua 场景额外叠加 [LuaSnippetManager] 的片段补全（既有 WP-012 行为）；
 * 其余语言仅使用静态关键字表 + 标识符补全（需求「实现 sora 原生支持的
 * 通用能力，lua 优先」）。
 */
class EditorLanguage(
    val scopeName: String,
    collectIdentifiers: Boolean,
) : TextMateLanguage(
        GrammarRegistry.getInstance().findGrammar(scopeName),
        GrammarRegistry.getInstance().findLanguageConfiguration(scopeName),
        GrammarRegistry.getInstance(),
        ThemeRegistry.getInstance(),
        collectIdentifiers,
    ) {
    private var editor: CodeEditor? = null

    fun attachEditor(editor: CodeEditor) {
        this.editor = editor
    }

    /**
     * 一次应用全部语言级设置（对应任务单 §7 映射表）。
     *
     * 全部为 sora 原生 setter（字段赋值级成本），值不变时无副作用；
     * 在 setEditorLanguage 装配后调用一次，不随 Compose 重组重复执行。
     */
    fun configure(config: EditorConfig) {
        useTab(config.useTab)
        getNewlineHandler().isEnabled = config.autoIndent
        getSymbolPairs().setEnabled(config.autoCloseBrackets)
        setAutoCompleteEnabled(config.autoCompletion)
        setTabSize(config.tabSize)
    }

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle,
    ) {
        try {
            super.requireAutoComplete(content, position, publisher, extraArguments)

            if (scopeName != LanguageDetector.LUA_SCOPE) return
            val editor = this.editor ?: return
            if (!isAutoCompleteEnabled) return

            val line = content.getLine(position.line)
            val prefix =
                if (position.column <= line.length) {
                    line.substring(0, position.column).takeLastWhile(Char::isIdentifierChar)
                } else {
                    ""
                }

            if (prefix.length >= 1) {
                LuaSnippetManager
                    .toCompletionItems()
                    .filter { it.label.startsWith(prefix, ignoreCase = true) }
                    .forEach { publisher.addItem(it) }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "requireAutoComplete degraded", t)
        }
    }

    private companion object {
        const val TAG = "EditorLanguage"
    }
}
