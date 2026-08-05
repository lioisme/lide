package com.lioisme.lide.build

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * APK v1（JAR）+ v2（APK Signature Scheme）签名器（纯 JDK，无第三方依赖）。
 *
 * - MANIFEST.MF：逐条目 SHA-256 摘要（未压缩内容）；
 * - CERT.SF：MANIFEST.MF 全文摘要 + 逐条目 section 摘要；
 * - CERT.RSA：手写 DER 的 CMS PKCS#7 SignedData，含 authenticatedAttributes
 *   （contentType + messageDigest），签名输入为 SET OF Attribute 的 DER 编码，
 *   与 libcore 的 JAR 校验（sun.security PKCS7）语义一致。
 * - 签名摘要算法 sha256，加密算法 RSA PKCS1v15（测试钥匙为 RSA-2048）。
 * - 最后叠加 v2 签名块（[V2Signer]）——Android 11+ 安装 targetSdk>=30 的
 *   APK 强制要求 v2+，仅 v1 会被拒绝（INSTALL_PARSE_FAILED_NO_CERTIFICATES）。
 */
object ApkSigner {
    private val B64 = Base64Compat

    /** 对 [apk] 全量重写并追加 META-INF 签名三件套；跳过原有 META-INF 条目；最后加 v2 块。 */
    fun sign(
        apk: ByteArray,
        key: PrivateKey,
        cert: X509Certificate,
    ): ByteArray {
        val v1 = signV1(apk, key, cert)
        return V2Signer.sign(v1, key, cert)
    }

    private fun signV1(
        apk: ByteArray,
        key: PrivateKey,
        cert: X509Certificate,
    ): ByteArray {
        val entries = readZip(apk)
        val out = ByteArrayOutputStream()
        val counter = ZipUtils.CountingOutputStream(out)
        ZipOutputStream(counter).use { zos ->
            val manifestSections = LinkedHashMap<String, ByteArray>()
            for ((name, method, data) in entries) {
                if (name.startsWith("META-INF/")) continue
                manifestSections[name] = sha256(data)
                ZipUtils.writeEntry(zos, counter, name, method, data)
            }
            val sections = buildSections(manifestSections)
            val manifest = buildManifest(sections)
            ZipUtils.writeEntry(zos, counter, "META-INF/MANIFEST.MF", ZipEntry.DEFLATED, manifest)
            val sf = buildSf(manifest, sections)
            ZipUtils.writeEntry(zos, counter, "META-INF/CERT.SF", ZipEntry.DEFLATED, sf)
            val sig = buildSignature(sf, key, cert)
            ZipUtils.writeEntry(zos, counter, "META-INF/CERT.RSA", ZipEntry.DEFLATED, sig)
        }
        return out.toByteArray()
    }

