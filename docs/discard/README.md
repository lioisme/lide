# 文档丢弃区（docs/discard/）

> **本目录存放「已完成 / 已过时 / 已被取代 / 无价值」的全部文档（统一丢弃区，唯一出口）。**
> 文件保留 git 历史（`git mv` 移动），不再维护、不参与状态管理。
> **Agents 规则：日常任务禁止把本目录当作事实来源或任务输入**——事实以 `docs/kb/` 为准，
> 当前工作以 `docs/in-progress/` 与 `docs/planned/` 为准；仅在追溯历史结论时按需查阅。
> 本目录由原 `docs/completed/`（30 篇已完成文档）与 `docs/archive/`（8 篇过时文档）于 2026-08-05 合并而来。

---

## 1. 任务文档生命周期（Agent 必须遵守）

1. 新任务 → 建立任务文档于 `docs/planned/`（或 `docs/in-progress/`），登记 `docs/kb/README.md` §2。
2. 执行中 → 文档停留在 `docs/in-progress/`；**每完成一项任务，必须在文档内对应条目标记
   「（已完成）」并备注工作内容 + 日期 + 验收证据**（构建命令 / 设备验证 / 测试）。
3. 整份任务文档全部完成 → `git mv` 到本目录（`docs/discard/`），并同步：
   - `docs/kb/README.md` §2 状态矩阵（移出进行中/计划中，若结论有沉淀价值则确保 `kb/` 已收录）；
   - `docs/TASKS.md`（任务看板）；
   - 本 README §3 清单追加一行（一句话 + 日期 + 对应 WP）。
4. 过时 / 方案未采纳 / 被取代的文档 → 随时 `git mv` 到本目录，登记归档原因。
5. 本目录是**单一路径出口**：不再使用 `completed/`、`archive/` 等其它终结目录。

## 2. 阅读导航（追溯历史结论时）

| 想查什么 | 去哪 |
|---|---|
| 当前事实（推荐） | `docs/kb/`（ARCHITECTURE / ENGINE_RUNTIME / EDITOR / DECISIONS / ROADMAP + 子库） |
| 当前工作状态 | `docs/TASKS.md` → `docs/in-progress/` |
| 历史审计结论 / 旧报告 | 本目录 §3 清单 → 对应文件 |
| 上游基线 / 社区语料 | `reference/`（只读，gitignore；2026-08-05 自 docs/reference/ 迁出，纯净基线已去重） |

## 3. 丢弃清单（35 篇，其中 15 篇已物理删除，条目保留作墓碑）

### 3.1 已完成文档（登记 39 篇；14 篇已物理删除）

> 结论均已沉淀进 `docs/kb/`；对应蒸馏位置见各条目「结论去向」。

