# lide 知识库

> 本目录是基于 `docs/` 全部文档 + `PROGRESS.md` / `WHITEPAPER.md` / `AGENTS.md` 整理的知识库文档集。
> 目标是让任何一篇工程文档的**角色、状态、结论、注意事项**都能在一分钟内被检索到。

---

## 1. 文档集结构

```
docs/
├── kb/            ← 本知识库（索引 + 分主题条目；唯一事实来源）
├── in-progress/   ← 进行中文档（2 篇）
├── planned/       ← 计划中文档（8 篇）
├── discard/       ← 丢弃区（唯一出口）：已完成/过时文档 33 篇（15 篇已物理删除，条目保留作墓碑），只追溯不维护
└── reference/     ← 参考源码资产（gitignored：androlua / sora-editor / Squircle-CE / project / 代码手册社区）
```

> 物理归档目录与 §2 状态分类一一对应；`docs/` 根目录不再平铺文档文件。
> （`docs/log/` 探针日志为历史资产，未收录进本仓库。）

| 文件 | 内容 | 对应原文档 |
|------|------|-----------|
| `README.md`（本文件） | 总索引 + **文档分类状态矩阵** + 阅读导航 | 全部 |
| `ARCHITECTURE.md` | 架构知识：模块拓扑（含 :sora-editor）、R1~R5 边界、关键机制、引擎初始化时序 | ENGINE_MIGRATION / ANDROLUA_INTEGRATION_PLAN / ANDROLUA_SOURCE_CODE_AUDIT / AGENTS.md / WHITEPAPER |
| `ENGINE_RUNTIME.md` | 引擎与运行时知识：选型、源码谱系、原生模块清单、兼容性证据链、已知差异 | ENGINE_LEVEL_COMPARISON / NATIVE_LIB_AUDIT / LIBS_SUPPORT_WORK_ORDER / EMULATOR_RUNTIME_AUDIT / GAME_ENGINE_PERFORMANCE_AUDIT |
| `EDITOR.md` | 编辑器知识：sora 集成约定、性能基线、历史事故教训、UI 对齐现状与剩余 | EDITOR_CRASH_ANALYSIS / EDITOR_DRAWER_AUDIT / EDITOR_PERFORMANCE_AUDIT(_V2) / EDITOR_UI_ALIGNMENT_WORK_ORDER |
| `DECISIONS.md` | 关键决策记录（按时间线，含反转决策） | 全部决策性文档 |
| `ROADMAP.md` | 计划中 / 进行中工作与文档维护事项 | PARITY_COMPLETION_WORK_ORDER / XPOSED_MODULE_FEASIBILITY_REPORT / 各审计报告行动项 |
| `androlua-lua/` | **Lua 工程开发知识库**（独立子库）：环境/特性/坑/方法/代码写法，双端验证流程落库处 | 见其 `README.md`；由 SKILL `androlua-project-validation` §9 驱动维护 |
| `androlua-community/` | **社区帖子训练知识库**（独立子库，2026-08-02 立项）：`代码手册社区` 2144 篇帖子三轴评注 + 正负面主题卡 + LLM 微调语料 | 见其 `TASK_ORDER.md` / `README.md`；初始批：全量初筛 + 人工复核第一批 |

---

## 2. 文档分类状态矩阵

> 分类口径：**进行中** = 工作单未关闭 / 路线图未执行完；
> **计划中** = 研究已毕但实施未排期（或历史草案）；**已丢弃** = 已完成 / 已过时 / 被取代
> （统一移入 `docs/discard/`，见 [discard/README.md](../discard/README.md) §3 清单，不再参与状态管理）。

### 2.1 已完成（全部移入 discard/，登记 37 篇，现存 23 篇）

> 2026-08-05 起已完成文档不再驻留 `docs/completed/`，统一 `git mv` 至 `docs/discard/`。
> 完整清单（含每篇一句话 + 结论蒸馏去向）见 `docs/discard/README.md` §3.1；
> 结论本身以本知识库（`ARCHITECTURE` / `ENGINE_RUNTIME` / `EDITOR` / `DECISIONS` / `ROADMAP`）为准。

### 2.2 进行中（2 篇 + 3 篇实时文档）

