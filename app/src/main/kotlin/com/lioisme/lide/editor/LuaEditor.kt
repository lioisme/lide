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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.minimap.MinimapConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/**
 * 纯编辑器组件（零宿主依赖）。
 *
 * 线程模型：
 * - [EditorContent] 由宿主在 IO 线程构造并注入，本组件经 `setText(Content)` 复用路径接入，
 *   跳过 `new Content(CharSequence)` 的 O(n) 重建（对齐 Squircle `DocumentRepositoryImpl`）；
 * - 文本快照（[EditorContent.snapshot]，内部 ReadWriteLock）可在后台线程安全执行，
 *   诊断 / 兜底保存快照一律在 [Dispatchers.Default] 上获取；
 * - 编辑器 API 调用（setText / diagnostics 赋值 / subscribeEvent 回调）保持主线程；
 * - 补全 / 诊断 / 事件回调全部 try-catch(Throwable) 降级，异常不得逃逸进 sora 主循环。
 *
 * 生命周期（2026-08-11 去池化，对齐 Squircle `compose/CodeEditor.kt`）：
 * - [CodeEditor] 每次进入编辑器组合创建（[remember] 持有 [EditorInstance]），
 *   页面离开经 [DisposableEffect.onDispose] 调 `release()` 销毁——回归朴素生命周期，
 *   无进程级单实例池、无跨页面状态、无 setText(null) 出池修补；
 * - 文件切换不重建实例：同组合内 `setText(Content)` 复用（undo 历史随 Content 保留）；
 * - 主题方案仍为进程级单例（[TextMateRuntime.colorScheme]，AGENTS.md 约定），
 *   [ThemeRegistry] 切换时经 ThemeChangeListener 广播刷新，与实例生命周期解耦。
 *
 * 重组模型与配置同步：
 * - 编辑事件只递增实例级 [diagTick]（StateFlow，非 Compose state）——稳态打字零重组；
 * - 诊断经 `diagTick.collectLatest` 500ms 防抖，后台线程增量扫描（[LuaDiagnostics.Incremental]）；
 * - 配置经 [EditorInstance.applyConfig] 守卫式 diff（值不变不赋值），
 *   语言级与视图级 setter 均只在值变化时触发；
 * - 大文件降级矩阵（>200KB）：wordwrap / 括号高亮 / 当前块 / minimap / stickyScroll
 *   / 光标动画 / 行号面板强制关闭，undo 栈 cap [LARGE_FILE_UNDO_STACK]。
 */
private const val LARGE_FILE_THRESHOLD = 200 * 1024
private const val LARGE_FILE_UNDO_STACK = 200
private const val DIAGNOSTIC_DEBOUNCE_MS = 500L
private const val TAG = "LuaEditor"

// P4（2026-08-11 设置瘦身，对标 Squircle 硬编码常量）：not 设置项。
private const val DEFAULT_UNDO_STACK_SIZE = 500

/**
 * 单编辑器实例的装配与状态宿主：文本级能力（语言装配 / 配置同步 / 行级诊断 /
 * undo-redo 上报），生命周期与所属组合一致（2026-08-11 替代进程级 [EditorViewPool]）。
 *
 * 全部字段实例级、主线程访问；构造仅创建 [CodeEditor] 并完成一次性装配与订阅，
 * 不触碰文本（由宿主经 [setText] 注入，跳过 O(n) 重建）。
 */
