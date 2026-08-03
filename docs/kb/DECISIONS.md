# lide 关键决策记录（决策时间线）

> 决策类结论汇总，含反转决策。阅读原则：**文档结论冲突时，以本表最后状态 + `PROGRESS.md` 为准**。

| # | 日期 | 决策 | 理由 | 影响/后续 |
|---|------|------|------|-----------|
| D-01 | 2026-07 上旬 | **采用 AndroLua Pro C 引擎（Lua 5.3.3）替代 LuaJ** | LuaJ 兼容层 6 个 P0 缺陷、性能 3~8× 差距、社区停更 | 启动迁移（WP-001~006）；源码审计确认 pro 是唯一可选（拒 AndroLua / AndroLuaX） |
| D-02 | 2026-07 中 | **预编译 .so + Java 源码进主 DEX，移除 engine.dex 独立 DEX 方案** | 消除双份运行时类；Java 运行时直接编译进宿主主 DEX（反 INTEGRATION_PLAN Phase 6 的「保留备用」建议） | `EngineActivity` 直接继承 `LuaActivity`，无需 DexClassLoader |
| D-03 | 2026-07 中 | **三个测试工程（demo/kleinbottle/testjson）资产化部署**（`assets/samples/` 首启提取） | 验收基准固定、pro 工程即验收基准 | 后期演进为 4 工程（+DemoProject）|
| D-04 | 2026-07 中 | **LuaJ 兼容层 ~2300 行整体删除** | 完全依赖原生 AndroLua 引擎 | WP-005 收口；`grep org.luaj.vm2` 零命中 |
| D-05 | 2026-07 底 | **运行链路简化：删除 RunScreen 宿主中间页，恢复「运行 → 引擎全屏直启」** | 真机发现「两个界面」：宿主结果页冗余（引擎自带控制台已展示输出），并会误导（run_no_result 文案） | 删除 `RunScreen.kt`/`EngineResult.kt`；物化失败仅 Toast |
| D-06 | 2026-07-31 | **「保留全局仓库 + 物化同步」为终态**（WP-007 P2-12） | 集中安装/版本/卸载 + 物化桥接 | **随后被 D-07 整体反转** |
| D-07 | 2026-07-31 | **删除全局依赖管理层（common/ 仓库），回归 pro「工程 = 自治单元」**（WP-009 方案 A） | S1 必修缺陷：全局仓库为空时 demo 样例直接拒绝运行；S2~S7 共 7 缺陷；运行时对 common/ 零引用 | 净删 ~1700 行 + 49×2 字符串 + 4 路由；反转 D-06 及 WP-008 P1-4~9；依赖声明单一事实源 = init.lua |
| D-08 | 2026-07-31 | **单进程口径**（AGENTS.md R2 修订为「EngineBridge → EngineActivity，同进程运行」） | 消除「独立进程」文档偏差 | R2 文本更新 |
| D-09 | 2026-08-01 | **dex 必须内存加载，禁止回退文件路径** | Android 8+/14+ W^X 拒绝 app 可写目录加载 dex（`/sdcard` FUSE 与 `/data/data` 均被拒）；真机 V-01/V-02 实证 | `LuaDexLoader` API≥26 走 `InMemoryDexClassLoader`；成为**有意的**与 pro 的差异（工作单 §10.1 修订） |
| D-10 | 2026-07-31 | **独立 APK 打包 v1.0 暂不支持**（WHITEPAPER 阶段四决策） | ① pro 打包器依赖仓库外的 `mao/sign` 签名 jar；② v1-only 签名 targetSdk≥30 不可安装；③ 宿主模板体积/方法数不可控；④ 与编辑/运行主链路正交 | **随后已立项开工**：`LuaCompiler`/`AXmlManifest`/`ApkBuilder`/`ApkSigner`/`V2Signer`/`BuildApkScreen` 代码已落地（commit 953592b 等），未登记 PROGRESS/白皮书 |
| D-11 | 2026-08-01 | **lluv / tcc 不预编译**，缺失模块由工程 `libs/` 补偿 | lluv 与 luv 功能重叠；tcc 无 arm64/x86_64 NDK 目标；lfs/lsqlite3 已补齐 | 差异声明入 WHITEPAPER §7.1 |
| D-12 | 2026-08-01 | **布局助手：资产先行、UI 单独立项** | 5 人日高工作量（P1 最大项） | `javaapi/*.aly` 模板已入 assets/layouts；预览/拖拽/转换器（WP-009 9-A~9-C）待立项 |
| D-13 | 2026-08-01 | **宿主 UI 一律官方/成熟组件，禁止手搓自研** | Squircle-CE 对齐约束（LIDE-UI-001） | 抽屉用官方 `ModalDrawerSheet(drawerState)`；唯一新增依赖 `reorderable:3.0.0`（Tab 拖拽） |
| D-14 | 2026-08-01 | **user_permission 拒绝后继续运行**（仅 Toast 提示） | 与 pro 行为一致：权限由脚本运行时自行处理 | WP-003 工作单；`PermissionResolver` 纯函数 + 单测 7 例 |
| D-15 | 2026-08-01 | **Xposed 模块可行性：技术可行但有前提** | 四支柱为真：libluajava 静态链接可注入、Class.forName+setAccessible 匹配、三 ABI 就绪、pro 反 Xposed 代码已清除；约束：v1-only 不可装、native 需自提取、de.robv API compileOnly | 路线图 M1~M5 未启动，前置 = 打包器落地（D-10） |
| D-16 | 2026-08-01 | **示例资产化：`assets/samples` 首启解包 → `assets/examples/*.alp` + 示例导入界面**（WP-003 扩展） | 需求「不内置工程」+ 开发者需内置示例参考；导入复用 `AlpManager.import`（与用户导入同一路径，支持覆盖重导入） | `ExamplesScreen.kt` / `Routes.EXAMPLES` / `make-examples.ps1` + `examples-src/` 6 示例；**反转 D-03**（原「资产化部署 + 首启提取」） |

---

## 反转关系图（重点）

```
D-06 保留全局仓库 ──(WP-009)──► D-07 删除全局仓库（反转）
D-10 打包暂缓 ──(打包器开工)──► 部分反转：代码已落地，待登记与验收
D-03 资产化部署+首启提取 ──(WP-003 扩展)──► D-16 示例资产化 .alp + 导入界面（反转）
INTEGRATION_PLAN Phase 6 保留 engine.dex ──(WP-002)──► D-02 移除独立 DEX
```

## 使用注意

- 白皮书（WHITEPAPER.md）阶段四仍写「规划中；v1.0 暂不支持」，与 D-10 的开工事实冲突，**待更新**。
- `ANDROLUA_FEATURE_PARITY_AUDIT.md` 的 P2-12 记录为「保留全局仓库」终态，其有效结论已被 D-07 取代，阅读时以 `ANDROLUA_PARITY_FULL_AUDIT_REPORT.md` 的架构反转注记为准。
