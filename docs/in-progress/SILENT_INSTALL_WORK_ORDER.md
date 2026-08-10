# 静默安装能力工作单（Root / Shizuku / Dhizuku）

| 元数据 | 内容 |
|--------|------|
| 文档版本 | 1.0 |
| 创建日期 | 2026-08-09 |
| 文档代号 | SILENT_INSTALL |
| 参考蓝本 | `reference/komi-store-main`（Komi Store 三通道静默安装实现：`core/data/.../services/{shizuku,dhizuku,root,installer}/`，2026-08-09 调研报告已拆解） |
| 总目标 | ① LIDE 宿主支持申请 Root（su）、Shizuku（含 SUI）、Dhizuku 三类特权，状态实时展示；② 打包产物安装可选用静默安装（免逐次确认），后端不可用/失败自动回落系统安装器，用户无感知阻塞 |
| 硬性约束 | AGENTS.md R1~R5（编辑器包纯文本级、新代码归宿主包、零原生产物入库）、引擎 dex 单份化契约（`:androlua` implementation 并入宿主根 dex 恰一份）、minSdk 24 / targetSdk 36、`verifyMinimalism` / `verifyMinimalismDebug` / `verifyVendorIsolation` 门禁、打包/签名/加密链改动必跑 `com.lioisme.lide.build.*` 单测 |
| 文档状态 | 实施完成，待设备实测（T14） |
| 完成登记 | 见 §8 完成记录（逐任务标记） |

---

## 1. 背景与需求定义（用户原话语义）

1. **权限能力**：LIDE 本体支持申请 Root、Shizuku 和 Dhizuku 权限；
2. **安装利用**：当 LIDE 拥有这些权限时，安装程序（打包产物安装）时可以选择使用这些权限进行安装；
3. **参考**：komi-store（`reference/komi-store-main`）为知识蓝本，移植其三通道实现并适配 LIDE 单模块架构。

### 关键决策（调研报告推荐，已按默认采纳执行）

| 决策 | 内容 |
|------|------|
| D-1 | UI 位置：AndroLua 设置页「打包选项」区新增「安装方式」组（单选 + 状态徽标 + 授权按钮/提示行）；构建打包对话框新增「本次安装方式」（默认跟随设置，本次可临时覆写，不回写持久化） |
| D-2 | 默认行为：`installBackend = DEFAULT`（系统安装器，pro 兼容），**不自动静默**——静默仅当用户显式选中且后端 READY 时启用 |
| D-3 | 安装归属（attribution）：默认不设置（komi 同款 SystemDefault）；Dhizuku 通道 Android 12+/14+ 更新归属门返回 `-2 (PENDING_USER_ACTION)` 时**回落系统安装器**（用户确认），不强制去归属重试（attribution 为空时无重试价值） |
| D-4 | HostPermissionScreen 权限页**不展示**三通道（Shizuku/Dhizuku 非系统运行时权限，Root 非权限体系），状态与授权入口仅在设置页「安装方式」组呈现 —— **2026-08-10 用户红线反转（T15）**：权限管理页新增「高级权限」区（section 恒等 T15，语义经 D-7 校正），三通道状态徽标 + 授权入口 + 不可用指引，与设置页共享同一 UI 组件（InstallBackendUi） |
| D-5 | 状态检测/安装执行全部后台线程 + `try/catch(Throwable)` 降级（AGENTS.md 编辑器降级约定同族）；UI 只读 `StateFlow` |
| D-6 | **通用安装入口（2026-08-10 用户需求 2，T16）**：除打包产物安装外，系统「用 LIDE 打开 APK」（VIEW + `application/vnd.android.package-archive` intent-filter）同样弹出安装方式选择对话框（InstallMethodDialog，初始值跟随设置页持久化，本次选择不回写），静默失败/未选后端一律回落系统安装器 |
| D-7 | **权限与功能分离口径（2026-08-10 用户澄清）**：Root / Shizuku / Dhizuku 是**权限**（授予 LIDE 本体的能力），「安装」是**功能**（消费权限的场景）。权限页只呈现权限授予（section「高级权限」+ 状态/授权/指引，不出现「安装」字样）；安装功能（通道选择）只出现在设置页「安装方式」与安装/构建对话框。二者共享同一后端状态源（InstallBackendUi），但不得互相命名为对方语义 |

