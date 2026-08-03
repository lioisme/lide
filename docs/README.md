# docs — 项目文档中心（Agent 入口）

> 本文档是 delua（AndroLuaX 衍生项目）的**唯一文档总索引**。
> 任何 Agent 接手本项目时，**先读本文件**，再按需跳转对应文档。
> 所有文档已按 2026-08-03 工作区实际源码逐一核对，路径与状态均为真实值。

---

## 1. 项目一句话定位

Android 端 Lua 脚本 IDE / 运行时（AndroLuaX 衍生，Material3 主题）。
Lua 为主力开发语言（业务层），Kotlin 仅 3 个底层桥接文件，res/ 仅主题与系统级资源。

| 关键事实 | 值 |
|---|---|
| 根目录包名 | `com.jesse205.androlua.androidX.material3` |
| 版本号 | `3.4(5.0.18)(Material3)`（versionCode 3499） |
| 模块 | `app`（应用）+ `androlua`（库） |
| 语言构成 | Lua（业务）+ Kotlin × 3（桥接）+ Java 零残留 |
| 构建 | AGP 8.1.1 / Kotlin 1.9.22 / compileSdk 34 / minSdk 21 / targetSdk 29 |

---

## 2. 文档索引表（按用途检索）

### 2.1 知识库（事实性资料，回答"是什么/在哪里"）

| 文档 | 内容 | 何时看 |
|---|---|---|
| [kb/project/project-overview.md](kb/project/project-overview.md) | 项目身份、模块结构、版本信息、入口流程 | 任何任务的第一步 |
| [kb/project/architecture.md](kb/project/architecture.md) | 三层架构（Lua/Kotlin/Res）、合规红线、开发约定 | 修改代码前判断改哪层 |
| [kb/project/code-map.md](kb/project/code-map.md) | 全量文件清单：Lua 层 / Kotlin 层 / 资源层 / 构建脚本 | 定位某个文件在哪个目录 |
| [kb/project/lua-modules.md](kb/project/lua-modules.md) | Lua 业务模块逐个说明（ui/ 新 UI、ui/old/ 旧 UI、resources/lua/ 系统库） | 改 Lua 代码时 |
| [kb/project/changelog.md](kb/project/changelog.md) | 版本更新历史（自 AndroLua+ 继承） | 需要了解版本演进 |

### 2.2 任务库（状态化信息，回答"做到哪了/下一步做什么"）

| 文档 | 状态 | 内容 |
|---|---|---|
| [TASKS.md](TASKS.md) | 看板 | 全部任务的汇总状态与优先级 |
| [completed/java2kotlin-migration.md](completed/java2kotlin-migration.md) | ✅ 已完成 | Java→Kotlin 迁移（3 文件，已落地） |
| [completed/audit-remediation.md](completed/audit-remediation.md) | ✅ 已完成 | 代码审计违规整改（27 项违规已清理） |
| [in-progress/ui-refactor.md](in-progress/ui-refactor.md) | 🚧 进行中 | UI 重构：新 `ui/` 框架 + 旧代码归档 `ui/old/` |
| [planned/backlog.md](planned/backlog.md) | 📋 计划中 | 技术债清单（TD1–TD7）+ 待办事项 |

---

## 3. 快速定位指南（按需求 → 文档）

| 你的需求 | 去这里 |
|---|---|
| "这个项目是干嘛的？" | [project-overview.md](kb/project/project-overview.md) |
| "想改某个界面/功能，改哪层？" | [architecture.md](kb/project/architecture.md) → [code-map.md](kb/project/code-map.md) |
| "某个 lua 模块做什么的？" | [lua-modules.md](kb/project/lua-modules.md) |
| "Kotlin 层有什么？能改吗？" | [code-map.md](kb/project/code-map.md) §Kotlin 层 |
| "构建/打包怎么跑？" | [code-map.md](kb/project/code-map.md) §构建脚本 → `build_all.ps1` |
| "现在有哪些未完成任务？" | [TASKS.md](TASKS.md) → [backlog.md](planned/backlog.md) |
| "这个项目到哪一步了？" | [TASKS.md](TASKS.md) |
| "版本 3.4 改了啥？" | [changelog.md](kb/project/changelog.md) |
| "为什么这个文件不能改？" | [architecture.md](kb/project/architecture.md) §红线规则 |

---

## 4. 文档维护约定（重要）

1. **状态必须实时**：任务文档的「状态」字段必须在任务变化当天更新，禁止滞后。
2. **路径必须真实**：所有引用的文件路径必须在工作区真实存在（本文档体系已核对过一遍，后续改动后需复核）。
3. **新文档必须注册**：新增文档后必须在本索引表 §2 登记，否则 Agent 找不到它。
4. **wiki/ 目录不可改**：`wiki/AndroLuaX-main/` 是上游参考基线，只读，用于对照合规性。
5. **根目录文档**：`README.md`（根）只做项目简介与入口指向，详细内容一律放 `docs/`。

---

> 最后核对时间：2026-08-03（基于工作区 HEAD 提交 6a45551 的实际文件状态）
