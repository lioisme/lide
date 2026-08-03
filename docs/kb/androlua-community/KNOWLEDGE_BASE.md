# AndroLua 社区项目知识库（类型 × 用途 × 正负标注）

> 文档编号：KB-COMM-002 ｜ 数据源：`docs/reference/代码手册社区/lua_files/`（2144 篇全量）
> 判定口径：`TASK_ORDER.md` + `STANDARDS.md`（三轴体系）｜ 置信度：`static_review`（静态文本评审，未实测）
> 数据生成：`tools/analyze.py` → `catalog/`（post_*.json / aggregate.json / quality_report.md / triage_shortlist.md）

---

## 0. 用法指南（How to use this KB）

| 你想做的事 | 去哪里 |
|---|---|
| 找某个用途域（网络/UI/存储…）的**正面写法与代表帖** | 本文档 §2 用途域表 → `positives/` 主题卡 |
| 查某篇帖子的三轴判定 | `catalog/post_<id>.json`（机器可读） |
| 找**反例与避坑**（命名/全局污染/平台绑定） | 本文档 §3 → `negatives/` 主题卡 |
| 识别**危险帖**（验证绕过/Hook/网盘破解） | 本文档 §4 → `negatives/harmful/` |
| 生成 LLM 训练语料 | `training/`（schema + jsonl） |

**统计总览（v2 全量正文扫描，2026-08-03）**

| 维度 | 分布 |
|---|---|
| 总数 | 2144 |
| kind | code_demo 1950 / text_note 154 / fragment 26 / reference_sheet 11 / announce 3 |
| verdict | positive 1005 / negative 922 / neutral 209 / harmful 8 |
| portability | android_api 1067 / universal 602 / host_private 273 / editor_bound 170 / obsolete 32 |

**核心结论**：社区语料约 **43% 判 negative**、约 **47% positive**，**47% 依赖 Android/host 私有能力**
（host_private 273 + editor_bound 170），可直接照搬的纯通用代码（universal）仅 28%。
任何入库样本必须：① 查 `catalog/` 三轴；② 引用原文片段；③ 保留 `manual_id` 回溯；④ 标注 `static_review`。

---

## 1. 类型轴（kind）× 质量轴（verdict）总览

| kind | 定义 | positive | neutral | negative | harmful | 处理建议 |
|---|---|---|---|---|---|---|
| code_demo | 可运行代码（含片段） | 1005 | 50 | 887 | 8 | 主力语料，正/负都取片段入库 |
| text_note | 教学/说明文字 | 0 | 138 | 16 | 0 | 概念讲解可作参考，不取代码 |
| fragment | 明显残缺 | 0 | 11 | 15 | 0 | 只作负面样本（社区"没写完"反例） |
| reference_sheet | 清单/手册（权限表、API 表） | 0 | 7 | 4 | 0 | 保留原文，做对照表 |
| announce | 公告/广告/水帖 | 0 | 3 | 0 | 0 | 不进入训练，归档即可 |

> 观察：`text_note`/`fragment` 一律不判 positive——代码库价值来自可运行片段。

## 2. 用途域（use-case）索引——按分类×正负标注

> 每个分类给出：样本数、正面代表（P=id）、负面代表（N=id 与判负理由）、及可复用方法要点。
> 完整清单见 `catalog/triage_shortlist.md`；单篇详情见 `catalog/post_<id>.json`。

### 2.1 UI / 布局（布局教程 156、控件属性 110、布局代码 22、Layout布局 35、UI控件 52）

- **总体**：布局类帖子整体质量较好（md 风格主页、启动图、仿 MIUI 弹窗等正面帖多），
  但大量帖子绑定宿主私有 API（`uiManager`、`ds.*`）或中文标识符。
- **正面代表**：P3435 md风格主页界面布局v3、P1875 启动图、P2228 两个仿MIUIDialog（三次优化）、
  P1412 轮播图、P4275 双层边框、P1280 EditText输入框控件、P4197 关于丝滑这件事。
