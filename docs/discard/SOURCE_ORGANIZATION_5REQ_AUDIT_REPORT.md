# 源码组织与上游一致性 全量审计报告（五需求）

> 状态：✅ 全部完成（2026-08-08 立项，整改按 Phase 0→4 执行；**Phase 0~4 全部完成**，2026-08-09 收口归档）
> 目标：对照五条源码组织要求，列出全部违规并完成整改（自驱动全链）
> 验收标准：Phase 0~4 各自构建/门禁命令全绿（见 §6）；违规清单 V1~V5 全部闭环
> 关联代码路径：`app/`、`editor/`、`third_party/androlua/`、`third_party/sora-editor/`、`docs/`、`settings.gradle.kts`

---

## 0. 元信息

- **审计基线**：`HEAD 5f7fdde` + 工作区未提交 WIP（引擎 dex 单份化 / 结构归位改造进行中，含 20 个 staged 改动，处于不一致中间态）
- **上游参照**：AndroLua Pro v4.4.2（`nirenr/AndroLua_pro` master，tree sha `abe5c25148a60ee44ddcccc212db6b96e836c793`，逐文件比对结论沿用 `JAVA_LIBRARIES_AUDIT_REPORT.md` 2026-08-08）；sora-editor 0.24.6（vendored `third_party/sora-editor/`，VERSION 校核一致）
- **审计方法**：git tree 全量清单 + 工作区 diff 核对 + 关键文件精读（build 脚本 / 清单 / runtime 规则 / 引擎编译清单）+ 包级 grep 实证；只读审计，未改动文件
- **方法边界**：pro 4.4.2 原版基线已从仓库去重（恢复配方见 `reference/README.md`），差异判定以既有逐文件审计记录 + 当前工作区 diff 为准

## 1. 五条需求

| # | 需求 |
|---|---|
| R-1 | LIDE 所有源码集中在 `/app` 目录 |
| R-2 | 与 androlua、sora-editor 上游基线拆开解耦、严格隔离 |
| R-3 | androlua 运行环境与 pro 完全一致（无缺失 / 无多余增量） |
| R-4 | 非 androlua 部分和其他上游基线，要求全部使用 kotlin 实现 |
| R-5 | 构建时与运行时同一个 androlua java 库，编译产出的 dex 库不允许重复 |

## 2. 逐项判定与违规清单

> 最终状态：V1-1/1-2/1-3、V2-1/2-2/2-3、V3-1/3-2/3-3/3-4、V4-1/4-2、V5-1 全部闭环（Phase 0~4 完成记录见 §4）。

### R-1 集中 /app —— ✅（Phase 0/1 完成后）

| # | 违规 | 证据 | 级别 |
|---|---|---|---|
| V1-1 | LIDE 宿主引擎代码位于 `third_party/androlua/`（WIP 把 `EngineActivity.java` 迁入上游目录并新增 `EngineContract.java`，方向性错误） | `git ls-tree HEAD app/src/main/java`；`third_party/androlua/src/main/java/com/lioisme/**` | P0 |
| V1-2 | LIDE 编辑器源码在独立 `editor/` 模块未聚合到 `app/` | `settings.gradle.kts` `include(":editor")`；`editor/src/main/kotlin/` 18 文件 | P1 |
| V1-3 | 动画资源双份：`app/src/main/res/anim/{slide_in_left,slide_out_right}.xml`（untracked）+ `third_party/androlua/src/main/res/anim/` 同名 staged 件并存 | 见 §4 清理结论 | P1 |

### R-2 上游隔离 —— ✅（Phase 2 完成后：UPSTREAM.md + verifyVendorIsolation 门禁 + 包名收口）

