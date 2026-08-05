package com.lioisme.lide.build

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class Base64CompatTest {
    private fun assertMatchesJdk(data: ByteArray) {
        val expected = Base64.getEncoder().encodeToString(data)
        assertEquals(expected, Base64Compat.encodeToString(data))
    }

    @Test
    fun `matches jdk base64 for standard vectors`() {
        assertMatchesJdk("".toByteArray())
        assertMatchesJdk("f".toByteArray())
        assertMatchesJdk("fo".toByteArray())
        assertMatchesJdk("foo".toByteArray())
        assertMatchesJdk("foob".toByteArray())
        assertMatchesJdk("fooba".toByteArray())
        assertMatchesJdk("foobar".toByteArray())
    }

    @Test
    fun `matches jdk base64 for digest-sized random payloads`() {
        val rnd = java.util.Random(42)
        for (bytes in intArrayOf(1, 2, 3, 20, 32, 64, 256, 512)) {
            val data = ByteArray(bytes)
            rnd.nextBytes(data)
            assertMatchesJdk(data)
        }
    }

    @Test
    fun `matches jdk base64 across a byte-value sweep`() {
        val data = ByteArray(256) { it.toByte() }
        assertMatchesJdk(data)
    }

    @Test
    fun `empty input yields empty output`() {
        assertEquals("", Base64Compat.encodeToString(ByteArray(0)))
    }
}
