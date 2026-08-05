// sora-editor editor 模块（0.24.6 上游源码收纳，本文件为本仓库自写最小构建，
// 不引上游 build-logic；上游版本/依赖信息见 third_party/sora-editor/README.md）。
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.rosemoe.sora"
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
}

repositories {
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    maven { url = uri("https://maven.aliyun.com/repository/google") }
    google()
    mavenCentral()
}

dependencies {
    api("androidx.annotation:annotation:1.10.0")
    implementation("androidx.collection:collection:1.5.0")
}
