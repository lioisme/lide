package com.lioisme.lide.log

/**
 * 日志级别（映射 Android logcat 优先级字符，单调升序可比较）。
 *
 * - `VERBOSE`(V) < `DEBUG`(D) < `INFO`(I) < `WARN`(W) < `ERROR`(E)；
 * - logcat 的 `F`（fatal）按 [ERROR] 展示（Android 日志 API 无独立 fatal 级别）。
 */
enum class LogLevel(
    val priority: Int,
    val symbol: Char,
) {
    VERBOSE(2, 'V'),
    DEBUG(3, 'D'),
    INFO(4, 'I'),
    WARN(5, 'W'),
    ERROR(6, 'E'),
    ;

    companion object {
        /** 按 logcat 优先级字符解析；未知字符（含 fatal `F`）回落 [ERROR]。 */
        fun fromSymbol(symbol: Char): LogLevel = entries.firstOrNull { it.symbol == symbol } ?: ERROR
    }
}
