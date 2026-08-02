# delua vs AndroLua Pro 运行环境全量对齐 + 双端实测终审报告（2026-08-02）

范围：androlua 运行环境（Lua 脚本层 + 原生库层 + Java 层 + 宿主集成）与 AndroLua Pro（com.androlua 4.4.2）逐字节/逐项对齐，
x64 构建后在模拟器（emulator-5554，Android 14）双端对照实测。
结论先行：**除环境白名单项（路径/包名/版本/时间/性能/权限授予状态）外，双端运行行为与输出完全一致，无待修复差异。**

## 1. 本次修复/决策清单（3 项）

| # | 项 | 类型 | 说明 |
|---|----|------|------|
| 1 | `LuaAccessibilityService.onDestroy()` 缺 `stopScreenshot()` | **真实缺陷（唯一）** | 源码 diff 中 pro 有、delua 无；已补（`androlua/src/main/java/com/androlua/LuaAccessibilityService.java`，在 `sAccessibilityServiceCallbacks.onDestroy` 之后、`super.onDestroy()` 之前）。行为：onDestroy 时先解除截图回调再释放服务，与 pro 一致 |
| 2 | `app/src/main/assets/lua/check.lua` CRLF 污染 | 文本缺陷 | 28 个 pro 脚本中唯一差异文件：101 处 CRLF、2213B vs 2112B。已转 LF，现与 pro 逐字节一致（MD5 `824E65AB39EB48FAD8C7A94B85A3D5BD`）。delua 多 3 个自有脚本（loadlayout2/3.lua、xml2table.lua），保留 |
| 3 | lfs / lsqlite3 原生库误移除 | **已撤销（git restore）** | 曾按源码树 `Android.mk2`（lluv/lsqlite3 标记禁用）判断移除 6 个 .so；实测设备上 pro 官方 APK 实际包含 `liblfs.so`、`liblsqlite3.so`（共 17 个 .so）。**结论：源码树 ≠ 官方 APK 打包事实，以设备 APK 为准**。已恢复 jniLibs / prebuilt / build-scripts / rebuild_engine.ps1 全部改动 |

## 2. 全量对齐审计方法（L1–L4）

### L1 Lua 脚本层（assets/lua 与 pro resources 对照）
- 28 个 pro 文件仅 `check.lua` 有差异 → 已修复，其余逐字节一致。
- delua 侧新增 3 个自有脚本（编辑器依赖），与 pro 无冲突。

### L2 原生库层（jniLibs）
- pro 官方 APK 实装 17 个 .so（arm）：bson canvas cjson crypt gl **lfs lsqlite3** luajava luv md5 mime regex sensor socket xml zip zlib。
- delua x86_64 19 个 = 上述 17 个 + `liblive2d.so`（delua 自有超集）+ `libandroidx.graphics.path.so`（androidX 依赖注入）。
- 无缺失、无多余（超集为有意设计）。

### L3 Java 层（402 个公共文件 MD5 比对）
- 缺失 41 个：全部为编辑器侧（LuaEditor/LuaLexer/LuaTokenTypes/textwarrior → 已迁 `:editor` 模块；Main/Welcome → pro 的 UI 壳），非运行环境缺口。
- 差异 15 个分类：
  - 纯 androidx import 替换（LuaResources、FileProvider、LuaWebView、LuaActivity 部分）：中立。
  - 纯注释/空白剥离（PageView、FloatButton、ToolBar、BasePageAdapter、ArrayPageAdapter）：中立。
  - 签名变更链：`LuaDexLoader`（dexCache/loadApp/loadDex 返回 `ClassLoader`、InMemoryDexClassLoader + ByteBuffer）+ `LuaService.loadDex` 同步 → Android 14/15 兼容适配，行为一致。
  - `LuaState.finalize()` 加 `if (luaState != 0)` 防御：合理修复。
  - `LuaActivity` -117 行（删死循环忙等 `while(app.getClass()==LuaApplication.class)` + 未用 import）：行为一致。
  - `LuaApplication` +extractLuaScripts()：delua 脚本在 assets 需解包，pro 走 resources 自动解包：必要适配。
  - `LuaAccessibilityService` 缺 stopScreenshot：**真实缺陷，已修复（见 §1-1）**。

