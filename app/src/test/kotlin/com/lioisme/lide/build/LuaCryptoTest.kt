package com.lioisme.lide.build

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaCryptoTest {
    private val magic = LuaCrypto.MAGIC

    @Test
    fun encrypt_prefix_and_body_multiple_of_8() {
        val plain = "print('hello')".toByteArray()
        val enc = LuaCrypto.encrypt(plain)
        assertTrue((enc.size - magic.size) % 8 == 0)
        assertArrayEquals(magic, enc.copyOfRange(0, magic.size))
        assertArrayEquals(plain, LuaCrypto.decrypt(enc))
    }

    @Test
    fun roundtrip_empty() {
        val enc = LuaCrypto.encrypt(ByteArray(0))
        assertEquals("magic + 一整块 PKCS7", 8 + 8, enc.size)
        assertArrayEquals(ByteArray(0), LuaCrypto.decrypt(enc))
    }

    @Test
    fun roundtrip_exact_block_adds_full_padding_block() {
        val plain = ByteArray(16) { it.toByte() }
        val enc = LuaCrypto.encrypt(plain)
        assertEquals(8 + 16 + 8, enc.size)
        assertArrayEquals(plain, LuaCrypto.decrypt(enc))
    }

    @Test
    fun decrypt_non_magic_returns_null() {
        assertNull(LuaCrypto.decrypt(byteArrayOf(1, 2, 3, 4)))
        assertNull(LuaCrypto.decrypt(ByteArray(32)))
    }

    @Test
    fun is_encrypted_flags_and_short_guard() {
        assertTrue(LuaCrypto.isEncrypted(magic))
        assertFalse(LuaCrypto.isEncrypted(ByteArray(magic.size - 1)))
        val tampered = byteArrayOf(0x4C, 0x49, 0x44, 0x45, 0x02, 0, 0, 0)
        assertFalse(LuaCrypto.isEncrypted(tampered))
    }

    @Test
    fun cross_engine_known_answer_vector() {
        // 运行端 C（lide_des.c，经 FIPS 向量验证）对同样输入产生的密文：
        // 锁死打包端 Kotlin 与运行端 C 的字节级一致。
        val plain = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 0)
        val expected =
            byteArrayOf(
                0x4C,
                0x49,
                0x44,
                0x45,
                0x01,
                0x00,
                0x00,
                0x00, // MAGIC
                0xDA.toByte(),
                0x39.toByte(),
                0x94.toByte(),
                0x7C.toByte(),
                0x37.toByte(),
                0xF7.toByte(),
                0x84.toByte(),
                0x63.toByte(),
                0xA2.toByte(),
                0xA8.toByte(),
                0x37.toByte(),
                0x91.toByte(),
                0x27.toByte(),
                0x0E.toByte(),
                0x91.toByte(),
                0xCB.toByte(),
            )
        assertArrayEquals(expected, LuaCrypto.encrypt(plain))
        assertArrayEquals(plain, LuaCrypto.decrypt(expected))
    }

    @Test
    fun build_opts_defaults_compile_and_encrypt_on() {
        val opts = BuildOpts()
        assertTrue(opts.compileLua)
        assertTrue(opts.encryptBytecode)
        assertTrue(BuildOpts(compileLua = false).compileLua.not())
        assertTrue(BuildOpts(encryptBytecode = false).encryptBytecode.not())
    }
}
