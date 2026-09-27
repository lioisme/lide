package com.lioisme.lide.install.shizuku

import android.os.Build
import android.os.ParcelFileDescriptor
import com.lioisme.lide.install.IShizukuInstallerService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger("ShizukuService")

/**
 * Shizuku UserService 安装器实现，运行于 Shizuku 按需启动的独立进程
 * （`Shizuku.bindUserService` 以类名反射实例化，见 [ShizukuServiceManager]）。
 *
 * 安装方式：`pm install [-i <安装者>] -S <size>` 并以 [ParcelFileDescriptor]
 * 经 stdin 管道灌入 APK 字节——Shell（UID 2000）上下文下 pm 直接调包管理服务，
 * 全程无系统确认页。
 *
 * 移植自 komi-store `ShizukuInstallerServiceImpl`（2026-08-09 调研），保留：
 * - 120s 安装 / 30s 卸载超时，超时强制 `destroyForcibly`；
 * - 成功判定 = 退出码 0 且 stdout 含 `Success`；
 * - 写线程超时中断（大包管道阻塞防护）。
 *
 * 注意：本类经 R8 反射实例化，整包 keep（proguard-rules.pro「静默安装 keep」段）。
 */
class ShizukuInstallerServiceImpl : IShizukuInstallerService.Stub() {
    companion object {
        /** API 26+ Process.destroyForcibly() 兼容：低版本回落 destroy()。 */
        private fun Process.destroyForciblySafe() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) destroyForcibly() else destroy()
        }

        private const val STATUS_SUCCESS = 0
        private const val STATUS_FAILURE = -1
        private const val INSTALL_TIMEOUT_SECONDS = 120L
        private const val UNINSTALL_TIMEOUT_SECONDS = 30L
        private const val PIPE_BUFFER_SIZE = 65_536

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
        installerPackageName: String?,
    ): Int {
        log("installPackage() called — fileSize=$fileSize, installer=$installerPackageName")
        return try {
            val safeInstaller = installerPackageName?.takeIf { it.isNotBlank() }
            val command =
                if (safeInstaller != null) {
                    arrayOf("pm", "install", "-i", safeInstaller, "-S", fileSize.toString())
                } else {
                    arrayOf("pm", "install", "-S", fileSize.toString())
                }
            log("Executing: ${command.joinToString(" ")}")

            val process =
                ProcessBuilder(*command)
                    // B3（2026-08-12）：redirectErrorStream 合并 stderr→stdout 单流读取——
                    // 顺序双流 readText() 在 stderr 超过 64KB 管道缓冲（如 pm 打印完整
                    // 堆栈）时会死锁（子进程阻塞写 stderr、父进程阻塞读 stdout）。
                    .redirectErrorStream(true)
                    .start()

            val writeThread =
                Thread {
                    try {
                        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                            process.outputStream.use { output ->
                                val buffer = ByteArray(PIPE_BUFFER_SIZE)
                                var bytesWritten = 0L
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                    bytesWritten += read
                                }
                                output.flush()
                                log("APK piped to pm stdin: $bytesWritten bytes (expected: $fileSize)")
                            }
                        }
                    } catch (e: Exception) {
                        logE("Error piping APK to pm stdin", e)
                    }
                }
            writeThread.start()

            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            val stderr = "" // B3（2026-08-12）：redirectErrorStream 合并后无独立 stderr 流

            writeThread.join(INSTALL_TIMEOUT_SECONDS * 1000)
            if (writeThread.isAlive) {
                logE("Write thread timed out after ${INSTALL_TIMEOUT_SECONDS}s, aborting install")
                writeThread.interrupt()
                process.destroyForciblySafe()
                return STATUS_FAILURE
            }

            val finished = process.waitFor(INSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                logE("pm install timed out after ${INSTALL_TIMEOUT_SECONDS}s, aborting")
                process.destroyForciblySafe()
                return STATUS_FAILURE
            }

            val exitCode = process.exitValue()
            log("pm install — exitCode=$exitCode, stdout='$stdout', stderr='$stderr'")

            if (exitCode == 0 && stdout.contains("Success")) {
                log("Install SUCCESS")
                STATUS_SUCCESS
            } else {
                logE("Install FAILED — exitCode=$exitCode, stdout='$stdout', stderr='$stderr'")
                STATUS_FAILURE
            }
        } catch (e: Exception) {
            logE("installPackage() exception", e)
            STATUS_FAILURE
        }
    }

    override fun uninstallPackage(packageName: String): Int {
        log("uninstallPackage() called for: $packageName")
        return try {
            val process =
                ProcessBuilder("pm", "uninstall", packageName)
                    .redirectErrorStream(true) // B3 同款：单流读取防 stderr 管道死锁
                    .start()
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            val stderr = ""

            val finished = process.waitFor(UNINSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                logE("pm uninstall timed out after ${UNINSTALL_TIMEOUT_SECONDS}s, aborting")
                process.destroyForciblySafe()
                return STATUS_FAILURE
            }

            val exitCode = process.exitValue()
            log("pm uninstall — exitCode=$exitCode, stdout='$stdout', stderr='$stderr'")

            if (exitCode == 0 && stdout.contains("Success")) {
                log("Uninstall SUCCESS")
                STATUS_SUCCESS
            } else {
                logE("Uninstall FAILED — exitCode=$exitCode, stdout='$stdout', stderr='$stderr'")
                STATUS_FAILURE
            }
        } catch (e: Exception) {
            logE("uninstallPackage() exception", e)
            STATUS_FAILURE
        }
    }

    override fun destroy() {
        log("destroy() — service being unbound")
    }
}
