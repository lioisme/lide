# Editor UI Alignment with Squircle-CE — Work Order & Quality Checklist

| Metadata | Value |
|----------|-------|
| Document ID | LIDE-UI-001 |
| Status | Draft — Awaiting Approval |
| Author | Engineering (Audit) |
| Stakeholders | Product, IDE Engineering, QA |
| Priority | P0 — Blocks editor UX acceptance |
| Baseline | lide HEAD `c7b35dd` · Squircle-CE `reference/squircle-ce-master` |
| Est. Effort | 4–5 engineering days (M1–M5) |
| **2026-08-05 复核** | WP-003~006 ✅ 已落地（EditorTabs.kt:104/203/223/273-291）；WP-001/002/007/008 ⛔ 被取代（抽屉已整体删除，文件树改 `ProjectFileBrowserDialog`，EditorComponents.kt:95）；WP-009/010 待办 |

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Background & Root-Cause Evidence](#2-background--root-cause-evidence)
3. [Scope](#3-scope)
4. [Reference Baseline (Squircle-CE)](#4-reference-baseline-squircle-ce)
5. [Work Packages](#5-work-packages)
   - [WP-001: Drawer Back-Key Contract (P0)](#wp-001-drawer-back-key-contract-p0)
   - [WP-002: Drawer Close-Race Fix (P0)](#wp-002-drawer-close-race-fix-p0)
   - [WP-003: Tab Drag-and-Drop Reorder (P0)](#wp-003-tab-drag-and-drop-reorder-p0)
   - [WP-004: Tab Card Style Alignment (P1)](#wp-004-tab-card-style-alignment-p1)
   - [WP-005: Tab Close Menu (P1)](#wp-005-tab-close-menu-p1)
   - [WP-006: New-Tab Auto-Scroll (P1)](#wp-006-new-tab-auto-scroll-p1)
   - [WP-007: Drawer Gesture Alignment (P1)](#wp-007-drawer-gesture-alignment-p1)
   - [WP-008: Hamburger Toggle Semantics (P2)](#wp-008-hamburger-toggle-semantics-p2)
   - [WP-009: File Tree Horizontal Scroll (P2)](#wp-009-file-tree-horizontal-scroll-p2)
   - [WP-010: Version Catalog (P2 — Optional)](#wp-010-version-catalog-p2--optional)
6. [Implementation Order & Milestones](#6-implementation-order--milestones)
7. [Engineering Conventions & Notices](#7-engineering-conventions--notices)
8. [Quality Gates](#8-quality-gates)
9. [Acceptance Criteria — Traceability Matrix](#9-acceptance-criteria--traceability-matrix)
10. [Manual Regression Test Plan](#10-manual-regression-test-plan)
11. [Risk Register](#11-risk-register)
12. [Rollback Plan](#12-rollback-plan)
13. [Definition of Done](#13-definition-of-done)
14. [Appendix — Audit Evidence](#14-appendix--audit-evidence)

---

## 1. Executive Summary

lide 宿主界面（`:app`）与编辑器界面（`:editor`）的 Tab 标签栏、侧边栏抽屉、文件树需与 Squircle-CE 对齐，并满足硬性约束：**宿主所有界面一律使用官方或成熟第三方组件，禁止手搓自研**。

全量审计结论（详见 `docs/` 历史审计与本报告 §2、§14）：

- **架构方向正确**：抽屉容器（`ModalNavigationDrawer`/`ModalDrawerSheet`）、Tab 容器（`LazyRow`）、文件树（`LazyColumn`）、工具栏（M3 `TopAppBar`）均为官方组件；Squircle-CE 自研的 `:core-ui`（`TabItem`/`DrawerSuite`）属"手搓"，**不在对齐范围**。
- **P0 缺陷 2 项（抽屉）**：① 系统返回键不会先关抽屉而是直接弹出编辑器（Material3 1.3.0 的 `ModalNavigationDrawer` 经字节码实证无内部 `BackHandler`，lide 亦未自备）；② `openFileFromTree` 中 `drawerState.close()` 在挂起 IO 之后执行，存在竞态，可将"打开"状态写入 saveable 状态，造成"返回主界面时抽屉自动打开"。
- **P1 差距 4 项（Tab 栏）**：标签卡样式（chip 卡 vs Squircle 扁平标签 + 底部指示条）、无拖拽排序、无"点击当前标签弹关闭菜单"、新标签不自动滚动到末尾。
- **P2 差距 3 项**：抽屉手势（不可拖拽关闭）、文件树无横向滚动、无版本目录。
- 唯一新增第三方依赖：**`sh.calvin.reorderable:reorderable:3.0.0`**（Squircle 同款；POM 实证与 lide 的 Compose BOM 2024.09.00 / 1.7.3 二进制兼容）。

交付物：10 个工作包（WP-001…WP-010）、13 条验收标准（AC-01…AC-13）、16 条手工回归用例（TC-01…TC-16）、8 项工程注意事项（N-01…N-08）。

---

## 2. Background & Root-Cause Evidence

### 2.1 抽屉缺陷根因（实证，见 §14）

| # | 现象 | 根因 | 证据 |
|---|------|------|------|
| E1 | 抽屉打开时按返回键，不关抽屉而是直接返回主界面 | Material3 1.3.0 `ModalNavigationDrawer` **无内部 BackHandler**；lide 亦无自定义 BackHandler（全库 grep 零命中） | §14.1 javap 字节码检索 |
| E2 | 返回编辑器界面时抽屉自动打开 | `rememberDrawerState` 为 `rememberSaveable` 实现；navigation-compose 2.8.5 按 back-stack entry 保存/恢复状态。若组合在抽屉 Open 时销毁（E1 触发链），Open 值落盘，同一 entry 恢复时抽屉重新打开 | §14.2 `DrawerState.Saver` 实证 |
| E3 | 打开文件期间抽屉敞开 | `EditorScreen.kt:104-112`：`scope.launch { session.openTab(relative); drawerState.close() }` —— `close()` 在挂起 IO（读盘 + `Content` 构造）**之后**执行；窗口期内按返回 → 协程随组合销毁取消，`close()` 永不执行 | `EditorScreen.kt` 全文 |

### 2.2 Tab 栏差距清单

| # | 维度 | lide 现状 | Squircle-CE 参照 | 处置 |
|---|------|-----------|------------------|------|
| G1 | 卡片样式 | chip 卡：`RoundedCornerShape(8.dp)`、`secondaryContainer` 底、高 34dp（`EditorTabs.kt:72-86`） | 扁平标签：高 36dp、无背景、选中态为底部 3dp `primary` 圆角指示条（`TabItem.kt` / `TabIndicator.kt`） | WP-004 |
| G2 | 脏标记 | 6dp 实心圆点 | 标题前缀 `"• "`（`DocumentTab.kt:58`） | WP-004 |
| G3 | 拖拽排序 | 无 | `sh.calvin.reorderable:3.0.0` + `longPressDraggableHandle` + 拖起 alpha 0.5 + 触感反馈（`DocumentTabLayout.kt:50-82`） | WP-003 |
| G4 | 关闭菜单 | 仅每标签 X | 点击**当前**标签弹菜单：Close / Close Others / Close All（`DocumentTab.kt:60-66`） | WP-005 |
| G5 | 新标签滚动 | 无 `LazyListState` | 追加时 `animateScrollToItem(size)`（Squircle EditorScreen 事件处理） | WP-006 |
| G6 | 排序持久化 | 无（会话内存态） | `documentRepository.reorderDocuments`（Room） | 本次不实现，见 §3 |

### 2.3 抽屉行为差距清单

| # | 维度 | lide 现状 | Squircle-CE 参照 | 处置 |
|---|------|-----------|------------------|------|
| G7 | 返回键 | 无（E1） | `BackHandler { if (isOpen) close else onBack }`（EditorScreen.kt:237-241） | WP-001 |
| G8 | 拖拽关闭 | `gesturesEnabled = false`（EditorScreen.kt:132） | `drawerGesturesEnabled = drawerState.isOpen`（:357） | WP-007 |
| G9 | 汉堡键 | 只开不关（`{ scope.launch { drawerState.open() } }`，EditorScreen.kt:177） | toggle 开合（EditorScreen.kt:103-108） | WP-008 |

### 2.4 文件树差距清单

| # | 维度 | lide 现状 | Squircle-CE 参照 | 处置 |
|---|------|-----------|------------------|------|
| G10 | 横向滚动 | 无（长路径省略号截断，`ProjectFileTree.kt:150-157`） | `horizontalScroll` + 条目最小宽 248dp + 宽度缓存（`FileExplorer.kt`） | WP-009 |

---

## 3. Scope

### 3.1 In Scope

- 抽屉：返回键契约（WP-001）、关闭竞态（WP-002）、手势对齐（WP-007）、汉堡 toggle（WP-008）。
- Tab 栏：拖拽排序（WP-003）、样式对齐（WP-004）、关闭菜单（WP-005）、新标签滚动（WP-006）。
- 文件树：横向滚动（WP-009）。
- 构建：版本目录（WP-010，可选）。

### 3.2 Out of Scope（明确排除）

| 项目 | 理由 |
|------|------|
| 照搬 Squircle `:core-ui`（`DrawerSuite`/`TabItem`/`TabIndicator`/DS IconButton 等） | 违反"禁止手搓自研"硬性约束；仅借鉴其**行为与视觉规范**，实现一律使用官方组件 |
| Tab 排序跨会话持久化（Room/磁盘） | lide 会话为内存态（`EditorSession` 按 `remember(project)` 重建）；需先建会话持久化基础设施，列为后续工作项 |
| 文件树长按多选 / 操作栏（ExplorerActionBar）、异步节点加载 | 超出本次对齐目标，列为后续工作项 |
| 抽屉边缘滑开手势 | 与编辑器触摸交互冲突（双方设计均禁止）；仅支持"打开后可拖拽关闭" |
| 编辑区（`LuaEditor`/诊断/搜索）任何改动 | 与本次 UI 对齐无关，禁止触碰 |

---

## 4. Reference Baseline (Squircle-CE)

| 项目 | 参照事实 |
|------|----------|
| Tab 行容器 | `core-ui` `TabLayout.kt`：`LazyRow(state, height(36.dp))` + 底部 `HorizontalDivider` |
| Tab 条目 | `TabItem.kt`：高 36dp，无背景，`Role.Tab`，ripple 指示；`TabIndicator.kt`：3dp 高、`RoundedCornerShape(topStart=6, topEnd=6)`、`colorPrimary`、仅选中态显示 |
| 关闭按钮 | DS `IconButton`（Secondary / XXS / ic_close / 横向 padding 8dp）；脏标记为标题 `"• "` 前缀 |
| 拖拽 | `sh.calvin.reorderable:reorderable:3.0.0`；`rememberReorderableLazyListState(state) { from, to -> ... }`；`ReorderableItem(state, key)`；`longPressDraggableHandle`；拖起 alpha 0.5 / 静止 1.0；触感 START/MOVE/END |
| 排序语义 | ViewModel `onDocumentMoved(from, to)`：校验边界 → 移动 → 调整选中位置（右移 −1 / 左移 +1 / 选中项随行）→ 持久化 |
| 抽屉容器 | 自研 `DrawerSuite`（anchoredDraggable fork）——**仅参照行为**：最大宽 312dp、scrim 0.45、`drawerGesturesEnabled = drawerState.isOpen`、汉堡 toggle、`BackHandler` 先关抽屉 |
| 抽屉状态 | `rememberDrawerState(DrawerValue.Closed)`，`rememberSaveable` 支撑；Nav3 `rememberSaveableStateHolderNavEntryDecorator` 按 entry 恢复 |
| 文件树 | `LazyColumn` + 展平节点 + 深度缩进（12dp × displayDepth）+ `horizontalScroll` + 最小宽 248dp；无第三方树库 |

---

## 5. Work Packages

### WP-001: Drawer Back-Key Contract (P0)

> **（被取代）2026-08-05**：抽屉已整体删除（2026-08 决策，`EditorComponents.kt:95` 改 `ProjectFileBrowserDialog` 全屏对话框），返回键契约随之消亡；本 WP 不再实施。验收 AC-01 标注 N/A。

| 属性 | 内容 |
|------|------|
| 目标 | 修复 E1：系统返回键在抽屉打开时**先关抽屉**，再按一次才退出编辑器（经典侧边栏抽屉契约） |
| 文件 | `app/src/main/kotlin/com/lioisme/lide/ui/screen/EditorScreen.kt` |
| 依赖 | 无 |
| 工时 | 0.5 天 |

**实现要点**

```kotlin
// EditorScreen.kt — 放在 ModalNavigationDrawer 之外、Box 之内（与 drawerState 同组合作用域）
BackHandler(enabled = drawerState.isOpen) {
    scope.launch { drawerState.close() }
}
```

- 采用**条件注册**（`enabled = isOpen`）而非 Squircle 的无条件分支：抽屉关闭时返回事件归 NavHost（导航返回），抽屉打开时由本处理器拦截，避免与 navigation-compose 2.8.5 的预测性返回（API 34+）竞争。
- 系统返回触发的 `popBackStack` 不经过 `onBack`（不主动 flush），脏内容由既有 `LuaEditor` 的 `onRelease` 兜底保存（`EditorScreen.kt:221-224`）——保持现状，不引入新行为。
- 禁止在 `:editor` 添加 BackHandler（R3：宿主专属逻辑在 `:app`）。

**验收标准（AC-01）**：抽屉打开时按系统返回键，仅关闭抽屉，编辑器停留；再次按返回键，编辑器返回主界面；关闭动画进行中连按返回不触发双重 pop（对照 §10 TC-01/TC-02/TC-03）。

---

### WP-002: Drawer Close-Race Fix (P0)

> **（被取代）2026-08-05**：同 WP-001，抽屉删除后 `openFileFromTree` 竞态不复存在；本 WP 不再实施。验收 AC-02 标注 N/A。

| 属性 | 内容 |
|------|------|
| 目标 | 修复 E3：抽屉关闭不再依赖挂起 IO 完成；杜绝 Open 状态落盘（E2 触发链） |
| 文件 | `app/src/main/kotlin/com/lioisme/lide/ui/screen/EditorScreen.kt`（`openFileFromTree`，104-112 行） |
| 依赖 | 无 |
| 工时 | 0.5 天 |

**实现要点**

```kotlin
fun openFileFromTree(node: FileNode) {
    if (node.isDirectory) return
    val prefix = project.path + File.separator
    val relative = if (node.path.startsWith(prefix)) node.path.removePrefix(prefix) else node.name
    scope.launch { drawerState.close() }          // 立即关闭，与 IO 无依赖
    scope.launch { session.openTab(relative) }    // 并行打开（原挂起逻辑不变）
}
```

- 两个独立协程：关闭立即执行，打开保持原异步语义；任一失败互不影响。
- 结合 WP-001 后，抽屉不存在"打开状态下组合被销毁"的合法路径；Open 状态无法写入 saveable，E2 触发链闭环。
- 防御性加固（可选，成本极低）：`LaunchedEffect(Unit) { drawerState.close() }`——entry 恢复时若被 M3 版本缺陷残留 Open，则静默归位（`close()` 在已关闭时为零开销 no-op）。

**验收标准（AC-02）**：点击文件树条目，抽屉立即收起（无"文件加载期间抽屉敞开"窗口）；文件打开失败时抽屉状态不受影响；连续快速点击多个条目无异常状态（对照 §10 TC-03/TC-04）。

---

### WP-003: Tab Drag-and-Drop Reorder (P0)

> **（已完成）2026-08-05**：`sh.calvin.reorderable:reorderable` 依赖落地（`editor/build.gradle.kts`）；`EditorTabs.kt:104,203` 接入 `rememberReorderableLazyListState` + `ReorderableItem` + `longPressDraggableHandle`；`EditorSession.kt:231,248` 新增 `moveTab(from, to)`；`EditorScreen.kt` 接线。验收 AC-03 通过（源码核对，未做真机回归）。

| 属性 | 内容 |
|------|------|
| 目标 | 修复 G3：标签支持长按拖拽排序，行为与 Squircle 一致 |
| 文件 | `editor/build.gradle.kts`；`editor/.../EditorTabs.kt`；`app/.../session/EditorSession.kt`；`app/.../ui/screen/EditorScreen.kt` |
| 依赖 | 无 |
| 工时 | 1 天 |

**实现要点**

1. **依赖**（唯一新增第三方依赖）：

```kotlin
// editor/build.gradle.kts dependencies 块
implementation("sh.calvin.reorderable:reorderable:3.0.0")
```

版本**固定 3.0.0**，禁止浮动版本（POM 实证：构建于 Compose 1.7.0 / Kotlin 1.9 stdlib，与 BOM 2024.09.00 → Compose 1.7.3 二进制兼容，见 §14.3）。

2. **`EditorTabs.kt` API 变更**（保持现有参数兼容，新增默认参数）：

```kotlin
@Composable
fun EditorTabs(
    tabs: List<EditorTab>,
    selectedFile: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    modifier: Modifier = Modifier,
    onTabMoved: (from: Int, to: Int) -> Unit = { _, _ -> },   // 新增，默认 no-op
    state: LazyListState = rememberLazyListState(),           // 新增，提升状态
) {
    val reorderableState = rememberReorderableLazyListState(state) { from, to ->
        onTabMoved(from.index, to.index)
    }
    LazyRow(state = state, ...) {
        items(tabs, key = { it.fileName }) { tab ->
            ReorderableItem(reorderableState, tab.fileName) { isDragging ->
                EditorTabItem(
                    ...,
                    modifier = Modifier
                        .alpha(if (isDragging) 0.5f else 1f)
                        .longPressDraggableHandle(onDragStarted = { /* 触感 START */ }, onDragStopped = { /* 触感 END */ }),
                )
            }
        }
    }
}
```

- 触感反馈用官方 API：`LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)`（拖动开始/结束各一次；拖动中 tick 可选，不强制——Squircle 的自研 `ViewCompat` 版本无需照搬）。
- 拖拽键仍为 `fileName`（项目内相对路径唯一，重排不改变键），`LazyRow`/`ReorderableItem` 键必须一致。
- 拖拽中条目 alpha 0.5（与 Squircle `DragAlpha` 常量一致）。

3. **`EditorSession.kt` 新增 `moveTab(from, to)`**：

```kotlin
/** 拖拽排序：仅重排标签列表；选中按 fileName 语义自动跟随，无需调整选中索引。 */
fun moveTab(from: Int, to: Int) {
    val current = _tabs.value
    if (from !in current.indices || to !in current.indices || from == to) return
    val mutable = current.toMutableList()
    val moved = mutable.removeAt(from)
    mutable.add(to, moved)
    _tabs.value = mutable
}
```

- 说明：lide 的选中语义是 `selectedFile: String?`（按文件名），**天然跟随**标签移动，无需 Squircle 的 `selectedPosition` 修正逻辑；`Content` 缓存（`contents`）按文件名键控，重排不触碰。
- 仅主线程调用（与现有 `_tabs` 变更约定一致）。

4. **宿主接线**：`EditorScreen.kt:197-205` 传 `onTabMoved = { from, to -> session.moveTab(from, to) }`。

**验收标准（AC-03）**：长按标签可拖拽重排；拖拽中条目半透明、无键冲突（无条目消失/重复）；选中标签移动后选中态跟随；脏标记跟随；重排不触发编辑器重载/重建（对照 §10 TC-05/TC-06）。排序在会话内存态内生效（跨会话持久化见 §3.2 Out of Scope）。

---

### WP-004: Tab Card Style Alignment (P1)

> **（已完成）2026-08-05**：`EditorTabs.kt:223,273-291` 扁平标签 + 底部 3dp `primary` 指示条 + `•` 脏前缀落地（`DocumentTab` 式样）；`EditorComponents.kt:112,168` 对齐引用。验收 AC-04 通过（源码核对）。

| 属性 | 内容 |
|------|------|
| 目标 | 修复 G1/G2：标签卡样式对齐 Squircle（扁平标签 + 底部指示条 + `•` 脏前缀） |
| 文件 | `editor/.../EditorTabs.kt`（`EditorTabItem`，65-122 行） |
| 依赖 | WP-003（同文件改动，建议一并提交） |
| 工时 | 0.5 天 |

**实现要点**

- 条目：高 **36dp**，**无卡片背景**（去掉 `Surface` 的 `secondaryContainer`/`surfaceContainerHigh` 容器色），改为无背景 + ripple 指示；点击行为保留 `onClick`（可用 `Modifier.selectable(role = Role.Tab, indication = ripple(), selected, onClick)`，对齐 Squircle 的 `Role.Tab` 语义与无障碍）。
- 选中指示：条目底部 **3dp** 高、`RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)`、`colorScheme.primary`，仅选中态显示（`animateColorAsState` 淡入淡出可选，不强制）。
- 文字：`MaterialTheme.typography.labelMedium`（或 `titleSmall`，与 36dp 条高匹配）；`maxLines = 1` + ellipsis 保留。
- 脏标记：圆点改为标题前缀 `"• "`（Squircle 同款）；非脏无前缀。`"• "` 属视觉前缀，无需独立 `contentDescription`（标题文本整体朗读）。
- 关闭按钮：保留 M3 `IconButton`（约 26dp、14dp 图标、现有 `cd_close_tab` 描述），横向 padding 8dp——与 Squircle DS XXS 等效，不引第三方。
- 标签行底色：与编辑器工具栏底色一致（`surface`），可加 1dp `surfaceContainer` 分隔线（对齐 Squircle `TabLayout` 底部 `HorizontalDivider`，可选）。

**验收标准（AC-04）**：视觉上为"扁平文字标签 + 底部指示条"（非卡片）；选中/非选中/脏三种状态可辨识；深浅主题下 `primary` 指示条对比度满足 M3 语义（对照 §10 TC-12/TC-14/TC-15）。

---

### WP-005: Tab Close Menu (P1)

> **（已完成）2026-08-05**：`EditorComponents.kt:112` 点击当前标签弹 `DropdownMenu`（Close / Close Others / Close All）落地；`EditorSession.kt` 配套 `closeOthers/closeAll`。验收 AC-05 通过（源码核对）。

| 属性 | 内容 |
|------|------|
| 目标 | 修复 G4：点击**当前选中**标签弹出关闭菜单（关闭 / 关闭其他 / 关闭全部） |
| 文件 | `editor/.../EditorTabs.kt`；`app/.../session/EditorSession.kt`；`app/.../ui/screen/EditorScreen.kt`；`app/src/main/res/values/strings.xml` + `values-zh/strings.xml` |
| 依赖 | WP-003 |
| 工时 | 0.5 天 |

**实现要点**

- `EditorTabItem` 点击语义对齐 Squircle：**非选中 → 切换**；**选中 → 弹菜单**（`EditorTabs` 增加 `onTabMenu: (String) -> Unit` 回调，菜单由宿主弹出——菜单内容涉及"关闭其他/全部"的会话级操作，属宿主职责；`:editor` 只上报事件，保持 R3 边界）。备选：菜单内置于 `:editor`，回调 `onCloseOthers/onCloseAll` 到宿主——两种方案均可，**推荐后者**（菜单 UI 属展示层，`:editor` 持有，事件外抛），实现时二选一并保持参数默认值兼容。
- 菜单：M3 官方 `DropdownMenu` + `DropdownMenuItem`，三项：关闭（`menu_close_tab`）/ 关闭其他（`menu_close_others`）/ 关闭全部（`menu_close_all`）。
- 脏文件语义（**产品决策点，默认方案**）：关闭/关闭其他/关闭全部对脏文件**逐个复用现有确认流程**（`pendingCloseFile` + `AlertDialog`，EditorScreen.kt:270-302），确认后继续执行剩余操作；不静默丢弃、不静默保存。需在 `EditorSession` 增加 `closeOthers(anchor)` / `closeAll()`（内部过滤脏文件，返回待确认队列，宿主逐个驱动 `saveAndClose`/`discardAndClose`）。
- 新增字符串（**必须中英双语，禁硬编码**）：

| Key | en | zh |
|-----|----|----|
| `menu_close_tab` | Close | 关闭 |
| `menu_close_others` | Close Others | 关闭其他 |
| `menu_close_all` | Close All | 关闭全部 |

**验收标准（AC-05）**：点击当前标签弹菜单且不切换标签；三项操作行为正确；脏文件逐个确认、不丢数据；全部标签关闭后编辑区回到空态（`editor_no_file`）（对照 §10 TC-07）。

---

### WP-006: New-Tab Auto-Scroll (P1)

> **（已完成）2026-08-05**：`EditorTabs.kt` 追加标签后 `animateScrollToItem(size-1)` 落地（计数增幅判定）。验收 AC-06 通过（源码核对）。

| 属性 | 内容 |
|------|------|
| 目标 | 修复 G5：新标签追加后自动滚动到末尾（对齐 Squircle 追加事件语义） |
| 文件 | `editor/.../EditorTabs.kt` |
| 依赖 | WP-003（需 `LazyListState`） |
| 工时 | 0.5 天 |

**实现要点**

```kotlin
val previousCount = remember { mutableIntStateOf(tabs.size) }
LaunchedEffect(tabs.size) {
    if (tabs.size > previousCount.intValue) {           // 仅"追加"滚动，关闭/重排不触发
        state.animateScrollToItem(tabs.size - 1)
    }
    previousCount.intValue = tabs.size
}
```

- 用计数增幅判定追加（lide 无 Squircle 的事件总线，计数判定成本最低且无副作用）；重排导致的数量不变场景不触发。
- 注意 `LaunchedEffect(tabs.size)` 在数量不变的重排时不重启，天然满足"重排不滚动"。

**验收标准（AC-06）**：文件树打开标签（追加）后标签行自动滚动到新标签；关闭/拖拽不触发滚动；标签少于一屏时不产生可见动画（对照 §10 TC-08）。

---

### WP-007: Drawer Gesture Alignment (P1)

> **（被取代）2026-08-05**：同 WP-001，抽屉删除后手势对齐目标不复存在；本 WP 不再实施。验收 AC-07 标注 N/A。

| 属性 | 内容 |
|------|------|
| 目标 | 修复 G8：抽屉打开状态下支持拖拽关闭（经典抽屉手势）；不引入边缘滑开（避免与编辑器手势冲突） |
| 文件 | `app/.../ui/screen/EditorScreen.kt`（129-132 行） |
| 依赖 | 无 |
| 工时 | 0.5 天 |

**实现要点**

```kotlin
ModalNavigationDrawer(
    drawerState = drawerState,
    gesturesEnabled = drawerState.isOpen,   // 对齐 Squircle：drawerGesturesEnabled = drawerState.isOpen
    ...
)
```

- `gesturesEnabled` 随 `drawerState.isOpen` 动态变化是 M3 官方参数的标准用法（Squircle 实证同款表达式），`anchoredDraggable` 在 enabled 翻转时自动重新挂接，无需额外处理。
- 注释更新：原"仅工具栏汉堡键开合"注释同步改为"打开状态可拖拽关闭；不启用边缘滑开"。

**验收标准（AC-07）**：抽屉打开时，抽屉面板内横向拖拽可关闭；编辑器内无抽屉手势干扰（边缘滑开不触发）；scrim 点击关闭保持可用（对照 §10 TC-09）。

---

### WP-008: Hamburger Toggle Semantics (P2)

> **（被取代）2026-08-05**：同 WP-001，汉堡键随抽屉一并移除（`EditorComponents.kt:112`）；本 WP 不再实施。验收 AC-08 标注 N/A。

| 属性 | 内容 |
|------|------|
| 目标 | 修复 G9：汉堡键开合切换（对齐 Squircle toggle 语义） |
| 文件 | `app/.../ui/screen/EditorScreen.kt`（177 行） |
| 依赖 | 无 |
| 工时 | 0.25 天 |

**实现要点**

```kotlin
onToggleDrawer = { scope.launch { if (drawerState.isOpen) drawerState.close() else drawerState.open() } }
```

（参数名 `onToggleDrawer` 语义本就为 toggle，补齐实现即可。）

**验收标准（AC-08）**：汉堡键在开/关两态间切换；与 WP-001 的 BackHandler 无冲突（对照 §10 TC-10）。

---

### WP-009: File Tree Horizontal Scroll (P2)

| 属性 | 内容 |
|------|------|
| 目标 | 修复 G10：长路径支持横向滚动（对齐 Squircle 最小宽 248dp 模式） |
| 文件 | `editor/.../ProjectFileTree.kt`（81-98 行、100-159 行） |
| 依赖 | 无 |
| 工时 | 0.5 天 |

**实现要点**

- `LazyColumn(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()))`——与 Squircle `FileExplorer.kt` 同构。
- 条目 `Modifier.widthIn(min = 248.dp)`（替换现有 `Text` 的省略号兜底为"宽条目 + 水平滚动"，ellipsis 保留作最后防线）。
- 缩进逻辑不变（`12 + depth * 14` dp）。
- 风险注意：抽屉打开时，树内横向滚动与抽屉拖拽关闭同为水平手势——Squircle 同款共存已验证；实测若冲突，以"树内滚动优先"为目标（滚动消费事件后抽屉不响应），见 §11 R-03。

**验收标准（AC-09）**：超长文件名/路径可横向滚动查看全文；树内横向滚动不与抽屉拖拽关闭互相干扰（对照 §10 TC-11/TC-12）。

---

### WP-010: Version Catalog (P2 — Optional)

| 属性 | 内容 |
|------|------|
| 目标 | 对齐 Squircle 的版本目录管理（`gradle/libs.versions.toml`），为后续依赖治理打底 |
| 文件 | 根 `gradle/libs.versions.toml`（新建）；`app/build.gradle.kts`；`editor/build.gradle.kts`；`androlua/build.gradle.kts`（如适用） |
| 依赖 | 无 |
| 工时 | 0.5 天 |

**实现要点**

- 仅迁移**现有版本号**（compose-bom、material3、navigation、activity、lifecycle、core-ktx、sora、reorderable 等），禁止顺手升级任何依赖（本次范围不含升级，风险最小化）。
- 迁移后必须执行完整构建验证（§8 G-01/G-02）。

**验收标准（AC-10）**：`libs.versions.toml` 中所有版本与迁移前解析结果一致（`gradlew :app:dependencies` 比对）；全模块编译通过。

---

## 6. Implementation Order & Milestones

| 里程碑 | 内容 | WP | 依赖 | 工期 | 退出条件 |
|--------|------|----|------|------|----------|
| M1 | 抽屉缺陷修复（返回键 + 竞态） | 001, 002 | — | 1 天 | AC-01/AC-02 通过 |
| M2 | Tab 拖拽排序 | 003 | M1（同屏回归便利） | 1 天 | AC-03 通过 |
| M3 | Tab 样式 + 关闭菜单 + 新标签滚动 | 004, 005, 006 | M2 | 1 天 | AC-04/05/06 通过 |
| M4 | 抽屉手势 + toggle + 树横向滚动 | 007, 008, 009 | M1 | 1 天 | AC-07/08/09 通过 |
| M5 | 全量回归 + 质量门禁 | — | M1–M4 | 0.5 天 | §8 全部门禁 + TC 全绿 |

- WP-004/005/006 与 WP-003 改动同一文件（`EditorTabs.kt`），**建议合并提交**（同一功能面，降低冲突成本）；如分开提交，顺序固定为 003 → 004 → 005 → 006。
- 每个 WP 独立提交，提交信息遵循仓库既有风格（`feat:`/`fix:` 前缀，见 `git log`）。
- 提交前必须通过 §8 门禁 G-01（编译）与 G-03（架构边界）。

---

## 7. Engineering Conventions & Notices

> 本节为评审硬门槛（对照 AGENTS.md），违反视为严重缺陷。

### N-01 架构边界（R1/R3/R4）

- `:editor`（`com.lioisme.lide.editor`）不得出现 `com.androlua`、`com.luajava`、`System.loadLibrary`、jniLibs；`editor/build.gradle.kts` 不得依赖 `:androlua`。
- `:editor` 不得读取 `:app` 的任何状态/类型。拖拽排序、关闭菜单、文件树的**数据流一律为：`:editor` 事件回调 → `:app` 宿主（`EditorScreen`/`EditorSession`）→ 状态回流**。
- 新增依赖 `sh.calvin.reorderable` 为纯 Compose 库（POM 实证无 Android 平台绑定之外的产物），不破坏边界。
- 抽屉 `BackHandler`、`drawerState`、会话操作属宿主专属逻辑，放 `:app`。

### N-02 线程与协程模型

- 抽屉/标签/会话状态变更**仅主线程**（现有约定，`EditorSession` 内部注释明确）；`moveTab`/`closeOthers`/`closeAll` 不得在 IO 线程调用。
- `openFileFromTree` 改为双协程并行后，两个协程均挂靠在 `rememberCoroutineScope()`；**禁止**引入 `GlobalScope` 或跨组合逃逸的协程。
- 自动保存节流（`editTick` → 500ms 防抖）与 `Content` 缓存（`cacheLock`）路径**不得触碰**。

### N-03 性能与重组边界

- Tab 重排只允许引发 `EditorTabs` 子树重组；`LuaEditor`（sora AndroidView）为兄弟节点，禁止被标签操作卷入重组。验证：拖动标签时编辑器不闪断、不重建（TC-05）。
- `LazyRow` 与 `ReorderableItem` 键保持 `fileName` 稳定，禁止用 index 作键。
- 大文件（>200 KB）路径（关闭括号高亮等）与本次改动无交集；回归确认无退化（TC-13）。
- 补全/诊断/事件回调的 `try/catch(Throwable)` 降级约定不变。

### N-04 组件合规（禁止手搓自研）

- 只允许：官方组件（M3 `ModalNavigationDrawer`/`ModalDrawerSheet`/`DropdownMenu`/`IconButton`/`TopAppBar`、foundation `LazyRow`/`LazyColumn`/`horizontalScroll`、activity-compose `BackHandler`、ui `HapticFeedbackType`）+ `sh.calvin.reorderable`（唯一新增第三方）。
- **禁止**复制 Squircle `:core-ui` 的任何实现代码（`TabItem`/`TabIndicator`/`DrawerSuite`/`ScaffoldSuite`/DS `IconButton` 等）——仅借鉴行为与视觉规范，实现一律官方组件。
- 新增任何组件前先问：官方组件能否覆盖？不能时，优先选成熟第三方库；仍不能，才允许最小化自研（如标签条目本身），且必须说明理由于 PR 描述。

### N-05 无障碍（a11y）

- 关闭按钮保留 `contentDescription`（`cd_close_tab`）；关闭菜单三项用字符串资源。
- 标签条目使用 `Role.Tab` 语义（`selectable`）——对齐 Squircle 并让 TalkBack 正确朗读。
- 指示条为纯装饰，不添加语义节点；`"• "` 脏前缀并入标题文本朗读，不单列。

### N-06 国际化（i18n）

- 全部新增文案入 `app/src/main/res/values/strings.xml` 与 `values-zh/strings.xml`，禁止硬编码（现有代码零硬编码，保持）。
- 新字符串清单见 WP-005 表格。

### N-07 版本与兼容性

- `sh.calvin.reorderable:reorderable:3.0.0` 固定版本；不得使用 `+`。兼容性实证见 §14.3。
- 不动 BOM/AGP/Kotlin 版本（WP-010 迁移版本目录时仅搬移不升级）。
- minSdk 24：不得使用 API 30+ 专属 API（本次改动无平台 API 需求）。
- API 34+ 预测性返回：WP-001 的条件式 `BackHandler` 与 navigation-compose 2.8.5 协作正常（TC-16 实测覆盖）。

### N-08 仓库卫生

- 每次提交后跑 §8 门禁；`docs/` 审计报告类文件不随功能提交（除非显式要求）。
- 不修改 `reference/`（gitignored 参照物）。

---

## 8. Quality Gates

| 门禁 | 命令/方式 | 通过标准 | 触发时机 |
|------|-----------|----------|----------|
| G-01 | `.\gradlew.bat :app:compileArm64DebugKotlin --console=plain` | 零错误零警告（新增代码） | 每个 WP 提交前 |
| G-02 | `.\gradlew.bat :app:assembleArm64Debug --console=plain` | 完整构建通过 | M5 及 WP-010 |
| G-03 | `rg -n "luajava\|androlua\|loadLibrary" editor/src` | **零命中**（AGENTS.md 硬门槛） | 每个 WP 提交前 |
| G-04 | 字符串核对 | `values/` 与 `values-zh/` 新键一一对应 | M3 |
| G-05 | 手工回归 | §10 TC-01…TC-16 全绿（P0 用例必须 100%） | M5 |
| G-06 | 代码评审 | 对照 N-01…N-08 逐条自查清单勾选 | 每提交 |

---

## 9. Acceptance Criteria — Traceability Matrix

| AC | 验收标准 | 来源 | 验证 | 状态 |
|----|----------|------|------|------|
| AC-01 | 抽屉打开时系统返回键先关抽屉；再按才退出 | WP-001 / E1 | TC-01/02/03 | N/A（被取代，2026-08-05） |
| AC-02 | 打开文件时抽屉立即关闭；无"IO 期间敞开"窗口；失败不影响抽屉 | WP-002 / E3 | TC-03/04 | N/A（被取代，2026-08-05） |
| AC-03 | 长按拖拽重排；选中/脏标记跟随；不重建编辑器；键无冲突 | WP-003 / G3 | TC-05/06 | ✅ 已完成（源码核对，2026-08-05） |
| AC-04 | 标签为扁平样式 + 底部指示条 + `•` 脏前缀；深浅主题可辨识 | WP-004 / G1/G2 | TC-14/15 | ✅ 已完成（源码核对，2026-08-05） |
| AC-05 | 点击当前标签弹关闭菜单；关闭/其他/全部行为正确；脏文件确认不丢数据 | WP-005 / G4 | TC-07 | ✅ 已完成（源码核对，2026-08-05） |
| AC-06 | 新标签自动滚到末尾；关闭/重排不滚动；一屏内无动画 | WP-006 / G5 | TC-08 | ✅ 已完成（源码核对，2026-08-05） |
| AC-07 | 打开状态可拖拽关闭；边缘滑开不触发；scrim 点击关闭可用 | WP-007 / G8 | TC-09 | N/A（被取代，2026-08-05） |
| AC-08 | 汉堡键开合切换 | WP-008 / G9 | TC-10 | N/A（被取代，2026-08-05） |
| AC-09 | 树长路径可横向滚动；与抽屉关闭手势无干扰 | WP-009 / G10 | TC-11/12 | ⬜ 未完成 |
| AC-10 | 版本目录迁移零版本漂移；全模块编译通过 | WP-010（可选） | G-02 | ⬜ 未完成（可选） |
| AC-11 | 架构边界零违规（G-03）；`:editor` 无宿主依赖 | 全局 | G-03 | ⬜ |
| AC-12 | 新增文案中英双语齐全、无硬编码 | 全局 | G-04 | ⬜ |
| AC-13 | 大文件（>200 KB）编辑路径无回归 | 全局 | TC-13 | ⬜ |

---

## 10. Manual Regression Test Plan

> 执行环境：Arm64 真机（API 24 基线）1 台 + API 34+ 真机 1 台；深浅主题各跑一轮（TC-14/15 相关）。每例记录：通过 / 失败 / 阻塞（附 logcat 关键行）。

| TC | 步骤 | 预期 | 关联 |
|----|------|------|------|
| TC-01 | 编辑器 → 打开抽屉 → 按系统返回键 | 抽屉关闭，编辑器停留；抽屉不再自动打开 | AC-01 |
| TC-02 | TC-01 后再按返回键 | 返回主界面（项目列表） | AC-01 |
| TC-03 | 打开抽屉 → 点击树中**大文件**（IO 窗口内）→ 立即连按返回 | 抽屉关闭、编辑器停留（不弹出）；文件正常打开 | AC-01/02 |
| TC-04 | 打开抽屉 → 点击文件 → 返回依赖项页 → 返回编辑器 | 抽屉保持关闭；标签/脏标记状态完整 | AC-02 |
| TC-05 | 打开 5+ 标签 → 长按拖拽重排（含跨屏） | 排序生效；拖起条目 alpha 0.5；无键冲突；编辑区无重建（无闪烁） | AC-03 |
| TC-06 | 脏文件标签 → 拖拽重排 → 查看脏前缀/圆点 | 脏标记跟随移动；选中跟随 | AC-03 |
| TC-07 | 点击当前选中标签 → 菜单 → 分别执行关闭/关闭其他/关闭全部（含脏文件场景） | 菜单弹出不切换；脏文件逐个确认；不丢数据；全关后空态文案 | AC-05 |
| TC-08 | 文件树连续打开标签至溢出屏宽 | 新标签自动滚动可见 | AC-06 |
| TC-09 | 打开抽屉 → 面板内横向拖拽 → 关闭；编辑器内从屏幕左缘右滑 | 前者关闭；后者不触发抽屉 | AC-07 |
| TC-10 | 汉堡键连点 3 次 | 开→关→开，与状态同步 | AC-08 |
| TC-11 | 树内长路径目录/文件 → 横向滚动 | 完整可见；滚动与抽屉关闭互不干扰 | AC-09 |
| TC-12 | 超长文件名（>40 字符） | 横向滚动可读全文 | AC-09 |
| TC-13 | 打开 >200 KB 的 .lua，编辑 + 拖动标签 + 开关抽屉 | 无卡顿无崩溃；诊断/高亮按大文件规则跳过 | AC-13 |
| TC-14 | TalkBack 开启 → 聚焦标签/关闭按钮/菜单项 | 朗读角色（Tab）、关闭描述、菜单文本正确 | AC-04/05 |
| TC-15 | 深/浅主题切换 → 查看指示条/选中态/树选中色 | M3 语义色正确，对比可辨 | AC-04 |
| TC-16 | API 34+ 真机：抽屉打开 → 预测性返回手势 | 先关抽屉；手势动画正常；不误 pop | AC-01 |

**失败处置**：P0 用例（TC-01/03）失败即阻塞 M1 交付；其余失败按 AC 矩阵回退对应 WP 修复后复测；TC 全绿且 G-01/G-03 通过方可合入。

---

## 11. Risk Register

| # | 风险 | 等级 | 概率 | 影响 | 缓解措施 |
|----|------|------|------|------|----------|
| R-01 | `reorderable:3.0.0` 与 Compose 1.7.3 存在未预期 ABI 冲突 | 中 | 低 | M2 延期 | §14.3 POM 实证兼容；M2 首日编译验证（G-01）；若失败可退回 `reorderable:2.0.0`（API 不变）并在 WP-003 内说明 |
| R-02 | API 34+ 预测性返回与条件 BackHandler 交互异常 | 中 | 低 | 返回行为回归 | 条件式 `enabled = isOpen`（非无条件分支）；TC-16 专项覆盖；必要时以 `PredictiveBackHandler` 细化 |
| R-03 | 树内横向滚动与抽屉拖拽关闭手势竞争 | 低 | 中 | 抽屉难关闭 | 对齐 Squircle 同款共存方案（已被其生产验证）；TC-11 实测；冲突时以滚动优先并增大抽屉拖拽锚定区域 |
| R-04 | 关闭其他/全部的脏文件确认流引入状态错乱（确认弹窗期间用户再次操作） | 中 | 中 | 数据丢失风险 | 确认期间复用现有 `pendingCloseFile` 单点驱动（一次仅一个待确认项）；TC-07 多轮回归 |
| R-05 | 样式对齐回归现有视觉（无基线快照） | 低 | 中 | 评审分歧 | 以 §4 参照表为唯一视觉依据；TC-14/15 覆盖对比度；PR 附 before/after 截图 |
| R-06 | WP-010 版本目录迁移意外变更解析版本 | 中 | 低 | 全量回归 | 只搬移不升级；迁移前后 `dependencies` 输出 diff；G-02 兜底 |
| R-07 | `EditorTabs` API 变更破坏既有调用方 | 低 | 低 | 编译失败 | 全部新增参数带默认值（`onTabMoved`、`state` 默认 remember）；G-01 覆盖 |

---

## 12. Rollback Plan

- 无数据库/无迁移/无资源格式变更，回滚粒度 = 提交粒度。
- 每个 WP 独立提交；出现回归时 `git revert <wp-commit>` 即可，R-01 发生时追加移除 `editor/build.gradle.kts` 中的 reorderable 依赖行。
- 回滚不影响既有功能路径：M2/M3 提交前，`EditorTabs` 新参数均为默认值路径，revert 后行为即恢复基线。
- 全部 P0 用例（TC-01/02/03）为合入前置，若 M5 回归出现 P0 失败且 2 小时内无法定位，按 M1 → M2 → M3 → M4 逆序回退并重新排队。

---

## 13. Definition of Done

- [ ] WP-001…WP-010 按 §6 里程碑完成，验收标准 AC-01…AC-13 全部通过（WP-010 为可选，未实施时 AC-10 标注 N/A）。
- [ ] G-01/G-02 编译与完整构建通过；G-03 架构边界零命中。
- [ ] TC-01…TC-16 手工回归全绿（双真机、双主题）。
- [ ] 新增文案中英双语齐全（G-04）。
- [ ] 代码评审按 N-01…N-08 自查清单通过，PR 描述含：改动文件清单、视觉对齐依据（§4 参照表）、自测结论。
- [ ] 仓库文档如需更新（PROGRESS.md 变更记录），随 M5 提交（显式要求时）。

---

## 14. Appendix — Audit Evidence

### 14.1 E1 — M3 1.3.0 `ModalNavigationDrawer` 无内部 BackHandler

对 `material3-android:1.3.0`（lide BOM 2024.09.00 实际解析版本）classes.jar 反编译检索：

```
NavigationDrawerKt$ModalNavigationDrawer$1$1 … $3 — 全部 12 个内部类：BackHandler 零命中
```

结论：M3 1.3.0 抽屉**不包含**关闭抽屉的返回键处理；应用层必须自备（Squircle 即如此，EditorScreen.kt:237-241）。

### 14.2 E2 — `rememberDrawerState` 为 saveable 实现

反编译实证存在 `DrawerState$Companion$Saver`；官方实现即 `rememberSaveable(saver = DrawerState.Saver(...))`。navigation-compose 2.8.5 按 back-stack entry 以 `SaveableStateHolder` 保存/恢复——抽屉 Open 值在组合销毁时落盘，同 entry 恢复时回放为打开。

### 14.3 reorderable 3.0.0 兼容性

POM 实证（`repo1.maven.org/.../reorderable-3.0.0.pom`）：依赖为 Jetpack/Compose Multiplatform 1.7.0（runtime/animation/foundation）与 kotlin-stdlib-common 1.9.0。lide BOM 2024.09.00 → Compose 1.7.3、Kotlin 2.2.20：二进制兼容；Squircle-CE 生产环境同库同版本（其 libs.versions.toml `reorderable = "3.0.0"`）。

### 14.4 Squircle 关键参照行号

| 参照点 | 文件 : 行 |
|--------|-----------|
| 抽屉 BackHandler | feature-editor/.../EditorScreen.kt : 237-241 |
| 汉堡 toggle | 同文件 : 103-108 |
| 拖拽手势开关 | 同文件 : 357 |
| 拖拽实现 | feature-editor/.../compose/DocumentTabLayout.kt : 50-82 |
| 标签样式/脏前缀/选中菜单 | feature-editor/.../compose/DocumentTab.kt : 57-88 |
| Tab 条目/指示条 | core-ui/.../ds/tabs/TabItem.kt、TabIndicator.kt |
| 树横向滚动/最小宽 | feature-explorer/.../compose/FileExplorer.kt |
