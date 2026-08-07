package com.lioisme.lide.build

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ApkSignerTest {
    private val key: java.security.PrivateKey by lazy {
        val der = loadResource("keys/testkey.pk8")
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
    }

    private val cert: java.security.cert.X509Certificate by lazy {
        val pem = String(loadResource("keys/testkey.x509.pem"), Charsets.UTF_8)
        CertificateFactory
            .getInstance("X.509")
            .generateCertificate(pem.byteInputStream()) as java.security.cert.X509Certificate
    }

    private fun loadResource(name: String): ByteArray = checkNotNull(checkNotNull(javaClass.classLoader).getResourceAsStream(name)).readBytes()

    private fun sampleApk(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            fun put(
                name: String,
                content: String,
                stored: Boolean = false,
            ) {
                val bytes = content.toByteArray()
                val entry =
                    ZipEntry(name).apply {
                        method = if (stored) ZipEntry.STORED else ZipEntry.DEFLATED
                        if (stored) {
                            size = bytes.size.toLong()
                            crc = CRC32().apply { update(bytes) }.value
                        }
                    }
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
            put("AndroidManifest.xml", "binary-manifest-bytes", stored = true)
            put("classes.dex", "dex-bytes-here")
            put("assets/lua/main.lua", "bytecode-bytes")
            put("META-INF/SERVICE.SF", "stale-signature") // 旧签名应被剔除
        }
        return out.toByteArray()
    }

    private fun entriesOf(apk: ByteArray): Map<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        ZipInputStream(apk.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                map[entry.name] = zis.readBytes()
            }
        }
        return map
    }

    @Test
    fun `sign adds META-INF trio and keeps payload`() {
        val signed = ApkSigner.sign(sampleApk(), key, cert)
        val entries = entriesOf(signed)
        assertNotNull(entries["META-INF/MANIFEST.MF"])
        assertNotNull(entries["META-INF/CERT.SF"])
        assertNotNull(entries["META-INF/CERT.RSA"])
        assertArrayEquals("binary-manifest-bytes".toByteArray(), entries["AndroidManifest.xml"])
        assertArrayEquals("bytecode-bytes".toByteArray(), entries["assets/lua/main.lua"])
        assertEquals(null, entries["META-INF/SERVICE.SF"])
    }

    @Test
    fun `manifest contains SHA-256 digests for all payload entries`() {
        val signed = ApkSigner.sign(sampleApk(), key, cert)
        val manifest =
            String(
                entriesOf(signed)["META-INF/MANIFEST.MF"]!!,
                Charsets.UTF_8,
            )
        assertTrue(manifest.contains("Manifest-Version: 1.0"))
        assertTrue(manifest.contains("Name: assets/lua/main.lua"))
        assertTrue(manifest.contains("SHA-256-Digest: "))
        assertTrue(manifest.contains("Name: AndroidManifest.xml"))
    }

    @Test
    fun `CERT SF hashes whole manifest`() {
        val signed = ApkSigner.sign(sampleApk(), key, cert)
        val entries = entriesOf(signed)
        val sf = String(entries["META-INF/CERT.SF"]!!, Charsets.UTF_8)
        assertTrue(sf.contains("SHA-256-Digest-Manifest: "))
        assertTrue(sf.contains("Signature-Version: 1.0"))
    }

    @Test
    fun `V1Verifier accepts genuine signature`() {
        val signed = ApkSigner.sign(sampleApk(), key, cert)
        assertTrue(V1Verifier.verify(signed, cert))
    }

    @Test
    fun `V1Verifier rejects tampered payload`() {
        val signed = ApkSigner.sign(sampleApk(), key, cert)
        val entries = entriesOf(signed)
        val tamperedMap = LinkedHashMap(entries)
        tamperedMap["classes.dex"] = "evil-bytes".toByteArray()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            for ((name, data) in tamperedMap) {
                zos.putNextEntry(ZipEntry(name).apply { method = ZipEntry.DEFLATED })
                zos.write(data)
                zos.closeEntry()
            }
        }
        assertTrue(!V1Verifier.verify(out.toByteArray(), cert))
    }

    @Test
    fun `apksigner verifies v2 signature`() {
        val signed = ApkSigner.sign(sampleApk(), key, cert)
        val output = verifyWithApksigner(signed, minSdk = 24)
        assertTrue(output.contains("Verified using v2 scheme (APK Signature Scheme v2)"))
    }

    @Test
    fun `default scheme v1v2v3 verified via v2 and v3`() {
        val signed = ApkSigner.sign(sampleApk(), key, cert, SigningScheme.V1V2V3)
        val entries = entriesOf(signed)
        assertNotNull(entries["META-INF/MANIFEST.MF"]) // v1 三件套存在
        val output = verifyWithApksigner(signed, minSdk = 24)
        assertTrue(output.contains("Verified using v2 scheme (APK Signature Scheme v2)"))
        assertTrue(output.contains("Verified using v3 scheme (APK Signature Scheme v3)"))
    }

    @Test
    fun `v1v2 scheme keeps jar trio but skips v3 block`() {
        val signed = ApkSigner.sign(sampleApk(), key, cert, SigningScheme.V1V2)
        val entries = entriesOf(signed)
        assertNotNull(entries["META-INF/MANIFEST.MF"])
        val output = verifyWithApksigner(signed, minSdk = 24)
        assertTrue(output.contains("Verified using v2 scheme (APK Signature Scheme v2): true"))
        assertTrue(output.contains("Verified using v3 scheme (APK Signature Scheme v3): false"))
    }

    @Test
    fun `v2 only drops jar trio and v3 block`() {
        val signed = ApkSigner.sign(sampleApk(), key, cert, SigningScheme.V2)
        val entries = entriesOf(signed)
        assertEquals(null, entries["META-INF/MANIFEST.MF"])
        assertEquals(null, entries["META-INF/CERT.SF"])
        val output = verifyWithApksigner(signed, minSdk = 24)
        assertTrue(output.contains("Verified using v2 scheme (APK Signature Scheme v2): true"))
        assertTrue(output.contains("Verified using v1 scheme (JAR signing): false"))
        assertTrue(output.contains("Verified using v3 scheme (APK Signature Scheme v3): false"))
    }

    @Test
    fun `v3 only signs with v3 block only`() {
        val signed = ApkSigner.sign(sampleApk(), key, cert, SigningScheme.V3)
        val entries = entriesOf(signed)
        assertEquals(null, entries["META-INF/MANIFEST.MF"])
        // v3 块内 minSdk 恒 ≥28（apksig processV3Configs），用 28 验证。
        val output = verifyWithApksigner(signed, minSdk = 28)
        assertTrue(output.contains("Verified using v3 scheme (APK Signature Scheme v3): true"))
        assertTrue(output.contains("Verified using v1 scheme (JAR signing): false"))
        assertTrue(output.contains("Verified using v2 scheme (APK Signature Scheme v2): false"))
    }

    @Test
    fun `scheme parse falls back to default on garbage`() {
        assertEquals(SigningScheme.V1V2V3, SigningScheme.parse(null))
        assertEquals(SigningScheme.V1V2V3, SigningScheme.parse(""))
        assertEquals(SigningScheme.V1V2V3, SigningScheme.parse("NOPE"))
        assertEquals(SigningScheme.V2V3, SigningScheme.parse("V2V3"))
    }

    private fun verifyWithApksigner(
        apk: ByteArray,
        minSdk: Int,
    ): String {
        val tmp = java.io.File.createTempFile("v2test", ".apk")
        try {
            tmp.writeBytes(apk)
            val proc =
                ProcessBuilder(
                    "java",
                    "-jar",
                    "D:\\Android\\build-tools\\35.0.0\\lib\\apksigner.jar",
                    "verify",
                    "--verbose",
                    "--min-sdk-version",
                    minSdk.toString(),
                    tmp.absolutePath,
                ).redirectErrorStream(true).start()
            val output = proc.inputStream.readBytes().toString(Charsets.UTF_8)
            val exit = proc.waitFor()
            assertTrue("apksigner exit=$exit:\n$output", exit == 0)
            return output
        } finally {
            tmp.delete()
        }
    }
}
