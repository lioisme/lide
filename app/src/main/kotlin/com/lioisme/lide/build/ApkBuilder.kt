package com.lioisme.lide.build

import com.lioisme.lide.model.Project
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
 *   编译开时全部 .lua 编译为字节码、.aly 包装后编译并改名 .lua 入库
 *   （pro bin.lua `gsub("aly$","lua")` 同款）；编译/加密范围仅限 .lua/.aly，
 *   其余文件（图片/字体/数据）恒原样入库（2026-08-09 pro 对齐）。
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
 * - 桌面图标（2026-08-09 P9 反转：LIDE 宿主自带图标）：宿主 manifest 声明
 *   android:icon="@drawable/icon"（res/drawable/icon.png，512×512 矢量渲染 PNG，
 *   宿主桌面图标 = 打包模板默认图标）。产物直接沿用模板的 icon 属性（原样保留、
 *   不重复注入）；模板无 icon 属性时才经 [ArscLookup] 解析宿主 arsc 的 drawable/icon
 *   资源 ID 兜底注入（TYPE_REFERENCE）。工程提供 icon.png 时覆盖产物
 *   res/drawable/icon.png 条目数据（arsc 以 TYPE_STRING 引用 res/ 文件路径，
 *   无需重写资源表），无工程图标时显示宿主图标，并保留 assets/lua/icon.png
 *   双份语义（引擎页运行时图标）。
 * - P0 运行时基座（dex 直用宿主，2026-08-09 源码组织三需求修订）：:androlua 以
 *   implementation 并入宿主单一 dex 集，不再有任何 dex 拆分/注入/标记。产物直接
 *   复用宿主根目录全量 classes*.dex（IDE 类 + 引擎类原样保留，接受多余内容）；
 *   manifest 无条件回落 `com.androlua.LuaApplication` 并移除 IDE MainActivity
 *   （引擎类恒随宿主 dex 进入产物，类均存在、无解析风险）。
 * - 按 [ProjectInput.signingScheme] 签名（v1/v2/v3 可选组合，[ApkSigner]），
 *   输出可直接 adb install 的 APK 字节。
 */
object ApkBuilder {
    /** 构建阶段（供 UI 进度提示；顺序即执行顺序）。 */
    enum class BuildStage {
        /** 读取宿主 APK 与签名材料。 */
        READ_HOST,

        /** 收集工程 Lua 源码与配置。 */
        COLLECT_LUA,

        /** 编译 Lua 文件（.lua 全部 + .aly 包装后）为字节码。 */
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
        /** true = 全部工程 .lua 编译为字节码、.aly 包装后编译并改名 .lua 入库；false = 全部以源码文本入库。 */
        val compileLua: Boolean = true,
        /** 编译/加密范围：仅 .lua/.aly 文件（[isLuaFile] 单一事实源），其余工程文件恒原样入库。 */
        val encryptBytecode: Boolean = true,
        /** 产物桌面图标入口（全限定类名）；默认引擎页，null 表示不干预。 */
        val launcherActivity: String? = DEFAULT_LAUNCHER_ACTIVITY,
        val mainFile: String = Project.DEFAULT_MAIN_FILE,
        /** 产物最低系统版本（UI1，2026-08-10）：null = 保持宿主（不写回 uses-sdk）。 */
        val minSdkVersion: Int? = null,
        /** 产物目标系统版本（P3，2026-08-11，pro `appsdk` 语义）：null = 保持宿主。 */
        val targetSdkVersion: Int? = null,
    )

    /**
     * 构建异常（T17，2026-08-12）：携带 [code] 供 UI 层映射本地化文案；
     * 默认 [BuildErrorCode.GENERAL]，历史抛点无感知兼容。
     */
    class BuildException(
        message: String,
        cause: Throwable? = null,
        val code: BuildErrorCode = BuildErrorCode.GENERAL,
    ) : Exception(message, cause)

    /**
     * 打包产物桌面图标入口：引擎页（无参数运行 filesDir/lua 工程）。
     * 产物 manifest 的 MAIN/LAUNCHER 会重定向至此（AXmlManifest.launcherActivity）。
     */
    const val DEFAULT_LAUNCHER_ACTIVITY = "com.lioisme.lide.engine.EngineActivity"

    /** 编译/加密范围判定（Q16 L8 收口）：仅 .lua/.aly（ignoreCase），其余工程文件恒原样入库。 */
    private fun isLuaFile(relPath: String): Boolean =
        relPath.endsWith(".lua", ignoreCase = true) || relPath.endsWith(".aly", ignoreCase = true)

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
     * 产物 Application（恒回落）：LuaApplication.extractLuaScripts 解包 assets/lua。
     * 2026-08-09 源码组织三需求：引擎类经 implementation 恒并入宿主 dex 集，
     * 不存在「无引擎」形态，不再需要 ENGINE_DEX_MARKER 存在性判定。
     */
    const val RUNTIME_APPLICATION = "com.androlua.LuaApplication"

