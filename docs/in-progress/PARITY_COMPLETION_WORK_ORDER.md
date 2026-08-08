# lide × AndroLua Pro 功能补全任务文档

| 元数据 | 内容 |
|--------|------|
| 文档版本 | 1.0 |
| 创建日期 | 2026-08-01 |
| 文档代号 | PARITY-COMPLETION |
| 审计依据 | 全量审计报告（FULL_AUDIT_REPORT_2026-08-01：P0/P1/P2 差异清单；结论蒸馏进 docs/kb/） |
| 参考实现 | `reference/androlua/AndroLua_pro-master/`（4.4.2，IDE 全 Lua 实现） |
| 总目标 | **pro 有的管理功能 lide 原则上也要有，但以 Kotlin/Java 原生实现**；引擎运行时层已 100% 对齐，本任务只补宿主 IDE 层 |
| 硬性约束 | AGENTS.md R1/R2（`:editor` 纯文本级、`luajava\|androlua\|loadLibrary` 零命中）、`:app` 同进程运行口径、`user_permission` 等 init.lua 语义、W^X dex 内存加载 |
| 文档状态 | 定稿（WP 执行状态按完成登记字段更新） |
| 完成登记 | **13/19 已完成**（WP-001/002/003/004/005/008/013/014/015/016/017/018/019；2026-08-05 按代码实况补登 WP-005/013/015/016/018，其中 WP-014 代码落地未真机验收）；剩余 WP-006/007/009/010/011/012（未开工） |

---

## 目录

