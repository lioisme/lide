# 模拟器定向全量回归测试报告（EMULATOR_REGRESSION_2026-08-07）

> 日期：2026-08-07
> 环境：x64 安卓模拟器 `emulator-5554`（Android 14 / API 34，x86_64 ABI）
> 构建：`.\gradlew.bat :app:assembleX64Debug`（BUILD SUCCESSFUL，`app-x64-debug.apk` 56 MB）
> 安装：`adb install -r`（Success）｜进程 `pid 27320` 全程无 FATAL / ANR
> 范围：**定向**（2026-08-07 及 8 月全月完工项）+ **全量**（启动→设置→编辑器→API 浏览器→示例→打包→签名→运行时）

---

## 0. 结论

23 项功能 + 性能检查全部 PASS，无回归。零 FATAL EXCEPTION、零 ANR、
零 `Input dispatching timed out`。核心链路（动态权限、级联菜单、API 浏览器搜索收起、
打包自装、packaged 产物独立运行）全部闭环。证据截图见 §8。

## 1. A 启动与基础

| # | 项 | 结果 | 证据 |
|---|---|---|---|
| A1 | 冷启动时间（`am start -W`） | ✅ | TotalTime **1110ms** / 复测 **1087ms**（稳定 ~1.1s） |
| A2 | 启动无崩溃 | ✅ | 首次启动后 `AndroidRuntime:E FATAL` 零命中 |
| A3 | 主界面结构 | ✅ | 工程列表 + 顶部「新建项目 / 设置 / 更多选项」，工程行含包名 |
| A4 | 会话级清扫 | ✅ | 全程 `pidof` 存活、无 ANR |

## 2. 设置与权限治理（SETTINGS_PERMISSIONS_REWORK 回归）

| # | 项 | 结果 | 证据 |
|---|---|---|---|
| B1 | 主设置页重组 | ✅ | 外观组（深色/主题/强调色）、编辑器、**AndroLua**（单入口 → API 与打包合并页）、**通用**（权限管理 / 签名独立入口） |
| B2 | 权限管理动态请求 | ✅ | 「特殊权限」清单逐类显示未授权/已授权；点「请求」弹 **系统悬浮窗许可页**（settings），授权后回写「悬浮窗=已授权」 |
| B3 | AndroLua 合并页 | ✅ | 工具（API 浏览器/示例）+ 打包 4 开关：**自动安装=开（默认）**、仅携带依赖原生库、**编译 Lua=开、加密 Lua=开**、默认 ABI →「x86_64」探测正确 |
| B4 | 签名入口 | ✅ | 通用 → 签名页「签名档案」三元组：内置 Android debug key / 生成新签名 / 导入密钥库 |

## 3. 编辑器（全语言 / 性能 V2 / 菜单）

| # | 项 | 结果 | 证据 |
|---|---|---|---|
| C1 | Lua 高亮 | ✅ | `hello` main.lua 渲染；像素取样 4+ 语法色（关键字橙 #CC7832 / 字符串黄 #FFC66D / 注释绿 #6A8759 / 文本灰 #CCCCCC） |
| C2 | 多语言高亮 | ✅ | `App.kt`（新建 MLProj 工程）打开后 5 种语法色（含 Kotlin 关键字蓝 #6897BB）；文件树全量列出 App.kt/Main.java/init.lua/main.lua |
| C3 | 右上角「更多」级联菜单 | ✅ | 一级分类（文件/工程/文本）→ 二级子菜单：文件→打开文件树/搜索；工程→工程信息/工程权限/构建 APK——Cascade 展开正常、无错位 |
| C4 | 大文件降级 | ✅ | 397 KB / 16000 行 `big.lua` 打开成功、Tab + 内容渲染、无崩溃、无 ANR |
| C5 | 打字响应 | ✅ | 注入 `localperftest123`，无 ANR / 无超时，App 存活 |

## 4. API 浏览器（搜索语义红线回归）

| # | 项 | 结果 | 证据 |
|---|---|---|---|
| D1 | 列表 + 分类 | ✅ | 预索引分类正确（构造控件/视图控件/工具类/接口…） |
| D2 | 过滤（后台） | ✅ | 检索 `FloatWindow` 过滤生效；无结果关键词显示「无匹配结果」而非卡死 |
| D3 | 搜索展开/收起（searchActive 红线） | ✅ | 点占位符展开（EditText [0,128][1440,352]），键入建议→点建议导航到详情页；**返回后搜索栏正常回落、无「收起→焦点回落→重新展开」死循环** |
| D4 | 详情页 Loaded 态 | ✅ | `class android.app.FloatWindow extends java.lang.Object` + 构建方法/公有 getter/setter 分组、成员倒序全列表 |

