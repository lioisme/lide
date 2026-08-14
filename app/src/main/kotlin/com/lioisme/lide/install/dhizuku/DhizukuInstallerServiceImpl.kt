package com.lioisme.lide.install.dhizuku

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.ParcelFileDescriptor
import com.lioisme.lide.install.IDhizukuInstallerService
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger("DhizukuService")

/**
 * Dhizuku UserService 安装器实现，运行于 Dhizuku 按需启动的独立进程
 * （`Dhizuku.bindUserService` 以类名反射实例化，见 [DhizukuServiceManager]）。
 *
 * 安装方式：设备所有者上下文内直接驱动系统 [PackageInstaller] Session——
 * API 31+ `setRequireUserAction(NOT_REQUIRED)` 免确认，`setInstallerPackageName`
 * 可指定安装归属；安装结果经唯一 action 的广播回执同步等待。
 *
 * 移植自 komi-store `DhizukuInstallerServiceImpl`（2026-08-09 调研），保留：
 * - **Android 14+ 更新归属门**：目标包原安装者为其它来源时系统返回
 *   `STATUS_PENDING_USER_ACTION`，本服务映射为 [STATUS_PENDING_USER_ACTION_REQUIRED]
 *   （-2），由上层回落系统安装器（D-3，不静默强装）；
 * - 120s 等待超时后按「包名 + 版本号」主动核验安装结果兜底；
 * - 未 commit 的 Session 一律 abandon，广播接收器一律反注册（防泄漏）；
 * - API 33+ 动态接收器按 `RECEIVER_NOT_EXPORTED` 注册。
 *
 * 注意：本类经 R8 反射实例化，整包 keep（proguard-rules.pro「静默安装 keep」段）。
 */
class DhizukuInstallerServiceImpl : IDhizukuInstallerService.Stub() {
    companion object {

        private const val STATUS_SUCCESS = 0
        private const val STATUS_FAILURE = -1

        /** 系统要求用户介入（Android 12+/14+ 更新归属门）：上层应回落系统安装器。 */
        const val STATUS_PENDING_USER_ACTION_REQUIRED = -2

        private const val INSTALL_TIMEOUT_SECONDS = 120L
        private const val UNINSTALL_TIMEOUT_SECONDS = 30L

        private const val ACTION_INSTALL_RESULT = "com.lioisme.lide.dhizuku.INSTALL_RESULT"
        private const val ACTION_UNINSTALL_RESULT = "com.lioisme.lide.dhizuku.UNINSTALL_RESULT"

        /** 安装结果广播的 PendingIntent 旗标（Android 12+ 须显式 FLAG_MUTABLE）。 */
        private val PENDING_INTENT_FLAGS =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

        private fun log(msg: String) = logger.debug { msg }

        private fun logW(msg: String) = logger.warn { msg }

        private fun logE(
            msg: String,
            e: Throwable? = null,
        ) = logger.error(e) { msg }
    }

