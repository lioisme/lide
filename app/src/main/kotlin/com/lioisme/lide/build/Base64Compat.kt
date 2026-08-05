package com.lioisme.lide.build

/**
 * API 24 兼容的 Base64 编码器（RFC 4648 标准字母表 + padding）。
 *
 * `java.util.Base64` 仅 API 26+ 可用，而 lide minSdk=24：v1 签名链在 API 24/25
 * 上打包会触发 NoClassDefFoundError。签名流程只用编码方向（MANIFEST/CERT 摘要），
 * 故此处仅实现 encode，输出与 `java.util.Base64.getEncoder().encodeToString`
 * 逐字节一致（纯 Kotlin，无 Android 依赖，可在任何线程/版本调用）。
 */
internal object Base64Compat {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /** 编码 [data] 为 Base64 文本（含必要 '=' padding）。空输入返回空串。 */
    fun encodeToString(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val out = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val hasB1 = i + 1 < data.size
            val hasB2 = i + 2 < data.size
            val b1 = if (hasB1) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (hasB2) data[i + 2].toInt() and 0xFF else 0
            out.append(ALPHABET[b0 ushr 2])
            out.append(ALPHABET[(b0 shl 4 or (b1 ushr 4)) and 0x3F])
            out.append(if (hasB1) ALPHABET[(b1 shl 2 or (b2 ushr 6)) and 0x3F] else '=')
            out.append(if (hasB2) ALPHABET[b2 and 0x3F] else '=')
            i += 3
        }
        return out.toString()
    }
}
