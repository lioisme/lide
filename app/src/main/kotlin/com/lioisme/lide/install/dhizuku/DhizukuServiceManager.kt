package com.lioisme.lide.install.dhizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import com.lioisme.lide.install.BackendStatus
import com.lioisme.lide.install.IDhizukuInstallerService
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import com.rosan.dhizuku.api.DhizukuUserServiceArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger("DhizukuServiceManager")

/**
 * Dhizuku 静默安装通道：Dhizuku API 封装（设备所有者上下文）。
 *
 * 移植自 komi-store `DhizukuServiceManager`（2026-08-09 调研），保留核心机制：
 * - **状态机**：`com.rosan.dhizuku` 包缺失 → UNAVAILABLE；`Dhizuku.init` 失败 →
 *   NOT_RUNNING；未授权 → PERMISSION_NEEDED；已授权 → READY；
 * - **授权**：[Dhizuku.requestPermission] 回调式（设备所有者代申请），回执后刷新状态；
 * - **UserService 绑定**：[getService] 在 Mutex 串行 + 15s 超时（suspendCancellableCoroutine），
 *   `bindUserService` 返回 false 立即结算 null；取消时 `unbindUserService(connection)`；
 * - 安装能力在 [DhizukuInstallerServiceImpl]（独立进程，PackageInstaller Session）。
 *
 * 线程契约：全部检测/绑定均在调用方协程（IO）执行，UI 线程不触碰 Dhizuku API；
 * [isReadyNow] 为无提示同步检测（init + 权限），供安装调度实时判定。
 */
