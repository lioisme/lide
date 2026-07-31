package com.lioisme.delua.androlua

import android.content.Context
import android.net.Uri
import android.util.Log
import com.androlua.LuaApplication
import java.io.File

class AlpManager(private val context: Context) {

    fun export(projectDir: String, outputPath: String): Boolean {
        val srcDir = File(projectDir)
        if (!srcDir.isDirectory) {
            Log.e("AlpManager", "project directory not found: $projectDir")
            return false
        }
        val alpFile = File(outputPath)
        return ZipUtil.zip(srcDir, alpFile)
    }

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

    fun import(alpFile: String): String? {
        val file = File(alpFile)
        if (!file.exists()) {
            Log.e("AlpManager", "alp file not found: $alpFile")
            return null
        }
        val name = file.nameWithoutExtension
        val targetDir = resolveTargetDir(name)
        return if (ZipUtil.unzip(file, targetDir)) {
            targetDir.absolutePath
        } else {
            null
        }
    }

    fun import(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val name = resolveNameFromUri(uri)
                val targetDir = resolveTargetDir(name)
                if (ZipUtil.unzipFromStream(inputStream, targetDir)) {
                    targetDir.absolutePath
                } else {
                    targetDir.deleteRecursively()
                    null
                }
            } ?: run {
                Log.e("AlpManager", "cannot open input stream for $uri")
                null
            }
        } catch (e: Exception) {
            Log.e("AlpManager", "import from URI failed: ${e.message}")
            null
        }
    }

    private fun resolveTargetDir(name: String): File {
        val luaExtDir = LuaApplication.getInstance().luaExtDir
        val targetDir = File(luaExtDir, "project/$name")
        if (targetDir.exists()) {
            Log.w("AlpManager", "project '$name' already exists, overwriting")
            targetDir.deleteRecursively()
        }
        return targetDir
    }

    private fun resolveNameFromUri(uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayName = it.getString(it.getColumnIndexOrThrow("_display_name"))
                return displayName.removeSuffix(".alp").ifBlank { "imported_project" }
            }
        }
        val path = uri.path ?: return "imported_project"
        return path.substringAfterLast('/').removeSuffix(".alp").ifBlank { "imported_project" }
    }
}
