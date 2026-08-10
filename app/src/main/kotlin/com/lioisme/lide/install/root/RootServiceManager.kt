package com.lioisme.lide.install.root

import android.content.Context
import android.util.Log
import com.lioisme.lide.install.BackendStatus
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Root 静默安装通道：基于 libsu（com.github.topjohnwu.libsu）的 su shell 封装。
 *
 * 移植自 komi-store `RootServiceManager`（2026-08-09 调研），保留核心机制：
 * - **授权记忆**：`SharedPreferences("lide_root", granted_before)`——首次授权后冷启动
 *   不再重复弹 su（持久授权下 `getShell()` 静默）；真实吊销由下次安装拉 shell 时
 *   [isRootGranted] 懒惰纠正并遗忘记忆；
 * - 安装 = `cp` 至 `/data/local/tmp/`（宿主进程可读，shell 可读）+ `pm install -r`
 *   （可选 `-i <安装者>`），`finally` 清理临时文件；
 * - `-i` / 包名校验走 [PACKAGE_NAME_PATTERN]，防命令注入；
 * - 成功判定 = 退出码 0 且 stdout 含 `Success`。
 *
 * 线程契约：状态刷新 / 安装均在 IO 线程（调用方为 [Dispatchers.IO] 上下文）；
 * [isReadyNow] 为无提示同步检测（不触发 su 授权弹窗），供安装调度实时判定。
 */