| 文档 | 日期 | 内容一句话 | 进度 | 注意 |
|------|------|-----------|------|------|
| `PARITY_COMPLETION_WORK_ORDER.md` | 2026-08-01 | 19 个 WP 补齐 pro 宿主 IDE 功能（对齐度 ~6 成 → 目标全齐） | **13/19 完成**：WP-001/002/003/004/005/008/013/014/015/016/017/018/019（2026-08-05 补登 005/013/015/016/018）；WP-014 代码落地未真机验收 | 剩余 WP-006/007/009/010/011/012（未开工，详见 ROADMAP.md 与 TASKS.md） |
| `EDITOR_UI_ALIGNMENT_WORK_ORDER.md` | 基线 c7b35dd | 10 WP 对齐 Squircle-CE 抽屉/Tab/文件树（禁手搓、仅官方组件） | **WP-003~006 已落地**（EditorTabs 拖拽排序 + DropdownMenu 关闭菜单，2026-08-05 复核）；WP-001/002/007/008 ⛔ 被取代（抽屉已删除→`ProjectFileBrowserDialog`）；WP-009/010 未完成 | 内部 WP 编号与 PROGRESS 编号是两套体系 |
| `RUNTIME_PACKAGING_5REQ_WORK_ORDER.md` | 2026-08-07 | 打包运行时五需求工作单（运行时解耦/载荷上下限/ABI 架构/输出选配/设置收敛）：审计结论 5 项判定 + 用户决策 D-1~D-3（32 位非必须、ABI 宿主驱动、输出默认自动安装）+ 分阶段计划 P1→P2→P3→P0 | **实施完成**：P1（ABI 架构固化）→ P2（输出选配）→ P3（设置收敛）→ P0（运行时基座，dex 替换路线：宿主内嵌 APK 设计被用户否决，改为单 dex 资产 `assets/runtime/classes.dex` + 打包器 dex 替换模式） | 触发：用户五需求；D-1 明确 NDK 不降级（保持 29.0.14206865），32 位留扩展路径；P0 完成，待设备端冒烟验证后归档 |
| `androlua-community/`（子库） | 2026-08-02 | 社区帖子训练知识库：2144 篇初筛完成；正负面主题卡第一批 + 训练语料 seed 已产出 | catalog 全量完成；positives 9 卡 / negatives 5 卡 + harmful 专项 1 目录；training seed 11 条 + batch 工具就绪 | 关键事实：语料为 300 字符预览（1740/2144 截断），verdict 均 `static_review`，未实测 |
| `PROGRESS.md`（docs/） | 实时 | 工作进度台账（WP-001~010 + 工作单尾项 + WP-014 打包器） | 进行中 | 2026-08-05 恢复自 git 历史对象 `7fc57389`（此前整行损坏）；打包器 WP-014 已补登记 |
| `WHITEPAPER.md`（docs/） | 实时 | 白皮书 v2.0：架构/路线图/原生模块差异声明 §7.1 | 进行中 | 2026-08-05 恢复自 git 历史对象 `36eac8c1`（此前缺 §7.1/§8/附录 A 尾部）；阶段四/五待随打包器更新 |
| `AGENTS.md`（根） | 实时 | 评审硬门槛：R1~R5 边界、构建验证命令、编辑器约定、/libs 机制约定 | 进行中 | 评审门禁，改动前必读 |

### 2.3 计划中（8 篇）

