# Editor 右上角「更多」多级菜单 — cascade 化工作单

| Metadata | Value |
|----------|-------|
| Document ID | LIDE-WO-2026-022 |
| Status | ✅ 已完成（2026-08-07，完成后归档 docs/discard） |
| Author | Engineering |
| Priority | P1 — 修复多级菜单位置 + 合规（官方/成熟组件） |
| Baseline | lide HEAD（cascade-compose 2.3.0 引入） |

---

## 1. 背景与目标

D-20（LIDE-WO-2026-020）落地了编辑器右上角「更多」多级菜单（一级=分类、二级=功能），但初版采用**手写嵌套**：

`Box { DropdownMenuItem(...); DropdownMenu(...) }` —— 二级 `DropdownMenu` 的 Popup 锚定在条目上的 `Box`，导致**二级菜单位置错位**（不显示在正确锚点），且属「手搓」实现，违反 N-04 / D-13「宿主 UI 一律官方或成熟第三方组件，禁止手搓自研」。

## 2. 目标

1. 编辑器右上角「更多」菜单保持多级（一级=分类、二级=功能，禁止平铺）。
2. 二级子菜单位置正确——用成熟第三方库的级联定位（屏幕边缘/键盘避让、条目右箭头、子菜单返回头）。
3. 彻底删除手写 `MoreSubmenuItem` 嵌套 `DropdownMenu` 方案。

## 3. 方案与依据

- Material3 官方 `DropdownMenu`（1.4.0）**无嵌套子菜单 API**（`Menu.kt` 实证）→ 必须引入成熟第三方。
- 选型：**`me.saket.cascade:cascade-compose:2.3.0`**（2023-10 最后一版 stable，Apache-2.0，Compose 嵌套菜单事实标准）。
- 兼容性实证（POM）：cascade 的 Compose 依赖为 **`runtime` scope**（ui 1.5.2 / material3 1.1.2 / activity-compose 1.7.2 / kotlin-stdlib 1.9.10）
  → 编译/运行解析时由消费方 BOM 2025.06.00（ui 1.8.x / **material3 1.4.0**）胜出，与 `reorderable:3.0.0`（§14.3 同款论证）一致。
- 运行时定位由 cascade `DropdownMenuPositionProvider` 处理：根菜单锚定 ⋮ 按钮父 Box，子菜单级联到分类项右侧、自动避让屏幕边缘与软键盘——直接修复「二级菜单位置错位」。
- 纯 Compose 库（无 `.so`/`dex`/无 `:androlua` 依赖）→ `R1` 门禁不受影响。

## 4. 改动内容

### 4.1 `editor/build.gradle.kts`

新增依赖（附兼容性注释）：

```kotlin
implementation("me.saket.cascade:cascade-compose:2.3.0")
```

### 4.2 `editor/.../EditorToolbar.kt`

- 根菜单 `DropdownMenu` → `CascadeDropdownMenu(expanded, onDismissRequest)`（仍包在 ⋮ 按钮同 Box 内，锚点不变）。
- `groupByCategory` 分组循环内，一级分类项渲染为 cascade `DropdownMenuItem(text, children={...})`
  （cascade 自动带右箭头 + 子菜单头返回键）；二级功能项保留 material3 `DropdownMenuItem(text, onClick)`。
- **删除**手写 `MoreSubmenuItem` 私有组件（含内部嵌套 `DropdownMenu`/`KeyboardArrowRight` 手工箭头）。
- KDoc/注释同步更新（多级菜单实现依据 = cascade）。

## 5. 验收证据（2026-08-07）

| # | 门禁 | 结果 |
|---|------|------|
| 1 | `:editor:compileDebugKotlin` | ✅ BUILD SUCCESSFUL |
| 2 | `:app:compileArm64DebugKotlin` | ✅ BUILD SUCCESSFUL |
| 3 | `:editor:dependencies --configuration debugRuntimeClasspath` | ✅ cascade material3 1.1.2 → **1.4.0**（BOM 胜出，无降版） |
| 4 | `:editor:runKtlintCheckOverMainSourceSet` | ✅ BUILD SUCCESSFUL |
| 5 | `:editor:testDebugUnitTest` `:androlua:testDebugUnitTest` `:app:testArm64DebugUnitTest` | ✅ 全绿 |
| 6 | `:app:assembleArm64Debug` | ✅ BUILD SUCCESSFUL |
| 7 | R1 门禁（`editor/src` 无 `luajava\|androlua\|loadLibrary`） | ✅ 零命中 |

## 6. 风险注记

- cascade 2.3.0（2023）面向 Compose 1.5：本项目 ui 1.8.x/material3 1.4.0 为「旧码新运行时」（反向于本仓 `TipIconButton` NoSuchMethodError 先例），风险更低；编译/依赖解析/测试均绿。真机视觉回归见后续综合复测。
- 回滚粒度 = 提交粒度：移除 `editor/build.gradle.kts` 依赖行 + 恢复 `EditorToolbar.kt` 即可。