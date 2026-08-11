package com.lioisme.lide.build

/**
 * 打包错误码单一事实源（2026-08-12 T17 错误消息资源化）。
 *
 * 打包链（[ApkBuilder] 等）抛出 [BuildException] 时必须携带本枚举码；
 * UI 层（BuildApkDialog）按码映射 `build_error_*` 本地化文案展示——
 * 用户可见错误消息不直出硬编码中文/异常细节；未分类异常回落 [GENERAL]。
 *
 * 码 → 文案映射表见 BuildApkDialog.buildErrorDisplay（stringRes 中英双份）。
 */
enum class BuildErrorCode {
    /** 模板 APK 缺少 AndroidManifest.xml。 */
    TEMPLATE_MANIFEST_MISSING,

    /** 宿主 ABI 与所选 ABI 交集为空。 */
    HOST_ABI_MISMATCH,

    /** 宿主 manifest 二进制解析失败。 */
    HOST_MANIFEST_PARSE,

    /** 宿主自声明权限解析失败。 */
    HOST_PERMISSIONS_PARSE,

    /** manifest 补丁失败。 */
    MANIFEST_PATCH,

    /** 工程缺少入口 main 文件。 */
    PROJECT_MAIN_FILE_MISSING,

    /** Lua 编译失败（消息含编译器行号细节，UI 保留展示）。 */
    LUA_COMPILE,

    /** 未分类/未预料的构建失败。 */
    GENERAL,
}