| 文档 | 日期 | 内容一句话 | 状态 | 注意 |
|------|------|-----------|------|------|
| `API_BROWSER_FREEZE_FIX_PLAN.md` | 2026-08-03 | API 浏览器卡死根因（A/B/C）修复执行单；修复已落地并真机验证 | 修复已完成 | 保留于 planned 作为搜索语义冻结红线（AGENTS.md 引用）与历史任务单 |
| `EDITOR_FEATURE_COMPLETION_WORK_ORDER.md` | 2026-08-06 | 编辑器功能完善至 sora-editor 原生上限（F8）：sora 原生能力清单 + 差距分析 + P0~P4 分期方案 + 21 项设置映射 + 验收门槛 | 实施中：P0~P3 已完成，P4 文档已完成；仅剩设备功能验收矩阵 §8.3（推迟至模拟器复测） | 决策 D1~D4 已确认；P2 附 baseline profile（R8+`baseline-prof.txt`）与 `perf-check.ps1`；P3 附 `update-sora-editor.ps1` + `.github/workflows/update-sora-editor.yml` + R1 grep 入 CI；设备验收后归档 docs/discard |
| `backlog.md` | 实时 | 计划中任务 & 技术债清单（TD1~TD7、F6~F8 等） | 计划中 | 新任务登记处 |
| `XPOSED_MODULE_FEASIBILITY_REPORT.md` | 2026-07-31 | Xposed 模块可行性：技术可行（四支柱为真）但有前提 | 研究已定稿，路线图 M1~M5 全部未启动 | 前置依赖：打包器（WP-014）落地并真机验收 |
| `ARCHITECTURE_CLEANUP_AUDIT_REPORT.md` | 2026-08-06 | 架构审计：清理补丁式/妥协性/重复性代码（5 类补丁子系统、~59 防御 catch、双源语言目录等）+ 最优解目标架构 + Phase A~D 迁移计划 | 实施中：Phase A（P0×5）与 Phase B（B-1~B-5）全部完成，Phase C 部分完成（C-3/C-4 + C-1/C-2 安全子项），Phase D 文档漂移部分完成 | 三项根因：ApiIndex 负查找（A-1 根治）、语言数据双源（A-2 根治）、navigation/m3 补丁围栏（C-1/C-2 待真机验证后拆除）；完成记录见文档 §7 |
| `ARCHITECTURE_CLEANUP_BATCH2_REPORT.md` | 2026-08-07 | 第二轮实施批次 P1~P7（签名链路 apksig 化 + V1 验签适配、ZipUtils 收敛、TextMateRuntime 单一服务、scope/边界词常量收敛、宿主真实缺陷、三处去重、androlua 清理、EngineContract/AXml styled-pool 拒绝、测试迁移）+ 全量验证 | **已完成（2026-08-07）**：145 单测 0 失败（68/29/48），assembleArm64Debug 绿，R1 门禁零命中；验收证据见报告 §P6 | 遗留：C-1/C-2 待真机验证、D-3 catch 清理、§4.4 五项（产物模式/指纹/哨兵/测试钥/组合副作用） |
| `SETTINGS_PERMISSIONS_REWORK_WORK_ORDER.md` | 2026-08-07 | 设置与权限治理：① LIDE 自身动态权限 + 权限页单击仅申请该权限 ② 打包 APK 动态权限（LuaActivity.onRequestPermissionsResult → Lua 全局回调，pro 对等）③ 移除 Lua 手册 ④ 移除帮助 ⑤ 编辑器设置恢复默认 ⑥ 权限管理移出 AndroLua 页（入主设置「通用」分区）⑦⑧ 设置入口拆分：编辑器 / AndroLua 两个独立入口与独立页面（无「设置」后缀；合并方案当日被用户否决） | **已完成（2026-08-07）**：assembleArm64Debug 绿（含二轮拆分后重跑），R1 门禁零命中 | 取代并归档 `SETTINGS_ORGANIZATION_WORK_ORDER.md`（→ discard §3.2）；待模拟器 UI 回归（四分区/编辑器页/AndroLua 页/恢复默认/权限单击）与打包产物动态权限实测后归档 docs/discard |
| `SETTINGS_SIMPLIFY_WORK_ORDER.md` | 2026-08-07 | 设置界面简约化：① AndroLua 设置与打包选项合并为单入口页（工具 + 打包 + 默认 ABI）② 去除解释性文案 ③ 收敛单功能专题 ④ 自动安装改单一开关（默认启用，pro 兼容）⑤ 签名管理独立界面（主设置「通用」分区入口） | **已完成（2026-08-07）**：删除 `BuildOptionsScreen`/`Routes.BUILD_OPTIONS`；`AndroLuaSettingsScreen` 重写为合并页；`compileArm64DebugKotlin`/`assembleArm64Debug`/build.* 单测/R1 门禁全绿 | 待模拟器 UI 回归（主设置五分区 → 合并页开关/ABI → 签名页 → 打包自动安装）后归档 docs/discard |
| `GAME_ENGINE_PERFORMANCE_AUDIT_REPORT.md` | 2026-08-01 | 石头/看板娘瓶颈定位，原生化端到端估算 3~15 倍 | 审计完成，优化建议未排期 | 看板娘已原生化（LIVE2D 迁移）；石头未动；诚实声明：估算未 profiler 校准 |
| `NATIVE_LIB_PACKAGING_AUDIT_REPORT.md` | 2026-08-07 | 原生引擎「全架构构建 + 按需打包」机制审计：pro `bin.lua` checklib 核对（单 ABI + 按需 .so）；lide 缺口（lib 全量携带、Lua 依赖非传递致 require 断裂）；方案 = universal 多 ABI + Kotlin 传递闭包 + ABI×lib 二维过滤 | **实施完成（2026-08-07）**：§7 T1~T5 全部落地（universal flavor、`NativeDependencyAnalyzer`、`ApkBuilder` ABI×lib 过滤 + launcher 重定向、打包 UI、95 单测 + 真实宿主设备端到端闭环）；报告 §7 记实施记录与验收证据 | 触发：用户要求全架构底层库 + 打包组合能力；NDK 29 移除 32 位工具链 → 实际交付双 ABI |
| `MINIMALISM_AUDIT_REPORT.md` | 2026-08-07 | LIDE 精简治理审计：合规取证（单一 Lua 运行时 / sora 单来源 / 零 jniLibs / 资产单份）+ 3 项可整改（runtime dex 模板 2.6MB 受控冗余、live2d 示例 8.95MB 胖资产、fileTree 死声明等） | **实施完成（2026-08-07）**：`lide.examples`（默认零内置）与 `lide.embedRuntimeDex`（默认开启）双门控 + `verifyMinimalism` 去重门禁入 CI + 死代码清理（fileTree/classes.jar/compileSdk 对齐）+ ExamplesScreen 空态；报告 §4 记实施记录 | 触发：用户要求「尽量精简、无重复 dex/原生库/资源」；用户决策：示例当前全去、后续内置 1-2 个代表性工程 |
| `PACKAGING_UI_REWORK_WORK_ORDER.md` | 2026-08-08 | 打包 UI 重构：① 签名 v1/v2/v3 + ABI 改经典 Checkbox ② 宿主不含的 ABI 置灰禁用（选项由 `ZipUtils.listAbis` 实测驱动，删硬编码名单）③ 「仅携带依赖的原生库」更名「优化打包」（仅针对原生库）④ 删除构建 APK 整页，仅保留构建打包对话框（`BuildPackagingDialog`，入口 = 编辑器 ⋮ 菜单） | **已完成（2026-08-08）**：`BuildApkScreen.kt` 已删；`AndroLuaSettingsScreen` 复选框化 + 宿主驱动；`BuildPackagingDialog.kt` 新建（迁移 internal 辅助函数，`BuildPackagingTest` 零改动）；AGENTS.md D-1 同步修订 | 待模拟器 UI 回归后归档 `docs/discard`；D-1 冻结修订：默认 ABI 未携带 → 置灰而非回落展示 |
| `BUILD_SIGNING_ENCRYPTION_WORK_ORDER.md` | 2026-08-07 | 打包构建三大能力工作单：① 签名管理（自有 keystore 档案/别名/指纹/默认）② Lua 编译范围可选 + 字节码 DES-ECB 加密（C 引擎 loader 单点解密，覆盖 main + require）③ 原生库按需携带 + 签名/加密/ABI 全部打包选项持久化通用化 | **T1~T3/T5 已完成（2026-08-07）**：编译/加密独立开关（默认均开）、签名三类（内置/导入/自生成，手写 X.509 零依赖）、build.* 43 单测全绿 + assembleArm64Debug 绿 + 模拟器 DES loopback；T4 文档同步进行中 | 文档位于 `docs/planned/`；加密定位防呆级（产物内密钥可提取）；R1~R5 合规（宿主域 + 引擎 C 源码，editor 零改动） |
| `PACKAGING_PAYLOAD_CLEANUP_WORK_ORDER.md` | 2026-08-07 | 打包载荷精化：产物仅含 androlua 工程所需内容——宿主条目按「运行期消费面」领域分类（`HostPolicy`，**默认拒绝**替代黑名单剔除），内置 `.alp` 示例 / `javaapi` 类表 / keys / textmate / layouts 一律不进产物（对齐 pro `bin.lua` 宿主 assets 整树剔除语义） | **已完成（2026-08-07）**：`HostPolicy.kt` + `ApkBuilder` 接入（删 `HOST_ASSET_EXCLUDED`）+ UI 载荷摘要（`build_dep_host_only` 双语）+ `ApkBuilderTest` **17/17 绿**（含 examples/javaapi 剔除 + default-deny 兜底断言）+ `assembleArm64Debug` 绿 | 待模拟器产物运行回归后归档 `docs/discard`；dex 级剥离（R8 独立 engine 包）为遗留大项不在本次范围，已注明 KDoc |
| `EMULATOR_REGRESSION_2026-08-07.md` | 2026-08-07 | 模拟器定向+全量回归测试报告：23 项功能/性能检查全 PASS——设置重组（AndroLua 合并页 + 权限/签名入口）、动态权限授权闭环、编辑器全语言高亮（Lua/Kotlin 像素取证）、更多菜单级联、API 浏览器搜索收起死循环红线回归、示例空态、打包自装 + packaged 产物独立运行（P0 runtime dex 替换）、大文件降级、性能（冷启动 1.1s / 105MB PSS） | **已完成（2026-08-07）**：x86_64 模拟器 / API 34 实测，零 FATAL/ANR，证据清单见报告 §8 | 归档前置：后续如做 arm64 真机矩阵（build-apks.ps1）可更新 §9 |

