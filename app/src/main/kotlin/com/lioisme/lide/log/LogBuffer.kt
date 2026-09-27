package com.lioisme.lide.log

/**
 * 线程安全环形日志缓冲（纯 Kotlin，JVM 可测）。
 *
 * 约定（2026-08-15 LOGCAT_CORE C3 重构）：
 * - **字节预算淘汰**：总字符预算 [DEFAULT_CHAR_BUDGET]（8M 字符 ≈ 16MB UTF-16
 *   堆），`totalChars` 记账（消息 + tag + 时间戳文本长度），超预算丢最旧直至
 *   满足——消除「3000 条 × 16KB ≈ 98MB」最坏 OOM 路径（P0-2）；
 *   `capacity` 仍为条目硬上限（双保险，防御性）。
 * - **归并分片**：续行经 [appendContinuation] 追加到尾部 StringBuilder（O(1)/行），
 *   下次 [append] 或 [snapshot]/[entryById] 前 [flushTail] 固化
 *   （O(消息总长)/消息，替代逐行 `copy(message + "\n" + text)` 的 O(n²) 拼接）。
 * - **续行超限截断**：builder 超 [LogcatStore.MAX_MESSAGE_LENGTH]（16KB）时
 *   丢弃续行并置尾部 entry `truncated = true`（消除单条无限增长路径）。
 * - 全部操作 [synchronized]（采集器线程写 + 测试线程/详情页读并存）。
 */
class LogBuffer(
    private val capacity: Int,
    private val charBudget: Int = DEFAULT_CHAR_BUDGET,
) {
    private val entries = ArrayDeque<LogEntry>()
    private var totalChars = 0L

    /** 尾部 entry 的消息拼接缓冲（续行归并，避免 O(n²) 全量复制）。 */
    private var tailMessage: StringBuilder? = null

    /** 追加条目；字节预算超限丢最旧。 */
    @Synchronized
    fun append(entry: LogEntry) {
        flushTail()
        entries.addLast(entry)
        totalChars += charCount(entry)
        tailMessage = StringBuilder(entry.message)
        evict()
    }

    /** 把续行归并到最新条目（无条目时不生效）。返回是否产生更新。 */
    @Synchronized
    fun appendContinuation(text: String): Boolean {
        val last = entries.lastOrNull() ?: return false
        val tail = tailMessage
        if (tail == null) {
            // builder 已被快照固化（发布间隙的续行）：回退直接归并到最新条目
            // （低频路径：实时流中续行紧跟 Entry 行，窗口几乎不可能在两者间到期；
            // 每条消息的归并次数有界，不构成 O(n²) 风险）。
            return mergeToTail(last, text)
        }
        if (tail.length + text.length + 1 > LogcatStore.MAX_MESSAGE_LENGTH) {
            // 超限：丢弃续行并标记截断（消息保持 ≤16KB，消除无限增长路径）。
            markTailTruncated()
            return true
        }
        tail.append('\n').append(text)
        totalChars += text.length + 1L
        evict()
        return true
    }

    @Synchronized
    fun clear() {
        entries.clear()
        totalChars = 0L
        tailMessage = null
    }

    @Synchronized
    fun isEmpty(): Boolean = entries.isEmpty()

    /** 当前条目快照（新列表，UI 只读；先固化未完成归并）。 */
    @Synchronized
    fun snapshot(): List<LogEntry> {
        flushTail()
        return entries.toList()
    }

    /** 按 id 查找（环形溢出清除后返回 null → 详情页 Failed 态；先固化未完成归并）。 */
    @Synchronized
    fun entryById(id: Long): LogEntry? {
        flushTail()
        return entries.firstOrNull { it.id == id }
    }

    /** 把尾部 builder 固化进最新条目（记账不变：增量已在 [appendContinuation] 记入）。 */
    private fun flushTail() {
        val tail = tailMessage ?: return
        tailMessage = null
        val last = entries.lastOrNull() ?: return
        val merged = tail.toString()
        // 消息变化：messageLower 索引同步重建（A4 契约）。
        entries.removeLast()
        entries.addLast(last.copy(message = merged, messageLower = merged.lowercase()))
    }

    /** 续行超限截断：尾部 entry 置 truncated（消息不变，O(1) copy）。 */
    private fun markTailTruncated() {
        val last = entries.lastOrNull() ?: return
        if (last.truncated) return
        entries.removeLast()
        entries.addLast(last.copy(truncated = true))
    }

    /** 回退归并：直接合并到最新条目（builder 已被快照固化后的低频续行）。 */
    private fun mergeToTail(
        last: LogEntry,
        text: String,
    ): Boolean {
        if (last.message.length + text.length + 1 > LogcatStore.MAX_MESSAGE_LENGTH) {
            markTailTruncated()
            return true
        }
        val merged = last.message + "\n" + text
        totalChars -= last.message.length
        totalChars += merged.length
        entries.removeLast()
        entries.addLast(last.copy(message = merged, messageLower = merged.lowercase()))
        evict()
        return true
    }

    /** 字节预算淘汰：超限丢最旧直至满足（容量硬上限同时生效）。 */
    private fun evict() {
        while (entries.size > capacity || totalChars > charBudget) {
            val removed = entries.removeFirstOrNull() ?: break
            totalChars -= charCount(removed)
        }
    }

    /** 单条字符成本（消息 + tag + 时间戳文本，预算记账口径）。 */
    private fun charCount(entry: LogEntry): Long = entry.message.length.toLong() + entry.tag.length + entry.timeText.length

    companion object {
        /** 总字符预算：8M 字符 ≈ 16MB UTF-16 堆（日志风暴下条目数自动收缩，内存有界）。 */
        const val DEFAULT_CHAR_BUDGET = 8 * 1024 * 1024
    }
}
