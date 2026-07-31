package com.lioisme.delua.project

import android.content.Context
import android.util.Log
import com.androlua.LuaApplication
import com.lioisme.delua.editor.FileNode
import com.lioisme.delua.model.Project
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
                val appnameRegex = Regex("""appname\s*=\s*"([^"]*)"""")
                val packageRegex = Regex("""(?:package|packagename)\s*=\s*"([^"]*)"""")
                val iconRegex = Regex("""icon\s*=\s*"([^"]*)"""")
                appnameRegex.find(content)?.let { appName = it.groupValues[1] }
                packageRegex.find(content)?.let { packageName = it.groupValues[1] }
                iconRegex.find(content)?.let { icon = it.groupValues[1] }
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
     * 递归扫描工程目录为文件树（目录在前、按名排序；仅收录 .lua 文件与
     * 含 .lua 后代的目录）。节点路径为绝对路径，供侧边栏工程树渲染。
     */
    fun listProjectTree(project: Project): FileNode {
        val root = File(project.path)
        return FileNode(
            name = root.name,
            path = root.absolutePath,
            isDirectory = true,
            children = scanTreeDir(root),
        )
    }

    private fun scanTreeDir(dir: File): List<FileNode> {
        val entries = dir.listFiles() ?: return emptyList()
        val dirs = mutableListOf<FileNode>()
        val files = mutableListOf<FileNode>()
        for (entry in entries) {
            if (entry.isDirectory) {
                val children = scanTreeDir(entry)
                if (children.isNotEmpty()) {
                    dirs += FileNode(entry.name, entry.absolutePath, true, children)
                }
            } else if (entry.isFile && entry.name.endsWith(".lua", ignoreCase = true)) {
                files += FileNode(entry.name, entry.absolutePath, false)
            }
        }
        dirs.sortBy { it.name }
        files.sortBy { it.name }
        return dirs + files
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
        val packageName = "com.lioisme.delua.$name"
        try {
            File(dir, "main.lua").writeText("-- $name\nprint(\"hello from $name!\")\n")
            File(dir, "init.lua").writeText("""
appname = "$name"
appver = "1.0"
packagename = "$packageName"
icon = nil
pages = {"main"}
user_permission = {}
dependencies = {
    libs = {},
    modules = {}
}
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