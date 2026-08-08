# 任务看板（Task Board）

> **类型**：任务看板 · 状态实时更新
> **用途**：回答「项目做到哪了」「现在该做什么」
> **权威状态矩阵**：[kb/README.md](kb/README.md) §2（本文档是其摘要入口）

---

## 当前进行中

| 任务 | 文档 | 进度 / 状态 |
|---|---|---|
| 对齐 pro 宿主 IDE 功能（19 个 WP） | [in-progress/PARITY_COMPLETION_WORK_ORDER.md](in-progress/PARITY_COMPLETION_WORK_ORDER.md) | **13/19 完成**（WP-001/002/003/004/005/008/013/014/015/016/017/018/019，其中 WP-014 代码落地未真机验收）；未开工 WP-006/007/009/010/011/012 |
| 编辑器性能 V2 收尾 | [discard/EDITOR_PERFORMANCE_AUDIT_V2_REPORT.md](discard/EDITOR_PERFORMANCE_AUDIT_V2_REPORT.md) | ✅ 已完成归档（2026-08-05）：阶段 A/B 落地，剩余 R8/阶段 C 入 backlog |
| 编辑器 UI 对齐（Squircle-CE 标杆） | [in-progress/EDITOR_UI_ALIGNMENT_WORK_ORDER.md](in-progress/EDITOR_UI_ALIGNMENT_WORK_ORDER.md) | WP-003~006 ✅ 已完成；WP-001/002/007/008 ⛔ 被取代（抽屉已删除→文件浏览对话框）；WP-009/010 未完成 |
| 编辑器功能完善至 sora 原生上限（F8） | [planned/EDITOR_FEATURE_COMPLETION_WORK_ORDER.md](planned/EDITOR_FEATURE_COMPLETION_WORK_ORDER.md) | P0~P3 ✅ 已完成（设置中心 21 项 / Snippet / 多错误聚合+quickfix / 折叠闭环仅渲染 / 文本操作 / 整词搜索 / R8+baseline profile / 降级矩阵 / perf-check / sora 更新管线+CI+R1 job / 台账）；P4 文档 ✅；**剩设备功能验收 §8.3（模拟器复测推迟）**，通过后归档 discard |
| 编辑器全语言支持（39 种 TextMate 文法） | [discard/EDITOR_ALL_LANGUAGES_SUPPORT_WORK_ORDER.md](discard/EDITOR_ALL_LANGUAGES_SUPPORT_WORK_ORDER.md) | ✅ 已完成并归档（2026-08-07）：资产迁入 `:editor` / LanguageCatalog+Detector+KeywordRegistry+EditorLanguage 替换 LuaLanguage / TextMateInitializer 全量注册 / LuaEditor 按文件 scope 装配；编译通过、R1~R5 复核过、APK 81 项资产核对、模拟器+真机多色高亮验证；全语言补全交互验收并入后续综合复测 |
| 社区代码知识库 | [kb/androlua-community/README.md](kb/androlua-community/README.md) | 2144 篇初筛完成；正负面卡 + 训练 seed 产出 |
| 独立 APK 打包器（WP-014，代码已落地） | [kb/ROADMAP.md](kb/ROADMAP.md) §1 | 代码落地 + 打包单测绿；未真机验收 |
| 设置与权限治理（独立入口 + 动态权限 + 移除手册/帮助） | [planned/SETTINGS_PERMISSIONS_REWORK_WORK_ORDER.md](planned/SETTINGS_PERMISSIONS_REWORK_WORK_ORDER.md) | ✅ **已完成（2026-08-07）**：8 项要求全部落地——引擎动态权限回调（LuaActivity.onRequestPermissionsResult）/ 移除 Lua 手册与帮助 / 编辑器设置恢复默认 / 权限管理移入主设置「通用」分区 / 编辑器与 AndroLua 两个独立入口独立页面（合并方案当日被否决，二轮拆分完成）；assembleArm64Debug 绿 + R1 门禁零命中；剩模拟器 UI 回归与打包产物动态权限实测后归档 discard |
| 插件功能移除 + 编辑器菜单治理 | [discard/PLUGIN_FEATURE_REMOVAL_WORK_ORDER.md](discard/PLUGIN_FEATURE_REMOVAL_WORK_ORDER.md) | ✅ **已完成并归档（2026-08-07，LIDE-WO-2026-020，D-20 部分反转 D-19 保留条款）**：删 PluginScreen/PluginManager/plugin.json/示例插件提取/插件路由与字符串，`.alp` 一律按工程导入（删 AlpKind.PLUGIN）；标签卡删 ⋮ 按钮（长按松手弹关闭菜单）；右上角「更多」菜单多级化（分类→子菜单）；assembleArm64Debug 绿 + 单测全绿 + `:editor` 主源 ktlint 通过 + R1 门禁零命中 |
| 编辑器右上角「更多」菜单 cascade 化 | [discard/EDITOR_MULTI_LEVEL_MENU_WORK_ORDER.md](discard/EDITOR_MULTI_LEVEL_MENU_WORK_ORDER.md) | ✅ **已完成并归档（2026-08-07，LIDE-WO-2026-022，D-21）**：D-20 手写 `MoreSubmenuItem`（Box 嵌套 DropdownMenu）二级菜单位置错位且违「禁手搓」；改用成熟第三方 `me.saket.cascade:cascade-compose:2.3.0`（M3 官方无嵌套菜单；POM 实证 runtime scope，BOM→material3 1.4.0 胜出），`CascadeDropdownMenu` + 一级分类 children 级联（右箭头/返回头/边缘与键盘避让）；删 `MoreSubmenuItem`；编译/依赖解析/单测/ktlint/assembleArm64Debug 全绿 + R1 门禁零命中 |
| 打包构建三大能力（签名管理 / Lua 编译加密 / 原生库通用化） | [planned/BUILD_SIGNING_ENCRYPTION_WORK_ORDER.md](planned/BUILD_SIGNING_ENCRYPTION_WORK_ORDER.md) | 🚧 进行中（2026-08-07 立项）：T1~T3/T5 已完成——签名三类（内置/导入/自生成）、编译/加密独立开关（默认均开）、build.* 43 单测全绿 + assembleArm64Debug 绿 + 模拟器 DES loopback；T4 文档同步（WHITEPAPER §5.5/阶段五 + AGENTS.md 已回填）收尾中 |
| 打包载荷精化（产物仅含 androlua 工程所需） | [planned/PACKAGING_PAYLOAD_CLEANUP_WORK_ORDER.md](planned/PACKAGING_PAYLOAD_CLEANUP_WORK_ORDER.md) | ✅ **已完成（2026-08-07）**：`HostPolicy` 领域分类（默认拒绝替代黑名单剔除）——内置 `.alp` 示例 / `javaapi` 类表 / keys / textmate / layouts 一律不进产物（对齐 pro bin.lua 宿主 assets 整树剔除）；`ApkBuilder` 接入 + UI 载荷摘要 + `ApkBuilderTest` 17/17 绿 + assembleArm64Debug 绿；剩模拟器产物运行回归后归档 discard |
| 设置界面简约化（AndroLua 与打包合并 / 自动安装开关 / 签名独立界面） | [planned/SETTINGS_SIMPLIFY_WORK_ORDER.md](planned/SETTINGS_SIMPLIFY_WORK_ORDER.md) | ✅ **已完成（2026-08-07）**：主设置页 AndroLua 单入口 → 合并页（工具：API 浏览器/示例；打包：自动安装/原生库/编译/加密 4 开关；默认 ABI 5 项）；删 `BuildOptionsScreen` 与 `Routes.BUILD_OPTIONS`；自动安装单一开关默认启用（pro 兼容）；签名管理主设置「通用」分区独立入口（`SigningScreen` 既有能力不动）；去解释性文案（10 条字符串删除、`signing_hint` 精简）；`compileArm64DebugKotlin`/`assembleArm64Debug`/build.* 单测/R1 门禁全绿；剩模拟器 UI 回归后归档 discard |
| 打包 UI 重构（复选框化 / ABI 宿主驱动置灰 / 优化打包更名 / 整页改对话框） | [planned/PACKAGING_UI_REWORK_WORK_ORDER.md](planned/PACKAGING_UI_REWORK_WORK_ORDER.md) | ✅ **已完成（2026-08-08）**：签名 v1/v2/v3 与 ABI 改经典 Checkbox；默认 ABI 选项由 `ZipUtils.listAbis` 实测驱动（删硬编码名单），未携带架构置灰禁用；「仅携带依赖的原生库」更名「优化打包」（仅针对原生库）；删除 `BuildApkScreen.kt` 整页与 `BUILD_APK` 路由，新建 `BuildPackagingDialog`（编辑器 ⋮ 菜单触发）；`BuildPackagingTest` 零改动全绿 + assembleArm64Debug 绿；剩模拟器 UI 回归后归档 discard |

