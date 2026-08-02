# 负面主题卡：全局变量污染与无结构脚本

> reviewed: 2026-08-02 ｜ confidence: static_review ｜ 判罚依据：STANDARDS §4.1-2/3

## 1. 现象与危害

社区帖普遍把**临时值直接写进全局作用域**：

- 变量不声明 `local`，跨函数/跨页面互相踩踏（重名覆盖极难排查）；
- 全部逻辑平铺在顶层（布局表 + 回调 + IO 混写），无函数分层、无模块边界；
- 偶发"全局名当参数"的引用错误（如 id 309 用 `activity.Context` —— 应为 `activity.getContext()`）。

## 2. 代表样本

```lua
-- id 298（美观卡片布局）：函数内直接写全局
function 卡片(a,b,c)
  ffrr= { CardView; id=a; ... }          -- ffrr 无 local → 全局
  ...
end
```

```lua
-- id 1890（反寂寞人）：全局遍布
-- id 1490（文字图标）：全屏框架 = {...} 顶层全局布局表
-- id 309：wifiManager=activity.Context.getSystemService(...)  ← activity.Context 错误引用
```

## 3. 修正写法（SOP）

```lua
-- 改前：ffrr = {...}（全局）
-- 改后：
local function makeCard(id, a, b, c)
  return { CardView; id = id, ... }
end
```

1. 一切内部变量加 `local`；函数返回值显式传递；
2. 布局表仅在需要全局唯一（如 activity 级 `layout` 变量）时定义，且命名前缀化；
3. 宿主注入的全局（`activity`/`this`/`service`/`import`）不在本规则内（白名单见
   `tools/analyze.py STOP_GLOBALS`）。

## 4. 与正向写法的分界

- 判别线索：`global_pollution` 标志 = 无 `local` 的顶层赋值 ≥ 8 处；
- 同一帖子若方法本身有价值但写法是全局污染，应**降级进 negatives 或改写为 positive 片段**，
  不得原样进 positives。

## 5. 相关帖子清单（抽查）

298, 1490, 1890, 309, 1230, 1490, 4252 等（全量见 catalog flags）。
