# lide 与 AndroLua 解耦化 / 插件化架构审计报告

| 项目 | 内容 |
|------|------|
| 报告编号 | LIDE-AUDIT-2026-014 |
| 审计对象 | lide 全仓（`:app` / `:editor` / `:androlua` / `:sora-editor`）+ 运行链路 + 原生引擎 + 打包器 + 文档治理 |
| 审计日期 | 2026-08-06 |
| 审计方式 | 源码全量阅读 + 耦合面检索 + 平台机制核实（Android 官方文档 / AOSP） |
| 审计目标 | 评估「LIDE = 纯 IDE + androlua = 独立 APK 插件」的可行性与技术栈，给出大厂级选型结论与迁移路线 |
| 结论 | **可行，且当前架构已具备拆分的大部分前提**。推荐「① 同一签名族双 APK + ② AIDL/ContentProvider/Broadcast 三通道 IPC + ③ Intent/ActivityResult 无缝跳转（主方案）→ ④ `SurfaceControlViewHost` 真嵌入（API 30+ 增强）」组合栈；**不推荐**经典插件化框架（Shadow/Replugin 等）与 IME/无障碍注入 |

> 本文属**立项级研究报告**（需求定义 + 技术选型 + 迁移规划），不是实施工作单；落地排期建议另立任务文档（见 §9）。

---

## 1. 执行摘要（TL;DR）

1. **需求本质**：用户要求的是「IDE 与 androlua 完全分家」——即 **进程级故障隔离**（alp 崩溃/闪退/卡顿只影响插件、不影响 IDE）+ **插件可独立安装为 APK** + **IDE 内丝滑进入插件任意界面** + **alp 工程运行环境在插件侧**，四合一架构改造。
2. **现状耦合事实**：当前**完全不隔离**。单 APK、单进程、`LuaApplication` 是全局单例、`LuaActivity`/`LuaService`/无障碍服务全部注册在宿主 manifest、原生引擎（506 个 C 源码文件）+ 运行时脚本 + 打包器（Lua 编译依赖 `libluajava.so` 的 `LuaState.dump`）全部归属 `:app`。任何 alp 工程的段错误（历史事故：`lua_close(NULL)` SIGSEGV、GBK `NewStringUTF` SIGABRT）会直接杀死 IDE 进程。**崩溃隔离缺口是本次改造的第一动机。**
3. **「真·嵌入」受平台约束**：Google 官方唯一能「把另一个应用的 UI 渲染进本应用窗口」的公开通道是 `SurfaceControlViewHost`（API 30+，View 体系，触控/焦点/无障碍/ANR 均已工程化）；「跨应用 Activity Embedding」仅 API 33+ 且主要面向大屏/折叠，compact 手机上不可用。**结论：主方案 = 跨应用跳转 + 结果回传（系统级隔离天然成立）；真嵌入（SCVH）作为 API 30+ 的渐进增强，不做主链路依赖。**
4. **三个必须先决策的次级架构点**（§6）：`LuaCompiler` 打包链归属（引擎搬走后宿主仍需「Lua 编译字节码」能力）；`ApiIndex` API 浏览器的 dex 枚举数据源（`com.androlua.*` 类随运行时搬走后将从宿主 dex 消失，浏览器 AndroLua 分类会空）；`luaExtDir` 工程目录的双进程共享模型。
5. **推荐技术栈**：保持 Kotlin + AGP 双工程构建；协议层用 AIDL（受控双向）+ ContentProvider（宣示/发现）+ Broadcast（事件）；安全护栏用同一签名家族 + `signature` 级 permission；LIDE 经 `<queries>` 探测插件版本并做「未装 / 过期 / 损坏」三态引导。
6. **工作量形态**：五个 Phase（§8），Phase 1（模块解耦 + 双 APK 构建）与 Phase 2（协议与数据面打通）为核心主路径，约占总量 60%；Phase 3~5 为体验增强与分发铺路。建议先固化 Phase 0 基线（探针 878/956 + 全部门禁）再动工。

---

## 2. 现状审计（全量）

### 2.1 架构拓扑（实测，2026-08-06）

```
settings.gradle.kts：:app + :editor + :androlua(third_party/androlua) + :sora-editor(:editor/:language-textmate)
```

| 层 | 模块 | 包 | 落地形态 |
|----|------|----|----------|
| IDE 宿主 | `:app` | `com.lioisme.lide` | Compose UI、项目管理、运行拉起、权限、设置、打包器、API 浏览器、示例/插件导入 |
| 引擎运行时 | `:androlua` | `com.androlua` / `com.luajava` / `com.android.cglib` / `com.nirenr`（纯 Java 源码，402 文件）+ `com.lioisme.lide.androlua`（Kotlin 编排：AlpManager/EngineBridge/LuaCompiler/PermissionResolver/PluginManager/AXmlManifest） | **编译进 `:app` 主 DEX，与 IDE 同进程** |
| 编辑器 | `:editor` | `com.lioisme.lide.editor` | 纯 Kotlin 文本能力，不触碰原生引擎（R1 合规：`rg "luajava\|androlua\|loadLibrary" editor/src` 零命中） |
| 编辑器底座 | `:sora-editor` | `io.github.rosemoe.sora`（vendored 0.24.6，LGPL-2.1） | 编辑器核心 + TextMate |

