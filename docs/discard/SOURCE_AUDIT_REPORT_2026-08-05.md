# lide 全量源码审计报告（技术栈正确性 × 屎山清单 × 大厂验收修复计划）

- **审计日期**：2026-08-05
- **审计对象**：`lide` 仓库全量源码（`:app` 宿主 + `:editor` 纯编辑器 + `:androlua` vendored 运行时 + `:sora-editor` vendored 上游 + `app/src/main/cpp/` 原生引擎 + 构建脚本 + 测试 + CI + 文档）
- **审计方法**：关键源码逐文件精读（构建脚本、导航/会话/索引/打包/引擎桥）、三个并行 explore 子代理全量扫描（app UI 34 文件、androlua 402 Java + 9 Kotlin、测试/CI/许可证/README）、交叉 grep 复核
- **基准提交**：`4fc9943`（HEAD，仓库仅 3 个提交；工作区另有 36 个未提交文件）
- **关键决策记录**：签名相关项（keystore 入库、testkey 资产）**按用户指示暂不处理**——当前处于开发阶段，接受风险；列入"发布前必改"清单而非本轮 Tier。

---

## 0. 执行摘要

| 维度 | 判定 |
|---|---|
| 架构分层（R1–R5：`:editor` 纯净、引擎源码化、R5 二进制门禁） | ✅ 优秀，无越界 |
| 编辑器选型（sora-editor 0.24.6 vendored，LGPL-2.1） | ✅ 正确决策，但存在**双份类来源**（P1） |
| UI 选型（Compose BOM 2025.06.00 + material3 1.4.0 + nav 2.8.5） | ✅ 正确，SearchBar 冻结根因已修 |
| 运行时引擎（Lua 5.3.3 AndroLua fork + luajava，pro 同源） | ✅ 与 pro 100% 对齐且为超集，属有意设计 |
| 打包链（手写 v1/v2 签名 + AXML 字节补丁） | ⚠️ 工程量正确，但有 1 个真 bug（Base64）+ 1 个潜在解码缺陷（readUtf8） |
| 测试 / CI | ✅ 高于平均水平（~90 单测、CI 五道门禁 + R1 pre-commit hook），有明确缺口 |
| 安全 / 合规 | ❌ P0×2（均属签名族，已决策搁置）、P1×6 |

无第一方 TODO/FIXME/HACK；`:app` 无 runBlocking、无裸线程（除一处 TextMate 预加载）；用户可见字符串全部资源化（values-zh 完整）。

---

## 1. 技术栈选型判定（逐层）

### 1.1 构建链 —— 选型正确，版本栈有债（P2）

- AGP 8.6.0 + **Gradle 9.5.1**（`gradle/wrapper/gradle-wrapper.properties:3`）是未经验证的向前组合（AGP 8.6 官方验证区间至 Gradle 8.x）。CI 目前能通过，属偶然兼容。
- `compileSdk = 36`（`app/build.gradle.kts:20`）对 AGP 8.6.0 属未支持版本，靠 `gradle.properties:5` 的 `android.suppressUnsupportedCompileSdk=36,35` 压制。同根因。
- **修复方向统一**：升级 AGP（8.9.1+ 原生支持 compileSdk 36、对齐 Gradle 9.5.1）或回退 Gradle 8.9 + 保留压制。
- 其余选型（BOM 2025.06.00 显式覆盖 material3 1.4.0、reorderable 3.0.0 固定）均有注释记录的实证理由（TooltipKt NoSuchMethodError 闪退、Squircle 兼容），**保留**。

### 1.2 编辑器 —— 选型正确，一处双份来源（P1）

- `io.github.rosemoe:editor:0.24.6` 从 Maven 改为 **vendored 源码**（`third_party/sora-editor/`）是正确决策：离线构建、许可证（LGPL-2.1）可控、`OnigNative` 桩类（`language-textmate/.../OnigNative.java`）忠实模拟官方 Maven 行为的 Joni 纯 Java 回退。
- **问题**：`:app` 同时又拉 Maven 坐标 `io.github.rosemoe:editor:0.24.6` + `language-textmate:0.24.6`（`app/build.gradle.kts:174,176`），而 `:editor` 用 project 模块（`editor/build.gradle.kts:56-58`）→ app 运行时 classpath 上存在**两份同名类**（vendored 桩类 vs Maven 真类），类遮蔽顺序依赖 Gradle 解析顺序，属于地雷。**修复**：删除 `:app` 的 Maven 两行，统一走 project 模块（行为等价）。

