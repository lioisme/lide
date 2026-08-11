# lide 编辑器知识库

> 来源：历史审计报告（`EDITOR_CRASH_ANALYSIS_REPORT.md`、`EDITOR_DRAWER_AUDIT_REPORT.md`、`EDITOR_PERFORMANCE_AUDIT_REPORT.md`、
> `EDITOR_PERFORMANCE_AUDIT_V2_REPORT.md`，均已删除，结论蒸馏于此）、`EDITOR_UI_ALIGNMENT_WORK_ORDER.md`（进行中）、`AGENTS.md`。

---

## 1. 技术底座与铁律

- 编辑器 = `com.lioisme.lide.editor` 包（2026-08-08 `:editor` 模块并单并入 `:app`；纯 Kotlin）+ sora-editor + TextMate Lua 语法；R1 边界零命中（含注释）。
- **初始化顺序（官方 demo 顺序，勿改）**：`colorScheme` → `setEditorLanguage` → `setText`。
- **TextMate 只经 `TextMateRuntime`（进程级一次性，`preload` 幂等触发；状态机 Loading/Ready/Failed，失败展示错误态 + 重试）**，不得在 UI 线程调用；已预热到进程启动（`LideApp`）。
- 编辑器 API（setText / diagnostics 赋值 / subscribeEvent 回调）保持主线程；文本快照可在后台线程取（`Content.toString` 内部 ReadWriteLock）。
- 自动保存：快照 + 写盘均 `Dispatchers.IO`，`onRelease` 兜底保存（修过 dispose 顺序丢改动）。
- 大文件（>200 KB）降级矩阵（任务单 §6.3，LuaEditor.applyConfig）：关自动换行 / 括号高亮 / 当前块高亮 / minimap / stickyScroll / 光标动画 / 行号面板，跳过行级诊断与标识符补全，undo 栈降至 200。
- 诊断的多错误聚合 + 可执行 quickfix：`LuaDiagnostics.check(source, quickfixSource: Content?)`，FixSpec ∈ {DeleteToken（substring 校验后删）、AppendText（行尾补闭合）、AppendAtFileEnd（跨行串/注释闭合）}；区域坐标平移由 sora `DiagnosticsContainer` 负责，quickfix 不自算。
- 搜索支持整词（`SearchOptions.TYPE_WHOLE_WORD`）；文本操作：selectLine / deleteLine / duplicateLine / toggleCase / 词级移动（`SelectionMovement`）经 moreActions「文本」分类暴露。
- 补全、诊断、事件回调必须 `try/catch(Throwable)` 降级，不得让异常逃逸进 sora 主循环。

---

## 2. 历史事故与教训（已修复，防止复发）

### 2.1 闪退根因 = 边界违规（`EDITOR_CRASH_ANALYSIS_REPORT.md`）

| 项 | 内容 |
|----|------|
| 根因 | `LuaDiagnostics` 从宿主导入 `com.luajava.LuaStateFactory` 进打字热路径（500ms 防抖 → 原生 LuaState 创建/关闭 → GC finalize 双关） |
| 崩溃向量 | ① `LuaState.close()` 无防重入 + `finalize()` 二次 `_close(0)` → SIGSEGV；② `newLuaState()` 在 try 外；③ GBK 错误消息经 `NewStringUTF` → SIGABRT——均不可被 Java 捕获 |
| 修复 | 编辑器独立包 `com.lioisme.lide.editor`（并入 `:app` 后仍保持包级隔离，编译期强隔离由 R1 门禁兜底）；纯 Kotlin 校验器重写 `LuaDiagnostics.kt`；AGENTS.md 写入 R1~R4；全部 try/catch(Throwable) 降级 |
| 现状 | `ui/editor/` 7 文件对 `com.androlua.*`/`com.luajava.*` 引用 0；全 app `com.luajava` 引用 0 |

### 2.2 抽屉三缺陷（历史审计 `EDITOR_DRAWER_AUDIT_REPORT`，已删）

| 缺陷 | 根因 | 修复 |
|------|------|------|
| P0-1 抽屉覆盖整个屏幕 | Modal 变体包住整个 Scaffold（全屏 scrim 覆盖顶栏） | 经典布局：抽屉移入 Scaffold 内容区，宽 ≤360dp |
| P0-2 退出时先展开再退出 | saveable `DrawerState` 复活链 + 防御性 LaunchedEffect 闪烁 | 状态根除：`remember { DrawerState(Closed) }`；退出 close→flush→back |
| P1 返回键吞键 | 手写 BackHandler 在关闭动画窗口期吞键 | 官方 `ModalDrawerSheet(drawerState)`（M3 1.3.0 内置返回 + 预测性动画） |
| 宽屏适配 | 无 | `material3-window-size-class`：≥840dp Permanent 抽屉 + 汉堡键隐藏（注意属性名 `widthSizeClass`，`windowWidthSizeClass` 是 1.4.0 别名） |

### 2.3 性能灾难链路（`EDITOR_PERFORMANCE_AUDIT_REPORT.md`，已修）

