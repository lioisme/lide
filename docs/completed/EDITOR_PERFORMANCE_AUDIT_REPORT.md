# lide 编辑器性能审计报告

> 审计对象：`app/src/main/kotlin/com/lioisme/lide/ui/editor/*`、`ui/screen/EditorScreen.kt`、`project/ProjectManager.kt`
> 审计基准：`docs/sora-editor-main/`（sora-editor 源码，已与依赖的 Maven `io.github.rosemoe:editor:0.24.6` / `language-textmate:0.24.6` AAR 反编译 API 逐一比对，确认一致）
> 审计日期：2026-07-31

---

## 1. 执行摘要

lide 的编辑器卡顿**不是 sora-editor 的瓶颈，而是 lide 集成方式的瓶颈**。lide 用 Compose `AndroidView` 把 `CodeEditor` 包成一个"受控组件"，每次按键都会走一遍"全文同步 + 全文重分析 + 全文重排版 + 多次全屏重绘"的灾难链路：

- **每敲一个字符**，lide 都会：
  1. 在 `ContentChangeEvent` 回调里把**整个文档** `toString()` 一次（O(n)，主线程）；
  2. 把全文放进 Compose 顶层 state，触发整屏重组；
  3. 在 `AndroidView.update` 里**无条件重建 `TextMateColorScheme`**，而 sora 的 `TextMateColorScheme.attachEditor()` 会调用 `editor.rerunAnalysis()` —— **打断正在进行的增量分析线程，复制全文，重新逐行 tokenize 整个文档**（O(n) 全文分词，后台线程 100% 占用，结果永远收敛不了，高亮每键被清空）；
  4. 无条件调用 `setTextSize()` → **整屏 `createLayout()` 重排版** + `invalidate()`；
  5. 无条件 `setTabWidth()` / `setNonPrintablePaintingFlags()` → 又各一次 `invalidate()`；
  6. 每次按键还伴随 O(n) 的 `text.toString() != content` 全文比较。

- 搜索框每敲一个字符，还会在主线程做一次全文 `toString()` + `split()`/`Regex` 计数，而 sora 自带的 `EditorSearcher` 本来就是后台搜索 + 事件推送的。

**结论**：sora-editor 0.24.6 自带完善的增量分析（`AsyncIncrementalAnalyzeManager` 的 `MSG_MOD` 路径只从改动行 tokenize 到状态收敛为止）、增量样式更新（`StyleUpdateRange`）、后台搜索（`PublishSearchResultEvent`）、渲染节点缓存（`RenderNodeHolder`）。lide 的代码把这套机制**全部绕过了**，把编辑器退化成了"每键全量重算"。

修复后应达到的基线（sora 的架构上限）：**单次按键 = 主线程 O(插入文本) 的文档修改 + 后台增量分析（通常 1~3 行）+ 受影响行的局部重绘**，与文件总大小基本无关。

---

## 2. 审计范围与方法

| 项 | 说明 |
|---|---|
| lide 端 | `LuaEditor.kt`、`EditorScreen.kt`、`LuaLanguage.kt`、`LuaSnippetManager.kt`、`SearchBar.kt`、`EditorToolbar.kt`、`ProjectManager.kt`、`SettingsManager.kt`、`app/build.gradle.kts` |
| sora-editor 端 | `CodeEditor.java`、`EditorRenderer.java`、`EditorSearcher.java`、`EditorStyleDelegate.java`、`EditorAutoCompletion.java`、`EditorInputConnection.java`、`AsyncIncrementalAnalyzeManager.java`、`Content.java`、`TextMateColorScheme.java`、`TextMateAnalyzer.java`、`TextMateLanguage.java`、`DirectAccessProps.java` |
| 方法 | 静态代码路径分析；0.24.6 AAR 反编译 API 与 docs 源码比对（签名一致，docs 源码可作为 0.24.6 行为依据）；参照 sora 官方 demo（`docs/sora-editor-main/app/src/main/.../MainActivity.kt`）的"标准用法"作为上限参照 |
| 关键结论的验证方式 | 见 §7 验证方案（thread 观察、gfxinfo、断点） |

---

## 3. 根因分析（按严重程度排序）

### 3.1 【P0-1】`AndroidView.update` 每键无条件重建 `TextMateColorScheme` → 每键全文重分析

**位置**：`app/.../ui/editor/LuaEditor.kt:156`

