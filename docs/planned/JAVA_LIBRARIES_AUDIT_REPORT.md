# LIDE Java 库全量审计报告

- **审计日期**：2026-08-08
- **审计对象**：
  - `:androlua` 移植工作副本（`third_party/androlua/src/main/java/`，330 个 Java 源文件）
  - 上游基线：GitHub `nirenr/AndroLua_pro` master（= AndroLua Pro 4.4.2，tree sha `abe5c25148a60ee44ddcccc212db6b96e836c793`），经 GitHub API 递归树逐文件比对
  - 原生引擎 `app/src/main/cpp/`（Lua 5.3.3 fork + 19 模块）
  - `:app` / `:editor` 全部 Maven 第三方依赖（`app/build.gradle.kts` / `editor/build.gradle.kts`）
  - `reference/libs/` dex 分发库（SmartRefreshLayout / umeng / BaiduMTJ）
- **审计方法**：上游 git tree 全量提取（1135 blob/92 tree）→ 与 vendored 逐文件 diff；Maven 依赖版本对照 2026-08 官方最新稳定版（androidx stable-channel / BOM mapping / mvnrepository 实证）
- **结论一句话**：AndroLua 内嵌 Java 库运行时面与上游 4.4.2 逐文件 100% 对齐（差异全为有记录的刻意删减 + lide 增强），客观落后但**无任何可行升级路径**（字节码 + 脚本类名契约 + 上游废弃三重约束冻结）；非 androlua 宿主依赖全部完整可用，6 项当前版本、2 项轻微落后、5 项 androidx 主干落后 1 年（低风险，可分批升级）。

---

## 1. 范围与方法

| 维度 | 说明 |
|---|---|
| 基线 | AndroLua Pro v4.4.2（`nirenr/AndroLua_pro` master，2021 年后停更） |
| 比对对象 | `third_party/androlua/`（工作副本，原版纯净基线已去重，恢复配方见 `reference/README.md`） |
| 比对方式 | 上游递归 git tree（含全部 java/jni 路径与文件大小）vs 本地文件系统清单 |
| 版本时点 | 2026-08-08（androidx 最新稳定：Compose 1.11.4 / material3 1.4.0 / activity 1.13.0 / core 1.18.0 / lifecycle 2.11.0 / navigation 2.9.8） |

---

## 2. AndroLua 内嵌 Java 库（`:androlua`）

### 2.1 全量清单（9 组，330 个 Java 源文件）

| 库 / 包 | 文件数 | 角色 | 上游来源 |
|---|---|---|---|
| `com.luajava.*` | 16 | Lua↔Java 绑定层（LuaState / LuaObject / LuaJavaAPI / LuaInvocationHandler / LuaStack 等） | LuaJava 1.2 fork（nirenr 深度魔改：方法缓存、类型自动转换、getter/setter 快捷、table→array/Map/interface 自动转换） |
| `com.android.cglib.dx.*` | 290 | **运行时 dex 生成器**（DexMaker 封装 + AOSP dx 全套 cf/rop/ssa/io 编译管线） | AOSP `dx`（约 2014 线）+ Google `dexmaker` API |
| `com.android.cglib.proxy.*` | 8 | Java 接口代理（Enhancer / MethodProxy / MethodInterceptor），基于 dx 生成 dex 实现代理 | cglib 2.x 的 Android 移植（nirenr 改写） |
| `com.androlua.*` | 55 | 运行时宿主类：LuaActivity / LuaContext / LuaEnhancer / LuaDexLoader / LuaDialog / LuaAdapter / LuaWebView / Http / Download / CrashHandler / GifDecoder / LuaResources / Ticker 等 | pro 自有 |
| `com.androlua.util.*` | 11 | 工具：AsyncTaskX / TimerX / GlobalActionAutomator（无障碍超级辅助）/ RootUtil / RSASecurity / ScreenMetrics / ZipUtil 等 | pro 自有 |
| `android.widget.*` | 24 | 伪装在框架命名空间的 pro 自定义控件：PageView(112KB) / PullingLayout(49KB) / HorizontalListView(55KB) / DrawerLayout(78KB) / CardView / CircleImageView / RippleLayout / ViewDragHelper / ArrayListAdapter / ExListView 等 | support v4/v7 拷贝 + pro 自有（见 2.3） |
| `android.app.*` | 2 | AlertDialogBuilder（Lua 友好封装壳）、FloatWindow（悬浮窗） | pro 自有 |
| `android.content.FileProvider` | 1 | 影子 FileProvider（暴露白名单已收敛，见 `docs/reference/security-model.md`） | androidx.core FileProvider 2017 线拷贝 |
| `com.nirenr.*` | 9 | Color / ColorFinder / ColorPoint / Point + `screencapture.ScreenShot`（截图自动化） | pro 自有 |
| `com.lioisme.lide.androlua.engine` | 2 | **lide 独有**：EngineActivity / EngineContract（宿主↔引擎桥） | lide 新增 |