| P0 | 问题 | 单键成本（2000 行/60KB） |
|----|------|--------------------------|
| 1 | `update` 块每键重建 `TextMateColorScheme` → 杀分析线程 + 全文重 tokenize（高亮闪白） | 5~20ms+（5000 行文件可达数百 ms/ANR） |
| 2 | 每键全文 `toString()` 写 Compose state → 整屏重组（IME 组合放大 2~6 倍） | — |
| 3 | 每键 `setTextSize()` → 整屏重排版 + 多次 invalidate | — |
| 4 | 每键 O(n) 比较，偏差触发 `setText` + `restartInput` 隐藏炸弹 | — |

修复（模块化重构落地）：守卫式配置同步（scheme 每实例一次）、`collectIdentifiers=false`、搜索计数事件驱动、主题切换移入 LaunchedEffect、文件 IO 后台读、自动保存 500ms 防抖 + IO 写盘、`onRelease` 兜底。

---

## 3. 性能现状与差距（V2 审计已核销，以 Squircle-CE 为标杆）

| 项 | 现状（2026-08-06 复核） | 差距 |
|----|------|------|
| 打字 | `editTick` StateFlow 事件驱动（EditorSession.kt:74-75,173），无每键整屏重组 | 稳态无重组已达成 |
| 打开文件 | `Content` 构造注入 IO 线程，77KB 首帧开耗已移除 UI 线程 | device 基准可用 `scripts/perf-check.ps1 -Scenario open` |
| 搜索 | 结果事件下沉 SearchBar，过滤在 `Dispatchers.Default`，整词已支持 | — |
| 冷启动 | release 已开 R8（引擎 dex 单份化后 Lua 可见 API 面断言并入 `verifyMinimalism`）+ baseline profile（`app/src/release/baseline-prof.txt` + profileinstaller，启动/编辑器首帧/打字路径） | baseline 收益待同机 A/B（`-Scenario cold`） |
| 大文件 | undo 栈 200，minimap/stickyScroll/光标动画/行号面板/标识符补全随 >200KB 强关（§6.3 全矩阵落地） | — |
| 代码折叠 | **无交互（事实记录）**：vendored 0.24.6 无任何折叠交互代码（`FoldingRegion` 死代码），仅块线渲染；上游 0.25+ 引入 | P3 升级管线后可复核 |

健康项（已确认）：预热、无全文镜像、IO 快照写盘、大文件降级、try/catch 兜底、增量分析正常。
性能门禁：`scripts/perf-check.ps1`（cold / gfx / open 77KB / type 200，阈值仅建议，同机 A/B 为准）。

---

## 4. 现状组件与待办（UI 对齐）

### 4.1 已存在（阶段 B 部分落地）

`com.lioisme.lide.editor`：`EditorTabs.kt`、`EditorToolbar.kt`、`LuaEditor.kt`、`LuaLanguage.kt`、`LuaDiagnostics.kt`（多错误聚合 + quickfix）、`LuaSnippetManager.kt`、`TextMateRuntime.kt`、`EditorConfig.kt`（21 项全量配置）、`EditorTextOps.kt`（selectLine/deleteLine/duplicateLine/toggleCase/词级移动）——`SearchBar.kt`（整词搜索/替换面板）已随 2026-08-09 第二次用户红线移除（见 §4.4 后注）
`:app`：`session/EditorSession.kt`（StateFlow 会话 + Content 缓存）、`EditorScreen.kt`、`ui/screen/EditorSettingsScreen.kt`（编辑器独立设置页：21 项设置分组通用/编辑/视图/输入 + 顶栏恢复默认按钮）、`ui/screen/SettingsScreen.kt`（主设置页四分区：显示/编辑器/AndroLua/通用（权限管理/语言/关于），编辑器与 AndroLua 独立入口）、`ui/screen/AndroLuaSettingsScreen.kt`（AndroLua 工具页：API 浏览器/示例；插件功能 2026-08-07 按用户要求移除）、`ui/theme/SettingsManager.kt`（21 键持久化 `lide_editor` 命名空间 + `resetEditor` 恢复默认）

### 4.2 剩余差距（按 `EDITOR_UI_ALIGNMENT_WORK_ORDER.md`）

| WP | 内容 | 状态 |
|----|------|------|
| WP-001/002 | 抽屉返回键契约 / 关闭竞态 | ⛔ 被取代（2026-08-05：抽屉已整体删除，改 `ProjectFileBrowserDialog`；2026-08-09 该对话框随「删除文件树」用户红线一并删除） |
| WP-003 | Tab 拖拽排序（`sh.calvin.reorderable:reorderable:3.0.0`，POM 实证兼容） | ✅ 已落地（2026-08-05 复核，EditorTabs） |
| WP-004 | Tab 样式对齐 Squircle（扁平 + 3dp 底部指示条） | ✅ 已落地（2026-08-05 复核，EditorTabs） |
| WP-005 | 点击当前 Tab 弹关闭菜单（DropdownMenu） | ✅ 已落地（2026-08-05 复核，EditorTabs） |
| WP-006 | 新 Tab 自动滚动 | ✅ 已落地（2026-08-05 复核，EditorTabs） |
| WP-007 | 抽屉手势对齐（gesturesEnabled） | ⛔ 被取代（2026-08-05：抽屉已删除） |
| WP-008 | 汉堡键语义（宽屏隐藏） | ⛔ 被取代（2026-08-05：汉堡键随抽屉移除，EditorComponents.kt:112） |
| WP-009 | 文件树横向滚动（最小宽 248dp） | ⛔ 已废止（2026-08-09 用户红线：文件树整体删除，`:editor` 的 `FileBrowser.kt`/`FileNode` 一并移除） |
| WP-010 | 版本目录（version catalog，P2 可选） | 未完成 |

