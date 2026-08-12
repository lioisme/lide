package com.lioisme.lide.project

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipUtil {
    /** 单一日志 TAG（Q13 收口：历史全限定名 Log.e("ZipUtil", ...) 统一为常量）。 */
    private const val TAG = "ZipUtil"

    /** C5（2026-08-12）Zip Bomb 防护上限：解压总字节 / 条目数 / 单条目大小。
     * 恶意 .alp 可声明极小压缩比以写满磁盘，超限即中止并抛异常（调用方清理半成品）。 */
    const val MAX_TOTAL_UNCOMPRESSED = 512L * 1024 * 1024
    const val MAX_ENTRY_COUNT = 10_000
    const val MAX_SINGLE_ENTRY = 64L * 1024 * 1024

    /**
     * 解压条目名安全化：拒绝绝对路径与含 `..` 段的条目（Zip Slip 防护），
     * 并将反斜杠统一为正斜杠。返回 null 表示条目不安全、应跳过。
     */
    private fun safeEntryName(entryName: String): String? {
        val normalized = entryName.replace('\\', '/')
        if (normalized.startsWith("/") || normalized == ".." || normalized.startsWith("../")) {
            return null
        }
        return if (normalized.split('/').any { it == ".." }) null else normalized
    }

    fun zipToStream(
        srcDir: File,
        outputStream: OutputStream,
    ): Boolean =
        try {
            ZipOutputStream(outputStream).use { zos ->
                srcDir.walkTopDown().forEach { file ->
                    val entryName = file.relativeTo(srcDir).path.replace('\\', '/')
                    if (file.isDirectory) {
                        zos.putNextEntry(ZipEntry("$entryName/"))
                        zos.closeEntry()
                    } else {
                        zos.putNextEntry(ZipEntry(entryName))
                        FileInputStream(file).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "zipToStream failed: ${e.message}")
            false
        }

    fun unzip(
        zipFile: File,
        destDir: File,
    ): Boolean =
        try {
            destDir.mkdirs()
            FileInputStream(zipFile).use { unzipFromStream(it, destDir) }
        } catch (e: Exception) {
            Log.e(TAG, "unzip failed: ${e.message}")
            false
        }

    fun unzipFromStream(
        inputStream: InputStream,
        destDir: File,
    ): Boolean =
        try {
            destDir.mkdirs()
            val canonicalDest = destDir.canonicalFile
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                var entryCount = 0
                var totalBytes = 0L
                while (entry != null) {
                    entryCount++
                    if (entryCount > MAX_ENTRY_COUNT) {
                        throw ZipBombException("entry count exceeded $MAX_ENTRY_COUNT")
                    }
                    val name = safeEntryName(entry.name)
                    if (name != null) {
                        val target = File(canonicalDest, name)
                        // 双保险：段级校验之外再做一次 canonical 前缀校验，
                        // 防符号链接等绕过手段把文件写出目标目录。
                        val canonicalTarget = target.canonicalFile
                        val underDest =
                            canonicalTarget.path == canonicalDest.path ||
                                canonicalTarget.path.startsWith(canonicalDest.path + File.separator)
                        if (underDest) {
                            if (entry.isDirectory) {
                                target.mkdirs()
                            } else {
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { out ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    var entryBytes = 0L
                                    var read: Int
                                    while (zis.read(buffer).also { read = it } != -1) {
                                        entryBytes += read
                                        totalBytes += read
                                        // C5：单条目 / 总量超限即中止——先查后写，
                                        // 超限 chunk 不落盘（防大文件先写满磁盘再被拒绝）。
                                        if (entryBytes > MAX_SINGLE_ENTRY) {
                                            throw ZipBombException(
                                                "entry ${entry.name} exceeds $MAX_SINGLE_ENTRY bytes",
                                            )
                                        }
                                        if (totalBytes > MAX_TOTAL_UNCOMPRESSED) {
                                            throw ZipBombException(
                                                "total uncompressed exceeds $MAX_TOTAL_UNCOMPRESSED bytes",
                                            )
                                        }
                                        out.write(buffer, 0, read)
                                    }
                                }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: ZipBombException) {
            Log.e(TAG, "unzipFromStream aborted: ${e.message}")
            false
        } catch (e: Exception) {
            val snapshot =
                try {
                    val children =
                        destDir.listFiles()?.joinToString { f ->
                            "${f.name} dir=${f.isDirectory} len=${f.length()}"
                        } ?: "null"
                    "dest=$destDir exists=${destDir.exists()} isDir=${destDir.isDirectory} children=[$children]"
                } catch (ex: Exception) {
                    "snapshot failed: ${ex.message}"
                }
            Log.e(TAG, "unzipFromStream failed: ${e.message}; $snapshot", e)
            false
        }

    /** 读取压缩包内首个不区分大小写匹配的文件文本；找不到或读取失败返回 null。 */
    fun readEntryText(
        zipFile: File,
        entryName: String,
    ): String? {
        return try {
            ZipFile(zipFile).use { zip ->
                val entry =
                    zip.entries().asSequence().firstOrNull { e ->
                        !e.isDirectory && e.name.equals(entryName, ignoreCase = true)
                    } ?: return null
                zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "readEntryText failed: ${e.message}")
            null
        }
    }
}

/** C5（2026-08-12）：解压超限中止信号（[ZipUtil.unzipFromStream] 分支捕获）。 */
private class ZipBombException(
    message: String,
) : IOException(message)
