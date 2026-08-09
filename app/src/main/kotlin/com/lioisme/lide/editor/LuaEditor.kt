package com.lioisme.lide.editor

import com.lioisme.lide.R

import android.content.Context
import android.util.Log
import android.util.TypedValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.minimap.MinimapConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

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
 * - 编辑事件只递增 [EditorViewPool.diagTick]（StateFlow，非 Compose state）——稳态打字零重组；
 * - 诊断经 `diagTick.collectLatest` 500ms 防抖，后台线程增量扫描（[LuaDiagnostics.Incremental]）；
 * - 文件切换以 Content 身份比较触发 `setText(Content)` 复用，不重建组件。
 *
 * 视图池（关键，防入口卡顿）：
 * - [CodeEditor] 为进程级单实例（[EditorViewPool]），首次创建后跨页面进出复用，
 *   每次进入编辑器只支付 setText + 布局，不再重建视图 / 语言 / 主题方案（实测省 ~100ms/次）；
 * - 事件回调经池内桥接字段按次注入（attachCallbacks），订阅只建立一次；
 * - 主题切换由 [ThemeRegistry] 广播给缓存的 [TextMateColorScheme]，无需重建。
 *
 * 配置同步（P0 设置中心）：
 * - 全部设置经 [EditorViewPool.applyConfig] 守卫式 diff（值不变不赋值），
 *   防止每键 setter 触发全量重排；
 * - 语言级设置（useTab / 缩进 / 闭合对 / 补全 / tabSize）一次应用；
 * - 标识符补全（collectIdentifiers）为构造级参数，设置变化时重建语言
 *   （一次全量分析，仅设置变更时发生）；
 * - 大文件降级矩阵（§6.3）：wordwrap / 括号高亮 / 当前块 / minimap / stickyScroll /
 *   光标动画 / 行号面板随 >200KB 强制关闭，标识符补全强制关。
 */
private const val LARGE_FILE_THRESHOLD = 200 * 1024
private const val LARGE_FILE_UNDO_STACK = 200
private const val DEFAULT_UNDO_STACK = 2500
private const val DIAGNOSTIC_DEBOUNCE_MS = 500L
private const val TAG = "LuaEditor"

/** Lua scope：词法诊断仅作用于 Lua 文法。未知扩展名文件落 [LanguageDetector.PLAIN_SCOPE]。 */

/**
 * 编辑器视图池：进程级单实例 [CodeEditor] + 单次事件订阅 + 进程级诊断状态。
 *
 * 进入编辑器只做「桥接回调 + setText + 配置 diff」，视图创建 / 语言装配 /
 * 主题方案构建仅首次支付。事件回调经 [attachCallbacks] 按次注入，订阅只建立一次；
 * 组合销毁（页面退出）不销毁视图，仅由宿主停用（AndroidView 自动分离，onRelease 兜底保存）。
 *
 * 线程：全部状态主线程访问；[diagTick] 为 StateFlow，事件回调（主线程）写入。
 * [applyConfig] 为主线程调用（Compose AndroidView update 阶段），内部有守卫。
 */
internal object EditorViewPool {
    /** 编辑事件节流信号：每键 +1，不触发重组；宿主经 collectLatest 防抖消费。 */
    val diagTick = MutableStateFlow(0)

    /** 当前诊断消息渲染器（Q15）：UI 组合期注入资源版；引用变化时重建校验器（语言/组合重建场景）。 */
    @Volatile
    private var diagProvider: DiagnosticMessageProvider = LuaDiagnostics.DEFAULT_PROVIDER

    @Volatile
    private var syntaxChecker: LuaDiagnostics.Incremental = LuaDiagnostics.Incremental()

    private var lastDiagSignature = ""
    /** 编辑器实例（Q13 单例收敛）：私有 + 主线程独占，禁止跨线程引用或逃逸。 */
    private var editor: CodeEditor? = null