### 4.3 功能缺失（任务单已完成项移出；剩余为 P2 以外事项）

- ✅ 已落地（2026-08-06，任务单 EDITOR_FEATURE_COMPLETION_WORK_ORDER G1~G9/G1x）：设置中心 21 项、原生 SnippetController、诊断多错误聚合 + quickfix、整词搜索、文本操作、标识符补全（默认关）、迷你地图、行为开关。
  - ⛔ 后注（2026-08-09）：其中「整词搜索」（搜索/替换面板 `SearchBar.kt` + Ctrl+F + ⋮ 菜单「搜索」项）已按用户红线移除，见 §4.4。
- ⛔ 折叠交互：vendored 0.24.6 无此能力（上游 0.25+），保持「仅渲染不交互」；待 P3 升级管线后复核。
- 未做：Inlay hints（上游编辑器无此能力）、未启用 Snippet 时的手写占位符剥离（P3 示例）。

### 4.4 文件选择现状（2026-08-09 用户红线反转后）

- **编辑器不再提供任何「打开其他文件」入口**（文件树对话框 `ProjectFileBrowserDialog` 与「打开文件」菜单项一并删除，`:editor` 的 `FileBrowser.kt`/`FileNode` 移除）；编辑器默认自动打开 main.lua（`EditorSession.initialize`），标签关闭/切换仅作用于已打开标签。
- **宿主文件选择一律走系统 SAF**：工程列表 `.alp` 批量导入（`OpenMultipleDocuments` + `AlpImportCoordinator.copyUriToTemp` 缓存临时文件）与签名档案导入（`OpenDocument` + contentResolver 直读）均直连系统选择器，无宿主内建浏览 UI、无全局引擎开关（原 `FilePickerEngine` 偏好与设置页「文件打开方式」条目已删）。
- 会话模型不变：标签/脏标记/自动保存仍以工程相对路径为唯一键（`session/EditorSession.kt`），文件选择不进入编辑器会话。

### 4.5 编辑器搜索现状（2026-08-09 第二次用户红线，整体移除）

- **编辑器搜索/替换功能整体移除**（2026-08-09 用户指令「移除编辑器界面的搜索功能及相关的代码」）：`:editor` 包 `SearchBar.kt`（查询/替换/匹配计数/大小写/正则/整词面板）删除；宿主 `EditorScreen` 的 Ctrl+F 快捷键、⋮ 更多菜单「文件 → 搜索」项、`EditorBody` 搜索面板槽位一并移除；`EditorComponents.kt` 恢复「标签行 + 编辑器 + 权限横幅」结构。
- 文案清理：`search_hint`/`replace_hint`/`replace`/`replace_all`/`case_sensitive`/`regex`/`whole_word`/`cd_search`/`cd_search_next`/`cd_search_prev`/`cd_search_close`/`cd_search_match_count`/`menu_category_file`（en/zh 全删）。
- 保留项：API 浏览器搜索（`ApiBrowserScreen` 官方 `SearchBar`/`SearchBarState` + `api_*` 文案）不受影响；编辑器的跳行定位（运行错误 → 滚动行）不依赖搜索。
- 验收证据：`:app:compileArm64DebugKotlin` + `:app:testArm64DebugUnitTest` + `:app:verifyMinimalismDebug`（引擎面 35 类唯一、29 根 dex）全部 BUILD SUCCESSFUL（2026-08-09）；`app/src/main/kotlin` 对 `SearchBar|getSearcher|isSearchVisible|cd_search|search_hint|PublishSearchResultEvent|EditorSearcher` 零命中（仅 API 浏览器官方 SearchBar 保留）。

---

## 5. 验收基线（改编辑器必测）

```
编译：  .\gradlew.bat :app:compileArm64DebugKotlin --console=plain
构建：  .\gradlew.bat :app:assembleX64Debug --console=plain（模拟器可装）
R1：    rg -n "luajava|androlua|loadLibrary" app/src/main/kotlin/com/lioisme/lide/editor   # 零命中（已并入 CI job verify-r1）
单测：  .\gradlew.bat :app:testX64DebugUnitTest（LuaDiagnosticsTest 24 例已并入 :app 等）
回归：  scripts/adb_regress.ps1（4 工程矩阵）；scripts/perf-check.ps1（性能基准）
```

## 6. 设置 ↔ sora API 映射（21 项全集，D1）

