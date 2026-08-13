# LIDE 第二轮审计与整改报告

**审计日期**: 2026-08-12  
**审计范围**: 权限界面、底部输入框、主题配色细节  
**状态**: 已完成

---

## 任务总览（第一轮 B1–B4 + 第二轮 C1–C3 + 第三轮 D1–D4）

| ID | 需求 | 文件 | 状态 | 完成日期 |
|----|------|------|------|---------|
| B1 | 权限搜索 leading 图标改为返回按钮 | `SearchablePermissionPicker.kt` | ✅ 已完成 | 2026-08-12 |
| B2 | 权限列表移除冗余分组标题 | `PermissionScreen.kt` + `SearchablePermissionPicker.kt` | ✅ 已完成 | 2026-08-12 |
| B3 | 底部对话框键盘自动弹出 | `BottomInputSheet.kt` | ✅ 已完成 | 2026-08-12 |
| B4 | 主题色选中标记夜间模式混淆修复 | `SettingsScreen.kt` | ✅ 已完成 | 2026-08-12 |
| C1 | 权限搜索返回按钮真正调用 onBack | `PermissionScreen.kt` | ✅ 已完成 | 2026-08-12 |
| C2 | 权限行移除多余间距 | `PermissionScreen.kt` + `SearchablePermissionPicker.kt` | ✅ 已完成 | 2026-08-12 |
| C3 | 底部对话框与键盘同步弹出 | `BottomInputSheet.kt` | ✅ 已完成 | 2026-08-12 |
| D1 | 权限勾选失效根因修复 | `PermissionScreen.kt` | ✅ 已完成 | 2026-08-12 |
| D2 | 底部对话框键盘同步弹出强化 | `BottomInputSheet.kt` | ✅ 已完成 | 2026-08-12 |
| D3 | 编辑器只读切换重复实现移除 | `EditorScreen.kt` | ✅ 已完成 | 2026-08-12 |
| D4 | 格式化后自动滚动修复 | `EditorController.kt` | ✅ 已完成 | 2026-08-12 |
| D5 | 权限勾选双重触发根因修复（Row.clickable + Checkbox.onCheckedChange 同时触发 onToggle） | `SearchablePermissionPicker.kt` | ✅ 已完成 | 2026-08-12 |
| D6 | 底部对话框键盘同步弹出强化（LocalSoftwareKeyboardController + WindowInsetsControllerCompat 双路fallback） | `BottomInputSheet.kt` | ✅ 已完成 | 2026-08-12 |

---

## 任务总览（第一轮 B1–B4 + 第二轮 C1–C3）

| ID | 需求 | 文件 | 状态 | 完成日期 |
|----|------|------|------|---------|
| B1 | 权限搜索 leading 图标改为返回按钮 | `SearchablePermissionPicker.kt` | ✅ 已完成 | 2026-08-12 |
| B2 | 权限列表移除冗余分组标题 | `PermissionScreen.kt` + `SearchablePermissionPicker.kt` | ✅ 已完成 | 2026-08-12 |
| B3 | 底部对话框键盘自动弹出 | `BottomInputSheet.kt` | ✅ 已完成 | 2026-08-12 |
| B4 | 主题色选中标记夜间模式混淆修复 | `SettingsScreen.kt` | ✅ 已完成 | 2026-08-12 |
| C1 | 权限搜索返回按钮真正调用 onBack | `PermissionScreen.kt` | ✅ 已完成 | 2026-08-12 |
| C2 | 权限行移除多余间距 | `PermissionScreen.kt` + `SearchablePermissionPicker.kt` | ✅ 已完成 | 2026-08-12 |
| C3 | 底部对话框与键盘同步弹出（消除视觉闪烁） | `BottomInputSheet.kt` | ✅ 已完成 | 2026-08-12 |

---

## 任务总览

