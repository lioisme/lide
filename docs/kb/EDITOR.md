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
