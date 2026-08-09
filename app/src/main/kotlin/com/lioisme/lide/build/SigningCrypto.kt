package com.lioisme.lide.build

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Calendar
import java.util.Date

/** 密钥库单项（元数据），供导入 UI 列别名选择。 */
data class AliasInfo(
    val alias: String,
    val algorithm: String,
    val certFingerprint: String,
    val hasPrivateKey: Boolean,
)

/**
 * 自签证书 subject DN 属性（RFC 5280 标准集，全部可选）：
 * 空字段在编码时按标准顺序（C→ST→L→O→OU→CN→E→street→postalCode）跳过；
 * [commonName] 留空时回落档案名称。
 */
data class CertSubject(
    val countryCode: String = "",
    val state: String = "",
    val locality: String = "",
    val organization: String = "",
    val organizationUnit: String = "",
    val commonName: String = "",
    val email: String = "",
    val street: String = "",
    val postalCode: String = "",
)

/** 应用内自生成的签名材料（RSA-2048 + 自签 X.509，零外部依赖）。 */
data class GeneratedKey(
    /** PKCS12 密钥库字节（可直接 [SigningManager.import] 或解析）。 */
    val storeBytes: ByteArray,
    val cert: X509Certificate,
    val fingerprint: String,
)

/** 纯 JVM 的密钥库解析 / 签名材料提取（无 Android 依赖，可单测）。 */
object SigningCrypto {
    /**
     * 从文件头猜测密钥库类型：
     * - JKS 魔数 `0xFE 0xED 0xFE 0xED`；
     * - 其余（含 PKCS#12 = ASN.1 DER SEQUENCE）回落为 [PKCS12_STORE_TYPE]。
     */
    fun detectStoreType(bytes: ByteArray): String {
        if (bytes.size >= 4 &&
            bytes[0] == 0xFE.toByte() && bytes[1] == 0xED.toByte() &&
            bytes[2] == 0xFE.toByte() && bytes[3] == 0xED.toByte()
        ) {
            return JKS_STORE_TYPE
        }
        return PKCS12_STORE_TYPE
    }

    /** 列出密钥库全部别名及其证书指纹 / 私钥算法。 */
    fun listAliases(
        content: ByteArray,
        storePassword: CharArray,
        storeType: String = detectStoreType(content),
    ): List<AliasInfo> {
        val ks = loadKeyStore(content, storePassword, storeType)
        val out = ArrayList<AliasInfo>(ks.size())
        val e = ks.aliases()
        while (e.hasMoreElements()) {
            val alias = e.nextElement()
            val cert = certOf(ks, alias)
            val keyAlgorithm = runCatching { ks.getKey(alias, storePassword)?.algorithm }.getOrNull()
            out +=
                AliasInfo(
                    alias = alias,
                    algorithm = keyAlgorithm ?: "UNK",
                    certFingerprint = cert?.let { fingerprintSha256(it) }.orEmpty(),
                    hasPrivateKey = keyAlgorithm != null,
                )
        }
        return out
    }

    /** 从密钥库取私钥 + 叶子证书，生成签名材料。 */
    fun resolveMaterial(
        content: ByteArray,
        storePassword: CharArray,
        alias: String,
        keyPassword: CharArray?,
        storeType: String = detectStoreType(content),
    ): SigningMaterial {
        val ks = loadKeyStore(content, storePassword, storeType)
        val key =
            ks.getKey(alias, keyPassword)
                ?: throw IllegalArgumentException("别名不存在或无私钥：$alias")
        if (key !is PrivateKey) {
            throw IllegalArgumentException("别名 $alias 不是私钥项（$key）")
        }
        val cert =
            certOf(ks, alias)
                ?: throw IllegalArgumentException("别名 $alias 无证书链")
        return SigningMaterial(key, cert)
    }