---

## 2. 架构设计

### 2.1 新包结构 `com.lioisme.lide.install`（宿主侧，符合 R3/R4）

```
app/src/main/kotlin/com/lioisme/lide/install/
├── InstallerBackend.kt            # enum DEFAULT/SHIZUKU/DHIZUKU/ROOT + parse 回落（BuildOpts 持久化用）
├── BackendStatus.kt               # 四态枚举 UNAVAILABLE/NOT_RUNNING/PERMISSION_NEEDED/READY（三通道统一）
├── InstallerCoordinator.kt        # 纯函数 resolveBackend + installSilently 降级链（IO 线程 + try/catch）
├── InstallerServices.kt           # object 运行时：init(context) 幂等 + 三 ServiceManager + 状态快照
├── root/RootServiceManager.kt     # libsu Shell 授权记忆 + cp/pm install
├── shizuku/ShizukuServiceManager.kt
├── shizuku/ShizukuInstallerServiceImpl.kt
├── dhizuku/DhizukuServiceManager.kt
└── dhizuku/DhizukuInstallerServiceImpl.kt
app/src/main/aidl/com/lioisme/lide/install/
├── IShizukuInstallerService.aidl # installPackage(pfd,size,attribution) / uninstallPackage / destroy
└── IDhizukuInstallerService.aidl # installPackage(pfd,size,expectedPkg,expectedVc,attribution) / ...
```

### 2.2 状态机（三通道统一映射）

| 状态 | Shizuku | Dhizuku | Root |
|------|---------|---------|------|
| UNAVAILABLE | 未安装（shizuku/sui 包缺失） | 未安装（dhizuku 包缺失）或 SDK<26 | 无 su/未知（NOT_AVAILABLE） |
| NOT_RUNNING | 已安装但 binder 死 | 已安装但 init 失败 | —（不存在） |
| PERMISSION_NEEDED | binder 活但未授权 | init 成功但未授权 | su 管理器未授予 |
| READY | 已授权 | 已授权 | shell.isRoot |

### 2.3 调度链（安装）

```
BuildPackagingDialog.startBuild → AfterBuild.INSTALL
  → InstallerCoordinator.installSilently(apkFile, backend)（installMethod 本次选择，默认跟随设置）
      ├─ backend = DEFAULT → 直接回落系统安装器
      ├─ SHIZUKU → getService() → pm install -S <size>（stdin 管道）→ 0 成功
      ├─ DHIZUKU → PackageInstaller Session（USER_ACTION_NOT_REQUIRED + 超时核验）→ 0 成功；-2 → 回落
      ├─ ROOT    → cp /data/local/tmp + pm install -r → 0 成功
      └─ 任一异常/非 0 → 回落 installApk()（ACTION_INSTALL_PACKAGE 系统安装器，现状不变）
```

### 2.4 关键机制（移植自 komi-store，全部保留）

- **Root 授权记忆**：`SharedPreferences("lide_root", granted_before)`，防每次冷启动重复弹 su；真实吊销由下次 install 拉 shell 时懒惰纠正；状态检测**不使用 `Shell.isAppGrantedRoot()`**（libsu 会实际创建主 shell 弹 su 确认），仅用 `getCachedShell()` 无提示判定，真实状态由「授权按钮 / 安装执行」拉主 shell 时懒惰纠正；
- **Shizuku UserService**：`daemon(false)` + `processNameSuffix("installer")` + `version(1)`；`getService()` Mutex + 15s 超时绑定；`-i` 归属可选（D-3 默认 null）；
- **Dhizuku**：`ActivityThread.currentApplication` 反射取 Context；`RECEIVER_NOT_EXPORTED` 注册（API 33+）；超时后 `verifyInstallSucceeded` 包版本核验兜底；SDK_INT<26 恒 UNAVAILABLE（API 库 minSdk 26，manifest `tools:overrideLibrary` 强制合并）；
- **包名注入防护**：`-i` / `pm uninstall` 参数过 `PACKAGE_NAME_PATTERN`（正则校验），防命令注入；
- **安装判定**：exitCode==0 且 stdout 含 `Success`。

