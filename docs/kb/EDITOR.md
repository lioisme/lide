# lide 编辑器知识库

> 来源：`EDITOR_CRASH_ANALYSIS_REPORT.md`、`EDITOR_DRAWER_AUDIT_REPORT.md`、`EDITOR_PERFORMANCE_AUDIT_REPORT.md`、
> `EDITOR_PERFORMANCE_AUDIT_V2_REPORT.md`、`EDITOR_UI_ALIGNMENT_WORK_ORDER.md`、`AGENTS.md`。

---

## 1. 技术底座与铁律

- 编辑器 = `:editor` 模块（纯 Kotlin/Java）+ sora-editor + TextMate Lua 语法；R1 边界零命中（含注释）。
- **初始化顺序（官方 demo 顺序，勿改）**：`colorScheme` → `setEditorLanguage` → `setText`。
- **TextMate 只经 `TextMateInitializer`（进程级一次性）**，不得在 UI 线程调用；已预热到进程启动（`LideApp` + `CountDownLatch`）。
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
| 修复 | `:editor` 独立 Gradle 模块（编译期强制隔离）；纯 Kotlin 校验器重写 `LuaDiagnostics.kt`；AGENTS.md 写入 R1~R4；全部 try/catch(Throwable) 降级 |
| 现状 | `ui/editor/` 7 文件对 `com.androlua.*`/`com.luajava.*` 引用 0；全 app `com.luajava` 引用 0 |

### 2.2 抽屉三缺陷（`EDITOR_DRAWER_AUDIT_REPORT.md`）

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
| 冷启动 | release 已开 R8（`verifyReleaseLuaSurface` 门禁防反射面破坏）+ baseline profile（`app/src/main/baseline-prof.txt` + profileinstaller，启动/编辑器首帧/打字路径） | baseline 收益待同机 A/B（`-Scenario cold`） |
| 大文件 | undo 栈 200，minimap/stickyScroll/光标动画/行号面板/标识符补全随 >200KB 强关（§6.3 全矩阵落地） | — |
| 代码折叠 | **无交互（事实记录）**：vendored 0.24.6 无任何折叠交互代码（`FoldingRegion` 死代码），仅块线渲染；上游 0.25+ 引入 | P3 升级管线后可复核 |

健康项（已确认）：预热、无全文镜像、IO 快照写盘、大文件降级、try/catch 兜底、增量分析正常。
性能门禁：`scripts/perf-check.ps1`（cold / gfx / open 77KB / type 200，阈值仅建议，同机 A/B 为准）。

---

## 4. 现状组件与待办（UI 对齐）

### 4.1 已存在（阶段 B 部分落地）

`:editor`：`EditorTabs.kt`、`SearchBar.kt`（整词搜索）、`EditorToolbar.kt`、`LuaEditor.kt`、`LuaLanguage.kt`、`LuaDiagnostics.kt`（多错误聚合 + quickfix）、`LuaSnippetManager.kt`、`TextMateInitializer.kt`、`EditorConfig.kt`（21 项全量配置）、`EditorTextOps.kt`（selectLine/deleteLine/duplicateLine/toggleCase/词级移动）
`:app`：`session/EditorSession.kt`（StateFlow 会话 + Content 缓存）、`EditorScreen.kt`、`ui/screen/EditorSettingsScreen.kt`（21 项设置中心，分组通用/编辑/视图/输入）、`ui/screen/SettingsScreen.kt`（编辑器分区入口）、`ui/theme/SettingsManager.kt`（21 键持久化 `lide_editor` 命名空间）

### 4.2 剩余差距（按 `EDITOR_UI_ALIGNMENT_WORK_ORDER.md`，内部 WP 编号与 PROGRESS 是两套体系）

| WP | 内容 | 状态 |
|----|------|------|
| WP-001/002 | 抽屉返回键契约 / 关闭竞态 | ⛔ 被取代（2026-08-05：抽屉已整体删除，改 `ProjectFileBrowserDialog`） |
| WP-003 | Tab 拖拽排序（`sh.calvin.reorderable:reorderable:3.0.0`，POM 实证兼容） | ✅ 已落地（2026-08-05 复核，EditorTabs） |
| WP-004 | Tab 样式对齐 Squircle（扁平 + 3dp 底部指示条） | ✅ 已落地（2026-08-05 复核，EditorTabs） |
| WP-005 | 点击当前 Tab 弹关闭菜单（DropdownMenu） | ✅ 已落地（2026-08-05 复核，EditorTabs） |
| WP-006 | 新 Tab 自动滚动 | ✅ 已落地（2026-08-05 复核，EditorTabs） |
| WP-007 | 抽屉手势对齐（gesturesEnabled） | ⛔ 被取代（2026-08-05：抽屉已删除） |
| WP-008 | 汉堡键语义（宽屏隐藏） | ⛔ 被取代（2026-08-05：汉堡键随抽屉移除，EditorComponents.kt:112） |
| WP-009 | 文件树横向滚动（最小宽 248dp） | 未完成 |
| WP-010 | 版本目录（version catalog，P2 可选） | 未完成 |

### 4.3 功能缺失（任务单已完成项移出；剩余为 P2 以外事项）

- ✅ 已落地（2026-08-06，任务单 EDITOR_FEATURE_COMPLETION_WORK_ORDER G1~G9/G1x）：设置中心 21 项、原生 SnippetController、诊断多错误聚合 + quickfix、整词搜索、文本操作、标识符补全（默认关）、迷你地图、行为开关。
- ⛔ 折叠交互：vendored 0.24.6 无此能力（上游 0.25+），保持「仅渲染不交互」；待 P3 升级管线后复核。
- 未做：Inlay hints（上游编辑器无此能力）、未启用 Snippet 时的手写占位符剥离（P3 示例）。

---

## 5. 验收基线（改编辑器必测）

```
编译：  .\gradlew.bat :app:compileArm64DebugKotlin --console=plain
构建：  .\gradlew.bat :app:assembleX64Debug --console=plain（模拟器可装）
R1：    rg -n "luajava|androlua|loadLibrary" editor/src   # 零命中（已并入 CI job verify-r1）
单测：  .\gradlew.bat :editor:testDebugUnitTest（LuaDiagnosticsTest 24 例等）
回归：  scripts/adb_regress.ps1（4 工程矩阵）；scripts/perf-check.ps1（性能基准）
```

## 6. 设置 ↔ sora API 映射（21 项全集，D1）

见任务单 EDITOR_FEATURE_COMPLETION_WORK_ORDER §7 全表；分组：通用（字号 8..32 / Tab 宽 1..8 / Tab 用空格）/ 编辑（自动缩进·闭合·补全·标识符补全·行级词法断言·只读）/ 视图（换行·行号·当前行·括号对·代码块线·迷你地图·粘性滚动·行号面板）/ 输入（空白·缩放·光标动画·硬键盘优先）。
持久化：`SettingsManager.kt`（`lide_editor` 21 键，StateFlow 镜像 + `apply()`），`:app` 只传入 `:editor` 纯值 `EditorConfig`，R4 满足。

## 7. sora-editor 版本台账索引

上游收纳与分层台账见 `third_party/sora-editor/README.md`（当前 0.24.6，`VERSION` 文件维护）。升级路径：`scripts/update-sora-editor.ps1`（手动）/ `.github/workflows/update-sora-editor.yml`（每周自动 PR + verify-vendor 一致性校验）。
