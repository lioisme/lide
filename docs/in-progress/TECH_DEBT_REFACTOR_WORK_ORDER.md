# 技术栈审计重构工作单（TECH_DEBT_REFACTOR_WORK_ORDER）

> **状态**：🔄 部分完成（T1–T5 真实验收；T6–T11 复核未通过重开回 backlog；T12 收尾已执行）
> **背景**：2026-08-09 全量代码审计（审计报告声称 75 文件改动；复核仅见 3 文件树内落盘
> ——build.gradle.kts / proguard-rules.pro / ApiCategoryClassifier.kt，其余 T6–T11 均为
> 未落盘陈述，本轮按「真实实现 → 真实验收」纪律复核执行）。
> **流程**：按「合理开发顺序」落地该报告：契约修复 → 性能热路径 → 去重清理 → UI 阻塞 → 依赖升级 → 收尾验证。
> **验收总则**：每项完成后标记「（已完成）」+ 工作内容 + 日期 + 验收证据（构建命令 / 测试结果）。

---

## 任务清单

### T1. 文档登记（本工作单 + backlog）— 已完成
- 工作内容：2026-08-09 创建本工作单；backlog.md 登记技术债重构任务。
- 验收：工作单与 backlog 条目存在且可检索。

### T2. 引擎面三处同步收敛（审计 D2）— 已完成
- 工作内容：2026-08-09 创建 `app/engine-surface.txt` 为引擎面类唯一事实源（35 条目，含分类器独有
  RippleLayout/RoundRectDrawable/RippleHelper/ViewDragHelper 补登记）；build.gradle.kts
  `engineSurfaceDescriptors` 改为读表派生（原 buildSet 手写清单删除）；proguard-rules.pro 注释指向
  单一事实源；ApiCategoryClassifier 新增 `android.app.AlertDialogBuilder` 前缀（修复 FRAMEWORK 误分类）
  并将前缀表改 internal；新增 `EngineSurfaceConsistencyTest`（4 用例：清单可解析 / 分类器→清单闭环 /
  清单→ANDROLUA 分类闭环 / 清单→R8 keep 覆盖闭环）。
- 验收：`.\gradlew.bat :app:testArm64DebugUnitTest --tests "com.lioisme.lide.apibrowser.EngineSurfaceConsistencyTest"` BUILD SUCCESSFUL（2026-08-09）。

### T3. ApiIndex dexNames 契约对齐（审计 D1）— 已完成
- 工作内容：2026-08-09
  - `Snapshot` 新增 `entryNames: Set<String>`（静态表 + 宿主 dex + 工程 libs + R 条目名全集）与
    `isKnown(name)`（O(1) 可加载判定）；`ApiIndex.loadClassRef` 增加门控：索引已建时未知类名
    直接返回 null，**零 `Class.forName` 负查找**（契约 D1 落地）。
  - 宿主 dex 枚举进程级缓存 `ApiIndex.hostDexNames`：buildBase 首次枚举后跨工程复用，切工程不重扫。
  - ApiBrowserScreen 空快照兜底补 `entryNames`；AGENTS.md「反射线程契约」表述同步（dexNames+entryNames）。
  - 新增 `ApiIndexContractTest`（5 用例：isKnown 双集命中 / 未知与内部类判定 / loadClassRef 正负路径 / 语义一致）。
- 验收：`:app:testArm64DebugUnitTest` 全量 179 用例 BUILD SUCCESSFUL（2026-08-09）。

### T4. 原生模块表对齐引擎源码（审计 R1 + D3）— 已完成
- 工作内容：2026-08-09
  - 对齐引擎源码单一事实源（app/src/main/cpp 各模块 Android.mk 的 LOCAL_MODULE）：补入
    luajava / lfs / lsqlite3，剔除引擎未构建的 ftp / http / import / mime / smtp（工程 libs/
    自带同名模块仍经 buildBase 扫描自动登记，功能不丢）；gl 经 luagl 目录 LOCAL_MODULE=gl 确认；
    zip 产物 libzip.so 加载名 zip 确认。列表字段改 internal。
  - D3 补充说明：`lua`（核心库，非模块）与 `live2d`（独立演示模块，源码无 luaopen，不可
    require）按设计不列入可 require 模块表，EngineNativeModulesTest 显式排除并注释原因。
  - 新增 `EngineNativeModulesTest`（3 用例：引擎可 require 模块不漏登 / 清单无幽灵模块 /
    无重复登记，双向闭环，zip 前缀归一）。
- 验收：`:app:testArm64DebugUnitTest` 全量 200 用例 BUILD SUCCESSFUL（2026-08-09）。
  教训：注释/KDoc 内不得出现 `cpp/*/` 这类 `*/` 序列（会提前闭合块注释，编译器报
  「Unclosed comment」级联错误）。

### T5. apibrowser 测试补位（审计 D8）— 已完成
- 工作内容：2026-08-09 补齐模块测试覆盖（审计期 test-results 中 ApiCategoryClassifierTest.xml /
  ApiUsageClassifierTest.xml 为历史残留，无对应源文件，原始「零测试」结论修正为本次重建）。
  新增四个测试文件：EngineSurfaceConsistencyTest（4）、ApiIndexContractTest（6）、
  EngineNativeModulesTest（3）、ApiCategoryClassifierTest（7）。
- 验收：分类器/快照/模块契约共 20 用例全绿（`:app:testArm64DebugUnitTest --tests "com.lioisme.lide.apibrowser.*"`）。

### T6. LuaDiagnostics 热路径（审计 E1/E2）— 复核未通过（原虚标）
- 工作内容：2026-08-09 复核：工作树无对应改动证据（LuaDiagnostics.kt 未含 close 序列
  预计算 / errorAt 行偏移表等实现），原「已完成」断言不成立。任务重开，回 backlog。
