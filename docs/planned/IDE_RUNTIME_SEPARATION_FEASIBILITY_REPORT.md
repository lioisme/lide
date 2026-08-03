# lide IDE × AndroLua 环境分离可行性审计报告

| 元数据 | 内容 |
|--------|------|
| **文档编号** | LIDE-AUDIT-009 |
| **版本** | 1.0 |
| **状态** | 审计结论（待决策） |
| **审计日期** | 2026-08-02 |
| **审计范围** | `:app`、`:androlua`、`:editor` 三模块全量源码、`app/AndroidManifest.xml`、构建脚本、`docs/completed/*` 审计链、`docs/kb/*`、`docs/reference/androlua/AndroLua_pro-master` 参考树 |
| **审计方法** | 全量源码逐文件阅读 + 依赖方向 grep + 行数统计 + R1 边界门禁复验 + 既有审计文档交叉核对 |
| **触发背景** | 用户提出「分离 lide 的 IDE 与 androlua 环境，二者关系对标 VS Code 与 Flutter 插件」 |

---

## 1. 结论速览（TL;DR）

### 1.1 方案判定：合理，且技术上完全可行，但需分级落地

| 结论项 | 判定 |
|--------|------|
| 方向合理性 | ✅ **合理**。工程演化史上已有三次「宿主与引擎解耦」动作（WP-002 Intent 解耦 → WP-005 删兼容层 → WP-009 删全局仓库），本次分离是同一方向的自然终点；`R1~R4` 架构规则已把 `:editor` 洗成纯文本层，为「编辑器壳」做好了前提 |
| 技术上可否做到 | ✅ **可以**，分三级深度（§5）：契约层分离（低风险，推荐首做）→ 进程级隔离（中）→ 双 APK 环境即插即用（完全体，高成本） |
| 类比准确性 | ⚠️ **需修正**。VS Code/Flutter 关系的本质是「编辑器壳 + 独立 SDK + daemon 进程协议」三件套；lide 现状已具备「Intent 启动 + 文件/结果协议」的雏形（约 1.5/3 件），但存在两个本质落差：**同进程继承耦合**（LideApp extends LuaApplication / EngineActivity extends LuaActivity）与**打包链依赖引擎字节码**（LuaCompiler 直接调用原生 LuaState） |

### 1.2 四个硬约束（任何分离方案都必须遵守）

1. **魔改字节码不可外包**：引擎是 nirenr 深度魔改的 Lua 5.3.3（9 个自定义语法），字节码与标准 luac 不兼容 → 编译 Lua 源码为字节码**必须调用引擎本体**，无法用纯 Kotlin/Java 替代。
2. **LuaState 不可跨进程**：`LuaState` 是 JNI 句柄（`System.loadLibrary("luajava")` 后由 C 侧持有），进程隔离后，编译/运行调用必须落在引擎进程内。
3. **W^X 约束不可回退**：Android 8+/14+ 拒绝从 app 可写目录加载 dex，`LuaDexLoader` 必须保持 `InMemoryDexClassLoader` 内存字节加载（V-01/V-02 真机回归兜底）。
4. **反射类面不可被 R8 误删**：29 个 Lua 可见类清单（`app/build.gradle.kts:141-171`）在环境独立成包后需随环境自持 keep 规则。

### 1.3 一个必须先修的结构性发现

**环境资产全部错位在 `:app`**：17 个 `.so`（`app/src/main/jniLibs/`）、31 个 Lua 运行时脚本、签名密钥、布局模板、luadoc 全部属于「androlua 环境」，却物理存放在 IDE 模块里。任何分离方案的第一步都是**资产归位**。

---

## 2. 现状架构审计

### 2.1 三模块拓扑与规模

```
settings.gradle.kts: :app + :androlua + :editor
```

| 模块 | 职责 | 代码量 | 包名 |
|------|------|--------|------|
| `:app` | IDE 宿主：Compose UI、工程管理、运行拉起、权限、设置、打包器 | Kotlin 5,004 行 + Java 102 行 | `com.lioisme.lide` |
| `:androlua` | 引擎运行时：AndroLua Pro 4.4.2 Java 全量源码 + lide 工具层 | Java 85,225 行 + Kotlin 1,166 行 | `com.androlua` / `com.luajava` / `com.android.cglib` / `android.widget` 等 |
| `:editor` | 纯文本编辑器：sora-editor + TextMate 高亮、行级诊断、补全 | Kotlin 1,730 行 | `com.lioisme.lide.editor` |

### 2.2 当前依赖方向

```
        ┌──────────┐      ┌───────────┐      ┌──────────┐
        │  :editor │  ←  │   :app    │  →   │ :androlua│
        │  (纯文本) │  ←  │(IDE 宿主) │  →   │(引擎运行时)│
        └──────────┘      └─────┬─────┘      └──────────┘
                                │ 继承 × 2 + API + 资产 + Manifest
                                ▼
                         libluajava.so + 16 附加 .so（jniLibs 在 :app）
```

- `:editor` → `:app`/`:androlua`：**零依赖**（R1 门禁 `grep luajava|androlua|loadLibrary editor/src` 复验零命中）。
- `:app` → `:androlua`：强耦合（§2.3 全清单）。
- `:editor` 的 LuaDiagnostics 是纯 Kotlin 词法校验（括号配对/块闭合/字符串注释完整性），不做运行时语义——这正是「VS Code 不认识 Flutter 也能编辑 Dart 语法」的对应物，**方向已对**。

### 2.3 `:app` → `:androlua` 耦合点全清单

