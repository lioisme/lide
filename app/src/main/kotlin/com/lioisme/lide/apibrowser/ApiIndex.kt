package com.lioisme.lide.apibrowser

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import dalvik.system.DexFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * API 索引：静态类表（assets/javaapi/android.lua，pro 类表平移 + 扩展）
 * + 宿主 dex 动态枚举（androidx/material 动态补齐）+ 工程 libs/ 下 dex|jar 动态枚举
 * + 原生库模块名扫描（libs/ 下 so，仅模块名级）。
 *
 * 类加载模型（架构审计 P0-1 根治，2026-08-06）：
 * - build 期把枚举出的全部类名保留为 [Snapshot.dexClassNames]（宿主 dex + 工程 libs），
 *   「可加载性」判定对枚举类名 O(1) 查表，不再作为反射负查找的入场费；
 *   反射只发生在真正需要类形态/父宿主遍历等语义之处，且统一走单张合并缓存
 *   （classCache + failedLoadCache 负缓存），同一类名全仓仅一次 [Class.forName]。
 *   由此删除：两阶段反射预算、warmUpRowInfo、assignUsages 节流、非空包装负缓存、单飞。
 *
 * 线程契约（AGENTS.md）：反射解析接口（rowInfo / isLoadable / classKind / isViewSubclass /
 * classify / reflect）禁止在 UI 线程直接调用——负面查找需扫描全 dex；UI 只读惰性缓存结果，
 * 列表行一律经 rowInfoAsync（IO）。dex 枚举判定为纯查表（O(1)）。
 * 全部反射路径 try/catch(Throwable) 降级（AGENTS.md 约定）。
 */
object ApiIndex {
    private const val ASSET_CLASS_TABLE = "javaapi/android.lua"

    /** 类形态（反射解析）：接口判定 + 完整类声明签名（列表行第二行）。 */
    data class ClassKind(
        val isInterface: Boolean,
        val signature: String,
    )

    /** 列表行反射信息：类形态 + 可加载性，一次类加载判定同时服务两处显示决策。 */
    data class RowInfo(
        val kind: ClassKind?,
        val loadable: Boolean,
    )

    /** 引擎内置 C 模块（cpath = nativeLibraryDir/lib*.so + 工程 libs）。 */
    private val builtinNativeModules =
        listOf(
            "bson",
            "canvas",
            "cjson",
            "crypt",
            "ftp",
            "gl",
            "http",
            "import",
            "md5",
            "mime",
            "regex",
            "sensor",
            "smtp",
            "socket",
            "xml",
            "zip",
            "zlib",
            "luv",
        )

    /** 不可变快照。 */
    data class Snapshot(
        val classes: List<ApiClassEntry>,
        val nativeModules: List<String>,
        /**
         * 与 [classes] 同序的小写化类名索引（build 期预计算一次 O(n)）。
         * 过滤时用大小写敏感 contains 代替 ignoreCase，仅内部过滤使用，外部不可变。
         */
        val nameLower: List<String>,
        /**
         * 分类 → 类条目索引（build 期一次预分组，索引对齐 [classes]/[nameLower]）。
         * 分类过滤直接取子集：O(选中分类条目数) 而非 O(全部条目)（性能审计 R3）。
         */
        val categories: Map<ApiCategory, List<Int>>,
        /** 分类计数（build 期 eager 预计算，供筛选菜单展示，避免组合阶段 lazy 首读抖动）。 */
        val categoryCounts: Map<ApiCategory, Int>,
        /**
         * build 期枚举出的全部类名（宿主 dex + 工程 libs + R 内部类）。
         * 「可加载性」O(1) 查表命中的类名可直接判定加载成功，免全 dex 扫描（性能审计 R2）。
         */
        val dexNames: Set<String>,
    )

    data class ApiClassEntry(
        val name: String,
        val category: ApiCategory,
        /** 来自工程 libs/ 下 dex|jar 动态枚举。 */
        val fromProject: Boolean,
        /** 用途分类（适配器/按钮控件/…），反射语义推断，失败为 null。 */
        val usage: ApiUsage? = null,
    )

    @Volatile
    private var snapshot: Snapshot? = null

    /** buildBase/completeBuild 互斥锁：杜绝并发构建互踩快照字段（R5a）。 */
    private val buildLock = Any()

    /** completeBuild() 是否已完成（幂等标志；invalidate 时复位）。 */
    @Volatile
    private var completePhaseDone = false

    /**
     * 类解析合并缓存（三套并一，架构审计 P0-1）：
     * 成功加载的 [Class] 缓存 + 已验证失败的类名负缓存。同一类名全进程至多一次
     * [Class.forName]；负缓存永远生效（CHM 拒绝 null 值，未命中类名落负缓存防重复扫描）。
     * 由 [loadClassRef] 唯一入口读写，线程安全。
     */
    private val classCache = ConcurrentHashMap<String, Class<*>>()

    private val failedLoadCache = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /** 已构建过的工程根集合签名（工程增减导致索引失效）。 */
    private var builtProjectRoots: Set<String>? = null

    @Volatile
    private var hostClassLoader: ClassLoader? = null

