# lide 知识库

> 本目录是基于 `docs/` 全部文档 + `PROGRESS.md` / `WHITEPAPER.md` / `AGENTS.md` 整理的知识库文档集。
> 目标是让任何一篇工程文档的**角色、状态、结论、注意事项**都能在一分钟内被检索到。

---

## 1. 文档集结构

```
docs/
├── kb/            ← 本知识库（索引 + 分主题条目）
├── completed/     ← 已完成文档（14 篇）
├── in-progress/   ← 进行中文档（3 篇）
├── planned/       ← 计划中文档（3 篇）
├── log/           ← 探针测试日志（数据资产）
└── reference/     ← 参考源码资产（AndroLua_pro-master / sora / Squircle-CE / luaj / 参考工程）
```

> 物理归档目录与 §2 状态分类一一对应；`docs/` 根目录不再平铺文档文件。

| 文件 | 内容 | 对应原文档 |
|------|------|-----------|
| `README.md`（本文件） | 总索引 + **文档分类状态矩阵** + 阅读导航 | 全部 |
| `ARCHITECTURE.md` | 架构知识：三层模块、R1~R4 边界、关键机制、引擎初始化时序 | ENGINE_MIGRATION / ANDROLUA_INTEGRATION_PLAN / ANDROLUA_SOURCE_CODE_AUDIT / AGENTS.md / WHITEPAPER |
| `ENGINE_RUNTIME.md` | 引擎与运行时知识：选型、源码谱系、原生模块清单、兼容性证据链、已知差异 | ENGINE_LEVEL_COMPARISON / NATIVE_LIB_AUDIT / LIBS_SUPPORT_WORK_ORDER / EMULATOR_RUNTIME_AUDIT / GAME_ENGINE_PERFORMANCE_AUDIT / docs/log |
| `EDITOR.md` | 编辑器知识：sora 集成约定、性能基线、历史事故教训、UI 对齐现状与剩余 | EDITOR_CRASH_ANALYSIS / EDITOR_DRAWER_AUDIT / EDITOR_PERFORMANCE_AUDIT(_V2) / EDITOR_UI_ALIGNMENT_WORK_ORDER |
| `DECISIONS.md` | 关键决策记录（按时间线，含反转决策） | 全部决策性文档 |
| `ROADMAP.md` | 计划中 / 进行中工作与文档维护事项 | PARITY_COMPLETION_WORK_ORDER / XPOSED_MODULE_FEASIBILITY_REPORT / 各审计报告行动项 |
| `androlua-lua/` | **Lua 工程开发知识库**（独立子库）：环境/特性/坑/方法/代码写法，双端验证流程落库处 | 见其 `README.md`；由 SKILL `androlua-project-validation` §9 驱动维护 |
| `androlua-community/` | **社区帖子训练知识库**（独立子库，2026-08-02 立项）：`代码手册社区` 2144 篇帖子三轴评注 + 正负面主题卡 + LLM 微调语料 | 见其 `TASK_ORDER.md` / `README.md`；初始批：全量初筛 + 人工复核第一批 |

---

## 2. 文档分类状态矩阵

> 分类口径：**已完成** = 行动项全部落地或审计结论已定案；**进行中** = 工作单未关闭 / 路线图未执行完；
> **计划中** = 研究已毕但实施未排期（或历史草案）。文档自述状态与结论不一致处已在「注意」列标注。

### 2.1 已完成（15 篇）

