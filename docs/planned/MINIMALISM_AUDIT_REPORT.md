# LIDE 精简治理审计与去重整改报告（MINIMALISM_AUDIT_REPORT）

> 日期：2026-08-07
> 审计对象：`app/src/main/cpp/`（19 个 ndk-build 模块）、`:app` / `:editor` / `:androlua` /
> `:sora-editor` 各模块 `build.gradle.kts`、模块 assets/res/jniLibs 分布、APK 装配面
> （`ApkBuilder`）与工作树二进制资产。
> 触发需求：**LIDE 本身就要尽量精简，不要有重复的 dex、原生库、资源等依赖。**
> 结论性质：审计已毕 + **整改实施完成（2026-08-07）**，见 §5。

---

## 0. 结论先行

1. **重复性风险在历史上已被系统性治理**：无 jniLibs 预编译目录、无 Maven 坐标与
   vendored 源码双份并存、单一 Lua 解释器、资源/资产无跨模块碰撞。
2. 审计发现 3 类可整改项：**P1** 引擎 dex 模板受控冗余（`assets/runtime/classes.dex`，
   2.6 MB）；**P2** 内置示例胖资产（`examples/live2d.alp`，8.95 MB 恒随包）；**P3**
   死声明/杂物（`:app` `fileTree("libs")` 空声明、根 `classes.jar` 残留、androlua
   `compileSdk=35`）。
3. 已按「依赖 → 示例 → 门控 → 门禁 → 文档」顺序完成整改，见 §4。

---

## 1. 合规结论与取证（已核实）

| 维度 | 状态 | 取证 |
|---|---|---|
| 二进制零入库 | ✅ | `git ls-files '*.so'/'*.dex'/'*.apk'/'*.zip'` 零命中；`.jar` 仅 `gradle/wrapper/gradle-wrapper.jar`（R5 引导例外） |
| 原生引擎纯源码 | ✅ | 引擎 19 模块全部位于 `app/src/main/cpp/`，AGP `externalNativeBuild`（ndk-build）规 assemble 时即时构建；全仓无 `jniLibs` 源目录 |
| **单一 Lua 运行时** | ✅ | `lua/Android.mk` 为 `BUILD_STATIC_LIBRARY`，仅 `libluajava.so` 静态链接；其余各模块 `LOCAL_SHARED_LIBRARIES := luajava`（`app/src/main/cpp/*/Android.mk`），无第二份解释器 |
| sora-editor 单一来源 | ✅ | 仅 `:sora-editor:editor` / `:sora-editor:language-textmate` project 模块（第三方源码收纳）；无 Maven `io.github.rosemoe:*` 坐标残留（历史已修） |
| textmate 无原生 onig | ✅ | language-textmate 走 Joni 纯 Java（`OnigNative` 桩，`NativeOnigConfig.isAvailable()` 返回 false），不引 `.so` |
| 资源/资产单份 | ✅ | lua/javaapi/layouts/keys 仅 `:app` assets；androlua 模块零 res/零 assets；无跨模块同名资源（AAPT2 合并保障） |
| 依赖版本对齐 | ✅ | `:app` 与 `:editor` 同 BOM 2025.06.00；`material:1.13.0` 非死依赖（API 浏览器反射面） |
| 打包产物按需 | ✅ | `NativeDependencyAnalyzer`（传递闭包）+ `ApkBuilder` ABI×库二维过滤已落地（`NATIVE_LIB_PACKAGING_AUDIT_REPORT.md` T1~T5） |

---

## 2. 发现的问题

### P1　引擎 dex 冗余（`assets/runtime/classes.dex`，**2026-08-08 已根治**）
`embedRuntimeDex` 将 universal runtime 变体 `classes.dex`（剥离 IDE 后的引擎面，
**2.6 MB**）写入 `src/main/assets/`，**恒随每个常规 APK 打包**；而同一引擎类同时也
存在于宿主自身 `classes*.dex`。属 P0 打包契约（dex 替换模板）下的**受控冗余**，
但直接与「精简」字面冲突。
**根治（2026-08-08，用户红线修订）**：不再有任何冗余副本——宿主对 `:androlua` 改
`compileOnly`（引擎类不进宿主 classes*.dex），引擎 dex 由 `engineDexExtract` +
`injectEngineDex<Variant>` 作为**根目录唯一** `classesN.dex` 注入宿主 APK
（`assets/engine.dex.idx` 标记契约），旧 `assets/runtime` 形态出现即被
`verifyMinimalism` 拒绝（见 P0-6 / AGENTS.md 运行时基座契约）。