    /**
     * 应用内自生成签名（零依赖）：
     * 1. RSA-2048 密钥对；
     * 2. 手写 X.509 v3 自签证书 DER（RFC 5280 子集：可自定义 subject DN 全字段 +
     *    有效期，SHA256withRSA、RSA SPKI），并经平台 [CertificateFactory] 回环解析自校验；
     * 3. 组装 PKCS12 密钥库（[alias]/[password]）。
     *
     * [subject] 为空字段按标准顺序跳过；[subject.commonName] 留空回落 [name]。
     * [validYears] 必须 ∈ [1, 1000]（超出抛 [IllegalArgumentException]）。
     * [password] 为 null 时自动生成 12 位强密码（PKCS12 要求 ≥6 位）。
     */
    fun generate(
        name: String,
        alias: String = "lidekey",
        password: CharArray? = null,
        subject: CertSubject = CertSubject(commonName = name),
        validYears: Int = DEFAULT_VALID_YEARS,
    ): GeneratedKey {
        require(validYears in 1..MAX_VALID_YEARS) {
            "有效期须在 1~$MAX_VALID_YEARS 年之间（got $validYears）"
        }
        val pwd = password ?: randomPassword().toCharArray()
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val pair = kpg.generateKeyPair()
        val cert = buildSelfSignedCert(subject, name, pair, validYears)
        val ks = KeyStore.getInstance(PKCS12_STORE_TYPE)
        ks.load(null, null)
        ks.setKeyEntry(alias, pair.private, pwd, arrayOf(cert))
        val out = ByteArrayOutputStream()
        ks.store(out, pwd)
        return GeneratedKey(storeBytes = out.toByteArray(), cert = cert, fingerprint = fingerprintSha256(cert))
    }