```kotlin
update = { editor ->
    editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())   // ← 每键执行
    ...
}
```

**sora 侧证据**（`TextMateColorScheme.java:352-361`）：

```java
@Override
public void attachEditor(@NonNull CodeEditor editor) {
    super.attachEditor(editor);
    try { themeRegistry.loadTheme(currentTheme); } catch (Exception e) { }
    setTheme(currentTheme);
    editor.rerunAnalysis();          // ← 关键：换配色 = 全文重分析
}
```

`rerunAnalysis()` → `AnalyzeManager.rerun()`（`AsyncIncrementalAnalyzeManager.java:145-164`）：

```java
public void rerun() {
    if (thread != null) {
        if (thread.isAlive()) { thread.interrupt(); thread.abort = true; }  // 杀掉增量分析线程
        thread = null;
    }
    var ref = getContentRef();
    if (ref != null) {
        final var text = ref.getReference().copyText(false, useShallowCopy); // UI 线程全文拷贝
        ...
        thread = new LooperThread(); ...
        sendNewStyles(null);           // ← 立刻清空全部样式（高亮消失）
        thread.start();                // 新线程从第 0 行全文 tokenize
    }
}
```

`LooperThread.initialize()`（`AsyncIncrementalAnalyzeManager.java:492-516`）逐行调用 `TextMateAnalyzer.tokenizeLine()` → `grammar.tokenizeLine2(line, ...)`（`TextMateAnalyzer.java:213`，每行 2 秒超时的 TextMate 文法正则分词，lide 未打包 `oniguruma-native`，走纯 Java Joni），随后 `computeBlocks()` 全文重算折叠块（`TextMateAnalyzer.java:175-205`）。

**后果**（文件越大越严重）：

1. 每次按键都打断上一轮分析线程（工作全部作废），`AsyncAnalyzer-N` 线程被反复创建，后台 CPU 100% 空转；
2. `sendNewStyles(null)` 使高亮每键消失一次（肉眼可见的"闪白"）；
3. `copyText` 全文拷贝发生在 **UI 线程**；
4. 快速打字时分析永远无法完成，增量分析（`insert`/`delete` 的 `MSG_MOD` 路径）被彻底废掉。

### 3.2 【P0-2】`ContentChangeEvent` 回调每键全文 `toString()` + 状态回写

**位置**：`LuaEditor.kt:144-150` + `EditorScreen.kt:42-44,58-60`

```kotlin
subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
    val newText = text.toString()      // ← 每次事件全量拷贝（O(n)）
    if (newText != currentContent) {
        currentOnContentChanged(newText)   // ← 全文写入 Compose state，触发整屏重组
    }
    currentOnUndoRedoStateChanged(canUndo(), canRedo())
}
```

**sora 侧证据**：`Content.toString()` → `toStringBuilder()`（`Content.java:961-976`）遍历全部行拼接，O(n) 分配；IME 组合输入（中文拼音）一次击键会产生**多个** `ACTION_INSERT`/`ACTION_DELETE` 事件（`CodeEditor.java:5290/5339` 逐事件 dispatch），即一次中文输入可能触发 2~6 次全文拷贝 + 状态更新 + 重组。

**后果**：每键主线程多次 O(n) 大对象分配（GC 压力）、整屏重组（`EditorScreen` 的 Scaffold/Toolbar/SearchBar 全部参与重组）。

### 3.3 【P0-3】`update` 块每键 `setTextSize()` → 整屏重排版

**位置**：`LuaEditor.kt:165`

```kotlin
editor.setTextSize(editorConfig.fontSize.toFloat())   // 配置没变也每键执行
```

**sora 侧证据**（`CodeEditor.java:1358-1363`）：

```java
public void setTextSizePx(@Px float size) {
    setTextSizePxDirect(size);
    requestLayoutIfNeeded();
    createLayout();     // ← 整屏重排版（WordwrapLayout 重建、全部可见行测量）
    invalidate();       // ← 全屏重绘
}
```

同块里的 `setTabWidth()`（`CodeEditor.java:2556-2565`）与 `setNonPrintablePaintingFlags()`（`CodeEditor.java:2212-2219`）同样每键无条件执行并 `invalidate()`。**一次按键至少 3 次全屏 invalidate**。

### 3.4 【P0-4】全文镜像状态 + `update` 里 O(n) 全文比较

