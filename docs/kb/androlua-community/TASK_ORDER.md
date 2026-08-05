# 社区帖子训练知识库 — 建设任务书（TASK ORDER）

> 文档编号：KB-COMM-001 ｜ 负责人：编辑器/知识库组 ｜ 状态：进行中
> 本文是「AndroLua 社区帖子训练知识库」（`docs/kb/androlua-community/`）**唯一的立项与验收依据**。
> 后续一切新增/修订都必须先读本文档与 `STANDARDS.md`。

---

## 1. 结论先行（TL;DR）

社区帖子（2144 篇）**不是可直接作为训练样本/权威文档的语料**：

- 部分使用了**特定 AndroLua 客户端版本的私有 API**（`com.androlua.*`），在 lide /
  AndroLua Pro 中不可直接运行；
- 大量作者**命名、逻辑、架构不规范**（中文标识符、全局变量污染、未对齐缩进、代码残缺、
  混入广告/公告/纯文本等）；
- 但其中**有价值的方法、思路、可复用片段**是 lide 工程开发与社区治理的重要输入。

本任务：**把 2144 篇社区帖子转化为一套分类清晰、口径统一、可审计、可持续增量的
「社区知识库 + LLM 训练语料」**。

---

## 2. 背景与业务价值

| 价值方向 | 说明 |
|---------|------|
| AndroLua 工程开发 | 方法/思路可直接迁移进 lide 宿主与 Lua 工程；负面样本用于避免踩坑 |
| 定向 LLM 微调 | 输出 `training/*.jsonl` 指令微调样本，教会大模型识别 AndroLua 专属写法与反例 |
| 评审依据 | `STANDARDS.md` 中的正负面判定与可信度分级，可供任何人对帖子做一致性复评 |

---

## 3. 数据来源与现状画像（已核验）

### 3.1 来源

- 目录：`reference/代码手册社区/lua_files/`（45 个分类子目录，共 **2144 篇** `.lua`）
- 元数据：`reference/代码手册社区/lua_manual.db`（SQLite，字段见 `tools/catalog.py` 注释）
- 采集脚本：`fetch_lua.py`（仅用于原始拉取，本库**不依赖**线上 API）

### 3.2 现状量化（全量扫描结论，见 `catalog/quality_report.md`）

- 中位体积约 300 字节 → 约 3/4 是**微型片段**，非完整教程；
- 约 60 篇直接引用 `com.androlua.*` 私有类（`Http` / `LuaDrawable` / `LuaTimer` /
  `LuaDexLoader` / `LuaAdapter` 等），另有多篇引用 FusionApp 专用环境能力；
- 约 325 篇出现**中文/拼音标识符**（Lua 5.3 可运行，但属非规范写法）；
- 部分源码本身即**残缺/被截断**（Word 级别截断），上传即不完整；
- 分类名为「控件属性 → 适配器 / 数据存储 / 设备系统」等存在重叠与自动分类遗留，需按本文档
  §5 归一化并**保留原始分类作为线索**。

---

## 4. 分类体系（三轴）

每篇帖子在入库时必须落 **三轴标签**，缺一不可：

### 4.1 轴 A：内容质地（`kind`）

| kind | 定义 | 示例 |
|------|------|------|
| `code_demo` | 可运行的代码（含片段） | 动画封装、Banner 轮播 |
| `text_note` | 教学/说明文字（无实质代码） | Lua GC 讲解、io.open 模式表 |
| `reference_sheet` | 清单/手册（权限列表、url scheme） | Android 权限 212 个 |
| `announce` | 公告/水帖/广告（无代码价值） | 手册公告、远程公告 |
| `fragment` | 明显残缺（截断于语句中） | 收尾 `impor` —— 直接标记 |

### 4.2 轴 B：质量评价（verdict）

| verdict | 定义 | 入库去向 |
|---------|------|---------|
| `positive` | 方法/思路有价值、写法可借鉴、可复用 | `positives/` |
| `negative` | 命名/逻辑/架构有明确问题，可作反例 | `negatives/` |
| `neutral` | 中规中矩，仅作参考留存 | `catalog/` 保留 |
| `harmful` | 有安全/伦理风险（见 STANDARDS §3） | `negatives/harmful` 专项 |

### 4.3 轴 C：可移植性（`portability`）

| 档位 | 含义 |
|------|------|
| `universal` | 纯 Lua 5.3 标准能力，任何宿主可运行 |
| `android_api` | 依赖 Android public API（Activity / Intent / View），Pro/lide 可运行 |
| `host_private` | 依赖宿主私有类（`com.androlua.*`、`activity.getLuaDir` 等）——**lide/Pro 需验证** |
| `editor_bound` | 依赖特定编辑器能力（FusionApp `uiManager`、`ds.*`、Cover/uiplugin） |
| `obsolete` | 依赖已废弃 API / 已在系统行为中失效 |
| `unverifiable` | 无法判定（残缺/无上下文） |

---

## 5. 评估标准（判定规则总库）

完整判定细则见 `STANDARDS.md`；本库评审/扫描的**硬规则**：

1. 每个 `catalog/post_*.json` 必须带 source 指纹（manual_id+file_path），保证可回溯。
2. 自动标签仅作为**初筛**；任何进入 `positives/` / `negatives/` 的条目，必须经**人工复核**
   并具有「作者一眼可见」的可引用片段。
3. 可信度分级沿用 androlua-lua 约定：`✅实测` / `⚠仅pro` / `⚠仅lide` / `未实测`。
   本库帖子的判定原则上应标注为 **`未实测`**（代码面评审结论，不代表设备行为）。

---

## 6. 产物定义（目录）

```
docs/kb/androlua-community/
├── TASK_ORDER.md          ← 本文档
├── README.md              ← 库索引 + 统计 + 维护约定（含变更登记表）
├── STANDARDS.md           ← 判定标准全库
├── catalog/               ← 全量机器清单：post_*.json（每篇一文件）+ 聚合报告
├── positives/             ← 正面主题卡（按主题分目录）
├── negatives/             ← 负面主题卡（含 harmful 子目录）
├── training/              ← LLM 微调数据集输出（schema + jsonl）
├── tools/                 ← 分析/生成脚本（analyze.py 等）
└── 数据原文只读引用
   reference/代码手册社区/lua_files/  （只读，不迁移）
```

## 7. 验收口径（Definition of Done）

一篇帖子「已入库」= 满足：

- [x] `catalog/post_<id>.json` 存在且三轴标签完整（kind / verdict / portability）；
- [x] 若 verdict ∈ {positive, negative, harmful}, 对应主题卡已人工撰写并引用其片段；
- [x] 已在 `README.md` 变更登记表登记一行；
- [x] 若是 `character` 检测到的危险/生态破坏型，已置 paused/玷污标记并在报告计数。

批量收敛口径：单批 20 篇；批次完成同步更新 `catalog/quality_report.md` 与 README 统计。

## 8. 风险与边界（诚实声明）

1. **可信度**：本库条目大部分基于**静态文本评审**，未经设备实测；凡涉 lide/Pro 兼容为
   端口，必须走 `androlua-project-validation` SKILL 双端实测后才可升格为 `✅实测`。
2. **语料残缺**：多篇为片段，训练样本在使用前必须做完整性过滤（见 training/README）。
3. **版权**：保留作者元数据原作者标注；入库时保留 source 行，不涂抹。
4. **隐私/安全**：涉及 Hook/Root/绕过验证等 `harmful` 内容仅作识别与记录，不在新工程中
   主动复刻；见 `negatives/harmful` 的边界说明。

## 9. 本任务当前执行清单

见 `README.md §变更登记`（每次增量追加一行）。