---

## 3. 实施任务表（按开发顺序）

| # | 任务 | 涉及文件 | 验收 | 状态 |
|---|------|---------|------|------|
| T1 | 文档登记（backlog + 本工作单） | `docs/planned/backlog.md`、本文件 | 任务登记到位 | 已完成 |
| T2 | 依赖与构建：shizuku-api/provider 13.1.5、hidden-api-stub 4.4.0（compileOnly）、dhizuku-api 2.5.4、libsu-core 6.0.0；`buildFeatures { aidl = true }`；jitpack 仓库；Dhizuku minSdk 26 处理（overrideLibrary + SDK 门控） | `app/build.gradle.kts`、`settings.gradle.kts`、`AndroidManifest.xml`、`DhizukuServiceManager.kt` | compile 通过；verify 门禁不受影响 | 已完成 |
| T3 | AIDL 接口 ×2 | `app/src/main/aidl/com/lioisme/lide/install/` | aidl 生成类可编译 | 已完成 |
| T4 | 服务实现 ×2（pm 管道 / PackageInstaller Session） | `install/shizuku/ShizukuInstallerServiceImpl.kt`、`install/dhizuku/DhizukuInstallerServiceImpl.kt` | 移植完整，含超时/核验/降级 | 已完成 |
| T5 | 状态枚举 + 三个 ServiceManager + InstallerServices 运行时 | `BackendStatus.kt`、`root/RootServiceManager.kt`、`shizuku/ShizukuServiceManager.kt`、`dhizuku/DhizukuServiceManager.kt`、`InstallerServices.kt` | 状态流正确；init 幂等；LideApp.onCreate 接入 | 已完成 |
| T6 | InstallerCoordinator（resolve 纯函数 + 静默安装降级链） | `InstallerCoordinator.kt` | 纯逻辑可单测；非 0/异常回落 | 已完成 |
| T7 | 设置持久化：`BuildOpts.installBackend` + SettingsManager 键 | `build/BuildOpts.kt`、`ui/theme/SettingsManager.kt` | 读写回落正确 | 已完成 |
| T8 | proguard keep（install 包 + rikka + dhizuku api） | `app/proguard-rules.pro` | release R8 不混淆反射实例化类 | 已完成 |
| T9 | manifest：dhizuku 权限 + queries + ShizukuProvider | `app/src/main/AndroidManifest.xml` | 打包器 renameAuthorities 自动改写 authority | 已完成 |
| T10 | 文案（en + zh 双份） | `res/values/strings.xml`、`res/values-zh/strings.xml` | 双语齐全 | 已完成 |
| T11 | UI：设置页「安装方式」组 + 构建对话框「本次安装方式」 | `ui/screen/AndroLuaSettingsScreen.kt`、`ui/screen/BuildPackagingDialog.kt` | 单选/徽标/授权按钮/置灰逻辑正确 | 已完成 |
| T12 | 单元测试（parse 回落 / resolveBackend / 模式校验） | `app/src/test/kotlin/com/lioisme/lide/install/` | JVM 测试全绿 | 已完成 |
| T13 | 验证：compile + 全量相关单测 + verifyMinimalismDebug | — | 见 §4 | 已完成 |
| T14 | 设备实测 + kb 蒸馏 + 收卷（整单完成后） | `docs/kb/` | 三链路真机/模拟器验证后蒸馏 | 未开始 |
| T15 | 权限页「高级权限」区（D-4 反转 + D-7 语义校正）：三通道状态 + 授权 + 指引（权限语义，无「安装」命名）；共享 UI 组件抽取（状态文案/指引/系统安装器入口三处复用） | `ui/screen/InstallBackendUi.kt`（新）、`ui/screen/PermissionScreen.kt`、`ui/screen/AndroLuaSettingsScreen.kt`、`ui/screen/BuildPackagingDialog.kt` | 权限页三通道可见可授权；设置页/对话框行为不变；compile 通过 | 已完成 |
| T16 | 通用 APK 安装入口：MainActivity VIEW .apk + manifest intent-filter + 安装方式选择对话框（静默回落系统安装器） | `ui/screen/InstallMethodDialog.kt`（新）、`MainActivity.kt`、`AndroidManifest.xml`、`InstallBackendUi.kt` | 系统「用 LIDE 打开 APK」→ 选方式安装；compile 通过 | 已完成 |

