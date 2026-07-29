package com.delua.ui.editor

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.delua.androlua.engine.LuaRunner
import com.delua.ui.theme.LocalIsDarkTheme
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
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

    LaunchedEffect(isDarkMode) {
        if (initReady) {
            val themeName = if (isDarkMode) "darcula" else "quietlight"
            ThemeRegistry.getInstance().setTheme(themeName)
        }
    }

    if (!initReady) return

    AndroidView(
        factory = { ctx ->
            CodeEditor(ctx).apply {
                val tmLanguage = TextMateLanguage.create("source.lua", true)
                tmLanguage.setAutoCompleteEnabled(true)
                tmLanguage.setCompleterKeywords(
                    arrayOf(
                        "and", "break", "do", "else", "elseif", "end", "false",
                        "for", "function", "goto", "if", "in", "local", "nil",
                        "not", "or", "repeat", "return", "then", "true", "until", "while",
                        "print", "require", "type", "pairs", "ipairs", "tostring", "tonumber",
                        "table", "string", "math", "io", "os", "coroutine", "debug",
                    )
                )
                setEditorLanguage(tmLanguage)

                colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                isLineNumberEnabled = true
                isHighlightCurrentLine = true
                isHighlightBracketPair = true
                tabWidth = 4

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
            if (editor.text.toString() != content) {
                editor.setText(content)
            }
        },
        modifier = modifier,
    )
}

fun updateDiagnostics(editor: CodeEditor, source: String, luaRunner: LuaRunner) {
    val errors = luaRunner.checkSyntax(source)
    if (errors.isEmpty()) {
        editor.diagnostics = null
        return
    }
    val container = DiagnosticsContainer()
    val text = editor.text
    for (error in errors) {
        val line = error.line - 1
        val startIndex = text.getCharIndex(line, 0)
        val endIndex = text.getCharIndex(line, text.getColumnCount(line))
        container.addDiagnostic(
            DiagnosticRegion(startIndex, endIndex, DiagnosticRegion.SEVERITY_ERROR)
        )
    }
    editor.diagnostics = container
}