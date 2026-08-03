package com.lioisme.delua.apibrowser

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import dalvik.system.DexFile
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * API 索引：静态类表（assets/javaapi/android.lua，pro 类表平移 + 扩展）
 * + 宿主 dex 动态枚举（androidx/material）+ 工程 libs/ 下 dex|jar 动态枚举
 * + 原生库模块名扫描（libs/ 下 so，仅模块名级）。
 *
 * 线程模型：build() 在 IO 线程整体构建，产物为不可变快照；反射验证带并发缓存。
 * 全部反射路径 try/catch(Throwable) 降级（AGENTS.md 约定）。
 */
object ApiIndex {

    private const val ASSET_CLASS_TABLE = "javaapi/android.lua"

    /** 引擎内置 C 模块（cpath = nativeLibraryDir/lib?.so + 工程 libs）。 */
    private val builtinNativeModules = listOf(
        "bson", "canvas", "cjson", "crypt", "ftp", "gl", "http", "import",
        "md5", "mime", "regex", "sensor", "smtp", "socket", "xml", "zip", "zlib", "luv",
    )

    /**
     * 宿主 dex 枚举的纳入前缀：静态类表缺口的 AndroidX/Material 类自动补齐。
     * 前缀过滤 + 跳过内部类（$），控制条目量与噪音。
     */
    private val hostDexPrefixes = listOf(
        "androidx.", "com.google.android.material.",
    )

    /** 不可变快照。 */
    data class Snapshot(
        val classes: List<ApiClassEntry>,
        val nativeModules: List<String>,
    ) {
        val categoryCounts: Map<ApiCategory, Int> by lazy {
            classes.groupingBy { it.category }.eachCount()
        }
    }

    data class ApiClassEntry(
        val name: String,
        val category: ApiCategory,
        /** 来自工程 libs/ 下 dex|jar 动态枚举。 */
        val fromProject: Boolean,
    )

    @Volatile
    private var snapshot: Snapshot? = null

    /** 反射验证缓存：类名 → 是否可加载。失败负缓存，防重复探活。 */
    private val loadableCache = ConcurrentHashMap<String, Boolean>()

    private val viewAssignCache = ConcurrentHashMap<String, Boolean>()

    /** 已构建过的工程根集合签名（工程增减导致索引失效）。 */
    private var builtProjectRoots: Set<String>? = null

    @Volatile
    private var hostClassLoader: ClassLoader? = null

    fun cached(): Snapshot? = snapshot

