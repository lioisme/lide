package com.lioisme.lide.import

import android.content.Context
import android.net.Uri
import com.lioisme.lide.project.AlpManager
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger("AlpImportCoordinator")

/**
 * .alp 导入协调器（架构审计 B-1）：两屏（工程列表 / 示例）共用的
 * 「临时文件 + 冲突确认状态机 + 执行导入」单一入口。
 *
 * 收敛内容：
 * - 内容 URI → 缓存临时 .alp 文件（`alp_${ts}_$raw` 命名，用完即删）；
 * - 无副作用导入计划（planImport）与冲突挂起状态（[Pending]，取代
 *   此前 `ConflictPending` / `PendingImport` 双份同构状态类）；
 * - 按模式执行导入与临时文件清理。
 *
 * 线程：全部方法供宿主在 IO 协程内调用；状态类为纯值，挂起状态由宿主
 * Compose state 持有（本类不持有任何 Android/Compose 依赖，JVM 可单测）。
 */
class AlpImportCoordinator(
    private val context: Context,
) {
    /** 待确认导入：临时 .alp 文件路径 + 已解析的导入计划（冲突态）。 */
    data class Pending(
        val tempPath: String,
        val plan: AlpManager.ImportPlan,
        // 源文件是否为导入临时副本（是则导入/放弃后删除；工程项目文件源不可删）。
        val isTemp: Boolean = true,
    )

    /**
     * 将内容 [uri] 复制到缓存临时文件并返回绝对路径；失败返回 null。
     * [prefix] 用于区分来源（alp / example），仅影响临时文件名。
     */
    fun copyUriToTemp(
        uri: Uri,
        prefix: String = "alp",
    ): String? =
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val rawName = File(uri.lastPathSegment ?: "import.alp").name
                val f = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}_$rawName")
                f.parentFile?.mkdirs()
                input.copyTo(f.outputStream())
                f.absolutePath
            }
        } catch (t: Throwable) {
            logger.error(t) { "copyUriToTemp failed: uri=$uri" }
            null
        }

    /** 无副作用解析导入计划；文件无效/不存在返回 null（临时文件由调用方清理）。 */
    fun plan(tempPath: String): AlpManager.ImportPlan? = AlpManager(context).planImport(tempPath)

    /** 按模式执行导入；[deleteAfter] 为真时清理源文件（临时副本）并返回结果（失败返回 null）。 */
    fun import(
        tempPath: String,
        mode: AlpManager.ImportMode,
        deleteAfter: Boolean = true,
    ): AlpManager.AlpImportResult? =
        try {
            val result = AlpManager(context).import(tempPath, mode)
            if (deleteAfter) File(tempPath).delete()
            result
        } catch (t: Throwable) {
            logger.error(t) { "import failed: temp=$tempPath mode=$mode" }
            if (deleteAfter) File(tempPath).delete()
            null
        }

    /** 放弃挂起导入：清理临时文件。 */
    fun discard(tempPath: String) {
        File(tempPath).delete()
    }
}
