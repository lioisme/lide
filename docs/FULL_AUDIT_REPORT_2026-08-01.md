# delua × AndroLua Pro 全量审计报告

- **审计日期**：2026-08-01
- **审计对象**：`delua`（`:app` + `:androlua` + `:editor` 三模块，Kotlin 宿主） vs `docs/reference/androlua/AndroLua_pro-master`（AndroLua Pro 4.4.2，Lua IDE + Java/原生运行时）
- **审计方法**：全量源码逐文件阅读（pro 侧 IDE Lua 页面 1913 行 main.lua + 6 个页面 + bin.lua 打包链；delua 侧全部 Kotlin/Java 宿主代码）、全仓库 grep 交叉验证、独立 worktree 编译验证、R1 边界门禁复验、真机日志（`docs/log/*.log`）与既有审计文档交叉核对
- **基准提交**：`36eac8c`（HEAD）

---

## 0. 执行摘要

### 结论一：引擎运行时层与 pro 已 100% 对齐，且为超集

- `com.luajava` 16 类、`com.androlua` 主体 42+ 类、`com.android.cglib.*` 全链（~280 文件）逐字节同源，编译进宿主主 DEX；
- 内置 Lua 脚本 31 个（pro 28 个，多 layouthelper 族 + mime 等）逐字节一致；原生模块 17 个 × 3 ABI（pro 15 个 × 1 ABI，delua 补 lfs/lsqlite3 + x86_64）；
- 引擎级实测对等：探针 878/956 vs pro 879/956（唯一差异 `require("socket")` 已定案为 delua 资产解包缺陷并修复，`ENGINE_LEVEL_COMPARISON_REPORT.md`）；
- `/libs` 全链路真机矩阵 V-01~V-10 全绿（`PROGRESS.md` WP-010）；
- W^X 约束下 dex 内存加载（`InMemoryDexClassLoader`）为**有意的、必要的**与 pro 差异。

### 结论二：宿主 IDE 功能对齐度约 6 成，差距集中在"创作/构建/诊断闭环"三块

pro 的 IDE 是一个 **Lua 实现的完整工作台**（编辑/查错/格式化/函数导航/导入分析/布局助手/日志/打包/权限编辑/帮助手册）。delua 的 Compose 宿主覆盖了**工程管理、编辑器、运行、插件、设置、文档**主干，但以下 pro 能力在 delua 中缺失或未闭环：

| 缺失类别 | 内容 | 影响 |
|---|---|---|
| **打包（最大缺口）** | bin.lua 等效能力：luac 编译、manifest 二进制补丁、权限白名单过滤、checklib/.using 依赖分析、签名安装 | v1.0 已决策暂不支持（有据可查），但"一键打包"是 pro 核心卖点 |
| **权限语义** | init.lua `user_permission` 写而不读；PermissionScreen 与工程声明完全脱节 | 工程权限声明形同虚设 |
| **运行诊断闭环** | pro 运行出错回传（路径:行号 跳转、`nil global` 建议 import）；delua 单向拉起、宿主零回传 | 出错定位靠人工 |
| **创作工具链** | 布局助手（可视拖拽）、Java API 浏览器、导入分析（fiximport）、函数导航、格式化、查错、单文件编译、日志查看器、符号键栏 | 高频创作特性缺失 |
| **文件管理** | 编辑器内不能新建/重命名/删除文件、无工程重命名、导入同名直接覆盖 | 基本文件操作缺口 |

### 结论三：工程状态发现 —— HEAD 可编译，但当前工作区处于不可编译的未提交半成品

- HEAD（36eac8c）在独立 worktree 验证 `:app:compileArm64DebugKotlin` **BUILD SUCCESSFUL**；
- 但工作区含未提交 WIP（`docs/EDITOR_DRAWER_AUDIT_REPORT.md` Phase 0~5 已完成、Phase 6 验证未执行）：
  1. `EditorScreen.kt` 引用**不存在的** `NavigationDrawerPanel`（:179）与 `EditorBody`（:191）两个 composable；
  2. 使用 `androidx.activity.compose.LocalActivity`（:8, :76），需 activity-compose ≥ 1.10，工程锁定 1.9.3；
  3. `material3-window-size-class` 依赖已加（`app/build.gradle.kts:116`）。