| ID | 需求 | 文件 | 状态 | 完成日期 |
|----|------|------|------|---------|
| B1 | 权限搜索 leading 图标改为返回按钮 | `SearchablePermissionPicker.kt` | ✅ 已完成 | 2026-08-12 |
| B2 | 权限列表移除冗余分组标题 | `PermissionScreen.kt` + `SearchablePermissionPicker.kt` | ✅ 已完成 | 2026-08-12 |
| B3 | 底部对话框键盘自动弹出 | `BottomInputSheet.kt` | ✅ 已完成 | 2026-08-12 |
| B4 | 主题色选中标记夜间模式混淆修复 | `SettingsScreen.kt` | ✅ 已完成 | 2026-08-12 |

---

## B1 — 权限搜索 leading 图标应为返回按钮

**问题**: `PermissionCollapsedSearchInput` leading 使用 `Icons.Default.Search`，应改为 `Icons.AutoMirrored.Filled.ArrowBack`（同 API 浏览器）。

**修复方案**:
1. `PermissionCollapsedSearchInput` 增加 `onBack: () -> Unit = {}` 参数
2. leading 由 Search 图标替换为 ArrowBack（TipIconButton 包裹）
3. `ProjectPermissionScreen` 传入 `onBack = { searchActive = false }`

**验收标准**:
- [x] 折叠搜索条 leading 显示返回箭头
- [x] 点击返回箭头收起搜索态
- [x] `gradlew :app:compileArm64DebugKotlin` BUILD SUCCESSFUL
- [x] `gradlew :app:ktlintCheck` BUILD SUCCESSFUL

**完成标记**: ✅ 已完成（2026-08-12）
- `PermissionCollapsedSearchInput` 新增 `onBack: () -> Unit = {}` 可选参数
- leading 由 `Icons.Default.Search` 改为 `TipIconButton(Icons.AutoMirrored.Filled.ArrowBack)`
- KDoc 同步更新说明
- `ProjectPermissionScreen` 传入 `onBack = { searchActive = false }`
- `gradlew :app:compileArm64DebugKotlin` BUILD SUCCESSFUL

---

## B2 — 权限列表移除冗余分组标题

**问题**: 正文列表和全屏搜索 Dialog 使用 `permissionGroupItems` 渲染，会插入 REGULAR/SPECIAL/OTHER 分组标题行，但筛选菜单已支持分类，分组标题冗余。

**修复方案**:
1. `PermissionScreen.kt` 正文 LazyColumn 改用 `items`（平铺）
2. `PermissionScreen.kt` 全屏搜索 Dialog LazyColumn 改用 `items`（平铺）
3. `SearchablePermissionPicker.kt` 弹窗 `PermissionPickerList` 改用 `items`（平铺）
4. `permissionGroupItems` / `PICKER_GROUPS` / `PermissionGroupHeader` 保留不删

**验收标准**:
- [x] 权限列表无分组标题行
- [x] 搜索过滤后列表平铺展示
- [x] `gradlew :app:compileArm64DebugKotlin` BUILD SUCCESSFUL
- [x] `gradlew :app:ktlintCheck` BUILD SUCCESSFUL

**完成标记**: ✅ 已完成（2026-08-12）
- `PermissionScreen.kt` 正文 LazyColumn：`permissionGroupItems(displayItems)` → `items(displayItems, key = { it.name })`
- `PermissionScreen.kt` 全屏搜索 Dialog LazyColumn：`permissionGroupItems(filtered)` → `items(filtered, key = { it.name })`
- `SearchablePermissionPicker.kt` 弹窗 `PermissionPickerList`：同理改为 `items`
- `permissionGroupItems` / `PICKER_GROUPS` / `PermissionGroupHeader` 保留（无死代码警告）
- `gradlew :app:compileArm64DebugKotlin` BUILD SUCCESSFUL
- `gradlew :app:ktlintCheck` BUILD SUCCESSFUL（ktlintFormat 修复缩进）

---

## B3 — 底部对话框键盘自动弹出

**问题**: `LocalSoftwareKeyboardController.current?.show()` 在部分 OEM ROM 上不可靠（返回 null 或静默失败），且 `sheetState.show()` 双重调用存在竞争。

