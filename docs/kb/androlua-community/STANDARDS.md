# 社区帖子判定标准（STANDARDS）

> 本文件是 `docs/kb/androlua-community/` 的**判定总库**。
> 自动分析与人工评审必须遵循同一套规则，以保证跨批次一致性、可审计、可用于定标评审。

---

## 1. 目标对象与边界

- 对象：`reference/代码手册社区/lua_files/**` 下的每一篇帖子源码。
- 只对**文本内容**做评定；`✅实测` 级结论必须经设备验证（SKILL `androlua-project-validation`），
  未经实测一律标注 `未实测` 或 `静态评审`。

## 2. 三轴标签口径

见 TASK_ORDER §4。三种轴的值必须与源码事实对应，禁止因「难评」而填「不适用」：

| 轴 | 枚举 | 判定依据 |
|----|------|---------|
| kind | code_demo / text_note / reference_sheet / announce / fragment | §3 |
| verdict | positive / negative / neutral / harmful | §4 |
| portability | universal / android_api / host_private / editor_bound / obsolete / unverifiable | §5 |

## 3. kind 判定细则

- `code_demo`：含至少一条指令（赋值/调用/函数定义），且语句完整度 ≥ 1 条。
- `text_note`：纯教学/说明段落（含 Markdown、中文长文），无可执行主逻辑。
- `reference_sheet`：清单型（权限、路由表、颜色表、API 列表）。
- `announce`：公告、广告、水帖、无内容。
- `fragment`：内容在语句中部断裂（以 `en`, `impor` 等截断串为证据，或注释明确「没写完」）。

> 说明：`kind=fragment` 的帖子不参与 positives 记录，但可在 negatives 中作为「不完整的
> 社区示例」负面样本。

## 4. verdict 判定细则（正/负/中性/危险）

### 4.1 直接判 `negative` 的情形（示意非穷尽）

1. **命名问题**
   - 中文/拼音标识符作为函数/函数名/变量名（如 `function 隐藏输入法(...)`）；
   - 无意义单字母全局变量、`a`,`b`,`c` 大面积；命名不达意；
   - 全局变量泄漏（不使用 `local`，将临时值写到全局）。
2. **逻辑/语法问题**
   - 明显语法错误（`en` 而非 `end`、括号/引号不匹配、用 `;` 当语句分隔的 Lua）；
   - `loadstring` 动态执行的不可控片段且无解释；
   - 死代码/明显错误的注释与实现不符。
3. **架构问题**
   - 全部逻辑写在顶层全局作用域、无函数分层；
   - 把 UI、业务、IO 写在一个脚本；无任何封装意图。
4. **平台绑定问题（明确不可移植）**
   - 直接使用 `com.androlua.*` 私有类且无说明/降级路径；
   - 直接依赖特定编辑器全局对象（`ui`、`ds.*`、`Corner`、`LuaActivity` 默认方法、`this`）且不兼容 lide/Pro 默认启动页方式。

### 4.2 判 `positive` 的情形

- 解决了具体问题且代码可独立运行；思路表述清晰、可迁移；
- 封装合理（简单函数包裹、入参出参明确、注释必要不铺张）；
- 使用标准 Lua 库完成功能（table/string/json 解析、IO、时间）；
- 演示了 Android 平台能力（Intent、ContentProvider、WindowManager、传感器…）且 API 选择正确的是通用 public API。

### 4.3 判 `harmful`（注意：负向类型之一，但语义隔离）

- 绕过验证（远程验证破解、VIP 授权绕过、注入）；
- Hook / Root 提权 / 辅助功能滥用（无障碍抓数据、微信枪红包类）；
- 诱导用户安装来源不明 APK / 拉取远程代码执行（`loadstring` 远程内容、io.popen 远程）。
- 处理原则：**识别与归档，不用于新工程**；`negatives/harmful/` 中只做「识别特征 + 危害」描述，不放可执行攻击面。

### 4.3 `neutral`

不属于上述且可正常运行的普通片段。

## 5. portability 判定

| 档位 | 触发关键特征 |
|------|------------|
| universal | 仅纯 Lua 标准库 |
| android_api | 引 `android.*`/`java.*` 公开类，无宿主/编辑器专用词 |
| host_private | 含 `com.androlua` / `activity.getLuaDir` / `activity.getLuaActivity` / `LuaActivity` 默认场量 |
| editor_bound | `FusionApp`、`fa2`（含 `uiManager`、`loadlayout` 之外的编辑器 API）或明确写「只支持XXX编辑器」 |
| obsolete | 引用的 API 已在高版本被移除（`PorterDuff.Mode` 旧枚举等）、或注明已失效 |
| unverifiable | fragment/无上下文/无法判断 |

> 注：`loadlayout` / `activity` / `this` 属于 AndroLua **宿主标准**（Pro 与 lide 均提供）
> → 归 `android_api`；只有当还引用了「编辑器本身专属」能力时才升级为 `editor_bound`。

## 6. 出评条目字段（模板）

```yaml
manual_id: 12345
title: 取标题
source_file: "实用代码/12345_xxx.lua"
kind: code_demo
verdict: negative          # positive | negative | neutral | harmful
portability: host_private
confidence: static_review   # static_review | device_tested | unverified
tags:                       # 可选 lide 专属标签
  - chinese_identifier
  - global_pollution
  - androlua_private
summary: 一两句结论
evidence:
  - "所属关键片段（引文）"
reference: 人工复核人/日期
```

## 7. 与 androlua-lua 子库关系

- 本库输出**负面/正面标签与片段**供工程侧引用；
- 工程侧（`docs/kb/androlua-lua/`）只记录 **已实测**的结论；两者不互相污染：本库默认 `static_review`。