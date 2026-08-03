import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.BufferedOutputStream
import java.io.FileOutputStream

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
    namespace = "com.lioisme.lide"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lioisme.lide"
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

// 内置示例 .alp：构建时从 build-scripts/examples-src/ 重新打包（跨平台，替代仅 Windows
// 可用的 make-examples.ps1）。产物落 src/main/assets/examples/，并被 Android assets 打进 APK。
val generateExamples = tasks.register("generateExamples") {
    group = "build"
    description = "从 build-scripts/examples-src/ 重新生成内置示例 .alp 文件"
    val srcRoot = rootProject.file("build-scripts/examples-src")
    val outDir = layout.projectDirectory.dir("src/main/assets/examples").asFile
    inputs.dir(srcRoot)
    outputs.dir(outDir)
    doLast {
        outDir.mkdirs()
        srcRoot.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { dir ->
            val outFile = outDir.resolve(dir.name + ".alp")
            outFile.delete()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zip ->
                // zip 条目用 '/' 分隔符、UTF-8 文件名，与旧产物兼容
                dir.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(dir).path }.forEach { f ->
                    val rel = f.relativeTo(dir).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(rel))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            println("generateExamples: ${outFile.name}")
        }
    }
}
tasks.named("preBuild").configure { dependsOn(generateExamples) }

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(project(":androlua"))
    implementation(project(":editor"))
    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // material3 显式 1.4.0：新版 SearchBar（SearchBarState + ExpandedFullScreenSearchBar）
    // 为 1.4.0 新增 API，需 AGP 8.6+（compose 1.9 线）。
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material3:material3-window-size-class:1.4.0")
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