1. [背景与目标](#1-背景与目标)
2. [测试基础设施（本机）](#2-测试基础设施本机)
3. [工作包总览](#3-工作包总览)
4. [阶段一：P0 阻塞级（WP-001 ~ WP-003）](#4-阶段一p0-阻塞级wp-001--wp-003)
5. [阶段二：P1 高优先级（WP-004 ~ WP-013）](#5-阶段二p1-高优先级wp-004--wp-013)
6. [阶段三：P2 可选/工程化（WP-014 ~ WP-019）](#6-阶段三p2-可选工程化wp-014--wp-019)
7. [依赖关系与执行顺序](#7-依赖关系与执行顺序)
8. [验收总则](#8-验收总则)
9. [回滚与应急](#9-回滚与应急)
10. [风险管理](#10-风险管理)
11. [附录 A：ADB / 构建命令速查](#11-附录-aadb--构建命令速查)
12. [附录 B：pro 参考文件映射](#12-附录-bpro-参考文件映射)

---

## 1. 背景与目标

### 1.1 现状（审计结论摘录）

lide 引擎运行时层与 pro **已 100% 对齐且为超集**（`com.luajava`/`com.androlua`/cglib 逐字节同源、17 个原生模块 × 3 ABI、31 个内置 Lua 脚本、探针 878/956 ≈ pro 879/956、/libs 真机矩阵 V-01~V-10 全绿）。

但宿主 IDE 层对齐度约 **6 成**。pro 的 IDE（Lua 实现，`assets/main.lua` 1913 行 + 6 个页面 + `bin.lua` 打包链）具备而 lide 缺失/未闭环的能力，按审计报告分级：

- **P0**：工作区编译修复、`user_permission` 写而不读、运行诊断闭环缺失；
- **P1**：工程属性页、编辑器文件操作、查错/格式化/函数导航、导入分析、Java API 浏览器、布局助手、最近文件、日志查看器、补全增强、运行入口；
- **P2**：独立 APK 打包（v1.0 已决策暂不支持）、导入覆盖确认、权限页细化、解析结构化、字号/Tab UI、测试骨架。

### 1.2 设计原则

1. **Kotlin 原生实现**：pro 的 Lua 页面逻辑平移为 Kotlin/Compose；脚本资产（`android.lua` 类表、`layouthelper` 逻辑、`console.lua` 编译链）作为**参考数据/逻辑**平移，不把 Lua 代码当宿主跑；
2. **边界不变**：补全/诊断/高亮/导航等文本级能力放 `:editor`（R1）；查错（luac 编译）、运行、权限、打包放 `:app`（R2）；
3. **每 WP 完成即真机/模拟器回归**（ADB 见 §2），不只以编译通过为准；
4. **pro 工程即验收基准**：`scripts/examples-src/` 的 4 个工程（demo / DemoProject / kleinbottle / testjson）在 pro 生态可正常运行，作为每次回归的固定基准（构建期由 `:app:generateExamples` 打包为 `assets/examples/*.alp`，2026-08-07 起 samples/ 已随 LIDE-WO-2026-019 删除）。

---

## 2. 测试基础设施（本机）

### 2.1 设备

| 项 | 值 |
|---|---|
| ADB | `D:\Android\platform-tools\adb.exe`（已入 PATH） |
| 设备 | `emulator-5554`（在线，`adb devices` 可见） |
| 架构 / 系统 | x86_64 / Android 14（API 34，`ro.build.version.release=14`） |
| ABI 对应 flavor | **x64**（`assembleX64Debug`）；arm64 flavor 仅真机使用 |
| 历史残留 | 模拟器现存旧包名 `com.lide`（早期安装），与本任务无关，可 `adb uninstall com.lide` 清理 |

### 2.2 回归基准工程

| 工程 | 来源 | 验证能力 |
|---|---|---|
| `demo` | scripts/examples-src（pro 生态移植） | ListView + HTTP + JSON + LuaAdapter |
| `DemoProject` | scripts/examples-src | 页面链（page_calc/page_info）+ libs 说明 |
| `kleinbottle` | scripts/examples-src | OpenGL（libgl.so）+ createProxy |
| `testjson` | scripts/examples-src | 复杂 UI（LuaDrawable/OverScroller）+ dkjson |
| pro IDE 页面 | pro-master `assets/{project,projectinfo,plugin,javaapi,layouthelper}/` | 功能对照基准（作为 WP-004/007/008/009 的目标行为定义） |

### 2.3 验证命令（详见附录 A）

```
构建模拟器包： .\gradlew.bat :app:assembleX64Debug --console=plain
安装：         adb -s emulator-5554 install -r app\build\outputs\apk\x64\debug\app-x64-debug.apk
推工程：       adb -s emulator-5554 push <proj> /storage/emulated/0/AndroLua/project/
拉起运行：     adb -s emulator-5554 shell am start -a android.intent.action.VIEW ^
                 -d "file:///storage/emulated/0/AndroLua/project/demo/main.lua" com.lioisme.lide
日志：         adb -s emulator-5554 logcat -d | findstr /i "lide luajava lua"
截图：         adb -s emulator-5554 exec-out screencap -p > s.png
```

> 注意：Android 11+ 全文件访问权限需在「所有文件访问」设置页授予（LIBS-006 经验）；debug 构建可用 `adb shell pm grant` 授权。

---

## 3. 工作包总览

| 阶段 | WP | 标题 | 对应审计 | 工时 | 风险 |
|---|---|---|---|---|---|
| 一 | **WP-001** | 工作区编译修复（抽屉重构收尾） | P0-1 | 0.5 人日 | 低 |
| 一 | **WP-002** | 运行诊断闭环（结果回传 + 错误跳行 + import 建议） | P0-3 | 2 人日 | 中 |
| 一 | **WP-003** | user_permission 语义（运行前请求 + 页面联动） | P0-2 | 1.5 人日 | 中 |
| 二 | **WP-004** | 工程属性页（pro projectinfo 平移） | P1-1 | 2 人日 | 中 |
| 二 | **WP-005** | 编辑器文件操作 + 工程重命名 | P1-2 | 1 人日 | 低 |
| 二 | **WP-006** | 查错 / 格式化 / 函数导航 / gotoLine | P1-3 | 2.5 人日 | 中 |
| 二 | **WP-007** | 导入分析（fiximport）+ 错误建议 import | P1-4 | 1.5 人日 | 中 |
| 二 | **WP-008** | Java API 浏览器 | P1-5 | 1.5 人日 | 中 |
| 二 | **WP-009** | 布局助手（预览 → 拖拽 → 转换器，分期） | P1-6 | 5 人日 | 高 |
| 二 | **WP-010** | 最近文件 / 继续上次 | P1-7 | 1 人日 | 低 |
| 二 | **WP-011** | 日志查看器（logcat 过滤） | P1-8 | 1 人日 | 低 |
| 二 | **WP-012** | 补全增强（生命周期 + 成员 + 类名） | P1-9 | 1.5 人日 | 中 |
| 二 | **WP-013** | 运行入口跟随当前标签 | P1-10 | 0.5 人日 | 低 |
| 三 | **WP-014** | 独立 APK 打包（立项设计 + luac 验证） | P2-1 | 5 人日 | 高 |
| 三 | **WP-015** | 导入同名确认 + 插件卸载 | P2-2 | 0.5 人日 | 低 |
| 三 | **WP-016** | 权限页细化（单项请求 + 特殊权限） | P2-3 | 1 人日 | 低 |
| 三 | **WP-017** | init.lua 解析结构化 + 残留清理 | P2-4/5 | 1 人日 | 中 |
| 三 | **WP-018** | 字号/Tab 设置 UI + 帮助手册 | P2-6 | 1 人日 | 低 |
| 三 | **WP-019** | 自动化测试骨架 + ADB 回归矩阵 | P2-7/8 | 2 人日 | 中 |

---

## 4. 阶段一：P0 阻塞级（WP-001 ~ WP-003）

---

### WP-001 工作区编译修复（抽屉重构收尾）

| 属性 | 值 |
|---|---|
| **严重程度** | P0 — 阻塞级 |
| **前置依赖** | 无 |
| **预估工时** | 0.5 人日 |
| **风险等级** | 低 |

##### 问题描述

当前工作区（未提交 WIP，历史审计 EDITOR_DRAWER_AUDIT_REPORT Phase 0~5 已完成、Phase 6 验证未执行）无法编译：

1. `EditorScreen.kt:179` 引用不存在的 `NavigationDrawerPanel`、`:191` 引用不存在的 `EditorBody`（两组件在全仓与 git 历史中均无定义）；
2. `EditorScreen.kt:8,76` 使用 `androidx.activity.compose.LocalActivity`，需 activity-compose ≥ 1.10，工程锁定 `1.9.3`（`app/build.gradle.kts:118`）；
3. `app/build.gradle.kts:116` 已加 `material3-window-size-class`（未提交）。

对照：HEAD（36eac8c）在独立 worktree 验证 `:app:compileArm64DebugKotlin` **BUILD SUCCESSFUL**。

##### 修复方案（三选一，推荐 ①）

| 方案 | 做法 |
|---|---|
| ① 补齐组件（推荐） | 按 `EDITOR_DRAWER_AUDIT_REPORT.md` Phase 2/5 设计，将 `EditorScreen.kt` 的抽屉与主体抽取为 `EditorScreen.kt` 内私有 `NavigationDrawerPanel`（Modal 窄屏 / Permanent 宽屏分支，官方 `ModalNavigationDrawer`/`PermanentNavigationDrawer`）与 `EditorBody`（标签行 + LuaEditor + 搜索面板 + 存储权限横幅）两个 composable，保持 `DrawerContent`/`StoragePermissionBanner` 现结构 |
| ② API 替换 | `LocalActivity.current` → `LocalContext.current as Activity`（1 处类型声明，`calculateWindowSizeClass(activity)` 需 activity 参数），不动依赖 |
| ③ 升级依赖 | activity-compose → 1.10.x，同时复核 compose-bom 兼容性（升级面较大，不推荐单独为本任务做） |

##### 验收标准

| # | 标准 | 验证方法 |
|---|---|---|
| ACC-1 | `.\gradlew.bat :app:compileArm64DebugKotlin` 零错误 | 编译 |
| ACC-2 | `.\gradlew.bat :app:assembleX64Debug` 完整构建通过 | 编译 |
| ACC-3 | R1 门禁零命中（`editor/src` 对 `luajava\|androlua\|loadLibrary`） | grep/Select-String |
| ACC-4 | 抽屉回归：转屏后抽屉保持关闭；抽屉开启时返回键先收起再退出；360dp 窄屏抽屉不覆盖顶栏；宽屏（≥840dp）抽屉常驻且汉堡键隐藏 | 模拟器（adb 旋转 `settings put system user_rotation`）+ 截图比对 |
| ACC-5 | WIP 变更整体提交，`EDITOR_DRAWER_AUDIT_REPORT.md` Phase 6 勾选完成 | git log |

---

### WP-002 运行诊断闭环（结果回传 + 错误跳行 + import 建议）

| 属性 | 值 |
|---|---|
| **严重程度** | P0 — 阻塞级（运行体验核心差距） |
| **前置依赖** | WP-001 |
| **预估工时** | 2 人日 |
| **风险等级** | 中 |

##### 问题描述

当前运行是**单向拉起**（`RunEngineLauncher.launch` → `startActivity` → EngineActivity 全屏，引擎页内置控制台展示输出），宿主收不到任何运行结果。pro 的闭环（`main.lua:1503-1533` onActivityResult）：

- 解析运行错误文本中的 `路径:行号:` → **自动跳转到出错行**；
- 解析 `a nil value (global 'X')` → **弹窗建议 `import "..."`**；
- 运行前 `func.check(true)` 语法预检，失败不运行。

##### 方案

1. **引擎侧**（`:app`，EngineActivity.java）：重写 `onCreate` 捕获 `LuaState.LdoFile` 抛出的 `LuaException`（`LuaActivity.runScript` 错误路径），连同 `print` 输出（`outputBuffer` 语义，历史 WP-003 曾实现后删除）在 `onDestroy` 经 **`setResult(RESULT_OK, Intent)` + ActivityResult 通道**回传（同进程 Activity 栈，无需文件协议）；
2. **宿主侧**（`RunEngineLauncher.kt` + `EditorScreen.kt`）：改 `rememberLauncherForActivityResult` 启动；收到结果后：
   - 成功 → Snackbar 提示运行结束（可选时长）；
   - 失败 → 底部对话框展示错误文本，**「跳到出错行」**按钮（解析 `路径:行号:` → `session` 打开对应文件 + `CodeEditor.gotoLine`）；
   - `nil global 'X'` → 展示 **「插入 import」** 建议按钮（联动 WP-007 的类表匹配）；
3. 历史文件协议（`engine_result.json`）不恢复；结果以 Intent extra 传递（`String[]` 或 序列化对象），注意 extra 体积上限（输出超限截断）；
4. 引擎页内置控制台保留（双通道：引擎内可见 + 返回后宿主可查）。

##### 验收标准

| # | 标准 | 验证方法 |
|---|---|---|
| ACC-1 | 运行 demo 工程正常退出后返回编辑器，Snackbar 提示且无崩溃 | ADB 回归 |
| ACC-2 | 运行含 `local x = nil; print(x.y)` 的工程 → 宿主展示错误文本 + 「跳到出错行」点击后定位到对应行（截图比对行高亮） | ADB 回归 |
| ACC-3 | 运行含 `nil global 'http'` 的工程 → 宿主给出 import 建议入口 | ADB 回归 |
| ACC-4 | 脚本 print 大量输出时 extra 截断而非崩溃 | ADB 回归 |
| ACC-5 | 含死循环脚本：引擎页可 ANR 提示，返回宿主不崩（超时提示为可选增强） | ADB 回归 |

---

### WP-003 user_permission 语义（运行前请求 + 页面联动）

| 属性 | 值 |
|---|---|
| **严重程度** | P0 — 阻塞级（工程权限声明形同虚设） |
| **前置依赖** | WP-001、WP-017（结构化解析，可先行实现 WP-017 的解析部分） |
| **预估工时** | 1.5 人日 |
| **风险等级** | 中 |

##### 问题描述

`init.lua` 模板写入 `user_permission = {}`（`ProjectManager.kt:144`、`MainActivity.kt:154`），但全仓（androlua Java + app Kotlin）**零消费**。pro 侧：`projectinfo/main.lua:85-101` 把勾选权限写回 init.lua；打包时按白名单过滤 manifest（`bin.lua:324-329`）；`permission.lua` 的数据源 = APK `requestedPermissions`。

##### 方案

1. **解析**（依赖 WP-017 的结构化读取器）：`ProjectManager.loadUserPermissions(project)` 返回 `List<String>`（数组项支持字符串/裸标识符）；
2. **运行前请求**（`RunEngineLauncher`）：启动引擎前检查工程声明中未授予的运行时权限 → `RequestMultiplePermissions` 一次请求（对齐 pro 的"运行前保证权限"体验）；拒绝时 Toast 提示并继续/中止（决策：继续运行，权限由脚本运行时自行处理，与 pro 一致）；
3. **PermissionScreen 联动**：新增「当前工程权限」区块——列出工程声明项及其授予状态（复用现有 granted/denied 行组件）；
4. **模板口径**：`ProjectManager.createProject` 与 `MainActivity.adoptStandaloneFile` 的模板统一（WP-017 清理 `dependencies` 死块时一并处理）。

##### 验收标准

| # | 标准 | 验证方法 |
|---|---|---|
| ACC-1 | 工程 init.lua 声明 `user_permission={"CAMERA"}` → 模拟器运行该工程 → 系统权限对话框出现 | ADB 回归（截图） |
| ACC-2 | 权限页显示该工程声明权限及授予状态 | ADB 回归 |
| ACC-3 | 取消勾选/改写 init.lua 后，PermissionScreen 与运行行为同步（无缓存） | ADB 回归 |
| ACC-4 | 无 user_permission 块的工程运行不受影响（空集合） | ADB 回归 |

---

## 5. 阶段二：P1 高优先级（WP-004 ~ WP-013）

---

### WP-004 工程属性页（pro projectinfo 平移）

| 属性 | 值 |
|---|---|
| **严重程度** | P1 |
| **前置依赖** | WP-001、WP-017（结构化解析/写回） |
| **预估工时** | 2 人日 |
| **风险等级** | 中 |

##### 问题描述

lide 无工程属性编辑。pro `projectinfo/main.lua`（145 行 + layout.aly 138 行）支持编辑：`appname/appver/appcode/appsdk/path_pattern/packagename/theme/app_key/app_channel/developer/description/debugmode` + 权限多选（写回 `user_permission`），`theme` 为 `android.R.style.Theme_*` Spinner。

##### 方案（Compose 新路由 `projectinfo/{projectName}`）

1. 表单页（TextField 系）+ 权限多选对话框（权限清单数据源：`PackageManager.getSystemAvailableFeatures` 或静态常用表，对齐 `permission.lua`）；
2. 保存 = 结构化改写 init.lua（复用 WP-017 的写入器：字段级 set / `user_permission` 数组替换，保持文件其余内容与注释不动，字符串经 `%q` 等价转义）；
3. 入口：工程列表长按菜单 + 编辑器抽屉工程名区域；
4. `theme/debugmode` 字段在打包 WP-014 生效前仅持久化（UI 可见、不影响运行）。

##### 验收标准

| # | 标准 | 验证方法 |
|---|---|---|
| ACC-1 | 修改 appname/appver/packagename/description → 保存 → `adb pull init.lua` 校验内容正确且注释保留 | ADB |
| ACC-2 | 权限多选写回 `user_permission` 数组，格式与 pro 模板一致 | ADB |
| ACC-3 | 工程列表/编辑器标题显示新 appname | ADB 回归 |
| ACC-4 | 含注释/长字符串的既有 init.lua 编辑后不损坏（WP-017 用例） | 单测 + ADB |

---

### WP-005 编辑器文件操作 + 工程重命名

> **（已完成）2026-08-05**：EditorComponents.kt:112 文件树操作菜单（新建/重命名/删除）+ `renameProjectEntry`（:168）；ProjectManager.kt:231,248 工程重命名（重名 -1/-2 防护）。验收证据：代码实况核对；待真机回归补充。

| 属性 | 值 |
|---|---|
| **严重程度** | P1 |
| **前置依赖** | WP-001 |
| **预估工时** | 1 人日 |
| **风险等级** | 低 |

##### 问题描述

文件树只读：编辑器内不能新建/重命名/删除文件、不能新建目录（pro 文件浏览器长按删除 + 目录导航）；工程无重命名。

##### 方案

1. `ProjectFileTree.kt` 节点长按菜单：新建 .lua / 新建目录 / 重命名 / 删除（含确认；打开中的文件删除/重命名联动 `EditorSession` 标签——路径更新、脏标记迁移、Content 缓存失效）；
2. `ProjectManager` 新增 `renameProject(old, new)`（目录改名 + 引用校验）；入口：工程列表长按菜单；
3. 非法字符/重名冲突防护（重名后缀 -1/-2，对齐 .alp 导入策略）。

##### 验收标准

| # | 标准 | 验证方法 |
|---|---|---|
| ACC-1 | 树内新建/重命名/删除文件即时反映，编辑器打开/保存路径正确 | ADB 回归 |
| ACC-2 | 打开中的文件被重命名 → 标签路径跟随、无脏数据丢失 | ADB 回归 |
| ACC-3 | 工程重命名后列表/路由/运行链路正确 | ADB 回归 |

---

### WP-006 查错 / 格式化 / 函数导航 / gotoLine

| 属性 | 值 |
|---|---|
| **严重程度** | P1 |
| **前置依赖** | WP-001、WP-002（查错结果回传链路） |
| **预估工时** | 2.5 人日 |
| **风险等级** | 中 |

##### 问题描述

pro 提供：`func.check`（luac 编译查错，`console.lua:87` build）、`console.format`（格式化）、`goto_func`（函数导航）、`goto_line`。lide 均无（仅行级词法诊断）。

##### 方案（注意 R2 边界——编译属运行时语义，放 `:app`）

| 子项 | 实现 | 位置 |
|---|---|---|
| 查错 | EngineActivity 增加 **check 模式**（extra `check=1`：仅 `LuaState.loadfile` 编译不执行 → 错误文本经 WP-002 通道回传）；EditorScreen「查错」按钮触发，错误跳行复用 WP-002 | `:app` |
| 格式化 | 纯 Kotlin Lua 格式化器（`:editor` 文本级）：缩进规范（块关键字 depth）、连续空格规范化、尾随空白清理；**v1 仅做缩进级**，不动字符串/长括号内容 | `:editor` |
| 函数导航 | `:editor` 词法扫描 `function`/`local function`/`方法定义` 列表 → 抽屉式弹出列表 → `CodeEditor.gotoLine`（sora 原生 API） | `:editor` |
| gotoLine | 工具栏「转到行」输入框 → `gotoLine` | `:editor` |

##### 验收标准

| # | 标准 | 验证方法 |
|---|---|---|
| ACC-1 | 含语法错误工程 → 查错 → 错误回传并跳行（不启动运行） | ADB 回归 |
| ACC-2 | 正确工程查错 → 提示语法通过，可一键转运行 | ADB 回归 |
| ACC-3 | 格式化前后 `loadfile` 编译结果一致（等价性），字符串/长注释原样保留 | 单测 |
| ACC-4 | 函数导航列出函数并跳转正确行 | ADB 回归 |

---

### WP-007 导入分析（fiximport）+ 错误建议 import

| 属性 | 值 |
|---|---|
| **严重程度** | P1 |
| **前置依赖** | WP-001、WP-002（错误建议入口） |
| **预估工时** | 1.5 人日 |
| **风险等级** | 中 |

##### 问题描述

pro `javaapi/fiximport.lua`（122 行）：LuaLexer 遍历 token 收集「点号后标识符」→ 与 `javaapi/android.lua` 类表匹配 → 多选复制 `import "..."`。lide 无。

##### 方案（`:editor` 文本级实现，R1 合规）

1. 类表数据源：平移 pro `android.lua`（4510 行类名表）为 Kotlin 资源（`assets/javaapi_classes.txt` 或 Kotlin 编译期提取），`:editor` 与 `:app`（WP-008）共用；
2. `LuaDiagnostics`/独立扫描器产出「未导入类候选」：正则/词法提取标识符 → 类表后缀匹配（`[%.$]Name$` 语义）→ 排除已 import 与 Lua 内置；
3. UI：工具栏「导入分析」→ 底部弹层多选列表（全选/复制）→ 在文件头部插入 `import "..."` 行（保持首行注释/缩进结构）；
4. WP-002 的 `nil global 'X'` 建议直接复用本匹配器。

##### 验收标准

| # | 标准 | 验证方法 |
|---|---|---|
| ACC-1 | 打开引用未导入类的文件 → 分析列出候选（命中 android.lua 类表） | 单测（类表样例）+ ADB |
| ACC-2 | 插入 import 后行号/结构不破坏（首行 shebang/注释场景） | 单测 |
| ACC-3 | `nil global 'X'` 错误建议入口与 WP-002 联调通过 | ADB 回归 |

---

### WP-008 Java API 浏览器

| 属性 | 值 |
|---|---|
| **严重程度** | P1 |
| **前置依赖** | WP-001 |
| **预估工时** | 1.5 人日 |
| **风险等级** | 中 |

##### 问题描述

pro `javaapi/main.lua`（163 行）：类表 → 反射展示构造器/事件（`setOnXxxListener` 推导 `onXxx`）/getter/setter/字段/方法，两级过滤 + 长按复制。lide 无。

##### 方案

1. 新路由 `javaapi`（`:app`）：类表数据源同 WP-007；列表页（搜索过滤）→ 详情页（`Class.forName` 反射，事件推导规则与 pro 一致）；
2. 长按/点击复制类名与方法签名；「插入 import」按钮跳转编辑器会话；
3. 反射失败类（宿主未打包的 pro 扩展类）显示占位提示（如 `LuaEditor` 等已被替换的类，标注「已由 sora 替代」）。

##### 验收标准

| # | 标准 | 验证方法 |
|---|---|---|
| ACC-1 | 搜索 `LuaActivity`/`TextView` → 详情展示构造器/方法/事件，与 pro 页面一致 | ADB 回归（截图对照） |
| ACC-2 | 复制与「插入 import」可用 | ADB 回归 |

---

### WP-009 布局助手（预览 → 拖拽 → 转换器，分期）

| 属性 | 值 |
|---|---|
| **严重程度** | P1（工作量最大项） |
| **前置依赖** | WP-001、WP-004（工程上下文） |
| **预估工时** | 5 人日（分 3 期） |
| **风险等级** | 高 |

##### 问题描述

pro `layouthelper/main.lua`（590 行）：将 `.aly` 布局表用 `loadlayout2.lua` 渲染为可触摸 UI——单指拖拽移动/边缘缩放、按控件类型弹属性表、添加/删除/父/子控件、枚举属性下拉（orientation/gravity/scaleType）、相对布局 22 属性、`src` 图片选择、`dumplayout2` 序列化回 `.aly`、预览/保存；`xml2table.lua`（135 行）为 Android XML → 布局表转换器。lide 仅有 `assets/layouts/javaapi/*.aly` 模板资产（提取到 `filesDir/layouts/` 后**零消费**）。

##### 方案（分期立项）

| 期 | 内容 | 交付 |
|---|---|---|
| 9-A（1 人日） | `.aly` 入口与预览：编辑器打开 `.aly` → `loadlayout` 语义等价物（`:app` 复用引擎 `loadlayout.lua` 渲染预览 Activity）；`filesDir/layouts/` 模板接入 UI（「布局模板」入口） | 预览闭环 |
| 9-B（2 人日） | 属性编辑 + 结构操作：控件树列表、属性表单（枚举下拉复用 pro `checks` 表数据）、添加/删除/父子切换；`dumplayout` 序列化写回 `.aly`（平移 `dumplayout2` 逻辑为 Kotlin） | 编辑闭环 |
| 9-C（2 人日） | 可视拖拽（拖拽移动/边缘缩放，平移 layouthelper 手势逻辑）+ XML→布局表转换器（平移 xml2table 为 Kotlin） | 对齐 pro |

> 9-A 先行验收后 9-B/C 独立立项，避免长阻塞。

##### 验收标准

| # | 标准 | 验证方法 |
|---|---|---|
| ACC-1 | 从编辑器打开样例 `.aly` → 预览渲染与引擎 loadlayout 一致 | ADB 回归（截图对照 pro 逻辑） |
| ACC-2 | 9-B：修改控件属性 → 保存 → `adb pull .aly` 校验布局表正确 | ADB |
| ACC-3 | 9-C：拖拽移动控件 → 保存 → 位置写入 layout_x/layout_y | ADB 回归 |
| ACC-4 | 9-C：Android XML 布局转换 → 预览可用 | ADB 回归 |

---

### WP-010 最近文件 / 继续上次

| 属性 | 值 |
|---|---|
| **严重程度** | P1 |
| **前置依赖** | WP-001 |
| **预估工时** | 1 人日 |
| **风险等级** | 低 |

##### 问题描述

pro 用 `lua.hist`（50 条历史）+ `lua.conf`（luapath + 光标）+ `lua.proj`（上次工程）持久化；重启恢复上次工程与光标。lide 每次启动回工程列表页，无跨会话状态。

##### 方案

1. `AppStateStore`（SharedPreferences，`SettingsManager` 同款模式）：上次工程/上次标签组（含光标 offset）/最近工程列表（含最近文件，去重上限 50，对齐 pro）；
2. 工程列表页「最近」分区（或进入时提示「继续上次」）；
3. 编辑器打开文件时记录光标（`ContentChangeEvent`/onDispose 时机，复用自动保存节流）。

##### 验收标准

| # | 标准 | 验证方法 |
|---|---|---|
| ACC-1 | 打开工程编辑后强杀应用重启 → 恢复上次工程与标签（光标尽力恢复） | ADB 回归 |
| ACC-2 | 最近列表正确去重/排序/上限 | 单测 |
| ACC-3 | 历史记录被清除入口（设置页） | ADB 回归 |

---

### WP-011 日志查看器（logcat 过滤）

| 属性 | 值 |
|---|---|
| **严重程度** | P1 |
| **前置依赖** | WP-001 |
| **预估工时** | 1 人日 |
| **风险等级** | 低 |

##### 问题描述

pro `logcat.lua`（143 行）：`io.popen("logcat -d -v long")` + All/Lua/Test/Tcc/Error/Warning/Info/Debug/Verbose/Clear 过滤 + 关键字过滤。lide 无。

##### 方案

1. 新路由 `logcat`（`:app`）：`ProcessBuilder("logcat", "-d", "-v", "time")` 拉取 → 过滤芯片组（标签含 `Lua`/进程 pid 关联）+ 级别过滤 + 关键字输入；
2. API 29+ `READ_LOGS` 受限：默认展示**本进程**日志（`logcat --pid=<自 pid>`）+ 全量日志需 READ_LOGS（manifest 已声明，提示授予）；Lua 错误/print 输出（`sendMsg` 的 Log 通道）必须可见；
3. 入口：设置页「日志」。

##### 验收标准

| # | 标准 | 验证方法 |
|---|---|---|
| ACC-1 | 运行含 `print` 与错误脚本 → 日志页可过滤出 Lua 相关行（错误定位文本可见） | ADB 回归 |
| ACC-2 | 级别/关键字过滤生效，清空可用 | ADB 回归 |

---

### WP-012 补全增强（生命周期 + 成员 + 类名）

| 属性 | 值 |
|---|---|
| **严重程度** | P1 |
| **前置依赖** | WP-001、WP-007（类表数据源） |
| **预估工时** | 1.5 人日 |
| **风险等级** | 中 |

##### 问题描述

pro：后台 task 构造 15 个生命周期方法名 + javaapi 类短名 → `editor.addNames`；`activity.getMethods()` → `addPackage("activity", ...)`。lide：仅 22 关键字 + 10 snippet，无 Java API/成员补全。

##### 方案（`:editor`）

1. `LuaLanguage` 补全数据扩充：
   - 生命周期方法集（onCreate/onResume/...，平移 pro 15 个，表驱动）；
   - javaapi 类短名表（WP-007 数据源子集，含 package 前缀匹配）；
   - `activity.`/`service.`/`this.` 等常用对象成员表（从 LuaActivity 公开方法静态提取为 Kotlin 常量表，平移 `getMethods` 语义——注意运行态反射不可用，改用静态表）；
2. 补全触发规则：关键字补全（现有）+ 成员补全（`xx.` 前缀）+ 类名补全（大写/包名前缀）。

##### 验收标准

| # | 标准 | 验证方法 |
|---|---|---|
| ACC-1 | 输入 `act` → 出现生命周期方法/类名候选项 | 单测 + ADB 截图 |
| ACC-2 | 输入 `activity.` → 出现 `sendMsg/setContentView/newActivity/...` 成员 | ADB 截图 |
| ACC-3 | 补全插入后语法高亮/缩进正常，无崩溃（异常降级保持） | ADB 回归 |

---

### WP-013 运行入口跟随当前标签

> **（已完成）2026-08-05**：EditorScreen.kt:156-159 运行逻辑按当前标签分流（main.lua → 工程；其他 → 该文件）。验收证据：代码实况核对；待真机回归补充。

| 属性 | 值 |
|---|---|
| **严重程度** | P1 |
| **前置依赖** | WP-001 |
| **预估工时** | 0.5 人日 |
| **风险等级** | 低 |

##### 问题描述

`EditorScreen.kt:170` 恒运行 `project.mainFile`，与当前激活标签无关（用户编辑 page_xxx.lua 点运行，跑的是 main.lua）。

##### 方案

1. 运行逻辑改为：当前标签为 `main.lua` → 运行工程；其他文件 → 运行该文件（`EngineActivity` luaDir 仍为工程根，`init.lua`/`import` 上下文正确）；
2. 工具栏长按运行/菜单给出「运行当前文件 vs 运行 main.lua」二选（pro 语义：工程则 main、单文件则当前）。

##### 验收标准

| # | 标准 | 验证方法 |
|---|---|---|
| ACC-1 | 编辑非 main 标签 → 运行 → 引擎执行该文件（可 print 验证） | ADB 回归 |
| ACC-2 | main.lua 标签运行行为不变 | ADB 回归 |

---

## 6. 阶段三：P2 可选/工程化（WP-014 ~ WP-019）

---

### WP-014 独立 APK 打包（立项设计 + luac 验证）

| 属性 | 值 |
|---|---|
| **严重程度** | P2（pro 最大单项能力；v1.0 已书面决策暂不支持，本 WP 为立项推进） |
| **前置依赖** | WP-002（运行/编译链）、WP-003（权限白名单） |
| **预估工时** | 5 人日（分 3 期） |
| **风险等级** | 高 |

##### 问题描述

pro `bin.lua`（398 行）：以宿主 APK 为模板重打包——luac 编译、AXmlDecoder manifest 二进制补丁（包名/label/版本/权限白名单置空/versionCode）、checklib 依赖分析（`.using` 清单 → 内置 .so/.lua 去重）、图标注入、签名（testkey/platform）、自动安装。lide 无（`assets/keys` 未入仓，无任何打包代码）。

##### 方案（分期立项）

| 期 | 内容 |
|---|---|
| 14-A（1 人日） | **luac 验证**：`:app` 新增 `LuaCompile` 工具（LuaState `loadbuffer + string.dump` → `.luac`），CLI/UI 入口「编译」；验证 `.luac` 可被引擎 `loadfile` 执行 |
| 14-B（2 人日） | **manifest 补丁**：自研 AXML 二进制解析/重写器（pro `mao.res.AXmlDecoder` 源码不在仓库，需独立实现 chunk 解析：STRING/RESOURCE/MAP/START_ELEMENT 表）——先行设计文档 + chunk 读写骨架 |
| 14-C（2 人日） | **checklib + 重打包 + 签名**：require/import 正则依赖分析（平移 bin.lua:131-174 逻辑）、zip 重打包过滤（:104-129 可移除清单）、v1 签名（JAR）+ v2 签名（APK Signing Block 实现，v1-only 在 targetSdk≥30 不可安装） |

> 验收通过后更新 `WHITEPAPER.md` 阶段四状态与 `PROGRESS.md`。`assets/keys/`（AOSP testkey）随 14-C 入仓。

##### 验收标准

| # | 标准 | 验证方法 |
|---|---|---|
| ACC-1 | 14-A：`loadfile` 编译任意 samples 工程脚本 → `.luac` → 引擎可运行且行为一致 | ADB 回归 |
| ACC-2 | 14-B：解析宿主 manifest → 重写包名/label → 新 manifest 可被 aapt/系统识别（或单元断言 chunk 结构） | 单测 |
| ACC-3 | 14-C：demo 工程打包 APK → 安装 → 运行正常（demo 不依赖宿主 IDE 运行时） | ADB 回归 |
| ACC-4 | 权限白名单过滤生效（未勾选权限在产物 manifest 中消失） | 反编译/系统行为验证 |

---

### WP-015 导入同名确认 + 插件卸载

> **（已完成）2026-08-05**：AlpImportDialogs.kt:12 导入同名冲突确认（覆盖/改名/取消）；PluginScreen 插件卸载。验收证据：代码实况核对；待真机回归补充。

| 属性 | 值 |
|---|---|
| **严重程度** | P2 |
| **前置依赖** | WP-001 |
| **预估工时** | 0.5 人日 |
| **风险等级** | 低 |

##### 问题描述

`AlpManager.kt:102-106` 同名工程/插件导入直接 `deleteRecursively` 覆盖无确认；`PluginScreen` 无卸载/删除。

##### 方案

1. 导入检测同名 → 确认对话框（覆盖/改名/取消，改名沿用 `-1/-2` 策略）；
2. 插件卡片长按 → 卸载确认（删除目录 + 刷新）。

##### 验收标准

| # | 标准 | 验证方法 |
|---|---|---|
| ACC-1 | 同名导入弹确认，三选项均正确落盘 | ADB 回归 |
| ACC-2 | 插件可卸载，列表刷新 | ADB 回归 |

---

### WP-016 权限页细化（单项请求 + 特殊权限）

> **（已完成）2026-08-05**：PermissionScreen.kt:488-524 特殊权限分组（OVERLAY/WRITE_SETTINGS/IGNORE_BATTERY/INSTALL_PACKAGES 跳系统页）。验收证据：代码实况核对；待真机回归补充。

| 属性 | 值 |
|---|---|
| **严重程度** | P2 |
| **前置依赖** | WP-001 |
| **预估工时** | 1 人日 |
| **风险等级** | 低 |

##### 问题描述

`PermissionScreen` 一次请求全部 24 项；`SYSTEM_ALERT_WINDOW / WRITE_SETTINGS / REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 等特殊权限无管理入口（manifest 已声明）。

##### 方案

1. 权限行独立「请求」按钮（单项）；已授予自动置灰；
2. 特殊权限分组：`SYSTEM_ALERT_WINDOW`（`ACTION_MANAGE_OVERLAY_PERMISSION`）、`WRITE_SETTINGS`（`ACTION_MANAGE_WRITE_SETTINGS`）、`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`、`REQUEST_INSTALL_PACKAGES`（WP-014 前置）；
3. 关联 WP-003 的工程声明区块。

##### 验收标准

| # | 标准 | 验证方法 |
|---|---|---|
| ACC-1 | 单项请求独立生效 | ADB 回归 |
| ACC-2 | 悬浮窗/修改系统设置跳转系统页并回写状态 | ADB 回归 |

---

### WP-017 init.lua 解析结构化 + 残留清理

| 属性 | 值 |
|---|---|
| **严重程度** | P2（WP-003/004 的底座，建议提前） |
| **前置依赖** | WP-001 |
| **预估工时** | 1 人日 |
| **风险等级** | 中 |

##### 问题描述

1. `ProjectManager.kt:43-48` 正则解析 `appname` 会命中注释内容；`PluginManager.fieldOf` 同病；
2. `MainActivity.kt:155-158` 收养文件生成的 init.lua 含 `dependencies = {libs={}, modules={}}` 死块（引擎不消费，与 WP-010 模板口径不一致）。

##### 方案

1. **结构化解析器** `LuaConfigParser`（`:app`，纯 Kotlin）：init.lua 顶层 key-value 提取（跳过注释/字符串/长括号）、`user_permission`/`pages` 数组解析、字段级写入器（保留原文格式与注释，替换单字段）；
2. 替换 `ProjectManager`/`PluginManager`/`MainActivity` 全部正则读法；新增单测（注释命中、长字符串、数组缺省场景）；
3. 收养模板删除 `dependencies` 块，`createProject` 模板同步（口径统一：`appname/appver/packagename/icon/pages/user_permission`）。

##### 验收标准

| # | 标准 | 验证方法 |
|---|---|---|
| ACC-1 | 含 `-- appname="注释"` 的文件解析结果不含该值 | 单测 |
| ACC-2 | 写回后 init.lua 仅目标字段变化，其余字节级不变 | 单测 |
| ACC-3 | 新收养/新工程 init.lua 无 `dependencies` 块 | grep 断言 |

---

### WP-018 字号/Tab 设置 UI + 帮助手册

> **（部分完成）2026-08-05 字号/Tab 设置；帮助手册未落地（2026-08-09 审计收口）**：
> SettingsScreen.kt 字号/Tab 设置已完成；「HelpScreen.kt + HelpParser.kt 帮助手册」**从未实现**（源码不存在），
> 工作单原登记为已完成属文档漂移。2026-08-09 全量审计：`assets-zh/help.txt`（59 行 @章节@ 帮助文本，
> 仅中文本、无代码消费、无 gradle sourceSet 配置、默认不进 APK）已删除；帮助页任务仍待实现，重开后
> 平移 pro `help.lua`（1329 行）为 `assets/help.txt`（en + zh 双份）+ Compose 章节渲染，入口设于设置页。

| 属性 | 值 |
|---|---|
| **严重程度** | P2 |
| **前置依赖** | WP-001 |
| **预估工时** | 1 人日 |
| **风险等级** | 低 |

##### 问题描述

`SettingsManager` 有 `font_size/tab_size` key 且 `LuaEditor` 消费，但 SettingsScreen 无入口；帮助手册缺失（pro `help.lua` 32 节 @章节@ 纯文本）。

##### 方案

1. 设置页「编辑器」组加字号（Slider/步进）与 Tab 宽度；
2. 帮助页：平移 `help.lua` 为 `assets/help.txt`（@章节@ 结构化），Compose 渲染章节列表 + 正文（复用 `LuaManualScreen` WebView 或纯文本列表——决策：纯文本章节列表 + 折叠正文，离线可用）；入口：设置页「帮助」。

##### 验收标准

| # | 标准 | 验证方法 |
|---|---|---|
| ACC-1 | 字号/Tab 调整实时生效并持久化 | ADB 回归 |
| ACC-2 | 帮助页章节可展开阅读 | ADB 回归 |

---

### WP-019 自动化测试骨架 + ADB 回归矩阵

| 属性 | 值 |
|---|---|
| **严重程度** | P2（工程化底座，建议随第一批 WP 并行铺） |
| **前置依赖** | WP-001 |
| **预估工时** | 2 人日 |
| **风险等级** | 中 |

##### 问题描述

全仓零测试源集（无 `test`/`androidTest`）；真机回归依赖人工探针。

##### 方案

1. **单测**（JVM，`app/src/test` + `editor/src/test`）：`LuaConfigParser`、`ZipUtil` zip-slip、`AlpManager` kind 分流、`LuaDiagnostics` 增量用例、格式化等价性、WP-007 类表匹配——覆盖 WP 相关纯逻辑；
2. **回归脚本** `scripts/adb_regress.ps1`：构建 x64 → 安装 → 推送 4 个基准工程 → 逐个拉起运行 → 断言（logcat 关键行/进程存活/截图存档 `%TEMP%\lide-verify\`，对齐 LIBS-006 矩阵风格）；
3. CI 追加 `test` job（`./gradlew test`）；ADB 脚本本地维护不入 CI。

##### 验收标准

| # | 标准 | 验证方法 |
|---|---|---|
| ACC-1 | `.\gradlew.bat :app:testX64DebugUnitTest :editor:testDebugUnitTest` 全绿 | 构建 |
| ACC-2 | `scripts/adb_regress.ps1` 一键跑完 4 工程矩阵并输出 PASS/FAIL 汇总 | 本机执行 |
| ACC-3 | 任一 WP 提交前先跑相关单测（提交检查清单落实） | 流程 |

---

## 7. 依赖关系与执行顺序

```
WP-001（编译修复，总闸门）
 ├─ WP-002（诊断闭环）
 │   ├─ WP-006（查错回传依赖）
 │   └─ WP-007（错误建议 import）
 ├─ WP-017（结构化解析，提前做）
 │   ├─ WP-003（user_permission）
 │   └─ WP-004（工程属性页）
 ├─ WP-005 / WP-010 / WP-011 / WP-012 / WP-013 / WP-015 / WP-016 / WP-018（可并行）
 ├─ WP-008（类表数据源与 WP-007 共享，可并行）
 ├─ WP-009（分 3 期，9-A 可并行）
 └─ WP-014（依赖 WP-002/003，阶段三）
WP-019（测试骨架，第一批并行铺）
```

**建议批顺序**：批 1 = WP-001 + WP-019；批 2 = WP-002 + WP-003；批 3 = WP-004 + WP-005 + WP-006；批 4 = WP-007 + WP-008 + WP-010 ~ WP-013；批 5 = WP-009 + WP-011；批 6 = WP-015 ~ WP-018；批 7 = WP-014（独立立项）。

---

## 8. 验收总则

1. **编译门禁**（每个 WP 提交前）：
   - `.\gradlew.bat :app:compileArm64DebugKotlin --console=plain` ✅
   - `.\gradlew.bat :app:assembleX64Debug --console=plain` ✅（模拟器可安装）
   - 涉及 `:editor` 的 WP 追加 R1 门禁：`Select-String -Path editor\src -Pattern "luajava|androlua|loadLibrary" -Recurse` 零命中
2. **回归基准**：4 个 samples 工程（demo/DemoProject/kleinbottle/testjson）在模拟器逐个运行不崩、关键功能正常（WP-019 脚本化）；
3. **设备验证**：每 WP 至少一次 ADB 实测（截图存档 `%TEMP%\lide-verify\`）；`pm clear`/`pm grant` 在生产构建不可用的限制沿用 LIBS-006 经验（debug 构建可 grant）；
4. **文档登记**：完成后更新 `PROGRESS.md`（WP-XXX 小节：完成内容/验收表/剩余）；结论性变更同步 `WHITEPAPER.md`；
5. **提交约定**：`WP-XXX: <description>`；不得夹带未关联改动；
6. **拒绝验收**：物理/模拟器崩溃、ANR、新 lint 告警、门禁未过、无设备验证记录。

---

## 9. 回滚与应急

| 场景 | 处置 |
|---|---|
| 单 WP 引入回归 | `git revert HEAD --no-edit`；相关单测先行复现 |
| 原生/构建链问题 | `git checkout -- prebuilt/ app/src/main/jniLibs/` 恢复已知良好 .so（LIBS 经验） |
| WIP 未提交内容损坏 | 工作区 WIP 先 `git stash push` 再恢复；WP-001 完成后立即提交切干净基线 |
| WP 三日内无法验收 | 标记 **BLOCKED** 记入风险日志，跳过后续非依赖 WP |
| 模拟器状态污染（权限/数据） | `adb -s emulator-5554 uninstall com.lioisme.lide` 后重装（冷启动语义，LIBS-006 经验） |

---

## 10. 风险管理

| ID | 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|---|
| R-001 | activity-compose 升级破坏 API（若走方案③） | 中 | 中 | 优先方案①/②；升级需全量回归抽屉 |
| R-002 | AXmlDecoder 无源码，manifest 补丁需自研 | 高 | 高 | 14-B 先行设计文档 + chunk 级单测，独立立项 |
| R-003 | v1-only 签名现代 Android 不可装 | 高 | 高 | 14-C 必须含 v2 Signing Block；不实现则不发布打包 |
| R-004 | layouthelper 平移工作量超估 | 中 | 高 | 三期满隔离，9-A 先验收预览闭环 |
| R-005 | x86_64 模拟器与 arm64 真机行为差异（W^X、native） | 中 | 中 | 引擎层改动（WP-002 引擎侧）双 ABI 各验证一次 |
| R-006 | 运行时权限请求打断运行流程（WP-003） | 中 | 低 | 拒绝后继续运行，与 pro 行为一致 |
| R-007 | READ_LOGS API 29+ 受限（WP-011） | 高 | 低 | 默认本进程日志，全量降级提示 |
| R-008 | `LocalActivity` 相关组件拆分引入 UI 回归 | 低 | 中 | WP-001 验收含抽屉回归清单（EDITOR_DRAWER_AUDIT_REPORT 五条） |

---

## 11. 附录 A：ADB / 构建命令速查

```powershell
# 构建（模拟器 x64 / 真机 arm64）
.\gradlew.bat :app:assembleX64Debug --console=plain
.\gradlew.bat :app:assembleArm64Debug --console=plain
.\gradlew.bat :app:compileArm64DebugKotlin --console=plain
.\gradlew.bat :app:testX64DebugUnitTest :editor:testDebugUnitTest --console=plain

# ADB
adb devices
adb -s emulator-5554 install -r app\build\outputs\apk\x64\debug\app-x64-debug.apk
adb -s emulator-5554 uninstall com.lioisme.lide
adb -s emulator-5554 push scripts\examples-src\demo /storage/emulated/0/AndroLua/project/
adb -s emulator-5554 pull /storage/emulated/0/AndroLua/project/demo/init.lua .
adb -s emulator-5554 shell am start -a android.intent.action.VIEW -d "file:///storage/emulated/0/AndroLua/project/demo/main.lua" com.lioisme.lide
adb -s emulator-5554 shell am start -n com.lioisme.lide/.engine.EngineActivity --es projectDir /storage/emulated/0/AndroLua/project/demo --es mainFile main.lua
adb -s emulator-5554 logcat -d | Select-String -Pattern "lide|luajava|Lua"
adb -s emulator-5554 exec-out screencap -p > %TEMP%\lide-verify\s.png
adb -s emulator-5554 shell pm grant com.lioisme.lide android.permission.CAMERA
adb -s emulator-5554 shell settings put system user_rotation 1   # 旋转（抽屉回归）
```

## 12. 附录 B：pro 参考文件映射

| WP | pro 参考文件 | 平移内容 |
|---|---|---|
| 002 | `assets/main.lua:1503-1533`（onActivityResult/错误解析） | 错误解析规则 |
| 003 | `assets/projectinfo/main.lua:85-101`、`resources/lua/permission.lua`、`bin.lua:324-329` | 权限写回/数据源/白名单 |
| 004 | `assets/projectinfo/main.lua`（145L）+ `layout.aly` | 字段表 + 表单逻辑 |
| 006 | `resources/lua/console.lua`（format/build/build_aly）、`assets/main.lua`（goto_func/goto_line/check） | 编译/格式化语义 |
| 007 | `assets/javaapi/fiximport.lua`（122L） | 词法匹配 + 类表 |
| 008 | `assets/javaapi/main.lua` + `android.lua`（4510L） | 类表数据 + 反射展示规则 |
| 009 | `assets/layouthelper/main.lua`（590L）+ `loadlayout2/3.lua` + `xml2table.lua` | 拖拽/属性表/序列化/转换器 |
| 010 | `assets/main.lua:568-569,824-859,1543-1552`（lua.conf/hist/proj） | 状态文件语义 → SharedPreferences |
| 011 | `assets/logcat.lua`（143L） | 过滤逻辑 |
| 012 | `assets/main.lua:1808-1851`（addNames/addPackage） | 补全数据表 |
| 014 | `resources/lua/bin.lua`（398L）+ `assets/keys/` | 打包全链 |
| 018 | `assets/help.lua`（1329L，@章节@ 格式） | 帮助内容资产 |

---

*本文档为 lide 与 AndroLua Pro 功能补全的执行基准；每 WP 完成情况按 §8 验收总则登记至 `PROGRESS.md`。*
