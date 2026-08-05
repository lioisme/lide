# 任务单：lide 编辑器功能完善至 sora-editor 原生上限（F8）

> **状态**：🔵 实施中（2026-08-06 立项；P0 全部 + P1 功能项（G2/G3/G4/G6/G7/G8/G9/G1x）已完成，G5 折叠已闭环（仅渲染不交互）；P2 性能上限（R8+baseline profile/降级矩阵/基准脚本）已完成；P3 sora 更新管线（脚本/自动 PR/一致性校验/R1 grep 入 CI/台账）已完成；P4 文档已完成，设备功能验收矩阵 §8.3 待执行（模拟器测试推迟至后续））
> **关联**：`docs/planned/backlog.md` F8、`docs/kb/EDITOR.md`、AGENTS.md R1~R5
> **验收门禁**：§8（编译 / 单测 / 设备矩阵 / 性能阈值 / 红线复核）

---

## 1. 执行摘要

- **上限判定基准**：sora-editor 0.24.6 原生能力全量枚举（§4），含「本仓库不可用」清单（LSP、原生 oniguruma、多光标——原生不存在）。
- **lide 现状**：编辑器内核（视图池、增量诊断、搜索、Tab）已接近性能上限，功能面只消费了 sora 能力的一小部分（§5 差距表 12 项）。
- **实施分期**：P0 编辑器专属设置中心（21 项全集，通用设置页仅留入口）→ P1 功能完善（SnippetController / 诊断 tooltip / 多错误聚合 / 折叠验证 / 文本操作增强 / 搜索整词 / 标识符补全设置项 / 迷你地图）→ P2 性能上限（R8 + baseline profile / 降级矩阵扩展 / 基准脚本）→ P3 sora-editor CI 更新管线（脚本 + 每周自动 PR + 上游一致性校验 + R1 grep 入 CI）→ P4 文档与验收。
- **决策记录（2026-08-06 用户确认）**：
  - D1 设置中心 = 对标 squircle 全集 21 项（推荐项）。
  - D2 文档内标识符补全默认关，提供设置项（推荐项）。
  - D3 sora-editor CI 更新管线 = 每周定时 + 手动触发，全绿自动开 PR（推荐项）。
  - D4 迷你地图纳入设置项（推荐项）。

---

## 2. 报告元信息

| 项 | 内容 |
|---|---|
| 报告日期 | 2026-08-06 |
| 审计对象 | `:editor` 模块、`:app` 宿主接线、`third_party/sora-editor`（0.24.6 vendored，LGPL-2.1）、`reference/squircle-ce-master`（sora fork 0.24.3 基）、CI（`.github/workflows/build.yml`）、`docs/kb/` |
| 审计方法 | 源码静态审计（关键类逐行核对行号）+ 三方交叉对照（上游能力面 × squircle 落地 × lide 现状） |
| 核心红线 | AGENTS.md R1~R5、初始化顺序（colorScheme → setEditorLanguage → setText）、主线程契约、try/catch(Throwable) 降级、大文件降级（>200KB）、`docs/kb/` 唯一事实来源 |

---

## 3. 目标

1. **功能上限**：lide 编辑器功能与性能达到 sora-editor 原生上限；原则上仅实现 sora-editor 原生具备的功能，技术上难以实现 / 原生不存在的功能不做（§4.5 不做清单）。
2. **设置中心**：编辑器功能增量 → 编辑器专属设置界面；通用设置界面「编辑器」分区仅保留入口。
3. **参考基线**：上游 squircle-ce（`reference/squircle-ce-master/feature-editor`）为 UI/落地范本。
4. **集成形态**：sora-editor 以库被 lide 集成（源码 vendored 于 `third_party/sora-editor/`），保留 CI 流水线可更新特性；不魔改、不与其他代码耦合（白名单机制 + 上游一致性校验）。

---

## 4. sora-editor 原生能力清单（上限判定基准）

> 版本注意（0.24.6 API 更名，勿按旧版名检索）：无 `Styler`/`CompletionAnalyzer`/`InlayHintAnalyzer`/`InlayHintController`/`CodeBlockLineAnalyzer`/`TextAnalysisResult`；
> 对应为 `Styles`/`Language.requireAutoComplete`/`InlayHintsContainer`/`AsyncIncrementalAnalyzeManager.CodeBlockAnalyzeDelegate`/`LineTokenizeResult`。
> 无 `getCursorPosition()`（用 `getCursor().left()/right()`）。**多光标 0.24.6 全库 grep 零命中，原生不存在。**

### 4.1 文本内核（`<E>/text/`，`<E>` = `third_party/sora-editor/editor/src/main/java/io/github/rosemoe/sora`）

