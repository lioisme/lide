# delua 编辑器审计报告（v3）：编辑器-宿主边界、闪退根因与关联性说明

> 审计对象：`app/src/main/kotlin/com/lioisme/delua/ui/editor/*`、`ui/screen/EditorScreen.kt`、`DeluaApp.kt`、`:androlua` 模块
> 对照源码：`docs/sora-editor-main/`（editor / language-textmate，API 已与 Maven AAR `io.github.rosemoe:editor:0.24.6` 比对）
> 审计日期：2026-07-31
> 本文回答的核心问题：**"编辑器是纯粹的 editor，androlua 属于宿主管辖——为什么分析结论会指向 luajava 这类原生库？"**

---

## 0. TL;DR

1. **你说得对，这是架构定论：** 编辑器与宿主是两层，边界不可逾越。luajava / `libluajava.so` 属于宿主（运行时引擎进程），编辑器进程内不应存在任何对它的引用。
2. **上一轮分析指向 luajava，是因为上一版 delua 代码本身违规了。** 闪退的直接触发者是 `LuaDiagnostics.kt`——它从宿主模块导入了 `com.luajava.LuaStateFactory`（JNI 原生 Lua）到编辑器打字热路径。**这不是"编辑器需要 luajava"，而是"编辑器代码非法借用了宿主的原生引擎"。** 分析结论是"违规代码是崩溃原因"，不是"编辑器应该修 luajava"。
3. **根因 ≠ 机制。** 根因是边界违规（架构层）；机制是违规用法触发宿主原生库的缺陷（技术层）。修复 = 恢复边界（移除依赖），而非修补 luajava。
4. **边界已恢复（当前代码已修复）。** `ui/editor/` 目录 7 个文件对 `com.androlua.*` / `com.luajava.*` 的运行时引用为 **0**；`LuaDiagnostics` 已是纯 Kotlin 单遍词法校验器；TextMate 初始化已预热到进程启动。构建通过。
5. **纯编辑器路径的残余风险已复核**：诊断索引过期、分析线程异常、预热阻塞等均无崩溃风险（见 §5，附源码证据）。

---

## 1. 架构原则：编辑器与宿主的责任边界

### 1.1 分层模型

```
┌─────────────────────────────────────────────────────────┐
│ 宿主层（Host）                                            │
│ · Lua 运行时（EngineBridge 独立进程，libluajava.so）      │
│ · 项目文件 / 权限 / 模块与库管理 / 执行入口（LuaActivity） │
│ · com.androlua.*、com.luajava.* 唯一合法的持有者          │
├─────────────────────────────────────────────────────────┤
│ 编辑器层（Editor）—— 本报告审计范围                        │
│ · 文本展示/编辑、语法高亮（TextMate）、补全、搜索、撤销    │
│ · 行级诊断（纯文本分析，零原生）                           │
│ · 唯一允许的依赖：sora-editor + Android/Compose 基础      │
└─────────────────────────────────────────────────────────┘
```

### 1.2 边界规则（强制）

| 规则 | 内容 | 违反后果 |
|---|---|---|
| R1 | 编辑器代码**禁止** import `com.androlua.*` / `com.luajava.*`，禁止触碰 `libluajava.so` | 原生崩溃（见 §4） |
| R2 | 编辑器**禁止**把宿主能力拉进打字/渲染热路径（语法编译校验、执行等） | 卡顿、崩溃 |
| R3 | 编辑器与宿主的交互只通过**纯数据**（文本、文件路径、结果），且只发生在低频路径（打开/保存/运行） | 架构腐化 |
| R4 | 编辑器进程内的一切分析必须是**纯 Kotlin/Java**（无 JNI） | 不可捕获的原生崩溃面 |

### 1.3 职责划分表

| 功能 | 归属 | 依据 |
|---|---|---|
| 语法高亮（TextMate 文法） | 编辑器 | sora `language-textmate`，纯 Java（joni） |
| 关键词/片段补全 | 编辑器 | sora 补全 API + `LuaSnippetManager`（纯 Kotlin） |
| 行级语法诊断（括号/块/字符串闭合） | 编辑器 | 纯词法分析，零依赖（现实现） |
| **Lua 字节码编译校验（`luac` 级）** | **宿主** | 需要 Lua 编译器；运行时进程已有 |
| 脚本执行 | 宿主 | `EngineBridge` 独立进程 |
| 项目文件读写/权限 | 宿主 | `ProjectManager`（宿主服务层） |
| 模块/库依赖解析 | 宿主 | `DependencyManager` |

---

## 2. 直接回答：为什么上一轮分析指向 luajava

### 2.1 结论先行

