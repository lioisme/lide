# 编辑器四需求整改工作单（EDITOR_THEME_FONT_FORMAT）

> **登记**：2026-08-11，docs/planned/backlog.md §2i
> **来源需求**（用户 2026-08-11 四项要求，全量审计后立项）：
> 1. 编辑器界面的颜色必须完全跟随主题系统（现状：宿主 Material3 壁纸/种子动态色
>    与编辑器 TextMate 固定双主题割裂；工程 theme 覆盖只作用文本区；编辑器内部
>    多元素 fallback sora 默认色）；
> 2. 编辑器字体采用 Squircle CE 字体组合（现状：仅系统 3 族，Squircle 默认
>    JetBrains Mono + 6 款内置等宽 + 外部导入）；
> 3. 编辑器性能与优雅度至少达到 Squircle CE（多数已对齐/超越，差距 =
>    补全窗口样式 / 诊断 tooltip 主题键 / 主题切换 UI / 格式化）；
> 4. 实现语法格式化（pro 有「代码→格式化」+ Ctrl+L；LIDE 完全缺失，
>    要求更优雅、更完美）。
> **用户拍板**（2026-08-11）：
> - 字体资产：内置 Squircle 6 款 ttf（OFL），登记 AGENTS.md R5 例外入库；
> - 主题模式：派生宿主 Material3 色（默认跟随）+ 独立主题集（手选）并存；
> - 格式化强度：缩进 + 空白清理档位（零语义风险）。
> **质量要求**：代码质量/架构达大厂验收标准；UI 优美简约、MD3 规范、官方组件库。

## 基线审计（2026-08-11，全量比对 reference/squircle-ce-master + reference/AndroLua_pro-master + vendor sora-editor）

- **主题**：LIDE 宿主 UI = `LideTheme`（wallpaper 动态色 / 种子色生成，Theme.kt:61-96）；
  编辑器文本区 = TextMate 固定 `quietlight`/`darcula` 仅按 isDark 二值切换
  （TextMateRuntime.kt:116、LuaEditor.kt:437-441）；`LideTextMateColorScheme` 只回填
  LINE_DIVIDER/STICKY_SCROLL_DIVIDER 两键。darcula（TM 格式）无 caret / lineNumber /
  completionWindowBackground；quietlight 无 editor.foreground / editorSuggestWidget.* /
  tooltip*。sora 原生 `applyTMTheme` 根本不映射 LINE_NUMBER（TextMateColorScheme.java:262-317）
  → 深色主题下行号色 sora 默认。工程 theme 覆盖（EditorScreen.kt:115-122 effectiveDarkTheme）
  只传给 LuaEditor → 「编辑器深、周围浅」割裂。补全窗口 = sora 默认样式。
  Squircle 标杆：单 JSON 源 global.*/editor.*（17 键完整映射）/tokenColors，
  SquircleScheme 动态换色不重建 + 兜底链。
- **字体**：Squircle 默认 JetBrains Mono（SettingsManager.kt:119），内置 6 款 ttf
  （feature-fonts/assets/fonts/），外部导入 files/fonts/，兜底 TypefaceProvider.DEFAULT=MONOSPACE；
  LIDE = 系统 3 族（LuaEditor.kt:601-608 fontFamilyTypeface），零内置零导入。
- **性能**：LIDE 已对齐（去池化/Content 复用/缓存/守卫 diff/防抖诊断/IO 快照）
  并超越（大文件降级矩阵/增量诊断/minimap）；差距 = 补全窗口样式、tooltip 主题键、
  主题切换 UI、格式化。
- **格式化**：pro = JFlex 词法缩进器（AutoIndent.java:49-113）+ 整文替换 +
  beginBatchEdit 单组撤销 + 菜单/Ctrl+L 入口；LIDE 零实现。

## 任务清单

### 阶段 0 治理基线

- [x] **T0 文档登记**：backlog §2i 登记 + 本工作单建立 + AGENTS.md 字体资产例外登记
  （已完成 2026-08-11；新增 `assets/fonts/*.ttf` 例外，仿 .alp 先例）

### 阶段 1 编辑器主题体系（需求 1 + G1/G2/G3/G5）

