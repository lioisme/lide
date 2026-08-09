package com.lioisme.lide.build

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    /** 以真实宿主 manifest 的 delua 变体（测试样本）为蓝本的合成宿主 APK。 */
    private fun syntheticHostApk(): ByteArray {
        val manifest = loadResource("manifest_sample_delua.bin")
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
            // 宿主默认桌面图标（2026-08-09 P9：宿主自身无图标，本条目为打包模板默认图标；
            // 产物经 ArscLookup 注入 android:icon 引用它，工程 icon.png 覆盖即替换本条目数据）。
            put("res/drawable/icon.png", "host-icon-png".toByteArray(), stored = true)
            put("assets/lua/bin.lua", "-- host bin".toByteArray())
            put("assets/lua/notused.lua", "-- host not used".toByteArray())
            put("assets/keys/testkey.pk8", "key-bytes".toByteArray())
            put("assets/textmate/lua.tmLanguage.json", "{}".toByteArray())
            // 宿主 IDE 专属资产（内置示例工程 / API 类表 / 布局预览）：不得进入产物。
            put("assets/examples/demo.alp", "fake alp payload".toByteArray())
            put("assets/examples/live2d.alp", "big fake alp payload".toByteArray())
            put("assets/javaapi/android.lua", "-- class table".toByteArray())
            put("assets/layouts/javaapi/layout.aly", "aly-bytes".toByteArray())
            // 原生库（双 ABI）：libluajava 恒保留 / compose 库默认剔除 / 其余按依赖。
            put("lib/x86_64/libfoo.so", "foo-x86".toByteArray(), stored = true)
            put("lib/x86_64/libluajava.so", "lj-x86".toByteArray(), stored = true)
            put("lib/x86_64/libandroidx.graphics.path.so", "gpath-x86".toByteArray(), stored = true)
            put("lib/arm64-v8a/libfoo.so", "foo-arm".toByteArray(), stored = true)
            put("lib/arm64-v8a/libmime.so", "mime-arm".toByteArray(), stored = true)
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
            requiredNativeLibs = setOf("libfoo.so"),
            // 测试样本 manifest 的引擎类（与真实宿主同构，delua 变体）。
            launcherActivity = "com.lioisme.delua.engine.EngineActivity",
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

    /** 真实 luajava 中 native（仅真机可用）；单测注入等价编译器。 */
    private fun fakeCompiler(): (String, String) -> ByteArray {
        val header = byteArrayOf(0x1B.toByte(), 0x4C, 0x75, 0x61) // \x1bLua
        return { source, _ ->
            require(source.isNotBlank()) { "empty source" }
            header + "FAKE".toByteArray()
        }
    }

    @Test
    fun `build produces installable v1-signed apk`() {
        val apk = ApkBuilder.build(syntheticHostApk(), input(), key, cert, compileLua = fakeCompiler())
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
        val apk = ApkBuilder.build(syntheticHostApk(), input(), key, cert, compileLua = fakeCompiler())
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
    fun `compileLua=true compiles all project lua to bytecode`() {
        // 隔离加密开关，只验证编译语义（新默认：全部编译）。
        val input = input().copy(encryptBytecode = false)
        val apk = ApkBuilder.build(syntheticHostApk(), input, key, cert, compileLua = fakeCompiler())
        val entries = entriesOf(apk)

        val luaHeader = byteArrayOf(0x1B.toByte(), 0x4C, 0x75, 0x61)
        assertArrayEquals(luaHeader, entries["assets/lua/main.lua"]!!.copyOfRange(0, 4))
        assertArrayEquals(luaHeader, entries["assets/lua/init.lua"]!!.copyOfRange(0, 4))
        assertArrayEquals(luaHeader, entries["assets/lua/lib/util.lua"]!!.copyOfRange(0, 4))
        // 宿主引擎库不经编译管线，保持宿主原始字节。
        assertArrayEquals("-- host bin".toByteArray(), entries["assets/lua/bin.lua"])
    }

    @Test
    fun `compileLua=false ships all project lua as plain source`() {
        val input = input().copy(compileLua = false, encryptBytecode = false)
        val apk = ApkBuilder.build(syntheticHostApk(), input, key, cert, compileLua = fakeCompiler())
        val entries = entriesOf(apk)

        assertArrayEquals("print('hello')\n".toByteArray(), entries["assets/lua/main.lua"])
        assertArrayEquals("-- config\nappname='Demo'\n".toByteArray(), entries["assets/lua/init.lua"])
        assertArrayEquals("-- helper\n".toByteArray(), entries["assets/lua/lib/util.lua"])
    }

    @Test
    fun `encryptBytecode independent of compileLua`() {
        // 编译开 + 加密开：加密的是字节码。
        val inputCompiled = input().copy(compileLua = true, encryptBytecode = true)
        val apkCompiled = ApkBuilder.build(syntheticHostApk(), inputCompiled, key, cert, compileLua = fakeCompiler())
        val mainEnc = entriesOf(apkCompiled)["assets/lua/main.lua"]!!
        assertTrue(LuaCrypto.isEncrypted(mainEnc))
        val luaHeader = byteArrayOf(0x1B.toByte(), 0x4C, 0x75, 0x61)
        assertArrayEquals(luaHeader, LuaCrypto.decrypt(mainEnc)!!.copyOfRange(0, 4))

        // 编译关 + 加密开：加密的是源码文本（运行端按文本加载，已实证）。
        val inputRaw = input().copy(compileLua = false, encryptBytecode = true)
        val apkRaw = ApkBuilder.build(syntheticHostApk(), inputRaw, key, cert, compileLua = fakeCompiler())
        val rawEntries = entriesOf(apkRaw)
        assertTrue(LuaCrypto.isEncrypted(rawEntries["assets/lua/main.lua"]!!))
        assertArrayEquals("print('hello')\n".toByteArray(), LuaCrypto.decrypt(rawEntries["assets/lua/main.lua"]!!)!!)
        assertArrayEquals("-- helper\n".toByteArray(), LuaCrypto.decrypt(rawEntries["assets/lua/lib/util.lua"]!!)!!)
    }

    @Test
    fun `host engine lua whitelisted and host-only assets excluded`() {
        val apk = ApkBuilder.build(syntheticHostApk(), input(), key, cert, compileLua = fakeCompiler())
        val entries = entriesOf(apk)

        // 白名单内引擎库保留；未列入的宿主引擎库剔除
        assertArrayEquals("-- host bin".toByteArray(), entries["assets/lua/bin.lua"])
        assertTrue("assets/lua/notused.lua" !in entries)
        // 宿主专属资产（签名密钥 语法高亮）不进入产物
        assertTrue("assets/keys/testkey.pk8" !in entries)
        assertTrue("assets/textmate/lua.tmLanguage.json" !in entries)
    }

    @Test
    fun `host ide-only assets like builtin examples and api class table excluded`() {
        val apk = ApkBuilder.build(syntheticHostApk(), input(), key, cert, compileLua = fakeCompiler())
        val entries = entriesOf(apk)

        // 内置示例工程（含大体积 live2d）不进入产物
        assertTrue("assets/examples/demo.alp" !in entries)
        assertTrue("assets/examples/live2d.alp" !in entries)
        // API 浏览器类表与布局预览模板（IDE 专属）不进入产物
        assertTrue("assets/javaapi/android.lua" !in entries)
        assertTrue("assets/layouts/javaapi/layout.aly" !in entries)
    }

    @Test
    fun `host policy classifies entries with default-deny for unknown assets`() {
        // 运行核心面与引擎能力保留
        assertEquals(HostPolicy.HostEntryDomain.MANIFEST, HostPolicy.classifyHostEntry("AndroidManifest.xml"))
        assertEquals(HostPolicy.HostEntryDomain.RUNTIME_CORE, HostPolicy.classifyHostEntry("classes.dex"))
        assertEquals(HostPolicy.HostEntryDomain.RUNTIME_CORE, HostPolicy.classifyHostEntry("resources.arsc"))
        assertEquals(HostPolicy.HostEntryDomain.RUNTIME_CORE, HostPolicy.classifyHostEntry("res/drawable/icon.png"))
        assertEquals(HostPolicy.HostEntryDomain.ENGINE_LUA, HostPolicy.classifyHostEntry("assets/lua/import.lua"))
        assertEquals(HostPolicy.HostEntryDomain.NATIVE_LIB, HostPolicy.classifyHostEntry("lib/arm64-v8a/libluajava.so"))
        // 任意未登记资产默认拒绝（未来新增 IDE 资产天然不被打入：
        assertEquals(HostPolicy.HostEntryDomain.HOST_ONLY, HostPolicy.classifyHostEntry("assets/examples/x.alp"))
        assertEquals(HostPolicy.HostEntryDomain.HOST_ONLY, HostPolicy.classifyHostEntry("assets/javaapi/android.lua"))
        assertEquals(HostPolicy.HostEntryDomain.HOST_ONLY, HostPolicy.classifyHostEntry("assets/unknown/newdir/x.txt"))
        assertEquals(HostPolicy.HostEntryDomain.HOST_ONLY, HostPolicy.classifyHostEntry("assets/help.txt"))
    }

    @Test
    fun `project files override same-name host engine lib`() {
        val input =
            input().copy(
                compileLua = false,
                encryptBytecode = false,
                engineLuaLibs = listOf("bin.lua"),
                luaSources =
                    input().luaSources.toMutableMap().apply {
                        this["bin.lua"] = "-- project bin".toByteArray()
                    },
            )
        val apk = ApkBuilder.build(syntheticHostApk(), input, key, cert, compileLua = fakeCompiler())
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
                compileLua = { _, _ -> throw IllegalArgumentException("main.lua:3: syntax error") },
            )
            assertTrue("should have thrown", false)
        } catch (e: ApkBuilder.BuildException) {
            assertTrue(e.message!!.isNotBlank())
        }
    }

    @Test
    fun `native libs filtered by abi x dependency`() {
        val apk = ApkBuilder.build(syntheticHostApk(), input(), key, cert, compileLua = fakeCompiler())
        val entries = entriesOf(apk)

        // 依赖命中的 libfoo.so：双 ABI 全保留
        assertArrayEquals("foo-x86".toByteArray(), entries["lib/x86_64/libfoo.so"])
        assertArrayEquals("foo-arm".toByteArray(), entries["lib/arm64-v8a/libfoo.so"])
        // libluajava 恒保留（bin.lua checklib 同款）
        assertArrayEquals("lj-x86".toByteArray(), entries["lib/x86_64/libluajava.so"])
        // compose 图形库默认剔除 + 未命中依赖的库剔除
        assertTrue("lib/x86_64/libandroidx.graphics.path.so" !in entries)
        assertTrue("lib/arm64-v8a/libmime.so" !in entries)
    }

    @Test
    fun `abi selection keeps only chosen abi`() {
        val input =
            input().copy(
                abiSelection = setOf("x86_64"),
            )
        val apk = ApkBuilder.build(syntheticHostApk(), input, key, cert, compileLua = fakeCompiler())
        val entries = entriesOf(apk)

        assertTrue("lib/x86_64/libfoo.so" in entries)
        assertTrue("lib/x86_64/libluajava.so" in entries)
        assertTrue("arm64-v8a 未选中应剔除", entries.keys.none { it.startsWith("lib/arm64-v8a/") })
    }

    @Test
    fun `invalid abi selection throws`() {
        val input =
            input().copy(
                abiSelection = setOf("mips64"),
            )
        try {
            ApkBuilder.build(syntheticHostApk(), input, key, cert, compileLua = fakeCompiler())
            assertTrue("should have thrown", false)
        } catch (e: ApkBuilder.BuildException) {
            assertTrue(e.message!!.contains("ABI"))
        }
    }

    @Test
    fun `stripNativeLibs=false keeps all host libs`() {
        val input =
            input().copy(
                requiredNativeLibs = emptySet(),
                stripNativeLibs = false,
            )
        val apk = ApkBuilder.build(syntheticHostApk(), input, key, cert, compileLua = fakeCompiler())
        val entries = entriesOf(apk)

        assertTrue("lib/x86_64/libandroidx.graphics.path.so" in entries)
        assertTrue("lib/arm64-v8a/libmime.so" in entries)
        assertTrue("lib/x86_64/libfoo.so" in entries)
    }

    @Test
    fun `launcher redirected to engine activity with exported true`() {
        val apk = ApkBuilder.build(syntheticHostApk(), input(), key, cert, compileLua = fakeCompiler())
        val manifest = entriesOf(apk)["AndroidManifest.xml"]!!

        assertEquals("com.lioisme.delua.engine.EngineActivity", AXmlManifest.readLauncherActivity(manifest))
        // 新入口 exported=true（API 31+ 有 intent-filter 必须显式 exported）
        assertEquals(true, AXmlManifest.readExported(manifest, "com.lioisme.delua.engine.EngineActivity"))
        // 旧入口（IDE MainActivity）不再持有 MAIN/LAUNCHER
        assertTrue(AXmlManifest.readLauncherActivity(manifest) != "com.lioisme.delua.MainActivity")
        // 旧入口其他 intent-filter（VIEW 等）不受影响
        assertEquals(true, AXmlManifest.readExported(manifest, "com.lioisme.delua.MainActivity"))
    }

    @Test
    fun `launcherActivity=null leaves launcher untouched`() {
        val input =
            input().copy(
                launcherActivity = null,
            )
        val apk = ApkBuilder.build(syntheticHostApk(), input, key, cert, compileLua = fakeCompiler())
        val manifest = entriesOf(apk)["AndroidManifest.xml"]!!

        assertEquals("com.lioisme.delua.MainActivity", AXmlManifest.readLauncherActivity(manifest))
    }

    @Test
    fun `stored entries are 4-byte aligned in final apk`() {
        val apk = ApkBuilder.build(syntheticHostApk(), input(), key, cert, compileLua = fakeCompiler())
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

    // ── P0 运行时基座（dex 直用宿主，2026-08-09 无条件形态：引擎并入单 dex 集）────────

    /** 宿主 APK 根目录含多份 dex + 旧标记资产（历史 injectEngineDex 产物形态，作回归输入）。 */
    private fun hostApkWithRuntimeDexAsset(): ByteArray {
        val manifest = loadResource("manifest_sample.bin")
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            fun put(
                name: String,
                bytes: ByteArray,
            ) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
            put("AndroidManifest.xml", manifest)
            // 宿主根目录全量 dex（引擎类已随 implementation 并入此集合）。
            put("classes.dex", "host-dex-full-ide".toByteArray())
            put("classes2.dex", "host-dex2-more-ide".toByteArray())
            put("classes3.dex", "engine-dex-only".toByteArray())
            // 旧引擎 dex 定位标记（历史 injectEngineDex 产物形态）：残留即回归信号。
            put("assets/engine.dex.idx", "classes3.dex".toByteArray())
            put("resources.arsc", "arsc-bytes".toByteArray())
            put("assets/lua/bin.lua", "-- host bin".toByteArray())
            put("assets/examples/demo.alp", "fake alp payload".toByteArray())
            put("lib/arm64-v8a/libluajava.so", "lj-host-arm".toByteArray())
            put("lib/x86_64/libluajava.so", "lj-host-x86".toByteArray())
        }
        return out.toByteArray()
    }

    @Test
    fun `host dex entries kept intact`() {
        // 2026-08-09：产物直用宿主全量根 dex（IDE 类 + 引擎类原样保留，接受多余内容）；
        // 无任何 dex 选取/替换逻辑，旧标记资产即便残留也被 default-deny 剔除。
        val apk = ApkBuilder.build(hostApkWithRuntimeDexAsset(), input(), key, cert, compileLua = fakeCompiler())
        val entries = entriesOf(apk)

        // 宿主 classes*.dex 全部原样保留（多 dex 无需重编号，ART 按序自动加载）。
        assertEquals("host-dex-full-ide", String(entries.getValue("classes.dex")))
        assertEquals("host-dex2-more-ide", String(entries.getValue("classes2.dex")))
        assertEquals("engine-dex-only", String(entries.getValue("classes3.dex")))
        // 引擎 Lua 库 / 原生库仍来自宿主（模板 = 宿主自身）。
        assertEquals("-- host bin", String(entries.getValue("assets/lua/bin.lua")))
        assertEquals("lj-host-arm", String(entries.getValue("lib/arm64-v8a/libluajava.so")))
        // 旧引擎 dex 标记属历史构建元数据，产物一律不带。
        assertTrue("assets/engine.dex.idx" !in entries)
        // 宿主 IDE 专属资产同样剔除（default-deny 语义延续）。
        assertTrue("assets/examples/demo.alp" !in entries)
    }

    @Test
    fun `dex entries kept and abi scope stays host-driven`() {
        // 模板 = 宿主：ABI 枚举/过滤仍由宿主实际条目驱动（D-1 冻结结论），dex 全量保留。
        val input =
            input().copy(
                abiSelection = setOf("x86_64"),
            )
        val apk = ApkBuilder.build(hostApkWithRuntimeDexAsset(), input, key, cert, compileLua = fakeCompiler())
        val entries = entriesOf(apk)
        assertEquals("host-dex-full-ide", String(entries.getValue("classes.dex")))
        assertEquals("host-dex2-more-ide", String(entries.getValue("classes2.dex")))
        assertEquals("engine-dex-only", String(entries.getValue("classes3.dex")))
        assertEquals("lj-host-x86", String(entries.getValue("lib/x86_64/libluajava.so")))
        assertTrue("lib/arm64-v8a/libluajava.so" !in entries)
    }

    @Test
    fun `manifest unconditionally falls back to runtime application`() {
        // 2026-08-09：引擎类恒随宿主 dex 集进入产物，无「无引擎」形态——
        // 宿主即便不含任何 dex 标记，manifest 也无条件回落引擎 Application
        // 并移除 IDE 主入口（类均存在，无解析风险）。
        val apk =
            ApkBuilder.build(
                hostApkWithRuntimeDexAsset(),
                input().copy(launcherActivity = "com.lioisme.lide.engine.EngineActivity"),
                key,
                cert,
                compileLua = fakeCompiler(),
            )
        val manifest = entriesOf(apk)["AndroidManifest.xml"]!!

        assertEquals("com.lioisme.lide.engine.EngineActivity", AXmlManifest.readLauncherActivity(manifest))
        assertTrue("LuaApplication 必须进入 manifest", "com.androlua.LuaApplication" in readPoolStrings(manifest))
        assertNull("MainActivity 必须整树移除", AXmlManifest.readExported(manifest, "com.lioisme.lide.MainActivity"))
    }

    // ── 2026-08-09 pro 打包对齐：加密范围 / .aly 编译 / 桌面图标 ──────────────

    @Test
    fun `only lua and aly files are encrypted`() {
        // 需求②核心回归：图片/数据等非 Lua 文件不得加密（历史缺陷：全量加密致图片损坏）。
        val iconBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4)
        val input =
            input().copy(
                luaSources =
                    input().luaSources.toMutableMap().apply {
                        this["icon.png"] = iconBytes
                        this["res/theme.txt"] = "plain data".toByteArray()
                    },
            )
        val apk = ApkBuilder.build(syntheticHostApk(), input, key, cert, compileLua = fakeCompiler())
        val entries = entriesOf(apk)

        assertArrayEquals(iconBytes, entries["assets/lua/icon.png"])
        assertArrayEquals("plain data".toByteArray(), entries["assets/lua/res/theme.txt"])
        // Lua 文件仍按开关加密
        assertTrue(LuaCrypto.isEncrypted(entries["assets/lua/main.lua"]!!))
    }

    @Test
    fun `aly compiled to bytecode and renamed to lua`() {
        val layout = "{TextView;layout_width='fill';}".toByteArray()
        val input =
            input().copy(
                compileLua = true,
                encryptBytecode = false,
                luaSources = input().luaSources.toMutableMap().apply { this["layout.aly"] = layout },
            )
        val apk = ApkBuilder.build(syntheticHostApk(), input, key, cert, compileLua = fakeCompiler())
        val entries = entriesOf(apk)

        val luaHeader = byteArrayOf(0x1B.toByte(), 0x4C, 0x75, 0x61)
        assertArrayEquals(luaHeader, entries["assets/lua/layout.lua"]!!.copyOfRange(0, 4))
        assertTrue("assets/lua/layout.aly" !in entries)
    }

    @Test
    fun `aly stays raw when both switches off`() {
        val layout = "{TextView;layout_width='fill';}".toByteArray()
        val input =
            input().copy(
                compileLua = false,
                encryptBytecode = false,
                luaSources = input().luaSources.toMutableMap().apply { this["layout.aly"] = layout },
            )
        val apk = ApkBuilder.build(syntheticHostApk(), input, key, cert, compileLua = fakeCompiler())
        val entries = entriesOf(apk)

        assertArrayEquals(layout, entries["assets/lua/layout.aly"])
    }

    @Test
    fun `aly encrypted as wrapped chunk text when compile off encrypt on`() {
        // C 端 luaL_loadfilex 解密后直接 loadbufferx（无 "return " 前缀能力），
        // 加密文本必须是可独立加载的包装 chunk，裸表文本会语法错误。
        val layout = "{TextView;}"
        val input =
            input().copy(
                compileLua = false,
                encryptBytecode = true,
                luaSources = input().luaSources.toMutableMap().apply { this["layout.aly"] = layout.toByteArray() },
            )
        val apk = ApkBuilder.build(syntheticHostApk(), input, key, cert, compileLua = fakeCompiler())
        val enc = entriesOf(apk)["assets/lua/layout.aly"]!!

        assertTrue(LuaCrypto.isEncrypted(enc))
        assertEquals("local layout=$layout\nreturn layout", String(LuaCrypto.decrypt(enc)!!, Charsets.UTF_8))
    }

    @Test
    fun `aly compiled then encrypted renamed to lua with LIDE magic`() {
        val input =
            input().copy(
                compileLua = true,
                encryptBytecode = true,
                luaSources = input().luaSources.toMutableMap().apply { this["layout.aly"] = "{TextView;}".toByteArray() },
            )
        val apk = ApkBuilder.build(syntheticHostApk(), input, key, cert, compileLua = fakeCompiler())
        val entries = entriesOf(apk)

        val enc = entries["assets/lua/layout.lua"]!!
        assertTrue(LuaCrypto.isEncrypted(enc))
        assertTrue("assets/lua/layout.aly" !in entries)
    }

    @Test
    fun `project icon overrides host icon entry with dual semantics`() {
        val projectIcon = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 9, 9, 9, 9)
        val input =
            input().copy(
                luaSources = input().luaSources.toMutableMap().apply { this["icon.png"] = projectIcon },
            )
        val apk = ApkBuilder.build(syntheticHostApk(), input, key, cert, compileLua = fakeCompiler())
        val entries = entriesOf(apk)

        // 桌面图标条目被工程图标替换（pro bin.lua 同款；arsc TYPE_STRING 引用该路径）
        assertArrayEquals(projectIcon, entries["res/drawable/icon.png"])
        // 双份语义：assets/lua/icon.png 同样注入（引擎页运行时图标）
        assertArrayEquals(projectIcon, entries["assets/lua/icon.png"])
    }

    @Test
    fun `host default icon kept when project has none`() {
        val apk = ApkBuilder.build(syntheticHostApk(), input(), key, cert, compileLua = fakeCompiler())
        val entries = entriesOf(apk)

        assertArrayEquals("host-icon-png".toByteArray(), entries["res/drawable/icon.png"])
    }

    /** 解析 AXML 字符串池（UTF-16/UTF-8 双形态），供 manifest 内容断言。 */
    private fun readPoolStrings(axml: ByteArray): List<String> {
        val out = mutableListOf<String>()
        var pos = 8
        while (pos < axml.size) {
            val type = readU16(axml, pos)
            val size = readU32(axml, pos + 4).toInt()
            if (size <= 0 || pos + size > axml.size) break
            if (type == 0x0001) {
                val stringCount = readU32(axml, pos + 8).toInt()
                val flags = readU32(axml, pos + 16).toInt()
                val utf8 = flags and 0x100 != 0
                val stringsStart = pos + readU32(axml, pos + 20).toInt()
                val offsets = (0 until stringCount).map { readU32(axml, pos + 28 + 4 * it).toInt() }
                for (o in offsets) {
                    var p = stringsStart + o
                    val strLen =
                        if (utf8) {
                            val b = axml[p].toInt() and 0xFF
                            p += if (b and 0x80 != 0) 2 else 1
                            b and 0x7F
                        } else {
                            val l = (axml[p].toInt() and 0xFF) or ((axml[p + 1].toInt() and 0xFF) shl 8)
                            p += 2
                            l
                        }
                    val bytes =
                        if (utf8) {
                            val b = axml[p].toInt() and 0xFF
                            p += if (b and 0x80 != 0) 2 else 1
                            axml.copyOfRange(p, p + strLen)
                        } else {
                            val arr = ByteArray(strLen * 2)
                            for (k in 0 until strLen) {
                                arr[k * 2] = axml[p + k * 2]
                                arr[k * 2 + 1] = axml[p + k * 2 + 1]
                            }
                            arr
                        }
                    out += String(bytes, if (utf8) Charsets.UTF_8 else Charsets.UTF_16LE)
                }
                break
            }
            pos += size
        }
        return out
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
