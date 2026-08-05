# lide 知识库

> 本目录是基于 `docs/` 全部文档 + `PROGRESS.md` / `WHITEPAPER.md` / `AGENTS.md` 整理的知识库文档集。
> 目标是让任何一篇工程文档的**角色、状态、结论、注意事项**都能在一分钟内被检索到。

---

## 1. 文档集结构

```
docs/
├── kb/            ← 本知识库（索引 + 分主题条目；唯一事实来源）
├── in-progress/   ← 进行中文档（3 篇）
├── planned/       ← 计划中文档（4 篇）
├── discard/       ← 丢弃区（唯一出口）：已完成/过时文档 41 篇，只追溯不维护
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

### 2.1 已完成（全部移入 discard/，共 30 篇）

> 2026-08-05 起已完成文档不再驻留 `docs/completed/`，统一 `git mv` 至 `docs/discard/`。
> 完整清单（含每篇一句话 + 结论蒸馏去向）见 `docs/discard/README.md` §3.1；
> 结论本身以本知识库（`ARCHITECTURE` / `ENGINE_RUNTIME` / `EDITOR` / `DECISIONS` / `ROADMAP`）为准。

### 2.2 进行中（2 篇 + 3 篇实时文档）

| 文档 | 日期 | 内容一句话 | 进度 | 注意 |
|------|------|-----------|------|------|
| `PARITY_COMPLETION_WORK_ORDER.md` | 2026-08-01 | 19 个 WP 补齐 pro 宿主 IDE 功能（对齐度 ~6 成 → 目标全齐） | **13/19 完成**：WP-001/002/003/004/005/008/013/014/015/016/017/018/019（2026-08-05 补登 005/013/015/016/018）；WP-014 代码落地未真机验收 | 剩余 WP-006/007/009/010/011/012（未开工，详见 ROADMAP.md 与 TASKS.md） |
| `EDITOR_UI_ALIGNMENT_WORK_ORDER.md` | 基线 c7b35dd | 10 WP 对齐 Squircle-CE 抽屉/Tab/文件树（禁手搓、仅官方组件） | **WP-003~006 已落地**（EditorTabs 拖拽排序 + DropdownMenu 关闭菜单，2026-08-05 复核）；WP-001/002/007/008 ⛔ 被取代（抽屉已删除→`ProjectFileBrowserDialog`）；WP-009/010 未完成 | 内部 WP 编号与 PROGRESS 编号是两套体系 |
| `EDITOR_ALL_LANGUAGES_SUPPORT_WORK_ORDER.md` | 2026-08-06 | 全语言（39 种 TextMate 文法）支持：资产/目录/关键字表入 `:editor`；修复 Lua 高亮回归 | 已完成（2026-08-06 落地 + 模拟器验证） | 资产 81 个于 `editor/src/main/assets/textmate/`（含 `language-configuration`）；Lua 词法诊断保持 `source.lua` 专属 |
| `androlua-community/`（子库） | 2026-08-02 | 社区帖子训练知识库：2144 篇初筛完成；正负面主题卡第一批 + 训练语料 seed 已产出 | catalog 全量完成；positives 9 卡 / negatives 5 卡 + harmful 专项 1 目录；training seed 11 条 + batch 工具就绪 | 关键事实：语料为 300 字符预览（1740/2144 截断），verdict 均 `static_review`，未实测 |
| `PROGRESS.md`（docs/） | 实时 | 工作进度台账（WP-001~010 + 工作单尾项 + WP-014 打包器） | 进行中 | 2026-08-05 恢复自 git 历史对象 `7fc57389`（此前整行损坏）；打包器 WP-014 已补登记 |
| `WHITEPAPER.md`（docs/） | 实时 | 白皮书 v2.0：架构/路线图/原生模块差异声明 §7.1 | 进行中 | 2026-08-05 恢复自 git 历史对象 `36eac8c1`（此前缺 §7.1/§8/附录 A 尾部）；阶段四/五待随打包器更新 |
| `AGENTS.md`（根） | 实时 | 评审硬门槛：R1~R5 边界、构建验证命令、编辑器约定、/libs 机制约定 | 进行中 | 评审门禁，改动前必读 |

### 2.3 计划中（4 篇）

| 文档 | 日期 | 内容一句话 | 状态 | 注意 |
|------|------|-----------|------|------|
| `API_BROWSER_FREEZE_FIX_PLAN.md` | 2026-08-03 | API 浏览器卡死根因（A/B/C）修复执行单；修复已落地并真机验证 | 修复已完成 | 保留于 planned 作为搜索语义冻结红线（AGENTS.md 引用）与历史任务单 |
| `EDITOR_FEATURE_COMPLETION_WORK_ORDER.md` | 2026-08-06 | 编辑器功能完善至 sora-editor 原生上限（F8）：sora 原生能力清单 + 差距分析 + P0~P4 分期方案 + 21 项设置映射 + 验收门槛 | 实施中：P0~P3 已完成，P4 文档已完成；仅剩设备功能验收矩阵 §8.3（推迟至模拟器复测） | 决策 D1~D4 已确认；P2 附 baseline profile（R8+`baseline-prof.txt`）与 `perf-check.ps1`；P3 附 `update-sora-editor.ps1` + `.github/workflows/update-sora-editor.yml` + R1 grep 入 CI；设备验收后归档 docs/discard |
| `backlog.md` | 实时 | 计划中任务 & 技术债清单（TD1~TD7、F6~F8 等） | 计划中 | 新任务登记处 |
| `XPOSED_MODULE_FEASIBILITY_REPORT.md` | 2026-07-31 | Xposed 模块可行性：技术可行（四支柱为真）但有前提 | 研究已定稿，路线图 M1~M5 全部未启动 | 前置依赖：打包器（WP-014）落地并真机验收 |
| `GAME_ENGINE_PERFORMANCE_AUDIT_REPORT.md` | 2026-08-01 | 石头/看板娘瓶颈定位，原生化端到端估算 3~15 倍 | 审计完成，优化建议未排期 | 看板娘已原生化（LIVE2D 迁移）；石头未动；诚实声明：估算未 profiler 校准 |

### 2.4 未登记的新进展（知识库补录）

> 2026-08-05 治理后 WP-014 已补登记，无遗留未登记进展。新进展请遵循 §5 维护约定及时登记。

### 2.5 已丢弃（docs/discard/，41 篇）

已完成 / 过时 / 被取代文档统一移入 `docs/discard/`（`git mv` 保留历史），**不参与状态管理、不作为事实来源**：

- **已完成 30 篇**（原 `docs/completed/`）：结论均已蒸馏进本知识库，逐篇一句话 + 蒸馏去向见 `docs/discard/README.md` §3.1。
- **过时 8 篇**（原 `docs/archive/`）：`WORK_ORDER`（历史总任务书）、`IDE_RUNTIME_SEPARATION_FEASIBILITY_REPORT`（方案未采纳，与 R1~R5 冲突）、`ui-refactor`（Lua UI 时代草稿，代码已删除）、`audit-remediation`（Compose 前整改清单）、`java2kotlin-migration`（已反转：重新 vendor 上游 Java）、`architecture`/`lua-modules`/`code-map`（kb/project 三篇自标过期）。
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
