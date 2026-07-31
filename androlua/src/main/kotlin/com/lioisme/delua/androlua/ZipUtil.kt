package com.lioisme.delua.androlua

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipUtil {

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

    fun zipToStream(srcDir: File, outputStream: OutputStream): Boolean {
        return try {
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
            android.util.Log.e("ZipUtil", "zipToStream failed: ${e.message}")
            false
        }
    }

    fun unzip(zipFile: File, destDir: File): Boolean {
        return try {
            destDir.mkdirs()
            FileInputStream(zipFile).use { unzipFromStream(it, destDir) }
        } catch (e: Exception) {
            android.util.Log.e("ZipUtil", "unzip failed: ${e.message}")
            false
        }
    }

    fun unzipFromStream(inputStream: InputStream, destDir: File): Boolean {
        return try {
            destDir.mkdirs()
            val canonicalDest = destDir.canonicalFile
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = safeEntryName(entry.name)
                    if (name != null) {
                        val target = File(canonicalDest, name)
                        // 双保险：段级校验之外再做一次 canonical 前缀校验，
                        // 防符号链接等绕过手段把文件写出目标目录。
                        val canonicalTarget = target.canonicalFile
                        val underDest = canonicalTarget.path == canonicalDest.path ||
                            canonicalTarget.path.startsWith(canonicalDest.path + File.separator)
                        if (underDest) {
                            if (entry.isDirectory) {
                                target.mkdirs()
                            } else {
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { zis.copyTo(it) }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("ZipUtil", "unzipFromStream failed: ${e.message}")
            false
        }
    }

    /** 读取压缩包内首个不区分大小写匹配的文件文本；找不到或读取失败返回 null。 */
    fun readEntryText(zipFile: File, entryName: String): String? {
        return try {
            ZipFile(zipFile).use { zip ->
                val entry = zip.entries().asSequence().firstOrNull { e ->
                    !e.isDirectory && e.name.equals(entryName, ignoreCase = true)
                } ?: return null
                zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
            }
        } catch (e: Exception) {
            android.util.Log.e("ZipUtil", "readEntryText failed: ${e.message}")
            null
        }
    }
}
