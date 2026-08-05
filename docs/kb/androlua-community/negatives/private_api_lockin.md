# 负面主题卡：宿主私有 API 绑定（com.androlua.* 版本锁死）

> reviewed: 2026-08-02 ｜ confidence: static_review（存在性已对 Pro 4.4.2 源码核对） ｜ 判罚依据：STANDARDS §4.1-4 / §5

## 1. 现象与危害

60+ 篇帖子直接 `import`/`bindClass` **`com.androlua.*` 私有类**。这些类随客户端版本
**不存在、改名或签名变化**，导致跨版本/跨宿主（lide）不可运行：

- `LuaAdapter` / `LuaAsyncTask` / `LuaDexLoader` / `LuaDrawable` / `LuaEditor` /
  `LuaFragment` / `LuaLexer` / `LuaRecyclerAdapter` / `LuaTimer` / `LuaTimerTask` /
  `LuaTokenTypes` / `LuaUtil` / `LuaWebView` / `Ticker` / `Http` / `RootUtil` / `Welcome`
- ⚠ **`LuaRecyclerAdapter` 在 AndroLua Pro 4.4.2 源码中不存在**（已核对
  `docs/reference/androlua/AndroLua_pro-master`）——id 3428、3734 引用即失败。

## 2. 代表样本

```lua
-- id 4321：function FileDialogSelect(StartPath, Callback)
--   local LuaAdapter = luajava.bindClass "com.androlua.LuaAdapter"  ← 私有类

-- id 4225：LongProgressBar=luajava.bindClass("com.androlua.LuaDexLoader")
--   (this.applicationContext).loadDex(...)  ← 私有类 + 运行时 dex 下载

-- id 3734/3428：bindClass "com.androlua.LuaRecyclerAdapter"  ← Pro 源码不存在

-- id 2397：import "com.androlua.*" （SoraEditor 演示帖，直接全量导入私有包）
```

## 3. 判定与改造 SOP

1. **先判类存在性**：查 `docs/reference/androlua/` 源码树（`grep class LuaXXX`）；
2. 不存在 → 用公开 API 重写（如 RecyclerView + 自带 Adapter 实现）；
3. 存在但签名未知 → 双端实测（`androlua-project-validation` SKILL）后再入工程库；
4. 任何新工程**禁止首选项**使用私有类：优先 `android.*` / `java.*` 公开 API，私有类仅作
   宿主兼容层兜底并加 `pcall` 降级。

## 4. 边界说明

- 判别 `host_private` ≠ 一定失败：`Http`/`LuaDrawable`/`LuaTimer` 等在 Pro 4.4.2 确实存在，
  但**签名版本敏感**，一律标记 `⚠需双端实测`；
- 本文档与 catalog 的 `host_private` 统计（67 篇）即此类风险面。

## 5. 相关帖子清单（全量 60 篇见 catalog，代表）

4321, 2785, 2698, 4225, 3428, 3734, 2397, 4152, 4207, 2091, 2311, 2129, 2777,
2045, 454, 3996, 1410, 2657, 2692, 3238, 4324, 4698, 4697。
