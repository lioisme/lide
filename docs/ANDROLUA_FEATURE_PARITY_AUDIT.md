# delua × AndroLua_pro 工程 / 库 / 插件 / 模块系统一致性审计报告

> 审计对象：`delua` 工程（`androlua/` + `app/` 模块）vs `docs/reference/androlua/AndroLua_pro-master/`（AndroLua pro 4.4.2 源码）
> 审计基准：pro 源码按字节比对（`git diff --no-index` + hash 比对），delua 侧补充全量运行时链路追踪。
> 审计日期：2026-07-31
> 结论速览：**引擎层（C 原生 + Lua 运行时脚本）已完全对齐；宿主管理机制（库/插件/模块）是 delua 自创的"全局仓库"模型，与 pro 的"工程内自行管理"模型存在 3 处断链，另有 5 类运行时组件未接入，导致特性不一致。**

---

## 1. 审计范围与方法

| 范围 | 方法 |
|---|---|
| delua 侧 | `androlua/src/main/java`（~430 文件）、`androlua/src/main/kotlin`（12 文件）、`app/src/main/java`、`app/src/main/kotlin`、`app/src/main/assets`、`app/src/main/jniLibs`、两份 `build.gradle.kts`、`AndroidManifest.xml` |
| pro 侧 | `app/src/main/java`（~500 文件）、`app/src/main/assets`（IDE Lua 页面）、`app/src/main/resources/lua`（28 脚本）、`app/src/main/jni`（21 个原生模块）、`AndroidManifest.xml`、`build.gradle` |
| 比对手段 | 全量文件清单 diff、逐字节 hash 比对、`git diff --no-index` 细粒度 diff、运行时调用链追踪（LuaDexLoader → import.lua → LuaActivity → EngineActivity） |

---

## 2. AndroLua_pro 的运行时管理机制（基准模型）

pro 的工程 / 库 / 插件 / 模块全部采用 **沙盒内自组织 + 工程内自带** 模型，没有任何全局"仓库"概念：

### 2.1 工程（Project）

```
{luaExtDir}/project/<name>/
├── init.lua        # 元数据：appname/appver/appcode/appsdk/packagename/developer/description/debugmode/user_permission
├── main.lua        # 入口脚本
├── icon.png        # 图标
└── libs/           # ← 工程内库目录（jar/dex/so 随工程打包带走）
```

- 工程发现：`LuaActivity.onCreate → getLuaPath()`（Intent 传入），`initENV()` 以独立环境 `loadfile(init.lua)` 读取元数据（appname→标题、debugmode→调试开关、theme→主题）。
- 工程导入/导出：`.alp`（zip 格式），`Welcome/Main` 通过 Intent `VIEW/EDIT *.alp` 打开，`ZipUtil` 解压到 `luaExtDir/project/`。
- pro 自带工程管理 UI 是 **Lua 页面**：`assets/project/main.lua`（列表）、`assets/projectinfo/main.lua`（属性/权限编辑），直接 `loadfile(init.lua,"bt",app)` 读元数据。

### 2.2 库系统（工程内自行管理）

- **Java/Dex 库**：`<project>/libs/*.jar|*.dex` → `LuaDexLoader.loadLibs()/loadDex()` → `DexClassLoader(path, odexDir, nativeLibraryDir, appClassLoader)`，缓存于 `dexCache`（MD5 键），并加入 `dexList` 供 `import "xxx:com.foo"` 的 `luacontext.loadDex()` 使用；jar 内资源通过反射 `AssetManager.addAssetPath` 合入 `LuaResources`（Lua 侧 `activity.getAssets()`/`getResources()` 可读）。
- **Native 库**：`<project>/libs/lib<fn>.so` → `LuaDexLoader.loadLib()` 复制到 `getDir(fn)`，登记进 `libCache`；`import.lua` 的 `libsloader` 按 `package.loadlib(libs[前缀], "luaopen_...")` 加载（`import "gl"`、`import "cjson"` 等走此路径）。
- **系统 Cpath**：`luaCpath = nativeLibraryDir/lib?.so + libDir/lib?.so` → `require "socket"` 等直接 `loadlib` 内置 15 个 `.so`。
- 关键点：**库的"安装"就是"放进工程 libs/ 目录"**，`.alp` 导出即携带，导入即生效，无注册表。

