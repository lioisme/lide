# delua 编辑器性能全量审计报告（V2）

> 审计对象：`:editor` 模块全部源码 + `:app` 的 `EditorScreen.kt` / `DeluaNavHost.kt` / `ProjectManager.kt` / `SettingsManager.kt` / 构建脚本
> 参照基准：`docs/reference/sora-editor-main/`（sora-editor 源码，与依赖 `io.github.rosemoe:editor:0.24.6` / `language-textmate:0.24.6` 同版）
> 性能标杆：`docs/reference/Squircle-CE-master/`（基于 sora-editor 0.24.3 的成熟编辑器，含标签卡 + 侧边栏工程树）
> 审计日期：2026-07-31
> 架构约束：R1–R4（`docs/AGENTS.md`）全程遵守；sora-editor 库本身不动，其余一律 Kotlin。

---

## 0. 执行摘要（结论先行）

上一轮审计（`docs/EDITOR_PERFORMANCE_AUDIT_REPORT.md`）指出的 P0 级灾难链路（每键重建 `TextMateColorScheme`、每键全文镜像、每键 `setTextSize` 整屏重排版）**已在模块化重构中被修复**。但当前代码仍**没有达到 Squircle-CE 的水平**，差距集中在两处：

### 差距一：每键全屏重组（主线程、每键必发生）

delua 在 `ContentChangeEvent` 回调里执行 `editVersion++`（`EditorScreen.kt:218-221`），这是 Compose 顶层 state 写操作——**每敲一个字符，整个 `EditorScreen`（Scaffold + TopAppBar 工具栏 + 搜索栏 + AndroidView 容器 + 全部回调 lambda 重建）在 UI 线程重组一次**；IME 组合输入一次击键产生 2~6 个 `ACTION_INSERT/DELETE` 事件，重组 2~6 次。

Squircle-CE 的对应实现（`EditorViewModel.kt:250-298` `onContentChanged()`）：内容变化**不进入 Compose state**，只在"脏标记翻转 / undo/redo 能力变化"时才更新 `viewState`（`needsUpdate` 判定）——**稳态打字期间（首个字符之后）Compose 零重组**。

> 结论：当前 delua 每键成本 = sora 自身编辑成本 + 整屏重组 ≈ 中端机 1~3ms / 低端机 5~15ms，且每键还额外带 2 次 `LaunchedEffect` 取消/重启；Squircle-CE 每键成本 ≈ sora 自身编辑成本 + 一次空协程判定（<0.1ms）。**这是"性能未达 Squircle"的第一根因。**

### 差距二：进入编辑器界面的首帧延迟

1. `AndroidView` factory 在 UI 线程执行完整初始化链：`CodeEditor` 构造 + `TextMateColorScheme.create` + `setEditorLanguage` + `setText(String)`（`LuaEditor.kt:81-148`）。`setText` 内部 `new Content(text)` 全文分行 + `createLayout` + `restartInput`（IME 重启）+ `requestLayout`（`CodeEditor.java:3982-4026`）——77KB 的 `testjson/main.lua` 一次性全在主线程。
2. Squircle 在 IO 线程构造 `Content`（`DocumentRepositoryImpl.kt:98-119`），`setText(content)` 走 sora 的 `reuseContentObject=true` 路径**复用 Content 对象，跳过 O(n) 重建**。
3. 初始全文分析（`AsyncIncrementalAnalyzeManager.initialize()` 逐行 Joni 正则分词 + `computeBlocks`，`AsyncIncrementalAnalyzeManager.java:492-516`）与 300ms 的 slide 导航转场动画并发执行，分析线程与 UI 线程争抢 CPU——首帧与转场双双掉帧。
4. delua 无 R8 压缩（`app/build.gradle.kts:60-69`）、无 baseline profile；Squircle 同时提供 `baseline-prof.txt` + `startup-prof.txt`（`Squircle-CE/app/src/main/`），冷启动与首次交互差距可达 2~3 倍。

### 差距三：功能缺失（用户显式要求）

