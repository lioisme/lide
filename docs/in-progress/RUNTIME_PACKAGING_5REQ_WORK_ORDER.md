# 打包运行时五需求工作单（运行时解耦 / 载荷上下限 / ABI 架构 / 输出选配 / 设置收敛）

| 元数据 | 内容 |
|--------|------|
| 文档版本 | 1.0 |
| 创建日期 | 2026-08-07 |
| 文档代号 | RUNTIME-PACKAGING-5REQ |
| 审计依据 | `docs/planned/NATIVE_LIB_PACKAGING_AUDIT_REPORT.md`（机制审计，T1~T5 已完成）、`docs/planned/PACKAGING_PAYLOAD_CLEANUP_WORK_ORDER.md`（载荷精化，已完成）、`docs/planned/BUILD_SIGNING_ENCRYPTION_WORK_ORDER.md`（打包三大能力，T1~T3/T5 已完成）、上游 AndroLua Pro v4.4.2（`reference/` 恢复配方见 `reference/README.md`） |
| 总目标 | 五需求全量交付：① androlua 运行环境与 LIDE 完全解耦隔离；② 打包载荷上限＝整个 androlua 运行环境、下限＝按需原生库/依赖；③ 支持多架构（arm64/x64/arm32/未来新架构）打包，前置条件是宿主含目标架构；④ 每次打包可选配产物输出方式；⑤ 打包高级选项全部通用化进设置页 |
| 硬性约束 | AGENTS.md R1~R5（`:editor` 纯文本级、仓库零二进制原生产物入库）、`luaApiSurface` 三处同步约定（app/build.gradle.kts / proguard-rules.pro / ApiCategoryClassifier）、打包/签名/加密链改动必跑 `com.lioisme.lide.build.*` 单测 |
| 文档状态 | 进行中（P0 已完成 + 2026-08-08 引擎 dex 单份化修订完成，待全链验证与设备端冒烟） |
| 完成登记 | 见 §8 完成记录 |

---

## 1. 背景与五需求定义（用户原话语义）

用户对打包运行时的五项要求：

1. **运行时解耦**：androlua 运行环境与 LIDE（IDE 自身）完全解耦隔离——打包产物不应携带 IDE 专属内容；
2. **载荷上下限**：打包产物载荷的上限＝「整个 androlua 运行环境」（pro 同款能力全集），下限＝「仅按需的原生库/依赖」；
3. **ABI 架构支持**：不要求强制支持 32 位，但架构上必须可扩展到 arm64、x64、arm32 以及未来可能出现的新架构——**宿主含哪些 ABI 的原生库，打包器就自动支持打包哪些架构的 APK**，且支持多架构组合构建，需要专业的构建流程；
4. **输出可选配**：每次打包可选择产物输出方式（默认自动安装，pro 兼容）；
5. **高级选项通用化**：打包高级选项全部统一到设置页（打包页只读默认值 + 临时选配）。

---

## 2. 审计结论（事实核对 + 差距定位）

### 2.1 现状摘要（五需求逐一判定）

| # | 需求 | 判定 | 依据 |
|---|------|------|------|
| 1 | 运行时解耦 | ❌ 未达成 | 产物 dex 为宿主整体合并 dex（IDE 类与引擎类共生）；`universal/debug` 实测 30 个 `classes*.dex`，含 Compose/sora/编辑器/API 浏览器类；`HostPolicy` 仅剥离 assets/res 级 IDE 内容，dex 级剥离属遗留大项 |
| 2 | 载荷上下限 | ⚠️ 部分达成 | 上限：Lua 模块召回 + 原生库二维过滤为 pro 超集（T2/T3 已完成）；下限：dex 底座仍为宿主整体（IDE + 引擎共存），「最小载荷」不成立 |
| 3 | ABI 架构 | ✅ 宿主驱动机制成立（32 位可选未含） | `ZipUtils.listAbis`（宿主 `lib/<abi>/` 目录动态枚举，无白名单，未来新 ABI 天然通用）+ `ApkBuilder.effectiveAbis = 选择 ∩ 宿主`（空交集抛 `BuildException`）+ 打包 UI ABI 复选组宿主驱动；当前宿主 flavor 仅 x86_64/arm64-v8a（NDK 29 已无 32 位工具链），设置页默认 ABI 硬编码仅 3 项 |
| 4 | 输出可选配 | ⚠️ 部分达成 | 产物固定写 `<luaExtDir>/apk/`，固定命名 `<pkg>-<ver>.apk`，打包成功恒自动安装（pro 兼容）；无「仅保存/分享」选项、无输出目录/命名选配 |
| 5 | 设置收敛 | ⚠️ 部分达成 | `BuildOpts` 5 项已持久化（stripNativeLibs/compileLua/encryptBytecode/signingProfileId/preferAbi，build 域偏好键）；但 `BuildApkScreen` 高级折叠卡内 strip/compile/encrypt 开关与设置页**重复**（双重 UI），ABI 默认选项硬编码缺 32 位项 |

