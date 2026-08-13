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
