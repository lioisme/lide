package com.lioisme.lide.build

import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * APK 装配/签名共用的 ZIP 写入工具。
 *
 * Android 11+（targetSdk 30+）安装校验要求 resources.arsc 等 STORED 条目
 * 4 字节对齐：写入条目的 LOCAL 头 extra 字段填充零字节，使数据区起始偏移
 * 4 对齐（等价 zipalign），不破坏顺序读取（ZipInputStream 按 extra_len 跳过）。
 *
 * 位置追踪不依赖手工算术：DEFLATED 条目会附带 16 字节 data descriptor
 * （LOC 中 csize/size/crc 为零，标志位 3），手工累加会漂移，因此用
 * [CountingOutputStream] 统计真实写出的字节数作为下一条目偏移。
 */
object ZipUtils {

    const val LOC_HEADER_SIZE = 30

    /** 统计写出的字节数，作为 ZIP 内下一条目的真实偏移。 */
    class CountingOutputStream(private val out: OutputStream) : OutputStream() {
        var count: Long = 0
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }

    /** 计算使 STORED 条目数据区 4 对齐所需的 extra 填充长度；非 STORED 返回 0。 */
    fun alignExtra(name: String, method: Int, pos: Long): Int {
        if (method != ZipEntry.STORED) return 0
        val nameLen = name.toByteArray(Charsets.UTF_8).size
        return ((4 - ((pos + LOC_HEADER_SIZE + nameLen) % 4)) % 4).toInt()
    }

    /**
     * 写入条目；[counter].count 必须等于下一条目 LOCAL 头的起始偏移。
     * STORED 条目自动补齐 size/crc 与 4 字节对齐 extra。
     */
    fun writeEntry(
        zos: ZipOutputStream,
        counter: CountingOutputStream,
        name: String,
        method: Int,
        data: ByteArray,
    ) {
        val extraLen = alignExtra(name, method, counter.count)
        val entry = ZipEntry(name).apply {
            this.method = method
            if (method == ZipEntry.STORED) {
                size = data.size.toLong()
                crc = CRC32().apply { update(data) }.value
                if (extraLen > 0) setExtra(ByteArray(extraLen))
            }
        }
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }
}