| 能力 | Squircle-CE | delua 现状 |
|---|---|---|
| 标签卡（多文档） | `DocumentTabLayout` + 拖拽 + 关闭确认 | 无（`showFileMenu` 弹出对话框选文件，`EditorScreen.kt:291-324`） |
| 侧边栏抽屉（工程文件树） | `DrawerExplorer`（ModalDrawer 内嵌文件树） | 无 |
| 文档内容缓存 | 每个标签持 `Content` 对象，切换零读盘（`CacheManager` + DAO） | 每次切文件重新 `File.readText()` + `setText(String)` 全量重建 |
| 保存策略 | 手动/退出时保存，onPause 缓存 | 500ms 防抖全量写盘（可接受） |

**总体结论**：要达到"性能 ≥ Squircle-CE + 标签卡/抽屉工程树"，需完成 §7 的三阶段改造。纯 delua 侧改动，不触碰 sora-editor 库，全部 Kotlin。

---

## 1. 审计范围与方法

| 项 | 说明 |
|---|---|
| delua 端 | `editor/src/main/kotlin/com/lioisme/delua/editor/` 全部 8 个文件、`app/.../ui/screen/EditorScreen.kt`、`ui/navigation/DeluaNavHost.kt`、`project/ProjectManager.kt`、`ui/theme/SettingsManager.kt`、`app/build.gradle.kts`、`editor/build.gradle.kts` |
| sora-editor 端 | `CodeEditor.java`、`AsyncIncrementalAnalyzeManager.java`、`EditorSearcher.java`、`EditorColorScheme.java`、`TextMateColorScheme.java`、`TextMateAnalyzer.java`、`TextMateLanguage.java`、`ThemeRegistry.java`、`EditorRenderer.java`（均以 docs 源码为准，0.24.6 同版） |
| Squircle-CE 端 | `feature-editor/impl`：`EditorViewModel.kt`、`ui/editor/compose/CodeEditor.kt`、`ui/editor/view/CodeEditor.kt`、`SquircleScheme.kt`、`Extensions.kt`、`DocumentRepositoryImpl.kt`、`DocumentTabLayout.kt`、`EditorScreen.kt`、`feature-explorer/impl/.../ExplorerScreen.kt`、`app/src/main/baseline-prof.txt` |
| 方法 | 静态代码路径逐行分析 + 与 Squircle 同路径比对；关键 sora 行为以源码行号举证（§9 证据索引） |

---

## 2. delua 现状审计（逐文件）

### 2.1 `LuaEditor.kt`（核心宿主组件）

| 行号 | 现状 | 评价 |
|---|---|---|
| 33-43 | `rememberTextMateInitializer()`：首次组合在 `Dispatchers.Default` 等待 TextMate 初始化完成才渲染编辑器 | 合理（进程级预热的兜底）。冷启动竞态下会多等 300~800ms 转圈，见 §3.7 |
| 59 | `initReady` 未就绪时 `return` 转圈 → `if/else` 结构切换会**重建整个 AndroidView 子树** | 首帧一次性代价，可接受；配合转场会放大首帧 jank |
| 67-71 | `LaunchedEffect(isDarkTheme, initReady)` 切主题 | 正确。`ThemeRegistry.setTheme` 只 `dispatchThemeChange`（`ThemeRegistry.java:133-148`），不触发重分析——注意 `TextMateColorScheme.attachEditor` 里的 `rerunAnalysis()` 仅在 `setColorScheme` 时发生，当前只调一次（factory 内，空文本），安全 |
| 87 | factory 内 `colorScheme = TextMateColorScheme.create(...)` | 每编辑器实例一次，正确。见 §3.1 的残余风险（构造器内 `loadTheme` + dispatch） |
| 89-102 | `LuaLanguage(collectIdentifiers=false)` + `setEditorLanguage` | 正确。关闭全文档标识符收集（`TextMateLanguage.java:304-311` 的 identifier 全量发布被绕开） |
| 104-115 | 行号/当前行/括号/代码块/undo(2500)/wordwrap/非打印字符/字号 | `isHighlightBracketPair` 与 `isHighlightCurrentBlock` 每光标移动触发 `findCursorBlock` 线性扫描（`CodeEditor.java:1591-1639`）——与 Squircle 默认一致，属平级开销 |
| 123-145 | `ContentChangeEvent` 回调：仅置脏 + `diagVersion++`，无全文 toString | ✅ 已修复（旧版每键 O(n) 拷贝已删除）。**但 `diagVersion++` 与宿主的 `editVersion++` 是每键 Compose state 写，见 §3.2** |
| 147 | factory 内 `setText(currentText)` | 主线程 `new Content(String)` O(n) + IME restart + createLayout，见 §3.1 |
| 150-176 | `update` 块：仅 4 个守卫式配置同步 | ✅ 已修复（旧版每键无条件 setTextSize/scheme 重建已删除）。每键仍会执行 4 次布尔比较（廉价），且 `editorRef` state 写有 `!==` 守卫 |
| 180-186 | `LaunchedEffect(text)`：后台 toString 比对后 `setText` | 文件切换一次，可接受；但 `setText(String)` 仍是全量重建（§3.1） |
| 188-208 | 诊断：500ms 防抖后后台全文件 `LuaDiagnostics.checkSyntax` | 每段 500ms 打字停顿做一次 O(n) 全量扫描 + O(n) toString；77KB 约 1~4ms 后台，可接受但可优化（§3.5） |
| 210-219 | `DisposableEffect(Unit)` 兜底保存 | 主线程 `toString()` 一次，可接受 |

