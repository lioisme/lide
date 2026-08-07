# 设置界面简约化工作单（AndroLua 与打包合并 / 自动安装开关 / 签名独立界面）

> **状态**：（已完成 2026-08-07，待模拟器 UI 回归后归档） · **日期**：2026-08-07
> **目标**：按用户 5 项要求对设置体系做简约化整改：① AndroLua 设置与打包选项设置合并为单一入口页；
> ② 去除界面上大量解释性文案；③ 收敛「单功能单独成专题」的繁琐结构；④ 自动安装改为单一开关且默认启用；
> ⑤ 签名管理独立成界面（开发者自管理签名）。总体要求：设置界面简约明了、美观大方，代码质量与架构设计
> 达到大厂验收标准。
> **验收**：`:app:assembleArm64Debug` BUILD SUCCESSFUL；`:app:testArm64DebugUnitTest --tests "com.lioisme.lide.build.*"` 全绿；
> R1 门禁零命中；合并页/主设置页/签名页结构符合本工作单 §3 目标结构。

---

## 1. 用户要求 → 实现映射（5 项）

1. **AndroLua 设置与打包选项设置合并**：主设置页「AndroLua」分区由两行（AndroLua 工具 / 打包选项）
   收拢为单行入口 → 合并页 `AndroLuaSettingsScreen`（工具分区：API 浏览器/示例；打包分区：自动安装/
   原生库/编译/加密；默认 ABI 分区）。用户确认（2026-08-07）：签名管理入口不放合并页，在主设置页
   「通用」分区独立一行；默认 ABI 选择保留在合并页。
2. **去除解释性文案**：删除打包选项页长 desc（`build_opt_native_desc`/`build_opt_compile_desc`/
   `build_opt_encrypt_desc`/`build_opt_output_hint` 等），开关副标题统一为「开/关」状态值（与编辑器
   设置页风格一致）；精简 `signing_hint` 长文案为一行。
3. **收敛繁琐结构**：删除仅承载 1~2 项的独立页面 `BuildOptionsScreen` 与路由 `BUILD_OPTIONS`；
   打包分区 4 个开关同区平铺，不再各自成「专题」小节；「默认签名」单选区随签名独立界面移除。
4. **自动安装单一开关（默认启用）**：`AfterBuild.INSTALL` 为默认（pro 兼容，既有默认不变），UI 由
   「自动安装/仅保存」单选改为单个 Switch（开=INSTALL，关=SAVE）。
5. **签名管理独立界面**：主设置页「通用」分区新增「签名管理」独立行（→ `SigningScreen`，既有
   档案列表/默认星标/导入/生成/删除能力不动）；打包页「构建中管理」链接保留。

## 2. 任务分解（按依赖顺序执行，标注完成状态）

### T1 重构 AndroLuaSettingsScreen 为合并页（工具 + 打包 + 默认 ABI）

- 文件：`app/src/main/kotlin/com/lioisme/lide/ui/screen/AndroLuaSettingsScreen.kt`
- 内容：
  - 「工具」分区：API 浏览器 / 示例（保留原回调参数）。
  - 「打包」分区（4 个开关同区平铺，副标题 = 开/关 状态）：
    - 自动安装（`opts.afterBuild == AfterBuild.INSTALL`，关 → SAVE）
    - 仅携带依赖的原生库（`opts.stripNativeLibs`）
    - 编译 Lua 文件（`opts.compileLua`）
    - Lua 加密（`opts.encryptBytecode`）
  - 「默认 ABI」分区：保留 5 项 ChoiceRow（宿主全部 / arm64-v8a / x86_64 / armeabi-v7a / x86）。
  - 持久化统一经 `SettingsManager.updateBuild`。
- 状态：（**已完成 2026-08-07**）：重写为合并页三分区结构；`ChoiceRow` 组件随 `BuildOptionsScreen` 删除而迁入本文件（private）；工具行去解释性副标题；`SettingsItem`/`SettingsToggleItem` 的 `subtitle` 改为默认空串 + 空值不渲染（组件卫生，消除空行渲染）；新增 `tools`/`packaging` 分区标题字符串；验收 = `:app:compileArm64DebugKotlin` BUILD SUCCESSFUL（2026-08-07）。

### T2 删除 BuildOptionsScreen 与路由

- 删除 `app/src/main/kotlin/com/lioisme/lide/ui/screen/BuildOptionsScreen.kt`；
- 删除 `Routes.BUILD_OPTIONS` 与 `LideNavHost.kt` 对应 `slideComposable` 与 import；
- 同步更新 `BuildApkScreen.kt` 注释中「设置页打包选项」措辞。
- 状态：（**已完成 2026-08-07**）：文件已物理删除；`Routes.BUILD_OPTIONS` 常量、导航块与 import 全部移除；`BuildApkScreen.kt:368` 注释改为「AndroLua 合并页」；全仓 grep 零残留（`BUILD_OPTIONS`/`BuildOptionsScreen` 无命中）。

### T3 主设置页精简 + 签名管理独立入口

- `SettingsScreen.kt`：
  - 「AndroLua」分区收拢为单行（AndroLua → 合并页），移除「打包选项」行；
  - 「通用」分区新增「签名管理」行（`onSigningClick` → `Routes.SIGNING`）；
  - 移除 `onBuildOptionsClick` 参数。
- `LideNavHost.kt`：`SettingsScreen` 调用点同步。
- 状态：（**已完成 2026-08-07**）：主设置页目标结构达成（显示/编辑器/AndroLua 单行/通用含签名管理）；签名行图标 `Icons.Default.VpnKey`（与权限行 Lock 图标区分）；移除 `onBuildOptionsClick` 参数与 `Build` 图标 import（无死代码）。

