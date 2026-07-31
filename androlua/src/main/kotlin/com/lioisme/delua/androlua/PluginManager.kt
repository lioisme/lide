package com.lioisme.delua.androlua

import android.util.Log
import org.json.JSONObject
import java.io.File

data class PluginInfo(
    val name: String,
    val dir: String,
    val version: String = "1.0",
    val description: String = "",
    val main: String = "main.lua",
    val author: String = "",
)

class PluginManager {

    private val plugins = mutableListOf<PluginInfo>()

    fun scan(pluginDir: String): List<PluginInfo> {
        plugins.clear()
        val dir = File(pluginDir)
        if (!dir.isDirectory) {
            dir.mkdirs()
            return emptyList()
        }
        dir.listFiles()?.filter { it.isDirectory }?.forEach { pluginDirEntry ->
            val info = loadPlugin(pluginDirEntry)
            if (info != null) {
                plugins.add(info)
            }
        }
        return plugins.toList()
    }

    fun installFromAlp(alpFile: String, pluginDir: String): PluginInfo? {
        val file = File(alpFile)
        if (!file.exists()) {
            Log.e("PluginManager", "alp file not found: $alpFile")
            return null
        }
        val name = file.nameWithoutExtension
        val targetDir = File(pluginDir, name)
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        if (!ZipUtil.unzip(file, targetDir)) {
            return null
        }
        val info = loadPlugin(targetDir)
        if (info != null) {
            plugins.add(info)
        }
        return info
    }

    private fun loadPlugin(dir: File): PluginInfo? {
        val jsonFile = File(dir, "plugin.json")
        if (jsonFile.exists()) {
            return try {
                val json = JSONObject(jsonFile.readText())
                PluginInfo(
                    name = json.optString("name", dir.name),
                    dir = dir.absolutePath,
                    version = json.optString("version", "1.0"),
                    description = json.optString("description", ""),
                    main = json.optString("main", "main.lua"),
                    author = json.optString("author", ""),
                )
            } catch (e: Exception) {
                Log.w("PluginManager", "failed to parse plugin.json in ${dir.name}: ${e.message}")
                null
            }
        }
        return loadProjectStylePlugin(dir)
    }

    /**
     * 兼容 pro 的 .alp 插件（无 plugin.json，是标准 Lua 工程）：
     * 入口为 main.lua；元信息从 init.lua 的 appname/appver/description 字段读取。
     */
    private fun loadProjectStylePlugin(dir: File): PluginInfo? {
        val initFile = File(dir, "init.lua")
        if (!initFile.exists()) return null
        val mainFile = File(dir, "main.lua")
        if (!mainFile.exists()) return null
        return try {
            val init = initFile.readText()
            PluginInfo(
                name = fieldOf(init, "appname") ?: dir.name,
                dir = dir.absolutePath,
                version = fieldOf(init, "appver") ?: "1.0",
                description = fieldOf(init, "description") ?: "",
                main = "main.lua",
                author = fieldOf(init, "author") ?: "",
            )
        } catch (e: Exception) {
            Log.w("PluginManager", "failed to read init.lua in ${dir.name}: ${e.message}")
            null
        }
    }

    private fun fieldOf(content: String, field: String): String? {
        val regex = Regex("""(?m)^\s*$field\s*=\s*"([^"]*)"\s*$""")
        val quoted = regex.find(content)?.groupValues?.get(1)
        if (quoted != null) return quoted
        val plain = Regex("""(?m)^\s*$field\s*=\s*([^\s"]+)\s*$""")
            .find(content)?.groupValues?.get(1)
        return plain?.takeIf { !it.startsWith("{") && !it.startsWith("[") && !it.startsWith("nil") }
    }
}
