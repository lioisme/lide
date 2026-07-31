package com.lioisme.delua.androlua

import android.content.Context
import android.net.Uri
import android.util.Log
import com.androlua.LuaApplication
import java.io.File

class AlpManager(private val context: Context) {

    /** .alp 容器类型：与 pro 的 `mode` 字段对齐（"plugin" → 插件，其余 → 工程）。 */
    enum class AlpKind { PROJECT, PLUGIN }

    data class AlpImportResult(val dir: String, val kind: AlpKind)

    fun export(projectDir: String, uri: Uri): Boolean {
        val srcDir = File(projectDir)
        if (!srcDir.isDirectory) {
            Log.e("AlpManager", "project directory not found: $projectDir")
            return false
        }
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                ZipUtil.zipToStream(srcDir, outputStream)
            } ?: run {
                Log.e("AlpManager", "cannot open output stream for $uri")
                false
            }
        } catch (e: Exception) {
            Log.e("AlpManager", "export to URI failed: ${e.message}")
            false
        }
    }

    fun import(alpFile: String): AlpImportResult? {
        val file = File(alpFile)
        if (!file.exists()) {
            Log.e("AlpManager", "alp file not found: $alpFile")
            return null
        }
        val initContent = ZipUtil.readEntryText(file, "init.lua")
        val kind = AlpNaming.resolveKind(file.name, initContent)
        val name = AlpNaming.resolveName(file.name, initContent)
        val targetDir = resolveTargetDir(kind, name)
        return if (ZipUtil.unzip(file, targetDir)) {
            AlpImportResult(targetDir.absolutePath, kind)
        } else {
            targetDir.deleteRecursively()
            null
        }
    }

    fun import(uri: Uri): AlpImportResult? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val rawName = resolveNameFromUri(uri)
                val tempFile = File(context.cacheDir, "alp_${System.currentTimeMillis()}_$rawName")
                tempFile.parentFile?.mkdirs()
                inputStream.use { it.copyTo(tempFile.outputStream()) }
                val result = import(tempFile.absolutePath)
                tempFile.delete()
                result
            } ?: run {
                Log.e("AlpManager", "cannot open input stream for $uri")
                null
            }
        } catch (e: Exception) {
            Log.e("AlpManager", "import from URI failed: ${e.message}")
            null
        }
    }

    /**
     * 类型判定（pro main.lua:1127-1131 同款逻辑）：
     * zip 内 init.lua 的 `mode = "plugin"`，或 .alp 文件名以 `<name>_plugin` 开头。
     */
    private fun resolveKind(alpFileName: String, initContent: String?): AlpKind {
        val modePlugin = initContent?.let {
            Regex("""(?m)^\s*mode\s*=\s*["']plugin["']\s*$""").containsMatchIn(it)
        } ?: false
        val namePlugin = Regex("""^[^._]+_plugin""").containsMatchIn(alpFileName)
        return if (modePlugin || namePlugin) AlpKind.PLUGIN else AlpKind.PROJECT
    }

    /**
     * 目录名（pro importx:1153 同款规则）：优先 init.lua 的 appname，
     * 否则取文件名去扩展名后截断到首个 `.`/`_`。同名时按 [AlpNaming.dedupeName] 追加 -1/-2。
     */
    private fun resolveTargetDir(kind: AlpKind, name: String): File {
        val luaExtDir = LuaApplication.getInstance().luaExtDir
        val subDir = if (kind == AlpKind.PLUGIN) "plugin" else "project"
        val targetDir = File(luaExtDir, "$subDir/$name")
        if (targetDir.exists()) {
            Log.w("AlpManager", "alp '${subDir}/${name}' already exists, overwriting")
            targetDir.deleteRecursively()
        }
        return targetDir
    }

    private fun resolveNameFromUri(uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayName = it.getString(it.getColumnIndexOrThrow("_display_name"))
                return displayName.removeSuffix(".alp").ifBlank { "imported" }
            }
        }
        val path = uri.path ?: return "imported"
        return path.substringAfterLast('/').removeSuffix(".alp").ifBlank { "imported" }
    }
}
