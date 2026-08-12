package com.lioisme.lide.project

import android.util.Log
import com.lioisme.lide.model.Project
import com.lioisme.lide.project.AlpNaming
import java.io.File

/**
 * 工程内文件树操作的纯逻辑（WP-005）：名称校验 / 去重 / 锚点保护 / 越界防护。
 * 无 Android 框架依赖（仅 File API），与 [ProjectManager] 的宿主耦合（Context、
 * luaExtDir）分离，可单测；文件系统副作用集中在宿主调用点。目录参数一律为绝对路径。
 */
object ProjectEntryOps {
    private const val TAG = "ProjectEntryOps"

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

    /** 工程锚点保护：main.lua / init.lua 禁止重命名与删除。 */
    fun isProjectAnchor(name: String): Boolean =
        name == Project.DEFAULT_MAIN_FILE || name == "init.lua"

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
                part.isEmpty() || part == "." -> Unit
                part == ".." -> {
                    if (segments.isEmpty()) return null
                    segments.removeAt(segments.size - 1)
                }
                else -> segments.add(part)
            }
        }
        return segments
    }

    /** 校验 + 去重后的目标名；非法输入返回 null。 */
    private fun targetName(
        dir: File,
        requested: String,
    ): String? {
        if (!isValidEntryName(requested)) return null
        return dedupeEntryName(dir, requested)
    }

    /** 新建文件（[dirPath] 为绝对路径）。 */
    fun createFile(
        rootDir: File,
        dirPath: String,
        requestedName: String,
    ): Boolean {
        if (!isInsideRoot(rootDir, dirPath)) return false
        val name = targetName(File(dirPath), requestedName) ?: return false
        return try {
            val f = File(dirPath, name)
            f.parentFile?.mkdirs()
            f.createNewFile()
        } catch (e: Exception) {
            Log.w(TAG, "createFile failed: $dirPath/$name", e)
            false
        }
    }

    /** 新建目录（[dirPath] 为绝对路径）。 */
    fun createDirectory(
        rootDir: File,
        dirPath: String,
        requestedName: String,
    ): Boolean {
        if (!isInsideRoot(rootDir, dirPath)) return false
        val name = targetName(File(dirPath), requestedName) ?: return false
        return try {
            File(dirPath, name).mkdirs()
        } catch (e: Exception) {
            Log.w(TAG, "createDirectory failed: $dirPath/$name", e)
            false
        }
    }

    /**
     * 重命名条目。返回最终文件名（冲突时已去重 -1/-2）；校验失败 / 锚点 /
     * 越界 / 指向工程根返回 null。
     */
    fun renameEntry(
        rootDir: File,
        oldPath: String,
        requestedName: String,
    ): String? {
        val old = File(oldPath)
        if (old == rootDir) return null
        if (!isInsideRoot(rootDir, oldPath)) return null
        if (isProjectAnchor(old.name)) return null
        val name = targetName(old.parentFile ?: return null, requestedName) ?: return null
        val target = File(old.parentFile, name)
        if (old == target) return name
        return try {
            if (old.renameTo(target)) name else null
        } catch (e: Exception) {
            Log.w(TAG, "renameEntry failed: $oldPath -> $name", e)
            null
        }
    }

    /** 删除文件/目录（目录须不含 main.lua/init.lua，防工程损坏）。 */
    fun deleteEntry(
        rootDir: File,
        path: String,
    ): Boolean {
        val target = File(path)
        if (target == rootDir) return false
        if (!isInsideRoot(rootDir, path)) return false
        if (isProjectAnchor(target.name)) return false
        if (target.isDirectory) {
            val children = target.listFiles()?.toList() ?: emptyList()
            if (children.any { isProjectAnchor(it.name) }) return false
        }
        return try {
            if (target.isDirectory) target.deleteRecursively() else target.delete()
        } catch (e: Exception) {
            Log.w(TAG, "deleteEntry failed: $path", e)
            false
        }
    }
}
