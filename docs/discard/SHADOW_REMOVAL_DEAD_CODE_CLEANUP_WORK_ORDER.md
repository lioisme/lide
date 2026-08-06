# Shadow 插件机制拆除 + 死代码/死依赖/死资产全量清除 任务报告

| 项目 | 内容 |
|------|------|
| 文档编号 | LIDE-WO-2026-019 |
| 文档类型 | 任务报告 / 执行工作单 |
| 编制日期 | 2026-08-07 |
| 编制依据 | 全量代码审计（`git ls-files` / `git status` / 全库 grep / 资源引用比对）+ 用户要求两项：①取消 Shadow 插件机制，androlua 仅作上游基线集成；②清除所有死代码、死依赖、死资产 |
| 审计范围 | 全部工作树（含未跟踪/被忽略残留）、`settings.gradle.kts`、各模块 `build.gradle.kts`、`assets/`、`docs/`、`.github/`、`scripts/`、`.gitignore` |
| 状态 | **（已完成）** 2026-08-07 全部条目执行完毕并验收通过，见 §5.3 验收记录 |

> 文档生命周期：全部条目完成后按 `AGENTS.md` 文档治理约定 `git mv` 至 `docs/discard/`，并同步更新
> `docs/kb/README.md` §2 矩阵、`docs/TASKS.md`、`docs/discard/README.md` §3。

---

## 1. 执行摘要（审计结论）

1. **Shadow 插件机制（loader/plugin/runtime 分层 + Plugin SDK 平台）的源码主体已经拆除**
   （`settings.gradle.kts` 仅剩 `:app / :editor / :androlua / :sora-editor`；git 零追踪 loader/plugin/sdk 源码），
   但**残留物遍布四类**：磁盘构建产物（约 503 MB）、未入库 APK 资产（12.3 MB，会随 `assemble` 打进 APK）、
   三篇文档正文引用、一份「设计定稿」仍驻留 `docs/planned/`（按治理口径仍属「计划中」，未归档）。
2. **androlua 上游基线集成形态无需任何代码改动**：`:androlua` 为 library 模块被 `:app` 引用，
   与「插件平台」方案（androlua 独立成插件 APK）无关的 `PluginScreen/PluginManager`
   （pro 兼容的目录式 Lua 插件）**判定为保留对象**，不在本次拆除范围。
3. **死资产 2 处**（`assets/samples/`、`assets/lide-plugins/`）、**死依赖 1 条**
   （`material3-window-size-class`）、**死字符串 17 条**（app 16 + editor 1，双语）、**空目录 1 处**
   （`third_party/patches/`）均经引用比对证实；**未发现死 Kotlin/Java 源文件**
   （`Haptics.kt` / `EditorTextOps.kt` / `SearchBar.kt` / `Base64Compat` 等疑似项全部有实际调用方，见 §4.4 排除清单）。
4. **文档治理动作 6 项**：D-18 反转登记（D-19）、`PLUGIN_PLATFORM_REPORT.md` 归档 discard、
   `kb/README.md` §2.3 / `TASKS.md` / `discard/README.md` / `third_party/README.md` 同步、`.gitignore` 增加废弃守卫。

---

## 2. 任务一：取消 Shadow 插件机制（androlua 仅作上游基线集成）

### 2.1 架构现状判定（无需改代码）

- `settings.gradle.kts`：`include(":app") / (":editor") / (":androlua") / (":sora-editor")` —
  **无任何 loader / plugin / sdk 模块**，Shadow 模块已从构建图摘除。
- `:androlua`（`third_party/androlua/`）为 library，`:app` 以 `implementation(project(":androlua"))`
  集成 —— **即用户要求的「上游基线集成」形态，保持不动**。
- 代码级 grep 证实：`com.tencent.shadow`、`PluginProvider/IPluginHost/PluginDescriptor`、
  `ProxyActivity`、`lide-plugins` 在 `app/src`、`editor/src`、`third_party/androlua/src` **零命中**。
- 结论：**无源码可删**，本次拆除 = 清除残留物 + 收口文档。

