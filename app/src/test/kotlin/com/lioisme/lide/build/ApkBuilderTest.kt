package com.lioisme.lide.build

import com.lioisme.lide.androlua.AXmlManifest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ApkBuilderTest {
    private val key: java.security.PrivateKey by lazy {
        val der = loadResource("keys/testkey.pk8")
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
    }

    private val cert: java.security.cert.X509Certificate by lazy {
        val pem = String(loadResource("keys/testkey.x509.pem"), Charsets.UTF_8)
        CertificateFactory
            .getInstance("X.509")
            .generateCertificate(pem.byteInputStream()) as java.security.cert.X509Certificate
    }

    private fun loadResource(name: String): ByteArray = checkNotNull(checkNotNull(javaClass.classLoader).getResourceAsStream(name)).readBytes()

    /** 以真实宿主 manifest 为蓝本的合成宿主 APK。 */
    private fun syntheticHostApk(): ByteArray {
        val manifest = loadResource("manifest_sample.bin")
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            fun put(
                name: String,
                bytes: ByteArray,
                stored: Boolean = false,
            ) {
                val entry =
                    ZipEntry(name).apply {
                        method = if (stored) ZipEntry.STORED else ZipEntry.DEFLATED
                        if (stored) {
                            size = bytes.size.toLong()
                            crc = CRC32().apply { update(bytes) }.value
                        }
                    }
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
            put("AndroidManifest.xml", manifest, stored = true)
            put("classes.dex", "dex-bytes".toByteArray())
            put("resources.arsc", "arsc-bytes".toByteArray(), stored = true)
            put("assets/lua/bin.lua", "-- host bin".toByteArray())
            put("assets/lua/notused.lua", "-- host not used".toByteArray())
            put("assets/samples/demo/main.lua", "-- sample".toByteArray())
            put("assets/keys/testkey.pk8", "key-bytes".toByteArray())
            put("assets/textmate/lua.tmLanguage.json", "{}".toByteArray())
            // 空名零字节条目（对齐/壳产物）：必须被跳过，不能触发重复条目拒绝
            put("", byteArrayOf(), stored = true)
        }
        return out.toByteArray()
    }

    private fun input(): ApkBuilder.ProjectInput =
        ApkBuilder.ProjectInput(
            packageName = "com.demo.app",
            appName = "Demo",
            versionName = "2.0",
            versionCode = 1234,
            declaredPermissions =
                listOf(
                    "android.permission.INTERNET",
                    "android.permission.CAMERA",
                    "android.permission.VIBRATE",
                    "android.permission.INVALID_NOT_IN_HOST",
                ),
            luaSources =
                mapOf(
                    "main.lua" to "print('hello')\n".toByteArray(),
                    "init.lua" to "-- config\nappname='Demo'\n".toByteArray(),
                    "lib/util.lua" to "-- helper\n".toByteArray(),
                ),
            // 需求②：宿主引擎 Lua 库按需保留，未列出的整包剔除。
            engineLuaLibs = listOf("bin.lua"),
        )

    private fun entriesOf(apk: ByteArray): Map<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        ZipInputStream(apk.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                map[entry.name] = zis.readBytes()
            }
        }
        return map
    }

    /** 真实 luajava 为 native（仅真机可用）；单测注入等价编译器。 */
    private fun fakeCompiler(): (String, String) -> ByteArray {
        val header = byteArrayOf(0x1B.toByte(), 0x4C, 0x75, 0x61) // \x1bLua
        return { source, _ ->
            require(source.isNotBlank()) { "empty source" }
            header + "FAKE".toByteArray()
        }
    }

    @Test
    fun `build produces installable v1-signed apk`() {
        val apk = ApkBuilder.build(syntheticHostApk(), input(), key, cert, fakeCompiler())
        val entries = entriesOf(apk)

        assertNotNull(entries["AndroidManifest.xml"])
        assertNotNull(entries["classes.dex"])
        assertNotNull(entries["resources.arsc"])
        assertNotNull(entries["META-INF/MANIFEST.MF"])
        assertNotNull(entries["META-INF/CERT.SF"])
        assertNotNull(entries["META-INF/CERT.RSA"])
        assertTrue(V1Verifier.verify(apk, cert))
    }

    @Test
    fun `manifest rewritten with project identity and whitelist`() {
        val apk = ApkBuilder.build(syntheticHostApk(), input(), key, cert, fakeCompiler())
        val manifest = entriesOf(apk)["AndroidManifest.xml"]!!

        assertEquals("com.demo.app", AXmlManifest.readPackageName(manifest))
        val permissions = AXmlManifest.readUsesPermissions(manifest).toSet()
        assertTrue("android.permission.INTERNET" in permissions)
        assertTrue("android.permission.CAMERA" in permissions)
        assertTrue("android.permission.VIBRATE" in permissions)
        // 白名单 = 声明 ∩ 宿主：宿主没有的声明被过滤
        assertTrue("android.permission.INVALID_NOT_IN_HOST" !in permissions)
        // 宿主自声明权限（动态接收器）随包名联动
        assertTrue("com.demo.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" in permissions)
        assertTrue("com.lioisme.lide.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" !in permissions)
    }

    @Test
    fun `main lua compiled to bytecode others kept as source`() {
        val apk = ApkBuilder.build(syntheticHostApk(), input(), key, cert, fakeCompiler())
        val entries = entriesOf(apk)

        val main = entries["assets/lua/main.lua"]!!
        assertArrayEquals(byteArrayOf(0x1B.toByte(), 0x4C, 0x75, 0x61), main.copyOfRange(0, 4))
        assertArrayEquals("-- config\nappname='Demo'\n".toByteArray(), entries["assets/lua/init.lua"])
        assertArrayEquals("-- helper\n".toByteArray(), entries["assets/lua/lib/util.lua"])
        assertArrayEquals("-- host bin".toByteArray(), entries["assets/lua/bin.lua"])
    }

    @Test
    fun `host engine lua whitelisted and host-only assets excluded`() {
        val apk = ApkBuilder.build(syntheticHostApk(), input(), key, cert, fakeCompiler())
        val entries = entriesOf(apk)

        // 白名单内引擎库保留；未列入的宿主引擎库剔除
        assertArrayEquals("-- host bin".toByteArray(), entries["assets/lua/bin.lua"])
        assertTrue("assets/lua/notused.lua" !in entries)
        // 宿主专属资产（示例/签名密钥/语法高亮）不进入产物
        assertTrue("assets/samples/demo/main.lua" !in entries)
        assertTrue("assets/keys/testkey.pk8" !in entries)
        assertTrue("assets/textmate/lua.tmLanguage.json" !in entries)
    }

    @Test
    fun `project files override same-name host engine lib`() {
        val input =
            input().copy(
                engineLuaLibs = listOf("bin.lua"),
                luaSources =
                    input().luaSources.toMutableMap().apply {
                        this["bin.lua"] = "-- project bin".toByteArray()
                    },
            )
        val apk = ApkBuilder.build(syntheticHostApk(), input, key, cert, fakeCompiler())
        val entries = entriesOf(apk)

        assertArrayEquals("-- project bin".toByteArray(), entries["assets/lua/bin.lua"])
    }

    @Test
    fun `compile error surfaces as BuildException`() {
        val badInput =
            input().copy(
                luaSources =
                    input().luaSources.toMutableMap().apply {
                        this["main.lua"] = "function (".toByteArray()
                    },
            )
        try {
            ApkBuilder.build(
                syntheticHostApk(),
                badInput,
                key,
                cert,
                { _, _ -> throw IllegalArgumentException("main.lua:3: syntax error") },
            )
            assertTrue("should have thrown", false)
        } catch (e: ApkBuilder.BuildException) {
            assertTrue(e.message!!.isNotBlank())
        }
    }

    @Test
    fun `stored entries are 4-byte aligned in final apk`() {
        val apk = ApkBuilder.build(syntheticHostApk(), input(), key, cert, fakeCompiler())
        // resources.arsc 是 STORED 条目：R+ 安装校验要求数据区 4 字节对齐。
        val offset = storedDataOffset(apk, "resources.arsc")
        assertTrue("resources.arsc data offset $offset must be 4-aligned", offset % 4 == 0L)
        assertTrue(
            ZipInputStream(apk.inputStream()).use { zis ->
                var stored = false
                while (true) {
                    val e = zis.nextEntry ?: break
                    if (e.name == "resources.arsc") stored = e.method == ZipEntry.STORED
                }
                stored
            },
        )
    }

    /** 经 CEN→LOC 手工解析 STORED 条目的数据区偏移。 */
    private fun storedDataOffset(
        apk: ByteArray,
        target: String,
    ): Long {
        var eocd = apk.size - 22
        while (eocd >= 0 &&
            !(
                apk[eocd] == 0x50.toByte() && apk[eocd + 1] == 0x4B.toByte() &&
                    apk[eocd + 2] == 0x05.toByte() && apk[eocd + 3] == 0x06.toByte()
            )
        ) {
            eocd--
        }
        require(eocd >= 0) { "no EOCD" }
        val cdOffset = readU32(apk, eocd + 16)
        val cdSize = readU32(apk, eocd + 12)
        var pos = cdOffset
        while (pos < cdOffset + cdSize) {
            require(
                apk[pos] == 0x50.toByte() && apk[pos + 1] == 0x4B.toByte() &&
                    apk[pos + 2] == 0x01.toByte() && apk[pos + 3] == 0x02.toByte(),
            ) { "bad CEN" }
            val nameLen = readU16(apk, pos + 28)
            val extraLen = readU16(apk, pos + 30)
            val commentLen = readU16(apk, pos + 32)
            val name = String(apk, pos + 46, nameLen, Charsets.UTF_8)
            if (name == target) {
                val locOffset = readU32(apk, pos + 42)
                val locNameLen = readU16(apk, locOffset + 26)
                val locExtraLen = readU16(apk, locOffset + 28)
                return (locOffset + 30 + locNameLen + locExtraLen).toLong()
            }
            pos += 46 + nameLen + extraLen + commentLen
        }
        throw AssertionError("entry not found: $target")
    }

    private fun readU16(
        b: ByteArray,
        off: Int,
    ): Int = (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    private fun readU32(
        b: ByteArray,
        off: Int,
    ): Int = readU16(b, off) or (readU16(b, off + 2) shl 16)
}
