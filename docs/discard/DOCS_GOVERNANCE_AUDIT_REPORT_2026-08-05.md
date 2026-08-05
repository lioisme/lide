# 文档治理审计与重组报告（2026-08-05）

- **状态**：✅ 已完成（2026-08-05）
- **任务类型**：文档治理审计（全量盘点 → 去重分类 → 统一丢弃区 → 治理约定成文）
- **验收证据**：见 §6（命令验证全绿）
- **依据**：用户指令「审计分析项目中所有文档、上游基线等参考资料，全部去重、分类；已完成/无价值文档统一丢弃；有价值文档与上游基线分类整理供 agent 参考；Agent 完成任务须标记『已完成』并备注，整份任务文档完成后归档到丢弃目录」

---

## 1. 审计范围与方法

- **范围**：`docs/` 全量（kb / completed / archive / in-progress / planned / reference / 顶层四件套）、
  根 `README.md`、`AGENTS.md`、`.opencode/skills/*`（2 个 skill）、`docs` 内 git 跟踪状态与全部跨文档引用。
- **方法**：目录/文件枚举（含 git `ls-files` 与 `status` 交叉核对）→ 逐篇读取头部与状态标记 →
  引用关系 grep 全量扫描 → 内容重合度核对（kb 蒸馏 vs 源报告）。

## 2. 审计发现

### 2.1 资产盘点（重组前）

| 区域 | 数量 | 状态判定 |
|---|---|---|
| `docs/kb/` | 6 篇主条目 + project(2) + androlua-lua(6) + androlua-community(含 2144 篇 post_*.json 语料) | ✅ 有价值，保留（唯一事实来源） |
| `docs/completed/` | 30 篇（29 篇 git 跟踪 + 1 篇未跟踪 `SOURCE_AUDIT_REPORT_2026-08-05.md`） | ⚠️ 已完成，结论已蒸馏进 kb，应整体丢弃 |
| `docs/archive/` | 8 篇 + README 索引 | ⚠️ 已过时，应并入统一丢弃区 |
| `docs/in-progress/` | 3 篇（PARITY 19WP 8/19、编辑器性能 V2、编辑器 UI 对齐） | ✅ 真进行中，保留 |
| `docs/planned/` | 4 篇（backlog、API_BROWSER_FREEZE_FIX_PLAN、XPOSED、GAME_ENGINE_PERFORMANCE） | ✅ 保留（FREEZE_FIX_PLAN 为 AGENTS.md 引用红线，须保留） |
| `docs/reference/` | androlua / sora-editor / squircle-ce / libs / project / 代码手册社区（2146 文件） | ✅ 有价值，已分类 + README 索引，gitignore 只读 |
| 顶层 | README / TASKS / PROGRESS / WHITEPAPER | ✅ 保留 |

### 2.2 问题清单（重组前）

| # | 问题 | 影响 |
|---|---|---|
| D1 | `docs/completed/` 与 `docs/archive/` 两个「终结目录」并存，且 completed 仍是活跃命名 | Agent 日常检索易把 30 篇已完成报告当输入，污染上下文 |
| D2 | 数量账目不一致：kb/README 记「completed 29 篇」，实际 30（`SOURCE_AUDIT_REPORT_2026-08-05.md` 未登记且未 git 跟踪） | 索引失真 |
| D3 | `docs/reference/` 索引写 `Squircle-CE-master/`，实际目录 `squircle-ce-master/` | 路径不真实 |
| D4 | `AGENTS.md` 第 91 行**截断**（`/libs 机制约定` 标题后正文丢失，仓库重整损坏） | 硬门槛文档残缺 |
| D5 | 跨文档引用 `docs/completed/`×~20、`docs/archive/`×~9（含 AGENTS.md、PROGRESS.md、2 个 skill、NavigationGate.kt 源码注释） | 一旦移动全部失效 |
| D6 | 任务完成状态分散在 PROGRESS.md / TASKS.md / kb 矩阵，缺少「任务文档内逐项标记 + 完成后归档」的统一成文约定 | Agent 行为不一致 |

## 3. 分类方案（执行）

**保留（唯一事实来源与工作区）**：
- `docs/kb/`（含两个子库）— 事实 / 结论 / 位置 / 注意事项四要素索引
- `docs/in-progress/`（3）、`docs/planned/`（4）— 当前与未来工作
- `docs/reference/` — 上游基线（gitignore，只读）
- 顶层 README / TASKS / PROGRESS / WHITEPAPER

**丢弃（统一出口 `docs/discard/`）**：
- 原 `docs/completed/` 30 篇 → `docs/discard/`（每篇在索引 §3.1 登记一句话 + 结论蒸馏去向）
- 原 `docs/archive/` 8 篇 + README → `docs/discard/`（索引 §3.2 登记丢弃原因）
- 两个旧目录删除；`docs/discard/README.md` 为唯一清单与登记处

