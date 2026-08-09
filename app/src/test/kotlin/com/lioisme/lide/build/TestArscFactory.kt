package com.lioisme.lide.build

import java.io.ByteArrayOutputStream

/**
 * 合成最小 resources.arsc（纯字节，无 Android 依赖）：紧凑 entry 布局，
 * 与真实 aapt2 36 宿主产物（2026-08-09 P11 实证）同构：
 * 顶层 ResTable_header + 全局字符串池(UTF-8/UTF-16) + package chunk
 * （288B 头，typeStrings/keyStrings 相对偏移），内嵌 typeStrings/keyStrings
 * 池 + drawable type chunk（单 entry：esize=8 头 + STRING value）。
 * 供 ArscLookupTest / ApkBuilderTest 混淆宿主用例共用。
 */
internal object TestArscFactory {

    fun syntheticArsc(
        pkgId: Int = 0x7f,
        iconKey: String = "icon.png",
        iconPath: String = "res/drawable/icon.png",
        utf8: Boolean = true,
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // ---- 顶层 ResTable_header ----
        // 暂先只写 header type/size 占位，最后回填。
        val headerOff = out.size()
        u16(out, 0x0002)
        u16(out, 12)
        u32(out, 0) // size 占位（最后回填）
        u32(out, 1) // packageCount

        // ---- 全局字符串池（icon 路径） ----
        val gpoolOff = out.size()
        writePool(out, listOf(iconPath), utf8)
        val gpoolSize = out.size() - gpoolOff

        // ---- package chunk ----
        val pkgOff = out.size()
        u16(out, 0x0200)
        u16(out, 288) // headerSize
        u32(out, 0) // size 占位
        u32(out, pkgId)
        repeat(128) { u16(out, 0) } // name[128]
        val typeStringsOff = 288
        u32(out, typeStringsOff) // @268
        u32(out, 0) // lastPublicType @272
        u32(out, 0) // keyStrings @276 占位
        u32(out, 0) // lastPublicKey @280
        u32(out, 0) // typeIdOffset @284

        // typeStrings pool（@pkg+288）
        val tsOff = out.size()
        writePool(out, listOf("drawable"), utf8)
        val tsSize = out.size() - tsOff
        // keyStrings pool（紧随）
        val ksOff = out.size()
        writePool(out, listOf(iconKey), utf8)
        val ksSize = out.size() - ksOff
        val keyStringsOff = ksOff - pkgOff
        // 回填 keyStrings 偏移（@pkg+276）
        patchU32(out, pkgOff + 276, keyStringsOff)

        // ---- drawable type chunk（id=1，单 entry，紧凑布局） ----
        // entriesStart = 224（真实 aapt2 36：56 头 + offset 数组区全 0 + 数据 gap），
        // offset 区（56..196）保持全 0，解析器据此判定紧凑布局。
        val typeOff = out.size()
        u16(out, 0x0201)
        u16(out, 56) // headerSize
        u32(out, 0) // size 占位
        out.write(1) // id = typeNames indexOf("drawable")+1 = 1
        out.write(0) // flags
        u16(out, 0) // reserved
        u32(out, 1) // entryCount
        u32(out, 224) // entriesStart
        repeat(36) { out.write(0) } // config
        repeat(168) { out.write(0) } // offset 数组区 + gap（全 0）
        // entry: u16 size(8) + u16 flags(0) + u32 key(0) + Res_value
        u16(out, 8)
        u16(out, 0)
        u32(out, 0) // key = keyStrings[0]
        u16(out, 8) // Res_value.size
        out.write(0) // res0
        out.write(0x03) // TYPE_STRING
        u32(out, 0) // data = 全局池[0] = iconPath
        val typeSize = out.size() - typeOff
        patchU32(out, typeOff + 4, typeSize)

        // ---- 回填 chunk sizes ----
        patchU32(out, headerOff + 4, out.size())
        patchU32(out, pkgOff + 4, 288 + tsSize + ksSize + typeSize)
        val bytes = out.toByteArray()
        return bytes
    }

    private fun writePool(
        out: ByteArrayOutputStream,
        strings: List<String>,
        utf8: Boolean,
    ) {
        val dataStart = 28 + 4 * strings.size
        // 先编码字符串 payload（offsets 相对数据区起点 = dataStart）。
        val offsets = ArrayList<Int>(strings.size)
        var cursor = 0
        val payload = ByteArrayOutputStream()
        for (s in strings) {
            offsets.add(cursor)
            if (utf8) {
                val bytes = s.toByteArray(Charsets.UTF_8)
                payload.write(bytes.size.coerceAtMost(0x7F)) // 长度前缀（简单场景 < 128）
                payload.write(bytes.size)
                payload.write(bytes)
                cursor += 2 + bytes.size
            } else {
                val bytes = s.toByteArray(Charsets.UTF_16LE)
                u16(payload, bytes.size / 2) // code unit 数
                payload.write(bytes)
                cursor += 2 + bytes.size
            }
        }
        val body = payload.toByteArray()
        // 完整池体一次性写入（header + offsets 数组 + 数据区）。
        val padded = ByteArray(dataStart + cursor)
        u16(padded, 0, 0x0001)
        u16(padded, 2, 28)
        u32(padded, 4, padded.size)
        u32(padded, 8, strings.size)
        u32(padded, 12, 0) // styleCount
        u32(padded, 16, if (utf8) 0x100 else 0)
        u32(padded, 20, dataStart)
        u32(padded, 24, 0) // stylesStart
        for (i in offsets.indices) {
            u32(padded, 28 + 4 * i, offsets[i])
        }
        System.arraycopy(body, 0, padded, dataStart, body.size)
        out.write(padded)
    }

    private fun u16(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
    }

    private fun u16(buf: ByteArray, off: Int, v: Int) {
        buf[off] = (v and 0xFF).toByte()
        buf[off + 1] = ((v ushr 8) and 0xFF).toByte()
    }

    private fun u32(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 24) and 0xFF)
    }

    private fun u32(buf: ByteArray, off: Int, v: Int) {
        buf[off] = (v and 0xFF).toByte()
        buf[off + 1] = ((v ushr 8) and 0xFF).toByte()
        buf[off + 2] = ((v ushr 16) and 0xFF).toByte()
        buf[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }

    private fun patchU32(out: ByteArrayOutputStream, off: Int, v: Int) {
        val bytes = out.toByteArray()
        u32(bytes, off, v)
        out.reset()
        out.write(bytes)
    }
}
