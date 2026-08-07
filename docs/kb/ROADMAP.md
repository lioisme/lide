# lide 路线图与待办台账

> 进行中 / 计划中工作的单一索引。原文档：`PARITY_COMPLETION_WORK_ORDER.md`、`EDITOR_PERFORMANCE_AUDIT_V2_REPORT.md`、
> `EDITOR_UI_ALIGNMENT_WORK_ORDER.md`、`XPOSED_MODULE_FEASIBILITY_REPORT.md`、`GAME_ENGINE_PERFORMANCE_AUDIT_REPORT.md`、
> 各审计报告行动项。完成即登记 `PROGRESS.md` 并从本表勾销。

---

## 1. 独立 APK 打包（WP-014，进行中 · 代码已落地）

> 来源：`PARITY_COMPLETION_WORK_ORDER.md` §WP-014 + git log（953592b/fecaa61/9be67b5）。

| 期 | 内容 | 状态 |
|----|------|------|
| 14-A | luac 字节码化（`LuaCompiler`：loadbuffer + dump，去 BOM，错误带行号） | ✅ 已落地 |
| 14-B | AXML 二进制 manifest 解析/重写（`AXmlManifest`：字符串池/属性/元素 chunk，包名/label/版本/权限白名单，纯字节级） | ✅ 已落地 |
| 14-C | checklib 依赖分析 + zip 重打包 + 签名（`ApkBuilder`/`ZipUtils`/`ApkSigner`/`V2Signer`，v1 + v2 Signing Block） | 代码已落地 |
| — | `BuildApkScreen` + 权限目录（`PermissionCatalog`）+ 打包单测（`BuildPackagingTest`） | ✅ 已落地 |

**剩余**：登记 `PROGRESS.md`；真机验收（打包 → 安装 → 运行，ACC-1~4）；白皮书阶段四/五状态更新；`assets/keys`（AOSP testkey）入仓。
**前置注意**：v1-only 签名在现代 Android（targetSdk≥30）不可安装，v2 必须随包。

---

## 2. 宿主 IDE 功能补全（PARITY-COMPLETION 剩余 WP）

> **13/19 已完成**（WP-001/002/003/004/005/008/013/014/015/016/017/018/019，其中 WP-014 代码落地未真机验收）。
> 2026-08-02：WP-008（Java API 浏览器）✅ 已落地，详见 `docs/discard/API_BROWSER_AUDIT_REPORT.md` §8（资产/数据层/UI/导航全量交付，x64 debug 构建通过）。
> 2026-08-05：补登 WP-005/013/015/016/018（代码实况核对，见 `PARITY_COMPLETION_WORK_ORDER.md` 各小节）。

| WP | 标题 | 优先级 | 建议批次 |
|----|------|--------|---------|
| WP-006 | 查错 / 格式化 / 函数导航 / gotoLine（查错 = EngineActivity check 模式，`:app`；格式化/导航 = `:editor`） | P1 | 批 3 |
| WP-007 | 导入分析（fiximport）+ 错误建议 import（类表数据源 = 平移 pro `android.lua` 4510 行 → **共享 WP-008 已落地的 `assets/javaapi/android.lua` 4196 条**） | P1 | 批 4 |
| WP-009 | 布局助手（9-A 预览 / 9-B 属性编辑 / 9-C 拖拽 + XML 转换器，分期独立立项） | P1 | 批 5 |
| WP-010 | 最近文件 / 继续上次（AppStateStore：上次工程 + 标签 + 光标，上限 50） | P1 | 批 4 |
| WP-011 | 日志查看器（logcat 过滤；API 29+ READ_LOGS 受限 → 默认本进程日志） | P1 | 批 5 |
| WP-012 | 补全增强（生命周期 15 方法 + 类短名 + activity./service. 成员静态表） | P1 | 批 4 |

---

## 3. 编辑器性能与 UI 对齐

### 3.1 性能（V2 阶段 A/B/C）

| 阶段 | 项 | 状态 |
|------|----|------|
| A | 消灭每键整屏重组（`editVersion` → StateFlow / Squircle `needsUpdate` 模式） | ✅ 已落地（editTick StateFlow，EditorSession.kt） |
| A | 搜索链路结果下沉 SearchBar（消除 ×2 重组） | ✅ 已落地（SearchBar 订阅 PublishSearchResultEvent） |
| A | 打开文件 IO 线程构造 `Content` + `setText(content: Content)` 注入 + scheme 单例 | ✅ 已落地（LuaEditor Content 注入） |
| A | 转场淡入（100ms） | ⛔ 被取代（2026-08-04 决策：全部页面 slide 150ms，禁用 fade） |
| A | release 开 R8 + baseline profile | 未完成（入 backlog） |
| A | 诊断改增量扫描 | ✅ 已落地（LuaDiagnostics Incremental） |
| B | EditorTabs / ProjectFileTree / EditorSession / Content 缓存 | ✅ 已落地 |
| C | 诊断 tooltip、原生 SnippetController、增量保存 | 未开始（入 backlog） |

> 2026-08-05：阶段 A/B 按代码实况核销（V2 报告已归档 `docs/discard/`）；剩余 R8/baseline profile 与阶段 C 转入 `planned/backlog.md`。

验收基准（AC）：gfxinfo jank=0、AsyncAnalyzer 线程唯一、styles 非空、77KB 打开耗时 ≤ Squircle、搜索流畅、R1 零命中。

### 3.2 UI 对齐（LIDE-UI-001，内部 WP）

