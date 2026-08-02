# 正向主题卡：WebView 使用与下载（WebViewClient / WebChromeClient / DownloadListener）

> reviewed: 2026-08-02 ｜ confidence: static_review ｜ portability: android_api（可运行于 Pro/delua）

## 1. 思路要点

id 1464《WebView使用指南：WebViewClient，WebChromeClient和Download》是社区里少见的
**「看这一篇就够了」式系统教程**：把三类核心回调接口
（`WebChromeClient`、`WebViewClient`、`DownloadListener`）绑定到 Lua 全局，作为
「以 WebView 为主的工程初始模板」。
id 1360《WebView各种下载》覆盖直链下载 / 网页下载 / 下载进度监听，是下载链路的完整参考。

## 2. 代表片段（id 1464 预览）

```lua
local WebChromeClient = luajava.bindClass "android.webkit.WebChromeClient"
local WebViewClient   = luajava.bindClass "android.webkit.WebViewClient"
local DownloadListener= luajava.bindClass "android.webkit.DownloadListener"
```

（正文在约 300 字符处截断，完整模板需回源。）

## 3. 可复用方法

1. **回调解耦**：三个接口分别在各自入口设置（setWebChromeClient / setWebViewClient /
   setDownloadListener），页面级逻辑只订阅自己关心的回调——这是模板化的正确姿势。
2. **渲染进程知识**（id 2383/2384 研究结论，虽然帖子是"屏蔽粘贴板"应用，但结论通用）：
   - webview 未加载时 `window` 等全局已可访问（DOM 未渲染）；
   - 每次 `loadUrl` 开新渲染进程（标签隔离）；
   - **未加载时注入的全局变量在页面加载后依然存在** → 可在 `loadUrl` 前用
     `addJavascriptInterface`/`evaluateJavascript` 预注入脚本（替代 `window.alert` 等）。
   这是实现「网页增强」的通用原理，注意用途边界（勿用于隐私窃取）。
3. **下载进度监听**（id 1360）：`DownloadListener.onDownloadStart` 拿 url 后自己接管
   `HttpURLConnection` 下载并回调进度，避免系统下载器黑盒。

## 4. 工程落地建议

- 全部为公开 Android API（`android.webkit.*`），Pro/delua 可直接运行；
- 下载接管时注意 Android 10+ 分区存储与 `WRITE_EXTERNAL_STORAGE` 变化，社区帖多为旧版
  写法，落地需按目标 SDK 修正（见 negatives/obsolete 主题）。

## 5. 相关帖子

| id | 标题 | 可借鉴点 |
|----|------|---------|
| 1464 | WebView使用指南 | 三类回调的模板化工程结构 |
| 1360 | WebView各种下载 | 直链/网页下载 + 进度监听 |
| 2383/2384 | WebView渲染机制/屏蔽粘贴板 | 渲染进程与预注入原理（结论可复用） |
| 1597 | WebView相关（预览） | 待回源补充 |

## 6. 风险提示

- 预览截断影响完整模板的引用；预注入脚本涉及对第三方网页的修改，注意合规边界。
