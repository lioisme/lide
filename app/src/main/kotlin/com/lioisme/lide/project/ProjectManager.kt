package com.lioisme.lide.project

import android.content.Context
import android.util.Log
import com.androlua.LuaApplication
import com.lioisme.lide.androlua.LuaConfigParser
import com.lioisme.lide.androlua.PermissionResolver
import com.lioisme.lide.model.Project
import java.io.File

class ProjectManager(private val context: Context) {

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
        return dir.listFiles()
            ?.filter { it.isDirectory && File(it, "main.lua").exists() && File(it, "init.lua").exists() }
            ?.map { loadProject(it) }
            ?: emptyList()
    }

    private fun loadProject(dir: File): Project {
        val files = dir.listFiles()
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
            } catch (_: Exception) { }
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
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".lua") }
            ?.map { it.name }
            ?.sorted() ?: listOf(project.mainFile)
    }

    /**
     * 读取工程 init.lua 声明的 user_permission（WP-003）。
     * 读失败/缺省 → 空集合（降级，不抛）。
     */
    fun loadUserPermissions(project: Project): List<String> {
        return try {
            val initFile = File(project.path, "init.lua")
            if (initFile.exists()) {
                PermissionResolver.declared(initFile.readText())
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getProjectScript(project: Project, fileName: String = project.mainFile): String {
        val file = File(project.path, fileName)
        return try {
            if (file.exists()) file.readText() else "-- empty"
        } catch (_: Exception) {
            "-- error reading file"
        }
    }

    fun saveProjectScript(project: Project, fileName: String, content: String): Boolean {
        return try {
            val file = File(project.path, fileName)
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            Log.e("ProjectManager", "save failed: ${e.message}")
            false
        }
    }

    fun createProject(name: String): Project? {
        val extDir = LuaApplication.getInstance().luaExtDir
        Log.d("ProjectManager", "createProject: name=$name, luaExtDir=$extDir")
        val base = File(extDir, "project")
        if (!base.exists() && !base.mkdirs()) {
            Log.e("ProjectManager", "Failed to create project directory: $extDir")
            return null
        }
        val dir = File(base, name)
        if (!dir.mkdirs()) {
            Log.e("ProjectManager", "Failed to create project directory: ${dir.absolutePath}")
            return null
        }
        val packageName = "com.lioisme.lide.$name"
        try {
            File(dir, "main.lua").writeText("-- $name\nprint(\"hello from $name!\")\n")
            File(dir, "init.lua").writeText("""
appname = "$name"
appver = "1.0"
packagename = "$packageName"
icon = nil
pages = {"main"}
user_permission = {}
""".trimIndent())
        } catch (e: Exception) {
            Log.e("ProjectManager", "Failed to write project files: ${e.message}")
            dir.deleteRecursively()
            return null
        }
        Log.d("ProjectManager", "Created project at ${dir.absolutePath}")
        return Project(
            name = name,
            path = dir.absolutePath,
            files = listOf("main.lua", "init.lua"),
            packageName = packageName,
        )
    }

    fun deleteProject(project: Project): Boolean {
        return try {
            File(project.path).deleteRecursively()
        } catch (_: Exception) {
            false
        }
    }
}