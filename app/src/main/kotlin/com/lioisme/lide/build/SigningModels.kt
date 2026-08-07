package com.lioisme.lide.build

import java.security.PrivateKey
import java.security.cert.X509Certificate

/** 已解析的签名材料（供 [ApkBuilder] / [ApkSigner] 消费）。 */
data class SigningMaterial(
    val key: PrivateKey,
    val cert: X509Certificate,
) {
    val algorithm: String
        get() = key.algorithm
}

/**
 * 签名档案（持久化元数据）。内置调试档案（[SigningManager.BUILTIN_TESTKEY_ID]）
 * 指向 assets/keys 材料；导入档案指向复制到 filesDir/signing/ 的密钥库文件。
 */
data class SigningProfile(
    val id: String,
    val name: String,
    /** 密钥库文件相对 filesDir/signing/ 的文件名；内置档案为空（用 assets）。 */
    val storeFile: String,
    /** 密钥库类型（"PKCS12"/"JKS"）；内置为空。 */
    val storeType: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
    val algorithm: String,
    /** 证书 SHA-256 指纹（冒号分隔的大写十六进制）。 */
    val certFingerprint: String,
    val isBuiltin: Boolean,
) {
    val displayName: String
        get() = name
}
