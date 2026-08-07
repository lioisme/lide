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
    /** 宿主 APK 原生库条目（ABI × 库名 × 未压缩大小）。 */
    data class NativeLibEntry(
        val abi: String,
        val base: String,
        val size: Long,
    )

    /** 从字节数组读取全部条目（名称 → 压缩方法 → 原始数据）。 */
    fun readEntries(
        bytes: ByteArray,
        skipBlankNames: Boolean = false,
    ): List<Triple<String, Int, ByteArray>> {
        val out = ArrayList<Triple<String, Int, ByteArray>>()
        ZipInputStream(bytes.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                // 空名条目（zip 对齐/壳工具产物，STORED 零字节）无负载，跳过；
                // 否则重写时 name="" 会触发 Android 13+/JDK 18+ 的重复条目拒绝。
                if (skipBlankNames && entry.name.isBlank()) continue
                out += Triple(entry.name, entry.method, zis.readBytes())
            }
        }
        return out
    }

    /**
     * 仅枚举条目名与未压缩大小（CEN 直读，不解压数据）。
     * UI 摘要/ABI 枚举用：完整解压整个宿主 APK 只为列名属浪费。
     * 空名条目（对齐/壳产物）不返回。
     */
    fun listEntryNames(bytes: ByteArray): List<Pair<String, Long>> {
        val out = ArrayList<Pair<String, Long>>()
        var eocd = bytes.size - 22
        while (
            eocd >= 0 &&
            !(
                bytes[eocd] == 0x50.toByte() && bytes[eocd + 1] == 0x4B.toByte() &&
                    bytes[eocd + 2] == 0x05.toByte() && bytes[eocd + 3] == 0x06.toByte()
            )
        ) {
            eocd--
        }
        if (eocd < 0) return out
        val cdOffset = u32(bytes, eocd.toLong() + 16)
        val cdSize = u32(bytes, eocd.toLong() + 12)
        var pos = cdOffset
        while (pos + 46 <= cdOffset + cdSize) {
            if (
                !(
                    bytes[pos.toInt()] == 0x50.toByte() && bytes[pos.toInt() + 1] == 0x4B.toByte() &&
                        bytes[pos.toInt() + 2] == 0x01.toByte() && bytes[pos.toInt() + 3] == 0x02.toByte()
                )
            ) {
                break
            }
            val nameLen = u16(bytes, pos + 28)
            val extraLen = u16(bytes, pos + 30)
            val commentLen = u16(bytes, pos + 32)
            val size = u32(bytes, pos + 24)
            val name = String(bytes, pos.toInt() + 46, nameLen, Charsets.UTF_8)
            if (name.isNotEmpty()) out += name to size
            pos += 46L + nameLen + extraLen + commentLen
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
