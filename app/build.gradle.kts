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
        // 运行时引擎 dex（P0，需求 1，引擎 dex 单份化 2026-08-08）：仅 androlua 运行环境
        // + 引擎核心，经 engineDexExtract 解出 classes.dex 为 build/engine/engine.dex，
        // 由 injectEngineDex 注入宿主 APK 根目录（单一 classloader 自动加载，无重复副本）。
        // R8 keep 面见 src/runtime/runtime-rules.pro（luaApiSurface + 引擎类，不 keep IDE UI）；
        // 该 buildType 不参与宿主常规发布（assembleXxxDebug/Release 不受影响）。
        create("runtime") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            // 不用 proguard-android-optimize.txt：R8 优化会生成合成 outline 辅助类
            // （$$ExternalSyntheticOutline*），其短混淆名与宿主 release R8 的同名类
            // 在合并 classloader 内冲突（2026-08-08 模拟器实测 NoSuchMethodError：
            // 引擎 b0.c=compose outline vs 宿主 b0.c=GridSlotCache）。只 shrink+obfuscate，
            // 被混淆类统一 repackage 到 lide_runtime_ 前缀（见 runtime-rules.pro）。
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
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

// P0 运行时基座（引擎 dex 单份化，2026-08-08）：宿主安装包不再内嵌 assets/runtime/classes.dex。
// 契约（AGENTS.md 运行时基座契约，用户红线：所有 dex 一律在宿主 APK 根目录、禁其他目录、禁多份）：
//  - 宿主根目录 classes*.dex 仅含 IDE 代码（:androlua 为 compileOnly，引擎类不进宿主 dex）；
//  - 引擎 dex 唯一一份：由 runtime buildType（universal flavor）R8 产物解出（engineDexExtract
//    → build/engine/engine.dex），经 injectEngineDex<Variant> 追加进各宿主变体 dex 合并产物
//    （mergeLibDex 尾组 "zz" 子目录，package 任务按序编号为根目录 classesN.dex）；
//  - 根目录条目名经非 dex 标记 assets/engine.dex.idx 记录（P0-7 修订（2026-08-08）：
//    打包器不再据此选取 dex——产物直用宿主全量根 dex（ApkBuilder dex 替换模式已移除），
//    标记仅作引擎存在性判定：命中合法条目时打包产物 manifest 回落 LuaApplication +
//    移除 IDE MainActivity，未命中保持宿主 manifest（pro 模板语义，兼容瘦身构建）。
//    ApkBuilder.ENGINE_DEX_MARKER）。
// 发布链：:app:assembleUniversalRuntime → :app:assembleXxxDebug/Release（宿主携带根 dex）。
// 精简治理：`-Plide.embedRuntimeDex=false` 剔除引擎 dex（默认 true 保持 P0 契约）。
val engineDexEnabled =
    providers
        .gradleProperty("lide.embedRuntimeDex")
        .orElse("true")
        .map { it != "false" }

// runtime APK 路径（engineDexExtract 与 inject 新鲜度校验共用）。
val engineRuntimeApkFile = layout.buildDirectory.file("outputs/apk/universal/runtime/app-universal-runtime.apk")

val engineDexExtract =
    tasks.register("engineDexExtract") {
        group = "build"
        description = "解出 runtime 变体 classes.dex 为 build/engine/engine.dex（根目录注入源；先执行 :app:assembleUniversalRuntime；-Plide.embedRuntimeDex=false 关闭）"
        val runtimeApk = engineRuntimeApkFile
        val outDex = layout.buildDirectory.file("engine/engine.dex")
        // 源产物可能尚未构建（首次构建 / 未跑 assembleUniversalRuntime）：不能声明为
        // inputs.file（Gradle 校验输入存在性会失败），以 property 记录路径与时间戳。
        val srcApk = runtimeApk.get().asFile
        inputs.property("engineDexEnabled", engineDexEnabled)
        inputs.property("runtimeApkPath", srcApk.absolutePath)
        inputs.property(
            "runtimeApkStamp",
            providers.provider { if (srcApk.exists()) srcApk.lastModified() else 0L },
        )
        outputs.file(outDex).withPropertyName("engineDex")
        doLast {
            val out = outDex.get().asFile
            if (!engineDexEnabled.get()) {
                // 门控关闭：移除旧产物，宿主不携带引擎 dex（打包器按无引擎形态装配，
                // manifest 不回落）
                if (out.exists()) {
                    out.delete()
                    println("engineDexExtract: lide.embedRuntimeDex=false -> 已移除 ${out.path}")
                } else {
                    println("engineDexExtract: lide.embedRuntimeDex=false -> 跳过生成（无旧产物）")
                }
                return@doLast
            }
            if (!srcApk.exists()) {
                println("engineDexExtract: 未找到 ${srcApk.path}（先执行 :app:assembleUniversalRuntime），跳过")
                return@doLast
            }
            val dexEntries = mutableListOf<String>()
            ZipInputStream(srcApk.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.matches(Regex("classes\\d*\\.dex"))) dexEntries += entry.name
                    entry = zis.nextEntry
                }
            }
            check("classes.dex" in dexEntries) { "runtime APK 缺少 classes.dex：${srcApk.path}" }
            val extra = dexEntries.filter { it != "classes.dex" }
            if (extra.isNotEmpty()) {
                println("WARN: runtime 变体含多 dex（$extra），仅提取 classes.dex（引擎核心）")
            }
            out.parentFile.mkdirs()
            var found = false
            ZipInputStream(srcApk.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "classes.dex") {
                        out.outputStream().buffered().use { outStream -> zis.copyTo(outStream) }
                        found = true
                        break
                    }
                    entry = zis.nextEntry
                }
            }
            if (!found) throw GradleException("runtime APK 缺少 classes.dex：${srcApk.path}")
            println("engineDexExtract: ${out.path} (${out.length()} bytes)")
        }
    }