| # | 违规 | 证据 | 级别 |
|---|---|---|---|
| V2-1 | 同 V1-1；包名链 `com.lioisme.lide.androlua.engine.*` 锁死 manifest / ApkBuilder / runtime-rules.pro / 注释共 6 处 | 见 §5 收口清单 | P0 |
| V2-2 | 上游改动台账缺失：`third_party/androlua/UPSTREAM.md` 不存在（既有审计 D-2 登记待补、搁置）；上游被直接改而非补丁隔离（W^X、annotation 剥离×4、FileProvider 白名单、lsqlite3 mk、CrashHandler 偏离） | `ARCHITECTURE_CLEANUP_AUDIT_REPORT.md` D-2 | P0 |
| V2-3 | sora-editor 接入点（vendored build.gradle.kts 为 LIDE 自写最小构建）未登记为唯一允许接入点 | `third_party/sora-editor/editor/build.gradle.kts` 首注释 | P2 |
| ✅ | `:editor` 对 luajava/androlua 零依赖（build.gradle.kts 实证）；sora 源码零 LIDE 污染（grep 实证） | — | — |

### R-3 与 pro 一致 —— ✅（缺失侧零、增量侧 4 类全部闭环并登记 UPSTREAM.md）

- ✅ 缺失侧：运行时 Java 面 100% 完整（330 文件 vs 上游 tree 逐文件比对，`JAVA_LIBRARIES_AUDIT_REPORT.md`）；删除项全部有据（textwarrior/IDE 壳/SopEditView/multidex/support 桩等）；`lluv`/`tcc` 不提供由 WHITEPAPER §7.1 明示
- ✅ 增量侧（全部登记 UPSTREAM.md 台账，见 §4 Phase 2）：

| # | 增量 | 判定 | 处理 |
|---|---|---|---|
| V3-1 | `com.lioisme.lide.androlua.engine` 2 文件（WIP 迁入 :androlua） | R-1/2/4 联合违规 | 移出（Phase 0） |
| V3-2 | 引擎原生扩展 `lide_des.c/h`（编入 `cpp/lua/Android.mk`）、`live2d/` | LIDE 功能增量，无台账登记 | UPSTREAM.md 登记 |
| V3-3 | 上游改写位置（annotation 剥离 ×4、W^X、FileProvider 白名单、lsqlite3 mk、CrashHandler） | 无 diff 基线，"vendor 只读"声明与事实不符 | UPSTREAM.md 台账 + 门禁 |
| V3-4 | 引擎 `res/anim` 挪移 + WIP 新增约 150 行装配逻辑 | 冗余/中间态 | 清理收编 |

### R-4 全 Kotlin —— ✅（宿主 100% Kotlin；原生 C 例外合规）

- ✅ 宿主 LIDE 100% Kotlin（`app` 源码树零 `.java`）；pro 的 Lua 界面已由 Compose 重写
- ✅ 边界判定（合规）：`:androlua` 上游"即 Java"（2021 停更锁死）；`lide_des.c`/`live2d` 属原生引擎 C 例外

### R-5 同一份库、无 dex 重复 —— ✅ 机制成立 + 三重断言加固（Phase 3 完成）

- ✅ 单源：`:app` 对 `:androlua` `compileOnly` + `testImplementation` + `runtimeImplementation`；引擎类唯一一份经 `engineDexExtract` → `injectEngineDex<Variant>` 注入根目录 `classesN.dex`；`verifyMinimalism` 断言根目录单份
- ✅ 构建时同步：`LuaCompiler.kt` 复用同一 `com.luajava`（`LuaStateFactory` + `LloadBuffer` + `dump`）
- ✅ 打包时：P0-7 已废弃 dex 替换模式，产物直接复用宿主根目录全量 `classes*.dex`（`PACKAGING_DEX_SIMPLIFY_WORK_ORDER.md`）
- ✅ V5-1 闭环：Phase 0 归位后「引擎类只进引擎 dex（runtime 变体 R8 剥离）+ IDE 类与引擎页只进 IDE dex」结构成立；2026-08-09 实证引擎 dex 纯净（775 类，IDE 树全 0）
- ✅ 加固 1：`checkDexDuplication` 四类跨 dex 断言（引擎面唯一 / 双向包前缀隔离 / ideTreeInEngine），scanDexClassNames type_ids 二次间接修复后生效
- ⚠️ 加固 2（登记）：`runtimeImplementation` 为 AGP 自定义配置，升级 AGP 需复查（台账登记）

## 2. 整改方案（Phase 0→4）

### Phase 0（P0）—— 修正 WIP 方向：LIDE 代码离开上游目录（R-1/2/4）

