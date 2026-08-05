package com.lioisme.lide.androlua

import android.content.Context
import android.net.Uri
import android.util.Log
import com.androlua.LuaApplication
import java.io.File

class AlpManager(private val context: Context) {

    /** .alp 容器类型：与 pro 的 `mode` 字段对齐（"plugin" → 插件，其余 → 工程）。 */
    enum class AlpKind { PROJECT, PLUGIN }

    /** 同名冲突处理策略（WP-015）：覆盖 / 改名（dedupe -1/-2）。 */
    enum class ImportMode { OVERWRITE, RENAME }

    data class AlpImportResult(val dir: String, val kind: AlpKind)

    /** 导入计划：无副作用地解析类型/目标名/冲突态，供 UI 先弹确认再执行（WP-015）。 */
    data class ImportPlan(
        val kind: AlpKind,
        val name: String,
        val targetDir: File,
        val conflict: Boolean,
    )

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

    fun planImport(alpFile: String): ImportPlan? {
        val file = File(alpFile)
        if (!file.exists()) {
            Log.e("AlpManager", "alp file not found: ${file.name}")
            return null
        }
        val initContent = ZipUtil.readEntryText(file, "init.lua")
        val kind = AlpNaming.resolveKind(file.name, initContent)
        val name = AlpNaming.resolveName(file.name, initContent)
        val targetDir = targetDirFor(kind, name)
        return ImportPlan(kind, name, targetDir, conflict = targetDir.exists())
    }

    /**
     * 执行导入。[mode] 决定同名冲突处置：OVERWRITE 删除既有目标（默认，历史语义），
     * RENAME 经 [AlpNaming.dedupeName] 追加 -1/-2 后缀（非破坏，供自动导入路径使用）。
     */
    fun import(
        alpFile: String,
        mode: ImportMode = ImportMode.OVERWRITE,
    ): AlpImportResult? {
        val plan = planImport(alpFile) ?: return null
        val target =
            when {
                !plan.conflict -> plan.targetDir
                mode == ImportMode.RENAME -> {
                    val parent = plan.targetDir.parentFile
                    val deduped =
                        File(
                            parent,
                            AlpNaming.dedupeName(plan.name) { File(parent, it).exists() },
                        )
                    deduped
                }
                else -> plan.targetDir.also { it.deleteRecursively() }
            }
        return if (ZipUtil.unzip(File(alpFile), target)) {
            AlpImportResult(target.absolutePath, plan.kind)
        } else {
            target.deleteRecursively()
            null
        }
    }

    fun import(
        uri: Uri,
        mode: ImportMode = ImportMode.OVERWRITE,
    ): AlpImportResult? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val rawName = resolveNameFromUri(uri)
                val tempFile = File(context.cacheDir, "alp_${System.currentTimeMillis()}_$rawName")
                tempFile.parentFile?.mkdirs()
                inputStream.use { it.copyTo(tempFile.outputStream()) }
                val result = import(tempFile.absolutePath, mode)
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
     * 目标目录（pro importx:1153 同款规则）：优先 init.lua 的 appname，
     * 否则取文件名去扩展名后截断到首个 `.`/`_`。
     * 不做任何删除/覆盖（冲突判定与处置分离，WP-015）。
     */
    private fun targetDirFor(kind: AlpKind, name: String): File {
        val luaExtDir = LuaApplication.getInstance().luaExtDir
        val subDir = if (kind == AlpKind.PLUGIN) "plugin" else "project"
        return File(luaExtDir, "$subDir/$name")
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