**运行时模型：单进程。** 全仓 `android:process` 零命中；`EngineBridge → EngineActivity(extends LuaActivity)` 同进程 Activity 栈直启。

### 2.2 耦合面逐项清单（解耦影响面）

| # | 耦合点 | 证据 | 拆分后去向 | 备注 |
|---|--------|------|-----------|------|
| C1 | **Application 基类** | `LideApp : LuaApplication()`（LideApp.kt:10）；`LuaApplication` 持有静态单例 + 工作目录 + `extractLuaScripts`（MD5 版本化资产提取） | 插件侧保留 `LuaApplication`（含 CrashHandler）；IDE 侧改为普通 `Application` | `LideApp` 中的 `SettingsManager/LocaleManager/TextMate` 初始化留在 IDE；`bootstrapAssets` 按角色划分 |
| C2 | **全局单例读取（15 处）** | `LuaApplication.getInstance().luaExtDir` 直接读于 `MainActivity:127/181`、`ProjectManager:37/189`、`BuildApkScreen:300`、`ExamplesScreen:71`、`PluginScreen:63` 等 | 收敛为 IDE 侧 `EngineEnv` 配置接口，或经 IPC 向插件查询权威目录 | 移除 IDE 对「AndroLua 目录」的直接知识 |
| C3 | **引擎组件注册** | `app/AndroidManifest.xml:135-192` 注册 `com.androlua.LuaActivity/LuaActivityX` + `LuaService` + `LuaAccessibilityService` + `ScreenCaptureActivity` | 全部转入插件 manifest | 无障碍服务是插件固有权限面，必须在插件 APK |
| C4 | **原生引擎** | `app/src/main/cpp/` 506 文件（liblua/libluajava + 21 原生模块 + live2d），NDK `29.0.14206865`，由 `:app:externalNativeBuild`（ndk-build）构建进 IDE APK | 搬进插件 APK（`externalNativeBuild` 指向插件 `src/main/cpp`） | R5 红线不变：仍是源码形态不入库（实测 `git ls-files *.so/*.dex/*.zip` = 0，`*.jar`（排除 gradle wrapper）= 0） |
| C5 | **运行时 Lua 脚本 + 资产** | `assets/lua/*.lua`（31 个库 + runtime）经 `extractLuaScripts` 提取到 `filesDir/lua` | 归插件 | IDE 不再需要 `assets/lua` |
| C6 | **打包器（Lua 编译链）** | `LuaCompiler`（LuaState loadbuffer+dump）、`AXmlManifest`、`ApkBuilder/ApkSigner/V2Signer` | 见 §6.1 枢纽 1 | 打包依赖引擎能力，必须解决 |
| C7 | **R8 反射类面** | `app/build.gradle.kts:207-238` 的 `luaApiSurface` 门禁 + proguard keep 规则（com.androlua/com.luajava/com.nirenr 等） | 门禁与 keep 规则迁入插件工程；IDE 的 keep 面大幅收窄（方法数/体积双降） | 防 R8 误删回归的机制必须平移，不能丢 |
| C8 | **API 浏览器数据源** | `ApiIndex.kt:159` 以 `File(context.applicationInfo.sourceDir)` 枚举**宿主自己**的 dex 类名；`ApiCategory.kt:19` 静态表含 `com.androlua.*` 白名单 | 见 §6.2 枢纽 2 | 拆走后宿主 dex 无 androlua 类，浏览器分类会空 |
| C9 | **Intent 协议** | `EngineBridge.kt:27` 用 `setClassName(context.packageName, "...engine.EngineActivity")`；manifest intent-filter：`androlua://` 双 host + `.alp/.lua` VIEW/EDIT | 引擎 intent 改为「插件包名 + 插件注册的 exported 组件」；`androlua://` 路由决策可留在 IDE 并转发插件 | `ActivityResult` 通道保持 |
| C10 | **`import.lua` / `activity` 全局** | `LuaActivity.initLua` 注册 `activity`/`service` 全局；`import.lua:13` `local luacontext = activity or service` | 插件 APK 运行时不改动任何此机制（探针 878/956 兼容目标） | 引擎侧零源码变更，这是兼容性的最大保障 |
| C11 | **IDE 侧辅助资产** | `assets/plugin/`、`assets/layouts/`、`assets/luadoc/`、`assets/examples/*.alp` | `layouts/luadoc` 属编辑器辅助功能留在 IDE；示例 .alp 与 plugin 示例按角色迁移 | 按「引擎相关 / 编辑器相关」划分 |

### 2.3 崩溃隔离缺口（为什么现在 alp 崩溃会杀 IDE）

```
（单进程）alp 运行 → libluajava 原生线程 / 原生模块 → SIGSEGV 或 SIGABRT / 原生 OOM
    └── 原生层错误不可被 Java try/catch 捕获 → 进程死亡 → IDE 一起死
```