- 验收：待真实实现 + LuaDiagnosticsTest 全绿。

### T7. 机械去重与一致性（审计 2.5/2.6 项）— 复核未通过（原虚标）
- 工作内容：2026-08-09 复核：无树内证据。任务重开，回 backlog。
- 验收：待真实实现 + 编译/单测全绿。

### T8. SigningScreen 主线程阻塞异步化（审计 U2）— 复核未通过（原虚标）
- 工作内容：2026-08-09 复核：无树内证据。任务重开，回 backlog。
- 验收：待真实实现 + 打包链单测全绿。

### T9. navigation-compose 2.9.8 + NavigationGate 根除（审计 P1）— 复核未通过（原虚标）
- 工作内容：2026-08-09 复核：build.gradle.kts 仍为 navigation-compose **2.8.5**（未升级），
  树内无 2.9.8 相关改动。任务重开，回 backlog（R8）。
- 验收：待真实升级 + NavigationGate 删除 + 导航回归。

### T10. 依赖栈批量升级（审计 §5）— 复核未通过（原虚标）
- 工作内容：2026-08-09 复核：activity-compose 仍 1.9.3 / core-ktx 1.15.0 / lifecycle 2.8.7，
  BOM 未升级。任务重开，回 backlog（R9）。
- 验收：待真实升级 + 编译/全量单测全绿。

### T11. LuaCrypto 手写 PKCS#7 改官方 PKCS5Padding（审计 2.5 项）— 复核未通过（原虚标）
- 工作内容：2026-08-09 复核：无树内证据（LuaCrypto 未改官方 API）。任务重开，回 backlog（R10）。
- 验收：待真实实现 + LuaCryptoTest 全绿。

### T12. 收尾全量验证 — 已完成（本轮复核实际执行）
- 工作内容：2026-08-09 复核执行编译 + 全量单测 + verifyMinimalismDebug。
- 验收：
  - `:app:compileArm64DebugKotlin` / `:app:testArm64DebugUnitTest`（200 用例）BUILD SUCCESSFUL；
  - `:app:verifyMinimalismDebug`（debug 直出同断言，含 checkEngineDexMerge）BUILD SUCCESSFUL；
  - ktlint：本次会话新增/修改文件全部合规；但全仓 `ktlintCheck` 存在约 1000 处既有违规
    （LanguageKeywordRegistry 838 / AXmlManifest 83 / LuaConfigParser 65 / LuaCompiler 等），
    原「ktlint 全绿」断言不成立——已登记 backlog R11，另行专项处理，不在本工作单范围。

---

## 复核发现（2026-08-09 第二轮）

除任务本体外，复核确认以下两处原断言不成立（均已在工作单/backlog 修正）：
1. **T12「ktlint 全绿」**：实际 `ktlintCheck` 约 1000 处既有违规（见上），已登记 backlog R11。
2. **审计期「apibrowser 零测试」**：test-results 中 ApiCategoryClassifierTest.xml /
   ApiUsageClassifierTest.xml 无对应源文件（历史残留），T5 已重建 20 用例。
3. **测试工作目录陷阱**：Gradle 单测工作目录 = 模块目录（非仓库根），新测试文件均按此约定
   用相对路径读取 engine-surface.txt / proguard-rules.pro / src/main/cpp（与
   LanguageCatalogConsistencyTest 同约定）。

---

## 审计关键结论备忘（本工作单依据）

| 编号 | 结论 | 处置（2026-08-09 复核后） |
|---|---|---|
| D1 | `Snapshot.dexNames` 死代码；AGENTS.md 契约「O(1) 查表免负查找」未实现 | ✅ T3：接入查表（entryNames + isKnown + loadClassRef 门控）+ 宿主快照进程级缓存 + 文档措辞修订 |
| D2 | 引擎面三处同步（build.gradle / proguard / 分类器）且已漂移（分类器独有 4 类、AlertDialogBuilder 误分类） | ✅ T2：engine-surface.txt 单一事实源 + 修复分类 + 一致性测试 |
| D3 | 原生模块表双处重复且漂移（浏览器缺 lfs/lsqlite3/luajava；live2d/lua 按设计不列入） | ✅ T4：对齐 cpp 源码 + 双向一致性测试 |
| E1/E2 | 诊断热路径每字符 String 分配、errorAt O(k·n)、countLines 全量 O(n) | ⏸ T6 复核未通过 → 重开（backlog R5） |
| U2 | SigningScreen 6 处主线程密码学/文件 IO | ⏸ T8 复核未通过 → 重开（backlog R7） |
| P1 | NavigationGate 为 navigation 2.8.x 缺陷规避（16ms 轮询） | ⏸ T9 复核未通过（仍 2.8.5）→ 重开（backlog R8） |
| — | 依赖栈落后（BOM 2025.06 / nav 2.8.5 / lifecycle 2.8.7 / activity 1.9.3） | ⏸ T10 复核未通过 → 重开（backlog R9） |
| — | 手写 PKCS#7 可被官方 PKCS5Padding 替代 | ⏸ T11 复核未通过 → 重开（backlog R10） |
| — | ktlint 全仓约 1000 处既有违规，lint 长期阻断 | 新增（backlog R11） |

## 不动项（有意的妥协，禁止回退）

AXmlManifest 手写 AXML、SigningCrypto 手写 X.509 DER、DES-ECB 规约、LuaConfigParser 文本级扫描、
LuaCompiler 复用引擎 LuaState、sora/androlua vendor 结构、单 dex 集、单 :app 模块。