// 每宿主变体（debug/release）注入：引擎 dex 追加进 package 的 dex 输入末端
// （debug 走 mergeLibDex 尾组子目录 "zz"；release 由 R8 直接出 dex，追加为 minify
// 输出目录末位文件 "zz.dex"，字典序恒排 classesN 后），由 package 任务编号为
// 根目录 classesN.dex（位置公式见 injectBody 内注释，实测语义）；同时把条目名写入非 dex
// 标记 assets/engine.dex.idx（打包器引擎存在性判定契约，见上）。runtime 变体
// 不注入（其产物即引擎 dex 来源，不可回环）。不直接依赖 assembleUniversalRuntime
// （会构成 preBuild → extract → assemble → preBuild 环）：发布链脚本先 assemble 再宿主。
//
// P0-6 全链验证缺陷修复（2026-08-08）：release 的 minify（R8）任务**执行**时，
// Gradle 在任务完成时定格其输出快照（R8 声明输出仅 classes.dex）——若引擎 dex 仅由
// 独立 inject 任务在 minify 之后写入输出目录，package 消费的是该输出快照，不会包含
// 追加的 zz.dex → APK 缺引擎 dex（verifyMinimalism「标记指向但根目录无该条目」失败）。
// 修复：把注入实体挂到 minify 的 doLast（任务完成时快照含 zz.dex），inject 任务
// 保留幂等落位（minify up-to-date 时 doLast 不执行，由 inject 补写）。
// debug 的 mergeLibDex 输出为目录级输入（实测 APK 含引擎 dex），不额外挂钩；
// AGP 升级若语义变化需同步补 doLast。
androidComponents {
    listOf("debug", "release").forEach { bt ->
        onVariants(selector().withBuildType(bt)) { variant ->
            val vn = variant.name.replaceFirstChar { it.uppercaseChar() }
            val mergeAssetsTask = "merge${vn}Assets"
            val compressAssetsTask = "compress${vn}Assets"
            // dex 输入目录（package 实际消费形态）：
            //  - debug：mergeLibDex 输出（子目录为单位，zz 尾组子目录）；
            //  - release：R8（minify${vn}WithR8）输出，无 mergeLibDex 任务。
            val dexDir =
                if (bt == "debug") {
                    layout.buildDirectory
                        .dir("intermediates/dex/${variant.name}/mergeLibDex$vn")
                        .get()
                        .asFile
                } else {
                    layout.buildDirectory
                        .dir("intermediates/dex/${variant.name}/minify${vn}WithR8")
                        .get()
                        .asFile
                }

            // 注入实体 1/2：仅把引擎 dex 拷贝为末位 zz 条目（幂等）。release minify 的
            // doLast 只调本函数（该时点 assets 压缩/merge 可能未执行，标记由 inject 任务落位）。
            // 返回是否实际写入（引擎 dex 缺失时为 false，调用方据此跳过标记写入）。
            fun writeEngineDex(): Boolean {
                if (!engineDexEnabled.get()) {
                    println("injectEngineDex$vn: lide.embedRuntimeDex=false -> 跳过注入")
                    return false
                }
                val engineFile = engineDexExtract.map { it.outputs.files }.get().singleOrNull()
                if (engineFile == null || !engineFile.exists()) {
                    println("injectEngineDex$vn: 引擎 dex 缺失（先执行 :app:assembleUniversalRuntime），跳过")
                    return false
                }
                // 新鲜度防呆（2026-08-08 单命令多目标实测竞态）：engineDexExtract 不依赖
                // assembleUniversalRuntime（防环），宿主 assemble 与之并行时可能读到旧 APK。
                // 判据：runtime APK 落盘晚于提取产物 → 引擎 dex 过期，抛错引导分命令执行
                // （发布链契约：先 assembleUniversalRuntime 再宿主；verifyMinimalism/
                // 打包链同此）。
                val rtApk = engineRuntimeApkFile.get().asFile
                if (rtApk.exists() && rtApk.lastModified() > engineFile.lastModified() + 2000) {
                    throw GradleException(
                        "引擎 dex 过期：${rtApk.path}（mtime ${rtApk.lastModified()}）晚于提取产物 " +
                            "${engineFile.path}（mtime ${engineFile.lastModified()}）。请先单独执行 " +
                            ":app:assembleUniversalRuntime 再执行宿主构建（同一命令多目标会构成竞态）。",
                    )
                }
                check(dexDir.isDirectory) { "dex 合并输出缺失：$dexDir（AGP 任务图变更？）" }
                val out = if (bt == "debug") File(dexDir, "zz/classes.dex") else File(dexDir, "zz.dex")
                out.parentFile.mkdirs()
                // Windows 下增量构建时旧注入产物可能被文件监视器短暂占用，删除加短重试，
                // 避免 FileAlreadyExistsException 偶发失败。
                var copied = false
                for (attempt in 0 until 5) {
                    if (out.exists() && !out.delete() && attempt == 4) {
                        throw GradleException("无法覆盖注入目标：$out（文件被占用？）")
                    }
                    if (!out.exists()) {
                        engineFile.copyTo(out)
                        copied = true
                        break
                    }
                    Thread.sleep(100)
                }
                if (!copied) throw GradleException("无法写入注入目标：$out")
                return true
            }

            // 注入实体 2/2：写引擎 dex + 落位标记（inject 任务 doLast 专用，
            // 依赖 merge/compress assets 完成后执行，标记才随包发布）。
            fun injectBody() {
                if (!writeEngineDex()) return
                // dex 编号实测语义（20xx-08-08，哈希映射实证）：
                //  - debug：package 的 dexFolders 解析顺序 = [mergeExtDex 首文件(classes.dex)
                //    → mergeLibDex 各子目录(含文件者，字典序) → 其余]；"zz" 字典序恒排数字后
                //    → 引擎条目位置 = 1 + lib 文件数(不含 zz) + 1。
                //  - release：package 按输入文件排序后按位置编号（index 0 → classes.dex），
                //    R8 输出恒为 classes.dex，追加的 "zz.dex" 排序恒最后 → 位置 = 现有
                //    dex 文件数(不含 zz) + 1（无 debug 的首文件偏移）。
                // 编号规则：pos==1 → classes.dex，否则 classes$pos.dex。
                // 注意：计数必须排除注入自身（debug 的 zz 子目录 / release 的 zz.dex）——
                // 增量构建时上次注入产物仍滞留，计入会漂移 +1，造成标记与 APK 实际条目错位
                // （引擎存在性误判、manifest 回落失效）。
                val existingDexFiles =
                    if (bt == "debug") {
                        dexDir
                            .listFiles()
                            ?.filter { it.isDirectory && it.name != "zz" }
                            ?.count { dir -> (dir.listFiles()?.count { it.isFile } ?: 0) > 0 }
                            ?: 0
                    } else {
                        dexDir.listFiles()?.count { it.isFile && it.name != "zz.dex" } ?: 0
                    }
                val pos = if (bt == "debug") existingDexFiles + 2 else existingDexFiles + 1
                val dexName = if (pos == 1) "classes.dex" else "classes$pos.dex"
                val engineFile = checkNotNull(engineDexExtract.map { it.outputs.files }.get().singleOrNull())
                // 标记只写一处（2026-08-08 修复）：压缩资产 out/assets 下的
                // <name>.jar（package 实际消费形态：jar 内单条目 "assets/engine.dex.idx"，
                // 与 compress 任务产物同构）。
                // 注意：**不得**把 engine.dex.idx 写进 merge 资产中间产物——那会成为
                // CompressAssets 增量输入，下次构建 compress 重跑时会把该文件压成同名
                // 条目，与手工 jar 冲突（CompressAssetsWorkAction 报 already contains
                // entry）。compress 输入保持不变则其恒 UP-TO-DATE，jar 由本任务删除重建。
                val compressOutAssets =
                    layout.buildDirectory
                        .dir("intermediates/compressed_assets/${variant.name}/$compressAssetsTask/out/assets")
                        .get()
                        .asFile
                if (compressOutAssets.isDirectory) {
                    // inject 恒执行（无 outputs）：旧 jar 必须删掉重建——ZipOutputStream
                    // 对同名条目直接抛 "already contains entry"（增量构建二次注入实测崩）。
                    val markerJar = File(compressOutAssets, "engine.dex.idx.jar")
                    if (markerJar.exists() && !markerJar.delete()) {
                        throw GradleException("无法删除旧标记 jar：$markerJar（文件被占用？）")
                    }
                    ZipOutputStream(BufferedOutputStream(FileOutputStream(markerJar))).use { zos ->
                        zos.putNextEntry(ZipEntry("assets/engine.dex.idx"))
                        zos.write(dexName.toByteArray(Charsets.UTF_8))
                        zos.closeEntry()
                    }
                } else {
                    throw GradleException("压缩资产输出缺失：$compressOutAssets（AGP 任务图变更？）")
                }
                println("injectEngineDex$vn: 根目录 $dexName (${engineFile.length()} bytes) + assets/engine.dex.idx")
            }

            val inject =
                tasks.register("injectEngineDex$vn") {
                    group = "build"
                    description = "把引擎 dex（engineDexExtract 产物）注入根目录 classesN.dex 并写 assets/engine.dex.idx（-Plide.embedRuntimeDex=false 关闭）"
                    // 压缩资产（package 实际读取的 assets 形态）必须已完成，标记才随包发布；
                    // 本任务不声明 outputs → 恒执行，引擎 dex/标记变更总能重新落位。
                    dependsOn(engineDexExtract, mergeAssetsTask, compressAssetsTask)
                    if (bt == "debug") {
                        dependsOn("mergeLibDex$vn")
                    } else {
                        dependsOn("minify${vn}WithR8")
                    }
                    doLast { injectBody() }
                }
            // release 关键挂钩：minify 执行时输出快照在任务完成时定格，独立任务追加的
            // zz.dex 不在快照内（package 漏包）；doLast 内写入保证完成时快照含引擎 dex。
            // 注意必须用 configureEach 延迟挂钩：onVariants 回调期 minify 任务尚未注册，
            // 直接 tasks.named 会在配置期抛 UnknownTaskException。
            if (bt == "release") {
                tasks.configureEach {
                    if (name == "minify${vn}WithR8") {
                        doLast { writeEngineDex() }
                    }
                }
            }
            tasks.matching { it.name == "package$vn" }.configureEach { dependsOn(inject) }
        }
    }
}

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
    // 引擎 dex 单份化（2026-08-08，用户红线：dex 一律在宿主 APK 根目录、禁多份）：
    //  - compileOnly：宿主仅编译期可见引擎类，宿主 classes*.dex 不含引擎类（引擎类唯一的
    //    运行期来源 = injectEngineDex 注入的根目录引擎 dex，单一 classloader 自动加载）；
    //  - testImplementation：JVM 单测可直接引用引擎类；
    //  - runtimeImplementation：runtime 变体（引擎 dex 来源）携带引擎类供 R8 keep。
    compileOnly(project(":androlua"))
    testImplementation(project(":androlua"))
    "runtimeImplementation"(project(":androlua"))

    // IDE 依赖树（compose / material / navigation / cascade / reorderable / sora / apksig）
    // 2026-08-08 引擎 dex 纯净化（模拟器实测 NoSuchMethodError 根因）：
    //  - compileOnly：三个变体编译期均需符号（runtime 变体也编译 main 源集）；
    //  - debugImplementation / releaseImplementation：宿主 IDE 变体携带完整运行时；
    //  - runtime 变体零 IDE 运行时（不携带实现类 + 不进 R8 规则输入），引擎 dex
    //    不再混入 androidx.compose 等 IDE 依赖树，宿主 debug/release 与引擎 R8
    //    产物在同一 classloader 内不得出现同名类（AndroidComposeView 等）。
    // 后续新增 IDE 依赖必须沿用本形态，禁止裸 implementation（否则 P0 断言
    // verifyRuntimeSurface/verifyMinimalism 的 androidx 前缀检查直接失败）。
    compileOnly(platform("androidx.compose:compose-bom:2025.06.00"))
    debugImplementation(platform("androidx.compose:compose-bom:2025.06.00"))
    releaseImplementation(platform("androidx.compose:compose-bom:2025.06.00"))
    // 引擎 dex 纯净化的解析侧副作用（2026-08-08）：runtime 变体的 compose 依赖为
    // compileOnly（runtimeClasspath 无 compose 链），consistent resolution 下
    // org.jetbrains:annotations 回退到 kotlin-stdlib 的 13.0，与 compileClasspath
    // compose 链要求的 23.0.0 冲突。统一钉到 compose 链实际版本（向后兼容 13.0）。
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
            "com.google.android.material:material:1.13.0",
            // 编辑器（并入自原 :editor 模块，2026-08-08 源码组织五需求 Phase 1）：
            // Tab 拖拽排序（WP-003，Squircle 同款固定版本；POM 实证与 Compose 1.7.3 二进制兼容）。
            "sh.calvin.reorderable:reorderable:3.0.0",
            // 多级「更多」菜单（一级分类 → 二级级联子菜单；M3 官方无嵌套菜单，成熟第三方）。
            "me.saket.cascade:cascade-compose:2.3.0",
            // Android Baseline Profile：src/main/baseline-prof.txt 经 R8 编译进 APK。
            "androidx.profileinstaller:profileinstaller:1.4.1",
            // APK v1/v2/v3 签名官方实现（Google，AGP 同源）。纯 Java，minSdk 24 可用。
            "com.android.tools.build:apksig:8.13.2",
        )
    for (d in ideDeps) {
        compileOnly(d)
        debugImplementation(d)
        releaseImplementation(d)
    }
    // ui-tooling-preview 为 IDE 开发工具库（@Preview 渲染 + PreviewActivity manifest 组件）：
    // 仅 debug 变体需要（Android 官方模板同款 debugImplementation），
    // release/runtime 变体不携带——runtime 基座因此不残留 tooling 规则与组件。
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
    // sora-editor（0.24.6 vendored 源码）：编辑器核心 + TextMate 高亮，同样只进宿主变体。
    compileOnly(project(":sora-editor:editor"))
    debugImplementation(project(":sora-editor:editor"))
    releaseImplementation(project(":sora-editor:editor"))
    compileOnly(project(":sora-editor:language-textmate"))
    debugImplementation(project(":sora-editor:language-textmate"))
    releaseImplementation(project(":sora-editor:language-textmate"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

// Lua 可见 API 面回归门禁（P3-4 三处同步 + P0 引擎 dex 单份化 2026-08-08 修正）：
// **反向断言**：宿主 release 变体不保留任何引擎类（:androlua 为 compileOnly，
// 引擎类唯一来源 = injectEngineDex 根目录引擎 dex；R8 保留面见 src/runtime/
// runtime-rules.pro），mapping 中出现任一引擎面类 = 双份引擎代码回归，即失败。
// 引擎面实际保留由 verifyRuntimeSurface（runtime 变体 mapping）与
// verifyMinimalism 跨 dex 断言（APK 级）双重把关。
//
// 维护约定（P3-4，三处同步）：新增 pro 自定义控件 / 引擎表面类时，需同时登记于
//   1. 本列表（R8 保留门禁校验对象）
//   2. app/src/runtime/runtime-rules.pro 的 -keep 规则（R8 实际保留手段）
//   3. ApiCategoryClassifier.androluaAndroidPrefixes（API 浏览器分类）
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
    description = "反向校验 release 构建未保留任何引擎面类（引擎类唯一在根目录引擎 dex，P0 契约）"
    dependsOn("assembleArm64Release")
    doLast {
        val mapping = file("build/outputs/mapping/arm64Release/mapping.txt")
        check(mapping.exists()) { "未找到 mapping 文件：$mapping" }
        val content = mapping.readText()
        val leaked = luaApiSurface.filter { "$it -> " in content }
        check(leaked.isEmpty()) {
            "引擎面类泄漏进宿主 release dex（R8 未剥离 → 双份引擎代码回归）：\n" +
                leaked.joinToString("\n") { "  - $it" }
        }
        println("宿主 release 引擎剥离校验通过：${luaApiSurface.size} 个引擎面类均未进宿主 dex")
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
        // IDE 依赖树代表。EngineActivity（引擎页，2026-08-08 归位 app 主源）同样
        // 必须被剥离：引擎页唯一一份在 IDE dex（打包产物经 P0-7 直用宿主根 dex），
        // 引擎 dex 不得携带任何 LIDE 宿主类（源码组织五需求，防跨 dex 重复）。
        val ideClasses =
            listOf(
                "com.lioisme.lide.MainActivity",
                "com.lioisme.lide.LideApp",
                "com.lioisme.lide.ui.theme.SettingsManager",
                "com.lioisme.lide.engine.EngineActivity",
                "com.lioisme.lide.engine.EngineContract",
            )
        val kept = ideClasses.filter { it in content }
        check(kept.isEmpty()) {
            "runtime 变体残留 IDE 类（未被 R8 剥离）：\n" + kept.joinToString("\n") { "  - $it" }
        }
        // 包级前缀断言（源码组织审计 Phase 2，2026-08-08）：引擎 dex 不得含任何
        // com.lioisme.lide.engine.* 类（引擎页唯一在 IDE dex，防跨 dex 重复）；
        // 同理 com.lioisme.lide.ui / build / project 等 IDE 业务包零残留。
        val lidePrefixes =
            listOf(
                "com.lioisme.lide.engine.",
                "com.lioisme.lide.ui.",
                "com.lioisme.lide.build.",
                "com.lioisme.lide.project.",
            )
        val leakedByPrefix =
            content
                .lines()
                .filter { it.contains(" -> ") }
                .map { it.substringBefore(" -> ") }
                .filter { orig -> lidePrefixes.any { orig.startsWith(it) } }
        check(leakedByPrefix.isEmpty()) {
            "runtime 变体残留 LIDE 宿主类（包前缀剥离断言，源码组织审计）：\n" +
                leakedByPrefix.joinToString("\n") { "  - $it" }
        }
        // IDE 依赖树前缀断言（2026-08-08 模拟器实测 NoSuchMethodError 根因修复）：
        // runtime 变体（= 引擎 dex 来源）不得携带任何 androidx.compose / appcompat /
        // material / navigation / activity-compose 等 IDE 依赖树类（R8 mapping 中
        // 保留即失败）。这些类若进入引擎 dex，与宿主 debug/release dex 同名类在同一
        // classloader 内冲突（如 AndroidComposeView 构造器签名不一致 → 崩溃）。
        // 宿主 IDE 依赖已改为 compileOnly + debugImplementation + releaseImplementation
        // 三形态，runtime 变体零 IDE 运行时；此处为回归防线。
        val ideDepPrefixes =
            listOf(
                "androidx.compose.",
                "androidx.activity.",
                "androidx.navigation.",
                "androidx.appcompat.",
                "com.google.android.material.",
                "sh.calvin.reorderable.",
                "me.saket.cascade.",
                "androidx.profileinstaller.",
                "com.android.tools.build.apksig.",
                "io.github.rosemoe.",
            )
        val ideLeaked =
            content
                .lines()
                .filter { it.contains(" -> ") }
                .map { it.substringBefore(" -> ") }
                .filter { orig -> ideDepPrefixes.any { orig.startsWith(it) } }
        check(ideLeaked.isEmpty()) {
            "runtime 变体残留 IDE 依赖树类（引擎 dex 纯净断言，2026-08-08）：\n" +
                ideLeaked.joinToString("\n") { "  - $it" }
        }
        println("runtime 变体验证通过：luaApiSurface ${luaApiSurface.size} 个类保留，IDE 类/依赖树已剥离（含 engine 包前缀）")
    }
}

// ── 跨 dex 类重复断言（2026-08-08 源码组织五需求 R-5 加固）────────────────────
// 最小 DEX class_defs 解析：不依赖任何 dex 解析库，仅读 string pool + class_defs
// 表（类名均为 ASCII MUTF-8）。跳过无 "dex\n" 魔数的条目（D8 元数据数据段，
// 内含 checksum JSON/SMAP 字符串，非真实 dex，不得参与判定）。
// 返回 Map<根 dex 条目名, class 描述符列表>。
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

/** 引擎侧类描述符（跨 dex 唯一性判定对象）：luaApiSurface + LuaApplication + FileProvider。 */
val engineSurfaceDescriptors: Set<String> =
    buildSet {
        addAll(luaApiSurface.map { "L${it.replace('.', '/')};" })
        add("Lcom/androlua/LuaApplication;")
        add("Landroid/content/FileProvider;")
    }

/**
 * 跨 dex 类重复断言（R-5 加固，2026-08-08）：
 * 1. 引擎面类描述符在全部根 dex 中恰好出现一次（引擎 dex 单份 + IDE dex 零引擎类）；
 * 2. 引擎 dex（标记所指）必须含引擎核心（LuaActivity/LuaApplication）且不含任何
 *    Lcom/lioisme/ 类（LIDE 宿主类唯一在 IDE dex，防跨 dex 重复/错位）；
 * 3. 其余根 dex（IDE 侧）不得含引擎面类。
 * 返回引擎 dex 条目名（无引擎 dex 时返回 null）。
 */
fun checkDexDuplication(apkFile: java.io.File): String? {
    val entries =
        ZipFile(apkFile).use { zf ->
            val enum = zf.entries()
            buildList {
                while (enum.hasMoreElements()) add(enum.nextElement().name)
            }
        }
    val marker = "assets/engine.dex.idx"
    var engineDexName: String? = null
    if (engineDexEnabled.get()) {
        val markerContent =
            ZipFile(apkFile).use { zf ->
                val e = zf.getEntry(marker) ?: return@use null
                zf
                    .getInputStream(e)
                    .readBytes()
                    .toString(Charsets.UTF_8)
                    .trim()
            }
        if (markerContent != null && markerContent.matches(Regex("classes\\d*\\.dex"))) {
            engineDexName = markerContent
        }
    }
    val dexFiles = entries.filter { it.matches(Regex("classes\\d*\\.dex")) }
    val classByDex = LinkedHashMap<String, List<String>>()
    ZipFile(apkFile).use { zf ->
        for (name in dexFiles) {
            val bytes = zf.getInputStream(zf.getEntry(name)).readBytes()
            classByDex[name] = scanDexClassNames(bytes)
        }
    }
    val occurrence = HashMap<String, MutableList<String>>()
    for ((dexName, classes) in classByDex) {
        for (c in classes) {
            occurrence.getOrPut(c) { ArrayList() }.add(dexName)
        }
    }
    val dupes =
        engineSurfaceDescriptors
            .mapNotNull { d -> occurrence[d]?.let { d to it } }
            .filter { (_, dexes) -> dexes.size != 1 }
    check(dupes.isEmpty()) {
        "跨 dex 类重复校验失败（R-5，每个引擎类必须全 APK 恰好一份）：\n" +
            dupes.joinToString("\n") { (d, dexes) -> "  $d -> $dexes" }
    }
    val missing =
        engineSurfaceDescriptors.filter { it !in occurrence }
    check(missing.isEmpty()) {
        "跨 dex 类缺失校验失败：引擎面类在任何 dex 均不存在：\n" + missing.joinToString("\n")
    }
    if (engineDexName != null) {
        val engineClasses = classByDex[engineDexName].orEmpty()
        check("Lcom/androlua/LuaActivity;" in engineClasses && "Lcom/androlua/LuaApplication;" in engineClasses) {
            "跨 dex 类校验失败：引擎 dex $engineDexName 缺少 LuaActivity/LuaApplication（引擎核心未注入）"
        }
        val lideInEngine = engineClasses.filter { it.startsWith("Lcom/lioisme/") }
        check(lideInEngine.isEmpty()) {
            "跨 dex 类校验失败：引擎 dex $engineDexName 携带 LIDE 宿主类（应唯一在 IDE dex）：\n" +
                lideInEngine.joinToString("\n") { "  - $it" }
        }
        // IDE 依赖树纯净断言（2026-08-08 模拟器实测 NoSuchMethodError 根因）：
        // 引擎 dex 不得含 androidx/compose、appcompat、material、sora 等 IDE 依赖树类
        // （这些类随宿主 IDE 变体进 IDE dex，引擎侧同名类 → 合并 classloader 冲突）。
        // 引擎自有 android.content.FileProvider 副本允许（engineSurfaceDescriptors 白名单）。
        val ideTreeInEngine =
            engineClasses.filter { c ->
                c.startsWith("Landroidx/compose/") ||
                    c.startsWith("Landroidx/appcompat/") ||
                    c.startsWith("Landroidx/activity/") ||
                    c.startsWith("Landroidx/navigation/") ||
                    c.startsWith("Lcom/google/android/material/") ||
                    c.startsWith("Lio/github/rosemoe/") ||
                    c.startsWith("Lsh/calvin/reorderable/") ||
                    c.startsWith("Lme/saket/cascade/") ||
                    c.startsWith("Landroidx/profileinstaller/")
            }
        check(ideTreeInEngine.isEmpty()) {
            "跨 dex 类校验失败：引擎 dex $engineDexName 携带 IDE 依赖树类（同名类与宿主冲突风险）：\n" +
                ideTreeInEngine.joinToString("\n") { "  - $it" }
        }
    }
    val ideDexes = classByDex.keys - engineDexName
    val engineInIde = ArrayList<String>()
    // 引擎类前缀全集（源码组织审计 Phase 3，2026-08-08）：IDE dex 不得含
    // com/androlua、com/luajava、com/nirenr、com/android/cglib、android/widget
    // 前缀类（:androlua 为 compileOnly，引擎类唯一在根目录引擎 dex）。
    val enginePrefixes =
        listOf(
            "Lcom/androlua/",
            "Lcom/luajava/",
            "Lcom/nirenr/",
            "Lcom/android/cglib/",
            "Landroid/widget/",
            "Landroid/app/AlertDialogBuilder;",
            "Landroid/app/FloatWindow;",
            "Landroid/content/FileProvider;",
        )
    for (dexName in ideDexes) {
        val classes = classByDex[dexName].orEmpty()
        for (d in engineSurfaceDescriptors) {
            if (d in classes) engineInIde += "$dexName : $d"
        }
        for (prefix in enginePrefixes) {
            for (c in classes) {
                if (c.startsWith(prefix)) engineInIde += "$dexName : $c"
            }
        }
    }
    check(engineInIde.isEmpty()) {
        "跨 dex 类校验失败：IDE dex 携带引擎类（:androlua 应为 compileOnly，不进 IDE dex）：\n" +
            engineInIde.distinct().joinToString("\n")
    }
    println("跨 dex 类重复校验通过：引擎面 ${engineSurfaceDescriptors.size} 类全部唯一（引擎 dex=${engineDexName ?: "无"}）")
    return engineDexName
}

// 精简治理门禁（2026-08-07，见 docs/planned/MINIMALISM_AUDIT_REPORT.md 实施项 T4）：
// 对 release APK 做载荷体检，防止「重复 dex / 原生库 / 资源」回归：
//  1. jniLibs 源目录禁令：app/src/main/jniLibs 被重建即拒（引擎仅允许 cpp 源码构建，R5）；
//  2. lib/<abi>/ 白名单：仅允许引擎 18 模块 + androidx 图形库；未知 .so = 预编译回包即失败；
//  3. 同 ABI 同名 .so 唯一性：防「ndk 引擎 + jniLibs 预编译」双引擎并包；
//  4. ABI 允许集：arm64-v8a / x86_64（x86、armeabi-v7a 为未来扩展位）；
//  5. 默认零内置示例：lide.examples=none 时 APK 不得含 assets/examples/*.alp；
//  6. 引擎 dex 单份化契约（2026-08-08，用户红线）：dex 一律在 APK 根目录；
//     旧 assets/runtime/classes.dex 内嵌形态出现即失败；默认开启时根目录必须有
//     assets/engine.dex.idx 标记且标记所指根 dex 条目存在。
// 载荷体积摘要（软日志）。
tasks.register("verifyMinimalism") {
    group = "verification"
    description = "校验 APK 精简：无 jniLibs 预编译回包、无重复原生库、默认零内置示例"
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

        // 6) 引擎 dex 单份化契约（2026-08-08）：dex 一律在 APK 根目录，禁止多份/他处。
        check(entries.none { it.first == "assets/runtime/classes.dex" }) {
            "精简校验失败：旧 assets/runtime/classes.dex 内嵌形态已废弃（dex 必须位于 APK 根目录，用户红线）"
        }
        val engineDexMarker = "assets/engine.dex.idx"
        if (engineDexEnabled.get()) {
            val markerEntry = entries.firstOrNull { it.first == engineDexMarker }
            check(markerEntry != null) {
                "精简校验失败：默认开启引擎 dex（P0 契约），但 APK 缺标记 $engineDexMarker"
            }
            val engineDexName =
                ZipFile(apkFile).use { zf ->
                    zf
                        .getInputStream(zf.getEntry(engineDexMarker))!!
                        .readBytes()
                        .toString(Charsets.UTF_8)
                        .trim()
                }
            check(engineDexName.matches(Regex("classes\\d*\\.dex"))) {
                "精简校验失败：引擎 dex 标记内容非法（应为根目录 classesN.dex）：$engineDexName"
            }
            check(entries.any { it.first == engineDexName }) {
                "精简校验失败：引擎 dex 标记指向 $engineDexName 但根目录无该条目"
            }
            val engineEntry = entries.first { it.first == engineDexName }
            println("引擎 dex 单份校验通过：根目录 $engineDexName（${engineEntry.second / 1024} KB）")
        } else {
            val markerEntry = entries.firstOrNull { it.first == engineDexMarker }
            if (markerEntry != null) {
                println("WARN：lide.embedRuntimeDex=false 但 APK 仍含引擎 dex 标记（注入未剔除干净）")
            }
        }

        // 7) 跨 dex 类重复断言（R-5 加固，2026-08-08）：引擎面类全 APK 恰好一份 +
        //    引擎 dex 无 LIDE 宿主类 + IDE dex 无引擎类。
        checkDexDuplication(apkFile)

        // 载荷摘要（软告警 + CI 直读日志）
        val libBytes = entries.filter { it.first.startsWith("lib/") && it.first.endsWith(".so") }.sumOf { it.second }
        val top = entries.sortedByDescending { it.second }.take(8)
        println("=== verifyMinimalism 载荷摘要（${apkFile.name}）===")
        println("条目数 ${entries.size}，原生库载荷 ${libBytes / 1024} KB，内置示例 ${exampleAlps.size} 个")
        top.forEach { (name, size) -> println("  %8d KB  $name".format(size / 1024)) }
        println("精简校验通过：无预编译 .so 回包、无重复原生库、默认零内置示例")
    }
}