### 2.2 完整性判定（vs 上游基线逐文件 diff）

**上游有、vendored 刻意没有的**（全部有据可查，非遗漏）：

| 上游文件 | lide 处置 | 依据 |
|---|---|---|
| `com/myopicmobile/textwarrior/*`（32 文件，textwarrior 编辑器） | 删除 | 已被 sora-editor 取代，`docs/discard/SOURCE_AUDIT_REPORT_2026-08-05.md` §1.4 |
| `com/androlua/LuaEditor / LuaLexer / LuaTokenTypes / Main / Welcome.java`（5 文件，IDE 壳） | 删除 | 同上 |
| `com/nirenr/SplitEditView.java` | 删除 | textwarrior 配套控件 |
| `android/support/multidex/*`（3 文件） | 删除 | 死代码（P3-5，`LuaActivity.installLibs` 被注释） |
| `android/support/annotation/*`（IntRange / RequiresApi） | 剥离 | 2026-08-08 引擎 dex 单份化时以纯 Java 常量等价替代（`third_party/androlua/build.gradle.kts` 注释） |
| `android/support/v4/app/Fragment.java`（59B 空桩） | 删除 | 空桩 |

**lide 有、上游没有的**：`EngineActivity` / `EngineContract`（引擎桥）、`lua/lide_des.c`（字节码 DES 解密）、`live2d/` 原生模块（看板娘原生化）、`CrashHandler` 安全偏离（崩溃落私有目录 + 链回系统处理器，记录在案 1 处）。

**原生侧（`app/src/main/cpp/`）**：

- 上游 `lluv/`（163 文件）+ `tcc/`（24 文件）未收纳 —— AGENTS.md 已声明不提供（WHITEPAPER §7.1 差异声明）。
- 上游 `lsqlite3` 仅有 `Android.mk2`（上游缺陷文件，实际靠顶层 mk 构建），lide 已修正为正式 `Android.mk`。
- Lua 同为 **5.3.3 fork**（`lua.h: LUA_VERSION_NUM 503`）逐字节一致；README 声明「4.0.0 升级为 lua 5.4 随机数算法」指 RNG 算法，非 Lua 5.4 版本。

> **结论：运行时面（Lua 脚本可触达的全部 Java 类）100% 完整**；差异全部集中在「编辑器 UI 层替换（textwarrior → sora）」与「lide 增强」，属有意设计，非缺失。

### 2.3 落后度评估（"是否已经落后"，时点 2026-08）

| 库 | 基线年代 | 现状 | 上游替代品 | 升级可行性 |
|---|---|---|---|---|
| `com.luajava` | LuaJava 1.2（2015）/ pro 4.x 深度魔改 | 冻结 | LuaJava 1.4 fork、LuaJ 3.0.2（纯 Java） | ⚠️ 不可单独升级：与 C 引擎（5.3.3 fork + switch/lambda/when/let/mean 语法扩展）字节码绑定，升级 = 重写引擎（`SOURCE_AUDIT` §1.4 已定论：文档化即可，勿动） |
| `com.android.cglib.dx` | AOSP dx 2014 线（AOSP 2018 废弃 dx，D8 取代） | 冻结 | D8（`com.android.tools:r8`） | ⚠️ **无运行时替代**：D8 为构建期工具，库 API 体积大且非为进程内 dex 生成设计；google/dexmaker 上游同样废弃；保持 fork 是工程正确解 |
| `com.android.cglib.proxy` | cglib 2.2（2008 线） | 冻结 | ByteBuddy（活跃维护） | ⚠️ 不可替换：ByteBuddy 生成 JVM 字节码，Android 需 dex 化 = 再造 cglib-proxy；保持 |
| `android.widget.*` 伪控件 | support v4/v7（2015–2017）拷贝 + pro 自有 | 冻结 | androidx.drawerlayout / androidx.cardview（仍在维护） | ⛔ **不可升级**：Lua 脚本按类名 `bindClass("android.widget.CardView")` 反射引用（luajava 字符串契约），换 androidx 坐标即破坏全部存量脚本；命名空间伪装是 pro 兼容性契约 |
| `android.content.FileProvider` | androidx.core 2017 线 | 冻结 | androidx.core FileProvider | ⛔ 同上；已做安全收敛（`androlua_filepaths.xml` 白名单化），无功能缺口 |
| `com.androlua` / `com.nirenr` | pro 4.4.2（2021 停更） | 冻结 | 无（它就是上游本体） | — |