> 分析指向 luajava **不是**因为编辑器依赖它，而是因为**上一版 delua 的编辑器代码违规地引用了它**——`LuaDiagnostics.kt` 把宿主模块的 `com.luajava.LuaStateFactory` 拉进了编辑器打字路径。闪退是这条违规的直接后果。修复不是"修 luajava"，而是"把 luajava 从编辑器里移除"（已完成）。

### 2.2 当时的推导链（证据 → 结论）

| 步骤 | 事实 | 结论 |
|---|---|---|
| 1 | 闪退发生在"输入后几秒内"，进程直接退出，**无 Java 异常界面**（原生崩溃特征） | 崩溃来自原生层，Java try/catch 全部无效 |
| 2 | 编辑器路径上（当时）**唯一**的原生代码 = `LuaDiagnostics` 调用的 `libluajava.so`（sora 本身纯 Java：tm4e 的 Oniguruma 是 joni，`TextMateAnalyzer.java:35-38` import 全部为 `org.eclipse.tm4e.*`） | 原生崩溃只可能来自 LuaDiagnostics |
| 3 | 崩溃与"诊断功能引入"同时出现；输入停止 → 500ms 防抖 → 创建/关闭原生 LuaState → GC 双关 | 时序完全吻合（500ms 防抖 + GC 周期） |
| 4 | `LuaState.close()` 无防重入 + `finalize()` 二次 `_close(0)`；`newLuaState()` 在 try 之外（`LuaDiagnostics.kt:14`） | 存在确定性原生崩溃向量（§4） |

### 2.3 机制与根因的区分（避免误读）

```
根因（架构层）  边界违规：编辑器代码导入了宿主原生引擎（LuaDiagnostics → com.luajava）
    │
    ▼
违规用法        瞬态 LuaState × 后台线程 × close+GC 双关 × 非 UTF-8 错误消息回传
    │
    ▼
机制（技术层）  lua_close(NULL) SIGSEGV / NewStringUTF SIGABRT / 未捕获 Error
    │
    ▼
现象            输入后数秒内进程闪退
```

- 宿主（EngineBridge 进程）用同一引擎从未崩溃，因为宿主的用法是**长生命周期 state、单线程、常驻引用**——从不触发 finalize 双关；
- 违规代码把引擎用在了**宿主从未使用过的方式**上（短生命周期、多线程、显式 close），于是宿主能容忍的缺陷在编辑器里变成必崩路径；
- 因此：**"与 luajava 相关"不等于"luajava 有罪"，而是"越界代码以错误方式使用它"**。这也正是边界规则 R1/R4 存在的意义。

---

## 3. 边界违规点审计（历史与现状）

### 3.1 历史违规点（上一轮版本，已修复）

| 文件（旧版） | 违规引用 | 违规点 |
|---|---|---|
| `ui/editor/LuaDiagnostics.kt` | `import com.luajava.LuaStateFactory`（旧版） | 编辑器层依赖宿主原生引擎，且在打字热路径运行 |
| `ui/editor/LuaEditor.kt`（旧版） | 无直接引用，但调用了上述诊断 | 通过调用链把原生代码引入主进程 |

### 3.2 现状：import 审计表（当前代码）

对 `app/src/main/kotlin/com/lioisme/delua/ui/editor/` 全量 grep `luajava` / `androlua`：

| 文件 | 结果 |
|---|---|
| `LuaEditor.kt` | ✅ 0 引用（仅 sora + compose + kotlinx） |
| `LuaLanguage.kt` | ✅ 0 引用（仅 sora） |
| `LuaSnippetManager.kt` | ✅ 0 引用（仅 sora） |
| `LuaDiagnostics.kt` | ✅ 0 运行时引用（注释声明"不触碰 libluajava.so"；实现为纯 Kotlin 词法校验器，`docs/EDITOR_CRASH_ANALYSIS_REPORT.md` §5.1 之代码已落地） |
| `TextMateInitializer.kt` | ✅ 0 引用（纯 sora） |
| `EditorToolbar.kt` / `SearchBar.kt` | ✅ 0 引用 |

全 app 层面，`com.androlua` 仅出现在 **3 个宿主层文件**：`DeluaApp.kt`（宿主基类 `LuaApplication`）、`project/ProjectManager.kt`（宿主服务层）、`ui/screen/PluginScreen.kt`（宿主功能）。`com.luajava` 运行时引用为 **0**。

**结论：边界已恢复。** 编辑器层现在是纯 sora 集成。

---

## 4. 闪退机制复盘（作为"违规必崩"的技术依据）

> 本节仅作技术存档，说明为什么 R1/R4 是硬规则。当前代码已不包含这些路径。

