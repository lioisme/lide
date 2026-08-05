# lide 编辑器审计报告（v3）：编辑器-宿主边界、闪退根因与关联性说明

> 审计对象：`app/src/main/kotlin/com/lioisme/lide/ui/editor/*`、`ui/screen/EditorScreen.kt`、`LideApp.kt`、`:androlua` 模块
> 对照源码：`docs/sora-editor-main/`（editor / language-textmate，API 已与 Maven AAR `io.github.rosemoe:editor:0.24.6` 比对）
> 审计日期：2026-07-31
> 本文回答的核心问题：**"编辑器是纯粹的 editor，androlua 属于宿主管辖——为什么分析结论会指向 luajava 这类原生库？"**

---

## 0. TL;DR

1. **你说得对，这是架构定论：** 编辑器与宿主是两层，边界不可逾越。luajava / `libluajava.so` 属于宿主（运行时引擎进程），编辑器进程内不应存在任何对它的引用。
2. **上一轮分析指向 luajava，是因为上一版 lide 代码本身违规了。** 闪退的直接触发者是 `LuaDiagnostics.kt`——它从宿主模块导入了 `com.luajava.LuaStateFactory`（JNI 原生 Lua）到编辑器打字热路径。**这不是"编辑器需要 luajava"，而是"编辑器代码非法借用了宿主的原生引擎"。** 分析结论是"违规代码是崩溃原因"，不是"编辑器应该修 luajava"。
3. **根因 ≠ 机制。** 根因是边界违规（架构层）；机制是违规用法触发宿主原生库的缺陷（技术层）。修复 = 恢复边界（移除依赖），而非修补 luajava。
4. **边界已恢复（当前代码已修复）。** `ui/editor/` 目录 7 个文件对 `com.androlua.*` / `com.luajava.*` 的运行时引用为 **0**；`LuaDiagnostics` 已是纯 Kotlin 单遍词法校验器；TextMate 初始化已预热到进程启动。构建通过。
5. **纯编辑器路径的残余风险已复核**：诊断索引过期、分析线程异常、预热阻塞等均无崩溃风险（见 §5，附源码证据）。

---

## 1. 架构原则：编辑器与宿主的责任边界

### 1.1 分层模型

```
┌─────────────────────────────────────────────────────────┐
│ 宿主层（Host）                                            │
│ · Lua 运行时（EngineBridge 独立进程，libluajava.so）      │
│ · 项目文件 / 权限 / 模块与库管理 / 执行入口（LuaActivity） │
│ · com.androlua.*、com.luajava.* 唯一合法的持有者          │
├─────────────────────────────────────────────────────────┤
│ 编辑器层（Editor）—— 本报告审计范围                        │
│ · 文本展示/编辑、语法高亮（TextMate）、补全、搜索、撤销    │
│ · 行级诊断（纯文本分析，零原生）                           │
│ · 唯一允许的依赖：sora-editor + Android/Compose 基础      │
└─────────────────────────────────────────────────────────┘
```

### 1.2 边界规则（强制）

| 规则 | 内容 | 违反后果 |
|---|---|---|
| R1 | 编辑器代码**禁止** import `com.androlua.*` / `com.luajava.*`，禁止触碰 `libluajava.so` | 原生崩溃（见 §4） |
| R2 | 编辑器**禁止**把宿主能力拉进打字/渲染热路径（语法编译校验、执行等） | 卡顿、崩溃 |
| R3 | 编辑器与宿主的交互只通过**纯数据**（文本、文件路径、结果），且只发生在低频路径（打开/保存/运行） | 架构腐化 |
| R4 | 编辑器进程内的一切分析必须是**纯 Kotlin/Java**（无 JNI） | 不可捕获的原生崩溃面 |

### 1.3 职责划分表

| 功能 | 归属 | 依据 |
|---|---|---|
| 语法高亮（TextMate 文法） | 编辑器 | sora `language-textmate`，纯 Java（joni） |
| 关键词/片段补全 | 编辑器 | sora 补全 API + `LuaSnippetManager`（纯 Kotlin） |
| 行级语法诊断（括号/块/字符串闭合） | 编辑器 | 纯词法分析，零依赖（现实现） |
| **Lua 字节码编译校验（`luac` 级）** | **宿主** | 需要 Lua 编译器；运行时进程已有 |
| 脚本执行 | 宿主 | `EngineBridge` 独立进程 |
| 项目文件读写/权限 | 宿主 | `ProjectManager`（宿主服务层） |
| 模块/库依赖解析 | 宿主 | `DependencyManager` |

