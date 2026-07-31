package com.lioisme.delua.androlua.common

import android.content.Context
import com.androlua.LuaApplication
import com.lioisme.delua.androlua.common.model.LibraryInfo
import com.lioisme.delua.androlua.common.model.ModuleInfo
import com.lioisme.delua.androlua.common.model.ProjectDependencies
import org.json.JSONObject
import java.io.File

/**
 * 统一管理层：全局仓库（common/libs + common/modules）的安装 / 卸载 / 校验，
 * 以及运行前把声明的依赖物化（materialize）到工程目录——这是全局仓库与
 * pro 式"工程内自行管理"模型之间的唯一桥梁。
 *
 * 事实源约定：工程依赖声明的单一事实源是 `<project>/init.lua` 的
 * `dependencies = { libs = {...}, modules = {...} }` 块（pro 兼容）；
 * delua.json 已废弃，读路径于本次审计收口移除。
 */
class DependencyManager(private val context: Context) {

    private val luaExtDir: String get() = LuaApplication.getInstance().luaExtDir
    private val commonDir: File get() = File(luaExtDir, "common")
    val commonLibsDir: File get() = File(commonDir, "libs")
    val commonModulesDir: File get() = File(commonDir, "modules")

    private val libraryRegistry = LibraryRegistry(commonLibsDir)
    private val moduleRegistry = ModuleRegistry(commonModulesDir)

    fun ensureDirsExist() {
        commonDir.mkdirs()
        commonLibsDir.mkdirs()
        commonModulesDir.mkdirs()
    }

    // ------------------------------------------------------------------
    // 库仓库
    // ------------------------------------------------------------------

    /**
     * 安装库。单构件约束：
     * - jar/dex → `<name>-<version>.<ext>`（重装先清除旧构件，无孤儿版本）；
     * - so → `lib<name>.so`（libsloader / libCache 要求库名与文件名严格一致，
     *   版本只记录在 metadata.json，升级即覆盖）。
     */
    fun installLibrary(
        sourceFile: File,
        name: String,
        version: String,
        description: String = "",
        author: String = ""
    ): Result<Unit> = runCatching {
        ensureDirsExist()
        val libDir = File(commonLibsDir, name)
        libDir.mkdirs()
        removeArtifacts(libDir)
        val ext = sourceFile.extension.lowercase()
        val destName = if (ext == "so") "lib$name.so" else "$name-$version.$ext"
        val destFile = File(libDir, destName)
        sourceFile.copyTo(destFile, overwrite = true)
        val info = LibraryInfo(
            name = name,
            version = version,
            description = description,
            author = author,
            filePath = destFile.absolutePath,
            fileSize = destFile.length(),
            installTime = System.currentTimeMillis()
        )
        libraryRegistry.writeMetadata(libDir, info)
    }

    private fun removeArtifacts(libDir: File) {
        libDir.listFiles()?.forEach { f ->
            if (f.isFile && f.name != "metadata.json") {
                f.delete()
            }
        }
    }

    fun uninstallLibrary(name: String): Result<Unit> = runCatching {
        val libDir = File(commonLibsDir, name)
        if (!libDir.isDirectory) throw IllegalArgumentException("Library '$name' not found")
        val projectsWithDep = findProjectsUsingLibrary(name)
        if (projectsWithDep.isNotEmpty()) {
            throw IllegalStateException("Library '$name' is used by projects: ${projectsWithDep.joinToString(", ")}")
        }
        libraryRegistry.remove(name)
    }

    fun listLibraries(): List<LibraryInfo> = libraryRegistry.scan()

    // ------------------------------------------------------------------
    // 模块仓库
    // ------------------------------------------------------------------