1. `EngineActivity.java` / `EngineContract.java` 反迁并 Kotlin 化 → `app/src/main/kotlin/com/lioisme/lide/engine/`（包名 `com.lioisme.lide.engine`，消除 V1-1/V4-1/V4-2）
   - 设计要点（R-5 保全）：P0-7 起打包产物直用宿主全量根 dex → **引擎页归入 IDE dex 即可满足打包产物运行**；删除 `-keep class com.lioisme.lide.androlua.engine.**` 后，runtime 变体 R8 按 manifest 可达性把引擎页剥离出引擎 dex → 全 APK 唯一一份（IDE dex），引擎 dex 不再携带任何 LIDE 宿主类
2. 全链收口（键字符串一致性由 `EngineContractTest` JVM 用例守护）：manifest 声明 / `ApkBuilder.DEFAULT_LAUNCHER_ACTIVITY` / `MainActivity` import / `EditorScreen` import / `EngineResultParser` KDoc / `AXmlManifest` 注释
3. 清理：`third_party/androlua/src/main/res/anim/` 2 文件 + `pains android.support` 空目录 + 引擎 res 残留
4. 验收：`:app:assembleUniversalRuntime` + `:app:assembleArm64Debug` + `verifyRuntimeSurface`（新增引擎页剥离断言）绿

### Phase 1 —— 编辑器并单（R-1 完成）

1. `git mv editor/src/main/kotlin/.../editor → app/src/main/kotlin/.../editor`；textmate 资产并入 `app/src/main/assets/textmate`；`editor/src/test`（2 测试）→ `app/src/test`；资源合并资源合并去冲突
2. 依赖并入 `app/build.gradle.kts`（BOM 已有；reorderable / cascade / sor)；`settings.gradle.kts` 移除 `:editor`
3. 门禁语义改写（AGENTS.md R1~R5 保留语义、更换载体）：模块边界 → 包边界（`com.lideous.lide.editor` 包禁令 + 禁跨包引用）
4. 验收：`compileArm64DebugKotlin` + `testArm64DebugUnitTest`（含迁入测试）全绿

### Phase 2（R-2/R-3 完整）

1. 新建 `third_party/androlua/UPSTREAM.md`：版本 / 上游 tree sha / 导入日期 / 逐项改动登记（改动清单 + 理由 + 日期）
2. 新增 `verifyVendorIsolation` 门禁任务（挂 CI）：`third_party/androlua/src`、`third_party/sora-editor/src` 下不存在 `com/lioisme/**`；UPSTREAM.md 存在且改动登记一致；`:androlua` 构建脚本零；第三方依赖
3. 验收：CI job 绿；`verifyRuntimeSurface` 扩展断言引擎 dex 必不含 `com.lideous.lide.engine.*`

### Phase 3（R-5 加固）

1. `verifyMinimalism` 扩展：解析各根 `classes*.dex` 字符串池，断言：
   - `luaApiSurface` 每类跨 dex 仅出现一份（无重复副本）
   - 引擎 dex 不含 `Lcom/lioisme/lide/engine/` 前缀类（引擎页唯一在 IDE dex）
   - IDE `classes*.dex`（引擎 dex 之外）不含 `Lcom/androlua/`、`Lcom/luajava/`、`Lcom/nirenr/`、`Lcom/android/cglib/`、`Landroid/widget/` 引擎类前缀
2. 打包链回归：`scripts/build-apks.ps1` 6 变体 + 签名逐包校验；`ApkBuilderTest` 全量（含无引擎兜底用例）
3. 全链：`testArm64DebugUnitTest --tests "com.lioisme.lide.build.*"` + 三项 verify + APK 根目录检查

### Phase 4（文档收口）

1. 本报告完成标记 + 归档 `docs/discard/`；同步 kb/README §2 / TASKS.md / discard/README §3
2. AGENTS.md 契约改写（源码组织五契约 + 门禁命令更新）

## 3. 验收命令（每阶段通用）