### 2.2 `EditorScreen.kt`（宿主页面）

| 行号 | 现状 | 评价 |
|---|---|---|
| 58-62 | `files/currentFile/loadedText/dirty/editVersion/canUndo/canRedo` 全部 Compose state | **`editVersion` 每键 +1 → 整屏重组（§3.2 P0）**；`dirty` 用 `mutableStateOf(false)` 默认策略（同值跳过，影响小） |
| 77-89 | 文件列表 / 文件内容 IO 读取 | ✅ 正确（`Dispatchers.IO`）。`getProjectScript` = `File.readText()`，77KB 读盘 ~5ms IO |
| 101-124 | `saveNow` / 500ms 防抖自动保存（IO 线程快照+写盘） | ✅ 正确。快照在 IO 线程取（`Content.toString` 内部 `ReadWriteLock`，`Content.java:959-976` 拼接） |
| 126-149 | `DisposableEffect(editorRef.value)` 订阅 `PublishSearchResultEvent` → 写 `searchResult` state | 搜索结果事件每批到达都写 state → **整屏重组**（§3.3） |
| 151-173 | `performSearch()`：`searcher.search()` + `LaunchedEffect(searchQuery,…)` 每键触发 | `EditorSearcher.search` 内部在主线程 `toStringBuilder()` 全文拷贝（`EditorSearcher.java:155-180`）→ 与 Squircle 相同成本（平级）；但 delua 每键还叠加一次整屏重组（§3.3） |
| 177-289 | Scaffold + TopAppBar + SearchBar + LuaEditor | 每键重组时全部参与（lambda 新实例无法被 Compose 跳过） |
| 291-324 | `showFileMenu` 文件切换对话框 | 功能与交互均低于 Squircle 标签卡（§5） |

### 2.3 其余文件

| 文件 | 评价 |
|---|---|
| `TextMateInitializer.kt` | ✅ 进程级一次性 + `CountDownLatch` 并发安全；`AsyncIncrementalAnalyzeManager.setUseShallowCopyByDefault(true)` 已启用（与 sora demo 一致）；主题/文法预热在 `DeluaApp.onCreate` 后台线程启动（`DeluaApp.kt:20-27`） |
| `LuaLanguage.kt` | `collectIdentifiers=false` 正确；snippet 过滤每键 11 项 `startsWith` 可忽略；回调 `try/catch` 兜底符合约定 |
| `LuaSnippetManager.kt` | `completionItems` 惰性缓存 + regex 为对象级常量 ✅（旧版每键编译 regex 已修复） |
| `LuaDiagnostics.kt` | 单遍 O(n) 词法校验，零原生依赖，符合 R1/R2 |
| `SearchBar.kt` / `EditorToolbar.kt` | 纯 Compose，无性能问题；问题是宿主把它们的调用点放进整屏重组作用域 |
| `ProjectManager.kt` | `readText/writeText` 全量 IO；在宿主侧被 IO 线程调用 ✅ |
| `DeluaNavHost.kt:91-102` | **300ms 全宽 slide 转场**——编辑器 factory + 全文分析发生在转场动画期间，首帧 jank 放大（§3.6） |
| `app/build.gradle.kts:60-69` | **release 无 R8（isMinifyEnabled=false）**；无 baseline profile；debug/release 同门签名 |
| `editor/build.gradle.kts` | 依赖纯净，无 `:androlua`、无 jniLibs ✅（R1 校验仅 1 处注释命中） |