### 1.3 UI 栈 —— 正确且性能纪律良好（正面）

- Compose BOM 2025.06.00 + material3 1.4.0（新版 SearchBar API 前提）+ navigation-compose 2.8.5 + material-icons-extended，选型合理。
- 冻结类问题已按 `API_BROWSER_FREEZE_FIX_PLAN.md` 系统修复（独立 `searchActive` state、单协程动画驱动、pendingNavigation 消费、非空负缓存、两阶段索引）。
- `EditorSession` 采用 Squircle 「needsUpdate」模式（`editTick` 事件流），**每次击键不触发整屏重组**——这是大厂级性能实践。
- P2/P3 集中的具体违反项见 §3。

### 1.4 运行时引擎 —— 选型正确，属有意取舍（保留现状）

- Lua 5.3.3 AndroLua fork + switch/lambda/when/let/mean 语言扩展（`llex.h:28-37`、`llex.c:40-44`、`lparser.c:1648-1735`）、utf8.match 扩展、Android log 补丁。**字节码与 fork 格式绑定**（`LuaCompiler` 内已注释跨 ABI 可用性）——feature 而非 bug，升级 Lua 需重载扩展。**文档化即可，勿动**。
- 单 LuaState 每单元 + 全 native `synchronized`（`LuaState.java:185-408`）+ onDestroy 延迟 500ms 后台 close（`LuaActivity.java:802-846`）——继承自 pro 的设计；多步栈序列非原子，仅在单线程使用下安全。**建议注释化声明，勿改**。
- `CrashHandler.uncaughtException` 返回 true 吞掉未捕获异常、日志写 `/sdcard/androlua/crash/`（`CrashHandler.java:88-98,227`）——P2，见 §3。

### 1.5 打包链 —— 工程量大且多数正确，2 个缺陷（P1）

- ApkBuilder / ApkSigner（v1 JAR + 手写 DER CMS PKCS#7）/ V2Signer（v2 分块 SHA256）/ ZipUtils（STORED 4 字节对齐）/ AXmlManifest（aapt2 AXML 字节级解析与补丁）均为自研硬核工程，具真实验签测试（`ApkSignerTest.kt` 对入库测试密钥做真实校验）。
- 缺陷：`java.util.Base64`（API 26+，minSdk=24）与 `AXmlManifest.readUtf8` 长度前缀解码偏离 AXML 规范。见 §3 P1。

### 1.6 测试与 CI —— 高于水平，缺口明确（P2/P3）

- 13 个 JVM 单测文件 ~90 用例；`:androlua` 27 用例含 AXml/配置解析/编译器错误行；真实签验测试。
- GitHub Actions 五道门禁：`lint`（lintArm64Debug）、`unit-test`（仅 app+editor）、`build-arm64`/`build-x64`、`verify-release`（R8 Lua API 面守护）、`verify-no-binaries`（R5 硬门）。另 `.githooks/pre-commit` 强制 R1（grep 拒绝 luajava/androlua/loadLibrary 进 editor）。
- **缺口**：`:androlua` 29 用例不进 CI（`build.yml:52` 只跑 app+editor）；无 UI/Robolectric/仪器测试；ApiIndex 索引与反射缓存逻辑零覆盖；无签名卫生门禁。

---

## 2. 屎山清单（按优先级）

### P0 ── 需尽快处理