class RootServiceManager(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val prefs =
        context.applicationContext
            .getSharedPreferences(ROOT_PREFS, Context.MODE_PRIVATE)

    private val configured = AtomicBoolean(false)

    private val _status = MutableStateFlow(BackendStatus.UNAVAILABLE)
    val status: StateFlow<BackendStatus> = _status.asStateFlow()

    /** 进程内一次性主 shell 配置（timeout 10s；已建 shell 后 setDefaultBuilder 抛 IllegalStateException）。 */
    private fun configureDefaultShell() {
        if (configured.get()) return
        synchronized(this) {
            if (configured.get()) return
            try {
                Shell.setDefaultBuilder(
                    Shell.Builder.create()
                        .setTimeout(SHELL_TIMEOUT_SECONDS),
                )
            } catch (e: IllegalStateException) {
                Log.d(TAG, "configureDefaultShell() — main shell already constructed, skipping")
            }
            configured.set(true)
        }
    }

    /** 注册初始化：配置 shell 并后台刷新状态（LideApp.onCreate 幂等调用）。 */
    fun initialize() {
        configureDefaultShell()
        scope.launch(Dispatchers.IO) { refreshStatusBlocking() }
    }

    /** 刷新可用性状态（异步）。 */
    fun refreshStatus() {
        configureDefaultShell()
        scope.launch(Dispatchers.IO) { refreshStatusBlocking() }
    }

    /**
     * 申请 Root 授权：强制建立主 shell（持久授权静默，未授权则弹 su 确认）。
     * 结果经 [refreshStatus] 回写状态流。
     */
    fun requestPermission() {
        configureDefaultShell()
        scope.launch(Dispatchers.IO) {
            Log.d(TAG, "requestPermission() — forcing main shell creation")
            try {
                Shell.getShell()
            } catch (e: Exception) {
                Log.w(TAG, "requestPermission() — getShell failed: ${e.javaClass.simpleName}: ${e.message}")
            }
            refreshStatusBlocking()
        }
    }

    /** 无提示同步检测：当前是否已持有 root（不弹 su，供安装调度实时判定）。 */
    fun isReadyNow(): Boolean =
        try {
            val granted = Shell.getCachedShell()?.isRoot == true
            if (granted) rememberGranted(true)
            granted
        } catch (e: Exception) {
            Log.w(TAG, "isReadyNow() — check failed: ${e.message}")
            false
        }

    /**
     * Root 静默安装。返回 [STATUS_SUCCESS]（0）/ [STATUS_FAILURE]（-1）；未持有 root 返回 null。
     * [installerPackageName] 为可选 `-i` 归属（D-3 默认不传），须过 [PACKAGE_NAME_PATTERN]。
     */
    suspend fun installPackage(
        apkFile: File,
        installerPackageName: String?,
    ): Int? = withContext(Dispatchers.IO) {
        configureDefaultShell()
        if (!isRootGranted()) {
            Log.w(TAG, "installPackage() — root not granted, aborting")
            return@withContext null
        }
        val safeInstaller = installerPackageName?.takeIf { it.isNotBlank() }
        if (safeInstaller != null && !PACKAGE_NAME_PATTERN.matches(safeInstaller)) {
            Log.w(TAG, "installPackage() — rejecting non-conformant installerPackageName='$safeInstaller'")
            return@withContext STATUS_FAILURE
        }

        val tmpPath = "/data/local/tmp/lide_${System.currentTimeMillis()}_${(0..Int.MAX_VALUE).random()}.apk"
        val srcPath = shellQuote(apkFile.absolutePath)
        val tmpPathQuoted = shellQuote(tmpPath)

        try {
            val copyRes = Shell.cmd("cp $srcPath $tmpPathQuoted && chmod 644 $tmpPathQuoted").exec()
            if (!copyRes.isSuccess) {
                Log.e(
                    TAG,
                    "installPackage() — staging copy failed: exit=${copyRes.code} " +
                        "out='${copyRes.out.joinToString("\n")}' err='${copyRes.err.joinToString("\n")}'",
                )
                return@withContext STATUS_FAILURE
            }

            val command =
                buildString {
                    append("pm install ")
                    if (safeInstaller != null) append("-i ").append(safeInstaller).append(' ')
                    append("-r ")
                    append(tmpPathQuoted)
                }
            Log.d(TAG, "installPackage() — executing: $command")
            val result = Shell.cmd(command).exec()
            val stdout = result.out.joinToString("\n").trim()
            val stderr = result.err.joinToString("\n").trim()
            Log.d(TAG, "installPackage() — exit=${result.code} stdout='$stdout' stderr='$stderr'")
            if (result.isSuccess && stdout.contains("Success")) {
                STATUS_SUCCESS
            } else {
                Log.w(TAG, "installPackage() — pm reported failure: stdout='$stdout' stderr='$stderr'")
                STATUS_FAILURE
            }
        } finally {
            Shell.cmd("rm -f $tmpPathQuoted").submit()
        }
    }

    private fun refreshStatusBlocking() {
        val computed = computeStatus()
        if (_status.value != computed) {
            Log.d(TAG, "refreshStatus() — $computed (was ${_status.value})")
        }
        _status.value = computed
    }

    private fun computeStatus(): BackendStatus {
        // 仅用无提示检测（getCachedShell + 授权记忆），避免冷启动意外弹 su；
        // 真实 su 可用性与授权状态由「授权按钮 / 安装执行」拉主 shell 时懒惰纠正。
        Shell.getCachedShell()?.let { shell ->
            rememberGranted(shell.isRoot)
            return if (shell.isRoot) BackendStatus.READY else BackendStatus.UNAVAILABLE
        }
        // 全新进程（尚未建 shell）：信任此前授权记忆（持久授权下无需重复弹窗，GH#705 同款），
        // 否则提示待授权（su 未装时按钮侧会静默失败并保持状态，不误导）。
        return if (wasGrantedBefore()) BackendStatus.READY else BackendStatus.PERMISSION_NEEDED
    }

    /**
     * 建立 root shell 并确认是否实际持有 root。持久授权下静默；吊销后 shell 非 root，
     * 此时遗忘记忆使状态纠正。
     */
    private fun isRootGranted(): Boolean =
        try {
            val granted = Shell.getShell().isRoot
            rememberGranted(granted)
            granted
        } catch (e: Exception) {
            Log.w(TAG, "isRootGranted() — getShell failed: ${e.message}")
            false
        }

    private fun wasGrantedBefore(): Boolean = prefs.getBoolean(KEY_GRANTED_BEFORE, false)

    private fun rememberGranted(granted: Boolean) {
        if (prefs.getBoolean(KEY_GRANTED_BEFORE, false) != granted) {
            prefs.edit().putBoolean(KEY_GRANTED_BEFORE, granted).apply()
        }
    }

    /** 单引号包 shell 引号：`'` → `'\''`，防路径注入。 */
    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    companion object {
        private const val TAG = "RootServiceManager"
        private const val ROOT_PREFS = "lide_root"
        private const val KEY_GRANTED_BEFORE = "granted_before"
        private const val STATUS_SUCCESS = 0
        private const val STATUS_FAILURE = -1
        private const val SHELL_TIMEOUT_SECONDS = 10L

        /** 包名 / 安装者归属名校验（防 `-i` / `pm uninstall` 命令注入）。 */
        private val PACKAGE_NAME_PATTERN =
            Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$""")
    }
}