| 文档 | 日期 | 内容一句话 | 对应 WP | 注意 |
|------|------|-----------|---------|------|
| `ENGINE_MIGRATION_WORK_ORDER.md` | 立项期 | LuaJ→AndroLua C 引擎迁移总规划（7 WP / 21 天 / 8 ACC） | WP-001~006 | 标题仍为 Draft，但其全部 WP 已执行完毕，属历史规划文档 |
| `ANDROLUA_SOURCE_CODE_AUDIT_REPORT.md` | 2026-07-31 | 三源码树谱系审计：选 Pro、拒 AndroLuaX（闭源），确认 Lua 5.3.3 深度魔改 9 语法 | WP-001 选型 | 终稿；魔改字节码与标准 Lua 5.3.3 不兼容 |
| `ANDROLUA_INTEGRATION_PLAN.md` | 2026-07-31 | 引擎集成 7 Phase 方案（Java EngineActivity 继承 LuaActivity、静态编译替代 engine.dex） | WP-002/003 | 方案已全部落地；engine.dex 备用建议被 WP-002 否决 |
| `NATIVE_LIB_AUDIT.md` | 2026-07-31 | 缺失 13 原生库，修复构建源后 NDK 29 全量编译 42/42 成功 | WP-001/WP-010 | 自述「阶段 B 待执行」，实际已由 LIBS-003/006 收尾，**文档未回填** |
| `ENGINE_LEVEL_COMPARISON_REPORT.md` | 2026-07-31 | 探针 878 vs 879/956，定案 socket 失败 = 资产解包子目录缺陷并已修复 | WP-004 验证 | 重跑验证已由 EMULATOR_RUNTIME_AUDIT 覆盖 |
| `ANDROLUA_FEATURE_PARITY_AUDIT.md` | 2026-07-31 | 工程/库/插件/模块一致性审计，P0/P1/P2 全部完成 | WP-007 | P2-12「保留全局仓库」终态后被 WP-009 反转 |
| `ANDROLUA_PARITY_FULL_AUDIT_REPORT.md` | 2026-07-31 | 独立全量复核：运行时 100% 对齐、§12 行动全收口（含架构反转注记） | WP-008 | 对等审计的最新版，supersedes 早期结论 |
| `DEPENDENCY_MODEL_AUDIT_REPORT.md` | 2026-07-31 | 全局依赖仓库（common/）去留审计，方案 A 已执行（净删 ~1700 行） | WP-009 | 反转 WP-007 P2-12 / WP-008 P1-4~9 决策 |
| `LIBS_SUPPORT_WORK_ORDER.md` | 2026-08-01 | /libs 全链路收口 LIBS-001~007，真机矩阵 V-01~V-10 全绿；修复 W^X dex 加载 | WP-010 | 标题仍为 Draft（状态字段遗留，内容已完成） |
| `FULL_AUDIT_REPORT_2026-08-01.md` | 2026-08-01 | 全量审计（基准 36eac8c）：引擎 100% 对齐且超集、IDE 对齐 ~6 成、P0/P1/P2 共 21 项 | PARITY 工作单 | 其行动项由 PARITY_COMPLETION_WORK_ORDER 承接 |
| `EMULATOR_RUNTIME_AUDIT_REPORT.md` | 2026-08-01 | 12 参考工程 lide vs pro 日志逐字一致；修复 LuaActivity 未注册 + cleartext 两缺陷 | WP-010 验证 | ⚠ 该文件尚未 `git add`（未跟踪） |
| `EDITOR_CRASH_ANALYSIS_REPORT.md` | 2026-07-31 | 闪退根因 = 编辑器边界违规引入 luajava，已通过 `:editor` 独立模块 + R1~R4 恢复 | 编辑器重构期 | 定稿；P0~P2 全部落地 |
| `EDITOR_DRAWER_AUDIT_REPORT.md` | 2026-08-01 | 抽屉三缺陷修复 + 宽屏 Permanent 适配，Phase 0~6 全部完成 | WP-001（工作单） | 真机回归 5 项已由「工作单 WP-001」验证通过 |
| `EDITOR_PERFORMANCE_AUDIT_REPORT.md` | 2026-07-31 | 定位 4 个 P0 性能灾难链路（每键重建 scheme 等），根因已修复 | 编辑器重构期 | 阶段 B/C 建议并入 V2 报告继续跟踪 |
| `NAVIGATION_FREEZE_FIX_REPORT.md` | 2026-08-04 | 快速进出界面冻结根因 = NavHost$33$1 转场收尾回调在「转场中 pop」时丢失（entry 销毁内容残留）；单一漏斗门控（NavigationGate，RESUMED 后才导航）根治；4 界面 ×100 次循环零冻结零崩溃 | 导航冻结修复 | 定稿；未升级 navigation 2.9.x（方案 B 留待评估） |

### 2.2 进行中（3 篇 + 3 篇实时文档）

| 文档 | 日期 | 内容一句话 | 进度 | 注意 |
|------|------|-----------|------|------|
| `PARITY_COMPLETION_WORK_ORDER.md` | 2026-08-01 | 19 个 WP 补齐 pro 宿主 IDE 功能（对齐度 ~6 成 → 目标全齐） | **6/19 完成**：WP-001 编译修复、WP-002 运行诊断闭环、WP-003 user_permission、WP-004 工程属性页、WP-017 解析结构化、WP-019 测试骨架 | 剩余 WP-005~016、WP-018（详见 ROADMAP.md） |
| `EDITOR_PERFORMANCE_AUDIT_V2_REPORT.md` | 2026-07-31 | 确认 V1 的 P0 已修复；剩余差距：每键整屏重组（P0-B）、首帧初始化（P0-A）、功能缺失 | 阶段 B 部分落地（EditorTabs / EditorSession / ProjectFileTree 已存在）；阶段 A 未全执行；阶段 C 未开始 | 以 Squircle-CE 为标杆 |
| `EDITOR_UI_ALIGNMENT_WORK_ORDER.md` | 基线 c7b35dd | 10 WP 对齐 Squircle-CE 抽屉/Tab/文件树（禁手搓、仅官方组件） | WP-001/002/008 已被抽屉审计以官方 API 落地；Tab 卡/文件树组件已建，WP-003~007/009/010 未完成 | 草案待批准；内部 WP 编号与 PROGRESS 编号是两套体系 |
| `androlua-community/`（子库） | 2026-08-02 | 社区帖子训练知识库：2144 篇初筛完成；正负面主题卡第一批 + 训练语料 seed 已产出 | catalog 全量完成；positives 9 卡 / negatives 5 卡 + harmful 专项 1 目录；training seed 11 条 + batch 工具就绪 | 关键事实：语料为 300 字符预览（1740/2144 截断），verdict 均 `static_review`，未实测 |
| `PROGRESS.md`（docs/） | 实时 | 工作进度台账（WP-001~010 + 工作单 WP-001~004/017/019） | 进行中 | **最新条目未登记打包器进展**（见 2.4） |
| `WHITEPAPER.md`（docs/） | 实时 | 白皮书 v2.0：架构/路线图/原生模块差异声明 §7.1 | 进行中 | 阶段四仍写「规划中」，但打包器代码已落地（见 2.4） |
| `AGENTS.md`（根） | 实时 | 评审硬门槛：R1~R4 边界、构建验证命令、编辑器约定、/libs 机制约定 | 进行中 | 评审门禁，改动前必读 |