- 历史证据：`EDITOR_CRASH_ANALYSIS_REPORT` 中 `lua_close(NULL)` SIGSEGV 与 GBK `NewStringUTF` SIGABRT 双崩溃向量；W^X dex 内存加载修复（D-09）只解决了加载方式，不改变「同进程 = 同故障域」的本质。
- **ANR 语义**：alp 脚本在主线程死循环/阻塞 → 插件进程 `applicationNotResponding` → 同进程的 IDE 一并 ANR。
- **结论**：任何「同进程」方案（含经典插件化框架的 dex 注入）都无法满足崩溃/ANR 隔离——**独立进程是唯一硬门槛**。

### 2.4 资产 / 构建审计

| 项 | 实测 |
|----|------|
| 模块数 | 5（`:app` + `:androlua` + `:editor` + `:sora-editor:editor` + `:sora-editor:language-textmate`） |
| 引擎源码 | `app/src/main/cpp/` 506 文件（含 live2d），ndk-build 由 `externalNativeBuild` 驱动 |
| 二进制红线 R5 | 合规（见 C4） |
| 边界红线 R1 | 合规（`:editor` 零引擎引用） |
| 权限面 | `app/AndroidManifest.xml:8-67` 声明 60+ 权限（pro 兼容）→ 拆走后随插件转移，IDE 收窄 |
| min/target | minSdk 24 / targetSdk 36 / compileSdk 36；ABI 变体 `x64`/`arm64` |
| 构建命令 | `.\gradlew.bat :app:compileArm64DebugKotlin` / `:app:assembleArm64Debug`；插件建成后对应 `:plugin:assemble*` |
| 单测 | `:app` / `:editor` / `:androlua` 三组 JVM 单测（打包链 AXml/LuaCompiler/PermissionResolver 均有覆盖） |

### 2.5 文档与决策现状（相关既有产物）

- `D-02`：预编译 .so + Java 源码进主 DEX、移除 engine.dex。本次改造实质是 **D-02 的一级反转**——运行时从主 DEX 出站，但不是回到动态加载，而是以独立 APK 承载。
- `D-08`：单进程口径（AGENTS.md R2）。本文若被采纳，R2 需修订为「引擎运行于独立插件 APK（独立进程），IDE 经 IPC/Intent 驱动」。
- `XPOSED_MODULE_FEASIBILITY_REPORT`（2026-07-31）：已验证「libluajava 静态链可注入任意进程、headless LuaContext、精简骨架 dex」——其 `HeadlessLuaContext` 思路可直接复用于本方案的「插件进程内无 Activity 启动 Lua」与 SCVH 嵌入壳。
- **既有「插件」概念澄清（易混淆点）**：现行 `PluginScreen` + `PluginManager`（读 `plugin/plugin.json` 或 init.lua 的目录式 Lua 插件，`AlpKind.PLUGIN`）与「独立 APK 插件」是**两种异物**。新入口建议命名「运行时 / 引擎插件」，不占用 `plugins` 路由，避免导航语义冲突。

---

## 3. 目标架构定义（需求解构）

### 3.1 诉求 → 架构级约束

| # | 诉求（用户原话归纳） | 对应的架构级约束 |
|---|---------------------|------------------|
| R-A | LIDE 是**纯粹的 IDE** | `:app` 删除一切引擎运行时：不再依赖 `com.androlua`/`com.luajava`/`libluajava.so`；仅保留对插件索引的远程只读；R1~R5 收紧为「IDE 进程零原生引擎面」 |
| R-B | androlua 是**独立 APK 插件** | 插件自持完整引擎（.so、manifest、权限、CrashHandler、luaExtDir）；独立安装/卸载/升级，不与 IDE 绑定发布 |
| R-C | 安装插件后 LIDE 内可**丝滑进入任何界面** | 两块：① 跨应用跳转转场化 + 结果回传闭环；②（API 30+）SCVH 真嵌入为 LIDE 窗口内子 Surface |
| R-D | alp 运行**只在插件进程** | 物理强制：不同 APK → 不同进程 → 脚本崩溃/原生崩溃/ANR 只杀插件进程，IDE 独立存活 |
| R-E | IDE 感知插件的存在与健康 | `<queries>` + ContentProvider 宣示 + AIDL 心跳 + 版本协商；未装/过期给引导 UI |

### 3.2 核心矛盾：跨进程「嵌入」的物理约束（关键事实）

Android 的进程隔离是系统级、不可绕过的：**另一个进程的 Activity/View 无法直接加入你的 ViewGroup**。官方跨应用 UI 呈现通道只有：

| 通道 | API | 形式 | 对「丝滑进入任何界面」 |
|------|-----|------|------------------------|
| **启动别的 Activity（Intent）** | 全版本 | 任务切换到插件进程的全屏 Activity | ✅ 主方案（转场 + 返回回传已满足） |
| **`SurfaceControlViewHost`** | API 30+ | 插件把 View 层次渲染进 Surface，`SurfacePackage` 经 Binder 传给宿主 `SurfaceView`，宿主显示 + 输入转移 | ✅ 真嵌入（推荐增强；触控/焦点/ANR 有官方机制） |
| **跨应用 Activity Embedding** | API 33+ | 宿主把插件 Activity 嵌入宿主 Task 分屏容器 | ⚠️ 仅大屏/折叠；compact 手机不可用 |
| **RemoteViews** | 全版本 | 固定组件集（通知/桌面） | ❌ 通用 UI 不适用 |
| **IME / 无障碍覆盖注入** | 特性服务 | 自定义窗口（需特殊权限 + 用户手动开启） | ❌ 权限与体验代价高 |

