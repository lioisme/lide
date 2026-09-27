package com.lioisme.lide.log

import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * logcat 进程流式读取器（`logcat -v threadtime`，无 `-d` 持续流）。
 *
 * 权限说明：现代 Android（7.0+）未授予 READ_LOGS 的应用只能收到本进程 UID
 * 的条目（logd 按 UID 过滤）——LIDE 宿主与 androlua 引擎同进程（AGENTS R2），
 * 恰好覆盖「整个软件」的全部日志；不额外按 pid 过滤（子进程同 UID 条目一并可见）。
 *
 * 容错（2026-08-15 LOGCAT_CORE C2 重构）：
 * - **`-T` 续读**：进程重启（流中断）后经 [updateTimestamp] 记录的最后时间戳
 *   传 `logcat -T '<ts>'`，从上次消费点续读，消除重复条目（首次启动不传，读
 *   缓冲开头）；同毫秒多行存在轻微重复可能（logcat 原生语义，Android Studio
 *   同款做法，文档化接受）。
 * - **退避重试**：指数退避 + jitter（[backoffDelayMs]，2s 基数 ×2 递增、上限
 *   30s），替代原固定 5s；Streaming 成功后归零。
 * - **进程回收**：`destroy()` 后 2s 未退出 → `destroyForcibly()`（防子进程
 *   残留阻塞）；销毁前先 destroy 再关 errorStream（防 stderr 管道写满阻塞）。
 * - 全路径 `try/catch(Throwable)` 降级（AGENTS 红线）。
 */
class LogcatReader(
    private val onLine: (String) -> Unit,
    private val onStatus: (LogcatStatus) -> Unit,
) {
    @Volatile
    private var active = false

    /** 最后一条已消费 Entry 的时间戳（`MM-DD HH:MM:SS.mmm`），供 `-T` 续读。 */
    @Volatile
    private var lastTimestamp: String? = null

    /** 记录最后一条已消费 Entry 的时间戳（采集器线程回调；跨线程 @Volatile）。 */
    fun updateTimestamp(timeText: String) {
        lastTimestamp = timeText
    }

    /** 阻塞读取循环（调用方置于 Dispatchers.IO）；[stop] 后退出。 */
    suspend fun run() {
        active = true
        var attempt = 0
        while (active) {
            var process: Process? = null
            try {
                process = ProcessBuilder(logcatCommand(lastTimestamp)).start()
                onStatus(LogcatStatus.Streaming)
                attempt = 0
                process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
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
                    if (process != null && !process.waitFor(2, TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                    }
                    process?.errorStream?.close()
                } catch (_: Throwable) {
                }
            }
            if (active) {
                onStatus(LogcatStatus.Unavailable)
                delay(backoffDelayMs(attempt))
                attempt++
            }
        }
    }

    fun stop() {
        active = false
    }
}

/**
 * logcat 启动命令（纯函数，JVM 可测）：首次不传 `-T`（读缓冲开头）；
 * 续启传最后时间戳（从上次消费点续读，防重复）。
 */
internal fun logcatCommand(resumeTimestamp: String?): List<String> =
    if (resumeTimestamp == null) {
        listOf("logcat", "-v", "threadtime")
    } else {
        listOf("logcat", "-v", "threadtime", "-T", resumeTimestamp)
    }

/**
 * 指数退避 + jitter（纯函数，JVM 可测）：2s 基数 ×2 递增（封顶 30s）+
 * 0..1000ms 抖动，大厂标准退避策略；attempt 从 0 起，Streaming 成功后归零。
 */
internal fun backoffDelayMs(
    attempt: Int,
    random: Random = Random.Default,
): Long {
    val exp = BASE_BACKOFF_MS shl attempt.coerceIn(0, MAX_BACKOFF_SHIFT)
    return (exp + random.nextLong(0, JITTER_MS)).coerceAtMost(MAX_BACKOFF_MS)
}

private const val BASE_BACKOFF_MS = 2_000L

/** 指数位移上限（2s→4s→8s→16s→32s，封顶 [MAX_BACKOFF_MS]）。 */
private const val MAX_BACKOFF_SHIFT = 4

private const val MAX_BACKOFF_MS = 30_000L

/** 抖动范围（0..1000ms，防惊群重连）。 */
private const val JITTER_MS = 1_000L