| 能力 | 关键类 | 说明 |
|---|---|---|
| 文本模型 | `Content`（行列表 + 写时复制行，ReadWriteLock） | 快照/子区间/后台读安全 |
| 编辑 API | insert/delete/replace/undo/redo/`beginBatchEdit` | 全部带监听器 + UndoManager + Indexer + Bidi 联动 |
| 撤销 | `UndoManager`（Parcelable，**磁盘序列化 `writeFile`/`readFile`**） | 跨会话保留撤销栈（squircle CacheManager 已用） |
| 查找工具 | `text/TextUtils` | indexOf/lastIndexOf/创建缩进/退格偏移 |
| 坐标↔文本 | `Indexer`/`CachedIndexer`（增量分段二分） | `getCharPosition` |
| emoji/bidi/分词 | `EmojiPropertyProvider`、`TextBidi`、`WordBreakerIcu`、`ICUUtils` | 选区/断词正确性 |

### 4.2 语言能力（`<E>/lang/`）

| 域 | 原生能力 |
|---|---|
| 分析 | `AsyncIncrementalAnalyzeManager`（异步增量、浅拷贝、分析中推送样式）、`SimpleAnalyzeManager`、`PlainTextAnalyzeManager` |
| 括号 | `BracketsProvider`/`OnlineBracketsMatcher`；符号对自动补全/删除/回车联动（`DirectAccessProps.symbolPairAutoCompletion`） |
| 补全 | `CompletionPublisher`（阈值合并）、`SimpleCompletionItem`、FuzzyScore 模糊匹配、**`IdentifierAutoComplete` 文档内标识符补全**、`CompletionItemKind` |
| snippet | **`SnippetController`（原生 tab-stop 导航）**、`CodeSnippetParser`（VS Code 语法 `${1:xxx}`/transform）、变量解析器（`$CLIPBOARD/$RANDOM/$TM_FILENAME` 等） |
| 诊断 | `DiagnosticsContainer`（区间查询 + 文本变化自动平移）、`Quickfix`、三种指示样式（NONE/UNDERLINE/WAVE）、**`EditorDiagnosticTooltipWindow` 原生气泡**（brief/detailed/quickfix） |
| 折叠 | `CodeBlock` + TextMate `IndentRange` 缩进折叠（含 offside 与折叠正则） |
| 格式化 | `AsyncFormatter`/`formatCodeAsync`（Lua 无现成 formatter 实现） |
| smartEnter | `NewlineHandler` 接口 + **`TextMateNewlineHandler`**（onEnter/indentRules/注释内缩进继承）、`QuickQuoteHandler` |
| styling | `Styles`/`Span`/`TextStyle`（19-bit 打包）、`LineStyles`（行背景/侧边图标）、`InlayHintsContainer`、`StylePatch` 增量补丁、`HighlightTextContainer`（搜索高亮） |

### 4.3 widget/ 视图层

- **文本操作**：insertText/commitText（3 重载）/deleteText/undo/redo/cut/copy/paste/`duplicateLine`/indent/unindent/`indentOrCommitTab`/selectAll/selectWord/`SelectionMovement`（词级/行级/页级）/setSelectionRegion。
- **视图**：行号（可 pin/可隐藏/对齐/悬停提示面板 `setDisplayLnPanel`）、软换行（antiWordBreaking + RTL）、`setLineSpacing`、字体全套（size/typeface/letterSpacing/ligature/`setFontFeatureSettings`）、非打印字符（8 种 flag）、双指缩放 `isScalable`、滚动条、光标动画（3 种 Animator）、放大镜 `Magnifier`。
- **搜索**：`EditorSearcher`（正则/整词/大小写、循环跳转、`replaceCurrentMatch`/`replaceAll`、保留大小写替换、正则反向引用）。
- **迷你地图（0.24.6 新增）**：`MinimapRenderer` + 字符块模式，`props.showMinimap` 开关（squircle 0.24.3 无此能力——lide 可超）。
- **DirectAccessProps**（约 90 项行为开关）：stickyScroll、`highlightMatchingDelimiters`、`boldMatchingDelimiters`、`enhancedHomeAndEnd`、`formatPastedText`、`singleDirectionFling`、`showBidiDirectionIndicator`、`hardwrapColumn` 等。
- **组件系统**：`EditorAutoCompletion`（补全窗，Layout/Adapter 可替换）、`EditorTextActionWindow`（选中文本条）、`EditorDiagnosticTooltipWindow`、`EditorContextMenuCreator`（鼠标右键菜单）。
- **事件**：EventManager 全套（ContentChange/SelectionChange/Scroll/KeyBinding/Click/Snippet/PublishSearchResult/PublishDiagnostics/InlayHintClick…）。
- **主题**：`EditorColorScheme` 84 个颜色槽 + 5 个 builtin 主题（Darcula/Eclipse/GitHub/NotepadXX/VS2019）。
- **硬件键盘**：方向/Home/End/PageUp/Ctrl+CAXVZYDW/J 原生处理；`KeyBindingEvent` 可拦截。

### 4.4 language-textmate

