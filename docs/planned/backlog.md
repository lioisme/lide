# 计划中任务 & 技术债清单（Backlog）

> **状态**：📋 计划中
> **用途**：登记未启动的任务、已知技术债、遗留风险
> **新任务登记处**：任何新任务先写在这里，带关键词便于 Agent 检索
> **2026-08-05 清理**：TD1（keystore.properties 提取）、TD2（targetSdk 36）、TD6（jetifier 移除）、TD7（nonTransitiveRClass=true 已开）、F4（ui/old/ 已删除）、wiki/ 与 build_all.ps1 引用（均已删除）从清单移除。

---

## 1. 技术债（来自 Java→Kotlin 迁移附录 A，已对照代码复核）

| ID | 问题 | 位置 | 严重度 | 处置建议 |
|---|---|---|---|---|
| TD3 | `LuaDialog.kt:61` 的 `cancel()` 调 `super.dismiss()` 而非 `cancel()` | `androlua/.../LuaDialog.kt` | 低 | 确认为有意行为则加注释留档 |
| TD4 | 对话框 listItemLayout 字段仅构造期使用 | `LuaDialog.kt` 构造函数 | 低 | 内联或删除 |
| TD5 | Manifest 权限声明过多（大量系统权限） | `app/src/main/AndroidManifest.xml` | 低 | 审计并移除未用权限（先做运行时行为确认） |

## 2. 功能/质量待办（Agent 认领用）

| # | 任务 | 优先级 | 关键词 |
|---|---|---|---|
| F1 | **补全 Kotlin 公有 API 的 KDoc 文档注释**（3 个 .kt 文件 + 全部 companion 成员） | 中 | `KDoc` `LuaDialog` `LuaMaterialDialog` `AlertControllerBridge` |
| F2 | 审计 Phase 4 构建验证：`.\gradlew.bat :app:assembleArm64Debug` + `assert` 打包断言脚本检查 | 高 | `assembleDebug`、验收 |
| F3 | 设备功能实测（Gate 3 场景：启动/项目列表/下拉刷新/打开/长按/返回） | 高 | 验收、设备测试 |
| F5 | 考虑新 UI 依赖方向：`main.lua` 中 `AlertDialogBuilder = MaterialAlertDialogBuilder` 的全局替换完整度检查 | 低 | 全局变量、Material3 |
| F6 | **R8 收缩 + baseline profile**（V2 报告阶段 A 遗留） | 中 | `R8` `baseline profile` `ProGuard` |
| F7 | **性能优化阶段 C**：诊断 tooltip、原生 SnippetController、增量保存 | 中 | `SnippetController`、增量保存、诊断 tooltip |
| F8 | **编辑器功能完善至 sora-editor 原生上限**（历史任务单 EDITOR_FEATURE_COMPLETION_WORK_ORDER，已蒸馏）：P0 设置中心 21 项全集（通用设置页仅留入口）/ P1 原生 SnippetController·诊断 tooltip·多级聚合·折叠验证·文本操作增强·整词搜索·标识符补全·迷你地图 / P2 R8+baseline profile·降级矩阵扩展·基准脚本 / P3 R1 grep 入 CI。**后注（2026-08-09）**：其中「整词搜索」（搜索/替换面板）已按用户红线整体移除（见 docs/kb/EDITOR.md §4.5），其余保持已落地 | 高 | `编辑器设置`、`SnippetController`、`诊断 tooltip`、`迷你地图`、`baseline profile` |
| F9 | **文件选择器（✅ 完结，2026-08-09 用户红线反转→整体废弃）**：阶段一/二内建全盘选择器 + 双引擎开关曾落地（见工作单 FILE_PICKER_WORK_ORDER.md，已归档删除）。**反转**：内建选择器（`ui/picker` 包/Routes.PICKER/PickerBridge/双引擎偏好）与编辑器文件树对话框全部删除，文件选择一律走系统 SAF（.alp 批量导入 `OpenMultipleDocuments` + copyUriToTemp；签名导入 `OpenDocument` 直读）；编辑器不再打开其他文件、仅自动打开 main.lua。结论蒸馏见 docs/kb/EDITOR.md §4.4 | 已废弃 | `FILE_PICKER`、文件选择器、SAF |
| F10 | **静默安装能力（Root / Shizuku / Dhizuku）**：① 宿主支持申请 Root（libsu）、Shizuku（含 SUI）、Dhizuku 三类**权限**（权限管理页「高级权限」区呈现授权，D-7：权限与功能分离，不与安装功能混称）；② 打包产物安装可选用静默安装（免逐次确认），不可用/失败自动回落系统安装器（**安装为功能**：设置页「安装方式」组 + 构建对话框「本次安装方式」）；③ **通用 APK 安装（2026-08-10 T16）**：系统「用 LIDE 打开 APK」→ 安装方式选择对话框（普通/三通道，静默失败回落系统安装器）。持久化 `BuildOpts.installBackend`。见工作单 docs/in-progress/SILENT_INSTALL_WORK_ORDER.md | 高 | `SILENT_INSTALL`、静默安装、`Shizuku`、`Dhizuku`、Root、libsu |

## 2b. 技术栈审计重构（2026-08-09 全量审计，工作单 docs/in-progress/TECH_DEBT_REFACTOR_WORK_ORDER.md）

| # | 任务 | 优先级 | 关键词 |
|---|---|---|---|
| R1 | 引擎面三处同步收敛（build.gradle / proguard / 分类器）为单一事实源 + 修复 AlertDialogBuilder 分类 | 高 |（已完成）|
| R2 | ApiIndex dexNames 契约对齐：O(1) 可加载性查表 + 宿主 dex 快照进程级缓存 + 修订 AGENTS.md 措辞 | 高 |（已完成）|
| R3 | 原生模块表收敛：ApiIndex 补全 lfs/lsqlite3/live2d/luajava + cpp 目录一致性测试 | 高 |（已完成）|
| R4 | apibrowser 测试补位（分类器/过滤/缓存契约） | 高 |（已完成）|
| R5 | LuaDiagnostics 热路径（close 序列预计算 / errorAt 行偏移表 / countLines 增量） | 中 |（已完成）|
| R6 | 机械去重（basename / randomPassword / 错误前缀正则 / u16u32 / 主题名 / 种子色 / onOff / 权限池 / main.lua / 签名 Triple / catch Throwable） | 中 |（已完成）|
| R7 | SigningScreen 主线程密码学/文件 IO 异步化（6 处） | 中 |（已完成）|
| R8 | navigation-compose 升级 2.9.8 后删除 NavigationGate（缺陷根除回归） | 中 |（已完成）|
| R9 | 依赖栈批量升级：BOM 2026.06.00 / lifecycle 2.10 / activity-compose 1.13 / core-ktx / coroutines-test | 中 |（已完成）|
| R10 | LuaCrypto 手写 PKCS#7 → 官方 PKCS5Padding（先核 C 端规约） | 低 | `PKCS5` `DES` |
| R11 | **ktlint 全量红线（2026-08-09 复核）：`ktlintCheck` 既有违规约 1000 处（LanguageKeywordRegistry 838 / AXmlManifest 83 / LuaConfigParser 65 / LuaCompiler 等），`lint` 任务长期阻断；原工作单 T12「ktlint 全绿」断言不成立。建议整仓格式化专项或配置 baseline 后回归** | 中 | `ktlint` `lint` |

## 2c. 代码质量整改行动（2026-08-09 全量审计，工作单 docs/in-progress/CODE_QUALITY_ACTION_WORK_ORDER.md）

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| C1 | Q1–Q5 阶段一：数据安全与卡顿止血（轮询改门控 / 搜索移 Default / 空 catch 治理 / 签名档案备份 / 正则收口+去重） | 高 | ✅ 已完成 2026-08-09 | `CODE_QUALITY_ACTION` `Q1`–`Q5` |
| C2 | Q6–Q10 阶段二：架构补课（线程契约 / 门面接口 / ViewModel / 参数化+测试 / 函数拆分） | 高 | ✅ 已完成 2026-08-09 | `Q6`–`Q10` |
| C3 | Q11–Q13 阶段三：性能上限（诊断热路径 / 详情缓存 / 单例收敛） | 中 | ⏸ 进行中 | `Q11` `Q12` `Q13` |
| C4 | Q14–Q16 阶段四：规范收敛（文案资源化 / 诊断 i18n / P2 清理 14 项） | 中 | ⏸ 进行中 | `Q14` `Q15` `Q16` |
| C5 | Q17 终验：全量单测 + compile + verifyMinimalismDebug | 高 | ⏳ 待执行 | `Q17` |

## 2d. 编辑器全量审计整改（2026-08-10，已完工：工作单已删，结论蒸馏进 docs/kb/EDITOR.md §9）

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| A1 | T1 增量诊断收敛丢弃编辑点后「行局部错误」（不匹配右括号/多余 end）——收敛条件补 `lastErrorMaxLine` 门 | 高 | ✅ 已完成 2026-08-10 | `LuaDiagnostics` `收敛` `错误消失` |
| A2 | T2 同字节长度文件切换后诊断永不出现——`onNewText` 未复位 `lastCheckedLength` | 高 | ✅ 已完成 2026-08-10 | `LuaDiagnostics` `同长切换` |
| A3 | T3 自动保存只落盘当前选中标签——防抖改 `flushAllDirty`（脏标签全量） | 高 | ✅ 已完成 2026-08-10 | `EditorSession` `自动保存` `进程被杀丢数据` |
| A4 | T4 跳行/插 import 与池化编辑器内容切换竞态——按 Content 身份绑定后定位 | 中 | ✅ 已完成 2026-08-10 | `EditorScreen` `跳行竞态` |
| A5 | T5 EditorViewPool 存活期强引用最后 Content+undo 栈——onDispose 剥离文本 | 中 | ✅ 已完成 2026-08-10 | `LuaEditor` `视图池` `内存` |
| A6 | T6 AGENTS.md R2 / EditorToolbar 注释「搜索」措辞与用户红线矛盾同步 | 低 | ✅ 已完成 2026-08-10 | `文档漂移` |
| A7 | T7 saveProjectScript 原子写（tmp+rename 兜底，含 writeInitFields） | 低 | ✅ 已完成 2026-08-10 | `ProjectManager` `原子写` |

## 2e. 编辑器菜单/撤销重做/设置上限整改（2026-08-10，工作单 docs/in-progress/EDITOR_MENU_UNDO_SETTINGS_WORK_ORDER.md）

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| M1 | T2 撤销/重做状态同步（SET_NEW_TEXT/attach 重算 + 点击显式重算 + readOnly 门控） | 高 | ✅ 已完成（2026-08-10） | `undo` `redo` `状态同步` |
| M2 | T3 Appbar 菜单重组 文件/编辑/工程 三分类 + 编辑器设置/AndroLua 设置直达 + 剪贴板四件套 + EditorSession.reload | 高 | ✅ 已完成（2026-08-10） | `菜单` `文件/编辑/工程` |
| M3 | T4 设置互斥（wordWrap↔stickyScroll）+ T5 上限扩展（字体族/行高/undo 栈容量/粘性行数） | 中 | ✅ 已完成（2026-08-10） | `编辑器设置` `sora 上限` |

## 2f. 全量审计整改二（2026-08-10，工作单 READONLY_SDK_THEME_WORK_ORDER 已完工删除，结论见 docs/kb/EDITOR.md §10）

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| R1 | 只读绕过修复：insertImport（运行错误对话框）与 toggleCase（文本操作）直写 Content 绕过 sora 门禁 | 高 |（已完成）| `readOnly` `insertImport` `toggleCase` |
| R2 | 只读切换 IME 同步（restartInput / hideSoftInput），修复切回可写键盘假死 | 高 |（已完成）| `readOnly` `InputConnection` |
| R3 | 只读 UX：编辑/保存菜单禁用 + 编辑器只读横幅提示 | 中 |（已完成）| `readOnly` `菜单禁用` `横幅` |
| R4 | 线号分割线宽度 / 固定行号（pinLineNumber）两项 sora 上限能力接线 | 中 |（已完成）| `setDividerWidth` `setPinLineNumber` |
| R5 | 打包 API 级别下移：AXmlManifest uses-sdk 解析/写回 + ManifestEdit + 构建对话框 minSdk 选项 + 测试 | 中 |（已完成）| `uses-sdk` `minSdkVersion` `打包` |
| R6 | WP-014 工程 theme 运行时消费（逐工程深色，编辑器文本区跟随） | 中 |（已完成）| `WP-014` `theme` `深色` |

