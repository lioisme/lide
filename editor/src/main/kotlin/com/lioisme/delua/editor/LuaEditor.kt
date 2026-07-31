package com.lioisme.delua.editor

import android.util.Log
import android.util.TypedValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 纯编辑器组件（零宿主依赖）。
 *
 * 线程模型：
 * - 文本快照（[io.github.rosemoe.sora.text.Content.toString]）内部持有 ReadWriteLock，
 *   可在后台线程安全执行；因此诊断 / 整文件快照一律在 [Dispatchers.Default] 上获取；
 * - 编辑器 API 调用（setText / diagnostics 赋值 / subscribeEvent 回调）保持主线程；
 * - 补全 / 诊断 / 事件回调全部 try-catch(Throwable) 降级，异常不得逃逸进 sora 主循环。
 */
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

private const val LARGE_FILE_THRESHOLD = 200 * 1024
private const val TAG = "LuaEditor"

@Composable
fun LuaEditor(
    text: String,
    isDarkTheme: Boolean,
    config: EditorConfig,
    onContentModified: () -> Unit,
    onUndoRedoStateChanged: (canUndo: Boolean, canRedo: Boolean) -> Unit,
    onRelease: (content: String) -> Unit,
    editorRef: MutableState<CodeEditor?>,
    modifier: Modifier = Modifier,
) {
    val initReady = rememberTextMateInitializer()
    val currentText by rememberUpdatedState(text)
    val currentOnContentModified by rememberUpdatedState(onContentModified)
    val currentOnUndoRedoStateChanged by rememberUpdatedState(onUndoRedoStateChanged)
    val currentOnRelease by rememberUpdatedState(onRelease)
    var diagVersion by remember { mutableStateOf(0) }
    var lastDiagSignature by remember { mutableStateOf("") }

    LaunchedEffect(isDarkTheme, initReady) {
        if (initReady) {
            ThemeRegistry.getInstance().setTheme(if (isDarkTheme) "darcula" else "quietlight")
        }
    }

    if (!initReady) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    AndroidView(
        factory = { ctx ->
            CodeEditor(ctx).apply outer@{
                val isLargeFile = currentText.length > LARGE_FILE_THRESHOLD

                // 官方 demo 顺序：colorScheme → language → setText，
                // 使分析器重启都发生在空文本阶段，setText 成为唯一一次全量分析。
                colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())

                val lang = LuaLanguage(collectIdentifiers = false).apply {
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

                isLineNumberEnabled = true
                isHighlightCurrentLine = true
                isHighlightBracketPair = !isLargeFile
                tabWidth = config.tabSize

                isHighlightCurrentBlock = !isLargeFile
                setBlockLineWidth(3f)

                isUndoEnabled = true
                this@outer.text.undoManager.setMaxUndoStackSize(2500)

                isWordwrap = config.wordWrap && !isLargeFile

                applyNonPrintableFlags(config)

                setTextSize(config.fontSize.toFloat())

                var lastCanUndo = false
                var lastCanRedo = false
                subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                    try {
                        when (event.action) {
                            ContentChangeEvent.ACTION_INSERT, ContentChangeEvent.ACTION_DELETE -> {
                                currentOnContentModified()
                                if (!isLargeFile) diagVersion++
                            }
                            ContentChangeEvent.ACTION_SET_NEW_TEXT -> {
                                diagnostics = null
                                if (!isLargeFile) diagVersion++
                            }
                        }
                        val canU = canUndo()
                        val canR = canRedo()
                        if (canU != lastCanUndo || canR != lastCanRedo) {
                            lastCanUndo = canU
                            lastCanRedo = canR
                            currentOnUndoRedoStateChanged(canU, canR)
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "content change callback failed", t)
                    }
                }

                setText(currentText)
            }
        },
        update = { editor ->
            // 视图就绪后发布到宿主。写入发生在 update 阶段而非 factory 组合期，
            // 同一实例只发布一次，避免组合期间的状态写竞争。
            if (editorRef.value !== editor) {
                editorRef.value = editor
            }
            val isLargeFile = currentText.length > LARGE_FILE_THRESHOLD
            val expectedWordWrap = config.wordWrap && !isLargeFile
            if (editor.isWordwrap != expectedWordWrap) {
                editor.isWordwrap = expectedWordWrap
            }
            if (editor.tabWidth != config.tabSize) {
                editor.tabWidth = config.tabSize
            }
            val expectedFlags = nonPrintableFlags(config)
            if (editor.nonPrintablePaintingFlags != expectedFlags) {
                editor.nonPrintablePaintingFlags = expectedFlags
            }
            val expectedTextSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                config.fontSize.toFloat(),
                editor.resources.displayMetrics,
            )
            if (editor.textSizePx != expectedTextSizePx) {
                editor.setTextSize(config.fontSize.toFloat())
            }
        },
        modifier = modifier,
    )

    LaunchedEffect(text) {
        val editor = editorRef.value ?: return@LaunchedEffect
        val current = withContext(Dispatchers.Default) { editor.text.toString() }
        if (current != text) {
            editor.setText(text)
        }
    }

    LaunchedEffect(diagVersion) {
        if (diagVersion == 0) return@LaunchedEffect
        delay(500)
        val editor = editorRef.value ?: return@LaunchedEffect
        val (regions, signature) = withContext(Dispatchers.Default) {
            val source = editor.text.toString()
            if (source.length > LARGE_FILE_THRESHOLD) return@withContext emptyList<DiagnosticRegion>() to ""
            val result = LuaDiagnostics.checkSyntax(source)
            result to diagnosticSignature(result)
        }
        if (signature == lastDiagSignature) return@LaunchedEffect
        lastDiagSignature = signature
        try {
            editor.diagnostics = if (regions.isEmpty()) null
            else DiagnosticsContainer().apply {
                addDiagnostics(regions)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "apply diagnostics failed", t)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val editor = editorRef.value
            if (editor != null) {
                // 兜底保存：编辑内容最后一次快照交给宿主持久化。
                currentOnRelease(editor.text.toString())
                editorRef.value = null
            }
        }
    }
}

private fun diagnosticSignature(regions: List<DiagnosticRegion>): String = buildString {
    for (r in regions) {
        append(r.startIndex).append(':')
        append(r.endIndex).append(':')
        append(r.severity).append(':')
        append(r.detail?.briefMessage).append('|')
    }
}

private fun nonPrintableFlags(config: EditorConfig): Int {
    return if (config.showWhitespace)
        CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or
        CodeEditor.FLAG_DRAW_WHITESPACE_INNER or
        CodeEditor.FLAG_DRAW_WHITESPACE_TRAILING or
        CodeEditor.FLAG_DRAW_WHITESPACE_FOR_EMPTY_LINE or
        CodeEditor.FLAG_DRAW_LINE_SEPARATOR or
        CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION or
        (if (config.wordWrap) CodeEditor.FLAG_DRAW_SOFT_WRAP else 0)
    else 0
}

private fun CodeEditor.applyNonPrintableFlags(config: EditorConfig) {
    setNonPrintablePaintingFlags(nonPrintableFlags(config))
}