```
.\\gradlew.bat :app:compileArm64DebugKotlin --console=plain
.\\gradlew.bat :app:assembleArm64Debug --console=plain
.\\gradlew.bat :app:testArm64DebugUnitTest --console=plain
.\\gradlew.bat :app:verifyMinimalism --console=plain
.\\gradlew.bat :app:verifyRuntimeSurface --console=plain
.\\gradlew.bat :app:verifyReleaseLuaSurface --console=plain
rg -n "luajava|androlua|loadLibrary" editor/src
rg -n "com\\.lioisme" third_party/androlua/src third_party/sora-editor/src
git ls-files '*.so' '*.dex' '*.zip'
```

## 4. 完成记录

- **Phase 0**：（已完成 2026-08-08）
  - `EngineActivity.java` / `EngineContract.java` 反迁并 Kotlin 化 → `app/src/main/kotlin/com/lioisme/lide/engine/`（`EngineActivity.kt` / `EngineContract.kt`，包名 `com.lioisme.lide.engine`）；
  - `EngineBridge.kt` 自 `third_party/androlua` 迁入 `app/.../engine/`（RM）；`third_party/androlua` 内 `com/lioisme/**` 零残留（rg 实证）；
  - 全链收口：manifest `com.lioisme.lide.engine.EngineActivity`、`ApkBuilder.DEFAULT_LAUNCHER_ACTIVITY`、`MainActivity`/`EditorScreen` import、`RunEngineLauncher`/`EngineBridge` R.anim 引用一致（EngineContractTest JVM 用例守护）；
  - 清理：`app/src/main/java` 空目录链删除；androlua `res/anim` / `android.support` 空目录清理（git 无残留）；
  - **修复**：`app/build.gradle.kts` injectEngineDex 不再写 merge-assets 中间产物（AGP CompressAssets 增量重跑与手工 jar 冲突 → "already contains entry" 崩溃），标记只维护 `compressed_assets/out/assets/engine.dex.idx.jar`（二次构建幂等验证通过）；
  - 验收：`:app:assembleUniversalRuntime` + `:app:assembleArm64Debug` + `verifyRuntimeSurface` + `verifyMinimalism`（连续两轮）全绿；`testArm64DebugUnitTest`（含 build.* 打包单测）全绿。
- **Phase 1**：（已完成 2026-08-08）
  - `git mv editor/src/main/kotlin/.../editor → app/src/main/kotlin/.../editor`（15 文件）；textmate 资产并入 `app/src/main/assets/textmate`；`editor/src/test`（LanguageCatalogConsistencyTest / LuaDiagnosticsTest）→ `app/src/test`；
  - 字符串资源：editor 模块 30 键（values + values-zh）早前会话已并入 app，逐键核对无缺失；
  - 资源引用收口：editor 包内 `R` import 改为 `com.lioisme.lide.R`（EditorTabs/TextOps/Toolbar/FileBrowser/LuaEditor/SearchBar）；
  - 依赖并入 `app/build.gradle.kts`；`settings.gradle.kts` 移除 `include(":editor")`；`editor/build.gradle.kts` 删除；
  - 门禁语义改写（AGENTS.md R1~R5 模块边界 → 包边界 + 验证命令更新）；CI 收口：`build.yml` lint/unit-test/verify-r1 与 `update-sora-editor.yml` gates 改单模块任务；`scripts/update-sora-editor.ps1` 门禁命令同步；
  - 事故记录：ktlint 崩于损坏 WIP 残留 `app/.../editor/render/EditorLineDecorationFactory.kt`（乱码截断文件，非 git 内容、无引用）——已清除；修复过程中一次 PS ANSI 读写出中文乱码，经 `git show HEAD:... >` 字节级恢复 + 显式 UTF-8 重写，`git diff` 复核仅剩 import 行；
  - 验收：`:app:compileArm64DebugKotlin` + `:app:testArm64DebugUnitTest`（含迁入测试）全绿；ktlint 全绿；R1 grep（editor 包）零命中。