**位置**：`LuaEditor.kt:157-159`

```kotlin
if (editor.text.toString() != content) {   // 每键 O(n) 比较（Kotlin String equals 逐字符）
    editor.setText(content)                // 不匹配时全量重置
}
```

`sora 侧 setText`（`CodeEditor.java:3982-4026`）：新建 `Content`（O(n)）、重置 cursor/selection/touchHandler、`renderer.onEditorFullTextUpdate()`、`analyzeManager.reset(...)`（全文重分析）、dispatch `ACTION_SET_NEW_TEXT`、`createLayout()`、**`inputMethodManager.restartInput()`（IME 重启）**、`requestLayout()`。

正常打字时 `update` 里比对结果为"相等"（不触发 setText），但**每次按键仍付出 O(n) 比较成本**；一旦出现偏差（如 IME 组合态与 state 不同步、`replaceAll` 后），会把编辑器连 IME 一起重置，属于隐藏的稳定性/性能炸弹。

### 3.5 【P1-1】搜索计数在主线程全文计算

**位置**：`EditorScreen.kt:70-95`

```kotlin
fun performSearch() {
    ...
    editor.getSearcher().search(searchQuery, options)
    val text = editor.text.toString()          // ← 主线程 O(n) 拷贝
    matchCount = ... text.split(searchQuery).size - 1 ...   // ← 主线程 O(n) 扫描/正则
}
LaunchedEffect(searchQuery, isCaseSensitive, isRegex) { performSearch() }  // 每敲一个字符触发
```

**sora 侧证据**：

- `EditorSearcher.search()`（`EditorSearcher.java:155-168`）→ `executeMatch()`（:173-180）→ `new SearchRunnable(editor.getText(), ...)`，其构造函数 `this.text = content.toStringBuilder()`（:568-570）——**全量拷贝发生在调用线程（主线程）**；
- 匹配本身在后台线程跑（`SearchRunnable.run()` :578-635），结果经 `PublishSearchResultEvent` 推送（:613-618）；
- 更糟：`EditorSearcher` 订阅了 `ContentChangeEvent`（:84-88），**搜索激活期间每敲一个键（编辑区）都会重新 `executeMatch()`，再次主线程全文拷贝**；
- sora 渲染器自带匹配高亮（`EditorRenderer.java:1273-1281`），lide 根本不需要自己画。

lide 用主线程正则 + `split` 重算了一遍 sora 已经在后台算好的结果。

### 3.6 【P1-2】主题切换在组合期间同步执行

**位置**：`LuaEditor.kt:87-96`

```kotlin
var lastTheme by remember { mutableStateOf<String?>(null) }
if (lastTheme != targetTheme) {
    lastTheme = targetTheme
    ThemeRegistry.getInstance().setTheme(targetTheme)   // ← 组合期副作用，同步通知所有 listener
}
```

`ThemeRegistry.setTheme`（`ThemeRegistry.java:133+`）同步回调 `TextMateAnalyzer.onChangeTheme` 与所有已注册 `TextMateColorScheme`；且 `TextMateColorScheme` 因 P0-1 每键重建/注册/注销 listener。应放进 `LaunchedEffect` 并只保留一个 scheme 实例。

### 3.7 【P1-3】文件读取在组合期间/主线程同步进行

**位置**：`EditorScreen.kt:38-44`（`remember { mutableStateOf(getProjectScript(...)) }`）+ `LaunchedEffect(currentFile)`（:58-60）

`ProjectManager.getProjectScript`（`ProjectManager.kt:72-79`）是 `File.readText()` 同步 IO；大文件（数百 KB）在打开编辑器的瞬间阻塞主线程。文件切换同样如此。

### 3.8 【P1-4】`props.stickyScroll = true` 每帧绘制开销

**位置**：`LuaEditor.kt:128-131`

**sora 侧证据**：`EditorRenderer.getStuckCodeBlocks()`（`EditorRenderer.java:1020-1088`）在**每次 draw** 中遍历可见区域代码块并 `Collections.sort(visibleBlocks)`（:1056），还影响 `ensurePositionVisible` 的滚动逻辑（`CodeEditor.java:2262`）。滚屏时每帧都多一次块扫描+排序。功能属于"锦上添花"，应默认关闭或做成设置项。

### 3.9 【P1-5】代码块高亮（`isHighlightCurrentBlock`）每光标移动/每样式更新线性扫描

