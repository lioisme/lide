---
name: community-code-audit
description: Use when auditing, cataloging, or building a knowledge base from a large corpus of community-sourced code samples (snippets, forum posts, tutorial code) — classifying them by content type, quality verdict (positive/negative/neutral/harmful), and portability, then producing machine-readable catalog + human-reviewed theme cards + optional LLM training data. Ecosystem-agnostic: works for Lua/AndroLua, Python, JS, shell, etc. Trigger keywords: 社区代码审计, 语料标注, 帖子正负标注, 代码知识库, corpus audit, verdict classification, 训练语料, triage shortlist. Do NOT use for single-file review or for running/testing projects on devices (use androlua-project-validation for AndroLua device testing).
---

# 社区代码语料审计与知识库建设（通用）

对「社区/论坛/教程站点批量抓取的代码样本」做系统化审计：**内容质地（kind）× 质量评价（verdict）× 可移植性（portability）** 三轴标注，输出全量机器清单、聚合报告、人工复核短名单与可选训练语料。本 SKILL 与语言生态无关——把「规则库」替换为目标生态的关键词即可复用到 Lua、Python、JS、Shell、Kotlin 等任何语料。

## 0. 铁律

1. **全量正文，不做预览**：判定必须基于磁盘上的完整文件内容。若元数据（DB/API 预览）与磁盘正文不一致，以磁盘为准——先验证（编码、行数、文末截断）再扫描。
2. **静态评审 vs 设备实测分离**：本 SKILL 只做静态文本评审（`static_review`）；涉及运行行为/兼容性的结论必须走目标生态的实测流程，禁止以静态结论冒充实测。
3. **正负双轨都要产**：positive（可复用方法）+ negative（反例/避坑）缺一不可，且每条都必须有可回溯片段（文件路径/行号/原文引文）。
4. **规则要校准，校准要留痕**：任何自动规则的误报/漏报修正，必须：复现样例 → 改规则 → 全量重跑 → 在报告中记录「为何改、影响多少条」。
5. **harmful 只识别归档，不复制攻击面**：绕过验证/Hook/破解类内容仅做特征识别与危害描述，不放可执行攻击面，也不进正面库。
6. **语料只读**：原始语料目录不迁移、不修改；所有产物写进独立的 KB 目录。

## 1. 工作流（7 步）

### Step 1 语料盘点与事实核验

- 列出语料根目录与文件数（`glob`/`find`），确认命名规律（是否 `^(\d+)_` 前缀可关联元数据 DB）。
- **编码核验**：全量文件按目标编码（通常 UTF-8 严格模式）读取，统计失败数；生成「来源级事实」表：
  - 文件数 / 总字节 / 总行数 / 空文件数 / 文末截断数（`truncated_tail`：以 `)` `}` `end` `;` 等收尾为 complete，其余需人工抽查分类）。
- 元数据 DB（如有）先抽样 3–5 条核对：id 对应、标题、作者、时间、分类。
- ⚠ 常见坑：接口预览 ≠ 正文（v1 教训：1740/2144 被误判「截断」，实际磁盘正文完整）；控制台 GBK 显示乱码 ≠ 文件乱码（读文件时用 `encoding="utf-8"`，打印到 UTF-8 文件再查看）。

### Step 2 定义三轴体系（先写标准文档）

在 KB 目录建立 `TASK_ORDER.md`（任务书+验收口径）+ `STANDARDS.md`（判定细则），三轴必须先行定义，否则跨批次无法一致：

| 轴 | 枚举（示例） | 判定依据 |
|---|---|---|
| kind（质地） | code_demo / text_note / reference_sheet / announce / fragment | 有无可执行语句、是否清单型、是否公告水帖、是否文末残缺 |
| verdict（质量） | positive / negative / neutral / harmful | §3 规则 |
| portability（可移植） | universal / target_api / host_private / editor_bound / obsolete / unverifiable | 依赖标准库、平台公开 API、宿主私有 API、特定编辑器能力、已废弃 API |