**补充修复（2026-08-08，P0-6 全链验证缺陷）**：release 的 `minify<vn>WithR8` 任务
**执行**时输出快照在任务完成时刻定格（R8 声明输出仅 classes.dex）——引擎 dex 仅由
独立 `injectEngineDex<Variant>` 任务事后写入目录时不在快照内，package 漏包，
`verifyMinimalism`「标记指向但根目录无该条目」失败。已重构 `app/build.gradle.kts`：
注入实体拆为 `writeEngineDex()`（仅写 dex，幂等）与 `injectBody()`（dex + 双处标记），
release 侧经 `tasks.configureEach`（onVariants 回调期 minify 任务未注册，禁止
`tasks.named` 提前解析）把 `writeEngineDex` 挂到 minify doLast——任务完成时快照即含
引擎 dex；inject 任务保留幂等落位兜底（minify up-to-date 时补写）。
**验收证据（2026-08-08）**：`:app:assembleUniversalRuntime` → `:app:verifyMinimalism`
BUILD SUCCESSFUL；`app-arm64-release.apk` 根目录 = `classes.dex`(5.4MB, IDE) +
`classes2.dex`(2,608,288B, 引擎，与 `engineDexExtract` 一致) +
`assets/engine.dex.idx`="classes2.dex"，引擎类单份；debug 变体
`injectEngineDexUniversalDebug` 同样成功（`classes17.dex` + 标记）。

### P2　内置示例胖资产（`assets/examples/live2d.alp`）
`generateExamples` 默认把 `scripts/examples-src/` 全部工程生成 `.alp` 打包（7 个，
  共约 9.0 MB），其中 **live2d.alp 8.95 MB**（占全部 assets 12.1 MB 的 74%）。
用户决策：**当前去除所有内置 .alp 工程，后续仅内置 1-2 个代表性工程**。

### P3　死声明 / 杂物
- `app/build.gradle.kts` 的 `implementation(fileTree("libs") { include("*.jar") })`：
  `app/libs/` 仅 `.gitkeep`，空声明（:androlua 已清理，:app 漏清）。
- 仓库根 `classes.jar` 残留（已 gitignore，物理未删）。
- `third_party/androlua` `compileSdk = 35` 与其余模块 36 不一致。

---

## 3. 整改方案与决策

| # | 方案 | 用户决策 |
|---|---|---|
| T1 | runtime dex 门控：`-Plide.embedRuntimeDex=false` 剔除资产；默认 true | **加门控开关默认保留**（Recommended） |
| T2 | 示例门控：`-Plide.examples=all|名单`，默认零内置；UI 按存在过滤 | **当前去除所有内置 .alp**，后续内置 1-2 个代表性工程 |
| T3 | 死声明清理：删 fileTree / classes.jar / androlua compileSdk 对齐 | — |
| T4 | 去重门禁 `:app:verifyMinimalism` + 接入 CI | — |
| T5 | 审计报告 + KB/TASKS/AGENTS 登记 | — |

---

## 4. 实施记录（2026-08-07）

### T1 runtime dex 门控（已完成）
- `app/build.gradle.kts`：新增 `embedRuntimeEnabled`（`lide.embedRuntimeDex`，默认
  `true`）；`embedRuntimeDex` 任务挂 `inputs.property("embedRuntimeEnabled")`，
  `false` 时**删除旧资产并跳过生成**（幂等），打包器缺失资产时兜底宿主 dex（P0 契约）。
- 验收：`-Plide.embedRuntimeDex=false assembleArm64Debug` 产包无该资产；默认构建保持现状。
- **2026-08-08 修订**：`embedRuntimeDex`/`assets/runtime` 形态废弃（引擎 dex 单份化，
  见 §4 T1'）；门控属性名 `lide.embedRuntimeDex` 保留，语义改为「剔除根目录引擎 dex」。

### T1' 引擎 dex 单份化（2026-08-08，用户红线：dex 一律宿主 APK 根目录、禁他处、禁多份）
- `:app` 对 `:androlua` 依赖改 `compileOnly` + `testImplementation` +
  `runtimeImplementation`（宿主 classes*.dex 不含引擎类）。