| 打包运行时五需求（解耦/载荷/ABI/输出/设置收敛） | [in-progress/RUNTIME_PACKAGING_5REQ_WORK_ORDER.md](in-progress/RUNTIME_PACKAGING_5REQ_WORK_ORDER.md) | 🚧 **P1~P3 + P0 已完成（2026-08-07）+ 2026-08-08 引擎 dex 单份化修订（P0-6）**：用户红线「所有 dex 一律宿主 APK 根目录、禁他处、禁多份」——`:androlua` 改 compileOnly/testImplementation/runtimeImplementation，`embedRuntimeDex`/`assets/runtime` 形态废弃，改 `engineDexExtract` + `injectEngineDex<Variant>` 根目录单份注入 + `assets/engine.dex.idx` 标记契约（ApkBuilder `ENGINE_DEX_MARKER`），`verifyMinimalism` 增契约断言；A 阶段全绿（152 单测 0 失败 + verifyRuntimeSurface）；**剩全链验证（assembleUniversalRuntime → assembleArm64Debug → APK 根目录检查 → 模拟器冒烟）后归档** |
| 打包 dex 直用宿主（P0-7：移除打包专用 dex 设计） | [in-progress/PACKAGING_DEX_SIMPLIFY_WORK_ORDER.md](in-progress/PACKAGING_DEX_SIMPLIFY_WORK_ORDER.md) | 🚧 进行中（2026-08-08 立项）：`ApkBuilder` 删 dex 替换模式，产物直用宿主全量根 dex（IDE + 引擎类，接受多余内容）；标记仅作引擎存在性/清单回落门禁；宿主构建流程零改动；T1~T4 见工作单 |
| LIDE 精简治理（去重门禁：重复 dex/原生库/资源） | [planned/MINIMALISM_AUDIT_REPORT.md](planned/MINIMALISM_AUDIT_REPORT.md) | ✅ **已完成（2026-08-07）**：T1~T5 全落地——`lide.examples` 默认零内置 + `lide.embedRuntimeDex` 默认开启双门控；`verifyMinimalism` 去重门禁（jniLibs 重建即拒 / lib 白名单 / 跨 ABI 一致性 / 默认零内置示例）接入 CI（verify-release job）；删 fileTree("libs") 死声明 + 根 classes.jar + androlua compileSdk 对齐；ExamplesScreen 空态 `examples_empty`（en/zh）；AGENTS.md「精简治理」章节 + KB §2.3 登记。**2026-08-08 修订**：引擎 dex 单份化（P1 冗余根治，见运行时基座 P0-6）；剩 assembleArm64Debug + verifyMinimalism 实测后归档 discard |
| 模拟器定向全量回归（功能+性能，2026-08 完工项） | [in-progress/EMULATOR_REGRESSION_2026-08-07.md](in-progress/EMULATOR_REGRESSION_2026-08-07.md) | ✅ **已完成（2026-08-07）**：23 项全 PASS、零 FATAL/ANR——设置重组/动态权限/全语言高亮/级联菜单/API 搜索红线/示例空态/打包自装/产物独立运行/大文件降级；性能：冷启动 1110/1087ms、大文件 105MB PSS；证据截图 %TEMP%\opencode\regress\ | 归档前置：arm64 真机矩阵（build-apks.ps1）可更新报告 §9 后归档 discard |
| 源码组织五需求审计 + 整改（集中/app / 上游隔离 / pro 一致 / 全 Kotlin / 无 dex 重复） | [discard/SOURCE_ORGANIZATION_5REQ_AUDIT_REPORT.md](discard/SOURCE_ORGANIZATION_5REQ_AUDIT_REPORT.md) | ✅ **已完成（2026-08-09）**：Phase 0~4 全部闭环——引擎页 Kotlin 化归位 `app/.../engine/`；editor 并单进 app（:editor 模块删除）；UPSTREAM.md 台账 + `verifyVendorIsolation` 门禁（CI verify-vendor job）；`verifyRuntimeSurface` 包级断言（lidePrefixes + ideDepPrefixes）；`checkDexDuplication` 四类跨 dex 断言（scanDexClassNames **type_ids 二次间接修复**——旧实现解析错位致引擎面断言形同虚设）；**引擎 dex 纯净化**（IDE 依赖三形态 compileOnly+debug+release、compose-bom 三形态、constraints annotations 23.0.0、runtime themes.xml，模拟器 NoSuchMethodError 根因修复，runtime dex 5945→775 类）；6 变体打包回归 + ApkBuilderTest 全绿；模拟器回归 4 工程矩阵 8 项 PASS + 运行按钮全链路（编辑 → EngineActivity → Lua 输出）验证 | 已归档 docs/discard/；蒸馏进 AGENTS.md（scanDex 解析语义、IDE 三形态约定、verify 门禁命令） |