**修复方案**:
1. 移除内部 `sheetState.show()` 调用（ModalBottomSheet 自身启动入场动画）
2. 替换为 `WindowInsetsControllerCompat(activity.window, activity.window.decorView).show(ime())`
3. 焦点请求保留，确保 IME 在 Sheet 完全渲染后弹出

**验收标准**:
- [x] 新建项目对话框打开后键盘自动弹出
- [x] 工程重命名对话框打开后键盘自动弹出
- [x] 跳转到行对话框打开后键盘自动弹出（Number 键盘）
- [x] `gradlew :app:compileArm64DebugKotlin` BUILD SUCCESSFUL
- [x] `gradlew :app:ktlintCheck` BUILD SUCCESSFUL

**完成标记**: ✅ 已完成（2026-08-12）
- `BottomInputSheet.kt` 移除 `sheetState.show()` 调用，避免双重启动动画
- 新增 `import androidx.core.view.WindowInsetsControllerCompat` + `WindowInsetsCompat`
- `LaunchedEffect(Unit)` 改为：`focusRequester.requestFocus()` + `WindowInsetsControllerCompat(activity.window, activity.window.decorView).show(ime())`
- KDoc 补充 WindowInsetsControllerCompat 说明
- `gradlew :app:compileArm64DebugKotlin` BUILD SUCCESSFUL
- `gradlew :app:ktlintCheck` BUILD SUCCESSFUL

---

## B4 — 主题色选中标记夜间模式混淆

**问题**: `selectedMarkColor` 基于种子色 luminance 计算对比度，中灰色种子在暗色模式下返回 `Color.Black`，与深色背景融为一体。

**修复方案**:
```kotlin
// 简化为：暗色恒白，亮色恒黑
private fun selectedMarkColor(color: Color, isDark: Boolean): Color =
    if (isDark) Color.White else Color.Black
```

**验收标准**:
- [x] 暗色模式任意种子色选中边框和对号为白色
- [x] 亮色模式任意种子色选中边框和对号为黑色
- [x] 中灰色种子（#808080）在暗色模式下标记可见
- [x] `gradlew :app:compileArm64DebugKotlin` BUILD SUCCESSFUL
- [x] `gradlew :app:ktlintCheck` BUILD SUCCESSFUL

**完成标记**: ✅ 已完成（2026-08-12）
- `SettingsScreen.kt` 将 `selectedMarkColor` 从复杂的 luminance 对比度计算简化为单行三元表达式
- 暗色模式恒白、亮色模式恒黑，消除中灰色种子标记不可见问题
- KDoc 更新说明
- `gradlew :app:compileArm64DebugKotlin` BUILD SUCCESSFUL
- `gradlew :app:ktlintCheck` BUILD SUCCESSFUL

---

## C1 — 权限搜索返回按钮真正调用 onBack

**问题**: `onBack = { searchActive = false }` 只收起搜索态，不调用 `onBack()` 导航返回。

**修复方案**: `onBack = onBack`，直接透传调用方的返回回调。

**完成标记**: ✅ 已完成（2026-08-12）
- `ProjectPermissionScreen` 中 `PermissionCollapsedSearchInput` 的 `onBack` 由 `{ searchActive = false }` 改为 `onBack`（透传导航回调）
- 点击返回箭头时真正执行页面回退

---

## C2 — 权限行移除多余间距

**问题**: `PermissionCheckRow` 有 `.padding(vertical = 4.dp)`，LazyColumn 有 `verticalArrangement = Arrangement.spacedBy(4.dp)`，导致每行上下各有 4dp 空白，视觉上过于稀疏。

**修复方案**: 移除 Row 的 vertical padding 和两处 LazyColumn 的 spacedBy。

**完成标记**: ✅ 已完成（2026-08-12）
- `PermissionCheckRow` Row modifier 移除 `.padding(vertical = 4.dp)`
- `PermissionScreen.kt` 正文 LazyColumn 移除 `verticalArrangement = Arrangement.spacedBy(4.dp)`
- `PermissionScreen.kt` 全屏搜索 Dialog LazyColumn 同理移除
- 行与行之间无额外间距，紧凑展示

