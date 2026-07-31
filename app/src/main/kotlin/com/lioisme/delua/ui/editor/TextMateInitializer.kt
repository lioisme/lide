package com.lioisme.delua.ui.editor

import android.content.Context
import android.util.Log
import io.github.rosemoe.sora.lang.analysis.AsyncIncrementalAnalyzeManager
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.dsl.languages
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TextMate 运行时的进程级一次性初始化（主题、文法、文件提供器）。
 *
 * 由 [DeluaApp] 在进程启动时预热，编辑器首帧不再等待数百毫秒。
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
        if (started.compareAndSet(false, true)) {
            Thread {
                try {
                    runInit(appContext)
                } catch (t: Throwable) {
                    Log.e(TAG, "textmate initialization failed", t)
                } finally {
                    done.countDown()
                }
            }.apply {
                name = PRELOAD_THREAD_NAME
                isDaemon = true
            }.start()
        }
        done.await()
    }

    private fun runInit(context: Context) {
        AsyncIncrementalAnalyzeManager.setUseShallowCopyByDefault(true)

        FileProviderRegistry.getInstance().addFileProvider(
            AssetsFileResolver(context.assets)
        )

        val themeRegistry = ThemeRegistry.getInstance()
        for (name in THEMES) {
            val path = "textmate/$name.json"
            try {
                val input = FileProviderRegistry.getInstance().tryGetInputStream(path)
                if (input != null) {
                    themeRegistry.loadTheme(
                        ThemeModel(IThemeSource.fromInputStream(input, path, null), name)
                    )
                }
            } catch (_: Exception) {
            }
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

    private const val TAG = "TextMateInitializer"
    private const val PRELOAD_THREAD_NAME = "TextMatePreload"
    private val THEMES = arrayOf("quietlight", "darcula")
}
