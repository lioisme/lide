package com.lioisme.delua.androlua.common

import android.content.Context
import com.androlua.LuaApplication
import com.lioisme.delua.androlua.common.model.LibraryInfo
import com.lioisme.delua.androlua.common.model.ModuleInfo
import com.lioisme.delua.androlua.common.model.ProjectDependencies
import org.json.JSONObject
import java.io.File

class DependencyManager(private val context: Context) {

    private val luaExtDir: String get() = LuaApplication.getInstance().luaExtDir
    private val commonDir: File get() = File(luaExtDir, "common")
    val commonLibsDir: File get() = File(commonDir, "libs")
    val commonModulesDir: File get() = File(commonDir, "modules")
    private val registryFile: File get() = File(commonDir, "registry.json")

    private val libraryRegistry = LibraryRegistry(commonLibsDir)
    private val moduleRegistry = ModuleRegistry(commonModulesDir)

    fun ensureDirsExist() {
        commonDir.mkdirs()
        commonLibsDir.mkdirs()
        commonModulesDir.mkdirs()
    }

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
        val ext = sourceFile.extension
        val destName = "$name-$version.$ext"
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
        updateRegistry()
    }

    fun uninstallLibrary(name: String): Result<Unit> = runCatching {
        val libDir = File(commonLibsDir, name)
        if (!libDir.isDirectory) throw IllegalArgumentException("Library '$name' not found")
        val projectsWithDep = findProjectsUsingLibrary(name)
        if (projectsWithDep.isNotEmpty()) {
            throw IllegalStateException("Library '$name' is used by projects: ${projectsWithDep.joinToString(", ")}")
        }
        libraryRegistry.remove(name)
        updateRegistry()
    }

    fun listLibraries(): List<LibraryInfo> = libraryRegistry.scan()

    fun getLibraryInfo(name: String): LibraryInfo? {
        val libDir = File(commonLibsDir, name)
        return if (libDir.isDirectory) libraryRegistry.readMetadata(libDir) else null
    }

    fun installModule(
        sourceDir: File,
        name: String,
        version: String,
        description: String = "",
        entryPoint: String = "init.lua",
        author: String = ""
    ): Result<Unit> = runCatching {
        ensureDirsExist()
        val moduleDir = File(commonModulesDir, name)
        moduleDir.mkdirs()
        val luaFiles = sourceDir.listFiles()?.filter { it.name.endsWith(".lua") }
            ?: throw IllegalArgumentException("No .lua files found in source directory")
        for (file in luaFiles) {
            file.copyTo(File(moduleDir, file.name), overwrite = true)
        }
        val allFiles = moduleDir.listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
        val info = ModuleInfo(
            name = name,
            version = version,
            description = description,
            author = author,
            entryPoint = entryPoint,
            files = allFiles,
            installTime = System.currentTimeMillis()
        )
        moduleRegistry.writeMetadata(moduleDir, info)
        updateRegistry()
    }

    fun installModuleFromFiles(
        files: List<File>,
        name: String,
        version: String,
        description: String = "",
        entryPoint: String = "",
        author: String = ""
    ): Result<Unit> = runCatching {
        ensureDirsExist()
        val moduleDir = File(commonModulesDir, name)
        moduleDir.mkdirs()
        for (file in files) {
            file.copyTo(File(moduleDir, file.name), overwrite = true)
        }
        val allFiles = moduleDir.listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
        val ep = if (entryPoint.isNotEmpty()) entryPoint else (files.firstOrNull()?.name ?: "init.lua")
        val info = ModuleInfo(
            name = name,
            version = version,
            description = description,
            author = author,
            entryPoint = ep,
            files = allFiles,
            installTime = System.currentTimeMillis()
        )
        moduleRegistry.writeMetadata(moduleDir, info)
        updateRegistry()
    }

    fun uninstallModule(name: String): Result<Unit> = runCatching {
        val moduleDir = File(commonModulesDir, name)
        if (!moduleDir.isDirectory) throw IllegalArgumentException("Module '$name' not found")
        val projectsWithDep = findProjectsUsingModule(name)
        if (projectsWithDep.isNotEmpty()) {
            throw IllegalStateException("Module '$name' is used by projects: ${projectsWithDep.joinToString(", ")}")
        }
        moduleRegistry.remove(name)
        updateRegistry()
    }

    fun listModules(): List<ModuleInfo> = moduleRegistry.scan()

    fun getModuleInfo(name: String): ModuleInfo? {
        val moduleDir = File(commonModulesDir, name)
        return if (moduleDir.isDirectory) moduleRegistry.readMetadata(moduleDir) else null
    }

    fun getProjectDependencies(projectDir: String): ProjectDependencies {
        val initFile = File(projectDir, "init.lua")
        if (!initFile.exists()) return ProjectDependencies()
        val deluaJson = File(projectDir, "delua.json")
        if (deluaJson.exists()) {
            return parseDeluaJson(deluaJson)
        }
        return parseInitLuaDeps(initFile)
    }

    fun setProjectDependencies(projectDir: String, deps: ProjectDependencies): Result<Unit> = runCatching {
        val initFile = File(projectDir, "init.lua")
        if (!initFile.exists()) throw IllegalArgumentException("init.lua not found in $projectDir")
        var content = initFile.readText()
        content = replaceDependenciesInInitLua(content, deps)
        initFile.writeText(content)
    }

    fun validateDependencies(projectDir: String): DependencyValidationResult {
        val deps = getProjectDependencies(projectDir)
        val missingLibs = deps.libs.filter { name ->
            val libDir = File(commonLibsDir, name)
            !libDir.isDirectory || libraryRegistry.findJarFile(name) == null
        }
        val missingModules = deps.modules.filter { !File(commonModulesDir, it).isDirectory }
        return DependencyValidationResult(
            valid = missingLibs.isEmpty() && missingModules.isEmpty(),
            missingLibs = missingLibs,
            missingModules = missingModules
        )
    }

    /**
     * 把声明的依赖物化到工程内，使全局仓库与 pro 的"工程内自行管理"模型归一：
     * - Java/Dex 库 → `<project>/libs/`（LuaDexLoader.loadLibs 自动加载）
     * - Lua 模块 → `<project>/lua/`（package.path 已含 `工程目录/lua/?.lua`），
     *   含 init.lua 的模块同时落到 `<project>/lua/<name>/`（`require "name"` 走 `?/init.lua`）。
     * 物化前校验依赖完整性，缺失即报错，避免"声明了却 import 不到"。
     */
    fun materializeProjectDependencies(projectDir: String): Result<Unit> = runCatching {
        val validation = validateDependencies(projectDir)
        check(validation.valid) {
            val missing = (validation.missingLibs + validation.missingModules)
                .joinToString(", ")
            "Missing dependencies: $missing"
        }
        val deps = getProjectDependencies(projectDir)
        val projectLibsDir = File(projectDir, "libs")
        if (deps.libs.isNotEmpty()) {
            projectLibsDir.mkdirs()
            for (name in deps.libs) {
                val jar = libraryRegistry.findJarFile(name)
                    ?: throw IllegalStateException("Library '$name' has no jar/dex file")
                jar.copyTo(File(projectLibsDir, jar.name), overwrite = true)
            }
        }
        val luaDir = File(projectDir, "lua")
        for (name in deps.modules) {
            val moduleDir = File(commonModulesDir, name)
            val files = moduleDir.listFiles()?.filter { it.isFile } ?: emptyList()
            check(files.isNotEmpty()) { "Module '$name' is empty" }
            luaDir.mkdirs()
            for (file in files) {
                file.copyTo(File(luaDir, file.name), overwrite = true)
            }
            if (files.any { it.name == "init.lua" }) {
                val namespaceDir = File(luaDir, name)
                namespaceDir.mkdirs()
                for (file in files) {
                    file.copyTo(File(namespaceDir, file.name), overwrite = true)
                }
            }
        }
    }

    data class DependencyValidationResult(
        val valid: Boolean,
        val missingLibs: List<String>,
        val missingModules: List<String>
    )

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

    fun readRegistry(): JSONObject? {
        if (!registryFile.exists()) return null
        return try {
            JSONObject(registryFile.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun updateRegistry() {
        val registry = JSONObject().apply {
            put("schemaVersion", 1)
            put("lastUpdated", System.currentTimeMillis())
            val libs = JSONObject()
            libraryRegistry.scan().forEach { info ->
                libs.put(info.name, JSONObject().apply {
                    put("name", info.name)
                    put("version", info.version)
                    put("description", info.description)
                    put("fileSize", info.fileSize)
                })
            }
            put("libraries", libs)
            val mods = JSONObject()
            moduleRegistry.scan().forEach { info ->
                mods.put(info.name, JSONObject().apply {
                    put("name", info.name)
                    put("version", info.version)
                    put("description", info.description)
                    put("entryPoint", info.entryPoint)
                })
            }
            put("modules", mods)
        }
        commonDir.mkdirs()
        registryFile.writeText(registry.toString(2))
    }

    private fun parseDeluaJson(file: File): ProjectDependencies {
        return try {
            val json = JSONObject(file.readText())
            val deps = json.optJSONObject("dependencies")
            if (deps != null) {
                val libsArr = deps.optJSONArray("libs")
                val modulesArr = deps.optJSONArray("modules")
                ProjectDependencies(
                    libs = libsArr?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: emptyList(),
                    modules = modulesArr?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: emptyList()
                )
            } else ProjectDependencies()
        } catch (_: Exception) {
            ProjectDependencies()
        }
    }

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

    private fun replaceDependenciesInInitLua(content: String, deps: ProjectDependencies): String {
        val libsStr = if (deps.libs.isEmpty()) "" else {
            "    libs = {" + deps.libs.joinToString(", ") { "\"$it\"" } + "}"
        }
        val modulesStr = if (deps.modules.isEmpty()) "" else {
            "    modules = {" + deps.modules.joinToString(", ") { "\"$it\"" } + "}"
        }
        val depsBody = listOfNotNull(
            if (libsStr.isNotEmpty()) libsStr else null,
            if (modulesStr.isNotEmpty()) modulesStr else null
        ).joinToString(",\n")
        val depsBlock = if (depsBody.isNotEmpty()) {
            "\ndependencies = {\n$depsBody\n}"
        } else ""

        val depsRegex = Regex("""\ndependencies\s*=\s*\{[^}]*\}""")
        val hasExisting = depsRegex.containsMatchIn(content)
        return if (hasExisting) {
            depsRegex.replace(content, depsBlock)
        } else if (depsBlock.isNotEmpty()) {
            content.trimEnd() + "\n" + depsBlock.trimStart() + "\n"
        } else {
            content
        }
    }
}