- 修复方向：补上两组件定义（或回退引用）、`LocalActivity.current` 改用 `LocalContext.current as Activity`、或升级 activity-compose。

### 结论四：工程化质量基线良好

- R1 边界零命中（`editor/src` 对 `luajava|androlua|loadLibrary` 零引用，含注释）；
- R2 单进程口径与实现一致（AGENTS.md/白皮书已统一）；
- i18n 对齐：app 86/86、editor 21/21（values vs values-zh）；
- release 启用 R8 + `verifyReleaseLuaSurface` 门禁（27 个 Lua 可见类防混淆回归）；
- 宿主代码零 TODO/FIXME；
- 自动化测试缺失（无 test/androidTest 源集），真机回归依赖人工探针（V 矩阵）。

---

## 1. 审计范围与方法

| 维度 | 说明 |
|---|---|
| pro 侧 | `assets/main.lua`（1913 行 IDE 主入口）、`project/`、`projectinfo/`、`plugin/`、`javaapi/`（含 android.lua 4510 行类表 + fiximport）、`layouthelper/`、`help.lua`（1329 行）、`layout.lua`、`logcat.lua`、`luadoc.lua`、`resources/lua/bin.lua`（398 行打包链）、`resources/lua/{console,permission,autotheme,loadmenu}.lua`、Java 运行时 100+ 类、manifest（36 权限 + 9 组件）、`assets/keys/` |
| delua 侧 | `:app` 全部 Kotlin（18 文件）+ EngineActivity.java + Manifest（50 权限）；`:androlua` 全部 Java + 4 个 Kotlin 管理类；`:editor` 10 个 Kotlin 文件 |
| 验证 | 独立 worktree 编译 HEAD；工作区编译复现；`user_permission`/`loadLibrary` 等关键符号全仓 grep；权限/组件/i18n 计数比对 |

---

## 2. 架构对照

```
AndroLua Pro（单层）                        delua（双层）
┌─────────────────────────────┐            ┌───────────────────────────────┐
│ Welcome → Main(LuaActivity) │            │ :app MainActivity(Compose)     │
│  IDE = Lua 工程（main.lua + │            │  IDE = Kotlin 原生（18 屏/器）  │
│  6 个子页面，同套运行时）     │            │  └ :editor 纯编辑器（sora+TM） │
│  textwarrior 编辑链          │            │  └ RunEngineLauncher           │
│  ├ 编辑器 LuaEditor          │            │       └ EngineBridge(intent)   │
│  └ 打包 bin.lua              │            │       └ EngineActivity         │
├─────────────────────────────┤            ├───────────────────────────────┤
│ 同进程 LuaActivity 栈        │            │ :androlua 运行时（同进程）       │
│ com.androlua/* + com.luajava│            │  与 pro 逐字节同源 + W^X 修复   │
│ + cglib + 原生 15 .so        │            │  原生 17 .so × 3 ABI           │
└─────────────────────────────┘            └───────────────────────────────┘
```

- pro 的"IDE = Lua 工程"与 delua 的"纯编辑器模块 + 宿主运行时"在架构意图上同构：pro 的 `assets/` ↔ delua 的宿主资源，pro 的 `files/` 运行时目录 ↔ delua 数据目录；
- 运行协议对齐：pro `newActivity(path, arg)`（Intent data=file:// + `ARG` extra + `{luadir, luapath}` varargs）↔ delua `EngineBridge.createEngineIntent(projectDir, mainFile, args)`（同一 `arg` extra 键，`LuaActivity.java:220` 消费）；
- 磁盘布局对齐：`/storage/emulated/0/AndroLua/{project,plugin}/` 与 pro 同路径；已按 WP-009 决策**回归纯"工程内自行管理"模型**（common/ 全局仓库已删）。

