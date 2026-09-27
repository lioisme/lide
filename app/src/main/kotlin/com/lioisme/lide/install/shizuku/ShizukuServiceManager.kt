package com.lioisme.lide.install.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.lioisme.lide.install.BackendStatus
import com.lioisme.lide.install.IShizukuInstallerService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

private val logger = KotlinLogging.logger("ShizukuServiceManager")

/**
 * Shizuku 静默安装通道：Shizuku API 封装（含 SUI 兼容检测）。
 *
 * 移植自 komi-store `ShizukuServiceManager`（2026-08-09 调研），保留核心机制：
 * - **生命周期监听**：binder 到达/死亡/授权结果三监听器注册后驱动 [status] 刷新，
 *   授权回执后自动重算（`initialize()` 幂等，进程级一次性）；
 * - **状态机**：binder 活 + 已授权 → READY；binder 活未授权 → PERMISSION_NEEDED；
 *   binder 死 → 按 `moe.shizuku.privileged.api` / `com.sui` 包存在性区分
 *   UNAVAILABLE（未安装）与 NOT_RUNNING（未启动）；
 * - **UserService 绑定**：[getService] 在 Mutex 串行 + 15s 超时（suspendCancellableCoroutine），
 *   缓存实例按 binder ping 失效自动重绑；取消时 `unbindUserService(..., force=true)`；
 * - 安装能力在 [ShizukuInstallerServiceImpl]（独立进程，`pm install` stdin 管道）。
 *
 * 线程契约：全部检测/绑定均在调用方协程（IO）执行，UI 线程不触碰 Shizuku API；
 * [isReadyNow] 为无提示同步检测（binder ping + 权限），供安装调度实时判定。
 */