    override fun installPackage(
        pfd: ParcelFileDescriptor,
        fileSize: Long,
        expectedPackageName: String?,
        expectedVersionCode: Long,
        installerPackageName: String?,
    ): Int {
        log(
            "installPackage() called — fileSize=$fileSize, expected=$expectedPackageName@" +
                "$expectedVersionCode, installer=$installerPackageName",
        )

        val ctx: Context =
            currentApplicationOrNull() ?: run {
                logE("currentApplication() returned null — no context available")
                return STATUS_FAILURE
            }

        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        if (fileSize > 0) params.setSize(fileSize)
        // B4（2026-08-12）：setInstallerPackageName 为 API 34（UPSIDE_DOWN_CAKE）
        // 引入——先前守卫为 SDK_INT >= S（31），API 31-33 上抛 NoSuchMethodError
        // （Error 类型，catch(Exception) 抓不住）→ 静默崩溃。低版本记录日志忽略归属。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            installerPackageName?.takeIf { it.isNotBlank() }?.let { name ->
                try {
                    params.setInstallerPackageName(name)
                } catch (t: Throwable) {
                    logW("setInstallerPackageName($name) failed: ${t.message}")
                }
            }
        } else {
            log("setInstallerPackageName skipped — requires API 34, device is ${Build.VERSION.SDK_INT}")
        }

        var sessionId = -1
        var session: PackageInstaller.Session? = null
        var receiver: BroadcastReceiver? = null
        var committed = false
        return try {
            sessionId = installer.createSession(params)
            log("createSession() — sessionId=$sessionId")
            session = installer.openSession(sessionId)

            session.openWrite("apk", 0, fileSize).use { out ->
                ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                    val copied = input.copyTo(out)
                    out.flush()
                    session.fsync(out)
                    log("APK piped to session: $copied bytes (expected: $fileSize)")
                }
            }

            val latch = CountDownLatch(1)
            val resultRef = AtomicInteger(STATUS_FAILURE)
            val token = UUID.randomUUID().toString()
            val action = "$ACTION_INSTALL_RESULT.$token"

            receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        val status =
                            intent.getIntExtra(
                                PackageInstaller.EXTRA_STATUS,
                                PackageInstaller.STATUS_FAILURE,
                            )
                        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        log("install result — status=$status, message='$message'")
                        when (status) {
                            PackageInstaller.STATUS_SUCCESS -> {
                                resultRef.set(STATUS_SUCCESS)
                            }

                            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                                logW(
                                    "install requires user action (Android 14+ update-ownership gate) — " +
                                        "installerAttribution=$installerPackageName",
                                )
                                resultRef.set(STATUS_PENDING_USER_ACTION_REQUIRED)
                            }

                            else -> {
                                logW("install failed status=$status: $message")
                                resultRef.set(STATUS_FAILURE)
                            }
                        }
                        latch.countDown()
                    }
                }
            registerInternalReceiver(ctx, receiver, action)

            val pendingIntent =
                PendingIntent.getBroadcast(
                    ctx,
                    token.hashCode(),
                    Intent(action).setPackage(ctx.packageName),
                    PENDING_INTENT_FLAGS,
                )

            session.commit(pendingIntent.intentSender)
            committed = true
            log("session.commit() called, awaiting result...")

            val finished = latch.await(INSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                logE("install timed out after ${INSTALL_TIMEOUT_SECONDS}s — verifying package state")
                if (verifyInstallSucceeded(ctx, installer, sessionId, expectedPackageName, expectedVersionCode)) {
                    log("post-timeout verification confirmed install succeeded")
                    STATUS_SUCCESS
                } else {
                    logE("post-timeout verification could not confirm install")
                    STATUS_FAILURE
                }
            } else {
                resultRef.get()
            }
        } catch (e: Exception) {
            logE("installPackage() exception", e)
            STATUS_FAILURE
        } finally {
            if (!committed && sessionId >= 0) {
                try {
                    installer.abandonSession(sessionId)
                } catch (_: Exception) {
                }
            }
            try {
                session?.close()
            } catch (_: Exception) {
            }
            if (receiver != null) {
                try {
                    ctx.unregisterReceiver(receiver)
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun uninstallPackage(packageName: String): Int {
        log("uninstallPackage() called for: $packageName")

        val ctx: Context =
            currentApplicationOrNull() ?: run {
                logE("currentApplication() returned null — no context available")
                return STATUS_FAILURE
            }

        val installer = ctx.packageManager.packageInstaller
        val latch = CountDownLatch(1)
        val resultRef = AtomicInteger(STATUS_FAILURE)
        val token = UUID.randomUUID().toString()
        val action = "$ACTION_UNINSTALL_RESULT.$token"
        var receiver: BroadcastReceiver? = null

        return try {
            receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        val status =
                            intent.getIntExtra(
                                PackageInstaller.EXTRA_STATUS,
                                PackageInstaller.STATUS_FAILURE,
                            )
                        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        log("uninstall result — status=$status, message='$message'")
                        resultRef.set(if (status == PackageInstaller.STATUS_SUCCESS) STATUS_SUCCESS else STATUS_FAILURE)
                        latch.countDown()
                    }
                }
            registerInternalReceiver(ctx, receiver, action)

            val pendingIntent =
                PendingIntent.getBroadcast(
                    ctx,
                    token.hashCode(),
                    Intent(action).setPackage(ctx.packageName),
                    PENDING_INTENT_FLAGS,
                )

            installer.uninstall(packageName, pendingIntent.intentSender)

            val finished = latch.await(UNINSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                logE("uninstall timed out after ${UNINSTALL_TIMEOUT_SECONDS}s — verifying package state")
                if (!isPackageInstalled(ctx, packageName)) {
                    log("post-timeout verification confirmed uninstall succeeded")
                    STATUS_SUCCESS
                } else {
                    logE("post-timeout verification: package still installed")
                    STATUS_FAILURE
                }
            } else {
                resultRef.get()
            }
        } catch (e: Exception) {
            logE("uninstallPackage() exception", e)
            STATUS_FAILURE
        } finally {
            if (receiver != null) {
                try {
                    ctx.unregisterReceiver(receiver)
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun destroy() {
        log("destroy() — service being unbound")
    }

    /** 超时后的安装核验：目标包存在且版本 >= 期望版本即视为成功（broadcast 可能丢失）。 */
    private fun verifyInstallSucceeded(
        ctx: Context,
        installer: PackageInstaller,
        sessionId: Int,
        expectedPackageName: String?,
        expectedVersionCode: Long,
    ): Boolean {
        val targetPackage =
            expectedPackageName?.takeIf { it.isNotBlank() }
                ?: runCatching { installer.getSessionInfo(sessionId)?.appPackageName }
                    .onFailure { logE("getSessionInfo() failed during verification", it) }
                    .getOrNull()
                ?: return false
        val info = packageInfoOrNull(ctx, targetPackage) ?: return false
        if (expectedVersionCode <= 0L) return true
        val installedVersion =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        val matches = installedVersion >= expectedVersionCode
        if (!matches) {
            logW("installed $targetPackage@$installedVersion does not match expected@$expectedVersionCode")
        }
        return matches
    }

    private fun isPackageInstalled(
        ctx: Context,
        packageName: String,
    ): Boolean = packageInfoOrNull(ctx, packageName) != null

    private fun packageInfoOrNull(
        ctx: Context,
        packageName: String,
    ): android.content.pm.PackageInfo? =
        try {
            ctx.packageManager.getPackageInfo(packageName, 0)
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            null
        } catch (e: Exception) {
            logE("getPackageInfo($packageName) failed", e)
            null
        }

    /**
     * UserService 进程内无 Application 入口，经系统 API 反射取当前 Application
     * 作为 Context（ActivityThread.currentApplication → AppGlobals.getInitialApplication）。
     */
    private fun currentApplicationOrNull(): Context? =
        try {
            val cls = Class.forName("android.app.ActivityThread")
            val app = cls.getMethod("currentApplication").invoke(null)
            app as? Context
        } catch (e: Throwable) {
            try {
                val cls = Class.forName("android.app.AppGlobals")
                val app = cls.getMethod("getInitialApplication").invoke(null)
                app as? Context
            } catch (_: Throwable) {
                logE("Failed to obtain Application context", e)
                null
            }
        }

    private fun registerInternalReceiver(
        ctx: Context,
        receiver: BroadcastReceiver,
        action: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            ctx.registerReceiver(receiver, IntentFilter(action))
        }
    }
}
