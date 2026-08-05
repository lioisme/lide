package com.lioisme.lide.androlua

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
                name = LuaConfigParser.stringField(init, "appname") ?: dir.name,
                dir = dir.absolutePath,
                version = LuaConfigParser.stringField(init, "appver") ?: "1.0",
                description = LuaConfigParser.stringField(init, "description") ?: "",
                main = "main.lua",
                author = LuaConfigParser.stringField(init, "author") ?: "",
            )
        } catch (e: Exception) {
            Log.w("PluginManager", "failed to read init.lua in ${dir.name}: ${e.message}")
            null
        }
    }
}