### 2.3 模块系统（Lua 模块）

- `package.path` = `工程目录/?.lua;工程目录/lua/?.lua;工程目录/?/init.lua;` + `app_lua/?.lua;app_lua/lua/?.lua;app_lua/?/init.lua`（`luaMdDir = getDir("lua")`）。
- 运行时脚本（import.lua/loadlayout.lua/json.lua/socket 等 28 个）由 `Welcome.unApk("lua", luaMdDir)` 从 APK zip 自身解包进 `app_lua`（**MD5 校验跳过未变更文件**）。
- 工程内模块 = 工程目录下的任意 .lua，`require` 即可，随工程带走。

### 2.4 插件系统

- 结构：`{luaExtDir}/plugin/<name>/`，**元数据就是 init.lua**（`appname/appver/description`），入口 `main.lua`，图标 `icon.png`。
- 运行：pro 的插件管理页（`assets/plugin/main.lua`）`loadfile(init.lua,"bt",app)` 扫描，点击后 `activity.newActivity(<plugin>/main.lua, {luadir, luapath})`。
- 无安装/卸载概念，无版本依赖解析——**"插件"只是工程目录的另一形态**。

### 2.5 宿主 IDE（pro 用 Lua 写）

`assets/` 顶层：`main.lua`（IDE 首页）、`init.lua`、`layout.lua`、`help.lua`、`logcat.lua`、`javaapi/`（android.jar 类库定义 + `.aly` 布局模板，供补全/布局助手）、`layouthelper/`、`luadoc/`（离线 Lua 手册）、`keys/`（签名密钥）、`plugin|project|projectinfo/`（管理页面）。启动即由 `unApk("assets", localDir)` 解包到 files 目录。

---

## 3. delua 逐层比对结果

### 3.1 ✅ 完全一致（引擎核心，逐字节）

| 类别 | 内容 |
|---|---|
| Lua 运行时脚本 | `app/src/main/assets/lua/` 28 个文件与 pro `resources/lua/` **逐字节一致**（import.lua、loadlayout.lua、loadbitmap.lua、loadmenu.lua、json.lua、socket/、ltn12.lua、mime.lua、smtp.lua、ftp.lua、http.lua、xml.lua、base64.lua、bin.lua、hex.lua、mbox.lua、check.lua、console.lua、autotheme.lua、logcat.lua、options.lua、permission.lua、su.lua、test.lua、bmob.lua） |
| 原生库 | 15 个 `.so` × 3 ABI（arm64-v8a/armeabi-v7a/x86_64）：bson、canvas、cjson、crypt、gl、luajava、luv、md5、mime、regex、sensor、socket、xml、zip、zlib。与 pro 主 `Android.mk` 实际构建集 **完全一致**（pro 的 lfs/lsqlite3/lluv/tcc 均为 `Android.mk2` 禁用态，delua 正确未引入） |
| `com.luajava` | 全部 16 个类逐字节一致：`LuaState`(1277L)、`LuaJavaAPI`(1823L)、`LuaObject`、`LuaTable`、`LuaFunction`、`LuaInvocationHandler`、`LuaStack`、`LuaString`、`LuaList`、`JavaFunction`、`LuaException`、`LuaStateFactory`、`Console`、`LuaMetaTable`、`LuaMethodInterceptor`、`LuaAbstractMethodInterceptor` |
| `com.android.cglib` | dx（~270 文件）+ proxy（9 文件）完整一致，`LuaEnhancer` 依赖的代理/增强链无缺口 |
| `com.androlua`（主体） | 42+ 个类逐字节一致：`LuaAdapter`、`LuaArrayAdapter`、`LuaExAdapter`、`LuaMultiAdapter`、`LuaExpandableListAdapter`、`LuaThread`、`LuaTimer`、`LuaTimerTask`、`LuaAsyncTask`、`LuaRunnable`、`LuaDexLoader`、`LuaDexClassLoader`、`LuaEnhancer`、`Http`、`Download`、`LuaDialog`、`LuaDrawable`、`LuaBitmap`、`LuaBitmapDrawable`、`LuaAnimation`、`LuaFragment`、`LuaPreferenceFragment`、`LuaView`、`LuaWebView`(1 行 icon 差异除外)、`LuaCameraView`、`LuaBroadcastReceiver`、`LuaContentObserver`、`LuaFileObserver`、`LuaClient`、`LuaServer`、`LuaContext`、`LuaPrint`、`LuaAssetLoader`、`LuaGcable`、`LuaResources`(注解差异除外)、`Ticker`、`GifDecoder`、`NineBitmapDrawable`、`LoadingDrawable`、`CrashHandler`、`ZipUtil`、`util/*`（AsyncTaskX、ClickRunnable、RootUtil、RSASecurity、ScreenMetrics、TimerTaskX、TimerX、VolatileBox、VolatileDispose） |

