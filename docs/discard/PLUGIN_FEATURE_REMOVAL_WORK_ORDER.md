# 插件功能移除 + 编辑器菜单治理工作单（LIDE-WO-2026-020）

> **状态**：（已完成 2026-08-07，已归档 docs/discard/） · **日期**：2026-08-07
> **目标**：按用户 3 项要求：① 移除 androlua 插件功能及其文件/代码；② 编辑器标签卡移除 ⋮ 菜单按钮
> （长按松手即可调出菜单，功能重复）；③ 编辑器右上角「更多」菜单改为多级菜单（分类 → 子菜单）。
> **验收**：`:app:assembleArm64Debug` BUILD SUCCESSFUL；`git ls-files` 门禁零命中；
> 全量单测绿；`:editor` 主源 ktlint 通过；R1 门禁零命中。

---

## 背景

D-19（2026-08-07）取消 Shadow 插件平台时明确保留 `PluginScreen`+`PluginManager`
（pro 兼容目录式 Lua 插件）；用户进一步要求连 pro 兼容插件功能一并移除（本工作单
**部分反转 D-19 的保留条款**）。同时提出两处编辑器 UI 治理：标签卡 ⋮ 按钮与长按松手
弹菜单功能重复（C-3 曾反向演进：先有长按松手菜单，C-3 改为 ⋮ 常驻按钮，本次回退并
删除按钮）；右上角「更多」菜单需多级化（现为平铺 + 分类标题）。

## 用户要求 → 实现映射（3 项）

1. **移除 androlua 插件功能及文件/代码**：
   - 删除 `app/src/main/assets/plugin/plugin.json`、`ui/screen/PluginScreen.kt`、
     `:androlua` `PluginManager.kt`（`PluginInfo` 同删）；
   - `LideApp.extractExamplePlugin` 删除（示例插件不再提取）；
   - `MainActivity`：删 `pendingPluginImport` 状态与 .alp 插件分流（一律按工程导入）；
   - `LideNavHost`：删 `Routes.PLUGINS` 路由与导航接线、`pendingPluginImport` 参数；
   - `AndroLuaSettingsScreen`：删「插件」入口（页面只剩 API 浏览器 / 示例）；
   - `ProjectListScreen`：导入计数收敛为纯工程计数（删插件计数分支）；
   - `AlpManager`/`AlpNaming`：删 `AlpKind.PLUGIN`/`resolveKind`，.alp 一律导入
     `project/`（`AlpImportResult` 去 kind）；`AlpNamingTest` 删插件判定用例；
   - `ApkBuilder.HOST_ASSET_EXCLUDED` 删 `assets/plugin/`；
   - 字符串（en/zh）：删全部 `plugin_*`/`plugins*`/`import_plugin_success`/
     `import_mixed_success`；`alp_conflict_message` 文案去掉「或插件」。
2. **标签卡移除 ⋮ 菜单按钮，长按松手弹菜单**（`editor/EditorTabs.kt`）：
   - 删除常驻 `IconButton(⋮)` 与对应 DropdownMenu 入口；
   - 恢复「位移观察协程 + `onDragStopped`」手势：长按未超 touchSlop 位移直接松手
     → 打开关闭菜单（关闭 / 关闭其他 / 关闭全部）；有位移 → 拖拽排序不变；
   - KDoc 同步（含 C-3 反向演进注记）。
3. **右上角「更多」菜单多级化**（`editor/EditorToolbar.kt`）：
   - 按 `EditorMoreAction.category` 分组：一级 = 分类项（带
     `KeyboardArrowRight` 展开箭头），二级 = 功能项子菜单；
   - `groupByCategory` 保持出现顺序、null 并入上一组；删除平铺分类标题
     `MoreMenuCategoryHeader`；点击一级项只展开子菜单，点二级项后关闭根菜单并执行。
   - 宿主（`EditorScreen`/`EditorTextOps`）数据模型与接线不变。

## 实施内容（（已完成）2026-08-07）

1. **插件功能移除**：删 3 文件 + `plugin.json` 资产；`LideApp`/`MainActivity`/
   `LideNavHost`/`AndroLuaSettingsScreen`/`ProjectListScreen`/`ApkBuilder`/
   `AlpManager`/`AlpNaming`/`AlpImportCoordinator`(KDoc 三屏→两屏)/`AlpNamingTest`
   全部收口；strings en/zh 清理 22 条 + `alp_conflict_message` 文案修正。
2. **标签卡手势回退**：恢复 `pointerInput` 只读位移观察（`awaitEachGesture` +
   `LocalViewConfiguration.touchSlop`）+ `longPressDraggableHandle.onDragStopped`
   弹菜单；删除 ⋮ 按钮（含 `MoreVert`/`IconButton`/`Icon` 相关导入）。
3. **更多菜单多级化**：`MoreSubmenuItem`（Box 包裹一级项 + 嵌套 DropdownMenu），
   `groupByCategory` 分组；`Icons.AutoMirrored.Filled.KeyboardArrowRight` 箭头。

## 架构合规

- 插件移除全部位于 `:app` 与 `:androlua`（.alp 导入属宿主/运行时面），`:editor`
  零改动插件逻辑（R1~R5 满足）；`:editor` 仅做 UI 手势与菜单结构改动，纯 Compose。
- 标签卡菜单仍走 `EditorTabs` 回调上报宿主（关闭/关闭其他/关闭全部），会话语义不变。
- `AlpManager` 删 kind 后 `ImportPlan`/`AlpImportResult` 收窄，`AlpImportCoordinator`
  接口不变（两屏共用）。

## 验收证据

- `.\gradlew.bat :app:compileArm64DebugKotlin` → **BUILD SUCCESSFUL**（0 报错，无新增告警）
- `.\gradlew.bat :app:assembleArm64Debug` → **BUILD SUCCESSFUL**
- 单测：`:androlua:testDebugUnitTest` / `:app:testArm64DebugUnitTest` /
  `:editor:testDebugUnitTest` → 全部 **BUILD SUCCESSFUL**（AlpNamingTest 插件用例已随
  功能删除，其余用例不改语义）
- `:editor:runKtlintCheckOverMainSourceSet` → **BUILD SUCCESSFUL**（改动文件零违规；
  `ktlintTestSourceSetCheck` 既有失败属 `LanguageCatalogConsistencyTest.kt`（aaa23d5 引入，
  本次未触碰，另行治理）
- R1 门禁：`editor/src` 无 `luajava|androlua|loadLibrary` 命中；`git ls-files`
  `*.so|*.dex|*.zip` 与 `*.jar`（除 wrapper）零命中
- 行为复核（代码级）：`.alp(mode=plugin)` 现导入 `project/`；工程列表导入提示仅工程计数；
  AndroLua 页仅 API 浏览器/示例；标签卡无 ⋮ 按钮、长按松手弹关闭菜单；更多菜单三级两层。

## 遗留

- 多级子菜单定位（嵌套 Popup 在根菜单右下展开）与标签卡长按松手菜单体验，待模拟器 UI
  回归确认（并入既有综合复测批次）。
- 用户设备上既有的 `luaExtDir/plugin/` 目录不再被任何界面扫描（不自动删除，用户自行处置）。
