package com.lioisme.lide.build

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate

/**
 * APK Signature Scheme v2 签名器（纯 JDK，无第三方依赖）。
 *
 * 结构（与 apksig/apksigner 逐字节一致）：
 * - 签名块位于最后一个条目与中央目录之间：
 *   [u64 size][pair: u64 size | u32 id | value][u64 size]["APK Sig Block 42"]
 *   size = 总块大小 - 8；v2 pair id = 0x7109871A。
 * - value = [u32 len][signer]，signer = [u32 len][signedData][u32 len][signatures]
 *   [u32 len][publicKey]（v2 的 signer 只有三个字段）。
 *   signedData = [u32 len][digests][u32 len][certs][u32 len=0][attrs(空)]，
 *   digests = [u32 len][u32 签名算法 id][u32 len][整包摘要]，
 *   certs = [u32 len][证书 DER]（证书序列，无外层长度）。
 * - 签名输入 = signedData 结构字节（含其自身 u32 长度前缀）。
 * - 整包内容摘要（CHUNKED_SHA256，id 0x0100）：
 *   各段（条目区 / 中央目录 / EOCD）按 1MB 分块（块不跨段），
 *   每块摘要 = SHA-256(0xA5 || u32le(块长) || 块)，
 *   整包摘要 = SHA-256(0x5A || u32le(块数) || 各块摘要)。
 *   注意：EOCD 的中央目录偏移字段按原值参与摘要 —— 校验端会把该字段
 *   改写成签名块起点偏移（即原 CD 偏移），因此签名端原样使用未打补丁的
 *   EOCD 即可，无循环依赖。
 * - 算法：RSA = 0x0103 (PKCS1v15-SHA256)，EC = 0x0201 (ECDSA-SHA256)。
 *   注意：新版 v2 规范（2023+）中 0x0101 已改为 PSS-SHA256，PKCS1-SHA256 为 0x0103。
 */
object V2Signer {

    private const val MAGIC = "APK Sig Block 42"
    private const val V2_BLOCK_ID = 0x7109871A
    private const val ALGO_RSA_PKCS1_SHA256 = 0x0103
    private const val ALGO_ECDSA_SHA256 = 0x0201
    private const val CHUNK_SIZE = 1 shl 20

    /** 对 [apk]（v1 签名完成的整包字节）插入 v2 签名块并改写 EOCD 偏移。 */
    fun sign(apk: ByteArray, key: PrivateKey, cert: X509Certificate): ByteArray {
        val eocd = findEocd(apk) ?: throw IllegalArgumentException("EOCD not found in APK")
        val cdOffset = eocd.cdOffset

        val sections = listOf(
            apk.copyOfRange(0, cdOffset),
            apk.copyOfRange(cdOffset, eocd.offset),
            apk.copyOfRange(eocd.offset, apk.size),
        )
        val wholeFileDigest = chunkedSha256(sections)

        val certBytes = cert.encoded
        val (algoId, jca) = when (key.algorithm) {
            "EC" -> ALGO_ECDSA_SHA256 to "SHA256withECDSA"
            else -> ALGO_RSA_PKCS1_SHA256 to "SHA256withRSA"
        }
        // digests 记录里的 int 是签名算法 id（与 signatures 记录一致，由校验端交叉核对）
        val digests = concat(
            u32(4 + 4 + 32),
            u32(algoId),
            u32(32),
            wholeFileDigest,
        )
        val certs = concat(u32(certBytes.size), certBytes)
        // signedData = [u32 len][digests][u32 len][certs][u32 len=0][attrs(空)]，无外层长度
        val signedData = concat(u32(digests.size), digests, u32(certs.size), certs, u32(0))

        val signature = Signature.getInstance(jca).run {
            initSign(key)
            update(signedData)
            sign()
        }
        val signatures = concat(u32(4 + 4 + signature.size), u32(algoId), u32(signature.size), signature)

        val pubKey = cert.publicKey.encoded
        // signer = [u32 len][signedData][u32 len][signatures][u32 len][publicKey]（v2 无 certificates / attrs 字段）
        val signer = concat(
            u32(signedData.size), signedData,
            u32(signatures.size), signatures,
            u32(pubKey.size), pubKey,
        )
        // value = [u32 外层长度][u32 signer 长度][signer]（两层，与 apksig 一致）
        val value = concat(u32(4 + signer.size), u32(signer.size), signer)
        val pair = concat(u64(4L + value.size), u32(V2_BLOCK_ID), value)
        val blockSize = 8L + pair.size + 8L + 16L
        val block = ByteArrayOutputStream()
        block.write(u64(blockSize - 8))
        block.write(pair)
        block.write(u64(blockSize - 8))
        block.write(MAGIC.toByteArray(Charsets.US_ASCII))
        val blockBytes = block.toByteArray()

        val out = ByteArray(apk.size + blockBytes.size)
        // 布局：[条目区][签名块][中央目录][EOCD']，签名块插在中央目录之前。
        System.arraycopy(apk, 0, out, 0, cdOffset)
        System.arraycopy(blockBytes, 0, out, cdOffset, blockBytes.size)
        System.arraycopy(apk, cdOffset, out, cdOffset + blockBytes.size, apk.size - cdOffset)
        // EOCD 中央目录偏移字段（记录内偏移 16）指向签名块之后。
        writeU32(out, eocd.offset + blockBytes.size + 16, cdOffset + blockBytes.size)
        return out
    }

