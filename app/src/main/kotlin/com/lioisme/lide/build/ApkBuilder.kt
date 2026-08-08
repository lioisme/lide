package com.lioisme.lide.build

import java.io.ByteArrayOutputStream
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 打包装配器：宿主 APK + 工程文件 → 独立可安装 APK。
 *
 * - 复制宿主条目（排除签名与宿主引擎 Lua 库），注入 `assets/lua/<相对路径>`：
 *   工程目录全部文件（res、libs、.aly、图片等）原样打入（引擎打包模式
 *   以 filesDir/lua 为工程目录，运行时经 luaLpath/libs 目录读取）；
 *   main.lua 编译为字节码，其余 .lua/.aly 保持文本（LloadFile 与 loadlayout
 *   alyloader 均支持文本）。
 * - 宿主条目按 [HostPolicy] 领域分类（默认拒绝）：仅保留运行核心面
 *   （dex/res/arsc）、白名单命中的引擎 Lua 库与 ABI×依赖命中的原生库；
 *   其余宿主 IDE 专属内容（示例工程、API 类表、签名密钥、语法高亮、
 *   布局预览等）一律剔除——对齐 pro bin.lua 的 `^assets/` 整树剔除语义。
 * - 宿主引擎库（assets/lua 下）仅保留 [ProjectInput.engineLuaLibs] 命中的
 *   必需项（import/loadlayout/loadbitmap/loadmenu/permission + 工程 require/
 *   import 到的模块），杜绝整包携带。
 * - 原生库按「ABI × 依赖」二维过滤：ABI 取 [ProjectInput.abiSelection] 与宿主
 *   实际条目交集；库名取 [ProjectInput.requiredNativeLibs] 与依赖分析推导结果
 *   交集，libluajava.so 恒保留、Compose 图形库默认剔除（[ProjectInput.stripNativeLibs]
 *   为 false 时全量携带，pro 行为）。
 * - manifest：改包名 / label / 版本；uses-permission 白名单 = 工程声明 ∩ 宿主 +
 *   宿主自声明权限（自引用权限名随包名原位替换）；桌面图标入口重定向到引擎页
 *   （[DEFAULT_LAUNCHER_ACTIVITY]，点开直接运行工程）。
 * - P0 运行时基座（dex 直用宿主，2026-08-08 P0-7 修订）：不再为打包单独提供 dex——
 *   产物直接复用宿主根目录全量 classes*.dex（IDE 类 + 引擎类原样保留，接受多余
 *   内容）；`assets/engine.dex.idx` 标记仅作引擎存在性判定，命中时 manifest 回落
 *   `com.androlua.LuaApplication` 并移除 IDE MainActivity（引擎类随宿主 dex 全量
 *   进入产物，回落后类均存在、无解析风险）；未命中时保持宿主 manifest（pro 模板
 *   语义，兼容 embedRuntimeDex=false 的瘦身构建）。
 * - v1 + v2 签名（[ApkSigner]），输出可直接 adb install 的 APK 字节。
 */
object ApkBuilder {
    /** 构建阶段（供 UI 进度提示；顺序即执行顺序）。 */
    enum class BuildStage {
        /** 读取宿主 APK 与签名材料。 */
        READ_HOST,

        /** 收集工程 Lua 源码与配置。 */
        COLLECT_LUA,

        /** 编译 main.lua 为字节码。 */
        COMPILE_LUA,

        /** 装配 APK（manifest 补丁 + 条目重写 + 注入工程 Lua）。 */
        ASSEMBLE,

        /** v1 + v2 签名。 */
        SIGN,
    }