## 2g. 编辑器对标 Squircle CE 整改（2026-08-11，工作单 docs/in-progress/EDITOR_SQUIRCLE_ALIGNMENT_WORK_ORDER.md）

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| S1 | 真只读重构：删 ReadOnlyBanner / 菜单置灰去只读 / 删冗余 hideSoftInput / ⋮ 菜单加「只读」直达切换 | 高 | ✅ 已完成（2026-08-12，工作单 P1 收口；只读下替换按钮静默实测） | `readOnly` `只读` `Squircle` |
| S2 | 去池化：删除 EditorViewPool，回归 remember+release 朴素生命周期 | 高 | ✅ 已完成（2026-08-11，工作单 P2；含 S6 闪退修复） | `EditorViewPool` `去池化` |
| S3 | 行号分界线：主题 JSON 加 divider 色键 + LideTextMateColorScheme + 固定 1dp 分割线 | 高 | ✅ 已完成（2026-08-11，工作单 P3） | `LINE_DIVIDER` `分割线` |
| S4 | 设置瘦身：删 7 项（粘性行数/lnPanel/pin/分割线宽度/光标动画/行高/undo 栈）27→20 | 中 | ✅ 已完成（2026-08-11，工作单 P4；全库 grep 零残留） | `编辑器设置` `瘦身` |
| S5 | 功能补缺：sora 原生 EditorSearcher 查找/替换面板 + 跳行对话框 | 中 | ✅ 已完成（2026-08-12，工作单 P5；含全部替换自实现修 BadTokenException；x64 模拟器全链路冒烟零 FATAL） | `EditorSearcher` `查找替换` `跳行` |

## 2h. 工程属性四项整改（2026-08-11，全部完成，结论已蒸馏进 docs/kb/EDITOR.md 与 ARCHITECTURE.md）

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| P1 | 工程信息改名「工程属性」+ 分组重排（基本/系统/高级/权限四 Card） | 高 | ✅ 已完成（2026-08-11，工作单 P1；`project_properties` 键 + 四分区 + 引用点同步） | `工程属性` `ProjectInfoScreen` |
| P2 | 主题下拉：反射 android.R.style `Theme_*` 全量 + 常见主题映射别名（pro 同款） | 高 | ✅ 已完成（2026-08-11，工作单 P2；ThemeCatalog + 16 别名 + EditorScreen WP-014 走 resolveDark） | `主题下拉` `ThemeCatalog` `android.R.style` |
| P3 | 目标 SDK（appsdk）+ 最低 SDK（minsdk）工程属性持久化，打包透传写回 manifest（AXmlManifest targetSdkVersion 双形态） | 高 | ✅ 已完成（2026-08-11，工作单 P3；ManifestEdit.targetSdkVersion 双形态写回 + ApkBuilder 透传 + 三单测） | `目标SDK` `最低SDK` `uses-sdk` `appsdk` |
| P4 | 权限清单全面化（宿主 manifest 51 项全覆盖）+ 搜索 + 常用权限别名（仅工程两处） | 高 | ✅ 已完成（2026-08-11，工作单 P4；PermissionPickerList 共享组件 + PermissionCatalog aliases + normalize 硬化） | `权限` `PermissionCatalog` `搜索` `别名` |
| P5 | 构建 APK 去配置对话框：⋮ 菜单直接构建（进度+结果对话框），配置以设置页持久化 + 工程属性为准 | 高 | ✅ 已完成（2026-08-11，工作单 P5；BuildApkDialog 打开即构建，ABI/签名/安装方式等移设置页持久化消费） | `直接构建` `BuildPackagingDialog` `BuildApkDialog` |
| P6 | 源码位置索引落地：docs/SOURCE_LOCATION.md + docs/README + AGENTS.md 挂接（禁止云端拉取） | 高 | 🔄 进行中 | `SOURCE_LOCATION` `禁云拉取` |
| S6 | 编辑器闪退修复（P2.1 构造期泄漏 this：createLanguage attachEditor(editor) 读未赋值属性 NPE） | 高 | ✅ 已完成（2026-08-11，工作单 T7；修复=attachTo 参数化；x64 模拟器暴力测试零崩溃） | `闪退` `attachEditor` `构造期` |
| S7 | 只读失效深层 bug：sora `isEditable()` 动态 getter（`editable && !layoutBusy && !isFormatting()`），AndroidView update 阶段 layoutBusy=true → diff 读 getter 得假 false 跳过 setter | 高 | ✅ 已完成（2026-08-11：`lastEditable` 快照 diff + EditorInstance 构造块直接 `setEditable(!initialConfig.readOnly)`；x64 模拟器双向验证：只读开=拒输入、关=可输入，实时切换生效；唯一受此 bug 影响项，其余 getter 均为纯字段） | `readOnly` `isEditable` `layoutBusy` |
| S8 | 菜单瘦身（用户拍板）：文件分类仅「文件树」占位按钮（点击 Toast「文件树即将推出」）+ 编辑分类仅「编辑器设置」；工程分类 4 项保留；删 EditorTextOps.kt 与 14 个字符串 | 中 | ✅ 已完成（2026-08-11：`:app:assembleX64Debug` 安装验证——三分类正确、文件树子菜单单项、Toast 实拍确认） | `菜单瘦身` `文件树` `EditorTextOps` |
| S9 | 编辑器字体对齐 Squircle CE 组合：`FONT_FAMILY_DEFAULT`/`MONOSPACE` 同映射 `Typeface.MONOSPACE`（Squircle `TypefaceProvider.DEFAULT` 同款），设置页字体选项 4→3（删系统默认） | 中 | ✅ 已完成（2026-08-11：设置页「字体=等宽」确认 + 编辑器截图等宽渲染/高亮正常） | `字体` `MONOSPACE` `Squircle` |

## 2i. 编辑器四需求整改（2026-08-11，工作单 docs/in-progress/EDITOR_THEME_FONT_FORMAT_WORK_ORDER.md）

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| F11 | **主题跟随**：编辑器颜色完全跟随主题系统（宿主派生色默认 + 独立主题集手选并存；17 键完整映射；色带解耦修工程 theme 割裂；补全窗口/tooltip 主题化） | 高 | 🔄 进行中（2026-08-12 T19 子项：AppBar/标签行回归宿主 Material3 色板、色带机制退役、FOLLOW 编辑器背景改 surfaceContainerHigh 科学层级色，见工作单阶段 5） | `主题跟随` `SquircleScheme` `EditorChromeColors` `LuaCompletionWindow` |
| F12 | **字体组合**：Squircle CE 字体组合（内置 JetBrains Mono 等 6 款 ttf + 外部导入 + 预览选择，R5 例外登记） | 高 | 🔄 进行中 | `字体` `JetBrains Mono` `ttf` `LuaFonts` |
| F13 | **性能优雅度**：补全窗口样式/诊断 tooltip 主题键/主题切换 UI（其余已对齐或超越 Squircle） | 中 | 🔄 进行中 | `补全窗口` `tooltip` |
| F14 | **语法格式化**：纯 Kotlin LuaFormatter（缩进重排+空白清理档位，零依赖）；⋮ 菜单 + Ctrl+L；单组撤销 | 高 | 🔄 进行中 | `格式化` `LuaFormatter` `LuaLexer` `Ctrl+L` |

## 2j. 权限与编辑器外观治理（2026-08-12，工作单 docs/in-progress/EDITOR_APPEARANCE_GOVERNANCE_WORK_ORDER.md；全部完成，结论已蒸馏进 kb/EDITOR.md §13 与 kb/ARCHITECTURE.md §4）

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| G1 | **权限与用途分离**：权限页「通用权限」区行标题/分区改名 `perm_*`；PermissionCatalog 移除 Dhizuku API 常规条目（含「静默安装」别名）；命名/文档同步 | 高 | ✅ 已完成（2026-08-12，工作单 T3；perm_* 命名 + 分区改名 + catalog 移除 + ARCHITECTURE.md §4 契约） | `EDITOR_APPEARANCE_GOVERNANCE` `权限分离` `perm_` |
| G2 | **编辑器外观单一跟随**：删除「编辑器主题」手选（设置项/EditorThemeSheet/EditorConfig.editorTheme/editor_theme 键）；LIGHT/DARK 三态残留收敛；工程 init.lua theme 不再影响宿主（projectDarkOverride 删除） | 高 | ✅ 已完成（2026-08-12，工作单 T1/T2；设置页零主题选项 + 三态代码删除 + EditorPalette 装配） | `EDITOR_APPEARANCE_GOVERNANCE` `单一跟随` `editor_theme` |
| G3 | **双模科学配色库**：LIDE Classic（quietlight/darcula 过 4.5:1 门禁）+ Solarized Dual + Tomorrow Dual 共 3 套 × 亮/暗；EditorPalette 单一事实源 | 高 | ✅ 已完成（2026-08-12，工作单 T4；EditorPalette 3 对 6 JSON + THEMES 白名单 6 全加载） | `EDITOR_APPEARANCE_GOVERNANCE` `Solarized` `Tomorrow` `对比度` |
| G4 | **对比度门禁**：EditorPaletteContrastTest 逐 token WCAG AA ≥4.5:1 | 高 | ✅ 已完成（2026-08-12，工作单 T5；门禁测试 3/3 通过） | `EDITOR_APPEARANCE_GOVERNANCE` `WCAG` |
| G5 | **文档收口**：AGENTS.md / kb/EDITOR.md / kb/ARCHITECTURE.md 同步；旧工作单 T17/T18 补勾 | 中 | ✅ 已完成（2026-08-12，工作单 T6/T7；AGENTS.md 治理段重写 + EDITOR.md §13 + ARCHITECTURE.md §4 + 旧工作单补勾） | `EDITOR_APPEARANCE_GOVERNANCE` `文档` |

## 2k. 主题系统与国际化系统规范合规审计整改（2026-08-12，工作单 docs/in-progress/THEME_I18N_SPEC_WORK_ORDER.md）

> 来源需求：主题系统与国际化系统须符合官方规范或社区习惯；全量审计违规/未实现项并整改。
> 用户拍板（2026-08-12）：① 编辑器配色**方案**可在编辑器界面选择（Squircle CE / Terminus 同款），
> 每套方案保持亮/暗双模配对、明暗跟随宿主自动切换（G2「单一跟随」红线相应调整）；
> ② 宿主 Material3 色板引入官方 `material-color-utilities`（HCT）替换手工 HSL 派生。

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| T1 | XML 基础主题 DayNight 双份（values-night）+ Android 12+ Splash Screen（values-v31 双份） | 高 | ✅ 已完成 | `THEME_I18N` `values-night` `splash` |
| T2 | 宿主色板 HCT 化（material-color-utilities）+ ThemeContrastTest 门禁（含编辑器 chrome 文本槽） | 高 | ✅ 已完成 | `THEME_I18N` `HCT` `对比度门禁` |
| T3 | 编辑器配色方案可选 + 双模跟随（EditorConfig/Registry/Palette/LuaEditor/SchemeSheet/TextMateRuntime 初值）+ AGENTS.md 红线改写 | 高 | ✅ 已完成 | `THEME_I18N` `SchemeSheet` `EditorPalette` |
| T4 | 国际化整改：默认资源中文（accessibility_service_description）/复数/中英模板标点/错误消息资源化/DEFAULT_PROVIDER 英文基线/版本号 BuildConfig | 高 | ✅ 已完成 | `THEME_I18N` `i18n` `plurals` `错误码` |
| T5 | per-app language（Android 13+ localeConfig + locales_config.xml，API 33+ setApplicationLocales 联动） | 中 | ✅ 已完成 | `THEME_I18N` `localeConfig` `per-app` |
| T6 | lint 配置（abortOnError + HardcodedText/MissingTranslation 等）+ ResourceConsistencyTest + LocaleManagerTest | 中 | ✅ 已完成 | `THEME_I18N` `lint` `资源一致性` |
| T7 | 全量验证（assembleArm64Debug + verifyMinimalism + 单测）+ 工作单收口蒸馏 | 高 | ✅ 已完成 | `THEME_I18N` `验收` |

