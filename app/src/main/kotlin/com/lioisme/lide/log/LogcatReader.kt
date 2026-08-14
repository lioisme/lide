package com.lioisme.lide.log

import kotlinx.coroutines.delay

/**
 * logcat 进程流式读取器（`logcat -v threadtime`，无 `-d` 持续流）。
 *
 * 权限说明：现代 Android（7.0+）未授予 READ_LOGS 的应用只能收到本进程 UID
 * 的条目（logd 按 UID 过滤）——LIDE 宿主与 androlua 引擎同进程（AGENTS R2），
 * 恰好覆盖「整个软件」的全部日志；不额外按 pid 过滤（子进程同 UID 条目一并可见）。
 *
 * 容错：进程启动失败 / 流中断 → [LogcatStatus.Unavailable] + 固定退避重试；
 * 全路径 `try/catch(Throwable)` 降级（AGENTS 红线）。销毁前先 destroy 再关
 * errorStream（防子进程 stderr 管道写满阻塞 waitFor）。
 */
class LogcatReader(
    private val onLine: (String) -> Unit,
    private val onStatus: (LogcatStatus) -> Unit,
) {
    @Volatile
    private var active = false

    /** 阻塞读取循环（调用方置于 Dispatchers.IO）；[stop] 后退出。 */
    suspend fun run() {
        active = true
        while (active) {
            var process: Process? = null
            try {
                process = ProcessBuilder("logcat", "-v", "threadtime").start()
                onStatus(LogcatStatus.Streaming)
                process.inputStream.bufferedReader().use { reader ->
                    while (active) {
                        val line = reader.readLine() ?: break
                        onLine(line)
                    }
                }
            } catch (_: Throwable) {
                // 启动失败或流异常：降级 Unavailable 并退避重试，不崩溃。
            } finally {
                try {
                    process?.destroy()
                    process?.errorStream?.close()
                } catch (_: Throwable) {
                }
            }
            if (active) {
                onStatus(LogcatStatus.Unavailable)
                delay(RESTART_DELAY_MS)
            }
        }
    }

    fun stop() {
        active = false
    }

    private companion object {
        const val RESTART_DELAY_MS = 5_000L
    }
}