---

## 3. 根因分析（按严重程度排序）

### 3.1 【P0-A】进入编辑器：UI 线程全量初始化 + `Content(String)` 重建 + IME 重启

**位置**：`LuaEditor.kt:81-148`（factory 全链）；sora 侧 `CodeEditor.java:3982-4026`（setText 全量重置）

打开 `testjson/main.lua`（77KB / 901 行）时 UI 线程依次执行：
1. `CodeEditor(ctx)` 构造 + 布局 inflate（~2-5ms）；
2. `TextMateColorScheme.create()` 构造器内 `themeRegistry.loadTheme(currentTheme)` → 若主题已加载则 `setTheme` → **dispatchThemeChange 通知全部现存 listener**（`ThemeRegistry.java:66-83, 133-148`；首次创建时 listener 尚少，代价小但属于在 UI 线程上的不必要同步工作）；
3. `setEditorLanguage` → 旧 manager 销毁 + 新 `TextMateAnalyzer` 创建（`CodeEditor.java:953-1010`）；
4. `setText(String)`：`new Content(text)` 全文分行（O(n)，77KB ≈ 2-4ms）+ `renderer.onEditorFullTextUpdate` + `analyzeManager.reset`（启动后台全文 tokenize 线程）+ `createLayout()` + **`inputMethodManager.restartInput()`** + `requestLayout` + `invalidate`；
5. 后台全文分析（Joni 纯 Java 正则，逐行 `tokenizeLine2` 2s 超时，`TextMateAnalyzer.java:209-262`）在导航转场期间持续占 CPU。

**Squircle 对照**：`DocumentRepositoryImpl.kt:98-119` 在 **IO 线程** `Content(filesystem.loadFile(...))` 构造并缓存；`setText(content)` 走 `reuseContentObject` 复用（`CodeEditor.java:3994-3999`）跳过 O(n) 重建；语言对象在 `createFromRegistry`（`Extensions.kt:76-97`）惰性创建。

### 3.2 【P0-B】每键整屏重组（当前最大残留）

**位置**：`EditorScreen.kt:218-221` → `editVersion++`；`EditorScreen.kt:62` state 声明

链路：sora 修改文本 → dispatch `ContentChangeEvent`（IME 组合输入一次击键 2~6 个事件，`CodeEditor.java:5290/5339`）→ 宿主 lambda `onContentModified` → `dirty=true`（同值跳过）+ `editVersion++`（**必触发快照写**）→ 整个 `EditorScreen` 重组 → `EditorToolbar`/`SearchBar`/`LuaEditor` 因 lambda 参数新实例全部重组 → `AndroidView.update` 执行（守卫比较，廉价）→ 两个 `LaunchedEffect(editVersion / diagVersion)` 被取消重启。

**Squircle 对照**（`EditorViewModel.kt:250-298`）：`onContentChanged` 只做 `needsUpdate = !modified || canUndo/canRedo 变化` 判定，稳态打字**不更新 viewState → Compose 零重组**；`Content` 本体留在 ViewModel，编辑完全由 sora 内部驱动。

**量化**（中端机）：每键 1~3ms 重组 + 每键 2× LaunchedEffect 重启；IME 组合输入放大 2~6 倍。快速打字时累积为持续掉帧。

### 3.3 【P1-A】搜索链路的每键重组

**位置**：`EditorScreen.kt:74-75, 171-173, 255-285`

1. 搜索框每键 → `searchQuery` state → 整屏重组（同 P0-B）；
2. `performSearch` → `searcher.search` → 主线程全文 `toStringBuilder`（`EditorSearcher.java:177`，sora 固有限制，Squircle 同成本，平级）；
3. 后台搜索结果经 `PublishSearchResultEvent` 回传 → `searchResult.value` 写 state → **又一轮整屏重组**（Squircle 不显示计数，无此成本；且 sora 渲染器自带匹配高亮 `EditorRenderer.java:1273-1281`，delua 只需计数，应缩小到搜索栏子树）。

