package com.lioisme.lide.build

import com.lioisme.lide.androlua.AXmlManifest
import com.lioisme.lide.androlua.LuaCompiler
import com.lioisme.lide.androlua.ManifestEdit
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
 * - 宿主引擎库（assets/lua 下）仅保留 [ProjectInput.engineLuaLibs] 命中的
 *   必需项（import/loadlayout/loadbitmap/loadmenu/permission + 工程 require/
 *   import 到的模块），杜绝整包携带。
 * - manifest：改包名 / label / 版本；uses-permission 白名单 = 工程声明 ∩ 宿主 +
 *   宿主自声明权限（自引用权限名随包名原位替换）。
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
        val mainFile: String = "main.lua",
    )

    class BuildException(
        message: String,
        cause: Throwable? = null,
    ) : Exception(message, cause)

    fun build(
        hostApk: ByteArray,
        input: ProjectInput,
        key: PrivateKey,
        cert: X509Certificate,
        compileLua: (source: String, chunkName: String) -> ByteArray = LuaCompiler::compile,
        onStage: (BuildStage) -> Unit = {},
    ): ByteArray {
        onStage(BuildStage.READ_HOST)
        val hostEntries = readZip(hostApk)
        val manifestEntry =
            hostEntries.firstOrNull { it.first == "AndroidManifest.xml" }
                ?: throw BuildException("宿主 APK 缺少 AndroidManifest.xml")
        val manifestBytes = manifestEntry.third

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
        val mainBytecode =
            try {
                compileLua(String(mainSource, Charsets.UTF_8), input.mainFile)
            } catch (e: Exception) {
                throw BuildException(e.message ?: "main.lua 编译失败", e)
            }

        onStage(BuildStage.ASSEMBLE)
        val out = ByteArrayOutputStream()
        val counter = ZipUtils.CountingOutputStream(out)
        ZipOutputStream(counter).use { zos ->
            val seen = HashSet<String>()
            // 工程文件注入的条目名（宿主同名单条目不写入，工程覆盖）。
            val projectEntryNames = input.luaSources.keys.mapTo(HashSet()) { "assets/lua/${it.replace('\\', '/')}" }
            val engineLuaSet = input.engineLuaLibs.toHashSet()
            for ((name, method, data) in hostEntries) {
                if (name.startsWith("META-INF/")) continue
                if (name == "AndroidManifest.xml") {
                    if (!seen.add(name)) continue
                    ZipUtils.writeEntry(zos, counter, name, method, patchedManifest)
                    continue
                }
                // 工程同名文件由工程注入覆盖（不占 seen，避免吞掉工程条目）。
                if (name.startsWith("assets/lua/") && name in projectEntryNames) continue
                // 宿主引擎 Lua 库仅保留白名单命中的必需项。
                if (name.startsWith("assets/lua/")) {
                    val rel = name.removePrefix("assets/lua/")
                    if (rel !in engineLuaSet) continue
                } else if (HOST_ASSET_EXCLUDED.any { name.startsWith(it) }) {
                    // 编辑器/宿主专属资源（示例、语法高亮、签名密钥、文档、插件、布局预览）
                    // 打包产物不读取，剔除以免膨胀。
                    continue
                }
                if (!seen.add(name)) continue
                ZipUtils.writeEntry(zos, counter, name, method, data)
            }
            // 注入工程文件（main.lua 为字节码；其余 .lua/.aly/资源原样）。
            for ((relPath, source) in input.luaSources) {
                if (relPath == input.mainFile) continue
                val entryName = "assets/lua/${relPath.replace('\\', '/')}"
                if (entryName in seen) continue
                ZipUtils.writeEntry(zos, counter, entryName, ZipEntry.DEFLATED, source)
            }
            val mainEntryName = "assets/lua/${input.mainFile}"
            if (mainEntryName !in seen) {
                ZipUtils.writeEntry(zos, counter, mainEntryName, ZipEntry.DEFLATED, mainBytecode)
            }
        }
        onStage(BuildStage.SIGN)
        return ApkSigner.sign(out.toByteArray(), key, cert)
    }

    private fun dedupe(list: List<String>): List<String> {
        val seen = HashSet<String>()
        return list.filter { seen.add(it) }
    }

    /** 打包产物不读取的宿主资产目录（编辑器/宿主专属，随宿主 APK 剔除）。 */
    private val HOST_ASSET_EXCLUDED =
        listOf(
            "assets/samples/",
            "assets/textmate/",
            "assets/keys/",
            "assets/luadoc/",
            "assets/plugin/",
            "assets/layouts/",
        )

    private fun readZip(apk: ByteArray): List<Triple<String, Int, ByteArray>> =
        ZipUtils.readEntries(apk, skipBlankNames = true)
}
