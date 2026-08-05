# lide 宿主（IDE）性能审计报告（V2 · 2026-08-01）

> 审计对象：`:editor` 模块全部源码 + `:app` 的 `EditorScreen.kt` / `EditorSession.kt` /
> `ProjectListScreen.kt` / `ProjectManager.kt` / `LideApp.kt` / `SettingsManager.kt` / 构建脚本
> 参照基准：`docs/reference/sora-editor-main/`（sora-editor 0.24.6 源码）、`docs/reference/Squircle-CE-master/`
> 验证环境：x64 Android 14 模拟器（emulator-5554，adb root）
> 关联报告：`docs/completed/EDITOR_PERFORMANCE_AUDIT_REPORT.md`（V1，P0 问题已全部落实修复）

---

## 0. 执行摘要

V1 报告（2026-07-31）列出的 P0 问题**已全部修复**并经代码核实：

| V1 问题 | 现状 |
|---|---|
| P0-1 每键重建 TextMateColorScheme → 全文重分析 | ✅ factory 单例 + update 仅主题名变化才换（`LuaEditor.kt:111`） |
| P0-2 每键全文 toString + 状态回写整屏重组 | ✅ 编辑事件只递增 `editTick`（StateFlow，零重组），脏标记值变化才发射 |
| P0-3 每键 setTextSize / setTabWidth → 整屏重排版 | ✅ update 块全部 diff 化（`LuaEditor.kt:179-205`） |
| P0-4 全文镜像 + O(n) 比较 | ✅ 已删除；`Content` 缓存复用（`EditorSession`） |
| P1-1 搜索主线程重算计数 | ✅ 改为 `PublishSearchResultEvent` 事件驱动（`SearchBar.kt`） |
| P1-4 stickyScroll 每帧开销 | ✅ 已移除 |
| P1-5 代码块高亮冗余二次调用 | ✅ 已去重（大文件自动关闭） |
| P1-6 补全每键全文档标识符 | ✅ `collectIdentifiers = false` + snippet 惰性缓存 |
| 诊断 | ✅ 500ms 防抖 + 后台增量扫描 + 大文件跳过 |

**本次 V2 审计结论**：架构主体（会话驱动 + StateFlow 信号分离 + 增量诊断 + 事件驱动搜索）已对齐
Squircle `EditorViewModel` 范式，无"随时间/滑动越来越卡"的累积性缺陷（sora 搜索线程会
interrupt 旧任务、分析线程空闲即退出并释放 shadowed 文本、TextMate 各 registry 无无界缓存、
`LuaActivity.sLuaActivityMap` 在 onDestroy 清理、/data/anr 内无 lide 记录）。

**仍存在的问题**集中在 6 处低成本修正（见 §1），本次一并实施并做设备回归验证。

---

## 1. 问题清单（按优先级）

### P1 级（本次修复）

| # | 位置 | 问题 | 影响 |
|---|---|---|---|
| P1-1 | `EditorScreen.kt:95` | `Log.d("LideDebug", …)` 位于组合体顶部，**每次重组都执行**（含字符串拼接分配）；重组源：脏标记 / undo 能力 / 选中文件 / 主题 / 对话框状态等 | 调试残留 + 每重组日志开销 |
| P1-2 | `LuaEditor.kt:247-256` + `EditorScreen.kt:636` | `DisposableEffect.onDispose` 在主线程执行 `editor.text.toString()`（O(n) 全文拷贝 + 大对象分配），但宿主 lambda `{ session.flushSelectedDirtyAsync() }` **直接丢弃该字符串** | 退出编辑器时无谓的主线程 O(n) 拷贝与 GC 压力 |
| P1-3 | `SearchBar.kt:94-96` | `LaunchedEffect(query, …)` 每敲一键立即 `searcher.search()`；sora `EditorSearcher.executeMatch` 在**调用线程（主线程）**做 `content.toStringBuilder()`（O(n)）+ interrupt + `new Thread` | 大文件（>200KB，诊断已跳但搜索不跳）下每键主线程 O(n) 拷贝 + 线程对象风暴；连续键入时搜索频繁重启 |
| P1-4 | `EditorScreen.kt:91-94` | `selectedFile` 与 `selectedContent` 两个 StateFlow 分两次发射 → 文件切换产生**两次整屏重组**（第一帧空 content、第二帧带 content） | 文件切换帧率浪费 |

### P2 级（本次修复，低成本）