- **负面反例**：
  - N2515 兴许是一个简洁美观的主页布局 —— `chinese_identifier`（中文标识符）；
  - N1591/N1668 可展开卡片/仿md3的switch —— `editor_bound`（依赖手册/FusionApp 专属环境）；
  - N2330 一个列表分类布局 —— `private_api`（`com.androlua.*` 私有类）。
- **可复用方法**：layout 表驱动 UI（`loadlayout` 与纯 Lua table 混排）、
  ViewPager+TabLayout 联动（P3448）、ConstraintLayout 仿 QQ 登录（P3033）、
  自绘组件（开关/进度条/贝塞尔底栏）。

### 2.2 网络请求 / 爬虫（网络请求 32、网络操作 21、网络爬虫 24、jsoup 4、xpath 4）

- **正面代表**：P2137 okHttp请求封装函数、P4147 okhttp 封装方法、P4471 OpenAI非流式大模型请求工具、
  P4723 汽水音乐api、P5266 抖音快手视频简易解析、P3302 不容易失效的蓝奏云解析。
- **负面反例**：
  - N4064 蓝奏云 LanzouyunAPI 封装版、N1941/N1960 jsoup爬虫 —— `private_api`；
  - N4530 GET/POST/SSE 流式请求工具类 —— `obsolete`（已失效 API）；
  - N2767 小说自定义Xpath规则 —— `private_api`+中文标识符。
- **可复用方法**：`Http`/`HttpURLConnection` 异步封装、cjson 解析、防失效的蓝奏云解析算法、
  API 签名（CryptoJS 移植，P4287）。

### 2.3 数据存储 / 文件操作（数据存储 8、文件操作 46、SAF访问 3）

- **正面代表**：P72 SQLite数据库操作测试、P1675 原生文件上传、P1188 FileUtils文件处理工具类、
  P2504 FTP上传文件、P269 调用图库选择图片。
- **负面反例**：N3511 LuaSQLite数据库连贯操作、N3528 存储应用数据封装 —— `chinese_identifier`；
  N2347 SharedPreferences使用 —— `obsolete`。
- **可复用方法**：SQLite 纯 Lua 封装、SAF `ACTION_OPEN_DOCUMENT` 选文件、SharedPreferences 一次性标志位。

### 2.4 系统 / 设备（系统 101、设备系统 9）

- **正面代表**：P1647 Android动态权限申请、P2632 启动App每天执行一次事件、P2034 SSID&密码连接WiFi、
  P4817 获取手机名称、P4689 获取adb无线调试权限。
- **负面反例**：N1877 常驻通知&刷屏通知 —— `editor_bound`；N2950 获取系统主题色 —— `private_api`+全局污染。
- **可复用方法**：运行时权限请求（ActivityCompat）、WiFi/蓝牙/传感器公开 API 调用、EdgeToEdge 沉浸式。

### 2.5 消息 / 通知 / 对话框（消息通知 5、对话框 7+76、Menu菜单 6）

- **正面代表**：P4125 独立消息显示系统 html+lua、P4236 通知添加直接回复操作、P4244 流畅显示多条Toast、
  P5000 BottomSheetDialog代码添加器、P4230 AlertDialog点击按钮不关闭、P243 PopupMenu弹出式菜单。
- **负面反例**：N1342/N1343 AlertDialog/ProgressDialog —— `private_api`+中文标识符。
- **可复用方法**：通知 `setContentIntent`+直接回复 `RemoteInput`、Toast 队列去重、PopupMenu 主题定制。

### 2.6 动画 / 自绘 / 游戏（动画教程 68、自绘制 76、小游戏 82）

- **正面代表**：P1105 缩放动画、P2127 字符雨特效、P1661 自绘制开关、P3994 CircularSeekBar、
  P4315 2048游戏、P4046 流体模拟、P5141 贪吃蛇。