**去重结论**：`docs/kb/` 已完成对全部源报告的蒸馏（kb/README 矩阵逐一核对），丢弃区文档**无独有事实价值**；同一文件不双存，实现物理去重。

## 4. 执行记录

| 项 | 动作 |
|---|---|
| 建区 | 新建 `docs/discard/`，索引 README 合并 archive 原 README + kb 矩阵 30 条 + 8 条丢弃原因 |
| 移动 | `git mv` 29 篇跟踪 completed + 8 篇 archive 文档；`Move-Item` 未跟踪的 SOURCE_AUDIT_REPORT |
| 引用修正 | 被移动文档内部 `docs/completed/`→`docs/discard/`（7 文件）、`docs/archive/`→`docs/discard/`；`architecture.md` 的 `tasks/completed/`、`REPO_HARDENING_WORK_ORDER.md` 的落盘目标一并修正 |
| 索引更新 | `docs/README.md`（结构树 + 约定 1/5/7 条）、`docs/kb/README.md`（§1 结构、§2.1 改指针、§2.5 归档→丢弃、§3 路径、§5 维护约定、新增 §7 治理注记）、`docs/TASKS.md`（状态机 + 逐项标记规则）、`docs/kb/ROADMAP.md`、`docs/kb/androlua-lua/{FEATURES,TECHNIQUES,README}.md`、`docs/PROGRESS.md` |
| 硬门槛 | `AGENTS.md`：修正 `/libs` 引用 + **补齐截断的 /libs 机制正文** + 新增「文档治理约定」节（事实来源唯一 / 任务文档生命周期 / 完成后归档） |
| 技能 | `.opencode/skills/androlua-project-validation/SKILL.md`：8 处引用改 discard；产出物约定改为「结论先蒸馏 kb → 报告完成后归档 discard」 |
| 源码注释 | `NavigationGate.kt` 2 处 KDoc/注释引用改 discard |
| 根 README | docs 结构行 + 新增文档约定行更新 |

## 5. 治理约定（成文，Agent 必守）

1. **事实来源唯一**：当前事实 = `docs/kb/`；当前工作 = `docs/in-progress/`、`docs/planned/`；
   `docs/discard/` 禁止作为日常输入（仅追溯）。
2. **任务文档生命周期**：新任务登记 `docs/planned/`（或 in-progress）→ 执行中**每完成一项在文档内标记「（已完成）」+ 工作内容 + 日期 + 验收证据**（构建命令/设备验证/测试）→ 整份完成后 `git mv` 到 `docs/discard/`（唯一出口，不再用 completed/、archive/）→ 同步更新 kb/README §2、TASKS.md、discard/README §3。

## 6. 验收与验证证据

```
# 目录终态：discard(39 篇) / in-progress(3) / planned(4) / kb / reference；completed、archive 已不存在
(Get-ChildItem docs\discard -File).Count                  → 39
Test-Path docs\completed; Test-Path docs\archive          → False / False
git status --porcelain -- docs                            → 全部为 R（rename，历史保留）或 M（索引更新）

# 引用残留（排除 kb/README 与 discard/README 中的历史性「原 docs/completed」说明文字）：
grep -r "docs/completed" --include=*.md --include=*.kt --include=*.md     → 零活跃引用
grep -r "docs/archive"  --include=*.md --include=*.kt                     → 零活跃引用
```

- git 历史：全部移动经 `git mv`（含先前已 staged 的 2026-08-05 重组 rename），无文件删除、无内容丢失。
- 全文引用有效性：kb 矩阵/索引指向 discard 的路径全部存在（核对完成）。

## 7. 遗留事项与建议

| # | 事项 | 建议 |
|---|---|---|
| 1 | `docs/planned/API_BROWSER_FREEZE_FIX_PLAN.md` 修复已完成但**保留**在 planned（AGENTS.md 红线引用） | 维持现状；待 AGENTS.md 红线改写为指向 kb 后可移入 discard |
| 2 | `SOURCE_AUDIT_REPORT_2026-08-05.md` 此前未 git 跟踪 | 本次已移入 discard，建议随本批提交入库 |
| 3 | kb/README §2.2 中 `PROGRESS.md`/`WHITEPAPER.md` 列为「进行中实时文档」 | 保持；白皮书阶段四/五待打包器真机验收后更新 |
| 4 | in-progress 三篇工作单（PARITY 8/19 等） | 继续按新约定逐项标记；全部完成后依次 git mv 至 discard |
| 5 | 工作区存在 36+ 个未提交源码改动（重组前已存在） | 与本文档无关；提交时注意本批 docs 变更一并提交 |