### 2.2 上游基线事实（pro 4.4.2，判定依据）

- pro 宿主 APK 仅单 ABI（`armeabi-v7a`），「全架构」是**高于上游**的新要求；
- pro `bin.lua` `checklib` 依赖分析（递归 require/import 闭包，按需保留 `lib<name>.so`，`libluajava.so` 恒保留）——lide 已以 Kotlin 传递闭包平移且为超集（含缺失诊断 + 强制包含 + `.aly` 扫描）；
- pro 打包链 = 模板复制模型（正在运行的宿主 APK 为模板）+ v1 签名 + 自动安装。

### 2.3 关键决策（用户已确认，2026-08-07）

- **D-1（arm32/架构）**：32 位**非必须**；技术架构必须支持 arm64/x64/arm32/未来新架构——打包 ABI 集合由**宿主实际携带**驱动（宿主仅含 arm64 则只支持打包 arm64；宿主含 4 ABI 则自动支持 4 架构组合）。**NDK 不降级**（保持 `29.0.14206865`），32 位 ABI 支持以「flavor 加 abiFilters → 宿主携带 → 打包器自动可用」契约留作未来扩展路径（届时 NDK 27 为前置）。
- **D-2（ABI 归属）**：打包页 ABI 选配（每包临时选择）+ 设置页默认（持久化默认值）。
- **D-3（输出默认）**：打包成功后默认自动安装（pro 兼容）；设置页可改「仅保存/分享」。

---

## 3. 实施计划（分阶段，按用户确认顺序）

> 顺序：P1（ABI 架构固化）→ P2（输出选配）→ P3（设置收敛）→ P0（运行时基座）。P0 为最大工程放最后，且以 P1 的全 ABI 引擎底座为前提。

### 3.1 P1 — ABI 架构固化（需求 3 收尾）

| # | 任务 | 涉及文件 | 验收 |
|---|------|---------|------|
| P1-1 | 设置页默认 ABI 扩为 5 项（auto/arm64-v8a/x86_64/armeabi-v7a/x86） | `BuildOptionsScreen.kt` | 编译通过；5 项可选中持久化；preferAbi 不命中宿主时打包页回落全选（现有逻辑） |
| P1-2 | `scripts/build-apks.ps1` 变体矩阵参数化（ABI 组合可配置）+ 产物清单校验 | `scripts/build-apks.ps1` | 默认仍产出 6 变体；支持 `-Variants` 参数定制；签名校验保留 |
| P1-3 | AGENTS.md/WHITEPAPER 固化「宿主驱动 ABI 契约 + 未来架构扩展路径」 | `AGENTS.md`、`docs/WHITEPAPER.md` | 契约成文：加 abiFilters → 宿主携带 → 打包器零改动自动可用 |

### 3.2 P2 — 输出选配（需求 4，落地 D-3）

| # | 任务 | 涉及文件 | 验收 |
|---|------|---------|------|
| P2-1 | `BuildOpts` 增 output 域：`afterBuild`（INSTALL/SAVE 默认 INSTALL）、`outputDir`、`outputNaming` | `BuildOpts.kt`、`SettingsManager.kt`（build 域新偏好键） | 持久化读写；默认 INSTALL + 现有固定目录/命名语义 |
| P2-2 | `BuildApkScreen` 成功后按 `afterBuild` 分支：INSTALL=自动安装（pro 兼容）；SAVE=仅写盘 + 分享按钮 | `BuildApkScreen.kt` | 单测 + 编译通过；SAVE 分支不调 `installApk` |
| P2-3 | 设置页「产物输出」分区（afterBuild 单选 + 输出目录/命名说明） | `BuildOptionsScreen.kt` + strings（中英） | UI 呈现；持久化回读 |
| P2-4 | 单测：output 域持久化 + 分支逻辑 | `SettingsManagerTest`/build 包测试 | build.* 全绿 |