    /** 工程侧输入：版本与权限取自 init.lua 解析结果，luaSources 为相对路径→源码。 */
    data class ProjectInput(
        val packageName: String,
        val appName: String,
        val versionName: String,
        val versionCode: Int,
        val declaredPermissions: List<String>,
        /** 工程目录全部文件：相对路径（含子目录）→ 内容，注入 assets/lua/<路径>。 */
        val luaSources: Map<String, ByteArray>,
        /** 需保留的宿主引擎 Lua 库相对名（assets/lua/ 下，如 "import.lua"、"socket/http.lua"）。 */
        val engineLuaLibs: List<String> = emptyList(),
        /** 需要的原生库候选文件名（"libsocket.so" 等，由依赖分析推导，与宿主实际条目求交集）。 */
        val requiredNativeLibs: Set<String> = emptySet(),
        /** 产物包含的 ABI 子集；null = 宿主全部 ABI。空交集时构建失败。 */
        val abiSelection: Set<String>? = null,
        /** true = 按需携带原生库（默认）；false = 全量携带宿主各 ABI 原生库（pro 行为）。 */
        val stripNativeLibs: Boolean = true,
        /** true = 全部工程 .lua（含 [mainFile]）编译为字节码；false = 全部以源码文本入库。 */
        val compileLua: Boolean = true,
        /** Lua 文件加密（DES，见 [LuaCrypto]）；独立于编译：编译开加密字节码、编译关加密源码文本。 */
        val encryptBytecode: Boolean = true,
        /** 产物桌面图标入口（全限定类名）；默认引擎页，null 表示不干预。 */
        val launcherActivity: String? = DEFAULT_LAUNCHER_ACTIVITY,
        val mainFile: String = "main.lua",
    )

    class BuildException(
        message: String,
        cause: Throwable? = null,
    ) : Exception(message, cause)

    /**
     * 打包产物桌面图标入口：引擎页（无参数运行 filesDir/lua 工程）。
     * 产物 manifest 的 MAIN/LAUNCHER 会重定向至此（AXmlManifest.launcherActivity）。
     */
    const val DEFAULT_LAUNCHER_ACTIVITY = "com.lioisme.lide.engine.EngineActivity"

    /** 原生库恒保留项：引擎核心（bin.lua checklib 同款强制包含）。 */
    const val ALWAYS_KEEP_NATIVE_LIB = "libluajava.so"

    /**
     * 原生库默认剔除项：Compose 图形 JNI（打包产物仅启动 EngineActivity，
     * 全程不实例化 Compose 视图，类不会加载该库；剔除省 ~10KB/ABI）。
     */
    val ALWAYS_EXCLUDE_NATIVE_LIBS = setOf("libandroidx.graphics.path.so")

    /**
     * 单个原生库文件的保留判定（纯函数）：UI 摘要估算与装配过滤共用同一口径。
     * libluajava.so 恒保留；Compose 库默认剔除；其余仅保留依赖推导命中项。
     */
    fun nativeLibKept(
        libFile: String,
        requiredNativeLibs: Set<String>,
        stripNativeLibs: Boolean,
    ): Boolean {
        if (libFile.isEmpty()) return false
        if (!stripNativeLibs) return true
        if (libFile == ALWAYS_KEEP_NATIVE_LIB) return true
        if (libFile in ALWAYS_EXCLUDE_NATIVE_LIBS) return false
        return libFile in requiredNativeLibs
    }

    /**
     * 引擎 dex 定位标记资产：宿主 APK `assets/engine.dex.idx`（非 dex，内容为引擎 dex
     * 在宿主 APK 根目录的条目名，如 `classes31.dex`，由构建期 injectEngineDex 写入）。
     * 契约（引擎 dex 单份化，2026-08-08）：宿主根目录 classes*.dex 仅含 IDE 代码
     * （:androlua 为 compileOnly，引擎类不进宿主 dex），引擎 dex 唯一一份在根目录。
     * **P0-7 修订（2026-08-08）**：打包器不再据此选取 dex（产物直用宿主全量根 dex，
     * 不再做引擎单 dex 替换），标记仅作**引擎存在性判定**——命中合法条目时 manifest
     * 回落 `com.androlua.LuaApplication` 并移除 IDE MainActivity；未命中保持宿主
     * manifest（pro 模板语义，兼容 embedRuntimeDex=false 瘦身构建）。标记为宿主构建
     * 元数据，任何产物一律不带。旧 `assets/runtime/classes.dex` 内嵌形态已废弃。
     */
    const val ENGINE_DEX_MARKER = "assets/engine.dex.idx"

    /** 引擎 dex 存在时产物 Application：LuaApplication.extractLuaScripts 解包 assets/lua。 */
    const val RUNTIME_APPLICATION = "com.androlua.LuaApplication"