---

## 4. 门禁与验收标准

| 阶段 | 命令 / 动作 | 期望 |
|------|------------|------|
| 编译 | `.\gradlew.bat :app:compileArm64DebugKotlin --console=plain` | 通过 |
| 单测 | `.\gradlew.bat :app:testArm64DebugUnitTest --tests "com.lioisme.lide.install.*" --tests "com.lioisme.lide.build.*" --console=plain` | 全绿 |
| 门禁 | `.\gradlew.bat :app:verifyMinimalismDebug --console=plain`（debug 直出 dex 断言） | 通过（新依赖并入单 dex 集，引擎面类恰一份不受影响） |
| 设备 | Shizuku（装 shizuku/SUI 授权）、Dhizuku（`adb shell dpm set-device-owner`）、Root（magisk）三链路：授权按钮 → READY → 打包安装走静默；撤权限 → 自动回落系统安装器 | 三链路 + 降级链路 |

---

## 5. 风险与对策

| 风险 | 说明 | 对策 |
|------|------|------|
| R8 反射实例化 | Shizuku/Dhizuku UserService 按类名反射加载服务实现，R8 收缩/混淆即 NoSuchMethodError | T8 整包 keep（含 AIDL Stub 类） |
| ShizukuProvider authority 冲突 | 打包产物复用宿主 manifest 模板，authority 若带宿主包名前缀 | `AXmlManifest.patch.renameAuthorities`（既有机制）自动改写为 `<newPackage>.shizuku`，无需新逻辑 |
| Android 11+ 包可见性 | 未声明 `<queries>` 时 `getPackageInfo` 检测失效 | manifest 显式 `<queries>` 四条目（dhizuku 包 + provider + shizuku 包 + sui 包） |
| Android 14+ 更新归属门 | 目标包原安装者为 Play 等其它来源时 session 提交返回 PENDING_USER_ACTION | Dhizuku 返回 `-2` 即回落系统安装器（D-3），不静默强装 |
| 静默失败体验 | 静默安装失败不得阻塞/误导用户 | 一律回落系统安装器（现状路径），不打断 |
| Dhizuku 低版本 API | minSdk 24 上 PackageInstaller 无 `setRequireUserAction` | API 31+ 才调用（编译期门控）；库 minSdk 26 经 overrideLibrary 合并 + SDK_INT<26 运行时门控 |
| 上游隔离 / 精简门禁 | 新依赖只进 `:app` | `verifyVendorIsolation`、`verifyMinimalism` 重跑确认不受影响 |
| libsu 依赖源 | `com.github.topjohnwu.libsu` 仅 JitPack 发布 | `settings.gradle.kts` 统一仓库收归处补 jitpack.io（FAIL_ON_PROJECT_REPOS 不受影响） |

---

## 6. 完成后处理

整单完成后：结论蒸馏进 `docs/kb/`（ENGINE_RUNTIME.md 或新增安装能力小节），AGENTS.md 如涉及新硬性约定同步固化，然后删除本工作单（git 历史保留原文）。

---

## 7. 变更记录