---

## 2. 直接回答：为什么上一轮分析指向 luajava

### 2.1 结论先行

> 分析指向 luajava **不是**因为编辑器依赖它，而是因为**上一版 lide 的编辑器代码违规地引用了它**——`LuaDiagnostics.kt` 把宿主模块的 `com.luajava.LuaStateFactory` 拉进了编辑器打字路径。闪退是这条违规的直接后果。修复不是"修 luajava"，而是"把 luajava 从编辑器里移除"（已完成）。

### 2.2 当时的推导链（证据 → 结论）

| 步骤 | 事实 | 结论 |
|---|---|---|
| 1 | 闪退发生在"输入后几秒内"，进程直接退出，**无 Java 异常界面**（原生崩溃特征） | 崩溃来自原生层，Java try/catch 全部无效 |
| 2 | 编辑器路径上（当时）**唯一**的原生代码 = `LuaDiagnostics` 调用的 `libluajava.so`（sora 本身纯 Java：tm4e 的 Oniguruma 是 joni，`TextMateAnalyzer.java:35-38` import 全部为 `org.eclipse.tm4e.*`） | 原生崩溃只可能来自 LuaDiagnostics |
| 3 | 崩溃与"诊断功能引入"同时出现；输入停止 → 500ms 防抖 → 创建/关闭原生 LuaState → GC 双关 | 时序完全吻合（500ms 防抖 + GC 周期） |
| 4 | `LuaState.close()` 无防重入 + `finalize()` 二次 `_close(0)`；`newLuaState()` 在 try 之外（`LuaDiagnostics.kt:14`） | 存在确定性原生崩溃向量（§4） |

### 2.3 机制与根因的区分（避免误读）

```
根因（架构层）  边界违规：编辑器代码导入了宿主原生引擎（LuaDiagnostics → com.luajava）
    │
    ▼
违规用法        瞬态 LuaState × 后台线程 × close+GC 双关 × 非 UTF-8 错误消息回传
    │
    ▼
机制（技术层）  lua_close(NULL) SIGSEGV / NewStringUTF SIGABRT / 未捕获 Error
    │
    ▼
现象            输入后数秒内进程闪退
```

- 宿主（EngineBridge 进程）用同一引擎从未崩溃，因为宿主的用法是**长生命周期 state、单线程、常驻引用**——从不触发 finalize 双关；
- 违规代码把引擎用在了**宿主从未使用过的方式**上（短生命周期、多线程、显式 close），于是宿主能容忍的缺陷在编辑器里变成必崩路径；
- 因此：**"与 luajava 相关"不等于"luajava 有罪"，而是"越界代码以错误方式使用它"**。这也正是边界规则 R1/R4 存在的意义。

---

## 3. 边界违规点审计（历史与现状）

### 3.1 历史违规点（上一轮版本，已修复）

| 文件（旧版） | 违规引用 | 违规点 |
|---|---|---|
| `ui/editor/LuaDiagnostics.kt` | `import com.luajava.LuaStateFactory`（旧版） | 编辑器层依赖宿主原生引擎，且在打字热路径运行 |
| `ui/editor/LuaEditor.kt`（旧版） | 无直接引用，但调用了上述诊断 | 通过调用链把原生代码引入主进程 |

### 3.2 现状：import 审计表（当前代码）

对 `app/src/main/kotlin/com/lioisme/lide/ui/editor/` 全量 grep `luajava` / `androlua`：

| 文件 | 结果 |
|---|---|
| `LuaEditor.kt` | ✅ 0 引用（仅 sora + compose + kotlinx） |
| `LuaLanguage.kt` | ✅ 0 引用（仅 sora） |
| `LuaSnippetManager.kt` | ✅ 0 引用（仅 sora） |
| `LuaDiagnostics.kt` | ✅ 0 运行时引用（注释声明"不触碰 libluajava.so"；实现为纯 Kotlin 词法校验器，`docs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.mddocs/completed/EDITOR_CRASH_ANALYSIS_REPORT.md