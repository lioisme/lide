# 编辑器侧边栏抽屉：审计与修复实施报告

> 状态：**全部阶段实施完成**（2026-08-01，编译 + R1 门禁 + 完整构建通过）
> 范围：`app/.../ui/screen/EditorScreen.kt`（抽屉全部逻辑）；`:editor` 仅涉及 `EditorToolbar.kt` 参数化（零宿主依赖）
> 组件约束：仅使用官方 material3 / androidx 组件与维护良好的流行库，禁止手搓自研 UI 组件

## 一、结论摘要（审计结论）

| 编号 | 问题 | 严重度 | 根因 |
|---|---|---|---|
| P0-1 | 抽屉打开时覆盖整个屏幕 | 高 | 使用 Material3 Modal（模态）变体且包住整个 Scaffold：全屏 scrim + 抽屉满高覆盖顶栏/状态栏 |
| P0-2 | 退出编辑器时抽屉先展开再退出 | 高 | ① 退出路径未先关抽屉；② `rememberDrawerState` 的 saveable 复活链（转屏/进程重建恢复 Open）；③ 防御性 LaunchedEffect 补丁制造可见「展开→收起」闪烁 |
| P1 | 系统返回键在关闭动画窗口期吞键 | 中 | 手写 `BackHandler` 替代官方内置返回处理（M3 1.3.0 已提供带 `drawerState` 的新重载） |

组件选型合规（全部官方组件），缺陷为变体误用 + 状态滥用 + 退出路径缺关闭。

## 二、证据（源码级核对）

以下均从官方 `material3-1.3.0-sources.jar`（Google Maven）逐行核对：

1. `ModalNavigationDrawer`（NavigationDrawer.kt:314）：`Box(fillMaxSize)` = content + 全屏 `Scrim` + 抽屉 `Layout`（offset 动画）覆盖最上层。
2. `ModalDrawerSheet → DrawerSheet`（NavigationDrawer.kt:773-777）：`sizeIn(minWidth=240.dp, maxWidth=360.dp).fillMaxHeight()` —— 抽屉满高、宽度上限 360dp。
3. `rememberDrawerState`（NavigationDrawer.kt:283-290）= `rememberSaveable(saver = DrawerState.Saver)`，Saver 保存 `currentValue` —— 转屏/进程重建后抽屉复活为 Open。
4. `ModalDrawerSheet(drawerState)` / `DismissibleDrawerSheet(drawerState)` 新重载（KDoc 原文）："will handle back by default for all Android versions, as well as animate during predictive back on Android 14+"；其 Android 实现为 `PredictiveBackHandler(enabled = <isOpen 相关>)`（字节码核实：`NavigationDrawer_androidKt$DrawerPredictiveBackHandler` 读取 `DrawerState.isOpen` 计算 enabled，调用 `androidx/activity/compose/PredictiveBackHandlerKt`）—— 抽屉关闭时不注册回调，返回自然落到 NavHost，与手写 BackHandler 同契约但为官方实现且带预测性返回动画。
5. `DismissibleNavigationDrawer`（NavigationDrawer.kt:432-526，1.3.0/1.4.0 一致）：打开时 `contentPlaceable.placeRelative(sheetWidth + offset)` —— 内容被物理右移 360dp，窄屏会推出屏外，**不适用于全屏编辑器**，故不采用。

## 三、修复方案（分级）

### P0-1 经典化布局（解决「覆盖整个屏幕」）
保持官方 `ModalNavigationDrawer`（模态语义即经典抽屉），**移入 Scaffold 内容区（顶栏之下）**：
- 顶栏（汉堡/返回/运行键）保持可见可用；
- 抽屉不再覆盖状态栏与顶栏；scrim 只遮内容区；
- 宽度保持官方默认 ≤360dp，经典「左侧栏 + 右侧内容条」外观；
- Scrim 自带点外关闭（官方内置）。

### P0-2 根除「先展开再退出」
1. `ModalDrawerSheet(drawerState)` 替换手写 `BackHandler`（官方内置返回 + Android 14+ 预测性动画，吞键竞争消失）；
2. 工具栏返回键改为**先关抽屉再退出**：`scope.launch { drawerState.close(); onBack() }`（`close()` 对已关闭状态立即返回，零延迟）；
3. `rememberDrawerState` → `remember { DrawerState(DrawerValue.Closed) }`，删除防御性 `LaunchedEffect` —— 根除 saveable 复活链与首帧「展开」闪烁（`DrawerState` 裸构造仍为官方 API）。