    /** 整包内容摘要：按段分块，块不跨段。 */
    private fun chunkedSha256(sections: List<ByteArray>): ByteArray {
        val chunkDigests = ArrayList<ByteArray>()
        for (section in sections) {
            var off = 0
            while (off < section.size) {
                val len = minOf(CHUNK_SIZE, section.size - off)
                val md = MessageDigest.getInstance("SHA-256")
                md.update(0xA5.toByte())
                md.update(u32(len))
                md.update(section, off, len)
                chunkDigests += md.digest()
                off += len
            }
        }
        val whole = MessageDigest.getInstance("SHA-256")
        whole.update(0x5A.toByte())
        whole.update(u32(chunkDigests.size))
        for (cd in chunkDigests) whole.update(cd)
        return whole.digest()
    }

    private class Eocd(val offset: Int, val cdOffset: Int)

    private fun findEocd(apk: ByteArray): Eocd? {
        val limit = maxOf(0, apk.size - 22 - 0xFFFF)
        var i = apk.size - 22
        while (i >= limit) {
            if (apk[i] == 0x50.toByte() && apk[i + 1] == 0x4B.toByte() &&
                apk[i + 2] == 0x05.toByte() && apk[i + 3] == 0x06.toByte()
            ) {
                val commentLen = u16(apk, i + 20)
                if (i + 22 + commentLen == apk.size) {
                    return Eocd(i, u32(apk, i + 16))
                }
            }
            i--
        }
        return null
    }

    private fun concat(vararg parts: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        for (p in parts) out.write(p)
        return out.toByteArray()
    }

    private fun u32(v: Int): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v ushr 8) and 0xFF).toByte(),
        ((v ushr 16) and 0xFF).toByte(),
        ((v ushr 24) and 0xFF).toByte(),
    )

    private fun u64(v: Long): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v ushr 8) and 0xFF).toByte(),
        ((v ushr 16) and 0xFF).toByte(),
        ((v ushr 24) and 0xFF).toByte(),
        ((v ushr 32) and 0xFF).toByte(),
        ((v ushr 40) and 0xFF).toByte(),
        ((v ushr 48) and 0xFF).toByte(),
        ((v ushr 56) and 0xFF).toByte(),
    )

    private fun u32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun u16(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    private fun writeU32(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v ushr 8) and 0xFF).toByte()
        b[off + 2] = ((v ushr 16) and 0xFF).toByte()
        b[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }
}
