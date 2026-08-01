plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lioisme.delua.editor"
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
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
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
