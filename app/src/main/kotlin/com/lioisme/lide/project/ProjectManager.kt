package com.lioisme.lide.project

import android.util.Log
import com.androlua.LuaApplication
import com.lioisme.lide.androlua.LuaConfigParser
import com.lioisme.lide.androlua.PermissionResolver
import com.lioisme.lide.model.Project
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** 工程扫描与读写服务（纯 File 操作；扫描缓存为进程级）。 */
class ProjectManager {
    /** 工程目录扫描指纹：目录 mtime + init.lua 的 (mtime, length)。 */
    private data class ProjectFingerprint(
        val dirModified: Long,
        val initModified: Long,
        val initLength: Long,
    )

    private data class CachedProject(
        val fingerprint: ProjectFingerprint,
        val project: Project,
    )

    companion object {
        /**
         * 进程级工程扫描缓存：磁盘状态是进程共享的事实源，与 ProjectManager 实例无关，
         * Activity 重建/多实例共存时仍生效。指纹未变即复用上次解析结果，跳过逐工程
         * `readText` + 结构化解析；删除/重命名等已知变更显式失效，其余依赖指纹自动命中。
         * `ConcurrentHashMap.compute` 对同一目录原子化去重并发加载。
         */
        private val projectCache = ConcurrentHashMap<String, CachedProject>()
    }

    fun listProjects(): List<Project> {
        try {
            val extBase = File(LuaApplication.getInstance().luaExtDir)
            val projectSub = File(extBase, "project")
            return scanProjectsIn(projectSub)
        } catch (e: Exception) {
            Log.e("ProjectManager", "failed to scan luaExtDir: ${e.message}")
            return emptyList()
        }
    }

    private fun scanProjectsIn(dir: File): List<Project> {
        if (!dir.isDirectory) return emptyList()
        val entries = dir.listFiles() ?: return emptyList()
        return entries
            .asSequence()
            .filter { it.isDirectory && File(it, "main.lua").exists() && File(it, "init.lua").exists() }
            .mapNotNull { projectDir -> cachedProjectOf(projectDir)?.project }
            .toList()
    }

    /** 指纹命中复用缓存，否则解析并写入；compute 对同一目录原子化去重并发加载。 */
    private fun cachedProjectOf(dir: File): CachedProject? {
        val fingerprint = fingerprintOf(dir)
        return projectCache.compute(dir.absolutePath) { _, cached ->
            if (cached != null && cached.fingerprint == fingerprint) {
                cached
            } else {
                CachedProject(fingerprint, loadProject(dir))
            }
        }
    }

    private fun fingerprintOf(dir: File): ProjectFingerprint {
        val initFile = File(dir, "init.lua")
        return ProjectFingerprint(
            dirModified = dir.lastModified(),
            initModified = initFile.lastModified(),
            initLength = initFile.length(),
        )
    }

    private fun loadProject(dir: File): Project {
        val files =
            dir
                .listFiles()
                ?.filter { it.isFile && it.name.endsWith(".lua") }
                ?.map { it.name }
                ?.sorted() ?: listOf("main.lua", "init.lua")
        val initFile = File(dir, "init.lua")
        var packageName = ""
        var icon: String? = null
        var appName = dir.name
        if (initFile.exists()) {
            try {
                val content = initFile.readText()
                // 结构化解析（跳过注释/字符串，避免正则命中注释内容）。
                appName = LuaConfigParser.stringField(content, "appname") ?: appName
                packageName = LuaConfigParser.stringField(content, "packagename")
                    ?: LuaConfigParser.stringField(content, "package") ?: ""
                icon = LuaConfigParser.stringField(content, "icon")
            } catch (_: Exception) {
            }
        }
        return Project(
            name = appName,
            path = dir.absolutePath,
            files = files,
            packageName = packageName,
            icon = icon,
        )
    }

    fun listProjectFiles(project: Project): List<String> {
        val dir = File(project.path)
        return dir
            .listFiles()
            ?.filter { it.isFile && it.name.endsWith(".lua") }
            ?.map { it.name }
            ?.sorted() ?: listOf(project.mainFile)
    }