    /** 随机密码生成（Q5 去重：SigningManager 共用本实现，禁止再复制副本）。 */
    internal fun randomPassword(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val random = SecureRandom()
        return buildString { repeat(12) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }

    /** 证书 SHA-256 指纹（冒号分隔的大写十六进制）。 */
    fun fingerprintSha256(cert: X509Certificate): String =
        java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(cert.encoded)
            .joinToString(":") { "%02X".format(it) }

    private fun loadKeyStore(
        content: ByteArray,
        password: CharArray,
        storeType: String,
    ): KeyStore {
        val ks = KeyStore.getInstance(storeType)
        ByteArrayInputStream(content).use { ks.load(it, password) }
        return ks
    }

    private fun certOf(
        ks: KeyStore,
        alias: String,
    ): X509Certificate? = runCatching { ks.getCertificate(alias) as? X509Certificate }.getOrNull()

    const val PKCS12_STORE_TYPE = "PKCS12"
    const val JKS_STORE_TYPE = "JKS"

    /** 自签证书默认有效期（年）。 */
    const val DEFAULT_VALID_YEARS = 100

    /** 自签证书有效期上限（年，GeneralizedTime 编码支持至 9999 年）。 */
    const val MAX_VALID_YEARS = 1000

    // ---- 自签 X.509 v3（DER 手写，RFC 5280 子集）----

    private const val TAG_INTEGER = 0x02
    private const val TAG_BIT_STRING = 0x03
    private const val TAG_OID = 0x06
    private const val TAG_UTF8 = 0x0C
    private const val TAG_IA5 = 0x16
    private const val TAG_UTCTIME = 0x17
    private const val TAG_GENERALIZED_TIME = 0x18
    private const val TAG_SEQUENCE = 0x30
    private const val TAG_SET = 0x31

    private const val OID_CN = "2.5.4.3"
    private const val OID_C = "2.5.4.6"
    private const val OID_L = "2.5.4.7"
    private const val OID_ST = "2.5.4.8"
    private const val OID_STREET = "2.5.4.9"
    private const val OID_O = "2.5.4.10"
    private const val OID_OU = "2.5.4.11"
    private const val OID_POSTAL_CODE = "2.5.4.17"
    private const val OID_EMAIL = "1.2.840.113549.1.9.1"
    private const val OID_RSA_ENCRYPTION = "1.2.840.113549.1.1.1"
    private const val OID_SHA256_WITH_RSA = "1.2.840.113549.1.1.11"

    private val RANDOM = SecureRandom()

    /** subject DN 属性标准顺序（keytool 惯例；空值跳过）。 */
    private val SUBJECT_ATTRS: Array<Pair<String, (CertSubject) -> String>> =
        arrayOf(
            OID_C to { it.countryCode },
            OID_ST to { it.state },
            OID_L to { it.locality },
            OID_O to { it.organization },
            OID_OU to { it.organizationUnit },
            OID_CN to { it.commonName },
            OID_EMAIL to { it.email },
            OID_STREET to { it.street },
            OID_POSTAL_CODE to { it.postalCode },
        )

    /** 按 RFC 5280 定义须用 IA5String 编码的属性（emailAddress 为遗留 IA5 类型）。 */
    private val IA5_ATTRS = setOf(OID_EMAIL)

    private fun buildSelfSignedCert(
        subject: CertSubject,
        fallbackName: String,
        pair: KeyPair,
        validYears: Int,
    ): X509Certificate {
        val serial = ByteArray(16).also { RANDOM.nextBytes(it) }
        serial[0] = (serial[0].toInt() and 0x7F).toByte()
        val notBefore = Date(System.currentTimeMillis() - 86_400_000L)
        val notAfter =
            Calendar
                .getInstance()
                .apply {
                    timeInMillis = notBefore.time
                    add(Calendar.YEAR, validYears)
                }.time

        val tbs =
            der(
                TAG_SEQUENCE,
                concat(
                    der(0xA0, der(TAG_INTEGER, intContent(2))), // version v3, [0] EXPLICIT
                    der(TAG_INTEGER, serial), // serialNumber
                    algorithmIdentifier(OID_SHA256_WITH_RSA),
                    subjectDn(subject, fallbackName), // issuer
                    der(TAG_SEQUENCE, concat(timeBytes(notBefore), timeBytes(notAfter))), // validity
                    subjectDn(subject, fallbackName), // subject
                    subjectPublicKeyInfo(pair), // SPKI
                ),
            )
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(pair.private)
        signer.update(tbs)
        val signature = signer.sign()

        val derBytes =
            der(
                TAG_SEQUENCE,
                concat(
                    tbs,
                    algorithmIdentifier(OID_SHA256_WITH_RSA),
                    der(TAG_BIT_STRING, concat(byteArrayOf(0), signature)),
                ),
            )
        // 平台回环解析：既校验 DER 合法性，也产出可用的 X509Certificate。
        return CertificateFactory
            .getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(derBytes)) as X509Certificate
    }

    /**
     * 按标准顺序编码 subject DN：每个属性为单值 RDN（SET{SEQ{OID, String}}），
     * 空值跳过；[commonName] 留空时回落 [fallbackName] 保证 DN 非空。
     * 字符串类型：emailAddress 按 RFC 5280 用 IA5String，其余属性用 UTF8String。
     */
    private fun subjectDn(
        subject: CertSubject,
        fallbackName: String,
    ): ByteArray {
        val cn = subject.commonName.ifBlank { fallbackName }
        val attrs =
            SUBJECT_ATTRS.mapNotNull { (oid, pick) ->
                val value = if (oid == OID_CN) cn else pick(subject)
                value.trim().takeIf { it.isNotEmpty() }?.let { oid to it }
            }
        require(attrs.isNotEmpty()) { "subject DN 至少需要一个属性" }
        return der(
            TAG_SEQUENCE,
            concat(
                *attrs.map { (oid, value) ->
                    val tag = if (oid in IA5_ATTRS) TAG_IA5 else TAG_UTF8
                    val bytes = value.toByteArray(Charsets.UTF_8)
                    der(TAG_SET, der(TAG_SEQUENCE, concat(oidBytes(oid), der(tag, bytes))))
                }.toTypedArray(),
            ),
        )
    }

    private fun algorithmIdentifier(oid: String): ByteArray = der(TAG_SEQUENCE, concat(oidBytes(oid), byteArrayOf(0x05, 0x00))) // AlgId + NULL

