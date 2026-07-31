package com.lioisme.delua.androlua.common

import com.lioisme.delua.androlua.common.model.LibraryInfo
import org.json.JSONObject
import java.io.File

class LibraryRegistry(private val commonLibsDir: File) {

    fun scan(): List<LibraryInfo> {
        if (!commonLibsDir.isDirectory) return emptyList()
        return commonLibsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { readMetadata(it) }
            ?: emptyList()
    }

    fun readMetadata(libDir: File): LibraryInfo? {
        val metaFile = File(libDir, "metadata.json")
        if (!metaFile.exists()) return null
        return try {
            val json = JSONObject(metaFile.readText())
            val jarFile = libDir.listFiles()?.firstOrNull { f ->
                f.name.endsWith(".jar") || f.name.endsWith(".dex")
            }
            LibraryInfo(
                name = json.optString("name", libDir.name),
                version = json.optString("version", ""),
                description = json.optString("description", ""),
                author = json.optString("author", ""),
                filePath = jarFile?.absolutePath ?: "",
                fileSize = jarFile?.length() ?: 0,
                installTime = json.optLong("installTime", 0)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun writeMetadata(libDir: File, info: LibraryInfo) {
        libDir.mkdirs()
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("name", info.name)
            put("version", info.version)
            put("description", info.description)
            put("author", info.author)
            put("installTime", info.installTime)
        }
        File(libDir, "metadata.json").writeText(json.toString(2))
    }

    fun remove(name: String): Boolean {
        val dir = File(commonLibsDir, name)
        return if (dir.isDirectory) dir.deleteRecursively() else false
    }

    fun findJarFile(name: String): File? {
        val dir = File(commonLibsDir, name)
        return if (dir.isDirectory) {
            dir.listFiles()?.firstOrNull { it.name.endsWith(".jar") || it.name.endsWith(".dex") }
        } else null
    }
}
