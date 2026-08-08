# lide 知识库

> 本目录是 lide 工程知识库（唯一事实来源），基于 `docs/` 全部历史文档与 `WHITEPAPER.md` /
> `AGENTS.md` 蒸馏整理。目标是让任何工程事实在一分钟内被检索到。

---

## 1. 结构

```
docs/
├── kb/                ← 本知识库（索引 + 分主题条目；唯一事实来源）
│   ├── README.md      # 本文件：总索引 + 阅读导航
│   ├── ARCHITECTURE.md# 架构知识：模块拓扑、R1~R5 边界、关键机制、引擎初始化时序
│   ├── ENGINE_RUNTIME.md # 引擎与运行时知识：选型、源码谱系、原生模块清单、兼容性证据链、已知差异
│   ├── EDITOR.md      # 编辑器知识：sora 集成约定、性能基线、历史事故教训
│   └── androlua-community/ # 社区帖子训练知识库（catalog/positives/negatives/training，只读）
├── in-progress/       # 进行中工作单（见 kb/README.md §2）
├── planned/backlog.md # 计划中任务 & 技术债清单
└── WHITEPAPER.md      # 产品与架构白皮书（docs 根）
```

> 历史工作单 / 审计报告已全部删除，结论蒸馏在 `ARCHITECTURE` / `ENGINE_RUNTIME` /
> `EDITOR` / `WHITEPAPER` 中；`docs/` 根目录不再平铺文档文件。

## 2. 当前工作状态

| 状态 | 位置 |
|---|---|
| 进行中工作单 | `docs/in-progress/`（PARITY_COMPLETION / RUNTIME_PACKAGING_5REQ / PACKAGING_DEX_SIMPLIFY / EDITOR_UI_ALIGNMENT / EMULATOR_REGRESSION） |
| 计划中任务 & 技术债 | `docs/planned/backlog.md`（新任务登记处） |
| 实时文档 | `docs/WHITEPAPER.md`（白皮书）、`AGENTS.md`（评审硬门槛） |

## 3. 阅读导航

| 你的目的 | 先读 |
|---------|------|
| 评审代码 / 提交前检查 | `AGENTS.md` → `ARCHITECTURE.md` |
| 了解「现在做到哪了」 | `docs/README.md` → `docs/in-progress/` + `backlog.md` |
| 改编辑器 | `EDITOR.md` |
| 改引擎/运行时/打包 | `ENGINE_RUNTIME.md` |
| 产品全貌 / 架构 / 路线 | `WHITEPAPER.md` |
| 社区代码语料 | `androlua-community/README.md` |

## 4. 数据与参考资产（只读，不参与状态管理）

| 资产 | 内容 | 用途 |
|------|------|------|
| `app/vendor/androlua/` + `app/src/main/cpp/` | pro 4.4.2 移植工作副本（上游台账 `UPSTREAM.md`） | 运行时代码 / 引擎源码唯一真源 |
| `app/vendor/sora-editor/` | sora-editor 0.24.6 vendored（editor + language-textmate） | 编辑器对齐基准 |
| `reference/squircle-ce-master/` | Squircle CE 完整 IDE 工程 | 编辑器开发参考 / UI 对齐标杆 |
| `reference/project/` | 参考工程（demo、test、testjson、石头、看板娘 等） | 双端验证 / 探针 / 验收素材 |
| `reference/代码手册社区/` | 社区帖子语料（lua_files 45 类 2144 篇 + lua_manual.db） | community-code-audit 数据源 |

## 5. 维护约定

1. 新文档落入 `docs/in-progress/`（进行中工作单）或登记 `docs/planned/backlog.md`（计划中任务）；
   `docs/` 根下不放 `.md`（`README.md`、`WHITEPAPER.md` 除外）。
2. **任务文档生命周期**：执行中每完成一项任务，在文档内对应条目标记「（已完成）」并备注
   工作内容/日期/验收证据；**整份任务文档完成后把结论蒸馏进本知识库对应文档，然后删除该工作单**
   （git 历史保留原文，不再有 discard 归档区）。
3. 本知识库不复制源码级细节，只做「事实、结论、位置、注意事项」四要素索引；
   `docs/kb/` 与 `docs/WHITEPAPER.md` 是唯一事实来源。
