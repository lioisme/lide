# delua 编辑器知识库

> 来源：`EDITOR_CRASH_ANALYSIS_REPORT.md`、`EDITOR_DRAWER_AUDIT_REPORT.md`、`EDITOR_PERFORMANCE_AUDIT_REPORT.md`、
> `EDITOR_PERFORMANCE_AUDIT_V2_REPORT.md`、`EDITOR_UI_ALIGNMENT_WORK_ORDER.md`、`AGENTS.md`。

---

## 1. 技术底座与铁律

- 编辑器 = `:editor` 模块（纯 Kotlin/Java）+ sora-editor + TextMate Lua 语法；R1 边界零命中（含注释）。
- **初始化顺序（官方 demo 顺序，勿改）**：`colorScheme` → `setEditorLanguage` → `setText`。
- **TextMate 只经 `TextMateInitializer`（进程级一次性）**，不得在 UI 线程调用；已预热到进程启动（`DeluaApp` + `CountDownLatch`）。
- 编辑器 API（setText / diagnostics 赋值 / subscribeEvent 回调）保持主线程；文本快照可在后台线程取（`Content.toString` 内部 ReadWriteLock）。
- 自动保存：快照 + 写盘均 `Dispatchers.IO`，`onRelease` 兜底保存（修过 dispose 顺序丢改动）。
- 大文件（>200 KB）：关括号高亮 / 当前块 / 自动换行，跳过行级诊断。
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

## 3. 性能现状与差距（V2 审计，以 Squircle-CE 为标杆）

| 项 | 现状 | 差距 |
|----|------|------|
| 打字 | 每键 2~10ms（IME 组合 15~40ms），逼近 16.6ms 帧预算边缘 | **P0-B 每键整屏重组**：`editVersion++` 写 Compose state → 全屏重组 ×(2~6)；Squircle 稳态零重组（`needsUpdate` 判定 <0.1ms） |
| 打开文件 | 77KB/901 行首帧在 UI 线程做 CodeEditor 构造 + setText(String)（O(n) + IME 重启） | **P0-A**：Squircle 在 IO 线程构造 `Content` + `reuseContentObject` 复用 |
| 搜索 | 每键整屏重组 ×2（query state + 结果事件 state） | P1-A：结果下沉到 SearchBar |
| 冷启动 | release 无 R8、无 baseline/startup profile | 与 Squircle（自带两份）差 2~3 倍 |
| 大文件 | undo 栈 2500 上限，内存增长明显 | 待评估 |

健康项（已确认）：预热、无全文镜像、IO 快照写盘、大文件降级、try/catch 兜底、增量分析正常。

---

## 4. 现状组件与待办（UI 对齐）

### 4.1 已存在（阶段 B 部分落地）

`:editor`：`EditorTabs.kt`、`ProjectFileTree.kt`、`SearchBar.kt`、`EditorToolbar.kt`、`LuaEditor.kt`、`LuaLanguage.kt`、`LuaDiagnostics.kt`、`LuaSnippetManager.kt`、`TextMateInitializer.kt`、`EditorConfig.kt`
`:app`：`session/EditorSession.kt`（StateFlow 会话 + Content 缓存）、`EditorScreen.kt`

### 4.2 剩余差距（按 `EDITOR_UI_ALIGNMENT_WORK_ORDER.md`，内部 WP 编号与 PROGRESS 是两套体系）

| WP | 内容 | 状态 |
|----|------|------|
| WP-001/002 | 抽屉返回键契约 / 关闭竞态 | ✅ 已由抽屉审计以官方 API 落地 |
| WP-003 | Tab 拖拽排序（需 `sh.calvin.reorderable:reorderable:3.0.0`，POM 实证兼容） | 未完成 |
| WP-004 | Tab 样式对齐 Squircle（扁平 + 3dp 底部指示条） | 未完成 |
| WP-005 | 点击当前 Tab 弹关闭菜单 | 未完成 |
| WP-006 | 新 Tab 自动滚动 | 未完成 |
| WP-007 | 抽屉手势对齐（gesturesEnabled） | 未完成 |
| WP-008 | 汉堡键语义（宽屏隐藏） | ✅ 已落地（抽屉审计 Phase 5） |
| WP-009 | 文件树横向滚动（最小宽 248dp） | 未完成 |
| WP-010 | 版本目录（version catalog，P2 可选） | 未完成 |

### 4.3 功能缺失（V2 阶段 C，可选）

诊断 tooltip（DiagnosticsContainer）、原生 SnippetController、Inlay hints / minimap、TextMateNewlineHandler、增量保存。

---

## 5. 验收基线（改编辑器必测）

```
编译：  .\gradlew.bat :app:compileArm64DebugKotlin --console=plain
构建：  .\gradlew.bat :app:assembleX64Debug --console=plain（模拟器可装）
R1：    rg -n "luajava|androlua|loadLibrary" editor/src   # 零命中
单测：  .\gradlew.bat :editor:testDebugUnitTest（LuaDiagnosticsTest 14 例等）
回归：  scripts/adb_regress.ps1（4 工程矩阵 6/6 PASS）；抽屉五条回归清单（转屏、返回键、窄屏不盖顶栏、宽屏常驻）
```
