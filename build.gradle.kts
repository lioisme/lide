plugins {
    // 版本栈对齐（P2#16，2026-08-05 升级 8.6.0 → 8.13.2）：
    // - AGP 8.13 原生支持 compileSdk 36（8.9.1 起），gradle.properties 中
    //   android.suppressUnsupportedCompileSdk=36,35 压制已移除；
    // - Gradle 9.5.1 对 AGP 8.x 保持最佳努力兼容（9.6 起 AGP 8.x 不再被支持，
    //   升级 Gradle 9.6+ 前必须先升 AGP 9.x）；
    // - AGP 8.13.2 的 R8 8.13.19 支持 Kotlin 2.3，本项目 Kotlin 2.2.20 兼容。
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}
