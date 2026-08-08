# docs — 项目文档中心

> lide（AndroLua 衍生项目）文档总入口。
> 接手项目：先读根 [README.md](../README.md)（工程结构）与 [AGENTS.md](../AGENTS.md)（评审红线），
> 再按需检索本文档。

---

## 目录结构

```
docs/
├── README.md           ← 本文件：入口 + 检索索引
├── WHITEPAPER.md       # 产品与架构白皮书（架构 / 路线 / 原生模块差异 §7.1）
├── kb/                 # 知识库（是什么 / 为什么 / 在哪改）— 唯一事实来源
│   ├── README.md       #   知识库总索引 + 阅读导航
│   ├── ARCHITECTURE.md #   架构：R1~R5 边界、模块拓扑、关键机制
│   ├── ENGINE_RUNTIME.md#  引擎与运行时知识
│   ├── EDITOR.md       #   编辑器（sora 集成）知识
│   └── androlua-community/ # 社区帖子语料库（catalog/positives/negatives/training，只读）
├── in-progress/        # 进行中工作单 / 报告
└── planned/
    └── backlog.md      # 计划中任务 & 技术债清单（新任务登记处）
```

## 检索索引

| 你的需求 | 去看 |
|---|---|
| 「是什么/为什么/在哪改」 | [kb/README.md](kb/README.md) + [kb/ARCHITECTURE.md](kb/ARCHITECTURE.md) |
| 改代码前的评审红线 | [AGENTS.md](../AGENTS.md) |
| 产品与架构全貌 | [WHITEPAPER.md](WHITEPAPER.md) |
| 改编辑器 | [kb/EDITOR.md](kb/EDITOR.md) |
| 改引擎/运行时/原生库/打包 | [kb/ENGINE_RUNTIME.md](kb/ENGINE_RUNTIME.md) |
| 新任务 / 技术债 | [planned/backlog.md](planned/backlog.md) |
| 进行中工作 | [in-progress/](in-progress/) |
| 社区代码语料 | [kb/androlua-community/](kb/androlua-community/README.md) |
| 参考基线/上游源码 | `reference/`（只读，gitignore 排除）+ `app/vendor/`（上游构建模块，只读） |

## 文档维护约定

1. **事实来源唯一**：事实与结论以 `docs/kb/`、`docs/WHITEPAPER.md` 为准；
   历史文档结论已蒸馏进知识库后删除（git 历史保留原文）。
2. **路径必须真实**：所有引用的文件路径必须在工作区真实存在；改动路径后需复核本文档与 kb 索引。
3. **任务生命周期**：新任务登记 [planned/backlog.md](planned/backlog.md)；进行中工作单放
   `in-progress/`，每完成一项在文档内标记「（已完成）」+ 日期 + 验收证据；
   整份完成后把结论蒸馏进 `docs/kb/` 并删除该工作单。
4. **参考基线只读**：`reference/` 与 `app/vendor/` 禁止修改、禁止 `git add`。
5. **root 文档职责**：根目录仅保留源码、构建脚本与仓库配置（`README.md`、`AGENTS.md`、Gradle 骨架）；
   脚本一律入 `scripts/`；文档一律入 `docs/`，不再新增散落文档。

## 根目录入口文档

| 文件 | 职责 |
|---|---|
| [../README.md](../README.md) | 项目一句话定位 + 目录结构 + 常用命令 |
| [../AGENTS.md](../AGENTS.md) | 评审硬门槛（R1~R5、编辑器约定、架构契约） |
| [WHITEPAPER.md](WHITEPAPER.md) | 产品与架构白皮书 |
