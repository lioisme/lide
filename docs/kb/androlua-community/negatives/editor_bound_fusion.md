# 负面主题卡：编辑器专属绑定（FusionApp 等宿主不可移植）

> reviewed: 2026-08-02 ｜ confidence: static_review ｜ 判罚依据：STANDARDS §4.1-4 / §5 editor_bound

## 1. 现象与危害

约 104 篇帖子依赖**特定编辑器的专属能力**（FusionApp 的 `uiManager`、主题 API、
`fa2` 编译期特性、`LuaActivity` 默认方法等），在 Pro/delua 宿主直接运行会缺符号/缺环境：

- `activity.uiManager.colors.*`、`activity.UiManager.getColors()`（FusionApp 主题系统）；
- `fa2` 专属的编译/库注入方式（`@other 本项目只支持有ViewPager2库的编辑器` 类声明）；
- 帖子自己声明"只支持 XXX 编辑器"（id 2868、3037、1301 等）。

## 2. 代表样本

```lua
-- id 4231（番组计划新番时间表）：
function 获取主题配色()
  local colors = activity.UiManager.getColors()   -- FusionApp 专属 API
  local 主题配色 = {}
  主题配色.主色 = Color.parseColor(colors.colorPrimary)
  ...
```

```lua
-- id 3208（好看的启动页）：
if this.packageName == "com.androlua" or this.packageName == "com.xnm.FusionApp" then
  ...   -- 按宿主分叉：本身就说明不可移植
```

## 3. 判定与改造 SOP

1. 识别词表：`FusionApp` / `fa2` / `uiManager` / `UiManager` / `ac.` 宿主包名 / "只支持XXX"；
2. 改造：主题色改由 `resources`/`setColor` 参数注入；宿主分叉用 `pcall` 探测能力而非包名硬编码；
3. 移植前先用 SKILL 双端验证清单排除环境差异。

## 4. 与 host_private 的分界

- `editor_bound`：编辑器产品级能力（主题、编译、UI 管理器）；
- `host_private`：宿主运行库类（`com.androlua.*`）；
- 两者可并存（如 id 2397 同时 `import "com.androlua.*"` 与 sora 编辑器 API）。

## 5. 相关帖子清单（抽查）

4231, 3208, 4558, 4508, 4210, 3037, 3047, 5131, 2868, 1301, 1591, 2515 等。
