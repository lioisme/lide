package com.lioisme.lide.build

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 打包端 Lua 字节码加密（DES/ECB/NoPadding + PKCS#7，与运行端 C
 * [app/src/main/cpp/lua/lide_des.c] 严格同一规约）：
 *
 * 产物文件 = 8 字节魔数 `LIDE\x01\0\0\0` || DES-ECB(KEY, PKCS#7(字节码))
 *
 * KEY 固定内置（与 pro crypt DK 一致），不设参数，避免密钥扩散。
 */
object LuaCrypto {
    /** 加密文件魔数（与 C 端 LIDE_MAGIC_LEN 一致）。 */
    val MAGIC: ByteArray = byteArrayOf(0x4C, 0x49, 0x44, 0x45, 0x01, 0x00, 0x00, 0x00)

    private val KEY = byteArrayOf(0xFF.toByte(), 0xDD.toByte(), 0xBB.toByte(), 0x99.toByte(), 0x77.toByte(), 0x55.toByte(), 0x33.toByte(), 0x11.toByte())
    private const val TRANSFORMATION = "DES/ECB/NoPadding"

    private fun cipher(mode: Int): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, SecretKeySpec(KEY, "DES"))
        }

    /** 是否带 lide 加密魔数（O(n) 前缀判定）。 */
    fun isEncrypted(data: ByteArray): Boolean {
        if (data.size < MAGIC.size) return false
        for (i in MAGIC.indices) {
            if (data[i] != MAGIC[i]) return false
        }
        return true
    }

    /** 加密：magic || DES-ECB(PKCS#7(plaintext))。 */
    fun encrypt(plain: ByteArray): ByteArray {
        val padLen = 8 - (plain.size % 8)
        val padded = plain.copyOf(plain.size + padLen)
        for (i in plain.size until padded.size) padded[i] = padLen.toByte()
        val secret = cipher(Cipher.ENCRYPT_MODE).doFinal(padded)
        return MAGIC + secret
    }

    /** 解密：仅当带魔数时返回明文（去 PKCS#7），否则返回 null。 */
    fun decrypt(data: ByteArray): ByteArray? {
        if (!isEncrypted(data)) return null
        val body = data.copyOfRange(MAGIC.size, data.size)
        if (body.isEmpty() || body.size % 8 != 0) return null
        val plain = cipher(Cipher.DECRYPT_MODE).doFinal(body)
        val pad = plain.last().toInt() and 0xFF
        if (pad < 1 || pad > 8 || pad > plain.size) return null
        for (i in 1..pad) {
            if ((plain[plain.size - i].toInt() and 0xFF) != pad) return null
        }
        return plain.copyOfRange(0, plain.size - pad)
    }
}