class DhizukuServiceManager(
    private val context: Context,
) {
    private val _status = MutableStateFlow(BackendStatus.UNAVAILABLE)
    val status: StateFlow<BackendStatus> = _status.asStateFlow()

    private val bindMutex = Mutex()
    private var serviceConnection: ServiceConnection? = null

    @Volatile
    var installerService: IDhizukuInstallerService? = null
        private set

    /** 预热 Dhizuku 桥并刷新状态（LideApp.onCreate 进程级一次性调用）。 */
    fun initialize() {
        runCatching { Dhizuku.init(context) }
            .onFailure { logger.warn { "warm-up Dhizuku.init() threw: ${it.message}" } }
        refreshStatus()
    }

    /** 刷新可用性状态（异步）。 */
    fun refreshStatus() {
        _status.value = computeStatus()
    }

    /** 申请 Dhizuku 授权（设备所有者代申请；结果经监听器回执后刷新状态）。 */
    fun requestPermission() {
        try {
            if (!Dhizuku.init(context)) {
                logger.warn { "requestPermission() — Dhizuku.init() failed, cannot request" }
                refreshStatus()
                return
            }
            if (Dhizuku.isPermissionGranted()) {
                refreshStatus()
                return
            }
            Dhizuku.requestPermission(
                object : DhizukuRequestPermissionListener() {
                    @Throws(RemoteException::class)
                    override fun onRequestPermission(grantResult: Int) {
                        logger.debug {
                            "Dhizuku permission result: granted=${grantResult == PackageManager.PERMISSION_GRANTED}"
                        }
                        refreshStatus()
                    }
                },
            )
        } catch (e: Exception) {
            logger.warn { "Failed to request Dhizuku permission: ${e.message}" }
        }
    }

    /** 无提示同步检测：API≥26、包存在、init 成功且已授权（供安装调度实时判定）。 */
    fun isReadyNow(): Boolean {
        if (Build.VERSION.SDK_INT < MIN_SDK) return false
        return try {
            if (!isPackageInstalled(DHIZUKU_PACKAGE)) return false
            Dhizuku.init(context) && Dhizuku.isPermissionGranted()
        } catch (e: Exception) {
            logger.warn { "isReadyNow() — check failed: ${e.message}" }
            false
        }
    }

    /**
     * 获取（必要时绑定）Dhizuku UserService。状态非 READY 或绑定失败返回 null，
     * 由调用方回落系统安装器（不阻塞安装主链路）。
     */
    suspend fun getService(): IDhizukuInstallerService? {
        logger.debug { "getService() — current status=${_status.value}" }
        if (_status.value != BackendStatus.READY) {
            logger.warn { "getService() — Dhizuku not READY (status=${_status.value}), returning null" }
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

    private suspend fun bindService(): IDhizukuInstallerService? {
        logger.debug { "bindService() — attempting to bind Dhizuku UserService..." }
        return try {
            withTimeoutOrNull(BIND_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val componentName =
                        ComponentName(
                            context.packageName,
                            DhizukuInstallerServiceImpl::class.java.name,
                        )
                    logger.debug { "bindService() — component: $componentName" }

                    val args = DhizukuUserServiceArgs(componentName)

                    val connection =
                        object : ServiceConnection {
                            override fun onServiceConnected(
                                name: ComponentName?,
                                binder: IBinder?,
                            ) {
                                logger.debug { "onServiceConnected() — name=$name, binderAlive=${binder?.pingBinder()}" }
                                val service = IDhizukuInstallerService.Stub.asInterface(binder)
                                installerService = service
                                logger.debug { "Dhizuku installer service connected and cached" }
                                if (continuation.isActive) {
                                    continuation.resume(service)
                                }
                            }

                            override fun onServiceDisconnected(name: ComponentName?) {
                                installerService = null
                                logger.debug { "Dhizuku installer service disconnected: $name" }
                            }
                        }

                    serviceConnection = connection

                    logger.debug { "Calling Dhizuku.bindUserService()..." }
                    val bound = Dhizuku.bindUserService(args, connection)
                    logger.debug { "Dhizuku.bindUserService() returned: $bound" }
                    if (!bound && continuation.isActive) {
                        continuation.resume(null)
                    }

                    continuation.invokeOnCancellation {
                        logger.debug { "bindService() coroutine cancelled, unbinding..." }
                        try {
                            Dhizuku.unbindUserService(connection)
                        } catch (_: Exception) {
                        }
                    }
                }
            }.also { service ->
                if (service == null) {
                    logger.warn { "bindService() timed out or failed after ${BIND_TIMEOUT_MS}ms" }
                }
            }
        } catch (e: Exception) {
            logger.error { "Failed to bind Dhizuku service: ${e.javaClass.simpleName}: ${e.message}" }
            null
        }
    }

    private fun computeStatus(): BackendStatus =
        try {
            if (Build.VERSION.SDK_INT < MIN_SDK) {
                logger.debug { "computeStatus() — Dhizuku requires API $MIN_SDK+ (host=${Build.VERSION.SDK_INT})" }
                BackendStatus.UNAVAILABLE
            } else if (!isPackageInstalled(DHIZUKU_PACKAGE)) {
                logger.debug { "computeStatus() — Dhizuku package not installed" }
                BackendStatus.UNAVAILABLE
            } else {
                val initialized =
                    try {
                        Dhizuku.init(context)
                    } catch (e: Exception) {
                        logger.warn { "Dhizuku.init() threw: ${e.message}" }
                        false
                    }
                logger.debug { "computeStatus() — Dhizuku.init=$initialized" }
                if (!initialized) {
                    BackendStatus.NOT_RUNNING
                } else {
                    val granted =
                        try {
                            Dhizuku.isPermissionGranted()
                        } catch (e: Exception) {
                            logger.warn { "Dhizuku.isPermissionGranted() threw: ${e.message}" }
                            false
                        }
                    if (granted) {
                        logger.debug { "computeStatus() — READY" }
                        BackendStatus.READY
                    } else {
                        BackendStatus.PERMISSION_NEEDED
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn { "Error checking Dhizuku status: ${e.javaClass.simpleName}: ${e.message}" }
            BackendStatus.NOT_RUNNING
        }

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
        private const val DHIZUKU_PACKAGE = "com.rosan.dhizuku"
        private const val BIND_TIMEOUT_MS = 15_000L

        /** Dhizuku 最低支持版本（API 26 = Android 8.0，其文档约定；<26 时恒不可用）。 */
        private const val MIN_SDK = 26
    }
}