`TextMateLanguage`（工厂 `create(scope, collectIdentifiers)`、updateTheme/updateLanguage）、`TextMateColorScheme`（tmTheme → 色域）、`TextMateAnalyzer`（tokenizeLine 2s 超时、缩进折叠、标识符收集）、`TextMateNewlineHandler`、`TextMateSymbolPairMatch`（language-configuration.json 驱动）、tm4e 全引擎（JSON/Plist/YAML 语法解析）。正则走 **Joni 纯 Java**（`OnigNative` 桩恒 false，性能上限 = Joni）。

### 4.5 本仓库不可用 / 明确不做

| 项 | 原因 |
|---|---|
| LSP 客户端全部能力（语义补全/诊断/semantic tokens/hover/签名帮助/跳转定义） | `editor-lsp` 模块未收纳 |
| 原生 oniguruma 正则加速 | 桩类替代；R5 禁止 .so 入库 |
| language-java/kotlin/xml 等现成语言模块 | 未收纳 |
| **多光标编辑** | 0.24.6 原生不存在（grep 零命中） |
| 增量保存 | sora 无此 API（仅 change 事件）；宿主自研成本高收益低，不做 |
| Lua formatter | sora `AsyncFormatter` 接口存在但 Lua 无现成实现；语言规则自研超出「原生功能」范围，列入 backlog 候选 |
| TextMateNewlineHandler 深度定制 | language-configuration.json 已提供 Lua 缩进规则（squircle 亦未定制）；注释延续规则仅在需要时做 |

---

## 5. 差距分析（lide 现状 vs sora 上限 vs squircle）

### 5.1 已达成（无需动）

视图池复用（EditorViewPool）、增量诊断（500ms 防抖 + 行级增量）、搜索链路事件驱动（PublishSearchResultEvent）、Content IO 构造注入、守卫式配置同步（update 块 diff）、大文件降级、Tab 拖拽/关闭菜单、主题双套（darcula/quietlight）、R1 零命中、CI 五项门禁。

### 5.2 差距清单

| # | 能力 | lide 现状 | sora 原生 | squircle 参考 | 批次 |
|---|---|---|---|---|---|
| G1 | 编辑器设置中心 | 4 项散在通用设置页 | —（API 有，UI 自研） | `EditorSettings` 19 项 + CodeStyle 4 项 | **P0** |
| G2 | 原生 SnippetController | 手写文本插入，无 tab-stop | ✅ `SnippetController` | 未做 | P1 |
| G3 | 诊断 tooltip | 默认实例化未定制/未验证 | ✅ `EditorDiagnosticTooltipWindow` | 未做 | P1（backlog F7） |
| G4 | 诊断多错误聚合 | `LuaDiagnostics` 只报首个错误 | ✅ `DiagnosticsContainer` 多区域 | 未做 | P1 |
| G5 | 代码折叠 | TextMate 已算 blocks；**折叠交互在 0.24.6 不存在**（`FoldingRegion` 死代码，无手柄/触摸） | ⚠️ 仅块线渲染（CodeBlock），交互为 0.25+ | 未做 | P1（设备验证）✅ 降级：仅渲染不交互 |
| G6 | 文本操作增强 | 无 selectLine/duplicateLine/toggleCase/词级移动 | ✅ 原生 API | EditMenu 全套 | P1 |
| G7 | 标识符补全 | `collectIdentifiers=false`（性能取舍） | ✅ `IdentifierAutoComplete` | 开着 | P1（**D2：默认关+设置项**） |
| G8 | 迷你地图 | 无 | ✅ 0.24.6 原生 | 无（版本旧） | P1（**D4：纳入**） |
| G9 | 行为开关（stickyScroll/光标动画/行号面板/缩放/只读） | 未暴露设置 | ✅ props/API | 全开 | 随 P0 |
| G10 | R8 + baseline profile | 未开（backlog F6） | — | 自带两份 | P2 |
| G11 | sora-editor CI 自动更新 | 手动流程（README 文档） | — | 无 | P3 |
| G12 | R1 grep 入 CI | 仅本地验证命令 | — | — | P3 |

---

## 6. 实施方案

### P0 — 编辑器专属设置中心（G1，D1：21 项全集）

**交付物**：
1. `:editor` `EditorConfig.kt` 扩展为全量配置值对象（§7 映射表全部字段，带默认值）；R4 保持「宿主注入纯值」模式（`editor/src/main/kotlin/com/lioisme/lide/editor/EditorConfig.kt`）。
2. `:editor` `LuaLanguage.kt` 暴露语言级开关 setter：`newlineHandler.isEnabled` / `symbolPairs.setEnabled` / `useTab` / `setAutoCompleteEnabled` / `setCompleterKeywords`（`TextMateLanguage` 原生提供）。
3. `:editor` `LuaEditor.kt` 守卫式同步扩展（update 块 diff 后赋值，模式同 `LuaEditor.kt:295-331`）+ 大文件降级矩阵（§6.3）。
4. `:app` 新增 `EditorSettingsScreen.kt`（复用 SettingsScreen 的 Section/Item/Toggle/Stepper 组件模式；分组：通用/编辑/视图/输入/高级）；导航 `Routes.EDITOR_SETTINGS`（`LideNavHost.kt`）；`SettingsScreen.kt:158-204`「编辑器」分区收缩为单入口行「编辑器设置」。
5. `:app` `SettingsManager.kt` 扩展 `lide_editor` 命名空间键（StateFlow 镜像 + `.apply()` 异步落盘）。
6. 字符串资源 `values/strings.xml` + `values-zh/strings.xml` 成对。

