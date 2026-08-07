# 打包 UI 重构工作单（PACKAGING_UI_REWORK_WORK_ORDER）

> 日期：2026-08-08
> 触发：用户四项打包 UI 需求（1. 签名方案/ABI 用经典复选框；2. 宿主不含的 ABI 不显示或置灰；
> 3. 「仅携带依赖的原生库」更名「优化打包」、仅针对原生库；4. 删除构建 APK 整页，仅保留一个构建打包对话框）。

## 1. 需求判定（审计结论）

| # | 需求 | 审计现状 | 判定 |
|---|------|---------|------|
| 1 | 签名（v1/v2/v3）与 ABI 用经典复选框控件 | 设置页签名方案/默认 ABI 用 MD3 `FilterChip`（`AndroLuaSettingsScreen.kt`）；旧打包页 ABI 亦用 FilterChip | ❌ 不符 |
| 2 | 宿主不含的架构原生库不显示或置灰 | 设置页 ABI 选项**硬编码** `["arm64-v8a","x86_64","armeabi-v7a","x86"]`，不读宿主；宿主驱动仅存在于旧打包页（`ZipUtils.listAbis`） | ❌ 不符（硬编码名单违反 D-1「禁止按已知架构名判可用性」精神） |
| 3 | 「仅携带依赖的原生库」命名为「优化打包」，仅针对原生库 | 功能正确（`ApkBuilder.nativeLibKept` 只作用于 `lib/*.so`），标题/说明不符 | ⚠️ 命名不符 |
| 4 | 删除构建 APK 界面及相关代码，仅保留一个构建打包对话框 | 整页 `BuildApkScreen.kt`（765 行）+ 路由 `BUILD_APK` + 编辑器 ⋮ 菜单导航入口 | ❌ 不符 |

## 2. 整改实施（2026-08-08）

### A. 设置页（`AndroLuaSettingsScreen.kt`）——需求 1/2/3

- **A1 签名方案 → 经典 Checkbox**：v1/v2/v3 由 FilterChip 改为 `CheckboxRow`（Checkbox + 标签行）；
  最后一个勾选项禁用取消（`SigningScheme.fromFlags` 全关返回 null → 不回写，保证至少一项）。
- **A2 默认 ABI → 宿主驱动 + Checkbox + 置灰**：删除硬编码名单；`LaunchedEffect(Dispatchers.IO)` 读自身
  APK 中央目录（`packageManager.sourceDir` → `ZipUtils.listAbis`，与装配端同口径，O(CEN) 不解压）；
  候选 = 宿主 ABI ∪ 标准架构名（`STANDARD_ABIS`，仅作展示不判可用性）；宿主未携带的 ABI 以
  禁用态 Checkbox 置灰呈现（用户决策：置灰）；`preferAbi` 残留未携带架构时构建侧回落「宿主全部」。
- **A3 优化打包**：`build_opt_native_title` → 「优化打包」（en/zh），新增副标题
  `build_opt_native_subtitle` = 仅针对原生库（只携带工程依赖命中的 .so）；控件保持
  `SettingsToggleItem`（Switch，用户决策：开关型功能）。

### B. 构建打包对话框——需求 4

- **删除**：`ui/screen/BuildApkScreen.kt`（整文件）；`LideNavHost.kt` 的 `Routes.BUILD_APK` /
  `Routes.buildApk()` / slideComposable 块 / import；`EditorScreen.kt` 的 `onBuildApk` 参数
  （改为 `onManageSigning`）。
- **新建** `ui/screen/BuildPackagingDialog.kt`（同包名）：
  - `AlertDialog`（内容可滚动，`heightIn(max=520.dp)`）：工程摘要（init.lua）→ 依赖摘要 →
    ABI 多选（Checkbox，**仅宿主携带项**，默认 preferAbi∩宿主 或全选）→ 签名方案 v1/v2/v3
    Checkbox（默认设置页值，本次构建局部选择不回写）→ 签名档案 + 「管理」跳转 `SigningScreen`
    → 打包选项只读摘要（优化打包/编译/加密）→ 构建按钮 + 阶段进度 AlertDialog（Channel 桥主线程，
    模式与旧页一致）→ 成功路径展示 + 分享按钮 / 失败错误文案；`AfterBuild.INSTALL` 自动安装语义保留。
  - **迁移辅助函数**（原 BuildApkScreen 私有，保持 internal 与包名，`BuildPackagingTest` 零改动）：
    `collectProjectFiles` / `analyzeDependencies` / `engineLuaLibs` / `hostLuaSourceMap` /
    `hostLuaAssets` / `buildOutputName` / `sanitizeName` / `doBuild` / `installApk` / `shareApk` /
    `stageLabelRes` / `BuildResult`。
- **保留不删**：`SigningScreen.kt`（签名管理独立页，非打包页）；`ApkBuilder` / `ApkSigner` /
  `SigningManager` / `SigningCrypto` / `ZipUtils` / `NativeDependencyAnalyzer` / `HostPolicy` /
  `LuaCompiler`；`SettingsManager.buildOpts` 持久化域。

### C. 资源与文档

- `res/values{,-zh}/strings.xml`：新增 `build_opt_native_subtitle` / `build_opts_summary`；
  更新 `build_opt_native_title`（优化打包）/ `build_opt_abi_hint`（置灰语义）；
  删除 9 条孤儿串（`build_summary_group` / `build_hint` / `build_appname` / `build_package` /
  `build_dep_summary` / `build_dep_lua` / `build_dep_host_only` / `build_advanced` /
  `build_signature_scheme`，已确认全仓零引用）。
- `AGENTS.md`：修订 D-1「ABI 架构契约」——设置页默认 ABI 选项改为宿主驱动 + 未携带置灰禁用；
  打包 UI 唯一入口 = 构建打包对话框（`BuildPackagingDialog`），签名/ABI 用经典 Checkbox，
  「优化打包」为开关控件仅针对原生库。
- `BuildOpts.kt`：注释「打包页读作初始值、勾选即写回」→「构建对话框读作初始值」。

### D. 验证

- `:app:compileArm64DebugKotlin`（编译通过）
- `:app:testArm64DebugUnitTest --tests "com.lioisme.lide.build.*"`（含迁移后的 `BuildPackagingTest` 全绿）
- `:app:assembleArm64Debug`（BUILD SUCCESSFUL）

## 3. 与既有冻结结论的冲突处理

- **AGENTS.md D-1**：「宿主未携带该默认时打包页回落「宿主全部」，不做置灰」→ 被本次需求 2 取代为
  「置灰禁用（仅展示不可选，可用性判定只依据 `ZipUtils.listAbis` 实测）」；已同步修订 AGENTS.md，
  且新实现严格保持「禁止按已知架构名硬编码可用性判定」原则（标准架构名仅作展示候选，可用性 = 实测）。

## 4. 状态

- （**已完成 2026-08-08**）：A1~A3 + B1~B2 + C 全部落地；验证命令全绿
  （compile / build.* 单测 / assembleArm64Debug），证据见实施记录 §2 与验收清单。
- 待办：模拟器 UI 回归（构建对话框全流程 + 设置页置灰呈现）后 `git mv` 至 `docs/discard/`。