### 2.2 残留清单（全部经文件系统与 git 验证）

| # | 残留物 | 规模 | 性质 | 证据 |
|---|--------|------|------|------|
| S-1 | `third_party/shadow/`（Tencent Shadow SDK 全树） | 1,670 文件 / 11.8 MB | 仅 `build/` + `.gradle/` 缓存（源码已删，残留构建产物；git 零追踪） | `git ls-files third_party/shadow` = 0；`check-ignore -v` 命中 `build/`/`.gradle/` 规则 |
| S-2 | `androlua-loader/`、`androlua-plugin/`、`hello-loader/`、`hello-plugin/`、`hello-runtime/`、`plugin-sdk/` | 6 目录 6,687 文件 / 478.8 MB（其中 androlua-plugin 452.8 MB） | 各仅含 `build/` 残留（Shadow 插件打包产物，含引擎 .so 副本） | 目录下 `Get-ChildItem` 仅 `build`；git 零追踪 |
| S-3 | 根 `build/shadow/`（`ApkanalyzerPath.txt`）、`build/androlua-plugin/`、`build/hello-plugin/` | 3 项 12.5 MB | Shadow 打包器输出（`build/androlua-plugin/androlua-debug-1.0.0.zip` 等） | 根 `build/` 为被忽略构建产物 |
| S-4 | `app/src/main/assets/lide-plugins/`：`androlua-debug-1.0.0.zip`（10.2 MB）+ `hello-debug-1.0.0.zip`（2.7 MB） | 2 文件 12.3 MB | **未跟踪、未忽略（会随 APK 打包）**、全库零引用 —— 属「打进 APK 的死资产」 | `git status` 显示 `??`；`git check-ignore` 不命中；grep `lide-plugins` 零命中 |
| S-5 | `docs/planned/PLUGIN_PLATFORM_REPORT.md` | 447 行 | 插件平台「设计定稿」（LIDE-DESIGN-2026-001）—— 方案已取消，应归档 discard | `kb/README.md` §2.3 登记为计划中 |
| S-6 | 文档引用链（见 §4.1） | 6 处 | `kb/README.md`、`kb/DECISIONS.md` D-18 + 时间线、`TASKS.md`、`discard/README.md`、`discard/PLUGIN_SEPARATION_ARCHITECTURE_REPORT.md` 取代注记 | grep 逐条定位（行号见 §4.1） |

### 2.3 删除操作

```powershell
# S-1 ~ S-3：删除 Shadow 构建残留（全部为被忽略产物，不触碰 git 历史）
Remove-Item -Recurse -Force third_party\shadow
Remove-Item -Recurse -Force androlua-loader, androlua-plugin, hello-loader, hello-plugin, hello-runtime, plugin-sdk
Remove-Item -Recurse -Force build\shadow, build\androlua-plugin, build\hello-plugin

# S-4：删除 Shadow 插件包资产（未入库，直接删；若根 build/ 需保留整体，仅删 shadow 相关子项）
Remove-Item -Recurse -Force app\src\main\assets\lide-plugins

# S-5：归档（git mv 保留历史，符合治理约定）
git mv docs/planned/PLUGIN_PLATFORM_REPORT.md docs/discard/PLUGIN_PLATFORM_REPORT.md
```

### 2.4 保留判定（明确不在拆除范围）

| 对象 | 判定 | 理由 |
|------|------|------|
| `PluginScreen.kt` + `PluginManager.kt`（`plugin/` 目录式 Lua 插件） | **保留** | pro 兼容能力（`ANDROLUA_FEATURE_PARITY_AUDIT` 定义的插件 = 目录 + init.lua/main.lua），与 Shadow 插件平台是两种异物 |
| `assets/plugin/plugin.json` + `LideApp.extractExamplePlugin()` | **保留** | 同一 pro 兼容机制 |
| `ApiIndex`（`assets/javaapi/android.lua` 类表） | **保留** | API 浏览器数据源 |
| `assets/layouts/javaapi/*.aly`（P2-14 资产先行） | **保留** | 已登记决策 D-12（布局助手 UI 单独立项） |
| git 历史 commit `aaa23d5`（插件平台架构） | **不动** | 历史不可改写 |