class ShizukuServiceManager(
    private val context: Context,
) {
    private val _status = MutableStateFlow(BackendStatus.UNAVAILABLE)
    val status: StateFlow<BackendStatus> = _status.asStateFlow()

    private val bindMutex = Mutex()
    private var serviceConnection: ServiceConnection? = null
    private var boundUserServiceArgs: Shizuku.UserServiceArgs? = null

    @Volatile
    var installerService: IShizukuInstallerService? = null
        private set

    private val binderReceivedListener =
        Shizuku.OnBinderReceivedListener {
            logger.debug { "Shizuku binder received" }
            refreshStatus()
        }

    private val binderDeadListener =
        Shizuku.OnBinderDeadListener {
            logger.debug { "Shizuku binder dead" }
            installerService = null
            refreshStatus()
        }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            logger.debug {
                "Shizuku permission result: requestCode=$requestCode, " +
                    "granted=${grantResult == PackageManager.PERMISSION_GRANTED}"
            }
            refreshStatus()
        }

    /** 注册生命周期监听并刷新状态（LideApp.onCreate 进程级一次性调用）。 */
    fun initialize() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (e: Exception) {
            logger.warn { "Failed to register Shizuku listeners: ${e.message}" }
        }
        refreshStatus()
    }

    /** 刷新可用性状态（异步；binder 监听回调 / 授权回执自动驱动）。 */
    fun refreshStatus() {
        _status.value = computeStatus()
    }

    /** 申请 Shizuku 授权（弹 Shizuku 授权对话框；结果经 [permissionResultListener] 回写）。 */
    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            }
        } catch (e: Exception) {
            logger.warn { "Failed to request Shizuku permission: ${e.message}" }
        }
    }

    /** 无提示同步检测：binder 活且已授权（供安装调度实时判定）。 */
    fun isReadyNow(): Boolean =
        try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            logger.warn { "isReadyNow() — check failed: ${e.message}" }
            false
        }

    /**
     * 获取（必要时绑定）Shizuku UserService。状态非 READY 或绑定超时返回 null，
     * 由调用方回落系统安装器（不阻塞安装主链路）。
     */
    suspend fun getService(): IShizukuInstallerService? {
        logger.debug { "getService() — current status=${_status.value}" }
        if (_status.value != BackendStatus.READY) {
            logger.warn { "getService() — Shizuku not READY (status=${_status.value}), returning null" }
            return null
        }

        return bindMutex.withLock {
            installerService?.let { service ->
                try {
                    val alive = service.asBinder().pingBinder()
                    logger.debug { "getService() — cached service ping=$alive" }
                    if (alive) return@withLock service
                    logger.warn { "getService() — cached service binder dead, rebinding..." }
                    installerService = null
                } catch (e: Exception) {
                    logger.warn { "getService() — cached service error: ${e.message}, rebinding..." }
                    installerService = null
                }
            } ?: run {
                logger.debug { "getService() — no cached service, binding..." }
            }

            bindService()
        }
    }

    private suspend fun bindService(): IShizukuInstallerService? {
        logger.debug { "bindService() — attempting to bind Shizuku UserService..." }
        return try {
            withTimeoutOrNull(BIND_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val componentName =
                        ComponentName(
                            context.packageName,
                            ShizukuInstallerServiceImpl::class.java.name,
                        )
                    logger.debug { "bindService() — component: $componentName" }

                    val args =
                        Shizuku
                            .UserServiceArgs(componentName)
                            .daemon(false)
                            .processNameSuffix("installer")
                            .version(1)

                    val connection =
                        object : ServiceConnection {
                            override fun onServiceConnected(
                                name: ComponentName?,
                                binder: IBinder?,
                            ) {
                                logger.debug { "onServiceConnected() — name=$name, binderAlive=${binder?.pingBinder()}" }
                                val service = IShizukuInstallerService.Stub.asInterface(binder)
                                installerService = service
                                logger.debug { "Shizuku installer service connected and cached" }
                                if (continuation.isActive) {
                                    continuation.resume(service)
                                }
                            }

                            override fun onServiceDisconnected(name: ComponentName?) {
                                installerService = null
                                logger.debug { "Shizuku installer service disconnected: $name" }
                            }
                        }

                    serviceConnection = connection
                    boundUserServiceArgs = args

                    logger.debug { "Calling Shizuku.bindUserService()..." }
                    Shizuku.bindUserService(args, connection)
                    logger.debug { "Shizuku.bindUserService() called, waiting for callback..." }

                    continuation.invokeOnCancellation {
                        logger.debug { "bindService() coroutine cancelled, unbinding..." }
                        try {
                            Shizuku.unbindUserService(args, connection, true)
                        } catch (_: Exception) {
                        }
                    }
                }
            }.also { service ->
                if (service == null) {
                    logger.warn { "bindService() timed out after ${BIND_TIMEOUT_MS}ms" }
                }
            }
        } catch (e: Exception) {
            logger.error { "Failed to bind Shizuku service: ${e.javaClass.simpleName}: ${e.message}" }
            null
        }
    }

    private fun computeStatus(): BackendStatus =
        try {
            val binderAlive = Shizuku.pingBinder()
            logger.debug { "computeStatus() — pingBinder=$binderAlive" }
            if (binderAlive) {
                val permResult = Shizuku.checkSelfPermission()
                logger.debug { "computeStatus() — checkSelfPermission=$permResult" }
                if (permResult != PackageManager.PERMISSION_GRANTED) {
                    BackendStatus.PERMISSION_NEEDED
                } else {
                    logger.debug { "computeStatus() — READY" }
                    BackendStatus.READY
                }
            } else {
                val installed = isShizukuOrSuiInstalled()
                logger.debug { "computeStatus() — binder dead, shizukuOrSuiInstalled=$installed" }
                if (installed) BackendStatus.NOT_RUNNING else BackendStatus.UNAVAILABLE
            }
        } catch (e: Exception) {
            logger.warn { "Error checking Shizuku status: ${e.javaClass.simpleName}: ${e.message}" }
            BackendStatus.NOT_RUNNING
        }

    private fun isShizukuOrSuiInstalled(): Boolean = isPackageInstalled(SHIZUKU_PACKAGE) || isPackageInstalled(SUI_PACKAGE)

    private fun isPackageInstalled(packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }

    companion object {
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val SUI_PACKAGE = "com.sui"
        private const val BIND_TIMEOUT_MS = 15_000L
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
    }
}