| 文件 | 日期 | 内容一句话 | 结论去向 |
|------|------|-----------|---------|
| `ENGINE_MIGRATION_WORK_ORDER.md` | 立项期 | LuaJ→AndroLua C 引擎迁移总规划（7 WP / 21 天 / 8 ACC） | kb/ARCHITECTURE + PROGRESS WP-001~006 |
| `ANDROLUA_SOURCE_CODE_AUDIT_REPORT.md` | 2026-07-31 | 三源码树谱系审计：选 Pro、拒 AndroLuaX（闭源），确认 Lua 5.3.3 深度魔改 9 语法 | kb/ENGINE_RUNTIME |
| `ANDROLUA_INTEGRATION_PLAN.md` | 2026-07-31 | 引擎集成 7 Phase 方案（Java EngineActivity 继承 LuaActivity、静态编译替代 engine.dex） | kb/ARCHITECTURE |
| `NATIVE_LIB_AUDIT.md` | 2026-07-31 | 缺失 13 原生库，修复构建源后 NDK 29 全量编译 42/42 成功 | kb/ENGINE_RUNTIME（由 LIBS-003/006 收尾） |
| `ENGINE_LEVEL_COMPARISON_REPORT.md` | 2026-07-31 | 探针 878 vs 879/956，定案 socket 失败 = 资产解包子目录缺陷并已修复 | kb/ENGINE_RUNTIME |
| `ANDROLUA_FEATURE_PARITY_AUDIT.md` | 2026-07-31 | 工程/库/插件/模块一致性审计，P0/P1/P2 全部完成 | kb/ENGINE_RUNTIME（P2-12 终态后被 WP-009 反转） |
| `ANDROLUA_PARITY_FULL_AUDIT_REPORT.md` | 2026-07-31 | 独立全量复核：运行时 100% 对齐、§12 行动全收口 | kb/ENGINE_RUNTIME（对等审计最新版） |
| `DEPENDENCY_MODEL_AUDIT_REPORT.md` | 2026-07-31 | 全局依赖仓库（common/）去留审计，方案 A 已执行（净删 ~1700 行） | kb/DECISIONS D-07 |
| `LIBS_SUPPORT_WORK_ORDER.md` | 2026-08-01 | /libs 全链路收口 LIBS-001~007，真机矩阵 V-01~V-10 全绿；修复 W^X dex 加载 | kb/ENGINE_RUNTIME + AGENTS.md /libs 约定 |
| `FULL_AUDIT_REPORT_2026-08-01.md` | 2026-08-01 | 全量审计：引擎 100% 对齐且超集、IDE 对齐 ~6 成、P0/P1/P2 共 21 项 | PARITY 工作单承接（in-progress） |
| `EMULATOR_RUNTIME_AUDIT_REPORT.md` | 2026-08-01 | 12 参考工程 lide vs pro 日志逐字一致；修复 LuaActivity 未注册 + cleartext 两缺陷 | kb/ENGINE_RUNTIME |
| `EDITOR_CRASH_ANALYSIS_REPORT.md` | 2026-07-31 | 闪退根因 = 编辑器边界违规引入 luajava，已通过 `:editor` 独立模块 + R1~R4 恢复 | kb/EDITOR + AGENTS.md R1 |
| `EDITOR_DRAWER_AUDIT_REPORT.md` | 2026-08-01 | 抽屉三缺陷修复 + 宽屏 Permanent 适配，Phase 0~6 全部完成 | kb/EDITOR |
| `EDITOR_PERFORMANCE_AUDIT_REPORT.md` | 2026-07-31 | 定位 4 个 P0 性能灾难链路，根因已修复 | kb/EDITOR（阶段 B/C 并入 V2 报告） |
| `NAVIGATION_FREEZE_FIX_REPORT.md` | 2026-08-04 | 导航冻结根治（NavigationGate 单一漏斗门控） | kb/ARCHITECTURE + AGENTS.md |
| `ANDROLUA_RUNTIME_INVENTORY_REPORT.md` | 2026-08-02 | 运行时全量盘点：Java/原生/Lua/构建/耦合面逐字节（MD5）审计 | kb/ENGINE_RUNTIME（盘点基线）（已物理删除 2026-08-07） |
| `ANDROLUA_PARITY_DUAL_END_FINAL_VERIFICATION.md` | 2026-08-02 | 运行环境三对齐 + 双端实测终审：除白名单项完全一致 | kb/ENGINE_RUNTIME（已物理删除 2026-08-07） |
| `MD3_PROBE_DUAL_END_COMPARISON_REPORT.md` | 2026-08-01 | MD3 探针双端对比：pro 834/865、lide 832/865；lide = 真 MD3 组件 | kb/ENGINE_RUNTIME（已物理删除 2026-08-07） |
| `KANBAN_LIVE2D_BUGFIX_LUA_AUDIT_REPORT.md` | 2026-08-02 | 看板娘三问题（延迟崩溃/绘制错乱/规范化）同根因分析与修复 | kb/ENGINE_RUNTIME（Live2D）（已物理删除 2026-08-07） |
| `LIVE2D_NATIVE_MIGRATION_COMPLETION_REPORT.md` | 2026-08-02 | Live2D 原生迁移完成：Lua 直调 `liblive2d.so`，12 项双端 PASS | kb/ENGINE_RUNTIME（Live2D）（已物理删除 2026-08-07） |
| `API_BROWSER_AUDIT_REPORT.md` | 2026-08-02 | API 浏览器现状审计 + 可行性报告；已实施并模拟器验收 | kb/EDITOR + AGENTS.md API 约定 |
| `API_BROWSER_FREEZE_AUDIT.md` | 2026-08-03 | API 浏览器界面无响应根因全量审计（material 3.1.4.0 反编译核验） | AGENTS.md 搜索语义红线（修复单见 planned/API_BROWSER_FREEZE_FIX_PLAN.md）（已物理删除 2026-08-07） |
| `API_BROWSER_PERFORMANCE_AUDIT_REPORT.md` | 2026-08-03 | API 浏览器性能专项：冷启动/滚动徽章风暴/过滤开销/详情反射语义 | AGENTS.md + kb/EDITOR（已物理删除 2026-08-07） |
| `API_BROWSER_REDESIGN_PLAN.md` | 2026-08-03 | API 浏览器界面重构全量审计 + 任务执行单（T0~T6） | AGENTS.md + kb/EDITOR（已物理删除 2026-08-07） |
| `HOST_PERFORMANCE_AUDIT_REPORT.md` | 2026-08-01 | 宿主（IDE）性能审计 V2：编辑器热路径/列表/启动 | kb/EDITOR + kb/ROADMAP（已物理删除 2026-08-07） |
| `UI_REWORK_AUDIT_REPORT.md` | 2026-08-02 | UI 重构 P0 五项违规清单 + 解决方案，设备验证 PASS | kb/EDITOR（已物理删除 2026-08-07） |
| `LIVE2D_NATIVE_MIGRATION_PLAN.md` | 2026-08-01 | Live2D 原生迁移任务方法文档（目标/对照模式/验收） | 已完成（见完成报告）（已物理删除 2026-08-07） |
| `REPO_HARDENING_WORK_ORDER.md` | 2026-08-04 | 源码规范/可复现构建工作单（T1~T11 逐项验证） | kb/ARCHITECTURE + 终审报告（已物理删除 2026-08-07） |
| `REPO_HARDENING_FINAL_AUDIT.md` | 2026-08-04 | 仓库硬化终审 T1~T9 全部通过：源码可完全从仓库构建 | kb/ARCHITECTURE + AGENTS.md R5（已物理删除 2026-08-07） |
| `SOURCE_AUDIT_REPORT_2026-08-05.md` | 2026-08-05 | 全量源码审计：技术栈正确性 × 屎山清单 × 大厂验收修复计划（P0×2 签名族已决策搁置） | 行动项见 kb/ROADMAP + planned/backlog |
| `EDITOR_PERFORMANCE_AUDIT_V2_REPORT.md` | 2026-07-31 | 编辑器性能审计 V2：阶段 A 止血 + 阶段 B 标签卡/工程树全部落地；转场建议被 slide 决策取代 | kb/EDITOR §3 + kb/ROADMAP（剩余 R8/阶段 C 入 backlog） |
| `DOCS_GOVERNANCE_AUDIT_REPORT_2026-08-05.md` | 2026-08-05 | 文档治理审计：全量文档去重分类、统一丢弃区重组（本目录建立依据） | docs/discard/README §1 + kb/README §6/§7 |
| `REFERENCE_ASSETS_GOVERNANCE_AUDIT_REPORT.md` | 2026-08-05 | 上游基线资产治理：reference 数据湖迁出 docs/ + 纯净基线去重（已执行） | reference/README + kb/README §3（已物理删除 2026-08-07） |
| `SHADOW_REMOVAL_DEAD_CODE_CLEANUP_WORK_ORDER.md` | 2026-08-07 | Shadow 插件机制拆除 + 死代码/死依赖/死资产全量清除（LIDE-WO-2026-019）：删除 third_party/shadow 与 6 个 loader/plugin/sdk 残留（~503MB）、lide-plugins 资产 12.3MB、assets/samples 139.5KB、patches 空目录；移除 material3-window-size-class 死依赖 + 18 条死字符串；D-19 反转登记 D-18；全部验收门禁通过（构建/单测/APK 无插件资产） | kb/DECISIONS D-19 + kb/README §2 |
| `EDITOR_ALL_LANGUAGES_SUPPORT_WORK_ORDER.md` | 2026-08-06 | 全语言（39 种 TextMate 文法）支持：资产 81 项迁入 `:editor` / LanguageCatalog+Detector 替换 LuaLanguage / release（R8）grammar 加载失败修复（proguard keep）/ 模拟器+真机多色高亮验证 | kb/EDITOR + AGENTS.md 编辑器约定（遗留：全语言补全交互验收并入 `EDITOR_FEATURE_COMPLETION_WORK_ORDER` §8.3 综合复测） |
| `PLUGIN_FEATURE_REMOVAL_WORK_ORDER.md` | 2026-08-07 | 插件功能移除 + 编辑器菜单治理（LIDE-WO-2026-020）：删 PluginScreen/PluginManager/示例插件资产/插件路由与字符串，`.alp` 一律按工程导入（删 AlpKind.PLUGIN）；标签卡删 ⋮ 按钮（长按松手弹关闭菜单）；编辑器右上角更多菜单多级化（分类→子菜单）；assembleArm64Debug 绿 + 单测全绿 + 主源 ktlint 通过 + R1 门禁零命中 | kb/DECISIONS D-20 + AGENTS.md（编辑菜单/标签手势约定） |
| `EDITOR_MULTI_LEVEL_MENU_WORK_ORDER.md` | 2026-08-07 | 编辑器右上角「更多」多级菜单 cascade 化（LIDE-WO-2026-022）：M3 官方 DropdownMenu 无嵌套 API，手写 MoreSubmenuItem（Box 嵌套 DropdownMenu）致二级菜单位置错位且违「禁手搓」；引入成熟第三方 `me.saket.cascade:cascade-compose:2.3.0`（POM 实证 Compose 依赖为 runtime scope，BOM 2025.06.00 → material3 1.4.0 胜出），根菜单 CascadeDropdownMenu + 一级分类 children 级联（自动右箭头/子菜单返回头/屏幕边缘与键盘避让）；删 MoreSubmenuItem；编译/依赖解析/单测/ktlint/assembleArm64Debug 全绿 + R1 门禁零命中 | kb/DECISIONS D-21 |
| `SIGNING_GENERATE_CUSTOM_FIELDS_WORK_ORDER.md` | 2026-08-08 | 自生成签名证书内容自定义：`CertSubject`（C/ST/L/O/OU/CN/E/street/postalCode 全字段可选 + IA5String email）与 `validYears`（1~1000，默认 100）入 `SigningCrypto.generate`（默认参数向后兼容）；`GenerateDialog` 滚动表单 + 三校验（国家代码两位字母/邮箱含@/有效期整数）；build.* 57 单测全绿 + assembleArm64Debug 绿 | AGENTS.md 签名约定（RSA-2048 零依赖 DER 子集扩充；`SigningCrypto.generate` 签名） |
| `SOURCE_ORGANIZATION_5REQ_AUDIT_REPORT.md` | 2026-08-08 | 源码组织五需求审计 + 整改（Phase 0~4 全部完成 2026-08-09）：引擎页 Kotlin 化归位 `app/.../engine/`、editor 并单进 app、UPSTREAM.md 台账 + verifyVendorIsolation 门禁（CI verify-vendor）、verifyRuntimeSurface 包级断言、checkDexDuplication 四类跨 dex 断言（scanDexClassNames **type_ids 二次间接修复**）、引擎 dex 纯净化（IDE 依赖三形态 + runtime themes.xml + constraints，runtime dex 5945→775 类，模拟器 NoSuchMethodError 根因）、6 变体打包回归 + 模拟器 4 工程矩阵 + 运行按钮全链路 | AGENTS.md（scanDex 解析语义 / IDE 依赖三形态约定 / verify 门禁命令）+ kb/README §2 |