### 2.5 验证门禁（拆除后必须全部成立）

```powershell
# 1) 无任何残留目录/文件
Test-Path third_party\shadow; Test-Path androlua-loader; Test-Path plugin-sdk
Test-Path app\src\main\assets\lide-plugins
# 2) 文档引用收口
git grep -n "PLUGIN_PLATFORM_REPORT" docs/            # 应仅剩 discard 内条目
git grep -n "lide-plugins\|hello-debug\|androlua-debug" docs/ app/ editor/   # 零命中
# 3) 全仓符号门禁
git grep -rn "com.tencent.shadow" -- "*.kt" "*.java" "*.kts"   # 零命中
git grep -rn "IPluginHost\|IPluginProvider\|PluginDescriptor" -- app/src editor/src third_party/androlua/src  # 零命中
# 4) 构建回归
.\gradlew.bat :app:compileArm64DebugKotlin --console=plain
# 5) APK 体积对比（拆除前记录 lide-plugins 12.3 MB，拆除后不应再打入）
.\gradlew.bat :app:assembleArm64Debug --console=plain
```

---

## 3. 任务二：死代码 / 死依赖 / 死资产全量清除

### 3.1 死资产

| # | 资产 | 规模 | 判定证据 | 操作 |
|---|------|------|---------|------|
| D-1 | `app/src/main/assets/samples/`（demo / DemoProject / kleinbottle / testjson，14 文件） | 139.5 KB | D-16（2026-08-01）已反转 D-03：示例改由 `scripts/examples-src/` 构建期生成 `assets/examples/*.alp`（`ExamplesScreen` 读取）。`samples/` 4 工程与 `examples-src/` 同名工程**文件集与内容一致**；运行时代码零引用（仅 `ApkBuilder.HOST_ASSET_EXCLUDED` 排除名单 + 测试夹具名提及） | `git rm -r app/src/main/assets/samples` + 同步 §3.5 收口项 |
| D-2 | `app/src/main/assets/lide-plugins/*.zip` | 12.3 MB | 见 S-4（Shadow 插件包，零引用且会进 APK） | 随任务一删除 |
| D-3 | `third_party/patches/`（空目录） | 0 | 零文件、零 git 追踪；仅 `third_party/README.md` 表格行提及 | 删目录 + 同步 §3.5 |

### 3.2 死依赖（`app/build.gradle.kts`）

| # | 依赖 | 判定 | 操作 |
|---|------|------|------|
| D-4 | `implementation("androidx.compose.material3:material3-window-size-class:1.4.0")`（:174） | 全库 grep `WindowSizeClass` / `window-size-class` / `material3.adaptive` **零命中**，无任何 `calculateWindowSizeClass` 调用 | 删除该行 |
| D-5 | `implementation(fileTree("libs") { include("*.jar") })`（:164） | `app/libs/` 仅 `.gitkeep`，长期空置 | **可选**：保留（pro 兼容目录结构，.gitkeep 表明有意）；如删除需同时删 `app/libs/` |

### 3.3 死字符串（双语 `values/` + `values-zh/`，引用比对证实）

- **app**（16 条）：`back`、`select_file`、`cd_go_up`、`cd_close`、`cd_replace`、`cd_replace_all`、
  `cd_api_browser`、`word_wrap_on`、`word_wrap_off`、`whitespace_on`、`whitespace_off`、
  `perm_host_section`、`build_not_ready`、`api_native_module_desc`、`api_category_native`、
  `api_usage_title`、`api_kind_class`（共 17 条，含 `api_kind_class` 复检）
- **editor**（1 条）：`cd_close_tab`
- 判定依据：对全部 `*.kt`（app/editor/androlua）+ `*.xml`（manifest/xml 配置）做 `R.string.<名>` 精确匹配，
  零命中；`accessibility_service_description` 复检命中 `xml/accessibility_service_config.xml`，**排除**。
