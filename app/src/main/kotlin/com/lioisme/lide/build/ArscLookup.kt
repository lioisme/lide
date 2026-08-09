package com.lioisme.lide.build

/**
 * resources.arsc 最小查找器（纯字节级，无 Android 依赖）：
 * 定位 drawable 类型表中应用图标条目（key 为 "icon"）的资源 ID 与资源文件路径。
 *
 * 用途：
 * - [drawableIconId]：打包产物在模板无 icon 属性时兜底注入 android:icon 资源引用
 *   （TYPE_REFERENCE），不写死资源 ID；
 * - [drawableIconPath]：工程根 icon.png 覆盖桌面图标的装配目标——资源混淆构建
 *   （release R8 资源优化）会把图标文件重命名为短路径（res/9T.png 等），字面量
 *   匹配 res/drawable/icon.png 会落空，经 arsc 的 Res_value(TYPE_STRING) 引用
 *   全局字符串池取真实条目路径（2026-08-09 P11 实证：debug 为
 *   "res/drawable/icon.png"，release 为混淆短名）。
 *
 * 结构要点按真实 aapt2 36 宿主产物（debug + release 双份）逐字节核验
 * （2026-08-09 P11，Python 独立解析器对照）：
 * - 文件 = ResTable_header（12B，magic 0x0002）+ chunk 序列，首 chunk 从 offset 12 起；
 *   全局字符串池（0x0001，一般位于最前），随后 package chunk（0x0200）；
 * - package chunk：headerSize（u16@+2，真实 288）之后的区域才是内嵌 chunk 序列；
 *   id@8；typeStrings/keyStrings 为相对 package chunk 头的偏移（u32 @+268/@+276）；
 * - type chunk（0x0201）：typeId@+8（1 起）、entryCount@+12、entriesStart@+16；
 *   entry 数据为紧凑布局（aapt2 36 实测：config 后 entry 头直排，esize=8、无
 *   valueCount 字段），每项：u16 size(8) + u16 flags + u32 key（相对 keyStrings 池），
 *   随后紧跟 8 字节 Res_value（u16 size + u8 res0 + u8 dataType + u32 data）；
 *   兼容旧式 offset 数组（entriesStart 区前的 u32 偏移表，first offset != 0 时使用）；
 * - 图标 value 为 TYPE_STRING(0x03)：data = 全局字符串池索引 → 资源文件路径。
 *
 * 解析失败 / 无该资源返回 null（调用方差级降级：不注入 / 回落字面量路径）。
 */
internal object ArscLookup {
    // 图标 key：aapt2 归一化为 "icon"（真实产物 keyStrings 池实证，2026-08-09）；
    // 兼容历史 "icon.png" 写法。
    private val ICON_KEYS = setOf("icon", "icon.png")

    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_TABLE_PACKAGE_TYPE = 0x0200
    private const val RES_TABLE_TYPE_TYPE = 0x0201
    private const val TYPE_STRING = 0x03

    /** 图标条目（资源 ID + 文件路径，路径可能为 null 表示 value 非 STRING）。 */
    data class IconEntry(
        val resourceId: Int,
        val path: String?,
    )

    /** drawable 类型中应用图标条目的资源 ID（打包兜底注入用）。安全降级 null。 */
    fun drawableIconId(arsc: ByteArray?): Int? = safeFind(arsc)?.resourceId

    /** 应用图标引用的资源文件路径（如 res/drawable/icon.png 或混淆名 res/9T.png）。 */
    fun drawableIconPath(arsc: ByteArray?): String? = safeFind(arsc)?.path

    private fun safeFind(arsc: ByteArray?): IconEntry? {
        if (arsc == null || arsc.size < 12) return null
        return try {
            findIcon(arsc)
        } catch (_: Throwable) {
            null
        }
    }

    private fun findIcon(arsc: ByteArray): IconEntry? {
        // 1) 枚举顶层 chunk：记录全局字符串池（icon 路径解析用）与 package。
        var globalPool: Pair<Int, Int>? = null // (chunkOff, chunkSize)
        var pkgOff = -1
        var pkgSize = 0
        var pos = 12
        while (pos + 8 <= arsc.size) {
            val type = u16(arsc, pos)
            val size = u32(arsc, pos + 4).toInt()
            if (size < 8 || pos + size > arsc.size) return null
            when (type) {
                RES_STRING_POOL_TYPE -> if (globalPool == null) globalPool = pos to size
                RES_TABLE_PACKAGE_TYPE -> {
                    pkgOff = pos
                    pkgSize = size
                }
            }
            pos += size
        }
        if (pkgOff < 0) return null
        val icon = iconInPackage(arsc, pkgOff, pkgSize, globalPool?.first) ?: return null
        return icon
    }

    private fun iconInPackage(
        arsc: ByteArray,
        pkgOff: Int,
        pkgSize: Int,
        globalPoolOff: Int?,
    ): IconEntry? {
        val pkgId = arsc[pkgOff + 8].toInt() and 0xFF
        val typeNames =
            stringPool(arsc, pkgOff, u32(arsc, pkgOff + 268).toInt()) ?: return null
        val keyNames =
            stringPool(arsc, pkgOff, u32(arsc, pkgOff + 276).toInt()) ?: return null
        val drawableTypeId = typeNames.indexOfFirst { it == "drawable" } + 1
        if (drawableTypeId <= 0) return null
        val iconKeyIndex = keyNames.indexOfFirst { it in ICON_KEYS }
        if (iconKeyIndex < 0) return null

        // 2) package 内嵌 chunk 序列：从 package 头之后开始（headerSize）。
        //    typeStrings/keyStrings 池与 type chunk 均位于该区域（实测 288 = 池起点）。
        var p = pkgOff + (u16(arsc, pkgOff + 2).coerceAtLeast(12))
        val end = pkgOff + pkgSize
        while (p + 8 <= end) {
            val type = u16(arsc, p)
            val size = u32(arsc, p + 4).toInt()
            if (size < 8 || p + size > end) return null
            if (type == RES_TABLE_TYPE_TYPE && (arsc[p + 8].toInt() and 0xFF) == drawableTypeId) {
                findIconInType(arsc, p, size, pkgId, drawableTypeId, iconKeyIndex, globalPoolOff)
                    ?.let { return it }
            }
            p += size
        }
        return null
    }

