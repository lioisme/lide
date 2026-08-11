package com.lioisme.lide.editor

import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.github.rosemoe.sora.lang.analysis.AsyncIncrementalAnalyzeManager
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.langs.textmate.registry.reader.LanguageDefinitionReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.eclipse.tm4e.core.registry.IThemeSource

/**
 * TextMate 运行时单一服务（架构审计 C-4）。
 *
 * 收敛对象：
 * - 三件套进程级状态（`TextMateColorSchemeCache` / `EditorInitGate` /
 *   `TextMateInitializer` 分散入口）合并为本服务一个进程级状态机；
 * - 双预热入口（`LideApp.preloadTextMate` 与 `rememberTextMateInitializer`
 *   各起线程）收敛为 [preload] 一个触发入口，幂等；
 * - 初始化失败不再伪装就绪（原 `markReady()` 无条件执行）：[state] 进入
 *   [RuntimeState.Failed]，编辑器展示错误态 + 重试（[retry]）。
 *
 * 线程契约：初始化在后台线程执行（单线程，无嵌套/阻塞等待）；[state] 为
 * StateFlow 供 Compose 主线程消费；[colorScheme] 仅供初始化完成后（主线程）调用。
 */
object TextMateRuntime {

    /** 运行时状态机：Loading（初始化中/未触发）→ Ready / Failed。 */
    sealed interface RuntimeState {
        data object Loading : RuntimeState
        data object Ready : RuntimeState
        data object Failed : RuntimeState
    }

    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.Loading)
    val state: StateFlow<RuntimeState> = _state.asStateFlow()

    @Volatile
    private var initStarted = false

    /** 按「方案 + 明暗」缓存的方案对象（2026-08-12 方案可选反转后按主题名分槽；
     * 同主题名不重复构造，明暗/方案切换零重建）。 */
    private val hostSchemes = HashMap<String, LideHostColorScheme>()

    /**
     * 幂等预热（宿主进程启动调用）：仅触发后台初始化，不阻塞调用方。
     * 编辑器侧无需再起线程（AGENTS.md：不得在 UI 线程初始化）。
     * 初始化失败（[RuntimeState.Failed]）可经 [retry] 重新触发。
     *
     * [initialThemeName] 由宿主按「当前方案 + 当前明暗」传入（保 R4：
     * 运行时不读宿主状态；默认 = LIDE Classic 亮侧），仅决定初始装配主题，
     * 编辑器打开后由 LuaEditor 的 LaunchedEffect 按实际配置接管。
     */
    fun preload(
        context: Context,
        initialThemeName: String = EditorPalette.themeNameFor(EditorPalette.SCHEME_CLASSIC, isDark = false),
    ) {
        if (initStarted) return
        synchronized(this) {
            if (initStarted) return
            initStarted = true
        }
        Thread {
            try {
                runInit(context.applicationContext, initialThemeName)
                _state.value = RuntimeState.Ready
                Log.d(TAG, "textmate runtime ready (theme=$initialThemeName)")
            } catch (t: Throwable) {
                _state.value = RuntimeState.Failed
                Log.e(TAG, "textmate runtime failed", t)
            }
        }.apply {
            name = PRELOAD_THREAD_NAME
            isDaemon = true
        }.start()
    }

    /** 初始化失败后的重试入口（编辑器错误态按钮触发）。 */
    fun retry(context: Context) {
        if (_state.value == RuntimeState.Failed) {
            initStarted = false
            _state.value = RuntimeState.Loading
            preload(context)
        }
    }

    /**
     * 编辑器配色方案（2026-08-12 方案可选反转后唯一入口）。
     *
     * [LideHostColorScheme]：文本区背景/前景槽与 token 色取当前 TextMate 主题
     * （调用方须先按「方案 + 明暗」`setTheme(EditorPalette.themeNameFor(...))`
     * 再取本方案），chrome 部件色（行号/当前行/选区/浮层/滚动条/minimap）
     * 取宿主派生色；宿主色板变化经 [updateHostColors] 刷新，零重建。
     */
    fun hostColorScheme(
        scheme: Int,
        isDark: Boolean,
        host: HostEditorColors,
    ): io.github.rosemoe.sora.widget.schemes.EditorColorScheme {
        val themeName = EditorPalette.themeNameFor(scheme, isDark)
        return hostSchemes.getOrPut(themeName) { LideHostColorScheme(ThemeRegistry.getInstance(), host) }
    }

    /** 宿主色板刷新（值未变自动跳过）。 */
    fun updateHostColors(
        scheme: Int,
        isDark: Boolean,
        host: HostEditorColors,
    ) {
        hostSchemes[EditorPalette.themeNameFor(scheme, isDark)]?.updateHost(host)
    }

    private fun runInit(
        context: Context,
        initialThemeName: String,
    ) {
        val initStart = SystemClock.elapsedRealtime()
        AsyncIncrementalAnalyzeManager.setUseShallowCopyByDefault(true)

        FileProviderRegistry.getInstance().addFileProvider(
            AssetsFileResolver(context.assets),
        )

        val themeRegistry = ThemeRegistry.getInstance()
        for (name in EditorPalette.allThemes) {
            val path = "textmate/$name.json"
            try {
                val input = FileProviderRegistry.getInstance().tryGetInputStream(path)
                if (input != null) {
                    themeRegistry.loadTheme(
                        ThemeModel(IThemeSource.fromInputStream(input, path, null), name),
                    )
                }
            } catch (_: Exception) {
            }
        }
        themeRegistry.setTheme(initialThemeName)

        // 多语言化（任务单 EDITOR_ALL_LANGUAGES_SUPPORT_WORK_ORDER P1/P2）：
        // 语言目录单一事实源 = assets/textmate/languages.json（架构审计 P0-2 根治，2026-08-06）：
        // 经 sora 原生 LanguageDefinitionReader 批量读取（天然保留 embeddedLanguages 嵌入语言映射），
        // 优先走 loadGrammars(list) 批量加载（先 prepare 全部 scope id，跨语言嵌入引用可解析）；
        // 批量失败时逐条降级加载（任一文法失败仅记录日志，不中断其余文法，降级原则）。
        val grammarRegistry = GrammarRegistry.getInstance()
        val definitions = try {
            LanguageDefinitionReader.read(LANGUAGES_JSON)
        } catch (t: Throwable) {
            Log.e(TAG, "read languages.json failed", t)
            emptyList()
        }
        if (definitions.isNotEmpty()) {
            try {
                grammarRegistry.loadGrammars(definitions)
            } catch (t: Throwable) {
                Log.w(TAG, "batch grammar load failed, falling back per item", t)
                for (definition in definitions) {
                    try {
                        grammarRegistry.loadGrammar(definition)
                    } catch (itemError: Throwable) {
                        Log.w(TAG, "grammar load failed: ${definition.name}", itemError)
                    }
                }
            }
        }
        Log.d(TAG, "init total ${SystemClock.elapsedRealtime() - initStart}ms")
    }

    private const val TAG = "TextMateRuntime"
    private const val PRELOAD_THREAD_NAME = "TextMatePreload"
    private const val LANGUAGES_JSON = "textmate/languages.json"
}