| # | 耦合类型 | 位置 | 说明 |
|---|---------|------|------|
| C-1 | **继承（Application）** | `LideApp.kt:10` `class LideApp : LuaApplication()` | 环境初始化（目录、luaCpath/luaLpath、extractLuaScripts、CrashHandler）随 IDE 的 Application 生命周期执行 |
| C-2 | **继承（Activity）** | `EngineActivity.java:19` `extends LuaActivity` | 引擎页是环境 Activity 的子类；`sendMsg/sendError/getLuaPath/doFile` 全走环境实现 |
| C-3 | 静态 API | `MainActivity.kt:97,145`、`ProjectManager.kt:5`、`ExamplesScreen.kt:20`、`PluginScreen.kt:24` | `LuaApplication.getInstance().luaExtDir`（工程目录约定 `/sdcard/AndroLua`） |
| C-4 | 模块内 Kotlin 工具 | `androlua/.../com/lioisme/lide/androlua/` 8 类 1,166 行 | `AlpManager/AlpNaming`、`AXmlManifest`、`LuaCompiler`、`LuaConfigParser`、`PermissionResolver`、`PluginManager`、`ZipUtil`、`engine/EngineBridge` |
| C-5 | **编译链** | `LuaCompiler.kt` → `com.luajava.LuaStateFactory` | 打包器第一步「Lua→字节码」直接 new 原生 LuaState（`LloadBuffer` + `dump`） |
| C-6 | 打包器 | `ApkBuilder.kt` 依赖 `AXmlManifest`/`LuaCompiler`/`ManifestEdit` | 打包链（§3-12）跨越两模块 |
| C-7 | Manifest 组件 | `app/AndroidManifest.xml` 8 处 | `EngineActivity`、`com.androlua.LuaActivity`、`LuaActivityX`、`LuaService`、`LuaAccessibilityService`、`ScreenCaptureActivity`、`FileProvider`（来自 androlua 模块的 `android.content.FileProvider`） |
| C-8 | 原生库 | `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64}/` 17 .so × 3 ABI | 环境的核心 `.so` 存放在 IDE 模块 |
| C-9 | 环境资产 | `app/src/main/assets/` | `lua/`（31 脚本）、`layouts/javaapi/*.aly`、`keys/*`、`luadoc/*`、`examples/*.alp` 全在 IDE 模块 |
| C-10 | R8 keep | `app/build.gradle.kts:141-171` `luaApiSurface` 29 类 | Lua 反射类面与 IDE 的 R8 配置耦合 |
| C-11 | 运行时语义约定 | 隐式 | `activity`/`service` 全局、import 点语法/冒号语法、dex 内存加载、`luaCpath = nativeLibraryDir + "/lib?.so"`、`/libs` 目录、.alp 格式（详见 §3-9/§3-10） |

### 2.4 关键审计发现

1. **环境资产错位**（§1.3）：C-8/C-9 是本次分离的最大物理障碍，也是最早可执行的收益点——`:androlua` 模块 `build.gradle.kts` 当前只编译 Java 源码，不携带任何 jniLibs/assets。
2. **`LuaApplication.onCreate` 是环境的初始化入口**（`LuaApplication.java:157-194`）：外部存储探测 → 目录四件套（localDir/odexDir/libDir/luaMdDir）→ `luaCpath`/`luaLpath` 构造 → `extractLuaScripts()`（从 **IDE 的 assets** 解包 Lua 脚本，MD5 版本化）。这条链完整定义了「环境自举」逻辑，天然可抽为独立模块/APK 的 Application。
3. **运行结果协议已存在且可跨进程**：`EngineActivity.finish()` → `setResult(RESULT_OK, Intent)`，extra 携带 output（200KB 截断防 Binder 溢出）+ error 标记；宿主侧 `EngineResultParser`（纯 Kotlin，7 单测）解析。ActivityResult 跨进程天然可用 → 进程级隔离无需重写协议。
4. **历史文件协议可回退**：WP-002/003 曾用 `engine_result.json` + `engine_output.txt` 文件协议（现已被 setResult 取代），跨 APK 场景可复用该模式。
5. **pro 参考树自带分离蓝本**：pro 的 `Main.java`/`Welcome.java`（Java IDE）+ `assets/*.lua`（Lua IDE 1913 行）+ `bin.lua`（打包器）+ `assets/project`（内置工程）本身就分层存放；lide 的三模块划分比 pro 更干净，只是资产没归位。

---

## 3. AndroLua 环境全量清单（13 个子系统）

> 回答「androlua 环境包括哪些内容」：远不止 `.so`。以下为全量盘点，含位置、规模、对宿主的依赖、分离影响。

### 3-1 原生引擎库层（核心 .so）

| 项 | 内容 |
|----|------|
| 构成 | `libluajava.so`（魔改 Lua 5.3.3 + JNI 桥，117 个 JNI 导出符号）+ `libgl.so`（GLES 1.0 绑定）+ 15 个附加模块 |
| 附加模块 | cjson / socket / mime / xml / md5 / crypt / zip / zlib / regex / canvas / bson / luv / sensor / **lfs / lsqlite3**（lide 超集） |
| 规模 | 17 × 3 ABI（arm64-v8a / armeabi-v7a / x86_64），合计约 1.3 MB |
| 源码 | `docs/reference/androlua/AndroLua_pro-master/app/src/main/jni/`（398 个 C/C++ 文件，勿改动） |
| 构建链 | `build-scripts/jni/`（Android.mk 覆盖层 + `patches/`）+ `scripts/rebuild_engine.{ps1,sh}` + `prebuilt/` 产物归档 |
| 宿主依赖 | **无编译期依赖**；运行期由 `luaCpath = nativeLibraryDir + "/lib?.so"` 直接命中 |
| 分离影响 | 归位到环境后，`LuaApplication` 的 `getApplicationInfo().nativeLibraryDir` 自动指向环境 APK → **天然兼容** |

### 3-2 JNI 桥接 Java 层（com.luajava，16 类）

`LuaState` / `LuaStateFactory` / `LuaObject` / `LuaJavaAPI`（1823 行，反射缓存 + 类型转换）/ `LuaTable` / `LuaFunction` / `LuaList` / `LuaString` / `LuaStack` / `LuaMetaTable` / `LuaInvocationHandler` / `LuaMethodInterceptor` / `LuaAbstractMethodInterceptor` / `JavaFunction` / `LuaException` / `Console`。