### Step 3 写分析器（analyze.py，纯标准库）

单文件、可重复执行、全量重跑幂等。结构：

```
常量区：源目录 / KB 目录 / 正则规则 / 停用词表 / 人工复核清单(HARMFUL_OVERRIDES)
函数：read_text / classify_kind / classify_portability / verdict / truncation_fact
主流程：遍历文件 → 三轴 + flags → 写 catalog/post_<id>.json（ensure_ascii=False）
       → aggregate.json → triage_shortlist.md → quality_report.md
```

**规则设计要点（来自实战教训）**：

- **全局污染计数**：只统计「列 0 起、顶层、非 `local`」的赋值；缩进的 layout 表键值对（`layout_width='fill'`）不算；`x=--[[...]]` 隐藏代码惯用法不算。
- **危险/恶意正则必须语义收窄，宁漏勿错**：
  - 裸 `注入` 会误伤「JS注入」（WebView 常规手法）；要带靶标：`注入.{0,6}(dex|so|进程|微信|hook|dll)` 等；
  - 裸 `load` 会误伤 `Glide.load(url)`；远程执行只认 `loadstring|dofile` + URL 参数；
  - 裸 `多开` 会误伤「多开几个函数」这类正常句法；要带对象：`多开.{0,4}(应用|微信|QQ|游戏|分身)`；
  - `chen\b`、`sora\b` 这类编辑器名单独出现即判 editor_bound 会大量误报，需要上下文。
- **verdict 组合逻辑要分级**：`harmful` 只由强信号（`harmful_elevated` / 人工复核清单）驱动；
  `remote_exec`/`os_exec`/`hardcoded_secret` 单独出现 → negative（风险写法），不升级 harmful；
  `private_api`/`editor_bound`/`obsolete`/中文标识符/全局污染 → negative；干净 code_demo → positive。
- **人工复核清单（override）**：正则召回不了、但人工确认危害的条目，用 `manual_id → 理由` 字典硬编码，
  产出时打 `manual_override` flag 保留审计溯源（实战：8 篇 harmful 中 6 篇靠 override 兜底）。
- 每个 `catalog/post_<id>.json` 必须含：三轴、flags、source_file（相对路径）、line/char 数、confidence。

### Step 4 全量跑 + 抽样验证（迭代校准）

1. 跑 analyze.py，看总分布是否合理（例如 43% negative 要能解释得通）。
2. **写抽样脚本**（seed 固定可复现）：positive/negative/neutral/harmful 各抽 N 条打印标题+flags。
3. **逐条核查误报**：对每条可疑的，打印命中的原文上下文（`max(0,start-30):end+30`），确认规则误伤还是原文如此。
4. 修改规则 → 重跑 → 重抽样，直到：正面样本看着没问题、负面样本理由站得住、harmful 列表人工逐条确认。
5. 校准过程写入 quality_report 的版本说明。

### Step 5 写主题卡（人工产物）

- `positives/<主题>.md`：思路要点 / 代表片段（带 id）/ 可复用方法 / 风险提示。
- `negatives/<反例主题>.md`：触发特征 / 判罚依据 / 修正写法。
- `negatives/harmful/`：只写识别特征 + 危害，不写攻击面。
- 卡片头部 YAML 必须含 `reviewed`（人/日期）+ `confidence`（static_review）。
- 卡片示例字段：`manual_id / title / source_file / kind / verdict / portability / confidence / tags / summary / evidence(原文片段) / reference`。

### Step 6 知识库总览文档

写 `KNOWLEDGE_BASE.md`：统计总览 + kind×verdict 矩阵 + **用途域×正负标注**分节（每个用途域：样本数、正面代表 id、负面反例 id+理由、可复用方法）+ 负面专项表 + harmful 专项表 + 入库 DoD + 已知边界声明。

### Step 7 收尾（DoD 核对）

