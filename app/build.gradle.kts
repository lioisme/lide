import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropertiesFile = rootProject.file("app/keystore.properties")
val keystoreProperties = Properties().apply {
    load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.lioisme.delua"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lioisme.delua"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    flavorDimensions += "arch"
    productFlavors {
        create("x64") {
            dimension = "arch"
            ndk {
                abiFilters += "x86_64"
            }
        }
        create("arm64") {
            dimension = "arch"
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
    }

    signingConfigs {
        named("debug") {
            storeFile = file(keystoreProperties.getProperty("storeFile"))
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
        create("release") {
            storeFile = file(keystoreProperties.getProperty("storeFile"))
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            // R8：androlua / luaj / 宿主类均可能被 Lua 脚本与插件 dex 按类名反射引用，
            // keep 规则见 proguard-rules.pro；启用后显著缩小方法与常量池（P2 冷启动优化）。
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs(
                "src/main/jniLibs",
                // live2d 原生库独立目录：产物不与原版库存放一起
                // （build-scripts/live2d/libs/{abi}/liblive2d.so，三 ABI 交叉编译）
                "../build-scripts/live2d/libs"
            )
        }
    }

    testOptions {
        // JVM 单测：android.util.Log 等桩方法返回默认值（不加载设备类）。
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

repositories {
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    maven { url = uri("https://maven.aliyun.com/repository/google") }
    google()
    mavenCentral()
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(project(":androlua"))
    implementation(project(":editor"))
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.google.android.material:material:1.13.0")

    // sora-editor - 专业代码编辑器
    implementation("io.github.rosemoe:editor:0.24.6")
    // TextMate 语法高亮支持
    implementation("io.github.rosemoe:language-textmate:0.24.6")

    testImplementation("junit:junit:4.13.2")
}

// Lua 可见 API 面回归门禁：Lua 脚本 / 插件 dex 以字符串类名经 luajava
// bindClass/import 引用这些类，R8 的 Java 引用图看不到它们。本任务在
// release 构建后检查 mapping，任一缺失即失败（CI 与本地均可运行）。
val luaApiSurface = listOf(
    "android.widget.ArrayListAdapter",
    "android.widget.ArrayPageAdapter",
    "android.widget.BasePageAdapter",
    "android.widget.PageAdapter",
    "android.widget.ArrayExpandableListAdapter",
    "android.widget.ExListView",
    "android.widget.FloatButton",
    "android.widget.ToolBar",
    "android.widget.PageView",
    "android.widget.PageLayout",
    "android.widget.DrawerLayout",
    "android.widget.CircleImageView",
    "android.widget.CardView",
    "android.widget.HorizontalListView",
    "android.widget.PullingLayout",
    "android.widget.SlidingLayout",
    "android.app.AlertDialogBuilder",
    "android.app.FloatWindow",
    "com.nirenr.Color",
    "com.nirenr.ColorFinder",
    "com.nirenr.ColorPoint",
    "com.nirenr.screencapture.ScreenShot",
    "com.android.cglib.proxy.Enhancer",
    "com.android.cglib.dx.DexMaker",
    "com.luajava.LuaState",
    "com.luajava.LuaObject",
    "com.androlua.LuaActivity",
    "com.androlua.LuaEnhancer",
    "com.androlua.LuaContext"
)

tasks.register("verifyReleaseLuaSurface") {
    group = "verification"
    description = "校验 release 构建保留了 Lua 可见 API 面（防 R8 误删回归）"
    dependsOn("assembleArm64Release")
    doLast {
        val mapping = file("build/outputs/mapping/arm64Release/mapping.txt")
        check(mapping.exists()) { "未找到 mapping 文件：${mapping}" }
        val content = mapping.readText()
        val missing = luaApiSurface.filter { "$it -> " !in content }
        check(missing.isEmpty()) {
            "Lua 可见 API 面被 R8 移除：\n" + missing.joinToString("\n") { "  - $it" }
        }
        println("Lua 可见 API 面校验通过：${luaApiSurface.size} 个类全部保留")
    }
}