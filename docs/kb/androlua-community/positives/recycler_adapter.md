# 正向主题卡：列表与适配器（LuaAdapter / RecyclerView / ViewPager2）

> reviewed: 2026-08-02 ｜ confidence: static_review ｜ portability: 混合（LuaAdapter=host_private，RecyclerView=androidx 需 libs）

## 1. 思路要点

- `com.androlua.LuaAdapter`：Pro 宿主内置的 Lua 列表适配器，配合 GridView/ListView 使用
  （id 2785 MD配色、4321 文件选择器、4687、4362 网易云歌单）；
- `androidx.recyclerview.widget.*` + Material 组件：FusionApp 时代的高频写法
  （id 1301 RecyclerView列表视图、3037 如何使用RecyclerView）；
- `ViewPager2` 无限轮播（id 2868）：自定义 Indicator + 一屏三页，github 开源移植。

## 2. 代表片段

```lua
-- id 4321（预览）：文件选择器 —— LuaAdapter 弹窗列表
function FileDialogSelect(StartPath, Callback)
  local File = luajava.bindClass "java.io.File"
  local LuaAdapter = luajava.bindClass "com.androlua.LuaAdapter"
  local ListView = luajava.bindClass "android.widget.ListView"
```

```lua
-- id 2868（预览）：ViewPager2 无限轮播（注明依赖）
-- 本项目只支持有ViewPager2库的编辑器，非fa2 请使用其他adapter
require "import"
local function main( ... )
```

## 3. 可复用方法

1. **适配器回调化**：把列表点击回调抽成参数（如 `FileDialogSelect(StartPath, Callback)`），
   让选择器可复用——这是社区帖中少见的**良好封装意识**（同时见 negatives 对照）。
2. **数据源-视图分离**：`LuaAdapter` 的 setData + 布局模板 + getView 回调模式，等价于
   Java 版 ArrayAdapter；理解它即可移植任意列表页。
3. **依赖声明先行**：ViewPager2/RecyclerView 依赖 androidx 库，宿主不自带时必须先 `loadDex`
   或 /libs 注入（id 2868 注释明示"需要下载 dex 放 libs"）——这正好命中 delua 的
   `/libs 机制`，移植时按 LIBS_SUPPORT_WORK_ORDER 处理。

## 4. 工程落地建议

- `LuaAdapter` Pro 源码存在；delua 待双端实测。**注意 `LuaRecyclerAdapter` 在 Pro 4.4.2
  源码中不存在**（id 3428/3734 用到的类移植时会失败，见 negatives/private_api_lockin）。
- androidx 组件一律走 /libs dex 注入，不在宿主内置范围。

## 5. 相关帖子

| id | 标题 | 可借鉴点 |
|----|------|---------|
| 4321 | FileDialogSelect 弹窗文件选择器 | 回调式封装 |
| 1301 | RecyclerView列表视图 | androidx 完整示例（FA2 环境） |
| 2785 | MD配色参考 | LuaAdapter + GridView 网格 |
| 2868 | Banner ViewPager2 无限轮播 | 轮播 + 自定义 Indicator |
| 3037 | 如何使用RecyclerView | Glide + AppCompat 组合 |
