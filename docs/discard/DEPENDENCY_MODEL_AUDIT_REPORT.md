# lide 全局依赖管理层（common/ 仓库）去留审计报告

> 审计对象：`:androlua` 统一管理层（`com.lioisme.lide.androlua.common`）+ `:app` 三个管理界面（库/模块/依赖）
> 审计基线：AndroLua Pro 4.4.2「工程内自行管理」模型（`docs/reference/androlua/AndroLua_pro-master/`）
> 审计日期：2026-07-31　审计人：opencode（全量源码 + 调用链 grep + git 历史 + 资源/文档交叉验证）
> 触发背景：用户提出「取消全局仓库设计，回归 pro 式每个工程内部自行负责」的架构复审要求

> **执行状态（2026-07-31 更新）**：方案 A 已采纳并全部执行完毕（用户明确不做旧版本迁移兼容）——删除 `common/` 目录（6 文件 + 3 模型）、三个管理界面、4 条路由、2 个设置入口、物化调用链、51+2 条字符串、样例 dependencies 块与 `documentfile` 依赖；`RunEngineLauncher` 还原纯直启。验收：`:app:compileArm64DebugKotlin` ✅、`:app:assembleArm64Debug` ✅、`:editor` R1 边界 grep 零命中 ✅、全库残留符号扫描零命中 ✅。详见 `PROGRESS.md` WP-009。

---

## 1. 结论速览（TL;DR）

- **全局依赖管理层是 lide 的叠加设计，不是 pro 生态能力**：`common/libs` + `common/modules` + init.lua `dependencies` 块 + 运行前物化（`materializeProjectDependencies`）四件套，于提交 `7d1dd77`（2026-07-31，"feat: add androlua host module"）引入，本仓库 Java 运行时（`LuaDexLoader` / `LuaActivity` / `LuaApplication`）对 `common/` **零引用**——它只是宿主侧一层「安装面」，运行时唯一感知的是物化后的工程目录。
- **现状存在一个必修缺陷（S1）**：示例工程 `demo` 的 init.lua 声明 `dependencies = { modules = {"dkjson"} }`，而 `dkjson.lua` 实际随样例自带在工程目录内；全新安装后全局仓库为空，`validateDependencies` 判定 dkjson 缺失 → **demo 工程直接无法运行**（toast `run_materialize_failed`，引擎不启动）。全局仓库为空时，样例即坏。
- **既有决策记录与本方向冲突**：PROGRESS.md WP-007 / 审计报告 §12 的收口决策是「保留全局仓库 + 物化同步为终态」（只修了 registry.json/lide.json 等半成品），**没有任何任务文档要求取消全局设计**（WORK_ORDER.md 的 10 个 WP 全部是引擎迁移向，无此项）。本次审计即对该决策的复审。
- **推荐方案 A（回归 pro 工程自治，删除全局管理层）**：净删约 **1700+ 行**（管理类 617 行 + UI 1050 行）+ 43 条字符串 + 4 条路由 + 2 个设置入口 + 物化调用链；运行时行为与 pro 完全同构，样例缺陷自愈，概念单一。安装便利性可用「向工程复制文件」小功能（约 150 行）兜底，或直接交给系统文件管理器（pro 原样做法）。
- 备选方案 C（保留 UI 但降级为「工程内管理」）也可行，但工作量高于 A 且保留了一半概念；方案 B（维持现状）不解决 S1–S6 任何一项。

---

## 2. 审计范围与方法

| 维度 | 内容 |
|---|---|
| 管理类（:androlua） | `common/DependencyManager.kt`（545 行）、`common/LibraryRegistry.kt`（69 行）、`common/ModuleRegistry.kt`、`common/model/{LibraryInfo,ModuleInfo,ProjectDependencies}.kt` |
| UI（:app） | `ui/screen/{LibraryManagerScreen,ModuleManagerScreen,ProjectDependenciesScreen}.kt`（302+445+303 行）、`ui/navigation/LideNavHost.kt`（4 条路由）、`SettingsScreen.kt`（2 个入口）、`EditorScreen.kt`（依赖按钮） |
| 调用链 | `LideApp.onCreate` → `ensureDirsExist()`；`RunEngineLauncher.launch` → `materializeProjectDependencies(projectDir)` |
| 资源 | values / values-zh 各 43 条相关字符串；`run_materialize_failed` 等 |
| 运行时 | 全量 grep `androlua/src/main/java` 对 `common|materializ` 引用（零业务命中，仅 javadoc 词面） |
| 数据 | 磁盘布局 `/sdcard/AndroLua/common/{libs,modules}/`；工程内 `.lide-materialized.json`；样例 init.lua |
| 历史 | `git log --all --androlua/.../common/`（7d1dd77 引入、8abdfbb 重构）；WORK_ORDER.md（10 WP，无此项） |