### 3.2 过时 / 被取代文档（11 篇，1 篇已物理删除）

| 文件 | 原位置 | 归档原因（一句话） |
|------|--------|-------------------|
| `WORK_ORDER.md` | `planned/` | 引擎迁移期历史总任务书，已全部完成并被本目录 §3.1 逐项取代 |
| `IDE_RUNTIME_SEPARATION_FEASIBILITY_REPORT.md` | `planned/` | 编辑器/宿主运行时彻底拆分（独立进程/服务）可行方案未采纳，与现行 R1~R5 同进程边界架构冲突 |
| `ui-refactor.md` | `in-progress/` | Lua UI 时代（loadlayout）遗留草稿，对应代码已删除，无实现价值 |
| `audit-remediation.md` | `completed/` | Compose 改造前的审计整改清单，已过期且被 `FULL_AUDIT_REPORT_2026-08-01` 取代 |
| `java2kotlin-migration.md` | `completed/` | Java→Kotlin 迁移记录，后因重新 vendor 上游 Java 源码（`:androlua`）而反转失效 |
| `architecture.md` | `kb/project/` | 自标过期（原项目架构描述）；现行架构见 `docs/kb/ARCHITECTURE.md` |
| `lua-modules.md` | `kb/project/` | 自标过期（原 Lua 模块清单）；现行运行时模块见 `third_party/` 与 `reference/` |
| `code-map.md` | `kb/project/` | 自标过期（原代码地图）；现行模块拓扑见 `docs/kb/ARCHITECTURE.md` §3 |
| `PLUGIN_SEPARATION_ARCHITECTURE_REPORT.md` | `planned/` | 双 APK 分家审计（LIDE-AUDIT-2026-014，2026-08-06）**已被 `PLUGIN_PLATFORM_REPORT.md` 演进取代**：方案从「双 APK + 运行端口」升级为「开放 Plugin SDK 插件平台（小政府）」，其隔离缺口证据（11 耦合点）与验收锚点在platform 报告中继续交叉引用（已物理删除 2026-08-07） |
| `PLUGIN_PLATFORM_REPORT.md` | `planned/` | 插件平台「设计定稿」（LIDE-DESIGN-2026-001）——**2026-08-07 方案整体取消**（D-19 反转 D-18）：Shadow 插件机制拆除，androlua 维持上游基线 library 集成；连同前篇共三篇均为插件化历史，仅作追溯 |
| `SETTINGS_ORGANIZATION_WORK_ORDER.md` | `planned/` | 设置界面重组（AndroLua 专属设置页 + 四分区）**2026-08-07 被用户新要求取代**：Lua 手册/帮助整体移除、权限管理移出 AndroLua 页（入主设置「通用」分区），AndroLua 页重建为纯工具页（API 浏览器/插件/示例），入口形态最终为「编辑器 / AndroLua」两个独立入口（见 `planned/SETTINGS_PERMISSIONS_REWORK_WORK_ORDER.md`） |

