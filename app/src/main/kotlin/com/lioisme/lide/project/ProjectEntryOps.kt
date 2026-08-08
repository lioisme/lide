package com.lioisme.lide.project

import com.lioisme.lide.project.AlpNaming
import java.io.File

/**
 * 工程内文件树操作的纯逻辑（WP-005）：名称校验 / 去重 / 锚点保护 / 越界防护。
 * 全部为无副作用纯函数（仅文件系统判定），与 [ProjectManager] 的宿主耦合（Context、
 * luaExtDir）分离，可单测。目录参数一律为绝对路径。
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

    /** 工程锚点保护：main.lua / init.lua 禁止重命名与删除。 */
    fun isProjectAnchor(name: String): Boolean = name == "main.lua" || name == "init.lua"

    /** [path] 是否位于 [rootDir] 内（归一化路径前缀判定，防越界操作）。 */
    fun isInsideRoot(
        rootDir: File,
        path: String,
    ): Boolean =
        try {
            File(path).toPath().startsWith(rootDir.toPath())
        } catch (_: Throwable) {
            false
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
            false
        }
    }
}