- 宿主依赖：`LuaCompiler`（C-5）直接 new `LuaStateFactory`。
- 分离影响：整个包随环境走；跨进程后 LuaState 只能活在引擎进程。

### 3-3 AndroLua 运行时主体（com.androlua，52 类）

`LuaApplication`（LuaContext 实现，环境自举）、`LuaActivity`（运行入口，初始化时序：`loadLibrary → LuaStateFactory → pushJavaObject(activity) → setPackagePath → initENV → doFile`）、`LuaActivityX`、`LuaService`、`LuaFragment`、`LuaThread`、`LuaTimer`、`LuaTimerTask`、`LuaAsyncTask`、`LuaAdapter`/`LuaArrayAdapter`/`LuaExAdapter`/`LuaMultiAdapter`/`LuaExpandableListAdapter`、`LuaEnhancer`、`LuaDexLoader`（W^X 内存加载）、`LuaDexClassLoader`、`Http`、`Download`、`LuaWebView`、`LuaDialog`、`LuaContext`（接口）、`CrashHandler`、`LuaAccessibilityService`、`LuaClient`、`LuaServer`、`LuaContentObserver`、`LuaFileObserver`、`LuaBroadcastReceiver`、`LuaBitmap`/`LuaBitmapDrawable`/`LuaDrawable`/`LoadingDrawable`/`NineBitmapDrawable`、`LuaAnimation`、`LuaAssetLoader`、`LuaCameraView`、`LuaView`、`LuaResources`、`LuaGcable`、`LuaRunnable`、`LuaPrint`、`LuaUtil`、`Ticker`、`ZipUtil`、`GifDecoder`、`Download`、`LuaPreferenceFragment` 等。

- 宿主依赖：C-1/C-2/C-3（继承 + `getInstance().luaExtDir`）。
- 分离影响：**这一层是分离的核心难点**——IDE 当前通过继承进入环境（LideApp/EngineActivity），需要改造成「IDE 组合环境」或「环境自持 Activity」。

### 3-4 动态代理与字节码生成（com.android.cglib，~280 文件）

`proxy/`（Enhancer、ProxyBuilder、MethodInterceptor、MethodProxyExecuter 等 9 类）+ `dx/`（DexMaker + 完整 dx 字节码生成器移植，~270 文件）。

- 用途：`luajava.createProxy`、SAM 自动转换、运行时 dex 生成（插件/代理类）。
- 宿主依赖：无直接引用；经 R8 keep 清单（C-10）保护。
- 分离影响：环境 APK 需自持 keep 规则；`LuaEnhancer` 依赖此层。

### 3-5 布局表组件面（android.* 自定义类，~26 文件）

`android.widget.*` 20 个（`ArrayListAdapter`、`ArrayPageAdapter`、`BasePageAdapter`、`PageAdapter`、`ExListView`、`HorizontalListView`、`FloatButton`、`ToolBar`、`PageView`、`PageLayout`、`DrawerLayout`、`CircleImageView`、`CardView`、`CardViewImpl`、`CardViewDelegate`、`PullingLayout`、`SlidingLayout`、`ViewDragHelper`、`RippleHelper`、`RippleLayout`、`RoundRectDrawable` 等）+ `android.app.AlertDialogBuilder`/`FloatWindow` + `android.support.multidex`（3）+ `android.content.FileProvider`（1）。

- 用途：`loadlayout`/`import` 布局表引用的类面（`luaApiSurface` 一半以上在此）。
- 分离影响：环境 APK 的 DEX 自含即可。

### 3-6 屏幕/取色工具（com.nirenr，9 类）

`Color`/`ColorFinder`/`ColorPoint`/`Point`/`screencapture.{ScreenCaptureActivity,ScreenCaptureListener,ScreenShot,FileUtil}`。

- 宿主依赖：`ScreenCaptureActivity` 在 Manifest（C-7）。
- 分离影响：组件随环境 APK 声明。

### 3-7 Lua 运行时脚本（31 个）

`assets/lua/`：`import.lua`（类加载/导入机制核心，与 pro MD5 逐字节一致）、`loadlayout{,.lua}/loadlayout2/loadlayout3`（布局表解析器家族）、网络栈（`socket.lua` + `socket/{headers,tp,url}.lua` + `http.lua` + `smtp.lua` + `ftp.lua` + `mime.lua` + `ltn12.lua`）、数据（`json.lua`、`xml.lua`、`xml2table.lua`、`base64.lua`、`hex.lua`）、工具（`bin.lua` 打包器、`check.lua` 查错、`permission.lua`、`su.lua`、`console.lua`、`logcat.lua`、`autotheme.lua`、`test.lua`、`options.lua`、`mbox.lua`、`bmob.lua`、`loadmenu.lua`、`loadbitmap.lua`）。

- 宿主依赖：**在 `:app/assets`，经 `LuaApplication.extractLuaScripts()` 解包到 `filesDir/lua`**（C-9 错位点）。
- 分离影响：归位到环境 APK assets 后，环境 Application 自己解包自己——反而更干净。

### 3-8 引擎入口与生命周期语义

`LuaActivity` 初始化时序、`sendMsg`/`sendError` 双通道、`finish` 语义、`activity`/`service` 全局、`arg` 变长参数（Intent extra，`EngineBridge.EXTRA_SCRIPT_ARGS = "arg"` 必须与 LuaActivity 读取键同名）、非 UI 脚本自动 finish。

- 宿主依赖：`EngineActivity`（C-2）重写 `sendMsg/sendError/getLuaPath/finish` 实现结果回传。
- 分离影响：**运行契约的核心**，Tier 1 应固化为接口（§6）。

### 3-9 类加载与 dex 机制

`import.lua` 的 `import_dex_class` → `getClassLoaders()`（appClassLoader + engineDexLoader）遍历；`LuaDexLoader`（API≥26 走 `InMemoryDexClassLoader`，W^X 硬约束）；工程 `/libs` 目录（jar/dex/so 自动加载，V-01~V-10 真机矩阵全绿）。