**选型结论**：跨应用跳转做「主交互」，SCVH 做「增强（如插件调试窗口/控制台/API 参考内嵌）」，Embedding 留为平板增强；**不引入任何第三方插件化框架**（见 §4）。

---

## 4. 技术栈选型评估

### 4.1 决策口径

以四项衡量：**崩溃/ANR 隔离度（硬门槛）、开发成本、兼容面（min/target/OEM）、生态/分发**。

### 方案 A —— 同一签名族双 APK + 显式 Intent/ActivityResult 跳转

**做法**：独立插件工程产出独立 APK：含 androlua 原版 manifest 注册（LuaActivity 等 + 无障碍 + 60+ 权限）、引擎源码（ndk-build 打进插件）、`assets/lua`、`LuaApplication`；LIDE 经 `PackageManager` 解析插件包名 → 显式 Intent + `registerForActivityResult` 拉起。工程数据经共享目录/FileProvider/AIDL 共享（§6.3）。回调沿用现有 `EngineActivity` 的 `EXTRA_OUTPUT`/`EXTRA_ERROR` 协议（200KB 截断防 Binder 溢出），`EngineResultParser` 原样复用。

- 两工程共用同一 `keystore.properties` 签名 → 互信（`signature` 级 permission）。
- LIDE manifest 加 `<queries>` 声明插件包名（Package Visibility）。

**评价**：隔离 ✅（系统级）；复杂度低（协议简单）；兼容 ✅（minSdk 24 起全平台）；丝滑 ✅（转场复用现有 `slide_*` 动画；返回闭环与现状 `EngineActivity` 完全同构）。

### 方案 B —— 双 APK + `SurfaceControlViewHost` 真嵌入（API 30+）

**做法**：插件导出「嵌入式渲染服务/ContentProvider」：IDE 侧 `SurfaceView.getHostToken()` → 传插件 → 插件把运行页 View 树挂到 SCVH → `SurfacePackage` 经 Binder 回传 → IDE `SurfaceView.setChildSurfacePackage()` + `InputTransferToken`（API 31+ 触控/焦点/ANR 关联）。插件侧需「嵌入壳」（headless `LuaContext` + 渲染 View），可复用 Xposed 报告的 `HeadlessLuaContext` 思路。

**评价**：真「嵌在 IDE 窗口内」，但：仅 API 30+（低版本降级为方案 A）；嵌的是 View 树而非 Activity 生命周期；需做尺寸/配置同步；Surface 生命周期与 IPC 排查成本最高。

**结论**：作为 **v2 增强**，不阻塞主链路。

### 方案 C —— 跨应用 Activity Embedding（API 33+ / 大屏）

系统支持，但核心约束过大：仅 API 33+；官方明确要求宿主为 task root owner、主要在大屏/折叠设备生效、插件必须显式 opt-in（`allowUntrustedActivityEmbedding` 或 `knownActivityCerts` 证书白名单）。compact 手机（本产品默认形态）不可用。

**结论**：不选为主路线；若未来「折叠屏/可嵌入 IDE 界面」进入产品线，可经 Jetpack WindowManager 的 `SplitPairRule` 作为增强复用。

### 方案 D —— 单 APK 双进程（`android:process=":lua"`）

不独立安装，仍是同一 APK，但引擎组件跑在 `:lua` 独立进程：引擎进程崩溃/ANR 不影响 IDE 进程（隔离收益兑现 90%+），改动量约为双 APK 的 40%~60%。

**局限**：不是「独立插件 APK」（不满足 R-B 的产品语义）；同 uid 双进程不是 uid 级沙箱，仍需对权限与数据访问合理设防。

**定位**：过渡形态或兼容分支。若「独立插件 APK 分发」不是 v1 刚需，可先以 D 兑现隔离收益，协议层设计为「进程无关」（同进程 IPC 与跨进程 IPC 同构），后续平滑迁移到 A/B。

### 方案 E —— 经典插件化框架 / 注入方案（否决）

| 方案 | 否决理由 |
|------|----------|
| Shadow / RePlugin / VirtualApp / DroidPlugin | 设计目标是「未安装的插件 dex 由宿主同进程加载」，**不满足独立安装 APK + 进程隔离**；还引入组件代理、Manifest 合并、R8/类加载兼容等大量复杂度 |
| IME（输入法服务）伪装嵌入 | 必须被用户设为系统输入法；窗口定位限定软键盘区；被系统/应用商店按输入法对待 |
| 无障碍服务注入覆盖 | 权限等级高（需用户手动开启）、Play 审核风险大、稳定性与「丝滑」不可兼得 |
| VNC/投屏/截图流 | 非官方、延迟与触控回放体验差、兼容脆弱 |

---

## 5. 目标架构（推荐组合）