**核心结论：Lua 运行时语义层（引擎 + 脚本 + 类绑定）与 pro 完全等价，`import/require/loadlayout/loadbitmap/loadmenu/loadDex/thread/task/timer` 全部可用。**

### 3.2 ⚠️ 有意的适配修改（4 个文件，行为差异需知晓）

| 文件 | 改动 | 影响评估 |
|---|---|---|
| `LuaActivity.java`（-115/+17） | ① 删除 Xposed 反检测 `chcek1/check2/check3/a` 及 `runFunc("onHook")`；② `R.drawable.icon` → `android.R.drawable.sym_def_app_icon`（×3 处，适配无 res 工程）；③ `luaDir` private → protected（供 EngineActivity）；④ 新增 `mScriptError/getScriptError/setScriptError`；⑤ `android.support.annotation` → `androidx.annotation` | ① 属恶意软件痕迹清除，合理；② 图标从工程 icon.png 退化为系统图标（`setTitle` 中 TaskDescription 仍读工程 icon.png，仅快捷方式图标退化）；④ 为宿主回传错误信息而加，无副作用 |
| `LuaApplication.java`（+37） | 新增 `extractLuaScripts()`：首次启动把 `assets/lua/*` 复制到 `luaMdDir` | 对应 pro 的 `Welcome.unApk("lua", luaMdDir)`。差异：pro 每次启动按 **MD5 校验更新**，delua 仅 `if (outFile.exists()) continue` —— **升级 APK 内置脚本不生效**（低危）。delua 同时移除了 pro 的 `unApk("assets", localDir)`（即 javaapi/luadoc/keys/IDE 页面解包，见 3.4） |
| `LuaAccessibilityService.java`（-64） | 删除全部 `com.nirenr.screencapture.*`/`com.nirenr.Point` 依赖：`startScreenshot/getScreenshot/stopScreenshot`、`click(Point)/longClick(Point)/press(Point)/swipe(Point,Point)` 重载 | **Lua 侧 `service.startScreenshot()` 等 API 消失**（见 3.3 缺失类） |
| `PageView` / `BasePageAdapter` / `ArrayPageAdapter` | 仅删除 Javadoc/版权头注释，无逻辑改动 | 行为等价 ✅ |

另：`FileProvider.java`、`LuaResources.java`、`GlobalActionAutomator.java` 仅 `android.support.annotation` → `androidx.annotation`；`LuaWebView.java` 仅 icon 资源替换。

### 3.3 🔴 缺失的 Java 类（共 80 个，Lua 侧 API 缺失）

