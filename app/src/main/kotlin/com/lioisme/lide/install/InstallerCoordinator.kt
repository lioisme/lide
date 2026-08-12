package com.lioisme.lide.install

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 静默安装调度器：后端解析 + 静默安装执行，失败一律回落系统安装器（调用方）。
 *
 * 决策（D-2/D-3，见 docs/in-progress/SILENT_INSTALL_WORK_ORDER.md）：
 * - [resolveBackend] 纯函数：偏好非 DEFAULT 且实时检测 READY 才启用静默，否则回落；
 * - 安装归属（`-i` / `setInstallerPackageName`）默认不设置（null）；
 * - Dhizuku 返回 `PENDING_USER_ACTION_REQUIRED`（Android 14+ 更新归属门）即视为失败，
 *   由调用方转系统安装器（用户确认），不静默强装；
 * - 全部路径 `try/catch(Throwable)` 降级返回 false（AGENTS.md 降级约定）。
 *
 * 线程契约：安装执行在 [Dispatchers.IO]；UI 线程不触碰任何 Shizuku/Dhizuku/libsu API。
 */
object InstallerCoordinator {
    private const val TAG = "InstallerCoordinator"

    /** 后端成功码（与 AIDL 服务实现对齐）。 */
    private const val STATUS_SUCCESS = 0

    /**
     * 解析本次安装生效的后端（纯函数，可单测）：
     * 偏好为 [InstallerBackend.DEFAULT] 或对应后端实时不可用 → [InstallerBackend.DEFAULT]。
     * 检测回调异常按「不可用」处理（AGENTS.md 降级约定，D-5）。
     *
     * @param preference 用户偏好（设置页持久化 / 构建对话框本次选择）。
     * @param isReady 实时无提示检测回调（对应后端的 [InstallerServices] 实时判定）。
     */
    fun resolveBackend(
        preference: InstallerBackend,
        isReady: (InstallerBackend) -> Boolean,
    ): InstallerBackend {
        if (preference == InstallerBackend.DEFAULT) return InstallerBackend.DEFAULT
        return try {
            if (isReady(preference)) preference else InstallerBackend.DEFAULT
        } catch (e: Throwable) {
            Log.w(TAG, "resolveBackend() — readiness check threw: ${e.javaClass.simpleName}: ${e.message}")
            InstallerBackend.DEFAULT
        }
    }

    /**
     * 静默安装打包产物。成功返回 true；后端不可用 / 安装失败 / 需用户介入
     * （Dhizuku `-2`）/ 异常一律返回 false，由调用方回落系统安装器。
     */
    suspend fun installSilently(
        apkFile: File,
        backend: InstallerBackend,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            when (backend) {
                InstallerBackend.DEFAULT -> false
                InstallerBackend.SHIZUKU -> shizukuInstall(apkFile)
                InstallerBackend.DHIZUKU -> dhizukuInstall(apkFile)
                InstallerBackend.ROOT -> rootInstall(apkFile)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "$backend install exception, falling back: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    private suspend fun shizukuInstall(apkFile: File): Boolean {
        if (!InstallerServices.shizuku.isReadyNow()) return false
        val service = InstallerServices.shizuku.getService() ?: return false
        return ParcelFileDescriptor.open(apkFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            val result = service.installPackage(pfd, apkFile.length(), null)
            Log.d(TAG, "shizuku install result=$result")
            result == STATUS_SUCCESS
        }
    }

    private suspend fun dhizukuInstall(apkFile: File): Boolean {
        if (!InstallerServices.dhizuku.isReadyNow()) return false
        val (expectedPkg, expectedVc) = readApkIdentity(apkFile.absolutePath)
        val service = InstallerServices.dhizuku.getService() ?: return false
        return ParcelFileDescriptor.open(apkFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            val result = service.installPackage(pfd, apkFile.length(), expectedPkg, expectedVc, null)
            Log.d(TAG, "dhizuku install result=$result")
            result == STATUS_SUCCESS
        }
    }

    private suspend fun rootInstall(apkFile: File): Boolean {
        if (!InstallerServices.root.isReadyNow()) return false
        val result = InstallerServices.root.installPackage(apkFile, null)
        Log.d(TAG, "root install result=$result")
        return result == STATUS_SUCCESS
    }

    /** 读取目标 APK 包名 + 版本（Dhizuku 超时核验用；读取失败回落 null/-1）。 */
    private fun readApkIdentity(filePath: String): Pair<String?, Long> {
        val info =
            try {
                InstallerServices.appContext.packageManager.getPackageArchiveInfo(filePath, 0)
            } catch (e: Throwable) {
                Log.w(TAG, "getPackageArchiveInfo($filePath) failed: ${e.message}")
                null
            } ?: return null to -1L
        val versionCode =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        return info.packageName to versionCode
    }
}