### 4.1 崩溃向量（均不可被 Java 捕获）

1. **LuaState 双重 native close → SIGSEGV**：`LuaState.close()`（`LuaState.java:133-137`）无防重入，`finalize()`（:140-148）再次 `close()` 时以 `luaState == 0` 调用原生 `_close(0)` = `lua_close(NULL)`。标准 luajava 原生实现无 NULL 防护。
2. **后台线程运行原生引擎 + 崩溃点不在 try 内**：`LuaDiagnostics` 旧版 `newLuaState()` 位于 try 之外（`LuaDiagnostics.kt:14` 旧版）；`System.loadLibrary("luajava")` 在 `LuaState` 静态块（`LuaState.java:105-107`）。任一失败直接抛到 `Dispatchers.Default` → 进程崩溃。
3. **错误消息回传触发 JNI 非法编码 → SIGABRT**：`LloadBuffer` 失败时 Lua 把出错源行片段拼进错误消息，`toString(-1)` 经 `NewStringUTF` 回传；源文件含非 UTF-8 字节（GBK 中文注释）时 ART 直接 abort。

### 4.2 时序吻合

```
输入 → ContentChangeEvent → diagVersion++ → 500ms 防抖
     → LuaDiagnostics.checkSyntax（Dispatchers.Default）
     → 原生 LuaState 创建/编译/关闭 → GC → finalize 双关
```

"使用（输入）后几秒内闪退" = 500ms 防抖 + GC 周期 + 原生崩溃，三者叠加。

### 4.3 已排除的候选（避免误诊）

| 候选 | 结论 | 证据 |
|---|---|---|
| 自动保存协程 | 排除 | `ProjectManager.saveProjectScript` 全函数 try/catch（`ProjectManager.kt:81-91`） |
| sora 分析器线程 | 排除 | `AsyncIncrementalAnalyzeManager.java:519-627` 捕获 `Exception` |
| 搜索事件回调 | 排除 | 回调仅写 `MutableStateFlow`；sora `EventManager.java:226-261` 读锁保护 |
| 主题切换 | 排除 | `ThemeModel.java:75-86` 一次性解析缓存；`EditorColorScheme` 用 SparseIntArray（AAR 反编译确认） |
| Compose 状态写入时机 | 排除 | `diagVersion++` 位于主线程事件分发，非组合期快照冲突 |

---

## 5. 纯编辑器路径的残余风险复核（当前代码）

| # | 风险点 | 复核结论 | 证据 |
|---|---|---|---|
| 1 | 诊断结果与当前文本不同步（checkSyntax 期间用户继续输入 → region 索引过期） | **无崩溃**。sora `DiagnosticsContainer.shiftOnInsert/shiftOnDelete`（:104-157）按编辑增量修正索引并自动清除空区间；渲染端 `queryInRegion`（:96-102）只做区间比较，无越界访问；`setDiagnostics` 的 attach 仅存 WeakReference（`CodeEditor.java:4285-4296`） | 已核 `DiagnosticsContainer.java` 全源码 |
| 2 | 分析器线程抛 `Error`（如 joni 极端输入 StackOverflowError/OOM） | 风险极低：`handleMessage` 捕获 `Exception` 但不捕获 `Error`；tm4e `tokenizeLine2` 自带 2s 超时（`TextMateAnalyzer.java:213`）防死循环。可选加固：`TextMateLanguage` 子类包一层 try-catch-Error | 见 §6.4 |
| 3 | `TextMateInitializer.initialize` 的 `CountDownLatch.await()` 阻塞调用线程 | **当前无风险**：`DeluaApp.preloadTextMate()` 在独立线程调用（`DeluaApp.kt:19-26`）；`rememberTextMateInitializer` 经 `withContext(Dispatchers.Default)`（`LuaEditor.kt:28-34`）。**禁令：任何线程不得在 UI 线程直接调用 `initialize`**（其 KDoc 已注明） | `TextMateInitializer.kt:31-34,51` |
| 4 | 初始化失败（如资源缺失）导致 `findGrammar` 返回 null | 不崩进程：`LuaLanguage` 构造会得到 null grammar，分析线程异常被 `handleMessage` 吞掉，编辑器退化为无高亮 | `TextMateLanguage.java:189-193` |
| 5 | `editorRef.value = this` 在 AndroidView factory（组合期）写 Compose 状态 | ✅ 已修复：改为 `update` 块内按实例恒等条件写入（`editor/.../LuaEditor.kt`），同一实例只发布一次 | 已落地 |
| 6 | 自动保存 `editor.text.toString()` 主线程全量拷贝（`EditorScreen.kt`） | ✅ 已优化：`Content` 内部持有 `ReadWriteLock`（javap 反编译 `Content.class`：`appendToStringBuilder` 入口 `lock(false)` 取读锁，全行迭代在锁内），快照可安全移入 `Dispatchers.IO`；同时新增 `onRelease` 兜底保存，修复"child-first dispose 顺序导致 editorRef 先清空、返回时最后改动丢失"的隐患 | 已落地 |