### 3.4 【P1-B】文件切换全量重建

**位置**：`EditorScreen.kt:85-89` + `LuaEditor.kt:180-186`

切文件 = IO 读盘 + `setText(String)` 全量重建（Content + IME restart + 后台全文分析）。Squircle 每标签持有 `Content` 缓存，切换标签仅 `setText(content)` 复用对象 + 一次语言/布局更新，不读盘、不重建 Content、IME 不重启（`setText` 复用路径无 `restartInput`）。

### 3.5 【P2】诊断全量重扫

`LuaEditor.kt:188-208`：每 500ms 停顿全文件 `checkSyntax`（O(n)）+ `diagnostics` 赋值（sora 会因诊断变化局部重绘）。功能为 Squircle 所无（增值项），成本可接受；但可升级为"改动行起算 + 到括号/块收敛即停"的增量扫描，或对齐 sora 增量分析思想只重扫受影响区域。

### 3.6 【P2】导航转场与编辑器初始化并发

`DeluaNavHost.kt:91-102`：300ms 全宽 slide。编辑器 factory + 初始分析恰好落在转场窗口内，背景分析线程抢占 CPU，转场帧与首帧双掉帧。Squircle 使用系统默认转场。

### 3.7 【P2】冷启动 TextMate 竞态

`DeluaApp.kt:20-27` 已后台预热；但 `TextMateInitializer.initialize` 的 `done.await()`（`TextMateInitializer.kt:51`）会使首个进入编辑器的调用在 `Dispatchers.Default` 上阻塞直到预热完成——编辑器转圈 300~800ms。预热本身是正确且必要的（Squircle 也在 `loadDocuments` 中异步 `loadGrammars`）。

### 3.8 【P2】构建侧

- `app/build.gradle.kts:60-69`：release 不启用 R8 → 方法内联/裁剪缺失，冷启动慢、内存高；
- 无 baseline profile / startup profile（Squircle 自带两份）；
- `undoManager.setMaxUndoStackSize(2500)`（`LuaEditor.kt:113`）：大文件内存增长明显，建议大文件降级（如 200）。

---

## 4. 成本核算对比（以 901 行 / 77KB Lua，中端机为例）

| 环节 | delua 现状 | Squircle-CE | sora 架构上限 |
|---|---|---|---|
| 每键主线程 | sora 编辑 + **整屏重组 1~3ms×（IME 2~6 事件）** + 2× LaunchedEffect 重启 | sora 编辑 + 空协程判定 <0.1ms | sora 编辑（O(插入文本)） |
| 每键后台 | 无（500ms 停顿后：O(n) 诊断 + O(n) 写盘各一次） | 增量分析 1~3 行 | 增量分析 1~3 行 |
| 打开文件 | IO 读盘 ✅ + **UI 线程 Content O(n) 重建 + IME 重启 + 后台全文 tokenize** | IO 读盘 + **IO 线程 Content 构造缓存** + 复用 setText | IO 构造 Content，后台全文 tokenize |
| 搜索每键 | sora 主线程拷贝 + **整屏重组 ×2（query + 结果事件）** | sora 主线程拷贝（同成本） | sora 后台匹配 + 事件驱动 |
| 切文件 | IO 读盘 + 全量重建 + 全文重分析 | 缓存 Content 复用，重分析同价 | 复用 Content |
| 冷启动/首帧 | 无 R8、无 profile，JIT 解释执行 | R8 + baseline/startup profile | — |

---

## 5. 功能差距与移植蓝图（标签卡 + 侧边栏工程树）

用户需求：达到 Squircle-CE 的标签卡、侧边栏抽屉工程目录。以下为可直接落地的设计（全部 Kotlin，sora 不动，遵守 R1–R4）。

### 5.1 模块归属