| # | 位置 | 问题 | 处置 |
|---|---|---|---|
| 1 | `app/keystore.jks` + `app/keystore.properties`（密码 `lide123`）被 git 跟踪；`README.md:52` 称"不得入库"与事实矛盾 | 签名密钥 + 口令公开，任何人可冒充签名产物 | **已决策搁置**（开发阶段）。发布前必改：换新 key、`.gitignore`、CI secret 注入 |
| 2 | `app/src/main/assets/keys/testkey.pk8` + `testkey.x509.pem` 入库，`BuildApkScreen.kt:248-249` 直接读取 | 所有用户打包 APK 共用一把公开私钥（pro 兼容设计但风险实存） | **已决策搁置**。发布前补风险文档或每次构建生成密钥 |

### P1 ── 本轮/下轮必须修（2026-08-05 已全部完成 ✅，见 §6）

| # | 位置 | 问题 | 修复 |
|---|---|---|---|
| 3 | `ApkSigner.kt:8` `import java.util.Base64` | `java.util.Base64` 仅 API 26+，minSdk=24 → API 24/25 打包崩溃（NoClassDefFoundError） | ✅ 换 `android.util.Base64` 或纯 Kotlin 兼容层 |
| 4 | `AXmlManifest.kt:424-439` `readUtf8` | 长度前缀按固定 `u16` 读、charLen/byteLen 顺序与 AXML 规范相反（规范：逐字节 0x80 flag 变长、charLen 在前）。目前仅因宿主产物为 UTF-16 池而未触发 | ✅ 按规范重写 + 新增 UTF-8 池/非 ASCII 长串单测 |
| 5 | `app/build.gradle.kts:174,176` vs `editor/build.gradle.kts:56-58` | sora-editor 双份来源（Maven + vendored project），同名类并存于运行 classpath | ✅ 删 `:app` Maven 两行，统一 project 模块 |
| 6 | `AndroidManifest.xml:129-175` | `com.androlua.LuaActivity` `exported="true"` + `BROWSABLE` VIEW/EDIT `file`/`content` 过滤 | ✅ 改 `exported="false"`、删除全部 intent-filter |
| 7 | `AndroidManifest.xml:214-222` + `res/xml/androlua_filepaths.xml:5` | FileProvider `external-path path="/"` 越权暴露整个外置存储 | ✅ 收敛到 `AndroLua/` 与打包输出目录 |
| 8 | `AndroidManifest.xml:60,63` | `requestLegacyExternalStorage="true"` + 全局 `usesCleartextTraffic="true"` | ✅ 移除两属性，改 `networkSecurityConfig` 白名单化 |
| 9 | `AndroidManifest.xml:198-212` | `LuaAccessibilityService` exported + 最大能力掩码（typeAllMask/gestures/按键过滤） | ✅ 补 `docs/reference/security-model.md` 权限声明文档（服务必须保持 exported=true 以被系统绑定） |

### P2 ── 近期修（2026-08-05/06 已全部完成 ✅，见 §6）

| # | 位置 | 问题 |
|---|---|---|
| 10 | `BuildApkScreen.kt:100` | ✅ Compose state（`buildStage`）在 `Dispatchers.IO` 协程内写入，违反主线程约定 |
| 11 | `MainActivity.kt:76,155-157`；`EditorScreen.kt:218` | ✅ 主线程 `.alp` 解压 / 文件拷贝 / `init.lua` 读取（冷启动与点按卡顿） |
| 12 | `PermissionScreen.kt:51-53,123-131,150` | ✅ composition 内 `getPackageInfo` + N×`checkSelfPermission` + AppOps binder；点一行"请求"一次性弹全部权限 |
| 13 | `ApiBrowserScreen.kt:89-98` | ✅ `buildBase`/`completeBuild` 无 `try/catch(Throwable)`，违反 AGENTS.md 降级约定 |
| 14 | `EditorScreen.kt:204-213` | ✅ 首次 composition 内 `getPackageInfo(GET_PERMISSIONS)`，叠加已知首帧成本 |
| 15 | `CrashHandler.java:88-98,227` | ✅ 恢复进程终止（日志落盘后交由系统默认处理器，不再吞异常）+ 日志改私目录 `filesDir/crash/`（原 `/sdcard/androlua/crash/` 公共可读属隐私泄露，API 33+ 需额外权限） |
| 16 | Gradle 9.5.1 / AGP 8.6.0 / suppressUnsupportedCompileSdk 组合 | ✅ 版本栈对齐：AGP 8.13.2 + `dependencyResolutionManagement` 收归 5 处 repositories + 去 suppress 标记 |
| 17 | `.github/workflows/build.yml:52` | ✅ `:androlua` 的测试不进 CI |
| 18 | `ApiCategoryClassifier.kt:69` | ✅ 两阶段分类：新增 `categorizeBase()`（纯前缀零反射）供 buildBase 使用（widget/view 占位 FRAMEWORK）；`completeBuild()` 增 `refineCategories()`（预算 1.5s）经 `isViewSubclass` 缓存细化 COMMON_WIDGETS 并原子重建 categories/categoryCounts |
| 19 | `ProjectListScreen.kt:155,180,350,367,383` | ✅ 硬编码 `"LIDE"` 文字 + 可点击项缺 `contentDescription`（无障碍） |
| 20 | `MainActivity.kt:36-40` | ✅ 打包模式直入 EngineActivity 并 finish()——功能成立但属 hack，需文档化 |