- 操作：从 `app/src/main/res/values/strings.xml` 与 `values-zh/strings.xml`、`editor` 两语言文件各删对应条目。

### 3.4 死 Kotlin/Java 源文件

**结论：未发现。** 已对 72 个宿主/编辑器/androlua 管理 Kotlin 文件逐一做引用比对，
以下「疑似死代码」均证实有实际调用方，**全部保留**：

| 疑似项 | 实际调用方 |
|--------|-----------|
| `editor/Haptics.kt`（pressAndHoldHapticFeedback） | `EditorComponents.kt`、`EditorDialogs.kt`、`SearchBar.kt` |
| `editor/EditorTextOps.kt`（editorTextOpsActions） | `EditorScreen.kt:356`（更多菜单） |
| `editor/SearchBar.kt` | `EditorComponents.kt:62`（替换栏） |
| `editor/EditorToolbar.kt` / `EditorMoreAction` | `EditorScreen.kt:317` |
| `app/build/Base64Compat.kt` / `V2Signer.kt` / `ZipUtils.kt` | `ApkSigner.kt` / `ApkBuilder.kt` |
| `app/engine/EngineResultParser.kt`、`util/HelpParser.kt`、`i18n/LocaleManager.kt`、`project/ProjectEntryOps.kt`、`ui/theme/ThemeConfig.kt`、`build/ApkBuilder.kt` 全家 | 各屏幕/管理器直接引用 + 单测 |
| androlua 管理 9 类（AlpManager/AlpNaming/AXmlManifest/LuaCompiler/LuaConfigParser/PermissionResolver/PluginManager/ZipUtil/EngineBridge） | `MainActivity`、`AlpImportCoordinator`、`ProjectManager`、`BuildApkScreen`、`RunEngineLauncher`、`LideNavHost` 等 |
| 资源：`res/anim/slide_*`、`res/xml/*`、`assets/help.txt`、`assets/luadoc/`、`assets/keys/`、`assets/lua/`、`assets/javaapi/android.lua`、`assets/plugin/plugin.json` | `RunEngineLauncher`、manifest、`HelpScreen`、`LuaManualScreen`、`BuildApkScreen`、引擎 bootstrap、`ApiIndex.kt:31`、`LideApp` |

### 3.5 收口项（删除 D-1 的连带同步）

| # | 位置 | 改动 |
|---|------|------|
| 1 | `app/src/main/kotlin/com/lioisme/lide/build/ApkBuilder.kt:178` | 从 `HOST_ASSET_EXCLUDED` 移除 `"assets/samples/"`（该排除项唯一用途即 samples 目录） |
| 2 | `app/src/test/kotlin/com/lioisme/lide/build/ApkBuilderTest.kt:60,164` | 夹具条目 `assets/samples/demo/main.lua` → 改用 `assets/examples/demo.alp`（或移除断言），保持「排除」语义测试有效性 |
| 3 | `docs/in-progress/PARITY_COMPLETION_WORK_ORDER.md:51,71-74` | 「验收基准」引用 `app/src/main/assets/samples/` → 改为 `scripts/examples-src/`（同一内容的现行真源） |
| 4 | `docs/WHITEPAPER.md:379` | 引用 `app/src/main/assets/samples/DemoProject/libs/README.txt` → `scripts/examples-src/DemoProject/libs/README.txt` |
| 5 | `docs/discard/*` 历史引用 | 不动（丢弃区只追溯） |

### 3.6 可选清理（本地磁盘卫生，非入库项）

- 根 `build/`（`reports/problems` 等）、各模块 `build/`、根 `.gradle/`、`.kotlin/` —— 均被忽略、可再生，
  如需释放磁盘可整体删除（约 500 MB 中绝大部分来自 §2.2 残留）。

---

## 4. 文档治理动作（连同任务一/二一并提交）

### 4.1 需修订文档清单