> **完结（2026-08-12，全项完成）**：T1-T6 全部完成并通过门禁（compile +
> 全量单测 + lint 0 新问题 + assemble + verifyMinimalism(Debug) 全绿）；
> T7 设备验收已于 2026-08-12 在 Android 14 模拟器完成（中英 UI 双向、
> per-app locale 双向 + 重启保持、3 方案 × 明暗背景像素级核验、
> Splash 冷启动）。结论蒸馏于 docs/kb/EDITOR.md §14（编辑器配色方案
> 可选 + 双模跟随）与 docs/kb/ARCHITECTURE.md §7（HCT 色板 / i18n /
> per-app language / lint 门禁）；工作单已删除。

## 2l. 编辑器三需求整改：固定行号 / 字体收敛 / 格式化缺陷修复（2026-08-12，工作单 docs/in-progress/EDITOR_LINENUM_FONT_FORMAT_FIX_WORK_ORDER.md）
> 来源需求：① 编辑器支持固定行号；② 字体仅内置 + 用户导入，系统字体仅终极兜底
> （删内置字体不崩）；③ 修复格式化后跳最后一行 + 删除空格一次删四个的缺陷。
> 用户拍板：固定行号默认开；默认缩进改空格（对齐 Squircle，退格逐格删除）。
| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| L1 | **固定行号**：EditorConfig.fixedLineNumbers + SettingsRegistry 新键 `fixed_line_numbers`（不复用 P4 已删旧键）+ LuaEditor setPinLineNumber（构造 + 守卫 diff）+ 设置页开关 | 高 | ✅ 已完成（2026-08-12，工作单 T1/T2/T4；验收：compileArm64DebugKotlin + EditorConfigContractTest / EditorSettingsRegistryContractTest 全绿） | `EDITOR_LINENUM_FONT_FORMAT_FIX` `固定行号` `setPinLineNumber` |
| L2 | **字体收敛**：字体面板移除系统 3 族可选项（仅内置 + 导入）；fontFamilyLabel 回落默认；LuaFonts 两级兜底链 MONOSPACE → DEFAULT | 高 | ✅ 已完成（2026-08-12，工作单 T3；验收：LuaFontsFallbackTest 5 用例全绿——白名单/别名/拓扑/资产存在一致） | `EDITOR_LINENUM_FONT_FORMAT_FIX` `字体兜底` `LuaFonts` |
| L3 | **格式化修复**：format 光标恢复（setSelection 夹取）+ 文本一致性守卫；deleteMultiSpaces=1 / deleteEmptyLineFast=false 显式声明；use_tab 默认 false | 高 | ✅ 已完成（2026-08-12，工作单 T5/T6/T7；验收：LuaFormatterTest 增量 2 用例 + EditorConfigTest 全绿） | `EDITOR_LINENUM_FONT_FORMAT_FIX` `格式化` `光标` `deleteMultiSpaces` |
| L4 | **验证与收口**：LuaFontsFallbackTest / LuaFormatterTest 增量 + compile/test/verify 门禁 + AGENTS.md + kb 蒸馏 + 工作单收口 | 高 | 🔄 进行中（2026-08-12：单测已补全；compile + editor 单测全绿；全量验证与收口进行中） | `EDITOR_LINENUM_FONT_FORMAT_FIX` `验证` `收口` |

## 2m. 主界面与编辑器交互细节整改 + 全量审计批次 A（2026-08-12，工作单 docs/in-progress/UI_UX_DETAILS_WORK_ORDER.md）

> 来源需求（用户 2026-08-12 五条要求，全量审计后立项）：
> ① 切换语言时整个界面闪烁 → 修复（系统 recreate 首帧底色 + 列表缓存进程级化）；
> ② 编辑器调出输入法后返回主界面，焦点落在「新建项目」按钮 → 修复（离场释放编辑器 View 焦点）；
> ③ 新建项目/重命名等输入框对话框改为底部对话框（居中 → ModalBottomSheet，仅这两个，用户拍板）；
> ④ 主题色选中态选中框与对号颜色不一致（一黑一白）→ 统一按 WCAG 对比度取色的标记色；
> ⑤ 全量审计其他细节问题，清单与整改方案见工作单（P1/P2 批次 A 本次落地，P3 批次 B 登记）。
> 质量要求：代码质量/架构达大厂验收标准；每项完成在工作单标记（已完成）+ 验收证据。

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| U1 | 主题色选中态统一（选中框+对号同标记色，WCAG 对比度取色） | 高 | ✅ 已完成（2026-08-12，工作单 T1；selectedMarkColor + semantics selected） | `UI_UX_DETAILS` `ColorSheet` `选中态` |
| U2 | 编辑器返回主界面焦点残留修复（EditorController.releaseFocus + 离场释放 + 列表页兜底清焦） | 高 | ✅ 已完成（2026-08-12，工作单 T2；模拟器实测 focused=true 零节点） | `UI_UX_DETAILS` `焦点` `IME` |
| U3 | 新建/重命名对话框转 ModalBottomSheet（自动聚焦 + IME Done 提交） | 高 | ✅ 已完成（2026-08-12，工作单 T3；模拟器实测底部 sheet：标题/输入框/底部取消+创建） | `UI_UX_DETAILS` `ModalBottomSheet` `FocusRequester` |
| U4 | 语言切换闪烁修复（窗口背景对齐主题 surface + ProjectListCache 进程级缓存） | 高 | ✅ 已完成（2026-08-12，工作单 T4；模拟器实测中英双向切换生效 + recreate 后列表缓存立即可见 + 零 FATAL） | `UI_UX_DETAILS` `语言切换` `recreate` `闪烁` |
| U5 | 批次 A：SigningScreen 密码学/IO 异步化（generate/listAliases/目录扫描） | 中 | ✅ 已完成（2026-08-12，工作单 T5A；produceState/LaunchedEffect + Dispatchers.IO） | `UI_UX_DETAILS` `SigningScreen` `主线程` |
| U6 | 批次 A：系统栏图标明暗跟随应用外观（显式 SystemBarStyle + launcher effect） | 中 | ✅ 已完成（2026-08-12，工作单 T5B；systemBarStyleFor + WindowInsetsControllerCompat 同步） | `UI_UX_DETAILS` `enableEdgeToEdge` `SystemBarStyle` |
| U7 | 批次 A：自动保存写盘 NonCancellable（防 collectLatest 取消丢事件 + tmp 残留） | 中 | ✅ 已完成（2026-08-12，工作单 T5C；flushDirty 写盘段 NonCancellable） | `UI_UX_DETAILS` `自动保存` `NonCancellable` |
| U8 | 批次 A：字体设置 sheet 列表刷新 + SAF 复制移 IO（EditorSettingsScreen） | 中 | ✅ 已完成（2026-08-12，工作单 T5D；produceState(importVersion) + LuaFonts.externalLabels） | `UI_UX_DETAILS` `LuaFonts` `SAF` |
| U9 | 批次 A：EditorController 页面级 scope 清理 + 空态文案对比度 + 签名 RAW 异常文案资源化 | 中 | ✅ 已完成（2026-08-12，工作单 T5E；disposeScope + signing_*_failed 资源串） | `UI_UX_DETAILS` `协程` `对比度` `错误码` |
| U10 | 验证与收口：compile + 全量单测 + 设备验收清单 + kb 蒸馏 + 工作单收口 | 高 | ✅ 已完成（2026-08-12：compile + testArm64DebugUnitTest + verifyMinimalismDebug + lint 0 新增全绿；模拟器自动化冒烟 T2/T3/T4 通过（焦点/底部 sheet/语言切换+列表缓存）；T1/T5B 视觉项代码级验收；kb/EDITOR.md §15 蒸馏） | `UI_UX_DETAILS` `验收` `收口` |

## 2n. 全量规范审计整改（2026-08-12，工作单 docs/in-progress/FULL_AUDIT_SPEC_WORK_ORDER.md）

> 来源需求（用户 2026-08-12 六条要求，全量审计后立项）：
> ① 主题、权限、国际化等系统符合官方规范或社区习惯；② 逻辑实现尽量组件库原生实现；
> ③ 禁止修改组件库、用其 API；④ 在保持既有功能与规范代码前提下性能达语言与组件库上限；
> ⑤ 代码/API 用法遵守官方规范或社区习惯，杜绝不良编码习惯；⑥ 解决所有应当处理的临时性硬编码。
> 全量审计共发现 5 严重 / 10 高 / 20+ 中 / 30+ 低（报告见工作单「基线审计」段）。

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| A1 | S1 行级词法诊断 UI 全灭：双 diagTick 断线修复（实例级 flow 递增无人消费 / 组合级 flow 被消费无人递增） | 高 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `diagTick` `诊断` |
| A2 | S2 补全窗滚动无迭代上限（复制上游 DefaultCompletionLayout 时删除 MAX_SCROLL_ITERATIONS 防护） | 高 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `LuaCompletionWindow` `ANR` |
| A3 | S4 ProjectEntryOps `File.toPath()`（API 26）致 API 24/25 文件树操作静默失败 | 高 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `toPath` `minSdk` |
| A4 | S5 MainActivity content:// 全量拷贝在主线程（ANR 风险） | 高 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `主线程` `resolveFilePath` |
| A5 | H1 replaceAll 全文档替换后光标跳文末（sora Cursor.afterInsert 语义，注释不实） | 高 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `replaceAll` `光标` |
| B1 | S3 EditorInstance 构造先于 TextMate initReady 门控（GrammarRegistry 无锁读竞态） | 高 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `initReady` `竞态` |
| B2 | H2 syntaxChecker/messageProvider 跨线程无同步（增量丢失退化全量扫描） | 高 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `volatile` `并发` |
| B3 | H6 Shizuku stdout/stderr 顺序读取管道死锁（超 64KB stderr 卡 120s） | 高 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `管道` `死锁` |
| B4 | H7 Dhizuku setInstallerPackageName（API 34）守卫只到 S（31）+ catch(Exception) 抓不住 NoSuchMethodError | 高 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `API 34` `Dhizuku` |
| B5 | H9 androlua:// scheme 无 containment 校验（.. 段路径穿越） | 高 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `androlua://` `穿越` |
| B6 | H10 EngineResultParser 错误定位全文首匹配误报（未锚定 ERROR_PREFIX 行） | 中 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `EngineResultParser` |
| C1 | H4 ApkBuilder 中文异常消息直达用户（GENERAL/LUA_COMPILE 分支原样透传） | 中 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `错误码` `资源化` |
| C2 | H5 补全窗口 snippet 描述硬编码英文（中文用户补全窗永久英文） | 中 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `LuaSnippetManager` `i18n` |
| C3 | H8 「壁纸动态色」未走官方 dynamicColorScheme 且注释失实 | 中 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `dynamicColorScheme` |
| C4 | M-E1 同一 ttf 双 Typeface 实例（LuaFonts 缓存键应为 asset 文件名） | 中 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `LuaFonts` `缓存` |
| C5 | M-P1 ZipUtil 无 Zip Bomb 防护（条目数/总字节/单文件无上限） | 中 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `ZipUtil` `ZipBomb` |
| C6 | M-P2 AlpManager import 默认 OVERWRITE 破坏性（先删后解压） | 中 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `OVERWRITE` `导入` |
| C7 | M-P3 包名模板 com.lioisme.lide.<name> 两处重复 + 非法名不设防 | 中 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `包名` |
| C8 | M-B4 NativeDependencyAnalyzer LUA_STDLIB 缺 bit32/bit（与引擎模块表核对） | 低 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `LUA_STDLIB` |
| D1 | M-B1 打包链性能：宿主 APK 三遍解析 + arsc 双解析 + manifest 双解析 | 中 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `打包` `重复解析` |
| D2 | M-E5 200KB 大文件阈值两处独立定义（单一事实源缺失） | 低 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `LARGE_FILE_THRESHOLD` |
| D3 | M-P5 同一 init.lua 在 loadProject 流程被 parse 5-6 次 | 低 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `LuaConfigParser` |
| D4 | M-E6 补全前缀重复计算 + 两次分配（官方 CompletionHelper 已有） | 低 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `CompletionHelper` |
| E1 | M-T1/M-T2/M-T4 M3 token 合规：surfaceVariant alpha 叠卡、禁用态色、headline 字阶、displayLarge 字距、硬编码圆角 | 中 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `Material3` `token` |
| E2 | 死资源清理：7 对未引用字符串 + lint-baseline 条目 + assets-zh 空目录 | 中 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `死资源` |
| E3 | 裸魔法数提升常量（delay(100)/字号范围/take(60)/maxLength/2048/2050/guardLimit） | 低 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `魔法数` |
| E4 | 杂项收口：日志 TAG 统一、catch(Throwable)、EngineContract extra 键、RuntimePaths 子目录常量、死代码 | 低 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `TAG` `Throwable` |
| V1 | 终验：compile + 全量单测 + lint + verifyMinimalismDebug + kb 蒸馏 + 工作单收口 | 高 | ✅ 已完成（2026-08-12，工作单 FULL_AUDIT_SPEC 收口） | `FULL_AUDIT_SPEC` `验收` |