### 2.3 计划中（3 篇）

| 文档 | 日期 | 内容一句话 | 状态 | 注意 |
|------|------|-----------|------|------|
| `WORK_ORDER.md` | 2026-07-31 | 早期 10 WP 整改清单（重命名包名、权限清理等） | 历史草案，多数条目已被实际工程演进覆盖 | 建议归档或重写，勿以其为准 |
| `XPOSED_MODULE_FEASIBILITY_REPORT.md` | 2026-07-31 | Xposed 模块可行性：技术可行（四支柱为真）但有前提 | 研究已定稿，路线图 M1~M5 全部未启动 | 前置依赖：打包器（WHITEPAPER 阶段四/五）落地 |
| `GAME_ENGINE_PERFORMANCE_AUDIT_REPORT.md` | 2026-08-01 | 石头/看板娘瓶颈定位，原生化端到端估算 3~15 倍 | 审计完成，优化建议未排期 | 诚实声明：倍数为工程估算，未 profiler 校准 |

### 2.4 未登记的新进展（知识库补录）

以下进展存在于代码与 git 历史中，但 **PROGRESS.md / 白皮书未登记**，知识库先行记录：

| 进展 | 证据 | 状态 |
|------|------|------|
| **WP-014 独立 APK 打包器已开工**：`LuaCompiler`（luac 字节码化，14-A）、`AXmlManifest`（纯字节级 AXML 补丁，14-B）、`build/ApkBuilder + ApkSigner + V2Signer + ZipUtils`（重打包 + v1/v2 签名，14-C）、`BuildApkScreen`、打包单测 | commits 953592b / fecaa61 / 9be67b5 | 代码已落地；未登记 PROGRESS、未真机验收、白皮书阶段四未更新 |
| 工作区未提交改动：`EditorScreen.kt` / `EditorToolbar.kt` / 中英 strings / `AndroidManifest.xml` | `git status` | 进行中（打包/权限流程相关） |

---

## 3. 数据与参考资产（只读，不参与状态管理）

| 资产 | 内容 | 用途 |
|------|------|------|
| `docs/log/androluapro.log` | pro 宿主探针输出（2026-07-31 06:00:36，879/956） | lide 运行时对照基线 |
| `docs/log/lide.log` | lide 宿主探针输出（2026-07-31 16:01:37，878/956） | 一致性验证证据 |
| `docs/reference/androlua/`（AndroLua_pro-master） | pro 4.4.2 全量参考源码 + Lua 资产 + jni 398 个 C 文件 | 逐字节比对基准 / 移植来源 |
| `docs/reference/{sora-editor-main, Squircle-CE-master, luaj-master, project}` | 编辑器上游源码、Squircle-CE 标杆、LuaJ（已废弃）、参考工程 | 编辑器对齐基准 / 历史比对 |

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

1. 新文档落入 `docs/completed/` / `docs/in-progress/` / `docs/planned/` 对应分类目录（`docs/` 根下不放 .md），并在本文档 §2 对应分类登记一行（一句话 + 日期 + 对应 WP）。
2. 状态变化时（如某 WP 关闭），先 `git mv` 到新分类目录，再更新矩阵；反向也可由本矩阵驱动原文档回填（如 `LIBS_SUPPORT_WORK_ORDER` 标题 Draft、`NATIVE_LIB_AUDIT` 状态字段）。
3. 决策类结论（含反转）统一入 `DECISIONS.md`，源文档保留原文并交叉引用。
4. 本知识库不复制源码级细节，只做「事实、结论、位置、注意事项」四要素索引。