### 3-10 原生模块加载约定

`luaCpath = nativeLibraryDir + "/lib?.so" + libDir + "/lib?.so"`；`libsloader`/`getLibrarys()`/`package.loadlib` 双入口；工程自备 `.so` 与 APK 内置 `.so` 路径不同，混淆会 `UnsatisfiedLinkError`（libs 支持工单记录）。

### 3-11 环境资产（非代码）

`layouts/javaapi/*.aly`（布局助手模板 3 个）、`keys/testkey.*`（打包签名材料，v1/v2 签名用）、`luadoc/*`（离线 Lua 手册 457KB manual.html）。全部在 `:app/assets`（C-9 错位点）。

### 3-12 打包链（跨层混合体）

| 组件 | 位置 | 依赖 |
|------|------|------|
| `LuaCompiler`（Lua→字节码） | `:androlua` Kotlin | **原生 LuaState**（C-5） |
| `AXmlManifest`（AXML 二进制解析/补丁） | `:androlua` Kotlin | 纯字节级，无引擎依赖（有单测） |
| `ApkBuilder` + `ZipUtils`（APK 容器装配） | `:app` | 以**宿主 APK 为模板** |
| `ApkSigner` + `V2Signer`（v1/v2 签名） | `:app` | BouncyCastle |
| `PermissionResolver` / `LuaConfigParser` / `PluginManager` | `:androlua` Kotlin | 纯 Kotlin |

- 结论：打包链中**唯一绑定引擎的是 `LuaCompiler`**；其余均可随 IDE 或独立。
- 分离影响：若环境独立成 APK，打包器需「环境模板 APK」资产（pro 的 `bin.lua` 正是此模型：模板 APK + luac + manifest 字符串级补丁）。

### 3-13 Manifest 集成面

C-7 的 8 个组件 + 全部运行时权限声明（app manifest 顶部 ~50 条）。环境 APK 需自持组件与权限。

---

## 4. VS Code × Flutter 类比分析

### 4.1 该关系的三个本质特征

| 特征 | VS Code / Flutter 实现 | lide 现状 |
|------|------------------------|-----------|
| **① 编辑器壳不认知运行时** | VS Code 是通用编辑器；Dart 语法高亮/编辑由插件提供；插件不装也可编辑 | ✅ **已具备**（R1/R2）：`:editor` 纯文本级，Lua 高亮/诊断/补全零引擎依赖 |
| **② 独立交付与版本** | Flutter SDK 独立安装/升级；VS Code 与 Flutter 版本各自演进，靠版本探测对齐 | ❌ **未具备**：运行时与 IDE 同 APK 同 DEX 同版本 |
| **③ daemon 进程协议** | VS Code 插件 spawn `flutter daemon`，JSON-RPC 通信：launch/compile/log/热重载；崩溃互不影响 | ⚠️ **半具备**：Intent + setResult 协议存在（launch/output/error），但**同进程**——引擎 SIGSEGV（历史 EDITOR_CRASH_ANALYSIS 记载 `lua_close(NULL)` 双崩溃向量）、`LuaObject.finalize` 10 万行 print 崩溃会直接拖垮 IDE |

### 4.2 类比成立与不成立之处

- **成立**：① 已实现；③ 的协议形态（Intent + ActivityResult + 200KB 截断）已具 daemon 雏形；`:editor` 的独立演进能力已由模块边界保障。
- **不成立**：② 完全缺失；③ 缺进程边界。lide 当前等价于「VS Code 把 Flutter SDK 编译进了自己的二进制」——功能可用，但不是插件关系。
- **类比修正**：真正可对标的三级形态：
  - Tier 1 = 「VS Code 内嵌 SDK 但通过接口调用」（可插拔，但同进程同版本）
  - Tier 2 = 「VS Code 内嵌 SDK 但跑在独立进程」（= Flutter daemon 的进程隔离收益，无独立版本）
  - Tier 3 = 「VS Code + 独立安装的 SDK」（完全体：独立 APK、独立版本、动态发现）

---

## 5. 可行性论证与分级方案

### 5.1 Tier 1：契约层分离（推荐首做，~2-3 人日）

**目标**：单一 APK 内，把 `:app` 对环境的全部依赖收敛到一组接口，环境实现之；资产归位。

| 步骤 | 内容 |
|------|------|
| ① 资产归位 | `jniLibs`/`assets/lua`/`keys`/`layouts`/`luadoc` 移入 `:androlua`（或新 `:env` AAR）；AAR 原生支持 jniLibs 与 assets 随包；`:app` 仅 `implementation(project(":androlua"))` 引用 |
| ② 定义 `engine-api` 契约 | 新增纯 Kotlin 模块（或包）：`EngineRuntime`（初始化/luaExtDir/版本）、`EngineLauncher`（launch→session）、`EngineResult`（output/error/location）、`EngineCompiler`（compile→bytecode/diagnostics）、`EngineInfo`（版本探测）；`:app` 只依赖契约，`:androlua` 实现之（§6 草案） |
| ③ 继承改造 | `LideApp` 改组合：普通 Application + `EngineRuntime.initialize()`（把 `LuaApplication.onCreate` 初始化逻辑收敛为可显式调用的 `LuaEnvironment.initialize(context)`）；`EngineActivity extends LuaActivity` 保留（运行契约），但由 `EngineLauncher` 接口创建，宿主不再直接 new |
| ④ 打包链解耦 | `LuaCompiler` 走 `EngineCompiler` 接口（实现仍在引擎内）；`AXmlManifest`/`ApkBuilder` 保持纯 Kotlin |
| ⑤ 门禁升级 | R1 扩展为「`:app` 对 `com.androlua.*` 的直接 import 面白名单」 |

**收益**：可插拔契约成型；环境升级不影响 IDE 编译；资产单一事实源。
**风险**：低。行为零变化（同进程同 DEX）。

### 5.2 Tier 2：进程级隔离（~3-5 人日）

