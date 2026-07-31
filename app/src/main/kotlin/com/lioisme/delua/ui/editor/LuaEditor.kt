package com.lioisme.delua.ui.editor

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.lioisme.delua.ui.theme.LocalIsDarkTheme
import com.lioisme.delua.ui.theme.SettingsManager
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.dsl.languages
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource

private object TextMateInitializer {
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true

        FileProviderRegistry.getInstance().addFileProvider(
            AssetsFileResolver(context.assets)
        )

        val themeRegistry = ThemeRegistry.getInstance()
        for (name in listOf("quietlight", "darcula")) {
            val path = "textmate/$name.json"
            try {
                val input = FileProviderRegistry.getInstance().tryGetInputStream(path)
                if (input != null) {
                    themeRegistry.loadTheme(
                        ThemeModel(IThemeSource.fromInputStream(input, path, null), name)
                    )
                }
            } catch (_: Exception) { }
        }
        themeRegistry.setTheme("quietlight")

        GrammarRegistry.getInstance().loadGrammars(languages {
            language("lua") {
                grammar = "textmate/lua/syntaxes/lua.tmLanguage.json"
                scopeName = "source.lua"
                languageConfiguration = "textmate/lua/language-configuration.json"
            }
        })
    }
}

@Composable
fun rememberTextMateInitializer(): Boolean {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            TextMateInitializer.initialize(context.applicationContext)
        }
        ready = true
    }
    return ready
}

@Composable
fun LuaEditor(
    content: String,
    onContentChanged: (String) -> Unit,
    onUndoRedoStateChanged: (canUndo: Boolean, canRedo: Boolean) -> Unit,
    editorRef: MutableState<CodeEditor?>,
    modifier: Modifier = Modifier,
) {
    val isDarkMode = LocalIsDarkTheme.current
    val initReady = rememberTextMateInitializer()
    val currentContent by rememberUpdatedState(content)
    val currentOnContentChanged by rememberUpdatedState(onContentChanged)
    val currentOnUndoRedoStateChanged by rememberUpdatedState(onUndoRedoStateChanged)
    var lastTheme by remember { mutableStateOf<String?>(null) }
    val editorConfig by SettingsManager.editorConfig.collectAsState()

    if (initReady) {
        val targetTheme = if (isDarkMode) "darcula" else "quietlight"
        if (lastTheme != targetTheme) {
            lastTheme = targetTheme
            ThemeRegistry.getInstance().setTheme(targetTheme)
        }
    }

    if (!initReady) return

    AndroidView(
        factory = { ctx ->
            CodeEditor(ctx).apply outer@{
                val lang = LuaLanguage(true).apply {
                    setAutoCompleteEnabled(true)
                    setCompleterKeywords(
                        arrayOf(
                            "and", "break", "do", "else", "elseif", "end", "false",
                            "for", "function", "goto", "if", "in", "local", "nil",
                            "not", "or", "repeat", "return", "then", "true", "until", "while",
                            "print", "require", "type", "pairs", "ipairs", "tostring", "tonumber",
                            "table", "string", "math", "io", "os", "coroutine", "debug",
                        )
                    )
                    attachEditor(this@outer)
                }
                setEditorLanguage(lang)

                colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                isLineNumberEnabled = true
                isHighlightCurrentLine = true
                isHighlightBracketPair = true
                tabWidth = editorConfig.tabSize

                isHighlightCurrentBlock = true
                setHighlightCurrentBlock(true)
                setBlockLineWidth(3f)

                props.stickyScroll = true
                props.stickyScrollMaxLines = 3
                props.stickyScrollPreferInnerScope = true
                props.stickyScrollAutoCollapse = true

                isUndoEnabled = true
                text.undoManager.maxUndoStackSize = 2500

                isWordwrap = editorConfig.wordWrap

                applyNonPrintableFlags(editorConfig)

                setTextSize(editorConfig.fontSize.toFloat())

                setText(content)

                subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                    val newText = text.toString()
                    if (newText != currentContent) {
                        currentOnContentChanged(newText)
                    }
                    currentOnUndoRedoStateChanged(canUndo(), canRedo())
                }

                editorRef.value = this
            }
        },
        update = { editor ->
            editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
            if (editor.text.toString() != content) {
                editor.setText(content)
            }
            if (editor.isWordwrap != editorConfig.wordWrap) {
                editor.isWordwrap = editorConfig.wordWrap
            }
            editor.applyNonPrintableFlags(editorConfig)
            editor.tabWidth = editorConfig.tabSize
            editor.setTextSize(editorConfig.fontSize.toFloat())
        },
        modifier = modifier,
    )

    DisposableEffect(Unit) {
        onDispose {
            editorRef.value = null
        }
    }
}

private fun CodeEditor.applyNonPrintableFlags(config: SettingsManager.EditorConfig) {
    setNonPrintablePaintingFlags(
        if (config.showWhitespace)
            CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or
            CodeEditor.FLAG_DRAW_WHITESPACE_INNER or
            CodeEditor.FLAG_DRAW_WHITESPACE_TRAILING or
            CodeEditor.FLAG_DRAW_WHITESPACE_FOR_EMPTY_LINE or
            CodeEditor.FLAG_DRAW_LINE_SEPARATOR or
            CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION or
            (if (config.wordWrap) CodeEditor.FLAG_DRAW_SOFT_WRAP else 0)
        else 0
    )
}

fun updateDiagnostics(editor: CodeEditor, source: String) {
    editor.diagnostics = null
}