- **负面反例**：N1467 RecycleView滑动动画 —— `private_api`+中文标识符；N3898 黑暗迷宫 —— 全局污染。
- **可复用方法**：`ObjectAnimator`/`ValueAnimator` 驱动、Canvas 自绘、纯 Lua 算法（2048/贪吃蛇/流体）。

### 2.7 远程更新 / 热更新 / 验证（远程更新 16、远程公告 2、远程验证 2、编码转换 14）

- **正面代表**：P3465 好看的远程启动图、P3230 在线加载Zip、P1210 QQ收藏内容解析、P2108 字符串取MD5。
- **负面反例**：
  - N1484 解析JSON的远程更新方案（自动跳转安装）—— `private_api`+中文标识符；
  - N2412 Lua实现热更新 —— `private_api`（`LuaDexLoader` 依赖）；
  - N1997 简约远程公告 —— `editor_bound`。
- **风险提示**：远程加载代码（`loadstring` 远程内容、远程 dex 加载）属 `remote_exec`，
  在 lide 宿主中必须走白名单/签名校验，见 §4。

### 2.8 Lua 语言 / 教程（Lua代码 205、Lua教程 14、Androlua教程 58、table表 13、字符处理 24）

- **正面代表**：P4875 table拓展函数、P1495 table.sort排序示例、P2169 洗牌算法、P2896 二分查找、
  P2456 最简单的string.split实现、P5140 Aurora Toast Lua、P4810 流体浅变、P4056 3d绘图器。
- **负面反例**：N2551 MathForest —— 中文标识符+全局污染；N614/N615 数组判断 —— 中文标识符；
  N3018 最简单的面向对象2.1版 —— 中文标识符。
- **可复用方法**：标准库算法（排序/洗牌/二分）、面向对象封装（`class`）、
  table/string 工具函数、Lua+Java 互操作（`luajava.bindClass`）。

### 2.9 编码 / 加密（加密解密 8、编码转换部分）

- **正面代表**：P5 RC4加解密、P1245 字符串MD5、P3068 AES/DES对称加解密、P3737 社会主义核心价值观加密。
- **负面反例**：N3724 JCE加密解密 —— 全局污染；N2419 base64编码解码 —— `remote_exec`；
  N2462 BaseSalt —— 中文标识符。
- **注意**：`hardcoded_secret` 贴（硬编码密钥/token）一律 negative 或直接忽略，不作范例。

### 2.10 适配器 / 列表（适配器 17、Adapter容器 11）

- **正面代表**：P4977 封装RecyclerView Adapter、P1294 ListView列表适配器、P1401 ExpandableListView、
  P2201 GridView、P4777 notifyItemRemoved 正确使用。
- **负面反例**：N2597 LuaRecyclerAdapter —— 已人工核实 **Pro 4.4.2 源码中不存在该类**（私有/杜撰 API）；
  N3596 LuaAdapter、N1301 RecyclerView列表视图 —— `private_api`。
- **可复用方法**：通用 Adapter 封装（BaseAdapter 子类 + ViewHolder）、列表局部刷新。

### 2.11 Java / 互操作（Java教程 12）

- **正面代表**：P1314 Java的File类、P3068 AES/DES对称加解密、P3613 线程顺序执行、
  P1510 URConnection获取重定向最终指向。
- **负面反例**：N2523 爱发电赞助页 —— `private_api`+`sensitive_permission`；N4010 线程池 —— `private_api`。
- **可复用方法**：`luajava` 调 Java 标准库（File/URL/加密）、线程调度封装。

### 2.12 浏览器 / WebView（浏览器 65、JS教程 36）