| # | 文件:行 | 内容 | 操作 |
|---|---------|------|------|
| G-1 | `docs/kb/DECISIONS.md:24`（D-18） | 插件平台立项 | 追加 **D-19 反转登记**：取消插件平台（D-18 反转），androlua 维持 upstream 基线 library 集成（对应原 D-02/D-08 不反转）；时间线行 35 补注 |
| G-2 | `docs/kb/README.md:69` | §2.3 `PLUGIN_PLATFORM_REPORT.md` 条目（计划中） | 移除条目（文档已归档 discard）；§2.3 标题「4 篇」计数按现状重核（现实际 6 行，历史漂移一并修正） |
| G-3 | `docs/TASKS.md:33` | 插件平台立项行 | 删除或改标「已取消（2026-08-07）」 |
| G-4 | `docs/discard/README.md:86` | `PLUGIN_SEPARATION_ARCHITECTURE_REPORT.md` 条目「已被 PLUGIN_PLATFORM_REPORT 取代」 | 补注：`PLUGIN_PLATFORM_REPORT` 亦已归档（2026-08-07 方案取消），三者均为历史 |
| G-5 | `third_party/README.md` | `patches/` 表格行（补丁目录） | 删除该行（目录已空/已删） |
| G-6 | `.gitignore` | Legacy guards 区 | 追加废弃守卫：`third_party/shadow/`、`androlua-loader/`、`androlua-plugin/`、`hello-loader/`、`hello-plugin/`、`hello-runtime/`、`plugin-sdk/`、`app/src/main/assets/lide-plugins/`（防重建，同现有「参考资料/docs/reference」守卫手法） |
| G-7 | 本报告 | — | 完成后标记「（已完成）」+ 日期 + 验收证据，`git mv` 至 `docs/discard/` |

---

## 5. 执行顺序、风险与验收

### 5.1 执行顺序（推荐）

1. 文档先行：G-1~G-5（快、低风险）→ 2. 死字符串（§3.3）+ 死依赖（§3.2）+ 收口项（§3.5）→ 3. 删除
   `assets/samples/`（D-1）→ 4. 删除 Shadow 残留（§2.3，含 S-4/lide-plugins）→ 5. G-6 `.gitignore` 守卫 →
   6. 构建 + 全部验证门禁（§2.5）→ 7. 提交（分两个 commit：docs 收口 / 删除与代码清理）。

### 5.2 风险与缓解

| 风险 | 缓解 |
|------|------|
| 误删 `assets/samples/` 后回归基准失效 | 内容已与 `scripts/examples-src/` 逐文件核验一致（§3.1）；PARITY 文档引用同步（§3.5-3） |
| 字符串删除波及 manifest/XML 引用 | 仅删 `R.string.*` 在全部 .kt/.xml 零命中的条目；`accessibility_service_description` 等命中项已排除 |
| 根 `build/` 整删影响本地增量构建 | 仅删 shadow 相关子项（S-3），不动 `reports/` |
| 文档计数/链接漂移 | G-2 一并重核 §2.3 实际行数与「4 篇」标题 |

### 5.3 验收（全部通过即关闭本单）

| 验收项 | 证据 |
|--------|------|
| Shadow 残留归零 | §2.5 门禁 1/2/3 通过 |
| 构建回归 | `.\gradlew.bat :app:compileArm64DebugKotlin`、`:app:assembleArm64Debug`、CI 门禁（R1/lint/unit-test）全绿 |
| 单测回归 | `:app:testX64DebugUnitTest :editor:testDebugUnitTest :androlua:testDebugUnitTest` 全绿（ApkBuilderTest 收口后） |
| APK 瘦身 | 拆除前 vs 拆除后 APK 体积对比（预期减少 ≈ lide-plugins 12.3 MB） |
| 文档治理 | G-1~G-7 全部完成，本报告归档 discard 并更新 kb/TASKS/discard 三处索引 |

### 5.4 完成记录（2026-08-07，opencode 执行）