---

## 3. 全局管理层现状全景

### 3.1 组件与职责

| 组件 | 行数 | 职责 | 消费者 |
|---|---|---|---|
| `DependencyManager` | 545 | 库/模块安装、卸载（含引用拦截）、列表；init.lua `dependencies` 块读写（结构化扫描）；校验；**物化 + `.lide-materialized.json` 清理** | RunEngineLauncher、三个管理界面、LideApp |
| `LibraryRegistry` | 69 | `common/libs/<name>/` 的 metadata.json 读写、构件查找（jar/dex/so 单构件约束） | DependencyManager |
| `ModuleRegistry` | ~75 | `common/modules/<name>/` 的 metadata.json 读写、磁盘回退重列 | DependencyManager |
| `LibraryManagerScreen` | 302 | 全局库：搜索、安装（文件选择）、卸载（引用拦截）、详情 | Settings 入口 |
| `ModuleManagerScreen` | 445 | 全局模块：搜索、双安装入口（文件多选 / SAF 文件夹树递归复制）、卸载、详情 | Settings 入口 |
| `ProjectDependenciesScreen` | 303 | 工程级依赖勾选（libs/modules 复选框 → 写 init.lua）+ 工程本地 libs 只读展示 | 编辑器「依赖」按钮 |

### 3.2 数据流（唯一运行时桥）

```
安装面（lide 叠加层）                          运行时面（pro 模型，逐字节同构）
─────────────────────────                      ──────────────────────────
UI 安装 → common/libs/<name>/{jar|dex|so}   → 物化复制 → <project>/libs/<name>-<ver>.jar   → LuaDexLoader.loadLibs 自动加载
UI 安装 → common/modules/<name>/*.lua       → 物化复制 → <project>/lua/<rel>              → package.path「工程/lua/?.lua」命中
init.lua dependencies 块（声明）              → validateDependencies（缺失即拒跑）          → 卸载/取消声明后按清单清理残留
```

- **运行时对全局仓库零感知**：`LuaDexLoader.loadLibs`、`package.path` 前缀、`initENV` 执行 init.lua 全部与 pro 逐字节一致（审计报告 §5.1/§7 复核过）。物化后的工程目录与 pro 工程完全等价。
- **磁盘双份**：同一构件在 `common/` 与每个引用工程各存一份；`common/` 侧是「安装面」、工程侧是「运行时面」，二者经 `.lide-materialized.json` 维持同步。

### 3.3 依赖注入 UI 入口链

`SettingsScreen`（库管理器 / 模块管理器）→ `Routes.LIBRARIES/MODULES`；`EditorScreen` 顶栏「依赖」→ `Routes.DEPENDENCIES`。

---

## 4. 与 AndroLua Pro 模型的对照

| 维度 | pro（基准） | lide 现状 | 判定 |
|---|---|---|---|
| 库的管理单元 | 工程 `<name>/libs/` 目录，文件放入即生效 | 全局 `common/libs/` 仓库 + 声明 + 物化三件套 | ⚠️ 叠加 |
| 库加载 | `LuaDexLoader.loadLibs` 扫工程 libs/（jar/dex/so 均认） | 同源码逐字节 | ✅ 一致 |
| 模块 | 工程内任意 `.lua` + `package.path` 前缀 | 全局 `common/modules/` + 物化到工程 lua/ | ⚠️ 叠加 |
| 依赖声明 | **无此概念**（`dependencies` 块在 pro init.lua 中不存在） | init.lua `dependencies = {...}` 块 | ❌ lide 私有语法 |
| 版本管理 | 无（文件名即版本） | metadata.json 版本 + 单构件约束 + 卸载引用拦截 | ❌ lide 私有概念 |
| 运行前校验 | 无（缺失即运行时报错） | `validateDependencies` 缺失 → **拒绝启动** | ❌ 行为差异 |
| 示例工程 | 自带所需 .lua（dkjson 直接放工程内） | demo 样例改为依赖全局仓库（见 S1） | ❌ 缺陷 |
| .alp 导出 | 自动携带工程 libs/ 与 lua/ | 物化后同样携带（等价） | ✅ |

结论：pro 的哲学是「工程 = 自治单元，一切以目录内文件为准，无隐藏状态」；lide 全局层引入了**隐藏的全局状态 + 双事实源（声明 vs 实际文件）+ 运行前否决权**。

---

## 5. 由来与既有决策记录