    /** 产物整体移除的 IDE 主入口（宿主 applicationId = com.lioisme.lide）。 */
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
        val templateEntries = hostEntries
        val manifestEntry =
            templateEntries.firstOrNull { it.name == "AndroidManifest.xml" }
                ?: throw BuildException("模板 APK 缺少 AndroidManifest.xml", code = BuildErrorCode.TEMPLATE_MANIFEST_MISSING)
        val manifestBytes = manifestEntry.data

        // 模板原生库 ABI 枚举（lib/<abi>/...）：产物 ABI = 选择 ∩ 模板（保持原始顺序）。
        val hostAbis = ZipUtils.listAbis(templateEntries.map { it.name to 0L })
        val effectiveAbis =
            if (input.abiSelection == null) {
                hostAbis
            } else {
                input.abiSelection.filter { it in hostAbis }
            }
        if (input.abiSelection != null && effectiveAbis.isEmpty()) {
            throw BuildException(
                "宿主 APK 不包含所选 ABI（宿主: ${hostAbis.joinToString()}, 选择: ${input.abiSelection.joinToString()}）",
                code = BuildErrorCode.HOST_ABI_MISMATCH,
            )
        }
        val effectiveAbiSet = effectiveAbis.toHashSet()

        val hostPermissions =
            try {
                AXmlManifest.readUsesPermissions(manifestBytes).toSet()
            } catch (e: Exception) {
                throw BuildException("解析宿主 manifest 失败: ${e.message}", e, code = BuildErrorCode.HOST_MANIFEST_PARSE)
            }
        val selfDeclared =
            try {
                AXmlManifest.readSelfDeclaredPermissions(manifestBytes)
            } catch (e: Exception) {
                throw BuildException("解析宿主自声明权限失败: ${e.message}", e, code = BuildErrorCode.HOST_PERMISSIONS_PARSE)
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
                        // 引擎类恒随宿主全量 dex 进入产物：无条件回落引擎 Application 并
                        // 移除 IDE MainActivity（双桌面图标与 IDE 入口残留），类均存在无解析风险。
                        applicationName = RUNTIME_APPLICATION,
                        removeActivities = listOf(RUNTIME_REMOVED_ACTIVITY),
                        // 打包图标（2026-08-09 P9 反转：宿主自带 icon）：模板已声明
                        // android:icon="@drawable/icon"（宿主桌面图标 = 产物默认图标），
                        // AXmlManifest 对模板已有 icon 属性原样保留、不重复注入；
                        // applicationIconId 仅对模板无 icon 属性时兜底注入（TYPE_REFERENCE）。
                        // arsc 缺失/解析失败返回 null 即不注入（安全降级）。工程 icon.png
                        // 覆盖条目数据后 Launcher 即显示工程图标，无工程图标时显示宿主图标。
                        applicationIconId =
                            ArscLookup.drawableIconId(
                                templateEntries.firstOrNull { it.name == "resources.arsc" }?.data,
                            ),
                        // UI1（2026-08-10）：产物最低系统版本覆盖（null = 保持模板）。
                        minSdkVersion = input.minSdkVersion,
                        // P3（2026-08-11）：产物目标系统版本覆盖（pro appsdk 语义，null = 保持模板）。
                        targetSdkVersion = input.targetSdkVersion,
                    ),
                )
            } catch (e: Exception) {
                throw BuildException("manifest 补丁失败: ${e.message}", e, code = BuildErrorCode.MANIFEST_PATCH)
            }

        onStage(BuildStage.COLLECT_LUA)
        val mainSource =
            input.luaSources[input.mainFile]
                ?: throw BuildException("工程缺少 ${input.mainFile}", code = BuildErrorCode.PROJECT_MAIN_FILE_MISSING)
        onStage(BuildStage.COMPILE_LUA)

        // 工程桌面图标（可选）：工程根目录 icon.png（pro 同款约定），
        // 装配时覆盖宿主默认图标条目；未提供则用宿主图标。
        val projectIcon =
            input.luaSources.entries.firstOrNull { it.key.equals("icon.png", ignoreCase = true) }?.value
        // 图标条目名：经宿主 arsc 的 Res_value(TYPE_STRING) 引用解析真实路径
        // （资源混淆构建 = res/9T.png 等短名，字面量会落空；P11 实证 debug =
        // "res/drawable/icon.png"）；解析失败回落字面量（兼容 / 防御）。
        val iconEntryPath =
            ArscLookup.drawableIconPath(
                templateEntries.firstOrNull { it.name == "resources.arsc" }?.data,
            ) ?: "res/drawable/icon.png"

        // 编译与加密相互独立：compileLua=false 时全部文件（含 main.lua）以源码文本入库；
        // encryptBytecode 对入库内容（字节码或源码文本）统一 DES 加密（运行端 C 解密）。
        // 2026-08-09 pro 对齐：编译/加密范围仅限 .lua/.aly（[isLuaFile] 单一事实源）；
        // 其余工程文件（图片/字体/数据）一律原样入库——全量加密曾致图片资源损坏。
        // 返回（入库相对路径, 编码后内容）：.aly 编译时改名 .lua 入库（pro bin.lua
        // `gsub("aly$","lua")` 同款），改名后自然落入 .lua 加密范围。
        fun encodeRel(
            relPath: String,
            raw: ByteArray,
        ): Pair<String, ByteArray> {
            val isLuaSource = isLuaFile(relPath)
            val isAly = relPath.endsWith(".aly", ignoreCase = true)
            val content =
                if (input.compileLua && isLuaSource) {
                    try {
                        compileLua(String(raw, Charsets.UTF_8), relPath)
                    } catch (e: Exception) {
                        throw BuildException(e.message ?: "$relPath 编译失败", e, code = BuildErrorCode.LUA_COMPILE)
                    }
                } else if (isAly) {
                    // .aly 包装策略（pro console.build_aly 同款；2026-08-09 打包双开关对齐）：
                    // - 编译开：包装为返回 layout 表的 chunk 后编译字节码；
                    // - 编译关 + 加密开：包装文本入库——C 端 luaL_loadfilex 解密后直接
                    //   loadbufferx（无 "return " 前缀能力），裸表文本会语法错误；
                    // - 编译关 + 加密关：裸表文本原样入库（alyloader 文本分支按 "return "
                    //   前缀加载）。
                    val wrapped = "local layout=${String(raw, Charsets.UTF_8)}\nreturn layout"
                    if (input.compileLua) {
                        try {
                            compileLua(wrapped, relPath)
                        } catch (e: Exception) {
                            throw BuildException(e.message ?: "$relPath 编译失败", e, code = BuildErrorCode.LUA_COMPILE)
                        }
                    } else if (input.encryptBytecode) {
                        wrapped.toByteArray(Charsets.UTF_8)
                    } else {
                        raw
                    }
                } else {
                    raw
                }
            val encoded =
                if (input.encryptBytecode && (isLuaSource || isAly)) {
                    LuaCrypto.encrypt(content)
                } else {
                    content
                }
            val outRel =
                if (input.compileLua && isAly) {
                    // 移除 ".aly"/".ALY"（4 字符）后缀并改名为 .lua（pro gsub("aly$","lua") 同款）。
                    "${relPath.dropLast(4)}.lua"
                } else {
                    relPath
                }
            return outRel to encoded
        }

        val (mainOutRel, mainData) = encodeRel(input.mainFile, mainSource)

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
                // dex 直用宿主（2026-08-09）：宿主 classes*.dex 一律原样保留（RUNTIME_CORE），
                // 引擎类经 implementation 并入其中；旧 assets/engine.dex.idx 标记形态已删除。
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
                // 工程 icon.png 覆盖宿主默认桌面图标（pro bin.lua 同款：工程 icon.png →
                // 产物图标）。aapt2 36 的 arsc 对 PNG 以 TYPE_STRING 引用 res/ 文件路径
                // （不内嵌），替换 zip 条目数据即可生效、资源表零改动；目标条目名
                // 经 [iconEntryPath] 解析（混淆宿主为短路径），字面量仅作回退。
                // assets/lua/icon.png 由工程文件注入循环另行写入（引擎页运行时图标，
                // 双份语义同 pro）。
                val outData =
                    if (name == iconEntryPath && projectIcon != null) projectIcon else data
                if (!seen.add(name)) continue
                ZipUtils.writeEntry(zos, name, method, outData)
            }
            // 注入工程文件（依 compileLua/encryptBytecode 编码：编译或原样、可加密；
            // .aly 编译产物改名 .lua，同名冲突按 seen 先写者胜）。
            for ((relPath, source) in input.luaSources) {
                if (relPath == input.mainFile) continue
                val (outRel, data) = encodeRel(relPath, source)
                val entryName = "assets/lua/${outRel.replace('\\', '/')}"
                if (entryName in seen) continue
                ZipUtils.writeEntry(zos, entryName, ZipEntry.DEFLATED, data)
            }
            val mainEntryName = "assets/lua/${mainOutRel.replace('\\', '/')}"
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

    private fun readZip(apk: ByteArray): List<ZipUtils.ZipEntryData> = ZipUtils.readEntries(apk, skipBlankNames = true)
}