    /**
     * 构建索引。进程级缓存：工程根未变化时复用快照。
     * 必须在 IO 线程调用。
     */
    @SuppressLint("WrongThread")
    fun build(context: Context, projectRoots: List<File>): Snapshot {
        val roots = projectRoots.map { it.absolutePath }.toSet()
        hostClassLoader = context.classLoader
        if (snapshot != null && builtProjectRoots == roots) {
            return snapshot!!
        }
        val start = System.currentTimeMillis()
        val entries = LinkedHashMap<String, ApiClassEntry>()
        val nativeModules = LinkedHashSet<String>()
        nativeModules += builtinNativeModules

        // 1) 静态类表（pro 平移 + material/androidx 精选扩展）
        val staticNames = parseClassTable(context)
        for (name in staticNames) {
            entries.putIfAbsent(name, ApiClassEntry(name, ApiCategoryClassifier.categorize(name), fromProject = false))
        }

        // 2) 宿主 dex 枚举（androidx/material 动态补齐）
        enumerateDexFile(File(context.applicationInfo.sourceDir)) { name ->
            if (hostDexPrefixes.any { name.startsWith(it) } && '$' !in name) {
                entries.putIfAbsent(name, ApiClassEntry(name, ApiCategoryClassifier.categorize(name), fromProject = false))
            }
        }

        // 3) 工程 libs/ 下 dex|jar 枚举（真·自动识别）
        for (root in roots) {
            val libsDir = File(root, "libs")
            val libFiles = libsDir.listFiles { f -> f.isFile && (f.name.endsWith(".dex") || f.name.endsWith(".jar")) }
                ?: continue
            for (lib in libFiles) {
                enumerateDexFile(lib) { name ->
                    if ('$' !in name) {
                        entries.putIfAbsent(name, ApiClassEntry(name, ApiCategory.PROJECT, fromProject = true))
                    }
                }
            }
            // 原生库：libs/lib*.so → 模块名（仅模块名级识别，函数级不可静态获取）
            val soFiles = libsDir.listFiles { f -> f.isFile && f.name.startsWith("lib") && f.name.endsWith(".so") }
                ?: continue
            for (so in soFiles) {
                nativeModules += so.name.removePrefix("lib").removeSuffix(".so")
            }
        }

        // 4) R 资源类自动补齐：android.R + 宿主 R 的内部类（反射枚举）
        for (r in listOf("android.R", "com.lioisme.delua.R")) {
            try {
                val clazz = Class.forName(r, false, context.classLoader)
                for (inner in clazz.classes) {
                    val n = "${clazz.name}$${inner.simpleName}"
                    entries.putIfAbsent(n, ApiClassEntry(n, ApiCategory.R_RESOURCES, fromProject = false))
                }
                entries.putIfAbsent(r, ApiClassEntry(r, ApiCategory.R_RESOURCES, fromProject = false))
            } catch (_: Throwable) {
            }
        }

        snapshot = Snapshot(
            classes = entries.values.toList(),
            nativeModules = nativeModules.toList().sorted(),
        )
        builtProjectRoots = roots
        android.util.Log.d("ApiIndex", "built ${entries.size} classes, ${nativeModules.size} modules in ${System.currentTimeMillis() - start}ms")
        return snapshot!!
    }

    /** 使索引失效（工程 libs 变化后由宿主显式调用）。 */
    fun invalidate() {
        snapshot = null
        builtProjectRoots = null
    }

    /** 解析 assets/javaapi/android.lua 类表（return { "类名", ... }）。 */
    private fun parseClassTable(context: Context): List<String> {
        val result = ArrayList<String>()
        try {
            val text = context.assets.open(ASSET_CLASS_TABLE).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val regex = Regex("\"([^\"]+)\"")
            for (match in regex.findAll(text)) {
                val name = match.groupValues[1]
                if (name.isNotBlank()) result.add(name)
            }
        } catch (_: Throwable) {
        }
        return result
    }

    /** 枚举 dex 文件中全部类名（API 26+ deprecated 但功能保留，异常降级）。 */
    private fun enumerateDexFile(file: File, sink: (String) -> Unit) {
        if (!file.exists() || file.length() == 0L) return
        val dex = try {
            DexFile(file)
        } catch (_: Throwable) {
            return
        }
        try {
            val entries = dex.entries() ?: return
            while (entries.hasMoreElements()) {
                sink(entries.nextElement())
            }
        } catch (_: Throwable) {
        } finally {
            try {
                dex.close()
            } catch (_: Throwable) {
            }
        }
    }

    /** 类是否可加载（惰性缓存）。 */
    fun isLoadable(name: String): Boolean {
        return loadableCache.getOrPut(name) {
            try {
                Class.forName(name, false, hostClassLoader ?: ApiIndex::class.java.classLoader) != null
            } catch (_: Throwable) {
                false
            }
        }
    }

    /** 是否为 View 子类（反射验证，带缓存）。 */
    fun isViewSubclass(name: String): Boolean {
        return viewAssignCache.getOrPut(name) {
            try {
                val clazz = Class.forName(name, false, hostClassLoader ?: ApiIndex::class.java.classLoader)
                View::class.java.isAssignableFrom(clazz)
            } catch (_: Throwable) {
                false
            }
        }
    }
}