- **引入**：`7d1dd77`（2026-07-31）「feat: add androlua host module, engine bridge, lua manual screen」——`DependencyManager` 与三个管理界面随宿主模块重写一次性出现；`8abdfbb`（同日）「refactor: align plugin/module/library manager with host runtime」重构为现状。**属 lioisme fork 的自创设计，pro 生态无对应物。**
- **既有收口决策（与本方向冲突）**：
  - PROGRESS.md WP-007 §P2-12：「保留全局仓库 + 物化同步为终态」（2026-07-31 记录）；
  - 审计报告 §12.1：P1-6（registry.json 删除）、P1-9（lide.json 删除）、P1-5（物化清理）均按「保留」前提完成。
  - 这些决策修复的是全局层的**半成品**，未回答「该层是否应当存在」——正是本次复审的范围。
- **任务文档**：WORK_ORDER.md（10 个 WP，引擎迁移向 P0–P4）无全局依赖相关整改任务；无任何文档要求取消该设计。

---

## 6. 现状缺陷清单（按严重度）

| # | 严重度 | 缺陷 | 证据 |
|---|---|---|---|
| S1 | **必修** | 全新安装后示例工程 `demo` 不可运行：init.lua 声明 `modules={"dkjson"}`，但 `dkjson.lua` 随样例自带在工程内；`validateDependencies` 检查的是 `common/modules/dkjson`（空）→ 拒绝启动 | `assets/samples/demo/init.lua:8-10`、`DependencyManager.kt:205-217`、`RunEngineLauncher.kt:25-26` |
| S2 | 高 | 运行前否决权：pro 的「缺库 = 运行时错误」被前移为「缺库 = 拒绝启动」（toast 后引擎不启动），用户无法绕行 | `materializeProjectDependencies` `check(validation.valid)`（DependencyManager.kt:237-242） |
| S3 | 高 | 双事实源：依赖真实集合 = init.lua 声明 × 全局仓库内容；声明与文件不同步时行为不可预测（卸载拦截、物化清理都是为此打的补丁） | 全链 §3.2 |
| S4 | 中 | 磁盘双份 + 隐藏状态：`common/` 与工程内物化副本冗余；`.lide-materialized.json` 是用户不可见的元文件 | §3.2 |
| S5 | 中 | 全局语义与工程自治冲突：卸载全局库会因「被引用」被拒（pro 概念中不存在「引用」）；工程间共享一个构件版本，互不可见地耦合 | `uninstallLibrary:84-89` |
| S6 | 中 | 概念层数与文档成本：审计报告 §5.2 称其为「叠加层」，白皮书无此架构说明；AGENTS.md 亦未提及——新维护者需要三份文档才能理解依赖模型 | 全仓库文档 |
| S7 | 低 | i18n 残留：`dependencies_title` 英/中两版均含 `��` 乱码（`Dependencies �� %1$s`） | values/values-zh strings.xml:140 附近 |

---

## 7. 候选方案与对比

### 方案 A：取消全局管理层，回归 pro 工程自治（推荐）

- **删除**：`DependencyManager.kt`、`LibraryRegistry.kt`、`ModuleRegistry.kt`、`model/{LibraryInfo,ModuleInfo,ProjectDependencies}.kt`（约 617 行）；`LibraryManagerScreen`（302 行）、`ModuleManagerScreen`（445 行）、`ProjectDependenciesScreen`（303 行）；`LideNavHost` 4 条路由（LIBRARIES/MODULES/DEPENDENCIES + dependencies() 构造）；`SettingsScreen` 2 个入口；`EditorScreen` 依赖按钮；`LideApp` 的 `ensureDirsExist()`；`RunEngineLauncher` 的物化调用与 `run_materialize_failed`；相关 43 条字符串 ×2 语言。
- **改造**：`samples/{demo,testjson}/init.lua` 移除 `dependencies` 块（pro 语义，pro 的 initENV 空环境执行无副作用，历史工程残留块可留可清）。
- **补偿能力（可选，约 150 行）**：编辑器或工程页提供「向本工程添加文件」（SAF 文件选择 → 复制到 `project/<name>/libs/` 或 `lua/`），等价于把全局安装 UI 降为工程内导入；或直接沿用系统文件管理器（pro 原样）。
- **数据迁移（一次性）**：仅当用户已有 `common/` 存量时执行——按各工程 init.lua `dependencies` 块把 `common/libs/<name>` 构件复制到对应 `<project>/libs/`、`common/modules/<name>` 的 .lua 复制到 `<project>/lua/`，随后删除 `common/` 与各工程 `.lide-materialized.json`；迁移可在 `LideApp` 首启检测执行或写成文档步骤。
- **收益**：运行时行为与 pro 完全同构；S1 自愈（样例自带 dkjson）；S2/S3/S4/S5 全部消失；净代码减法；唯一概念口径；与已完成的 P2-13 单进程口径一样属于「文档归一」动作的延伸。
- **成本**：失去集中安装/版本/卸载便利；既有已物化工程的清理依赖迁移步骤。

