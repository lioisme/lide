package com.lioisme.lide.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ZipUtil zip-slip 防护与往返测试（WP-019 测试骨架）。
 */
class ZipUtilTest {
    private fun zipOf(entries: Map<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private fun tempDir(): File {
        val dir = File.createTempFile("lide-test-", "")
        dir.delete()
        dir.mkdirs()
        return dir
    }

    @Test
    fun `zip-slip 绝对路径条目被拒绝`() {
        val zip = zipOf(mapOf("/etc/hosts" to "evil", "main.lua" to "ok"))
        val dest = tempDir()
        try {
            assertTrue(ZipUtil.unzipFromStream(ByteArrayInputStream(zip), dest))
            assertFalse(File(dest, "etc/hosts").exists())
            assertTrue(File(dest, "main.lua").exists())
        } finally {
            dest.deleteRecursively()
        }
    }

    @Test
    fun `zip-slip 上级目录条目被拒绝`() {
        val zip = zipOf(mapOf("../evil.lua" to "evil", "a/b/c/../../x.lua" to "skip", "ok.lua" to "fine"))
        val dest = tempDir()
        try {
            assertTrue(ZipUtil.unzipFromStream(ByteArrayInputStream(zip), dest))
            val parent = dest.parentFile
            assertFalse(File(parent, "evil.lua").exists())
            assertFalse(File(parent, "a/b/c/x.lua").exists())
            assertTrue(File(dest, "ok.lua").exists())
        } finally {
            dest.deleteRecursively()
        }
    }

    @Test
    fun `zip-slip 多级上级目录条目被拒绝`() {
        val zip = zipOf(mapOf("../../etc/hosts" to "evil", "../../../system/x.lua" to "evil2", "ok.lua" to "fine"))
        val dest = tempDir()
        try {
            assertTrue(ZipUtil.unzipFromStream(ByteArrayInputStream(zip), dest))
            assertFalse(File("/etc/hosts").canRead())
            assertFalse(File("/system/x.lua").canRead())
            assertTrue(File(dest, "ok.lua").exists())
        } finally {
            dest.deleteRecursively()
        }
    }

    @Test
    fun `反斜杠路径统一为正斜杠后安全解压`() {
        val zip = zipOf(mapOf("..\\evil.lua" to "evil", "sub\\file.lua" to "fine"))
        val dest = tempDir()
        try {
            assertTrue(ZipUtil.unzipFromStream(ByteArrayInputStream(zip), dest))
            val parent = dest.parentFile
            assertFalse(File(parent, "evil.lua").exists())
            assertTrue(File(dest, "sub").listFiles()?.any { it.name == "file.lua" } == true)
        } finally {
            dest.deleteRecursively()
        }
    }

    @Test
    fun `正常层级目录结构与二进制内容往返`() {
        val bytes = ByteArray(256) { it.toByte() }
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            zos.putNextEntry(ZipEntry("src/"))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("src/main.lua"))
            zos.write("print(1)".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("src/icon.png"))
            zos.write(bytes)
            zos.closeEntry()
        }
        val dest = tempDir()
        try {
            assertTrue(ZipUtil.unzipFromStream(ByteArrayInputStream(bos.toByteArray()), dest))
            assertEquals("print(1)", File(dest, "src/main.lua").readText())
            assertTrue(File(dest, "src/icon.png").readBytes().contentEquals(bytes))
        } finally {
            dest.deleteRecursively()
        }
    }

    @Test
    fun `zipToStream 生成可被 unzip 还原的包`() {
        val src = tempDir()
        val dest = tempDir()
        try {
            File(src, "a.lua").writeText("x")
            File(src, "sub").mkdirs()
            File(src, "sub/b.lua").writeText("y")
            val bos = ByteArrayOutputStream()
            assertTrue(ZipUtil.zipToStream(src, bos))
            assertTrue(ZipUtil.unzipFromStream(ByteArrayInputStream(bos.toByteArray()), dest))
            assertEquals("x", File(dest, "a.lua").readText())
            assertEquals("y", File(dest, "sub/b.lua").readText())
        } finally {
            src.deleteRecursively()
            dest.deleteRecursively()
        }
    }

    @Test
    fun `单条目超过上限被拒绝`() {
        // C5：单条目 > 64MB（MAX_SINGLE_ENTRY）必须中止，不写满磁盘。
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            zos.putNextEntry(ZipEntry("big.bin"))
            val chunk = ByteArray(1024 * 1024)
            var total = 0
            while (total < ZipUtil.MAX_SINGLE_ENTRY + (1L * 1024 * 1024)) {
                zos.write(chunk)
                total += chunk.size
            }
            zos.closeEntry()
        }
        val dest = tempDir()
        try {
            assertFalse(ZipUtil.unzipFromStream(ByteArrayInputStream(bos.toByteArray()), dest))
            val written = File(dest, "big.bin").let { if (it.exists()) it.length() else 0 }
            assertTrue("partial write should not exceed limit, got $written", written <= ZipUtil.MAX_SINGLE_ENTRY)
        } finally {
            dest.deleteRecursively()
        }
    }

    @Test
    fun `总解压字节超过上限被拒绝`() {
        // C5：多条目合计 > 512MB（MAX_TOTAL_UNCOMPRESSED）必须中止。
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            val chunk = ByteArray(1024 * 1024)
            var total = 0
            while (total < ZipUtil.MAX_TOTAL_UNCOMPRESSED + (1L * 1024 * 1024)) {
                zos.putNextEntry(ZipEntry("f$total.bin"))
                zos.write(chunk)
                zos.closeEntry()
                total += chunk.size
            }
        }
        val dest = tempDir()
        try {
            assertFalse(ZipUtil.unzipFromStream(ByteArrayInputStream(bos.toByteArray()), dest))
        } finally {
            dest.deleteRecursively()
        }
    }
}