    /**
     * 从目录安装模块：保留相对路径（socket 式目录模块可入仓），
     * files[] 记录相对路径，供物化原样还原。
     */
    fun installModule(
        sourceDir: File,
        name: String,
        version: String,
        description: String = "",
        entryPoint: String = "init.lua",
        author: String = ""
    ): Result<Unit> = runCatching {
        ensureDirsExist()
        val luaFiles = collectLuaFiles(sourceDir)
        check(luaFiles.isNotEmpty()) { "No .lua files found in source directory" }
        val moduleDir = File(commonModulesDir, name)
        moduleDir.deleteRecursively()
        moduleDir.mkdirs()
        val relPaths = luaFiles.map { it.relativeTo(sourceDir).path.replace('\\', '/') }
        for (rel in relPaths) {
            val dest = File(moduleDir, rel)
            dest.parentFile?.mkdirs()
            File(sourceDir, rel).copyTo(dest, overwrite = true)
        }
        val info = ModuleInfo(
            name = name,
            version = version,
            description = description,
            author = author,
            entryPoint = entryPoint,
            files = relPaths,
            installTime = System.currentTimeMillis()
        )
        moduleRegistry.writeMetadata(moduleDir, info)
    }

    /** 从平铺文件列表安装模块（多文件选择器场景，文件已集中在临时目录）。 */
    fun installModuleFromFiles(
        files: List<File>,
        name: String,
        version: String,
        description: String = "",
        entryPoint: String = "",
        author: String = ""
    ): Result<Unit> = runCatching {
        ensureDirsExist()
        val luaFiles = files.filter { it.isFile && it.name.endsWith(".lua", ignoreCase = true) }
        check(luaFiles.isNotEmpty()) { "No .lua files found" }
        val moduleDir = File(commonModulesDir, name)
        moduleDir.deleteRecursively()
        moduleDir.mkdirs()
        val relPaths = luaFiles.map { it.name }
        for (file in luaFiles) {
            val dest = File(moduleDir, file.name)
            dest.parentFile?.mkdirs()
            file.copyTo(dest, overwrite = true)
        }
        val ep = if (entryPoint.isNotEmpty()) entryPoint else relPaths.first()
        val info = ModuleInfo(
            name = name,
            version = version,
            description = description,
            author = author,
            entryPoint = ep,
            files = relPaths,
            installTime = System.currentTimeMillis()
        )
        moduleRegistry.writeMetadata(moduleDir, info)
    }

    fun uninstallModule(name: String): Result<Unit> = runCatching {
        val moduleDir = File(commonModulesDir, name)
        if (!moduleDir.isDirectory) throw IllegalArgumentException("Module '$name' not found")
        val projectsWithDep = findProjectsUsingModule(name)
        if (projectsWithDep.isNotEmpty()) {
            throw IllegalStateException("Module '$name' is used by projects: ${projectsWithDep.joinToString(", ")}")
        }
        moduleRegistry.remove(name)
    }

    fun listModules(): List<ModuleInfo> = moduleRegistry.scan()