**目标**：环境 Activity 运行在 `android:process=":lua"`，获得 Flutter daemon 同款崩溃/内存隔离。

| 步骤 | 内容 |
|------|------|
| ① Manifest | `EngineActivity`（含 LuaActivity 继承链上的组件）声明 `android:process=":lua"` |
| ② 协议 | ActivityResult 跨进程天然可用（Binder 1MB 限制已有 200KB 截断）；`EngineResult` 改为 Parcelable 或复用历史文件协议（`engine_result.json`，WP-002 已有实现可回退） |
| ③ 编译链 | `LuaCompiler` 必须在引擎进程执行 → 新增 `LuaCompileService`（AIDL 同步编译）或改为「运行时编译兜底」（打包前引擎进程预热编译） |
| ④ 环境自举 | `:lua` 进程的 Application 持环境初始化（assets 已归位后自解包，天然自足）；宿主 `luaExtDir` 读 `/sdcard/AndroLua`（外部存储，双进程可读） |
| ⑤ 类加载 | `getClassLoaders()` 在当前进程内，不受影响；`InMemoryDexClassLoader` 在引擎进程内，语义不变 |

**收益**：引擎崩溃（SIGSEGV/GC 极限）不再拖垮 IDE；大 LuaState 堆与 IDE 隔离（`largeHeap` 可下放）；对齐 Flutter daemon 的进程边界。
**风险**：中。`:lua` 进程冷启动开销；Context/Asset/SharedPreferences 双进程双份；需回归 6 示例 + 探针矩阵（956 项双端对比已有基线）。

### 5.3 Tier 3：双 APK 环境即插即用（完全体，~2-3 周）

**目标**：`com.androlua` 环境独立 APK（自带 Application/Activity/Service/assets/jniLibs/AIDL 服务），IDE 经 PackageManager 动态发现 + 版本探测，实现真正的「VS Code + Flutter SDK」。

| 步骤 | 内容 |
|------|------|
| ① 环境 APK | 独立 `applicationId`（如 `com.lioisme.lide.env`），同签名；组件全量自持；AIDL `IEngineService`（launch/compile/getVersion） |
| ② 发现与版本 | IDE 经 `PackageManager.getPackageInfo` 探测环境安装与版本；版本探测可复用现成 **956 项引擎探针脚本**（lide 878/956 基线） |
| ③ 文件共享 | `/sdcard/AndroLua` 外部存储（API 30+ 需 `MANAGE_EXTERNAL_STORAGE`，IDE 已声明）；FileProvider 授权（`grantUriPermission`，`android.content.FileProvider` 已在环境侧） |
| ④ 打包链 | 打包器改用**环境模板 APK**（= pro `bin.lua` 模型：模板 + luac + manifest 补丁 + 签名）；环境 APK 内置为资产或首次运行时下载 |
| ⑤ 分发 | 商店双 APK 分发；环境可独立升级（兼容矩阵：IDE≥N ↔ 环境≥M） |
| ⑥ 兼容回退 | 环境缺失时 IDE 降级为「仅编辑」（第 4.1 节 ① 已保障编辑能力不依赖环境） |

**收益**：与 VS Code/Flutter 关系完全对齐；环境零改动升级（如补原生模块、换 Lua 版本）；IDE 可瘦身（-85K 行 Java -1.3MB .so）。
**成本**：双包签名/分发/存储权限链路；版本兼容矩阵维护；打包链改造；模拟器矩阵（x64 环境 APK 也要出）。

### 5.4 硬约束与不可行点（复述 §1.2 并给出对策）

| 约束 | 对策 |
|------|------|
| 魔改字节码只能由引擎编译 | 编译调用永远落在引擎进程/引擎 APK 内（Tier 2 AIDL / Tier 3 AIDL 服务）；`LuaCompiler` 不迁出引擎 |
| LuaState 不可跨进程 | 运行与编译都以「引擎侧执行、结果回传」为唯一形态；宿主侧永不持有 LuaState |
| W^X dex 加载 | 保持 `InMemoryDexClassLoader`；环境 APK 的可写目录无 dex 文件路径加载 |
| R8 反射面 | `luaApiSurface` 29 类 keep 规则随环境自持；`verifyReleaseLuaSurface` 任务迁移到环境模块 |

---

## 6. 契约（engine-api）草案

> Tier 1 落地物。纯 Kotlin 接口 + 数据类，`:app` 编译期只依赖此面。

```kotlin
// engine-api（纯 Kotlin，零 Android 框架之外依赖）
interface EngineRuntime {
    val luaExtDir: File                    // 工程目录约定 /sdcard/AndroLua
    fun initialize(context: Context)       // 环境自举（目录 + 脚本解包 + luaCpath）
    fun version(): EngineVersion           // {engine: "AndroLua 4.4.2", lua: "5.3.3(m)", modules: 17}
    fun probe(): EngineProbeResult         // 956 探针（可选，诊断用）
}

interface EngineLauncher {
    fun createLaunchIntent(projectDir: String, mainFile: String, args: Array<Any?>?): Intent
    // 运行结果经 ActivityResult 回传：EngineResult(output, error, location)
}

interface EngineCompiler {
    fun compile(source: String, chunkName: String): CompileResult
    // 成功 → ByteArray（魔改字节码）；失败 → LuaCompileException(line, msg)
}

data class EngineResult(
    val output: String,          // 截断 200KB
    val error: Boolean,          // ERROR_PATTERN 命中
    val location: ErrorLocation? // {file, line}，宿主 EngineResultParser 解析
)
```

- 实现侧：`:androlua` 提供 `AndroLuaRuntime : EngineRuntime`、`LuaActivityLauncher : EngineLauncher`、`NativeLuaCompiler : EngineCompiler`。
- 边界：`R2` 修订为「编译与运行经 `engine-api` 执行，宿主不直接触碰 `com.luajava.*`」；R1 门禁扩展至 `:app`（白名单除外）。

---

## 7. 风险登记

