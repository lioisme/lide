# lide 关键决策记录（决策时间线）

> 决策类结论汇总，含反转决策。阅读原则：**文档结论冲突时，以本表最后状态 + `PROGRESS.md` 为准**。

| # | 日期 | 决策 | 理由 | 影响/后续 |
|---|------|------|------|-----------|
| D-01 | 2026-07 上旬 | **采用 AndroLua Pro C 引擎（Lua 5.3.3）替代 LuaJ** | LuaJ 兼容层 6 个 P0 缺陷、性能 3~8× 差距、社区停更 | 启动迁移（WP-001~006）；源码审计确认 pro 是唯一可选（拒 AndroLua / AndroLuaX） |
| D-02 | 2026-07 中 | **预编译 .so + Java 源码进主 DEX，移除 engine.dex 独立 DEX 方案** | 消除双份运行时类；Java 运行时直接编译进宿主主 DEX（反 INTEGRATION_PLAN Phase 6 的「保留备用」建议） | `EngineActivity` 直接继承 `LuaActivity`，无需 DexClassLoader |
| D-03 | 2026-07 中 | **三个测试工程（demo/kleinbottle/testjson）资产化部署**（`assets/samples/` 首启提取） | 验收基准固定、pro 工程即验收基准 | 后期演进为 4 工程（+DemoProject）；**2026-08-07 起 `samples/` 已删除**（LIDE-WO-2026-019），改由 `:app:generateExamples` 从 `scripts/examples-src/` 构建期打包为 `assets/examples/*.alp` |
| D-04 | 2026-07 中 | **LuaJ 兼容层 ~2300 行整体删除** | 完全依赖原生 AndroLua 引擎 | WP-005 收口；`grep org.luaj.vm2` 零命中 |
| D-05 | 2026-07 底 | **运行链路简化：删除 RunScreen 宿主中间页，恢复「运行 → 引擎全屏直启」** | 真机发现「两个界面」：宿主结果页冗余（引擎自带控制台已展示输出），并会误导（run_no_result 文案） | 删除 `RunScreen.kt`/`EngineResult.kt`；物化失败仅 Toast |
| D-06 | 2026-07-31 | **「保留全局仓库 + 物化同步」为终态**（WP-007 P2-12） | 集中安装/版本/卸载 + 物化桥接 | **随后被 D-07 整体反转** |
| D-07 | 2026-07-31 | **删除全局依赖管理层（common/ 仓库），回归 pro「工程 = 自治单元」**（WP-009 方案 A） | S1 必修缺陷：全局仓库为空时 demo 样例直接拒绝运行；S2~S7 共 7 缺陷；运行时对 common/ 零引用 | 净删 ~1700 行 + 49×2 字符串 + 4 路由；反转 D-06 及 WP-008 P1-4~9；依赖声明单一事实源 = init.lua |
| D-08 | 2026-07-31 | **单进程口径**（AGENTS.md R2 修订为「EngineBridge → EngineActivity，同进程运行」） | 消除「独立进程」文档偏差 | R2 文本更新 |
| D-09 | 2026-08-01 | **dex 必须内存加载，禁止回退文件路径** | Android 8+/14+ W^X 拒绝 app 可写目录加载 dex（`/sdcard` FUSE 与 `/data/data` 均被拒）；真机 V-01/V-02 实证 | `LuaDexLoader` API≥26 走 `InMemoryDexClassLoader`；成为**有意的**与 pro 的差异（工作单 §10.1 修订） |
| D-10 | 2026-07-31 | **独立 APK 打包 v1.0 暂不支持**（WHITEPAPER 阶段四决策） | ① pro 打包器依赖仓库外的 `mao/sign` 签名 jar；② v1-only 签名 targetSdk≥30 不可安装；③ 宿主模板体积/方法数不可控；④ 与编辑/运行主链路正交 | **随后已立项开工（WP-014）**：`LuaCompiler`/`AXmlManifest`/`ApkBuilder`/`ApkSigner`/`V2Signer`/`BuildApkScreen` 代码已落地（commit 953592b 等）；2026-08-05 已补登记 PROGRESS WP-014；白皮书阶段四/五待更新 |
| D-11 | 2026-08-01 | **lluv / tcc 不预编译**，缺失模块由工程 `libs/` 补偿 | lluv 与 luv 功能重叠；tcc 无 arm64/x86_64 NDK 目标；lfs/lsqlite3 已补齐 | 差异声明入 WHITEPAPER §7.1 |
| D-12 | 2026-08-01 | **布局助手：资产先行、UI 单独立项** | 5 人日高工作量（P1 最大项） | `javaapi/*.aly` 模板已入 assets/layouts；预览/拖拽/转换器（WP-009 9-A~9-C）待立项 |
| D-13 | 2026-08-01 | **宿主 UI 一律官方/成熟组件，禁止手搓自研** | Squircle-CE 对齐约束（LIDE-UI-001） | 抽屉用官方 `ModalDrawerSheet(drawerState)`；唯一新增依赖 `reorderable:3.0.0`（Tab 拖拽） |
| D-14 | 2026-08-01 | **user_permission 拒绝后继续运行**（仅 Toast 提示） | 与 pro 行为一致：权限由脚本运行时自行处理 | WP-003 工作单；`PermissionResolver` 纯函数 + 单测 7 例 |
| D-15 | 2026-08-01 | **Xposed 模块可行性：技术可行但有前提** | 四支柱为真：libluajava 静态链接可注入、Class.forName+setAccessible 匹配、三 ABI 就绪、pro 反 Xposed 代码已清除；约束：v1-only 不可装、native 需自提取、de.robv API compileOnly | 路线图 M1~M5 未启动，前置 = 打包器落地（D-10） |
| D-16 | 2026-08-01 | **示例资产化：`assets/samples` 首启解包 → `assets/examples/*.alp` + 示例导入界面**（WP-003 扩展） | 需求「不内置工程」+ 开发者需内置示例参考；导入复用 `AlpManager.import`（与用户导入同一路径，支持覆盖重导入） | `ExamplesScreen.kt` / `Routes.EXAMPLES` / `make-examples.ps1` + `examples-src/` 6 示例；**反转 D-03**（原「资产化部署 + 首启提取」） |
| D-17 | 2026-08-04 | **导航一律走单一漏斗门控：仅 Lifecycle==RESUMED 时执行 navigate/popBackStack**（NavigationGate） | 冻结根因 = NavHost$33$1 转场收尾回调在「转场中 pop」时丢失（entry 销毁内容残留 → 点击永久失效）；官方规范即「仅 RESUMED 时导航」；业务零改动 | `NavigationGate.kt` 新增 5 个扩展函数；`LideNavHost.kt` 13 处调用全量替换 + 11 处 BackHandler；不升级 navigation 2.9.x（方案 B 留待评估）；详见 `NAVIGATION_FREEZE_FIX_REPORT.md` |
| D-18 | 2026-08-06 | **LIDE 插件平台（小政府）立项**：LIDE 收敛为纯宿主（仅 契约/准入/生命周期/导航/安全/中心 UI），androlua 独立为插件 APK（无 LAUNCHER、实现 Plugin 契约），对外开放固定 Plugin SDK——任何技术上满足注册声明的 APK 即可接入（类 Xposed 技术识别模型，默认无厂商白名单 + 可选受信证书开关）；业务数据一律类型化 AIDL/DTO，不用 scheme/裸 Intent；插件 UI 自带，LIDE 仅页面导航 | 用户立项要求（开放 API / 小政府 / 宿主完全不感知插件 / 进程隔离崩溃互不影响）；取 GMS 式绑定契约 + 跨进程隔离，拒 Xposed 同进程注入 | 定稿见 `planned/PLUGIN_PLATFORM_REPORT.md`（LIDE-DESIGN-2026-001，Phase 0~5）；**反转 D-08**（引擎由主进程迁出插件进程）；**部分反转 D-02**（Java 运行时/`.so` 由主 DEX 迁出插件 APK）；**D-10/D-16** 打包器与示例逻辑随之倾向插件侧；前审计 LIDE-AUDIT-2026-014 已归档 discard（11 耦合点证据继续作为迁移清单）。**2026-08-07 被 D-19 整体反转（方案取消）** |
| D-19 | 2026-08-07 | **取消插件平台（Shadow 插件机制拆除），androlua 仅作上游基线集成**：`:androlua` 维持 library 模块被 `:app` `implementation` 引用，引擎与宿主同进程（D-02/D-08 不反转）；删除全部插件平台残留（构建产物 / lide-plugins 资产 / 设计文档归档 discard / 文档引用收口）；`PluginScreen`+`PluginManager`（pro 兼容目录式 Lua 插件）不受影响 | 插件平台与「纯编辑器模块 + 宿主运行时」双层架构（R1~R5）目标冲突；loader/plugin/runtime 分层方案未进入任何产品验收；保留其引入前已被 ANDROLUA_PARITY_FULL_AUDIT 验证的 in-DEX 集成形态 | **反转 D-18**；`docs/planned/PLUGIN_PLATFORM_REPORT.md` 归档 `docs/discard/`（LIDE-WO-2026-019，2026-08-07）。**2026-08-07 保留条款被 D-20 部分反转** |
| D-20 | 2026-08-07 | **移除 pro 兼容目录式 Lua 插件功能（PluginScreen/PluginManager 整体删除）**：`.alp` 一律按工程导入 `project/`（删 `AlpKind.PLUGIN`/`resolveKind`），插件路由/示例插件资产/插件字符串全清；标签卡 ⋮ 菜单按钮删除（长按松手弹关闭菜单，功能重复）；编辑器右上角「更多」菜单多级化（分类 → 子菜单） | 用户要求移除 androlua 插件功能及文件/代码；同时治理两处冗余/平铺 UI | **部分反转 D-19 的「插件不受影响」条款**；执行见 `discard/PLUGIN_FEATURE_REMOVAL_WORK_ORDER.md`（LIDE-WO-2026-020，2026-08-07，已归档） |
| D-21 | 2026-08-07 | **编辑器右上角「更多」多级菜单改用成熟第三方 cascade（`me.saket.cascade:cascade-compose:2.3.0`）**：M3 官方 `DropdownMenu` 无嵌套子菜单 API；D-20 初版手写 `MoreSubmenuItem`（Box 嵌套 DropdownMenu）致二级菜单位置错位且违反 D-13「禁手搓」；cascade 自带级联定位（屏幕边缘/键盘避让）、条目右箭头、子菜单返回头 | 官方无嵌套菜单 + 禁手搓 → 唯一成熟第三方方案；POM 实证其 Compose 依赖为 runtime scope（ui 1.5.2 / material3 1.1.2），BOM 2025.06.00 → material3 1.4.0 胜出（同 reorderable 论证） | 根菜单 `CascadeDropdownMenu` + 一级分类 `children` 级联；删 `MoreSubmenuItem`；编译/依赖解析/单测/ktlint/assembleArm64Debug 全绿 + R1 门禁零命中；执行见 `discard/EDITOR_MULTI_LEVEL_MENU_WORK_ORDER.md`（LIDE-WO-2026-022，2026-08-07，已归档） |