### 2.4 未登记的新进展（知识库补录）

> 2026-08-05 治理后 WP-014 已补登记，无遗留未登记进展。新进展请遵循 §5 维护约定及时登记。

### 2.5 已丢弃（docs/discard/，登记 50 篇，现存 34 篇；15 篇文档已物理删除 + 1 项死代码资产已删除，条目保留作墓碑）

已完成 / 过时 / 被取代文档统一移入 `docs/discard/`（`git mv` 保留历史），**不参与状态管理、不作为事实来源**：

- **已完成 38 篇**（登记，其中 14 篇已物理删除；原 `docs/completed/` 30 篇 + 2026-08-07 新增 `SHADOW_REMOVAL_DEAD_CODE_CLEANUP_WORK_ORDER`、`EDITOR_ALL_LANGUAGES_SUPPORT_WORK_ORDER`、`PLUGIN_FEATURE_REMOVAL_WORK_ORDER`、`EDITOR_MULTI_LEVEL_MENU_WORK_ORDER` + 2026-08-08 新增 `SIGNING_GENERATE_CUSTOM_FIELDS_WORK_ORDER`）：结论均已蒸馏进本知识库，逐篇一句话 + 蒸馏去向见 `docs/discard/README.md` §3.1。
- **过时 11 篇**（原 `docs/archive/` 8 篇 + 插件化历史 2 篇 + `SETTINGS_ORGANIZATION_WORK_ORDER`）：`WORK_ORDER`（历史总任务书）、`IDE_RUNTIME_SEPARATION_FEASIBILITY_REPORT`（方案未采纳，与 R1~R5 冲突）、`ui-refactor`（Lua UI 时代草稿，代码已删除）、`audit-remediation`（Compose 前整改清单）、`java2kotlin-migration`（已反转：重新 vendor 上游 Java）、`architecture`/`lua-modules`/`code-map`（kb/project 三篇自标过期）、`PLUGIN_SEPARATION_ARCHITECTURE_REPORT`（插件化历史，**已物理删除 2026-08-07**）/`PLUGIN_PLATFORM_REPORT`（插件化历史，方案取消）、`SETTINGS_ORGANIZATION_WORK_ORDER`（被设置/权限治理新要求取代，**2026-08-07 移入**）。
- **2026-08-07 物理删除批次**：15 篇无外部引用的报告（结论均已蒸馏）+ `material3_searchbar_patch/` 死代码执行 `git rm`（git 历史保留原文）；登记表条目保留并标注「（已物理删除 2026-08-07）」，清单见 `docs/discard/README.md` §3。
- 丢弃原因逐条见 `docs/discard/README.md` §3.2；治理审计依据：`docs/discard/DOCS_GOVERNANCE_AUDIT_REPORT_2026-08-05.md`。