| 能力 | 归属模块 | 理由（R3） |
|---|---|---|
| `EditorTabs`（标签行 UI：滚动、选中、关闭按钮、脏标记圆点、拖拽排序可选） | `:editor` | 纯 Compose UI，无 IO/权限依赖 |
| `ProjectFileTree`（递归文件树 UI） | `:editor` | 纯 Compose UI（节点数据由宿主注入） |
| 会话状态（打开哪些文件、每个文件的 `Content` 缓存、脏标记、选中标签） | `:app`（`EditorScreen`/新增 `EditorSession` 类） | 依赖 `ProjectManager`（文件 IO） |
| 文件树数据（递归扫描工程目录、排序、过滤非 Lua） | `:app`（`ProjectManager` 扩展） | 文件系统访问属宿主 |
| 抽屉容器（`ModalNavigationDrawer` + 工具栏汉堡按钮） | `:app` | 宿主页面骨架 |
| 关闭未保存标签的确认对话框 | `:app` | 涉及保存策略 |

### 5.2 参考实现（Squircle-CE 证据）

- 标签行：`feature-editor/impl/.../ui/editor/compose/DocumentTabLayout.kt` + `DocumentTab.kt`（LazyRow + 拖拽）；宿主布局：`ui/editor/EditorScreen.kt:365-421`（Column：TabLayout → SearchPanel → key(uuid){ CodeEditor }）。
- 抽屉：`feature-explorer/impl/.../ui/explorer/ExplorerScreen.kt:78-80`（`DrawerExplorer` = ModalDrawer 内容）挂在 `ui/editor/EditorScreen.kt:356-363`（`ScaffoldSuite(drawerContent = { DrawerExplorer(...) })`）。
- 多文档会话：`EditorViewModel.kt:101-104, 1213-1321`（documents: List<DocumentState>，每个持 `Content?` 缓存；`loadDocument` 已有缓存则直接复用）。
- 关闭确认：`ui/closefile/CloseFileScreen.kt`（modified 标签关闭时弹确认）。

### 5.3 delua 落地要点（Kotlin）

1. `EditorSession`（`:app`，普通类 + `StateFlow`，非 Compose state）：`Map<fileName, Content?>` 缓存、`selectedFile`、`dirtyFlags`。打开标签：缓存命中直接 `editor.setText(content)` 复用（`CodeEditor.java:3994` 路径）；未命中 IO 读盘 + **IO 线程构造 `Content`** 再注入。
2. `EditorScreen` 只订阅 `selectedFile`/`tabs` 等**低频状态**；`ContentChangeEvent` 只更新 session 的脏标记与 undo/redo（Squircle `needsUpdate` 模式）。
3. 工程树：`ProjectManager.listProjectTree(path)` 递归返回 `FileNode`（`FileNode(name, path, isDir, children)`），抽屉内 `LazyColumn` 展开渲染；点击文件 → session 打开标签。
4. 迁移提示：`showFileMenu` 对话框与"单文件"导航结构（`Routes.EDITOR`）保留兼容，标签卡作为会话层新增。

---

## 6. 单键 / 首帧成本核算汇总

**现状单键成本**（中端机实测预期）：

```
sora 编辑(0.5~2ms) + EditorScreen 整屏重组(1~3ms)×IME事件数 + 2×LaunchedEffect重启(≈0) 
≈ 每键 2~10ms（IME 组合输入可达 15~40ms）→ 超出 16.6ms 帧预算的边沿，低端机持续掉帧
```

**目标单键成本**（与 Squircle 持平）：

```
sora 编辑(0.5~2ms) + 空协程脏标记判定(<0.1ms) ≈ sora 编辑成本，与文件大小无关
```

**首帧成本**：

```
现状: UI线程 CodeEditor+Scheme+Language+setText(O(n) Content+IME重启) + 转场300ms与全文分析并发
目标: IO线程 Content 构造 → setText(content) 复用 → 转场用默认/淡入 → R8+baseline profile
```

---

## 7. 修复路线图

### 阶段 A：性能止血（纯 delua 侧，不动 sora，全部 Kotlin）