> 架构级长期项（本次不落地，登记备忘）：H3 手写诊断管线迁移 sora 官方 AnalyzeManager
> 管线（sora 单 analyzer 机制限制，需子类化 TextMateAnalyzeManager，风险高，列为 backlog
> 长期项）；M-T2 列表行 ListItem 化（material3 1.4.0 可用，涉及 7 处组件，另列专项）；
> 密码明文存 SharedPreferences（SigningManager，已知债务）；DataStore 迁移评估。

## 2o. 死代码/死依赖/死资源全量清理（2026-08-12，工作单 docs/in-progress/DEAD_CODE_CLEANUP_WORK_ORDER.md）

> 来源需求（用户 2026-08-12）：全量审计项目代码，清理所有死代码、死依赖、死资源；
> 4 路并行审计 + 成员级二轮，结论全部经全仓 grep 实证。
> 用户拍板：① 文档契约 API 删除+文档同步；② 文件树占位全部删除、落地时重建；
> ③ uninstallPackage 权限能力保留；④ 用户 Lua 工具库/socket 套件保留；
> ⑤ testImplementation(:androlua) 删除 + AGENTS.md 同步。

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| D1 | editor 包 5 死方法（isSameAs/isBound/searchHasQuery/isKnownFile/externalCount） | 高 | ⏳ 进行中 | `DEAD_CODE_CLEANUP` `死代码` |
| D2 | EditorSession 5 死方法（replaceText/replaceAllText/reload/removeTab/renameTab） | 高 | ⏳ 进行中 | `DEAD_CODE_CLEANUP` `会话门面` |
| D3 | ProjectManager 文件树 4 包装 + install 死方法（refreshAll/refreshStatus） | 高 | ⏳ 进行中 | `DEAD_CODE_CLEANUP` `WP-005` |
| D4 | 测试死面（resolveDark/resolveBackend/checkSyntax/AXmlManifest read*3）+ 测试迁移 | 高 | ⏳ 进行中 | `DEAD_CODE_CLEANUP` `测试` |
| D5 | 文件树占位菜单/字符串 + BuildOpts 惰性配置链 + util 空目录 | 中 | ⏳ 进行中 | `DEAD_CODE_CLEANUP` `占位` |
| D6 | 死依赖（hidden:stub/dontwarn/testImplementation）+ 死资产 5 lua + 死脚本（bat/log/路径修正） | 高 | ⏳ 进行中 | `DEAD_CODE_CLEANUP` `依赖` `资产` |
| D7 | 文档同步（AGENTS.md/kb/backlog/lint baseline）+ 全量验证 + 收口 | 高 | ⏳ 进行中 | `DEAD_CODE_CLEANUP` `验证` |
| D8 | **备忘：文件树功能落地时重建** ProjectManager 4 包装 + EditorSession 3 方法（removeTab/renameTab/reload）+ 菜单入口（本次已删） | 低 | 📋 备忘 | `WP-005` `文件树` |

## 2r. Squircle 对齐四需求整改（2026-08-13，工作单 docs/in-progress/SQUIRCLE_ALIGNMENT_FOUR_FIX_WORK_ORDER.md）

> 来源需求（用户 2026-08-13 四条要求）：① 编辑器所有语言高亮与 Squircle CE 完全一致；
> ② 格式化代码禁止自动滚动到顶部/底部；③ 工程权限界面权限支持勾选和取消勾选；
> ④ 底部对话输入框调出时同步调出输入法。
> 用户拍板：① 完全一致 → 引入 Squircle 9 款主题 + 放宽 WCAG 4.5:1 门禁（改 Squircle 源一致断言）；
> ④ 即 BottomInputSheet 三场景实测仍不弹。
| # | 任务 | 优先级 | 状态 | 关键词 |
|---|------|--------|------|--------|
| S1 | 主题转换 + EditorPalette 9 款目录 + 装配链 + 方案选择器 + 门禁改版 + text.plain 对齐 | 高 | 🔄 进行中 | `SQUIRCLE_FOUR_FIX` `主题` `门禁` |
| S2 | 格式化滚动修复（sora 官方 onFormatSucceed 配方） | 高 | ⏳ 待执行 | `SQUIRCLE_FOUR_FIX` `格式化` |
| S3 | 工程权限勾选修复（不可变 Set + 整行 toggleable） | 高 | ⏳ 待执行 | `SQUIRCLE_FOUR_FIX` `权限` |
| S4 | BottomInputSheet 输入法同步修复 | 高 | ⏳ 待执行 | `SQUIRCLE_FOUR_FIX` `输入法` |
| S5 | 文档同步 + 全量验证 + ADB 模拟器实测 + 收口 | 高 | ⏳ 待执行 | `SQUIRCLE_FOUR_FIX` `验收` |

## 2s. 编辑器主题第三次拍板：双模跟随宿主（2026-08-13，结论已蒸馏至 docs/kb/EDITOR.md §14）

> 用户第三次拍板（2026-08-13）：编辑器主题**保持之前的适配全局外观系统**——
> 一套主题在宿主深色/浅色模式下自动呈现深色/浅色版本（**非 Squircle 单侧**）；
> **仅语法高亮与 Squircle CE 保持 100% 一致**（9 款 JSON 全量保留）。
> 实现（THEME_DUAL_MODE_FOLLOW_HOST）：
> - `EditorPalette` 恢复双模结构：`pairs` 3 对（Classic = intellij_light/darcula、
>   Solarized = solarized_light/obsidian、Eclipse = eclipse/visual_studio）+ 9 款
>   `ThemeEntry` 全量（加载白名单不变）+ `schemeBackgrounds`/`schemeLineNumbers`/
>   `schemeLineNumbersActive` 双模派生表 + 装配唯一入口 `themeNameFor(scheme, isDark)`
>   与 `lineNumberPairFor(scheme, isDark)`（越界回落）；
> - `TextMateRuntime`/`LuaEditor` 双参链路（`hostColorScheme(scheme, isDark, host)`、
>   `LaunchedEffect(initReady, themeScheme, isDarkTheme)` 换侧）；宿主 `LocalIsDarkTheme`
>   经 `EditorComponents` 注入 LuaEditor；LideApp 预热按「方案 + 系统明暗」；
> - 设置页恢复 3 对双模选择器（亮/暗双色条），`editor_scheme_classic/solarized/eclipse`
>   文案恢复，9 款单侧文案 `editor_theme_*` 删除（中英同步）；
> - `EditorPaletteContrastTest` 保留 Squircle 源一致断言 + 新增双模配对断言（6/6 通过）。
> 验收证据（2026-08-13 ADB x64 模拟器实测）：宿主浅色 → 编辑器 intellij_light 白底 +
> 多彩高亮；系统切深色 → 编辑器自动 darcula（文本区 #2F312F / 行号区 #1E1F22 /
> 语法绿 #6A8759）；设置页方案选择器 3 对双模色条正常。单测全绿 + lint 0 新问题。

## 2t. 工程权限列表三点整改（2026-08-13 拍板，改动已入库）

> 用户拍板：① 工程权限列表**不区分「常规/特殊…」分组标题**（与 AppBar 筛选
> 按钮功能冲突）；② 权限列表 item **无左右距**（此前误改到主界面工程列表，
> 本次恢复主界面列表原貌并改正确位置）；③ item **大小正常合适**。
> 实现：
> - `ProjectPermissionScreen`：页级 LazyColumn 平铺（`items(displayItems)`，
>   删除 `contentPadding(horizontal = 16.dp)` 全宽）；全屏搜索 Dialog 同步平铺；
> - `SearchablePermissionPicker`：删除 `permissionGroupItems` /
>   `PermissionGroupHeader` / `PICKER_GROUPS`（分组语义仅保留于筛选菜单
>   `PermissionGroupFilter`）；`PermissionCheckRow` 行内 `padding(vertical = 8.dp)`
>   保证行高合适；
> - `ProjectListScreen`（主界面工程列表）恢复 `padding(horizontal = 16.dp,
>   vertical = 12.dp)` + Divider 缩进（回退误改）。
> 二次拍板（同日）：① 权限 item 边距改「内部边距」——行全宽（无相对外部
> 边距），左右/顶底 = 行内 `padding(horizontal = 16.dp, vertical = 12.dp)`
> （对齐主界面工程列表行），列表/搜索 `spacedBy(4.dp)` 删除（行距由行内
> padding 承担）；② 工程属性页（ProjectInfoScreen）权限区 + 权限弹窗 +
> `user_permission` 读写整体删除（工程权限收敛至独立「工程权限」页），
> `PermissionPickerList` 组件与 `info_section_permissions` /
> `info_permissions_*` 资源（中英）同步删除。
> 三修（同日，用户长按波纹确认）：`PermissionCheckRow` 修饰符顺序对齐主界面
> 工程列表（`fillMaxWidth → toggleable → padding`）——**toggleable 在 padding
> 外层**，点击热区/波纹全宽（无相对外部边距），padding 内层只缩进内容
> （16/12dp）。
> 验收证据（2026-08-13 ADB x64 模拟器实测）：权限页首行 = 「访问网络」无分组
> 标题；行主体（toggleable/波纹区）bounds = `[0,352][1440,599]` 全宽、内容
> 起点 16dp 内部缩进；工程属性页 = 基本信息 → 系统 → 高级 → 保存（无「权限」
> 分区）。compile + 单测 + lint 全绿。

## 2p. 六项 UI 交互与外观整改（2026-08-12，工作单 docs/in-progress/UI_AUDIT_SIX_REQ_WORK_ORDER.md） ——（已完成 2026-08-12，结论蒸馏至 docs/kb/ARCHITECTURE.md §9，工作单已删除）
> 来源需求（用户 2026-08-12 六条要求，全量审计后立项）：
> 1. 工程权限界面简约美观，像 API 浏览器一样的搜索和列表，无其他无用内容；
> 2. 主题色框选和对号颜色按夜间/日间模式显示不同颜色（非统一黑色）；
> 3. 底部对话输入框弹出时输入法键盘同步弹出（不得先弹框再弹键盘）；
> 4. 编辑器格式化后不跳顶/不跳底，仅格式化代码（滚动位置保持）；
> 5. 编辑器「跳转到行」使用底部对话输入框（非居中对话框）；
> 6. LIDE 启动界面图标完整显示 + 背景颜色跟随外观系统。
> 质量要求：代码质量/架构达大厂验收标准；每项完成在工作单标记（已完成）+ 验收证据。
| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| R1 | 底部输入框键盘同步（sheetState 动画完成后再 requestFocus）+ BottomInputSheet 提取共享组件 | 高 |（已完成）| `UI_AUDIT_SIX_REQ` `ModalBottomSheet` `键盘同步` |
| R2 | 跳转到行改底部输入框（ModalBottomSheet + 自动聚焦 + IME Done + 数字键盘） | 高 |（已完成）| `UI_AUDIT_SIX_REQ` `GotoLineDialog` `底部输入框` |
| R3 | 格式化/全部替换后滚动位置恢复（firstVisibleLine 保存 + 不强制滚动） | 高 |（已完成）| `UI_AUDIT_SIX_REQ` `格式化` `滚动位置` |
| R4 | 工程权限界面重构（API 浏览器同款搜索 + 单一 LazyColumn + 去冗余文案） | 高 |（已完成）| `UI_AUDIT_SIX_REQ` `权限界面` `搜索` |
| R5 | 启动界面图标完整显示（adaptive icon）+ 背景跟随应用外观（含 API 24-30 windowBackground） | 高 |（已完成）| `UI_AUDIT_SIX_REQ` `Splash` `adaptive icon` |
| R6 | 主题色块选中标记色按明暗模式驱动（日间偏好黑、夜间偏好白，WCAG 4.5:1 兜底） | 中 |（已完成）| `UI_AUDIT_SIX_REQ` `ColorSheet` `标记色` |
| R7 | 验证收口：compile + 单测 + 门禁 + kb 蒸馏 + 工作单收口 | 高 |（已完成）| `UI_AUDIT_SIX_REQ` `验收` `收口` |