---

## 反转关系图（重点）

```
D-06 保留全局仓库 ──(WP-009)──► D-07 删除全局仓库（反转）
D-10 打包暂缓 ──(打包器开工)──► 部分反转：代码已落地，待登记与验收
D-03 资产化部署+首启提取 ──(WP-003 扩展)──► D-16 示例资产化 .alp + 导入界面（反转）
INTEGRATION_PLAN Phase 6 保留 engine.dex ──(WP-002)──► D-02 移除独立 DEX
D-02 Java 运行时进主 DEX / D-08 同进程单线程 ──(D-18 插件化立项)──► 引擎 Java 运行时与 .so 迁出主 DEX、引擎运行于插件进程（D-08 反转 / D-02 部分反转）──(2026-08-07 D-19 方案取消)──► 恢复：引擎 in-DEX 同进程（D-02/D-08 不反转）
D-18 插件平台立项 ──(LIDE-WO-2026-019)──► D-19 取消插件平台（Shadow 拆除）（反转）
```

## 使用注意

- 白皮书（WHITEPAPER.md）阶段四仍写「规划中；v1.0 暂不支持」，与 D-10 的开工事实冲突，**待更新**。
- `ANDROLUA_FEATURE_PARITY_AUDIT.md` 的 P2-12 记录为「保留全局仓库」终态，其有效结论已被 D-07 取代，阅读时以 `ANDROLUA_PARITY_FULL_AUDIT_REPORT.md` 的架构反转注记为准。