- [x] **T1 主题 JSON 补齐**（assets/textmate/darcula.json + quietlight.json）
  - [x] T1.1 darcula：settings[0].settings 补 caret / lineNumber / lineNumberActiveForeground
        / invisibles / completionWindowBackground / completionWindowBackgroundCurrent /
        tooltipBackground / tooltipBriefMessageColor / tooltipDetailedMessageColor /
        tooltipActionColor / highlightedDelimitersBackground / indentGuide 键
  - [x] T1.2 quietlight：colors 补 editor.foreground / editorSuggestWidget.*（background/
        foreground/selectedBackground/highlightForeground）/ tooltip* / editorCursor.foreground
        已存在核对
- [x] **T2 LideTextMateColorScheme 完整映射**（对齐 SquircleScheme 17 键 + 兜底链）
  - [x] T2.1 全键映射：background→WHOLE_BACKGROUND+LINE_NUMBER_BACKGROUND、
        foreground→TEXT_NORMAL、cursor→SELECTION_INSERT、handle→SELECTION_HANDLE、
        selection→SELECTED_TEXT_BACKGROUND、lineHighlight→CURRENT_LINE、
        highlightedDelimiters{Background,Foreground}、matchHighlight→MATCHED_TEXT_BACKGROUND、
        lineNumber{Background,Foreground,activeForeground}、indentGuide{Background,activeBackground}
        →BLOCK_LINE/BLOCK_LINE_CURRENT、popup*→COMPLETION_WND_*、tooltip*→DIAGNOSTIC_*
  - [x] T2.2 键缺失兜底：保留 sora 默认（super.applyDefault 后不回退）
- [x] **T3 EditorThemeResolver 宿主色派生**（editor 包，EditorTheme.kt）
  - [x] T3.1 「跟随宿主」模式：由宿主注入的 Material3 色板派生编辑器基础色
        （surface→背景、onSurface→前景、outline→行号、surfaceVariant→当前行、
        primary→光标/选中、primaryContainer→选区、outlineVariant→分隔线），
        以 ARGB Int 经 EditorConfig 值对象注入（零 sora 类型外泄）
  - [x] T3.2 派生方案以 `EditorColorScheme` 子类实现（LideHostColorScheme），
        与 TextMate token 高亮合成：基础槽宿主色 + token 色保留
- [x] **T4 LuaEditor 主题应用改造**
  - [x] T4.1 主题模式状态：跟随宿主（默认）/ 亮 / 暗 / darcula / quietlight（独立集）
  - [x] T4.2 跟随模式：LideHostColorScheme 装配；独立模式：ThemeRegistry.setTheme(name)
  - [x] T4.3 切换保持动态换色（进程级单例 scheme + ThemeChangeListener 广播，零重建）
- [x] **T5 编辑器色带解耦（工程 theme 覆盖语义修正）**
  - [x] T5.1 `EditorChromeColors` 纯值类（surface/onSurface/primary/onSurfaceVariant/
        outlineVariant）+ LocalEditorChrome + rememberEditorChrome
  - [x] T5.2 EditorToolbar / EditorTabs 由 MaterialTheme.colorScheme 改为注入色带
  - [x] T5.3 EditorComponents（ui 包）同改；EditorScreen 以 effectiveDarkTheme
        驱动整条编辑器色带（工具栏+标签行+文本区同源）
- [x] **T6 LuaCompletionWindow 补全窗口自定义**
  - [x] T6.1 SquircleCompletion 移植：圆角 + 1dp 描边（COMPLETION_WND_CORNER/
        COMPLETION_WND_BACKGROUND）、当前项（COMPLETION_WND_ITEM_CURRENT）、
        匹配文字（COMPLETION_WND_TEXT_MATCHED）、onApplyColorScheme 全色跟随
- [x] **T7 编辑器主题设置键**
  - [x] T7.1 EditorConfig 加 `editorTheme` 字段 + SettingsRegistry 键
  - [x] T7.2 EditorSettingsScreen 新增「编辑器主题」选择（跟随宿主/亮/暗）
  - [x] T7.3 字符串 zh/en 双语

### 阶段 2 字体（需求 2）

- [x] **T8 字体资产**：assets/fonts/ 入库 6 款 ttf（jetbrains_mono/droid_sans_mono/
      fira_code/source_code_pro/anonymous_pro/dejavu_sans_mono，来自
      reference/squircle-ce-master/feature-fonts，OFL）