| # | 风险 | 等级 | 缓解 |
|---|------|------|------|
| R-1 | 资产归位破坏 `extractLuaScripts` 的资源路径（assets 从 :app 移到 :androlua 后 `getAssets()` 仍指向合并后的 APK 资源——AAR 资源合并后路径不变，需回归验证） | 中 | 归位后立即跑 6 示例 + 探针矩阵 |
| R-2 | 进程隔离后 `getClassLoaders()`/SharedPreferences 双份，插件与设置跨进程不一致 | 中 | 明确宿主/引擎数据边界；设置走宿主文件传递 |
| R-3 | `:lua` 进程冷启动拖慢首次运行 | 低 | 预热进程（`startService`）+ 启动屏 |
| R-4 | Tier 3 双 APK 的 MANAGE_EXTERNAL_STORAGE 双授权体验 | 中 | IDE 侧统一引导；环境降级提示 |
| R-5 | 打包链改用环境模板 APK 后，模板与宿主版本漂移 | 中 | 模板随环境 APK 内置，构建时校验版本 |
| R-6 | R8 keep 规则分裂（环境独立 keep 面） | 低 | `luaApiSurface` 迁移 + 环境侧 `verifyReleaseLuaSurface` 任务 |

---

## 8. 建议路线图

| 阶段 | 内容 | 工作量 | 验收 |
|------|------|--------|------|
| **P0 资产归位** | jniLibs + 环境 assets 移入 `:androlua`；`:app` 引用收窄 | 0.5 人日 | 6 示例 + 探针 878/956 基线回归 |
| **P1 契约层**（Tier 1） | `engine-api` 接口 + 继承改造（LideApp 组合化）+ R2 修订 | 1.5-2 人日 | `:app` 对 `com.luajava` 直接 import 归零；编译链经 `EngineCompiler` |
| **P2 进程隔离**（Tier 2） | `:lua` 进程 + AIDL 编译服务 + 结果协议回退 | 3-5 人日 | 引擎崩溃不影响 IDE（注入崩溃探针验证）；双端探针矩阵全绿 |
| **P3 双 APK**（Tier 3，可选） | 环境独立 APK + 发现/版本 + 模板打包 | 2-3 周 | 卸载环境后 IDE 仍可编辑；安装环境即运行；新旧环境并存切换 |

**决策建议**：P0+P1 无争议应立即执行（零行为变化、纯结构优化）；P2 是「Flutter daemon 收益」的主要兑现点，推荐做；P3 视分发渠道需求决定，且 P0-P2 的产物（契约 + 资产归位 + 模板化打包）恰好是 P3 的全部前置，不存在返工。

---

## 9. 三路线专项分析（2026-08-02 追加）

> 用户提出三条技术路线：**路线 A** 双 APK 插件化（安装式 + 文件夹式）；**路线 B** 单 APK + 微信小程序技术；**路线 C** 其他路线。本节逐条判定。

### 9.1 路线 A：双 APK 插件化（环境 = lide 的插件）

用户描述「apk 插件可以安装被识别和调用，也可以把 apk 放到插件文件夹里被识别和调用」——这对应 Android 插件化生态的两种成熟形态。lide 能否实现，取决于环境（androlua）的特殊性：**环境不是纯逻辑插件，它带 Activity 组件生态**（LuaActivity 控制台、LuaService、LuaAccessibilityService、ScreenCaptureActivity）。这是判定路线 A 可行性的分水岭。

#### A-1 安装式插件（APK 安装后被识别和调用）：✅ 完全可行，无技术障碍

| 环节 | 实现机制 | 成熟度 |
|------|---------|--------|
| 识别 | 环境 APK 声明唯一 `action`（如 `com.lioisme.lide.ENV`）的 AIDL Service + meta-data 版本号；IDE 经 `PackageManager.queryIntentServices` 发现，`getPackageInfo(pkg, GET_SIGNATURES)` 校验**同签名**防假冒 | 官方 API，Tasker/Termux:API 同款模式，成熟 |
| 调用 | AIDL `IEngineService`（`getVersion` / `compile` / `launch` / `registerCallback`）；运行 UI 用环境 APK 自持的 `EngineActivity`（注册在环境 Manifest，`android:process=":lua"`），IDE `startActivityForResult` 拉结果 | 官方 API，成熟 |
| 组件 | 环境 APK 自持全部组件（LuaActivity/LuaService/无障碍/FileProvider），`nativeLibraryDir`/assets 自动归属环境 → **继承链、loadlayout、.so 加载全部零改动** | — |
| 文件共享 | 同 keystore 签名 + `/sdcard/AndroLua` 外部存储（IDE 已声明 MANAGE_EXTERNAL_STORAGE）+ `grantUriPermission` | 需真机回归 |
| 版本 | 契约版本号（int）+ 956 项探针脚本（lide 878/956 基线）双保险 | 现成资产 |

**结论：安装式 = 上轮报告 Tier 3 的细化，唯一需要新写的代码是 AIDL 服务层（~200 行）与发现/校验逻辑（~100 行）。环境侧运行时零改动。**

#### A-2 文件夹式插件（APK 放入插件目录被识别和调用）：⚠️ 可行，但有 4 条硬边界

机制：IDE 扫描插件目录（如 `/sdcard/AndroLua/plugins/*.apk`）→ 对每个 APK 做免安装动态加载。

