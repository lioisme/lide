package com.lioisme.lide.log

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 日志猫数据层（进程级单例，ApiIndex 同款模式；宿主能力，R3 合规）。
 *
 * 生命周期：`LideApp.onCreate` 调 [ensureStarted]（幂等）启动常驻采集器，
 * 进程存活期间持续收集，环形缓冲上限 [DEFAULT_CAPACITY] 条（溢出丢最旧）。
 * 打包产物不运行 LideApp（manifest 回落引擎入口 Activity），天然不含此功能。
 *
 * 线程契约：采集器线程（Dispatchers.IO）写入缓冲并原子发布不可变快照；
 * [MutableStateFlow] 为线程安全原子更新（单写者），UI 经 collectAsState 主线程
 * 消费；[clear]/[entryById] 任意线程安全。
 */
object LogcatStore {
    const val DEFAULT_CAPACITY = 3000

    /** 单条消息长度上限（超长截断，防异常日志撑爆内存；详情页展示截断后全文）。 */
    private const val MAX_MESSAGE_LENGTH = 16_384

    private val buffer = LogBuffer(DEFAULT_CAPACITY)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val _status = MutableStateFlow<LogcatStatus>(LogcatStatus.Unavailable)
    val status: StateFlow<LogcatStatus> = _status.asStateFlow()

    @Volatile
    private var started = false

    /** 单调 id 分配（仅采集器线程写）。 */
    private var nextId = 0L

    /** 幂等启动采集器（LideApp.onCreate 进程级一次性调用）。 */
    fun ensureStarted() {
        if (started) return
        started = true
        scope.launch {
            LogcatReader(onLine = ::appendRawLine, onStatus = { _status.value = it }).run()
        }
    }

    /** 采集器回调：解析一行并发布快照（IO 线程）。 */
    internal fun appendRawLine(raw: String) {
        when (val result = LogcatParser.parseLine(raw)) {
            is LogcatParser.Result.Entry -> {
                val message = result.message.take(MAX_MESSAGE_LENGTH)
                buffer.append(
                    LogEntry(
                        id = nextId++,
                        timeText = result.timeText,
                        pid = result.pid,
                        tid = result.tid,
                        level = result.level,
                        tag = result.tag,
                        message = message,
                    ),
                )
                _entries.value = buffer.snapshot()
            }

            is LogcatParser.Result.Unparsed -> {
                if (buffer.appendContinuation(result.text)) {
                    _entries.value = buffer.snapshot()
                }
            }

            LogcatParser.Result.Header -> {
                Unit
            }
        }
    }

    /** 清空：内存缓冲必清；系统缓冲 best-effort `logcat -c`（无 READ_LOGS 静默失败）。 */
    fun clear() {
        buffer.clear()
        _entries.value = emptyList()
        scope.launch {
            try {
                ProcessBuilder("logcat", "-c").start().waitFor()
            } catch (_: Throwable) {
                // 无 READ_LOGS 时清除被拒绝：仅内存清空即为预期降级。
            }
        }
    }

    /** 按 id 查条目（详情页；溢出清除后返回 null）。 */
    fun entryById(id: Long): LogEntry? = buffer.entryById(id)

    /** 测试复位（JVM 单测，单例状态隔离）。 */
    internal fun resetForTest() {
        buffer.clear()
        _entries.value = emptyList()
        _status.value = LogcatStatus.Unavailable
        nextId = 0L
        started = false
    }
}

/** 采集器状态：流式读取中 / 不可用（启动失败或流中断，自动退避重试）。 */
sealed interface LogcatStatus {
    data object Streaming : LogcatStatus

    data object Unavailable : LogcatStatus
}