### P1 宽屏适配（大厂验收标准）
`material3-window-size-class`（BOM 2024.09.00 已收录）→ 宽屏（Expanded ≥840dp）用官方 `PermanentNavigationDrawer` 常驻并排，窄屏保持 Modal 行为；宽屏隐藏工具栏汉堡键。

## 四、实施进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| Phase 0 | 建立本审计/进度文档 | ✅ 文档落地 |
| Phase 1 | 状态根除：`remember { DrawerState(Closed) }` 替换 saveable；删除防御性 `LaunchedEffect(Unit){ close() }` | ✅ 已完成：`EditorScreen.kt` 抽屉状态改为非 saveable 的官方 `DrawerState` 裸构造，删除 98-100 行补丁及其注释；转屏/进程重建不再复活 Open，首帧「展开→收起」闪烁根除 |
| Phase 2 | 经典布局：`ModalNavigationDrawer` 移入 Scaffold 内容区（顶栏之下），抽取 `DrawerContent` | ✅ 已完成：抽屉从包裹整页改为内嵌 Scaffold content；顶栏常驻可见；抽屉不再覆盖状态栏/顶栏；宽度保持官方默认 ≤360dp |
| Phase 3 | 官方返回处理：`ModalDrawerSheet(drawerState)` 替换手写 `BackHandler` | ✅ 已完成：删除 `EditorScreen.kt:138-140` 手写 BackHandler 与竞争说明注释，改用官方带 `drawerState` 的重载（内置返回 + Android 14+ 预测性返回动画） |
| Phase 4 | 退出先关抽屉：工具栏返回键 close→flush→back | ✅ 已完成：返回路径先 `drawerState.close()`（已关闭时零延迟）再异步兜底保存、再 popBackStack，杜绝「满屏抽屉随退出动画滑出」 |
| Phase 5 | 宽屏 Permanent 适配：window-size-class + 条件抽屉 + 汉堡键隐藏 | ✅ 已完成：`material3-window-size-class`（BOM 管理）判定 Expanded ≥840dp 时启用官方 `PermanentNavigationDrawer` 常驻并排，窄屏回退 Modal；`EditorToolbar` 新增 `showDrawerToggle` 参数（默认 true，`:editor` 无新增依赖）；新增 `NavigationDrawerPanel`/`EditorBody` 私有组件拆分（官方组件薄封装，非手搓） |
| Phase 6 | 验证：编译 + R1 门禁 + 完整构建 | ✅ 已完成：`compileArm64DebugKotlin` 通过 → R1 门禁零命中 → `assembleArm64Debug` 通过（见下方验证清单） |

## 五、验证清单（全部通过）

- [x] `.\gradlew.bat :app:compileArm64DebugKotlin --console=plain` 零错误（`BUILD SUCCESSFUL`）
- [x] R1 门禁：`editor/src` 中 `luajava|androlua|loadLibrary` 零命中
- [x] `.\gradlew.bat :app:assembleArm64Debug --console=plain` 完整构建通过
- [ ] 真机回归（待设备验证）：转屏后抽屉必须保持关闭；抽屉开时系统返回先收起再退出（Android 14+ 预测性动画）；360dp 窄屏抽屉不再满屏；关闭动画期间快速连按返回不吞键；宽屏（≥840dp）抽屉常驻、汉堡键隐藏

## 六、实施备注

- 依赖变更：`app/build.gradle.kts` 新增 `androidx.compose.material3:material3-window-size-class`（BOM 2024.09.00 版本管理，无版本号硬编码）。
- `:editor` 变更仅 `EditorToolbar.kt`：新增 `showDrawerToggle`（默认 `true`，向后兼容）。
- 实施中发现并修正：material3 1.3.0 的 `WindowSizeClass` 属性名为 `widthSizeClass`（`windowWidthSizeClass` 为 1.4.0 别名）；工程 activity-compose 1.9.3 无 `LocalActivity`，改用官方 `calculateWindowSizeClass(Activity)` + Context 链解包宿主 Activity（确定性，零依赖变更）。
