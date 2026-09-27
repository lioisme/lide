package com.lioisme.lide.log

import io.github.oshai.kotlinlogging.Level

/**
 * logcat 优先级字符 ↔ kotlin-logging [Level] 映射（2026-08-15 kotlin-logging 迁移）。
 *
 * - logcat 优先级字符集 `V D I W E F`；kotlin-logging 级别集 `TRACE DEBUG INFO WARN ERROR OFF`；
 * - `V`（verbose）→ [Level.TRACE]（kotlin-logging 无 verbose，TRACE 为其最细粒度）；
 * - `F`（fatal）与未知字符回落 [Level.ERROR]（Android 日志 API 无独立 fatal 级别）；
 * - 过滤语义经 [Level.toInt]（0/10/20/30/40/50，单调升序）比较，替代旧 [LogLevel.priority]。
 */
fun logcatSymbolToLevel(symbol: Char): Level =
    when (symbol) {
        'V' -> Level.TRACE
        'D' -> Level.DEBUG
        'I' -> Level.INFO
        'W' -> Level.WARN
        'E', 'F' -> Level.ERROR
        else -> Level.ERROR
    }

/** kotlin-logging [Level] → logcat 优先级字符（`formatted()` / 级别徽章共用）。 */
fun Level.symbolChar(): Char =
    when (this) {
        Level.TRACE -> 'V'

        Level.DEBUG -> 'D'

        Level.INFO -> 'I'

        Level.WARN -> 'W'

        Level.ERROR -> 'E'

        // OFF 不产生日志条目（logcat 无对应字符，防御性兜底，不应出现在解析结果中）。
        Level.OFF -> '?'
    }
