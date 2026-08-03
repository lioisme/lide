# 正向主题卡：系统小工具函数集（状态栏/夜间模式/屏幕尺寸/输入法）

> reviewed: 2026-08-02 ｜ confidence: static_review ｜ portability: android_api（可运行于 Pro/lide）

## 1. 思路要点

「小工具/实用代码」分类里有大量**单函数工具**，实现简单但覆盖 Android 常用能力，
是社区价值密度最高的部分（适合直接进工程工具库）：

| 能力 | 代表 id | 要点 |
|------|---------|------|
| 状态栏/导航栏高度 | 6 | `getIdentifier("status_bar_height",...)` + `getDimensionPixelSize` |
| 物理屏幕尺寸 | 1124 | DisplayMetrics + density 换算（⚠ 原帖 `en` 拼写错误，见 negatives） |
| 夜间模式判断 | 1195 | `configuration.uiMode` 与 `UI_MODE_NIGHT_YES` 比对 |
| 隐藏输入法 | 76 | InputMethodManager.hideSoftInputFromWindow（⚠ 中文命名） |
| Drawable 着色 | 100 | BitmapDrawable + PorterDuffColorFilter(SRC_ATOP) |
| Base64 | 55 | `android.util.Base64` encode/decode（⚠ 中文命名） |
| Root 检测 | 454 | `com.androlua.util.RootUtil`（宿主私有，需实测） |
| 系统语言 | 191 | `Locale.getDefault().getLanguage()/getCountry()` |

## 2. 代表片段

```lua
-- id 1195（预览）：夜间模式判断
function isNightMode()
  local configuration = activity.getResources().getConfiguration();
  return configuration.uiMode == configuration.UI_MODE_NIGHT_YES
end
```

```lua
-- id 100（预览）：Drawable 着色
function ToColor(path, color)
  local aa = BitmapDrawable(loadbitmap(tostring(path)))
  aa.setColorFilter(PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP))
  return aa
end
```

## 3. 可复用方法

1. **getIdentifier 反射式资源查找**：状态栏高度等系统资源的标准取法；
2. **版本分支优先**：夜间模式判断用 `(uiMode & UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES`，
   比原帖的 `==` 更稳（原帖还写了 `+1==`、`-1==` 的诡异兜底——见 negatives/syntax）；
3. **工具函数单文件化**：把这类函数收进 `utils.lua` 模块（`local utils = {}` + `return utils`），
   取代帖子里"复制到每页"的传播方式。

## 4. 工程落地建议

- 全部公开 API（除 RootUtil），lide 可运行；移植时按 §3.3 模块化；
- `loadbitmap` 是宿主扩展（Pro/lide 有），签名需实测。

## 5. 相关帖子

6, 1124, 1195, 76, 100, 55, 191, 454, 218, 154, 208, 173 等（全量见 catalog）。