| 加载项 | 可行性 | 依据 |
|--------|--------|------|
| dex | ✅ | 从 APK zip 读 classes.dex 字节 → `InMemoryDexClassLoader` 内存加载（W^X 兼容，**项目已有先例**：`LuaDexLoader` V-01/V-02 真机验证）；父加载器 = 宿主 ClassLoader |
| .so | ✅ | 从 APK zip 解压到 `filesDir/lib` → `System.load`（Android 允许私有目录 so 加载；链接器映射为 RX，不受 W^X 限制；Termux 等大量先例）。顺序约束：先 `libluajava.so` 再其余（附加模块 `LOCAL_SHARED_LIBRARIES := luajava`） |
| assets（lua 脚本/模板/密钥） | ✅ | 直接 `ZipFile` 读取 zip 条目解包，或反射 `AssetManager.addAssetPath`（API 30+ 受限，**规避：约定插件资源全放 assets 而非 res**——androlua 资产恰好全在 assets） |
| 版本识别 | ✅ | 项目已有 `AXmlManifest`（二进制 AXML 解析器）+ `plugin.json` 先例，可读插件 APK 的 manifest/meta 文件 |
| **Activity/Service 组件** | ❌ **不可用** | **免安装 APK 的组件未经 PackageManager 注册，无法以组件形态运行**。LuaActivity/LuaService/LuaAccessibilityService/ScreenCaptureActivity 全部失效。DroidPlugin/RePlugin 的桩 Activity + hook AMS 方案在 Android 10+ 日益受限（隐式广播限制、前台服务限制），且为 3 个组件引入整框架不值 |

**A-2 的正解：环境重塑为「运行时库」形态（宿主组件壳 + 环境运行时）**

```
IDE 宿主（壳，组件自持）                    环境插件（运行时库，无组件）
─────────────────────────              ─────────────────────────────
EngineActivity（宿主注册）              env.apk
├─ 控制台 UI（现有实现保留）        ├─ dex：com.luajava + com.androlua
├─ sendMsg/sendError 收集        │    + com.android.cglib + 工具层
├─ 结果回传（现有实现保留）        ├─ so：libluajava + 16 附加模块
└─ 反射实例化 IEnvironmentBootstrap ├─ assets：lua/31 + layouts + keys
     → LuaState/initENV/loadlayout └─ plugin.json（版本/入口/API level）
```

- **必须做的唯一重构**：把 `LuaActivity` 的「宿主职责」（initENV、生命周期桥、`activity` 全局注入）抽成可反射调用的 `LuaEnvironment` 运行时类（环境侧）；宿主 `EngineActivity` 改为直接实现控制台 + 生命周期语义（现状宿主已重写 sendMsg/sendError/getLuaPath/finish，语义面已经很薄，抽离工作量约 1-2 人日）。
- **能力降级清单**（文件夹模式下）：`LuaService` 后台脚本、无障碍服务、`ScreenCaptureActivity` 不可用 → 插件内声明「能力位」`plugin.json`，宿主按位展示降级提示。安装式插件不受影响（可全能力）。
- **崩溃隔离**：运行时库形态下环境 dex/so 进宿主进程 → **无进程隔离**（这是与安装式的本质差别）；但宿主现有 `try/catch(Throwable)` 降级纪律可兜底大部分，`libluajava` 的 SIGSEGV 类崩溃仍是同进程风险 → 如需隔离，文件夹式必须配合 `:lua` 进程壳（即 A-2 + Tier 2 组合）。

**A-2 结论**：可以实现，且**四个基础件全是项目现成能力**（InMemoryDexClassLoader 先例、AXmlManifest、plugin.json、文件协议）。代价 = 环境运行时库化重构（1-2 人日）+ 组件能力降级表。用户了解的项目「APK 放文件夹被识别」之所以能做成，是因为其插件是纯逻辑插件（无 Activity 生态）；androlua 环境带组件生态，文件夹形态必须以「运行时库 + 宿主壳」重塑。

#### A 路线总结

| 子形态 | 可行性 | 组件生态 | 进程隔离 | 工作量 |
|--------|--------|---------|---------|--------|
| 安装式（A-1） | ✅ 全能力 | 100% | ✅（环境自持进程） | AIDL ~300 行 |
| 文件夹式（A-2） | ⚠️ 能力降级 | 仅运行时 | ❌（除非组合 :lua 进程） | 重构 1-2 人日 + 加载器 ~300 行 |
| **推荐组合** | **安装式为主（默认能力）+ 文件夹式为备（降级）**，共用同一契约 | — | — | — |

### 9.2 路线 B：单 APK + 微信小程序技术

#### B-1 该技术栈是什么

微信小程序架构（FinClip/mPaaS 等容器同理）四大件：

| 件 | 微信实现 | 技术本质 |
|----|---------|---------|
| 逻辑层 | JS 引擎（V8/JSCore），**独立线程** | 脚本语言 + 独立执行线程 |
| 渲染层 | WebView（WXML 组件树）/ Skyline 自渲染 | **与逻辑层隔离**的渲染器 |
| 双线程通信 | JSBridge + `setData` 差分同步 | 消息通道 + 数据驱动 |
| 编译期 | webpack 编译 WXML/WXSS/JS → 虚拟 DOM 差分 | 声明式 UI 编译器 |

关键认识：**小程序技术的核心不是「语言」，是「逻辑/渲染双线程隔离 + 沙箱 + 数据驱动渲染」**。语言换成 Lua 在技术上是等价的（Lua 线程 + 桥 + 声明式 UI）。

#### B-2 可行性判定：技术上可做，方向上错位，成本是再造一个 FinClip

| 维度 | 判定 | 依据 |
|------|------|------|
| 技术可行 | ✅ | Lua 引擎现成；双线程 + 桥是成熟模式；声明式 UI 已有 `loadlayout` 家族可当 DSL 底座 |
| 工程难度 | 🔴 **高**（数月~年） | 需要：Lua↔UI 消息桥（协议设计 + 序列化 + 回调用例）、声明式 UI 编译/解释器、渲染层（WebView 或原生自渲染）、双线程生命周期同步、差分更新引擎——等于自研 FinClip |
| 生态断裂 | 🔴 **致命** | 小程序模型 = **受限 API 面**（沙箱内只能调白名单 API）；AndroLua 的立身之本 = **全量 API 面**（`luajava.bindClass` 任意 Java 类、loadlayout 直操作 View、`import` 任意 dex）。双线程隔离下 Lua 无法直触 View/Java 对象，**现有 .alp 工程、6 个样例、pro 生态脚本全部不兼容** |
| 性能 | 🔴 倒退 | 双线程 + 消息桥 + 数据同步的每次 UI 更新都有序列化开销；AndroLua 同线程直操作 View 是理论上限 |
| 解决问题了吗 | ❌ **没有** | 小程序架构的价值 = **沙箱安全隔离**（宿主 App 运行第三方不可信代码）。lide 是本地 IDE 运行**用户自己的代码**，用户代码本来就拥有全量权限——lide 没有「沙箱需求」，却要为它付出生态断裂 + 性能 + 工程量的代价 |
| 可借鉴子集 | ✅ | 「逻辑/UI 分离」已有等价物（`LuaThread`/`LuaTimer`/`LuaAsyncTask`）；「数据驱动渲染」若将来要可视化布局设计器，可单独立项（增量式，不动运行时） |