    fun cached(): Snapshot? = snapshot

    /**
     * 构建索引第一阶段（非反射，快）：静态 class 表解析 + dex 全枚举 + R 类补齐 + 模块扫描。
     * 发布基础快照（用途/行信息未补全），列表立即显示；反射补全见 [completeBuild]。
     * 进程级缓存：工程根未变化时复用当前快照（含已补全的完整快照）。
     * 必须在 IO 线程调用；支持并发调用（内部互斥单飞）。
     */
    @SuppressLint("WrongThread")
    fun buildBase(
        context: Context,
        projectRoots: List<File>,
    ): Snapshot {
        val roots = projectRoots.map { it.absolutePath }.toSet()
        hostClassLoader = context.classLoader
        snapshot?.let { s -> if (builtProjectRoots == roots) return s }
        synchronized(buildLock) {
            snapshot?.let { s -> if (builtProjectRoots == roots) return s }
            val start = System.currentTimeMillis()
            val entries = LinkedHashMap<String, ApiClassEntry>()
            val nativeModules = LinkedHashSet<String>()
            nativeModules += builtinNativeModules

            // 全部枚举类名：O(1) 可加载判定的权威集（不再按前缀丢弃，P0-1）
            val dexNames = HashSet<String>()

            // 1) 静态类表（pro 平移 + material/androidx 精选扩展）
            val staticNames = parseClassTable(context)
            for (name in staticNames) {
                entries.putIfAbsent(name, ApiClassEntry(name, ApiCategoryClassifier.categorizeBase(name), fromProject = false))
            }

            // 2) 宿主 dex 全量枚举：昵称只入集合；entry 注册仅筛 androidx/material + 非内部类
            enumerateDexFile(File(context.applicationInfo.sourceDir)) { name ->
                dexNames.add(name)
                if ((name.startsWith("androidx.") || name.startsWith("com.google.android.material.")) && '$' !in name) {
                    entries.putIfAbsent(name, ApiClassEntry(name, ApiCategoryClassifier.categorizeBase(name), fromProject = false))
                }
            }

            // 3) 工程 libs/ 下 dex|jar 枚举（真·自动识别）
            for (root in roots) {
                val libsDir = File(root, "libs")
                val libFiles =
                    libsDir.listFiles { f -> f.isFile && (f.name.endsWith(".dex") || f.name.endsWith(".jar")) }
                        ?: continue
                for (lib in libFiles) {
                    enumerateDexFile(lib) { name ->
                        dexNames.add(name)
                        if ('$' !in name) {
                            entries.putIfAbsent(name, ApiClassEntry(name, ApiCategory.PROJECT, fromProject = true))
                        }
                    }
                }
                // 原生库：libs/lib*.so → 模块名（仅模块名级识别）
                val soFiles =
                    libsDir.listFiles { f -> f.isFile && f.name.startsWith("lib") && f.name.endsWith(".so") }
                        ?: continue
                for (so in soFiles) {
                    nativeModules += so.name.removePrefix("lib").removeSuffix(".so")
                }
            }

            // 4) R 资源类自动补齐：android.R + 宿主 R 的内部类（反射枚举，类名入 dexNames）
            for (r in listOf("android.R", "com.lioisme.lide.R")) {
                try {
                    val clazz = Class.forName(r, false, context.classLoader)
                    dexNames.add(r)
                    for (inner in clazz.classes) {
                        val n = "${clazz.name}$${inner.simpleName}"
                        dexNames.add(n)
                        entries.putIfAbsent(n, ApiClassEntry(n, ApiCategory.R_RESOURCES, fromProject = false))
                    }
                    entries.putIfAbsent(r, ApiClassEntry(r, ApiCategory.R_RESOURCES, fromProject = false))
                } catch (_: Throwable) {
                }
            }

            val classList = entries.values.toList()
            val categories = classList.indices.groupBy { classList[it].category }
            val base =
                Snapshot(
                    classes = classList,
                    nameLower = classList.map { it.name.lowercase() },
                    categories = categories,
                    categoryCounts = categories.mapValues { it.value.size },
                    nativeModules = nativeModules.toList().sorted(),
                    dexNames = dexNames,
                )
            snapshot = base
            completePhaseDone = false
            builtProjectRoots = roots
            android.util.Log.d("ApiBrowser", "built base ${classList.size} classes, ${nativeModules.size} modules in ${System.currentTimeMillis() - start}ms")
            return base
        }
    }

    /**
     * 构建索引第二阶段（反射补全，无预算上界）：用途分类推断 + 分类细化，完成后原子替换进程级快照。
     * 幂等：已完成后直接返回；buildBase 未执行（快照为空）时直接返回。必须在 IO 线程调用。
     */
    fun completeBuild() {
        if (snapshot == null || completePhaseDone) return
        synchronized(buildLock) {
            if (completePhaseDone) return
            val start = System.currentTimeMillis()
            assignUsages()
            refineCategories()
            completePhaseDone = true
            android.util.Log.d("ApiIndex", "complete phase done ${System.currentTimeMillis() - start}ms")
        }
    }

