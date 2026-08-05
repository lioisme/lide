# 社区语料质量报告 v2（全量正文静态扫描）

> 生成：`tools/analyze.py` ｜ 口径：TASK_ORDER.md + STANDARDS.md ｜ `static_review`，未实测

## 0. 来源级事实（v2 修正）

- 本版改为**读取磁盘完整正文**（lua_files/**/*.lua，12.5MB/438K 行）；
- v1 所称「300 字符预览截断 1740/2144」系接口列表预览限制，**不是正文事实**，已废弃；
- 真实文末截断（truncated_tail）与空文件计数见下表。

| 截断状态 | 数量 |
|---|---|
| complete | 1785 |
| truncated_tail | 359 |

## 1. 三轴分布

### kind

| 值 | 数量 |
|---|---|
| code_demo | 1950 |
| text_note | 154 |
| fragment | 26 |
| reference_sheet | 11 |
| announce | 3 |

### verdict

| 值 | 数量 |
|---|---|
| positive | 1005 |
| negative | 922 |
| neutral | 209 |
| harmful | 8 |

### portability

| 值 | 数量 |
|---|---|
| android_api | 1067 |
| universal | 602 |
| host_private | 273 |
| editor_bound | 170 |
| obsolete | 32 |

## 2. 结论

- verdict 均为**静态初筛**（规则引擎），不代表可生产质量；
- 正/负面主题卡入库前必须人工复核原文与设备行为；
- 规则引擎召回面（flags 命中分布）可查 catalog/post_*.json。