---

## 3. 引擎运行时层审计（对齐确认）

| 项 | pro | delua | 判定 |
|---|---|---|---|
| `com.luajava` 16 类 | 同源 | 逐字节一致（含 LuaState 1277L / LuaJavaAPI 1823L） | ✅ |
| `com.androlua` 主体 | 同源 | 逐字节（4 处有意修改：Xposed 反检测移除、icon 兜底、luaDir 可见性、annotation 换 androidx） | ✅ |
| `com.android.cglib` dx+proxy | 同源 | 完整一致（Enhancer/DexMaker 供 createProxy/loadDex 动态类） | ✅ |
| android.widget 19 控件 / android.app / nirenr / multidex | 同源 | 全量在册（pro 的 `android.support.annotation/v4.Fragment` 已以 androidx 等价替换，编译实证） | ✅ |
| 内置 Lua 脚本 | 28 文件 | 31 文件（28 逐字节 + loadlayout2/3、xml2table 超集） | ✅ 超集 |
| 原生模块 | 15 × armeabi-v7a | 17 × {arm64,armv7,x86_64}（+lfs/lsqlite3；lluv/tcc 有意不编，WHITEPAPER §7.1） | ✅ 超集 |
| LuaDexLoader 机制 | static dexCache(MD5) + 实例 libCache + loadResources | 同构 + API≥26 InMemoryDexClassLoader（W^X 必修，有意差异） | ✅ |
| `package.path/cpath`、libsloader、import 冒号语法、`initENV` 空环境、print 双重覆盖 | 同 | 逐字符同构 | ✅ |
| 脚本提取 | unApk(MD5+长度增量) | extractLuaScripts（递归 + MD5 比对，修复 socket 目录中断缺陷） | ✅ |
| 引擎级实测 | 探针 879/956 | 878/956 → 修复后预期持平 | ✅ |

**真机证据**：`docs/log/` 两份探针日志 1123 行比对，实质差异仅 `require("socket")` 一项（已定案修复）；`/libs` 矩阵 V-01~V-10（dex 自动加载/so 加载/LuaSocket/工程 lua 子目录/资产升级覆盖/corrupt dex 降级/classloader 稳定）全绿。

---

## 4. 宿主 IDE 功能逐项对照矩阵

### 4.1 工程管理

| 功能 | pro | delua | 判定 |
|---|---|---|---|
| 工程列表 | Lua 页扫描 init.lua（含版本/描述/图标） | Compose 列表（文件夹+工程名+包名） | ✅ 等价 |
| 新建 | 表单：名称+包名 → init.lua + main.lua + layout.aly 三件套 | 对话框单输入 → init.lua + main.lua（**无 layout.aly**） | ⚠️ 简化 |
| 删除 | 列表长按 + 确认 | 长按 + 确认对话框 | ✅ |
| 重命名 | —（pro 亦无，靠改目录名） | **无** | ✅ 对等 |
| 打开/文件浏览 | 内置文件浏览器（../ 上级、目录/文件分类、FastScroll、长按删除、跳过已编译 0x1b） | 抽屉文件树（只读 .lua 浏览 + 打开） | ⚠️ 减 |
| 最近文件 | lua.hist（50 条）+ 光标位置恢复 + 搜索框过滤 | **无跨会话历史**（EditorSession 会话内多标签） | ❌ 缺 |
| 状态恢复 | lua.conf（上次工程+光标）、lua.proj | 每次启动回工程列表页 | ❌ 缺 |
| .alp 导入 | getalpinfo 读元数据 → mode 分流（plugin/build/普通）→ 同名自动改名 | kind 分流（plugin/project）+ zip-slip 防护；**同名直接覆盖无确认** | ⚠️ |
| .alp 导出 | 普通复制 / mode 下 luac 编译 + `.using` 依赖清单 | 整目录 zip（无编译、无清单） | ⚠️ 简化 |
| 工程属性页 | projectinfo 全字段编辑（appname/appver/appcode/appsdk/path_pattern/packagename/theme/app_key/app_channel/developer/description/debugmode）+ 权限多选写回 init.lua | **无** | ❌ 缺 |
| 权限编辑 | 基于 APK requestedPermissions 清单 → user_permission 写回 | PermissionScreen 只管宿主运行时权限，**不读不写工程声明** | ❌ 缺 |
| 单文件收养 | 直接拉起 LuaActivity 运行（file://*.lua） | 收养为单文件工程再编辑（合理差异） | ✅ |