| # | 位置 | 问题 |
|---|---|---|
| P2-1 | `ProjectListScreen.kt:215` | `itemsIndexed` 无 `key` → 刷新/重排后列表项组合状态按索引错位（滚动位置跳变） |
| P2-2 | `ProjectListScreen.kt:81-86` | `if (isRefreshing) { LaunchedEffect(Unit){…} }` 组合期条件副作用模式；应改为 `LaunchedEffect(isRefreshing)` |
| P2-3 | `ProjectListScreen.kt:68-79` | 三个 LaunchedEffect 可能对同一列表并发/重复 `loadProjects()`（refreshKey 与 pendingImportPaths 同时生效时读盘两次） |
| P2-4 | `EditorScreen.kt:204-212` | `requestRun` 每次点击运行都 `getPackageInfo`（包管理器查询），结果在安装周期内不变，可缓存 |
| P2-5 | `EditorScreen.kt:308` | `tabs.map { it.tab }` 每次重组重建列表（含 `EditorTab` 对象分配） |

### P3 级（记录，不本次实施）

| # | 位置 | 问题 | 建议 |
|---|---|---|---|
| P3-1 | `LideApp.kt:36-106` | `extractExamplePlugin` / `extractLayoutTemplates` / `extractLuadoc` 在 Application.onCreate 主线程同步做 IO（存在性检查 + 复制） | 移入后台线程（与引擎无关，宿主页读取时已就绪）；`LuaApplication.extractLuaScripts` 需保证引擎运行时就绪，保留主线程（25 个小文件 + MD5 ≈ 2-6ms，可接受） |
| P3-2 | `app/build.gradle.kts:117` | Compose BOM 2024.09.00（Compose 1.7.3 / material3 1.3.0）偏旧；1.8/1.9 含大量重组/布局/lazy 性能修复 | 升级 BOM 前需验证 `sh.calvin.reorderable:3.0.0`（按 1.7.3 编译）二进制兼容与 material3 API 变更；建议单独分支试升级 |
| P3-3 | `EditorSession.kt:49` | `contents` 缓存无上限（每标签一份 Content + undo 栈） | 加 LRU 上限（如 12）且仅逐出"未选中且非脏"项；需与标签生命周期联动，本次不动 |
| P3-4 | `ProjectManager.kt:24-30` | `listProjects` 每次全量 `loadProject`（列目录 + 读 init.lua 解析） | 工程数 >50 时首次进入列表有可见延迟；可加目录 mtime 指纹缓存 |
| P3-5 | `LuaEditor.kt:176-178` | `editorRef`（Compose state）在 AndroidView update（apply 阶段）写入 | 已用恒等守卫不会成环；可改普通容器（AtomicReference）彻底脱离快照系统 |
| P3-6 | `EditorTabs.kt` | 关闭标签时选中态依赖 `coerceAtMost` 索引推断 | 行为正确；关闭尾部标签时建议滚动到新选中标签 |

---

## 2. 已核实无问题（防回归证据）

| 项 | 证据 |
|---|---|
| 搜索线程不累积 | `EditorSearcher.executeMatch()`（sora `EditorSearcher.java:173-180`）先 interrupt 上一搜索线程再新建 |
| 分析线程不泄漏 | `AsyncIncrementalAnalyzeManager.LooperThread.run()`（:630-646）`messageQueue.take()` 阻塞，`handleMessage` 返回 false 即退出，finally release shadowed 文本 |
| TextMate 无无界缓存 | GrammarRegistry 各 map 在 reload 时 clear；TextMateAnalyzer 仅缓存 1 个正则 |
| 引擎 Activity 静态表清理 | `LuaActivity.onDestroy()`（:802-811）`sLuaActivityMap.remove(pageName)` |
| lide ANR（旧安装遗留，本次重装后未复现） | `/data/anr/` 内 2026-08-01 06:03/06:10 有 2 份 lide ANR，均属**重装前的旧安装**（安装路径 `~~gu4vR5W…` / `~~vJ8RtOF…`，与当前 `~~ZSngyHW…` 不同）；主线程卡在 `EngineActivity.onCreate → LuaActivity.doFile → pcall → LuaPrint.execute → sendMsg → MessageQueue.enqueueMessage`（Lua 脚本主线程执行期阻塞 20s）。引擎侧特性（脚本在主线程 doFile），非编辑器缺陷；本次重装构建后运行 demo 复测未再现 |
| 引擎输出 Binder 截断 | `EngineActivity.OUTPUT_LIMIT = 200KB`（防 Binder 事务溢出） |
| 自动保存不抢主线程 | `editTick.collectLatest` 500ms 防抖 + `flushDirty` 内 `withContext(IO)` 取快照写盘 |
| 大文件降级 | >200KB：跳过行级诊断 / 关闭括号高亮 / 当前块 / 换行，undo 栈降为 200 |
| 补全 / 诊断 / 事件回调 | 全部 `try/catch(Throwable)` 降级，不逃逸 sora 主循环 |

