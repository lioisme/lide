package com.lioisme.delua.androlua

/**
 * .alp 容器的类型与目标名解析（纯函数，JVM 可单测）。
 *
 * 语义对齐 pro（`main.lua:1127-1131` 类型判定 / `importx:1153` 目录名规则）。
 */
object AlpNaming {

    /**
     * 类型判定：zip 内 init.lua 的 `mode = "plugin"`，或 .alp 文件名以 `<name>_plugin` 开头。
     */
    fun resolveKind(alpFileName: String, initContent: String?): AlpManager.AlpKind {
        val modePlugin = initContent?.let {
            Regex("""(?m)^\s*mode\s*=\s*["']plugin["']\s*$""").containsMatchIn(it)
        } ?: false
        val namePlugin = Regex("""^[^._]+_plugin""").containsMatchIn(alpFileName)
        return if (modePlugin || namePlugin) AlpManager.AlpKind.PLUGIN else AlpManager.AlpKind.PROJECT
    }

    /**
     * 目标名：优先 init.lua 的 appname，否则取文件名去扩展名后截断到首个 `.`/`_`。
     */
    fun resolveName(alpFileName: String, initContent: String?): String {
        val appName = initContent?.let {
            Regex("""(?m)^\s*appname\s*=\s*"([^"]*)"\s*$""").find(it)?.groupValues?.get(1)
        }
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