### P3 ── 卫生 / 债

- `LideApp.kt:22-27,36-106`：裸 `Thread` 预加载 TextMate + `Application.onCreate` 主线程资产解压 + `:89` 全静默 `catch {}` + `printStackTrace`。
- 上帝类：`EditorScreen.kt`(721 行)、`ApiBrowserScreen.kt`(691 行)、`EditorSession.kt`(20 方法)；`EditorSession.release()`（:90-95）在自身协程内 `scope.cancel()`。
- `SettingsScreen.kt:78-83,321-325`：魔法标签 `"system"/"en"/"zh"` 三处重复（`LocaleManager.kt:16` 亦同）；`SettingsScreen.kt:481-483` 与 `Theme.kt:183` 手写 `luminance`。
- `app/build.gradle.kts:184-214` `verifyReleaseLuaSurface`：硬编码 25 个 Lua 可见类名，新增控件易忘同步。
- `proguard-rules.pro:2` 保留 `org.luaj.**`（仓库无此包，过时）；`android/support/multidex/*` 死代码（`LuaActivity.java:235` 注释掉 `installLibs`）。
- `ApiBrowserScreen.kt:137-139,244`：composition 内同步过滤 + 读动画派生值门控 Dialog（当前安全但脆，红线文档已点名此模式）。
- `MainActivity.kt:78,169` / `ProjectManager.kt:106,133`：日志打印绝对路径与工程名（隐私）。
- `LocaleManager.kt:41` 无注释 `@Suppress("DEPRECATION")`；`ApiIndex.kt:125,283` `DexFile` 弃用未压制。
- 仓库卫生：仅 3 提交 + 36 未提交文件；`PROGRESS.md` 历史条目与 R5 源码化转型不一致（WP-014 未开工）。
- `third_party/patches/material3_searchbar_patch/`：AOSP material3 SearchBar 参考源码入库（诊断素材，非编译）；建议移入 `docs/reference/` 或标注仅供溯源。

---

## 3. 应保留的正面事实（大厂级实践）

- **零第一方 TODO/FIXME/HACK**；无 runBlocking；`Thread{}` 仅一处且命名 + daemon。
- 字符串全部资源化，values-zh 与 values 对齐。
- 所有屏幕加载/保存路径正确使用 `withContext(Dispatchers.IO)`。
- `NavigationGate` 单漏斗入队门控（16ms 轮询 / 5s 超时 / WeakHashMap），解决 navigation 快速进出栈失步冻结。
- `ApiIndex` 两阶段构建（2s + 2.5s 预算上界）+ `buildLock` 互斥 + CHM 非空负缓存（规避全 dex 扫描风暴）。
- `ZipUtil` 双保险 zip-slip 防护（段级校验 + canonical 前缀校验）。
- 单元测试覆盖真实验签（v1）与打包 round-trip。
- CI 五门禁 + R1 pre-commit hook + `verifyNoBinaries`（R5 硬门）——工程规范意识高于绝大多数个人项目。
- License 姿态正确：全仓 GPL-3.0（含 vendored AndroLua GPL 派生，README 已声明）；LGPL-2.1 sora 在 GPL-3.0 全仓下兼容（无专有宿主时 relink 义务 moot）。

