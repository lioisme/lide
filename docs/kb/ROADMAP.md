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

> 6/19 已完成（WP-001 编译修复、WP-002 运行诊断闭环、WP-003 user_permission、WP-004 工程属性页、WP-017 解析结构化、WP-019 测试骨架）。
> 2026-08-02：WP-008（Java API 浏览器）✅ 已落地，详见 `docs/planned/API_BROWSER_AUDIT_REPORT.md` §8（资产/数据层/UI/导航全量交付，x64 debug 构建通过）。

| WP | 标题 | 优先级 | 建议批次 |
|----|------|--------|---------|
| WP-005 | 编辑器文件操作 + 工程重命名（文件树新建/重命名/删除，联动 EditorSession） | P1 | 批 3 |
| WP-006 | 查错 / 格式化 / 函数导航 / gotoLine（查错 = EngineActivity check 模式，`:app`；格式化/导航 = `:editor`） | P1 | 批 3 |
| WP-007 | 导入分析（fiximport）+ 错误建议 import（类表数据源 = 平移 pro `android.lua` 4510 行 → **共享 WP-008 已落地的 `assets/javaapi/android.lua` 4196 条**） | P1 | 批 4 |
| WP-008 | Java API 浏览器（类表 + 反射详情，事件推导规则对齐 pro） → **✅ 已落地并归档（2026-08-02，ACC 1/2/4~7 通过、ACC-3 环境受阻，见 §2.1 与 `API_BROWSER_AUDIT_REPORT.md` §6.1/§8）** | P1 | 批 4 |
| WP-009 | 布局助手（9-A 预览 / 9-B 属性编辑 / 9-C 拖拽 + XML 转换器，分期独立立项） | P1 | 批 5 |
| WP-010 | 最近文件 / 继续上次（AppStateStore：上次工程 + 标签 + 光标，上限 50） | P1 | 批 4 |
| WP-011 | 日志查看器（logcat 过滤；API 29+ READ_LOGS 受限 → 默认本进程日志） | P1 | 批 5 |
| WP-012 | 补全增强（生命周期 15 方法 + 类短名 + activity./service. 成员静态表） | P1 | 批 4 |
| WP-013 | 运行入口跟随当前标签（非 main.lua 标签跑当前文件） | P1 | 批 4 |
| WP-014 | 独立 APK 打包 → **见 §1（已开工）** | P2 | 批 7 |
| WP-015 | 导入同名确认（覆盖/改名/取消）+ 插件卸载 | P2 | 批 6 |
| WP-016 | 权限页细化（单项请求 + 特殊权限分组：悬浮窗/写设置/电池优化/安装未知来源） | P2 | 批 6 |
| WP-018 | 字号/Tab 设置 UI + 帮助手册（help.lua → assets/help.txt 纯文本章节） | P2 | 批 6 |

---

## 3. 编辑器性能与 UI 对齐

### 3.1 性能（V2 阶段 A/B/C）

| 阶段 | 项 | 状态 |
|------|----|------|
| A | 消灭每键整屏重组（`editVersion` → StateFlow / Squircle `needsUpdate` 模式） | 未完成 |
| A | 搜索链路结果下沉 SearchBar（消除 ×2 重组） | 未完成 |
| A | 打开文件 IO 线程构造 `Content` + `setText(content: Content)` 注入 + scheme 单例 | 未完成 |
| A | 转场淡入（100ms） | 未完成 |
| A | release 开 R8 + baseline profile | 未完成 |
| A | 诊断改增量扫描 | 未完成 |
| B | EditorTabs / ProjectFileTree / EditorSession / Content 缓存 | ✅ 已落地 |
| C | 诊断 tooltip、原生 SnippetController、增量保存 | 未开始 |

验收基准（AC）：gfxinfo jank=0、AsyncAnalyzer 线程唯一、styles 非空、77KB 打开耗时 ≤ Squircle、搜索流畅、R1 零命中。

### 3.2 UI 对齐（LIDE-UI-001，内部 WP）

| WP | 内容 | 状态 |
|----|------|------|
| 001/002/008 | 抽屉返回键 / 关闭竞态 / 汉堡语义 | ✅ 已由抽屉审计落地（官方 API） |
| 003 | Tab 拖拽排序（reorderable 3.0.0） | 待办 |
| 004 | Tab 样式对齐（扁平 + 3dp 底部指示条） | 待办 |
| 005 | 点击当前 Tab 弹关闭菜单 | 待办 |
| 006 | 新 Tab 自动滚动 | 待办 |
| 007 | 抽屉手势对齐 | 待办 |
| 009 | 文件树横向滚动（≥248dp） | 待办 |
| 010 | 版本目录（P2 可选） | 待办 |

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
| .alp 插件导入、插件运行 `{luadir, luapath}`、.so 库 import、SAF 文件夹安装、删除工程 | WP-008 剩余 | 待真机 |
| demo/testjson 全新安装可运行、`.so` 库 `libs/` 加载、`{luadir, luapath}` | WP-009 剩余 | 待真机 |
| ACC-02~05/08：demo/testjson/kleinbottle 运行、连续 10 次无 OOM、APK ≤30MB | PROGRESS 整体验收 | 待设备/构建后 |
| 打包器：产物安装运行、权限白名单过滤生效 | §1 | 待真机 |
| 无障碍/截图 API（LuaAccessibilityService）、.lua 拉起、手册渲染 | WP-007 剩余 | 待真机 |

---

## 7. 文档维护事项（低优先，随手处理）

| 项 | 说明 |
|----|------|
| `LIBS_SUPPORT_WORK_ORDER.md` | 标题 Status 字段仍为 Draft，内容已完成 → 更新状态字段 |
| `NATIVE_LIB_AUDIT.md` | 自述「阶段 B 待执行」，实际已由 WP-010 LIBS-003/006 收尾 → 回填 |
| `WHITEPAPER.md` | 阶段四/五状态与 §7.1 需随打包器进展更新 |
| `EMULATOR_RUNTIME_AUDIT_REPORT.md` | 未 `git add`（untracked）→ 提交 |
| `PROGRESS.md` | 登记打包器 WP-014 进展与工作区未提交改动 |
| `WORK_ORDER.md` | 历史草案，多数条目已过时 → 建议归档或标注 superseded |