### T4 自动安装开关（默认启用）

- 合并页内以 `SettingsToggleItem` 呈现自动安装开关；`BuildOpts.afterBuild` 默认值
  `AfterBuild.INSTALL` 不变（pro 兼容默认已启用）；`BuildApkScreen` 消费逻辑零改动。
- 状态：（**已完成 2026-08-07**）：`build_opt_auto_install` 开关落地——`checked = opts.afterBuild == AfterBuild.INSTALL`，切换写回 `INSTALL`/`SAVE`；默认启用语义保持（`BuildOpts.afterBuild = AfterBuild.INSTALL`）；`BuildApkScreen` 自动安装分支零改动。

### T5 字符串整改（en/zh 同步）

- 删除不再引用：`build_options`、`build_options_subtitle`、`build_opt_native_desc`、
  `build_opt_compile_desc`、`build_opt_encrypt_desc`、`build_opt_output_title`、
  `build_opt_output_install`、`build_opt_output_save`、`build_opt_output_hint`、
  `build_opt_signature_title`。
- 保留：`build_opt_native_title` / `build_opt_compile_label` / `build_opt_encrypt_title` /
  `build_opt_abi_title` / `build_opt_abi_auto` / `build_manage_signing`。
- 新增：`build_opt_auto_install`（自动安装）、`signing_manage_subtitle`（签名管理副标题）。
- 修改：`androlua_subtitle` → 「API 与打包」；`signing_hint` 精简为一行。
- 状态：（**已完成 2026-08-07**）：en/zh 双语同步落地；`build_opt_encrypt_title` 去除「（DES）」括注；`signing_hint` 由 3 行长文案精简为单行（中英）；10 条冗余字符串删除后全仓 `R.string.*` 引用零残留（grep 验证）；`tools`/`packaging` 分区标题新增。

### T6 文档同步

- `docs/kb/README.md` §2 矩阵登记本工作单；`docs/TASKS.md` 登记；`docs/in-progress/
  RUNTIME_PACKAGING_5REQ_WORK_ORDER.md` 中 `BuildOptionsScreen` 引用措辞同步（若提及页面名）。
- 状态：（**已完成 2026-08-07**）：`docs/kb/README.md` §2.3 新增本工作单条目（矩阵 7→8 篇）；
  §1 计划中文档数同步；`docs/TASKS.md` 当前进行中表新增条目；`RUNTIME_PACKAGING_5REQ_WORK_ORDER.md`
  完成记录表尾追加「后续 UI 迁移」注脚（P1-1/P2-3/P3-2 所指设置页 = AndroLua 合并页）。

### T7 全量验证与验收证据

- `.\gradlew.bat :app:compileArm64DebugKotlin --console=plain`
- `.\gradlew.bat :app:assembleArm64Debug --console=plain`
- `.\gradlew.bat :app:testArm64DebugUnitTest --tests "com.lioisme.lide.build.*" --console=plain`
- R1 门禁：`rg -n "luajava|androlua|loadLibrary" editor/src` 零命中。
- 状态：（**已完成 2026-08-07**）：全部通过，见 §5 验收证据。

## 3. 目标结构（最终态）

```
设置（主）
├─ 显示      外观 / 主题色
├─ 编辑器    编辑器（单入口 → 编辑器设置页）
├─ AndroLua  AndroLua（单入口 → 合并页）
└─ 通用      权限管理 / 签名管理 / 语言 / 关于

AndroLua（合并页）
├─ 工具      API 浏览器 / 示例
├─ 打包      自动安装[开关] / 原生库[开关] / 编译[开关] / 加密[开关]
└─ 默认 ABI  宿主全部 / arm64-v8a / x86_64 / armeabi-v7a / x86

签名（独立页，开发者自管理）
└─ 档案列表（默认星标）/ 生成新签名 / 导入密钥库
```

## 4. 架构合规

- 全部改动位于 `:app`（设置 UI + 字符串 + 路由），未触碰 `:editor`（R1~R5 满足）。
- 打包/签名/加密业务链零改动：仅 UI 入口与持久化消费位置调整；`AfterBuild` 默认语义
  （INSTALL，pro 兼容）保持不变。
- 设置持久化仍为唯一事实源 `SettingsManager`（`lide_build` 域），无新增键。

## 5. 验收证据（2026-08-07 全绿）

- `.\gradlew.bat :app:compileArm64DebugKotlin --console=plain` → **BUILD SUCCESSFUL**（32s；仅余既有
  MenuBook ×2 / ACTION_INSTALL_PACKAGE / unsafeCheckOpNoThrow 弃用警告，均在本任务改动文件之外）。
- `.\gradlew.bat :app:assembleArm64Debug --console=plain` → **BUILD SUCCESSFUL**（13s，126 tasks）。
- `.\gradlew.bat :app:testArm64DebugUnitTest --tests "com.lioisme.lide.build.*" --console=plain` →
  **BUILD SUCCESSFUL**（91 tasks，build.* 单测全绿）。
- R1 门禁：`editor/src` 搜索 `luajava|androlua|loadLibrary` → **零命中**（R1 GATE CLEAN）。
- 差集核对：全仓 `grep` `BUILD_OPTIONS`/`BuildOptionsScreen`/`build_options*`/`build_opt_output*`/
  `build_opt_signature_title` 均无残留引用。

待补充：模拟器 UI 回归（主设置页五分区 → AndroLua 合并页三分区开关/ABI → 签名管理独立页 → 打包页自动安装分支）与
打包产物回归（默认自动安装路径），通过后 `git mv` 归档 `docs/discard/`。