## 2q. 全量工程审计（2026-08-12，工作单 docs/in-progress/ENGINEERING_AUDIT_2026-08-12.md）

> 来源需求（用户 2026-08-12）：以第一性原理为目标，原生实现 > 组件库实现 > 自实现；代码规范遵循官方/社区习惯；功能性能质量达语言与组件库上限；架构质量达苹果谷歌大厂验收标准。
> 审计范围：102 文件 / ~957 KB 自有 Kotlin + 38 测试文件 + 构建系统；排除 vendor/androlua/cpp 源码。
> 实证：compile ✅ / unit-test ✅（38 文件）/ ktlint ✅（主代码 0 违规 + 测试规则豁免）/ lint ✅ / verifySoraIsolation ✅ / verifyVendorIsolation ✅。
> **全部 P0/P1/P2 任务已修复落地（2026-08-12）**。

| # | 任务 | 优先级 | 位置 | 状态 | 关键词 |
|---|---|---|---|---|---|
| P0-1 | **ApkBuilder.stripNativeLibs 参数硬编码 bug**：nativeLibKept 调用第二个参数硬编码 `stripNativeLibs = true`，忽略用户设置，Compose 图形库被错误剔除 | 高 | `build/ApkBuilder.kt:347` | ✅ 已完成 2026-08-12 | `ApkBuilder` `stripNativeLibs` `nativeLibKept` |
| P0-2 | **补齐 CI workflow**：`.github/workflows/` 目录缺失，9 个门禁任务无自动化执行，PR 合并无保护 | 高 | `.github/workflows/build.yml` | ✅ 误报（2026-08-12）：workflow 实际存在且完整（9 job），审计代理误报 | `CI` `GitHub Actions` `verify` |
| P0-3 | **StaticFieldLeak × 4 内存泄漏**：`InstallerServices` 静态持有 Context，long-running OOM 风险 | 高 | `install/InstallerServices.kt` | ✅ 已完成 2026-08-12：加 @Suppress 注明持有 Application context 为预期行为 | `StaticFieldLeak` `Context` `内存泄漏` |
| P0-4 | **DhizukuInstallerServiceImpl 反射路径 catch Exception 而非 Throwable**：违反 AGENTS.md 红线 | 高 | `install/dhizuku/DhizukuInstallerServiceImpl.kt:328,333` | ✅ 已完成 2026-08-12：改为 catch Throwable | `Throwable` `反射` `catch` |
| P0-5 | **Session/ApiIndex StateFlow 写入非主线程**：`withContext(ioDispatcher)` 后写 StateFlow，违反线程契约 | 高 | `session/EditorSession.kt:85,86,119,121` | ✅ 已完成 2026-08-12：IO 后切回 mainDispatcher 写 StateFlow | `StateFlow` `主线程` `线程契约` |
| P1-1 | **ktlint 全量格式化**：1658 行主代码违规 + 测试代码 ~80 行违规，阻断 lint 门禁运行 | 高 | 多处 | ✅ 已完成 2026-08-12：ktlintFormat 自动修复 + 10 处手动修复 + .editorconfig 测试代码规则豁免 | `ktlint` `格式` `CI` |
| P1-2 | **LuaDiagnostics 大文件守卫缺失**：check() 无长度保护，可直接触发 O(n) 全量扫描 | 中 | `editor/LuaDiagnostics.kt:206` | ✅ 已完成 2026-08-12：入口增加 EditorLimits.LARGE_FILE_THRESHOLD 快速返回 | `大文件` `性能` `守卫` |
| P1-3 | **lint 新错误 ic_splash_foreground.png**：bitmap 放在 densityless drawable 目录，不在 baseline 中阻断构建 | 中 | `res/drawable/ic_splash_foreground.png` | ✅ 已完成 2026-08-12：移入 `res/drawable-nodpi/` | `IconLocation` `lint` |
| P1-4 | **NewApi × 9 baseline 条目实际不可达**：List.addLast/removeLast（API 35）+ Process API（API 26）在 minSdk 24 设备不兼容 | 中 | baseline + `LuaDiagnostics.kt` / `ShizukuInstallerServiceImpl.kt` | ✅ 已完成 2026-08-12：addLast→add(size)、removeLast→removeAt(size-1)、destroyForcibly→SDK_O 守卫封装 | `NewApi` `minSdk` |
| P1-5 | **zip-slip 多层逃逸测试补充**：缺少 `../../etc/hosts` 等深层穿越用例 | 中 | `project/ZipUtilTest.kt` | ✅ 已完成 2026-08-12：补充多级 `../` 穿越测试用例 | `zip-slip` `安全` `测试` |
| P2-1 | FQN 风格统一（TextMateRuntime/LuaEditor/LuaCompletionWindow 3 处） | 低 | `editor/TextMateRuntime.kt:105` / `LuaEditor.kt:94` / `LuaCompletionWindow.kt:181-190` | ✅ 已完成 2026-08-12：统一添加 import，消除 FQN | `FQN` `import` |
| P2-2 | LARGE_FILE_UNDO_STACK 移至 EditorLimits 单一事实源 | 低 | `editor/LuaEditor.kt:76` | ✅ 已完成 2026-08-12：新增 `EditorLimits.LARGE_FILE_UNDO_STACK` | `LARGE_FILE` `EditorLimits` |
| P2-3 | AXmlManifest rebuildPool UTF-16 降级 KDoc 文档化 | 低 | `build/AXmlManifest.kt:872-902` | ✅ 已完成 2026-08-12：补充方法 KDoc 说明 UTF-16 降级行为 | `UTF-16` `KDoc` |
| P2-4 | BuildApkDialog 魔法字符串 "init"/"package" 改用枚举 | 低 | `ui/screen/BuildApkDialog.kt:141` | ✅ 已完成 2026-08-12：新增 `PreflightFail` sealed class | `魔法字符串` `sealed class` |
| P2-5 | EditorScreen closeQueue 加 rememberSaveable | 低 | `ui/screen/EditorScreen.kt:169` | ✅ 已完成 2026-08-12：改为 rememberSaveable | `rememberSaveable` |

## 2r. 编辑器性能审计与 Squircle 渲染面对齐（2026-08-13，工作单 docs/in-progress/EDITOR_PERF_AUDIT_WORK_ORDER.md）

> 来源需求（用户 2026-08-13）：全量审计 LIDE 编辑器 vs sora-editor（上限）/ Squircle CE（持平基线），
> 保持现有功能前提下做性能优化，最后经 ADB 双端实测（模拟器已装 Squircle CE）确认性能达标。
> 审计结论：LIDE 已全面对齐/超越 Squircle（守卫式 diff / Content 复用 / 防抖增量诊断 / 大文件降级矩阵）；
> 差距 = 4 项 Squircle 显式关闭的渲染开销开关 LIDE 保持 sora 默认（详见工作单「基线审计」段）。
> 质量要求：代码质量/架构达大厂验收标准；每项完成在工作单标记（已完成）+ 验收证据。

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| EP1 | 光标动画显式关闭（Gap-2：`setCursorAnimationEnabled(false)`，落实 EditorConfig P4「光标动画（关）」声明，对齐 Squircle view/CodeEditor.kt:59） | 高 | ✅ 已完成（2026-08-13，工作单 T1） | `EDITOR_PERF_AUDIT` `光标动画` |
| EP2 | 渲染裁剪对齐（Gap-1：`drawSideBlockLine` / `boldMatchingDelimiters` / `enableRoundTextBackground` 三项关默认，对齐 Squircle view/CodeEditor.kt:66-68） | 高 | ✅ 已完成（2026-08-13，工作单 T2） | `EDITOR_PERF_AUDIT` `渲染裁剪` |
| EP3 | 长行 RenderNode 缓存开启（`props.cacheRenderNodeForLongLines`，sora 上限能力，>128 字符行走硬件显示列表） | 中 | ✅ 已完成（2026-08-13，工作单 T3） | `EDITOR_PERF_AUDIT` `RenderNode` |
| EP4 | 构建验证（compile + 单元测试 + verifySoraIsolation + 红线 grep） | 高 | ✅ 已完成（2026-08-13，工作单 T4） | `EDITOR_PERF_AUDIT` `验证` |
| EP5 | ADB 双端性能对比实测（LIDE x64 debug vs Squircle CE：冷启动/滚动 fling/打字/大文件 framestats + meminfo） | 高 | ✅ 已完成（2026-08-13，工作单 T5/T6） | `EDITOR_PERF_AUDIT` `ADB` `framestats` |
| EP6 | 收口：工作单完成标记 + kb/EDITOR.md 性能基线章节蒸馏 | 高 | ✅ 已完成（2026-08-13，工作单 T7） | `EDITOR_PERF_AUDIT` `蒸馏` `收口` |

## 2u. 日志猫（Logcat 全局日志查看器）（2026-08-14，工作单 docs/in-progress/LOGCAT_WORK_ORDER.md）

> 来源需求（用户 2026-08-14 四项要求）：① 标准、专业的日志猫功能（全局日志，
> 非 androlua 专用）；② 界面与 API 浏览器一致（searchbar + searchview + 日志列表，
> 单击进详情展示完整日志）；③ 界面风格与 LIDE 整体一致（优美、简约）；
> ④ 专业筛选：All / Test / Tcc / Error / Warning / Info / Debug / Verbose / Clear。
> 质量要求：代码质量/架构达大厂验收标准；每项完成在工作单标记（已完成）+ 验收证据。

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| L1 | 数据层：`com.lioisme.lide.log` 包（LogEntry/LogLevel/LogFilter/LogcatParser/LogBuffer/LogcatReader/LogcatStore）+ JVM 单测 | 高 | ✅ 已完成 | `LOGCAT` `logcat` `LogcatStore` `LogcatParser` |
| L2 | 列表页：LogcatScreen + LogcatComponents（搜索状态机红线 + 8 项筛选 + 级别色行 + 空态 + 自动滚动） | 高 | ✅ 已完成 | `LOGCAT` `搜索红线` `筛选` |
| L3 | 详情页：LogcatDetailScreen（三态 + 完整消息 + 元数据 + 复制） | 高 | ✅ 已完成 | `LOGCAT` `详情` `三态` |
| L4 | 接线：Routes/导航注册 + AndroLua 设置「工具」区入口 + LideApp 启动采集 + i18n 双语文案 | 高 | ✅ 已完成 | `LOGCAT` `路由` `i18n` |
| L5 | 验证收口：compile + 单测 + 红线自检 + kb 蒸馏 + 工作单收口 | 高 | ✅ 已完成 | `LOGCAT` `验收` `收口` |

> **（已完成，2026-08-14）** L1–L5 全部完成：`com.lioisme.lide.log` 数据层 7 文件 +
> 列表/详情页 + 接线（Routes/入口/LideApp 启动采集/i18n 中英）。验收证据：
> `:app:compileArm64DebugKotlin` BUILD SUCCESSFUL（0 error/0 warning）；
> 全量 `:app:testArm64DebugUnitTest` = **355 tests / 0 failures**（含 logcat 四件套
> 36 例：Parser 12 + Filter 8 + Buffer 8 + Store 8）；红线自检三连 grep 全零输出
> （log 包零 sora/引擎引用、editor 包零 log 触碰）。结论已蒸馏进
> `docs/kb/ARCHITECTURE.md` §10；工作单已删除。