### 方案 B：维持现状（不推荐）

- 不解决 S1–S7 任何一项；「收口已完成」的现状即本轮复审的质疑对象。

### 方案 C：保留 UI，降级为「工程内管理」（中间态）

- 三个界面改造为以 `<project>` 为作用域：库管理 = 向 `project/<name>/libs/` 复制 jar/dex/so 并列出/删除；模块管理 = 向 `project/<name>/lua/` 复制 .lua（目录结构保留）；依赖页 = 工程内 libs/lua 文件浏览管理。
- 删除全局仓库、声明块解析、物化、校验、卸载拦截；`DependencyManager` 瘦身为 `ProjectArtifactManager`（约 150 行）。
- **收益**：保留 UI 便利性（文件选择/SAF 复制/删除），且全部操作直接作用于 pro 模型，无隐藏状态。
- **成本**：改造工作量高于 A（三个界面重写而非删除）；仍保留「管理界面」这一 pro 没有的概念层（但作用域正确）。

### 对比矩阵

| 维度 | A 删除回归 | B 维持 | C 降级为工程内 |
|---|---|---|---|
| pro 行为一致性 | ✅ 完全同构 | ⚠️ 叠加层 | ✅ 同构（UI 增强） |
| S1 样例缺陷 | ✅ 自愈 | ❌ 保留 | ✅ 自愈 |
| S2 运行前否决 | ✅ 消失 | ❌ 保留 | ✅ 消失 |
| S3/S4/S5 全局语义 | ✅ 消失 | ❌ 保留 | ✅ 消失 |
| 净代码量 | **-1700 行** | ±0 | 约 -900 行（含重写） |
| 安装便利性 | 无 UI（可加 150 行小功能） | 有 | 有 |
| 工作量 | 低（删除为主 + 迁移） | 0 | 中（三界面重写） |

---

## 8. 推荐意见

**推荐方案 A**：lide 的运行时已与 pro 逐字节同构，「工程 = 自治单元」是 pro 生态（.alp 分发、插件、libs/ 自动加载）的全部前提；全局层是宿主侧叠加，唯一价值是「集中安装」这一 IDE 便利性，而它带来的隐藏状态、双事实源、运行前否决权与样例耦合，成本远高于收益。A 与已完成的 P1-6/P1-9（registry.json、lide.json 收口）方向一致——那些收口本质是「全局层减法」，A 是减法的终点。

若用户希望保留「从文件选择器一键安装」的体验，可在 A 之上追加工程内导入（方案 C 的最小形态），此时 A 与 C 合并为「pro 模型 + 工程内导入 UI」。

**执行顺序建议**（若采纳 A）：
1. 删除管理类与三个界面、路由、入口、字符串（先删 UI 后删类，避免编译期悬空引用）；`RunEngineLauncher` 还原为纯直启（删物化与 toast）。
2. 样例 init.lua 去 `dependencies` 块；`LideApp` 去 `ensureDirsExist()`。
3. （可选）新增工程内导入功能 + 一次性数据迁移逻辑（检测 `common/` 存在 → 按声明落位 → 清 `common/` 与 `.lide-materialized.json`）。
4. 更新文档：PROGRESS.md 新 WP（反转 WP-007 P2-12 决策并注明原因）、审计报告 §12.1 对应条目改「已移除」、白皮书数据流章节补一句「无全局仓库」；修复 S7 乱码。
5. 验收：`:app:compileArm64DebugKotlin` + `:app:assembleArm64Debug`；demo/testjson/kleinbottle 全新安装可运行；R1 边界 grep 零命中。

---

## 9. 附录

- 删除清单（方案 A）：见 §7；运行时零改动（`LuaDexLoader`/`LuaActivity`/`LuaApplication`/`import.lua` 不动）。
- 保留清单：`AlpManager`（.alp 导入导出，pro 能力）、`PluginManager`/`PluginScreen`（pro 能力）、`ProjectManager`（工程 CRUD）、样例工程、luadoc/布局模板资产。
- 历史工程兼容：init.lua 残留 `dependencies` 块在删除读取端后无任何副作用（pro initENV 空环境执行）；已物化的 `libs/`、`lua/` 文件继续被 `loadLibs`/`require` 加载，无需用户干预，迁移仅针对「未物化但声明了」的存量。
- 相关文档现状：PROGRESS.md WP-007（P2-12 决策）、ANDROLUA_PARITY_FULL_AUDIT_REPORT.md §12.1（P1-5/6/9、P2-12 标记）需在本方向落地后同步修订。