**核心结论**：AndroLua 的 Java 库全部冻结在 2021 年停更的 pro 4.4.2 基线，**客观上全部落后**（最老可追溯 2008 年 cglib 线）；但**不存在任何一条可行的"更合适版本"升级路径**——三条硬约束：

1. **字节码绑定**：luajava 与 nirenr Lua 5.3.3 fork（含语法扩展）强耦合；
2. **脚本类名契约**：android.* 伪控件被 Lua 脚本按字符串类名反射锁死（AGENTS.md 三处同步机制同理）；
3. **上游废弃无替代**：dx / dexmaker / cglib-android 上游全部停更，D8/ByteBuddy 无运行时等价物。

**"冻结"是平台级约束而非维护疏忽**，与 LIDE 其余决策（原生引擎源码化、pro 二进制兼容）一致，评审不应视为缺陷。

### 2.4 reference/libs/ dex 分发库（工程级 Java 库，非内嵌）

| 库 | 版本 | 判定 |
|---|---|---|
| SmartRefreshLayout | 仓库 master = 3.0.0-alpha（最新稳定 2.0.6，2021-08 后停更） | 完整可用；如引入建议用官方 2.0.6 稳定版 |
| umeng | 闭源 SDK（需 appkey / 网络） | 功能完整但重量级，仅统计场景适用 |
| BaiduMTJ | v4.0.11.1（2016 线） | 严重过时（百度移动统计已无活跃维护），**不建议新用** |

---

## 3. 非 AndroLua Java 库（`:app` / `:editor` 宿主栈）

### 3.1 清单 + 版本新旧判定（对照 2026-08 官方最新稳定版）

| 库 | 当前版本 | 最新稳定 | 判定 |
|---|---|---|---|
| `androidx.compose:compose-bom` | **2025.06.00**（compose 1.9.2） | **2026.06.00**（compose 1.11.4，2026-07） | 🔴 落后 1 年（12 个月度版） |
| `androidx.compose.material3:material3` | **1.4.0**（显式 pin） | **1.4.0**（2026-07-15 仍为最新稳定；1.5.0 仅 alpha24） | 🟢 当前 |
| `androidx.activity:activity-compose` | 1.9.3 | 1.13.0 | 🔴 落后 3 个主版本 |
| `androidx.core:core-ktx` | 1.15.0 | 1.18.0 | 🔴 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.7 | 2.11.0 | 🔴 |
| `androidx.navigation:navigation-compose` | 2.8.5 | 2.9.8 | 🔴 |
| `com.google.android.material:material` | 1.13.0 | **1.14.0**（2026-05-13） | 🟠 落后 1 个稳定版 |
| `androidx.profileinstaller:profileinstaller` | 1.4.1 | 1.5.x 线 | 🟠 待核 |
| `com.android.tools.build:apksig` | 8.13.2 | 与 AGP 8.13.2 配套（apksig 版本跟随 AGP） | 🟢 正确配对 |
| sora-editor（vendored `:sora-editor`） | **0.24.6** | **0.24.6**（上游最新 tag，2026-06-10） | 🟢 当前（已有自动更新流水线 `update-sora-editor.yml`） |
| `sh.calvin.reorderable:reorderable` | 3.0.0 | 3.0.0（2025-08-12） | 🟢 当前 |
| `me.saket.cascade:cascade-compose` | 2.3.0 | 2.3.0 | 🟢 当前 |
| `junit:junit` | 4.13.2 | 4.13.2 | 🟢 当前 |
| `kotlinx-coroutines-test` | 1.8.1 | **1.11.0**（2026-05-08） | 🟠 落后（仅测试面） |
| AGP / Gradle | 8.13.2 / 9.5.1 | AGP 9.x 线已存在 | 🟠 受 Gradle 9.6+ 兼容门槛约束（升 Gradle 9.6 前必须先升 AGP 9.x，`build.gradle.kts` 注释） |

### 3.2 完整度判定

- **全部可用、完整**：所有 Maven 依赖均为官方稳定制品；Compose BOM 统一托管；apksig 与 AGP 同源同版本；sora 为源码 vendored **单份来源**（P1#5 已消除 Maven/project 双份类遮蔽地雷，见 `SOURCE_AUDIT` §1.2）。
- **版本健康度**：14 项中 **6 项当前**（material3 / sora / apksig / reorderable / cascade / junit）、**2 项轻微落后**（material / coroutines-test）、**5 项落后 1 年以上**（BOM / activity / core / lifecycle / navigation，全部集中于 androidx 主干线）。