> **（已完成，2026-08-14 修订 LOGCAT_REVISION）** 用户四项修订要求：
> ① 全局定位——入口从 AndroLua 设置页迁移至**主设置页「工具」分区**
> （SettingsScreen 新增分区 + `onLogcatClick`，AndroLua 页回滚）；
> ② Clear 按钮移至**筛选按钮左侧**（共享组件 `ApiSearchInputField` /
> `ApiCollapsedSearchInput` 新增可选 `clearSlot`，折叠/展开两态 [Clear][Filter]，
> 顶栏 actions 移除；API 浏览器/权限页默认 null 零影响）；
> ③ 筛选对齐 pro logcat.lua：菜单 **All/Lua/Test/Tcc/Error/Warning/Info/Debug/
> Verbose**（新增 `LUA(tag="lua")`；`TEST` 改 `tag="test"`；顺序断言入测试）；
> ④ 日志行重设计：**[级别色点] + [tag 徽章（surfaceContainerHighest 底 + 6dp 圆角
> + labelSmall 等宽）] + [时间 tnum 右对齐] + 消息单行省略**，删除逐行分隔线留白
> 呼吸。验收证据：`compileArm64DebugKotlin` 0 error（0 新增 warning）；
> 全量单测 = **357 tests / 0 failures**（LogFilterTest 10 例）；`verifySoraIsolation`
> PASS；红线 grep 三连全零。结论已并入 `docs/kb/ARCHITECTURE.md` §10。

## 2v. 日志猫界面重构（2026-08-14，工作单 docs/in-progress/LOGCAT_UI_REDESIGN_WORK_ORDER.md）

> 来源需求（用户 2026-08-14 三条 + 审计附加）：① 日志列表整齐简约——**时间固定列位**
> （当前因 tag 长短不同导致时间视觉锚点漂移）；② 列表**无相对外部间距**；③ 「清除」
> 功能移入菜单；④ 界面风格与 LIDE 完全一致（审计附加：搜索占位符文案错位）。
> 用户拍板：时间列 `MM-DD HH:MM:SS` 固定 84dp（毫秒降级详情页）；清除并入类型筛选
> 菜单（Delete + error 色）；直接清除无确认。
> 质量要求：代码质量/架构达大厂验收标准；每项完成在工作单标记（已完成）+ 验收证据。

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| R1 | 共享搜索组件占位符参数化（placeholder）+ 删除无调用方 clearSlot 死参数 | 高 | ✅ 已完成（2026-08-14，工作单 T1） | `LOGCAT_UI_REDESIGN` `placeholder` |
| R2 | LogRow 固定时间列重构（84dp 右对齐 tnum + tag 徽章弹性 maxWidth + 消息对齐） | 高 | ✅ 已完成（2026-08-14，工作单 T2） | `LOGCAT_UI_REDESIGN` `固定时间列` |
| R3 | 列表全宽（contentPadding 归零）+ 移除 clearSlot 挂载 | 高 | ✅ 已完成（2026-08-14，工作单 T3） | `LOGCAT_UI_REDESIGN` `全宽` |
| R4 | 清除并入筛选菜单（分隔线 + 清空项）+ 删除 LogClearButton | 高 | ✅ 已完成（2026-08-14，工作单 T4） | `LOGCAT_UI_REDESIGN` `清除` `菜单` |
| R5 | 验证收口：compile + 单测 + 红线自检 + kb 蒸馏 + 工作单收口 | 高 | ✅ 已完成（2026-08-14，工作单 T5/T6） | `LOGCAT_UI_REDESIGN` `验收` |

> **（已完成，2026-08-14 LOGCAT_UI_REDESIGN）** 日志猫界面三项重构 + 两项审计
> 修复全部落地：① 时间固定列（84dp 右对齐 tnum，`MM-DD HH:MM:SS` 毫秒降级详情页，
> tag 徽章 maxWidth 160dp，消息与 tag 左缘对齐）；② 列表全宽（contentPadding 归零）；
> ③ 「清空日志」并入类型筛选菜单底部（分隔线 + Delete + error 色，直接清除）；
> ④ 搜索占位符参数化（日志猫显示「搜索日志…」，修复「搜索类名…」错位）；
> ⑤ `LogClearButton`/`clearSlot` 死代码删除。验收证据：compileArm64DebugKotlin
> 0 error + 全量单测全绿 + ktlintCheck 相关文件 0 违规（顺手修复新文件 LogcatScreen
> 既有 6 处 + 文件尾换行）+ 红线 grep 三连全零。结论已蒸馏进
> `docs/kb/ARCHITECTURE.md` §10。

## 2w. 日志猫列表风格重构（2026-08-14，工作单 docs/in-progress/LOGCAT_LIST_REDESIGN_WORK_ORDER.md）

> 来源需求（用户 2026-08-14）：重构日志猫列表，要求整洁、优美、简约，设计风格
> 与 LIDE 整体界面一致。用户三项拍板：① 行结构 = **两行式应用列表**（对齐
> ProjectList/ClassRow 范式：色点垂直居中 + 行1 tag 主文字 + 行尾时间 tnum 右缘
> 对齐 + 行2 消息副行）；② **去掉 tag 灰块徽章**（surfaceContainerHighest 底块
> 删除，tag 纯文字 onSurface）；③ **加行末细分隔线**（outlineVariant 0.4 alpha，
> ClassRow/MemberRow 同款惯例）。
> 质量要求：代码质量/架构达大厂验收标准；每项完成在工作单标记（已完成）+
> 验收证据。

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| W1 | 代码：LogRow 两行式重构（色点居中 + tag 主行 + 时间行尾右缘 + 消息副行 + 分隔线 + 16dp 边距；删除 TIME_COLUMN_WIDTH/TIME_COLUMN_START 固定列机制） | 高 | ✅ 已完成（2026-08-14，工作单 T1） | `LOGCAT_LIST_REDESIGN` `LogRow` `两行式` |
| W2 | 验证：ktlintCheck + compileArm64DebugKotlin + 全量单测 + 红线 grep 三连 | 高 | ✅ 已完成（2026-08-14，工作单 T2） | `LOGCAT_LIST_REDESIGN` `验收` |
| W3 | 收口：工作单标记 + kb/ARCHITECTURE.md §10 日志行设计段更新 + 工作单删除 | 高 | ✅ 已完成（2026-08-14，工作单 T3） | `LOGCAT_LIST_REDESIGN` `蒸馏` `收口` |

> **（已完成，2026-08-14 LOGCAT_LIST_REDESIGN）** 日志行从「色点 + 时间固定
> 84dp 列 + tag 灰块徽章 + 消息 108dp 缩进 + 无分隔线 + 贴边」重构为 LIDE
> 主界面同款「两行式应用列表」：级别色点垂直居中；行1 = tag 主文字（labelMedium
> Monospace Medium onSurface + widthIn(max=160dp) 省略）→ 时间行尾右缘对齐
> （labelSmall tnum onSurfaceVariant，weight 占位天然列位）；行2 = 消息副行
> （bodySmall Monospace onSurfaceVariant 单行省略，与 tag 垂直对齐消除错位
> 阶梯）；行末 HorizontalDivider（outlineVariant 0.4 alpha）；行内
> padding(h16, v10)。删除固定时间列/消息缩进机制（TIME_COLUMN_WIDTH 84dp /
> TIME_COLUMN_START 108dp 常量删除），保留 TIME_TEXT_LENGTH/timeForList 与
> TAG_MAX_WIDTH。顺手修复 ktlint 既有违规（LogLevel 1 + LogcatDetailScreen 2 +
> SearchablePermissionPicker 文件头 KDoc 1 + log 包 5 文件 ktlintFormat 16 处），
> ktlintCheck 恢复全量 PASS。验收证据：ktlintCheck PASS + compileArm64DebugKotlin
> 0 error/0 warning + 全量 testArm64DebugUnitTest 全绿（357 tests / 0 failures）
> + 红线 grep 三连全零。结论已蒸馏进 `docs/kb/ARCHITECTURE.md` §10。

> **（已完成，2026-08-14 二次修订 LOGCAT_LIST_REVISION）** 用户验收反馈「列表
> 界面不合格，视觉上太混杂」后二次重构（用户三项拍板：① 消息为主角——日志
> 核心是内容而非来源；② 不受两行限制——改单行式；③ 级别色不放前面——级别
> 是筛选维度不是主角）。新 LogRow = **单行式消息主角**：**[消息 bodySmall
> Monospace onSurface weight 1f 单行省略] + [级别字母（语义色小字）] +
> [tag（labelSmall 等宽 onSurfaceVariant，maxWidth 96dp）] + [时间（labelSmall
> tnum onSurfaceVariant 右缘列位恒定）]** + 行末细分隔线（outlineVariant 0.4）；
> 行内 padding(h16, v8)；**无行首色点**（旧 8dp 色点与两行式 tag 主行形态全部
> 废止——tag 加粗主色文字即混杂感根源）；TAG_MAX_WIDTH 160→96dp。修复过程中
> PowerShell ANSI 解码误伤中文注释，文件整体重建（UTF-8/LF 校验通过）。
> 验收证据：ktlintCheck PASS + compileArm64DebugKotlin 0 error + 全量单测
> **357 tests / 0 failures**。结论已蒸馏进 `docs/kb/ARCHITECTURE.md` §10。

> **（已完成，2026-08-14 三次修订 LOGCAT_LIST_REVISION2）** 用户反馈「为什么是
> 横向三行？肯定是纵向行啊」——消息/tag/时间不应横向并排一行，应**纵向**排布。
> 新 LogRow = **纵向两行式（消息主行）**：**行1 [消息 bodySmall Monospace
> onSurface 全宽单行省略（主色主角）] → 行2 纵向 [级别字母（语义色小字）] +
> [tag（labelSmall 等宽 onSurfaceVariant，maxWidth 96dp）] + [时间（labelSmall
> tnum onSurfaceVariant 右缘列位恒定）]** + 行末细分隔线（outlineVariant 0.4）；
> 行内 padding(h16, v8)；消息全宽不缩进；级别字母置于副行（不做行首色点）。
> 验收证据：ktlintCheck PASS + compileArm64DebugKotlin 0 error/0 warning +
> 全量单测 **357 tests / 0 failures**。结论已蒸馏进
> `docs/kb/ARCHITECTURE.md` §10。
>
> **（2026-08-15 ADB 实机验收，x64Debug / Android 14 模拟器）** 安装
> `app-arm64-debug` 同源 `app-x64-debug.apk` 逐项验证通过：①列表每条日志为
> **纵向两行式**（行1 消息主色大字 / 行2 tag 灰 + 时间灰，级别 V/D/I/W 字母
> 语义色小字位于 tag 左侧，无行首色点，行间浅灰细分隔线，行高均匀）；②点击
> 进详情页（毫秒时间 / 级别 / tag / PID / TID / 完整消息 + 返回与复制按钮）；
> ③筛选弹层正常（tag 筛选 Lua/Test/Tcc + 级别 错误/警告/信息/详细 + 清除
> 日志）；④选「错误」后列表只剩 E/Error 条目（筛选生效）；⑤长按日志出现
> Snackbar「已复制到剪贴板」（复制契约生效）。

> **（已完成，2026-08-15 四次修订 LOGCAT_LIST_REVISION3：单行紧凑分组式）**
> 用户验收「还是不合格」并逐项点出：两行太高密度低 / 消息只有一行太少 /
> 太简了要刚刚好 / 不同信息不要放一起 / 满屏省略号。定稿形态 = **单行**：
> `[消息 bodySmall Monospace onSurface weight(1f) 单行省略（消息区最大化）] +
> Spacer(12) + [级别字母 labelMedium 语义色] + Spacer(6) + [tag labelMedium
> 等宽 onSurfaceVariant `widthIn(max = 120dp)`] + Spacer(6) + [时间 labelMedium
> tnum onSurfaceVariant 右缘列位恒定]`；行内 `padding(h16, v6)`（行高 32dp
> 级，一屏 21 条）；行末细分隔线不变。TAG_MAX_WIDTH 96→120dp（消除满屏
> 省略号：`om.lioisme.lide` / `CompatibilityChangeReporter` / `GraphicsEnvironment`
> 全名实机完整显示）。元数据与消息固定间距分隔不混排。
> 验收证据：ktlintCheck + compileX64DebugKotlin PASS；全量单测 357 tests /
> 0 failures；ADB 实机（x64Debug / Android 14）21 条/屏、消息全名显示、tag
> 无省略、消息左对齐时间右对齐列对齐整齐。