- `embedRuntimeDex` → `engineDexExtract`（解出 `build/engine/engine.dex`）+ 
  `injectEngineDex<Variant>`（androidComponents 挂 debug/release：注入
  `mergeLibDex<Variant>/zz/classes.dex`，package 任务编号为根目录 `classesN.dex`；
  写非 dex 标记 `assets/engine.dex.idx`）。
- `ApkBuilder.ENGINE_DEX_MARKER` 契约（内容须匹配 `classes\d*\.dex` 防路径注入）替代
  `RUNTIME_DEX_ENTRY`；`verifyMinimalism` 断言：默认开启根目录必有标记+引擎条目、
  旧 `assets/runtime/classes.dex` 出现即失败。

### T2 示例生成门控（已完成）
- `app/build.gradle.kts`：`generateExamples` 读 `lide.examples`（默认 `none`=
  零内置；`all`=全量；逗号名单=指定工程），先清空旧 `.alp` 再按选择重打包，切换幂等。
- `ExamplesScreen.kt`：`availableExamples` 按 `assets.list("examples")` 存在过滤；
  零内置时展示空态 `examples_empty`（en+zh 新增），`refreshInstalled` 同步收敛。
- `.gitignore` 已有 `assets/examples/*.alp` 不入库。

### T3 死代码清理（已完成）
- 删除 `app/build.gradle.kts` 的 `implementation(fileTree("libs"))`；物理删除
  `app/libs/`、`third_party/androlua/libs/`（含 `.gitkeep`）与根 `classes.jar`；
  `third_party/androlua` `compileSdk 35 → 36`（全仓统一）。

### T4 去重门禁 `verifyMinimalism`（已完成）
- `app/build.gradle.kts`：注册 `:app:verifyMinimalism`（依赖 `assembleArm64Release`），
  硬断言：① `app/src/main/jniLibs` 被重建即拒；② `lib/<abi>/` 仅允许引擎 18 模块 +
  `libandroidx.graphics.path.so`；③ 同 ABI 同名 `.so` 唯一；④ ABI 白名单；⑤
  `lide.examples` 未开启（默认）时 `assets/examples/*.alp` 必须为 0。软告警：
  runtime dex 模板存在（受控冗余）+ 载荷摘要（条目数/原生库 KB/top8）。
- CI `.github/workflows/build.yml`：`verify-release` job 改为
  `./gradlew verifyReleaseLuaSurface verifyMinimalism`（共享一次 assemble）。

### T5 文档登记（已完成）
- 本报告落 `docs/planned/`；`docs/kb/README.md` §2.3、`docs/TASKS.md` 登记；
  `AGENTS.md` 新增「精简治理」节 + 构建验证命令 + R5 例外按 config 门控。

---

## 5. 验证

- 编译：`.\gradlew.bat :app:compileArm64DebugKotlin --console=plain`
- 完整构建：`.\gradlew.bat :app:assembleArm64Debug --console=plain`
- 精简门禁：`.\gradlew.bat :app:verifyMinimalism`
- 打包单测：`.\gradlew.bat :app:testArm64DebugUnitTest --tests "com.lioisme.lide.build.*"`
- R1 门禁：`rg -n "luajava|androlua|loadLibrary" editor/src` 零命中

> 完成记录（2026-08-07）：T1/T2/T3/T4/T5 全落地并实测：
> - `:app:compileArm64DebugKotlin` 绿（修正 `verifyMinimalism` 内 `java.util.zip.ZipFile` 未导入
>   与 lib 白名单含 `.so` 后缀 vs 正则去后缀不一致两处缺陷）；
> - `:app:verifyMinimalism` 绿（release APK lib/arm64-v8a 18 个引擎库 + androidx.graphics.path，
>   同 ABI 唯一、跨 ABI 集合一致、默认零示例；WARN 记录 runtime dex 受控冗余）；
> - `:app:testArm64DebugUnitTest --tests "com.lioisme.lide.build.*"` 绿；
> - `-Plide.embedRuntimeDex=false` 实测删除 `assets/runtime/classes.dex`（2,662,092 B），默认重建；
> - `-Plide.examples=hello,demo` 实测生成 hello.alp/demo.alp，默认 none 清空为零内置；
> - `assembleArm64Debug` 绿（含 ktlint + lint）；R1 门禁零命中；`git ls-files` 二进制零入库。