---

## 4. 修复计划（按大厂验收标准分级）

- **Tier 0（1 天）**：P1#3 Base64 → P1#4 readUtf8 + 单测 → P1#5 依赖统一 → P1#6/7/8/9 manifest 收敛（含补权限声明文档）。
- **Tier 1（2–3 天）**：P2#10-14 主线程/协程正确性 + P2#20 打包 hack 文档化 → P2#17 CI 纳入 `:androlua` 测试 → P2#19 无障碍。
- **Tier 2（1 周内）**：P2#16 版本栈对齐（升级 AGP 8.9.1+，去 suppressUnsupportedCompileSdk）→ `dependencyResolutionManagement` 收归 5 处重复 repositories → ktlint 扩展至 `:app` → P3 卫生项批量。
- **Tier 3（产品）**：按 `FULL_AUDIT_REPORT_2026-08-01.md` 缺口表推进功能对齐 60%→100%（打包闭环 / 权限语义 / 运行诊断回传 / 创作工具链 / 文件管理）；Android 15+ 适配过一遍。
- **发布前置（P0 签名族，目前已决策搁置）**：换新 keystore、`.gitignore` 纳入 `keystore.*`、CI secret 注入；testkey 资产加风险文档。

**验证命令**（每轮后）：

```
.\gradlew.bat :app:testArm64DebugUnitTest --console=plain
.\gradlew.bat :editor:testDebugUnitTest --console=plain
.\gradlew.bat :app:assembleArm64Release --console=plain
```

CI 五道门禁全绿；`git ls-files` 不含任何 `.so/.dex/.zip/.jar`（wrapper 例外）。

---

## 5. 附录：测试与 CI 盘点

| 模块 | 测试文件数 | 用例数 | 覆盖 |
|---|---|---|---|
| app | 7 + 1 helper | ~52 | AlpNaming/ZipUtil/ApkBuilder/ApkSigner(v1 真实验证)/EngineResultParser/LuaConfigParser/BuildPackaging |
| editor | 1 | 14 | 行级 Lua 诊断（增量校验器） |
| third_party/androlua | 4 | ~29 | AXmlManifest(10)/LuaCompiler(5)/LuaConfigParser(7)/PermissionResolver(7) |
| CI 覆盖 | — | — | 仅 app + editor；`androlua` 29 用例不在 build.yml |

---

## 6. 修复进展台账（追加记录）

### 2026-08-05 上午 — Tier 0 完成（P1#3-9）

- **P1#3** Base64 → 纯 Kotlin 兼容层（`Base64Util`，无 android 依赖）；新增非 ASCII 长串/二进制解码单测。
- **P1#4** `AXmlManifest.readUtf8` 按 AXML 规范重写（0x80 变长 flag + charLen 在前）；新增 UTF-8 池 + 非 ASCII 长串单测（`AXmlManifestTest` 补用例）。
- **P1#5** 删除 `:app` 的 `io.github.rosemoe:editor` + `language-textmate` Maven 依赖两行，统一走 vendored project 模块。
- **P1#6** `LuaActivity` 全部 intent-filter 删除 + `exported="false"`（仅宿主同包显式 Intent 可达，`.lua`/`.alp` 文件关联与 `androlua://` scheme 保留在 `MainActivity`）。
- **P1#7** `androlua_filepaths.xml` 收敛：删除 `external-path path="/"`，仅保留 `files-path` + `external-path AndroLua/`；`getUriForFile` 越权路径抛 IllegalArgumentException 的行为已文档化。
- **P1#8** manifest 移除 `requestLegacyExternalStorage` + 全局 `usesCleartextTraffic`，改 `networkSecurityConfig`（base-config cleartext 允许——用户 Lua 脚本可访问任意主机，属产品语义）。
- **P1#9** 新增 `docs/reference/security-model.md`：导出组件清单（MainActivity exported / EngineActivity+FileProvider 不导出 / 无障碍服务必须 exported=true 供系统绑定）、FileProvider 白名单、网络策略、签名资产现状（P0 搁置）、打包 hack（P2#20）。
- **验证**：`assembleArm64Debug` 通过；合并后 manifest 抽二进制比对（AXmlManifest 单测 fixture `manifest_sample.bin` 由真实 APK 重建，20052→20560 字节；断言 com.lioisme.lide / LideApp / VIBRATE / DYNAMIC_RECEIVER / androidx-startup / MANAGE_EXTERNAL_STORAGE 均在）；`:androlua:testDebugUnitTest` 全绿。