| 分组 | 缺失文件 | Lua 侧影响 |
|---|---|---|
| `android.widget` 扩展控件（24） | CardView(+Delegate/Impl)、DrawerLayout(+Impl)、ToolBar、SlidingLayout、PullingLayout、RippleHelper、RippleLayout、RoundRectDrawable(+WithShadow)、CircleImageView、HorizontalListView、FloatButton、PageLayout、PageAdapter、ArrayExpandableListAdapter、ViewDragHelper | `import "android.widget.CardView"` 等 **运行时崩溃**（bindClass 失败） |
| `android.app`（2） | FloatWindow、AlertDialogBuilder | 悬浮窗 API 缺失 |
| `android.support.multidex`（3） | MultiDex、MultiDexExtractor、ZipUtil | 工程 libs 方法数 >64K 时无 `installLibs` 支持（当前主 DEX 无需，但 pro 原装能力缺失） |
| `com.nirenr`（9） | Color、ColorFinder、ColorPoint、Point、SplitEditView、screencapture/FileUtil、ScreenCaptureActivity、ScreenCaptureListener、ScreenShot | **截图 + 找色自动化 API 整体缺失**；`LuaAccessibilityService` 被连带削掉截图功能 |
| pro IDE 编辑链（33+5） | textwarrior/*（33 个）、LuaEditor、LuaLexer、LuaTokenTypes、Main、Welcome、SplitEditView | pro 的工程内 Lua 编辑器（`LuaEditor` 类在 Lua 侧可 import）被 delua 的 sora-editor 替代——**合理替换**，但 pro 脚本若 `import "com.androlua.LuaEditor"` 将失败 |
| `android.support.v4.app.Fragment`（1） | Fragment | LuaFragment 已用框架 Fragment，低危 |

### 3.4 🔴 资产差异（IDE 侧 Lua 资源）

| pro 资产 | delua 现状 | 影响 |
|---|---|---|
| `assets/main.lua`、`init.lua`、`layout.lua`、`help.lua`、`logcat.lua` | 无 | pro 的 Lua IDE 首页体系；delua 用 Compose 替代（合理） |
| `assets/javaapi/`（android.lua、layout.aly、mlayout.aly、clayout.aly、fiximport.lua、init.lua、main.lua） | 无 | pro 的补全数据 + 布局助手模板（`.aly` 由 `loadlayout` 家族解析）——delua 编辑器改用 textmate 高亮 + 自建诊断，**`loadlayout` 的 .aly 语法仍是运行时 API，缺失模板意味着 pro 的布局辅助能力消失** |
| `assets/layouthelper/`（init.lua、layout.lua、loadlayout2.lua、loadlayout3.lua、main.lua、xml2table.lua） | 无 | Lua 侧 `require "loadlayout2"`/`require "xml2table"` 等会失败 |
| `assets/luadoc/`（manual.html 等离线文档） | 无 | 离线文档缺失 |
| `assets/keys/`（keystore.ks、platform/testkey 签名） | 无 | 独立 APK 构建签名链缺失（delua 用自有 keystore.jks，属合理替换） |
| `assets/plugin/`（main.lua、init.lua、layout.aly、item.aly） | 仅有示例 `plugin/plugin.json` | pro 的插件管理页为 Lua 实现，delua 用 Compose PluginScreen 替代 |
| `assets/project/`、`assets/projectinfo/` | 无 | pro 的工程列表/属性编辑页；delua 用 ProjectListScreen/ProjectDependenciesScreen 替代 |
| `assets/play.png`、`redo.png`、`undo.png` | 无 | UI 图标，无关紧要 |

### 3.5 🔴 delua 自创"统一管理"系统（与 pro 模型的根本分歧）

delua 在 `androlua` 模块 Kotlin 侧新增 5 个管理组件，构建了一套 **全局仓库模型**：

```
{luaExtDir}/
├── project/<name>/           # 工程（与 pro 一致）
├── plugin/<name>/            # 插件（元数据用 plugin.json，pro 用 init.lua 字段）
├── common/
│   ├── libs/<name>/          # 全局库：metadata.json + <name>-<version>.jar|.dex
│   ├── modules/<name>/       # 全局模块：metadata.json + *.lua
│   └── registry.json         # 全局索引（schemaVersion/lastUpdated/libraries/modules）
└── delua.json（可选）         # 工程依赖声明（dependencies.{libs,modules}）
```

对应实现：`LibraryRegistry.kt`、`ModuleRegistry.kt`、`DependencyManager.kt`、`PluginManager.kt`、`AlpManager.kt`。

**3 处断链（P0/P1 级缺陷）**：

1. **依赖声明永不物化（P0）**：`ProjectDependenciesScreen` 把 `dependencies = {libs={"x"}, modules={"y"}}` 写进 `init.lua`（`DependencyManager.replaceDependenciesInInitLua`），但**没有任何代码**把 `common/libs/<name>` 的 jar/dex 复制进 `<project>/libs/` 或通知引擎；`LibraryRegistry.findJarFile()` 定义了但**零调用方**；`EngineBridge/EngineActivity` 不接收任何依赖信息。→ **声明的依赖在 Lua 运行时完全无效**，用户会得到"明明安装了却 import 不到"的割裂体验。
2. **验证只查存在性（P1）**：`validateDependencies` 仅检查 `common/libs/<name>` 目录是否存在，不校验 jar 有效性、版本、ABI。
3. **插件安装/运行为死代码（P1）**：`PluginManager.installFromAlp()` 零调用方；`PluginScreen` 只读列表，点击无动作；pro 的插件 = `newActivity(plugin/main.lua)` 直接运行，delua 未实现运行入口。插件元数据格式（plugin.json vs init.lua appname/appver）**与 pro 不兼容**——pro 生态插件放进 delua 的 plugin/ 目录不会被识别（扫描只看 plugin.json）。

另有 **2 处工程链路残缺（P1）**：

4. **运行结果回传死链（已按用户决定收口）**：宿主结果页 `RunScreen` 经真机验证出现"两个界面"（宿主页先展示 `run_no_result` 误导文案、引擎随后全屏运行），判定为冗余中间页后**移除**（见 §8 状态 #2）。当前"运行"= `RunEngineLauncher` 物化依赖 → 直启 `EngineActivity`，输出/错误由引擎自带控制台（status/ListView）展示，宿主不做结果回传。
5. **`common/` 目录与工程 libs/ 机制并存但互不感知**：pro 的工程内 `<project>/libs/` 机制（`LuaDexLoader.loadLibs`）delua **完整保留且可用**（DemoProject 样例也以此说明），但全局仓库与其零交互——两套模型并存、用户侧割裂。

### 3.6 🔴 Manifest / 组件注册差异

| 组件 | pro | delua | 后果 |
|---|---|---|---|
| `LuaService` | ✅ 声明 | ❌ 未声明 | `activity.startService()/bindService()/stopService()`（LuaActivity.java:1003/1010/1027 仍引用 `LuaService.class`）→ **运行时 ServiceNotFoundException 崩溃** |
| `LuaAccessibilityService` | ✅ 声明 + `res/xml/accessibility_service_config` | ❌ 类在、manifest 无、无 xml | 无障碍自动化整体不可用 |
| `FileProvider` | ✅ `<provider authorities=包名>` + `androlua_filepaths.xml` | ❌ 类在、manifest 无 | `activity.share()`（LuaActivity.java:716/736）与 `activity.getUriForFile/getUriForPath` → **IllegalArgumentException 崩溃** |
| `ScreenCaptureActivity` | ✅ | ❌（类也缺失） | 截图 API 缺失 |
| `MainActivity` | singleTask + VIEW/EDIT `.alp` + `.lua` 关联 + `androlua://` scheme | 仅 `.alp` VIEW（file/content） | 文件管理器里点 `.lua` 无法用 delua 打开；`androlua://` 协议脚本拉起不可用 |
| `android:largeHeap`、`persistent` | ✅ | ❌ | 大工程内存风险（persistent 不建议沿用，largeHeap 建议补） |
| 权限 | 41 项 | 36 项 | 缺失：`WRITE_SMS`、`WRITE_CALL_LOG`、`READ_FRAME_BUFFER`、`BATTERY_STATS`、`CLEAR_APP_CACHE`、`GET_PACKAGE_SIZE`、`SYSTEM_ALERT_WINDOW`（FloatWindow 依赖）、`SET_WALLPAPER`、`DOWNLOAD_WITHOUT_NOTIFICATION`、`ANSWER_PHONE_CALLS`、`CHANGE_NETWORK_STATE`、`com.android.launcher.permission.INSTALL_SHORTCUT`（桌面快捷方式 API 依赖） |

### 3.7 delua 特有的宿主层（不在 pro 范围内，评审通过）

`EngineBridge.kt` / `EngineActivity.java`（Intent + 文件协议）、sora-editor + TextMate（`LuaLanguage/LuaDiagnostics/TextMateInitializer`，符合 AGENTS.md R1-R4）、Compose UI 层、`DeluaApp`（samples 提取、TextMate 预加载）——均为合理的"IDE 工程化"替换，与 pro 无冲突。

---

## 4. 特性一致性矩阵

| 特性 | pro 机制 | delua 现状 | 一致性 |
|---|---|---|---|
| 工程结构（init.lua 元数据 + main.lua + icon.png + libs/） | `project/<name>/` | 同结构，`ProjectManager` 读 appname/packagename/icon ✅ | ✅ 一致 |
| 工程导入/导出 `.alp` | Main/Welcome + ZipUtil | `AlpManager`（zip/zipToStream，file+content URI）✅ | ✅ 一致（实现路径不同） |
| 工程内库 `libs/*.jar/.dex/.so` 自动加载 | `LuaDexLoader.loadLibs` | **逐字节同源码，机制完整保留** ✅ | ✅ 一致 |
| 全局库仓库 | 无此概念 | `common/libs` + registry.json（**未接入运行时**） | ❌ 冲突且断链 |
| 全局模块仓库 | 无此概念 | `common/modules`（**未接入运行时**） | ❌ 冲突且断链 |
| Lua 模块（require + 工程目录） | package.path 链 | 同链（luaLpath 一致） | ✅ 一致 |
| 运行时脚本（28 个） | `unApk("lua")` MD5 更新 | `extractLuaScripts()` 存在即跳过 | ⚠️ 一致但升级不生效 |
| 原生 C 库（15 个 .so） | jni 编译打包 | prebuilt + jniLibs 三 ABI | ✅ 一致 |
| 插件系统 | `plugin/<name>/` + init.lua 元数据 + newActivity 运行 | `plugin.json` 元数据 + 仅展示 + 死代码 install | ❌ 不兼容 + 不完整 |
| 无障碍 + 截图/找色 | LuaAccessibilityService + com.nirenr | 类被削 + manifest 无注册 | ❌ 缺失 |
| 后台服务 | LuaService | 类在、**manifest 未声明 → 崩溃** | ❌ 缺失 |
| 分享/文件 URI | FileProvider | 类在、**manifest 未声明 → 崩溃** | ❌ 缺失 |
| 编辑器 | textwarrior + LuaEditor + javaapi 补全 | sora-editor + TextMate | ✅ 合理替换（pro 脚本 import LuaEditor 除外） |
| 布局助手 | layouthelper/ + javaapi/*.aly | 无 | ❌ 缺失（loadlayout2/xml2table 等 Lua 库不可用） |
| 离线文档 | luadoc/ | 无 | ❌ 缺失（可后补） |
| IDE 管理页 | Lua 页面（main/project/plugin/projectinfo） | Compose 页面 | ✅ 合理替换 |
| 运行输出回传宿主 | pro 引擎内控制台 | EngineActivity 控制台 + **engine_result.json 死链** | ❌ 断链 |
| `.lua` 文件关联 / `androlua://` scheme | ✅ | ❌ | ❌ 缺失 |

---

## 5. 差距分级与修复行动项

### P0 —— 立即修复（运行链路断裂）

| # | 行动 | 涉及文件 | 验收标准 |
|---|---|---|---|
| 1 | **依赖物化**：运行前把 `common/libs/<name>` 的 jar/dex 与 `common/modules/<name>` 的 lua 物化到 `<project>/libs/` 与工程目录（或扩展 `EngineBridge` 传递依赖清单、`LuaDexLoader` 支持全局目录），使 `dependencies` 声明在引擎真实生效 | `DependencyManager.kt`、`EngineBridge.kt`、`RunScreen`（重建） | 声明依赖后 `import`/`require` 可用 |
| 2 | **结果回传闭环**：重建结果展示（恢复 RunScreen 或 Snackbar），消费 `engine_result.json` 与 `engine_output.txt` | `EngineActivity.java`、`EngineBridge.kt`、`DeluaNavHost.kt` | 引擎退出后宿主展示输出/错误 |
| 3 | **Manifest 注册三组件**：`LuaService`（含 luaDir/luaPath extra）、`FileProvider`（authorities=包名 + file_paths xml）、`LuaAccessibilityService`（+ accessibility_service_config xml） | `app/src/main/AndroidManifest.xml` + `res/xml/*` | `activity.startService()`、`activity.share()`、无障碍不崩溃 |

### P1 —— 近期补齐（特性一致性）

| # | 行动 | 验收标准 |
|---|---|---|
| 4 | 补齐缺失 Java 类：`android.widget` 24 控件（**优先 CardView/DrawerLayout/ToolBar/FloatButton/CircleImageView**，由 loadlayout 脚本高频引用）、`FloatWindow`、`AlertDialogBuilder`、multidex | `import "android.widget.CardView"` 等成功 |
| 5 | 补齐 `com.nirenr.*`（9 文件）+ 恢复 `LuaAccessibilityService` 截图 API 重载 | `service.startScreenshot()/getScreenshot()/click(Point)` 可用 |
| 6 | 插件系统对齐 pro：兼容 init.lua 元数据 + 支持 `newActivity(<plugin>/main.lua)` 运行入口；`installFromAlp` 接入 UI | pro 生态插件目录直接可扫、可运行 |
| 7 | 资产补齐：`assets/lua` 增加 `loadlayout2/loadlayout3/xml2table`（layouthelper 族）等 pro 运行时可 require 的脚本；`javaapi/*.aly` 与补全数据（如编辑器需要） | `require "loadlayout2"` 可用 |
| 8 | 权限补齐 12 项（含 `SYSTEM_ALERT_WINDOW`、`INSTALL_SHORTCUT`）与 `largeHeap` | 快捷方式/悬浮窗/短信 API 不闪退 |
| 9 | `extractLuaScripts` 改为版本化更新（同 pro 的 MD5 方案或版本文件） | 升级后内置脚本更新生效 |

### P2 —— 可选（体验/生态）

| # | 行动 |
|---|---|
| 10 | `.lua` 文件关联打开（VIEW/EDIT `*.lua`）与 `androlua://` scheme，打通外部脚本拉起 |
| 11 | 补齐 `luadoc/` 离线文档（可用 `docs/reference` 的 manual.html） |
| 12 | 决策"统一管理 vs 工程内管理"的终态：若保留全局仓库，建议将 `dependencies` 与 `<project>/libs/` 归一（物化即同步，避免两套模型并存）；若对齐 pro，则删除 common/ 概念，回归纯工程内管理 |

---

## 6. 附：关键文件速查

- delua 管理组件：`androlua/src/main/kotlin/com/lioisme/delua/androlua/{AlpManager,PluginManager,ZipUtil}.kt`、`common/{DependencyManager,LibraryRegistry,ModuleRegistry}.kt`
- 引擎桥接：`androlua/.../engine/EngineBridge.kt`、`app/src/main/{java/com/lioisme/delua/engine/EngineActivity.java, kotlin/com/lioisme/delua/engine/RunEngineLauncher.kt}`
- 运行时入口：`androlua/src/main/java/com/androlua/{LuaActivity,LuaApplication,LuaDexLoader}.java`
- 比对基线：`docs/reference/androlua/AndroLua_pro-master/app/src/main/{java,assets,resources/lua,jni}`

---

## 8. 行动项执行状态（2026-07-31 更新）

| # | 级别 | 行动 | 状态 |
|---|---|---|---|
| 3 | P0 | Manifest 注册三组件 + 权限/largeHeap | ✅ 完成（LuaService、LuaAccessibilityService + config xml、FileProvider + file_paths xml、LuaActivityX、ScreenCaptureActivity；12 项权限 + largeHeap） |
| 4 | P1 | 补齐缺失 Java 类（widget 19 + FloatWindow/AlertDialogBuilder + multidex 3） | ✅ 完成（SplitEditView 依赖 textwarrior 编辑链，被 sora-editor 合理替代，未移植） |
| 5 | P1 | 补齐 `com.nirenr.*` + 恢复 LuaAccessibilityService 截图 API | ✅ 完成（Color/ColorFinder/ColorPoint/Point + screencapture 4 文件；截图/点击 API 逐块恢复） |
| 2 | P0 | 结果回传闭环 | ✅→🔄 完成又移除（2026-07-31 用户决定）：真机"两个界面"问题（RunScreen 先显示 `run_no_result` 失败回显、引擎随后运行）判定为冗余中间页；删除 RunScreen/EngineResult，恢复 `RunEngineLauncher` 直启引擎（物化保留在启动链路），输出由引擎自带控制台展示 |
| 1 | P0 | 依赖物化 | ✅ 完成（`materializeProjectDependencies`：libs→`<project>/libs/`、modules→`<project>/lua/`(+`/lua/<name>/`)，含完整性校验，运行前调用） |
| 6 | P1 | 插件系统对齐 | ✅ 完成（init.lua 元数据 fallback + main.lua 入口；installFromAlp 接入 PluginScreen "+" 按钮；点击卡片直启引擎运行插件 main.lua（原经 RunScreen，随结果页一并简化）） |
| 7 | P1 | 资产补齐（layouthelper 族） | ✅ 完成（`androlua/src/main/assets/lua/`：loadlayout2/loadlayout3/xml2table，经 extractLuaScripts → luaMdDir，`require "loadlayout2"` 可用；xml2table 依赖 `require "import"`，由 assets/lua/import.lua 提供 ✅） |
| 9 | P1 | extractLuaScripts 版本化更新 | ✅ 完成（MD5 比对，变更才覆盖） |
| 8 | P1 | 权限补齐 | ✅ 完成（见 #3） |
| 10 | P2 | `.lua` 文件关联 / `androlua://` scheme | ✅ 完成（MainActivity 三类拉起：.alp 导入、.lua 打开编辑（独立文件自动收养为工程）、androlua:// 拉起运行/编辑；editor 路由支持 fileName） |
| 11 | P2 | luadoc/ 离线文档 | ✅ 完成（`assets/luadoc/` 4 文件 + 启动提取至 filesDir + 设置页"Lua Manual"入口（WebView 离线渲染）） |
| 12 | P2 | 管理模型终态决策 | ✅ 决策：**保留全局仓库 + 物化同步为终态**——common/ 仓库负责集中安装/版本/卸载，`materializeProjectDependencies`（P0-1）在运行前把声明依赖物化为 `<project>/libs/` 与 `<project>/lua/`，与 pro 的"工程内自行管理"模型在运行时归一（LuaDexLoader/package.path 感知的仍是工程目录）；delua.json/init.lua 声明即同步入口。两套模型不再割裂：全局仓库 = 安装/管理面，工程目录 = 运行时面，物化是唯一的桥 |

> 注：P1-4 中 `MultiDex.java` 已就位但保持 pro 同款注释态（`//MultiDex.installLibs(this)`），未启用。
> 所有改动已通过 `:app:compileArm64DebugKotlin` 与 `:app:assembleArm64Debug`（BUILD SUCCESSFUL）。

---

## 7. 总结

- **引擎与运行时语义已 100% 对齐**：28 个 Lua 脚本、15 个原生库、`com.luajava` 全部 16 类、cglib 全链、`com.androlua` 主体逐字节一致，LuaDexLoader 的工程内 libs 机制原样保留——"工程内自行管理"的 pro 核心模型在 delua 中依然成立。
- **delua 的"统一管理"（common/ 仓库 + dependencies 声明 + plugin.json）是新增层，且与运行时存在 3 处断链**：依赖不物化、结果不回传、插件不可运行；同时 manifest 未注册 LuaService/FileProvider/AccessibilityService 导致 3 类脚本 API 直接崩溃，80 个 Java 类（控件/截图/找色）缺失使 Lua API 面收窄。
- 建议按第 5 节 P0 → P1 → P2 顺序执行，先修断链、再补类与资产、最后决策管理模型的终态，即可达成与 AndroLua pro 的全面特性一致。
