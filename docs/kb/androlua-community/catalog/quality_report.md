# 社区语料质量报告（全量静态扫描）

> 生成：`tools/analyze.py` ｜ 口径：TASK_ORDER.md + STANDARDS.md ｜ `static_review`，未实测

## 0. 来源级事实（重要）

- 全部 2144 篇来自社区 list 接口 `manual_content` 预览（约 300 字符上限），完整正文需详情接口（未公开/未找到）。
- 文末语句不完整（preview_truncated）占比：1740/2144

| 截断状态 | 数量 |
|---|---|
| complete_or_unverifiable | 404 |
| preview_truncated | 1740 |

## 1. 三轴分布

### kind

| 值 | 数量 |
|---|---|
| code_demo | 1439 |
| text_note | 461 |
| fragment | 234 |
| reference_sheet | 5 |
| announce | 5 |

### verdict

| 值 | 数量 |
|---|---|
| positive | 1128 |
| neutral | 573 |
| negative | 435 |
| harmful | 8 |

### portability

| 值 | 数量 |
|---|---|
| android_api | 1054 |
| universal | 915 |
| editor_bound | 104 |
| host_private | 67 |
| obsolete | 4 |

## 2. 结论

- 语料以微型片段为主（预览受限），「positive」为**初筛候选**，不代表可生产质量；入库正/负面主题卡前必须人工复核原文与设备行为。
