package com.lioisme.lide.build

import java.security.MessageDigest
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.zip.ZipInputStream

/**
 * 独立 v1 签名校验器（仅测试用，刻意不复用签名器内部实现）：
 * 1. MANIFEST.MF 逐条目 SHA-256 与文件内容比对；
 * 2. CERT.SF 的 Digest-Manifest 与 MANIFEST.MF 比对；
 * 3. 解析 CERT.RSA（PKCS#7 CMS），用证书公钥验证签名：
 *    - 有 authenticatedAttributes（[0] IMPLICIT 或 SEQUENCE 编码）时，
 *      签名消息 = 重建的 SET OF Attribute DER 编码，且 messageDigest 属性
 *      必须等于 SF 摘要（旧自研签名器输出形态）；
 *    - 无 authenticatedAttributes 时（apksig 官方输出形态，见
 *      Pkcs7Encoder），签名消息 = SF 文件字节本身；
 * 4. MANIFEST/SF 均按 JAR 规范处理续行（行首空格折叠，72 字符折行）。
 */
object V1Verifier {
    private val B64: Base64.Decoder = Base64.getDecoder()

    fun verify(
        apk: ByteArray,
        cert: X509Certificate,
    ): Boolean {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(apk.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                entries[entry.name] = zis.readBytes()
            }
        }
        val manifest = entries["META-INF/MANIFEST.MF"] ?: return false
        val sf = entries["META-INF/CERT.SF"] ?: return false
        val rsa = entries["META-INF/CERT.RSA"] ?: return false

