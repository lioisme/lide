# 能力清单：API 与能力边界（Capability Inventory）

> 记录可用 API 与机制、以及 pro 有而 delua 无的能力边界。
> 依据：`docs/completed/ANDROLUA_FEATURE_PARITY_AUDIT.md`、`ANDROLUA_PARITY_FULL_AUDIT_REPORT.md`、双端实测。

## 1. 工程骨架能力（✅双端一致）

- `init.lua` 元数据：`appname` / `appver` / `appcode` / `appsdk` / `packagename` / `developer` / `description` / `debugmode` / `user_permission`。
- 依赖声明示例（demo）：`dependencies = { modules = {"dkjson"} }`。

## 2. import / require 体系（✅双端一致）

- 入口固定 `require "import"`。
- `import "android.widget.*"` 等通配导入；`import "dkjson"` 导 Lua 模块。
- `import "xxx:com.foo"` → 加载工程 `libs/` 中的 dex/jar 并绑定类（`xxx` 为库前缀）。
- `package.path` = 工程目录 `?.lua;lua/?.lua;?/init.lua` + 应用运行时脚本目录；工程内任意 .lua 可 `require`。
- 系统 Cpath 内置 15 个原生 `.so`，`require "socket"` 等直接 loadlib。

## 3. 原生库模块（✅双端一致，15 个）

bson、canvas、cjson、crypt、gl、luajava、luv、md5、mime、regex、sensor、socket、xml、zip、zlib
（pro 的 lfs/lsqlite3/lluv/tcc 为构建禁用态，双端都没有）。

## 4. UI 与控件（✅双端一致）

- `loadlayout`：布局表 → View；`activity.setContentView(loadlayout(layout))`。
- `LuaAdapter(activity, itemLayout)` 绑定列表；`LuaMultiAdapter` / `LuaExAdapter` / `LuaExpandableListAdapter` / `LuaArrayAdapter` 可用。
- `LuaDialog` / `LuaWebView` / `LuaView` / `LuaCameraView` / `LuaFragment` / `LuaBroadcastReceiver` / `LuaContentObserver` / `LuaFileObserver` / `LuaClient` / `LuaServer` / `LuaBitmap` 等可用。

## 5. 并发与生命周期（✅双端一致）

- `thread` / `task` / `timer`（`LuaThread` / `LuaAsyncTask` / `LuaTimer`）+ `LuaTimerTask` / `LuaRunnable`。
- `activity` 全局即 LuaActivity 代理：`activity.setContentView`、`activity.newActivity`、资源访问等。

## 6. 运行时全局（✅双端一致，探针实证）

- 全局函数 `tointeger` 存在（pro 魔改 libbaselib）；`math.tointeger == nil` —— 用全局的，别用 math 的。
- 引擎为 Lua 5.3.3 深度魔改（9 项语法扩展），**字节码与标准 Lua 5.3.3 不兼容**。

## 7. 工程内 libs 机制（✅双端一致）

- `<project>/libs/*.jar|*.dex` → `LuaDexLoader` 启动加载，MD5 缓存；`lib<fn>.so` → `package.loadlib`。
- dex 按包名解析失败时双端一致降级：打印 `NameNotFoundException` 后走 `InMemoryDexClassLoader` 字节加载（见 PITFALLS §2）。
- `.alp`（zip）导出/导入即携带 libs，无注册表概念。

## 8. 宿主应用功能（delua 独有）

| 功能 | 说明 | 位置 |
|---|---|---|
| 工程管理 | 工程列表（含包名/版本）、新建、导入 .alp、删除、重命名、排序（可拖拽） | `ProjectListScreen.kt` |
| 内置示例 | 顶栏「示例」入口（MenuBook 图标）→ `ExamplesScreen.kt`：6 个内置 `.alp`（demo / DemoProject / kleinbottle / TestJson / hello / listview），一键导入/重新导入，已导入徽标；导入复用 `AlpManager.import`（与用户导入同路径、同覆盖语义） | `assets/examples/*.alp`（`make-examples.ps1` 从 `build-scripts/examples-src/` 生成） |
| 代码编辑器 | sora-editor 引擎 + TextMate（Lua 高亮）、行级词法诊断、补全、自动保存 | `:editor` |
| 构建 APK | 独立打包器（D-10 已开工：`LuaCompiler`/`AXmlManifest`/`ApkBuilder`/`ApkSigner`/`V2Signer`/`BuildApkScreen`） | `:app` |

## 9. pro 有而 delua 无（⚠仅pro，`import` 会失败，属"pro 独有能力"不是 bug）

| 缺失能力 | 说明 |
|---|---|
| `com.nirenr.*` 截图/找色自动化 | Color、ColorFinder、ColorPoint、Point、ScreenCapture*；`LuaAccessibilityService` 的 `startScreenshot/click(Point)/swipe(Point,Point)` 族 |
| pro 自带编辑器类 | `com.androlua.LuaEditor`、`LuaLexer`、`LuaTokenTypes`、textwarrior 33 类 |
| `android.widget` 扩展控件 | CardView、DrawerLayout、ToolBar、SlidingLayout、PullingLayout、RippleHelper、CircleImageView、HorizontalListView、FloatButton、PageLayout、PageAdapter、ViewDragHelper 等 24 个 |
| 其他 | `FloatWindow`、`AlertDialogBuilder`、multidex 支持类、Xposed 反检测 `onHook` |

工程若依赖上表能力 → 在 delua 中判定为白名单差异（SKILL §6.5），需在报告中注明，不视为 delua 缺陷。