    /** 用途分类批量推断：全程走合并缓存（同会只存一条），不再预算。 */
    private fun assignUsages() {
        val s = snapshot ?: return
        val classes =
            s.classes.map { entry ->
                if (entry.fromProject) return@map entry
                val usage = ApiUsageClassifier.classify(entry.name)
                if (usage == entry.usage) entry else entry.copy(usage = usage)
            }
        snapshot = s.copy(classes = classes)
    }

    /** 分类细化（反射阶段，completeBuild 内调用）：widget/view 包条目经 View 子类反射验证
     * 细化 COMMON_WIDGETS；分类变化时原子重建分类索引并发布。 */
    private fun refineCategories() {
        val s = snapshot ?: return
        var changed = false
        val classes =
            s.classes.map { entry ->
                if (entry.fromProject || !ApiCategoryClassifier.isWidgetPackage(entry.name)) return@map entry
                val refined = ApiCategoryClassifier.categorize(entry.name)
                if (refined == entry.category) {
                    entry
                } else {
                    changed = true
                    entry.copy(category = refined)
                }
            }
        if (!changed) return
        val categories = classes.indices.groupBy { classes[it].category }
        snapshot =
            s.copy(
                classes = classes,
                categories = categories,
                categoryCounts = categories.mapValues { it.value.size },
            )
    }

    /** 使索引失效（工程 libs 变化后由宿主显式调用）。 */
    fun invalidate() {
        synchronized(buildLock) {
            snapshot = null
            builtProjectRoots = null
            completePhaseDone = false
        }
    }

    /** 解析 assets/javaapi/android.lua 类表（return { "name", ... }）。 */
    private fun parseClassTable(context: Context): List<String> {
        val result = ArrayList<String>()
        try {
            val text =
                context.assets
                    .open(ASSET_CLASS_TABLE)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
            var from = 0
            while (true) {
                val open = text.indexOf('"', from)
                if (open < 0) break
                val close = text.indexOf('"', open + 1)
                if (close < 0) break
                val name = text.substring(open + 1, close)
                if (name.isNotBlank()) result.add(name)
                from = close + 1
            }
        } catch (_: Throwable) {
        }
        return result
    }

    /** 枚举 dex 文件中全部类名。
     *  DexFile（API 26+ deprecated）：无公开替代的纯类名枚举 API。仅 IO 线程调用，异常降级。 */
    @Suppress("DEPRECATION")
    private fun enumerateDexFile(
        file: File,
        sink: (String) -> Unit,
    ) {
        if (!file.exists() || file.length() == 0L) return
        val dex =
            try {
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

    /** 类解析唯一入口（合并缓存）：成功类缓存 / 负缓存。线程安全。 */
    internal fun loadClassRef(name: String): Class<*>? {
        classCache[name]?.let { return it }
        if (name in failedLoadCache) return null
        return try {
            val clazz = Class.forName(name, false, hostClassLoader ?: ApiIndex::class.java.classLoader)
            classCache[name] = clazz
            clazz
        } catch (_: Throwable) {
            failedLoadCache.add(name)
            null
        }
    }

    /** 可加载判定：dex 枚举命中 / boot 框架前缀 → 即可用（零反射）；其余合并缓存。 */
    fun isLoadable(name: String): Boolean {
        snapshot?.dexNames?.let { if (name in it) return true }
        if (isBootFramework(name)) return true
        return loadClassRef(name) != null
    }

    private fun isBootFramework(name: String): Boolean = name.startsWith("android.") || name.startsWith("java.") || name.startsWith("javax.")

    /**
     * 列表行反射信息（类形态 + 可加载性）。缓存路径 O(1) 查表，未命中合并缓存解析
     * （每类名至多一次全 dex 扫描 → 负缓存）。线程契约见类头。
     */
    fun rowInfo(name: String): RowInfo {
        val clazz = loadClassRef(name) ?: return RowInfo(kind = null, loadable = false)
        val kind =
            ClassKind(
                isInterface = clazz.isInterface,
                signature =
                    buildString {
                        append(if (clazz.isInterface) "interface" else "class")
                        append(" ")
                        append(clazz.name)
                        clazz.superclass?.let { append(" extends ").append(it.name) }
                    },
            )
        return RowInfo(kind = kind, loadable = true)
    }

    /**
     * 列表行反射信息挂起版本：快路径缓存命中直接返回（UI 只读缓存，契约允许）；
     * 未命中在 IO 线程执行合并缓存解析，结果落缓存（fling 后再次命中即 O(1)）。
     * 线程契约：计算恒定在 IO，UI 走同步快路径。
     */
    suspend fun rowInfoAsync(name: String): RowInfo {
        classCache[name]?.let { return rowInfo(name) }
        return withContext(Dispatchers.IO) { rowInfo(name) }
    }

    /** 类形态信息（接口/签名），不可加载返回 null。线程契约同 [rowInfo]。 */
    fun classKind(name: String): ClassKind? = rowInfo(name).kind

    /** 是否为 View 子类（反射验证，带合并缓存）。 */
    fun isViewSubclass(name: String): Boolean {
        val clazz = loadClassRef(name) ?: return false
        return View::class.java.isAssignableFrom(clazz)
    }
}
