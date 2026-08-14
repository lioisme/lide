package com.lioisme.lide.log

import io.github.oshai.kotlinlogging.Level

/**
 * 单条日志（不可变，环形缓冲内共享）。
 *
 * @param id 进程内单调递增序号：LazyColumn key + 详情页路由参数
 *   （经 [LogcatStore.entryById] 查找，避免跨页面传递可变对象引用）。
 * @param timeText logcat 原始时间戳文本（`MM-DD HH:MM:SS.mmm`，streaming 天然有序）。
 * @param pid 产生进程 id。
 * @param tid 产生线程 id。
 * @param level 日志级别（kotlin-logging [Level]，2026-08-15 迁移；logcat 字符映射见 [logcatSymbolToLevel]）。
 * @param tag 日志 tag（含冒号 tag 按首 `": "` 切分语义，见 [LogcatParser]）。
 * @param message 完整消息（多行 continuation 已归并，见 [LogBuffer.appendContinuation]）。
 */
data class LogEntry(
    val id: Long,
    val timeText: String,
    val pid: Int,
    val tid: Int,
    val level: Level,
    val tag: String,
    val message: String,
)

/**
 * 标准 logcat 整行文本（社区惯例：Android Studio Logcat 长按复制 / pro
 * `logcat -v long` 同款语义）：
 * `MM-DD HH:MM:SS.mmm PID TID I/tag: message`。
 */
fun LogEntry.formatted(): String = "$timeText $pid $tid ${level.symbolChar()}/$tag: $message"