### 4.2 插件

| 功能 | pro | delua | 判定 |
|---|---|---|---|
| 扫描/元数据 | init.lua pcall | plugin.json 优先 + init.lua fallback | ✅ 超集 |
| 运行入口 | 恒 main.lua + `{luadir, luapath}` 上下文 | main.lua（已修正）+ 同上下文（DeluaNavHost 传参） | ✅ |
| 安装 | .alp mode=plugin 分流导入 | PluginScreen "+" 安装 + mode 分流 | ✅ |
| 卸载/禁用 | 无 | **无** | ✅ 对等 |
| 示例插件 | — | assets 提取 example 插件 | ➕ |

### 4.3 编辑器

| 功能 | pro（textwarrior） | delua（sora-editor + TextMate） | 判定 |
|---|---|---|---|
| 语法高亮/自动缩进/撤销重做/查找 | ✅ | ✅（含替换/全部替换/正则/大小写，优于 pro） | ✅ |
| 行级词法诊断 | 无（只编译时查错） | 纯 Kotlin 增量词法诊断（括号/块/长串）+ 500ms 防抖 + 大文件降级 | ➕ |
| 查错（编译） | func.check → luac | **无预编译查错按钮**（诊断只到词法层，表达式级由运行时兜底） | ⚠️ |
| 格式化 | console.format | **无** | ❌ |
| 单文件编译 luac | console.build | **无** | ❌ |
| 函数导航 / 转到行 | goto_func / goto_line | **无** | ❌ |
| 自动补全 | 15 生命周期方法 + javaapi 类表 + activity 方法包 | 22 关键字 + 10 snippet，**无 Java API/成员补全** | ⚠️ 弱 |
| 导入分析 fiximport | LuaLexer 分析 + import 建议 + 错误自动建议 | **无** | ❌ |
| Java API 浏览器 | 反射类/事件/getter/setter/字段/方法 + 复制 | **无** | ❌ |
| 布局助手 | 可视拖拽 + 属性表 + XML→布局表转换器 | **无**（.aly 模板资产已提取至 filesDir/layouts 但零消费入口） | ❌ |
| 符号键栏 | ps_bar 32 键 | **无** | ❌ |
| 快捷键 | Ctrl+O/P/S/E/R/N/U/I | 仅 Ctrl+F | ⚠️ |
| 大文件策略 | — | 200KB 阈值降级（wrap/括号高亮/诊断/undo 栈） | ➕ |
| 文件操作 | 浏览器内新建/删除 | 编辑器内**不能新建/重命名/删除文件**、不能新建目录 | ❌ |
| 运行目标 | 当前编辑文件或工程 main | **固定 main.lua**（EditorScreen.kt:170，与当前标签无关） | ⚠️ |

### 4.4 运行链路与诊断闭环

| 项 | pro | delua | 判定 |
|---|---|---|---|
| 运行前 | func.check 语法预检 + 保存 | 仅 flush 当前标签 + 直启 | ⚠️ |
| 拉起协议 | newActivity（file:// + ARG） | EngineBridge（同 ARG extra + 转场动画） | ✅ |
| 结果回传 | onActivityResult 解析错误 → 跳行 + nil global 建议 import | **单向拉起，零回传**（引擎页内置控制台展示） | ❌ |
| 控制台 | 引擎内置 | 引擎内置 | ✅ |
| 日志查看器 | logcat 多级过滤 | **无** | ❌ |
| 脚本拉起 | androlua:// 目录/脚本运行 | androlua:// 双 host（com.lioisme.delua + com.androlua）兼容 | ✅ |