    /** 语言装配期是否已开启标识符补全（主线程读 / 写，决定是否重建语言）。 */
    private var languageIdentifiersEnabled = false

    /** 最近一次已应用的语言级配置快照（symbolPairs 无 getter，diff 用快照比对）。 */
    private var lastAppliedConfig: EditorConfig? = null

    private val contentModifiedRef = AtomicReference<() -> Unit>({})
    private val undoRedoRef = AtomicReference<(Boolean, Boolean) -> Unit>({ _, _ -> })

    /** 注入本次组合的回调（进入编辑器时调用一次）。 */
    fun attachCallbacks(
        onContentModified: () -> Unit,
        onUndoRedoStateChanged: (Boolean, Boolean) -> Unit,
    ) {
        contentModifiedRef.set(onContentModified)
        undoRedoRef.set(onUndoRedoStateChanged)
    }

    /** 后台线程增量校验入口（诊断防抖协程调用，内部同步线程安全）。 */
    fun syntaxCheck(
        source: String,
        quickfixSource: Content? = null,
        messageProvider: DiagnosticMessageProvider = LuaDiagnostics.DEFAULT_PROVIDER,
    ): List<DiagnosticRegion> {
        // Q15：资源 provider 引用变化（组合重建/语言切换）时重建校验器（含其增量状态），
        // 首查全量扫描；同一组合内引用稳定（LuaEditor 组合期 remember 构造）。
        if (messageProvider !== diagProvider) {
            diagProvider = messageProvider
            syntaxChecker = LuaDiagnostics.Incremental(messageProvider)
        }
        return syntaxChecker.check(source, quickfixSource)
    }

    /** 最近一次已应用的诊断签名（主线程读）。 */
    fun lastSignature(): String = lastDiagSignature

    /**
     * 装配 EditorLanguage（acquire 首次创建与 applyConfig 重建共用）：
     * 构造级参数（collectIdentifiers）+ 关键字表 + 语言级配置 + 编辑器引用。
     */
    private fun createLanguage(
        scope: String,
        config: EditorConfig,
        collectIdentifiers: Boolean,
        editor: CodeEditor,
    ): EditorLanguage =
        EditorLanguage(scope, collectIdentifiers = collectIdentifiers).apply {
            setAutoCompleteEnabled(true)
            setCompleterKeywords(LanguageKeywordRegistry.keywordsFor(scope))
            configure(config)
            attachEditor(editor)
        }

    /** 记录已应用签名（主线程写）。 */
    fun updateSignature(signature: String) {
        lastDiagSignature = signature
    }

