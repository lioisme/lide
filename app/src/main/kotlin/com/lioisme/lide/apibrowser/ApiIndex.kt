package com.lioisme.lide.apibrowser

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import dalvik.system.DexFile
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * API 索引：静态类表（assets/javaapi/android.lua，pro 类表平移 + 扩展）
 * + 宿主 dex 动态枚举（androidx/material）+ 工程 libs/ 下 dex|jar 动态枚举
 * + 原生库模块名扫描（libs/ 下 so，仅模块名级）。
 *
 * 线程模型：两阶段构建——buildBase() 只做非反射阶段（静态解析 + dex 枚举，约 1s 级）并快速发布
 * 基础快照，completeBuild() 在后台补全反射阶段（用途推断 + 行信息预热，带时间预算上界）
 * 并原子替换为完整快照；均在 IO 线程执行，反射验证带并发缓存。
 * 反射查询接口（rowInfo / isLoadable / classKind / isViewSubclass）禁止在 UI 线程
 * 直接调用（AGENTS.md 线程契约），UI 只读惰性缓存结果。
 * 全部反射路径 try/catch(Throwable) 降级（AGENTS.md 约定）。
 */
object ApiIndex {

    private const val ASSET_CLASS_TABLE = "javaapi/android.lua"

    /** 用途分类反射推断的时间预算（毫秒），超时余量置 null，保证索引构建有上界。 */
    private const val USAGE_BUDGET_MS = 2000L

    /** 列表行信息（类形态 + 可加载性）预热预算（毫秒），build 后批量填充缓存，超时余量跳过，构建有上界。
     *  2500ms：负缓存（非空 RowInfo）生效后每类名仅扫描一次，预算利用率高，可覆盖更多首屏行。 */
    private const val ROW_INFO_WARMUP_BUDGET_MS = 2500L

    /** 类形态（反射惰性缓存）：接口判定 + 完整类声明签名（列表行第二行）。 */
    data class ClassKind(
        val isInterface: Boolean,
        val signature: String,
    )

    /**
     * 列表行反射信息：类形态 + 可加载性，一次类加载判定同时服务两处显示决策。
     * [kind] 为 null 表示类不可加载或反射失败；[loadable] 是该事实的显式化，
     * 显示层读 [loadable] 决定不可用指示（error 圆点），不依赖 [kind] 的隐式 null 语义。
     */
    data class RowInfo(
        val kind: ClassKind?,
        val loadable: Boolean,
    )

    /** 引擎内置 C 模块（cpath = nativeLibraryDir/lib?.so + 工程 libs）。 */
    private val builtinNativeModules = listOf(
        "bson", "canvas", "cjson", "crypt", "ftp", "gl", "http", "import",
        "md5", "mime", "regex", "sensor", "smtp", "socket", "xml", "zip", "zlib", "luv",
    )