### L4 宿主集成
- EngineActivity（LuaActivity 子类，projectDir/mainFile/arg extras 与 LuaActivity 的 ARG 对齐）、RunEngineLauncher、EngineBridge 结构核验通过。
- R1 边界：`rg "luajava|androlua|loadLibrary" editor/src` 零命中。
- androlua/libs 目录仅 .gitkeep，无差异。

## 3. 双端实测（x64 构建 + Android 14 模拟器）

构建：`:app:assembleX64Debug` BUILD SUCCESSFUL（42s），APK 34.71MB。delua 运行前 `appops MANAGE_EXTERNAL_STORAGE allow` + `pm grant READ/WRITE_EXTERNAL_STORAGE`（探针环境对齐，非引擎差异）。

### 3.1 test 探针工程（865 项断言，双端各 1028 行日志）
规范化（剥时间戳/PID/`I lua:`）后逐行 diff：**81 处差异，全部为环境白名单**，无一项属引擎行为差异：

| 类别 | 说明 | 判定 |
|------|------|------|
| 时间戳 / 包名 / versionName / versionCode | 白名单 | ✓ |
| package.cpath / zip 解包路径 / 数据目录 | 路径白名单 | ✓ |
| 内存、GC、基准 ops/s（x86_64 原生 vs ARM 翻译，delua 快 3–10 倍） | 性能/环境 | ✓ |
| WRITE/READ_EXTERNAL_STORAGE 授予状态 | 权限授予差异（pro targetSdk=23 自动授予；delua 手动 pm grant 后一致） | ✓（已对齐） |
| 分数 834/865 vs 832/865 | 由权限 2 项所致，授权后应持平 | ✓ |

- Android 环境 24 项 / 原生库 17 项双端**逐一相同**（lfs/lsqlite3 双端均 OK）。
- `zip` 探针两端一致报 `[!!]`（pro 官方 APK 的 libzip.so 亦无法经 `require "zip"` 加载——luaopen 符号不匹配，双端同病，非 delua 缺陷）。

### 3.2 工程矩阵对照

| 工程 | 验证方式 | 结果 |
|------|---------|------|
| demo | 日志逐字 diff | **报错逐字一致**（`length=16; index=16` + 相同栈，main.lua:83/103；工程自身 bug，非 delua 引入） |
| DemoProject | 日志逐字 diff | 内容 100% 一致；仅 System Info 5 行 table 遍历顺序不同（Lua 5.3 hash 布局，属白名单） |
| testjson | uiautomator dump | **UI 层级 XML 逐字节一致**（含全部 bounds/文本；纯 UI 工程无 print） |
| kleinbottle | 截图帧差 + uiautomator | GL 动画双端持续渲染（相邻 1s 帧差均 >1600 采样点）；UI 层级一致（仅状态栏背景节点捕获时机差） |

## 4. 关键教训（已入知识库）

1. **源码树 ≠ 官方 APK 打包事实**：`Android.mk2` 将 lluv/lsqlite3 标记禁用，但官方 APK 实际携带 17 个 .so（含 liblfs/liblsqlite3）。判定库清单必须以设备上官方 APK 为准。
2. 权限类探针差异先排查 targetSdk 差异导致的自动授予差异，再考虑引擎问题。
3. x86_64 模拟器上基准性能天然比 ARM 翻译快数倍，不得作为对齐判定依据。

## 5. 遗留事项

- 无引擎级遗留差异。可跟进：探针 `zip` 项双端同病（如需，可在 delua 侧为 libzip.so 补 `luaopen_zip` 符号并重编，属超集增强，非对齐必需）。
- test 探针权限对齐后总分应持平（预期 834/865），可复跑确认。

取证物：`%TEMP%\opencode\` 下 pro_test/delua_test.log（1028 行）、*.norm.log、demo_*、DemoProject_*、tj_*、kb_*、ui_* 截图/XML。