见任务单 EDITOR_FEATURE_COMPLETION_WORK_ORDER §7 全表；分组：通用（字号 8..32 / Tab 宽 1..8 / Tab 用空格）/ 编辑（自动缩进·闭合·补全·标识符补全·行级词法断言·只读）/ 视图（换行·行号·当前行·括号对·代码块线·迷你地图·粘性滚动·行号面板）/ 输入（空白·缩放·光标动画·硬键盘优先）。
持久化：`SettingsManager.kt`（`lide_editor` 21 键，StateFlow 镜像 + `apply()`），`:app` 只传入 `:editor` 纯值 `EditorConfig`，R4 满足。

## 7. sora-editor 版本台账索引

上游收纳与分层台账见 `app/vendor/sora-editor/README.md`（当前 0.24.6）。升级路径（2026-08-09 全量审计：`scripts/update-sora-editor.ps1` + `.github/workflows/update-sora-editor.yml` 已删除——third_party 迁址后断链且每周必失败；sora 为源码 vendor，上游改动一致性由 `scripts/verify-upstream-pristine.sh`（verify-vendor job）兜底）。

---

## 8. 词法诊断热路径与 i18n（2026-08-09 CODE_QUALITY Q11/Q15 蒸馏）

`LuaDiagnostics.Incremental`（`editor/LuaDiagnostics.kt`）性能与文案模型（改动此文件必读）：

- **增量行索引**：`buildLineStarts` 只重扫变更段（`unclosedArgs` 定界），`errorAt` O(1) 查表；
  行尾位置 = 下一行起始 **-1**（曾错 1 字符导致 quickfix 插入偏移）。
- **零分配 token**：括号 token 用 sealed `Frame`（`BracketFrame`/`BlockFrame`），消灭每次扫描
  `"bracket:$c"` 字符串分配；`pendingClose` 缓存长注释/长串闭合序列；`REPEAT_KINDS` 收口重复键。
- **文案模型（Q15）**：`MSG_*` 包级常量（消息键）+ `DiagnosticMessageProvider` 函数式接口
  （`fun interface`，默认中文模板）；`LuaEditor.diagMessageProvider = remember { object : ... }`
  资源本地化——**Kotlin SAM 转换不支持 vararg，必须对象表达式**；remember 引用稳定，
  `EditorViewPool.syntaxCheck` 以 provider 引用比较决定是否重建检查器。
- `MSG_*` 是包级 const（非 `LuaDiagnostics` 成员），同包直接用无前缀名。
- 新增文案一律双语文案库（values + values-zh），验收脚本比对两文件键集合必须全同步。

---

## 9. 编辑会话审计整改结论（2026-08-10，EDITOR_AUDIT_REMEDIATION_WORK_ORDER 完工蒸馏）

增量诊断、自动保存、跳行与池化生命周期四类缺陷已全部修复并回归（改动域必读）：

- **增量诊断收敛门（T1）**：`LuaDiagnostics.Incremental` 收敛条件必须含
  `lastErrorMaxLine <= maxEdited`——只按编辑行收敛会丢弃 `[收敛行, EOF]` 尾部的
  「行局部错误」（不匹配右括号 / 多余 `end`·`until`，不改变解析栈）；尾部存在旧错误时
  禁止提前收敛，须扫描至 EOF 重新收集；`onNewText` / 空结果 / 异常降级三路径复位。
- **同长度切换诊断缺失（T2）**：`onNewText()` 必须复位 `lastCheckedLength = -1`；
  否则新文本与旧文本字节长度相同时索引复用门命中（`minEdited==MAX && n==lastCheckedLength`）
  → lineStarts 已清空 → 提前返回空 → 诊断永久缺失。增量状态复位字段
  （`lastCheckedLength` / `lastErrorMaxLine`）缺一即出故障，改 `onNewText` 必查两个字段，
  回归用例「同字节长度文本切换后诊断不缺失」（19 字节等长双文本）。
- **自动保存全量落盘（T3）**：自动保存防抖消费 `EditorSession.flushAllDirty()`
  （遍历脏标签逐个落盘）而非仅 `flushSelectedDirty()`——防抖窗口内编辑 A 后切到 B、
  进程被杀（无 onCleared 兜底）时 A 不再丢修改；成本只增于实际脏文件。
- **跳行时序竞态（T4）**：跳行目标必须携带 Content 身份（`PendingJump(content, line)`，
  openTab 完成即捕获），消费效应以身份绑定为前置轮询等待（8ms 步进 / 5s 超时，
  仅文件切换瞬态，非常驻；同文件再跳行零等待）——openTab 完成至 LuaEditor `setText`
  生效之间存在窗口，对旧文档 `setSelection` 会被随后文本替换覆盖（定位丢失/错位），
  身份比较免误判。2026-08-11 门面收口后身份判定改经
  `EditorController.isShowing(content)`（内部 `editor.text === content.raw`，语义不变），
  定位执行也经 `setSelection`（内部夹取 + requestFocus）。