**架构判定**：设置 UI 与持久化属宿主职责（R3），放 `:app`；`:editor` 只接收 `EditorConfig` 并应用（R4 满足）。映射范本 = squircle `compose/CodeEditor.kt:86-99` 的 setter 行。

### P1 — 功能完善（仅 sora 原生能力）

| 任务 | 实现要点（sora API） | 位置 | 性能预算 |
|---|---|---|---|
| G2 Snippet | `CodeSnippetParser.parse` + `SimpleSnippetCompletionItem` + `SnippetController.startSnippet/shiftToNextTabStop`；`LuaSnippetManager` 改原生 snippet 文本（10 片段保持）；tab 跳占位符由 sora 原生处理 | `:editor` | 仅进入 snippet 时生效，打字热路径零开销 |
| G3 诊断 tooltip | 定制 `DiagnosticTooltipLayout`（brief/detailed/quickfix）+ `PublishDiagnosticsEvent` 驱动；quickfix 执行 try/catch | `:editor` | 悬停时触发 |
| G4 多错误聚合 | `LuaDiagnostics.check` 收集全部错误（括号栈/关键字栈/字符串并行累计），按区域排序去重；单测扩例 | `:editor` | 增量扫描已限界 |
| G5 折叠验证 | 设备验证 `isHighlightCurrentBlock` + 折叠手柄点击（sora 原生触摸）；正常即「启用完成」 | `:editor` | 大文件自动关（现有降级） |
| G6 文本操作 | 工具栏/长按菜单挂 selectLine/deleteLine/duplicateLine/toggleCase + `SelectionMovement` 词级移动（参考 squircle `view/Extensions.kt:46-74` + sora `moveOrExtendSelection`） | `:editor` | 一次性命令 |
| G7 标识符补全 | 设置项控制 `LuaLanguage(collectIdentifiers)`；**D2：默认关**；大文件强制关；WP-012 静态表补全走 `:app` 不与其竞争 | `:editor` | 默认关时零成本 |
| G8 迷你地图 | `props.showMinimap` + `minimapConfig`（字符块模式）；**D4：默认关**；大文件强制关 | `:editor` | 小文件位图渲染可接受 |
| G9 行为开关 | stickyScroll/光标动画/行号面板/`isScalable`/`isEditable` 经设置中心暴露（§7） | `:editor` | 见 §6.2 |
| G1x 搜索整词 | SearchBar FilterChip 增加 `SearchOptions.TYPE_WHOLE_WORD` | `:editor` | 零 |

### P2 — 性能上限（功能与性能兼得）

1. **R8 收缩 + baseline/startup profile**（backlog F6）：release 开 R8（现有 `verifyReleaseLuaSurface` 门禁已防反射面破坏），补 baseline profile（启动 + 编辑器首帧 + 打字路径），对齐 squircle。
2. **大文件降级矩阵扩展**（§6.3）：minimap/stickyScroll/光标动画/折叠/行号面板随 >200KB 自动关闭。
3. **性能基准脚本** `scripts/perf-check.ps1`：gfxinfo（jank=0）、77KB 打开耗时、5000 行打字帧率、冷启动对比（与 squircle 同机 A/B）；macrobenchmark 模块（P2 可选，参考 squircle `benchmark/`）。
4. TextMate 分析预算声明：Joni 纯 Java 为上限（2s tokenize 超时内建），不引入 oniguruma（R5）。

### P3 — sora-editor 集成与 CI 更新管线（零魔改、零耦合；D3）

1. `scripts/update-sora-editor.ps1`：README 流程脚本化——下载指定 tag zip → 整体替换 `editor/src/main` 与 `language-textmate/src/main` → **保留白名单**（`OnigNative.java` 桩 + 两模块 `build.gradle.kts`）→ 同步 `libs.versions.toml` 依赖版本 → 本地编译验证。
2. `.github/workflows/update-sora-editor.yml`（scheduled 每周 + `workflow_dispatch`）：checkout → 查上游最新 release tag → 与当前版本比较 → 新版则跑脚本 → `:app:compileArm64DebugKotlin` + `:editor:testDebugUnitTest` + `:app:assembleArm64Debug` → 全绿自动开 PR（标题含版本号）；红则 PR 标记失败附构建日志。
3. **上游一致性校验 job**：下载上游同 tag zip，比对白名单外文件哈希（`git diff --no-index`），防手改漂移。
4. CI 补 R1 grep job（`rg -n "luajava|androlua|loadLibrary" editor/src` 零命中入流水线，消除门禁休眠风险）。
5. `third_party/sora-editor/README.md` 增加版本台账（版本/日期/PR 号/变更摘要）。