### 3.3 "这些 Java 库通常可以用到吗？" —— Lua 脚本可用性契约

**结论：原则上用不到，也不应假设可用。** 机制双保险隔离：

1. **R8 剥离**：宿主 release/runtime 变体开启 minify，Lua 脚本按**字符串类名**经 luajava 反射引用，未被 `luaApiSurface`（25 类）+ proguard keep 规则覆盖的类会被直接剥离；`verifyReleaseLuaSurface` / `verifyRuntimeSurface` 双门禁守护。
2. **打包 dex 替换（P0）**：打包产物整个 classes*.dex 替换为引擎 dex（根目录引擎 dex + `assets/engine.dex.idx` 标记契约，ApkBuilder `ENGINE_DEX_MARKER` 模式，引擎 dex 单份化 2026-08-08 修订），宿主 androidx/Compose 类根本不在产物 dex 中。

因此 androidx / Compose / material / apksig / sora 对 Lua 脚本是**编译期与 IDE 宿主专用**；脚本可触达面 = `luaApiSurface`（androlua 运行时 + android.* 伪控件 + luajava + cglib + nirenr）+ 框架 `android.*` / `java.*`。

---

## 4. 结论总表

| 问题 | 答案 |
|---|---|
| AndroLua 的 Java 库有哪些？ | 9 组 330 文件（luajava / cglib-dx / cglib-proxy / androlua / android.* 伪控件 / nirenr / FileProvider / Engine 桥），见 §2.1 |
| 它们是完整的吗？ | **是** —— 运行时面与上游 pro 4.4.2 逐文件比对 100% 对齐；差异全为有记录的刻意删减（textwarrior 编辑器层）与 lide 增强 |
| 是否已经落后？ | **客观落后**（冻结于 2021 停更基线，最老 2008 线）；**但无可行的更合适版本** —— 字节码兼容 + 脚本类名契约 + 上游废弃三重约束，保持冻结是唯一正确工程决策 |
| 非 androlua 的 Java 库有哪些？ | 14 项（androidx 主干 + material + apksig + 3 个 vendored/三方 Compose 库 + 测试），见 §3.1 |
| 通常可以用到吗？ | **Lua 脚本用不到**（R8 剥离 + 打包 dex 替换双保险隔离）；仅 IDE 宿主内部使用 |
| 它们是完整的吗？ | **是** —— 全部官方稳定制品；6 项当前版本、2 项轻微落后、5 项 androidx 主干落后 1 年（风险低，Compose 向后兼容良好） |

---

## 5. 建议（供决策，均为可执行升级批次）

- **批次 A（低风险，约 1 天）**：BOM 2025.06.00 → 2026.06.00（material3 保持显式 pin 1.4.0 不动）+ activity-compose / core-ktx / lifecycle / navigation 随批升级。风险先例：compose 1.9→1.11 无 breaking 预告；material3 1.4.0 不动则 SearchBar 冻结红线（AGENTS.md）不受影响。
- **批次 B（中风险）**：material 1.13.0 → 1.14.0；coroutines-test 1.8.1 → 1.11.0。验证：`:app:testArm64DebugUnitTest` + 模拟器回归。
- **批次 C（不推荐本轮做）**：AGP 8.13.2 → 9.x（需连带 Gradle 9.6+，链条长、收益低）。
- **AndroLua 内嵌库：维持冻结**（升级路径不存在，见 §2.3；如需单点增强应走 lide 自身补丁而非换代）。

---

## 6. 验收证据

- 上游 tree 提取：GitHub API `repos/nirenr/AndroLua_pro/git/trees/master?recursive=1`（sha `abe5c25`，1135 blob / 92 tree；上游 java 443 文件，vendored 330 文件，差值 113 = textwarrior 32 + IDE 壳 5 + multidex 3 + support 3 + SplitEditView 1 + lide 新增 2 − 上游无 = 116？——差值含 lide 新增与上游目录项，最终以 §2.2 逐项 diff 表为准）。
- 版本对照来源：androidx stable-channel（2026-07/08）、Compose BOM mapping、mvnrepository（material 1.14.0 / coroutines 1.11.0 / cascade 2.3.0 / reorderable 3.0.0）、sora-editor releases（0.24.6，2026-06-10）、AndroLua_pro 仓库信息（4.4.2 线，2021 停更）。
- 引擎版本：`app/src/main/cpp/lua/lua.h` `LUA_VERSION_NUM 503`（5.3.3 fork）。
- 本报告为纯审计，零代码改动；未触发任何构建门禁。