- **池化编辑器引用滞留（T5）**：`LuaEditor` `DisposableEffect.onDispose` 必须
  `editor.setText(null)` 剥离进程级池化实例（`EditorViewPool`）对最后 Content + undo 栈
  （≤2500 快照）的强引用——会话 `release()` 清缓存后仍滞留至下次 setText 即跨工程泄漏；
  sora `setText(@Nullable CharSequence)` 空参合法（内部转 `new Content("")`），
  视图/主题/语言复用不受影响。
- **工程脚本原子写（T7）**：`ProjectManager.atomicWrite`——同目录 `.tmp` + `renameTo`
  原子替换，目标文件无中间截断态；rename 失败（Windows 开发机目标已存在时返回 false）
  与写入异常两路径均清理 tmp 并兜底直写；`saveProjectScript` 与 `writeInitFields`
  （同风险路径）统一走该入口。回归用例断言内容完整 + 无 `.tmp` 残留。

## 10. 只读 / SDK 下移 / 工程深色结论（2026-08-10，READONLY_SDK_THEME_WORK_ORDER 完工蒸馏）

全量审计整改二（R1–R6）完工，改动域必读：

- **只读绕过三处（R1）**：sora `setEditable(false)` 只挡编辑器主路径——
  ① `EditorScreen.insertImport` 直写 Content（运行错误对话框「插入 import」）
  ② `EditorTextOps.toggleCase` 直写 Content（方向/大小写）③ IME 软键盘注入（只读时键盘仍可输入）。
  ①/② 入口函数前置守卫（`config.readOnly` / `editor.isEditable`），③ 见 R2。
- **只读 IME 同步（R2）**：sora `setEditable` 不重建 InputConnection——进入只读必须
  `editor.hideSoftInput()` 收键盘，切回可写必须 `editor.restartInput()` 重建连接，否则
  「切回可写但打字无效/键盘不弹」假死。此接线在 `LuaEditor.applyConfig` 的 isEditable 差异块内。
- **只读 UX（R3）**：`EditorMoreAction` 增 `enabled`（构造参数序 = category/label/enabled/onClick，
  尾随 lambda 为 onClick——改参数序时全库尾随 lambda 调用点无需动）；写操作（剪切/粘贴/删除行/
  复制行/大小写）与 保存/全部保存 菜单置灰，复制/全选/选行保持可用；`EditorComponents.ReadOnlyBanner`
  （secondaryContainer + Lock 图标）置 StoragePermissionBanner 上方。
- **线号分割线 / 固定行号（R4）**：sora 原生 `setDividerWidth(float)`（0=不绘制）与
  `setPinLineNumber(boolean)`（**读端必须用 `isLineNumberPinned()`**——sora 有同名私有字段
  `pinLineNumber`，Kotlin 属性合成失败「field is private」）；设置键 `line_number_divider_width`
  （0..6 px）；固定行号设置键 = **`fixed_line_numbers`（2026-08-12 L1 恢复，默认 true，
  编辑器设置页「视图」组开关，行号关闭时置灰）**——P4 曾删的旧键 `pin_line_number`
  不得回退（历史引用一律不得回退，旧键复活即与 fixed_line_numbers 双键并存违反治理）；
  新键与旧键语义差异：新键默认**开**（对齐现代 IDE），旧键默认关；构造 + 守卫 diff 双处
  接线（`isLineNumberPinned()` 读取）。
- **minSdk 下移（R5）**：`AXmlManifest.readUsesSdk(bytes)` 返回 (min, target)——优先
  `uses-sdk` 元素属性、缺失回落 manifest 根属性（AGP/aapt2 折叠形态），全部带 android 命名空间
  校验；`ManifestEdit.minSdkVersion`（null=不干预）经 `rebuildUsesSdk`（重写或追加，TYPE_INT_DEC）
  写回，**manifest 根折叠形态必须改写根元素属性、不重复注入**（`hasUsesSdkElement` 预计算 +
  appendNeed 门控防双写）；2026-08-11 P3 起目标 SDK 由工程 init.lua `appsdk`（回落 `targetSdk`）
  驱动（`ManifestEdit.targetSdkVersion`，缺失 = 宿主），minSdk 由 `minsdk`（回落 `minSdk`）驱动，
  UI 经 `ZipUtils.readEntryData(hostApk, "AndroidManifest.xml")` + `readUsesSdk().first` 读宿主
  minSdk（解析失败降级 0）；**构建对话框局部 minSdk/ABI 选择已随 P5 退役**，minSdk<24 且未勾 v1
  时构建以失败结果展示（`build_sdk_need_v1`）。单测覆盖元素/折叠双形态读写回环。
- **工程深色（R6，WP-014；P2 2026-08-11）**：`ThemeRegistry#setTheme`（darcula/quietlight）已支持切换，
  消费端——`EditorScreen` 以 `produceState<Boolean?>(null, projectInfoVersion)` 后台读
  init.lua `theme` 字段后走 **`ThemeCatalog.resolveDark`**（兼容 legacy dark/light 与 `Theme_*` 名：
  别名表优先 + 名启发式 DayNight→null/含 Light→浅色/含 Black·Dark·Night→深色），runCatching 降级
  null，`effectiveDarkTheme = 覆盖值 ?: LocalIsDarkTheme` 传入 `EditorBody.isDarkTheme`
  （LuaEditor 现有 `LaunchedEffect(isDarkTheme)` 生效）；仅编辑器文本区跟随，宿主 M3 主题不受影响，
  未知值回落宿主。
