package com.lioisme.lide.project

import android.content.Context
import android.net.Uri
import com.lioisme.lide.engine.RuntimePaths
import java.io.File
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger("AlpManager")

class AlpManager(
    private val context: Context,
) {
    /** 同名冲突处理策略（WP-015）：覆盖 / 改名（dedupe -1/-2）。 */
    enum class ImportMode { OVERWRITE, RENAME }

    data class AlpImportResult(
        val dir: String,
    )

    /** 导入计划：无副作用地解析目标名/冲突态，供 UI 先弹确认再执行（WP-015）。 */
    data class ImportPlan(
        val name: String,
        val targetDir: File,
        val conflict: Boolean,
    )

    fun export(
        projectDir: String,
        uri: Uri,
    ): Boolean {
        val srcDir = File(projectDir)
        if (!srcDir.isDirectory) {
            logger.error { "project directory not found: $projectDir" }
            return false
        }
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                ZipUtil.zipToStream(srcDir, outputStream)
            } ?: run {
                logger.error { "cannot open output stream for $uri" }
                false
            }
        } catch (e: Exception) {
            logger.error { "export to URI failed: ${e.message}" }
            false
        }
    }

    fun planImport(alpFile: String): ImportPlan? {
        val file = File(alpFile)
        if (!file.exists()) {
            logger.error { "alp file not found: ${file.name}" }
            return null
        }
        val initContent = ZipUtil.readEntryText(file, "init.lua")
        val name = AlpNaming.resolveName(file.name, initContent)
        val targetDir = targetDirFor(name)
        return ImportPlan(name, targetDir, conflict = targetDir.exists())
    }

    /**
     * 执行导入。[mode] 决定同名冲突处置：RENAME 经 [AlpNaming.dedupeName] 追加
     * -1/-2 后缀（非破坏，默认——批量导入同名工程不再静默删除既有数据），
     * OVERWRITE 删除既有目标（仅用户显式确认路径使用，ProjectListScreen/
     * ExamplesScreen 冲突对话框）。
     */
    fun import(
        alpFile: String,
        mode: ImportMode = ImportMode.RENAME,
    ): AlpImportResult? {
        val plan = planImport(alpFile) ?: return null
        val target =
            when {
                !plan.conflict -> {
                    plan.targetDir
                }

                mode == ImportMode.RENAME -> {
                    val parent = plan.targetDir.parentFile
                    val deduped =
                        File(
                            parent,
                            AlpNaming.dedupeName(plan.name) { File(parent, it).exists() },
                        )
                    deduped
                }

                else -> {
                    plan.targetDir.also { it.deleteRecursively() }
                }
            }
        return if (ZipUtil.unzip(File(alpFile), target)) {
            AlpImportResult(target.absolutePath)
        } else {
            target.deleteRecursively()
            null
        }
    }

    /**
     * 目标目录（pro importx:1153 同款规则）：优先 init.lua 的 appname，
     * 否则取文件名去扩展名后截断到首个 `.`/`_`。
     * 不做任何删除/覆盖（冲突判定与处置分离，WP-015）。
     */
    private fun targetDirFor(name: String): File = File(RuntimePaths.projectsDir(), name)
}
