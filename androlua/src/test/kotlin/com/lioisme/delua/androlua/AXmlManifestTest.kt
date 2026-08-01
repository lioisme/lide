package com.lioisme.delua.androlua

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AXmlManifestTest {

    private fun sample(): ByteArray {
        return javaClass.getResourceAsStream("/manifest_sample.bin")!!.readBytes()
    }

    @Test
    fun `readPackageName reads host package`() {
        assertEquals("com.lioisme.delua", AXmlManifest.readPackageName(sample()))
    }

    @Test
    fun `readUsesPermissions covers host list`() {
        val perms = AXmlManifest.readUsesPermissions(sample())
        assertTrue(perms.size >= 50)
        assertTrue("android.permission.INTERNET" in perms)
        assertTrue("android.permission.CAMERA" in perms)
        assertTrue("android.permission.MANAGE_EXTERNAL_STORAGE" in perms)
    }

    @Test
    fun `readSelfDeclaredPermissions includes dynamic receiver permission`() {
        assertTrue(
            "com.lioisme.delua.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" in
                AXmlManifest.readSelfDeclaredPermissions(sample())
        )
    }

    @Test
    fun `patch rewrites package label version and permission filter`() {
        val edit = ManifestEdit(
            packageName = "com.demo.app",
            appLabel = "Demo",
            versionCode = 1234,
            versionName = "2.0",
            usesPermissions = listOf(
                "android.permission.INTERNET",
                "android.permission.CAMERA",
                "com.lioisme.delua.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            ),
        )
        val patched = AXmlManifest.patch(sample(), edit)

        assertEquals("com.demo.app", AXmlManifest.readPackageName(patched))
        assertEquals(
            listOf(
                "android.permission.INTERNET",
                "android.permission.CAMERA",
                "com.demo.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            ),
            AXmlManifest.readUsesPermissions(patched),
        )
        assertTrue(AXmlManifest.readUsesPermissions(patched).none { it == "android.permission.VIBRATE" })
    }

    @Test
    fun `patch adds package attr when missing`() {
        val edit = ManifestEdit(
            packageName = "com.renamed.app",
            appLabel = "Demo",
            versionCode = 1,
            versionName = "1.0",
            usesPermissions = listOf("android.permission.INTERNET"),
        )
        val patched = AXmlManifest.patch(sample(), edit)
        assertEquals("com.renamed.app", AXmlManifest.readPackageName(patched))
    }

    @Test
    fun `patch with same package keeps self-referenced permission`() {
        val edit = ManifestEdit(
            packageName = "com.lioisme.delua",
            appLabel = "delua",
            versionCode = 272,
            versionName = "1.0",
            usesPermissions = listOf(
                "android.permission.INTERNET",
                "com.lioisme.delua.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            ),
        )
        val patched = AXmlManifest.patch(sample(), edit)
        assertEquals("com.lioisme.delua", AXmlManifest.readPackageName(patched))
        assertEquals(
            listOf(
                "android.permission.INTERNET",
                "com.lioisme.delua.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            ),
            AXmlManifest.readUsesPermissions(patched),
        )
    }

    @Test
    fun `patch output is structurally valid`() {
        val edit = ManifestEdit(
            packageName = "com.demo.app",
            appLabel = "Demo",
            versionCode = 9,
            versionName = "9.9",
            usesPermissions = listOf("android.permission.INTERNET"),
        )
        val patched = AXmlManifest.patch(sample(), edit)
        assertEquals(0x0003, u16(patched, 0))
        assertEquals(patched.size.toLong(), u32(patched, 4))
        val poolCount = u32(patched, 16).toInt()
        assertTrue(poolCount > 150)
        assertEquals(patched.size, walkChunks(patched))
    }

    @Test
    fun `patch renames provider authorities with package prefix`() {
        val edit = ManifestEdit(
            packageName = "com.demo.buildapp",
            appLabel = "builddemo",
            versionCode = 2,
            versionName = "2.0",
            usesPermissions = listOf("android.permission.INTERNET"),
        )
        val patched = AXmlManifest.patch(sample(), edit)
        val poolText = String(patched, Charsets.UTF_16LE)
        assertFalse(
            "authorities 必须随包名重命名",
            poolText.contains("com.lioisme.delua.androidx-startup"),
        )
        assertTrue(poolText.contains("com.demo.buildapp.androidx-startup"))
        assertTrue(poolText.contains("com.demo.buildapp.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"))
        assertTrue(poolText.contains("com.demo.buildapp"))
        assertFalse(
            "组件类名不得重命名",
            poolText.contains("com.demo.buildapp.MainActivity"),
        )
    }

    @Test
    fun `patch chunks are 4-byte aligned`() {
        val edit = ManifestEdit(
            packageName = "com.demo.app",
            appLabel = "builddemo",
            versionCode = 2,
            versionName = "2.0",
            usesPermissions = listOf(
                "android.permission.INTERNET",
                "android.permission.CAMERA",
                "com.demo.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            ),
        )
        val patched = AXmlManifest.patch(sample(), edit)
        var pos = 8
        while (pos < patched.size) {
            val size = u32(patched, pos + 4).toInt()
            assertTrue(
                "chunk size $size at $pos must be 4-aligned (ResStringPool 校验)",
                size % 4 == 0,
            )
            pos += size
        }
    }

    @Test
    fun `patch preserves copied attrs like application name and int flags`() {
        val edit = ManifestEdit(
            packageName = "com.demo.app",
            appLabel = "Demo",
            versionCode = 2,
            versionName = "2.0",
            usesPermissions = listOf("android.permission.INTERNET"),
        )
        val patched = AXmlManifest.patch(sample(), edit)
        val strings = readUtf16Strings(patched)

        // 逐属性核验拷贝属性完整保留（回归：attrBytes 曾只拷 Res_value 8 字节，
        // 导致 android:name / debuggable / allowBackup 等被清零 → 打包 APK 起不来）。
        var pos = 8
        var found = false
        while (pos < patched.size) {
            val type = u16(patched, pos)
            val size = u32(patched, pos + 4).toInt()
            if (type == 0x0102) {
                val nameIdx = u32(patched, pos + 20).toInt()
                if (strings.getOrNull(nameIdx) == "application") {
                    found = true
                    val attrStart = u16(patched, pos + 24)
                    val attrSize = u16(patched, pos + 26)
                    val attrCount = u16(patched, pos + 28)
                    val base = pos + 16 + attrStart
                    var sawAppName = false
                    var sawIntAttr = false
                    for (a in 0 until attrCount) {
                        val p = base + a * attrSize
                        val nsStr = u32(patched, p).toInt().let { if (it >= 0) strings.getOrNull(it) else null }
                        val attrName = strings.getOrNull(u32(patched, p + 4).toInt())
                        val dataType = patched[p + 15].toInt() and 0xFF
                        val data = u32(patched, p + 16).toInt()
                        if (nsStr == "http://schemas.android.com/apk/res/android" && attrName == "name") {
                            assertEquals("android:name 必须是字符串", 0x03, dataType)
                            assertEquals("com.lioisme.delua.DeluaApp", strings[data])
                            sawAppName = true
                        }
                        if (attrName == "debuggable" || attrName == "allowBackup") {
                            if (dataType == 0x10 || dataType == 0x12) sawIntAttr = true
                        }
                    }
                    assertTrue("application android:name 必须保留", sawAppName)
                    assertTrue("int 属性（debuggable/allowBackup）必须保留", sawIntAttr)
                    break
                }
            }
            assertTrue("chunk size $size at $pos", size > 0 && pos + size <= patched.size)
            pos += size
        }
        assertTrue("未找到 application 元素", found)
    }

    private fun readUtf16Strings(patched: ByteArray): List<String> {
        val count = u32(patched, 16).toInt()
        val stringsStart = u32(patched, 28).toInt()
        val offsets = (0 until count).map { u32(patched, 36 + 4 * it).toInt() }
        val out = ArrayList<String>(count)
        for (o in offsets) {
            val p = 8 + stringsStart + o
            val len = u16(patched, p)
            out += String(patched, p + 2, len * 2, Charsets.UTF_16LE)
        }
        return out
    }

    private fun walkChunks(bytes: ByteArray): Int {
        var pos = 8
        while (pos < bytes.size) {
            val size = u32(bytes, pos + 4).toInt()
            assertTrue("chunk size $size at $pos", size > 0 && pos + size <= bytes.size)
            pos += size
        }
        return pos
    }

    private fun u16(b: ByteArray, o: Int): Int {
        return (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    }

    private fun u32(b: ByteArray, o: Int): Long {
        return (b[o].toLong() and 0xFF) or
            ((b[o + 1].toLong() and 0xFF) shl 8) or
            ((b[o + 2].toLong() and 0xFF) shl 16) or
            ((b[o + 3].toLong() and 0xFF) shl 24)
    }
}