| 日期 | 变更 | 备注 |
|------|------|------|
| 2026-08-09 | 工作单创建 + backlog F10 登记 | 调研报告（Root/Shizuku/Dhizuku 三通道分析）已产出，决策 D-1~D-5 采纳 |
| 2026-08-10 | T2~T13 实施完成（依赖/仓库/overrideLibrary/SDK 门控、AIDL、双服务实现、三管理器、协调器、持久化、proguard、manifest、文案、双 UI 入口、单测、验证） | 见 §8；T14 设备实测待做 |
| 2026-08-10 | T15/T16 实施完成（D-4 反转：权限页「安装特权」区；D-6：通用 APK 打开 → 安装方式选择对话框） | 见 §8；T14 设备实测待做 |
| 2026-08-10 | 权限与功能分离语义校正（D-7）：权限页 section 更名「高级权限」（`perm_privilege_section`，去「安装」字样）+ 新增提示文案（权限归属 + 安装功能入口指向）；UI/字符串/工作单/backlog 同步 | 用户澄清；见 §8 T15 补注 |
| 2026-08-10 | 权限页高级权限 UI 重构（用户反馈「太丑」+「不写大量描述性文本」）：行改 Card+图标风格（与权限页其余行一致），删除 desc/指引长句与 section 提示，`perm_privilege_hint` 字符串移除 | 见 §8 T15 补注 |
| 2026-08-10 | 设置/权限/对话框 UI 统一收口（用户四需求）：①权限页「高级权限」行副标题化（与 PermissionRow 布局完全一致）；②AndroLua 设置页与主设置/编辑器设置同款行样式（图标+标题+副标题+尾部控件，去 HorizontalDivider 与 section 提示）；③长描述文本全部删除（desc/指引/提示字符串 11 条移除）；④安装方式未获对应权限（非 READY）一律不可选（设置页 RadioButton、构建对话框、安装对话框三处同口径），静默失败仍回落系统安装器 | 见 §8 T15 补注；T14 设备实测待做 |

---

## 8. 完成记录（逐任务标记）

<!-- 每完成一项任务在此追加：日期 + 工作内容摘要 + 验收证据。 -->

