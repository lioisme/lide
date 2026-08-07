package com.lioisme.lide.build

import com.android.apksig.KeyConfig
import com.android.apksig.util.DataSinks
import com.android.apksig.util.DataSources
import java.nio.ByteBuffer
import java.security.PrivateKey
import java.security.cert.X509Certificate
import com.android.apksig.ApkSigner as ApksigSigner

/**
 * APK 签名器（官方 [ApksigSigner] 封装，替代原自研 ApkSigner/V2Signer）。
 *
 * - v1（JAR）+ v2（APK Signature Scheme）+ v3 全部由
 *   `com.android.tools.build:apksig`（Google / AGP 同源）实现：手写 DER/CMS、
 *   v2 签名块、整包分块摘要等安全关键逻辑不再自维护（原 ~460 行删除）；
 * - 签名方案组合经 [SigningScheme] 控制（`setV1/V2/V3SigningEnabled`），
 *   默认 v1+v2+v3 全兼容；仅 v2/v3 时产物不含 META-INF JAR 签名（体积更小）；
 * - STORED 条目 4 字节对齐由 apksig 在签名重写时自动完成（等价 zipalign），
 *   装配阶段的 ZipUtils 对齐代码随之移除；
 * - v4 签名关闭（v4 依赖安装器文件描述符校验与 .idsig 伴生文件，ByteArray 输出场景不使用）。
 *
 * 与旧实现输入输出契约一致：[sign] 接收整包字节，返回签名字节。
 */
object ApkSigner {
    /**
     * 对 [apk] 做指定方案组合的签名（产物兼容对应版本安装器）。
     *
     * [scheme] 为 [SigningScheme.V1V2V3]（默认，全兼容）。
     */
    fun sign(
        apk: ByteArray,
        key: PrivateKey,
        cert: X509Certificate,
        scheme: SigningScheme = SigningScheme.V1V2V3,
    ): ByteArray {
        val signerConfig =
            // 配置名同时决定 v1 签名文件名（META-INF/CERT.SF / CERT.RSA，Android 惯例）。
            ApksigSigner.SignerConfig
                .Builder("CERT", KeyConfig.Jca(key), listOf(cert))
                .build()
        val sink = DataSinks.newInMemoryDataSink()
        ApksigSigner
            .Builder(listOf(signerConfig))
            .setInputApk(DataSources.asDataSource(ByteBuffer.wrap(apk)))
            .setOutputApk(sink)
            // 显式指定 minSdk（宿主 minSdk 恒为 24）：避免 apksig 解析
            // AndroidManifest.xml 推断版本（解析失败会抛 MinSdkVersionException）。
            .setMinSdkVersion(24)
            .setV1SigningEnabled(scheme.v1)
            .setV2SigningEnabled(scheme.v2)
            .setV3SigningEnabled(scheme.v3)
            .setV4SigningEnabled(false)
            .build()
            .sign()
        val buffer = sink.getByteBuffer(0, sink.size().toInt())
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }
}