    private fun collectLuaFiles(dir: File): List<File> {
        if (!dir.isDirectory) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".lua", ignoreCase = true) }
            .sortedBy { it.absolutePath }
            .toList()
    }

    // ------------------------------------------------------------------
    // 工程依赖声明（单一事实源：init.lua 的 dependencies 块）
    // ------------------------------------------------------------------

    fun getProjectDependencies(projectDir: String): ProjectDependencies {
        val initFile = File(projectDir, "init.lua")
        if (!initFile.exists()) return ProjectDependencies()
        return parseInitLuaDeps(initFile)
    }

    fun setProjectDependencies(projectDir: String, deps: ProjectDependencies): Result<Unit> = runCatching {
        val initFile = File(projectDir, "init.lua")
        if (!initFile.exists()) throw IllegalArgumentException("init.lua not found in $projectDir")
        var content = initFile.readText()
        content = replaceDependenciesBlock(content, deps)
        initFile.writeText(content)
    }

    fun validateDependencies(projectDir: String): DependencyValidationResult {
        val deps = getProjectDependencies(projectDir)
        val missingLibs = deps.libs.filter { name ->
            val libDir = File(commonLibsDir, name)
            !libDir.isDirectory || libraryRegistry.findArtifactFile(name) == null
        }
        val missingModules = deps.modules.filter { !File(commonModulesDir, it).isDirectory }
        return DependencyValidationResult(
            valid = missingLibs.isEmpty() && missingModules.isEmpty(),
            missingLibs = missingLibs,
            missingModules = missingModules
        )
    }

    data class DependencyValidationResult(
        val valid: Boolean,
        val missingLibs: List<String>,
        val missingModules: List<String>
    )

    /**
     * 把声明的依赖物化到工程内，使全局仓库与 pro 的"工程内自行管理"模型归一：
     * - Java/Dex/原生库 → `<project>/libs/`（LuaDexLoader.loadLibs 自动加载；
     *   .so 以 `lib<name>.so` 命名，libsloader 按名命中）；
     * - Lua 模块 → `<project>/lua/<相对路径>`（package.path 已含 `工程/lua/?.lua`），
     *   含 init.lua 的模块同时落到 `<project>/lua/<name>/`（`require "name"` 走 `?/init.lua`）。
     *
     * 幂等且可清理：先校验依赖完整性（缺失即拒绝运行），再按上一轮物化清单
     * `.delua-materialized.json` 移除已声明但已取消/换版的残留文件
     * （只动本管理器物化的文件，用户手放的 libs/lua 文件不受影响）。
     */
    fun materializeProjectDependencies(projectDir: String): Result<Unit> = runCatching {
        val validation = validateDependencies(projectDir)
        check(validation.valid) {
            val missing = (validation.missingLibs + validation.missingModules)
                .joinToString(", ")
            "Missing dependencies: $missing"
        }
        val deps = getProjectDependencies(projectDir)
        val projectRoot = File(projectDir)
        val manifest = MaterializationManifest.load(projectRoot)

        val newLibFiles = mutableListOf<String>()
        val projectLibsDir = File(projectRoot, "libs")
        for (name in deps.libs) {
            val artifact = libraryRegistry.findArtifactFile(name)
                ?: throw IllegalStateException("Library '$name' has no jar/dex/so file")
            projectLibsDir.mkdirs()
            val dest = File(projectLibsDir, artifact.name)
            artifact.copyTo(dest, overwrite = true)
            newLibFiles += dest.relativeTo(projectRoot).path.replace('\\', '/')
        }
        syncMaterializedFiles(projectRoot, manifest.libraries, newLibFiles)

        val newModuleFiles = mutableListOf<String>()
        val luaDir = File(projectRoot, "lua")
        for (name in deps.modules) {
            val moduleDir = File(commonModulesDir, name)
            val info = moduleRegistry.readMetadata(moduleDir)
                ?: throw IllegalStateException("Module '$name' has no metadata")
            val files = info.files
            check(files.isNotEmpty()) { "Module '$name' is empty" }
            for (rel in files) {
                val src = File(moduleDir, rel)
                if (!src.isFile) continue
                luaDir.mkdirs()
                val dest = File(luaDir, rel)
                dest.parentFile?.mkdirs()
                src.copyTo(dest, overwrite = true)
                newModuleFiles += dest.relativeTo(projectRoot).path.replace('\\', '/')
            }
            if (files.any { it.equals("init.lua", ignoreCase = true) }) {
                val namespaceDir = File(luaDir, name)
                for (rel in files) {
                    val src = File(moduleDir, rel)
                    if (!src.isFile) continue
                    val dest = File(namespaceDir, rel)
                    dest.parentFile?.mkdirs()
                    src.copyTo(dest, overwrite = true)
                    newModuleFiles += dest.relativeTo(projectRoot).path.replace('\\', '/')
                }
            }
        }
        syncMaterializedFiles(projectRoot, manifest.modules, newModuleFiles)

        MaterializationManifest(libraries = newLibFiles, modules = newModuleFiles).save(projectRoot)
    }

    /** 删除上一轮物化清单中已不在声明集合内的文件，并向上清理空目录。 */
    private fun syncMaterializedFiles(
        projectRoot: File,
        previous: List<String>,
        current: List<String>
    ) {
        val currentSet = current.toHashSet()
        val rootPrefix = projectRoot.canonicalPath + File.separator
        for (rel in previous) {
            if (rel in currentSet) continue
            val target = File(projectRoot, rel)
            if (target.canonicalPath.startsWith(rootPrefix)) {
                target.delete()
                pruneEmptyParents(projectRoot, target)
            }
        }
    }

    private fun pruneEmptyParents(projectRoot: File, file: File) {
        var dir = file.parentFile ?: return
        while (dir != projectRoot && dir.listFiles()?.isEmpty() == true) {
            dir.delete()
            dir = dir.parentFile ?: return
        }
    }

    fun findProjectsUsingLibrary(libName: String): List<String> {
        val projectDir = File(luaExtDir, "project")
        if (!projectDir.isDirectory) return emptyList()
        return projectDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            val deps = getProjectDependencies(dir.absolutePath)
            if (deps.libs.contains(libName)) dir.name else null
        } ?: emptyList()
    }

    fun findProjectsUsingModule(moduleName: String): List<String> {
        val projectDir = File(luaExtDir, "project")
        if (!projectDir.isDirectory) return emptyList()
        return projectDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            val deps = getProjectDependencies(dir.absolutePath)
            if (deps.modules.contains(moduleName)) dir.name else null
        } ?: emptyList()
    }

    // ------------------------------------------------------------------
    // init.lua 依赖块解析 / 结构化替换
    // ------------------------------------------------------------------

    private fun parseInitLuaDeps(initFile: File): ProjectDependencies {
        return try {
            val content = initFile.readText()
            val libs = parseStringList(content, "libs")
            val modules = parseStringList(content, "modules")
            ProjectDependencies(libs = libs, modules = modules)
        } catch (_: Exception) {
            ProjectDependencies()
        }
    }

    private fun parseStringList(content: String, key: String): List<String> {
        val regex = Regex("""$key\s*=\s*\{([^}]*)\}""")
        val match = regex.find(content) ?: return emptyList()
        val body = match.groupValues[1].trim()
        if (body.isEmpty()) return emptyList()
        return body.split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
    }

    /**
     * 结构化替换 dependencies 块：跳过字符串 / 注释 / 长括号，定位
     * `dependencies` 标识符后配平花括号的完整块并替换；不存在则追加到文件末尾。
     * （替代历史脆弱正则：原方案要求块前有换行、不处理嵌套花括号，易重复追加。）
     */
    private fun replaceDependenciesBlock(content: String, deps: ProjectDependencies): String {
        val block = buildDependenciesBlock(deps)
        val range = findDependenciesBlock(content)
        return if (range != null) {
            content.replaceRange(range.first, range.last + 1, block)
        } else if (block.isNotEmpty()) {
            content.trimEnd() + "\n" + block + "\n"
        } else {
            content
        }
    }

    private fun buildDependenciesBlock(deps: ProjectDependencies): String {
        val lines = mutableListOf<String>()
        if (deps.libs.isNotEmpty()) {
            lines += "    libs = {" + deps.libs.joinToString(", ") { "\"$it\"" } + "}"
        }
        if (deps.modules.isNotEmpty()) {
            lines += "    modules = {" + deps.modules.joinToString(", ") { "\"$it\"" } + "}"
        }
        if (lines.isEmpty()) return ""
        return "dependencies = {\n" + lines.joinToString(",\n") + "\n}"
    }

    private val keyword = "dependencies"

    private fun findDependenciesBlock(content: String): IntRange? {
        var i = 0
        val n = content.length
        while (i < n) {
            when (content[i]) {
                '"', '\'' -> i = skipString(content, i)
                '-' -> {
                    if (i + 1 < n && content[i + 1] == '-') {
                        i = skipLineComment(content, i)
                    } else {
                        i++
                    }
                }
                '[' -> {
                    val end = skipLongBracket(content, i)
                    i = end ?: (i + 1)
                }
                else -> {
                    if (content[i] == 'd' && content.regionMatches(i, keyword, 0, keyword.length)) {
                        val beforeOk = i == 0 || !isWordChar(content[i - 1])
                        val afterOk = i + keyword.length >= n || !isWordChar(content[i + keyword.length])
                        if (beforeOk && afterOk) {
                            var j = skipWhitespace(content, i + keyword.length)
                            if (j < n && content[j] == '=') {
                                j = skipWhitespace(content, j + 1)
                                if (j < n && content[j] == '{') {
                                    val end = skipBalancedBraces(content, j)
                                    if (end != null) return i..end
                                }
                            }
                        }
                    }
                    i++
                }
            }
        }
        return null
    }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

    private fun skipWhitespace(content: String, start: Int): Int {
        var i = start
        while (i < content.length && content[i].isWhitespace()) i++
        return i
    }

    /** 跳过双引号 / 单引号字符串（处理转义），返回字符串结束后的下标。 */
    private fun skipString(content: String, start: Int): Int {
        val quote = content[start]
        var i = start + 1
        while (i < content.length) {
            when (content[i]) {
                '\\' -> i += 2
                quote -> return i + 1
                else -> i++
            }
        }
        return content.length
    }

    /** 跳过 `--` 行注释，返回行尾后的下标。 */
    private fun skipLineComment(content: String, start: Int): Int {
        var i = start + 2
        while (i < content.length && content[i] != '\n') i++
        return i
    }

    /** 若为长括号（`[[` / `[=[`）返回其结束后的下标，否则 null。 */
    private fun skipLongBracket(content: String, start: Int): Int? {
        var eq = 0
        var i = start + 1
        while (i < content.length && content[i] == '=') {
            eq++
            i++
        }
        if (i >= content.length || content[i] != '[') return null
        i++
        val closeTag = "]" + "=".repeat(eq) + "]"
        val end = content.indexOf(closeTag, i)
        return if (end < 0) content.length else end + closeTag.length
    }

    /** 从 `{` 起配平花括号（嵌套块内跳过字符串/注释/长括号），返回 `}` 下标。 */
    private fun skipBalancedBraces(content: String, openIdx: Int): Int? {
        var depth = 0
        var i = openIdx
        val n = content.length
        while (i < n) {
            when (content[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
                '"', '\'' -> i = skipString(content, i)
                '-' -> {
                    if (i + 1 < n && content[i + 1] == '-') {
                        i = skipLineComment(content, i)
                    } else {
                        i++
                    }
                }
                '[' -> {
                    val end = skipLongBracket(content, i)
                    if (end != null) i = end
                }
            }
            i++
        }
        return null
    }

    // ------------------------------------------------------------------
    // 物化清单（.delua-materialized.json）
    // ------------------------------------------------------------------

    /** 记录本管理器物化到工程内的文件（相对路径），供运行前同步清理。 */
    private data class MaterializationManifest(
        val libraries: List<String> = emptyList(),
        val modules: List<String> = emptyList(),
    ) {
        companion object {
            private const val FILE_NAME = ".delua-materialized.json"

            fun load(projectRoot: File): MaterializationManifest {
                val file = File(projectRoot, FILE_NAME)
                if (!file.exists()) return MaterializationManifest()
                return try {
                    val json = JSONObject(file.readText())
                    MaterializationManifest(
                        libraries = readStringList(json.optJSONArray("libraries")),
                        modules = readStringList(json.optJSONArray("modules")),
                    )
                } catch (_: Exception) {
                    MaterializationManifest()
                }
            }

            private fun readStringList(array: org.json.JSONArray?): List<String> {
                if (array == null) return emptyList()
                return (0 until array.length()).map { array.getString(it) }
            }
        }

        fun save(projectRoot: File) {
            val json = JSONObject().apply {
                put("schemaVersion", 1)
                put("libraries", libraries)
                put("modules", modules)
            }
            File(projectRoot, FILE_NAME).writeText(json.toString(2))
        }
    }
}
