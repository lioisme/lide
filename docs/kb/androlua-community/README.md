# AndroLua 社区帖子训练知识库（androlua-community）

> 本目录是**社区帖子语料**的评注化知识库：对 `reference/代码手册社区/lua_files/`
> 全部 **2144 篇**社区帖子做三轴判定（质地 kind / 质量 verdict / 可移植性 portability），
> 抽取出**正面方法与思路**、**负面反例**，并输出 **LLM 微调训练语料**。
>
> 本库是**独立静态评审库**（默认 `static_review`，不做设备断言）；涉及 lide/Pro 兼容结论，
> 一律需 `androlua-project-validation` 双端实测（流程见该 skill，结论随轮次沉淀）。

---

## 1. 目录结构

```
androlua-community/
├── TASK_ORDER.md       ← 立项任务书（建设依据与验收口径，先读）
├── STANDARDS.md        ← 判定标准总库（三轴 + 正负面细则 + 可信度）
├── README.md           ← 本文件：索引 + 统计 + 维护约定
├── catalog/            ← 全量机器清单 + 聚合报告 + 质量报告（analyze.py 生成）
│   ├── post_<id>.json          每篇一档（三轴 + flags + 截断状态）
│   ├── aggregate.json          聚合统计
│   ├── quality_report.md       语料质量画像（含截断事实）
│   └── triage_shortlist.md     人工复核短名单（正/负/harmful 候选）
├── positives/          ← 正面主题卡（方法、思路、可复用写法）
├── negatives/          ← 负面主题卡（命名/逻辑/平台绑定反例；harmful 专项）
├── training/           ← LLM 微调语料输出（schema + jsonl seed）
└── tools/              ← analyze.py（全量扫描/再生成）
```

## 2. 当前统计（2026-08-03 全量正文扫描 v2）

| 维度 | 分布 |
|------|------|
| 总数 | 2144 篇 |
| kind | code_demo 1950 / text_note 154 / fragment 26 / reference_sheet 11 / announce 3 |
| verdict（初筛） | positive 1005 / neutral 209 / negative 922 / harmful 8 |
| portability | android_api 1067 / universal 602 / host_private 273 / editor_bound 170 / obsolete 32 |
| 正文截断 | complete 1785 / truncated_tail 359 |

> ⚠ **来源级事实（v2 修正）**：v1 所称「300 字符预览截断 1740/2144」系接口列表预览限制，
> **不是正文事实**。本版改为读取磁盘完整正文（`lua_files/**/*.lua`，12.5MB/438K 行）；
> 真实文末截断 359 篇（truncated_tail）。verdict 均为静态初筛，未实测。

## 3. 使用方式

- **找方法/思路** → `positives/`（每卡含来源 id 与片段）
- **找反例/避坑** → `negatives/`（含判罚依据与修正写法）
- **类型×用途×正负标注总览** → `KNOWLEDGE_BASE.md`（KB-COMM-002）
- **批量筛选/审计** → `catalog/post_*.json`（机器可读）
- **微调数据** → `training/`（JSONL，schema 见 `training/README.md`）

## 4. 维护约定（硬要求）

1. 跑一次全量扫描：`python tools/analyze.py`（输出 catalog/ 四件套，可重复执行）。
2. 新增/修订主题卡：卡片头部 YAML 必须含 `reviewed`（人工复核人/日期）与 `confidence`；
   从 `catalog/` 取数时保留 `manual_id` 可回溯。
3. **判定升级**：`static_review` → `device_tested` 必须附双端验证记录（`androlua-project-validation` skill 流程）。
4. 变更登记：本文档 §5 追加一行。
5. 原始语料**只读**：不修改、不再移动（2026-08 随 reference 整体迁出 docs/） `reference/代码手册社区/` 下文件。

## 5. 变更登记

| 日期 | 主题 | 文件 |
|------|------|------|
| 2026-08-02 | 知识库立项：TASK_ORDER/STANDARDS/README + analyze.py 全量初筛（2144 篇）+ 质量报告（来源预览截断 1740/2144 事实） | 全部 |
| 2026-08-02 | 人工复核：pro 源码核对 com.androlua.* 类存在性（LuaRecyclerAdapter 不存在） | catalog/, negatives/private_api_lockin.md |
| 2026-08-02 | 第一批主题卡：positives 9 卡（网络/WebView/JSON/通知定时/自绘动画/列表适配器/文件存储/系统工具/热更新设计）；negatives 5 卡 + harmful 专项；2383/2384 误报降级 | positives/, negatives/ |
| 2026-08-02 | 训练语料：schema + 人工 seed 11 条 + build_training.py 批量候选（analyze 399 / reject 1，draft） | training/ |
| 2026-08-03 | **v2 全量正文扫描**：analyze.py 改为读磁盘完整正文（12.5MB/438K 行），修正 v1 预览截断误报；规则校准（shamrock 隐藏代码惯用法、layout 键值对、Glide.load 误报、注入/多开语义收窄）；harmful 双通道（正则初筛 + 人工复核清单 HARMFUL_OVERRIDES，8 篇） | tools/analyze.py, catalog/ |
| 2026-08-03 | 专业知识库正文：KB-COMM-002（类型×用途×正负标注 + 负面专项 + harmful 专项 + 入库 DoD） | KNOWLEDGE_BASE.md |
