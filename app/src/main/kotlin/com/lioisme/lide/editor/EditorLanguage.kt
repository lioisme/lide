package com.lioisme.lide.editor

import android.content.Context
import android.os.Bundle
import io.github.rosemoe.sora.lang.completion.CompletionHelper
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger("EditorLanguage")

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

    /** C2 附带（2026-08-12）：snippet 描述本地化用的应用 context。
     *  主线程 attach 时快照（经参数注入，满足 R4），补全回调不触碰
     *  `CodeEditor.getContext()`（lint WrongThread：分析线程推断为 worker）。 */
    private var appContext: Context? = null

    fun attachEditor(
        editor: CodeEditor,
        context: Context,
    ) {
        this.editor = editor
        this.appContext = context
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

            // D4（2026-08-12）：前缀计算复用 sora 原生 CompletionHelper.computePrefix
            // （超类 TextMateLanguage.requireAutoComplete 同款），替代手写
            // takeLastWhile + substring 双分配；语义一致（identifier 字符回溯）。
            val prefix = CompletionHelper.computePrefix(content, position) { it.isIdentifierChar() }

            if (prefix.length >= 1) {
                // C2：描述本地化——经 attach 时快照的 context 解析 snippet 描述资源
                // （分析线程不得调 CodeEditor.getContext，lint WrongThread 守护）。
                val context = appContext ?: return
                LuaSnippetManager
                    .toCompletionItems(context)
                    .filter { it.label.startsWith(prefix, ignoreCase = true) }
                    .forEach { publisher.addItem(it) }
            }
        } catch (t: Throwable) {
            logger.warn(t) { "requireAutoComplete degraded" }
        }
    }
}