## 已完成（摘要）

已完成文档 39 篇 + 过时文档 11 篇统一丢弃于 [discard/](discard/)（2026-08-07 起其中 15 篇已物理删除，条目保留作墓碑；引擎迁移 WP-001~010、导航冻结修复、API 浏览器、仓库硬化、Live2D 迁移、全语言支持、UI 重构/性能/盘点/双端验证等；设置重组 `SETTINGS_ORGANIZATION_WORK_ORDER` 已被设置/权限治理新要求取代而归档；2026-08-08 新增自生成签名证书内容自定义 `SIGNING_GENERATE_CUSTOM_FIELDS_WORK_ORDER`——`CertSubject` 全字段 + 有效期自定义，build.* 57 单测全绿 + assembleArm64Debug 绿；2026-08-09 新增源码组织五需求审计与整改 `SOURCE_ORGANIZATION_5REQ_AUDIT_REPORT`——Phase 0~4 全部闭环，审计+整改+回归全证据在报告 §4），清单与结论蒸馏去向见 [discard/README.md](discard/README.md) §3，矩阵见 [kb/README.md](kb/README.md) §2。

## 计划中 / 未排期

| 任务 | 文档 | 状态 |
|---|---|---|
| 技术债盘点（TD1~TD7） | [planned/backlog.md](planned/backlog.md) | 进行中（2026-08-05 清理：TD1/2/6/7 已核销，剩 TD3/4/5 + F1~F7） |
| Xposed 模块路线图（M1~M5） | [planned/XPOSED_MODULE_FEASIBILITY_REPORT.md](planned/XPOSED_MODULE_FEASIBILITY_REPORT.md) | 研究定稿，未启动 |
| 引擎性能优化建议 | [planned/GAME_ENGINE_PERFORMANCE_AUDIT_REPORT.md](planned/GAME_ENGINE_PERFORMANCE_AUDIT_REPORT.md) | 审计完成，建议未排期（看板娘已原生化，石头未动） |
| 架构清理与重构（补丁式/重复代码收敛） | [planned/ARCHITECTURE_CLEANUP_AUDIT_REPORT.md](planned/ARCHITECTURE_CLEANUP_AUDIT_REPORT.md) | 进行中（2026-08-06：Phase A 全部 + Phase B 全部 + Phase C 部分已完成，见文档完成记录） |
| 架构清理第二轮（P1~P7：apksig 签名链路 / TextMateRuntime 收敛 / 常量与去重 / 宿主缺陷 / EngineContract） | [planned/ARCHITECTURE_CLEANUP_BATCH2_REPORT.md](planned/ARCHITECTURE_CLEANUP_BATCH2_REPORT.md) | **✅ 已完成（2026-08-07）**：145 单测 0 失败 + assembleArm64Debug 绿 + R1 门禁零命中；遗留 C-1/C-2 真机验证、D-3 catch 清理、§4.4 五项（见报告 §P7） |
| ~~**LIDE 插件平台（小政府）立项：开放 Plugin SDK + LIDE 聚合 androlua 首个插件**~~ | ~~[planned/PLUGIN_PLATFORM_REPORT.md](planned/PLUGIN_PLATFORM_REPORT.md)~~ | **已取消（2026-08-07）**：Shadow 插件机制拆除，androlua 维持上游基线 library 集成（D-19 反转 D-18）；设计文档已归档 `discard/PLUGIN_PLATFORM_REPORT.md`，拆除执行见 `discard/SHADOW_REMOVAL_DEAD_CODE_CLEANUP_WORK_ORDER.md` |

## 维护规则

1. **状态机**：📋 计划中 → 🚧 进行中 → ✅ 已完成（不允许跳级）。
2. 新任务先登记 [planned/backlog.md](planned/backlog.md)；开始做时 `git mv` 到 [in-progress/](in-progress/)；**整份任务文档完成后 `git mv` 到 [discard/](discard/)**。
3. 每篇任务文档头部必须注明：状态、目标、验收标准、关联代码路径。
4. **逐项标记**：每完成一项任务，必须在任务文档对应条目标记「（已完成）」并备注工作内容 + 日期 + 验收证据（构建命令 / 设备验证 / 测试）。
5. 完成判断以**工作区真实代码/构建结果**为准；状态变化当天更新本文档 + 任务文档 + [kb/README.md](kb/README.md) §2 矩阵 + [discard/README.md](discard/README.md) §3 清单。