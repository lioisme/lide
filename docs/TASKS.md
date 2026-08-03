# 任务库总览（Task Board）

> **类型**：任务看板 · 状态实时更新
> **用途**：回答「项目做到哪了」「现在该做什么」
> **更新要求**：任务状态变化当天更新本文件 + 对应任务文档

---

## 当前状态速览（2026-08-03）

| 状态 | 任务 | 文档 |
|---|---|---|
| ✅ 已完成 | Java→Kotlin 迁移（3 文件） | [completed/java2kotlin-migration.md](completed/java2kotlin-migration.md) |
| ✅ 已完成 | 代码审计违规整改（Phase 1–3） | [completed/audit-remediation.md](completed/audit-remediation.md) |
| 🚧 进行中 | UI 重构（新 ui/ 框架 + 旧代码归档） | [in-progress/ui-refactor.md](in-progress/ui-refactor.md) |
| 📋 计划中 | 技术债清理（TD1–TD7）等 | [planned/backlog.md](planned/backlog.md) |

## 当前唯一进行中的主线

**UI 重构**：把旧版 Lua 界面（javaapi/editor/project/plugin/package 等模块）重构为新的 `assets/ui/` 框架（Material3 风格：settings、dexmanager、project_dex_config 已建成，ui/main.lua 已重写为项目列表）。

## 任务管理规则

1. **状态机**：📋 计划中 → 🚧 进行中 → ✅ 已完成（不允许跳级标记）。
2. 新任务先登记到 [planned/backlog.md](planned/backlog.md)，开始做时移到 [in-progress/](in-progress/)，完成后移到 [completed/](completed/)。
3. 每个任务文档头部必须包含：状态、目标、验收标准、关联代码路径。
4. 完成判断以**工作区真实代码**为准（例如：文件已存在、引用已清理、构建通过）。

## 关联

- 项目事实性资料 → [docs/README.md](README.md) §2.1 知识库
- 想找任务但不确定在哪 → 先在 [backlog.md](planned/backlog.md) 搜索关键词