```
┌────────────────────────────── LIDE（com.lioisme.lide）──────────────────────────────┐
│ MainActivity（Compose IDE）                                                          │
│  ├─ 运行时插件中心（<queries> 探测 + 未装/过期/损坏三态状态机）                       │
│  ├─ RunController：registerForActivityResult → 插件 RunActivity → 结果回传            │
│  ├─ ApiIndex 适配器：数据源 = 插件索引快照（§6.2）                                     │
│  └─ （可选）EmbeddedRunPanel：AndroidView(SurfaceView) ← SurfacePackage（API 30+）   │
└──────────────────────────────────┬───────────────────────────────────────────────────┘
           │ AIDL（受控双工）· ContentProvider（宣示）· Broadcast（事件）· SurfacePackage
           ▼
┌─────────────────────────── 独立插件 APK（com.lioisme.lide.runtime 族）────────────────┐
│ LuaApplication + CrashHandler（插件域）                                               │
│ ├─ RunActivity（extends LuaActivity，exported）— 全屏运行，结果回传                    │
│ ├─ LuaEmbedActivity（headless LuaContext + SCVH 渲染壳，API 30+）                    │
│ ├─ 引擎：libluajava/lib*.so（ndk-build）+ assets/lua + 21 原生模块                     │
│ ├─ 60+ 权限 + FileProvider + LuaAccessibilityService                                  │
│ └─ ILuaEngineService（AIDL server）+ 打包委托服务（LuaCompiler/签名）                  │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

### 5.1 模块化与构建改造（Gradle）

1. **新插件工程**（建议 `third_party/androlua/` 就地晋升为 `com.android.application`，或新建 `runtime/` 目录收纳）：
   - `build.gradle.kts` 由 `library → application`；`ndkBuild` 指向引擎源码（路径可保持 `src/main/cpp` 或集中管理）；
   - 签名配置与 `:app` 共用同一 `keystore.properties`（`signingConfig` 参数化）；
   - `luaApiSurface` R8 门禁任务（§2.2 C7）整体迁移到本工程；
   - ABI 变体（x64/arm64）与 `:app` 对齐，保证模拟器/真机双端可用。

2. **`:app` 侧收窄**：
   - `LideApp extends Application`（去掉 `LuaApplication` 继承）；
   - 15 处 `LuaApplication.getInstance()` 收敛为 `EngineEnv` 接口（配置注入，R4 精神）；
   - `EngineBridge/RunEngineLauncher` 改为「解析插件包 → 构造 Intent → ActivityResult」；
   - `AndroidManifest` 收敛：删除引擎组件注册与 60+ 权限（保留编辑器所需最小面）；`androlua://` 意图保留做路由并转发插件；
   - `LuaCompiler` 相关代码迁出（§6.1）。

3. **新建 `:protocol`（IDE 与插件共享的轻量定义模块）**：AIDL 接口、Bundle key schema、常量、数据类。纯接口，零引擎依赖，两 APK 编译期复用。

### 5.2 跨进程协议（IPC）

| 通道 | 用途 | 关键点 |
|------|------|--------|
| **AIDL（bound service）** | 受控双工：能力查询 / 心跳 / 打包委托 / 索引快照 | 插件暴露 `ILuaEngineService`；LIDE 侧 `ILideHost` 回调（便于插件回传进度/事件） |
| **ContentProvider** | 宣示「插件已装 + 版本 + 能力清单」，绑定前快速探测 | `<provider android:exported="true" android:permission="com.lioisme.lide.signature"/>`（signature 保护） |
| **Broadcast（显式 + signature permission）** | 单向通知：运行结束 / 文件变更 / 插件升级 | 全显式包名，`android:exported="true"` + signature 权限双保险 |
| **ActivityResult** | UI 主交互：`RunActivity` 全屏运行 + 返回（输出/错误回传） | 复刻 `EngineActivity` 的 `sendMsg/sendError/finish` 回传协议（200KB 截断） |
| **共享存储** | 工程数据交换（§6.3） | — |

### 5.3 LIDE ↔ 插件数据面（工程存储模型）

**现状**：`luaExtDir` = `Environment.getExternalStorageDirectory()/AndroLua`（含 SD 回退）；引擎以 `projectDir` 绝对路径运行工程目录（`main.lua` + `init.lua` + `libs/`）。

| 模型 | 做法 | 优点 | 缺点 |
|------|------|------|------|
| **① 共享公共目录（推荐启动）** | `project`/`plugin` 目录保持 `sdcard/AndroLua` 固定位置，两端以绝对路径读写；`MANAGE_EXTERNAL_STORAGE` 授权统一引导一次 | 零协议成本；工程文件实体唯一；pro 生态语义不变（`luaExtDir=sdcard/AndroLua`） | 依赖公共区权限稳定性；同存文件需约定「运行锁」防并发写 |
| **② 插件托管 + LIDE 经 IPC/Provider 虚拟树** | 工程收进插件私有 `filesDir`，LIDE 经 IPC 罗列/读写 | 隐私更强、隔离更深 | 全部文件 IO 跨进程，EditorScreen/文件树重构成本高 |
| **③ SAF 文档目录** | `ACTION_OPEN_DOCUMENT_TREE` 双向授权，经 DocumentsProvider 通路 | 系统级、无存储权限 | 编辑器读路径改 Uri，改动最大 |