    private fun findIconInType(
        arsc: ByteArray,
        typeOff: Int,
        typeSize: Int,
        pkgId: Int,
        typeId: Int,
        iconKeyIndex: Int,
        globalPoolOff: Int?,
    ): IconEntry? {
        val entryCount = u32(arsc, typeOff + 12).toInt()
        val entriesStart = u32(arsc, typeOff + 16).toInt()
        if (entryCount <= 0 || entriesStart < 56) return null

        // offset 数组 vs 紧凑布局（aapt2 36 实测：紧凑，offset 数组区全 0）。
        val offsBase = typeOff + 56
        val hasOffsets =
            offsBase + 4 * entryCount <= typeOff + typeSize &&
                u32(arsc, offsBase).toInt() != 0

        var ep = typeOff + entriesStart
        for (i in 0 until entryCount) {
            if (ep + 8 > typeOff + typeSize) break
            if (hasOffsets) {
                val off = u32(arsc, offsBase + 4 * i).toInt()
                if (off < 0 || off + 8 > typeSize) break
                ep = typeOff + entriesStart + off
            }
            val esize = u16(arsc, ep)
            if (u32(arsc, ep + 4).toInt() == iconKeyIndex) {
                // 命中图标条目：解析条目随后的 Res_value（非 COMPLEX）。
                val flags = u16(arsc, ep + 2)
                if (flags and 0x01 != 0) return null // COMPLEX（样式表等）不做路径解析
                val v = ep + 8
                if (v + 8 > typeOff + typeSize) return null
                val dataType = arsc[v + 3].toInt() and 0xFF
                val data = u32(arsc, v + 4).toInt()
                val path =
                    if (dataType == TYPE_STRING && globalPoolOff != null) {
                        globalString(arsc, globalPoolOff, data)
                    } else {
                        null
                    }
                return IconEntry((pkgId shl 24) or (typeId shl 16) or i, path)
            }
            // 紧凑布局步长：entry 头 8B + Res_value 8B（aapt2 36 esize=8，无 valueCount）；
            // 传统布局 esize=16（头 + 首个 value 尺寸自含）按 esize 前进。
            ep += when {
                esize == 8 -> 16
                esize >= 16 -> esize
                else -> 16
            }
        }
        return null
    }

    private fun globalString(
        arsc: ByteArray,
        poolOff: Int,
        index: Int,
    ): String? {
        val count = u32(arsc, poolOff + 8).toInt()
        if (index < 0 || index >= count) return null
        val pool = stringPool(arsc, poolOff, 0) ?: return null
        return pool.getOrNull(index)
    }

    /**
     * 读字符串池（UTF-8 / UTF-16 均支持，相对 [baseOff] 的偏移 [poolOff]）。
     * 返回 null 表示池结构异常；非法桶尾取池头（防御）。
     */
    private fun stringPool(arsc: ByteArray, baseOff: Int, poolOff: Int): List<String>? {
        if (poolOff < 0) return null
        val o = baseOff + poolOff
        if (o + 28 > arsc.size) return null
        val count = u32(arsc, o + 8).toInt()
        if (count < 0 || count > (arsc.size / 2)) return null
        val flags = u32(arsc, o + 16).toInt()
        val stringsStart = u32(arsc, o + 20).toInt()
        val utf8 = (flags and 0x100) != 0
        val out = ArrayList<String>(count)
        for (i in 0 until count) {
            val off = u32(arsc, o + 28 + 4 * i).toInt()
            val p = o + stringsStart + off
            if (p >= arsc.size) return null
            out += if (utf8) readUtf8(arsc, p) else readUtf16(arsc, p)
        }
        return out
    }

    private fun readUtf16(b: ByteArray, o: Int): String {
        val len = u16(b, o)
        if (o + 2 + len * 2 > b.size) return ""
        return String(b, o + 2, len * 2, Charsets.UTF_16LE)
    }

    private fun readUtf8(b: ByteArray, o: Int): String {
        var p = o
        val charLen = readUtf8Length(b, p)
        if (charLen == null) return ""
        p += charLen.second
        val byteLen = readUtf8Length(b, p)
        if (byteLen == null) return ""
        p += byteLen.second
        if (p + byteLen.first > b.size) return ""
        return String(b, p, byteLen.first, Charsets.UTF_8)
    }

    /** 解码一个 ResStringPool UTF-8 长度前缀：返回（长度, 占用字节数）。 */
    private fun readUtf8Length(b: ByteArray, o: Int): Pair<Int, Int>? {
        if (o >= b.size) return null
        val first = b[o].toInt() and 0xFF
        if (first and 0x80 == 0) return first to 1
        if (o + 1 >= b.size) return null
        val len = (first and 0x7F) shl 8 or (b[o + 1].toInt() and 0xFF)
        return len to 2
    }

    private fun u16(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFF) or
            ((b[o + 1].toLong() and 0xFF) shl 8) or
            ((b[o + 2].toLong() and 0xFF) shl 16) or
            ((b[o + 3].toLong() and 0xFF) shl 24)
}