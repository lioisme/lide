package com.lioisme.delua.androlua.common

import com.lioisme.delua.androlua.common.model.ModuleInfo
import org.json.JSONObject
import java.io.File

class ModuleRegistry(private val commonModulesDir: File) {

    fun scan(): List<ModuleInfo> {
        if (!commonModulesDir.isDirectory) return emptyList()
        return commonModulesDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { readMetadata(it) }
            ?: emptyList()
    }

    fun readMetadata(moduleDir: File): ModuleInfo? {
        val metaFile = File(moduleDir, "metadata.json")
        if (!metaFile.exists()) return null
        return try {
            val json = JSONObject(metaFile.readText())
            val filesArr = json.optJSONArray("files")
            val files = mutableListOf<String>()
            if (filesArr != null) {
                for (i in 0 until filesArr.length()) {
                    files.add(filesArr.getString(i))
                }
            }
            if (files.isEmpty()) {
                // 兼容旧版元数据缺 files 的场景：从磁盘重列（排除元数据自身）。
                moduleDir.walkTopDown()
                    .filter { it.isFile && it.name.endsWith(".lua", ignoreCase = true) }
                    .sortedBy { it.absolutePath }
                    .forEach { files.add(it.relativeTo(moduleDir).path.replace('\\', '/')) }
            }
            ModuleInfo(
                name = json.optString("name", moduleDir.name),
                version = json.optString("version", ""),
                description = json.optString("description", ""),
                author = json.optString("author", ""),
                entryPoint = json.optString("entryPoint", "init.lua"),
                files = files,
                installTime = json.optLong("installTime", 0)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun writeMetadata(moduleDir: File, info: ModuleInfo) {
        moduleDir.mkdirs()
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("name", info.name)
            put("version", info.version)
            put("description", info.description)
            put("author", info.author)
            put("entryPoint", info.entryPoint)
            put("files", info.files)
            put("installTime", info.installTime)
        }
        File(moduleDir, "metadata.json").writeText(json.toString(2))
    }

    fun remove(name: String): Boolean {
        val dir = File(commonModulesDir, name)
        return if (dir.isDirectory) dir.deleteRecursively() else false
    }
}
