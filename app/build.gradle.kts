import java.io.BufferedOutputStream
import java.io.File
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
        if (keystorePropertiesFile.exists()) {
            load(keystorePropertiesFile.inputStream())
        }
    }
// 签名密钥治理（2026-08-09 源码组织三需求）：keystore.jks/properties 不入库（gitignore），
// 本地构建读 app/keystore.properties；CI 经 secrets 注入同名文件；无密钥文件时
// debug 走 AGP 默认签名、release 回落 debug 签名（fork/PR 场景可构建，门禁不受阻）。

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
        if (keystoreProperties.isNotEmpty()) {
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
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.findByName("debug")
            isMinifyEnabled = false
        }
        release {
            // 无密钥文件时 signingConfig 为 null（release APK 不签名，仅供门禁/CI fork 场景；
            // AGP 装配不失败）。正式发布经 secrets 注入 app/keystore.properties 后走 release 签名。
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.findByName("debug")
            // R8：androlua / luaj / 宿主类均可能被 Lua 脚本与插件 dex 按类名反射引用，
            // 引擎 keep 面见 proguard-rules.pro「引擎 keep 面」段（原 src/runtime/runtime-rules.pro
            // 随运行时拆分机制一并删除，2026-08-09）；启用后显著缩小方法与常量池。
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
        // 静默安装（SILENT_INSTALL）：Shizuku/Dhizuku 远程服务经 AIDL 接口桥接。
        aidl = true
    }

    // T23（2026-08-12）：Android lint 错误级门禁。warningsAsErrors 使存量/新增
    // 告警全部阻断构建（增量零容忍）；baseline 冻结存量，CI 一票否决。
    lint {
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = false
        // T23（2026-08-12）：存量告警冻结于 baseline（第三方 WorkManager
        // 源码目录 + 历史代码），增量零容忍——新增告警即构建失败。
        baseline = file("lint-baseline.xml")
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
        // kotlin-logging（2026-08-15 迁移）：宿主 JVM 单测类路径携带 Android 变体，其 detectLogger
        // 仅识别 kotlin-logging-to-android-native（否则回落 Slf4j 缺类）。置位后走
        // AndroidNativeLoggerFactory，与运行时同工厂；单测下 android.util.Log 桩返回默认值（isLoggable=false）即 no-op。
        unitTests.all { it.systemProperty("kotlin-logging-to-android-native", "true") }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// 内置示例选择（精简治理，2026-08-07，见 AGENTS.md「精简治理」）：
// `-Plide.examples` 控制哪些工程生成 .alp 打包进 assets/examples/：
//   - "all"            → scripts/examples-src/ 下全部工程（开发期重生成用）
//   - "hello,demo"     → 逗号分隔名单，仅生成 `scripts/examples-src` 对应目录
//   - 未设置 / "none"  → 不生成任何内置示例（当前默认 = 零内置，APK 最小化）
// 示例源模板（含 live2d 资源）保留在 scripts/examples-src/ 作为后续选样池，
// 未来需恢复内置工程时仅需在构建命令添加该属性，无需改代码。
val examplesSelection =
    providers
        .gradleProperty("lide.examples")
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
                srcRoot
                    .listFiles()
                    ?.filter { it.isDirectory }
                    ?.sortedBy { it.name }
                    .orEmpty()
            val tokens =
                examplesSelection
                    .get()
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            val selected =
                when {
                    "all" in tokens -> {
                        allProjects
                    }

                    "none" in tokens || tokens.isEmpty() -> {
                        emptyList()
                    }

                    else -> {
                        allProjects.filter { dir ->
                            dir.name.lowercase() in tokens.map { it.lowercase() }
                        }
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
    // :androlua 引擎模块（androlua/luajava/cglib/vendor Java 全量源码收纳，JAVA_LIBRARIES_AUDIT）。
    // 2026-08-09 源码组织三需求：打包产物不需要专门的 dex 拆分——implementation 并入
    // :app 单一 dex 集（根目录 classes*.dex），引擎类恰一份且始终存在；构建打包 androlua
    // 工程时打包器直用宿主全量根 dex。R8 keep 面见本文件「引擎 keep 面」段。
    implementation(project(":androlua"))

    // IDE 依赖树（compose / material / navigation / cascade / reorderable / sora / apksig）
    // 2026-08-09：runtime 变体与引擎 dex 纯净化机制已删除，一律 implementation（单 dex 集）。
    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    constraints {
        implementation("org.jetbrains:annotations:23.0.0")
    }
    val ideDeps =
        listOf(
            "androidx.compose.ui:ui",
            "androidx.compose.ui:ui-graphics",
            // material3 显式 1.4.0：新版 SearchBar（SearchBarState + ExpandedFullScreenSearchBar）
            // 为 1.4.0 新增 API，需 AGP 8.6+（compose 1.9 线）。
            "androidx.compose.material3:material3:1.4.0",
            "androidx.compose.material:material-icons-extended",
            "androidx.activity:activity-compose:1.9.3",
            "androidx.navigation:navigation-compose:2.8.5",
            "androidx.core:core-ktx:1.15.0",
            "androidx.lifecycle:lifecycle-runtime-ktx:2.8.7",
            // 编辑器会话 ViewModel（Q8，2026-08-09）：旋转/配置变更保留打开标签与会话状态；
            // 与 lifecycle-runtime-ktx 同版本线（2.8.7）。
            "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7",
            // 编辑器（并入自原 :editor 模块，2026-08-08 源码组织五需求 Phase 1）：
            // Tab 拖拽排序（WP-003，Squircle 同款固定版本；POM 实证与 Compose 1.7.3 二进制兼容）。
            "sh.calvin.reorderable:reorderable:3.0.0",
            // 多级「更多」菜单（一级分类 → 二级级联子菜单；M3 官方无嵌套菜单，成熟第三方）。
            "me.saket.cascade:cascade-compose:2.3.0",
            // Android Baseline Profile：src/release/baseline-prof.txt 经 R8 编译进 APK。
            "androidx.profileinstaller:profileinstaller:1.4.1",
            // APK v1/v2/v3 签名官方实现（Google，AGP 同源）。纯 Java，minSdk 24 可用。
            "com.android.tools.build:apksig:8.13.2",
            // Material3 官方 HCT 色板算法（2026-08-12 T-2 整改）：Google material-color-utilities
            // 的 Kotlin 移植（Apache 2.0，零传递依赖，纯 JVM 可测——ThemeContrastTest 依赖）。
            // 官方 Java 版仅内嵌于 MDC-Android AAR，独立坐标不存在，故用同源移植。
            "com.materialkolor:material-color-utilities:4.1.1",
            // 日志门面唯一实现（KOTLIN_LOGGING，2026-08-15）：宿主全部日志经 kotlin-logging
            // 产出（Android 原生工厂直连 android.util.Log，懒求值 + isLoggable 门控内建；
            // slf4j-api 为库侧 compileOnly，选 native 工厂则运行时零 SLF4J 依赖）。
            // 版本 pin 8.0.4（maven 已发布 tag；HEAD 的 8.0.5 为未发布 next，禁引用）。
            "io.github.oshai:kotlin-logging:8.0.4",
        )
    for (d in ideDeps) {
        implementation(d)
    }
    // sora-editor（0.24.6 vendored 源码）：编辑器核心 + TextMate 高亮，并入宿主单 dex 集。
    implementation(project(":sora-editor:editor"))
    implementation(project(":sora-editor:language-textmate"))

    // 静默安装（SILENT_INSTALL，2026-08-09）：Root（libsu）/ Shizuku（含 SUI）/ Dhizuku 三通道。
    // 版本对齐 komi-store gradle/libs.versions.toml（shizuku 13.1.5 / dhizuku 2.5.4 /
    // libsu 6.0.0 / hidden-api-stub 4.4.0）。全部 implementation 并入宿主单 dex 集
    // （无 runtime 拆分，维持引擎 dex 单份化契约，checkEngineDexMerge 不受影响）。
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // Shizuku 隐藏 API 签名桩：仅编译期可见，不打入产物（compileOnly）。
    compileOnly("dev.rikka.hidden:stub:4.4.0")
    implementation("io.github.iamr0s:Dhizuku-API:2.5.4")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")

    testImplementation(project(":androlua"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

// Lua 可见 API 面（2026-08-09 引擎并入单 dex 集修订 + 2026-08-09 单一事实源收敛）：
// :androlua 以 implementation 并入宿主单一 dex 集，R8 keep 面见 proguard-rules.pro「引擎 keep 面」段
// （原 src/runtime/runtime-rules.pro 已随运行时拆分机制删除）。
//
// 维护约定（2026-08-09 收敛，禁止再引入第二份手写清单）：
// 引擎面类唯一事实源 = app/engine-surface.txt（一行一个点分全限定类名，# 注释）：
//   1. 本文件读表 → engineSurfaceDescriptors（checkEngineDexMerge 跨 dex 恰一份断言）；
//   2. proguard-rules.pro keep 覆盖由 EngineSurfaceConsistencyTest 断言（通配/单列命中）；
//   3. ApiCategoryClassifier.androluaAndroidPrefixes 条目必须在此登记，同测试断言归类 ANDROLUA。
// 新增 / 删除引擎表面类只改 engine-surface.txt 一处。
fun scanDexClassNames(dexBytes: ByteArray): List<String> {
    if (dexBytes.size < 0x70 || dexBytes[0] != 'd'.code.toByte() ||
        dexBytes[1] != 'e'.code.toByte() || dexBytes[2] != 'x'.code.toByte() || dexBytes[3] != '\n'.code.toByte()
    ) {
        return emptyList()
    }

    fun u32(off: Int): Int =
        (dexBytes[off].toInt() and 0xFF) or
            ((dexBytes[off + 1].toInt() and 0xFF) shl 8) or
            ((dexBytes[off + 2].toInt() and 0xFF) shl 16) or
            ((dexBytes[off + 3].toInt() and 0xFF) shl 24)

    fun uleb(off: Int): Pair<Int, Int> {
        var value = 0
        var shift = 0
        var p = off
        while (true) {
            val b = dexBytes[p].toInt() and 0xFF
            p++
            value = value or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        return value to p
    }

    val stringIdsSize = u32(0x38)
    val stringIdsOff = u32(0x3C)
    val typeIdsSize = u32(0x40)
    val typeIdsOff = u32(0x44)
    val classDefsSize = u32(0x60)
    val classDefsOff = u32(0x64)
    if (stringIdsSize <= 0 || typeIdsSize <= 0 || classDefsSize <= 0) return emptyList()
    val stringOffsets = IntArray(stringIdsSize)
    for (i in 0 until stringIdsSize) {
        stringOffsets[i] = u32(stringIdsOff + i * 4)
    }
    val names = ArrayList<String>(classDefsSize)
    for (i in 0 until classDefsSize) {
        // class_defs[].class_idx 是 type_ids 索引（2026-08-08 修复：此前误作
        // string_ids 索引导致类名解析错位，引擎类断言形同虚设）。二次间接：
        // class_idx -> type_ids[class_idx].descriptor_idx -> string_ids。
        val classIdx = u32(classDefsOff + i * 32)
        if (classIdx >= typeIdsSize) continue
        val descriptorIdx = u32(typeIdsOff + classIdx * 4)
        if (descriptorIdx >= stringIdsSize) continue
        val strOff = stringOffsets[descriptorIdx]
        val (nameLen, contentOff) = uleb(strOff)
        val sb = StringBuilder(nameLen)
        var p = contentOff
        var count = 0
        while (count < nameLen && p < dexBytes.size && dexBytes[p].toInt() != 0) {
            sb.append(dexBytes[p].toInt().toChar())
            p++
            count++
        }
        names.add(sb.toString())
    }
    return names
}

val engineSurfaceDescriptors: Set<String> =
    file("engine-surface.txt")
        .readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { "L${it.replace('.', '/')};" }
        .toSet()

/**
 * 引擎并入单 dex 集断言（2026-08-09 源码组织三需求，替代原跨 dex 拆分断言）：
 * 1. 引擎面类描述符在全部根 dex 中**恰好出现一次**（implementation 并入后引擎类
 *    与宿主同 dex 集，无任何拆分/注入/标记；R8 keep 面见「引擎 keep 面」段）；
 * 2. 引擎核心（LuaActivity / LuaApplication）必须存在；
 * 3. 旧形态回归即失败：assets/engine.dex.idx 标记、assets/runtime/classes.dex
 *    内嵌形态均不允许再出现（runtime buildType 与 injectEngineDex 已删除）。
 */
fun checkEngineDexMerge(apkFile: java.io.File) {
    val entries =
        ZipFile(apkFile).use { zf ->
            val enum = zf.entries()
            buildList {
                while (enum.hasMoreElements()) add(enum.nextElement().name)
            }
        }
    check("assets/engine.dex.idx" !in entries) {
        "旧形态回归：APK 含 assets/engine.dex.idx 标记（engineDexExtract/injectEngineDex 已删除）"
    }
    check(entries.none { it.startsWith("assets/runtime/") }) {
        "旧形态回归：APK 含 assets/runtime/ 内嵌引擎 dex（已废弃，见精简治理）"
    }
    val dexFiles = entries.filter { it.matches(Regex("classes\\d*\\.dex")) }
    val occurrence = HashMap<String, MutableList<String>>()
    ZipFile(apkFile).use { zf ->
        for (name in dexFiles) {
            for (c in scanDexClassNames(zf.getInputStream(zf.getEntry(name)).readBytes())) {
                occurrence.getOrPut(c) { ArrayList() }.add(name)
            }
        }
    }
    val dupes =
        engineSurfaceDescriptors
            .mapNotNull { d -> occurrence[d]?.let { d to it } }
            .filter { (_, dexes) -> dexes.size != 1 }
    check(dupes.isEmpty()) {
        "引擎类跨 dex 唯一性校验失败（implementation 并入后必须全 APK 恰好一份）：\n" +
            dupes.joinToString("\n") { (d, dexes) -> "  $d -> $dexes" }
    }
    val missing =
        engineSurfaceDescriptors.filter { it !in occurrence }
    check(missing.isEmpty()) {
        "引擎面类缺失校验失败（R8 keep 面遗漏或 :androlua 未并入 dex）：\n" + missing.joinToString("\n")
    }
    check("Lcom/androlua/LuaActivity;" in occurrence && "Lcom/androlua/LuaApplication;" in occurrence) {
        "引擎核心缺失：根 dex 无 LuaActivity/LuaApplication"
    }
    println("引擎并入单 dex 集校验通过：引擎面 ${engineSurfaceDescriptors.size} 类全部唯一存在（共 ${dexFiles.size} 个根 dex）")
}

// 精简治理门禁（2026-08-07，见 AGENTS.md「精简治理」）：
// 对 release APK 做载荷体检，防止「重复 dex / 原生库 / 资源」回归：
//  1. jniLibs 源目录禁令：app/src/main/jniLibs 被重建即拒（引擎仅允许 cpp 源码构建，R5）；
//  2. lib/<abi>/ 白名单：仅允许引擎 18 模块 + androidx 图形库；未知 .so = 预编译回包即失败；
//  3. 同 ABI 同名 .so 唯一性：防「ndk 引擎 + jniLibs 预编译」双引擎并包；
//  4. ABI 允许集：arm64-v8a / x86_64（x86、armeabi-v7a 为未来扩展位）；
//  5. 默认零内置示例：lide.examples=none 时 APK 不得含 assets/examples/*.alp；
//  6. 引擎并入单 dex 集（2026-08-09 源码组织三需求）：dex 一律在 APK 根目录；旧
//     assets/runtime/classes.dex 内嵌形态与 assets/engine.dex.idx 标记出现即失败；
//     引擎面类经 checkEngineDexMerge 断言跨根 dex 恰一份（R8 keep 面见「引擎 keep 面」段）。
// 载荷体积摘要（软日志）。
tasks.register("verifyMinimalism") {
    group = "verification"
    description = "校验 APK 精简：无 jniLibs 预编译回包、无重复原生库、默认零内置示例、引擎并入单 dex 集"
    dependsOn("assembleArm64Release")
    val engineLibs =
        setOf(
            "libluajava",
            "libcjson",
            "libsocket",
            "libmime",
            "liblfs",
            "liblsqlite3",
            "liblive2d",
            "libcanvas",
            "libzlib",
            "libgl",
            "libbson",
            "libcrypt",
            "libmd5",
            "libregex",
            "libsensor",
            "libxml",
            "libzip",
            "libluv",
        )
    val allowedExtLibs = setOf("libandroidx.graphics.path")
    val allowedAbis = setOf("arm64-v8a", "x86_64", "x86", "armeabi-v7a")
    doLast {
        // 1) jniLibs 源目录禁令
        val jniRoot = layout.projectDirectory.dir("src/main/jniLibs").asFile
        check(!jniRoot.exists()) { "精简校验失败：app/src/main/jniLibs 被重建（引擎仅允许 cpp 源码构建，R5）" }

        // 抓取 release APK
        val apkDir =
            layout.buildDirectory
                .dir("outputs/apk/arm64/release")
                .get()
                .asFile
        val apkFile =
            apkDir
                .listFiles()
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
        val tokens =
            examplesSelection
                .get()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        if (tokens.isEmpty() || "none" in tokens) {
            check(exampleAlps.isEmpty()) {
                "精简校验失败：lide.examples 未开启（默认 none），但 APK 携带内置示例：" +
                    exampleAlps.map { it.first }
            }
        }

        // 6) 引擎并入单 dex 集（2026-08-09）：dex 一律在 APK 根目录；旧 assets/runtime/
        //    内嵌形态与 assets/engine.dex.idx 标记出现即失败（runtime buildType /
        //    engineDexExtract / injectEngineDex 已删除，不得回退）。
        check(entries.none { it.first == "assets/runtime/classes.dex" }) {
            "精简校验失败：旧 assets/runtime/classes.dex 内嵌形态已废弃（dex 必须位于 APK 根目录，用户红线）"
        }

        // 7) 引擎类跨根 dex 恰一份断言（2026-08-09，替代原跨 dex 拆分断言）
        checkEngineDexMerge(apkFile)

        // 载荷摘要（软告警 + CI 直读日志）
        val libBytes = entries.filter { it.first.startsWith("lib/") && it.first.endsWith(".so") }.sumOf { it.second }
        val top = entries.sortedByDescending { it.second }.take(8)
        println("=== verifyMinimalism 载荷摘要（${apkFile.name}）===")
        println("条目数 ${entries.size}，原生库载荷 ${libBytes / 1024} KB，内置示例 ${exampleAlps.size} 个")
        top.forEach { (name, size) -> println("  %8d KB  $name".format(size / 1024)) }
        println("精简校验通过：无预编译 .so 回包、无重复原生库、默认零内置示例")
    }
}

// debug 变体的引擎并入单 dex 集门禁（2026-08-09）：开发期快速验证引擎类跨根 dex
// 恰一份（release 的 verifyMinimalism 已含同断言；debug 无 R8 混淆、结构更接近直出）。
tasks.register("verifyMinimalismDebug") {
    group = "verification"
    description = "校验 debug APK 引擎并入单 dex 集（R-5）：引擎面类唯一存在 + 无旧 dex 形态"
    dependsOn("assembleArm64Debug")
    doLast {
        val apkDir =
            layout.buildDirectory
                .dir("outputs/apk/arm64/debug")
                .get()
                .asFile
        val apkFile =
            apkDir
                .listFiles()
                ?.firstOrNull { it.isFile && it.extension == "apk" }
                ?: error("未找到 debug APK（先执行 :app:assembleArm64Debug）：$apkDir")
        checkEngineDexMerge(apkFile)
    }
}

// vendor 隔离门禁（源码组织审计 Phase 2，2026-08-08，路径随源码归位 2026-08-09 修订）：
// 上游目录（androlua / sora-editor，位于 app/vendor/）零 LIDE 代码入驻（R-2），
// 且改动台账一致（R-3 增量侧闭环，台账 = app/vendor/androlua/UPSTREAM.md）：
//  1. app/vendor/*/src 不存在 com/lioisme/**；
//  2. app/vendor/androlua/UPSTREAM.md 存在（改动台账，缺失 = 上游漂移即失败）；
//  3. 台账登记项与源码实证一致：W^X 修复命中（InMemoryDexClassLoader，允许的非逻辑
//     适配）、androidx.annotation 零命中（注解导入剥离，非逻辑适配）、
//     android/support/annotation 已还原（IntRange/RequiresApi 上游原样，2026-08-09）；
//  4. :androlua 构建脚本零第三方依赖（仅 android 库插件）；
//  5. 第三方模块构建脚本零仓库外部 URL 依赖（sora vendored 构建自足，R-3 缺失侧不回退）。
tasks.register("verifyVendorIsolation") {
    group = "verification"
    description = "校验上游目录隔离（R-2）：app/vendor/*/src 无 com/lioisme/** + UPSTREAM 台账一致"
    doLast {
        val upstreamDoc = rootProject.file("app/vendor/androlua/UPSTREAM.md")
        check(upstreamDoc.exists()) {
            "vendor 隔离失败：app/vendor/androlua/UPSTREAM.md 不存在（改动台账缺失，R-3 增量侧未闭环）"
        }

        val vendorSrcDirs =
            listOf(
                rootProject.file("app/vendor/androlua/src"),
                rootProject.file("app/vendor/sora-editor/editor/src"),
                rootProject.file("app/vendor/sora-editor/language-textmate/src"),
            )
        for (dir in vendorSrcDirs) {
            val d = file(dir)
            check(d.isDirectory) { "vendor 隔离失败：$dir 不是目录" }
            val lideFiles =
                d
                    .walkTopDown()
                    .filter { it.isFile }
                    .filter { rel -> Regex("com[/\\\\]lioisme[/\\\\]").containsMatchIn(rel.relativeTo(d).invariantSeparatorsPath) }
                    .toList()
            check(lideFiles.isEmpty()) {
                "vendor 隔离失败：$dir 含 LIDE 代码（com/lioisme/**，R-2 上游零污染）：\n" +
                    lideFiles.joinToString("\n") { "  - $it" }
            }
        }

        // 台账一致性实证
        val dexLoader = rootProject.file("app/vendor/androlua/src/main/java/com/androlua/LuaDexLoader.java")
        check(dexLoader.exists()) { "vendor 隔离失败：LuaDexLoader.java 缺失（上游结构漂移）" }
        check("InMemoryDexClassLoader" in dexLoader.readText()) {
            "vendor 隔离失败：LuaDexLoader 缺 W^X 修复（InMemoryDexClassLoader，台账 3.1 允许的非逻辑适配不一致）"
        }
        val annotationSupportDir = rootProject.file("app/vendor/androlua/src/main/java/android/support/annotation")
        check(annotationSupportDir.isDirectory) {
            "vendor 隔离失败：android/support/annotation 未还原（2026-08-09 台账 3.2 修订：应保留上游原样）"
        }
        for (f in listOf("IntRange.java", "RequiresApi.java")) {
            check(annotationSupportDir.resolve(f).isFile) {
                "vendor 隔离失败：android/support/annotation/$f 缺失（应还原为上游原样）"
            }
        }
        val annotationHits =
            rootProject
                .file("app/vendor/androlua/src")
                .walkTopDown()
                .filter { it.isFile && it.extension == "java" }
                .count { f -> "androidx.annotation" in f.readText() }
        check(annotationHits == 0) {
            "vendor 隔离失败：:androlua 源码含 androidx.annotation（台账 3.2 注解剥离未闭环）：$annotationHits 文件"
        }
        val androluaBuild = rootProject.file("app/vendor/androlua/build.gradle.kts").readText()
        check("com.android.library" in androluaBuild) {
            "vendor 隔离失败：:androlua 构建脚本非 android 库形态（台账 3.2 不一致）"
        }
        check(Regex("dependencies\\s*\\{").containsMatchIn(androluaBuild)) {
            "vendor 隔离失败：:androlua 构建脚本应有 dependencies 块（零第三方依赖，台账 3.2）"
        }
        // 命名空间注入（台账 3.2 登记，2026-08-11 解耦审计 A-1）：AGP 模块唯一命名空间，
        // 运行类名保持上游 com.androlua.* 不变（模块无 res、无 R 类），纯构建期元数据。
        check("namespace = \"com.lioisme.lide.androlua\"" in androluaBuild) {
            "vendor 隔离失败：:androlua 命名空间注入形态漂移（台账 3.2 登记为 com.lioisme.lide.androlua）"
        }

        // sora vendored 构建自足（白名单恢复机制登记于 verify-upstream-pristine，此处防外部 URL 回退）
        for (p in listOf(
            "app/vendor/sora-editor/editor/build.gradle.kts",
            "app/vendor/sora-editor/language-textmate/build.gradle.kts",
        )) {
            val f = rootProject.file(p)
            check(f.exists()) { "vendor 隔离失败：$p 缺失（sora 结构漂移）" }
            check(!Regex("https?://").containsMatchIn(f.readText())) {
                "vendor 隔离失败：$p 含仓库外部 URL 依赖（vendored 构建应自足）"
            }
        }
        println("vendor 隔离校验通过：上游目录零 com/lioisme/**，UPSTREAM.md 台账与源码一致（含 W^X 白名单与 annotation 还原断言）")
    }
}

// sora 门面隔离门禁（解耦审计 B 级收口，2026-08-11）：
// sora（io.github.rosemoe.*）只允许出现在 editor 包（LuaEditor/EditorLanguage/EditorDiagnostics
// 等文本级能力）与 vendor 源码；session/ui/project/engine 等宿主层一律经
// EditorContent / EditorController 门面（B-1~B-3 收口后无例外）——禁止直接 import 或 FQN
// 引用 sora 类型，违反即失败（防新代码扩散：换编辑器引擎时宿主层零改写）。
tasks.register("verifySoraIsolation") {
    group = "verification"
    description = "校验 sora 门面隔离（B 级）：editor 包外零 io.github.rosemoe 引用（含 FQN 形态）"
    doLast {
        val hostRoot = rootProject.file("app/src/main/kotlin/com/lioisme/lide")
        check(hostRoot.isDirectory) { "sora 隔离失败：宿主源码根缺失 $hostRoot" }
        val offenders = mutableListOf<String>()
        hostRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { f ->
                val rel = f.relativeTo(hostRoot).invariantSeparatorsPath
                !rel.startsWith("editor") && !rel.startsWith("editor/")
            }.forEach { f ->
                if ("io.github.rosemoe" in f.readText()) {
                    offenders += f.relativeTo(rootProject.rootDir).invariantSeparatorsPath
                }
            }
        check(offenders.isEmpty()) {
            "sora 隔离失败：editor 包外存在 io.github.rosemoe 引用（宿主层必须经 EditorContent/EditorController 门面）：\n" +
                offenders.joinToString("\n") { "  - $it" }
        }
        println("sora 隔离校验通过：editor 包外零 io.github.rosemoe 引用（import 与 FQN 形态全覆盖）")
    }
}
