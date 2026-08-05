package com.lioisme.lide.androlua

/**
 * .alp 容器的类型与目标名解析（纯函数，JVM 可单测）。
 *
 * 语义对齐 pro（`main.lua:1127-1131` 类型判定 / `importx:1153` 目录名规则）。
 *
 * init.lua 字段读取统一走 [LuaConfigParser] 结构化解析（跳过注释/字符串），
 * 不再使用裸正则（架构审计 §4.5：与 PluginManager 的解析行为保持一致，
 * 避免带尾注释时两套解析结果不一致）。
 */
object AlpNaming {

    /**
     * 类型判定：zip 内 init.lua 的 `mode = "plugin"`，或 .alp 文件名以 `<name>_plugin` 开头。
     */
    fun resolveKind(alpFileName: String, initContent: String?): AlpManager.AlpKind {
        val modePlugin =
            initContent != null &&
                LuaConfigParser.stringField(initContent, "mode") == "plugin"
        val namePlugin = Regex("""^[^._]+_plugin""").containsMatchIn(alpFileName)
        return if (modePlugin || namePlugin) AlpManager.AlpKind.PLUGIN else AlpManager.AlpKind.PROJECT
    }

    /**
     * 目标名：优先 init.lua 的 appname，否则取文件名去扩展名后截断到首个 `.`/`_`。
     */
    fun resolveName(alpFileName: String, initContent: String?): String {
        val appName = initContent?.let { LuaConfigParser.stringField(it, "appname") }
        if (!appName.isNullOrBlank()) return appName
        val base = alpFileName.substringBeforeLast('.')
        return base.substringBefore('.').substringBefore('_').ifBlank { "imported" }
    }

    /**
     * 同名冲突处理：目标名已存在时追加 `-1`/`-2` 后缀（对齐 .alp 导入策略）。
     * [exists] 谓词判定冲突（由调用方注入，避免依赖 Android 文件系统）。
     */
    fun dedupeName(base: String, exists: (String) -> Boolean): String {
        if (!exists(base)) return base
        var n = 1
        while (exists("$base-$n")) n++
        return "$base-$n"
    }
}