### 4.5 设置 / 主题 / 文档

| 项 | pro | delua | 判定 |
|---|---|---|---|
| 外观 | 自动日夜（autotheme 22:00-6:00）+ 编辑器配色扩展点 | SYSTEM/LIGHT/DARK + 壁纸动态色 + 自定义种子色 | ✅ 更强 |
| 字体/字号/换行 | 编辑器配色可编程（fonts/day.lua） | 字号/Tab 宽度 **key 存在但无 UI 入口**；换行/空白有 UI | ⚠️ |
| 语言 | — | en/zh/system 运行时切换（recreate） | ➕ |
| 离线文档 | help.lua 32 节 + luadoc WebView | luadoc WebView ✅；**帮助手册缺失** | ⚠️ |
| 关于/捐赠 | 关于 + 支付宝 + QQ 群 | 关于 → luadoc | ⚠️ |

### 4.6 打包（pro 最大单项能力）

| 环节 | pro bin.lua | delua |
|---|---|---|
| luac 字节码编译 | ✅ | ❌ |
| 以宿主 APK 为模板重打包 + AXmlDecoder manifest 二进制补丁（包名/label/版本/权限白名单置空/versionCode） | ✅ | ❌ |
| checklib：require/import 正则分析 + `.using` 清单 → 内置 .so/.lua 自动去重 | ✅ | ❌ |
| 图标/启动图注入、签名（testkey/platform）、安装 | ✅ | ❌（assets/keys 未入仓） |

> v1.0 已书面决策暂不支持（`WHITEPAPER.md` 阶段四）：mao/sign 签名 jar 不在仓库、v1-only 签名现代 Android 不可装、宿主模板体积不可控、优先级低。**结论成立**，但该能力仍应作为版本路线图最高优先项（平移 luac/checklib/manifest 三机制即可）。

---

## 5. 差异分级清单（P0 → P2）

### P0 —— 必须处理

| # | 项 | 证据 | 建议 |
|---|---|---|---|
| 0-1 | **工作区不可编译**（WIP 抽屉重构半成品） | EditorScreen.kt:8,76 `LocalActivity`（activity-compose 1.9.3 无此 API）；:179 `NavigationDrawerPanel`、:191 `EditorBody` 无定义；Phase 6 验证未执行（EDITOR_DRAWER_AUDIT_REPORT.md） | 补齐两组件或回退引用；`LocalActivity` 换 `LocalContext.current as Activity` 或升 activity-compose ≥1.10；完成后跑 Phase 6 清单 |
| 0-2 | **user_permission 写而不读** | ProjectManager.kt:144、MainActivity.kt:154 写入；全仓（androlua Java + app Kotlin）零消费 | 至少：运行前按工程声明批量请求运行时权限（pro 行为）；为打包预留读取点 |
| 0-3 | **运行诊断闭环缺失** | EngineBridge 单向 startActivity，无任何结果通道 | 引擎侧错误/退出码经 ActivityResult 或文件协议回传，宿主展示并可跳行（pro onActivityResult 语义） |

### P1 —— 近期补齐（对齐 pro 高频创作特性）

| # | 项 |
|---|---|
| 1-1 | 工程属性页（pro projectinfo 全字段 + 权限多选写回 init.lua） |
| 1-2 | 编辑器文件操作（新建/重命名/删除文件、新建目录）与工程重命名 |
| 1-3 | 查错（luac 预编译）、格式化、函数导航、gotoLine |
| 1-4 | 导入分析 fiximport（LuaLexer 词法 + import 建议），错误信息 → 建议 import |
| 1-5 | Java API 浏览器（android.lua 4510 行类表已在 pro 参考可平移） |
| 1-6 | 布局助手（layouthelper 可视化；.aly 模板资产已就位，接 UI 入口 + 预览） |
| 1-7 | 最近文件/继续上次（跨会话状态持久化，对齐 lua.conf/hist） |
| 1-8 | 日志查看器（logcat 过滤） |
| 1-9 | 补全增强：生命周期方法 + 常用成员（对齐 addPackage 语义） |
| 1-10 | 运行入口跟随当前标签（或明确的"运行 main.lua"UI 语义） |

