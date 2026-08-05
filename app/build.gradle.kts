import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // ktlint 统一扩展至宿主（P2#16 同族）：规则源为根 .editorconfig，与 :editor 同版本。
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

val keystorePropertiesFile = rootProject.file("app/keystore.properties")
val keystoreProperties =
    Properties().apply {
        load(keystorePropertiesFile.inputStream())
    }

android {
    namespace = "com.lioisme.lide"
    compileSdk = 36
    // 固定 NDK 版本：本地与 CI 统一（29.0.14206865，T9 终验同款），AGP externalNativeBuild 直接使用。
    ndkVersion = "29.0.14206865"

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
                "proguard-rules.pro",
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

    // 原生引擎：源码统一位于 src/main/cpp/（AGP 官方 ndk 源码目录）。
    // 由 AGP externalNativeBuild 直接调用 ndk-build，产物自动打进 APK，
    // 不再有 jniLibs 缓存目录 / rebuild 脚本（旧的 scripts/rebuild_engine.* 已废弃）。
    // APP_ABI 由 AGP 依据 abiFilters 注入；APP_STL/APP_PLATFORM/APP_OPTIM
    // 由同目录 Application.mk 声明（ndk-build 原生读取）。
    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/Android.mk")
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

// 内置示例 .alp：构建时从 scripts/examples-src/ 重新打包（跨平台，替代仅 Windows
// 可用的 make-examples.ps1），打包器脚本与示例源统一收纳于 scripts/。产物写入
// src/main/assets/examples/，构建后随 APK 发布；*.alp 不入库（.gitignore）。
val generateExamples =
    tasks.register("generateExamples") {
        group = "build"
        description = "从 scripts/examples-src/ 重新生成内置示例 .alp 文件"
        val srcRoot = rootProject.file("scripts/examples-src")
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

// ktlint（与 :editor 同配置）：规则源为根 .editorconfig；只检查本模块。
ktlint {
    version = "1.8.0"
    android = false
    ignoreFailures = false
}

// 与 :editor 对齐：lint 前先过 ktlint，违规阻断式记录（禁止静默跳过）。
tasks.named("lint").configure {
    dependsOn("ktlintCheck")
}

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

    // Android Baseline Profile：src/main/baseline-prof.txt（启动 + 编辑器首帧 + 打字路径）
    // 经 R8 编译进 APK，profileinstaller 负责 install 期写入 ART 云编译（API 34+ 由系统直读）。
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // sora-editor - 专业代码编辑器（0.24.6 源码收纳于 third_party/sora-editor/，见其 README）
    // 统一走 project 模块：:app 曾同时拉 Maven 坐标 io.github.rosemoe:*:0.24.6，
    // 与 :editor 的 vendored 源码构成两份同名类并存于运行 classpath（类遮蔽顺序依赖
    // Gradle 解析顺序，属地雷），现仅保留 project 模块一份来源。
    implementation(project(":sora-editor:editor"))
    // language-textmate 无 :app 直接使用面（仅 :editor 经 TextMateInitializer 使用），
    // 由 :editor 的 implementation 传递运行时可见，不再重复声明。

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

// Lua 可见 API 面回归门禁：Lua 脚本 / 插件 dex 以字符串类名经 luajava
// bindClass/import 引用这些类，R8 的 Java 引用图看不到它们。本任务在
// release 构建后检查 mapping，任一缺失即失败（CI 与本地均可运行）。
//
// 维护约定（P3-4，三处同步）：新增 pro 自定义控件 / 引擎表面类时，需同时登记于
//   1. 本列表（R8 保留门禁校验对象）
//   2. app/proguard-rules.pro 的 -keep 规则（R8 实际保留手段）
//   3. ApiCategoryClassifier.androluaAndroidPrefixes（API 浏览器分类）
// 任一遗漏会导致：脚本运行时类缺失 或 浏览器分类错误，且本门禁可捕获 1/2 的遗漏。
val luaApiSurface =
    listOf(
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
        "com.androlua.LuaContext",
    )

tasks.register("verifyReleaseLuaSurface") {
    group = "verification"
    description = "校验 release 构建保留了 Lua 可见 API 面（防 R8 误删回归）"
    dependsOn("assembleArm64Release")
    doLast {
        val mapping = file("build/outputs/mapping/arm64Release/mapping.txt")
        check(mapping.exists()) { "未找到 mapping 文件：$mapping" }
        val content = mapping.readText()
        val missing = luaApiSurface.filter { "$it -> " !in content }
        check(missing.isEmpty()) {
            "Lua 可见 API 面被 R8 移除：\n" + missing.joinToString("\n") { "  - $it" }
        }
        println("Lua 可见 API 面校验通过：${luaApiSurface.size} 个类全部保留")
    }
}