        if (!verifyManifestSections(manifest, entries)) return false
        if (!verifySfDigests(sf, manifest)) return false
        return verifyCms(rsa, sf, cert)
    }

    private fun verifyManifestSections(
        manifest: ByteArray,
        entries: Map<String, ByteArray>,
    ): Boolean {
        for (section in parseSections(manifest)) {
            val name = section.name ?: continue // header 段无摘要
            val digest = section.attrs["SHA-256-Digest"] ?: return false
            val data = entries[name] ?: return false
            if (!MessageDigest.isEqual(B64.decode(digest), sha256(data))) return false
        }
        return true
    }

    private fun verifySfDigests(
        sf: ByteArray,
        manifest: ByteArray,
    ): Boolean {
        val sfSections = parseSections(sf)
        val header = sfSections.firstOrNull { it.name == null } ?: return false
        val whole = header.attrs["SHA-256-Digest-Manifest"] ?: return false
        if (!MessageDigest.isEqual(B64.decode(whole), sha256(manifest))) return false
        val manifestSections = parseSections(manifest)
        // 逐条目摘要（若 SF 写入）：SHA-256-Digest-Manifest（旧格式，对折叠后
        // 文本摘要）或 SHA-256-Digest（apksig 格式，对原始 section 字节摘要）。
        for (section in sfSections) {
            val name = section.name ?: continue
            val wantManifest = section.attrs["SHA-256-Digest-Manifest"]
            val wantRaw = section.attrs["SHA-256-Digest"]
            if (wantManifest == null && wantRaw == null) continue
            val target =
                manifestSections.firstOrNull { it.name == name }
                    ?: return false
            val want = wantManifest ?: wantRaw!!
            val actual = sha256(if (wantManifest != null) target.unfoldedBytes else target.rawBytes)
            if (!MessageDigest.isEqual(B64.decode(want), actual)) return false
        }
        return true
    }

    /** 解析 MANIFEST/SF 的 section 列表（处理 72 字符折行续行）。 */
    private fun parseSections(text: ByteArray): List<ManifestSection> {
        val str = String(text, Charsets.UTF_8)
        val out = ArrayList<ManifestSection>()
        val lines = str.split("\r\n")
        var i = 0
        // header 段（首个非空行不是 "Name: " 起头时为 header）
        var headerAttrs: HashMap<String, String>? = null
        if (lines.isNotEmpty() && !lines[0].startsWith("Name: ")) {
            val attrs = HashMap<String, String>()
            while (i < lines.size && lines[i].isNotEmpty()) {
                val kv = lines[i].split(": ", limit = 2)
                if (kv.size == 2) attrs[kv[0]] = kv[1]
                i++
            }
            headerAttrs = attrs
            i++ // 跳过空行
        }
        while (i < lines.size) {
            if (!lines[i].startsWith("Name: ")) {
                i++
                continue
            }
            // section 起止（原始字节区间，含尾部空行，供 apksig 格式摘要比对）。
            val rawStart = lineOffset(lines, i)
            val attrs = HashMap<String, String>()
            val unfolded = StringBuilder(lines[i])
            i++
            while (i < lines.size && lines[i].isNotEmpty()) {
                if (lines[i].startsWith(" ")) {
                    // 续行：折叠（去掉换行与行首空格，追加到前一行文本）。
                    unfolded.append(lines[i].substring(1))
                } else {
                    unfolded.append("\r\n").append(lines[i])
                    val kv = lines[i].split(": ", limit = 2)
                    if (kv.size == 2) attrs[kv[0]] = kv[1]
                }
                i++
            }
            val end =
                if (i < lines.size) {
                    val idx = str.indexOf("\r\n\r\n", rawStart)
                    if (idx < 0) str.length else idx + 4
                } else {
                    str.length
                }
            out +=
                ManifestSection(
                    name = unfolded.substring(6, unfolded.indexOf('\r')),
                    attrs = attrs,
                    unfoldedBytes = (unfolded.toString() + "\r\n").toByteArray(Charsets.UTF_8),
                    rawBytes = str.substring(rawStart, end).toByteArray(Charsets.UTF_8),
                )
            i++
        }
        if (headerAttrs != null) {
            out.add(0, ManifestSection(null, headerAttrs, ByteArray(0), ByteArray(0)))
        }
        return out
    }

    /** 第 [i] 行在原文中的字节偏移（split("\r\n") 前的行首位置）。 */
    private fun lineOffset(
        lines: List<String>,
        i: Int,
    ): Int {
        var pos = 0
        for (k in 0 until i) pos += lines[k].length + 2
        return pos
    }

    private fun verifyCms(
        rsa: ByteArray,
        sf: ByteArray,
        cert: X509Certificate,
    ): Boolean {
        val contentInfo = Der.read(rsa, 0) ?: return false
        val ciParts = Der.children(contentInfo.content, 0) ?: return false
        if (ciParts.size < 2) return false
        // ContentInfo.contentType 必须是 signedData（1.2.840.113549.1.7.2）
        if (Der.encodeOidString(ciParts[0].content) != "1.2.840.113549.1.7.2") return false
        val signedData = Der.read(ciParts[1].content, 0) ?: return false
        val sdParts = Der.children(signedData.content, 0) ?: return false
        if (sdParts.size < 5) return false
        // encapContentInfo（第 3 项）的 contentType 必须是 data
        val encap = Der.children(sdParts[2].content, 0) ?: return false
        if (encap.isEmpty() || Der.encodeOidString(encap[0].content) != "1.2.840.113549.1.7.1") return false
        val signerInfos = sdParts.last()
        val signerInfo = Der.read(signerInfos.content, 0) ?: return false
        val parts = Der.children(signerInfo.content, 0) ?: return false

        // parts: [version, sid, digestAlgo, (attrs)?, encryptAlgo, signature]
        // - 旧自研签名器：6 项，attrs 为 [0] IMPLICIT（0xA0）；
        // - apksig（Pkcs7Encoder）：5 项，无 authenticatedAttributes，直接对内容签名。
        val sig = parts.lastOrNull() ?: return false
        val attrs = parts.firstOrNull { it.tag == 0xA0 }
        val sigInput: ByteArray
        if (attrs != null) {
            val attrsBytes = attrs.content
            val attrsList = Der.children(attrsBytes, 0) ?: return false
            var messageDigest: ByteArray? = null
            for (attr in attrsList) {
                val attrParts = Der.children(attr.content, 0) ?: continue
                if (attrParts.size < 2) continue
                if (Der.encodeOidString(attrParts[0].content) != "1.2.840.113549.1.9.4") continue
                val values = Der.children(attrParts[1].content, 0) ?: continue
                if (values.isNotEmpty() && values[0].tag == 0x04) messageDigest = values[0].content
            }
            val digest = messageDigest ?: return false
            if (!MessageDigest.isEqual(digest, sha256(sf))) return false
            // 签名输入 = 完整 SET OF Attribute 的 DER 编码（0x31 + 内容）。
            sigInput = Der.tlv(0x31, attrsBytes)
        } else {
            // apksig 形态：无 authenticatedAttributes，签名消息 = SF 内容。
            sigInput = sf
        }
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initVerify(cert.publicKey)
        signature.update(sigInput)
        return signature.verify(sig.content)
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private class ManifestSection(
        val name: String?,
        val attrs: Map<String, String>,
        /** 折叠续行后的 section 文本（旧格式摘要比对对象）。 */
        val unfoldedBytes: ByteArray,
        /** 原始 section 字节（含续行与尾部空行，apksig 格式摘要比对对象）。 */
        val rawBytes: ByteArray,
    ) {
        operator fun component1(): String? = name

        operator fun component2(): Map<String, String> = attrs
    }

    private class Tlv(
        val tag: Int,
        val content: ByteArray,
        val end: Int,
    )

    private object Der {
        fun read(
            bytes: ByteArray,
            offset: Int,
        ): Tlv? {
            if (offset + 2 > bytes.size) return null
            val tag = bytes[offset].toInt() and 0xFF
            var lenByte = bytes[offset + 1].toInt() and 0xFF
            var len = lenByte
            var pos = offset + 2
            if (lenByte and 0x80 != 0) {
                val n = lenByte and 0x7F
                if (n > 4 || pos + n > bytes.size) return null
                len = 0
                for (i in 0 until n) len = (len shl 8) or (bytes[pos + i].toInt() and 0xFF)
                pos += n
            }
            if (pos + len > bytes.size) return null
            return Tlv(tag, bytes.copyOfRange(pos, pos + len), pos + len)
        }

        /** 解析 SEQUENCE/SET 的内容为子元素列表。 */
        fun children(
            content: ByteArray,
            offset: Int,
        ): List<Tlv>? {
            val out = ArrayList<Tlv>()
            var pos = offset
            while (pos < content.size) {
                val tlv = read(content, pos) ?: return null
                out.add(tlv)
                pos = tlv.end
            }
            return out
        }

        /** 组一个 TLV（长度用短形式，≤127 字节时与标准 SET 编码一致）。 */
        fun tlv(
            tag: Int,
            content: ByteArray,
        ): ByteArray {
            require(content.size < 0x80) { "attrs too large" }
            val out = ByteArray(content.size + 2)
            out[0] = tag.toByte()
            out[1] = content.size.toByte()
            System.arraycopy(content, 0, out, 2, content.size)
            return out
        }

        fun encodeOidString(content: ByteArray): String {
            if (content.isEmpty()) return ""
            val first = content[0].toInt() and 0xFF
            val out = StringBuilder("${first / 40}.${first % 40}")
            var value = 0L
            for (i in 1 until content.size) {
                val b = content[i].toInt() and 0xFF
                value = (value shl 7) or (b and 0x7F).toLong()
                if (b and 0x80 == 0) {
                    out.append('.').append(value)
                    value = 0L
                }
            }
            return out.toString()
        }
    }
}
