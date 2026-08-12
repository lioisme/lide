package com.lioisme.lide.build

/**
 * 打包依赖分析：工程 Lua 源码 → 需要的宿主 Lua 库与原生库（.so）候选。
 *
 * 语义对齐 AndroLua Pro `bin.lua` 的 `checklib`（require/import 命中 → 库名归组），
 * 并补两处超集（审计报告 §7 T2）：
 * - 传递闭包：命中宿主 Lua 模块后**递归**扫描其 require/import（pro 仅对 import
 *   命中递归、require 只记路径不递归，工程 `http.lua` 依赖链会断裂）；
 * - .so 推导：模块首段 → `lib<首段>.so`（`socket.core` → `libsocket.so`、
 *   `mime.core` → `libmime.so`），由装配阶段与宿主实际 `lib/<abi>/` 条目求交集。
 *
 * 纯 Kotlin 无 Android 依赖（宿主 Lua 源/工程文件由调用方提供），JVM 单测直测。
 */
class NativeDependencyAnalyzer(
    private val hostLua: Map<String, String>,
    private val projectOwned: Set<String>,
    private val hostNativeLibs: Set<String> = emptySet(),
) {
    /**
     * 对全部工程源码（含 .using 内容）做闭包扫描。
     *
     * 缺失模块判定：`socket.core` 这类由 .so 提供的原生模块，宿主有对应库时不算
     * 缺失（pro checklib 同口径）；纯 Lua 模块与原生候选都不存在才告警。
     */
    fun analyze(projectSources: Iterable<String>): DependencyAnalysis {
        val requiredLua = LinkedHashSet<String>()
        val nativeLibs = LinkedHashSet<String>()
        val missing = LinkedHashSet<String>()
        val visited = HashSet<String>()
        val queue = ArrayDeque<String>()

        fun enqueue(source: String) {
            if (visited.add(source)) queue.addLast(source)
        }

        for (src in projectSources) enqueue(src)
        while (queue.isNotEmpty()) {
            val src = queue.removeFirst()
            for (m in IMPORT_RE.findAll(src)) {
                val first = m.groupValues[1]
                val second = m.groupValues[2]
                // Lua 标准库（table/string/os/...）不查宿主、不推导 .so、不告警
                // （pro checklib 对不存在的 lib 名静默忽略，同口径）。
                if (first in LUA_STDLIB) continue
                val path = modulePath(first, second)
                if (path in projectOwned) continue
                val host = hostLua[path]
                if (host != null) {
                    // 宿主同名 Lua 模块提供服务（socket.lua ↔ 可能还有 socket.core 搭档），
                    // 闭包扫描其内部依赖；.so 候选由内部 require 原生模块时推导。
                    if (requiredLua.add(path)) enqueue(host)
                    continue
                }
                // 宿主无同名 Lua 模块 → 原生模块候选（socket.core → libsocket.so）。
                val native = nativeLibFor(first)
                nativeLibs += native
                if (native !in hostNativeLibs) missing += path
            }
        }

        return DependencyAnalysis(
            luaLibs =
                (requiredLua + ENGINE_LUA_WHITELIST)
                    .filter { it !in projectOwned && it in hostLua }
                    .toSet(),
            nativeLibs = nativeLibs,
            missingModules = missing.toList(),
        )
    }

    companion object {
        /**
         * Lua 5.3 标准库模块名：require/import 命中后不查宿主、不推导 .so、不告警。
         * C8（2026-08-12）：对照引擎 linit.c 实际注册表（loadedlibs + LUA_COMPAT_5_2）
         * 补齐 bit32（官方 5.3 lbitlib 经 luaopen_bit32 注册）；Lua 5.4 的 `bit`
         * 引擎未提供，不入表。
         */
        val LUA_STDLIB =
            setOf(
                "table",
                "string",
                "os",
                "io",
                "math",
                "coroutine",
                "debug",
                "utf8",
                "package",
                "bit32",
            )

        /** 打包必须保留的宿主引擎 Lua 库（import.lua 无条件加载、permission 权限提示、loadlayout 系布局）。 */
        val ENGINE_LUA_WHITELIST =
            setOf(
                "import.lua",
                "loadlayout.lua",
                "loadlayout2.lua",
                "loadlayout3.lua",
                "loadbitmap.lua",
                "loadmenu.lua",
                "permission.lua",
            )

        /** 与 pro checklib 同口径的引用正则：require/import 后可选括号 + 单/双引号模块名（至多两段）。 */
        val IMPORT_RE = Regex("(?:require|import)\\s*\\(?\\s*['\"]([A-Za-z0-9_]+)\\.?([A-Za-z0-9_]*)['\"]")

        /** 模块名 → 宿主 Lua 相对路径（`socket.http` → `socket/http.lua`）。 */
        fun modulePath(
            first: String,
            second: String,
        ): String = if (second.isEmpty()) "$first.lua" else "$first/$second.lua"

        /** 模块首段 → 原生库候选文件名（`socket.core` → `libsocket.so`；首段即 Lua 侧模块名）。 */
        fun nativeLibFor(moduleFirstSegment: String): String = "lib$moduleFirstSegment.so"
    }
}

/** 依赖分析结果（宿主视角，装配前中间产物）。 */
data class DependencyAnalysis(
    /** 需保留的宿主 Lua 库相对名（已过滤工程自有文件与宿主不存在项，含白名单）。 */
    val luaLibs: Set<String>,
    /** 需要的原生库候选文件名（`lib<name>.so`，未与宿主实际条目求交集）。 */
    val nativeLibs: Set<String>,
    /** 工程/宿主引用但宿主与工程均不存在的模块（相对路径记法）。 */
    val missingModules: List<String>,
)