- **工程属性页（P1-P4，2026-08-11）**：入口文案统一 `project_properties`（⋮ 菜单 / 长按底单 /
  页标题）；四分区 = 基本信息（appname/packagename/appver/appcode/developer/description）+
  系统（目标 SDK/最低 SDK/主题下拉）+ 高级（方向/渠道/密钥/调试）+ 权限；「跟随宿主」= init.lua
  字段缺失（保存用 `LuaConfigParser.removeField` 删整行含行注释）；SDK 键与 pro 对齐
  （目标=`appsdk`、最低=`minsdk`，读回落 targetSdk/minSdk），裸整数写回；主题写回 `quoteLuaString`，
  下拉含保持宿主/常见别名（`ThemeCatalog.COMMON_ALIASES` 16 项）/反射全量 `Theme_*`（lazy 进程级缓存 +
  反射失败降级精选表）/legacy dark·light 自定义条目；权限弹窗与工程权限页共用
  `SearchablePermissionPicker.PermissionPickerList`（搜索 = 名称+中文名+别名，分组 REGULAR/SPECIAL/其他，
  未知权限透传），清单 = `PermissionCatalog.ENTRIES`（51 宿主声明 + 6 特殊 + aliases），
  `PermissionResolver.normalize` 含 `.` 即全限定名原样返回；读写一律经 `ProjectManager`
  `readInitContent`/`writeInitFields`/`loadUserPermissions`（B-1，UI 不自摸文件）。

## 11. sora 门面契约（2026-08-11 解耦审计 B 级收口，DECOUPLING_AUDIT_REPORT T2 完工蒸馏）

sora 类型（`io.github.rosemoe.*`）只允许出现在 `editor` 包（文本级能力）与 vendor 源码；
宿主层（session / ui / project / engine）一律经两个门面访问，禁止 import 或 FQN 引用：

- **`editor/EditorContent.kt`**（不透明文本句柄）：`of(text)` 工厂（宿主 IO 线程构造）、
  `snapshot()`（后台线程安全，内部 ReadWriteLock）、`isSameAs()`（引用身份判定，setText
  复用 / 跳行绑定前置）；`raw` 仅 `internal`（模块内供 session 门面实现）。
  改造路径：`EditorSession.Selection.content` / LRU 缓存 / `insertText`/`replaceText` /
  `flushDirty` 全量换用；`toString()` → `snapshot()`。
- **`editor/EditorController.kt`**（编辑器命令门面）：`bind`/`unbind`（LuaEditor
  AndroidView update / onDispose 期，主线程）、`isBound`/`isShowing`/`lineCount`、
  `setSelection(line, column)`（内部夹取 + requestFocus）、`undo`/`redo`/`canUndo`/`canRedo`
  （undo/redo 只读守卫由调用方前置拦截）；全部命令 try/catch(Throwable) 降级，异常不逃逸。
  改造路径：替代历史 `AtomicReference<CodeEditor?>` 发布（B-1 移除点）——
  `EditorScreen` 跳行消费（`isShowing` 轮询 + `setSelection`）与 `runUndoRedo`
  （`canUndo/canRedo` 重算上报）均经门面。

门禁守护：`:app:verifySoraIsolation`（walk `app/src/main/kotlin/com/lioisme/lide`，
editor 目录外扫描 `io.github.rosemoe` 字面量，import 与 FQN 双形态，违例即失败；
CI verify-sora-isolation job 挂接；负向拦截实证 = session 包植入 sora import 临时文件
→ FAILURE，移除 → SUCCESS）。关联：宿主路径与引擎前缀单一事实源见
`kb/ARCHITECTURE.md` §3.5。

## 12. 编辑器颜色体系收口（2026-08-12，EDITOR_THEME_FONT_FORMAT_WORK_ORDER T19 完工蒸馏）

**用户拍板**（2026-08-12）：AppBar 颜色与主界面/设置界面实现方式一致
（宿主 Material3 surface，不随编辑器主题模式）；编辑器文本区背景跟随主题系统
且使用科学专业的颜色；AppBar 与编辑器底色必须可区分。标签行/分隔线/空态一并
回归宿主 Material3（T5 色带机制 `EditorChromeColors.kt` 整体退役删除）。

**最终色彩契约**（x64 模拟器 6 场景像素实测，1440×2560 screencap 逐行采样）：