    /** 不可变快照。 */
    data class Snapshot(
        val classes: List<ApiClassEntry>,
        val nativeModules: List<String>,
        /**
         * 与 [classes] 同序的小写化类名索引（build 期预计算一次 O(n)）。
         * 过滤时用大小写敏感 contains 代替 ignoreCase（避免每次击键为每条目重建转换），
         * 快 5~10 倍；仅内部过滤使用，外部不可变。
         */
        val nameLower: List<String>,
        /**
         * 分类 → 类条目索引（build 期一次预分组，索引对齐 [classes]/[nameLower]）。
         * 分类过滤直接取子集：O(选中分类条目数) 而非 O(全部条目)（性能审计 R3）。
         */
        val categories: Map<ApiCategory, List<Int>>,
        /** 分类计数（build 期 eager 预计算，供筛选菜单展示，避免组合阶段 lazy 首读抖动）。 */
        val categoryCounts: Map<ApiCategory, Int>,
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
     * 列表行反射信息缓存（含负缓存）：键为类名，值为 [RowInfo] 实例。
     * 实现约定：**值永不为 null**（不可加载类以 RowInfo(kind = null, loadable = false) 入缓存）——
     * ConcurrentHashMap 禁止 null 值，若 compute 返回 null 则 putIfAbsent 抛 NPE 且条目永不落缓存，
     * 导致负查找（类不存在，需扫描全部 dex，单次 10~100ms）每次重查。非空包装是负缓存生效的前提
     * （AGENTS.md 线程契约：反射查询禁止 UI 线程直接调用，UI 只读惰性缓存结果）。
     */
    private val rowInfoCache = ConcurrentHashMap<String, RowInfo>()

    private val viewAssignCache = ConcurrentHashMap<String, Boolean>()

    /** 已构建过的工程根集合签名（工程增减导致索引失效）。 */
    private var builtProjectRoots: Set<String>? = null

    @Volatile
    private var hostClassLoader: ClassLoader? = null

    fun cached(): Snapshot? = snapshot

    /** 宿主 ClassLoader（buildBase() 后可用），供反射规则链复用。 */
    fun hostLoader(): ClassLoader? = hostClassLoader

    /**
     * 构建索引第一阶段（非反射，快）：静态类表解析 + dex 枚举 + R 类补齐 + 模块扫描。
     * 发布基础快照（用途/行信息未补全），列表可立即显示；反射补全见 [completeBuild]。
     * 进程级缓存：工程根未变化时复用当前快照（含已补全的完整快照）。
     * 必须在 IO 线程调用；支持并发调用（内部互斥单飞）。
     */
    @SuppressLint("WrongThread")
    fun buildBase(context: Context, projectRoots: List<File>): Snapshot {
        val roots = projectRoots.map { it.absolutePath }.toSet()
        hostClassLoader = context.classLoader
        snapshot?.let { s -> if (builtProjectRoots == roots) return s }
        synchronized(buildLock) {
            snapshot?.let { s -> if (builtProjectRoots == roots) return s }
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
            // 前缀先行（多数类名不匹配，prefix 比较失败即退出，免整串扫描）；
            // 匹配前缀后才做 '$' 内部类过滤（仅 ~16K 命中条目触发整串扫描）。
            enumerateDexFile(File(context.applicationInfo.sourceDir)) { name ->
                if ((name.startsWith("androidx.") || name.startsWith("com.google.android.material.")) && '$' !in name) {
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
            for (r in listOf("android.R", "com.lioisme.lide.R")) {
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

            val classList = entries.values.toList()
            val categories = classList.indices.groupBy { classList[it].category }
            val base = Snapshot(
                classes = classList,
                nameLower = classList.map { it.name.lowercase() },
                categories = categories,
                categoryCounts = categories.mapValues { it.value.size },
                nativeModules = nativeModules.toList().sorted(),
            )
            snapshot = base
            completePhaseDone = false
            builtProjectRoots = roots
            android.util.Log.d("ApiIndex", "built base ${entries.size} classes, ${nativeModules.size} modules in ${System.currentTimeMillis() - start}ms")
            return base
        }
    }

    /**
     * 构建索引第二阶段（反射补全，带时间预算上界）：用途分类推断 + 列表行信息预热，
     * 完成后原子替换进程级快照（[cached] 可见完整快照），列表徽章渐进完善。
     * 幂等：已完成后直接返回；buildBase 未执行（快照为空）时直接返回。
     * 必须在 IO 线程调用。
     */
    fun completeBuild() {
        if (snapshot == null || completePhaseDone) return
        synchronized(buildLock) {
            if (completePhaseDone) return
            val start = System.currentTimeMillis()
            assignUsages()
            warmUpRowInfo()
            completePhaseDone = true
            android.util.Log.d("ApiIndex", "complete phase done in ${System.currentTimeMillis() - start}ms")
        }
    }

    /** 用途分类批量推断：时间预算内对宿主可加载类做反射规则链，超时余量置 null。 */
    private fun assignUsages() {
        val s = snapshot ?: return
        val deadline = System.currentTimeMillis() + USAGE_BUDGET_MS
        var checked = 0
        var timedOut = false
        val classes = s.classes.map { entry ->
            if (timedOut || entry.fromProject) return@map entry
            val usage = ApiUsageClassifier.classify(entry.name)
            checked++
            if (checked % 64 == 0 && System.currentTimeMillis() > deadline) timedOut = true
            if (usage == entry.usage) entry else entry.copy(usage = usage)
        }
        snapshot = s.copy(classes = classes)
    }

    /**
     * 列表行信息预热：按列表顺序批量填充 rowInfoCache（经 [rowInfo]，非空包装含负缓存），
     * 使首屏及前序滚动行命中缓存（O(1) 查表），消除行级全 dex 扫描风暴。
     * 时间预算内尽力而为，超时余量跳过，未预热条目由行级 IO 惰性补齐（首次扫描一次后即缓存）。
     * completeBuild() 在 IO 线程调用本方法。
     */
    private fun warmUpRowInfo() {
        val s = snapshot ?: return
        val deadline = System.currentTimeMillis() + ROW_INFO_WARMUP_BUDGET_MS
        for (entry in s.classes) {
            if (System.currentTimeMillis() > deadline) return
            rowInfo(entry.name)
        }
    }

    /** 使索引失效（工程 libs 变化后由宿主显式调用）。 */
    fun invalidate() {
        synchronized(buildLock) {
            snapshot = null
            builtProjectRoots = null
            completePhaseDone = false
        }
    }

    /** 解析 assets/javaapi/android.lua 类表（return { "类名", ... }）。
     *  手动引号扫描替代 Regex.findAll（每行分配 MatchResult 的开销，实测约 1s）——
     *  单趟 indexOf 子串切分，毫秒级（性能审计 R1 冷启动优化）。 */
    private fun parseClassTable(context: Context): List<String> {
        val result = ArrayList<String>()
        try {
            val text = context.assets.open(ASSET_CLASS_TABLE).bufferedReader(Charsets.UTF_8).use { it.readText() }
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

    /**
     * 列表行反射信息（类形态 + 可加载性），惰性缓存。
     * 一次 Class.forName 同时服务「接口判定 + 可加载性」两处显示决策；
     * 不可加载类以非空 RowInfo 负缓存，**每类名仅首次全 dex 扫描一次，此后 O(1) 查表**。
     * 线程契约：禁止在 UI 线程直接调用（AGENTS.md）；列表行经
     * produceState / LaunchedEffect + Dispatchers.IO 获取，UI 只读缓存结果。
     */
    fun rowInfo(name: String): RowInfo = rowInfoCache.getOrPut(name) { computeRowInfo(name) }

    /**
     * 列表行反射信息挂起版本（单飞，滚动安全）：语义同 [rowInfo]。
     * - 快路径：缓存命中直接返回（UI 只读缓存，契约允许，零协程开销）；
     * - 慢路径：NonCancellable + IO 执行 CHM getOrPut 单飞 compute——同一类名仅一次全 dex 扫描，
     *   计算不随调用方（行 LaunchedEffect/produceState 生命周期）取消，结果必落缓存；
     *   fling 时行快速进出组合不再反复重启负查找（性能审计 R2）。
     * 线程契约：计算恒在 IO，UI 只走同步快路径。
     */
    suspend fun rowInfoAsync(name: String): RowInfo {
        rowInfoCache[name]?.let { return it }
        return withContext(Dispatchers.IO + NonCancellable) {
            rowInfoCache.getOrPut(name) { computeRowInfo(name) }
        }
    }

    private fun computeRowInfo(name: String): RowInfo {
        val kind = try {
            val clazz = Class.forName(name, false, hostClassLoader ?: ApiIndex::class.java.classLoader)
            val signature = buildString {
                append(if (clazz.isInterface) "interface" else "class")
                append(" ")
                append(clazz.name)
                clazz.superclass?.let { append(" extends ").append(it.name) }
            }
            ClassKind(clazz.isInterface, signature)
        } catch (_: Throwable) {
            null
        }
        return RowInfo(kind = kind, loadable = kind != null)
    }

    /** 类是否可加载（惰性缓存含负缓存）。线程契约同 [rowInfo]。 */
    fun isLoadable(name: String): Boolean = rowInfo(name).loadable

    /** 类形态信息（接口/签名），惰性缓存含负缓存，不可加载返回 null。线程契约同 [rowInfo]。 */
    fun classKind(name: String): ClassKind? = rowInfo(name).kind

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
