# 正向主题卡：异步网络请求封装（com.androlua.Http）

> reviewed: 2026-08-02 ｜ confidence: static_review（代码面评审） ｜ portability: host_private（需双端实测）

## 1. 思路要点

社区帖子中「远程更新 / 远程公告 / 远程验证 / 远程布局」本质上都复用同一套模式：
**用 `com.androlua.Http` 异步 GET 一个接口 → 回调里解析 JSON → 驱动 UI / 逻辑**。
（来源：id 2311《远程请求》直接点破：*"很多人抄完公告不会更新，抄完更新不会公告，
本质上都是一样的，都是通过接口获取数据并进行处理"* —— 这是值得吸收的抽象思路。）

## 2. 代表片段（id 2129 预览）

```lua
import "com.androlua.Http" --异步请求
import "cjson"             --解析json
```

（正文截断于约 300 字符预览，完整版需详情接口；id 2091《远程一次性通知》为完整可运行范例，
使用 SharedPreferences 存储一次性标志位 + Http 拉取公告内容。）

## 3. 可复用方法（从多篇提炼）

1. **统一请求封装**：把 url / params / callback 收进一个函数，避免每个页面手写 `Http.get`。
2. **回调线程语义**：Http 回调在子线程执行，改 UI 需 `Handler`/`runOnUiThread`（id 3996 示范了
   Handler 构造 + UI 通讯）。
3. **防抖与降级**：公告/更新拉取失败时用本地缓存兜底；一次性通知用 `SharedPreferences` 标志位
   防重复弹窗。
4. **校验票据**：id 2688 说明 `cjson` 解出的 `result` 字段要判断后再走逻辑——任何远程数据
   都必须先校验再使用（该帖自己的票据校验接口已失效，属反例，见 negatives）。

## 4. 工程落地建议（delua 视角）

- `com.androlua.Http` 在 Pro 4.4.2 源码 `com/androlua/Http.java` 中确认存在；delua 是否同签名
  需设备实测（待 `androlua-project-validation` 双端验证后回填）。
- 新工程更推荐自带纯 Lua 实现（`luajava.bindClass("java.net.HttpURLConnection")`）以摆脱宿主绑定；
  若接受绑定，需同时处理回调线程与超时。

## 5. 相关帖子

| id | 标题 | 可借鉴点 |
|----|------|---------|
| 2311 | 远程请求 | 接口抽象思维（公告=更新=请求） |
| 2091 | 远程一次性通知 | SharedPreferences 一次性标志 + 通知 |
| 3996 | 一个检查更新方法 | Handler + LuaAsyncTask 异步 |
| 2129 | API接口大全及使用 | 请求 + cjson + MediaPlayer 混用示例 |
| 1739 | 码易支付API对接 | 支付回调与验签流程 |

## 6. 风险提示

- 预览截断：以上 id 的完整源码大多在约 300 字符处截断，**引用前需回源核对**。
- 回调线程安全与弱网处理在多数帖子中缺失，属于社区通病（见 negatives/global_pollution 等）。