**约束确认**：不引入 git submodule（R5 仓库自洽 + 断网构建友好）；不上 Maven 坐标（双来源类遮蔽地雷，`app/build.gradle.kts` 注释已记录教训）。

### P4 — 文档与验收

- 本任务单逐条实施时标「（已完成）」+ 工作内容 + 日期 + 验收证据。
- `docs/kb/EDITOR.md` 更新：能力清单、设置↔API 映射表、性能预算与降级矩阵、sora 版本台账索引。
- 设备验收矩阵执行（§8.3）。
- 整份完成后 `git mv` 至 `docs/discard/` 并同步 `docs/kb/README.md` §2、`docs/TASKS.md`、`docs/discard/README.md` §3。

---

## 7. 设置项 ↔ sora API 映射全表（D1：21 项全集）

| 分组 | 设置项 | 默认 | sora API/属性 | 性能影响 | 大文件降级 |
|---|---|---|---|---|---|
| 通用 | 字号 | 14 | `setTextSize` | 一次性重排版 | — |
| 通用 | Tab 宽 | 4 | `tabWidth` | 低 | — |
| 通用 | Tab 用空格 | 开 | `LuaLanguage.useTab` | 低 | — |
| 编辑 | 自动缩进 | 开 | `LuaLanguage.newlineHandler.isEnabled` | 低 | — |
| 编辑 | 自动闭合括号 | 开 | `LuaLanguage.symbolPairs.setEnabled` | 低 | — |
| 编辑 | 补全 | 开 | `LuaLanguage.setAutoCompleteEnabled` | 中 | — |
| 编辑 | 文档标识符补全 | 关（D2） | `LuaLanguage(collectIdentifiers=true)` | 高（分析线程） | 强制关 |
| 编辑 | 行级词法诊断 | 开 | `LuaDiagnostics`（:editor 自研） | 后台防抖 | 强制关（现有） |
| 编辑 | 只读模式 | 关 | `isEditable` | 低 | — |
| 视图 | 自动换行 | 关 | `isWordwrap` | 布局成本 | 强制关（现有） |
| 视图 | 行号 | 开 | `isLineNumberEnabled` | 低 | — |
| 视图 | 当前行高亮 | 开 | `isHighlightCurrentLine` | 低 | — |
| 视图 | 括号匹配高亮 | 开 | `isHighlightBracketPair` | 中 | 强制关（现有） |
| 视图 | 代码块线 | 开 | `isHighlightCurrentBlock` + `setBlockLineWidth(3f)` | 中 | 强制关（现有） |
| 视图 | 迷你地图 | 关（D4） | `props.showMinimap` + `minimapConfig` | 中-高 | 强制关 |
| 视图 | 粘性滚动 | 关 | `props.stickyScroll` | 中 | 关 |
| 视图 | 行号面板 | 关 | `setDisplayLnPanel` | 低 | — |
| 输入 | 显示空白 | 关 | `nonPrintablePaintingFlags` | 渲染微增 | — |
| 输入 | 捏合缩放 | 开 | `isScalable` | 低 | — |
| 输入 | 光标动画 | 关 | `isCursorAnimationEnabled` | 重绘 | 关 |
| 输入 | 硬件键盘优先 | 开 | `isDisableSoftKbdIfHardKbdAvailable` | 低 | — |
| 高级 | 主题 | 跟随系统 | `ThemeRegistry.setTheme` + `TextMateColorScheme` | 切换一次性 | — |

> 与 squircle `EditorSettings`（19 项）对照：本表 21 项覆盖其全部并增补 lide 特有（词法诊断、标识符补全、迷你地图、行号面板）。

## 6.2 性能策略（原则与预算）

**六原则**：
1. 新功能一律「事件驱动 + 守卫式同步」，严禁进打字热路径（沿用 `diagTick` StateFlow 模式，`LuaEditor.kt:346-373`）。
2. 配置同步做 diff 守卫（`if (editor.xxx != expected) editor.xxx = expected`），防每键 setter 全量重排版。
3. 语言级设置一次应用，不随重组重复执行。
4. 大文件（>200KB）降级矩阵统一扩展（§6.3），新增高成本功能全部纳入。
5. 后台线程边界：分析（AsyncIncrementalAnalyzeManager 异步）、快照（Content.toString IO/Default）、写盘（IO）；主线程只做 API 赋值。
6. 所有新回调 `try/catch(Throwable)` 降级 + 预算上界（snippet 导航、tooltip、补全同）。

**新增功能性能预算表**：

| 功能 | 稳态成本 | 触发成本 | 降级 |
|---|---|---|---|
| SnippetController | 0 | 进入/跳转占位符 O(snippet 长) | 无 |
| 诊断 tooltip | 0（事件驱动） | 悬停渲染局部 | 大文件关 |
| 多错误聚合 | 增量扫描已限界 | 同现有 | 大文件跳过（现有） |
| 折叠交互 | 块渲染已有 | 点击切换局部重绘 | 大文件关高亮 |
| 文本操作命令 | 0 | 一次性命令 | 无 |
| 标识符补全 | 默认 0；开则分析线程收集 | 同 | 大文件关 |
| 迷你地图 | 位图增量重绘 | 全文档位图（首帧） | **大文件强制关** |
| stickyScroll | 行级复制 | 低 | 大文件关 |
| 光标动画 | 光标重绘 | 低 | 大文件关 |
| R8/profile | 启动期 | — | — |

