// sora-editor language-textmate 模块（0.24.6 上游源码收纳，本文件为本仓库自写最小构建）。
// 上游 compileOnly(projects.onigurunaNative) 的原生 JNI 类由 OnigNative.java 桩替代：
// 运行期 NativeOnigConfig.isAvailable() 捕获桩异常返回 false，走 Joni 纯 Java 路径，
// 与官方 Maven 制品（不带 oniguruma-native）行为一致。
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.rosemoe.sora.langs.textmate"
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
    compileOnly(project(":sora-editor:editor"))
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.jruby.jcodings:jcodings:1.0.64")
    implementation("org.jruby.joni:joni:2.2.7")
    implementation("org.snakeyaml:snakeyaml-engine:3.0.1")
    implementation("org.eclipse.jdt:org.eclipse.jdt.annotation:2.4.100")
}
