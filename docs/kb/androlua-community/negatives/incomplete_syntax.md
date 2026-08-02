# 负面主题卡：残缺示例与语法错误（语料完整性）

> reviewed: 2026-08-02 ｜ confidence: static_review ｜ 判罚依据：STANDARDS §3 fragment / §4.1-2

## 1. 来源级事实（必须先于一切使用了解）

- 社区 list 接口只回传 **约 300 字符预览**，完整正文需要详情接口（未公开/未找到）；
- 全量 2144 篇中 **1740 篇文末语句不完整**（`preview_truncated`）；
- 另有帖子作者自述"没写完"（id 2449：*"为什么就这一点？（我没写完）"*）。

## 2. 语法错误样本（预览内即可确证）

```lua
-- id 2688：if json.result == "0"  ← 缺少 then/end（预览截断前已可见不完整）
-- id 1124：return diagonalPixels / (160 * dm.density); en  ← "en" 应为 "end"
-- id 46  ：loadstring([[prit("呵呵")]])                    ← 故意演示错误（示范帖）
```

```lua
-- id 2449（作者自述残缺）：
-- 为什么就这一点？（我没写完）...
activity.setTheme(android.R.style.Theme_DeviceDefault_Light)  ← 正文只有主题设置
```

## 3. 使用规则（本库内强制）

1. **引用任何片段前查 `catalog/post_<id>.json` 的 `truncation` 字段**；
2. `preview_truncated` 的帖子不得作为"完整范例"训练样本（微调时会造成**模型学会输出残句**）；
3. 语法可证伪的帖子直接 `negative`（`syntax_hint` 标志），标注错误位置；
4. 训练语料生成（training/）默认**排除** `truncation=preview_truncated` 的代码主体。

## 4. 相关帖子清单（抽查）

2688, 1124, 2449, 2947, 2129, 1356, 2412 等（全量见 catalog flags：incomplete_fragment /
syntax_hint / truncated_tail）。