## 6.3 大文件降级矩阵（>200KB，现有 + 新增）

| 功能 | 现有（LuaEditor.kt:302-311） | 新增 |
|---|---|---|
| 自动换行 | 强制关 | — |
| 括号匹配高亮 | 强制关 | — |
| 当前块高亮 | 强制关 | — |
| 行级诊断 | 跳过 | — |
| undo 栈 | 200 | — |
| 迷你地图 | — | **强制关** |
| 粘性滚动 | — | 关 |
| 光标动画 | — | 关 |
| 行号面板 | — | 关 |
| 标识符补全 | — | 强制关 |
| 折叠高亮 | — | 关（手柄保留验证后定） |

## 6.4 执行记录

> 每完成一项在下方登记（AGENTS.md 文档治理）。当前全部改动均为工作副本未提交状态。

- **G1 编辑器设置中心（P0）：已完成（2026-08-06）**
  - `EditorConfig.kt` 全量 21 字段（§7 映射表全覆盖）；`SettingsManager.kt` 21 键常量 + `initEditor` 全量读 + `updateEditor` 全量写（`lide_editor` 命名空间，StateFlow 镜像，`.apply()` 落盘）；
  - 新增 `app/.../ui/screen/EditorSettingsScreen.kt`：21 项控件（字号 8..32 / Tab 宽 1..8 步进 + 19 开关），分组 通用/编辑/视图/输入（复用 SettingsSection/SettingsItem/SettingsToggleItem/SettingsStepperItem，全部改 `internal`）；
  - `LideNavHost.kt` 新增 `Routes.EDITOR_SETTINGS` + 导航接线；`SettingsScreen.kt` 编辑器分区收缩为单入口行（`editor_settings` + `editor_settings_subtitle`）；
  - i18n：`values`/`values-zh` strings.xml 成对补 23 键（editor_settings* / setting_on/off / use_tab / auto_indent / auto_close_brackets / auto_completion / identifier_completion / lexical_diagnostics / read_only / line_numbers / highlight_current_line / highlight_bracket_pair / highlight_current_block / minimap / sticky_scroll / ln_panel / scalable / cursor_animation / disable_soft_kbd_if_hard）；
  - 验收：`:app:ktlintMainSourceSetCheck` + `:app:compileArm64DebugKotlin` + `:editor:ktlintCheck` + `:editor:testDebugUnitTest` 全绿。
- **G2 原生 SnippetController：已完成** 见此前会话（`LuaSnippetManager` 原生 snippet 文本 + tab-stop 跳转）。
- **G3 诊断 tooltip + G4 多错误聚合：已完成（2026-08-06）**
  - `LuaDiagnostics.kt`：`ScanError(line, message, fix)` + `FixSpec`（DeleteToken / AppendText / AppendAtFileEnd）；`check(source, quickfixSource: Content? = null)`；EOF 未闭合模式/栈帧 → 补闭合 fix、不匹配闭合符 → DeleteToken fix；`buildRegions` 经 `region.detail.copy(quickfixes=...)` 挂接；`consumeEnd` 新签名（token 相对位置 + excess）；
  - `LuaEditor.kt`：`syntaxCheck(source, quickfixSource)` 传 `editor.text`；
  - 测试扩至 24 例全绿（补闭合/删除/追加/多 fix 独立执行/坐标失效守卫）。
- **G1x 搜索整词：已完成（2026-08-06）** `SearchBar.kt` 新增 `isWholeWord` + FilterChip（`whole_word` 双语言），搜索类型路由 TYPE_WHOLE_WORD。
- **G6 文本操作增强：已完成（2026-08-06）** 新增 `editor/.../EditorTextOps.kt`：selectLine / deleteLine / duplicateLine / toggleCase / 词级前后移动（`moveOrExtendSelection(SelectionMovement.PREVIOUS_WORD_BOUNDARY/NEXT_WORD_BOUNDARY)`），经 `EditorScreen` moreActions「文本」分类挂载（`menu_category_text` 双语言）；6 条操作字符串双语言成对。
- **G7 标识符补全 + G8 迷你地图 + G9 行为开关：已完成（2026-08-06）** 编辑器侧字段与守卫同步此前已落地；设置暴露随 G1 设置中心完成（D2/D4 默认关已落实）。
- **G5 折叠验证：已完成（2026-08-06，模拟器 emulator-5554，x86_64 debug 包）**
  - 设备实测：`FoldTest` 工程（嵌套 function/if/for/while 三级）在 CodeEditor 打开，点击行号区手柄位置无折叠反应；
  - 源码审计（与实测一致）：vendored sora-editor 0.24.6（与上游 tag 一致）**全模块无任何折叠交互代码**——
    `lang/folding/FoldingRegion.java`（含 collapsed/children）为死代码（全库零引用）；widget 层无手柄绘制、无折叠手势；
    折叠交互为上游 0.25+ 引入。故按 §9 风险行预案**降级为「仅渲染不交互」**：保留 `isHighlightCurrentBlock` +
    `setBlockLineWidth(3f)` 块线渲染（TextMate blocks 驱动），不实现自定义折叠 UI（P2 外事项，留待 sora 升级后复核）。
  - 全程无 FATAL/ANR（logcat 复核）。