### 3.3 P3 — 设置收敛（需求 5，落地 D-2）

| # | 任务 | 涉及文件 | 验收 |
|---|------|---------|------|
| P3-1 | `BuildApkScreen` 高级折叠卡删除与设置页重复的 stripNativeLibs/compileLua/encryptBytecode 开关；打包页只读设置页默认值，保留 ABI 选配 + 签名入口 | `BuildApkScreen.kt` | 高级卡仅剩 ABI 多选 + 签名入口；默认值来自 `SettingsManager.buildOpts` |
| P3-2 | 设置页 ABI 分区 5 项（P1-1 共用）+ 文案同步 | `BuildOptionsScreen.kt` | 见 P1-1 |

### 3.4 P0 — 运行时基座（需求 1，最大工程，最后）

| # | 任务 | 涉及文件 | 验收 |
|---|------|---------|------|
| P0-1 | 新增 `runtime` buildType + `app/src/runtime/` sourceSet 骨架（manifest/Application 主类对齐） | `app/build.gradle.kts`、`app/src/runtime/` | `assembleUniversalRuntime` 产出仅引擎 + 宿主核心（无 IDE UI）的 APK |
| P0-2 | `runtime-rules.pro`：白名单 keep `luaApiSurface` + 引擎类，R8 剥离 IDE 类 | `app/src/runtime/runtime-rules.pro`（或按 buildType 注入） | release 变体 mapping 校验：IDE 类被剥离、`luaApiSurface` 全保留 |
| P0-3 | ~~`embedRuntimeDex` 任务~~（**已废弃**，见 P0-6）→ `engineDexExtract`：解出 runtime 变体产物 `classes.dex` 为 `build/engine/engine.dex`（构建产物，非资产） | `app/build.gradle.kts` | 见 P0-6 |
| P0-4 | `ApkBuilder` dex 替换模式：宿主 `classes*.dex` 全替换为引擎 dex + manifest 回落 `LuaApplication`/移除 IDE MainActivity（`ManifestEdit.applicationName`/`removeActivities`）；宿主无该资产兜底宿主 APK | `ApkBuilder.kt`、`AXmlManifest.kt` | 打包单测：dex 替换正确、宿主/兜底双路径 |
| P0-5 | 产物 dex 载荷验证：dex 替换模式产物仅唯一 `classes.dex`（引擎，无 IDE 类）且可运行 | 打包单测 + 实测产物 | 产物仅 `classes.dex`，不含 IDE 专属类（如 `ui/screen/` 包） |

---

## 4. 验收总则（每阶段通用）

1. 打包/签名/加密链单测：`.\gradlew.bat :app:testArm64DebugUnitTest --tests "com.lioisme.lide.build.*"` 全绿；
2. 完整构建：`.\gradlew.bat :app:assembleArm64Debug --console=plain` BUILD SUCCESSFUL；
3. R1 门禁：`rg -n "luajava|androlua|loadLibrary" editor/src` 零命中；
4. 二进制红线：`git ls-files '*.so' '*.dex' '*.zip'` 空；`*.jar` 仅 wrapper 例外；
5. ktlint：新增/修改文件零违规（仓库预存文件例外不触碰）。

## 5. 回归范围

- 打包器全链路：`ApkBuilderTest`（17 用例）+ `NativeDependencyAnalyzerTest`（9 用例）+ `LuaCryptoTest`（7 用例）+ 签名档案测试；
- 宿主构建矩阵：`scripts/build-apks.ps1`（6 变体）签名校验；
- 设备端：x86_64 模拟器安装宿主 + 打包产物冒烟（EngineActivity 直达）；
- 工程样例：`scripts/examples-src/` 打包回归（demo/DemoProject/kleinbottle/testjson）。

## 6. 风险与边界