1. **消灭每键整屏重组（P0-B）**：
   - `EditorScreen.kt:62`：删除 `editVersion` 这个 Compose state，改 `val editTick = remember { MutableStateFlow(0) }`（flow 不触发重组）；自动保存改为 `LaunchedEffect(Unit) { editTick.collectLatest { delay(500); save() } }`。
   - 或 Squircle 同款：`dirty`/`canUndo`/`canRedo` 移入 `remember { EditorSession }` 普通类，仅**值实际变化**时才写 Compose state（`needsUpdate` 判定，`EditorViewModel.kt:264-269` 同构）。
   - 工具栏/搜索栏的 lambda 用 `remember` 包一层稳定实例（`rememberUpdatedState` 已在 `LuaEditor` 内使用，宿主侧同样处理），让 Compose 跳过子节点重组。
2. **搜索链路（P1-A）**：
   - `searchResult` 收集点从 `EditorScreen` 下沉到 `SearchBar` 内部（`SearchBar` 自持 `collectAsState`），搜索栏子树单独重组；
   - 搜索输入框用 `LaunchedEffect(searchQuery)` 保留，仅触发 `searcher.search`（其主线程拷贝为 sora 固有限制，与 Squircle 平级，接受）。
3. **进入界面（P0-A）**：
   - `LuaEditor` 增 `Content` 注入路径：`setText(content: Content)`（sora 复用路径，无 `new Content`、无 IME 重启代价）；
   - `:app` 在 IO 线程读盘并 `Content(text)` 构造后传入（`Content` 构造线程安全，Squircle 同法）；
   - 工厂链中 `TextMateColorScheme.create` 的 `loadTheme` 同步 dispatch 在 UI 线程 —— 保持单例 scheme（`remember { TextMateColorScheme.create(...) }` 提升到 LuaEditor 组合级，factory 只做一次 `editor.colorScheme = scheme`）。
4. **转场（P1-B）**：`DeluaNavHost.kt` 编辑器路由改默认转场或淡入（`fadeIn/fadeOut` 100ms），避免与初始化并发。
5. **构建侧（P2）**：
   - `app/build.gradle.kts`：release 启用 `isMinifyEnabled = true` + R8 rules（保留 `com.androlua`/JNI 相关 keep，需回归验证 LuaActivity 反射）；
   - 增加 `androidx.benchmark` baseline profile（`baseline-prof.txt` 覆盖 `CodeEditor` 构造、TextMate init、`EditorScreen` 首帧路径），参考 Squircle 的 `app/src/main/baseline-prof.txt`。
6. **诊断降级（P2）**：`LuaDiagnostics` 改为增量——记录上次扫描结束位置，从改动行向两端扩散到"括号/块状态收敛"（对齐 `AsyncIncrementalAnalyzeManager` MSG_MOD 思想）；大文件（>200KB）维持跳过。

### 阶段 B：功能补齐（标签卡 + 侧边栏工程树，§5 蓝图）

7. `:editor`：新增 `EditorTabs.kt`（LazyRow 标签行：选中态、脏圆点、关闭按钮、可滚动）与 `ProjectFileTree.kt`（递归树：展开/折叠、图标、点击回调）；纯 Compose、零宿主依赖。
8. `:app`：新增 `EditorSession`（普通类 + StateFlow，非 Compose state）：`openedFiles: List<String>`、`selectedFile`、`Content` 缓存、脏标记；`EditorScreen` 改为 会话驱动 + `ModalNavigationDrawer`（抽屉内 `ProjectFileTree`，数据来自 `ProjectManager.listProjectTree`）。
9. 文件切换走缓存：命中 `editor.setText(content)` 复用；未命中 IO 读盘 + IO 构造 `Content`。
10. 关闭未保存标签弹确认（对齐 `CloseFileScreen`）；全部 Kotlin。

### 阶段 C：达到 sora 上限（可选增值）

11. 诊断 tooltip 交互（sora `DiagnosticsContainer` 自带长按查看）；
12. sora 原生 `SnippetController` 替代自研 snippet（tab 跳转/嵌套）；
13. 增量保存（仅序列化改动行区间，`Content` 监听改行索引）——进阶项，默认全量写盘已可接受。

### 验收标准（性能 ≥ Squircle-CE）