### P2 —— 可选 / 工程化

| # | 项 |
|---|---|
| 2-1 | 独立 APK 打包（平移 bin.lua 三机制；含 assets/keys 入仓） |
| 2-2 | .alp 导入同名覆盖加确认（AlpManager.kt:102-106）；插件卸载入口 |
| 2-3 | PermissionScreen 单项/分组请求；SYSTEM_ALERT_WINDOW/WRITE_SETTINGS 等特殊权限入口 |
| 2-4 | init.lua 元数据解析结构化（正则命中注释问题，ProjectManager.kt:43-48 / PluginManager.fieldOf） |
| 2-5 | `adoptStandaloneFile` 生成的 init.lua 仍含 `dependencies={}` 死块（MainActivity.kt:155-158，引擎不消费），与 WP-010 模板口径不一致，建议删除 |
| 2-6 | 字号/Tab 宽度设置 UI 入口；帮助手册页 |
| 2-7 | 自动化测试骨架（当前零测试源集；引擎回归依赖真机探针矩阵） |
| 2-8 | 快捷键扩展（Ctrl+S 保存等） |

---

## 6. 工程化与质量审计

| 项 | 结果 |
|---|---|
| HEAD 编译（独立 worktree） | ✅ `:app:compileArm64DebugKotlin` BUILD SUCCESSFUL（43s） |
| 工作区编译 | ❌ 失败（WIP 半成品，见 P0-1） |
| R1 边界 | ✅ `editor/src` 对 `luajava\|androlua\|loadLibrary` 零命中 |
| 原生库加载点 | 仅 `LuaState.java:106` 一处 `System.loadLibrary`（运行时，合规） |
| 单进程口径 | ✅ AGENTS.md R2 = EngineBridge → EngineActivity 同进程，与实现一致 |
| i18n | ✅ app 86/86、editor 21/21（values vs values-zh 全对齐） |
| R8 门禁 | ✅ release 启用 + verifyReleaseLuaSurface（27 类） |
| TODO/FIXME | ✅ 宿主侧零残留（androlua 上游 Java 存根 ~100 处属 pro 同源遗留） |
| 权限声明 | 50 vs pro 36（超集，含宿主所需 MANAGE_EXTERNAL_STORAGE 等） |
| 测试 | ❌ 无 test/androidTest 源集 |

---

## 7. 审计局限

- 未做真机 UI 回归（本机无设备）；引擎能力引用既有 V 矩阵与探针日志；
- `:editor` 的 sora-editor 内部行为（补全弹窗、TextMate 渲染）未逐 API 验证，仅代码级审阅；
- pro 的 `mao/sign` 签名 jar 不在参考仓库，打包链无法在本地复现验证（与官方结论一致）。

---

## 8. 建议路线

1. **立即**：修复 P0-1 工作区编译（补组件/改 API/升依赖），跑 Phase 6 验证清单并提交；提交后重跑 `:app:assembleArm64Debug` 全量构建。
2. **短期**：P0-2/P0-3（权限语义 + 运行诊断闭环）——这两项直接决定"运行工程"体验与 pro 的差距感知。
3. **中期**：P1 创作工具链分批落地（属性页 → 文件操作 → 查错/格式化/导航 → fiximport/Java API → 布局助手）；复用 pro 参考源码平移（android.lua 类表、layouthelper 逻辑、console.lua 编译链均可在 :editor/:app 以 Kotlin 重写）。
4. **长期**：P2 打包能力（阶段四路线图）+ 自动化测试 + 真机回归矩阵常态化。

---

*本报告为 delua 全量审计结论；后续执行记录请登记于 `PROGRESS.md`。*