| 项 | 说明 |
|---|------|
| 32 位架构 | 非必须（D-1）；架构契约已留扩展路径；如需启用 → NDK 27 降级（27.3.13750724）为前置，属另行决策 |
| 产物 dex 剥离 | P0 运行时基座为 R8 剥离路线；`luaApiSurface` 三处同步为硬约束，遗漏会被 `verifyReleaseLuaSurface` 门禁捕获 |
| 模板切源 | dex 替换模式：引擎 dex 资产缺失时兜底宿主 APK（pro 语义），避免构建顺序破坏运行期打包 |
| NDK/AGP | 保持 29.0.14206865 不动；P0 不改 C 源码，零原生构建风险 |
| pro 一致性 | `bin.lua` 保持原样只读资产；lide 以 Kotlin 实现为准 |

## 7. 参考文件

- 上游：`reference/README.md`（pro 恢复配方）、`bin.lua` checklib 语义（`docs/planned/NATIVE_LIB_PACKAGING_AUDIT_REPORT.md` §2）
- lide：`app/build.gradle.kts`（flavor/ABI/签名）、`app/src/main/kotlin/com/lioisme/lide/build/{ApkBuilder,BuildOpts,HostPolicy,ZipUtils,NativeDependencyAnalyzer}.kt`、`app/src/main/kotlin/com/lioisme/lide/ui/screen/{BuildApkScreen,BuildOptionsScreen}.kt`、`app/src/main/kotlin/com/lioisme/lide/ui/theme/SettingsManager.kt`、`scripts/build-apks.ps1`
- 知识库：`docs/kb/README.md` §2、`docs/kb/ENGINE_RUNTIME.md`、`docs/WHITEPAPER.md` §7.1、`AGENTS.md`（打包构建约定 / 文档治理约定）

## 8. 完成记录

