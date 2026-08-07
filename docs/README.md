# docs — 项目文档中心

> 本文档是 lide（AndroLua 衍生项目）的文档总入口与检索索引。
> 接手项目时：先读根 [README.md](../README.md)（工程结构）与 [AGENTS.md](../AGENTS.md)（评审红线），
> 再回到本文档按需检索。

---

## 1. 目录结构（物理分类）

```
docs/
├── README.md           ← 本文件：入口 + 检索索引
├── TASKS.md             # 任务看板（做到哪 / 下一步）
├── PROGRESS.md           # 引擎迁移 WP 台账（自根目录迁入，2026-08）
├── WHITEPAPER.md         # 产品与架构白皮书（自根目录迁入，2026-08）
├── kb/                  # 知识库（是什么 / 为什么 / 在哪改）— 唯一事实来源
│   ├── README.md        #   知识库总索引 + 文档状态矩阵（最权威）
│   ├── ARCHITECTURE.md  #   架构：R1~R4 边界、编辑器/引擎约定
│   ├── ENGINE_RUNTIME.md#   引擎与运行时碎片事实
│   ├── EDITOR.md        #   编辑器（sora 集成）知识
│   ├── DECISIONS.md     #   关键决策时间线（含反转决策）
│   ├── ROADMAP.md       #   进行中/计划中工作路线
│   ├── project/         #   项目事实性资料（overview、changelog 维护中）
│   ├── androlua-lua/    #   Lua 工程开发知识库（独立子库）
│   └── androlua-community/ # 社区帖子训练知识库（独立子库：catalog/positives/negatives/training）
├── in-progress/          # 进行中工作单 / 报告（2 篇）
├── planned/              # 计划中 / 研究完成未排期（6 篇）
├── discard/              # 丢弃区（唯一出口）：已完成/过时文档 30 篇（15 篇已物理删除，条目保留作墓碑），只追溯不维护
└── reference/            # 参考项目基线 / 社区语料（git ignored，只读，不提交）
```

> `docs/log/`（探针日志）为历史资产，未收录进本仓库。

## 2. 检索索引

| 你的需求 | 去看 |
|---|---|
| 「现在做到哪了」 | [TASKS.md](TASKS.md) → [kb/ROADMAP.md](kb/ROADMAP.md) |
| 「是什么/为什么/在哪改」 | [kb/README.md](kb/README.md) 状态矩阵 + [kb/ARCHITECTURE.md](kb/ARCHITECTURE.md) |
| 改代码前的评审红线 | [AGENTS.md](../AGENTS.md) → [kb/ARCHITECTURE.md](kb/ARCHITECTURE.md) |
| 改编辑器 | [kb/EDITOR.md](kb/EDITOR.md) |
| 改引擎/运行时/原生库 | [kb/ENGINE_RUNTIME.md](kb/ENGINE_RUNTIME.md) |
| 做一个新功能/新方向 | [kb/ROADMAP.md](kb/ROADMAP.md) + [planned/](planned/) 对应报告 |
| Lua 工程怎么写 | [kb/androlua-lua/](kb/androlua-lua/README.md) |
| 社区代码语料 | [kb/androlua-community/](kb/androlua-community/README.md) |
| 参考基线/上游源码 | [reference/](reference/)（只读，gitignore 排除） |

## 3. 文档维护约定

1. **分类即状态**：新文档按状态落入 `in-progress/`、`planned/`；
   状态变化时 `git mv` 到新目录，并同步更新 [kb/README.md](kb/README.md) §2 状态矩阵；
   **文档全部完成或已过时后统一 `git mv` 到 [discard/](discard/README.md)（唯一丢弃出口，只追溯不维护）**。
2. **路径必须真实**：所有引用的文件路径必须在工作区真实存在；改动路径后需复核本文档与 kb 索引。
3. **新文档必须登记**：新增文档后必须在 [kb/README.md](kb/README.md) 登记一行（一句话 + 日期 + 关联 WP）。
4. **决策反转**：决策类结论（含反转）统一入 [DECISIONS.md](kb/DECISIONS.md)，源文档保留原文并交叉引用。
5. **参考基线只读**：`reference/` 下的参考项目与语料禁止修改、禁止 `git add`；
   根目录上游库归档 `third_party/`（`androlua`/`patches`/`sora-editor`）同样只读。
6. **root 文档职责**：根目录仅保留源码、构建脚本与仓库配置（`README.md`、`AGENTS.md`、Gradle 骨架）；脚本一律入 `scripts/`（打包/示例/回归）；文档一律入 `docs/`（进度台账 `PROGRESS.md`、白皮书 `WHITEPAPER.md` 亦驻 `docs/` 根），不再新增散落文档。
7. **任务文档生命周期（Agent 必守）**：每完成一项任务，在任务文档内对应条目标记「（已完成）」并备注工作内容/日期/验收证据；整份任务文档完成后 `git mv` 到 `discard/` 并更新本文档、[kb/README.md](kb/README.md) §2 与 [discard/README.md](discard/README.md) §3 清单。

## 4. 根目录入口文档

| 文件 | 职责 |
|---|---|
| [../README.md](../README.md) | 项目一句话定位 + 目录结构 + 常用命令 |
| [../AGENTS.md](../AGENTS.md) | 评审硬门槛（R1~R4、编辑器约定、性能红线） |
| [PROGRESS.md](PROGRESS.md) | 引擎迁移 WP 台账（docs 根） |
| [WHITEPAPER.md](WHITEPAPER.md) | 产品与架构白皮书（docs 根） |