> **（已完成，2026-08-15 五次修订 LOGCAT_LIST_REVISION4：标准 logcat 行形态）**
> 用户拍板：不再自定义设计，遵循官方规范 / 社区习惯，参考开源社区日志猫
> 实现。按仓库本地源码参照（`reference/AndroLua_pro-master/app/src/main/
> assets/logcat.lua`，`logcat -v long` 的 `I/lua: message` 前缀流式 +
> ListView 长按复制整条）与 Android Studio Logcat 惯例重做。定稿行形态 =
> 单行 **`[级别字母 labelMedium 语义色][/ 灰][tag labelMedium 等宽
> onSurfaceVariant maxWidth 120dp][: 灰] [消息 bodySmall Monospace onSurface
> weight(1f) 单行省略] + Spacer(10) + [时间 labelMedium tnum onSurfaceVariant
> 右缘列位恒定]`**（`I/lua: message` 标准前缀）；行内 `padding(h16, v6)`；
> 行末细分隔线不变。长按复制升级为**标准整行文本**（`LogEntry.formatted()`
> = `MM-DD HH:MM:SS.mmm PID TID I/tag: message`，Android Studio 同款，
> pro 复制整条语义）；新增 `LogEntry.formatted` 单测（`标准整行格式化`）。
> 验收证据：ktlintCheck + compileX64DebugKotlin PASS；全量单测 **358 tests /
> 0 failures**；ADB 实机（x64Debug / Android 14）标准前缀形态生效（级别色
> I 绿 / D 蓝 / W 橙 / E 红 / V 灰、消息主体、时间行尾、行高 109px 更紧凑）、
> 长按复制 Snackbar 正常。

> **（已完成，2026-08-15 六次修订 LOGCAT_LIST_REVISION5：纵向展开，消息多行
> 可读）** 用户拍板（原文）：「时间、日志内容、日志类型等全部都是仅一行显示，
> 怎么阅读？」——信息不得挤在一行，要方便阅读。定稿 = 每条日志**纵向两段**：
> **行1（主体）消息内容 bodySmall Monospace onSurface `maxLines = 3` 多行
> 完整显示**（不再单行截断；超长省略 + 详情页全文）+ **行2（元数据小字一
> 行）时间（labelSmall tnum onSurfaceVariant）· 级别字母（labelSmall 语义
> 色）· `/tag`（labelSmall 等宽 onSurfaceVariant `widthIn(max = 160dp)`）**；
> 行内 `padding(h16, v6)`；行高内容驱动（消息 1 行 ≈ 44dp / 3 行 ≈ 90dp）；
> 行末细分隔线不变；长按复制仍为 `entry.formatted()` 标准整行。
> 验收证据：ktlintCheck + compileX64DebugKotlin PASS；全量单测 **358 tests /
> 0 failures**；ADB 实机（x64Debug / Android 14）截图核验：每条日志上下两
> 段（消息多行完整可读，如 `Currently set values for:
> angle_gl_driver_selection_pkgs=[]` 两行完整显示；元数据 `08-15 02:09:25
> I/om.lioisme.lide` 位于消息下方小字行），日志阅读器观感。

> **（已完成，2026-08-15 七次修订 LOGCAT_LIST_REVISION6：规范整齐，级别字母
> 固定列）** 用户拍板：**保留现有 AppBar / SearchBar / SearchView / 筛选菜单
> （搜索状态机红线与 9 项筛选不动），仅重构日志列表**；要求界面规范、整齐、
> 不标新立异自由发挥（参考开源优秀项目）。定稿 LogRow = **级别字母固定列 +
> 纵向两段主体**：**[左列固定 24dp 级别字母 labelMedium 等宽语义色列内右对齐
> （每行字母落点与主体左缘恒定，级别扫描路径稳定，不随 tag 漂移，Android
> Studio logcat 精神）] + Spacer(8dp) + [主体 Column(weight 1f)：行1 消息
> bodySmall Monospace onSurface `maxLines = 3` 多行完整显示 → 行2 元数据
> 时间（labelSmall tnum onSurfaceVariant）· tag（labelSmall 等宽
> onSurfaceVariant `widthIn(max = 160dp)`）]**（级别字母自元数据行迁出至左
> 列，元数据行不再含 `/` 前缀）；行 `minHeight 56dp`（消息 1 行时行高一致，
> 滚动整齐）+ `padding(h16, v10)`；行末细分隔线不变。**空态升级为 M3 规范
> 空态**：`Icon(Terminal 44dp onSurfaceVariant α0.5)` + 12dp + 文案
> （bodyMedium onSurfaceVariant 居中，替代裸文本）。新增常量
> LEVEL_COLUMN_WIDTH(24dp) / LOG_ROW_MIN_HEIGHT(56dp)。长按复制仍为
> `entry.formatted()` 标准整行。验收证据：compileArm64DebugKotlin PASS +
> ktlintCheck **全量 PASS** + 全量单测 **358 tests / 0 failures**。

---

## 2x. 日志猫列表重构（2026-08-15，工作单 docs/in-progress/LOGCAT_LIST_STANDARD_WORK_ORDER.md）

> 来源需求（用户 2026-08-15）：**保留现有 AppBar / SearchBar / SearchView（含搜索
> 状态机红线与 9 项筛选菜单）不动，仅重构日志猫列表**；界面设计要规范、整齐，
> 不标新立异自由发挥，参考开源优秀项目，**符合行业规范而不是自己写**；用户明确
> 声明不遵循既有日志猫设计文档（多轮修订可能漂移或理解有误），以业界公开惯例为
> 准。用户拍板：行形态 = **AS 规范前缀式**（`I/tag: message`）。
> 质量要求：代码质量/架构达大厂验收标准；每项完成在工作单标记（已完成）+ 验收证据。

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| S1 | 登记：backlog 2x 条目 + 工作单创建 | 高 | ✅ 已完成（2026-08-15） | `LOGCAT_LIST_STANDARD` `登记` |
| S2 | 代码：LogRow 重写为 AS 规范前缀式（`I/tag: message` + 元数据行时间·PID-TID）+ 常量调整（删 LEVEL_COLUMN_WIDTH / TAG_MAX_WIDTH 160→120dp） | 高 | ✅ 已完成（2026-08-15） | `LOGCAT_LIST_STANDARD` `LogRow` `前缀式` |
| S3 | 验证：compileArm64DebugKotlin + ktlintCheck + 全量单测 + 红线 grep 三连 | 高 | ✅ 已完成（2026-08-15） | `LOGCAT_LIST_STANDARD` `验收` |
| S4 | 蒸馏：kb/ARCHITECTURE.md §10 日志行设计段更新（REVISION7）+ backlog 2x 注记 | 高 | ✅ 已完成（2026-08-15） | `LOGCAT_LIST_STANDARD` `蒸馏` |
| S5 | 收口：工作单标记全部完成 + 删除工作单 | 高 | ✅ 已完成（2026-08-15） | `LOGCAT_LIST_STANDARD` `收口` |

> **（已完成，2026-08-15 八次修订 LOGCAT_LIST_REVISION7：AS 规范前缀式，行业
> 惯例收口）** 用户拍板：**保留现有 AppBar / SearchBar / SearchView（搜索状态机
> 红线与 9 项筛选菜单不动），仅重构日志列表**；要求规范、整齐、不标新立异，参考
> 开源优秀项目、**符合行业规范而不是自己写**，明确不遵循既有日志猫设计文档（多轮
> 修订可能漂移或理解有误）。参考基线 = Android Studio Logcat Standard View（时间
> → PID-TID → 级别 → tag → 消息，等宽，soft-wrap）+ MatLog/CatLog 谱系（规范前缀
> `I/tag: message`）。定稿 LogRow = **规范前缀行 + 元数据小字行**：**行1** `[级别
> 字母 bodySmall 等宽级别色][/ 灰][tag bodySmall 等宽级别色 widthIn(max=120dp)
> 省略][: 灰][消息 bodySmall 等宽 onSurface weight(1f) maxLines=3 换行省略]`
> （`I/tag: message` 标准前缀，前缀与消息统一 bodySmall 等宽同字号基线对齐，
> 续行对齐消息列 = AS soft-wrap 语义）；**行2** `MM-DD HH:MM:SS（tnum）· PID-TID
> （等宽）`（`$pid-$tid`，AS Standard View 同款）；行 `minHeight 56dp` +
> `padding(h16, v10)`；行末细分隔线不变。**常量调整**：删 `LEVEL_COLUMN_WIDTH`
> (24dp) 固定列机制；`TAG_MAX_WIDTH` 160→120dp。不动项：级别色表/空态/列表容器/
> 自动滚动/详情页/数据层/i18n（零新增资源）。验收证据：compileArm64DebugKotlin
> BUILD SUCCESSFUL（0 error）+ ktlintCheck 全量 PASS + 全量单测 **358 tests /
> 0 failures / 0 errors** + 红线 grep 三连全零（editor 包零 luajava/androlua/
> loadLibrary、vendor src 零 com.lioisme、git ls-files 二进制零）。结论已蒸馏进
> `docs/kb/ARCHITECTURE.md` §10。

> **（2026-08-15 REVISION8 修订登记）** 用户验收反馈：REVISION7 前缀式**排版混乱**
> ——消息列随 tag 长度逐行漂移（锯齿参差）+ 前缀与消息混排在一行。用户拍板：
> **重构为「徽章固定列锚点」结构**（LogFox 同款）：固定宽级别徽章列 → 右侧消息/
> 元数据左缘全列表恒定对齐；消息独占整行区、零混排。任务表 S6–S10。

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| S6 | 登记：重建工作单 + backlog 2x 追加 REVISION8 任务行 | 高 | ✅ 已完成（2026-08-15） | `LOGCAT_LIST_STANDARD_R8` `登记` |
| S7 | 代码：LogRow 重构为徽章固定列（28dp 圆角徽章 + 右侧消息 3 行 + 元数据行 时间·PID-TID·tag），常量新增 LEVEL_BADGE_WIDTH | 高 | ✅ 已完成（2026-08-15） | `LOGCAT_LIST_STANDARD_R8` `LogRow` `徽章固定列` |
| S8 | 验证：compileArm64DebugKotlin + ktlintCheck + 全量单测 + 红线 grep 三连 | 高 | ✅ 已完成（2026-08-15：ktlintCheck + testArm64DebugUnitTest BUILD SUCCESSFUL——ktlint 曾报 LogcatComponents.kt:253 chain-method-invocation「Unexpected newline before '.'」（background 参数跨行尾逗号后接 .padding），已修为单行链；全量单测 358 tests / 0 failures / 0 errors（40 文件）；红线三连全零：editor 包零 luajava/androlua/loadLibrary、vendor src 零 com.lioisme、git ls-files 二进制零） | `LOGCAT_LIST_STANDARD_R8` `验收` |
| S9 | 蒸馏：kb/ARCHITECTURE.md §10 日志行设计段更新（REVISION8）+ backlog 2x 注记 | 高 | ✅ 已完成（2026-08-15：ARCHITECTURE.md §10 改写为 REVISION8 徽章固定列定稿 + 本 backlog 完成注记更新） | `LOGCAT_LIST_STANDARD_R8` `蒸馏` |
| S10 | 收口：工作单标记全部完成 + 删除工作单 | 高 | ✅ 已完成（2026-08-15：工作单 LOGCAT_LIST_STANDARD_WORK_ORDER.md 已删除，结论蒸馏于 ARCHITECTURE.md §10） | `LOGCAT_LIST_STANDARD_R8` `收口` |