**结论：当前纯编辑器路径无崩溃级残余风险。** 剩余项均为 P2 级优化或可选加固。

---

## 6. 边界保障体系（防回归）

### 6.1 边界规则（✅ 已写入 AGENTS.md，规则 R1–R4）

1. `:editor`（包 `com.lioisme.delua.editor`）禁止出现 `com.androlua`、`com.luajava`、`System.loadLibrary`、jniLibs；其 `build.gradle.kts` 禁止依赖 `:androlua`；
2. 编辑器进程内的所有分析（诊断、补全、高亮）必须是纯 Kotlin/Java，禁止触碰 `libluajava.so`；
3. 任何"需要 Lua 编译器/执行器"的功能（如 `luac` 级校验、代码格式化、跳转定义）一律归宿主或独立模块，通过进程边界（`EngineBridge`）或纯数据接口交互；
4. `:editor` 不得读取 `:app` 的任何状态或类型；宿主依赖一律经参数 / 接口注入（`LuaEditor` 的 `isDarkTheme`/`config` 参数、`onRelease` 回调即此模式的实例）；
5. 宿主改动（`LuaApplication`、`libluajava.so` 等）不得影响编辑器可用性——编辑器不 import 它们，崩溃面即被隔离。

### 6.2 编辑器独立 Gradle 模块（✅ 已实施，编译期强制隔离）

已抽取 `:editor` 模块（`editor/`，namespace `com.lioisme.delua.editor`）：

```
:editor        （依赖 sora-editor、language-textmate、compose —— 编译期无法引用 :androlua）
:app           宿主组装（依赖 :editor + :androlua；EditorScreen 只做数据搬运）
```

- `settings.gradle.kts` 已 `include(":editor")`；`:app` 声明 `implementation(project(":editor"))`；
- `editor/build.gradle.kts` 只依赖 sora-editor / language-textmate / compose，**不声明 `project(":androlua")`**；
- 迁移文件：`LuaEditor` / `LuaLanguage` / `LuaSnippetManager` / `LuaDiagnostics` / `TextMateInitializer` / `EditorToolbar` / `SearchBar` / `EditorConfig`；
- 字符串资源（搜索、工具栏、cd_*）随迁至 `editor/src/main/res/values/strings.xml`（宿主仍在用的 `cd_back` 等保留在 `:app`，资源命名空间独立不冲突）；
- 解耦：`SettingsManager` 改用模块 `EditorConfig` 类型；明暗主题改为 `isDarkTheme: Boolean` 参数注入。

"编辑器是纯的"从此是**编译期保证**：`rg -n "luajava|androlua|loadLibrary" editor/src` 零命中。

### 6.3 诊断功能的归属裁决（回应"宿主管辖"的边界）

| 诊断层级 | 归属 | 现状/建议 |
|---|---|---|
| 行级词法诊断（括号、块、字符串闭合） | **编辑器**（纯 Kotlin） | 已落地（`LuaDiagnostics.kt` 纯词法校验器，O(n)，无原生） |
| 编译级校验（真实 `luac` 语义） | **宿主** | 运行时进程（EngineBridge）已有 Lua 编译器；编辑器如需，应通过"保存后异步回传诊断"的宿主服务实现，**绝不在编辑器进程内加载 Lua 引擎** |

### 6.4 加固（✅ 已落地）

- `LuaLanguage.requireAutoComplete` 整体 `try/catch(Throwable)` 降级（`LuaLanguage.kt`）——分析线程异常从进程级降级为该次补全缺失；
- `LuaEditor` 的 `ContentChangeEvent` 回调、`diagnostics` 赋值同样 `try/catch(Throwable)`（`LuaEditor.kt`）；
- `EditorScreen` 搜索事件回调 `try/catch(Throwable)`，异常时清空匹配计数（`EditorScreen.kt`）；
- 大文件（>200KB）：关闭诊断 / 块高亮 / 折行 / 括号高亮（`LuaEditor.kt`）。

---

## 7. 修复进度与验收标准

### 7.1 进度