| 宿主外观 | 编辑器主题 | AppBar/标签行（宿主 surface） | 编辑器文本区 | 来源 |
|---|---|---|---|---|
| 暗 | FOLLOW | #1A1C1E | #252629（surfaceContainerHigh） | MD3 容器层级 |
| 暗 | LIGHT | #1A1C1E | #F5F5F5 | quietlight `editor.background` |
| 暗 | DARK | #1A1C1E | #242424 | darcula `background` |
| 亮 | FOLLOW | #FDFCFF | #E6E7EC（surfaceContainerHigh） | MD3 容器层级 |
| 亮 | LIGHT | #FDFCFF | #F5F5F5 | quietlight |
| 亮 | DARK | #FDFCFF | #242424 | darcula |

要点：

- **AppBar 与主界面同源**：`EditorToolbar` 直接
  `MaterialTheme.colorScheme.surface`/`onSurface`（ProjectListScreen 同款），
  删除 `chrome` 参数注入链（Toolbar/Tabs/EditorBody/EditorScreen）。
- **编辑器背景三态**：LIGHT/DARK = TextMate 官方主题色（sora 原生
  `TextMateColorScheme.applyVSCTheme/applyTMTheme` 映射 `editor.background`/`background`
  → `WHOLE_BACKGROUND`，零覆盖）；FOLLOW = 宿主 `surfaceContainerHigh`
  （`rememberHostEditorColors.background`，比 AppBar 的 surface 高一档层级，
  明暗两侧均与 chrome 温和区分，遵循 MD3 明暗表面层级规范）。
- **浮层层级**：补全窗/tooltip 背景升 `surfaceContainerHighest`
  （`generateLightScheme` 补 #DADBE1、`generateDarkScheme` 补 #2F3033）——
  浮层 > 画布 > chrome 三层递进。
- **标签行归属**：`EditorTabs` 背景与选中/未选中标签色（primary/onSurfaceVariant）
  全部改 `MaterialTheme.colorScheme.*`；分隔线 outlineVariant、空态文案
  onSurfaceVariant 同步回归。
- **历史修正**：旧 AGENTS.md 记录「DARK = RGB(26,28,30)」有误，实测 darcula
  编辑器背景 = #242424（宿主暗 surface #1A1C1E 的误记）；三路主题切换实测
  均为正确 TextMate 色。

验证：`:app:compileArm64DebugKotlin` + `:app:testArm64DebugUnitTest` +
`:app:verifySoraIsolation` + `:app:verifyMinimalismDebug` 全绿；x64 模拟器
三路主题 × 宿主亮暗 6 场景像素实测通过（AppBar ≠ 编辑器底色，语法高亮
token 色正确：#448C27 quietlight 字符串绿 / #6A8759 darcula 字符串绿）。
关联：EditorTheme.kt `HostEditorColors` / `LideHostColorScheme`（FOLLOW 烘焙
宿主色）；TextMateRuntime.kt `hostColorScheme`/`colorScheme()` 单例。

## 13. 编辑器外观单一跟随宿主（2026-08-12 治理红线，EDITOR_APPEARANCE_GOVERNANCE_WORK_ORDER 完工蒸馏）

**用户拍板**（2026-08-12，覆盖 §12 三态方案）：编辑器外观不是独立主题，是
宿主外观的派生。设置页禁止出现任何编辑器外观/主题选项；`editor_theme` 偏好键、
三态 `EditorThemeMode`、`EditorThemeSheet`、`projectDarkOverride`（工程
init.lua theme 驱动宿主）全部删除，历史引用一律不得回退。

**颜色单一事实源 `EditorPalette`**（editor 包，纯 Kotlin，零 sora 外泄）：

| 套名 | 亮侧（isDark=false） | 暗侧（isDark=true） |
|---|---|---|
| LIDE Classic | quietlight（`#F5F5F5` 底） | darcula（`#242424` 底） |
| Solarized Dual | solarized_light | solarized_dark |
| Tomorrow Dual | tomorrow | tomorrow_night |

- 激活主题名 = `EditorPalette.themeNameFor(isDark)`，随宿主 `LocalIsDarkTheme`
  切换；宿主切换浅/深色时触发编辑器重组。
- 装配链：`TextMateRuntime`（`themeRegistry.setTheme(名)` + 进程级单例
  `LideTextMateColorScheme` 广播）——`darcula`/`quietlight`/`solarized_*`/
  `tomorrow*` 为 THEMES 加载白名单，6 个 JSON 全加载、单侧主题禁止入库。
- 全部 6 主题背景均为平铺色（无渐变），token 配色库补全到 6 组对照；
  文本承载色 token 对主题背景 WCAG AA 严格档 ≥ 4.5:1，由
  `EditorPaletteContrastTest` 门禁守护（解析 assets/textmate/ 全部激活主题
  逐 token 断言，含双模配对结构完整性 + 全 token 对比度两项用例）——
  主题 JSON 任何改动必须过门禁。
- **颜色分层契约（§12 T19 延续，不随治理回退）**：AppBar/标签行/分隔线/空态
  恒为宿主 Material3（surface + onSurface）；编辑器文本区独立着色（双模主题色，
  sora 原生 `editor.background`/`background` → WHOLE_BACKGROUND）；补全窗/
  tooltip 浮层 = `surfaceContainerHighest`（浮层 > 画布 > chrome 三层递进）。

