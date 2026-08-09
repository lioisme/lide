package com.lioisme.lide.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class AXmlManifestTest {

    private fun sample(): ByteArray {
        return javaClass.getResourceAsStream("/manifest_sample.bin")!!.readBytes()
    }

    @Test
    fun `readPackageName reads host package`() {
        assertEquals("com.lioisme.lide", AXmlManifest.readPackageName(sample()))
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
            "com.lioisme.lide.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" in
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
                "com.lioisme.lide.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
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
            packageName = "com.lioisme.lide",
            appLabel = "LIDE",
            versionCode = 272,
            versionName = "1.0",
            usesPermissions = listOf(
                "android.permission.INTERNET",
                "com.lioisme.lide.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            ),
        )
        val patched = AXmlManifest.patch(sample(), edit)
        assertEquals("com.lioisme.lide", AXmlManifest.readPackageName(patched))
        assertEquals(
            listOf(
                "android.permission.INTERNET",
                "com.lioisme.lide.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
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
            poolText.contains("com.lioisme.lide.androidx-startup"),
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
                            assertEquals("com.lioisme.lide.LideApp", strings[data])
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

    @Test
    fun `patch rewrites application name and removes activities unconditionally`() {
        // 2026-08-09：引擎并入单 dex 集后补丁无条件回落引擎 Application + IDE 主入口整树移除。
        val edit = ManifestEdit(
            packageName = "com.demo.app",
            appLabel = "Demo",
            versionCode = 2,
            versionName = "2.0",
            usesPermissions = listOf("android.permission.INTERNET"),
            launcherActivity = "com.lioisme.lide.engine.EngineActivity",
            applicationName = "com.androlua.LuaApplication",
            removeActivities = listOf("com.lioisme.lide.MainActivity"),
        )
        val patched = AXmlManifest.patch(sample(), edit)
        val strings = readUtf16Strings(patched)

        var appName: String? = null
        val activityNames = mutableListOf<String>()
        var pos = 8
        while (pos < patched.size) {
            val type = u16(patched, pos)
            val size = u32(patched, pos + 4).toInt()
            if (type == 0x0102) {
                val elName = strings.getOrNull(u32(patched, pos + 20).toInt())
                val attrStart = u16(patched, pos + 24)
                val attrSize = u16(patched, pos + 26)
                val attrCount = u16(patched, pos + 28)
                val base = pos + 16 + attrStart
                var nameAttr: String? = null
                for (a in 0 until attrCount) {
                    val p = base + a * attrSize
                    val nsStr = strings.getOrNull(u32(patched, p).toInt())
                    val attrName = strings.getOrNull(u32(patched, p + 4).toInt())
                    val dataType = patched[p + 15].toInt() and 0xFF
                    if (nsStr == "http://schemas.android.com/apk/res/android" &&
                        attrName == "name" &&
                        dataType == 0x03
                    ) {
                        nameAttr = strings.getOrNull(u32(patched, p + 16).toInt())
                    }
                }
                when (elName) {
                    "application" -> appName = nameAttr
                    "activity" -> activityNames += nameAttr ?: ""
                }
            }
            assertTrue("chunk size $size at $pos", size > 0 && pos + size <= patched.size)
            pos += size
        }
        assertEquals("com.androlua.LuaApplication", appName)
        assertFalse("MainActivity 必须整树移除", "com.lioisme.lide.MainActivity" in activityNames)
        assertTrue("EngineActivity 声明保留", "com.lioisme.lide.engine.EngineActivity" in activityNames)
    }

    @Test
    fun `patch injects application icon reference when template has none`() {
        // 2026-08-09 P9：宿主自身无图标（manifest 无 android:icon），打包器注入
        // TYPE_REFERENCE 资源引用（引用产物 arsc 中存在的 drawable/icon 条目）。
        // 样本 manifest_sample.bin 的 application 无 icon 属性（宿主 icon 移除前基线）。
        val edit = ManifestEdit(
            packageName = "com.demo.app",
            appLabel = "Demo",
            versionCode = 2,
            versionName = "2.0",
            usesPermissions = listOf("android.permission.INTERNET"),
            applicationIconId = 0x7f05000c,
        )
        val patched = AXmlManifest.patch(sample(), edit)
        val strings = readUtf16Strings(patched)

        var found = false
        var pos = 8
        while (pos < patched.size) {
            val type = u16(patched, pos)
            val size = u32(patched, pos + 4).toInt()
            if (type == 0x0102) {
                val nameIdx = u32(patched, pos + 20).toInt()
                if (strings.getOrNull(nameIdx) == "application") {
                    val attrStart = u16(patched, pos + 24)
                    val attrSize = u16(patched, pos + 26)
                    val attrCount = u16(patched, pos + 28)
                    val base = pos + 16 + attrStart
                    for (a in 0 until attrCount) {
                        val p = base + a * attrSize
                        val nsStr = u32(patched, p).toInt().let { if (it >= 0) strings.getOrNull(it) else null }
                        val attrName = strings.getOrNull(u32(patched, p + 4).toInt())
                        val dataType = patched[p + 15].toInt() and 0xFF
                        val data = u32(patched, p + 16).toInt()
                        if (nsStr == "http://schemas.android.com/apk/res/android" && attrName == "icon") {
                            found = true
                            assertEquals("android:icon 必须是资源引用", 0x01, dataType)
                            assertEquals(0x7f05000c, data)
                        }
                    }
                }
            }
            assertTrue("chunk size $size at $pos", size > 0 && pos + size <= patched.size)
            pos += size
        }
        assertTrue("application 必须注入 android:icon", found)
        assertTrue("字符串池必须含 icon 属性名", strings.contains("icon"))
    }

    @Test
    fun `patch keeps template icon when template declares one`() {
        // 未来宿主自带 android:icon 时：attrBytes 原样保留且不重复注入。
        // 以 patch 两次的产物为模板（前一次注入的 icon 属性成为模板已有属性）。
        val first = AXmlManifest.patch(
            sample(),
            ManifestEdit(
                packageName = "com.demo.app",
                appLabel = "Demo",
                versionCode = 2,
                versionName = "2.0",
                usesPermissions = listOf("android.permission.INTERNET"),
                applicationIconId = 0x7f05000c,
            ),
        )
        val second = AXmlManifest.patch(
            first,
            ManifestEdit(
                packageName = "com.demo.app",
                appLabel = "Demo",
                versionCode = 2,
                versionName = "2.0",
                usesPermissions = listOf("android.permission.INTERNET"),
                applicationIconId = 0x7f05000c,
            ),
        )
        val strings = readUtf16Strings(second)

        var iconCount = 0
        var pos = 8
        while (pos < second.size) {
            val type = u16(second, pos)
            val size = u32(second, pos + 4).toInt()
            if (type == 0x0102) {
                val nameIdx = u32(second, pos + 20).toInt()
                if (strings.getOrNull(nameIdx) == "application") {
                    val attrStart = u16(second, pos + 24)
                    val attrSize = u16(second, pos + 26)
                    val attrCount = u16(second, pos + 28)
                    val base = pos + 16 + attrStart
                    for (a in 0 until attrCount) {
                        val p = base + a * attrSize
                        val nsStr = u32(second, p).toInt().let { if (it >= 0) strings.getOrNull(it) else null }
                        val attrName = strings.getOrNull(u32(second, p + 4).toInt())
                        if (nsStr == "http://schemas.android.com/apk/res/android" && attrName == "icon") {
                            iconCount++
                        }
                    }
                }
            }
            assertTrue("chunk size $size at $pos", size > 0 && pos + size <= second.size)
            pos += size
        }
        assertEquals("模板已有 icon 属性时不得重复注入", 1, iconCount)
    }

    // ===== UTF-8 字符串池专项（aapt ENCODE_LENGTH 变长前缀） =====

    @Test
    fun `utf8 pool decodes mixed-length prefixes correctly`() {
        // 索引 4 = 中文串：charLen<0x80（单字节前缀）、byteLen>=0x80（两字节前缀）。
        // 旧实现固定 u16 + 反序读取在此必错，回归锚点。
        val axml = buildUtf8Axml(packageIndex = 4)
        assertEquals("测试".repeat(45), AXmlManifest.readPackageName(axml))
    }

    @Test
    fun `utf8 pool decodes lengths above 0x80 for both prefixes`() {
        // 索引 3 = 130 个 ASCII：charLen 与 byteLen 均为两字节前缀。
        val axml = buildUtf8Axml(packageIndex = 3)
        assertEquals("a".repeat(130), AXmlManifest.readPackageName(axml))
    }

    @Test
    fun `patch rebuilds utf8 source pool to utf16 output`() {
        val axml = buildUtf8Axml(packageIndex = 4)
        val edit = ManifestEdit(
            packageName = "com.demo.app",
            appLabel = "Demo",
            versionCode = 2,
            versionName = "2.0",
            usesPermissions = listOf("android.permission.INTERNET"),
        )
        val patched = AXmlManifest.patch(axml, edit)
        assertEquals("com.demo.app", AXmlManifest.readPackageName(patched))
        // 输出池固定为 UTF-16（flags=0），不再含 UTF-8 标志。
        assertEquals(0L, u32(patched, 8 + 16))
        assertEquals("Demo", readFirstLabel(patched))
    }

    private fun readFirstLabel(patched: ByteArray): String {
        val count = u32(patched, 16).toInt()
        val stringsStart = u32(patched, 28).toInt()
        val out = ArrayList<String>(count)
        for (i in 0 until count) {
            val o = u32(patched, 36 + 4 * i).toInt()
            val p = 8 + stringsStart + o
            val len = u16(patched, p)
            out += String(patched, p + 2, len * 2, Charsets.UTF_16LE)
        }
        return out.firstOrNull { it == "Demo" } ?: ""
    }

    private fun encodeUtf8Len(len: Int): ByteArray {
        return if (len < 0x80) byteArrayOf(len.toByte())
        else byteArrayOf((0x80 or (len shr 8)).toByte(), (len and 0xFF).toByte())
    }

    private fun encodeUtf8Entry(s: String): ByteArray {
        val bytes = s.encodeToByteArray()
        val out = ByteArrayOutputStream()
        out.write(encodeUtf8Len(s.length))
        out.write(encodeUtf8Len(bytes.size))
        out.write(bytes)
        out.write(0)
        return out.toByteArray()
    }

    /** 构建最小 UTF-8 池 AXML：池 + manifest 元素（含一个 package 属性）。 */
    private fun buildUtf8Axml(packageIndex: Int): ByteArray {
        val strings = listOf(
            "http://schemas.android.com/apk/res/android",
            "manifest",
            "package",
            "a".repeat(130), // charLen 与 byteLen 均两字节前缀
            "测试".repeat(45), // charLen 单字节 / byteLen 两字节前缀
            "ok",
        )
        check(packageIndex in strings.indices && packageIndex >= 3)

        val stringsStart = 28 + 4 * strings.size
        var dataSize = 0
        val offsets = IntArray(strings.size)
        for (i in strings.indices) {
            offsets[i] = dataSize
            dataSize += encodeUtf8Entry(strings[i]).size
        }
        val pool = ByteArray(28 + 4 * strings.size + dataSize)
        putU16(pool, 0, 0x0001)
        putU16(pool, 2, 28)
        putU32(pool, 4, pool.size.toLong())
        putU32(pool, 8, strings.size.toLong())
        putU32(pool, 12, 0)
        putU32(pool, 16, 0x100) // UTF8_FLAG
        putU32(pool, 20, stringsStart.toLong())
        putU32(pool, 24, 0)
        for (i in strings.indices) putU32(pool, 28 + 4 * i, offsets[i].toLong())
        for (i in strings.indices) {
            val entry = encodeUtf8Entry(strings[i])
            entry.copyInto(pool, stringsStart + offsets[i])
        }

        // manifest start 元素：ns=android URI，attr=package（值为 packageIndex 处字符串）。
        val element = ByteArray(56)
        putU16(element, 0, 0x0102)
        putU16(element, 2, 16)
        putU32(element, 4, element.size.toLong())
        putU32(element, 8, 0)
        putU32(element, 12, 0xFFFFFFFFL)
        putU32(element, 16, 0)
        putU32(element, 20, 1) // "manifest"
        putU16(element, 24, 20)
        putU16(element, 26, 20)
        putU16(element, 28, 1)
        putU32(element, 36, 0) // ns=android URI
        putU32(element, 40, 2) // "package"
        putU32(element, 44, 0xFFFFFFFFL) // raw
        putU16(element, 48, 8) // Res_value.size
        element[50] = 0
        element[51] = 0x03 // TYPE_STRING
        putU32(element, 52, packageIndex.toLong())

        val end = ByteArray(24)
        putU16(end, 0, 0x0103)
        putU16(end, 2, 16)
        putU32(end, 4, end.size.toLong())
        putU32(end, 8, 1)
        putU32(end, 12, 0xFFFFFFFFL)
        putU32(end, 16, 0xFFFFFFFFL)
        putU32(end, 20, 1)

        val total = 8 + pool.size + element.size + end.size
        val out = ByteArray(total)
        putU16(out, 0, 0x0003)
        putU16(out, 2, 8)
        putU32(out, 4, total.toLong())
        pool.copyInto(out, 8)
        element.copyInto(out, 8 + pool.size)
        end.copyInto(out, 8 + pool.size + element.size)
        return out
    }

    private fun putU16(out: ByteArray, o: Int, v: Int) {
        out[o] = (v and 0xFF).toByte()
        out[o + 1] = ((v shr 8) and 0xFF).toByte()
    }

    private fun putU32(out: ByteArray, o: Int, v: Long) {
        out[o] = (v and 0xFF).toByte()
        out[o + 1] = ((v shr 8) and 0xFF).toByte()
        out[o + 2] = ((v shr 16) and 0xFF).toByte()
        out[o + 3] = ((v shr 24) and 0xFF).toByte()
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