private class EditorInstance(
    context: Context,
    initialConfig: EditorConfig,
    initialScope: String,
    private val onContentModified: () -> Unit,
    private val onUndoRedoStateChanged: (Boolean, Boolean) -> Unit,
) {
    /** 编辑事件节流信号：每键 +1，不触发重组；组合层经 collectLatest 防抖消费。 */
    val diagTick = MutableStateFlow(0)

    private var messageProvider: DiagnosticMessageProvider = LuaDiagnostics.DEFAULT_PROVIDER
    private var syntaxChecker: LuaDiagnostics.Incremental = LuaDiagnostics.Incremental()
    private var lastDiagSignature = ""

    /** 最近一次已应用的语言级配置快照（symbolPairs 无 getter，diff 用快照比对）。 */
    private var lastAppliedConfig: EditorConfig? = null

    /** 语言装配期是否已开启标识符补全（决定是否重建语言）。 */
    private var languageIdentifiersEnabled = false

    /**
     * 只读状态快照（applyConfig diff 依据）。
     *
     * sora 的 `isEditable()` 是动态 getter（`editable && !layoutBusy && !isFormatting()`），
     * AndroidView update 阶段 layoutBusy 尚为 true 时 getter 恒返 false，
     * 若 diff 读 getter 会误判「已是只读」跳过 setter，editable 字段保持 true
     * （2026-08-11 实测：全局只读开关无效）。故以本快照比对，构造块亦直接落字段。
     */
    private var lastEditable = true

    /** 已上报的 undo/redo 能力（值未变化不回调，needsUpdate 语义对齐 Squircle）。 */
    private var lastCanUndo = false
    private var lastCanRedo = false

    val editor: CodeEditor =
        CodeEditor(context).apply {
            // 官方 demo 顺序：colorScheme → language → setText，
            // 使分析器重启都发生在空文本阶段，setText 成为唯一一次全量分析。
            colorScheme = TextMateRuntime.colorScheme()

            setEditorLanguage(createLanguage(initialScope, initialConfig, initialConfig.identifierCompletion, this))
            languageIdentifiersEnabled = initialConfig.identifierCompletion

            // 只读初值直接落字段（sora isEditable() 动态 getter 见 lastEditable 注释，
            // 构造期即设好，不依赖 update 阶段 diff）。
            setEditable(!initialConfig.readOnly)
            lastEditable = !initialConfig.readOnly

            isLineNumberEnabled = true
            isHighlightCurrentLine = true
            tabWidth = initialConfig.tabSize
            setBlockLineWidth(3f)
            isUndoEnabled = true
            applyNonPrintableFlags(initialConfig)
            setTextSize(initialConfig.fontSize.toFloat())

            // T5（2026-08-10）sora 上限扩展：初值与守卫式 diff 对齐。
            val initialTypeface = fontFamilyTypeface(initialConfig.fontFamily)
            setTypefaceText(initialTypeface)
            setTypefaceLineNumber(initialTypeface)

            // 迷你地图字符块模式（任务单 G8：0.24.6 原生，默认关由 applyConfig 控制）。
            props.minimapConfig = MinimapConfig(minimapDrawTextAsBlocks = true)

            // P3（2026-08-11）：行号分界线恒为 1dp（sora 原生默认宽度），
            // 颜色由 LideTextMateColorScheme 按主题键映射，不再暴露宽度设置。
            setDividerWidth(dpUnit)

            subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                try {
                    when (event.action) {
                        ContentChangeEvent.ACTION_INSERT, ContentChangeEvent.ACTION_DELETE -> {
                            // 记录编辑行区间供增量诊断；sora 事件回调位于主线程，
                            // Incremental.onEdit 内部同步，线程安全。
                            syntaxChecker.onEdit(event.changeStart.line, event.changeEnd.line)
                            onContentModified()
                            if (text.length <= LARGE_FILE_THRESHOLD) diagTick.value++
                        }

                        ContentChangeEvent.ACTION_SET_NEW_TEXT -> {
                            diagnostics = null
                            syntaxChecker.onNewText()
                            lastDiagSignature = ""
                            if (text.length <= LARGE_FILE_THRESHOLD) diagTick.value++
                        }
                    }
                    syncUndoRedoState()
                } catch (t: Throwable) {
                    Log.w(TAG, "content change callback failed", t)
                }
            }
        }

    init {
        // 首次同步 undo/redo 能力（对齐 Squircle 三时机：attach 立即推送一次），
        // 不依赖下一次编辑事件才纠正按钮灰态。
        syncUndoRedoState()
    }

    /** 重算并上报 undo/redo 能力（主线程）。值未变化不回调（needsUpdate 语义）。 */
    private fun syncUndoRedoState() {
        val canU = editor.canUndo()
        val canR = editor.canRedo()
        if (canU == lastCanUndo && canR == lastCanRedo) return
        lastCanUndo = canU
        lastCanRedo = canR
        try {
            onUndoRedoStateChanged(canU, canR)
        } catch (_: Throwable) {
            // 降级：上报失败不影响编辑器主循环。
        }
    }

    private fun createLanguage(
        scope: String,
        config: EditorConfig,
        collectIdentifiers: Boolean,
        attachTo: CodeEditor,
    ): EditorLanguage =
        EditorLanguage(scope, collectIdentifiers = collectIdentifiers).apply {
            setAutoCompleteEnabled(true)
            setCompleterKeywords(LanguageKeywordRegistry.keywordsFor(scope))
            configure(config)
            attachEditor(attachTo)
        }

    /**
     * 后台线程增量校验入口（诊断防抖协程调用，内部同步线程安全）。
     * 资源 provider 引用变化（组合重建/语言切换）时重建校验器含其增量状态。
     */
    fun syntaxCheck(
        source: String,
        quickfixSource: Content? = null,
        provider: DiagnosticMessageProvider = LuaDiagnostics.DEFAULT_PROVIDER,
    ): List<DiagnosticRegion> {
        if (provider !== messageProvider) {
            messageProvider = provider
            syntaxChecker = LuaDiagnostics.Incremental(provider)
        }
        return syntaxChecker.check(source, quickfixSource)
    }

    fun lastSignature(): String = lastDiagSignature

    fun updateSignature(signature: String) {
        lastDiagSignature = signature
    }

    /**
     * 守卫式配置同步（主线程，AndroidView update 阶段调用）。
     *
     * 原则：读当前值比对，值不变不赋值（防每键 setter 全量重排）。
     * 语言级字段以 [lastAppliedConfig] 快照比对（symbolPairs 无 getter）；
     * 视图级字段直接读 editor getter。只读切换仅落 `isEditable`（sora 原生门禁，
     * setEditable(false) 自行 hideSoftInput + stopSnippet，无额外 IME 修补）。
     *
     * [isLargeFile] 为 >200KB 降级矩阵开关：高成本功能强制关闭，与设置值无关。
     */
    fun applyConfig(
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

        // === 视图级守卫式同步（含大文件降级矩阵 P4） ===
        val expectedWordWrap = config.wordWrap && !isLargeFile
        if (editor.isWordwrap != expectedWordWrap) {
            editor.isWordwrap = expectedWordWrap
        }
        if (editor.isHighlightBracketPair != config.highlightBracketPair && !isLargeFile) {
            editor.isHighlightBracketPair = config.highlightBracketPair
        }
        if (editor.isHighlightCurrentBlock != config.highlightCurrentBlock && !isLargeFile) {
            editor.isHighlightCurrentBlock = config.highlightCurrentBlock
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
        if (editor.props.showMinimap != config.minimap && !isLargeFile) {
            editor.props.showMinimap = config.minimap
        }
        if (editor.props.stickyScroll != config.stickyScroll && !isLargeFile) {
            editor.props.stickyScroll = config.stickyScroll
        }
        if (editor.isScalable != config.scalable) {
            editor.isScalable = config.scalable
        }
        val wantEditable = !config.readOnly
        if (lastEditable != wantEditable) {
            editor.setEditable(wantEditable)
            lastEditable = wantEditable
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

        // === T5（2026-08-10）sora 上限扩展：守卫式 diff，值不变不赋值 ===
        val expectedTypeface = fontFamilyTypeface(config.fontFamily)
        if (editor.typefaceText != expectedTypeface || editor.typefaceLineNumber != expectedTypeface) {
            editor.setTypefaceText(expectedTypeface)
            editor.setTypefaceLineNumber(expectedTypeface)
        }
        lastAppliedConfig = config
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
    content: EditorContent,
    isDarkTheme: Boolean,
    config: EditorConfig,
    fileName: String?,
    onContentModified: () -> Unit,
    onUndoRedoStateChanged: (canUndo: Boolean, canRedo: Boolean) -> Unit,
    onRelease: () -> Unit,
    onEditorBound: () -> Unit = {},
    controller: EditorController,
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
    // remember(fileName) 值变更后，经 rememberUpdatedState 读取最新值（防 stale closure）。
    val currentResolvedScope by rememberUpdatedState(resolvedScope)

    // Q15：诊断消息本地化渲染器（组合期 remember 稳定引用——同一组合内引用不变，
    // EditorInstance 据引用比较复用校验器与增量状态；资源模板带 %1$s 参数占位）。
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

    // 编辑事件节流信号：组合级 remember，实例订阅回调写入（主线程），诊断协程消费。
    val diagTick = remember { MutableStateFlow(0) }

    // 编辑器实例随组合创建/销毁（去池化）：文件切换同组合复用实例 + setText(Content)，
    // 语言切换经 applyConfig 内部 diff 重建；页面离开时 onDispose 调 release() 销毁。
    val instance =
        remember {
            EditorInstance(
                context = appContext,
                initialConfig = currentConfig,
                initialScope = currentResolvedScope,
                onContentModified = { currentOnContentModified() },
                onUndoRedoStateChanged = { u, r -> currentOnUndoRedoStateChanged(u, r) },
            )
        }

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
            // 进入即按当前 Content 同步（同一实例只 set 一次，换文件经 update 路径）。
            if (instance.editor.text !== currentContent.value.raw) {
                instance.editor.setText(currentContent.value.raw)
            }
            instance.editor
        },
        update = { editor ->
            // 视图就绪后经门面发布到宿主（B 级收口：宿主只经 EditorController 访问实例）。
            // 写入发生在 update 阶段而非 factory 组合期，同一实例只绑定一次，
            // 避免组合期间的状态写竞争。
            if (controller.editor !== editor) {
                controller.bind(editor)
                // 宿主侧跳行/定位的绑定门控（一次完成，幂等）。
                currentOnEditorBound()
            }
            val contentValue = currentContent.value
            val isLargeFile = contentValue.raw.length > LARGE_FILE_THRESHOLD
            instance.applyConfig(currentConfig, currentResolvedScope, isLargeFile)
        },
        modifier = modifier,
    )

    // 文件切换：Content 复用路径（跳过 O(n) 重建）；同一实例不重复设置。
    // 每份 Content 的 undo 栈深度按文件大小降级（T5，P4 固定 500；大文件 cap 200），
    // 避免大文件内存膨胀；复用路径 undo 历史随 Content 保留。
    LaunchedEffect(content) {
        content.raw.undoManager.setMaxUndoStackSize(
            if (content.raw.length > LARGE_FILE_THRESHOLD) {
                LARGE_FILE_UNDO_STACK
            } else {
                DEFAULT_UNDO_STACK_SIZE
            },
        )
        val editor = controller.editor ?: return@LaunchedEffect
        if (editor.text !== content.raw) {
            editor.setText(content.raw)
        }
    }

    // 500ms 防抖诊断：后台线程增量扫描 + 签名比对去重。
    // 「行级词法诊断」设置关闭时跳过扫描并清空诊断（重启后签名复位，恢复时自动重跑）。
    LaunchedEffect(Unit) {
        diagTick.collectLatest {
            if (it == 0) return@collectLatest
            delay(DIAGNOSTIC_DEBOUNCE_MS)
            val editor = controller.editor ?: return@collectLatest
            // 行级词法诊断仅 Lua 文法（LuaDiagnostics 专用解析器）；其他语言跳过。
            if (currentResolvedScope != LanguageDetector.LUA_SCOPE) {
                if (editor.diagnostics != null) {
                    editor.diagnostics = null
                    instance.updateSignature("")
                }
                return@collectLatest
            }
            if (!currentConfig.lexicalDiagnostics) {
                if (editor.diagnostics != null) {
                    editor.diagnostics = null
                    instance.updateSignature("")
                }
                return@collectLatest
            }
            val (regions, signature) =
                withContext(Dispatchers.Default) {
                    val source = editor.text.toString()
                    if (source.length > LARGE_FILE_THRESHOLD) {
                        return@withContext emptyList<DiagnosticRegion>() to ""
                    }
                    val result = instance.syntaxCheck(source, editor.text, diagMessageProvider)
                    result to diagnosticSignature(result)
                }
            if (signature == instance.lastSignature()) return@collectLatest
            instance.updateSignature(signature)
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
            controller.unbind()
            // 去池化生命周期终结：销毁实例（sora release 清分析器/窗口/语言/监听器）。
            instance.editor.release()
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

/** T5：字体族 id → Android [android.graphics.Typeface]（代码文本与行号同族）。
 * 2026-08-11 用户红线（Squircle 字体组合）：默认（0）与「等宽」同值映射
 * [android.graphics.Typeface.MONOSPACE]——对齐 Squircle CE `TypefaceProvider.DEFAULT`
 * （core-common TypefaceProvider.kt:23 `DEFAULT = Typeface.MONOSPACE`）。
 * MONOSPACE 为 Android 系统等宽组合（Roboto Mono/Droid Sans Mono fallback 链），
 * 未内置字体文件（Squircle 同款，feature-fonts 仅提供自选字体覆盖）。 */
private fun fontFamilyTypeface(fontFamily: Int): android.graphics.Typeface =
    when (fontFamily) {
        EditorConfig.FONT_FAMILY_MONOSPACE, EditorConfig.FONT_FAMILY_DEFAULT ->
            android.graphics.Typeface.MONOSPACE
        EditorConfig.FONT_FAMILY_SANS -> android.graphics.Typeface.SANS_SERIF
        EditorConfig.FONT_FAMILY_SERIF -> android.graphics.Typeface.SERIF
        else -> android.graphics.Typeface.MONOSPACE
    }