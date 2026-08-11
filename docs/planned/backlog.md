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
| R1 | 引擎面三处同步收敛（build.gradle / proguard / 分类器）为单一事实源 + 修复 AlertDialogBuilder 分类 | 高 | `engineSurface` `三处同步` |
| R2 | ApiIndex dexNames 契约对齐：O(1) 可加载性查表 + 宿主 dex 快照进程级缓存 + 修订 AGENTS.md 措辞 | 高 | `dexNames` `DexFile` `O(1)` |
| R3 | 原生模块表收敛：ApiIndex 补全 lfs/lsqlite3/live2d/luajava + cpp 目录一致性测试 | 高 | `builtinNativeModules` |
| R4 | apibrowser 测试补位（分类器/过滤/缓存契约） | 高 | `apibrowser 测试` |
| R5 | LuaDiagnostics 热路径（close 序列预计算 / errorAt 行偏移表 / countLines 增量） | 中 | `LuaDiagnostics` `热路径` |
| R6 | 机械去重（basename / randomPassword / 错误前缀正则 / u16u32 / 主题名 / 种子色 / onOff / 权限池 / main.lua / 签名 Triple / catch Throwable） | 中 | `去重` |
| R7 | SigningScreen 主线程密码学/文件 IO 异步化（6 处） | 中 | `SigningScreen` `主线程` |
| R8 | navigation-compose 升级 2.9.8 后删除 NavigationGate（缺陷根除回归） | 中 | `NavigationGate` `navigation 2.9` |
| R9 | 依赖栈批量升级：BOM 2026.06.00 / lifecycle 2.10 / activity-compose 1.13 / core-ktx / coroutines-test | 中 | `依赖升级` |
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
| R1 | 只读绕过修复：insertImport（运行错误对话框）与 toggleCase（文本操作）直写 Content 绕过 sora 门禁 | 高 | ✅ 已完成（2026-08-10，工作单收口） | `readOnly` `insertImport` `toggleCase` |
| R2 | 只读切换 IME 同步（restartInput / hideSoftInput），修复切回可写键盘假死 | 高 | ✅ 已完成（2026-08-10，工作单收口） | `readOnly` `InputConnection` |
| R3 | 只读 UX：编辑/保存菜单禁用 + 编辑器只读横幅提示 | 中 | ✅ 已完成（2026-08-10，工作单收口） | `readOnly` `菜单禁用` `横幅` |
| R4 | 线号分割线宽度 / 固定行号（pinLineNumber）两项 sora 上限能力接线 | 中 | ✅ 已完成（2026-08-10，工作单收口） | `setDividerWidth` `setPinLineNumber` |
| R5 | 打包 API 级别下移：AXmlManifest uses-sdk 解析/写回 + ManifestEdit + 构建对话框 minSdk 选项 + 测试 | 中 | ✅ 已完成（2026-08-10，工作单收口） | `uses-sdk` `minSdkVersion` `打包` |
| R6 | WP-014 工程 theme 运行时消费（逐工程深色，编辑器文本区跟随） | 中 | ✅ 已完成（2026-08-10，工作单收口） | `WP-014` `theme` `深色` |

## 2g. 编辑器对标 Squircle CE 整改（2026-08-11，工作单 docs/in-progress/EDITOR_SQUIRCLE_ALIGNMENT_WORK_ORDER.md）

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|---|---|---|---|
| S1 | 真只读重构：删 ReadOnlyBanner / 菜单置灰去只读 / 删冗余 hideSoftInput / ⋮ 菜单加「只读」直达切换 | 高 | 🔄 进行中 | `readOnly` `只读` `Squircle` |
| S2 | 去池化：删除 EditorViewPool，回归 remember+release 朴素生命周期 | 高 | 待执行 | `EditorViewPool` `去池化` |
| S3 | 行号分界线：主题 JSON 加 divider 色键 + LideTextMateColorScheme + 固定 1dp 分割线 | 高 | 待执行 | `LINE_DIVIDER` `分割线` |
| S4 | 设置瘦身：删 7 项（粘性行数/lnPanel/pin/分割线宽度/光标动画/行高/undo 栈）21→14 | 中 | 待执行 | `编辑器设置` `瘦身` |
| S5 | 功能补缺：sora 原生 EditorSearcher 查找/替换面板 + 跳行对话框 | 中 | 待执行 | `EditorSearcher` `查找替换` `跳行` |

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

## 3. 风险与备忘

| 项 | 说明 |
|---|---|
| **keystore 敏感** | `app/src/main/assets/keys/` 含签名密钥与平台密钥，勿提交泄露；`app/keystore.properties`（gitignored）为 release/debug 签名路径唯一来源，不得硬编码回 build.gradle。 |
| **构建环境** | Windows 下用 `.\gradlew.bat :app:assembleArm64Debug`；NDK 固定 `29.0.14206865`（AGP `externalNativeBuild` ndk-build）。 |
| **编辑器性能红线** | 编辑器 API 调用保持主线程；补全/诊断/事件回调必须 `try/catch(Throwable)` 降级（AGENTS.md）。 |

---

> 新任务登记格式建议：`### [F8] 任务标题` + 优先级 + 关联文件 + 验收标准
> 关联：根目录 `README.md`（docs 入口为 `docs/README.md`）