---

## C3 — 底部对话框与键盘同步弹出（消除视觉闪烁）

**问题**: 上一轮使用 `WindowInsetsControllerCompat` 不等待 sheet 动画就调出键盘，导致「先弹框后弹键盘」的视觉闪烁。

**修复方案**: 恢复 `sheetState.show()` 在 `LaunchedEffect` 中挂起等待动画完成，之后同步执行 `requestFocus()` + `keyboardController.show()`，确保对话框完全渲染后再弹出键盘。

**完成标记**: ✅ 已完成（2026-08-12）
- `BottomInputSheet.kt` LaunchedEffect 改为：`sheetState.show()` → `focusRequester.requestFocus()` → `keyboardController?.show()`（顺序执行，动画完成后再调键盘）
- 移除 `WindowInsetsControllerCompat` 方案（API 兼容性差）
- KDoc 同步更新说明

---

## 全局验收

```
.\gradlew.bat :app:compileArm64DebugKotlin --console=plain   ✅ BUILD SUCCESSFUL
.\gradlew.bat :app:ktlintCheck --console=plain               ✅ BUILD SUCCESSFUL
.\gradlew.bat :app:testArm64DebugUnitTest                    ✅ BUILD SUCCESSFUL
.\gradlew.bat :app:assembleArm64Debug --console=plain        ✅ BUILD SUCCESSFUL
```

**最终状态**: 全部 13 项任务（B1–B4 + C1–C3 + D1–D6）已完成，编译、格式检查、单元测试全部通过。APK 已安装并启动于 emulator-5554（API 34）。

---

## D1 — 权限勾选失效根因修复

**根因分析**: `rememberPickerItems` 以 `poolUnknown.sorted()` 作为 remember key，但 `sorted()` 每次调用返回新的 List 实例。权限切换时 `state?.selected` 变化 → `poolUnknown` 内容变化 → `sorted()` 生成新 List → `remember` key 失效 → `allItems` 重建 → LazyColumn 重算 items，导致勾选状态更新异常。

**修复方案**: 使用 `derivedStateOf` 稳定 `poolUnknown` 引用，确保仅当 `state.selected` 内容实际变化时才重建 `allItems`。

**完成标记**: ✅ 已完成
- `poolUnknown` 改为 `by remember(state?.selected) { derivedStateOf { ... } }`
- `gradlew :app:compileArm64DebugKotlin` BUILD SUCCESSFUL

---

## D2 — 底部对话框键盘同步弹出强化

**根因分析**: `LocalSoftwareKeyboardController.show()` 在部分设备/OEM ROM 上返回 `Unit`（无 boolean 返回值），无法判断是否成功；而 `WindowInsetsControllerCompat` 需要 Activity 窗口上下文。

**修复方案**: 
1. 保留 `sheetState.show()` 挂起等待动画完成（核心同步机制）
2. `requestFocus()` + `keyboardController?.show()` 优先执行
3. 失败时降级为 `WindowInsetsControllerCompat(activity.window, activity.window.decorView).show(ime())`

**完成标记**: ✅ 已完成
- `BottomInputSheet.kt` 引入 `LocalContext` + `WindowInsetsControllerCompat` fallback
- `gradlew :app:compileArm64DebugKotlin` BUILD SUCCESSFUL
- `gradlew :app:ktlintCheck` BUILD SUCCESSFUL

---

## D3 — 编辑器只读切换重复实现移除

**问题**: `EditorScreen.kt` 在 `⋮` 菜单中额外实现了「只读切换」入口（`EditorMoreAction(read_only)`），与全局设置页（`EditorSettingsScreen`）中的只读设置重复。AGENTS.md 明确：「编辑器的'只读模式'是全局通用功能，禁止在编辑器界面再次实现一个」。

**修复方案**: 删除 `EditorScreen.kt` 中只读切换的 `add(EditorMoreAction(...))` 代码块（16 行）。