    /**
     * 守卫式配置同步（主线程，AndroidView update 阶段调用）。
     *
     * 原则：读当前值比对，值不变不赋值（防每键 setter 全量重排）。
     * 语言级字段以 [lastAppliedConfig] 快照比对（symbolPairs 无 getter）；
     * 视图级字段直接读 editor getter。
     *
     * [isLargeFile] 为 >200KB 降级矩阵开关：高成本功能强制关闭，与设置值无关。
     */
    fun applyConfig(
        editor: CodeEditor,
        config: EditorConfig,
        scope: String?,
        isLargeFile: Boolean,
    ) {
        val applied = lastAppliedConfig
        // scope 为 null（宿主未提供文件名）时按纯文本处理，不再以 Lua 兜底。
        val resolvedScope = scope ?: LanguageDetector.PLAIN_SCOPE
        val effectiveIdentifiers = config.identifierCompletion && !isLargeFile
        val lang = editor.editorLanguage as? EditorLanguage

        if (
            lang == null ||
            lang.scopeName != resolvedScope ||
            effectiveIdentifiers != languageIdentifiersEnabled
        ) {
            // 语言或标识符补全（构造级参数）变化时重建语言（一次全量分析，仅设置变更发生）。
            val newLang =
                createLanguage(resolvedScope, config, effectiveIdentifiers, editor).also {
                    // setEditorLanguage 会清空 diagnostics：强制诊断重跑并复位签名。
                    updateSignature("")
                    diagTick.value++
                }
            editor.setEditorLanguage(newLang)
            languageIdentifiersEnabled = effectiveIdentifiers
        } else if (lang != null) {
            if (applied == null || applied.useTab != config.useTab) lang.useTab(config.useTab)
            if (applied == null || applied.autoIndent != config.autoIndent) {
                lang.newlineHandler.isEnabled = config.autoIndent
            }
            if (applied == null || applied.autoCloseBrackets != config.autoCloseBrackets) {
                lang.symbolPairs.setEnabled(config.autoCloseBrackets)
            }
            if (applied == null || applied.autoCompletion != config.autoCompletion) {
                lang.setAutoCompleteEnabled(config.autoCompletion)
            }
            if (applied == null || applied.tabSize != config.tabSize) lang.setTabSize(config.tabSize)
        }

        // === 视图级守卫式同步（含大文件降级矩阵，任务单 §6.3） ===
        val expectedWordWrap = config.wordWrap && !isLargeFile
        if (editor.isWordwrap != expectedWordWrap) {
            editor.isWordwrap = expectedWordWrap
        }
        if (editor.isHighlightBracketPair != (config.highlightBracketPair && !isLargeFile)) {
            editor.isHighlightBracketPair = config.highlightBracketPair && !isLargeFile
        }
        if (editor.isHighlightCurrentBlock != (config.highlightCurrentBlock && !isLargeFile)) {
            editor.isHighlightCurrentBlock = config.highlightCurrentBlock && !isLargeFile
        }
        if (editor.isHighlightCurrentLine != config.highlightCurrentLine) {
            editor.isHighlightCurrentLine = config.highlightCurrentLine
        }
        if (editor.isLineNumberEnabled != config.lineNumbers) {
            editor.isLineNumberEnabled = config.lineNumbers
        }
        if (editor.tabWidth != config.tabSize) {
            editor.tabWidth = config.tabSize
        }
        if (editor.props.showMinimap != (config.minimap && !isLargeFile)) {
            editor.props.showMinimap = config.minimap && !isLargeFile
        }
        if (editor.props.stickyScroll != (config.stickyScroll && !isLargeFile)) {
            editor.props.stickyScroll = config.stickyScroll && !isLargeFile
        }
        if (editor.isCursorAnimationEnabled != (config.cursorAnimation && !isLargeFile)) {
            editor.isCursorAnimationEnabled = config.cursorAnimation && !isLargeFile
        }
        if (editor.isDisplayLnPanel != (config.lnPanel && !isLargeFile)) {
            editor.isDisplayLnPanel = config.lnPanel && !isLargeFile
        }
        if (editor.isScalable != config.scalable) {
            editor.isScalable = config.scalable
        }
        if (editor.isEditable != !config.readOnly) {
            editor.isEditable = !config.readOnly
        }
        if (editor.isDisableSoftKbdIfHardKbdAvailable != config.disableSoftKbdIfHardKbd) {
            editor.setDisableSoftKbdIfHardKbdAvailable(config.disableSoftKbdIfHardKbd)
        }
        val expectedFlags = nonPrintableFlags(config)
        if (editor.nonPrintablePaintingFlags != expectedFlags) {
            editor.nonPrintablePaintingFlags = expectedFlags
        }
        val expectedTextSizePx =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                config.fontSize.toFloat(),
                editor.resources.displayMetrics,
            )
        if (editor.textSizePx != expectedTextSizePx) {
            editor.setTextSize(config.fontSize.toFloat())
        }
        lastAppliedConfig = config
    }

    /** 获取（必要时创建）池化编辑器。首次创建完成一次性装配与订阅。 */
    fun acquire(
        context: Context,
        config: EditorConfig,
        scope: String? = null,
    ): CodeEditor {
        editor?.let { return it }
        // 使用 applicationContext 持有池化视图：CodeEditor 不绑定窗口/主题资源，
        // 避免旋转等 Activity 重建时池仍强引用旧 Activity（内存泄漏）。
        val ed =
            CodeEditor(context.applicationContext).apply outer@{
                // 官方 demo 顺序：colorScheme → language → setText，
                // 使分析器重启都发生在空文本阶段，setText 成为唯一一次全量分析。
                colorScheme = TextMateRuntime.colorScheme()

                val resolvedScope = scope ?: LanguageDetector.PLAIN_SCOPE
                val lang = createLanguage(resolvedScope, config, config.identifierCompletion, this@outer)
                languageIdentifiersEnabled = config.identifierCompletion
                setEditorLanguage(lang)

                isLineNumberEnabled = true
                isHighlightCurrentLine = true
                tabWidth = config.tabSize
                setBlockLineWidth(3f)

                isUndoEnabled = true

                applyNonPrintableFlags(config)

                setTextSize(config.fontSize.toFloat())

                // 迷你地图字符块模式（任务单 G8：0.24.6 原生，默认关由 applyConfig 控制）。
                props.minimapConfig = MinimapConfig(minimapDrawTextAsBlocks = true)

                var lastCanUndo = false
                var lastCanRedo = false
                subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                    try {
                        when (event.action) {
                            ContentChangeEvent.ACTION_INSERT, ContentChangeEvent.ACTION_DELETE -> {
                                // 记录编辑行区间供增量诊断；sora 事件回调位于主线程，
                                // Incremental.onEdit 内部同步，线程安全。
                                syntaxChecker.onEdit(event.changeStart.line, event.changeEnd.line)
                                contentModifiedRef.get().invoke()
                                if (text.length <= LARGE_FILE_THRESHOLD) diagTick.value++
                            }

                            ContentChangeEvent.ACTION_SET_NEW_TEXT -> {
                                diagnostics = null
                                syntaxChecker.onNewText()
                                lastDiagSignature = ""
                                if (text.length <= LARGE_FILE_THRESHOLD) diagTick.value++
                            }
                        }
                        val canU = canUndo()
                        val canR = canRedo()
                        if (canU != lastCanUndo || canR != lastCanRedo) {
                            lastCanUndo = canU
                            lastCanRedo = canR
                            undoRedoRef.get().invoke(canU, canR)
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "content change callback failed", t)
                    }
                }
            }
        lastAppliedConfig = config
        editor = ed
        return ed
    }
}

