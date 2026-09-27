package com.lioisme.lide.install

import android.content.Context
import com.lioisme.lide.install.dhizuku.DhizukuServiceManager
import com.lioisme.lide.install.root.RootServiceManager
import com.lioisme.lide.install.shizuku.ShizukuServiceManager
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger("InstallerServices")

/**
 * 静默安装运行时（进程级单例，`LideApp.onCreate` 幂等初始化）。
 *
 * 持有三个后端的 [ServiceManager][com.lioisme.lide.install] 单例与共享后台 scope；
 * UI 经各 `status: StateFlow<BackendStatus>` 展示，安装调度经 [InstallerCoordinator] 执行。
 *
 * 初始化顺序契约：本对象先于任何 UI 组合访问（[LideApp.onCreate] 调用 [init]），
 * 未初始化即访问抛出 [IllegalStateException]（尽早暴露接线错误）。
 */
object InstallerServices {
    /** 后端共享后台 scope（状态刷新 / 安装执行；独立于 Activity 生命周期）。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val initialized = AtomicBoolean(false)

    @Suppress("StaticFieldLeak")
    private var ctx: Context? = null

    @Suppress("StaticFieldLeak")
    private var rootManager: RootServiceManager? = null

    @Suppress("StaticFieldLeak")
    private var shizukuManager: ShizukuServiceManager? = null

    @Suppress("StaticFieldLeak")
    private var dhizukuManager: DhizukuServiceManager? = null

    /** 幂等初始化：仅首次调用注册监听并刷新状态，重复调用不重置（AGENTS.md 初始化幂等约定）。 */
    fun init(context: Context) {
        if (initialized.get()) return
        synchronized(this) {
            if (initialized.get()) return
            ctx = context.applicationContext
            rootManager = RootServiceManager(ctx!!, scope)
            shizukuManager = ShizukuServiceManager(ctx!!)
            dhizukuManager = DhizukuServiceManager(ctx!!)
            rootManager!!.initialize()
            shizukuManager!!.initialize()
            dhizukuManager!!.initialize()
            initialized.set(true)
            logger.debug { "init() — three backends initialized" }
        }
    }

    /** 宿主 ApplicationContext（协调器读取 APK 元数据等轻量用途）。 */
    val appContext: Context
        get() = ctx ?: error("InstallerServices.init() must be called before access")

    val root: RootServiceManager
        get() = rootManager ?: error("InstallerServices.init() must be called before access")

    val shizuku: ShizukuServiceManager
        get() = shizukuManager ?: error("InstallerServices.init() must be called before access")

    val dhizuku: DhizukuServiceManager
        get() = dhizukuManager ?: error("InstallerServices.init() must be called before access")
}