---

## 3. 验证记录（2026-08-01，x64 Android 14 模拟器）

### 3.1 修复前基线（重装前旧安装）

| 项 | 数值 |
|---|---|
| 冷启动（force-stop 后 `am start -W`） | TotalTime ≈ 960ms |
| 内存（`dumpsys meminfo`） | PSS ≈ 116MB / Java Heap Alloc ≈ 25MB（空闲态） |
| 工程列表 | 6 个示例工程，LazyColumn 渲染正常 |
| 模拟器 ABI | x86_64（`libluajava.so` 已随 WP-006 补齐） |

### 3.2 修复后回归（`assembleX64Debug` 新装 + 全场景）

| 场景 | 结果 |
|---|---|
| 冷启动（force-stop 后 `am start -W`） | TotalTime = 847ms（≤ 基线 960ms，不劣化） |
| 编辑器连续输入（`input text` 两批） | 79 帧 / janky 5.06%（50th=5ms、90th=10ms、95th=21ms、99th=101ms；首帧含 IME 弹出） |
| 搜索键入（防抖验证） | 395 帧 / janky 2.28%（50th=6ms、90th=11ms、95th=14ms、99th=48ms）；输入 "function" → 计数 **0/7** 事件驱动正确；全清后键入残串 "functiontion" → 0/0（计数始终与查询一致，150ms 防抖生效，停止键入后才搜索） |
| 工程列表滑动（下拉/上拉各 1 次 + 快速滑动） | 78 帧 / janky 0%（50th=5ms、90th=6ms、99th=15ms） |
| 编辑器滚动（4 次连续 swipe） | 350 帧 / janky 1.14%（50th=5ms、90th=10ms、99th=69ms） |
| 运行 demo 工程（EngineActivity） | EngineActivity Displayed +145ms；无 ANR、无崩溃；demo 工程 md5 报错 `length=16; index=16` 为**已知工程自身 bug**（`docs/kb/androlua-lua/PITFALLS.md` #1，pro/lide 双端逐字一致，预期行为） |
| ANR / 崩溃复查 | `/data/anr/` 无新文件；logcat 无 `FATAL EXCEPTION` / `ANR in com.lioisme` |
| 内存（复测） | TOTAL PSS ≈ 124MB（会话加载 demo + 搜索后），无泄漏趋势 |
| R1 边界 | `luajava/androlua/loadLibrary` 在 `editor/src` 零命中 |

复测目标达成情况：
- ✅ 冷启动 TotalTime ≤ 修复前（847 < 960）；
- ✅ 搜索框连续键入只触发一次搜索（150ms 防抖，计数事件驱动正确）；
- ✅ 工程列表 / 编辑器滚动 0%~1.1% janky，无随时间劣化；
- ✅ 无新增 ANR / 崩溃（logcat 与 /data/anr 复查）；
- ⏳ 文件切换一次重组（P1-4）与列表刷新单次读盘（P2-3）已按代码路径确认（合并后原子发射 / 单 LaunchedEffect 去重），未做埋点级断言。

### 3.3 编辑器入口卡顿专项（Phase B，本轮）

**现象**：进入编辑器界面肉眼可见卡顿（实测 tap→编辑器可见 ≈2.6s，含 uiautomator dump 开销污染）。

**归因结论**（埋点实测，2026-08-01 23:47-23:52）：
- TextMate 初始化仅 **61-74ms**，进程启动即完成，编辑器入口 caller wait = **0ms**（排除预热等待）；
- 入口成本大头是 **AndroidView factory 每次进入重建**：`CodeEditor` 构造 ~93-104ms + `TextMateColorScheme.create` ~72ms + `setText` ~50ms ≈ **150ms/次主线程**，二次进入 colorScheme 由缓存降至 0ms 后仍 ~130ms；
- 组合级 `rememberTextMateInitializer` 每次进入都从 false 开始 → 多一帧 spinner + 一次全量重组（~38ms）。

**修复**：
1. `EditorViewPool`（`LuaEditor.kt`）：进程级单实例 `CodeEditor` + 单次事件订阅 + 单次 `LuaLanguage` 装配；事件回调经 `attachCallbacks` 按次注入；进入只做「桥接回调 + setText(身份相等跳过) + 配置 diff」。实测二次进入 factory **acquire=0ms setText=1ms total=1ms**。
2. `TextMateColorSchemeCache`（进程级单例，实现 `ThemeChangeListener` 自动跟随主题切换）。
3. `EditorInitGate`（进程级 StateFlow 快照）：初始化完成后再次进入首组合即得 true，消除 spinner 帧与双组合。

