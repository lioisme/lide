# 正向主题卡：JSON 解析（cjson）与结构化数据

> reviewed: 2026-08-02 ｜ confidence: static_review ｜ portability: universal（`cjson` 为标准宿主扩展）

## 1. 思路要点

JSON 是社区帖最核心的数据交换格式。社区帖提供了两种主流用法：
- `import "cjson"` + `decode/encode`（id 1882、2688、5131）；
- 自绘字符串截取（id 2635 蓝奏云 Post 参数：适配 `'` 与 `"` 两种引号变体）。

## 2. 代表片段

```lua
-- id 2688（预览）：URL 解码后截取 JSON 对象再解码
import "java.net.URLDecoder"
local url = URLDecoder.decode(data)
msg = url:match("({.*})")
local json = import "cjson".decode(msg)
if json.result == "0" then ... end
```

```lua
-- id 5131（预览）：读写 JSON 文件（主题配置文件）
local json = require "cjson"
local function loadJson(path)
  local f = io.open(path, "r")
  local t = f:read("*a")
  f:close()
  return json.decode(t)
end
```

## 3. 可复用方法

1. **正则截取 + cjson 解码**：对非标准/嵌套 JSON 响应，先用 `match` 提取对象字面量再解码，
   比手写字符串切割健壮；但注意 `({.*})` 对含 `}` 的深层嵌套会误截，长文本建议直接全文解码。
2. **文件级 JSON 配置**：`io.open` + `read("*a")` + `json.decode` 是"把配置存进文件"的标准
   三段式；写回时 `json.encode` + `io.open(path,"w+")`。
3. **校验后使用**：解码后必须对关键字段判空/判型（`result == "0"` 这类判断），远程数据
   不可信是通用原则（id 2688 的票据校验已失效，见 negatives）。

## 4. 工程落地建议

- `cjson` 在 Pro/delua 宿主中为标准扩展（SYNTAX_MANUAL 有记录），可直接 `import "cjson"`；
- 大 JSON 的 `({.*})` 截取有性能与正确性风险，优先 `json.decode(全文)` + `pcall` 包裹。

## 5. 相关帖子

| id | 标题 | 可借鉴点 |
|----|------|---------|
| 1882 | JSON解析与启动统计 | 解析 + 统计的完整示例（含布局） |
| 2688 | 腾讯防水墙 | URL 解码 + match + cjson 链 |
| 5131 | 手册浅色主题替换 | 文件级 JSON 读写（io + cjson） |
| 2635 | 蓝奏云文件夹Post参数 | 引号变体适配与参数缓存 10 分钟 |
| 5066 | 蓝奏云解析 acw_sc__v2 | 参数解析进阶（预览截断） |
