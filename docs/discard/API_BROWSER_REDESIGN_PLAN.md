# API 浏览器界面重构 —— 全量审计报告 + 任务执行单

> 立项：2026-08-03 ｜ 基线：`HEAD 134e168`（+ 工作副本未提交改动，见 T1）
> 审计方式：源码逐行审计 + material3 1.4.0 AAR 反编译核验（javap 实际签名）+ 依赖缓存核验 + R1 门禁扫描
> 性质：静态审计 + 任务驱动实施。本文档是唯一任务来源：每完成一项任务须在此文档标注「已完成」并简述工作内容。

---

## 第一部分：审计报告（结论与违规清单）

### 1. 结论摘要

1. **依赖已就绪，无需升级**：`:app` 与 `:editor` 均已声明 material3 **1.4.0**，AGP 8.6.0 满足要求。AAR 反编译确认 1.4.0 已含新版搜索全家桶：`SearchBarState`（stable）、`SearchBar(state, inputField)`、`TopSearchBar`、`ExpandedFullScreenSearchBar`、`ExpandedDockedSearchBar`、`DockedSearchBar`。**`SearchInput` 不在 1.4.0 内**（属更新版 material3-search 线），inputField 槽位使用 `SearchBarDefaults.InputField`。
2. **需求一差距**：当前搜索是「手搓 BasicTextField 浮层」（`SearchOverlay`），非官方组件；需换为 `SearchBarState + SearchBar（AppBar 内）+ ExpandedFullScreenSearchBar（Dialog 全屏）`，建议列表天然独立于正文列表。
3. **需求二差距**：当前行显示「FQN 全名 + 分类徽章」，需改为「简单名 + 类型徽章（接口/容器控件…）+ FQN API 单独一行」。
4. **安全红线**：接口判定所需反射数据 `ApiIndex.rowInfo/classKind` 与缓存、用途数据 `entry.usage`、字符串资源 `api_kind_*`/`api_usage_*` 全部已存在（后者零引用待接线）；本次重构**不需要新增任何反射 API**，只需在 IO 线程接好行级读取。
5. **必踩的坑（历史冻结根因 3）**：`rememberSearchBarState` 显式传动画 spec 会因引用相等性导致无限重组冻结——必须无参调用。

### 2. 违规项清单

| 编号 | 级别 | 内容 | 处理 |
|---|---|---|---|
| V1 | 必须 | 搜索未使用官方最新全屏搜索组件，且 SearchBar 不在 AppBar 中（`ApiBrowserScreen.kt:175-204, 260-418`） | T4 |
| V2 | 必须 | 列表行不符合「名称 + 类型徽章 + API」三要素（`ClassRow`，`ApiBrowserScreen.kt:453-527`） | T3 |
| V3 | 必须 | 类型徽章的反射读取禁止进 UI 线程（防冻结回归，`ApiIndex.kt:255-300`） | T3（随 T2 数据源） |
| V4 | 必须 | `warmUpLoadable` 只预热 loadableCache；行级改读 rowInfo 后 kindCache 冷，需同步改造（`ApiIndex.kt:198-205`） | T2 |
| V5 | 必须 | `rememberSearchBarState` 禁止显式传动画 spec（无限重组冻结红线） | T4 |
| V6 | 必须 | 工作副本存在大量未提交改动，重构前需定基线 | T1 |
| V7 | 建议 | 孤儿字符串资源 `api_kind_*`/`api_usage_*` 本次接线 | T3 |
| V8 | 建议 | 行级 IO 协程快速滚动启停与固定行高（P1） | T3 |
| V9 | 记录 | `ApiClassDetailScreen` 成员过滤仍为 `OutlinedTextField`（范围外） | 不处理 |
| V10 | 记录 | 无 version catalog，依赖靠人工同步（范围外） | 不处理 |
| V11 | 确认 | R1 门禁与 :editor 边界，本次重构 :editor 零改动 | T5 验证 |

### 3. 目标架构（骨架）

```
Scaffold
├─ TopAppBar(
│    navigationIcon = 返回,
│    title = {
│        SearchBar(state = searchBarState,               // 折叠态，驻留 AppBar
│            inputField = {
│                SearchBarDefaults.InputField(
│                    value=query, onValueChange,
│                    expanded = state.currentValue == Expanded,
│                    onExpandedChange = { it → animateToExpanded / animateToCollapsed },
│                    placeholder / leadingIcon / trailingIcon(清除),
│                )
│            })
│    },
│    actions = { 分类筛选 DropdownMenu },)
├─ 正文区：LazyColumn(bodyList) → 新 ClassRow（简单名+类型徽章 / FQN 单独一行）
└─ if (state.currentValue == SearchBarValue.Expanded) {
     ExpandedFullScreenSearchBar(state, inputField = {同款 InputField}) {
         LazyColumn(searchResults) → 新 ClassRow   // Dialog 新窗口，与正文列表完全独立
     }
   }
```