| WP | 内容 | 状态 |
|----|------|------|
| 001/002/008 | 抽屉返回键 / 关闭竞态 / 汉堡语义 | ✅ 已由抽屉审计落地（官方 API）→ **2026-08-05 抽屉已整体删除**，相关项随抽屉消亡 |
| 003 | Tab 拖拽排序（reorderable 3.0.0） | ✅ 已落地（EditorTabs） |
| 004 | Tab 样式对齐（扁平 + 3dp 底部指示条） | ✅ 已落地（EditorTabs） |
| 005 | 点击当前 Tab 弹关闭菜单（DropdownMenu） | ✅ 已落地（EditorTabs） |
| 006 | 新 Tab 自动滚动 | ✅ 已落地（EditorTabs） |
| 007 | 抽屉手势对齐 | ⛔ 被取代（抽屉已删除，2026-08-05） |
| 009 | 文件树横向滚动（≥248dp） | 待办 |
| 010 | 版本目录（P2 可选） | 待办 |

> 2026-08-05 复核：WP-003~006 代码证据 = `EditorTabs.kt`（`sh.calvin.reorderable.*` + `DropdownMenu`）；抽屉已移除（`ProjectFileTree.kt` 不再存在），对应项改按 Tab 体系执行；文件树能力现由 `ProjectFileBrowserDialog`（EditorComponents.kt:95）承载。

---

## 4. Xposed 模块（研究完成，实施未排期）

> 来源：`XPOSED_MODULE_FEASIBILITY_REPORT.md`。前置 = §1 打包器收尾。

| 里程碑 | 内容 | 工作量 |
|--------|------|--------|
| M1 | 基础打包器（依赖 §1）+ 二进制 manifest + v1+v2 签名 | 高 |
| M2 | PC 模板 + 骨架 dex（com.luajava 裁剪 + Entry + XposedLuaHook + HeadlessLuaContext） | 中 |
| M3 | 打包器「Xposed 模块模式」（meta-data + xposed_init + ABI lib/ + scope UI） | 中 |
| M4 | `xposed.lua` 运行库 + 线程模型/延迟初始化加固 | 中 |
| M5 | 测试矩阵（LSPosed 1.9.x / Vector 15–17 / LSPatch / 三 ABI / Android 14+ JNI） | 低-中 |

关键约束：v1-only 签名不可用；native 库必须模块自提取加载；de.robv API 必须 compileOnly；反检测（/proc/self/maps 可见 libluajava.so）不适用于安全对抗目标。

---

## 5. 游戏 Demo 性能优化（未排期，按性价比排序）

> 来源：`GAME_ENGINE_PERFORMANCE_AUDIT_REPORT.md`。先做 30 秒 systrace/simpleperf 校准再动工。

1. 石头：传感器 `SENSOR_DELAY_GAME/UI` + dt 累积 + Choreographer/Ticker vsync 驱动（2~4×）
2. 石头：Lua 热路径缓存字段 getter、6 视图批处理为 1 次 Java 调用
3. 石头：debugmode=false + 关 status 打印
4. 看板娘：`RENDERMODE_WHEN_DIRTY` + 模型更新节流 + 生命周期挂起 GL 循环
5. 通用：引擎 .so 化（box2d C / Cubism 原生 SDK，5~15×）
6. 长期：in-memory dex 预编译缓存（JIT profile 持久化）

---

## 6. 待设备验证清单（回归兜底）

| 项 | 来源 | 状态 |
|----|------|------|
| `.so` 库 import、SAF 文件夹安装、删除工程 | WP-008 剩余 | 待真机 |
| demo/testjson 全新安装可运行、`.so` 库 `libs/` 加载、`{luadir, luapath}` | WP-009 剩余 | 待真机 |
| ACC-02~05/08：demo/testjson/kleinbottle 运行、连续 10 次无 OOM、APK ≤30MB | PROGRESS 整体验收 | 待设备/构建后 |
| 打包器：产物安装运行、权限白名单过滤生效 | §1 | 待真机 |
| 无障碍/截图 API（LuaAccessibilityService）、.lua 拉起 | WP-007 剩余 | 待真机 |
| 手册渲染（~~luadoc WebView~~ / ~~帮助页~~） | ~~WP-007/P2-11/WP-018~~ | **已取消（2026-08-07）**：Lua 手册与帮助界面按用户要求整体移除（见 `planned/SETTINGS_PERMISSIONS_REWORK_WORK_ORDER.md` 要求 ③④） |

---

## 7. 文档维护事项（低优先，随手处理）

| 项 | 说明 |
|----|------|
| `WHITEPAPER.md` | 阶段四/五状态随打包器进展更新（§7.1 已随恢复文档存在） |
| `PROGRESS.md` | ✅ 已补登记 WP-014 打包器（2026-08-05）；工作区改动待提交后复核 |
| `LIBS_SUPPORT_WORK_ORDER.md` | ✅ 已回填 Status 字段并移入 `docs/discard/`（2026-08-05） |
| `NATIVE_LIB_AUDIT.md` | ✅ 已回填「阶段 B 由 LIBS-003/006 收尾」并移入 `docs/discard/`（2026-08-05） |
| `EMULATOR_RUNTIME_AUDIT_REPORT.md` | ✅ 已跟踪并移入 `docs/discard/`（2026-08-05 复核删除旧注记） |
| `WORK_ORDER.md` | ✅ 已丢弃至 `docs/discard/`（2026-08-05） |
| `docs/discard/README.md` | 丢弃区索引（已完成/过时文档清单 + 登记处） |