    private fun subjectPublicKeyInfo(pair: KeyPair): ByteArray {
        val pub = pair.public as RSAPublicKey
        val rsaKey =
            der(
                TAG_SEQUENCE,
                concat(
                    der(TAG_INTEGER, intContent(pub.modulus)),
                    der(TAG_INTEGER, intContent(pub.publicExponent)),
                ),
            )
        return der(
            TAG_SEQUENCE,
            concat(
                algorithmIdentifier(OID_RSA_ENCRYPTION),
                der(TAG_BIT_STRING, concat(byteArrayOf(0), rsaKey)),
            ),
        )
    }

    private fun intContent(big: BigInteger): ByteArray = big.toByteArray()

    private fun intContent(v: Int): ByteArray = intContent(BigInteger.valueOf(v.toLong()))

    private fun timeBytes(time: Date): ByteArray {
        val cal = Calendar.getInstance().apply { timeInMillis = time.time }

        fun two(n: Int): String = "%02d".format(n)
        val y = cal.get(Calendar.YEAR)
        val body =
            if (y >= 2050) {
                // GeneralizedTime（YYYYMMDDHHMMSSZ）
                "%04d%s%s%s%s%sZ".format(
                    y,
                    two(cal.get(Calendar.MONTH) + 1),
                    two(cal.get(Calendar.DAY_OF_MONTH)),
                    two(cal.get(Calendar.HOUR_OF_DAY)),
                    two(cal.get(Calendar.MINUTE)),
                    two(cal.get(Calendar.SECOND)),
                )
            } else {
                // UTCTime（YYMMDDHHMMSSZ）
                "%s%s%s%s%s%sZ".format(
                    two(y % 100),
                    two(cal.get(Calendar.MONTH) + 1),
                    two(cal.get(Calendar.DAY_OF_MONTH)),
                    two(cal.get(Calendar.HOUR_OF_DAY)),
                    two(cal.get(Calendar.MINUTE)),
                    two(cal.get(Calendar.SECOND)),
                )
            }
        return der(if (y >= 2050) TAG_GENERALIZED_TIME else TAG_UTCTIME, body.toByteArray(Charsets.US_ASCII))
    }

    private fun oidBytes(oid: String): ByteArray {
        val arcs = oid.split('.').map { it.toLong() }
        val out = ByteArrayOutputStream()
        // 首弧 = 前两段合并值（X.690 8.19.4），其后每段独立编码。
        writeOidComponent(out, arcs[0] * 40 + arcs[1])
        for (i in 2 until arcs.size) writeOidComponent(out, arcs[i])
        return der(TAG_OID, out.toByteArray())
    }

    private fun writeOidComponent(
        out: ByteArrayOutputStream,
        v: Long,
    ) {
        var x = v
        val digits = IntArray(12)
        var n = 0
        digits[n++] = (x and 0x7F).toInt()
        x = x shr 7
        while (x > 0) {
            digits[n++] = (x and 0x7F).toInt()
            x = x shr 7
        }
        for (i in n - 1 downTo 1) out.write(digits[i] or 0x80)
        out.write(digits[0])
    }

    private fun der(
        tag: Int,
        content: ByteArray,
    ): ByteArray = concat(byteArrayOf(tag.toByte()), lengthBytes(content.size), content)

    private fun lengthBytes(n: Int): ByteArray =
        if (n < 0x80) {
            byteArrayOf(n.toByte())
        } else {
            val bytes =
                BigInteger
                    .valueOf(n.toLong())
                    .toByteArray()
                    .dropWhile { it == 0.toByte() }
                    .toByteArray()
            concat(byteArrayOf((0x80 or bytes.size).toByte()), bytes)
        }

    private fun concat(vararg parts: ByteArray): ByteArray {
        val total = parts.sumOf { it.size }
        val out = ByteArray(total)
        var off = 0
        for (p in parts) {
            p.copyInto(out, off)
            off += p.size
        }
        return out
    }
}