- **P2 性能上限：已完成（2026-08-06）**
  1. **R8 + baseline profile（G10）**：release 已开 `isMinifyEnabled=true`（现有）；新增
     `app/src/main/baseline-prof.txt`（覆盖启动 / 项目列表首帧 / 编辑器装配 / sora widget+lang+textmate 包级通配 / TextMate 分析路径，
     语法与 androidx 依赖规范一致）＋ `app/build.gradle.kts` 依赖 `androidx.profileinstaller:profileinstaller:1.4.1`；
     验收：`:app:expandArm64ReleaseArtProfileWildcards` 解析通过 → `:app:compileArm64ReleaseArtProfile` 产出
     `binary_art_profile/arm64Release/baseline.prof`（5082B）/ `x64Release/baseline.prof`（5689B）；release 完整构建
     `:app:assembleArm64Release` + `:app:assembleX64Release` 全绿，`verifyReleaseLuaSurface` 复核「29 个类全部保留」。
  2. **大文件降级矩阵扩展（§6.3）**：核验 `LuaEditor.kt` 守卫式同步已含 minimap/stickyScroll/cursorAnimation/行号面板/标识符补全
     随 >200KB 全部强制关（含 undo 栈 200、自动换行/括号/当前块），任务单 §6.3 新增列全部落实，无需补码。
  3. **性能基准脚本** `scripts/perf-check.ps1`：cold（am start -W -S 3 次中位）/ gfx（滚动 jank）/ open（77KB
     `Perf77/big.lua` 深链 WaitTime）/ type（200 字符 gfxinfo jank）四场景 + 建议阈值判定；本次修复 PS 5.1 兼容
     （`& $array` 改 splat、变量内插 `${Metric}`、UTF-8 BOM），ParseFile 语法校验通过。**设备实测推迟**（模拟器复测
     时执行 §8.4 阈值判定）。
  4. TextMate 预算声明：Joni 纯 Java 上限已按 §4.4 记录，不引入 oniguruma（R5）。

- **P3 sora-editor 更新管线：已完成（2026-08-06）**
  1. `scripts/update-sora-editor.ps1`：`-Tag` / 自动取 GitHub latest / `-SkipBuild`；下载 tag zip → 整体替换
     `editor/src/main` + `language-textmate/src/main` → **白名单恢复**（`editor/build.gradle.kts`、`language-textmate/build.gradle.kts`、
     `OnigNative.java` 桩）→ 按上游 `gradle/libs.versions.toml` 同步依赖版本（annotation/collection/gson/jcodings/joni/snakeyamlEngine/jdt 映射）→
     写 `third_party/sora-editor/VERSION` → 门禁（脚本内编译+单测，`-SkipBuild` 时由 CI 承接）。UTF-8 BOM + PS 5.1 兼容，ParseFile 通过。
  2. `.github/workflows/update-sora-editor.yml`：`scheduled`（cron 每周一 02:00 UTC）+ `workflow_dispatch`；job `check-and-update`
     （查上游 latest tag 与 `VERSION` 比对→版本步进时跑更新脚本（`-SkipBuild`，continue-on-error 记录 `script_rc`）→
     `:app:compileArm64DebugKotlin` + `:editor:testDebugUnitTest` + `:app:assembleArm64Debug` 门禁（`gates_rc`）→
     `gh pr create` 全绿标题 `chore(third_party): bump sora-editor X -> Y`、红则 `⚠ ... (build failed)`）＋ job `verify-vendor`
     （下载上游同 tag zip、白名单外文件 `git diff --no-index -r` 与工作树比对，防手改漂移）。
  3. **R1 grep 入 CI（G12）**：`.github/workflows/build.yml` 新增 job `verify-r1`（editor/src 零命中 `luajava|androlua|loadLibrary`）＋
     `verify-no-binaries`（对照 AGENTS.md R5 命令，.so/.dex/.zip/jar 白名单外零跟踪）。G11（自动更新）随 2 完成。
  4. `third_party/sora-editor/README.md` 增加「版本台账」表格 + 更新流程改写（脚本化为推荐路径）；新增 `third_party/sora-editor/VERSION`（记 0.24.6）。
  5. **一致性校验原则确认**：不引入 git submodule、不上 Maven 坐标（沿用 §3.4/§P3 约束）。

---