    private fun readZip(apk: ByteArray): List<Triple<String, Int, ByteArray>> {
        val out = ArrayList<Triple<String, Int, ByteArray>>()
        ZipInputStream(apk.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val data = zis.readBytes()
                out += Triple(entry.name, entry.method, data)
            }
        }
        return out
    }

    private fun buildSections(manifestSections: Map<String, ByteArray>): List<Pair<String, ByteArray>> =
        manifestSections.map { (name, digest) ->
            val sb = StringBuilder()
            appendLine(sb, "Name: $name")
            appendLine(sb, "SHA-256-Digest: ${B64.encodeToString(digest)}")
            sb.append("\r\n")
            name to sb.toString().toByteArray(Charsets.UTF_8)
        }

    private fun buildManifest(sections: List<Pair<String, ByteArray>>): ByteArray {
        val sb = StringBuilder()
        appendLine(sb, "Manifest-Version: 1.0")
        appendLine(sb, "Created-By: 1.0 (lide)")
        sb.append("\r\n")
        for ((_, section) in sections) sb.append(String(section, Charsets.UTF_8))
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun buildSf(
        manifest: ByteArray,
        sections: List<Pair<String, ByteArray>>,
    ): ByteArray {
        val sb = StringBuilder()
        appendLine(sb, "Signature-Version: 1.0")
        appendLine(sb, "SHA-256-Digest-Manifest: ${B64.encodeToString(sha256(manifest))}")
        sb.append("\r\n")
        for ((name, section) in sections) {
            appendLine(sb, "Name: $name")
            appendLine(sb, "SHA-256-Digest-Manifest: ${B64.encodeToString(sha256(section))}")
            sb.append("\r\n")
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    /** 72 字节行宽折行（JAR 规范，SHA-256 摘要 44 字符，仅长 Name 需要续行）。 */
    private fun appendLine(
        sb: StringBuilder,
        line: String,
    ) {
        var rest = line
        while (rest.length > 70) {
            sb.append(rest, 0, 70).append("\r\n ")
            rest = rest.substring(70)
        }
        sb.append(rest).append("\r\n")
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    // ===== CMS PKCS#7 =====

    private fun buildSignature(
        sf: ByteArray,
        key: PrivateKey,
        cert: X509Certificate,
    ): ByteArray {
        val contentDigest = sha256(sf)

        val oidSha256 = Der.oid(2, 16, 840, 1, 101, 3, 4, 2, 1)
        val oidRsa = Der.oid(1, 2, 840, 113549, 1, 1, 1)
        val oidData = Der.oid(1, 2, 840, 113549, 1, 7, 1)
        val oidSignedData = Der.oid(1, 2, 840, 113549, 1, 7, 2)
        val oidContentType = Der.oid(1, 2, 840, 113549, 1, 9, 3)
        val oidMessageDigest = Der.oid(1, 2, 840, 113549, 1, 9, 4)

        val digestAlgo = Der.seq(oidSha256, Der.null0())
        val encryptAlgo = Der.seq(oidRsa, Der.null0())

        val contentTypeAttr = Der.seq(oidContentType, Der.set(oidData))
        val messageDigestAttr = Der.seq(oidMessageDigest, Der.set(Der.octet(contentDigest)))
        // 签名输入 = 完整 SET OF Attribute 的 DER 编码（含 0x31 头）。
        val signedAttrsSet = Der.set(contentTypeAttr, messageDigestAttr)
        // authenticatedAttributes 是 [0] IMPLICIT SET OF：内容 = 无 SET 头的原始
        // Attribute 序列（A0 头即取代 0x31），与 JDK/libcore/apksig 读取约定一致。
        val signedAttrsRaw = Der.raw(contentTypeAttr, messageDigestAttr)

        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(key)
        signer.update(signedAttrsSet)
        val signature = signer.sign()

        val issuerAndSerial =
            Der.seq(
                cert.issuerX500Principal.encoded,
                Der.int(cert.serialNumber.toByteArray()),
            )

        val signerInfo =
            Der.seq(
                Der.int(byteArrayOf(0x01)),
                issuerAndSerial,
                digestAlgo,
                Der.tlv(0xA0, signedAttrsRaw),
                encryptAlgo,
                Der.octet(signature),
            )

        val signedData =
            Der.seq(
                Der.int(byteArrayOf(0x01)),
                Der.set(digestAlgo),
                Der.seq(oidData),
                Der.tlv(0xA0, cert.encoded),
                Der.set(signerInfo),
            )

        return Der.seq(oidSignedData, Der.tlv(0xA0, signedData))
    }

    /** 最小 DER 编码器。 */
    private object Der {
        fun tlv(
            tag: Int,
            content: ByteArray,
        ): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(tag)
            val len = content.size
            when {
                len < 0x80 -> {
                    out.write(len)
                }

                len < 0x100 -> {
                    out.write(0x81)
                    out.write(len)
                }

                len < 0x10000 -> {
                    out.write(0x82)
                    out.write(len shr 8)
                    out.write(len and 0xFF)
                }

                else -> {
                    out.write(0x83)
                    out.write(len shr 16)
                    out.write(len shr 8)
                    out.write(len and 0xFF)
                }
            }
            out.write(content)
            return out.toByteArray()
        }

        fun seq(vararg parts: ByteArray): ByteArray = tlv(0x30, concat(parts))

        fun set(vararg parts: ByteArray): ByteArray = tlv(0x31, concat(parts))

        fun raw(vararg parts: ByteArray): ByteArray = concat(parts)

        fun octet(b: ByteArray): ByteArray = tlv(0x04, b)

        fun null0(): ByteArray = byteArrayOf(0x05, 0x00)

        fun int(v: ByteArray): ByteArray = tlv(0x02, v)

        fun oid(
            first: Int,
            second: Int,
            vararg rest: Int,
        ): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(40 * first + second)
            for (arc in rest) writeBase128(out, arc)
            return tlv(0x06, out.toByteArray())
        }

        private fun writeBase128(
            out: ByteArrayOutputStream,
            v: Int,
        ) {
            val groups = ArrayList<Int>()
            var value = v
            groups.add(value and 0x7F)
            value = value ushr 7
            while (value > 0) {
                groups.add(0, value and 0x7F)
                value = value ushr 7
            }
            for (i in groups.indices) {
                out.write(if (i < groups.lastIndex) groups[i] or 0x80 else groups[i])
            }
        }

        private fun concat(parts: Array<out ByteArray>): ByteArray {
            val out = ByteArrayOutputStream()
            for (p in parts) out.write(p)
            return out.toByteArray()
        }
    }
}