    /** 引擎 dex 存在时整体移除的 IDE 主入口（宿主 applicationId = com.lioisme.lide）。 */
    const val RUNTIME_REMOVED_ACTIVITY = "com.lioisme.lide.MainActivity"

    fun build(
        hostApk: ByteArray,
        input: ProjectInput,
        key: PrivateKey,
        cert: X509Certificate,
        signingScheme: SigningScheme = SigningScheme.V1V2V3,
        compileLua: (source: String, chunkName: String) -> ByteArray = LuaCompiler::compile,
        onStage: (BuildStage) -> Unit = {},
    ): ByteArray {
        onStage(BuildStage.READ_HOST)
        val hostEntries = readZip(hostApk)
        // 引擎存在性判定（P0-7：不再据此选取 dex，产物直用宿主全量根 dex）：
        // 标记内容须匹配 classes\d*\.dex（防任意路径注入）且根目录存在该条目。
        val enginePresent =
            hostEntries.firstOrNull { it.first == ENGINE_DEX_MARKER }
                ?.third
                ?.toString(Charsets.UTF_8)
                ?.trim()
                ?.takeIf { it.matches(Regex("classes\\d*\\.dex")) }
                ?.let { name -> hostEntries.any { it.first == name } }
                ?: false
        val templateEntries = hostEntries
        val manifestEntry =
            templateEntries.firstOrNull { it.first == "AndroidManifest.xml" }
                ?: throw BuildException("模板 APK 缺少 AndroidManifest.xml")
        val manifestBytes = manifestEntry.third

        // 模板原生库 ABI 枚举（lib/<abi>/...）：产物 ABI = 选择 ∩ 模板（保持原始顺序）。
        val hostAbis = ZipUtils.listAbis(templateEntries.map { it.first to 0L })
        val effectiveAbis =
            if (input.abiSelection == null) {
                hostAbis
            } else {
                input.abiSelection.filter { it in hostAbis }
            }
        if (input.abiSelection != null && effectiveAbis.isEmpty()) {
            throw BuildException(
                "宿主 APK 不包含所选 ABI（宿主: ${hostAbis.joinToString()}, 选择: ${input.abiSelection.joinToString()}）",
            )
        }
        val effectiveAbiSet = effectiveAbis.toHashSet()

        val hostPermissions =
            try {
                AXmlManifest.readUsesPermissions(manifestBytes).toSet()
            } catch (e: Exception) {
                throw BuildException("解析宿主 manifest 失败: ${e.message}", e)
            }
        val selfDeclared =
            try {
                AXmlManifest.readSelfDeclaredPermissions(manifestBytes)
            } catch (e: Exception) {
                throw BuildException("解析宿主自声明权限失败: ${e.message}", e)
            }
        val whitelist =
            dedupe(
                input.declaredPermissions.filter { it in hostPermissions } + selfDeclared,
            )

        val patchedManifest =
            try {
                AXmlManifest.patch(
                    manifestBytes,
                    ManifestEdit(
                        packageName = input.packageName,
                        appLabel = input.appName,
                        versionCode = input.versionCode,
                        versionName = input.versionName,
                        usesPermissions = whitelist,
                        // 产物图标入口重定向到引擎页（点开直接运行工程，而非宿主 IDE）。
                        launcherActivity = input.launcherActivity,
                        // 引擎 dex 随宿主全量 dex 进入产物：回落引擎 Application 并移除
                        // IDE MainActivity（双桌面图标与 IDE 入口残留），类均存在无解析风险。
                        applicationName = if (enginePresent) RUNTIME_APPLICATION else null,
                        removeActivities = if (enginePresent) listOf(RUNTIME_REMOVED_ACTIVITY) else emptyList(),
                    ),
                )
            } catch (e: Exception) {
                throw BuildException("manifest 补丁失败: ${e.message}", e)
            }

        onStage(BuildStage.COLLECT_LUA)
        val mainSource =
            input.luaSources[input.mainFile]
                ?: throw BuildException("工程缺少 ${input.mainFile}")
        onStage(BuildStage.COMPILE_LUA)

        // 编译与加密相互独立：compileLua=false 时全部文件（含 main.lua）以源码文本入库；
        // encryptBytecode 对入库内容（字节码或源码文本）统一 DES 加密（运行端 C 解密）。
        fun encodeRel(
            relPath: String,
            raw: ByteArray,
        ): ByteArray {
            val content =
                if (input.compileLua && relPath.endsWith(".lua", ignoreCase = true)) {
                    try {
                        compileLua(String(raw, Charsets.UTF_8), relPath)
                    } catch (e: Exception) {
                        throw BuildException(e.message ?: "$relPath 编译失败", e)
                    }
                } else {
                    raw
                }
            return if (input.encryptBytecode) LuaCrypto.encrypt(content) else content
        }

        val mainData = encodeRel(input.mainFile, mainSource)

        onStage(BuildStage.ASSEMBLE)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            val seen = HashSet<String>()
            // 工程文件注入的条目名（宿主同名单条目不写入，工程覆盖）。
            val projectEntryNames = input.luaSources.keys.mapTo(HashSet()) { "assets/lua/${it.replace('\\', '/')}" }
            val engineLuaSet = input.engineLuaLibs.toHashSet()
            for ((name, method, data) in templateEntries) {
                if (name.startsWith("META-INF/")) continue
                if (name == "AndroidManifest.xml") {
                    if (!seen.add(name)) continue
                    ZipUtils.writeEntry(zos, name, method, patchedManifest)
                    continue
                }
                // dex 直用宿主（P0-7）：宿主 classes*.dex 一律原样保留（RUNTIME_CORE）；
                // 定位标记（assets/engine.dex.idx）为宿主构建元数据，任何产物一律不带。
                if (name == ENGINE_DEX_MARKER) {
                    continue
                }
                // 宿主条目按领域分类（HostPolicy 唯一事实源，default-deny）：
                // 工程同名文件由工程注入覆盖（不占 seen，避免吞掉工程条目）。
                when (HostPolicy.classifyHostEntry(name)) {
                    HostPolicy.HostEntryDomain.ENGINE_LUA -> {
                        if (name in projectEntryNames) continue
                        // 宿主引擎 Lua 库仅保留白名单命中的必需项。
                        val rel = name.removePrefix("assets/lua/")
                        if (rel !in engineLuaSet) continue
                    }

                    HostPolicy.HostEntryDomain.NATIVE_LIB -> {
                        // 原生库二维过滤：ABI × 库名（判定统一走 nativeLibKept）。
                        val parts = name.split('/')
                        val abi = parts.getOrNull(1)
                        val libFile = parts.getOrNull(2)
                        if (abi == null || abi !in effectiveAbiSet) continue
                        if (input.stripNativeLibs &&
                            !nativeLibKept(libFile ?: "", input.requiredNativeLibs, stripNativeLibs = true)
                        ) {
                            continue
                        }
                    }

                    // 宿主 IDE 专属内容（示例工程、API 类表、签名密钥、语法高亮、布局预览）
                    // 产物 APP 运行期不消费（仅解包 assets/lua/**），默认剔除以免膨胀。
                    HostPolicy.HostEntryDomain.HOST_ONLY -> {
                        continue
                    }

                    else -> {
                    }
                }
                if (!seen.add(name)) continue
                ZipUtils.writeEntry(zos, name, method, data)
            }
            // 注入工程文件（依 compileLua/encryptBytecode 编码：编译或原样、可加密）。
            for ((relPath, source) in input.luaSources) {
                if (relPath == input.mainFile) continue
                val entryName = "assets/lua/${relPath.replace('\\', '/')}"
                if (entryName in seen) continue
                val data = encodeRel(relPath, source)
                ZipUtils.writeEntry(zos, entryName, ZipEntry.DEFLATED, data)
            }
            val mainEntryName = "assets/lua/${input.mainFile}"
            if (mainEntryName !in seen) {
                ZipUtils.writeEntry(zos, mainEntryName, ZipEntry.DEFLATED, mainData)
            }
        }
        onStage(BuildStage.SIGN)
        return ApkSigner.sign(out.toByteArray(), key, cert, signingScheme)
    }

    private fun dedupe(list: List<String>): List<String> {
        val seen = HashSet<String>()
        return list.filter { seen.add(it) }
    }

    private fun readZip(apk: ByteArray): List<Triple<String, Int, ByteArray>> = ZipUtils.readEntries(apk, skipBlankNames = true)
}
