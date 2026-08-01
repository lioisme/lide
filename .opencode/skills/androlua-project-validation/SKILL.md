---
name: androlua-project-validation
description: Use when developing, importing, or validating AndroLua Lua 工程 (docs/reference/project/*, 工程 libs/*.dex) against BOTH AndroLua Pro 原版 (com.androlua) and delua (com.lioisme.delua) on the x64 Android emulator via ADB. Trigger keywords: androlua 工程测试, 双端验证, pro/delua 对比, 行为不一致, 报错不一致, .alp 打包, 工程导入, libs 编译, 知识库落库, androlua-lua. Use ONLY when the task involves running/validating an AndroLua Lua project, NOT for editing delua source code without device verification.
---

# AndroLua 工程双端一致性验证（AndroLua Pro 原版 → delua）

## 0. 铁律（评审硬门槛）

1. **先 pro 后 delua**：开发/移植任何 Lua 工程，必须先保证在 AndroLua Pro 原版（`com.androlua`）中正常运行；pro 正常运行（含打包 `.alp` 后运行）之后，才允许进入 delua 验证。
2. **行为必须一模一样**：同一工程、同一环境、同一输入，delua 的输出（UI、日志、报错、栈、截图）必须与 pro 逐字一致。**连报错都应该一样**，仅允许环境/路径/包名类差异（见 §6 白名单）。
3. **修 delua，不修工程**：一旦发现不一致，根因应定位在 delua 侧（原生库、Java 库、AndroLua 引擎、宿主集成、manifest），修复 delua；**禁止**为掩盖 delua 缺陷而改动测试工程。工程自身 bug 不是差异——它必须在两端同样报错。
4. 按 §5 分层排查，只允许收敛到白名单（§6）后判定"一致"。
5. **知识必须落库**：每轮开发/验证中发现的一切**特性、坑、建议、方法、代码写法、环境事实**，收尾前必须持久化到知识库 `docs/kb/androlua-lua/`（多文档，见 §9）；不得只留在对话里。

## 1. 环境与关键路径

| 项 | 值 |
|---|---|
| 设备 | 本机 x64 架构安卓虚拟机，ADB 默认 `emulator-5554`（先 `adb devices` 确认 `device` 状态） |
| AndroLua Pro 原版 | 包名 `com.androlua`（4.4.2），源码 `docs/reference/androlua/AndroLua_pro-master/`，运行时脚本 `app/src/main/resources/lua/` |
| delua | 包名 `com.lioisme.delua`，宿主经 `EngineActivity` 转发；运行时脚本 `app/src/main/assets/lua/`（与 pro 逐字节一致）；引擎模块 `:androlua`、宿主 `:app`、编辑器 `:editor` |
| 设备工程根 | `/storage/emulated/0/AndroLua/project/<name>/`（`init.lua` + `main.lua` + 可选 `libs/`） |
| 参考工程（pro 已验） | `docs/reference/project/`：demo、test、testjson、kleinbottle、DemoProject、probe、builddemo、「Sora editor」、简、石头、看板娘、雷达统计图 等 |
| 库源码（可编译 .dex 进工程 libs/） | `docs/reference/libs/`：BaiduMTJ、SmartRefreshLayout、umeng 等 |
| 回归脚本 | `scripts/adb_regress.ps1 [-Device emulator-5554] [-SkipBuild]`（构建 x64 → 安装 → 推 4 个标准工程 → 逐个运行 → 日志/前台/截图判定，6 项全 PASS） |
| 日志基线 | `docs/log/androluapro.log`、`docs/log/delua.log`（同一 test 工程 956 项 probe 输出）；临时对比日志存 `%TEMP%\opencode\` |
| 双端运行时一致性报告 | `docs/completed/ANDROLUA_FEATURE_PARITY_AUDIT.md`、`ANDROLUA_PARITY_FULL_AUDIT_REPORT.md`、`EMULATOR_RUNTIME_AUDIT_REPORT.md`、`ENGINE_LEVEL_COMPARISON_REPORT.md` |

构建命令（AGENTS.md）：编译检查 `.\gradlew.bat :app:compileArm64DebugKotlin --console=plain`；完整构建 `.\gradlew.bat :app:assembleArm64Debug --console=plain`；模拟器用 x64：`.\gradlew.bat :app:assembleX64Debug --console=plain`。

## 2. 双端启动（已验证命令）

```powershell
# 推工程（<name> 目标名）：
adb shell mkdir -p /storage/emulated/0/AndroLua/project/<name>
adb push docs\reference\project\<name> /storage/emulated/0/AndroLua/project/<name>

# pro 原版（-n 指定组件，绕过 ResolverActivity 选择器）：
adb shell am start -a android.intent.action.VIEW -d "file:///storage/emulated/0/AndroLua/project/<name>/main.lua" -n com.androlua/com.androlua.LuaActivity

# delua（同一 intent，组件指向 delua 的 LuaActivity）：
adb shell am start -a android.intent.action.VIEW -d "file:///storage/emulated/0/AndroLua/project/<name>/main.lua" -n com.lioisme.delua/com.androlua.LuaActivity
```

- 注意：不带 `-n` 的裸 file:// VIEW 会进 ResolverActivity；`androlua://` scheme 只进各自主程序 UI（需手工点工程行 → 点「运行」），仅作 UI 路径抽查。
- 每次启动前 `adb shell am force-stop com.androlua`（或 delua）清状态；抓日志前 `adb logcat -c`。

## 3. 标准流程（Phase 0–6）

**Phase 0 前置**：`adb devices` 确认设备；确认两端 APK 已装（`adb shell pm list packages` 应含 `com.androlua` 与 `com.lioisme.delua`）；确认设备工程目录存在。
**Phase 0 读知识库（先读后用）**：开发/验证开始前，先读 `docs/kb/androlua-lua/` 对应文档——`PITFALLS.md`（已知坑，先规避）、`FEATURES.md`（能力边界，别依赖 delua 无的 API）、`TECHNIQUES.md`（可复用方法与代码写法）、`ENVIRONMENT.md`（命令与路径）；与本轮相关的条目直接引用复用。

**Phase 1 pro 验证**：推工程 → 启动 pro → `Start-Sleep 5~8s` → 抓取：
- `adb logcat -d -s lua:* AndroidRuntime:E`（引擎错误通道，pro 的关键字 `I lua:`）
- `adb exec-out screencap -p > verify\<name>-pro.png`（UI 证据）
- 判定：无 `FATAL EXCEPTION`、无非预期 `Runtime error`；有 print 的工程核对日志内容。pro 不通过 → **工程问题，返回改工程，不进 delua**。
- 工程自带 `libs/*.jar|*.dex|*.so` 时：在 pro 下必须验证 `import "xxx:com.foo"` / `require` 生效。
- 若工程可打包：在 pro 中执行 `.alp` 打包并运行打包产物通过后，才进入 Phase 3（打包验证仅对交付型工程必需，可跳过并注明）。

**Phase 2 delua 验证**：同样的工程文件、同样的启动方式 → 抓同样两组证据（日志 + 截图）。

**Phase 3 逐字对比**：
- 日志：两端 logcat 输出逐字 diff；**只允许** §6 白名单差异。
- 截图：对比 UI 布局关键帧（可 `adb shell uiautomator dump` 输出 UI 树辅助比对）。
- 报错：错误消息、`stack traceback`、类名、行号必须一致（行号允许与白名单 §6.2 匹配的固定偏移）。

**Phase 4 白名单核对**：把发现的每一处差异逐一对照 §6；不在白名单 → 判 FAIL，进入 Phase 5。

**Phase 5 修复 delua**：按 §5 定位并修复，重新构建 → 安装 → 回到 Phase 2 全量复测。

**Phase 6 归档**：修复/验证完成必须归档：
- 新审计报告写入 `docs/completed/`，运行日志存 `%TEMP%\opencode\` 并注明路径；更新 `docs/kb/*` 与 PROGRESS.md 对应条目。
- **知识库落库**：本轮发现的特性/坑/建议/方法/代码写法/环境变化按 §9 写入 `docs/kb/androlua-lua/` 对应文档，并在其 README 变更登记追加一行；无新发现也要复核一遍。

## 4. 对比取证命令速查

```powershell
adb logcat -c                                          # 清日志
adb logcat -d -s lua:* AndroidRuntime:E               # 引擎错误 + 崩溃
adb logcat -d > verify\<name>-<side>.log               # 全量日志
adb exec-out screencap -p > verify\<name>-<side>.png   # 截图
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
adb shell pidof com.androlua / com.lioisme.delua      # 进程存活
adb shell "dumpsys activity activities" | Select-String topResumedActivity
```

## 5. 不一致分层排查（自上而下，逐层收敛）

> 原则：先排除工程/环境因素，再按"运行时脚本 → 原生库 → Java 绑定 → 宿主集成"四层向内排查。每层给出具体比对手段。

| 层 | 差异可能来源 | 比对手段 |
|---|---|---|
| L0 工程与执行环境 | 设备上工程文件不一致（推错目录/旧文件）、权限差异、网络/时间/文件系统副作用 | 两端 `adb shell ls -R` + `md5sum` 工程目录；重推工程再测；白名单 §6.1 |
| L1 Lua 运行时脚本 | `import.lua`、`loadlayout.lua`、`json.lua`、`socket/*` 等 28 个脚本不一致 | `fc /b app\src\main\assets\lua\* docs\reference\androlua\AndroLua_pro-master\app\src\main\resources\lua\*`（应零差异） |
| L2 原生库 `.so` | 缺库、ABI 不对、版本不一致 | 比对 `app/src/main/jniLibs/` 与 pro `Android.mk` 构建集（15 个：bson/canvas/cjson/crypt/gl/luajava/luv/md5/mime/regex/sensor/socket/xml/zip/zlib）；`adb shell pm path` + `ls lib/arm64` 核对设备端装载；diff hash 同版本库 |
| L3 Java 绑定 | `com.luajava`（16 类）、`com.androlua` 主体（42+）、`com.android.cglib` 链差异；`LuaDexLoader`/`LuaApplication`/`LuaActivity` 有意改动点（见 §6.2） | `git diff --no-index` 逐文件比对 pro 源码与 `androlua/src/main/`；检查改动点是否落入 §6.2 |
| L4 宿主集成（delua 特有） | manifest（Activity 注册、intent-filter、权限、cleartext、targetSdk 行为差）、`EngineActivity` 转发、资源解析（icon）、`LuaApplication` 脚本解包、`LuaDexLoader` 降级路径 | 对照 `docs/completed/EMULATOR_RUNTIME_AUDIT_REPORT.md` 既往缺陷与修复（LuaActivity 未注册 → ActivityNotFoundException；targetSdk 明文 HTTP → usesCleartextTraffic）；检查 `engine/` 宿主层对 LuaActivity/运行时脚本的解包与传参 |
| L5 语言运行时语义 | `tointeger` 等全局函数、math 库差异 | 用探针工程（`docs/reference/project/probe`）双端输出对比；两端引擎均同源（`libluajava.so`），如仍不同再向上追 L2/L4 |

**修复落点约定（AGENTS.md 架构边界 R1–R4）**：运行时/引擎缺陷改 `:androlua` 或 `:app` 宿主（EngineActivity/EngineBridge/manifest）；`:editor` 禁止触碰 `com.androlua`/`com.luajava`/`loadLibrary`（验证：`rg -n "luajava|androlua|loadLibrary" editor/src` 零命中）。

## 6. 已知合理差异白名单（出现在此清单 = 不算不一致，禁止"修"）

1. **环境/路径/包名**：日志中的 `/storage/emulated/0/AndroLua/...` 路径相同，但宿主应用名/包名（`com.lioisme.delua` vs `com.androlua`）、数据目录、版本号、图标可不同；`package.path` 中的应用目录类名可不同。
2. **固定代码偏移**：delua 有意改动文件产生的行号偏移（如 `LuaDexLoader.loadApp/loadDex/loadLibs` 差 +4 行）；错误文本本身必须逐字一致。
3. **宿主包装帧**：栈顶/栈中出现的 `com.lioisme.delua.engine.EngineActivity.onCreate` 等宿主转发帧（架构必然产物，不进入脚本行为）。
4. **delua 自创宿主日志**：`DeluaDebug` 等 delua 专属 tag 输出行（pro 无对应行，属宿主，不影响 Lua 行为）。
5. **pro 有而 delua 有意移除/替换**（判定"缺 API"前必查）：Xposed 反检测与 `onHook`；`com.nirenr.*` 截图/找色自动化 API（`LuaAccessibilityService` 的 screenshot/click 族）；pro 自带编辑器类（`com.androlua.LuaEditor`、textwarrior 33 类）→ 被 sora-editor 替代；`android.widget.CardView/DrawerLayout/ToolBar` 等 24 个扩展控件、`FloatWindow/AlertDialogBuilder`、multidex 支持类。工程若依赖这些，属"pro 独有能力"而非 delua 缺陷，需在报告中按 §5 L3/L4 结论区记载。
6. **`.alp` 打包产物差异**：打包后运行行为一致即可，产物文件 hash 无一致要求。

## 7. 工程 libs 机制（`docs/reference/libs/` 使用法）

- 工程库 = 放进 `<project>/libs/` 的 `*.jar` / `*.dex` / `lib<fn>.so`，`LuaDexLoader` 启动时加载，`import "xxx:com.foo"` 可用，`.alp` 导出即携带（详见 `docs/completed/LIBS_SUPPORT_WORK_ORDER.md`）。
- `docs/reference/libs/` 下库源码（BaiduMTJ、SmartRefreshLayout、umeng 等）可用 `d8`/`dx` 编译为 `.dex` 放入工程 `libs/` 测试；双端验证时两端用**同一份** dex 文件（先 `md5sum` 确认）。
- 已知降级路径：`libs/` 中 dex 无法按包名解析时两端都打印 `NameNotFoundException` 后走字节加载降级——该打印在两端必须逐字一致（含方法行号，仅允许 §6.2 偏移）。

## 8. 产出物要求

- 每轮验证至少产出：双端日志存档（`%TEMP%\opencode\`）、截图、结论表（工程 × 结果 × 证据）。
- 修复类结论必须写 `docs/completed/` 报告：现象、根因定位（落到 L0–L5 层）、修复文件、验证命令存档、白名单对照。
- 回归可复跑 `scripts/adb_regress.ps1`（6 项矩阵，含 demo/DemoProject/kleinbottle/testjson）。

## 9. 知识库（`docs/kb/androlua-lua/`，存放知识 + 使用知识）

> 本 SKILL 的唯一知识存取点：**每轮开始先读它（Phase 0），每轮结束写它（Phase 6）**。
> 与 `docs/kb/`（delua 工程知识库）区分：本库只持久化 **Lua 工程侧**开发知识。

| 文件 | 记录什么 |
|------|---------|
| `README.md` | 索引 + 维护约定 + 变更登记（每轮追加一行：日期 + 主题 + 文件） |
| `ENVIRONMENT.md` | 环境事实：模拟器、双端包名/路径、ADB 命令、构建（环境变更时更新） |
| `FEATURES.md` | 特性/API 边界：import/require 体系、15 原生库、loadlayout/控件、线程定时器、libs 机制、pro 独有缺失能力 |
| `PITFALLS.md` | 坑：现象 → 根因 → 规避/判定 → 验证命令；双端实证标 `✅双端一致`，单端标 `⚠仅pro/⚠仅delua` |
| `TECHNIQUES.md` | 方法/建议/代码写法：工程骨架、LuaAdapter、探针、libs 编译 .dex、诊断命令、建议清单 |

维护约定：
1. **每轮开发结束必须落库**（铁律 5，Phase 6 强制）：新发现按主题写入对应文档，并在 README 变更登记追加一行。
2. 条目格式：现象/事实 → 根因 → 规避或判定方法 → 验证命令；注明来源（实测/参考工程/审计报告）；可附最小代码片段。
3. 只记录已确认事实：双端实测标 `✅双端一致`；单端标 `⚠`；推测标 `未确认` 并尽快验证补正。
4. 已废弃/已修复的条目标注状态、保留历史，不删除证据链。
5. 参考工程 `docs/reference/project/` 与库源码 `docs/reference/libs/` 是活样本，引用时写相对路径。

## 10. 相关文档

- `docs/completed/EMULATOR_RUNTIME_AUDIT_REPORT.md` — 既往实测审计 + 已修复 manifest 缺陷（必读，含已知限制 §5）
- `docs/completed/ANDROLUA_FEATURE_PARITY_AUDIT.md` / `ANDROLUA_PARITY_FULL_AUDIT_REPORT.md` — 分层逐字节比对基准
- `docs/completed/ENGINE_LEVEL_COMPARISON_REPORT.md` — 956 项 probe 双端日志基线
- `docs/kb/ARCHITECTURE.md`、`docs/kb/ENGINE_RUNTIME.md` — 模块边界与运行时职责
- `scripts/adb_regress.ps1` — 双端回归脚本（pro 侧未纳入矩阵，需按 §3 手工补）
- `docs/kb/androlua-lua/` — Lua 工程侧知识库（§9）：特性/坑/方法/环境