**位置**：`LuaEditor.kt:124-126`（重复调用 `setHighlightCurrentBlock(true)`，第二个调用冗余）

**sora 侧证据**：`CodeEditor.setSelection`（:3550-3551）、`setStyles`（:4257-4259）、`updateStyles`（:4271-4273）都会调 `findCursorBlock()`（:1591-1639）——**线性遍历整个代码块列表**，而 `TextMateAnalyzer` 产出的 `suppressSwitch` 为 `Integer.MAX_VALUE`（`AsyncIncrementalAnalyzeManager.java:454`），意味着遍历不会提前终止。函数多的文件（每个 function 一个 block）每次光标移动/每次样式更新都全量扫一遍 block 列表。

### 3.10 【P1-6】自动补全每次按键的累积开销

**位置**：`LuaLanguage.kt:27-50`、`LuaSnippetManager.kt:64-107`

- `collectIdentifiers = true`（`LuaLanguage.kt:13`）：`TextMateAnalyzer` 从全文档收集标识符（`TextMateAnalyzer.java:265-272`），`TextMateLanguage.requireAutoComplete` 每次按键把**全文档标识符**全部发布（`TextMateLanguage.java:304-311`）；大文件标识符上千，每次按键遍历一遍；
- `LuaSnippetManager.toCompletionItems()` **每次按键**创建 11 个匿名 `SimpleCompletionItem`，且 `clearSnippetSyntax()` 每次对每个 snippet **重新编译两个 Regex**（`LuaSnippetManager.kt:64-68`）——regex 编译是每键都付出的真实成本；
- 补全窗口每次按键都通过 `EditorAutoCompletion.onContentChange`（`EditorAutoCompletion.java:145-177`）触发，按键吞吐高时雪上加霜。

### 3.11 【P2】其他

| # | 位置 | 问题 |
|---|---|---|
| P2-1 | `LuaEditor.kt:152` | `editorRef.value = this` 在 AndroidView factory（组合期间）写 Compose 状态，属于组合期副作用 |
| P2-2 | `LuaEditor.kt:191-193` | `updateDiagnostics()` 是空实现（`editor.diagnostics = null`），诊断功能完全没用上 sora 的能力 |
| P2-3 | `EditorScreen.kt:42` | `content` 无保存防抖，且"编辑器 ↔ state 全文同步"架构导致无法增量保存 |
| P2-4 | `LuaEditor.kt:134` | undo 栈 2500 上限，超大文件内存增长明显（可接受，但需知晓） |
| P2-5 | `app/build.gradle.kts:64` | release `isMinifyEnabled = false`，无 R8 压缩，不影响编辑器本身但拖慢启动与内存 |

---

## 4. 单次按键成本核算（以 2000 行 / ~60KB Lua 文件，中端手机为例）

| 步骤 | 执行位置 | 成本量级 |
|---|---|---|
| 1. 文档修改 + dispatch `ContentChangeEvent`（含 IME 组合，2~6 次事件） | `CodeEditor.java:5290/5339` | 正常（增量），但每事件触发步骤 2 |
| 2. lide 回调 `text.toString()` ×(2~6) | `Content.java:961-976` | O(n) ≈ 60KB×6 拷贝 + GC，约 1~3ms |
| 3. state 更新 → EditorScreen 整屏重组 | Compose | 约 0.5~2ms |
| 4. `update`：`TextMateColorScheme.create` + `attachEditor`→`rerunAnalysis` | `TextMateColorScheme.java:352-361`、`AsyncIncrementalAnalyzeManager.java:145-164` | UI 线程 `copyText` O(n) 约 1ms；后台全文 tokenize 约 0.4~4s（每键重启，永不收敛）；`sendNewStyles(null)` 清高亮 |
| 5. `update`：`text.toString() != content` 全文比较 | `LuaEditor.kt:157` | O(n) ≈ 0.1~0.5ms |
| 6. `update`：`setTextSize` → `createLayout` + `invalidate` | `CodeEditor.java:1358-1363` | 整屏重排版 1~5ms + 下一帧全屏重绘 3~10ms |
| 7. `update`：`setTabWidth` + `setNonPrintablePaintingFlags` | `CodeEditor.java:2556-2565, 2212-2219` | 各一次 invalidate |
| 8. 增量分析结果回传（如果还能跑到） | `EditorStyleDelegate.java:141-146` | 正常路径，被步骤 4 反复打断 |