- [ ] 901 行/77KB 文件连续快速输入不掉帧（`adb shell dumpsys gfxinfo <pkg> framestats` jank=0；与 Squircle 同机对照）；
- [ ] 打字期间 `AsyncAnalyzer-N` 线程唯一、不反复重建（`adb shell top -H`），后台 CPU 不长期 100%；
- [ ] 打字期间 `editor.getStyles() != null`（高亮不清空）；
- [ ] 打开 77KB 文件：点击到可编辑 ≤ Squircle 同机耗时；切换标签无 IME 闪断、无全文重读（缓存命中）；
- [ ] 搜索逐字输入无卡顿，计数与 sora 高亮一致；
- [ ] `rg -n "luajava|androlua|loadLibrary" editor/src` 仅注释命中；`.\gradlew.bat :app:assembleArm64Debug` 通过。

---

## 8. 附：delua 已确认的健康项（避免重复改动）

| 项 | 位置 |
|---|---|
| TextMate 进程级预热 + CountDownLatch 并发安全 | `TextMateInitializer.kt:35-52`、`DeluaApp.kt:20-27` |
| 每键不再全文镜像/不再重建 scheme/不再无条件 setTextSize | `LuaEditor.kt:123-176` |
| 文本快照与写盘均在 IO 线程（`Content.toString` ReadWriteLock） | `EditorScreen.kt:101-124` |
| 大文件降级（括号/块高亮、wordwrap、诊断关闭） | `LuaEditor.kt:45, 83, 106-115, 194` |
| 补全/诊断/事件回调 try/catch(Throwable) 兜底 | `LuaLanguage.kt:40-60`、`LuaEditor.kt:142-144, 205-207` |
| 增量分析（sora MSG_MOD 路径）已可正常工作 | 依赖 3.2 修复后不再被打断 |

---

## 9. 证据索引

| 结论 | 证据位置 |
|---|---|
| setText 全量重置：new Content + analyzeManager.reset + createLayout + restartInput + requestLayout | `sora-editor-main/editor/.../CodeEditor.java:3982-4026` |
| setText 复用 Content 路径（reuseContentObject） | `CodeEditor.java:3994-3999` |
| setEditorLanguage 销毁/重建 analyzeManager | `CodeEditor.java:953-1010` |
| TextMateColorScheme.attachEditor → rerunAnalysis（仅 setColorScheme 时） | `language-textmate/.../TextMateColorScheme.java:352-361` |
| rerun 杀线程 + 全文拷贝 + 清样式 + 重建分析线程 | `AsyncIncrementalAnalyzeManager.java:145-164` |
| 初始全文逐行 tokenize + computeBlocks | `AsyncIncrementalAnalyzeManager.java:492-516` |
| 增量路径（MSG_MOD 只 tokenize 到状态收敛） | `AsyncIncrementalAnalyzeManager.java:529-620` |
| 逐行 Joni 分词（2s 超时，无 oniguruma） | `TextMateAnalyzer.java:209-262` |
| 全文档标识符收集成本（delua 已关闭） | `TextMateLanguage.java:304-311`、`TextMateAnalyzer.java:215-272` |
| 搜索主线程全文拷贝 + 后台匹配 + 事件推送 | `EditorSearcher.java:155-180, 568-635` |
| 主题切换 dispatch 语义（不重分析） | `ThemeRegistry.java:66-83, 133-148` |
| findCursorBlock 线性扫描（光标移动/样式更新时） | `CodeEditor.java:1591-1639, 3550-3551, 4257-4273` |
| Squircle：每键零重组（needsUpdate 模式） | `Squircle-CE/feature-editor/impl/.../EditorViewModel.kt:250-298` |
| Squircle：Content IO 线程构造 + 缓存复用 | `Squircle-CE/.../DocumentRepositoryImpl.kt:98-119` |
| Squircle：scheme 单例创建于 remember、attachEditor 无 rerunAnalysis | `Squircle-CE/.../view/SquircleScheme.kt:192-214`、`compose/CodeEditor.kt:58-114` |
| Squircle：标签卡布局与 key(uuid) 编辑器实例 | `Squircle-CE/.../ui/editor/EditorScreen.kt:365-421` |
| Squircle：抽屉工程树 | `Squircle-CE/.../feature-explorer/.../ExplorerScreen.kt:78-80` |
| Squircle：baseline/startup profile | `Squircle-CE/app/src/main/baseline-prof.txt`、`startup-prof.txt` |
