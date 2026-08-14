package com.lioisme.lide.log

import io.github.oshai.kotlinlogging.Level

/**
 * 日志类型筛选（pro `logcat.lua` 菜单与语义的逐项复刻，2026-08-14 LOGCAT 修订）。
 *
 * 菜单顺序 = pro 原版 `assets/logcat.lua`：All / Lua / Test / Tcc /
 * Error / Warning / Info / Debug / Verbose（[LogFilter.entries] 遍历即菜单序）。
 *
 * 语义映射（与 pro `logcat -d -v long <s>` 一致）：
 * - [ALL]：全部（`*:*`）；
 * - [LUA]：仅 tag `lua`（`lua:* *:S`）——Lua 脚本 print + 引擎 Lua 业务日志通道；
 * - [TEST]：仅 tag `test`（`test:* *:S`）——工程测试日志通道（约定
 *   `Log.i("test", …)` 标记），现无来源恒空态，保留以对齐 pro 用户心智模型；
 * - [TCC]：仅 tag `tcc`（`tcc:* *:S`）——pro 时代 tcc 编译器输出；
 *   LIDE 不构建 tcc（WHITEPAPER §7.1），该筛选恒空态；
 * - [ERROR]/[WARNING]/[INFO]/[DEBUG]/[VERBOSE]：级别 ≥ 阈值的全部 tag（`*:E/W/I/D/V`）。
 */
enum class LogFilter(
    private val tag: String? = null,
    private val minLevel: Level? = null,
) {
    ALL,
    LUA(tag = "lua"),
    TEST(tag = "test"),
    TCC(tag = "tcc"),
    ERROR(minLevel = Level.ERROR),
    WARNING(minLevel = Level.WARN),
    INFO(minLevel = Level.INFO),
    DEBUG(minLevel = Level.DEBUG),
    VERBOSE(minLevel = Level.TRACE),
    ;

    /** 类型匹配（查询词过滤不在此列，见 [filterEntries]）。 */
    fun matches(entry: LogEntry): Boolean =
        when {
            tag != null -> entry.tag == tag
            minLevel != null -> entry.level.toInt() >= minLevel.toInt()
            else -> true
        }
}

/**
 * 列表过滤：类型筛选 ∩ 查询词（tag + message 小写子串，忽略大小写）。
 * 纯函数，后台线程调用（UI 仅经 produceState/remember 消费，见 AGENTS 红线）。
 */
internal fun filterEntries(
    entries: List<LogEntry>,
    filter: LogFilter,
    query: String,
): List<LogEntry> {
    val q = query.trim().lowercase()
    if (filter == LogFilter.ALL && q.isEmpty()) return entries
    return entries.filter { entry ->
        filter.matches(entry) &&
            (
                q.isEmpty() ||
                    entry.tag.lowercase().contains(q) ||
                    entry.message.lowercase().contains(q)
            )
    }
}