**合计：主线程每键约 5~20ms+（超过 60fps 预算 16.6ms），后台分析线程 100% 占用**。文件越大，步骤 2/4/5/6 全部线性放大：5000 行文件每键可达数百 ms 级卡顿甚至 ANR。这正是"编辑器界面性能极差"的直接原因。

对比 sora 架构上限：步骤 1 后应只产生"改动行 → tokenize 至状态收敛（1~3 行）"的后台工作（`AsyncIncrementalAnalyzeManager.java:529-620` MSG_MOD 路径）+ 局部重绘（`StyleUpdateRange`/`RenderNodeHolder` 增量）。

---

## 5. 与 sora-editor 上限的差距（功能层面）

| 能力 | sora 0.24.6 提供 | lide 现状 |
|---|---|---|
| 增量语法分析（`AsyncIncrementalAnalyzeManager`） | 有（MSG_MOD 增量路径） | **被 P0-1 绕过，退化全量** |
| 增量样式更新/局部重绘 | 有（`StyleUpdateRange`、`RenderNodeHolder`、`SparseStylePatches`） | 被多轮全屏 invalidate 掩盖 |
| 后台搜索 + 结果事件（`PublishSearchResultEvent`） | 有 | 未使用，主线程重算 |
| 匹配高亮（`MATCHED_TEXT_BACKGROUND`） | 渲染器内置 | 未察觉（免费功能） |
| 诊断（`DiagnosticsContainer` + tooltip + quickfix 入口） | 有 | 空实现（`updateDiagnostics` 占位） |
| LSP 模块（`editor-lsp` 独立 artifact） | 有（补全/诊断/跳转/悬停） | 未依赖 |
| Inlay hints（`registerInlayHintRenderers`） | 有 | 未使用 |
| 原生 Snippet（`SnippetController` + 变量解析器） | 有 | 自研简易 snippet（无 tab 跳转/嵌套） |
| 代码块高亮/折叠块数据（`CodeBlock`） | 有 | 已开，但每次线性扫描（P1-5） |
| sticky scroll | 有 | 已开但每帧开销（P1-4） |
| 括号匹配/自动配对/换行缩进（`TextMateNewlineHandler`/`SymbolPairMatch`） | 有 | 已用 |
| 撤销/重做、非打印字符绘制、软换行、行号等 | 有 | 已用 |

---

## 6. 修复路线图

### 阶段 A：止血（1~2 天，纯 lide 侧改动，不改 sora）

1. **`update` 块改为"仅配置真正变化才执行"**（`LuaEditor.kt:155-166`）：
   - 删除无条件 `editor.colorScheme = TextMateColorScheme.create(...)`；factory 里创建**一次** `TextMateColorScheme` 实例，`update` 中仅在主题名变化时 `editor.colorScheme = 同一实例`（仅主题切换时付一次全量分析代价）；
   - `setTextSize`/`setTabWidth`/`setNonPrintablePaintingFlags` 仅在与上次应用值不同时调用；
   - 删除 `text.toString() != content` 比较与回写逻辑。
2. **删除"编辑器 ↔ Compose state 全文镜像"**：`ContentChangeEvent` 回调只置"脏"标记（`editor.text.length`/`canUndo` 等轻量信息）；文件切换用 `LaunchedEffect(currentFile)` 直接 `editor.setText(...)`；保存（返回/运行/防抖）时直接读 `editor.text.toString()`。`EditorScreen` 不再持有 `content` state，整屏不再每键重组。
3. **搜索计数改为事件驱动**：`subscribeAlways<PublishSearchResultEvent>` + `searcher.getMatchedPositionCount()`（并可用 `searcher.getCurrentMatchedPositionIndex()` 显示当前位置）；删除 `performSearch` 里的 `text.toString()` 与 `split`/`Regex` 计数；`LaunchedEffect(searchQuery,...)` 只调用 `searcher.search(...)`（其内部主线程 `toStringBuilder` 也可接受，每键一次；如需极致优化可自行在后台线程先 `editor.text.toString()` 再 `search`，或接受 sora 默认行为——匹配本身在后台）。
4. **主题切换移入 `LaunchedEffect`**（组合期副作用移除）。
5. **默认关闭 `props.stickyScroll`**（或移入设置项），保留代码即可。
6. **修复 `isHighlightCurrentBlock`**：去掉冗余二次调用；大文件可考虑关闭（或仅在小文件开启）。