## 8. 验收标准（评审硬门槛）

### 8.1 构建与静态

```
编译：  .\gradlew.bat :app:compileArm64DebugKotlin --console=plain
构建：  .\gradlew.bat :app:assembleArm64Debug --console=plain
R1：    rg -n "luajava|androlua|loadLibrary" editor/src   # 零命中（P3 起并入 CI）
R5：    git ls-files '*.so' '*.dex' '*.zip'；'*.jar'（wrapper 豁免）
lint：  :editor:ktlintCheck + :app lint（CI 已有）
```

### 8.2 单元测试

`:editor:testDebugUnitTest`（LuaDiagnosticsTest 14 例 + 新增：多错误聚合、snippet 解析）、`:app:testX64DebugUnitTest`、`:androlua:testDebugUnitTest` 全绿。

### 8.3 设备功能验收矩阵（模拟器 + 真机，双端验证 SKILL）

> 验收状态：**待执行**（2026-08-06 推迟至后续模拟器复测；届时按表逐项记录结果，并运行 `scripts/perf-check.ps1 -Scenario all` 录入 §8.4 判定）。

| 场景 | 验收 |
|---|---|
| 设置中心 | 21 项逐项生效（改设置 → 编辑器即时反映，无需重启）；持久化跨进程重启保持 |
| 通用设置页 | 「编辑器设置」单入口；原 4 项已迁移 |
| Snippet | 补全片段 → tab-stop 逐占位符跳转 → 导航结束回到正常编辑 |
| 诊断 tooltip | 悬停/长按错误区域显示 brief + detailed + quickfix |
| 折叠 | Lua `if/end`、`function/end` 折叠展开收起正常 |
| 搜索 | 整词/正则/大小写/替换/循环跳转（回归现有 5 项） |
| 大文件 | 5000 行/200KB+ 打开无卡顿；降级矩阵生效（minimap/sticky/动画关闭） |
| 主题 | darcula/quietlight 切换 + 设置持久化 + 进程重启保持 |
| 回归 | 4 工程矩阵 `scripts/adb_regress.ps1` 6/6 PASS |

### 8.4 性能验收（量化阈值）

| 指标 | 阈值 |
|---|---|
| 打字稳态 | gfxinfo jank=0（2000 行/60KB，20 秒输入） |
| 打开 77KB | ≤ Squircle 同机实测（P2 基准脚本对比） |
| 冷启动（release+R8+profile 后） | 与 Squircle 差距 ≤30%（基线前差 2~3 倍） |
| 全屏重组 | 稳态打字零重组（diagTick 模式回归，systrace 无 Compose 全屏重组） |

### 8.5 代码评审清单（大厂级）

R1~R5 边界零命中；初始化顺序未动；主线程契约；try/catch(Throwable) 全覆盖；无状态写竞争（视图池 AtomicReference 模式）；i18n（values/values-zh 成对）；无障碍（新交互控件 contentDescription）；无死代码/无二进制入库；`docs/kb` 事实来源同步更新。

---

## 9. 风险与开放问题

| 项 | 说明 |
|---|---|
| SnippetController 与现有手写补全路径并存 | 过渡期两套片段来源，实施时以原生 snippet 文本统一，删除手写占位符剥离逻辑 |
| 折叠交互未验证 | ✅ 已闭环（2026-08-06）：0.24.6 无任何折叠交互代码（`FoldingRegion` 死代码），按预案降级为仅渲染不交互；待上游 sora 升级（0.25+）后于 P3 更新管线中复核 |
| baseline profile 收益不确定 | P2 已落地（R8 + `baseline-prof.txt` 打包进 release），构建期收益路径已验证（AOT 规则编译产物）；启动实测 A/B 待模拟器复测（推迟执行） |
| 自动更新 PR 噪音 | 一致性校验 + 构建门禁过滤；失败 PR 标记需人工 review |
| 上游 0.24.6 后版本 API 变化 | P3 白名单桩 `OnigNative` 需按上游 API 变化同步更新签名（README 已注明） |

---

## 10. 任务 WBS 与依赖

| 期 | 任务 | 依赖 |
|---|---|---|
| P0 | 设置中心（EditorConfig 扩展 → LuaLanguage setter → LuaEditor 同步 → SettingsManager → EditorSettingsScreen → 导航 → 资源） | 无 |
| P1 | Snippet / tooltip / 多错误聚合 / 折叠验证 / 文本操作 / 整词搜索 / 标识符补全 / 迷你地图 | P0（设置项承接） |
| P2 | R8 + baseline profile / 降级矩阵扩展 / 基准脚本 | P1（功能定版） |
| P3 | 更新脚本 / CI 工作流 / 一致性校验 / R1 job / README 台账 | 无 |
| P4 | kb 更新 / 验收执行 / 归档 | P0~P3 |

---

> 决策记录：D1~D4 已确认（2026-08-06，见 §1）。实施按 P0→P4 顺序执行，每阶段完成后在本单对应条目标记「（已完成）」+ 验收证据。
