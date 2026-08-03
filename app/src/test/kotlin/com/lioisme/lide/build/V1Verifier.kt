package com.lioisme.lide.build

import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.Signature
import java.util.Base64
import java.util.zip.ZipInputStream

/**
 * 独立 v1 签名校验器（仅测试用，刻意不复用 ApkSigner 内部实现）：
 * 1. MANIFEST.MF 逐条目 SHA-256 与文件内容比对；
 * 2. CERT.SF 的 Digest-Manifest 与 MANIFEST.MF 比对；
 * 3. 解析 CERT.RSA（PKCS#7 CMS），用证书公钥验证签名；
 * 4. 签名消息 = 重建的 SET OF Attribute DER 编码（0x31 + [0] IMPLICIT 内容），
 *    与 JDK / Android libcore / apksig 的 JAR 校验路径等效。
 */
object V1Verifier {

    private val B64: Base64.Decoder = Base64.getDecoder()

    fun verify(apk: ByteArray, cert: X509Certificate): Boolean {
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

    private fun verifyManifestSections(manifest: ByteArray, entries: Map<String, ByteArray>): Boolean {
        val sections = parseSections(String(manifest, Charsets.UTF_8))
        for ((name, section) in sections) {
            if (name == null) continue // header 段无摘要
            val digest = section["SHA-256-Digest"] ?: return false
            val data = entries[name] ?: return false
            if (!MessageDigest.isEqual(B64.decode(digest), sha256(data))) return false
        }
        return true
    }

    private fun verifySfDigests(sf: ByteArray, manifest: ByteArray): Boolean {
        val sections = parseSections(String(sf, Charsets.UTF_8))
        val header = sections.entries.firstOrNull() ?: return false
        val whole = header.value["SHA-256-Digest-Manifest"] ?: return false
        if (!MessageDigest.isEqual(B64.decode(whole), sha256(manifest))) return false
        // 逐条目摘要（如果有）逐条比对
        for ((name, attrs) in sections) {
            if (name == null || attrs["SHA-256-Digest-Manifest"] == null) continue
            val want = attrs["SHA-256-Digest-Manifest"] ?: return false
            val sectionBytes = findManifestSection(manifest, name) ?: return false
            if (!MessageDigest.isEqual(B64.decode(want), sha256(sectionBytes))) return false
        }
        return true
    }

    /** 解析清单 section：header 行之后按 "Name: " 起始分段；header 段 name=null。 */
    private fun parseSections(text: String): LinkedHashMap<String?, Map<String, String>> {
        val out = LinkedHashMap<String?, Map<String, String>>()
        val lines = text.split("\r\n")
        var i = 0
        if (lines.isNotEmpty() && !lines[0].startsWith("Name: ")) {
            val attrs = HashMap<String, String>()
            while (i < lines.size && lines[i].isNotEmpty()) {
                val kv = lines[i].split(": ", limit = 2)
                if (kv.size == 2) attrs[kv[0]] = kv[1]
                i++
            }
            out[null] = attrs
        }
        while (i < lines.size) {
            if (!lines[i].startsWith("Name: ")) { i++; continue }
            val name = lines[i].substring(6)
            val attrs = HashMap<String, String>()
            i++
            while (i < lines.size && lines[i].isNotEmpty()) {
                val kv = lines[i].split(": ", limit = 2)
                if (kv.size == 2) attrs[kv[0]] = kv[1]
                i++
            }
            out[name] = attrs
            i++
        }
        return out
    }

    /** 在 MANIFEST.MF 中定位某条目 section 的原始字节（含尾部空行）。 */
    private fun findManifestSection(manifest: ByteArray, name: String): ByteArray? {
        val text = String(manifest, Charsets.UTF_8)
        val marker = "Name: $name\r\n"
        val start = text.indexOf(marker)
        if (start < 0) return null
        val end = text.indexOf("\r\n\r\n", start)
        val last = if (end < 0) text.length else end + 4
        return text.substring(start, last).toByteArray(Charsets.UTF_8)
    }

    private fun verifyCms(rsa: ByteArray, sf: ByteArray, cert: X509Certificate): Boolean {
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

        // parts: [version, sid, digestAlgo, [0] attrs, encryptAlgo, signature]
        val attrs = parts.firstOrNull { it.tag == 0xA0 } ?: return false
        val sig = parts.lastOrNull() ?: return false

        // authenticatedAttributes（[0] IMPLICIT SET OF）：A0 内容 = 无 SET 头的
        // Attribute 序列；签名输入 = 重建的完整 SET 编码（0x31 + 长度 + 内容）。
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

        // 签名输入 = SET OF Attribute 的 DER 编码（与 ApkSigner 签名对象一致）
        val sigInput = Der.tlv(0x31, attrsBytes)
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initVerify(cert.publicKey)
        signature.update(sigInput)
        return signature.verify(sig.content)
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private class Tlv(val tag: Int, val content: ByteArray, val end: Int)

    private object Der {
        fun read(bytes: ByteArray, offset: Int): Tlv? {
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
        fun children(content: ByteArray, offset: Int): List<Tlv>? {
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
        fun tlv(tag: Int, content: ByteArray): ByteArray {
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