    /**
     * 读取工程 init.lua 声明的 user_permission（WP-003）。
     * 读失败/缺省 → 空集合（降级，不抛）。
     */
    fun loadUserPermissions(project: Project): List<String> =
        try {
            val initFile = File(project.path, "init.lua")
            if (initFile.exists()) {
                PermissionResolver.declared(initFile.readText())
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }

    fun getProjectScript(
        project: Project,
        fileName: String = project.mainFile,
    ): String {
        val file = File(project.path, fileName)
        return try {
            if (file.exists()) file.readText() else "-- empty"
        } catch (_: Exception) {
            "-- error reading file"
        }
    }

    fun saveProjectScript(
        project: Project,
        fileName: String,
        content: String,
    ): Boolean =
        try {
            val file = File(project.path, fileName)
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            Log.e("ProjectManager", "save failed: ${e.message}")
            false
        }

    fun createProject(name: String): Project? {
        val extDir = LuaApplication.getInstance().luaExtDir
        // 隐私（P3-7）：不打印 luaExtDir/工程绝对路径，仅记录工程名。
        Log.d("ProjectManager", "createProject: name=$name")
        val base = File(extDir, "project")
        if (!base.exists() && !base.mkdirs()) {
            Log.e("ProjectManager", "Failed to create project base directory")
            return null
        }
        val dir = File(base, name)
        if (!dir.mkdirs()) {
            Log.e("ProjectManager", "Failed to create project directory: $name")
            return null
        }
        val packageName = "com.lioisme.lide.$name"
        try {
            File(dir, "main.lua").writeText("-- $name\nprint(\"hello from $name!\")\n")
            File(dir, "init.lua").writeText(
                """
appname = "$name"
appver = "1.0"
packagename = "$packageName"
icon = nil
pages = {"main"}
user_permission = {}
                """.trimIndent(),
            )
        } catch (e: Exception) {
            Log.e("ProjectManager", "Failed to write project files: ${e.message}")
            dir.deleteRecursively()
            return null
        }
        Log.d("ProjectManager", "Created project $name")
        return Project(
            name = name,
            path = dir.absolutePath,
            files = listOf("main.lua", "init.lua"),
            packageName = packageName,
        )
    }

    fun deleteProject(project: Project): Boolean =
        try {
            projectCache.remove(project.path)
            File(project.path).deleteRecursively()
        } catch (_: Exception) {
            false
        }

    // ---- WP-005 文件树操作 + 工程重命名（纯逻辑见 [ProjectEntryOps]，可单测）----

    private fun rootDir(project: Project): File = File(project.path)

    /** 条目名校验（委托 [ProjectEntryOps]）。 */
    fun isValidEntryName(name: String): Boolean = ProjectEntryOps.isValidEntryName(name)

    /** 新建 .lua 文件（[dirPath] 为工程内绝对路径）。 */
    fun createProjectFile(
        project: Project,
        dirPath: String,
        requestedName: String,
    ): Boolean = ProjectEntryOps.createFile(rootDir(project), dirPath, requestedName)

    /** 新建目录（[dirPath] 为工程内绝对路径）。 */
    fun createProjectDirectory(
        project: Project,
        dirPath: String,
        requestedName: String,
    ): Boolean = ProjectEntryOps.createDirectory(rootDir(project), dirPath, requestedName)

    /** 重命名工程内条目（锚点/越界/非法名防护见 [ProjectEntryOps]）。 */
    fun renameProjectEntry(
        project: Project,
        oldPath: String,
        requestedName: String,
    ): String? = ProjectEntryOps.renameEntry(rootDir(project), oldPath, requestedName)

    /** 删除工程内文件/目录（含锚点目录保护）。 */
    fun deleteProjectEntry(
        project: Project,
        path: String,
    ): Boolean = ProjectEntryOps.deleteEntry(rootDir(project), path)

    /**
     * 工程重命名（WP-005）：目录改名 + init.lua appname 同步。
     * 重名冲突自动去重 -1/-2（对齐 .alp 导入策略）；失败返回 null。
     * 成功后返回刷新后的 [Project] 数据（路径/名称），供列表刷新使用。
     */
    fun renameProject(
        project: Project,
        requestedName: String,
    ): Project? {
        val parent = File(project.path).parentFile ?: return null
        val name = ProjectEntryOps.dedupeEntryName(parent, requestedName) ?: return null
        val targetDir = File(parent, name)
        if (project.path == targetDir.absolutePath) return project
        val ok =
            try {
                File(project.path).renameTo(targetDir)
            } catch (_: Exception) {
                false
            }
        if (!ok) return null
        projectCache.remove(project.path)
        projectCache.remove(targetDir.absolutePath)
        try {
            val initFile = File(targetDir, "init.lua")
            if (initFile.exists()) {
                initFile.writeText(
                    LuaConfigParser.setField(
                        initFile.readText(),
                        "appname",
                        LuaConfigParser.quoteLuaString(name),
                    ),
                )
            }
        } catch (_: Exception) {
        }
        return Project(
            name = name,
            path = targetDir.absolutePath,
            files = project.files,
            packageName = project.packageName,
            icon = project.icon,
        )
    }
}