- **Phase 2**：（已完成 2026-08-09）
  - 新建 `third_party/androlua/UPSTREAM.md`：上游 tree sha / 导入日期 / 逐项改动登记（W^X、annotation 剥离×4、FileProvider 白名单、lsqlite3 mk、CrashHandler、lide_des/live2d 增量）；
  - 新增 `verifyVendorIsolation` 门禁任务（app/build.gradle.kts 尾部，路径经 `rootProject.file()` 解析——`:app` 内 `file()` 会解析到 app/ 下致误判）：①third_party 两目录零 `com/lioisme/**` ②UPSTREAM.md 存在且台账字面量断言 ③:androlua 构建脚本零外部依赖 ④sora 两 build.gradle.kts 无外部 URL；CI `build.yml` 新增 verify-vendor job（verify-r1 后）；
  - `verifyRuntimeSurface` 扩展：新增 `lidePrefixes`（`Lcom/lioisme/lide/engine|ui|build|project` 引擎 dex 剥离断言）+ `ideDepPrefixes`（androidx.compose./activity./navigation./appcompat./material./reorderable./cascade./profileinstaller./apksig./io.github.rosemoe. 引擎 dex 禁含断言）；
  - 验收：verifyVendorIsolation + verifyRuntimeSurface（全量 13m36s 重跑）全绿。
- **Phase 3**：（已完成 2026-08-09）
  - `checkDexDuplication`（verifyMinimalism 内）扩展四类断言：①引擎面类全集跨 dex 仅一处 ②引擎 dex 无 `Lcom/lioisme/` ③IDE dex 无引擎类前缀全集（Lcom/androlua/、Lcom/luajava/、Lcom/nirenr/、Lcom/android/cglib/、Landroid/widget/ 等）④引擎 dex 无 androidx.compose./appcompat./activity./navigation./material./sora 等 IDE 树（ideTreeInEngine）；
  - **关键 bug 修复（2026-08-09 实证）**：`scanDexClassNames()` 的 `class_defs[].class_idx` 实为 **type_ids 索引**，旧实现误作 string_ids 索引直接解析类名 → 引擎面断言全部错位（历史"通过"为巧合）。修复为二次间接（class_idx → type_ids[idx].descriptor_idx → string_ids[descriptor_idx]，偏移 0x40/0x44）。修复后 verifyMinimalismDebug + verifyMinimalism 双双通过；
  - **模拟器崩溃修复（NoSuchMethodError AndroidComposeView，2026-08-09）**：runtime 变体把 androidx.compose（AndroidComposeView 在 classes17.dex）等 IDE 依赖树随 consumer rules 带进引擎 dex，与宿主同名类在合并 classloader 冲突。修复：IDE 依赖全部改 **compileOnly + debugImplementation + releaseImplementation 三形态**（`ideDeps` 列表 + sora-editor project 依赖双模块同形态）；compose-bom 2025.06.00 三形态；`constraints { implementation("org.jetbrains:annotations:23.0.0") }` 解决 consistent resolution 冲突；新增 `app/src/runtime/res/values/themes.xml` 覆盖 Theme.Lide 为 @android:style/Theme.Material.NoActionBar（runtime 零 material3 依赖）；`ui-tooling-preview` 恒 debugImplementation。修复后 runtime dex 5945 → 775 类，compose/appcompat/activity/sora/material 全 0；
  - 打包链回归：`scripts/build-apks.ps1` 6 变体（x64/arm64/universal × Debug/Release）全建 + 签名逐包校验（v2 通过，CN=LIDE）；release 引擎 dex = classes2.dex（R8 合并）、debug = classes17.dex，`assets/engine.dex.idx` 指向一致；`testArm64DebugUnitTest --tests "com.lioisme.lide.build.*"` 全绿；
  - 模拟器回归（emulator-5554，x86_64 转译 arm64）：`scripts/adb_regress.ps1` 4 工程矩阵 8 项全 PASS（demo/DemoProject/kleinbottle/testjson + INIT）；新建项目（RegressionTest）+ 编辑 + 自动保存 + **运行按钮全链路**（EditorScreen → EngineActivity → Lua 执行 `hello from RegressionTest!` → BACK 返回）验证通过；am start 直拉引擎 + androlua:// URI 拉起两条路径均验证。
- **Phase 4**：（已完成 2026-08-09）
  - 本报告完成标记 + 归档 `docs/discard/`；同步 kb/README §2 / TASKS.md / discard/README §3；
  - AGENTS.md 契约追加：scanDex 解析语义、IDE 依赖三形态约定、verifyVendorIsolation/checkDexDuplication 门禁命令。