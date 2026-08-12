package com.lioisme.lide.project

import android.util.Log
import com.lioisme.lide.engine.RuntimePaths
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
        private const val TAG = "ProjectManager"

        /**
         * 进程级工程扫描缓存：磁盘状态是进程共享的事实源，与 ProjectManager 实例无关，
         * Activity 重建/多实例共存时仍生效。指纹未变即复用上次解析结果，跳过逐工程
         * `readText` + 结构化解析；删除/重命名等已知变更显式失效，其余依赖指纹自动命中。
         * `ConcurrentHashMap.compute` 对同一目录原子化去重并发加载。
         */
        private val projectCache = ConcurrentHashMap<String, CachedProject>()

        /**
         * init.lua 模板单一事实源（P4b 去重）：MainActivity 单文件收养与
         * [createProject] 共用，禁止各写一份。
         */
        fun initLuaTemplate(
            appName: String,
            packageName: String,
        ): String =
            """
            appname = "$appName"
            appver = "1.0"
            packagename = "$packageName"
            icon = nil
            pages = {"main"}
            user_permission = {}
            """.trimIndent()

        /**
         * C7（2026-08-12）：工程包名生成单一事实源（MainActivity 单文件收养与
         * [createProject] 共用，消除 `com.lioisme.lide.<name>` 模板两处重复）。
         *
         * 清洗规则（工程名可含空格/连字符，而 Java 包段要求标识符字符）：
         * 小写化、非字母数字/下划线字符 → 下划线、剥首尾下划线；空结果回落 "app"。
         */
        fun packageNameFor(name: String): String {
            val cleaned =
                name.trim().lowercase()
                    .map { ch -> if (ch.isLetterOrDigit() || ch == '_') ch else '_' }
                    .joinToString("")
                    .trim('_')
            return "com.lioisme.lide." + cleaned.ifEmpty { "app" }
        }
    }

    /**
     * 扫描 luaExtDir/project 下全部工程（Q9，审计 P1-2）。
     *
     * [luaExtDir] 由调用方注入：宿主在调用点取 `RuntimePaths.luaExtDir()`，
     * 本类不再持有进程级全局依赖——JVM 单测可传临时目录直接验证扫描/解析逻辑。
     */
    fun listProjects(luaExtDir: String): List<Project> {
        try {
            val extBase = File(luaExtDir)
            val projectSub = File(extBase, RuntimePaths.PROJECT_DIR_NAME)
            return scanProjectsIn(projectSub)
        } catch (e: Exception) {
            Log.e(TAG, "failed to scan luaExtDir: ${e.message}")
            return emptyList()
        }
    }

    private fun scanProjectsIn(dir: File): List<Project> {
        if (!dir.isDirectory) return emptyList()
        val entries = dir.listFiles() ?: return emptyList()
        return entries
            .asSequence()
            .filter { it.isDirectory && File(it, Project.DEFAULT_MAIN_FILE).exists() && File(it, "init.lua").exists() }
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
        val initFile = File(dir, "init.lua")
        var packageName = ""
        var icon: String? = null
        var appName = dir.name
        if (initFile.exists()) {
            try {
                val content = initFile.readText()
                // 结构化解析（跳过注释/字符串，避免正则命中注释内容）。
                // D3：单次 parse 取全部字符串字段（原按字段各扫一遍）。
                val fields = LuaConfigParser.stringFields(content)
                appName = fields["appname"] ?: appName
                packageName = fields["packagename"] ?: fields["package"] ?: ""
                icon = fields["icon"]
            } catch (e: Exception) {
                Log.w(TAG, "loadProject: parse init.lua failed (fallback to dir name): ${dir.name}", e)
            }
        }
        return Project(
            name = appName,
            path = dir.absolutePath,
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
     * 读取工程 init.lua 原始文本（不存在/读失败 → null，显式可空，B-1 域服务收敛）。
     */
    fun readInitContent(project: Project): String? =
        try {
            val initFile = File(project.path, "init.lua")
            if (initFile.exists()) initFile.readText() else null
        } catch (e: Exception) {
            Log.w(TAG, "readInitContent failed: ${project.name}", e)
            null
        }

    /**
     * 读取工程 init.lua 声明的 user_permission（WP-003）。
     * 读失败/缺省 → 空集合（降级，不抛）。
     */
    fun loadUserPermissions(project: Project): List<String> =
        try {
            val content = readInitContent(project)
            if (content != null) PermissionResolver.declared(content) else emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "loadUserPermissions parse failed: ${project.name}", e)
            emptyList()
        }

    /**
     * 字段级写入 init.lua（B-1 域服务：UI 不再自摸文件）。
     * 原子读改写；返回是否成功。
     */
    fun writeInitFields(
        project: Project,
        transform: (String) -> String,
    ): Boolean =
        try {
            val content = readInitContent(project) ?: return false
            atomicWrite(File(project.path, "init.lua"), transform(content))
            true
        } catch (e: Exception) {
            Log.w(TAG, "writeInitFields failed: ${project.name}", e)
            false
        }

    /** 读取工程脚本原文；文件缺失/读取失败 → null（不伪造占位文本）。 */
    fun getProjectScript(
        project: Project,
        fileName: String = project.mainFile,
    ): String? {
        val file = File(project.path, fileName)
        return try {
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            Log.w(TAG, "getProjectScript failed: ${project.name}/$fileName", e)
            null
        }
    }

    /**
     * 原子写盘（T7，2026-08-10）：同目录 tmp + renameTo 原子替换。
     *
     * `writeText` 直写中途中断会截断工程文件；tmp 与目标同目录、renameTo 在同一
     * 文件系统内为原子替换，目标文件要么保持旧内容要么是新内容，无中间截断态。
     * tmp 写入失败删除残留并兜底直写；renameTo 失败同样兜底直写（尽力而为，
     * 不改变调用方「false + 日志」的失败语义）。
     */
    private fun atomicWrite(
        file: File,
        content: String,
    ) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            tmp.writeText(content)
            if (!tmp.renameTo(file)) {
                // renameTo 平台差异（Windows 目标已存在时失败；Android POSIX rename 原子替换）。
                Log.w(TAG, "atomic rename failed, fallback direct write: ${file.name}")
                tmp.delete()
                file.writeText(content)
            }
        } catch (e: Exception) {
            tmp.delete()
            Log.w(TAG, "atomic write failed, fallback direct write: ${file.name}", e)
            file.writeText(content)
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
            atomicWrite(file, content)
            true
        } catch (e: Exception) {
            Log.e(TAG, "save failed: ${e.message}")
            false
        }

    fun createProject(
        name: String,
        luaExtDir: String,
    ): Project? {
        // 名称校验（Q9，复用文件树条目规则）：拒绝空名/路径分隔符/通配符等，
        // 防止用户输入经 mkdirs 逃逸工程根目录（宿主写外部存储的路径注入面）。
        if (!ProjectEntryOps.isValidEntryName(name)) {
            Log.w(TAG, "createProject: invalid name rejected: $name")
            return null
        }
        // 隐私（P3-7）：不打印 luaExtDir/工程绝对路径，仅记录工程名。
        Log.d(TAG, "createProject: name=$name")
        val base = File(luaExtDir, RuntimePaths.PROJECT_DIR_NAME)
        if (!base.exists() && !base.mkdirs()) {
            Log.e(TAG, "Failed to create project base directory")
            return null
        }
        val dir = File(base, name)
        if (!dir.mkdirs()) {
            Log.e(TAG, "Failed to create project directory: $name")
            return null
        }
        val packageName = packageNameFor(name)
        try {
            File(dir, Project.DEFAULT_MAIN_FILE).writeText("-- $name\nprint(\"hello from $name!\")\n")
            File(dir, "init.lua").writeText(initLuaTemplate(name, packageName))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write project files: ${e.message}")
            dir.deleteRecursively()
            return null
        }
        Log.d(TAG, "Created project $name")
        return Project(
            name = name,
            path = dir.absolutePath,
            packageName = packageName,
        )
    }

    fun deleteProject(project: Project): Boolean =
        try {
            projectCache.remove(project.path)
            File(project.path).deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "deleteProject failed: ${project.name}", e)
            false
        }

    // ---- 工程重命名（纯逻辑见 [ProjectEntryOps]，可单测）----

    /**
     * 条目名校验（委托 [ProjectEntryOps]；新建工程入口与重命名对话框共用）。
     */
    fun isValidEntryName(name: String): Boolean = ProjectEntryOps.isValidEntryName(name)

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
            } catch (e: Exception) {
                Log.w(TAG, "renameProject: directory rename failed: ${project.name}", e)
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
        } catch (e: Exception) {
            // 元数据同步失败：回滚目录改名，保证磁盘工程名与显示名一致；回滚也失败时
            // 保留告警（目录已改名但 appname 未同步，用户可见工程名由列表刷新兜底）。
                Log.e(TAG, "renameProject: init.lua appname sync failed, rolling back rename: $name", e)
            val rollbackOk =
                try {
                    targetDir.renameTo(File(project.path))
                } catch (re: Exception) {
                    Log.e(TAG, "renameProject: rollback rename failed: ${project.name}", re)
                    false
                }
            if (!rollbackOk) {
                Log.e(TAG, "renameProject: rollback failed, disk name and init.lua appname mismatch: $name")
            }
            return null
        }
        return Project(
            name = name,
            path = targetDir.absolutePath,
            packageName = project.packageName,
            icon = project.icon,
        )
    }
}