- **正面代表**：P1443 下拉刷新、P4086 html高亮文本、P1367 js网页文本URL转超链接、P639 去悬浮窗广告。
- **负面反例**：N1360 WebView各种下载 —— `private_api`；N1464 WebView使用指南 —— `editor_bound`+敏感权限。
- **风险提示**：`注入JS` 是社区高频手法（拦截网盘下载、去广告、破解验证码），
  多数属 `harmful`（§4）或至少 `remote_exec` 风险，**禁止**直接照搬。

---

## 3. 负面专项（反例库：5 大类，见 `negatives/`）

| 反例主题 | 触发特征（flags） | 计数 | 修正建议 |
|---|---|---|---|
| 中文/拼音标识符 | `chinese_identifier` | 493 | 改英文驼峰命名；Lua 5.3 可运行但不可维护 |
| 全局变量污染 | `global_pollution` | 86 | 全部 `local`；封装函数收拢作用域 |
| 宿主私有 API 绑定 | `androlua_private` / `private_api` | 273 | 换公开 API 或标注兼容性待实测 |
| 编辑器专属能力 | `editor_bound` | 340 | 归入「手册/FusionApp 专属」，不迁移 |
| 残缺/未写完 | `incomplete_mark` / `incomplete_fragment` / `syntax_hint` | 52 | 只作反例，不入正面库 |
| 已失效 | `obsolete` | 64 | 标注版本失效时间，不引用 |
| 远程执行风险 | `remote_exec` / `os_exec` | 84 | 一律视为风险写法（见 §4 边界） |
| 硬编码密钥 | `hardcoded_secret` | 6 | 禁止入库范例 |

## 4. harmful 专项（8 篇，仅识别归档，不用于新工程）

> 判定标准见 STANDARDS §4.3；正则初筛 + 人工复核清单（`HARMFUL_OVERRIDES`）双通道。

| id | 标题 | 危害特征 |
|---|---|---|
| 2274/2275/2276 | 组合按键(无障碍)免root验证-绕过（系列3帖） | 滑块验证码识别/卡密校验绕过 |
| 2146 | 网盘破解绕过原版（蓝奏云直链） | WebView 拦截+注入JS 绕过原版下载限制 |
| 4697 | 追星注册模块 | 设备码/卡密验证绕过注册模块 |
| 2826 | 获取现在运行的是什么程序 | 无障碍注入重写其他应用模块 |
| 1598 | 仿Momo | Xposed hook 微信（防检测） |
| 4460 | 一键爬取iMyShare工具站工具 | 网盘直链破解批量爬取 |

**边界声明**：`remote_exec`（拉远程代码/dex）与 `os_exec` 单独出现时只判 negative（风险写法），
只有叠加明确的绕过/破解意图才升级 harmful。harmful 内容**只识别与记录特征，不复制攻击面**。

## 5. 入库流程（DoD 对照）

1. 从 `catalog/post_<id>.json` 取三轴 + flags；
2. 打开原文 `lua_files/<分类>/<id>_<标题>.lua` 复核片段；
3. 写 `positives/` 或 `negatives/` 主题卡（头部 YAML 含 `reviewed`+`confidence`）；
4. 若人工复核结果与自动判定不符 → 直接编辑 `post_<id>.json` 的 verdict/flags 并登记原因；
5. 更新 `README.md` 变更登记表；重新跑 `analyze.py` 保持 catalog 同步。

## 6. 已知边界（诚实声明）

- 全部判定为 `static_review`（静态文本），**未做设备实测**；涉及 lide/Pro 兼容结论必须
  走 `androlua-project-validation` 双端验证后才能升格 `✅实测`。
- `post_<id>.json` 中 `truncation=truncated_tail`（359 篇）表示文末被来源截断，引用片段时避开文末。
- 自动 verdict 有系统性偏差风险：`chinese_identifier`/`private_api` 是主要判负原因，
  不代表代码不可运行；正负判定面向「可迁移性 + 规范性」，非运行成败。

---

*生成：2026-08-03 ｜ tools/analyze.py v2（全量正文）｜ 判定：static_review*
