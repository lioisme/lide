plugins {
    id("com.android.library")
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
}

dependencies {
    // 零第三方依赖：引擎 dex 侧（androlua/luajava/cglib/nirenr/engine 契约）纯 Java + Android SDK。
    // androidx.annotation 曾用于 vendor Java（FileProvider/LuaActivity/LuaResources/
    // GlobalActionAutomator），2026-08-08 引擎 dex 单份化时全部剥离（纯 Java 常量等价）。
}
