# LLM 微调训练语料（androlua-community/training）

> 输出格式与生成规则。当前为 **seed 级**（示例样本），批量生成由 `tools/build_training.py` 负责。

## 1. 目标

为 AndroLua 定向 LLM 提供三类监督样本：

| type | 用途 | 标签来源 |
|------|------|---------|
| `analyze` | 输入社区代码片段 → 输出三轴判定 + 问题清单（正/负样本） | catalog 初筛 + 人工复核 |
| `refactor` | 输入反面写法 → 输出规范化改写 + 理由 | negatives 卡片 |
| `reject` | 输入有害请求 → 输出拒绝与替代建议 | negatives/harmful |

## 2. 数据格式（JSONL，每行一个样本）

```json
{
  "type": "analyze",
  "instruction": "请评审以下 AndroLua 社区代码片段，给出 kind/verdict/portability 判定与问题清单",
  "input": "<片段代码或引用>",
  "output": "{...判定与清单...}",
  "meta": {
    "manual_id": 76,
    "title": "隐藏输入法",
    "source_truncated": true,
    "verdict": "negative",
    "flags": ["chinese_identifier"],
    "confidence": "static_review"
  }
}
```

## 3. 生成规则（强制）

1. **排除截断主体**：`truncation == "preview_truncated"` 的帖子**不得**整体进 input
   （避免模型学残句）；仅可作 `refactor` 的错误位置示例（附说明）。
2. **harmful 只走 reject**：不得把 harmful 样本放进 `analyze` 的 output 正向部分。
3. 每条样本必须带 `meta.confidence`；`static_review` 样本在 README 声明不可作为设备事实。
4. 人工复核记录写入 `meta.reviewed`（人/日期）后才可作为**精选集**（training/curated/）。

## 4. 目录

```
training/
├── README.md        ← 本文档
├── schema.md        ← 字段明细（下述）
└── seed/            ← 人工精选 seed（analyze/refactor/reject 各若干）
```

## 5. 当前规模

- seed：analyze 6 / refactor 3 / reject 2 = 11 条（见 `seed/`，人工编写，作为后续批量生成的风格基准）；
- drafts：`drafts/batch_candidates.jsonl` 由 `tools/build_training.py` 生成（analyze 399 / reject 1，
  均带 `"draft": true`，需人工复核后才可并入精选集）。

## 6. 字段说明（schema 摘要）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | string | ✅ | analyze / refactor / reject |
| instruction | string | ✅ | 任务指令（与 input/output 构成 SFT 三元组） |
| input | string | ✅ | 输入（代码/请求）；reject 的 input 为有害请求原文 |
| output | string | ✅ | 期望输出（含理由） |
| meta.manual_id | int | ✅ | 源帖子 id，可回溯 catalog |
| meta.source_truncated | bool | ✅ | 是否预览截断 |
| meta.verdict/flags | string/list | ✅ | 判定标签（人工复核为准） |
| meta.confidence | string | ✅ | static_review / device_tested |
| meta.reviewed | string | 精选集必填 | 复核人 + 日期 |
