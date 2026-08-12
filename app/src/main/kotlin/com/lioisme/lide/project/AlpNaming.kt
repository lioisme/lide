package com.lioisme.lide.project

/**
 * .alp 容器的目标名解析（纯函数，JVM 可单测）。
 *
 * 语义对齐 pro（`importx:1153` 目录名规则）。
 *
 * init.lua 字段读取统一走 [LuaConfigParser] 结构化解析（跳过注释/字符串），
 * 不再使用裸正则（架构审计 §4.5：避免带尾注释时解析结果不一致）。
 * （插件容器类型判定已随插件功能移除——.alp 一律按工程导入。）
 */
object AlpNaming {
    /**
     * 目标名：优先 init.lua 的 appname，否则取文件名去扩展名后截断到首个 `.`/`_`。
     */
    fun resolveName(
        alpFileName: String,
        initContent: String?,
    ): String {
        val appName = initContent?.let { LuaConfigParser.stringField(it, "appname") }
        if (!appName.isNullOrBlank()) return appName
        val base = alpFileName.substringBeforeLast('.')
        return base.substringBefore('.').substringBefore('_').ifBlank { "imported" }
    }

    /**
     * 同名冲突处理：目标名已存在时追加 `-1`/`-2` 后缀（对齐 .alp 导入策略）。
     * [exists] 谓词判定冲突（由调用方注入，避免依赖 Android 文件系统）。
     */
    fun dedupeName(
        base: String,
        exists: (String) -> Boolean,
    ): String {
        if (!exists(base)) return base
        var n = 1
        while (exists("$base-$n")) n++
        return "$base-$n"
    }
}
