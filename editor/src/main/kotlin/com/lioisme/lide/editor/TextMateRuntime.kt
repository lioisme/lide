package com.lioisme.lide.editor

import android.content.Context
import android.util.Log
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 * 线程契约：初始化在后台线程执行；[state] 为 StateFlow 供 Compose 主线程消费；
 * [colorScheme] 仅供初始化完成后（主线程）调用。
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

    private var scheme: TextMateColorScheme? = null

    /**
     * 幂等预热（宿主进程启动调用）：仅触发后台初始化，不阻塞调用方。
     * 编辑器侧无需再起线程（AGENTS.md：不得在 UI 线程初始化）。
     */
    fun preload(context: Context) {
        if (initStarted) return
        synchronized(this) {
            if (initStarted) return
            initStarted = true
        }
        Thread {
            try {
                TextMateInitializer.initialize(context.applicationContext)
                _state.value = RuntimeState.Ready
                Log.d(TAG, "textmate runtime ready")
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
     * 进程级单例 TextMateColorScheme（原 TextMateColorSchemeCache 收敛）。
     *
     * [TextMateColorScheme] 实现 [ThemeChangeListener]，主题切换时自动跟随
     * 注册表更新，无需按编辑器实例重建。创建成本 ~70ms（主线程），复用避免
     * 每进一次编辑器都支付一次。
     */
    fun colorScheme(): TextMateColorScheme =
        scheme ?: TextMateColorScheme.create(ThemeRegistry.getInstance()).also { scheme = it }

    private const val TAG = "TextMateRuntime"
    private const val PRELOAD_THREAD_NAME = "TextMatePreload"
}