// debug 变体的跨 dex 重复门禁（2026-08-08）：开发期快速验证引擎 dex 单份契约
// （release 的 verifyMinimalism 已含同断言；debug 无 R8 混淆、结构更接近直出）。
tasks.register("verifyMinimalismDebug") {
    group = "verification"
    description = "校验 debug APK 跨 dex 类重复（R-5）：引擎面类唯一 + 引擎 dex 无 LIDE 宿主类"
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
        checkDexDuplication(apkFile)
    }
}

// vendor 隔离门禁（源码组织审计 Phase 2，2026-08-08）：
// 上游目录（androlua / sora-editor）零 LIDE 代码入驻（R-2），且改动台账一致（R-3 增量侧闭环）：
//  1. third_party/androlua/src 与 third_party/sora-editor/src 不存在 com/lioisme/**；
//  2. third_party/androlua/UPSTREAM.md 存在（改动台账，缺失 = 上游漂移即失败）；
//  3. 台账登记项与源码实证一致：W^X 修复命中（InMemoryDexClassLoader）、androidx.annotation
//     零命中（annotation 剥离）、:androlua 构建脚本零第三方依赖（仅 android 库插件）；
//  4. 第三方模块构建脚本零仓库外部 URL 依赖（sora vendored 构建自足，R-3 缺失侧不回退）。
tasks.register("verifyVendorIsolation") {
    group = "verification"
    description = "校验上游目录隔离（R-2）：third_party/*/src 无 com/lioisme/** + UPSTREAM 台账一致"
    doLast {
        val upstreamDoc = rootProject.file("third_party/androlua/UPSTREAM.md")
        check(upstreamDoc.exists()) {
            "vendor 隔离失败：third_party/androlua/UPSTREAM.md 不存在（改动台账缺失，R-3 增量侧未闭环）"
        }

        val vendorSrcDirs =
            listOf(
                rootProject.file("third_party/androlua/src"),
                rootProject.file("third_party/sora-editor/editor/src"),
                rootProject.file("third_party/sora-editor/language-textmate/src"),
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
        val dexLoader = rootProject.file("third_party/androlua/src/main/java/com/androlua/LuaDexLoader.java")
        check(dexLoader.exists()) { "vendor 隔离失败：LuaDexLoader.java 缺失（上游结构漂移）" }
        check("InMemoryDexClassLoader" in dexLoader.readText()) {
            "vendor 隔离失败：LuaDexLoader 缺 W^X 修复（InMemoryDexClassLoader，台账 3.1 不一致）"
        }
        val annotationHits =
            rootProject
                .file("third_party/androlua/src")
                .walkTopDown()
                .filter { it.isFile && it.extension == "java" }
                .count { f -> "androidx.annotation" in f.readText() }
        check(annotationHits == 0) {
            "vendor 隔离失败：:androlua 源码含 androidx.annotation（台账 3.2 annotation 剥离未闭环）：$annotationHits 文件"
        }
        val androluaBuild = rootProject.file("third_party/androlua/build.gradle.kts").readText()
        check("com.android.library" in androluaBuild) {
            "vendor 隔离失败：:androlua 构建脚本非 android 库形态（台账 3.2 不一致）"
        }
        check(Regex("dependencies\\s*\\{").containsMatchIn(androluaBuild)) {
            "vendor 隔离失败：:androlua 构建脚本应有 dependencies 块（零第三方依赖，台账 3.2）"
        }

        // sora vendored 构建自足（白名单恢复机制登记于 update-sora-editor.ps1，此处防外部 URL 回退）
        for (p in listOf(
            "third_party/sora-editor/editor/build.gradle.kts",
            "third_party/sora-editor/language-textmate/build.gradle.kts",
        )) {
            val f = rootProject.file(p)
            check(f.exists()) { "vendor 隔离失败：$p 缺失（sora 结构漂移）" }
            check(!Regex("https?://").containsMatchIn(f.readText())) {
                "vendor 隔离失败：$p 含仓库外部 URL 依赖（vendored 构建应自足）"
            }
        }
        println("vendor 隔离校验通过：上游目录零 com/lioisme/**，UPSTREAM.md 台账与源码一致")
    }
}