### 3.3 非文档资产归档（补登）

| 条目 | 原位置 | 归档原因（一句话） |
|------|--------|-------------------|
| `material3_searchbar_patch/` | `third_party/patches/` | 无任何构建引用的 AOSP 副本（~127KB 死代码），搜索组件缺陷历史围栏；架构审计 C-2（2026-08-06）迁移归档（已物理删除 2026-08-07） |

补充说明：

- 本目录全部文档与 `docs/kb/` 的内容重合度已逐一核对，**无独有事实价值**（结论均已蒸馏）。
- `kb/project/` 保留 `project-overview.md` 与 `changelog.md`（仍在维护）。
- 2026-08-05 建立本目录（合并 `completed/` 30 篇 + `archive/` 8 篇 + 索引），同时完成 10 份损坏文档的恢复（见 `docs/kb/README.md` 底部注记）。
- 治理审计与重组依据：`docs/discard/DOCS_GOVERNANCE_AUDIT_REPORT_2026-08-05.md`（✅ 已完成，2026-08-05）。
- 上游基线资产治理（reference 数据湖迁出 docs/ + 纯净基线去重）：`docs/discard/REFERENCE_ASSETS_GOVERNANCE_AUDIT_REPORT.md`（✅ 已完成，2026-08-05）。
- **2026-08-07 断舍离（物理删除批次）**：15 篇无任何外部引用的报告（结论均已蒸馏进 `docs/kb/`，仅本登记表引用）与 `material3_searchbar_patch/`（129KB 死代码，归档原因见 §3.3）执行 `git rm` 物理删除；git 历史保留原文可恢复（`git show <commit>^:docs/discard/<file>`）。本登记表条目全部保留并标注「（已物理删除 2026-08-07）」，一句话摘要 + 结论去向仍可追溯。同日 `EDITOR_ALL_LANGUAGES_SUPPORT_WORK_ORDER.md` 已完成归档自 `planned/` 移入（§3.1）；`SETTINGS_ORGANIZATION_WORK_ORDER.md` 被设置/权限治理新要求取代，移入 §3.2（见 `planned/SETTINGS_PERMISSIONS_REWORK_WORK_ORDER.md`）。