**推荐**：「① → ② → ③」演进路径。启动期用 ①（成本最低、兼容 pro）；运行锁 `run.lock` 约定：插件进程持有期间 LIDE 只读提示。odex/lib 等私有目录本就归插件私有区，只共享工程实体目录。

### 5.4 无缝衔接（UX）

- **转场**：复用现有 `slide_in_right/slide_out_left` 动画资源（`RunEngineLauncher.applyLaunchTransition` 逻辑平移）；插件 `finish()` 时回传结果 + 播放返回转场。
- **回宿主闭环**：与 `EngineActivity` 现协议完全一致（`EXTRA_OUTPUT` + `EXTRA_ERROR` + 200KB 截断），`EngineResultParser` 原样复用：失败 → 底部对话框 + 跳到出错行 + nil global 推荐插入 import（现状能力不变）。
- **三态引导**：`queries` 探测 → 未装（引导安装插件 APK）/ 过期（提示升级）/ 损坏（提示重装）；插件版本与 IDE 兼容 marker（ContentProvider 暴露 versionName）。
- **IDE 内嵌（可选 v2）**：`EmbeddedRunPanel`（Compose `AndroidView(SurfaceView)`）→ SCVH；插件侧 `LuaEmbedActivity`（headless + 渲染 View 挂 SCVH）——复用 Xposed 报告 `HeadlessLuaContext` 思路。

---

## 6. 三个枢纽决策（必须先定）

### 6.1 枢纽 1：打包器归属（引擎在插件，IDE 仍要「打包 → APK」）

现状打包链 `BuildApkScreen → LuaCompiler(LuaState.loadbuffer+dump) → AXmlManifest → ApkBuilder → ApkSigner`——**`LuaCompiler` 依赖引擎 LuaState**，拆分后宿主无引擎。

| 方案 | 做法 | 评价 |
|------|------|------|
| ① 打包委托给插件（推荐） | LIDE 打包按钮 → AIDL 调插件「打包请求（工程路径）→ 插件内 LuaState 编译 → 产物写插件文件 → 签名 → FileProvider 回传」 | 干净符合 R2 边界；`BuildApkScreen` UI 留在 IDE；AIDL 新增打包接口 |
| ② IDE 内置纯 Java「luac 编译器」 | 把 Lua 5.3 语法编译（字节码 dump）移植为纯 Java | 独立维护一份与引擎魔改字节码兼容的编译器，成本高，且违背「IDE 零引擎」目标。否决 |
| ③ 打包作用域移到插件 UI | 打包页/产物管理收到插件，IDE 只提供入口 Intent | 最符合「纯 IDE」终极形态；用户需在插件内操作 |

**推荐：v1 走 ①，长期演进为 ③。**

### 6.2 枢纽 2：API 浏览器数据源

- `ApiIndex` 构建数据源 = `applicationInfo.sourceDir`（宿主 APK dex）+ 工程 `libs/`（`ApiIndex.kt:159`）。拆完后 `com.androlua` 等类不在宿主 dex。
- **落点（推荐分两步）**：
  1. 解析插件包 `PackageManager.getApplicationInfo(插件包).sourceDir` → 用现有 `enumerateDexFile` 对插件 dex 枚举（同 `DexFile` API，IO 线程，异常降级）；
  2. 演进：插件启动时构建「API 索引快照」（dexNames + 静态表）经 AIDL 下发，LIDE 缓存——更快且与插件版本强一致。
- 反射 `loadClassRef` 的 ClassLoader 需能解析插件类 → `DexClassLoader(插件路径, ...)`；「仅对存在的类 forName」（架构审计 A-1）规则不变。
- `luaApiSurface` R8 门禁随插件工程迁移（§2.2 C7）。

### 6.3 枢纽 3：进程形态与演进（D → A/B）

- 若「独立插件 APK」是硬需求（用户原话即为此），直接走 A/B；
- 若先验证隔离价值，Phase 1 可先用 `android:process=":lua"`（方案 D）跑通协议（协议设计为进程无关），Phase 2 再拆为独立 APK；
- **协议层从第一天起按「跨进程」设计**（Binder/Intent 天然跨进程），避免二次返工。

---

## 7. 风险与缓解