- [x] **T9 LuaFonts 字体解析**（editor 包，新文件）
  - [x] T9.1 内置字体表（id→asset 路径）+ files/fonts ttf 外部扫描 + 导入/选择
        （SAF 导入入口在 T11 面板；进程级 Typeface 缓存供守卫式 diff）
  - [x] T9.2 兜底 Typeface.MONOSPACE（对齐 Squircle TypefaceProvider.DEFAULT）
- [x] **T10 EditorConfig.fontFamily 扩展 + LuaEditor 接线**
  - [x] T10.1 字体 id 枚举扩展（内置 6 + 系统 3 + 外部导入 BASE），默认 = JetBrains Mono
  - [x] T10.2 fontFamilyTypeface 改经 LuaFonts 解析；守卫式 diff 不变
- [x] **T11 字体选择面板升级**：内置 6 款预览（24sp 等宽示例）+ 系统 3 族
      + SAF 导入入口（OpenDocument → files/fonts/ → invalidate 重扫）

### 阶段 3 语法格式化（需求 4，缩进+空白清理档位）

- [x] **T12 LuaLexer 词法层抽取**（editor 包，新文件）
  - [x] T12.1 从 LuaDiagnostics.scanLine 抽取共享词法原语（字符串/行注释/长注释/
        长字符串/长括号/标识符/关键字），产出 token 流（类型+文本+偏移）
  - [x] T12.2 LuaDiagnostics 增量检查点与 LuaFormatter 共用词法层（单事实源），
        行为等价回归（既有诊断单测锁）
- [x] **T13 LuaFormatter**（纯 Kotlin，零依赖，R1 合规）
  - [x] T13.1 单遍 token 流缩进重排：块栈（if/for/while/do/function/repeat +
        end/until）；elseif/else/end/until 提前减级；字符串/注释内关键字零误伤
  - [x] T13.2 空白清理：行尾空格删除、连续空行折叠（保留 1）、行首缩进重写
        （尊重 useTab/tabSize）
  - [x] T13.3 大文件（>200KB）后台执行，不阻塞主线程
- [x] **T14 格式化应用链**
  - [x] T14.1 EditorController.format()：后台计算 → 主线程 beginBatchEdit +
        Content.replace 全量替换 → 单组撤销；try/catch 降级
  - [x] T14.2 EditorSession 全文档替换门面（统一脏标记 + 自动保存节流）
- [x] **T15 格式化入口**：⋮「编辑」分类「格式化」项 + 硬键盘 Ctrl+L（sora
      KeyBindingEvent）+ 只读置灰 + 完成 Snackbar
- [x] **T16 LuaFormatterTest**：缩进正确性/注释保护/字符串保护/长括号/换行符归一/
      大文件短路/useTab-tabSize 矩阵（22 用例全绿）

### 阶段 4 验证与收口

- [x] **T17 全量验证**（已完成 2026-08-12，治理收口时一并核销：
      2026-08-12 治理（EDITOR_APPEARANCE_GOVERNANCE_WORK_ORDER）已移除
      三态手选，T17.4 的「主题三路切换」实测语义被「单一跟随宿主」取代，
      治理实测：暗 → darcula #242424 / 亮 → quietlight #F5F5F5 双模跟随）
  - [x] T17.1 `:app:compileArm64DebugKotlin` + `:app:testArm64DebugUnitTest`
        （editor.* + build.* 全量）
  - [x] T17.2 `:app:verifySoraIsolation`（新组件零 sora 类型外泄）
  - [x] T17.3 `:app:verifyMinimalismDebug`（字体进 assets，门禁不受影响）
  - [x] T17.4 `:app:assembleX64Debug` + 模拟器实测（2026-08-12 治理后语义：
        x64 模拟器 emulator-5554 双模跟随 + 像素采样 + 权限页实测，
        验收证据见 EDITOR_APPEARANCE_GOVERNANCE_WORK_ORDER 验证矩阵）
- [x] **T18 收口**（已完成 2026-08-12）：工作单全文标注（已完成）+ 结论蒸馏
      （docs/kb/EDITOR.md §12 追加）+ backlog 状态回填（F11/F12 标注 T19 阶段
      收口；2026-08-12 治理单 2j 段 G1-G5 已另行登记与核销）

### 阶段 5 颜色体系收口（T19，2026-08-12 用户新要求）

