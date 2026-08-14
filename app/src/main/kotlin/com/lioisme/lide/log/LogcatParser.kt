package com.lioisme.lide.log

import io.github.oshai.kotlinlogging.Level

/**
 * 纯 Kotlin `logcat -v threadtime` 行解析器（JVM 可测，零 Android 依赖）。
 *
 * 目标格式（threadtime）：
 * ```
 * MM-DD HH:MM:SS.mmm   PID   TID  P  TAG: MSG
 * 08-14 10:22:33.123  1234  5678  I lua: hello world
 * ```
 *
 * 容错约定：
 * - **tag/消息切分**：优先级字符后按首个 `": "` 切分（pidcat 同款标准语义）；
 *   含冒号 tag（如引擎 `javaGc: `）会被切分为 tag=`javaGc`、消息以 `" : "` 开头——
 *   该歧义为 logcat 文本格式固有，文档化接受；tag 两侧空白（threadtime 定宽填充）trim。
 * - **多行消息**：logcat 将长消息的续行原样输出（无 threadtime 前缀），
 *   返回 [Result.Unparsed] 由缓冲层归并（见 [LogBuffer.appendContinuation]）。
 * - **缓冲头**：`----- beginning of ...` 返回 [Result.Header] 丢弃。
 * - **乱行兜底**：不匹配且无归并目标时直接丢弃，不产生脏条目。
 */
object LogcatParser {
    /** threadtime 行正则：日期 时间 空格 PID TID 级别字符 剩余(tag: msg)。 */
    private val LINE_REGEX =
        Regex(
            """^(\d{2}-\d{2}) (\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+(.*)$""",
        )

    /** 缓冲分节头（`----- beginning of main` / `--------- beginning of crash`）。 */
    private val HEADER_REGEX = Regex("^[-]+ beginning of .*$")

    /** 解析结果。 */
    sealed interface Result {
        /** 标准条目（tag/消息已切分）。 */
        data class Entry(
            val timeText: String,
            val pid: Int,
            val tid: Int,
            val level: Level,
            val tag: String,
            val message: String,
        ) : Result

        /** 非标准行（多行消息续行等），由缓冲层决定归并或丢弃。 */
        data class Unparsed(
            val text: String,
        ) : Result

        /** 缓冲分节头，直接丢弃。 */
        data object Header : Result
    }

    fun parseLine(raw: String): Result {
        if (raw.isBlank()) return Result.Header
        if (HEADER_REGEX.matches(raw)) return Result.Header
        val match = LINE_REGEX.matchEntire(raw) ?: return Result.Unparsed(raw)
        val rest = match.groupValues[6]
        val separator = rest.indexOf(": ")
        val tag =
            if (separator >= 0) {
                rest.substring(0, separator).trim()
            } else {
                rest.trim()
            }
        val message = if (separator >= 0) rest.substring(separator + 2) else ""
        return Result.Entry(
            timeText = "${match.groupValues[1]} ${match.groupValues[2]}",
            pid = match.groupValues[3].toInt(),
            tid = match.groupValues[4].toInt(),
            level = logcatSymbolToLevel(match.groupValues[5][0]),
            tag = tag,
            message = message,
        )
    }
}