**权限页与用途分离**（同单治理，T3）：权限页「高级权限」区更名「通用权限」，
行标题一律 `perm_*` 前缀（`perm_root`/`perm_shizuku`/`perm_dhizuku`），
状态行（已授权/需要授权/服务未运行/不可用）为通用词，与设置页「安装方式」组
共用 `InstallBackendUi` 单一事实源；PermissionCatalog 移除 Dhizuku API 常规
条目（含「静默安装」别名），工程如需声明经「其他（未登记）」透传组。
同步于 kb/ARCHITECTURE.md §4「权限与用途分离契约」。

验证：`:app:compileArm64DebugKotlin` 全绿；`:app:testArm64DebugUnitTest`
EditorPaletteContrastTest 3/3 通过（双模配对结构 / 亮侧全 token / 暗侧全
token）；LuaConfigParserTest 既有失败与本治理无关（stash 验证基线同样失败）。
关联：EditorPalette.kt（主题名常量 + 激活表 + 对比度对照）、
EditorPaletteContrastTest.kt（门禁）、PermissionCatalog.kt / PermissionScreen.kt /
InstallBackendUi.kt（perm_* 命名）。

## 14. 编辑器配色方案可选 + 明暗双模跟随宿主（2026-08-12 用户拍板反转，THEME_I18N_SPEC_WORK_ORDER 完工蒸馏）

**反转声明**：§13「单一跟随宿主」（当日治理）经用户拍板反转——编辑器文本区
明暗**唯一输入源**仍 = 宿主外观（`LocalIsDarkTheme`），但配色**方案**可在
编辑器设置页选择（Squircle CE / Terminus 同款方案选择器）。§13 中「设置页
禁止任何编辑器外观/主题选项」与「`editor_theme`/三态模式已删除」仅后半
（旧键与三态类删除、`projectDarkOverride` 删除）仍有效；方案用**新键**
`editor_scheme`（`IntKey`），历史引用一律不得回退。

**方案 = 亮/暗双模配对**（每套方案两套 TextMate 主题，随宿主明暗自动切换对应侧）：

| 方案 | 亮侧（isDark=false） | 暗侧（isDark=true） |
|---|---|---|
| LIDE Classic（`SCHEME_CLASSIC`=0） | quietlight（`#F5F5F5` 底） | darcula（`#242424` 底） |
| Solarized Dual（`SCHEME_SOLARIZED`=1） | solarized_light | solarized_dark |
| Tomorrow Dual（`SCHEME_TOMORROW`=2） | tomorrow | tomorrow_night |

- **单一事实源** `EditorPalette`：`pairs` = 3 套亮/暗主题名配对（方案索引
  `SCHEME_*` 常量 = `pairs` 下标）；`allThemes` = TextMate 加载白名单（6 个
  JSON 全加载）；`schemeBackgrounds` = 方案面板亮/暗预览色条（与 JSON 背景
  同源，对比度测试断言）；激活主题名 = `themeNameFor(scheme, isDark)`
  （越界 coerce 回落）。
- **持久化**：`EditorConfig.themeScheme` + SettingsRegistry `IntKey("editor_scheme")`
  （默认 CLASSIC）；编辑器设置项计数为 21（含新增「配色方案」行）。
- **装配链**：`TextMateRuntime` 单例按主题名分槽缓存 `LideHostColorScheme`
  （同主题名不重复构造，明暗/方案切换零重建；`ThemeRegistry.setTheme` 广播
  驱动 token 重算）；`preload(context, initialThemeName)` 由宿主 LideApp
  按「当前方案 + 系统明暗」计算传入（R4：运行时不读宿主状态）；编辑器打开后
  由 LuaEditor LaunchedEffect(effectiveDark, initReady, themeScheme) 接管。
- **对比度门禁（G4，跨 §13 延续强化）**：`EditorPaletteContrastTest` 解析
  assets/textmate/ 全部激活主题逐 token 断言（含 `schemeBackgrounds` 预览
  色条与 JSON 同源断言、pairs↔schemeBackgrounds↔JSON 三方同源）；
  `ThemeContrastTest` 守护宿主 HCT 色板 token + chrome 槽（AppBar/标签行/
  分隔线 = surface+onSurface）× 全部 3 方案背景颜色槽 ≥ 4.5:1。
- **颜色分层契约（§12 T19 延续）**：不变——AppBar/标签行/分隔线/空态恒为
  宿主 Material3；文本区独立着色；浮层 `surfaceContainerHighest`。
- 历史 `EditorChromeColors.kt` 色带删除不回退；§13「DARK=RGB(26,28,30)」
  误记（宿主 surface）以契约为准。

验证：`:app:compileArm64DebugKotlin` + `:app:testArm64DebugUnitTest`
（EditorPaletteContrastTest / ThemeContrastTest 全绿）+ `:app:lintArm64Debug`
（0 新问题）+ `:app:assembleArm64Debug` + `:app:verifyMinimalism` +
`:app:verifyMinimalismDebug` 全绿；设备截图核验待 P7 统一执行。