| 项 | 状态 |
|---|---|
| P0：移除编辑器对 luajava 的依赖（纯 Kotlin 校验器） | ✅ 已落地（`LuaDiagnostics.kt` 重写，`ui/editor` 零宿主引用） |
| P1：TextMate 初始化预热到进程启动 | ✅ 已落地（`DeluaApp.kt:16,19-26` + `TextMateInitializer.kt`，CountDownLatch 保证先到者阻塞、后到者等待） |
| P1：factory 顺序对齐官方 demo（scheme→language→setText） | ✅ 已落地（`editor/.../LuaEditor.kt` factory） |
| P1：`collectIdentifiers=false` | ✅ 已落地（`editor/.../LuaEditor.kt`） |
| P1：`initReady` 期间显示加载指示 | ✅ 已落地（`editor/.../LuaEditor.kt`） |
| P2：`editorRef` 写入移出组合期 factory | ✅ 已落地（`update` 块恒等条件写入） |
| P2：自动保存快照 / 写盘 IO 化 | ✅ 已落地（`EditorScreen.kt` saveNow / 自动保存；`Content.toString` 读锁线程安全） |
| P2：`onRelease` 兜底保存（修复 dispose 顺序丢改动） | ✅ 已落地（`editor/.../LuaEditor.kt` + `EditorScreen.kt`） |
| 架构：`:editor` 独立 Gradle 模块 | ✅ 已落地（编译期边界，`editor/build.gradle.kts` 无 `:androlua`） |
| AGENTS.md 边界规则（R1–R4） | ✅ 已写入 |
| 加固：补全 / 诊断 / 事件回调 `try-catch(Throwable)` | ✅ 已落地（`LuaLanguage.kt`、`LuaEditor.kt`、`EditorScreen.kt`） |
| 编译 | ✅ `:app:compileArm64DebugKotlin` BUILD SUCCESSFUL |
| 完整构建 | ✅ `:app:assembleArm64Debug` BUILD SUCCESSFUL |

### 7.2 验收标准

1. **闪退**：进入编辑器 → 输入/停止输入循环 10 次，每次停留 10s，无闪退；logcat 无 SIGSEGV/SIGABRT；`luaState` finalize 日志不再出现；
2. **进入体验**：首次冷启动后，再进编辑器首帧即出编辑器，无白屏/加载圈；
3. **打字**：`AsyncAnalyzer-N` 线程唯一且不反复重建；`dumpsys gfxinfo` 无持续 jank；
4. **诊断**：语法错误行红色波浪线 500ms 内出现，修改后自动消失；
5. **边界**：`editor/src/` 全量 grep 无 `androlua|luajava|loadLibrary` 引用（除设计注释）；`:editor` 模块无 jniLibs、不依赖 `:androlua`。

---

## 8. 附录：证据索引

| 结论 | 证据位置 |
|---|---|
| sora 全链路纯 Java（无原生） | `TextMateAnalyzer.java:35-38`（import 均为 tm4e/joni） |
| 旧版违规点 | 旧版 `LuaDiagnostics.kt:3,14`（`import com.luajava.LuaStateFactory`；`newLuaState()` 在 try 外） |
| 宿主引擎缺陷（双关/无防重入） | `LuaState.java:133-148`（close + finalize）、`LuaStateFactory.java:101-104` |
| 现状零引用 | `editor/src/` grep `luajava|androlua|loadLibrary` → 仅 1 条设计注释；`app/` 无 `com.lioisme.delua.ui.editor` 残留 |
| `Content.toString` 线程安全（ReadWriteLock） | javap 反编译 `Content.class`（editor-0.24.6）：`appendToStringBuilder` 入口 `lock(false)` 取读锁、全行迭代在锁内；`length()` O(1)（缓存 `textLength`） |
| `:editor` 编译期隔离 | `editor/build.gradle.kts`（无 `:androlua`）、`settings.gradle.kts`（include）、`app/build.gradle.kts`（`project(":editor")`） |
| 边界规则落地 | `AGENTS.md`（R1–R4、构建命令、编辑器约定） |
| 诊断索引过期无崩溃 | `DiagnosticsContainer.java:96-157`（shift/query 均安全） |
| 分析线程异常处理 | `AsyncIncrementalAnalyzeManager.java:519-627`（catch Exception） |
| tm4e 超时防护 | `TextMateAnalyzer.java:213`（`Duration.ofSeconds(2)`） |
| 预热线程不阻塞主线程 | `DeluaApp.kt:19-26`、`TextMateInitializer.kt:35-52` |
| factory 顺序对齐 demo | `LuaEditor.kt:76-91,134`、`docs/sora-editor-main/app/.../MainActivity.kt:280-296` |
| 官方 demo 无任何原生引擎 | `MainActivity.kt:517-531`（诊断仅 DiagnosticsContainer） |