**结果**（framestats 单帧最大 gap，reset 后测量）：
- 修复前：首次进入 204.6/175.6ms 双巨帧，二次进入 ~118/93ms；
- 修复后：冷首次进入单帧 **86.8ms**（含不可避免的 CodeEditor 首建），热重入单帧 **100ms**（其中 ~78ms 为导航转场 + 屏幕组合框架开销，~20ms 为编辑器挂载/布局/首帧）；
- 打字→脏标记→自动保存链路、文件还原、进程存活均验证通过，无崩溃。

**测试插曲（记录在案，非产品缺陷）**：埋点阶段曾把 `ViewTreeObserver.removeOnDrawListener` 放进 `onDraw` 回调（框架明确禁止），主线程 FATAL → 进程退出路径 `DestroyJavaVM → WaitForOtherNonDaemonThreadsToExit` 等待非守护线程（sora `AsyncAnalyzer-N` LooperThread、tm4e pool-3 线程）→ 20s 输入超时 ANR。已删除该埋点；产品代码事件回调全部 try/catch 不产生主线程 FATAL。系统性启示：**sora/tm4e 分析线程均为非守护线程，任何主线程致命异常都会在退出路径挂起→ANR 对话框**，属库固有行为，产品侧以「异常不逃逸」防线规避。

---

## 4. 本次修复清单（Phase A）

1. **P1-1** `EditorScreen.kt`：删除 `LideDebug` 日志行。
2. **P1-2** `LuaEditor.kt`：`onRelease` 签名 `(content: String) -> Unit` → `() -> Unit`，删除 onDispose 的 `editor.text.toString()`。
3. **P1-3** `SearchBar.kt`：`LaunchedEffect(query, …)` 内增加 150ms 防抖（`delay` + 取消语义由 LaunchedEffect 重启天然提供），键入停止后才发起搜索（sora 匹配本身在后台，结果事件驱动计数不变）。
4. **P1-4** `EditorSession.kt` + `EditorScreen.kt`：`_selectedFile` / `_selectedContent` 合并为单一 `Selection(file, content)` StateFlow，文件切换原子发射一次。
5. **P2-1** `ProjectListScreen.kt`：`itemsIndexed(projects, key = { it.path })`。
6. **P2-2** `ProjectListScreen.kt`：`if (isRefreshing) LaunchedEffect(Unit)` → `LaunchedEffect(isRefreshing)`。
7. **P2-3** `ProjectListScreen.kt`：三处加载入口合并为 `LaunchedEffect(Unit, refreshKey, pendingImportPaths.size) { loadProjects() }`（首次进入 / 属性保存后 / 导入后均触发，且天然去重）。
8. **P2-4** `EditorScreen.kt`：`hostDeclared` 权限集合移入 `remember { … }`。
9. **P2-5** `EditorScreen.kt`：`tabs.map { it.tab }` 用 `remember(tabs)` 缓存。

## 5. 本次修复清单（Phase B · 编辑器入口卡顿）

1. **视图池** `LuaEditor.kt`：新增 `EditorViewPool`（进程级单实例 `CodeEditor` + 单次 `LuaLanguage` 装配 + 单次 `ContentChangeEvent` 订阅，回调经 `attachCallbacks` 按次注入桥接宿主）；`AndroidView` factory 不再创建新视图，仅同步内容（身份相等跳过 setText）与桥接回调；组合销毁（`onDispose`）只做 `editorRef=null` + 兜底保存，不销毁视图。
2. **主题方案缓存** `LuaEditor.kt`：`TextMateColorSchemeCache` 进程级单例，`ThemeChangeListener` 自动跟随 `ThemeRegistry` 主题切换。
3. **初始化门控** `LuaEditor.kt` + `TextMateInitializer.kt`：组合级 `remember { mutableStateOf(false) }` 改为进程级 `EditorInitGate`（StateFlow 快照 + `TextMateInitializer.isReady()`）；初始化完成后再次进入无 spinner 帧、无双组合。
4. **文档化测试事故**：`ViewTreeObserver.removeOnDrawListener` 在 `onDraw` 内调用导致主线程 FATAL + 退出路径挂起 ANR（详见 §3.3），埋点已全部移除；产品代码无此路径。

验收命令（R1 边界零命中）：

```
rg -n "luajava|androlua|loadLibrary" editor/src
```

## 6. 列表加载专项（Phase C · 全列表惰性/触发式/下拉刷新）

### 6.1 背景与根因

用户反馈：从设置 / 编辑器返回工程列表时可能出现列表重建，伴随卡顿与空态闪烁。