| 阶段 | 项 | 证据 | 日期 |
|---|---|---|---|
| P1 | P1-1 设置页默认 ABI 5 项 | `BuildOptionsScreen.kt` ABI 分区扩为 auto/arm64-v8a/x86_64/armeabi-v7a/x86（D-1 注释固化「默认与宿主携带解耦，打包页回落全选」） | 2026-08-07 |
| P1 | P1-2 build-apks 参数化 | `scripts/build-apks.ps1` 增 `-Abis`/`-Types`/`-Variants` 参数（默认 6 变体不变；变体→路径映射校验） | 2026-08-07 |
| P1 | P1-3 ABI 契约成文 | `AGENTS.md` 新增「ABI 架构契约（D-1 冻结结论）」节；`WHITEPAPER.md` 阶段五补 ABI 契约条目 | 2026-08-07 |
| P2 | P2-1~P2-4 输出选配 | `BuildOpts.kt`（`AfterBuild` 枚举 + outputDir/outputNaming 域 + `parse` 纯函数）、`SettingsManager`（after_build/output_dir/output_naming 偏好键）、`BuildApkScreen`（afterBuild 分支 + `buildOutputName` 模板）、`BuildOptionsScreen` 产物输出分区、中英 strings；`BuildPackagingTest` 7 用例全绿（新增 4：命名模板/占位符/非法字符/AfterBuild.parse） | 2026-08-07 |
| P3 | P3-1/P3-2 设置收敛 | `BuildApkScreen` 高级卡删 strip/compile/encrypt 重复开关（仅留 ABI 选配 + 签名入口），只读设置页默认值；死字符串 `build_native_policy`/`build_compile_policy`/`build_encrypt_label` 删除 | 2026-08-07 |
| P0 | P0-1 runtime 变体 + sourceSet | `app/build.gradle.kts` 增 `runtime` buildType（R8 开启，`matchingFallbacks=release`）；`app/src/runtime/` 含 `AndroidManifest.xml`（application 换为 `com.androlua.LuaApplication`、`tools:node=remove` 剔除 MainActivity）+ `runtime-rules.pro`（白名单 keep 引擎/luaApiSurface，刻意不含 `com.lioisme.lide.**` 全 keep） | 2026-08-07 |
| P0 | P0-2 R8 剥离门禁 | `verifyRuntimeSurface` 任务：mapping 断言 luaApiSurface 26 类全保留 + MainActivity/LideApp/SettingsManager 剥离；实测 universalRuntime 单 dex 2.66MB（原宿主多 dex），compose 残留 26 类（AndroidX 库自带 ModifierNodeElement 反射 keep 规则，非 IDE 业务类） | 2026-08-07 |
| P0 | P0-3 base.apk 嵌入（**已否决**，改用 dex 资产） | `embedRuntimeBase` 任务（复制过滤 assets/runtime/ 防嵌套膨胀，挂 preBuild）；宿主 APK 实测含 `assets/runtime/base.apk`（classes.dex 与 universalRuntime 一致，无 IDE 类）。**用户否决内嵌 APK 设计**，整套废弃：宿主只携带单 dex 资产 `assets/runtime/classes.dex` | 2026-08-07 |
| P0 | P0-4 dex 替换模式（替代模板切源） | `embedRuntimeDex`（解出 classes.dex）+ `ApkBuilder.RUNTIME_DEX_ENTRY`（宿主 classes*.dex 全替换为引擎 dex，manifest 经 `ManifestEdit.applicationName`/`removeActivities` 回落 LuaApplication + 移除 MainActivity）；模板 = 宿主，ABI/权限仍以宿主为准 | 2026-08-07 |
| P0 | P0-5 产物 dex 载荷验证 | `ApkBuilderTest` 3 用例改造为 dex 替换模式（引擎 dex 替换宿主 classes*.dex/ABI 仍宿主驱动/无资产兜底宿主）；AXmlManifest 增 `applicationName` 覆盖 + `removeActivities` 整树移除；`ui-tooling-preview` 改 `debugImplementation` | 2026-08-07 |
| P0 | P0-6 **引擎 dex 单份化（2026-08-08 用户红线修订，废弃 P0-3/P0-4 资产形态）** | 用户红线：所有 dex 必须位于宿主 APK **根目录**、禁止其他目录、禁止任何重复副本。落地：`:app` 对 `:androlua` 依赖改 `compileOnly` + `testImplementation` + `runtimeImplementation`（引擎类不进宿主 classes*.dex）；`embedRuntimeDex`（assets/runtime 形态）替换为 `engineDexExtract`（解出 `build/engine/engine.dex`）+ `injectEngineDex<Variant>`（androidComponents 挂 debug/release，注入 `mergeLibDex<Variant>/zz/classes.dex` 并按 ext→lib→project 编号为根目录 `classesN.dex`，同时写非 dex 标记 `assets/engine.dex.idx`）；`ApkBuilder.ENGINE_DEX_MARKER`（内容须匹配 `classes\d*\.dex` 防路径注入）替代 `RUNTIME_DEX_ENTRY`；`verifyMinimalism` 增契约断言（默认开启根目录必有标记+引擎条目、旧 assets/runtime 形态出现即失败）；EngineActivity 动画去模块资源依赖（`EXTRA_ANIM_IN/OUT`，宿主 `R.anim` 经 EngineBridge 注入）；.gitignore 删除 assets/runtime 条目 | 2026-08-08 |
| P0 | P0-7 **打包 dex 直用宿主（2026-08-08 修订，废弃 P0-4 dex 替换模式）** | 用户要求：移除「专门给打包构建 APK 时提供 dex」的设计——可接受产物有多余内容，不接受本体多份引擎类、不接受魔改宿主构建流程。落地：`ApkBuilder` 删 dex 替换模式（`ENGINE_DEX_MARKER` 不再选 dex），产物直接复用宿主根目录**全量 classes*.dex**（IDE 类 + 引擎类原样保留）；标记仅作引擎存在性判定（manifest 回落 `LuaApplication` + 移除 IDE MainActivity 门禁，未命中保持宿主 manifest）；宿主构建流程（runtime buildType / engineDexExtract / injectEngineDex / verifyMinimalism）零改动；`ApkBuilderTest` 4 用例改写为全量保留语义。详见 `PACKAGING_DEX_SIMPLIFY_WORK_ORDER` | 2026-08-08 |

> **后续 UI 迁移（2026-08-07，设置简约化 `docs/planned/SETTINGS_SIMPLIFY_WORK_ORDER.md` T1~T4）**：
> 设置页「打包选项」独立页 `BuildOptionsScreen` 已删除，其全部打包选项（自动安装开关替代「产物输出」单选、
> 原生库 / 编译 / 加密开关、默认 ABI 5 项）迁入 AndroLua 合并页 `AndroLuaSettingsScreen`（「打包」分区）；
> 签名默认选择迁入独立 `SigningScreen`（主设置「通用」分区入口）。P1-1/P2-3/P3-2 中所指「设置页」即该合并页。