- [ ] catalog/post_*.json 全量存在（计数 = 语料总数）
- [ ] aggregate.json / quality_report.md / triage_shortlist.md 已生成且数字一致
- [ ] positive/negative/harmful 三类主题卡都有且含原文片段
- [ ] README.md 统计已更新 + 变更登记表追加一行
- [ ] 规则校准过程有留痕（report 版本说明）
- [ ] 明确标注 static_review，未实测事项列出

## 2. 规则库模板（通用正则骨架）

```python
# —— kind ——
INCOMPLETE_MARK_RE = re.compile(r"(没写完|未完|待补|示例从略|未完待续|……)", re.I)
PLAYED_KEYWORDS = re.compile(r"\b(function|end|local|import|require|return|for|while|if|setContentView)\b")

# —— portability ——
HOST_PRIVATE_RE   = re.compile(r"(com\.androlua|activity\.getLuaDir|LuaDexLoader|LuaAdapter)", re.I)  # 宿主私有
EDITOR_BOUND_RE   = re.compile(r"(FusionApp|fa2\b|uiManager|droidscript|只支持.*编辑器)", re.I)         # 编辑器专属
TARGET_API_RE     = re.compile(r"\b(android|androidx|com\.google\.android|java\.)[A-Za-z_.]")           # 平台公开 API
OBSOLETE_RE       = re.compile(r"(已失效|已被移除|不支持|deprecated)", re.I)

# —— verdict flags ——
CHINESE_IDENT_RE   = re.compile(r"(?m)^\s*(local function|function)\s+[\u4e00-\u9fff]|^\s*[\u4e00-\u9fff]{2,10}\s*=")
GLOBAL_ASSIGN_RE   = re.compile(r"(?m)^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*")   # 仅列 0 顶层
HARMFUL_RE = re.compile(  # 语义收窄，宁漏勿错
    r"(破解|绕过|白嫖).{0,8}(卡密|授权|会员|验证码|原版|收费|付费)|"
    r"(卡密|验证码|滑块).{0,8}(破解|绕过|识别)|"
    r"网盘.{0,8}(破解|绕过|直链|无限速)|抢红包|自动抢|抢单|短信轰炸|免流|盗取|改机|云机|"
    r"Xposed.{0,10}(注入|hook)|hook.{0,10}微信|无障碍.{0,10}(注入|hook)", re.I)
REMOTE_EXEC_RE = re.compile(
    r"(?:loadstring|dofile)\s*\([^)]*(?:https?://|url\s*=|dl|down)|"
    r"http[s]?://[^\"'\s]+\.(?:dex|lua|so)\b|loadDex\s*\([^)]*http", re.I)
SECRET_RE = re.compile(r"(password|passwd|pwd|secret|api[_-]?key|token)\s*[:=]\s*[\"'][A-Za-z0-9+/=_\-]{8,}[\"']", re.I)
```

> 复用到新生态时：替换「宿主私有/编辑器专属/危险词」为目标生态的高频私有类、框架名与违规手法词；中文标识符规则改为目标语言的反模式（如 Python 的驼峰全局、JS 的 var 泄漏）。

## 3. 环境要点

- 分析器只用 **Python 标准库**（io/re/os/json/glob），不依赖 pip；Windows 下直接用 `python.exe` 全路径（如 `D:\tools\msys64\ucrt64\bin\python.exe`）。
- 打印中文时控制台可能是 GBK：脚本内写文件用 `encoding="utf-8"`，查看用 UTF-8 读回；或先 `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8`。
- JSON 输出必须 `ensure_ascii=False, indent=1`，保证人类可读、diff 友好。

## 4. 与设备验证 SKILL 的关系

- 本 SKILL（静态审计）输出 positive/negative 标签与片段，供工程侧引用；
- 工程侧结论（如「lide 可运行」「Pro 兼容」）必须由 `androlua-project-validation` 双端实测后落 `docs/kb/` 工程知识库，两者不互相污染。