---

## 3. 数据与参考资产（只读，不参与状态管理）

| 资产 | 内容 | 用途 |
|------|------|------|
| `docs/log/androluapro.log`（历史） | pro 宿主探针输出（2026-07-31 06:00:36，879/956） | lide 运行时对照基线；**lide 仓库未收录，为历史资产** |
| `docs/log/lide.log`（历史） | lide 宿主探针输出（2026-07-31 16:01:37，878/956） | 一致性验证证据；同上 |
| `third_party/androlua/`（运行时） + `app/src/main/cpp/`（引擎） | pro 4.4.2 移植工作副本（原版纯净基线已去重归档，恢复配方见 `reference/README.md`） | 运行时代码 / 引擎源码唯一真源 |
| `third_party/sora-editor/` | sora-editor 0.24.6 vendored（editor + language-textmate；原版完整树已去重归档） | 编辑器对齐基准 |
| `reference/squircle-ce-master/` | Squircle CE 完整 IDE 工程 | 编辑器开发参考 / UI 对齐标杆 |
| `reference/project/` | 参考工程（demo、test、testjson、石头、看板娘 等） | 双端验证 / 探针 / 验收素材 |
| `reference/代码手册社区/` | 社区帖子语料（lua_files 45 类 2144 篇 + lua_manual.db） | community-code-audit 数据源 |

