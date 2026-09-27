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
 * 进程存活期间持续收集，环形缓冲上限 [DEFAULT_CAPACITY] 条 + 字节预算
 * [LogBuffer.DEFAULT_CHAR_BUDGET]（8M 字符，2026-08-15 C3 起双保险淘汰，
 * 消除最坏 ~98MB OOM 路径）。打包产物不运行 LideApp（manifest 回落引擎入口
 * Activity），天然不含此功能。
 *
 * 线程契约：采集器线程（Dispatchers.IO）写入缓冲并**节流发布**不可变快照
 * （[FLUSH_INTERVAL_MS] 窗口合并，日志风暴时发布频率 ≤10/s，2026-08-15 C4）；
 * [MutableStateFlow] 为线程安全原子更新（单写者），UI 经 collectAsState 主线程
 * 消费；[clear]/[entryById] 任意线程安全。
 */
object LogcatStore {
    const val DEFAULT_CAPACITY = 3000

    /** 单条消息长度上限（超长截断 + truncated 标记；详情页展示截断后全文）。 */
    const val MAX_MESSAGE_LENGTH = 16_384

    /** 节流发布窗口：窗口内合并多行一次性发布（日志风暴降频）。 */
    private const val FLUSH_INTERVAL_MS = 100L

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

    /** 节流时间源（internal 可注入虚拟时钟测试；[resetForTest] 恢复）。 */
    @Volatile
    internal var nowProvider: () -> Long = System::currentTimeMillis

    /** 上次发布时刻；初始为窗口负值保证首条立即发布（真实/虚拟时钟均成立）。 */
    private var lastPublish = -FLUSH_INTERVAL_MS

    /** 采集器实例（`-T` 续读时间戳回写用，A1 契约；@Volatile 跨线程安全）。 */
    @Volatile
    private var reader: LogcatReader? = null

    /** 幂等启动采集器（LideApp.onCreate 进程级一次性调用）。 */
    fun ensureStarted() {
        if (started) return
        started = true
        scope.launch {
            val r =
                LogcatReader(
                    onLine = ::appendRawLine,
                    onStatus = { _status.value = it },
                )
            reader = r
            r.run()
        }
    }

    /** 采集器回调：解析一行并入缓冲（IO 线程）；节流窗口到期发布快照。 */
    internal fun appendRawLine(raw: String) {
        when (val result = LogcatParser.parseLine(raw)) {
            is LogcatParser.Result.Entry -> {
                val truncated = result.message.length > MAX_MESSAGE_LENGTH
                buffer.append(
                    LogEntry(
                        id = nextId++,
                        timeText = result.timeText,
                        pid = result.pid,
                        tid = result.tid,
                        level = result.level,
                        tag = result.tag,
                        message = result.message.take(MAX_MESSAGE_LENGTH),
                        truncated = truncated,
                    ),
                )
                // `-T` 续读时间戳回写（A1 契约；@Volatile 跨线程安全）。
                reader?.updateTimestamp(result.timeText)
                maybePublish()
            }

            is LogcatParser.Result.Unparsed -> {
                if (buffer.appendContinuation(result.text)) {
                    maybePublish()
                }
            }

            LogcatParser.Result.Header -> {
                Unit
            }
        }
    }

    /** 节流发布：窗口到期一次性发布快照（未到期不发布，多行合并到窗口末）。 */
    private fun maybePublish() {
        val now = nowProvider()
        if (now - lastPublish >= FLUSH_INTERVAL_MS) {
            lastPublish = now
            _entries.value = buffer.snapshot()
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

    /** 测试复位（JVM 单测，单例状态隔离；恢复真实时钟与初始发布时刻）。 */
    internal fun resetForTest() {
        buffer.clear()
        _entries.value = emptyList()
        _status.value = LogcatStatus.Unavailable
        nextId = 0L
        started = false
        reader = null
        nowProvider = System::currentTimeMillis
        lastPublish = -FLUSH_INTERVAL_MS
    }

    /** 测试：强制发布当前快照（节流窗口未到期时断言用）。 */
    internal fun flushForTest() {
        lastPublish = nowProvider()
        _entries.value = buffer.snapshot()
    }

    /** 测试：注入虚拟时钟（节流窗口行为断言用；[resetForTest] 恢复真实时钟）。 */
    internal fun setClockForTest(provider: () -> Long) {
        nowProvider = provider
    }
}

/** 采集器状态：流式读取中 / 不可用（启动失败或流中断，自动退避重试）。 */
sealed interface LogcatStatus {
    data object Streaming : LogcatStatus

    data object Unavailable : LogcatStatus
}