/**
 * TextMate 运行时就绪状态（C-4：单一服务 [TextMateRuntime] 消费侧）。
 * 宿主进程启动已预热（幂等），编辑器只需消费状态：
 * - Loading：展示 spinner；
 * - Failed：展示错误态 + 重试（不再伪装就绪）；
 * - Ready：进入编辑器装配。
 */
@Composable
fun rememberTextMateState(): TextMateRuntime.RuntimeState {
    val context = LocalContext.current
    val runtimeState by TextMateRuntime.state.collectAsState()
    // 兜底触发：宿主未预热（如单元测试宿主）时此处首次触发初始化。
    LaunchedEffect(Unit) {
        TextMateRuntime.preload(context.applicationContext)
    }
    return runtimeState
}

@Composable
fun LuaEditor(
    content: Content,
    isDarkTheme: Boolean,
    config: EditorConfig,
    fileName: String?,
    onContentModified: () -> Unit,
    onUndoRedoStateChanged: (canUndo: Boolean, canRedo: Boolean) -> Unit,
    onRelease: () -> Unit,
    onEditorBound: () -> Unit = {},
    editorRef: AtomicReference<CodeEditor?>,
    modifier: Modifier = Modifier,
) {
    val runtimeState = rememberTextMateState()
    val appContext = LocalContext.current.applicationContext
    val currentOnContentModified by rememberUpdatedState(onContentModified)
    val currentOnUndoRedoStateChanged by rememberUpdatedState(onUndoRedoStateChanged)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val currentOnEditorBound by rememberUpdatedState(onEditorBound)
    val currentConfig by rememberUpdatedState(config)
    // 文件 → 语言 scope 由 :editor 解析（文本级能力，宿主只传文件名）。
    // 传入值可能是完整路径，先取 basename 再按扩展名/精确名映射。
    val resolvedScope = remember(fileName) {
        val base = fileName?.substringAfterLast('/')?.substringAfterLast('\\')
        if (base.isNullOrEmpty()) LanguageDetector.PLAIN_SCOPE else LanguageDetector.detect(base)
    }
    // 诊断收集器（LaunchedEffect(Unit) 长驻）不得捕获首帧 scope：
    // remember(fileName) 值变更后，经 rememberUpdatedState 读取最新值（防 P0-4 stale closure）。
    val currentResolvedScope by rememberUpdatedState(resolvedScope)

    // Q15：诊断消息本地化渲染器（组合期 remember 稳定引用——同一组合内引用不变，
    // EditorViewPool 据引用比较复用校验器与增量状态；资源模板带 %1$s 参数占位）。
    val diagMessageProvider =
        remember {
            object : DiagnosticMessageProvider {
                override fun message(key: String, vararg args: Any): String {
                    val resId =
                        when (key) {
                            MSG_UNCLOSED_LONG_COMMENT -> R.string.diag_unclosed_long_comment
                            MSG_UNCLOSED_LONG_STRING -> R.string.diag_unclosed_long_string
                            MSG_UNCLOSED_QUOTED_STRING -> R.string.diag_unclosed_quoted_string
                            MSG_UNMATCHED_CLOSE -> R.string.diag_unmatched_close
                            MSG_UNCLOSED_TOKEN -> R.string.diag_unclosed_token
                            MSG_EXCESS_TOKEN -> R.string.diag_excess_token
                            MSG_AT_LINE -> R.string.diag_at_line
                            MSG_FIX_DELETE_TOKEN -> R.string.fix_delete_token
                            MSG_FIX_APPEND_LINE_END -> R.string.fix_append_line_end
                            MSG_FIX_APPEND_FILE_END -> R.string.fix_append_file_end
                            else -> return key
                        }
                    return appContext.getString(resId, *args)
                }
            }
        }

    // rememberUpdatedState：事件回调 / factory / update 块中读取「最新」值而不触发重组。
    val currentContent = rememberUpdatedState(content)

    val initReady = runtimeState is TextMateRuntime.RuntimeState.Ready

    LaunchedEffect(isDarkTheme, initReady) {
        if (initReady) {
            ThemeRegistry.getInstance().setTheme(if (isDarkTheme) "darcula" else "quietlight")
        }
    }

    if (!initReady) {
        // C-4：初始化失败展示错误态 + 重试（不再伪装就绪，也不无限转圈）。
        if (runtimeState is TextMateRuntime.RuntimeState.Failed) {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.textmate_init_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { TextMateRuntime.retry(appContext) }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        } else {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    AndroidView(
        factory = { ctx ->
            val editor = EditorViewPool.acquire(ctx, config, resolvedScope)
            EditorViewPool.attachCallbacks(currentOnContentModified, currentOnUndoRedoStateChanged)
            // 池化视图可能残留上次文件内容：进入即按当前 Content 同步，避免闪旧文本。
            if (editor.text !== currentContent.value) {
                editor.setText(currentContent.value)
            }
            editor
        },
        update = { editor ->
            // 视图就绪后发布到宿主。写入发生在 update 阶段而非 factory 组合期，
            // 同一实例只发布一次，避免组合期间的状态写竞争。
            if (editorRef.get() !== editor) {
                editorRef.set(editor)
                // 宿主侧跳行/定位的绑定门控（一次完成，幂等）。
                currentOnEditorBound()
            }
            val contentValue = currentContent.value
            val isLargeFile = contentValue.length > LARGE_FILE_THRESHOLD
            EditorViewPool.applyConfig(editor, currentConfig, resolvedScope, isLargeFile)
        },
        modifier = modifier,
    )

    // 文件切换：Content 复用路径（跳过 O(n) 重建）；同一实例不重复设置。
    // 每份 Content 的 undo 栈深度按文件大小降级，避免大文件内存膨胀。
    LaunchedEffect(content) {
        content.undoManager.setMaxUndoStackSize(
            if (content.length > LARGE_FILE_THRESHOLD) LARGE_FILE_UNDO_STACK else DEFAULT_UNDO_STACK,
        )
        val editor = editorRef.get() ?: return@LaunchedEffect
        if (editor.text !== content) {
            editor.setText(content)
        }
    }

    // 500ms 防抖诊断：后台线程增量扫描 + 签名比对去重。
    // 「行级词法诊断」设置关闭时跳过扫描并清空诊断（重启后签名复位，恢复时自动重跑）。
    LaunchedEffect(Unit) {
        EditorViewPool.diagTick.collectLatest {
            if (it == 0) return@collectLatest
            delay(DIAGNOSTIC_DEBOUNCE_MS)
            val editor = editorRef.get() ?: return@collectLatest
            // 行级词法诊断仅 Lua 文法（LuaDiagnostics 专用解析器）；其他语言跳过。
            if (currentResolvedScope != LanguageDetector.LUA_SCOPE) {
                if (editor.diagnostics != null) {
                    editor.diagnostics = null
                    EditorViewPool.updateSignature("")
                }
                return@collectLatest
            }
            if (!currentConfig.lexicalDiagnostics) {
                if (editor.diagnostics != null) {
                    editor.diagnostics = null
                    EditorViewPool.updateSignature("")
                }
                return@collectLatest
            }
            val (regions, signature) =
                withContext(Dispatchers.Default) {
                    val source = editor.text.toString()
                    if (source.length > LARGE_FILE_THRESHOLD) return@withContext emptyList<DiagnosticRegion>() to ""
                    val result = EditorViewPool.syntaxCheck(source, editor.text, diagMessageProvider)
                    result to diagnosticSignature(result)
                }
            if (signature == EditorViewPool.lastSignature()) return@collectLatest
            EditorViewPool.updateSignature(signature)
            try {
                editor.diagnostics =
                    if (regions.isEmpty()) {
                        null
                    } else {
                        DiagnosticsContainer().apply {
                            addDiagnostics(regions)
                        }
                    }
            } catch (t: Throwable) {
                Log.w(TAG, "apply diagnostics failed", t)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // 兜底保存：宿主持有 Content 引用（会话缓存），无需在此做主线程 O(n) 快照，
            // 由宿主在 IO 线程自行读取并落盘。
            currentOnRelease()
            editorRef.set(null)
        }
    }
}

private fun diagnosticSignature(regions: List<DiagnosticRegion>): String =
    buildString {
        for (r in regions) {
            append(r.startIndex).append(':')
            append(r.endIndex).append(':')
            append(r.severity).append(':')
            append(r.detail?.briefMessage).append('|')
        }
    }

private fun nonPrintableFlags(config: EditorConfig): Int =
    if (config.showWhitespace) {
        CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or
            CodeEditor.FLAG_DRAW_WHITESPACE_INNER or
            CodeEditor.FLAG_DRAW_WHITESPACE_TRAILING or
            CodeEditor.FLAG_DRAW_WHITESPACE_FOR_EMPTY_LINE or
            CodeEditor.FLAG_DRAW_LINE_SEPARATOR or
            CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION or
            (if (config.wordWrap) CodeEditor.FLAG_DRAW_SOFT_WRAP else 0)
    } else {
        0
    }

private fun CodeEditor.applyNonPrintableFlags(config: EditorConfig) {
    setNonPrintablePaintingFlags(nonPrintableFlags(config))
}
