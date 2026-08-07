package com.lioisme.lide.build

/**
 * 宿主 APK 条目领域分类（打包载荷唯一事实源）。
 *
 * 打包产物 = 宿主核心运行面 + 引擎 Lua 库 + 工程文件；宿主 IDE 专属内容
 * 一律不得进入产物。分类依据 = 「产物 APP 运行期是否会消费该能力」：
 *
 * | 领域 | 判定 | 产物处理 |
 * |---|---|---|
 * | MANIFEST | AndroidManifest.xml | 替换为 patched 版 |
 * | RUNTIME_CORE | classes 各 dex / resources.arsc / res 树 / META-INF 树 | 原样保留（签名后由 ApkSigner 重建 META-INF） |
 * | ENGINE_LUA | assets/lua 树 | 白名单 + 依赖闭包召回（[ApkBuilder] 过滤） |
 * | NATIVE_LIB | lib 树 | ABI 与依赖二维过滤（[ApkBuilder] 过滤） |
 * | HOST_ONLY | 其余任何 assets 树（兜底） | 默认剔除（default-deny） |
 *
 * 兜底规则对齐 AndroLua Pro `bin.lua` 对宿主 assets 整树剔除的语义：
 * 产物 APP 运行期仅解包 assets/lua 目录（LuaApplication.extractLuaScripts），
 * 其余资产（examples/、javaapi/、keys/、layouts/、textmate/ 等）均为
 * IDE 专属 → 不进入产物。新增宿主 IDE 资产无需改本表，天然不被打入
 * （漂移/漏配免疫）。
 */
object HostPolicy {
    /** 宿主条目领域。 */
    enum class HostEntryDomain {
        /** AndroidManifest.xml（由 [ApkBuilder] 替换为 patched 版）。 */
        MANIFEST,

        /** 运行核心面：dex / 资源表 / res / 签名元数据。 */
        RUNTIME_CORE,

        /** 引擎 Lua 库（assets/lua 树），按白名单 + 依赖闭包召回。 */
        ENGINE_LUA,

        /** 原生库（lib 树），按 ABI 与依赖过滤。 */
        NATIVE_LIB,

        /** 宿主 IDE 专属内容，默认剔除。 */
        HOST_ONLY,
    }

    /**
     * 宿主条目 → 领域分类（纯函数，幂等）。
     *
     * 规则优先级：manifest 精确命中 → assets/lua 前缀 → lib 前缀 →
     * 其余 assets 前缀（HOST_ONLY 兜底）→ 根级核心面。
     */
    fun classifyHostEntry(entryName: String): HostEntryDomain {
        if (entryName.isEmpty()) return HostEntryDomain.RUNTIME_CORE
        if (entryName == "AndroidManifest.xml") return HostEntryDomain.MANIFEST
        if (entryName.startsWith("assets/lua/")) return HostEntryDomain.ENGINE_LUA
        if (entryName.startsWith("lib/")) return HostEntryDomain.NATIVE_LIB
        if (entryName.startsWith("assets/")) return HostEntryDomain.HOST_ONLY
        return HostEntryDomain.RUNTIME_CORE
    }
}
