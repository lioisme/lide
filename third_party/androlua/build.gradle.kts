plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lioisme.lide.androlua"
    // 与 :app / :editor / sora 模块对齐（精简治理：全局统一 compileSdk，避免 35/36 混编）
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // androidx.annotation 被上游 vendor Java（FileProvider/LuaActivity/LuaResources/
    // GlobalActionAutomator）使用；androidx.core / lifecycle-common 无引用（P5 清理），
    // fileTree("libs") 目录仅含 .gitkeep（死声明，P5 清理）。
    implementation("androidx.annotation:annotation:1.9.1")
    testImplementation("junit:junit:4.13.2")
}