> **需求**（用户拍板，2026-08-12）：AppBar 颜色与主界面/设置界面实现方式
> 保持一致（宿主 Material3 surface，不随编辑器主题模式）；编辑器文本区背景
> 跟随主题系统且使用科学专业的颜色；AppBar 与编辑器底色必须可区分。
> **决策**：标签行/分隔线/空态一并回归宿主 Material3（色带机制退役）；
> FOLLOW 编辑器背景 = 宿主 `surfaceContainerHigh`（MD3 容器层级，
> 与 AppBar(surface) 温和区分）；LIGHT/DARK 保持 TextMate 官方主题色
> （quietlight #F5F5F5 / darcula #242424，sora 原生映射零改动）。

- [x] **T19 编辑器颜色规范收口（色带退役 + AppBar 宿主化 + FOLLOW 科学色）**
      （已完成 2026-08-12）
  - [x] T19.1 EditorToolbar：AppBar `containerColor`/`titleContentColor` 改用
        `MaterialTheme.colorScheme.surface`/`onSurface`（与 ProjectListScreen.kt:256-260 同款）
  - [x] T19.2 标签行/分隔线/空态回归宿主 Material3（EditorTabs/EditorComponents
        全 `chrome.*` → `MaterialTheme.colorScheme.*`）
  - [x] T19.3 色带机制退役：删除 EditorChromeColors.kt
        （EditorChromeColors/LightChrome/DarkChrome/chromeForMode/rememberEditorChrome）
        + EditorScreen/EditorToolbar/EditorTabs/EditorComponents 参数链清理
  - [x] T19.4 FOLLOW 编辑器背景 = 宿主 `surfaceContainerHigh`（EditorTheme.kt
        rememberHostEditorColors.background）；补全窗/tooltip 浮层 → 
        `surfaceContainerHighest`（Theme.kt generateLight/DarkScheme 补齐槽位，
        浮层 > 画布 > chrome 的 MD3 层级语义）
  - [x] T19.5 验证：`:app:compileArm64DebugKotlin` + `:app:testArm64DebugUnitTest`
        （editor.*）+ `:app:verifySoraIsolation` + `:app:verifyMinimalismDebug`
        全绿；x64 模拟器三路主题 × 宿主亮暗 6 场景像素实测通过
        （AppBar #1A1C1E/#FDFCFF 宿主 surface ≠ 编辑器 #252629/#E6E7EC/
        #F5F5F5/#242424；高亮 token 色正确）
  - [x] T19.6 收口：AGENTS.md T17/T18 段落更新（色带退役 + surfaceContainerHigh
        语义 + 修正 DARK 像素记录）+ 结论蒸馏（docs/kb/EDITOR.md §12 追加）

## 验收标准

1. 主题：改壁纸/种子色/明暗 → 编辑器文本区与宿主 UI 同步变化（跟随模式默认）；
   手动选择独立主题（亮/暗/darcula/quietlight）立即生效且不重建编辑器；
   工程 theme 字段驱动整条编辑器色带（工具栏/标签行/文本区同源），无「编辑器深、
   周围浅」割裂；深色主题下光标/行号/补全窗口/诊断 tooltip 全部可见协调。
2. 字体：默认 JetBrains Mono；设置页 6 款内置预览可选 + 系统 3 族 + 外部导入；
   正文/行号同字体；失败回落 MONOSPACE。
3. 格式化：⋮「编辑→格式化」+ Ctrl+L；缩进按块结构重排、行尾/空行清理；
   字符串/注释内关键字零改动；一次撤销整体还原；大文件不卡主线程。
4. 性能：稳态打字零重组回归；主题切换零重建；格式化大文件不阻塞 UI。
5. 门禁：verifySoraIsolation / verifyMinimalism / 全量单测全绿。

## 红线自查（AGENTS.md）

- 全部改动在 `com.lioisme.lide.editor` / `com.lioisme.lide.ui.*` /
  `com.lioisme.lide.session`；格式化器纯 Kotlin 零原生依赖（R1/R2 合规）；
- sora 门面：新组件（主题解析/格式化/字体解析）零 `io.github.rosemoe` 类型外泄
  （LideHostColorScheme 等仅 editor 包内使用；宿主经值对象/门面交互）；
- 编辑器 API 主线程；补全/诊断/事件回调 try/catch(Throwable) 降级保持；
- 字体 ttf 为 R5 登记例外（AGENTS.md §精简治理同步）；`git ls-files '*.ttf'`
  允许 6 款内置字体（assets/fonts/），其余二进制仍禁止。