数据流不变：`produceState + IO` 构建索引 → `bodyList`/`searchResults` 两个独立 `remember(snapshot, category, query)`（解耦、O(n) 字符串过滤）→ 行内 IO 取 `rowInfo` 渲染徽章。

### 4. 实施红线清单

1. **UI 线程零反射**：所有 `Class.forName` 路径（含徽章读取）必须位于 `Dispatchers.IO` + `try/catch(Throwable)`。
2. **`rememberSearchBarState()` 无参调用**，spec 必须缓存实例（V5）。
3. **建议列表与正文列表保持两个独立 LazyColumn**，不合并（需求一明确要求）。
4. 点击建议：先收起搜索态再导航（避免 Dialog 压住新页面）。
5. 徽章文案全部走 `stringResource`（复用 `api_kind_*`/`api_usage_*`），不硬编码。
6. 简单名派生规则：`$` 优先于 `.`；FQN 第二行完整保留（长按复制仍复制 FQN）。
7. 依赖版本不再变更（1.4.0 已就绪）；如编译器要求 `@OptIn`，只加在必要处。

### 5. 验证计划

1. `.\gradlew.bat :app:compileArm64DebugKotlin --console=plain` 通过；
2. R1 门禁：`rg -n "luajava|androlua|loadLibrary" editor/src` 零命中；
3. 真机：AppBar 内点击搜索条 → 全屏展开 + 键盘自动弹出 → 输入过滤即时 → 点击建议进详情 → 返回键收起 → 正文列表不受搜索污染；
4. 徽章抽样：`MenuPresenter$Callback`（接口）、`ActionBarContextView`（容器控件）、`java.io.File`（类）、libs 工程类（工程库）；
5. 列表加载后滚动 30 秒无卡顿、返回键即时响应（冻结审计回归项）；
6. 二次进入（索引缓存命中路径）重复 3-5。

---

## 第二部分：任务执行单（依赖排序）

| 编号 | 任务 | 关联违规 | 依赖 | 优先级 | 状态 |
|---|---|---|---|---|---|
| T1 | 基线确认：核对工作副本未提交改动归属并记录（不提交，仅定基线） | V6 | — | 高 | 已完成 |
| T2 | 数据层改造：`warmUpLoadable` 改预热 `rowInfo`（一次 Class.forName 同时填 kindCache/loadableCache） | V4 | T1 | 高 | 已完成 |
| T3 | 列表行重构：新 ClassRow（简单名 + 类型徽章 + FQN 行），行级 rowInfo IO 读取，固定行高，接线 `api_kind_*`/`api_usage_*` 资源 | V2, V3, V7, V8 | T2 | 高 | 已完成 |
| T4 | 搜索重构：`SearchBarState + SearchBar（AppBar 内）+ ExpandedFullScreenSearchBar` 全屏独立建议列表；删除手搓 SearchOverlay/BackHandler/键盘控制 | V1, V5 | T3 | 高 | 已完成 |
| T5 | 验证：编译 `:app:compileArm64DebugKotlin` + R1 门禁零命中 | V11 | T4 | 高 | 已完成 |
| T6 | 文档收尾：全部任务标注完成状态与工作简述 | — | T5 | 中 | 已完成 |

### 执行记录

- **T1 基线确认：已完成（2026-08-03）**
  工作副本未提交改动核对结果（`git status --short`）：
  - 与本次重构相关的既有改动（冻结修复 + 依赖升级）：`app/build.gradle.kts`（material3 1.4.0 声明）、`app/.../apibrowser/ApiIndex.kt`（warmUpLoadable/缓存修复）、`app/.../apibrowser/ApiUsage.kt`（新增）、`app/.../ui/screen/ApiBrowserScreen.kt`（行级反射 IO 化修复）、`editor/build.gradle.kts`（BOM 2025.06.00 + material3 1.4.0）、`docs/discard/API_BROWSER_FREEZE_AUDIT.md`（新增）。
  - 无关改动：`docs/kb/androlua-community/catalog/*` 语料文件（社区审计产物，与本次无关）。
  - `git stash list` 为空，无未完成暂存。
  - 结论：冻结修复已包含在工作副本内（行级反射已 IO 化、`warmUpLoadable` 已存在），本次重构以当前工作副本为基线叠加；重构完成后由用户决定整体提交。本次任务不执行提交。

