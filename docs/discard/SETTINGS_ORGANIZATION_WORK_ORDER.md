# 设置界面重组工作单（AndroLua 专属设置页）

> **状态**：（已完成 2026-08-07） · **日期**：2026-08-07
> **目标**：整理设置界面——AndroLua 相关设置像编辑器设置一样拥有专门界面（入口页形态）。
> **验收**：`:app:compileArm64DebugKotlin` BUILD SUCCESSFUL；R1 门禁零命中（本任务未触碰 `:editor`）。

---

## 背景

设置页 `SettingsScreen` 原为三分区（显示/编辑器/通用），「通用」分区混装 AndroLua 相关条目
（API 浏览器、示例、插件、权限管理、Lua 手册）与 App 级条目（语言、帮助、关于），
且「关于」行实际误指向 LuaManualScreen（`onClick = onManualClick`，标签与目标不一致）。

## 方案（用户确认）

- 新页形态：入口页（hub），与 `EditorSettingsScreen` 同款（`LideTopBar` + `SettingsSection`/`SettingsItem`）。
- 归入 AndroLua 设置页的条目：Lua 手册、权限管理、插件、示例、API 浏览器。
- 「通用」分区按行业惯例保留：语言、帮助、关于。

## 实施内容（（已完成）2026-08-07）

1. **新增 `app/.../ui/screen/AndroLuaSettingsScreen.kt`**（137 行）：入口页，三分组——
   - 工具与文档：API 浏览器（GridView）、Lua 手册（AutoMirrored MenuBook）；
   - 运行时：权限管理（Lock）；
   - 扩展与资源：插件（Extension）、示例（PlayArrow）。
   复用 `SettingsScreen.kt` 中已有 `internal` 的 `SettingsSection`/`SettingsItem`，零复制。

2. **`SettingsScreen.kt` 重组为四分区**：
   - 显示（外观/主题色，不动）→ 编辑器（入口不动）→ **AndroLua（新分区，单入口行）** → 通用（精简）；
   - 删除 5 个回调参数（`onApiBrowserClick/onExamplesClick/onManualClick/onPermissionsClick/onPluginsClick`），
     新增 `onAndroLuaSettingsClick`；
   - 修正历史缺陷：「关于」行由误导航 LuaManualScreen 改为页内 `AlertDialog`（App 名 + 版本，行业惯例）；
   - 清理随之失效的 5 个图标 import（GridView/MenuBook/Lock/Extension 等）。

3. **`LideNavHost.kt`**：新增 `Routes.ANDROLUA_SETTINGS = "androlua-settings"` + `slideComposable`
   接线（5 个导航回调迁至新页目的地）；`SETTINGS` 目的地同步瘦身。

4. **字符串**（`values/strings.xml` + `values-zh/strings.xml`）：
   `androlua`（分区标题）、`androlua_settings`、`androlua_settings_subtitle`、
   `androlua_section_tools`、`androlua_section_runtime`、`androlua_section_content`、
   `androlua_manual_subtitle`；复用已有 `lua_manual`/`permission_management`/`plugins`/
   `examples`/`api_browser` 等条目。

5. **架构合规**：全部改动位于 `:app`（宿主），未触碰 `:editor`（R1~R5 满足；设置 UI 属宿主职责 R3）。

## 验收证据

- `.\gradlew.bat :app:compileArm64DebugKotlin --console=plain` → **BUILD SUCCESSFUL**（73 tasks，无新增告警；
  仅余既有 MenuBook 弃用警告 ×4 与系统 API 弃用警告，均在本任务改动之外）。
- R1 门禁（回归，本任务零改动 `:editor`）：`rg -n "luajava|androlua|loadLibrary" editor/src` → 无命中。
- 待补充：真机/模拟器 UI 回归（设置页四分区导航链 + AndroLua 页五入口跳转 + 关于弹窗）。
