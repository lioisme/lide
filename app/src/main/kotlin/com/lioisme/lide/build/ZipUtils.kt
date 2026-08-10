package com.lioisme.lide.build

import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * APK 装配共用的 ZIP 工具（读写）。
 *
 * 历史：写入侧原实现 LOCAL 头 extra 填充实现 STORED 条目 4 字节对齐
 * （等价 zipalign），签名切换到官方 apksig 后对齐由签名器重写时自动完成，
 * 此处不再自维护；[writeEntry] 仅保留 STORED 必需的 size/crc 填充。
 */
object ZipUtils {
    // ZIP 中央目录/EOCD 布局常量（Q16 L4：魔法偏移提常量，格式见 APPNOTE）。
    private const val EOCD_MIN_SIZE = 22
    private const val EOCD_CD_SIZE_OFFSET = 12
    private const val EOCD_CD_OFFSET_OFFSET = 16
    private const val CEN_HEADER_SIZE = 46
    private const val CEN_COMPRESSED_SIZE_OFFSET = 24
    private const val CEN_NAME_LEN_OFFSET = 28
    private const val CEN_EXTRA_LEN_OFFSET = 30
    private const val CEN_COMMENT_LEN_OFFSET = 32
    private const val EOCD_SIGNATURE = 0x06054B50L
    private const val CEN_SIGNATURE = 0x02014B50L

    /** 宿主 APK 原生库条目（ABI × 库名 × 未压缩大小）。 */
    data class NativeLibEntry(
        val abi: String,
        val base: String,
        val size: Long,
    )

    /** ZIP 条目（名称 × 压缩方法 × 原始数据）。 */
    data class ZipEntryData(
        val name: String,
        val method: Int,
        val data: ByteArray,
    )

    /** 从字节数组读取全部条目（名称 → 压缩方法 → 原始数据）。 */
    fun readEntries(
        bytes: ByteArray,
        skipBlankNames: Boolean = false,
    ): List<ZipEntryData> {
        val out = ArrayList<ZipEntryData>()
        ZipInputStream(bytes.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                // 空名条目（zip 对齐/壳工具产物，STORED 零字节）无负载，跳过；
                // 否则重写时 name="" 会触发 Android 13+/JDK 18+ 的重复条目拒绝。
                if (skipBlankNames && entry.name.isBlank()) continue
                out += ZipEntryData(entry.name, entry.method, zis.readBytes())
            }
        }
        return out
    }

    /** 按条目名读取单个条目原始数据；不存在返回 null（UI1，2026-08-10 新增）。 */
    fun readEntryData(
        bytes: ByteArray,
        name: String,
    ): ByteArray? {
        ZipInputStream(bytes.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: return null
                if (entry.name == name) return zis.readBytes()
            }
        }
    }

    /**
     * 仅枚举条目名与未压缩大小（CEN 直读，不解压数据）。
     * UI 摘要/ABI 枚举用：完整解压整个宿主 APK 只为列名属浪费。
     * 空名条目（对齐/壳产物）不返回。
     */
    fun listEntryNames(bytes: ByteArray): List<Pair<String, Long>> {
        val out = ArrayList<Pair<String, Long>>()
        var eocd = bytes.size - EOCD_MIN_SIZE
        while (
            eocd >= 0 &&
            u32(bytes, eocd.toLong()) != EOCD_SIGNATURE
        ) {
            eocd--
        }
        if (eocd < 0) return out
        val cdOffset = u32(bytes, eocd.toLong() + EOCD_CD_OFFSET_OFFSET)
        val cdSize = u32(bytes, eocd.toLong() + EOCD_CD_SIZE_OFFSET)
        var pos = cdOffset
        while (pos + CEN_HEADER_SIZE <= cdOffset + cdSize) {
            if (u32(bytes, pos) != CEN_SIGNATURE) {
                break
            }
            val nameLen = u16(bytes, pos + CEN_NAME_LEN_OFFSET)
            val extraLen = u16(bytes, pos + CEN_EXTRA_LEN_OFFSET)
            val commentLen = u16(bytes, pos + CEN_COMMENT_LEN_OFFSET)
            val size = u32(bytes, pos + CEN_COMPRESSED_SIZE_OFFSET)
            val name = String(bytes, pos.toInt() + CEN_HEADER_SIZE, nameLen, Charsets.UTF_8)
            if (name.isNotEmpty()) out += name to size
            pos += CEN_HEADER_SIZE.toLong() + nameLen + extraLen + commentLen
        }
        return out
    }

    /** 宿主 APK 实际携带的 ABI 列表（lib/<abi>/ 目录，保持出现顺序去重）。 */
    fun listAbis(entries: List<Pair<String, Long>>): List<String> =
        entries
            .mapNotNull { it.first }
            .filter { it.startsWith("lib/") }
            .mapNotNull { it.split('/').getOrNull(1)?.takeIf { seg -> seg.isNotBlank() } }
            .distinct()

    /** 宿主 APK 原生库条目（lib/<abi>/<lib>.so），供 ABI × 库名二维过滤与体积估算。 */
    fun listNativeLibEntries(entries: List<Pair<String, Long>>): List<NativeLibEntry> =
        entries
            .mapNotNull { (name, size) ->
                if (!name.startsWith("lib/")) return@mapNotNull null
                val parts = name.split('/')
                if (parts.size < 3 || parts[2].isEmpty()) {
                    null
                } else {
                    NativeLibEntry(parts[1], parts[2], size)
                }
            }

    /** 宿主 APK 原生库文件名集合（去重，依赖分析候选求交集用）。 */
    fun listNativeLibNames(entries: List<Pair<String, Long>>): Set<String> = listNativeLibEntries(entries).mapTo(HashSet()) { it.base }

    private fun u16(
        b: ByteArray,
        o: Long,
    ): Int = (b[o.toInt()].toInt() and 0xFF) or ((b[o.toInt() + 1].toInt() and 0xFF) shl 8)

    private fun u32(
        b: ByteArray,
        o: Long,
    ): Long =
        (b[o.toInt()].toLong() and 0xFF) or
            ((b[o.toInt() + 1].toLong() and 0xFF) shl 8) or
            ((b[o.toInt() + 2].toLong() and 0xFF) shl 16) or
            ((b[o.toInt() + 3].toLong() and 0xFF) shl 24)

    /** 写入条目；STORED 条目自动补齐 size/crc（ZipOutputStream 硬性要求）。 */
    fun writeEntry(
        zos: ZipOutputStream,
        name: String,
        method: Int,
        data: ByteArray,
    ) {
        val entry =
            ZipEntry(name).apply {
                this.method = method
                if (method == ZipEntry.STORED) {
                    size = data.size.toLong()
                    crc = CRC32().apply { update(data) }.value
                }
            }
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }
}