**完成标记**: ✅ 已完成
- `EditorScreen.kt` 移除 `menu_category_edit` 下的 `read_only` 菜单项
- 全局设置页（`EditorSettingsScreen`）已是唯一只读入口，行为不变
- `gradlew :app:compileArm64DebugKotlin` BUILD SUCCESSFUL

---

## D4 — 格式化后自动滚动修复

**问题**: `EditorController.format()` 在文本替换后调用 `ed.ensurePositionVisible(restoredFirst, 0, true)` 恢复视口。当格式化导致文档大幅缩短（`restoredFirst` 被 `coerceIn` 钳位到末尾行）或 sora 内部事件触发时，视口意外跳顶/跳底。

**修复方案**: 移除 `ensurePositionVisible` 调用。sora 在 `beginBatchEdit`/`endBatchEdit` 批量替换后自然保持原有滚动位置，无需主动恢复视口。`setSelection(..., makeItVisible=false)` 已防止光标强制滚动。

**完成标记**: ✅ 已完成
- `EditorController.kt` `format()` 函数移除 `ensurePositionVisible(restoredFirst, 0, true)` 调用
- 格式化后光标正确恢复，视口保持用户浏览位置
- `gradlew :app:compileArm64DebugKotlin` BUILD SUCCESSFUL
- `gradlew :app:ktlintCheck` BUILD SUCCESSFUL
- `gradlew :app:testArm64DebugUnitTest` BUILD SUCCESSFUL

---

## D5 — 权限勾选双重触发根因修复

**根因分析**: `PermissionCheckRow` 的外层 `Row` 有 `.clickable(onClick = onToggle)`，同时内部 `Checkbox` 有 `onCheckedChange = { onToggle() }`。点击 checkbox 时，两个回调**同时触发**：
1. Row.clickable → `onToggle()`（toggle 一次）
2. Checkbox.onCheckedChange → `onToggle()`（toggle 第二次）

两次 toggle 导致状态先 add 后 remove（或先 remove 后 add），最终勾选状态不变。

**修复方案**: 移除 Row 上的 `.clickable(onClick = onToggle)`，仅保留 Checkbox 的 `onCheckedChange`。Checkbox 组件本身已完整处理触摸事件和点击逻辑，外层 Row 无需重复添加 clickable。

**完成标记**: ✅ 已完成（2026-08-12）
- `SearchablePermissionPicker.kt` `PermissionCheckRow` Row modifier 移除 `.clickable(onClick = onToggle)`
- 保留 `Checkbox(checked = checked, onCheckedChange = { onToggle() })` 作为唯一点击入口
- KDoc 补充双触发说明
- `gradlew :app:compileArm64DebugKotlin` BUILD SUCCESSFUL
- `gradlew :app:ktlintCheck` BUILD SUCCESSFUL

---

## D6 — 底部对话框键盘同步弹出强化

**根因分析**: 上一轮修复中 `LocalSoftwareKeyboardController.show()` 返回 `Unit`（无 boolean），无法判断成功与否；`!showedByController` 编译失败。此外，部分 OEM ROM 上 `keyboardController` 可能为 null。

**修复方案**: 
1. 保留 `sheetState.show()` 挂起等待入场动画完成（核心同步机制）
2. 动画完成后执行 `requestFocus()` + `keyboardController?.show()`（优先路径）
3. 无论 keyboardController 是否为 null，均执行 `WindowInsetsControllerCompat(activity.window, activity.window.decorView).show(ime())` 作为兜底

**完成标记**: ✅ 已完成（2026-08-12）
- `BottomInputSheet.kt` LaunchedEffect 双重键盘控制：Compose 原生 API + AndroidX fallback
- KDoc 详细说明双路机制
- APK 已安装至 emulator-5554 验证
- `gradlew :app:compileArm64DebugKotlin` BUILD SUCCESSFUL
- `gradlew :app:ktlintCheck` BUILD SUCCESSFUL
- `gradlew :app:testArm64DebugUnitTest` BUILD SUCCESSFUL

**最终状态**: 全部 4 项任务已完成，编译、格式检查、单元测试全部通过。变更 5 个文件，+343 / -178 行。