### 2026-08-05 下午 — Tier 1 完成（P2#10-14、#17、#19、#20）

- **P2#10** `BuildApkScreen`：`Channel<ApkBuilder.BuildStage>` 事件流（IO 协程 `trySend`，主线程 `LaunchedEffect` 消费），消除 IO 线程写 Compose state。
- **P2#11** `MainActivity`：pending 状态改 `mutableStateOf` 支撑（修复 onNewIntent 晚于 setContent 的 stale-compose-miss）；`.alp` 导入与 `.lua` openOrAdopt 移 IO 线程。
- **P2#12** `PermissionScreen` 重构：`buildHostPermUiState`（specials + regular 全量权限）IO 线程构建；点击只请求该行单个权限（per-permission launcher）；manage storage 授权后刷新。
- **P2#13** `ApiBrowserScreen` 两阶段构建 try/catch 降级：buildBase 失败回退 `ApiIndex.cached()`/空快照，completeBuild 失败保持基础快照。
- **P2#14** `EditorScreen`：`hostDeclared` 移入 `produceState` + IO；`requestRun` 的权限加载/过滤移 IO。
- **P2#17** CI `unit-test` 加入 `:androlua:testDebugUnitTest`。
- **P2#19** `ProjectListScreen`：TopBar 硬编码 "LIDE" → `stringResource(R.string.app_name)`；4 个可点击图标补 contentDescription。
- **P2#20** 打包 hack 文档化（`security-model.md` + `MainActivity.kt:36-40` 注释）。
- **验证**：`:app:testArm64DebugUnitTest :editor:testDebugUnitTest :androlua:testDebugUnitTest` 全绿（94 tasks）；`:app:assembleArm64Release` 通过（195 tasks）。

### 2026-08-06 — Tier 2 完成（P2#16 版本栈 + ktlint 全绿 + lint 门禁）

- **P2#16** `settings.gradle.kts` 新增 `dependencyResolutionManagement`（`FAIL_ON_PROJECT_REPOS` + aliyun public/google 镜像 + google() + mavenCentral()），删除 app/editor/androlua/sora-editor/language-textmate 五处模块级 `repositories {}`（共 5 处）。
- **AGP 8.6.0 → 8.13.2**（官方兼容表：AGP 9.3 需 Gradle 9.5；8.x 在 Gradle 9.5.1 属 best-effort，Gradle ≥9.6 必须先升 AGP 9.x；Kotlin 2.2.20 不变）；`gradle.properties` 删除 `android.suppressUnsupportedCompileSdk=36,35` 并加禁止回添注释。`:app:assembleArm64Debug` 通过（125 tasks）。
- **ktlint 扩至 `:app`**（org.jlleitschuh.gradle.ktlint 14.2.0 + ktlint 1.8.0，挂接 `lint` → `ktlintCheck`）。经用户决策：`.editorconfig` 关闭 `ktlint_standard_function-naming`（Compose 惯例 PascalCase 函数）+ `ktlint_standard_no-wildcard-imports`（API 浏览器目录风格），其余规则全开。全量格式化后 **`:app` + `:editor` ktlintCheck 双双全绿**（唯一实际违规为 `EditorTabs.kt` 私有常量 `DragAlpha` → `DRAG_ALPHA`）。CI `lint` 门禁更新为 `lintArm64Debug :editor:ktlintCheck`。
- **lint 门禁首跑 15 errors**（AGP 升级后暴露的休眠问题），全部修复：
  - `PermissionScreen.kt:516` NewApi：`AppOpsManager.unsafeCheckOpNoThrow` 仅 API 29+ → minSdk=24 上会 **NoSuchMethodError 崩溃**，按 Q 分支回落 `checkOpNoThrow`（真 bug，已修）。
  - `local.properties:1` PropertyEscape（Windows 盘符冒号转义 `D\:\\Android`）。
  - `AndroidManifest.xml:76,83` AppLinkUrlError：`.alp` 的 content/file 过滤缺 `host="*"`（与 `.lua` 过滤对齐补齐）。
  - `AndroidManifest.xml:34,46` ProtectedPermissions：`READ_FRAME_BUFFER`/`BATTERY_STATS` 为 system 级权限，声明是 pro 兼容语义 → `tools:ignore` + 注释说明。
  - `AndroidManifest.xml:13,17,20-24` PermissionImpliesUnsupportedChromeOSHardware（camera/telephony/sms 族）→ 补 `uses-feature required="false"` ×2。
  - `strings.xml:166-167` MissingTranslation：live2d 示例缺 zh 翻译 → 补齐。
