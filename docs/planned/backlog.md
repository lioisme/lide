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

## 3. 风险与备忘

| 项 | 说明 |
|---|---|
| **keystore 敏感** | `app/src/main/assets/keys/` 含签名密钥与平台密钥，勿提交泄露；`app/keystore.properties`（gitignored）为 release/debug 签名路径唯一来源，不得硬编码回 build.gradle。 |
| **构建环境** | Windows 下用 `.\gradlew.bat :app:assembleArm64Debug`；NDK 固定 `29.0.14206865`（AGP `externalNativeBuild` ndk-build）。 |
| **编辑器性能红线** | 编辑器 API 调用保持主线程；补全/诊断/事件回调必须 `try/catch(Throwable)` 降级（AGENTS.md）。 |

---

> 新任务登记格式建议：`### [F8] 任务标题` + 优先级 + 关联文件 + 验收标准
> 关联：根目录 `README.md`（docs 入口为 `docs/README.md`）
