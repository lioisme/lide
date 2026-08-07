package com.lioisme.lide.build

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 应用内自生成签名（零依赖手写 X.509 v3）验证：
 * 生成 → 平台解析 → 密钥库回环 → v1 签名校验。
 */
class SigningCryptoTest {
    private val password = "lide-gen-pass-123".toCharArray()

    @Test
    fun `generated store is a valid pkcs12 with expected alias`() {
        val gen = SigningCrypto.generate("Demo Key", "demoalias", password)

        val ks = KeyStore.getInstance("PKCS12")
        ks.load(gen.storeBytes.inputStream(), password)
        assertTrue(ks.containsAlias("demoalias"))
        assertEquals("RSA", ks.getKey("demoalias", password)?.algorithm)
        assertArrayEquals(gen.cert.encoded, ks.getCertificate("demoalias")!!.encoded)
    }

    @Test
    fun `generated certificate parses via platform and self-verifies`() {
        val gen = SigningCrypto.generate("Demo Key")

        val parsed =
            java.security.cert.CertificateFactory
                .getInstance("X.509")
                .generateCertificate(gen.cert.encoded.inputStream())
        // 回环解析出的证书与生成时一致，且平台能核验自签签名（DER 完整性双保险）。
        gen.cert.verify(parsed.publicKey)
        gen.cert.checkValidity()
        assertEquals("RSA", parsed.publicKey.algorithm)
    }

    @Test
    fun `resolveMaterial roundtrips generated keystore`() {
        val gen = SigningCrypto.generate("Roundtrip Key", "rt", password)

        val material = SigningCrypto.resolveMaterial(gen.storeBytes, password, "rt", password)
        assertEquals("RSA", material.key.algorithm)
        assertArrayEquals(gen.cert.encoded, material.cert.encoded)
    }

    @Test
    fun `listAliases shows fingerprint and private key`() {
        val gen = SigningCrypto.generate("List Key", "lk", password)

        val aliases = SigningCrypto.listAliases(gen.storeBytes, password)
        assertEquals(1, aliases.size)
        val info = aliases.single()
        assertEquals("lk", info.alias)
        assertEquals("RSA", info.algorithm)
        assertTrue(info.hasPrivateKey)
        assertEquals(gen.fingerprint, info.certFingerprint)
        assertTrue(gen.fingerprint.contains(":"))
    }

    @Test
    fun `wrong password fails to open generated store`() {
        val gen = SigningCrypto.generate("Locked Key", "lock", password)
        val wrong = "nope-nope".toCharArray()

        try {
            SigningCrypto.listAliases(gen.storeBytes, wrong)
            fail("wrong password should throw")
        } catch (_: Exception) {
            // 预期：密钥库完整性校验失败
        }
    }

    @Test
    fun `apk signed with generated key passes V1Verifier`() {
        val gen = SigningCrypto.generate("Apk Sign Key", "apksign", password)
        val material = SigningCrypto.resolveMaterial(gen.storeBytes, password, "apksign", password)

        val apk = sampleApk()
        val signed = ApkSigner.sign(apk, material.key, material.cert)
        assertTrue(V1Verifier.verify(signed, material.cert))
    }

    @Test
    fun `custom subject dn keeps all fields in standard order`() {
        val subject =
            CertSubject(
                countryCode = "CN",
                state = "Shanghai",
                locality = "Shanghai",
                organization = "Acme Inc",
                organizationUnit = "Dev Team",
                commonName = "Demo CN",
                email = "dev@example.com",
                street = "Main St 1",
                postalCode = "200000",
            )
        val gen = SigningCrypto.generate("Demo Key", "dn", password, subject, 10)

        val name = gen.cert.subjectX500Principal.name
        assertEquals(gen.cert.subjectX500Principal.name, gen.cert.issuerX500Principal.name)
        // RFC 2253 字符串为 DER 顺序的反向（C 在最末）：…,STREET,E,CN,OU,O,L,ST,C
        assertTrue("unexpected order: $name", name.endsWith(",C=CN"))
        assertTrue("missing state: $name", name.contains("ST=Shanghai"))
        assertTrue("missing locality: $name", name.contains("L=Shanghai"))
        assertTrue("missing org: $name", name.contains("O=Acme Inc"))
        assertTrue("missing ou: $name", name.contains("OU=Dev Team"))
        assertTrue("missing cn: $name", name.contains("CN=Demo CN"))
        // emailAddress（1.2.840.113549.1.9.1，IA5String）：OpenJDK 无 RFC 2253 助记符 → 十六进制转储，按内容字节断言。
        val emailHex = "dev@example.com".toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
        assertTrue("missing email: $name", name.contains("1.2.840.113549.1.9.1=") && name.contains(emailHex))
        assertTrue("missing street: $name", name.contains("STREET=Main St 1"))
        assertTrue("missing postal: $name", name.contains("2.5.4.17="))
        assertTrue("street should precede cn: $name", name.indexOf("STREET=Main St 1") < name.indexOf("CN=Demo CN"))
        assertTrue("locality should precede state: $name", name.indexOf("L=Shanghai") < name.indexOf("ST=Shanghai"))
        gen.cert.verify(gen.cert.publicKey)
        gen.cert.checkValidity()
    }

    @Test
    fun `blank subject fields are skipped and cn falls back to name`() {
        val gen = SigningCrypto.generate("Fallback Name", "fb", password, CertSubject())
        assertEquals("CN=Fallback Name", gen.cert.subjectX500Principal.name)
    }

    @Test
    fun `custom validity is respected`() {
        val gen = SigningCrypto.generate("Val Key", "val", password, validYears = 2)
        val days =
            (gen.cert.notAfter.time - gen.cert.notBefore.time) / 86_400_000L
        assertTrue("expected ~730 days, got $days", days in 729..732)
        gen.cert.checkValidity()
    }

    @Test
    fun `invalid validity is rejected`() {
        try {
            SigningCrypto.generate("Bad Key", "bad", password, validYears = 0)
            fail("validYears=0 should throw")
        } catch (_: IllegalArgumentException) {
            // 预期
        }
        try {
            SigningCrypto.generate("Bad Key", "bad", password, validYears = 1001)
            fail("validYears=1001 should throw")
        } catch (_: IllegalArgumentException) {
            // 预期
        }
    }

    private fun sampleApk(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            val bytes = "payload".toByteArray()
            val entry =
                ZipEntry("assets/lua/main.lua").apply {
                    method = ZipEntry.DEFLATED
                }
            zos.putNextEntry(entry)
            zos.write(bytes)
            zos.closeEntry()
        }
        return out.toByteArray()
    }
}
