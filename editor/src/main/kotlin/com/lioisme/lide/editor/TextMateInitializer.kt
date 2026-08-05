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
import org.eclipse.tm4e.core.registry.IThemeSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TextMate 运行时的进程级一次性初始化（主题、文法、文件提供器）。
 *
 * 由宿主在进程启动时预热，编辑器首帧不再等待数百毫秒。
 * 并发语义：无论哪个线程先调用，初始化只执行一次；后续调用者阻塞等待
 * 初始化完成（[CountDownLatch]），保证调用返回后注册表必然可用。
 */
object TextMateInitializer {
    private val started = AtomicBoolean(false)
    private val done = CountDownLatch(1)

    /**
     * 确保 TextMate 运行时已初始化。
     *
     * 首次调用在后台线程执行初始化并立即返回前的语义为阻塞：
     * 所有调用者在返回后都可安全使用 [GrammarRegistry] / [ThemeRegistry]。
     * 注意：不得在 UI 线程直接调用（初始化耗时数百毫秒）。
     */
    fun initialize(context: Context) {
        val appContext = context.applicationContext
        val callerStart = SystemClock.elapsedRealtime()
        if (started.compareAndSet(false, true)) {
            Thread {
                try {
                    runInit(appContext)
                } catch (t: Throwable) {
                    Log.e(TAG, "textmate initialization failed", t)
                } finally {
                    done.countDown()
                    Log.d(TAG, "init total ${SystemClock.elapsedRealtime() - initStart}ms")
                }
            }.apply {
                name = PRELOAD_THREAD_NAME
                isDaemon = true
            }.start()
        }
        done.await()
        Log.d(TAG, "caller wait ${SystemClock.elapsedRealtime() - callerStart}ms")
    }

    /**
     * 是否已完成初始化（进程级快照查询，不阻塞；未完成时为 false）。
     */
    fun isReady(): Boolean = done.count == 0L

    private fun runInit(context: Context) {
        initStart = SystemClock.elapsedRealtime()
        AsyncIncrementalAnalyzeManager.setUseShallowCopyByDefault(true)

        FileProviderRegistry.getInstance().addFileProvider(
            AssetsFileResolver(context.assets),
        )

        val themeRegistry = ThemeRegistry.getInstance()
        for (name in THEMES) {
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
        themeRegistry.setTheme("quietlight")

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
    }

    private const val TAG = "TextMateInitializer"
    private const val PRELOAD_THREAD_NAME = "TextMatePreload"
    private const val LANGUAGES_JSON = "textmate/languages.json"
    private val THEMES = arrayOf("quietlight", "darcula")
    private var initStart = 0L
}
