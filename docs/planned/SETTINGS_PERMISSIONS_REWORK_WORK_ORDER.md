# 设置与权限治理工作单（合并入口 + 动态权限 + 移除手册/帮助）

> **状态**：（已完成 2026-08-07） · **日期**：2026-08-07
> **目标**：按用户 8 项要求重组设置/权限体系，并打通宿主与打包产物的运行时动态权限链路。
> **验收**：`:app:assembleArm64Debug` BUILD SUCCESSFUL；R1 门禁零命中；打包产物支持 Lua 动态申请权限。

---

## 背景

设置页此前经 `SETTINGS_ORGANIZATION_WORK_ORDER`（已归档 `docs/discard/`）拆出
「AndroLua 设置」独立入口页。用户进一步提出 8 项要求，其中两项与既有实现冲突、两项为
引擎能力缺口、四项为功能删除/重组。

## 用户要求 → 实现映射（8 项）

1. **LIDE 自身权限动态申请 + 权限管理界面单击仅申请该权限**：
   - 运行门（EditorScreen.requestRun）运行前只动态请求「工程声明 ∩ 宿主已声明」中未授予的
     权限，不一次性弹全部；
   - 权限管理页（HostPermissionScreen）每行独立请求按钮，`permLauncher.launch(arrayOf(perm.name))`
     只请求被单击的权限（P2#12 既有行为，本任务核对保留）。
2. **打包构建的 .apk 支持动态申请权限**：新增 `LuaActivity.onRequestPermissionsResult`
   （:androlua，pro 对等 P-3.6）——脚本 `activity.requestPermissions({...}, code)` 的结果
   路由回 Lua 全局 `onRequestPermissionsResult(requestCode, {perms}, {results})`；
   打包产物与宿主共用该引擎，随 `assemble` 编译进主 DEX。
3. **移除 Lua 手册**：删除 `LuaManualScreen`、`Routes.MANUAL`、`assets/luadoc/`（4 个文件）、
   `LideApp.extractLuadoc`、`ApkBuilder.HOST_ASSET_EXCLUDED` 中 `assets/luadoc/`、相关字符串。
4. **移除帮助界面**：删除 `HelpScreen`、`HelpParser`（含单测）、`Routes.HELP`、`assets/help.txt`、
   相关字符串。
5. **编辑器设置支持恢复默认**：`SettingsManager.resetEditor(context)`（清空 `lide_editor`
   键表并回落默认值），合并页顶栏 Refresh 图标按钮 + Toast 反馈。
6. **权限管理移出 AndroLua 设置**：权限管理（HostPermissionScreen）移入主设置页「通用」分区
   （与语言/关于并列，作为 LIDE 通用运行时能力）。
7. **编辑器设置与 AndroLua 设置不再单独列出**：合并为单一入口「编辑器与 AndroLua」。
8. **入口/页面标题不带「设置」后缀**：入口与合并页标题均为「编辑器与 AndroLua」。

## 实施内容（（已完成）2026-08-07）

1. **引擎动态权限（要求 2）**：`third_party/androlua/.../LuaActivity.java` 新增
   `onRequestPermissionsResult` 覆写（置于 `onActivityResult` 之后）：`super` 转发后，
   存在 Lua 全局 `onRequestPermissionsResult` 函数时以 `debug.traceback` 保护调用；
   `permissions`/`grantResults` 显式转为 Lua 顺序表（`pushObjectValue` 不拆解数组，
   直接 push 为 userdata 会导致脚本无法迭代）；异常经 `sendError` 上报，无全局函数时静默。
2. **移除 Lua 手册（要求 3）**：删除 `LuaManualScreen.kt`、路由 `MANUAL` 及导航接线、
   `assets/luadoc/`（contents.html/glossary.html/manual.html/logo.gif）、
   `LideApp.extractLuadoc()`、`ApkBuilder` 打包剔除清单中的 `assets/luadoc/`、
   字符串 `lua_manual`/`lua_manual_missing`/`androlua_manual_subtitle`。
3. **移除帮助界面（要求 4）**：删除 `HelpScreen.kt`、`HelpParser.kt`、`HelpParserTest.kt`、
   路由 `HELP` 及接线、`assets/help.txt`、字符串 `help`/`help_subtitle`/`help_title`/`help_missing`。
4. **入口拆分（要求 7/8 二轮修订）**：
   - 一轮（2026-08-07 上午）：删除 `AndroLuaSettingsScreen.kt` 与路由 `ANDROLUA_SETTINGS`，
     `EditorSettingsScreen` 重构为合并页「编辑器与 AndroLua」（21 项设置 + AndroLua 工具分区），
     主设置页收拢为三分区。
   - **二轮（2026-08-07 下午，用户否决合并设计「不要混在一起」）**：恢复拆分——
     重建 `AndroLuaSettingsScreen.kt`（AndroLua 工具页：API 浏览器/插件/示例）与路由
     `ANDROLUA_SETTINGS`；`EditorSettingsScreen` 移除 AndroLua 分区与工具回调，仅剩
     21 项设置四分区（通用/编辑/视图/输入）；主设置页 `SettingsScreen` 四分区
     （显示/编辑器/AndroLua/通用），编辑器与 AndroLua 为两个独立入口，各自独立页面，
     均无「设置」后缀（标题「编辑器」/「AndroLua」）。
5. **恢复默认（要求 5）**：`SettingsManager.resetEditor` + 编辑器页顶栏 Refresh 按钮
   （`TipIconButton` + tooltip，重置后 Toast 确认）。
6. **权限管理归位（要求 6）**：主设置页通用分区新增权限管理入口（`Routes.PERMISSIONS`）。
7. **字符串**（en/zh）：删 `editor_settings`/`editor_settings_subtitle`/
   `androlua_settings`/`androlua_settings_subtitle`/`androlua_section_*`/`help*`/`lua_manual*`；
   增 `editor`/`androlua`（分区+入口+页标题复用）/`editor_subtitle`/`androlua_subtitle`/
   `restore_defaults`/`restore_defaults_done`；二轮删除 `editor_androlua`/
   `editor_androlua_settings`/`editor_androlua_subtitle`。

## 架构合规

- 全部改动位于 `:app`（设置 UI、资产、打包器）与 `:androlua`（引擎运行时权限回调），
  未触碰 `:editor`（R1~R5 满足）。
- 动态权限回调走 `runFunc` 同款「全局函数 + traceback + sendError」协议，与既有
  `onActivityResult`/`onStart` 等生命周期回调一致，不引入新机制。
- 打包产物动态权限路径与 pro 行为对等（脚本侧写法不变：
  `activity.requestPermissions({...}, code)` + 全局 `onRequestPermissionsResult`）。

## 验收证据

- `.\gradlew.bat :app:assembleArm64Debug --console=plain` → **BUILD SUCCESSFUL**
  （125 tasks；仅余既有 MenuBook 弃用警告 ×2 与系统 API 弃用警告，均在本任务改动之外）；
  二轮拆分后重跑：`:app:compileArm64DebugKotlin` 与 `:app:assembleArm64Debug` 均
  **BUILD SUCCESSFUL**（2026-08-07 下午）。
- R1 门禁（回归，本任务零改动 `:editor`）：`rg -n "luajava|androlua|loadLibrary" editor/src`
  → 无命中。
- 待补充：模拟器 UI 回归（设置页四分区 + 编辑器页 21 项 + AndroLua 页三工具 + 恢复默认
  Toast + 权限页单击单请求）与打包产物动态权限实测（安装产物 → 脚本 requestPermissions
  → 全局回调收到结果）。