| 项 | 结果 | 证据 |
|----|------|------|
| §2.3 S-1~S-4 删除 | `third_party/shadow`（长路径残留经 `\\?\` 前缀补删）、6 个 loader/plugin/runtime/sdk 目录、根 `build/shadow\|androlua-plugin\|hello-plugin`、`app/src/main/assets/lide-plugins`、`third_party/patches` 全部删除 | `Test-Path` 五项全部 False |
| S-5 归档 | `git mv docs/planned/PLUGIN_PLATFORM_REPORT.md docs/discard/` | moved |
| D-1 删除 | `git rm -r app/src/main/assets/samples`（14 文件） | git 状态 D |
| D-4 死依赖 | `app/build.gradle.kts` 移除 `material3-window-size-class:1.4.0` | 构建成功 |
| §3.3 死字符串 | app 17 条 + editor 1 条 × 双语 4 文件删除 | 编译成功（R 资源无悬空引用） |
| §3.5 收口 | `ApkBuilder.HOST_ASSET_EXCLUDED` 移除 samples 行；`ApkBuilderTest` 夹具/断言移除 samples 条目（keys/textmate 断言保留）；PARITY 4 处 + WHITEPAPER 1 处引用改 `scripts/examples-src/` | ApkBuilderTest 7/7 通过 |
| G-1~G-6 | D-19 反转登记 + 时间线补注；kb/README §2.3 移除 PLUGIN_PLATFORM 行并重核计数（计划中 7 篇 / planned 8→7 / discard 44→45）；TASKS 插件行标已取消；discard/README §3.2 补 `PLUGIN_PLATFORM_REPORT.md` 行（10 篇）；third_party/README 删 patches 行；`.gitignore` 追加 8 条废弃守卫 | 见各文件 |
| §2.5 门禁 | ① 残留目录 Test-Path 全 False；② grep `lide-plugins\|hello-debug\|androlua-debug` 仅历史叙述命中；③ `com.tencent.shadow`（kt/java/kts）零命中、`IPluginHost\|IPluginProvider\|PluginDescriptor` 仅 `assets/javaapi/android.lua:1800`（AOSP `MediaCas$PluginDescriptor`，API 浏览器数据，非 Shadow） | grep exit 1 |
| 构建回归 | `:app:compileArm64DebugKotlin` BUILD SUCCESSFUL（56s，仅存量 deprecation 警告）；`:app:assembleArm64Debug` BUILD SUCCESSFUL（21s，NDK 引擎随构建正常产出） | — |
| 单测回归 | `:app:testX64DebugUnitTest :editor:testDebugUnitTest :androlua:testDebugUnitTest` BUILD SUCCESSFUL；ApkBuilderTest 7/7、ApkSignerTest 6/6、BuildPackagingTest 3/3 等全绿 | test-results XML |
| APK 验证 | `app-arm64-debug.apk` 44.01 MB，Zip 条目扫描 `lide-plugins\|samples\|shadow` 零命中（预期瘦身 ≈12.3 MB 插件资产） | 实测 |

G-7：本报告已标记「（已完成）」并 `git mv` 至 `docs/discard/`；kb/README §2、TASKS.md、discard/README §3 索引已同步。

---

## 6. 审计证据附录

- 残留物规模表：`third_party/shadow` 1,670 文件/11.8 MB；`androlua-plugin` 5,619 文件/452.8 MB；
  6 个 loader/plugin/runtime/sdk 目录合计 6,687 文件/478.8 MB；根 `build/` 4 文件/12.5 MB；
  `lide-plugins/` 2 文件/12.3 MB。
- 关键 grep 证据：
  - `git ls-files` 对 `third_party/shadow`、`androlua-loader`、`plugin-sdk` 等 = 0（源码从未入库/已删）。
  - 全库（app/editor/androlua 源码 + build 脚本 + workflow + scripts）对
    `com.tencent.shadow`、`IPluginHost/IPluginProvider/PluginDescriptor`、`lide-plugins` = 零命中。
  - `assets/samples` 与 `scripts/examples-src` 共享 4 工程文件集一致（`git diff --no-index` 仅列出来自
    examples-src 独有工程 hello/listview/live2d 与大小写差异 testjson↔TestJson）。
  - 死字符串比对：313（app）/31（editor）条全量名称 × 全量 .kt/.xml 引用，命中 17 条。
- 编制日期：2026-08-07；审计人：opencode（deepseek-v4-flash-free）。
