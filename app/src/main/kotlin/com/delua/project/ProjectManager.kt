package com.delua.project

import android.content.Context
import com.delua.model.Project
import java.io.File

class ProjectManager(private val context: Context) {

    private val projectsDir: File
        get() = File(context.filesDir, "projects").also { it.mkdirs() }

    fun listProjects(): List<Project> {
        val list = mutableListOf<Project>()
        list.addAll(loadBuiltinProjects())
        val dirs = projectsDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        for (dir in dirs) {
            val mainFile = File(dir, "main.lua")
            if (mainFile.exists()) {
                list.add(loadProject(dir))
            }
        }
        return list
    }

    private fun loadProject(dir: File): Project {
        val files = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".lua") }
            ?.map { it.name }
            ?.sorted() ?: listOf("main.lua")
        val initFile = File(dir, "init.lua")
        var packageName = ""
        var icon: String? = null
        if (initFile.exists()) {
            try {
                val content = initFile.readText()
                val packageRegex = Regex("""package\s*=\s*"([^"]*)"""")
                val iconRegex = Regex("""icon\s*=\s*"([^"]*)"""")
                packageRegex.find(content)?.let { packageName = it.groupValues[1] }
                iconRegex.find(content)?.let { icon = it.groupValues[1] }
            } catch (_: Exception) { }
        }
        return Project(
            name = dir.name,
            path = dir.absolutePath,
            files = files,
            packageName = packageName,
            icon = icon
        )
    }

    private fun loadBuiltinProjects(): List<Project> {
        val list = mutableListOf<Project>()
        try {
            val assetManager = context.assets
            val samples = assetManager.list("samples") ?: return list
            for (sample in samples) {
                val path = context.getDir("builtin_$sample", Context.MODE_PRIVATE).absolutePath
                val dir = File(path)
                if (!dir.exists()) {
                    copyAssetDir("samples/$sample", dir)
                }
                list.add(loadProject(dir))
            }
        } catch (_: Exception) {
        }
        return list
    }

    private fun copyAssetDir(assetPath: String, destDir: File) {
        val assetManager = context.assets
        val items = assetManager.list(assetPath) ?: return
        for (item in items) {
            val childAssetPath = "$assetPath/$item"
            val childFile = File(destDir, item)
            try {
                val subs = assetManager.list(childAssetPath)
                if (subs != null && subs.isNotEmpty()) {
                    childFile.mkdirs()
                    copyAssetDir(childAssetPath, childFile)
                } else {
                    childFile.parentFile?.mkdirs()
                    val input = assetManager.open(childAssetPath)
                    childFile.outputStream().use { out -> input.copyTo(out) }
                    input.close()
                }
            } catch (_: Exception) {
                childFile.parentFile?.mkdirs()
                try {
                    val input = assetManager.open(childAssetPath)
                    childFile.outputStream().use { out -> input.copyTo(out) }
                    input.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun listProjectFiles(project: Project): List<String> {
        val dir = File(project.path)
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".lua") }
            ?.map { it.name }
            ?.sorted() ?: listOf(project.mainFile)
    }

    fun getProjectScript(project: Project, fileName: String = project.mainFile): String {
        val file = File(project.path, fileName)
        return if (file.exists()) file.readText() else "-- empty"
    }

    fun saveProjectScript(project: Project, fileName: String, content: String) {
        val file = File(project.path, fileName)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    fun createProject(name: String): Project {
        val dir = File(projectsDir, name)
        dir.mkdirs()
        val packageName = "com.delua.$name"
        File(dir, "main.lua").writeText("-- $name\nprint(\"hello from $name!\")\n")
        File(dir, "init.lua").writeText("""
appname = "$name"
appver = "1.0"
package = "$packageName"
icon = nil
pages = {"main"}
user_permission = {}
""".trimIndent())
        return Project(
            name = name,
            path = dir.absolutePath,
            files = listOf("main.lua", "init.lua"),
            packageName = packageName
        )
    }
}
