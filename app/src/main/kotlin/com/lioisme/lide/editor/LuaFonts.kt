package com.lioisme.lide.editor

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.StringRes
import com.lioisme.lide.R
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * T9/T11（2026-08-11）字体解析：内置 6 款等宽 ttf（Squircle CE 组合，OFL）+
 * 外部导入（files/fonts 目录 ttf）。对齐 Squircle `TypefaceProvider`：
 * - 内置字体经 assets 加载（assets/fonts/<name>.ttf，白名单见 AGENTS.md R5 例外）；
 * - 外部字体 id = [EditorConfig.FONT_FAMILY_EXTERNAL_BASE] + files/fonts 扫描序
 *   （按文件名排序）；导入/删除后经 [invalidate] 清缓存重扫；
 * - L2（2026-08-12 收敛）：系统字体仅作**终极兜底**、不再作为可选项——
 *   加载失败 / 未知 id 走两级兜底链 [fallback]（`Typeface.MONOSPACE` →
 *   `Typeface.DEFAULT`，`TypefaceProvider.DEFAULT` 同款语义加固）；
 *   系统族常量 id（1/2/3）保留仅为兜底与历史持久化兼容（老用户遗留值仍可渲染，
 *   设置面板已不再展示）；
 * - 兜底为原生实现方式（`Typeface` 静态常量直取），无自写字体回退逻辑；
 * - Typeface 实例进程级缓存（按 id），供 LuaEditor 守卫式 diff 稳定比较
 *   （createFromAsset 每次返回新对象，不缓存会让 diff 恒不相等、每次重组重设字体）。
 */
object LuaFonts {

    /** 内置字体 id → asset 文件名（id 常量见 [EditorConfig] companion；值与设置键持久化）。 */
    internal val builtinAssets: Map<Int, String> =
        mapOf(
            EditorConfig.FONT_FAMILY_DEFAULT to "jetbrains_mono.ttf",
            EditorConfig.FONT_FAMILY_JETBRAINS_MONO to "jetbrains_mono.ttf",
            EditorConfig.FONT_FAMILY_DROID_SANS_MONO to "droid_sans_mono.ttf",
            EditorConfig.FONT_FAMILY_FIRA_CODE to "fira_code.ttf",
            EditorConfig.FONT_FAMILY_SOURCE_CODE_PRO to "source_code_pro.ttf",
            EditorConfig.FONT_FAMILY_ANONYMOUS_PRO to "anonymous_pro.ttf",
            EditorConfig.FONT_FAMILY_DEJAVU_SANS_MONO to "dejavu_sans_mono.ttf",
        )

    /** 内置字体 id → 显示名（设置页 / 面板列表）。 */
    @StringRes
    internal fun builtinLabelRes(id: Int): Int =
        when (id) {
            EditorConfig.FONT_FAMILY_DEFAULT,
            EditorConfig.FONT_FAMILY_JETBRAINS_MONO,
            -> R.string.font_jetbrains_mono
            EditorConfig.FONT_FAMILY_DROID_SANS_MONO -> R.string.font_droid_sans_mono
            EditorConfig.FONT_FAMILY_FIRA_CODE -> R.string.font_fira_code
            EditorConfig.FONT_FAMILY_SOURCE_CODE_PRO -> R.string.font_source_code_pro
            EditorConfig.FONT_FAMILY_ANONYMOUS_PRO -> R.string.font_anonymous_pro
            EditorConfig.FONT_FAMILY_DEJAVU_SANS_MONO -> R.string.font_dejavu_sans_mono
            else -> R.string.font_jetbrains_mono
        }

    private val cache = ConcurrentHashMap<String, Typeface>()

    /** 外部字体扫描结果（按文件名排序的文件列表）；null = 未扫描。 */
    private var externalFiles: List<File>? = null

