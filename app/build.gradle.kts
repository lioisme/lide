import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
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
        // universal：双 64 位 ABI 的 fat 构建（NDK 29 已移除 32 位 ABI 工具链，
        // armeabi-v7a / x86 不可构建；lib/ 全量随包，供打包器按 ABI 自由组合）。
        create("universal") {
            dimension = "arch"
            ndk {
                abiFilters += listOf("x86_64", "arm64-v8a")
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
        // 运行时引擎 dex（P0，需求 1）：仅 androlua 运行环境 + 引擎核心，
        // 经 embedRuntimeDex 解出 classes.dex 为 assets/runtime/classes.dex 随宿主发布，
        // 打包器以它为 dex 替换来源（ApkBuilder dex 替换模式）→ 产物 dex 不再携带 IDE 类。
        // R8 keep 面见 src/runtime/runtime-rules.pro（luaApiSurface + 引擎类，不 keep IDE UI）；
        // 该 buildType 不参与宿主常规发布（assembleXxxDebug/Release 不受影响）。
        create("runtime") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "src/runtime/runtime-rules.pro",
            )
            // :androlua / :editor / :sora-editor 等库模块无 runtime buildType，
            // 依赖变体解析回落 release（R8 keep 面由其模块 proguard 保证）。
            matchingFallbacks += listOf("release")
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

// 内置示例选择（精简治理，2026-08-07，见 docs/planned/MINIMALISM_AUDIT_REPORT.md）：
// `-Plide.examples` 控制哪些工程生成 .alp 打包进 assets/examples/：
//   - "all"            → scripts/examples-src/ 下全部工程（开发期重生成用）
//   - "hello,demo"     → 逗号分隔名单，仅生成 `scripts/examples-src` 对应目录
//   - 未设置 / "none"  → 不生成任何内置示例（当前默认 = 零内置，APK 最小化）
// 示例源模板（含 live2d 资源）保留在 scripts/examples-src/ 作为后续选样池，
// 未来需恢复内置工程时仅需在构建命令添加该属性，无需改代码。
val examplesSelection =
    providers.gradleProperty("lide.examples")
        .orElse("none")
        .map { it.trim().lowercase() }

// 内置示例 .alp：按 examplesSelection 从 scripts/examples-src/ 重新打包（跨平台，
// 替代仅 Windows 可用的 make-examples.ps1），打包器脚本与示例源统一收纳于 scripts/。
// 产物写入 src/main/assets/examples/，构建后随 APK 发布；*.alp 不入库（.gitignore）。
val generateExamples =
    tasks.register("generateExamples") {
        group = "build"
        description = "按 lide.examples 生成内置示例 .alp（none=零内置 / all=全量 / 名单=指定工程）"
        val srcRoot = rootProject.file("scripts/examples-src")
        val outDir = layout.projectDirectory.dir("src/main/assets/examples").asFile
        inputs.dir(srcRoot)
        inputs.property("examplesSelection", examplesSelection)
        outputs.dir(outDir)
        doLast {
            outDir.mkdirs()
            // 先清空旧产物：切换 lide.examples 时，失效的 .alp 不得残留
            outDir.listFiles()?.filter { it.isFile && it.name.endsWith(".alp") }?.forEach { it.delete() }
            val allProjects =
                srcRoot.listFiles()
                    ?.filter { it.isDirectory }
                    ?.sortedBy { it.name }
                    .orEmpty()
            val tokens =
                examplesSelection.get()
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            val selected =
                when {
                    "all" in tokens -> allProjects
                    "none" in tokens || tokens.isEmpty() -> emptyList()
                    else ->
                        allProjects.filter { dir ->
                            dir.name.lowercase() in tokens.map { it.lowercase() }
                        }
                }
            if (selected.isEmpty()) {
                println("generateExamples: lide.examples=${examplesSelection.get()} -> 未生成内置示例（零内置，精简策略）")
                return@doLast
            }
            selected.forEach { dir ->
                val outFile = outDir.resolve(dir.name + ".alp")
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

// P0 运行时基座嵌入：runtime buildType（universal flavor）产物 → assets/runtime/classes.dex。
// 只嵌单个 dex 素材（2.6MB，非 APK）：打包器以宿主为模板时发现该资产，
// 把产物 dex 条目替换为引擎 dex（dex 替换模式），宿主不再内嵌 APK（用户否决内嵌 APK）。
// 不直接依赖 assembleUniversalRuntime（会构成 preBuild → embed → assemble → preBuild 环），
// 仅复制「已存在」的 runtime 产物；发布链脚本先 assembleUniversalRuntime 再 assemble 宿主。
//
// 精简治理（2026-08-07）：该资产使 APK 同时存在「宿主 classes*.dex 中的引擎类」与
// 「剥离 IDE 后的引擎 dex」两份引擎代码（受控冗余）。`-Plide.embedRuntimeDex=false`
// 可剔除该资产（默认 true 保持 P0 契约）；缺失时打包器兜底宿主 APK 自身 dex
// （AGENTS.md 运行时基座契约），不阻断运行期打包。
val embedRuntimeEnabled =
    providers.gradleProperty("lide.embedRuntimeDex")
        .orElse("true")
        .map { it != "false" }

val embedRuntimeDex =
    tasks.register("embedRuntimeDex") {
        group = "build"
        description = "解出 runtime 变体的 classes.dex 为 assets/runtime/classes.dex（打包模板；先执行 :app:assembleUniversalRuntime；-Plide.embedRuntimeDex=false 关闭）"
        val runtimeApk = layout.buildDirectory.file("outputs/apk/universal/runtime/app-universal-runtime.apk")
        val outDex = layout.projectDirectory.file("src/main/assets/runtime/classes.dex")
        // 源产物可能尚未构建（首次构建 / 未跑 assembleUniversalRuntime）：不能声明为
        // inputs.file（Gradle 校验输入存在性会失败），以 property 记录路径与时间戳。
        val srcApk = runtimeApk.get().asFile
        inputs.property("embedRuntimeEnabled", embedRuntimeEnabled)
        inputs.property("runtimeApkPath", srcApk.absolutePath)
        inputs.property(
            "runtimeApkStamp",
            providers.provider { if (srcApk.exists()) srcApk.lastModified() else 0L },
        )
        outputs.file(outDex).withPropertyName("engineDex")
        doLast {
            if (!embedRuntimeEnabled.get()) {
                // 门控关闭：移除旧资产，APK 不携带该引擎 dex 模板（由打包器兜底宿主 dex）
                val stale = outDex.asFile
                if (stale.exists()) {
                    stale.delete()
                    println("embedRuntimeDex: lide.embedRuntimeDex=false -> 已移除 ${stale.path}")
                } else {
                    println("embedRuntimeDex: lide.embedRuntimeDex=false -> 跳过生成（无旧资产）")
                }
                return@doLast
            }
            if (!srcApk.exists()) {
                println("embedRuntimeDex: 未找到 ${srcApk.path}（先执行 :app:assembleUniversalRuntime），跳过")
                return@doLast
            }
            val dexEntry = "classes.dex"
            val out = outDex.asFile
            out.parentFile.mkdirs()
            var found = false
            ZipInputStream(srcApk.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == dexEntry) {
                        out.outputStream().buffered().use { outStream -> zis.copyTo(outStream) }
                        found = true
                        break
                    }
                    entry = zis.nextEntry
                }
            }
            if (!found) throw GradleException("runtime APK 缺少 $dexEntry：${srcApk.path}")
            println("embedRuntimeDex: ${out.path} (${out.length()} bytes)")
        }
    }
tasks.named("preBuild").configure { dependsOn(embedRuntimeDex) }

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
    implementation(project(":androlua"))
    implementation(project(":editor"))
    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    // ui-tooling-preview 为 IDE 开发工具库（@Preview 渲染 + PreviewActivity manifest 组件）：
    // 仅 debug 变体需要（Android 官方模板同款 debugImplementation），
    // release/runtime 变体不携带——runtime 基座因此不残留 tooling 规则与组件。
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
    // material3 显式 1.4.0：新版 SearchBar（SearchBarState + ExpandedFullScreenSearchBar）
    // 为 1.4.0 新增 API，需 AGP 8.6+（compose 1.9 线）。
    implementation("androidx.compose.material3:material3:1.4.0")
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

    // APK v1/v2/v3 签名官方实现（Google，AGP 同源）。替代自研 ApkSigner/V2Signer
    // （手写 DER/CMS + v2 块，~460 行安全关键代码；apksig 提供对齐、v3、密钥轮换）。
    // 纯 Java，minSdk 24 可用（D8 负责 desugar，无需 core library desugaring）。
    implementation("com.android.tools.build:apksig:8.13.2")

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

// P0 运行时基座门禁：runtime 变体 mapping 校验「引擎面全保留 + IDE 类被 R8 剥离」。
// IDE 类剥离依据：runtime 变体 manifest 不引用 MainActivity/LideApp（src/runtime/
// AndroidManifest.xml 移除 + application 换为 LuaApplication），R8 从 manifest
// 入口可达性裁剪掉 IDE 依赖树。
tasks.register("verifyRuntimeSurface") {
    group = "verification"
    description = "校验 runtime 变体：luaApiSurface 全保留 + IDE 类被剥离（P0 需求 1）"
    dependsOn("assembleUniversalRuntime")
    doLast {
        val mapping = file("build/outputs/mapping/universalRuntime/mapping.txt")
        check(mapping.exists()) { "未找到 mapping 文件：$mapping" }
        val content = mapping.readText()
        val missing = luaApiSurface.filter { "$it -> " !in content }
        check(missing.isEmpty()) {
            "runtime 变体 Lua 可见 API 面被 R8 移除：\n" + missing.joinToString("\n") { "  - $it" }
        }
        // 断言 IDE 专属类被剥离（mapping 中无其条目）。
        // MainActivity 为 IDE 入口（含全部 intent-filter）；LideApp 为 IDE Application
        // （引用 SettingsManager/SigningManager/TextMateRuntime 等）；Compose UI 类为
        // IDE 依赖树代表。注意 sora 编辑器类经 TextMate 在 runtime 无入口，同样剥离。
        val ideClasses =
            listOf(
                "com.lioisme.lide.MainActivity",
                "com.lioisme.lide.LideApp",
                "com.lioisme.lide.ui.theme.SettingsManager",
            )
        val kept = ideClasses.filter { it in content }
        check(kept.isEmpty()) {
            "runtime 变体残留 IDE 类（未被 R8 剥离）：\n" + kept.joinToString("\n") { "  - $it" }
        }
        println("runtime 变体验证通过：luaApiSurface ${luaApiSurface.size} 个类保留，IDE 类已剥离")
    }
}

// 精简治理门禁（2026-08-07，见 docs/planned/MINIMALISM_AUDIT_REPORT.md 实施项 T4）：
// 对 release APK 做载荷体检，防止「重复 dex / 原生库 / 资源」回归：
//  1. jniLibs 源目录禁令：app/src/main/jniLibs 被重建即拒（引擎仅允许 cpp 源码构建，R5）；
//  2. lib/<abi>/ 白名单：仅允许引擎 18 模块 + androidx 图形库；未知 .so = 预编译回包即失败；
//  3. 同 ABI 同名 .so 唯一性：防「ndk 引擎 + jniLibs 预编译」双引擎并包；
//  4. ABI 允许集：arm64-v8a / x86_64（x86、armeabi-v7a 为未来扩展位）；
//  5. 默认零内置示例：lide.examples=none 时 APK 不得含 assets/examples/*.alp。
// 软告警（仅日志）：assets/runtime/classes.dex 存在（P0 打包模板，受控冗余）与载荷体积摘要。
tasks.register("verifyMinimalism") {
    group = "verification"
    description = "校验 APK 精简：无 jniLibs 预编译回包、无重复原生库、默认零内置示例"
    dependsOn("assembleArm64Release")
    val engineLibs =
        setOf(
            "libluajava", "libcjson", "libsocket", "libmime", "liblfs",
            "liblsqlite3", "liblive2d", "libcanvas", "libzlib", "libgl",
            "libbson", "libcrypt", "libmd5", "libregex", "libsensor",
            "libxml", "libzip", "libluv",
        )
    val allowedExtLibs = setOf("libandroidx.graphics.path")
    val allowedAbis = setOf("arm64-v8a", "x86_64", "x86", "armeabi-v7a")
    doLast {
        // 1) jniLibs 源目录禁令
        val jniRoot = layout.projectDirectory.dir("src/main/jniLibs").asFile
        check(!jniRoot.exists()) { "精简校验失败：app/src/main/jniLibs 被重建（引擎仅允许 cpp 源码构建，R5）" }

        // 抓取 release APK
        val apkDir = layout.buildDirectory.dir("outputs/apk/arm64/release").get().asFile
        val apkFile =
            apkDir.listFiles()
                ?.firstOrNull { it.isFile && it.extension == "apk" }
                ?: error("未找到 release APK（先执行 :app:assembleArm64Release）：$apkDir")
        val entries =
            ZipFile(apkFile).use { zf ->
                val enum = zf.entries()
                buildList {
                    while (enum.hasMoreElements()) {
                        val e = enum.nextElement()
                        add(e.name to e.size)
                    }
                }
            }

        // 2/3/4) lib/ 清单：abi -> (basename -> count)
        val libPerAbi = LinkedHashMap<String, MutableMap<String, Int>>()
        for ((name, _) in entries) {
            val m = Regex("^lib/([^/]+)/([^/]+)\\.so$").find(name) ?: continue
            val map = libPerAbi.getOrPut(m.groupValues[1]) { LinkedHashMap() }
            map[m.groupValues[2]] = (map[m.groupValues[2]] ?: 0) + 1
        }
        check(libPerAbi.isNotEmpty()) { "精简校验失败：APK lib/ 无任何原生库（引擎丢失）" }
        for ((abi, libs) in libPerAbi) {
            check(abi in allowedAbis) { "精简校验失败：非预期 ABI 目录 lib/$abi（疑似含 32 位/未知架构预编译）" }
            val unknown = libs.keys - engineLibs - allowedExtLibs
            check(unknown.isEmpty()) { "精简校验失败：lib/$abi 含白名单外 .so（疑似 jniLibs 预编译回包）：$unknown" }
            for ((basename, count) in libs) {
                check(count == 1) { "精简校验失败：lib/$abi/$basename 出现 $count 份（重复原生库）" }
            }
        }
        // 引擎库跨 ABI 一致性（防局部覆盖/漏包）＋ JNI 核心存在性
        val engineSetPerAbi = libPerAbi.mapValues { (_, map) -> map.keys.filter { it in engineLibs }.toSet() }
        val referenceSet = engineSetPerAbi.values.first()
        for ((abi, set) in engineSetPerAbi) {
            check(set == referenceSet) {
                "精简校验失败：ABI 引擎库集合不一致 $abi：缺失=${referenceSet - set} 多余=${set - referenceSet}"
            }
            check("libluajava" in set) { "精简校验失败：$abi 缺少 libluajava.so（JNI 核心未打包）" }
        }

        // 5) 默认零内置示例
        val exampleAlps = entries.filter { it.first.startsWith("assets/examples/") && it.first.endsWith(".alp") }
        val tokens = examplesSelection.get().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty() || "none" in tokens) {
            check(exampleAlps.isEmpty()) {
                "精简校验失败：lide.examples 未开启（默认 none），但 APK 携带内置示例：" +
                    exampleAlps.map { it.first }
            }
        }

        // 载荷摘要（软告警 + CI 直读日志）
        if (entries.any { it.first == "assets/runtime/classes.dex" }) {
            println("WARN（受控冗余）：assets/runtime/classes.dex 存在（P0 打包模板；可 -Plide.embedRuntimeDex=false 剔除）")
        }
        val libBytes = entries.filter { it.first.startsWith("lib/") && it.first.endsWith(".so") }.sumOf { it.second }
        val top = entries.sortedByDescending { it.second }.take(8)
        println("=== verifyMinimalism 载荷摘要（${apkFile.name}）===")
        println("条目数 ${entries.size}，原生库载荷 ${libBytes / 1024} KB，内置示例 ${exampleAlps.size} 个")
        top.forEach { (name, size) -> println("  %8d KB  $name".format(size / 1024)) }
        println("精简校验通过：无预编译 .so 回包、无重复原生库、默认零内置示例")
    }
}
