package com.lioisme.delua.androlua

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipUtil {

    fun zip(srcDir: File, zipFile: File): Boolean {
        return try {
            zipFile.parentFile?.mkdirs()
            FileOutputStream(zipFile).use { zipToStream(srcDir, it) }
        } catch (e: Exception) {
            android.util.Log.e("ZipUtil", "zip failed: ${e.message}")
            false
        }
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
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val target = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { zis.copyTo(it) }
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
}