    /**
     * 字体 id → [Typeface]：内置 6 款经 assets 加载（进程级缓存），
     * 外部导入（id ≥ [EditorConfig.FONT_FAMILY_EXTERNAL_BASE]）经 files/fonts 扫描；
     * 系统族常量（1/2/3）直取系统字体（终极兜底语义，不再作为可选项）；
     * 其余一切失败走两级兜底链 [fallback]（MONOSPACE → DEFAULT），绝不返回 null。
     *
     * C4（2026-08-12）：缓存键由 id 改「asset 文件名 / 文件路径」——id 0（DEFAULT）
     * 与 4（JETBRAINS_MONO）同映射 jetbrains_mono.ttf，按 id 缓存同一文件两份
     * Typeface；按文件名缓存后两 id 共享实例，且 LuaEditor diff 相等性判定一致。
     */
    fun resolve(context: Context, fontFamily: Int): Typeface {
        if (fontFamily in 1..EditorConfig.FONT_FAMILY_SERIF) {
            return systemFamily(fontFamily)
        }
        val key = cacheKey(context, fontFamily) ?: return fallback()
        cache[key]?.let { return it }
        val resolved = loadByKey(context, key) ?: fallback()
        cache[key] = resolved
        return resolved
    }

    /** 系统族常量直取（MONOSPACE/SANS/SERIF；恒非空，无缓存必要）。 */
    private fun systemFamily(fontFamily: Int): Typeface =
        when (fontFamily) {
            EditorConfig.FONT_FAMILY_MONOSPACE -> Typeface.MONOSPACE
            EditorConfig.FONT_FAMILY_SANS -> Typeface.SANS_SERIF
            EditorConfig.FONT_FAMILY_SERIF -> Typeface.SERIF
            else -> fallback()
        }

    /** 缓存键：内置 = `asset:<文件名>`；外部 = 文件绝对路径；解析失败返回 null。 */
    private fun cacheKey(
        context: Context,
        fontFamily: Int,
    ): String? =
        if (fontFamily >= EditorConfig.FONT_FAMILY_EXTERNAL_BASE) {
            val index = fontFamily - EditorConfig.FONT_FAMILY_EXTERNAL_BASE
            scanExternal(context)?.getOrNull(index)?.absolutePath
        } else {
            builtinAssets[fontFamily]?.let { "asset:$it" }
        }

    private fun loadByKey(
        context: Context,
        key: String,
    ): Typeface? =
        try {
            if (key.startsWith(ASSET_KEY_PREFIX)) {
                Typeface.createFromAsset(context.assets, "fonts/${key.removePrefix(ASSET_KEY_PREFIX)}")
            } else {
                Typeface.createFromFile(File(key))
            }
        } catch (_: Throwable) {
            null
        }

    /** 终极兜底链（L2）：等宽系统字体 → 系统默认字体；恒非空，保证删光内置
     *  / 导入字体后编辑器不崩溃（原生 Typeface 静态常量，零自写回退逻辑）。 */
    private fun fallback(): Typeface =
        try {
            Typeface.MONOSPACE
        } catch (_: Throwable) {
            Typeface.DEFAULT
        }

    /** 外部字体 id → 显示名（文件名去扩展名；越界返回 null）。 */
    fun externalLabel(context: Context, id: Int): String? {
        val index = id - EditorConfig.FONT_FAMILY_EXTERNAL_BASE
        val files = scanExternal(context) ?: return null
        return files.getOrNull(index)?.name?.substringBeforeLast('.')
    }

    /** 外部字体 id → 显示名 全量列表（面板一次性渲染，顺序与 id 严格一致；单次扫描）。 */
    fun externalLabels(context: Context): List<Pair<Int, String>> {
        val files = scanExternal(context) ?: return emptyList()
        val base = EditorConfig.FONT_FAMILY_EXTERNAL_BASE
        return files.mapIndexed { index, f -> base + index to f.name.substringBeforeLast('.') }
    }

    /** 导入/删除外部字体后清缓存（下次 resolve/扫描重建）。 */
    fun invalidate() {
        cache.clear()
        externalFiles = null
    }

    /** 缓存键前缀（内置字体 asset 名；见 [cacheKey]）。 */
    private const val ASSET_KEY_PREFIX = "asset:"

    /** files/fonts 扫描（缓存；ttf 过滤 + 按文件名排序，与 id 序一致）。 */
    private fun scanExternal(context: Context): List<File>? {
        externalFiles?.let { return it }
        val dir = File(context.filesDir, "fonts")
        val list = try {
            dir.listFiles { f -> f.isFile && f.extension.equals("ttf", ignoreCase = true) }
                ?.sortedBy { it.name }?.toList() ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
        externalFiles = list
        return list
    }
}
