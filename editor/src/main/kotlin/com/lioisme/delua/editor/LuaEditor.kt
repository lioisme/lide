package com.lioisme.delua.editor

import android.util.Log
import android.util.TypedValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/**
 * 纯编辑器组件（零宿主依赖）。
 *
 * 线程模型：
 * - [Content] 由宿主在 IO 线程构造并注入，本组件经 `setText(Content)` 复用路径接入，
 *   跳过 `new Content(CharSequence)` 的 O(n) 重建（对齐 Squircle `DocumentRepositoryImpl`）；
 * - 文本快照（[Content.toString]）内部持有 ReadWriteLock，可在后台线程安全执行；
 *   因此诊断 / 兜底保存快照一律在 [Dispatchers.Default] 上获取；
 * - 编辑器 API 调用（setText / diagnostics 赋值 / subscribeEvent 回调）保持主线程；
 * - 补全 / 诊断 / 事件回调全部 try-catch(Throwable) 降级，异常不得逃逸进 sora 主循环。
 *
 * 重组模型（关键）：
 * - 编辑事件只递增 [diagTick]（StateFlow，非 Compose state）——稳态打字零重组；
 * - 诊断经 `diagTick.collectLatest` 500ms 防抖，后台线程增量扫描（[LuaDiagnostics.Incremental]）；
 * - 文件切换以 Content 身份比较触发 `setText(Content)` 复用，不重建组件。
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
private const val LARGE_FILE_UNDO_STACK = 200
private const val DEFAULT_UNDO_STACK = 2500
private const val DIAGNOSTIC_DEBOUNCE_MS = 500L
private const val TAG = "LuaEditor"

@Composable
fun LuaEditor(
    content: Content,
    isDarkTheme: Boolean,
    config: EditorConfig,
    onContentModified: () -> Unit,
    onUndoRedoStateChanged: (canUndo: Boolean, canRedo: Boolean) -> Unit,
    onRelease: (content: String) -> Unit,
    editorRef: MutableState<CodeEditor?>,
    modifier: Modifier = Modifier,
) {
    val initReady = rememberTextMateInitializer()
    val currentOnContentModified by rememberUpdatedState(onContentModified)
    val currentOnUndoRedoStateChanged by rememberUpdatedState(onUndoRedoStateChanged)
    val currentOnRelease by rememberUpdatedState(onRelease)

    // rememberUpdatedState：事件回调 / factory / update 块中读取「最新」值而不触发重组。
    val currentContent = rememberUpdatedState(content)

    // 诊断节流信号：StateFlow 不触发重组；collectLatest 提供 500ms 防抖。
    val diagTick = remember { MutableStateFlow(0) }
    val syntaxChecker = remember { LuaDiagnostics.Incremental() }
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
                tabWidth = config.tabSize
                setBlockLineWidth(3f)

                isUndoEnabled = true

                applyNonPrintableFlags(config)

                setTextSize(config.fontSize.toFloat())

                var lastCanUndo = false
                var lastCanRedo = false
                subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                    try {
                        when (event.action) {
                            ContentChangeEvent.ACTION_INSERT, ContentChangeEvent.ACTION_DELETE -> {
                                // 记录编辑行区间供增量诊断；sora 事件回调位于主线程，
                                // Incremental.onEdit 内部同步，线程安全。
                                syntaxChecker.onEdit(event.changeStart.line, event.changeEnd.line)
                                currentOnContentModified()
                                if (currentContent.value.length <= LARGE_FILE_THRESHOLD) diagTick.value++
                            }
                            ContentChangeEvent.ACTION_SET_NEW_TEXT -> {
                                diagnostics = null
                                syntaxChecker.onNewText()
                                lastDiagSignature = ""
                                if (currentContent.value.length <= LARGE_FILE_THRESHOLD) diagTick.value++
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

                setText(currentContent.value)
            }
        },
        update = { editor ->
            // 视图就绪后发布到宿主。写入发生在 update 阶段而非 factory 组合期，
            // 同一实例只发布一次，避免组合期间的状态写竞争。
            if (editorRef.value !== editor) {
                editorRef.value = editor
            }
            val contentValue = currentContent.value
            val isLargeFile = contentValue.length > LARGE_FILE_THRESHOLD
            val expectedWordWrap = config.wordWrap && !isLargeFile
            if (editor.isWordwrap != expectedWordWrap) {
                editor.isWordwrap = expectedWordWrap
            }
            if (editor.isHighlightBracketPair != !isLargeFile) {
                editor.isHighlightBracketPair = !isLargeFile
            }
            if (editor.isHighlightCurrentBlock != !isLargeFile) {
                editor.isHighlightCurrentBlock = !isLargeFile
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

    // 文件切换：Content 复用路径（跳过 O(n) 重建）；同一实例不重复设置。
    // 每份 Content 的 undo 栈深度按文件大小降级，避免大文件内存膨胀。
    LaunchedEffect(content) {
        content.undoManager.setMaxUndoStackSize(
            if (content.length > LARGE_FILE_THRESHOLD) LARGE_FILE_UNDO_STACK else DEFAULT_UNDO_STACK
        )
        val editor = editorRef.value ?: return@LaunchedEffect
        if (editor.text !== content) {
            editor.setText(content)
        }
    }

    // 500ms 防抖诊断：后台线程增量扫描 + 签名比对去重。
    LaunchedEffect(Unit) {
        diagTick.collectLatest {
            if (it == 0) return@collectLatest
            delay(DIAGNOSTIC_DEBOUNCE_MS)
            val editor = editorRef.value ?: return@collectLatest
            val (regions, signature) = withContext(Dispatchers.Default) {
                val source = editor.text.toString()
                if (source.length > LARGE_FILE_THRESHOLD) return@withContext emptyList<DiagnosticRegion>() to ""
                val result = syntaxChecker.check(source)
                result to diagnosticSignature(result)
            }
            if (signature == lastDiagSignature) return@collectLatest
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