## 5. 示例 / 工程 / 运行时（E）

| # | 项 | 结果 | 证据 |
|---|---|---|---|
| E1 | 示例空态（零内置） | ✅ | 示例页显示 `examples_empty` 文案（精简模式），无内置 `.alp` |
| E2 | Lua 工程运行（Engine 链路） | ✅ | 编辑器「运行」→ `logcat I lua: hello: layout loaded`，Lua UI（Hello, lide! / Click Me）渲染；无 FATAL |
| E3 | 新工程扫描 | ✅ | 冷启动识别新建 `MLProj`（init/main/App.kt/Main.java 文件树全量列出） |

## 5. 打包 / 签名 / 运行时基座（F）

| # | 项 | 结果 | 证据 |
|---|---|---|---|
| F1 | 打包页载荷摘要 | ✅ | App=hello / 包名 com.lide.hello / 版本 1.0(1) / 权限 1 项；依赖=7 Lua 库 + 1 原生库（~355 kB）+ ABI x86_64；**宿主 IDE 剔除 89 项（~5.45 MB）**（HostPolicy 生效） |
| F2 | 默认编译+加密打包 | ✅ | 点「构建」→ 产物 `com.lide.hello-1.0.apk`（5.05 MB）落 `/storage/emulated/0/AndroLua/apk/` |
| F3 | 自动安装 | ✅ | 构建后触发系统「要更新此应用吗？」→「已安装应用。」（PackageInstaller 会话确认） |
| F4 | packaged 产物独立运行（P0 dex 替换） | ✅ | 打开产物 → 独立进程 `com.lide.hello` 运行 Lua（`hello: layout loaded`），无 FATAL —— **runtime classes.dex 替换 + LuaApplication 入口成立** |
| F5 | 签名页三档案 | ✅ | 内置 Android debug key 档案默认选中；生成新签名 / 导入密钥库入口可见 |

## 6. 性能（G）

| 指标 | 实测 |
|---|---|
| 冷启动 TotalTime（×2） | 1110ms / 1087ms（Emulator x86_64，debug 包） |
| 编辑器打开 397KB 大文件 | <3s 完成渲染、无阻塞；Tab 列表正常 |
| 内存（大文件打开态） | TOTAL PSS 104,963 KB（~105 MB） |
| 打字响应（adb 注入） | 1.5s 内完成、无 ANR |
| API 浏览器过滤 | 键入即反馈（后台线程 produceState） |

## 7. 过程观察（非缺陷，记录备查）

- 通过外部 push 向**正在编辑中的工程**添加文件时，已打开的文件树若未重开会保持旧快照；重开（LaunchedEffect(currentDir)）或新建工程后全量列出 → 行为正常，非 bug。
- `am start VIEW file://….kt -n …/.MainActivity` 仅回到工程列表、不自动打开文件（project 编辑器模式设计如此，未视为缺陷）。

## 8. 证据清单（`%TEMP%\opencode\regress\`）

| 文件 | 内容 |
|---|---|
| `01-main.png` | 工程列表主界面 |
| `02-settings.png` | 设置页（分组 + 权限/签名入口） |
| `03-overlay-granted.png` | 系统悬浮窗许可（已授权） |
| `04-examples-empty.png` | 示例空态（examples_empty） |
| `05-apibrowser.png` | API 浏览器列表（分类预索引） |
| `07-detail.png` | 详情页 Loaded（FloatWindow 反射成员） |
| `08-editor.png` / `10-kotlin-editor.png` | Lua / Kotlin 语法高亮 |
| `11-bigfile.png` | 397KB 大文件编辑器 |
| `12-run.png` | 工程运行（Lua UI 接管） |
| `13-buildapk.png` | 打包页载荷摘要 |
| `14-packaged-run.png` | packaged 产物独立运行 |
| `15-signing.png` | 签名档案页（三档） |

## 9. 后续建议

- 打包产物在 x86_64 的 APK 尺寸 5.05MB（原宿主 56MB debug）证明 dex 替换 + HostPolicy 剔除达成瘦身目标；后续可跑 `scripts/build-apks.ps1` 做 arm64 真机全矩阵。
- 新增工程运行时已验，建议接入「工程运行」性能时间（lua layout 加载耗时）逐步可观测。