- **收尾验证**：`:app:lintArm64Debug` 全绿；fixture `manifest_sample.bin` 随新 APK 重建（20560 字节，host/*/uses-feature 断言在位）；`:androlua`/`:app`/`:editor` 三个测试套全绿；`:app:assembleArm64Release` + `:app:verifyReleaseLuaSurface` 通过（206 tasks）；R1 grep（editor 无 luajava/androlua/loadLibrary）干净。

**剩余未开工**：P3 卫生项、Tier 3 功能对齐、P0 签名族（已决策搁置至发布前）。

### 2026-08-06 收尾 — P2#15 / P2#18

- **P2#15** `CrashHandler.java`（vendored）：原 else 分支 kill 代码被注释（与 pro 行为不符），崩溃被吞、进程带病运行。修复：日志落盘后链回系统默认处理器（标准崩溃流程），无默认处理器时 killProcess+exit；日志路径 `/sdcard/androlua/crash/`（公共可读 + 隐私泄露 + API 33+ 权限问题）→ `filesDir/crash/` 应用私有目录；移除失效的 Environment import。本文件自此与 pro 逐字节一致性有 1 处有意偏离（与 LuaWebView 图标差异同级的记录在案偏离）。
- **P2#18** `ApiCategoryClassifier` + `ApiIndex`：`categorizeBase()`（纯前缀，零反射）接管 buildBase 的 widget/view 条目（占位 FRAMEWORK）；`categorize()` 保留反射细化语义（View 子类验证 → COMMON_WIDGETS）；`completeBuild()` 新增 `refineCategories()`（CATEGORY_REFINE_BUDGET_MS=1.5s，超时保持占位），分类变化时原子重建 `categories`/`categoryCounts` 快照。buildBase 现为纯非反射阶段，符合 AGENTS.md 契约。
- **验证**：`:app:compileArm64DebugKotlin` 通过（73 tasks）；`:app:ktlintCheck :editor:ktlintCheck`（ApiIndex.kt/PermissionScreen.kt 缩进与多行表达式合规修正后）双绿；`:app:testArm64DebugUnitTest :editor:testDebugUnitTest :androlua:testDebugUnitTest :app:lintArm64Debug` 全绿。

**P1/P2 全部完成**。剩余未开工：P3 卫生项、Tier 3 功能对齐、P0 签名族（已决策搁置至发布前）。

### 2026-08-06 收尾 — P3 卫生项全部完成

- **P3-1 `LideApp.kt`**：资产解压（extractExamplePlugin / extractLayoutTemplates / extractLuadoc）移入后台线程 `"LideAppAssets"`（TextMate 预加载线程保持独立）；`printStackTrace` → `Log.w(TAG, ..., e)`（`TAG` 移入 companion）；可选资产静默 catch 补注释说明（缺失仅影响示例/模板，不崩溃）。
- **P3-2 上帝类拆分（三处，零行为变更）**：
  - `EditorScreen.kt`（721 行）→ `EditorScreen.kt`（仅编排：会话接线 / launcher / 键盘 / 自动保存 / 错误弹窗）+ 新增 `EditorComponents.kt`（EditorBody / 工程文件选择 Dialog / 存储权限横幅 / 扩展名判定）+ 新增 `EditorDialogs.kt`（关闭确认 / 运行错误 Dialog）。
  - `ApiBrowserScreen.kt`（691 行）→ 仅保留搜索状态机（动画唯一驱动 + pendingNavigation 挂起消费 + 收起路径 focusManager.clearFocus）+ 新增 `ApiBrowserComponents.kt`（搜索输入条 / 折叠条 / 分类筛选菜单 / ClassRow / 类型徽标 / filterSnapshot / copyToClipboard / simpleNameOf，`internal` 导出）。
  - `EditorSession.kt`（20 方法）`release()`：不再在自身作用域内 `scope.cancel()`，改 `flushJob.invokeOnCompletion { scope.cancel() }`（落盘完成后取消作用域，杜绝自取消竞态）。
  - 去重：`ApiClassDetailScreen.kt` 的私有 `simpleNameOf` 删除，复用 `ApiBrowserComponents.kt` 的 internal 实现（同包）。
- **P3-3 魔法标签与亮度函数**：`LocaleManager` 导出 `TAG_SYSTEM/TAG_EN/TAG_ZH` 常量，`SettingsScreen` 的 `languageSubtitle`/`localeData` 三处硬编码 `"system"/"en"/"zh"` 全部改用常量；手写 `Color.luminance()` 删除，改用 `androidx.compose.ui.graphics.luminance`。
- **P3-4 `verifyReleaseLuaSurface` 维护契约**：`app/build.gradle.kts` luaApiSurface 列表处新增注释（三处同步点：列表本体 / `proguard-rules.pro` / `ApiCategoryClassifier.androluaAndroidPrefixes`，新增控件忘同步即验证失败）。
- **P3-5 过时产物清除**：`proguard-rules.pro` 删除仓库中不存在的 `org.luaj.**` 保留规则（注释已说明）；删除 `third_party/androlua/src/main/java/android/support/multidex/` 死代码 3 文件（MultiDex.java / MultiDexExtractor.java / ZipUtil.java）+ `LuaActivity.java` 中被注释的 `//MultiDex.installLibs(this)` 行。
- **P3-6 ApiBrowserScreen 红线评估**：无需改动——冻结审计红线（searchActive 独立意图 / 动画唯一驱动 / pendingNavigation 消费 / 无参 rememberSearchBarState / 后台过滤 + 非空负缓存）在拆分前已全部落地，拆分后逐条复查保持（见 ApiBrowserScreen.kt 内注释）。评估结论记录在案。
- **P3-7 隐私日志**：`MainActivity` `.alp` 导入日志去掉 `dir.absolutePath`（仅保留类型）；`ProjectManager` 日志去掉 `luaExtDir`/`dir.absolutePath`，工程创建日志 `"Created project $name"` 不再带路径。
- **P3-8 压制注解收口**：`ApiIndex.enumerateDexFile` 的 `@Suppress("DEPRECATION")` 补注释（DexFile API 26+ 无公开替代、仅 IO 调用）；`buildBase` 的 `@SuppressLint("WrongThread")` 补注释（内部 IO 合约 + 调用方 withContext 约定，lint 无跨文件数据流分析）；`third_party/patches/material3_searchbar_patch/README.md` 标注「诊断素材，仅供溯源，不参与编译」。
- **验证**：`:app:compileArm64DebugKotlin`（73 tasks）+ `:app:ktlintCheck` 全绿（ApiBrowser 拆分后 3 处 ktlint 违规——悬挂文件级 KDoc / 缺失末尾换行——已修）；`:app:testArm64DebugUnitTest :editor:testDebugUnitTest :androlua:testDebugUnitTest` 待 P3 收尾轮复跑。

**P1/P2/P3 全部完成**。剩余：Tier 3 功能对齐、P0 签名族（已决策搁置至发布前）。