| # | 风险 | 影响 | 缓解 |
|---|------|------|------|
| 1 | 插件未装/损坏/过期 → 运行与打包不可用 | 主流程功能缺失 | `queries` + ContentProvider 探测 + 三态引导 + 「仅编辑器」降级模式（可编辑不可运行） |
| 2 | SCVH 嵌入受 API 30 下限限制 | v2 功能在旧设备不可用 | `@ChecksApi(30)` + 低版本降级为方案 A（功能一致） |
| 3 | 双进程同写工程文件 → 数据损坏 | 工程损坏 | 运行锁 `run.lock`（插件持有）+ IDE 编辑时只读提示 + IPC「占用/空闲」查询 |
| 4 | 插件 APK 体积/权限膨胀（引擎 + 60 权限） | 安装体积、审核关注 | 引擎体积基线 ~22MB 可接受；60 项权限仅声明不运行时申请；分发策略单独评审（§8 Phase 4） |
| 5 | R8 keep 面分割漏洞（Lua 反射类在插件） | 插件 Release 运行时类缺失 | `luaApiSurface` 门禁任务迁移到插件模块并保持「构建时自动查 mapping」 |
| 6 | luajava 类从 IDE 进程消失 → API 浏览器/工程 libs 反射链路变化 | 浏览器功能退化 | §6.2 落点；工程 libs 的 dex 枚举仍走 `DexFile` 路径（与现状一致） |
| 7 | 双 APK 签名不一致（各自 keystore） | 安全信任链失效 | 两工程强制共用签名配置（CI 参数化）+ 校验任务 |
| 8 | 两端对工程路径的理解不一致 | 目录错位 | §6.3 模型 ① 同一 `sdcard/AndroLua`；插件为权威路径源，IDE 经 IPC 获取根路径再拼相对路径 |
| 9 | 引擎卡主线程 → 插件 ANR（虽不影响 IDE） | 插件体验差 | 插件侧运行页自身保持可取消（Stop 按钮杀进程/停脚本）；必要时提供「强制停止」入口 |
| 10 | `third_party/androlua` 从 library 晋升 application 的维护影响 | 迁移期混乱 | 保持目录结构 + README 登记变更说明 |
| 11 | 文档决策历史反转（D-02/D-08） | 事实冲突 | DECISIONS.md 登记反转记录（循现有格式） |
| 12 | 双 APK 升级节奏不一致（IDE 新版要求插件新版） | 功能不匹配 | 兼容 marker（min/max 插件版本）由 ContentProvider 暴露，IDE 校验并引导 |

---

## 8. 迁移路线图（Phase，可验证）

### Phase 0：基线固化
- 冻结当前线：`.\gradlew.bat :app:assembleArm64Debug` + 三组单测全绿；R1/R5 门禁通过。
- 留存基线证据：探针 878/956 输出、demo 工程运行日志（供拆后对比）。

### Phase 1：解耦 / 插件 APK 成型（核心大项）
- 插件工程 `application` 化：引擎源码、`assets/lua`、Java 运行时、Kotlin 编排（EngineBridge/LuaCompiler/PermissionResolver 等）全部迁入；
- `:app` 移除 `:androlua` 依赖；`LideApp` 改普通 `Application`；manifest 收敛（引擎组件 + 60+ 权限迁出）；
- 插件 APK 可独立构建 + `adb install` + 自行运行 demo。
- **验收**：① 双 APK 安装成功；② `dexdump` 抽查 `:app` dex 无 `com.androlua` 类；③ LIDE 作为纯编辑器可开/存/搜文件；④ LIDE 进程无引擎线程。

### Phase 2：协议 + 数据面打通（核心大项）
- `:protocol` 模块（AIDL schema + 常量）；
- 插件 `ILuaEngineService` + 宣示 Provider + 事件 Broadcast；
- LIDE `RunController`：探测 → 绑定 → Intent 启动 → ActivityResult 回收 → `EngineResultParser`；
- 工程目录模型 ① + 运行锁。
- **验收**：运行链路全闭环（运行 → 插件页 → 输出回宿主 → 跳错行）；`androlua://` 路由生效；三态引导 UI 可用。

### Phase 3：体验增强（可并行）
- SCVH 嵌入壳（API 30+ 检测 + 降级 A）；
- 插件内嵌调试输出面板；API 浏览器索引快照（§6.2）；
- 模拟器/真机 E2E 矩阵（x64/arm64 × Debug/Release）。

### Phase 4：分发与合规
- 插件独立签名与发布；IDE/插件版本协商与升级引导；
- 插件 APK 安装分发链路（本地 APK 文件 / 应用商店）；
- Play 审核面评估（插件 60+ 权限的 manifest 声明策略）；
- CI 双 APK 产物 + 签名门禁。

### Phase 5：长期演进
- 打包器最终形态（插件内打包 + 跨应用展示，§6.1-③）；
- API 浏览器数据面最终化（索引快照 IPC 下发 + 缓存衰减）；
- 文档/DECISIONS/白皮书/AGENTS.md 治理收口（§9）。

---

## 9. 文档与治理动作（报告采纳后要做）

1. **AGENTS.md**：
   - R1 追加子句：`:app` 同样禁止 `com.androlua`/`com.luajava`/`System.loadLibrary`（门禁 `rg "luajava\|androlua\|loadLibrary" app/src editor/src` 双模块零命中）；
   - R2 修订：引擎运行于独立插件 APK（独立进程），宿主经 IPC/Intent 驱动；
   - R4 补充「宿主状态一律经参数/接口注入，新增跨进程一律走 `:protocol`」。
2. **DECISIONS.md**：登记 D-18（本报告采纳，含 D-02/D-08 反转注记）。
3. **kb/README.md §2 矩阵 + TASKS.md**：登记本文档（planned 分类）。
4. **导航命名**：新入口「运行时 / 引擎插件」，不占用现有 `plugins` 路由。

---

## 10. 验收标准（可执行、证据链）

