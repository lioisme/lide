# 正向主题卡：自绘与动画（LuaDrawable / ObjectAnimator / 属性动画）

> reviewed: 2026-08-02 ｜ confidence: static_review ｜ portability: 混合（LuaDrawable=host_private，动画=android_api）

## 1. 思路要点

社区「自绘制」分类（76 篇）是 **Lua 驱动 `android.graphics` 画布**的高频玩法：
- `com.androlua.LuaDrawable`：把 Lua 代码变成 Drawable（id 826、1161、2692、3069、5123、4324 等）；
- 动画封装函数（id 1616）：一键导入常用动画（RotateAnimation / ObjectAnimator / 插值器）；
- 属性动画概述（id 1116）与补间动画概述（id 1117）是系统化说明帖。

## 2. 代表片段

```lua
-- id 2692（预览）：贪吃蛇 —— 自绘游戏骨架
local LinearLayout = luajava.bindClass "android.widget.LinearLayout"
local LuaDrawable  = luajava.bindClass "com.androlua.LuaDrawable"
local Paint = luajava.bindClass "android.graphics.Paint"
local Path  = luajava.bindClass "android.graphics.Path"
```

```lua
-- id 1616（预览）：动画封装（节选）
import "android.view.animation.LinearInterpolator"
import "android.view.animation.Animation"
import "android.view.animation.RotateAnimation"
import "android.animation.ObjectAnimator"
```

## 3. 可复用方法

1. **Drawable 即代码**：`LuaDrawable` 用 Lua 函数定义 `onDraw` 逻辑，适合图表、游戏棋盘、
   动态图标；比一堆 View 组合更省内存，但刷新频率与线程需控制。
2. **动画三件套**：插值器 + 动画对象 + `startAnimation`；属性动画（ObjectAnimator）更平滑、
   可组合；社区帖常用「封装函数 + 一行调用」模式，适合做工程工具库。
3. **游戏主循环**：`Handler.postDelayed` 或 Ticker 驱动重绘（id 2692 贪吃蛇、4324 翻翻看）。

## 4. 工程落地建议

- 自绘的 `Paint`/`Path`/`Canvas` 均为公开 API，lide 可直接运行；`LuaDrawable` 类名 Pro 确认
  存在，lide 待双端实测；
- 动画封装帖（1616）要求 `import "dh"` 自定义模块，跨宿主不可用——移植时应内联依赖。

## 5. 相关帖子

| id | 标题 | 可借鉴点 |
|----|------|---------|
| 2692 | 贪吃蛇 | LuaDrawable + Handler 游戏循环 |
| 3069 | 绘制正n边形 | 数学绘制（Polygon） |
| 1616 | 动画封装函数等 | 动画封装模块化思想 |
| 1116/1117 | 属性/补间动画概述 | 系统化教程 |
| 4324 | 翻翻看（Matching Pairs） | 自绘卡片游戏 |
