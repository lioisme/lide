package com.lioisme.lide.build

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec

/**
 * 签名档案管理（宿主域，仅 :app 使用）：
 * - 档案元数据存 `lide_signing` 偏好（JSON）；密钥库文件复制到应用私有 `filesDir/signing/`
 *   （不落公共存储）。
 * - 内置档案 [BUILTIN_ID] 指向 assets/keys 的 AOSP 调试钥（与旧打包行为一致，零迁移）。
 * - 构建接入：[loadMaterial] 返回 [SigningMaterial] 交给 [ApkSigner]。
 * - 注意：storePassword/keyPassword 与 pro 一致以明文存私有偏好；Android Keystore 加密入 backlog。
 */
object SigningManager {
    const val BUILTIN_ID = "builtin-testkey"
    private const val PREF_NAME = "lide_signing"
    private const val KEY_PROFILES = "profiles"
    private const val KEY_DEFAULT = "default"
    private const val FILE_PREFIX = "keystore_"

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs() = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun signingDir(): File = File(appContext.filesDir, "signing").apply { mkdirs() }

    /** 内置调试档案（assets/keys），恒可用回退。 */
    fun builtinProfile(): SigningProfile =
        SigningProfile(
            id = BUILTIN_ID,
            name = "Android debug key",
            storeFile = "",
            storeType = "",
            storePassword = "",
            keyAlias = "testkey",
            keyPassword = "",
            algorithm = "RSA",
            certFingerprint = "",
            isBuiltin = true,
        )

    /** 全部档案：内置 → 导入（按名称排序）。 */
    fun profiles(): List<SigningProfile> =
        listOf(builtinProfile()) +
            readImported().sortedBy { it.name.lowercase() }

    /** 按 ID 解析档案；未知/空 → 内置。 */
    fun profile(id: String?): SigningProfile =
        if (id == null || id == BUILTIN_ID) {
            builtinProfile()
        } else {
            readImported().firstOrNull { it.id == id } ?: builtinProfile()
        }

    fun defaultId(): String = prefs().getString(KEY_DEFAULT, null) ?: ""

    fun setDefault(id: String) {
        prefs().edit().putString(KEY_DEFAULT, id).apply()
    }

    /** 导入密钥库：生成 ID / 复制字节到私目录 / 保存档案 / 首个档案即为默认。 */
    fun import(
        name: String,
        storeType: String,
        storePassword: String,
        keyAlias: String,
        algorithm: String,
        certFingerprint: String,
        bytes: ByteArray,
    ): SigningProfile {
        val id = generateFileId()
        val file = File(signingDir(), FILE_PREFIX + id)
        file.writeBytes(bytes)
        val profile =
            SigningProfile(
                id = id,
                name = name,
                storeFile = file.name,
                storeType = storeType,
                storePassword = storePassword,
                keyAlias = keyAlias,
                keyPassword = storePassword,
                algorithm = algorithm,
                certFingerprint = certFingerprint,
                isBuiltin = false,
            )
        upsert(profile)
        if (defaultId().isBlank()) setDefault(id)
        return profile
    }

    fun delete(id: String) {
        if (id == BUILTIN_ID) return
        val imported = readImported()
        val target = imported.firstOrNull { it.id == id } ?: return
        File(signingDir(), target.storeFile).takeIf(File::exists)?.delete()
        write(imported.filterNot { it.id == id })
        if (defaultId() == id) prefs().edit().remove(KEY_DEFAULT).apply()
    }

    /** 导出档案的原始密钥库字节（供 SAF CreateDocument 写盘）；内置档案 / 未知 ID 返回 null。 */
    fun exportBytes(id: String): ByteArray? {
        if (id == BUILTIN_ID) return null
        val target = readImported().firstOrNull { it.id == id } ?: return null
        val file = File(signingDir(), target.storeFile)
        return if (file.isFile) file.readBytes() else null
    }

    /** 应用内自生成签名档案（RSA-2048 自签 X.509，零依赖）：密码留空或不足 6 位自动生成并随档案保存。 */
    fun generate(
        name: String,
        alias: String = "lidekey",
        password: String? = null,
        subject: CertSubject? = null,
        validYears: Int = SigningCrypto.DEFAULT_VALID_YEARS,
    ): SigningProfile {
        val pwd = password?.takeIf { it.length >= 6 } ?: randomPassword()
        val gen = SigningCrypto.generate(name, alias, pwd.toCharArray(), subject ?: CertSubject(commonName = name), validYears)
        return import(
            name = name,
            storeType = SigningCrypto.PKCS12_STORE_TYPE,
            storePassword = pwd,
            keyAlias = alias,
            algorithm = "RSA",
            certFingerprint = gen.fingerprint,
            bytes = gen.storeBytes,
        )
    }

    /** 签名材料：内置走 assets PKCS#8；导入走私有密钥库。 */
    fun loadMaterial(id: String? = null): SigningMaterial {
        val p = profile(id)
        return if (p.isBuiltin) {
            loadBuiltinMaterial()
        } else {
            val file = File(signingDir(), p.storeFile)
            if (!file.isFile) {
                throw IllegalStateException("签名密钥库缺失：${p.name}（请重新导入）")
            }
            SigningCrypto.resolveMaterial(
                content = file.readBytes(),
                storePassword = p.storePassword.toCharArray(),
                alias = p.keyAlias,
                keyPassword = p.keyPassword.toCharArray(),
                storeType = p.storeType,
            )
        }
    }

    /** 内置 assets 调试钥（AOSP testkey，PKCS#8 + X.509 PEM）。 */
    fun loadBuiltinMaterial(): SigningMaterial {
        val keyBytes = appContext.assets.open("keys/testkey.pk8").use { it.readBytes() }
        val pemBytes = appContext.assets.open("keys/testkey.x509.pem").use { it.readBytes() }
        val key =
            KeyFactory
                .getInstance("RSA")
                .generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        val cert =
            CertificateFactory
                .getInstance("X.509")
                .generateCertificate(pemBytes.inputStream()) as java.security.cert.X509Certificate
        return SigningMaterial(key, cert)
    }

    private fun generateFileId(): String =
        java.util.UUID
            .randomUUID()
            .toString()
            .replace("-", "")
            .take(16)

    private fun randomPassword(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val random = java.security.SecureRandom()
        return buildString { repeat(12) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }

    private fun upsert(profile: SigningProfile) {
        val list = readImported().toMutableList()
        list.removeAll { it.id == profile.id }
        list.add(profile)
        write(list.sortedBy { it.name.lowercase() })
    }

    private fun readImported(): List<SigningProfile> {
        val raw = prefs().getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    add(arr.getJSONObject(i).fromJson())
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun write(list: List<SigningProfile>) {
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        prefs().edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    private fun SigningProfile.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("storeFile", storeFile)
            .put("storeType", storeType)
            .put("storePassword", storePassword)
            .put("keyAlias", keyAlias)
            .put("keyPassword", keyPassword)
            .put("algorithm", algorithm)
            .put("certFingerprint", certFingerprint)

    private fun JSONObject.fromJson(): SigningProfile =
        SigningProfile(
            id = optString("id"),
            name = optString("name"),
            storeFile = optString("storeFile"),
            storeType = optString("storeType"),
            storePassword = optString("storePassword"),
            keyAlias = optString("keyAlias"),
            keyPassword = optString("keyPassword"),
            algorithm = optString("algorithm"),
            certFingerprint = optString("certFingerprint"),
            isBuiltin = false,
        )
}