根因：Compose 导航（`NavHost`）会销毁非当前目的地的组合；`ProjectListScreen` 的
`projects` 状态为页面级 `remember`，每次返回都重置为空 → 先渲染空态，再经
`LaunchedEffect` 触发 `ProjectManager.listProjects()`（IO 全量扫盘 + 逐个读 init.lua）
后弹出列表。即「返回 = 空态闪烁 + 重建」。

### 6.2 全列表审计矩阵（修复后）

| 列表 | 屏幕 | 惰性加载 | 触发式加载 | 下拉刷新 | 处理 |
|---|---|---|---|---|---|
| 工程列表 | ProjectListScreen | ✅ LazyColumn+key | ✅ 缓存提升 NavHost | ✅ 已有 | **状态提升至 NavHost 层** |
| 内置示例 | ExamplesScreen | ✅ LazyColumn | ✅ 进入时 | ✅ 新增 | +PullToRefreshBox |
| 插件列表 | PluginScreen | ✅ LazyColumn | ✅ 进入时 | ✅ 新增 | +PullToRefreshBox |
| 宿主权限 | HostPermissionScreen | ✅ LazyColumn | ✅ 进入时 | ✅ 新增 | +PullToRefreshBox（覆盖系统设置返回后的陈旧状态） |
| 文件树 | FileBrowser（:editor） | ✅ LazyColumn | ✅ 目录切换时 | ✅ 新增 | 可选 onRefresh 参数，宿主对话框接线 |
| 设置页 | SettingsScreen | ✅ Grid/Column | 静态配置 UI | — | 非数据列表，不做（记录） |
| 工程属性 | ProjectInfoScreen | ✅ LazyColumn | 静态表单 | — | 非数据列表，不做（记录） |
| 工程权限池 | ProjectPermissionScreen | ✅ LazyColumn | 进入时 IO | — | 编辑表单，不做（记录） |
| 构建日志 | BuildApkScreen | ✅ LazyColumn | 流式追加 | — | 日志流，不做（记录） |
| Lua 手册 | LuaManualScreen | WebView | — | — | 非列表，不做（记录） |

### 6.3 修复清单

1. **工程列表缓存提升**（核心）`LideNavHost.kt` + `ProjectListScreen.kt`：
   `projects` 状态与 `refreshProjects`（suspend，IO 读盘）提升至 NavHost 层，
   导航往返不销毁；页面改为纯消费方，保留唯一触发点
   `LaunchedEffect(Unit, refreshKey, pendingImportPaths.size) { refresh() }`，
   下拉刷新 / 创建 / 删除 / 导入完成共用同一 `refresh`。
   返回列表时先渲染缓存，后台触发式刷新，空态闪烁与重复全量扫描消除。
2. **示例页** `ExamplesScreen.kt`：`PullToRefreshBox` 包裹列表，下拉重查已安装状态（isDirectory 检查，同步廉价）。
3. **插件页** `PluginScreen.kt`：`PullToRefreshBox` 包裹列表与空态；`refresh()` 内 try/finally 驱动 `isRefreshing`。
4. **宿主权限页** `PermissionScreen.kt`：`PullToRefreshBox` 包裹列表，下拉重建 `buildHostPermissionList`（重查授予状态）。
5. **文件树** `FileBrowser.kt`（:editor，纯组件）：新增可选 `isRefreshing` / `onRefresh` 参数，非空时启用 `PullToRefreshBox`，缺省保持纯展示；`EditorScreen.kt` 文件对话框接线：抽取 `loadEntries(dir)`，`LaunchedEffect(currentDir)` 与下拉共用，下拉重扫当前目录。
6. **R1 边界**：`:editor` 的 FileBrowser 改动零宿主依赖（刷新回调由宿主注入），`rg` 验证零命中。

### 6.4 设备验证（x64 模拟器，`assembleX64Debug` 新装）

- **设置往返**（列表 → 设置 → 返回）：83 帧，2 帧超 16.7ms（18.7 / 20.9ms，转场动画帧），
  无重建帧；修复前返回帧含空态渲染 + IO 全量重扫。
- **编辑器往返**（列表 → demo → 返回）：72 帧，0 卡顿，max 3.7ms。
- **下拉刷新**：工程列表 / 示例 / 插件 / 宿主权限 / 文件树对话框五处逐一滑动触发，
  `AndroidRuntime` 全空（零崩溃）；插件页空态下拉、文件树对话框（dkjson.lua/init.lua/main.lua）下拉均正常。
- 返回列表后 uiautomator 断言 6 个工程行完整渲染（demo/kleinbottle/DemoProject/TestJson/hello/listview）。