- **T1（已完成，2026-08-09）**：backlog.md 登记 F10（SILENT_INSTALL）+ 本工作单创建（含决策 D-1~D-5、架构设计、任务表）。验收证据：`docs/planned/backlog.md` F10 行、本文件。
- **T2（已完成，2026-08-09）**：`app/build.gradle.kts` 新增 shizuku-api 13.1.5 / shizuku-provider 13.1.5 / hidden-api-stub 4.4.0（compileOnly）/ dhizuku-api 2.5.4 / libsu-core 6.0.0，并开启 `buildFeatures { aidl = true }`；依赖全部 `implementation`（单 dex 集形态，无 runtime 拆分）。配套：`settings.gradle.kts` 补 jitpack.io 仓库（libsu 仅 JitPack 发布，统一收归 dependencyResolutionManagement）；Dhizuku-API minSdk 26 > 宿主 24，manifest 加 `tools:overrideLibrary`（`<uses-sdk>` 元素，合并器契约位置）+ `DhizukuServiceManager` SDK_INT<26 门控（恒 UNAVAILABLE）。验收证据：`compileArm64DebugKotlin` 通过（见 T13 汇总）。
- **T3（已完成，2026-08-09）**：AIDL 接口 `IShizukuInstallerService.aidl` / `IDhizukuInstallerService.aidl`（installPackage(pfd,fileSize,expectedPkg?,expectedVc?,attribution?) / uninstallPackage / destroy）。验收证据：aidl 生成类经 T13 编译。
- **T4（已完成，2026-08-09）**：`ShizukuInstallerServiceImpl`（pm install stdin 管道：`-S <size>` + `-r` + 可选 `-i`；`destroy()` 退出进程）与 `DhizukuInstallerServiceImpl`（PackageInstaller Session：USER_ACTION_NOT_REQUIRED、API31+ setRequireUserAction(false)、结果广播接收器、120s 超时 + `verifyInstallSucceeded` 包名/版本核验兜底、-2 标记 PENDING_USER_ACTION_REQUIRED 回落；`PENDING_INTENT_FLAGS` 并入既有 companion）。验收证据：T13 编译 + 单测。
- **T5（已完成，2026-08-09）**：`BackendStatus` 四态枚举；`RootServiceManager`（libsu 主 shell 配置、授权记忆 prefs、无提示 getCachedShell 检测——**不使用 `Shell.isAppGrantedRoot()`**（会弹 su），真实授权经授权按钮/安装执行拉主 shell 懒惰纠正）；`ShizukuServiceManager`（shizuku/sui 双包检测、权限监听、Mutex+15s 绑定）；`DhizukuServiceManager`（包检测 + init + 授权 + 绑定，SDK 门控）；`InstallerServices`（object，init 幂等 + 三管理器 + `appContext`）；`LideApp.onCreate` 接入。验收证据：T13 编译。
- **T6（已完成，2026-08-09）**：`InstallerCoordinator`：`resolveBackend` 纯函数（DEFAULT 恒回落；检测回调异常降级 DEFAULT）+ `installSilently`（IO 线程，四后端分发，`try/catch(Exception)` 全降级返回 false；Dhizuku `-2` 视为失败回落；install 归属默认 null）。验收证据：`InstallerCoordinatorTest` 6 用例全绿。
- **T7（已完成，2026-08-09）**：`BuildOpts.installBackend: InstallerBackend = DEFAULT` + `SettingsManager` 键 `install_backend`（读写 + `InstallerBackend.parse` 回落）。验收证据：T13 编译；`InstallerBackendTest` 3 用例全绿。
- **T8（已完成，2026-08-09）**：`app/proguard-rules.pro` 新增 `com.lioisme.lide.install.**` / `rikka.shizuku.**` / `com.rosan.dhizuku.api.**` 整包 keep + hidden/dhizuku dontwarn。验收证据：T13 编译（release R8 真机验证归 T14）。
- **T9（已完成，2026-08-09）**：manifest 新增 `com.rosan.dhizuku.permission.API` 权限 + `<queries>`（shizuku/sui/dhizuku 包 + dhizuku provider）+ `rikka.shizuku.ShizukuProvider`（authority `com.lioisme.lide.shizuku`，打包器 renameAuthorities 自动改写）+ `tools:overrideLibrary`（Dhizuku-API minSdk 26）。验收证据：processArm64DebugMainManifest 通过。
- **T10（已完成，2026-08-09）**：`values/strings.xml` + `values-zh/strings.xml` 双份 19 条（install_method_*/install_backend_*/install_dialog_method_*）。验收证据：T13 编译（aapt 资源合并通过）。
- **T11（已完成，2026-08-09）**：设置页 `AndroLuaSettingsScreen` 新增「安装方式」SettingsSection（四后端单选 CheckboxRow + 状态徽标（圆点 + READY/PERMISSION_NEEDED/NOT_RUNNING/UNAVAILABLE）+ PERMISSION_NEEDED 时「授权」按钮 + 不可用态指引文案）；构建对话框 `BuildPackagingDialog` 新增「安装方式（本次构建）」局部状态（默认跟随设置、UNAVAILABLE 置灰、行尾状态徽标），成功分支按 `installMethod` 经 `InstallerCoordinator.installSilently` 分发，false 回落 `installApk()` 系统安装器（原路径）。验收证据：T13 编译。
- **T12（已完成，2026-08-09）**：`app/src/test/kotlin/com/lioisme/lide/install/InstallerBackendTest.kt`（parse 回落契约 3 用例）+ `InstallerCoordinatorTest`（resolveBackend 6 用例：DEFAULT 恒回落 / READY 命中 / 不可用回落 / 回调短路 / 回调异常降级 / 状态枚举）。期间发现 `resolveBackend` 未对 isReady 回调异常降级，已补 try/catch（D-5）。验收证据：`testArm64DebugUnitTest --tests "com.lioisme.lide.install.*"` 9 用例全绿。
- **T13（已完成，2026-08-09）**：`.\gradlew.bat :app:compileArm64DebugKotlin` 通过（仅既有弃用警告）；`testArm64DebugUnitTest --tests "com.lioisme.lide.build.*"` 92 用例全绿（打包/签名/加密链无回归）；`testArm64DebugUnitTest --tests "com.lioisme.lide.install.*"` 9 用例全绿；`:app:verifyMinimalismDebug` 通过（新依赖并入单 dex 集，引擎面类恰一份断言不受影响；测试 JVM 在本机偶发 native 内存崩溃，`--max-workers=1` 后稳定）。验收证据：上述命令输出。
- **T14（未开始）**：设备实测（三链路：Shizuku/SUI、Dhizuku dpm set-device-owner、Root magisk）+ release R8 真机验证 + kb 蒸馏 + 整单收卷。
- **T15（已完成，2026-08-10，含 D-7 语义校正 + UI 重构补注）**：权限管理页新增「高级权限」区（`perm_privilege_section`：en "Advanced permissions" / zh 「高级权限」，**权限语义命名，不含「安装」字样**），三通道（Shizuku/Dhizuku/Root）经 `InstallBackendStatusRow` 展示状态徽标 + PERMISSION_NEEDED「授权」按钮（状态经各后端 StateFlow 实时回流，授权页返回经广播回调自刷）。**2026-08-10 用户反馈 UI 重构**：行组件改为与权限页其余行一致的 Card + 图标风格（READY → CheckCircle primary / 其余 → Error error）+ 标题 + 短状态词 + 「授权」FilledTonalButton，删除描述行与不可用指引长句、删除 section 长提示（`perm_privilege_hint` 字符串移除）——界面不保留大量描述性文本。配套抽取共享件 `ui/screen/InstallBackendUi.kt`：`installBackendStatusLabel`（状态文案）、`backendHintRes`（不可用指引，设置页保留使用）、`InstallBackendStatusRow`（权限页行）、`installApkViaSystem`（ACTION_INSTALL_PACKAGE 入口，自 BuildPackagingDialog 私有函数提升）——设置页（`InstallBackendRow` 内联状态文案/指引删除）、构建对话框（`installBackendStatusText` 私有函数删除、`installApk` 私有函数删除）全部改走共享件，单一事实源。新增文案：`perm_privilege_section` / `install_confirm` / `install_success` / `install_failed` / `install_apk_title`（en+zh）。验收证据：`.\gradlew.bat :app:compileArm64DebugKotlin --console=plain` 通过（重构后再次通过）。
- **T16（已完成，2026-08-10）**：通用 APK 安装入口（D-6）——`AndroidManifest.xml` MainActivity 新增两个 intent-filter（content/file + `application/vnd.android.package-archive`）；`MainActivity.handleLaunchIntent` 新增 `.apk` 分支（`pendingApkFile` Compose state，一次性消费语义同 pendingImportPaths）；新建 `ui/screen/InstallMethodDialog.kt`（对话框标题「安装安装包」+ 文件名 + 安装方式选择（初始值 = 设置页持久化 `BuildOpts.installBackend`，本次选择不回写）+ 状态徽标 + 「安装」执行：非 DEFAULT 且后端静默成功 → `install_success` toast，否则回落 `installApkViaSystem`）。验收证据：`.\gradlew.bat :app:compileArm64DebugKotlin --console=plain` 通过。
- **UI 统一收口（2026-08-10，用户四需求）**：①`InstallBackendStatusRow`（InstallBackendUi.kt）改与 `PermissionRow` 完全同款布局——状态词从行尾移至标题下副标题位（READY primary / 其余 error），行不再整体可点（仅「授权」按钮交互），高级权限与特殊/常规权限视觉一致；②AndroLua 设置页四区全部改用主设置/编辑器设置同款行样式：删除 section 顶部提示与 HorizontalDivider，「安装方式」改 `InstallBackendRadioRow`（新共享件，图标+标题+状态副标题+尾部 RadioButton，PERMISSION_NEEDED 时附带「授权」按钮），「签名方案」「默认 ABI」改 `CheckboxSettingRow`（图标+标题+尾部 Checkbox，同 SettingsToggleItem 布局），旧 `CheckboxRow`/`InstallBackendRow` 私有组件删除；③长描述文本全删：`install_method_hint`、`install_method_*_desc` ×4、`install_backend_hint_*` ×4、`build_opt_scheme_hint`、`build_opt_abi_hint`、`install_dialog_method_hint` 共 11 条双语字符串移除，`backendHintRes` 随指引删除；④安装方式未获对应权限一律不可选：设置页 `enabled = status == READY`，构建对话框与安装对话框 `available = READY`（原为仅 UNAVAILABLE 置灰）——PERMISSION_NEEDED 仅剩「授权」动作，授权完成 StateFlow 回流后该行自动可选项；静默失败回落系统安装器链路不变（`InstallerCoordinator.installSilently` 对非 READY 已返回 false）。验收证据：`.\gradlew.bat :app:compileArm64DebugKotlin --console=plain` BUILD SUCCESSFUL（仅既有弃用警告）；T14 设备实测待做。
