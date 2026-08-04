# 任务看板（Task Board）

> **类型**：任务看板 · 状态实时更新
> **用途**：回答「项目做到哪了」「现在该做什么」
> **权威状态矩阵**：[kb/README.md](kb/README.md) §2（本文档是其摘要入口）

---

## 当前进行中

| 任务 | 文档 | 进度 / 状态 |
|---|---|---|
| 对齐 pro 宿主 IDE 功能（19 个 WP） | [in-progress/PARITY_COMPLETION_WORK_ORDER.md](in-progress/PARITY_COMPLETION_WORK_ORDER.md) | 6/19 完成（剩余 WP-005~016、018） |
| 编辑器性能 V2 收尾 | [in-progress/EDITOR_PERFORMANCE_AUDIT_V2_REPORT.md](in-progress/EDITOR_PERFORMANCE_AUDIT_V2_REPORT.md) | 阶段 B 部分落地，A/C 未全执行 |
| 编辑器 UI 对齐（Squircle-CE 标杆） | [in-progress/EDITOR_UI_ALIGNMENT_WORK_ORDER.md](in-progress/EDITOR_UI_ALIGNMENT_WORK_ORDER.md) | WP-001/002/008 已落地，余项未完成 |
| 社区代码知识库 | [kb/androlua-community/README.md](kb/androlua-community/README.md) | 2144 篇初筛完成；正负面卡 + 训练 seed 产出 |
| 独立 APK 打包器（WP-014，代码已落地） | [kb/ROADMAP.md](kb/ROADMAP.md) §未登记进展 | 未真机验收、未登记 PROGRESS |

## 已完成（摘要）

已完成文档 15 篇在 [completed/](completed/)（引擎迁移 WP-001~010、导航冻结修复、以及编辑器/审计/打包器审计等），矩阵见 [kb/README.md](kb/README.md) §2.1。

## 计划中 / 未排期

| 任务 | 文档 | 状态 |
|---|---|---|
| 技术债盘点（TD1~TD7） | [planned/backlog.md](planned/backlog.md) | 计划中 |
| Xposed 模块路线图（M1~M5） | [planned/XPOSED_MODULE_FEASIBILITY_REPORT.md](planned/XPOSED_MODULE_FEASIBILITY_REPORT.md) | 研究定稿，未启动 |
| 引擎/宿主性能优化建议 | [planned/GAME_ENGINE_PERFORMANCE_AUDIT_REPORT.md](planned/GAME_ENGINE_PERFORMANCE_AUDIT_REPORT.md)、[planned/HOST_PERFORMANCE_AUDIT_REPORT.md](planned/HOST_PERFORMANCE_AUDIT_REPORT.md) | 审计完成，建议未排期 |

## 维护规则

1. **状态机**：📋 计划中 → 🚧 进行中 → ✅ 已完成（不允许跳级）。
2. 新任务先登记 [planned/backlog.md](planned/backlog.md)；开始做时 `git mv` 到 [in-progress/](in-progress/)；完成后 `git mv` 到 [completed/](completed/)。
3. 每篇任务文档头部必须注明：状态、目标、验收标准、关联代码路径。
4. 完成判断以**工作区真实代码/构建结果**为准；状态变化当天更新本文档 + 任务文档 + [kb/README.md](kb/README.md) §2 矩阵。