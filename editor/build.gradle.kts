plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

android {
    namespace = "com.lioisme.lide.editor"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // JVM 单测：android.util.Log 等桩方法返回默认值（不加载设备类）。
        unitTests.isReturnDefaultValues = true
    }
}

repositories {
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    maven { url = uri("https://maven.aliyun.com/repository/google") }
    google()
    mavenCentral()
}

dependencies {
    // 必须与 :app 运行时完全同版（BOM 2025.06.00 → material3 1.4.0）：
    // 曾用 BOM 2024.09.00（material3 1.2.1）编译 TipIconButton 的 PlainTooltip，
    // 运行时解析 1.4.0 的 TooltipKt → NoSuchMethodError 长按闪退（实测复现）。
    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.15.0")

    // Tab 拖拽排序（WP-003，Squircle 同款固定版本；POM 实证与 Compose 1.7.3 二进制兼容）。
    implementation("sh.calvin.reorderable:reorderable:3.0.0")

    // sora-editor - 专业代码编辑器
    implementation("io.github.rosemoe:editor:0.24.6")
    // TextMate 语法高亮支持
    implementation("io.github.rosemoe:language-textmate:0.24.6")

    testImplementation("junit:junit:4.13.2")
}

// ktlint（T6）：规则源为根 .editorconfig；只检查本模块，避免全仓超大 diff。
ktlint {
    version = "1.8.0"
    android = false
    ignoreFailures = false
}

// 接入 Android lint 门禁：:editor:lint 前置跑 ktlint（基线违规数以报告形式记录，不做全量修复）。
tasks.named("lint") {
    dependsOn("ktlintCheck")
}