### 阶段 B：架构完善（1 周内）

7. 文件打开/切换改 `Dispatchers.IO` 读取，进入编辑器前完成；
8. 自动保存防抖（500ms）+ IO 线程写盘，避免"返回时整文件同步写"；
9. 补全优化：大文件关闭 `collectIdentifiers`（或限制发布条目）；`LuaSnippetManager` 的 Regex 提升为 `companion object` 预编译常量，`toCompletionItems` 结果做缓存（内容未变时复用）；
10. 评估 `AsyncIncrementalAnalyzeManager.setUseShallowCopyByDefault(true)` 降低大文件初始分析内存；
11. 考虑把 `EditorScreen` 的 `files`/对话框等静态内容用 `remember`/`derivedStateOf` 隔离，避免与编辑状态耦合。

### 阶段 C：达到 sora-editor 上限（功能）

12. 启用诊断：后台 `luac -p`（或轻量 Lua 语法解析）→ `DiagnosticsContainer`（error/warning 波浪线 + 点击 tooltip + quickfix 入口）；
13. 引入 `io.github.rosemoe:editor-lsp`，接入补全/悬停/跳转定义（Lua LSP 如 `lua-language-server` 需桥接）；
14. 使用 sora 原生 `SnippetController` 替代自研 snippet（获得 tab 停靠、嵌套、变量解析）；
15. Inlay hints、minimap（实验性模块）按需接入；
16. 换行缩进/符号配对确认 `TextMateNewlineHandler` 生效（已随 `TextMateLanguage` 启用）。

### 验收标准（性能）

- 5000 行 / 150KB 文件：连续快速输入不掉帧（`adb shell dumpsys gfxinfo <pkg> framestats` 检查 jank）；
- 打字过程中 `AsyncAnalyzer-N` 线程**唯一且不反复重建**（`adb shell top -H` 观察），后台 CPU 不长期 100%；
- 打字过程中 `editor.getStyles()` 不为 null（高亮不消失）；
- 搜索框逐字输入无卡顿，计数结果与 sora 高亮一致。

---

## 7. 附录：关键证据代码索引

| 结论 | 证据位置 |
|---|---|
| attachEditor 触发全文重分析 | `language-textmate/.../TextMateColorScheme.java:352-361` |
| rerun 杀线程+全文拷贝+清空样式+重建线程 | `editor/.../AsyncIncrementalAnalyzeManager.java:145-164`、`initialize()` :492-516 |
| 增量路径（被绕过） | `AsyncIncrementalAnalyzeManager.java:529-620` |
| 每行 TextMate 文法分词 | `language-textmate/.../TextMateAnalyzer.java:209-262` |
| setText 全量重置（IME 重启） | `editor/.../CodeEditor.java:3982-4026` |
| setColorScheme → attachEditor | `CodeEditor.java:4178-4186` |
| setTextSizePx → createLayout+invalidate | `CodeEditor.java:1353-1363` |
| setTabWidth / setNonPrintablePaintingFlags 各自 invalidate | `CodeEditor.java:2556-2565`、`2212-2219` |
| Content.toString 全量拼接 | `editor/.../Content.java:959-976` |
| 搜索主线程全文拷贝 + 后台匹配 + 事件推送 | `editor/.../EditorSearcher.java:155-180`、`568-570`、`578-635` |
| 搜索期间每编辑事件重搜 | `EditorSearcher.java:84-88` |
| 匹配高亮内置绘制 | `editor/.../EditorRenderer.java:1273-1281` |
| sticky scroll 每 draw 计算 | `EditorRenderer.java:1020-1088`、`494-639` |
| findCursorBlock 线性扫描 | `CodeEditor.java:1591-1639`、`3550-3551`、`4257-4273` |
| 全文档标识符补全 | `language-textmate/.../TextMateLanguage.java:304-311`、`TextMateAnalyzer.java:265-272` |
| 官方 demo 的标准用法（事件驱动、一次性 setText、复用 scheme、防抖 50ms） | `app/.../MainActivity.kt:225-272`、`506-543`、`238-244` |
| lide 的 P0 问题代码 | `app/.../ui/editor/LuaEditor.kt:100-175`、`app/.../ui/screen/EditorScreen.kt:42-99,138-144` |