> **（已完成，2026-08-15 九次修订 LOGCAT_LIST_REVISION8：徽章固定列锚点）** 用户
> 验收反馈 REVISION7 前缀式**排版混乱**：消息列随 tag 长度逐行漂移成锯齿、前缀与
> 消息混排一行。用户拍板：重构为**徽章固定列锚点**结构（LogFox 同款）。定稿
> LogRow = `[级别徽章固定 28dp 列（圆角 6dp、级别色 15% alpha 淡底 + 级别色字母
> 居中，私有 LevelBadge）] + 固定 12dp 间距 + [主体 Column(weight 1f)：行1 消息
> bodySmall 等宽 onSurface maxLines=3 独占整行区零混排；行2 元数据 labelSmall
> 弱色一行 时间（tnum）· PID-TID（等宽）· tag（等宽 widthIn(max=120dp)）]`——
> 消息/元数据左缘全列表恒定对齐（行业列式规范：perfetto / MAUI.Sherpa / AS
> Logcat 同构）。常量：新增 LEVEL_BADGE_WIDTH(28dp) / BADGE_TO_CONTENT_GAP(12dp) /
> LEVEL_BADGE_CORNER(6dp) / LEVEL_BADGE_ALPHA(0.15f)；LOG_ROW_MIN_HEIGHT /
> TAG_MAX_WIDTH(120dp) / MAX_MESSAGE_LINES(3) / timeForList 保留；新增 imports
> （background / RoundedCornerShape）。不动项：级别色表/空态/列表容器/自动滚动/
> 详情页/数据层/i18n（零新增资源）/ AppBar / SearchBar / SearchView / 筛选菜单 /
> 搜索状态机红线。验收证据：ktlintCheck + testArm64DebugUnitTest BUILD SUCCESSFUL
> （ktlint 曾报 LogcatComponents.kt:253 chain-method-invocation「Unexpected newline
> before '.'」，background 参数跨行尾逗号后接 .padding 触发，已修为单行链）+
> 全量单测 **358 tests / 0 failures / 0 errors**（40 文件）+ 红线 grep 三连全零
> （editor 包零 luajava/androlua/loadLibrary、vendor src 零 com.lioisme、git
> ls-files 二进制零）。结论已蒸馏进 `docs/kb/ARCHITECTURE.md` §10。

---

## 2y. 日志能力全面迁移 kotlin-logging（2026-08-15，工作单 docs/in-progress/KOTLIN_LOGGING_WORK_ORDER.md）

> 来源需求（用户红线 2026-08-15）：日志猫完全使用 kotlin-logging 组件库实现，禁止自己手搓。
> 已收纳基线：`reference/kotlin-logging/`（v8.0.4，commit 0aaa57c）+ `reference/kotlin-logging-example-android/`
> （官方 Android 示例；经 ghfast.top 镜像克隆，台账已更新 docs/SOURCE_LOCATION.md 与 reference/README.md）。
> 审计结论：kotlin-logging 为「生产门面」（Android 原生工厂直连 android.util.Log；懒求值 + isLoggable 门控），
> 官方无 logcat 读取能力（v8 已删 MessageBufferLogger）；引擎 Java 与 Lua print 均为 logd 原生来源。
> 读取面取舍用户已拍板 = **选项 A**：写入面全量 kotlin-logging；日志猫保留 logcat 子进程唯一传输，
> 领域模型（级别枚举）对齐 kotlin-logging `Level`。

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| L1 | 依赖装配 + Android 原生工厂配置（LideApp companion init 设 `kotlin-logging-to-android-native`，pin `io.github.oshai:kotlin-logging:8.0.4` 入 ideDeps） | 高 | ✅ 已完成 | `KOTLIN_LOGGING` `kotlin-logging` `native 工厂` |
| L2 | 写入面全量迁移：23 文件 140 处 `android.util.Log.*` + 23 个 `const val TAG` → `KotlinLogging.logger("短名")`（懒求值，D4 显式命名） | 高 | ✅ 已完成 | `KOTLIN_LOGGING` `写入面` `TAG 清零` |
| L3 | 读取面级别对齐：删 `log/LogLevel.kt`，改 kotlin-logging `Level` + `log/LevelMappings.kt`（symbolChar/logcatSymbolToLevel，V→TRACE…F→ERROR）；LogFilter/LogcatParser/LogEntry/UI 消费点随动 + 测试更新新增 | 高 | ✅ 已完成 | `KOTLIN_LOGGING` `读取面` `Level 对齐` |
| L4 | 收尾门禁：宿主树 `android.util.Log`/`const val TAG`/`Log\.[vdiew]\(` 归零（vendor 豁免）+ 全量单测 + verifyMinimalismDebug/verifyVendorIsolation/verifySoraIsolation + 设备验收矩阵（九项筛选逐项一致）+ 文档蒸馏 | 高 | ✅ 已完成 | `KOTLIN_LOGGING` `验收` `门禁` |

> ✅ **整单完成（2026-08-15）**：L1-L4 全部落地。验证证据：单测全集 **363 tests / 0
> failures**（原 358 + 新增 LevelMappingsTest 5）；`compileArm64DebugKotlin` /
> `verifyMinimalismDebug` / `verifyVendorIsolation` / `verifySoraIsolation` /
> `assembleArm64Debug` 全绿；`arm64DebugRuntimeClasspath` 零 slf4j；宿主树
> `android.util.Log`/`Log\.[vdiew]\(`/`const val TAG = "` 零代码命中（LogFilter.kt:13
> 文档注释保留；SigningCrypto `TAG_*`/LocaleManager `TAG_*` 为 DER 常量/locale 键非日志
> tag）；`LogLevel` 全树归零。**设备验收（2026-08-15 补验，x64Debug/Android 14 模拟器）**：
> 工厂生效行 `active logger factory: AndroidNativeLoggerFactory` 两次启动确认；宿主短名
> tag 实测（`D ProjectManager: createProject: …`）；日志猫九项筛选/LUA/ERROR/搜索词过滤、
> Lua print（`I lua: hello: layout loaded`）、引擎错误日志、详情/复制/清空全部实测通过。
> 结论蒸馏：`docs/kb/ARCHITECTURE.md` §11（日志架构：kotlin-logging 迁移）；工作单已删除。

---

## 3. 风险与备忘

| 项 | 说明 |
|---|---|
| **keystore 敏感** | `app/src/main/assets/keys/` 含签名密钥与平台密钥，勿提交泄露；`app/keystore.properties`（gitignored）为 release/debug 签名路径唯一来源，不得硬编码回 build.gradle。 |
| **构建环境** | Windows 下用 `.\gradlew.bat :app:assembleArm64Debug`；NDK 固定 `29.0.14206865`（AGP `externalNativeBuild` ndk-build）。 |
| **编辑器性能红线** | 编辑器 API 调用保持主线程；补全/诊断/事件回调必须 `try/catch(Throwable)` 降级（AGENTS.md）。 |

---

> 新任务登记格式建议：`### [F8] 任务标题` + 优先级 + 关联文件 + 验收标准
> 关联：根目录 `README.md`（docs 入口为 `docs/README.md`）

---

## 2z. 日志猫核心重构（2026-08-15，已完成）

> 来源需求（用户 2026-08-15）：以第一性原理为目标，日志猫所有代码/逻辑按
> **原生实现＞组件库实现＞自实现** 排序；编码规范遵循官方规范/社区习惯；功能、
> 性能、质量达到语言与组件库上限；架构达到苹果/谷歌大厂验收标准；逐项完成在
> 工作单标记（已完成）+ 工作内容 + 验收证据。
> 审计结论（2026-08-15 深度审计，方案已定稿）：整体架构方向正确（读取面 =
> logcat 子进程唯一原生路径、写入面 = kotlin-logging 组件库、UI = M3 官方组件、
> 级别语义 = kotlin-logging Level 唯一事实源，均已符合原生＞组件库＞自实现）；
> 问题集中在**实现未达性能/正确性上限**：P0-1 进程重启重复条目（缺 `-T` 续读）、
> P0-2 最坏 ~98MB 内存 OOM 风险（无字节预算）、P1-1 每行全量快照发布（日志风暴
> GC 灾难）、P1-2 过滤每次全量 lowercase 分配（缺预建索引）、P1-3 多行归并 O(n²)
> 拼接、P2-1 共享搜索组件 `Api*` 命名错位、P2-2 copyToClipboard label 语义错位、
> P2-3 kotlin-logging 基线 8.0.5 与 pin 8.0.4 不一致、P2-4 截断无标记、P3 细节
> （UTF-8 显式 / destroyForcibly 超时 / 指数退避 jitter）。
> 用户拍板（2026-08-15）：字节预算 8MB 淘汰；过滤 = 节流 + 小写索引（不做增量
> 过滤，下期迭代）；`-T` 时间戳续读（同毫秒轻微重复接受，AS 同款）。

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| C1 | 登记：backlog 2z 条目 + 工作单创建 | 高 | ✅ 已完成（2026-08-15） | `LOGCAT_CORE` `登记` |
| C2 | 代码 A1：LogcatReader `-T` 续读 + UTF-8 + destroyForcibly 2s 超时 + 指数退避 jitter（logcatCommand / backoffDelayMs 抽纯函数） | 高 | ✅ 已完成（2026-08-15） | `LOGCAT_CORE` `A1` `Reader` |
| C3 | 代码 A2：LogBuffer 字节预算 8MB 淘汰（totalChars 记账）+ 归并 builder 分片 + 续行超限截断 | 高 | ✅ 已完成（2026-08-15） | `LOGCAT_CORE` `A2` `Buffer` |
| C4 | 代码 A3：LogcatStore 100ms 节流批量发布 + 虚拟时钟注入（nowProvider）+ truncated 标记 | 高 | ✅ 已完成（2026-08-15） | `LOGCAT_CORE` `A3` `节流` |
| C5 | 代码 A4：LogEntry 小写索引（tagLower/messageLower 构造期一次）+ filterEntries 零分配 | 高 | ✅ 已完成（2026-08-15） | `LOGCAT_CORE` `A4` `索引` |
| C6 | 测试 D1：新增 LogBufferTest / LogFilterTest / Reader 命令与退避测试 + LogcatStoreTest 节流适配 | 高 | ✅ 已完成（2026-08-15） | `LOGCAT_CORE` `测试` |
| C7 | 架构 B1：共享搜索组件中立化至 ui/components（SearchInputField / CollapsedSearchInput / copyToClipboard(label)） | 中 | ✅ 已完成（2026-08-15） | `LOGCAT_CORE` `B1` `中立化` |
| C8 | 架构 B2：kotlin-logging 版本复核（8.0.5 maven 实测未发布，维持 8.0.4 为正确 pin） | 中 | ✅ 已完成（2026-08-15） | `LOGCAT_CORE` `B2` `版本` |
| C9 | UI C：详情页截断标记（logcat_truncated 资源 + 弱色标注） | 中 | ✅ 已完成（2026-08-15） | `LOGCAT_CORE` `C` `截断标记` |
| C10 | 验证 D2：compileArm64DebugKotlin + 全量单测（42 类 367 tests 全绿）+ ktlintCheck 全量 PASS + 红线四连 grep 全零 | 高 | ✅ 已完成（2026-08-15） | `LOGCAT_CORE` `验收` |
| C11 | 收尾 E：蒸馏 ARCHITECTURE.md §10 修订（数据层契约/组件中立化）+ backlog 2z 注记 + 工作单已删除 | 中 | ✅ 已完成（2026-08-15） | `LOGCAT_CORE` `蒸馏` |

> 完成注记（2026-08-15）：工作单 `docs/in-progress/LOGCAT_CORE_REFACTOR_WORK_ORDER.md`
> 全程逐项标记（已完成）+ 工作内容 + 验收证据，收口后已删除（git 历史保留）；
> 结论蒸馏至 `docs/kb/ARCHITECTURE.md` §10（-T 续读 / 字节预算 8MB / 节流 100ms /
> 小写索引 / truncated 标记 / 组件中立化 / 8.0.4 pin 复核）。附带收益：ktlintCheck
> 全量 PASS 清零 KOTLIN_LOGGING 迁移遗留 30 文件 import 排序/空行违规（原登记
> backlog R11 专项随本次收口，无独立工作单）。

---