- **T2 数据层改造：已完成（2026-08-03）**
  修改 `app/src/main/kotlin/com/lioisme/lide/apibrowser/ApiIndex.kt`：
  - `warmUpLoadable()` → `warmUpRowInfo()`：按列表顺序预热 `rowInfo(name)`（填 kindCache，含负缓存），预算常量改名 `ROW_INFO_WARMUP_BUDGET_MS`（1500ms 不变，超时跳过）。
  - `rowInfo(name)` 内部在取 `classKind` 后同步写 `loadableCache[name] = kind != null`：一次 `Class.forName` 同时填充两缓存，两缓存保持一致，`isLoadable` 与详情页等调用方直接受益；KDoc 明确线程契约。

- **T3 列表行重构：已完成（2026-08-03）**
  修改 `app/src/main/kotlin/com/lioisme/lide/ui/screen/ApiBrowserScreen.kt` 的 `ClassRow` 与新增辅助组件：
  - 行结构改为需求二三要素：第一行 = 简单名（`simpleNameOf`，`$` 优先于 `.`，如 `MenuPresenter$Callback → Callback`）+ 类型徽章；第二行 = FQN API（labelSmall Monospace，单行省略）。
  - 类型徽章 `TypeBadge`：接口（`api_kind_interface`，primaryContainer）→ 用途分类（`api_usage_*` 9 条资源接线，secondaryContainer）→ 兜底类（`api_kind_class`，surfaceVariant）；工程库条目（usage 恒 null）用 tertiary 色系区分。
  - 不可用行保留「当前设备不可用」error 色提示 + 整行灰显语义。
  - 行级反射从 `isLoadable` 升级为 `rowInfo`（一次 Class.forName 同时得接口判定 + 可加载性），保持 `LaunchedEffect + Dispatchers.IO + try/catch` 线程契约（V3）；命中 T2 预热缓存时 O(1)。
  - 固定行高（两行单行文本 + 固定间距 + divider），消除 LazyColumn 测量抖动（V8）。

- **T4 搜索重构：已完成（2026-08-03）**
  修改 `ApiBrowserScreen.kt`：
  - 删除全部手搓实现：`SearchOverlay`（BasicTextField 浮层）、`AnimatedVisibility` 遮罩、`FocusRequester`/键盘控制、`BackHandler`、搜索触发条 Surface。
  - 新增 `rememberSearchBarState()`（**无参调用**，V5 红线注释防护）驱动：折叠态 `SearchBar(state, inputField)` 内嵌 `TopAppBar` title 槽位（SearchBar 在 AppBar 中，需求一）；展开态 `ExpandedFullScreenSearchBar`（Dialog 新窗口，官方内置返回键/缩放动画/键盘管理）承载建议列表 LazyColumn。
  - 建议列表与正文列表分属不同组合树，完全解耦（需求一）；点击建议先 `animateToCollapsed()` 再导航，避免 Dialog 覆盖新目的地。
   - 共用 `ApiSearchInputField`（`SearchBarDefaults.InputField` String 版，无 deprecated）承载两态输入；清除按钮、placeholder、leadingIcon 语义保留。

- **T5 验证：已完成（2026-08-03）**
  1. 编译 `.\gradlew.bat :app:compileArm64DebugKotlin --console=plain`：**BUILD SUCCESSFUL**。
     中间修复两处：① `ApiSearchInputField` 补 `@OptIn(ExperimentalMaterial3Api::class)`（InputField 属实验性 API）；② 重构期间 Scaffold content lambda 提前多闭合一层（孤立 `}`，Syntax error），已按括号深度定位删除，全文件深度校验平衡（0）。
     剩余警告仅 `DexFile` 系列 deprecation（`ApiIndex.kt:234,239,246`），为既有代码，非本次改动引入。
  2. R1 门禁：`editor/src` 内 `luajava|androlua|loadLibrary` 扫描零命中（PowerShell Select-String）。
  3. 真机验证项（验证计划 3-6 条：展开/输入/点击建议/返回键/长列表滚动/二次进入）需在设备上人工确认，本次静态验证阶段不执行。

- **T6 文档收尾：已完成（2026-08-03）**
  任务清单全部完成（T1-T6）；T1-T5 执行记录如上。工作副本改动保持未提交（由用户决定整体提交）。