---

## 4. 阅读导航

| 你的目的 | 先读 |
|---------|------|
| 评审代码 / 提交前检查 | `AGENTS.md` → `ARCHITECTURE.md` |
| 了解「现在做到哪了」 | `README.md §2` → `PROGRESS.md` → `ROADMAP.md` |
| 了解「为什么这么做」 | `DECISIONS.md`（决策时间线） |
| 改编辑器 | `EDITOR.md` |
| 改引擎/运行时/打包 | `ENGINE_RUNTIME.md` |
| 评估新能力（Xposed/打包/性能优化） | `ROADMAP.md` + 对应源报告 |

---

## 5. 维护约定

1. 新文档落入 `docs/in-progress/` / `docs/planned/` 对应分类目录（`docs/` 根下不放 .md），并在本文档 §2 对应分类登记一行（一句话 + 日期 + 对应 WP）。
2. 状态变化时（如某 WP 关闭），先 `git mv` 到新分类目录，再更新矩阵。
3. **任务文档生命周期**：执行中每完成一项任务，在任务文档内对应条目标记「（已完成）」并备注工作内容/日期/验收证据；**整份任务文档完成后 `git mv` 到 `docs/discard/`** 并在 `docs/discard/README.md` §3 登记一行（一句话 + 日期 + 对应 WP）；过时 / 被取代 / 方案未采纳的文档同样随时移入（登记丢弃原因）。不删除 git 历史。
4. 决策类结论（含反转）统一入 `DECISIONS.md`，源文档保留原文并交叉引用。
5. 本知识库不复制源码级细节，只做「事实、结论、位置、注意事项」四要素索引；`docs/kb/` 是唯一事实来源，`docs/discard/` 只追溯不维护。

---

## 6. 2026-08-05 文档治理注记（恢复 + 重组）

- **恢复**：10 份在仓库重整时损坏的文档（路径展开 bug 造成整行重复 / 截断，含 `docs/completed/*` 4 篇、
  `docs/in-progress/*` 2 篇、`PROGRESS.md`、`WHITEPAPER.md`、`FULL_AUDIT_REPORT_2026-08-01.md`、
  `ANDROLUA_PARITY_FULL_AUDIT_REPORT.md`、`GAME_ENGINE_PERFORMANCE_AUDIT_REPORT.md`）
  自 git 历史完好对象恢复（内容 + 工程名重命名映射），
  `PROGRESS.md` 补充重建 WP-009 节（原节为历史遗留乱码，依据 `DEPENDENCY_MODEL_AUDIT_REPORT.md` 重建）。
- **重组**：6 篇已完成文档自 `planned/`/`in-progress/` 移入 `completed/`；8 篇过时文档移入 `docs/archive/`。
- **核对**：全库链接有效性扫描通过（本批次链接更新 + 待办中链接见 `ROADMAP.md` §7 最新状态）。

## 7. 2026-08-05 文档治理注记（统一丢弃区）

- **丢弃区合并**：`docs/completed/`（30 篇）+ `docs/archive/`（8 篇）合并为唯一丢弃出口 `docs/discard/`
  （现 41 篇 = 33 已完成 + 8 过时，索引见其 README §3）；`completed/`、`archive/` 两目录已删除。
- **任务文档生命周期成文**：Agent 每完成一项任务须在文档内标记「（已完成）」+ 工作内容；整份任务文档完成后
  `git mv` 至 `docs/discard/`。约定写入 `AGENTS.md`、`docs/README.md` §3、本文件 §5。
- **引用修正**：`AGENTS.md`、`PROGRESS.md`、`kb/androlua-lua/*`、`kb/ROADMAP.md`、`.opencode/skills/*` 中
  `docs/completed/`/`docs/archive/` 引用全部改为 `docs/discard/`；`SOURCE_AUDIT_REPORT_2026-08-05.md` 补登记入丢弃清单。
- 治理审计报告：`docs/discard/DOCS_GOVERNANCE_AUDIT_REPORT_2026-08-05.md`（本批重组依据）。