**B 路线结论：不推荐。** 它解决的是 lide 不存在的问题（第三方代码隔离），牺牲的是 lide 的核心资产（全量 API 生态）。除非未来场景变为「lide 作为宿主让第三方小程序运行」（商业模式变化），否则不立项。

### 9.3 路线 C：其他技术路线

| # | 路线 | 可行性 | 说明 |
|---|------|--------|------|
| C-1 | **AAR 依赖式**（现状 + 资产归位） | ✅ 零风险 | 环境以 Gradle 模块/AAR 编译进 IDE APK；版本跟随 IDE。这是任何方案的地基（P0/P1） |
| C-2 | **同 APK 进程隔离**（`:lua` 进程，引擎 Activity 或 AIDL EngineService） | ✅ 中成本 | 上轮报告 Tier 2。**变体：Bound Service 形态**——IDE `bindService` 到 `:lua` 进程的 `EngineService`，launch/compile/log 流全走 AIDL（最贴近 Flutter daemon：daemon 是服务不是 UI）；运行 UI 由服务拉起同进程 EngineActivity |
| C-3 | **运行时下载包**（非 APK 形态的环境 = zip 目录） | ✅ 低成本 | 环境发布为 zip（`runtime.dex` + `lib/*.so` + `lua/` + `plugin.json`），IDE 下载/内置到私有目录 → InMemoryDexClassLoader + System.load + 解包。**与 A-2 文件夹式共享全部边界**（组件不可用 → 运行时库形态），但不需要 APK 格式。适合「环境随 IDE 版本内嵌、可热更新」 |
| C-4 | **引擎多版本并存**（C-2/C-3/A-2 的增值） | ✅ | 插件目录/私有目录放多版本，IDE 按工程切换——「VS Code 多 Flutter SDK」同款能力；版本契约 + 956 探针现成 |
| C-5 | **Termux 式**（环境走系统包管理器/终端） | ❌ 排除 | lide 是手机 IDE 场景，与终端工作流错位 |
| C-6 | **Xposed/Magisk 模块形态**（环境做成 root 模块） | ❌ 排除 | 依赖 root，分发与调试成本不可接受 |

### 9.4 三路线决策矩阵

| 用户诉求 | 路线 | 判定 | 一句话 |
|---------|------|------|--------|
| 双 APK，环境当插件（含「APK 放文件夹」） | A | ✅ **采纳（推荐）** | 安装式全能力 + 文件夹式降级，共用契约；四个基础件全是项目现成能力 |
| 单 APK + 微信小程序技术 | B | ❌ **否决** | 解决不存在的问题（沙箱），牺牲核心资产（全量 API 生态），工程量 FinClip 级 |
| 其他路线 | C | ✅ 采纳子集 | C-1（地基）→ C-2（进程隔离，推荐）→ C-3/C-4（热更新/多版本，增值） |

**最终推荐组合**（与 §8 路线图合并）：

```
P0 资产归位（C-1）          — 0.5 人日，必做
P1 契约层 engine-api        — 1.5-2 人日，必做（A-2/C-3 的前提）
P2 进程隔离 + EngineService（C-2 变体）— 3-5 人日，推荐
P3 安装式插件 APK（A-1）    — 1-2 周，主形态
P4 文件夹式插件（A-2）      — 1-1.5 周，备形态（含 LuaActivity 宿主壳重构）
P5 运行时下载包/多版本（C-3/C-4）— 视需求，复用 P4 全部机制
```

P1-P4 的依赖链：P1 契约是 P3/P4 的共同协议面；P4 的「宿主壳 + 运行时库」重构产出物（LuaEnvironment 抽离）也是 P3 环境 APK 的内部结构——**两条支线共用同一重构，不存在返工**。

---

## 10. 附录：验证命令

```
R1 门禁：        rg -n "luajava|androlua|loadLibrary" editor/src          # 必须零命中
契约门禁（P1 后）：rg -n "com\.luajava|com\.androlua" app/src/main/kotlin  # 仅白名单
编译检查：        .\gradlew.bat :app:compileArm64DebugKotlin --console=plain
完整构建：        .\gradlew.bat :app:assembleArm64Debug --console=plain
单测：            .\gradlew.bat :app:testX64DebugUnitTest :editor:testDebugUnitTest :androlua:testDebugUnitTest
引擎重建：        scripts\rebuild_engine.ps1
探针回归：        x64 模拟器 + ADB（双端 pro/lide 对比，基线 878/956）
```

---

*审计完成。数据来源：三模块全量源码、`app/AndroidManifest.xml`、`app/build.gradle.kts`（含 `luaApiSurface`）、`androlua` 模块 Java/Kotlin 源、`docs/completed/{FULL_AUDIT_REPORT,NATIVE_LIB_AUDIT,ANDROLUA_SOURCE_CODE_AUDIT_REPORT,DEPENDENCY_MODEL_AUDIT_REPORT}.md`、`docs/kb/{ARCHITECTURE,ENGINE_RUNTIME}.md`、`PROGRESS.md`、`WHITEPAPER.md`、AndroLua Pro 4.4.2 参考树。行数为 PowerShell 统计值（含空行/注释）。*