| 验收项 | 判定方法 |
|--------|----------|
| ① IDE 纯净性 | `rg "luajava\|androlua\|loadLibrary" app/src editor/src` 零命中；`dexdump` 抽查宿主 APK 无 androlua 类；`ps -A \| grep lide` 只有 IDE 进程 |
| ② 插件独立性 | 插件 APK 单独安装/卸载/升级不破坏 IDE；IDE 卸载后插件仍可独立运行 demo |
| ③ 无缝跳转 | LIDE 内点「运行」→ 转场进入插件全屏页 → 返回宿主 → 输出/错误结果正确回传（含 200KB 截断） |
| ④ 崩溃隔离（硬指标） | alp 脚本 `os.exit()` / 原生崩溃（`am crash <插件uid>`）/ 主线程死循环 → 插件进程被杀或 ANR，**IDE 进程存活且可继续编辑**（`am kill` 验证 + logcat 采集） |
| ⑤ 兼容性回归 | 探针 878/956 在插件进程全量复跑（引擎零源码变更前提下应持平）；demo/testjson/kleinbottle/DemoProject 双端 E2E |
| ⑥ 三态引导 | 未装/过期/损坏三态下 UI 引导正确，安装完成后无需重启 IDE 即可运行 |
| ⑦ 门禁全绿 | R1/R5 校验 + 三组单测 + 插件 R8 luaApiSurface 门禁 + ktlint |

---

## 附录 A：证据索引（file → 位置）

| 主题 | 位置 |
|------|------|
| LideApp 继承 LuaApplication | `app/src/main/kotlin/com/lioisme/lide/LideApp.kt:10` |
| 单例依赖 15 处 | `MainActivity.kt:127,181`、`ProjectManager.kt:37,189`、`BuildApkScreen.kt:300-301`、`ExamplesScreen.kt:71`、`PluginScreen.kt:63` 等 |
| 引擎组件注册 | `app/src/main/AndroidManifest.xml:135-192` |
| 引擎运行链路 | `app/src/main/java/com/lioisme/lide/engine/EngineActivity.java:19`、`app/src/main/kotlin/com/lioisme/lide/engine/RunEngineLauncher.kt`、`third_party/androlua/src/main/kotlin/com/lioisme/lide/androlua/engine/EngineBridge.kt:27` |
| 原生引擎源码 | `app/src/main/cpp/`（Android.mk / Application.mk / live2d），NDK 29.0.14206865 |
| 打包链 | `app/src/main/kotlin/com/lioisme/lide/build/ApkBuilder.kt`、`third_party/androlua/.../LuaCompiler.kt`、`ui/screen/BuildApkScreen.kt:300` |
| API 浏览器 dex 数据源 | `app/src/main/kotlin/com/lioisme/lide/apibrowser/ApiIndex.kt:159,313` |
| R8 luaApiSurface 门禁 | `app/build.gradle.kts:207-238` |
| 权限清单 60+ | `app/src/main/AndroidManifest.xml:8-67` |
| Lua 插件（现有概念） | `third_party/androlua/.../PluginManager.kt`、`ui/screen/PluginScreen.kt` |
| R1 边界合规 | `editor/build.gradle.kts`（无引擎依赖）、AGENTS.md R1 |
| 现有回传协议 | `EngineBridge.kt:EXTRA_PROJECT_DIR/MAIN_FILE/ARG`、`EngineActivity.java:EXTRA_OUTPUT/ERROR/OUTPUT_LIMIT` |
| 可复用 headless 思路 | `docs/planned/XPOSED_MODULE_FEASIBILITY_REPORT.md`（HeadlessLuaContext） |

## 附录 B：术语

| 术语 | 含义 |
|------|------|
| SCVH | `SurfaceControlViewHost`：跨进程把 View 层次渲染进宿主 Surface 的系统组件 |
| SurfacePackage | 跨进程传递的渲染 Surface 权柄（Parcelable） |
| 跨应用 Activity Embedding | 把另一 APK 的 Activity 嵌入宿主 Task 分屏的系统能力（API 33+） |
| AIDL | Android Interface Definition Language，跨进程接口定义 |
| 运行时插件（本报告） | 承载引擎的独立插件 APK（与既有「Lua 插件目录」概念区分） |
| 三态引导 | 插件未装 / 过期 / 损坏 三种状态的检测与引导 UX |

## 附录 C：最终评审结论

LIDE 走向「**纯 IDE + 独立引擎插件 APK**」在技术上成立，且当前 R1~R5 治理（`:editor` 边界、R5 源码红线、D-09 W^X 差异、Xposed 报告 headless 能力）已铺好一半地基。**推荐技术栈 = 同一签名双 APK（`:app` 纯净 IDE + 插件 APK）+ AIDL/ContentProvider/Broadcast 三通道 IPC + Intent/ActivityResult 主运行 + SCVH 真嵌入渐进增强**；工程存储用共享 `sdcard/AndroLua` 模型 ① 起步；API 浏览器与打包链两个枢纽分别按 §6.2 / §6.1 的方案推进；`LuaApplication`/`LuaActivity`/`import.lua` 等引擎机制在插件侧零改动（兼容性最大保障）。建议以 Phase 0 基线 → Phase 1/2 主链路快速兑现「运行崩溃 → 仅插件阵亡、IDE 存活」的 E2E 验收（§10-④），再序贯补上嵌入、分发与治理收口。若不接受双 APK 分发形态、只想先验证隔离价值，可用 `android:process=":lua"`（方案 D）作为过渡第一步，但协议层按跨进程设计，目标形态以本报告推荐为准。
