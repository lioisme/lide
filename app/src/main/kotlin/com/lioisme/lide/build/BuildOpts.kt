package com.lioisme.lide.build

import com.lioisme.lide.install.InstallerBackend

/** 打包成功后的产物处理方式。 */
enum class AfterBuild {
    /** 自动调起系统安装器（AndroLua Pro 兼容，默认）。 */
    INSTALL,

    /** 仅写盘保存，由用户手动分享/安装。 */
    SAVE,
    ;

    companion object {
        /** 持久化字符串 → 枚举；非法/空值回落默认 [INSTALL]（防偏好键残留）。 */
        fun parse(raw: String?): AfterBuild =
            try {
                if (raw.isNullOrEmpty()) INSTALL else valueOf(raw)
            } catch (_: IllegalArgumentException) {
                INSTALL
            }
    }
}

/**
 * APK 签名方案组合（v1 JAR / v2 / v3 块开关，全局打包选项）。
 *
 * 版本语义：v1 = 传统 JAR 签名（全版本可验，体积最大）；v2 = API 24+ 整包摘要
 * （本宿主 minSdk=24，必配）；v3 = API 28+（块内 minSdk 恒 ≥28，见 apksig
 * `processV3Configs`）。组合覆盖全部 7 种非空开关组合（全关 = 无签名产物，非法），
 * 按兼容面从宽到窄排序。
 */
enum class SigningScheme(
    /** 是否写入 v1（META-INF JAR 签名三件套）。 */
    val v1: Boolean,
    /** 是否写入 v2（APK Signing Block）。 */
    val v2: Boolean,
    /** 是否写入 v3（APK Signing Block v3，API 28+ 验证）。 */
    val v3: Boolean,
    /** 展示名（"v1 + v2 + v3" 风格，UI 直接使用）。 */
    val label: String,
) {
    V1V2V3(true, true, true, "v1 + v2 + v3"),
    V1V2(true, true, false, "v1 + v2"),
    V1V3(true, false, true, "v1 + v3"),
    V2V3(false, true, true, "v2 + v3"),
    V1(true, false, false, "v1"),
    V2(false, true, false, "v2"),
    V3(false, false, true, "v3"),
    ;

    companion object {
        /** 持久化字符串 → 枚举；非法/空值回落默认 [V1V2V3]（防偏好键残留）。 */
        fun parse(raw: String?): SigningScheme =
            try {
                raw?.let { valueOf(it) } ?: V1V2V3
            } catch (_: IllegalArgumentException) {
                V1V2V3
            }

        /** 开关组合 → 枚举；全关（无签名）返回 null，由 UI 层保证至少选中一项。 */
        fun fromFlags(v1: Boolean, v2: Boolean, v3: Boolean): SigningScheme? =
            entries.firstOrNull { it.v1 == v1 && it.v2 == v2 && it.v3 == v3 }
    }
}

/**
 * 打包通用选项（持久化于设置页「打包选项」；构建对话框读作初始值）。
 *
 * - [stripNativeLibs]：优化打包（默认开，仅针对原生库——只携带依赖命中的 .so，
 *   不影响 Lua 库）。
 * - [compileLua]：全部工程 .lua 编译为字节码、.aly 包装后编译并改名 .lua 入库
 *   （默认开，pro 同款）；关 = 全部以源码文本入库。
 * - [encryptBytecode]：仅 .lua/.aly 文件 DES 加密（默认开，pro 口径）；独立于编译——
 *   编译开加密字节码、编译关加密源码文本（运行端解密后按文本加载）；其余工程文件
 *   （图片/字体/数据）恒原样入库。
 * - [signingProfileId]：默认签名档案（空 = 内置调试钥）。
 * - [signingScheme]：签名方案组合（默认 v1+v2+v3 全兼容，与旧行为一致）。
 * - [preferAbi]：默认 ABI（空 = 宿主全部；设置页仅宿主携带的 ABI 可选）。
 * - [afterBuild]：产物输出方式（默认自动安装，pro 兼容）。
 * - [installBackend]：静默安装后端（默认系统安装器；仅显式选择且后端 READY 时静默，
 *   失败自动回落系统安装器，见 SILENT_INSTALL 工作单 D-2/D-3）。
 * - [outputDir]：产物输出目录（空 = 默认 `<luaExtDir>/apk/`；非空 = 绝对路径，不可写时回落默认）。
 * - [outputNaming]：产物文件名模板（空 = 默认 `<包名>-<版本>.apk`；支持 `{package}` / `{version}`
 *   占位符；结果经 sanitize，非法时回落默认）。
 */
data class BuildOpts(
    val stripNativeLibs: Boolean = true,
    val compileLua: Boolean = true,
    val encryptBytecode: Boolean = true,
    val signingProfileId: String = "",
    val signingScheme: SigningScheme = SigningScheme.V1V2V3,
    val preferAbi: String = "",
    val afterBuild: AfterBuild = AfterBuild.INSTALL,
    val installBackend: InstallerBackend = InstallerBackend.DEFAULT,
    val outputDir: String = "",
    val outputNaming: String = "",
)
