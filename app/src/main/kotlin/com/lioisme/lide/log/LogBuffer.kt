package com.lioisme.lide.log

/**
 * 线程安全环形日志缓冲（纯 Kotlin，JVM 可测）。
 *
 * 约定：
 * - 容量固定，满时丢弃最旧条目（内存有界，日志无限流）。
 * - 多行消息归并：[appendContinuation] 把续行追加到最新条目的 [LogEntry.message]。
 * - 全部操作 [synchronized]（采集器线程写 + 测试线程/详情页读并存）。
 */
class LogBuffer(
    private val capacity: Int,
) {
    private val entries = ArrayDeque<LogEntry>()

    /** 追加条目；容量满时丢弃最旧。 */
    @Synchronized
    fun append(entry: LogEntry) {
        if (entries.size >= capacity) {
            entries.removeFirst()
        }
        entries.addLast(entry)
    }

    /** 把续行归并到最新条目（无条目时不生效）。返回是否产生更新。 */
    @Synchronized
    fun appendContinuation(text: String): Boolean {
        val last = entries.lastOrNull() ?: return false
        entries.removeLast()
        entries.addLast(last.copy(message = last.message + "\n" + text))
        return true
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun isEmpty(): Boolean = entries.isEmpty()

    /** 当前条目快照（新列表，UI 只读）。 */
    @Synchronized
    fun snapshot(): List<LogEntry> = entries.toList()

    /** 按 id 查找（环形溢出清除后返回 null → 详情页 Failed 态）。 */
    @Synchronized
    fun entryById(id: Long): LogEntry? = entries.firstOrNull { it.id == id }
}
