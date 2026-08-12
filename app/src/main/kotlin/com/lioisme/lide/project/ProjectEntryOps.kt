package com.lioisme.lide.project

import java.io.File

/**
 * 工程文件系统操作防护的纯逻辑（WP-005 收缩，2026-08-12）：名称校验 / 去重 / 越界防护。
 * 无 Android 框架依赖（仅 File API），与 [ProjectManager] 的宿主耦合（Context、
 * luaExtDir）分离，可单测。
 *
 * 2026-08-12（DEAD_CODE_CLEANUP T3）：createFile / createDirectory / renameEntry /
 * deleteEntry 及其锚点保护经审计为文件树功能占位代码（零生产调用，仅测试直测），
 * 随 ProjectManager 4 包装一并删除；文件树功能落地时重建（backlog D8）。
 */
object ProjectEntryOps {
    private const val INVALID_NAME_CHARS = "\\/:*?\"<>|"

    /** 条目名校验：非空、非 `.`/`..`、无路径分隔/非法字符、无控制字符。 */
    fun isValidEntryName(name: String): Boolean =
        name.isNotBlank() &&
            name != "." &&
            name != ".." &&
            name.none { it in INVALID_NAME_CHARS } &&
            !name.any { it.isISOControl() }

    /** 重名去重（-1/-2 后缀，对齐 .alp 导入策略 [AlpNaming.dedupeName]）。 */
    fun dedupeEntryName(
        dir: File,
        base: String,
    ): String = AlpNaming.dedupeName(base) { File(dir, it).exists() }

    /** [path] 是否位于 [rootDir] 内（归一化词法前缀判定，防越界操作）。
     *
     * S4（2026-08-12）：原实现 `File.toPath().startsWith(...)` 为 API 26 方法，
     * minSdk 24/25 上抛 NoSuchMethodError 被 catch 吞 → Android 7.0/7.1 文件树
     * 操作全部静默失败。改为纯字符串段级解析（`..` 段回退、`.`/空段剥离，
     * 越出根即判越界），零系统调用、全 API 可用，同时封堵 `..` 词法绕过。
     */
    fun isInsideRoot(
        rootDir: File,
        path: String,
    ): Boolean {
        val rootAbs = rootDir.absolutePath
        // 根标识一致性：相对路径 / 异盘符路径直接拒判（防段序列巧合匹配）。
        val rootRoot = pathRoot(rootAbs) ?: return false
        if (pathRoot(path) != rootRoot) return false
        val rootSegs = normalizeSegments(rootAbs) ?: return false
        val pathSegs = normalizeSegments(path) ?: return false
        if (pathSegs.size < rootSegs.size) return false
        return rootSegs.indices.all { rootSegs[it] == pathSegs[it] }
    }

    /** 路径根标识：POSIX 绝对路径 `/`、Windows 盘符 `C:`；相对路径返回 null。 */
    private fun pathRoot(path: String): String? =
        when {
            path.startsWith('/') -> "/"
            path.length >= 2 && path[1] == ':' -> path.substring(0, 2).uppercase()
            else -> null
        }

    /** 段级归一化：`..` 回退上一段（越出根返回 null），`.`/空段剥离；null = 越界/非法。 */
    private fun normalizeSegments(path: String): List<String>? {
        val segments = ArrayList<String>()
        for (part in path.split('/', '\\')) {
            when {
                part.isEmpty() || part == "." -> {
                    Unit
                }

                part == ".." -> {
                    if (segments.isEmpty()) return null
                    segments.removeAt(segments.size - 1)
                }

                else -> {
                    segments.add(part)
                }
            }
        }
        return segments
    }
}
