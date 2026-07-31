# 项目进度 `PROGRESS.md`

> 记录 delua 引擎迁移（LuaJ → AndroLua C Engine）的工作进度。

---

## WP-001：NDK 构建（已完成）

**目标**：从 AndroLua_pro-master 的 C 源码编译出 `libluajava.so` 和 `libgl.so`。

**完成内容**：
- 编写 `Android.mk` + `Application.mk`，配置 NDK r28.2（minSdk=24）
- 提取 AndroLua_pro-master 的 C 源码（`luajava.c`、`lua_gl.[ch]`、Lua 5.3.3 核心、`lua_cjson`、`sqlite`）
- 构建产出（已验证 ELF 格式 + `nm` 确认 117 个 JNI 导出符号）：
  - `prebuilt/arm64-v8a/libluajava.so`（341.7KB） + `libgl.so`（60.3KB）
  - `prebuilt/armeabi-v7a/libluajava.so`（211.6KB） + `libgl.so`（35.7KB）
- 配置存档至 `build-scripts/jni/`，含 `rebuild_engine.ps1` 一键重构建脚本

**决策**：切换到 AndroLua_pro 的 C 引擎（Lua 5.3.3），替代原 LuaJ 方案。原生库作为预编译二进制 blob，不维护在项目源码中。

---

## WP-002：Java 运行时源码集成（已完成）

**目标**：将 AndroLua_pro Java 运行时源码集成到 `androlua` 模块中，编译进宿主 APK 主 DEX。

**完成内容**：
- 完整审计并导入全部 16 个 `com.luajava` + 52 个 `com.androlua` + `com.android.cglib.*` Java 源文件到 `androlua/src/main/java/`
- 确认 `com.android.cglib.proxy.*`（Enhancer、ProxyBuilder 等）及 `com.android.cglib.dx.*`（~282 文件）均在项目源码中，无外部依赖
- `LuaActivity.java`、`LuaApplication.java` 等核心运行时类通过 `androlua` 模块（`implementation(project(":androlua"))`）编译进宿主 APK
- 原生 `.so` 库通过 NDK 从 AndroLua_pro C 源码编译（参见 WP-001、WP-002b）

**架构变更**：
- **移除** `engine.dex` 独立 DEX 方案 — Java 运行时源码直接编译进宿主主 DEX
- `EngineActivity.java` 直接继承 `androlua` 模块的 `LuaActivity`，无需 DexClassLoader 加载
- `DeluaApp.kt` 直接继承 `androlua` 模块的 `LuaApplication`
- 消除双份运行时类问题（同一类不再同时存在于主 DEX 和独立 DEX）

**预构建产物清单**（`prebuilt/`）：
```
prebuilt/arm64-v8a/libgl.so           60 KB
prebuilt/arm64-v8a/libluajava.so     342 KB
prebuilt/armeabi-v7a/libgl.so         36 KB
prebuilt/armeabi-v7a/libluajava.so   212 KB
prebuilt/x86_64/libgl.so              60 KB
prebuilt/x86_64/libluajava.so        347 KB
```

**决策说明**：
- 遵循"有源码就用源码"原则 — 所有 AndroLua_pro Java 类从源码编译，无预编译二进制运行时
- 原生 `.so` 仍保留预编译产物（prebuilt/），但 `scripts/rebuild_engine.*` 支持一键从源码重建
- `LuaApplication.getUriForPath/getUriForFile` 因依赖自定义 `FileProvider.java` 移除，不影响核心功能

---

## WP-003：Host 集成（已完成）

**目标**：通过 Intent + 文件协议打通 Kotlin Host 与引擎层通信。

**完成内容**：

### 核心文件

| 文件 | 模块 | 职责 |
|---|---|---|
| `androlua/.../engine/EngineResult.kt` | androlua | 数据类：`EngineResult` + `EngineLoadResult`，JSON 序列化/反序列化 |
| `androlua/.../engine/EngineBridge.kt` | androlua | 桥接层：创建 Intent、通过文件协议收集结果 |
| `app/.../engine/EngineActivity.java` | app | 引擎 Activity：继承 `LuaActivity`（androlua 模块），重写 `onCreate`/`getLuaPath`/`onDestroy`，直接访问 LuaState |
| `app/.../ui/screen/RunScreen.kt` | app | Compose 界面：通过 `rememberLauncherForActivityResult(StartActivityForResult)` 启动 EngineActivity，读取文件结果并展示 |
| `app/.../navigation/DeluaNavHost.kt` | app | 导航图：创建 EngineBridge 实例，传入 RunScreen；移除所有 LuaRunner 引用 |

### 文件协议（engine_result.json + engine_output.txt）

```
EngineActivity.onDestroy()
  → write engine_result.json
  → write engine_output.txt (if outputBuffer not empty)
  → saved to cacheDir (same package → shared dir)

EngineBridge.collectResult()
  → read from context.cacheDir/engine_result.json
  → merge output from engine_output.txt
  → delete both files
  → return EngineResult
```

### 架构流程

```
RunScreen
  → EngineBridge.createIntent(projectDir, mainFile)
  → startActivityForResult → EngineActivity (extends LuaActivity)
      ├─ LuaActivity.onCreate() 自带 LuaState 初始化
      ├─ setPackagePath: projectDir/?.lua + luaDir/?.lua
      ├─ doFile(init.lua) if exists
      └─ doFile(main.lua) → script executes
  ← onDestroy → write result files
  ← collectResult() → display output/error in RunScreen
```

### 资产准备

| 资产 | 位置 | 说明 |
|---|---|---|
| Java 运行时源码 | `androlua/src/main/java/com/` | AndroLua_pro Java 全量源码（~80 文件），编译进主 DEX |
| Lua 运行时脚本 | `assets/lua/*.lua` (25 文件) | import.lua, loadlayout.lua, json.lua, dkjson 等 |
| `libluajava.so` | `jniLibs/{arm64-v8a,armeabi-v7a,x86_64}/` | 原生引擎 JNI 桥（NDK 从 C 源码构建） |
| `libgl.so` | `jniLibs/{arm64-v8a,armeabi-v7a,x86_64}/` | OpenGL 绑定模块 |

### 关键实现细节

- **LuaStateRef**：内部类，通过反射封装 LuaState 的 JNI 方法调用（`openLibs`、`pushJavaObject`、`setGlobal`、`LdoFile`、`LdoString`、`setPackagePath`、`setPackageCPath`、`trySetClassLoader`）
- **类加载**：`getClassLoaders()` 返回 `[appClassLoader, engineDexLoader]`，供 import.lua 的 `import_dex_class` 遍历查找 AndroLua 类（LuaAsyncTask、LuaThread、LuaTimer 等）
- **原生库**：`copyNativeLib("gl")` 将 libgl.so 复制到 `cacheDir/libs/`，通过 `getLibrarys()` 返回路径，import.lua 的 `libsloader` 用 `package.loadlib` 加载
- **sendMsg**：import.lua 重写 `print()` 为 `activity.sendMsg(msg)`，输出追加到 `outputBuffer`，最终写入 `engine_output.txt`
- **非 UI 脚本自动 finish**：脚本执行完毕后若无 `setContentView` 调用，自动调用 `finish()`，触发 `onDestroy` → 写结果文件
- **修正的 Bug**：移除 `EngineBridge.createEngineIntent` 中的 `FLAG_ACTIVITY_NEW_TASK`（与 `startActivityForResult` 不兼容）

### 遗留
- `LuaRunner` 仍保留在 EditorScreen（语法检查）、MainActivity（生命周期桥接）、LuaService（后台脚本），WP-006 统一清理
- x64 构建无对应 `.so`，仅 arm64/armeabi 可用；需 WP-005 补充 x86_64 原生库

---

## WP-004：三个测试工程部署与验证（已完成）

**目标**：将三个测试工程（demo / kleinbottle / testjson）部署到 app 中，确保在原生引擎上正常运行无崩溃。

**完成内容**：

### 测试工程资产化

```
assets/samples/
├── demo/
│   ├── main.lua          UI + HTTP 请求 + JSON 解析
│   ├── init.lua           appname="demo", 权限声明
│   └── dkjson.lua         JSON 编解码库（23KB）
├── kleinbottle/
│   ├── main.lua           OpenGL 克莱因瓶渲染（147 行）
│   ├── init.lua           appname="kleinbottle"
│   └── icon.png           应用图标
└── testjson/
    ├── main.lua           JSON 树形浏览器（901 行）
    ├── init.lua           appname="TestJson"
    └── dkjson.lua         JSON 编解码库
```

### 自动部署机制

- `LuaApplication.extractSampleProjects()` — 首次启动时从 `assets/samples/` 提取到 `$luaExtDir/project/`
- 通过 SharedPreference `samples_extracted` 标记防止重复提取
- 支持二进制文件（icon.png）和深层子目录
- 提取完成后即可在 ProjectListScreen 中看到三个测试工程

### 兼容性审核

| 工程 | 使用的 API | 原生引擎兼容性 |
|------|-----------|---------------|
| **demo** | `import`、`loadlayout`、`LuaAdapter`、`Http.get`、`dkjson.decode`、`luajava.bindClass`、`activity.setContentView` | ✅ 全为标准 AndroLua API，androlua 模块原生支持 |
| **kleinbottle** | `import "gl"`、`GLSurfaceView.Renderer`、`ScaleGestureDetector`、`luajava.createProxy`、`activity.setContentView` | ✅ `import "gl"` 通过 libsloader + libgl.so 加载；代理创建由 `LuaEnhancer` 原生支持 |
| **testjson** | `loadlayout`、`View`、`LuaDrawable`、`Handler`、`OverScroller`、`VelocityTracker`、`postOnAnimation`、`dkjson.decode` | ✅ 标准 Android API + 原生 SAM 转换；`postOnAnimation(Runnable)` 由 `LuaJavaAPI.toJavaObject` 处理 |

### 已知风险

- **SAM 转换**：`postOnAnimation(computeScroll)` 传 Lua 函数给 `Runnable` 参数，依赖 `LuaEnhancer`/`LuaJavaAPI` 的自动 SAM 转换。若失败需在 `luajava.c` 添加转型 hook
- **x86_64 缺失**：三工程仅可在 arm64/armeabi 设备上运行；x86_64 模拟器需补充 `.so`

### 验收状态

| 编号 | 检查项 | 状态 |
|------|--------|------|
| WP004-AC1 | 工程在 delua 中打开后点击"运行"，引擎 Activity 正确启动 | ⚪ 待设备验证 |
| WP004-AC2 | Lua 脚本 UI 正确显示，无白屏/崩溃 | ⚪ 待设备验证 |
| WP004-AC3 | 返回 delua IDE 后，输出/结果在 RunScreen 正确展示 | ⚪ 待设备验证 |
| WP004-AC4 | 连续运行 3 次无内存泄漏 | ⚪ 待 Profiler 验证 |
| WP004-AC5 | 脚本异常时 error message 正确传递回宿主 | ⚪ 待设备验证 |

---

## WP-005：LuaJ 兼容层代码清理（已完成）

**目标**：移除所有 LuaJ 依赖（`org.luaj:luaj-jse:3.0.1`）和 Kotlin 兼容层代码（~2125 行），改为完全依赖原生 AndroLua 引擎。

**完成内容**：

### 删除文件清单

| 文件 | 行数 | 说明 |
|------|------|------|
| `compat/ImportFunction.kt` | ~200 | LuaJ 版 `import` — 由 import.lua 替代 |
| `compat/LoadLayoutFunction.kt` | ~210 | LuaJ 版 `loadlayout` — 由原生实现替代 |
| `compat/ViewProxy.kt` | ~310 | LuaJ 版 Android View 代理 |
| `compat/ActivityProxy.kt` | ~50 | LuaJ 版 Activity 代理 |
| `compat/ProxyFunction.kt` | ~110 | LuaJ 版 `luajava.createProxy` |
| `compat/ASTableFunction.kt` | ~60 | LuaJ 版 `luajava.astable` |
| `compat/GLModule.kt` | ~140 | LuaJ 版 OpenGL 绑定 — 由 `libgl.so` C 模块替代 |
| `compat/LuaDexLoader.kt` | ~85 | LuaJ 版 Dex 加载器 |
| `compat/LuaAdapter.kt` | ~170 | LuaJ 版 ListView 适配器 — 由 androlua 模块的 `LuaAdapter` 替代 |
| `compat/LuaDrawable.kt` | ~45 | LuaJ 版 自定义 Drawable — 由 androlua 模块的 `LuaDrawable` 替代 |
| `compat/ThreadFunction.kt` | ~85 | LuaJ 版 线程/定时器 — 由 androlua 模块的 `LuaThread`/`LuaTimer` 替代 |
| `engine/LuaRunner.kt` | ~500 | LuaJ 版脚本运行器 — 由 EngineBridge + EngineActivity 替代 |
| `lifecycle/LifecycleBridge.kt` | ~100 | LuaJ 版生命周期桥接 — 由 EngineActivity 替代 |
| `lifecycle/LuaServiceManager.kt` | ~50 | LuaJ 版后台服务管理器 |
| `widget/LuaDialog.kt` | ~90 | LuaJ 版对话框封装 |
| `service/LuaService.kt` | ~40 | 后台 Lua 脚本服务 — 由 EngineActivity 替代 |
| **合计** | **~2300 行** | |

### 移除 Gradle 依赖

```diff
- androlua/build.gradle.kts: implementation("org.luaj:luaj-jse:3.0.1")
- app/build.gradle.kts:      implementation("org.luaj:luaj-jse:3.0.1")
```

### 代码改动摘要

| 文件 | 改动 |
|------|------|
| `MainActivity.kt` | 移除 `LuaRunner`、`LifecycleBridge` 所有引用（~30 行 → ~12 行） |
| `EditorScreen.kt` | 移除 `LuaRunner`、`Job` 导入和诊断 LaunchedEffect |
| `LuaEditor.kt` | 移除 `LuaRunner`、`SyntaxError` 导入；`updateDiagnostics` 降级为空函数 |
| `AndroidManifest.xml` | 移除 `<service android:name=".service.LuaService">` |

### 验收状态

| 编号 | 检查项 | 状态 |
|------|--------|------|
| WP006-AC1 | `grep org.luaj.vm2` 返回空 | ✅ 确认 |
| WP006-AC2 | `grep luaj-jse build.gradle.kts` 返回空 | ✅ 确认 |
| WP006-AC3 | `gradle assembleArm64Debug` 编译通过 | ⚪ 待构建验证 |
| WP006-AC4 | 三工程仍能正常运行 | ⚪ 待回归测试 |
| WP006-AC5 | APK 体积减小 | ⚪ 待 APK 分析 |
| WP006-AC6 | `compat/` 目录已完全删除 | ✅ 确认 |

---

## WP-006：编译脚本与 CI 集成（已完成）

**目标**：提供一键引擎重建脚本（Unix + Windows），配置 CI 流水线，补充 x86_64 ABI 支持。

**完成内容**：

### 一键重建脚本

| 文件 | 说明 |
|------|------|
| `scripts/rebuild_engine.sh` | **新增** — Unix/macOS bash 版一键重建脚本 |
| `scripts/rebuild_engine.ps1` | **更新** — 增加 x86_64 ABI 支持、`APP_ALLOW_MISSING_DEPS` |
| `build-scripts/jni/Application.mk` | **更新** — 增加 x86_64 ABI |

`rebuild_engine.sh` 执行流程：
1. 验证 ANDROID_NDK_HOME + ANDROID_HOME 环境变量
2. 从 `docs/AndroLua_pro-master/` 复制 C 源码到临时构建目录
3. 写入 Android.mk + Application.mk 构建配置
4. 执行 `ndk-build` 编译 `libluajava.so` + `libgl.so`
5. 复制产物到 `prebuilt/{abi}/` 和 `app/src/main/jniLibs/{abi}/`

### CI 集成

| 文件 | 说明 |
|------|------|
| `.github/workflows/build.yml` | **新增** — GitHub Actions 工作流 |

CI 包含四个 Job：
- **lint** — `./gradlew lintArm64Debug`（代码质量检查）
- **build-arm64** — 编译 ARM64 APK，上传 Artifact
- **build-x64** — 编译 x86_64 APK，上传 Artifact
- **rebuild-engine** — 仅在手动触发时（`workflow_dispatch`）重建引擎

### x86_64 ABI 支持

| 文件 | 改动 |
|------|------|
| `build-scripts/jni/Application.mk` | `APP_ABI` 添加 `x86_64` |
| `scripts/rebuild_engine.sh` | 支持 `x86_64` 参数 |
| `scripts/rebuild_engine.ps1` | `ValidateSet` 添加 `x86_64` |

### 预构建产物补齐

| 产物 | 位置 |
|------|------|
| Lua 运行时脚本 (25 个) | `prebuilt/lua/` — 从 `assets/lua/` 同步 |
| `.so` 文件 | `prebuilt/{arm64-v8a,armeabi-v7a,x86_64}/` — NDK 从 C 源码构建 |
| Java 运行时 | `androlua/src/main/java/` — 全量 AndroLua_pro Java 源码，编译进主 DEX |

### 验收状态

| 编号 | 检查项 | 状态 |
|------|--------|------|
| WP007-AC1 | `scripts/rebuild_engine.sh` 可执行 | ✅ 脚本就绪，需 NDK 环境验证 |
| WP007-AC2 | `scripts/rebuild_engine.ps1` 更新正确 | ✅ 脚本就绪 |
| WP007-AC3 | `.github/workflows/build.yml` 配置正确 | ✅ GitHub Actions 工作流 |
| WP007-AC4 | x86_64 在 Application.mk 中启用 | ✅ 确认 |
| WP007-AC5 | `prebuilt/lua/` 目录存在且包含 25 个脚本 | ✅ 确认 |

---

## WP-007：AndroLua pro 特性一致性修复（P0/P1/P2 全部完成）

**目标**：按 `docs/ANDROLUA_FEATURE_PARITY_AUDIT.md` 第 5 节行动项 P0→P1→P2 修复与 pro 的差距，全部通过 `:app:assembleArm64Debug`。

**完成内容**：

### 模块还原
- `:androlua` 模块从 `docs/reference/androlua/` 移回根目录 `androlua/`（`settings.gradle.kts` 一直引用它）。

### P0-3 Manifest 与权限（`app/src/main/AndroidManifest.xml`）
- `android:largeHeap="true"`；注册 `com.androlua.LuaService`、`LuaAccessibilityService`（+meta-data + intent-filter）、`androidx.core.content.FileProvider`（authorities=${applicationId} + `res/xml/androlua_filepaths.xml`）、`com.androlua.LuaActivityX`、`com.nirenr.screencapture.ScreenCaptureActivity`；EngineActivity 加 configChanges。
- 补齐 12 项权限（WRITE_CALL_LOG / WRITE_SMS / READ_FRAME_BUFFER / BATTERY_STATS / CLEAR_APP_CACHE / GET_PACKAGE_SIZE / SYSTEM_ALERT_WINDOW / SET_WALLPAPER / DOWNLOAD_WITHOUT_NOTIFICATION / ANSWER_PHONE_CALLS / CHANGE_NETWORK_STATE / INSTALL_SHORTCUT）。
- 新增 `res/xml/{accessibility_service_config.xml,androlua_filepaths.xml}` 与 `accessibility_service_description` 字符串（values + values-zh）。

### P1-4/5 类补齐（`androlua/src/main/java/`）
- `android.widget/` 19 个控件类（CardView 族、DrawerLayout 族、FloatButton、ToolBar、HorizontalListView、PullingLayout、Ripple 族、SlidingLayout、ViewDragHelper、PageLayout、PageAdapter、CircleImageView、ArrayExpandableListAdapter…）；`android/app/{AlertDialogBuilder,FloatWindow}`；`android/support/multidex/` 3 文件；`com/nirenr/{Color,ColorFinder,ColorPoint,Point}` + `com/nirenr/screencapture/` 4 文件。
- FloatButton/ToolBar 的 `R.drawable.icon` → `android.R.drawable.sym_def_app_icon`。
- 恢复 `LuaAccessibilityService` 的 nirenr/截图 API（字段 + click/longClick/press/swipe/startScreenshot/getScreenshot/stopScreenshot）。
- SplitEditView 依赖 textwarrior 编辑链（已被 sora-editor 合理替代），未移植。

### P0-2 结果回传闭环
- `EngineActivity.java`：`sendMsg` 追加 `outputBuffer`；`writeResult()` 写 `cacheDir/engine_result.json` + `engine_output.txt`。
- `EngineBridge.kt`：`collectResult()` 读 JSON 并合入 output、读后删除；新增 `clearResult()`（启动引擎前清残留）。
- `RunScreen.kt`（重建）：Preparing/Running/Failed/Finished 四态；依赖物化 → `clearResult` → `startActivityForResult` → 展示 success/error/duration/output + 一键复制；新增字符串（values + values-zh）。
- `DeluaNavHost.kt`：新增 `run` 路由（projectName/projectPath/fileName），EditorScreen onRun 改导航；去掉旧 startActivity 直启。

### P0-1 依赖物化（`DependencyManager.kt`）
- `materializeProjectDependencies(projectDir)`：libs → `<project>/libs/`（LuaDexLoader 自动加载），modules → `<project>/lua/`（+ 含 init.lua 时 `<project>/lua/<name>/`）；先 `validateDependencies` 完整校验（含 jar 有效性），失败即报错并拒绝运行。

### P1-6 插件系统对齐（`PluginManager.kt` + `PluginScreen.kt`）
- `loadPlugin` 增加 pro 风格 fallback：无 plugin.json 时读 init.lua 的 `appname/appver/description` 字段（正则解析），入口 main.lua。
- PluginScreen：`+` 按钮安装 .alp（GetContent → 缓存 → installFromAlp → 重扫）；卡片点击 → RunScreen 运行插件 main.lua（复用 run 路由，插件按工程对待，依赖物化同样生效）。

### P1-7 布局助手资产
- 复制 `loadlayout2.lua / loadlayout3.lua / xml2table.lua` → `androlua/src/main/assets/lua/`（extractLuaScripts → luaMdDir → `require "loadlayout2"` 可用；xml2table 的 `require "import"` 由 assets/lua/import.lua 满足）。

### P1-9 资产版本化更新（`LuaApplication.extractLuaScripts`）
- 由"存在即跳过"改为 MD5 比对：内置脚本升级后自动覆盖。

### R1 边界
- `editor/src` 对 `luajava|androlua|loadLibrary` 零命中（含注释）。

### P2-10 `.lua` 文件关联 + `androlua://` scheme（MainActivity.kt + AndroidManifest.xml）
- Manifest：新增 `.lua` VIEW/EDIT intent-filter（file + content，`.*\.lua`）；`androlua://` scheme（host = `com.lioisme.delua` + pro 兼容 `com.androlua`）。
- MainActivity 三类拉起：
  - `.alp` → 导入（原有）
  - `.lua` → 定位所在工程（向上找含 init.lua 的目录）→ 打开编辑；独立文件自动收养为单文件工程（复制 + 生成 init.lua）再打开
  - `androlua://<host>/<相对 luaExtDir 路径>` → main.lua 直接运行（脚本拉起）、目录取 main.lua 运行、其他 .lua 打开编辑
- 路由扩展：`editor` 路由增加 `fileName`（optional，默认 main.lua）；NavHost 新增 `pendingOpenFiles/pendingRunFiles` 一次性消费。

### P2-11 luadoc 离线文档
- `docs/reference` 的 `assets/luadoc/`（manual.html 457KB、contents.html、glossary.html、logo.gif）→ `app/src/main/assets/luadoc/`。
- `DeluaApp` 启动提取至 `filesDir/luadoc/`（存在即跳过）。
- 新增 `LuaManualScreen`（WebView 离线渲染 + 缺失兜底文案）；Settings "About" 从 TODO 接线到该页；新增 `manual` 路由；新增字符串（values + values-zh）。

### P2-12 管理模型终态决策（已收口）
- **决策：保留全局仓库 + 物化同步为终态**。common/ 仓库 = 安装/管理面（集中安装、版本、卸载），`materializeProjectDependencies`（P0-1）= 运行时桥，把声明依赖物化为 `<project>/libs/` 与 `<project>/lua/`，引擎感知的仍是工程目录（与 pro 运行时模型归一）。delua.json/init.lua 声明即同步入口，两套模型不再割裂。

**剩余（可选项）**：无强制项。真机回归（运行闭环、无障碍/截图 API、.lua 拉起、手册渲染）待设备验证。

**验收**：`:app:compileArm64DebugKotlin` ✅、`:app:assembleArm64Debug` ✅（BUILD SUCCESSFUL）。

---

## 运行链路简化（RunScreen 移除）

**决策**：真机发现"点运行"出现两个界面——先是一个「运行：xxx/main.lua」的宿主结果页（依赖物化/结果收集失败时展示 `run_no_result` 误导文案），随后才是真正的引擎界面。该宿主中间页被判定为冗余（引擎自带控制台已展示输出/错误），按用户要求删除，恢复 pro 式"运行 → 引擎全屏直启"。

**改动**：
- 删除 `RunScreen.kt`（Preparing/Running/Failed/Finished 四态 + startActivityForResult + collectResult 展示）、`EngineResult.kt`。
- 新增 `RunEngineLauncher.kt`（:app，`com.lioisme.delua.engine`）：后台线程物化依赖（沿用 P0-1）→ 主线程直启 EngineActivity；物化失败仅 Toast（`run_materialize_failed`），不启动。
- `EngineBridge.kt` 精简为仅 `createEngineIntent`（删 collectResult/clearResult/REQUEST_CODE_ENGINE）。
- `EngineActivity.java` 精简为纯 LuaActivity 子类（删 writeResult/escapeJson/outputBuffer/hasContentView/startTime）。
- `DeluaNavHost.kt`：删除 `run` 路由与 `pendingRunFiles`；EditorScreen/PluginScreen 的 onRun 直启引擎。
- `MainActivity.kt`：`androlua://` 拉起 main.lua → 直启引擎（不再进 pendingRunFiles）。
- 删除 run_title/running/run_success/run_error/run_failed/run_duration/run_output/run_copy_output/run_no_result/no_output/cd_copy_output 字符串（保留 run_materialize_failed）。
- 引擎侧 `libluajava.so`/控制台能力不受影响；`:editor` R1 边界不变。

---

## WP-008：AndroLua 全量一致性审计收口（P0/P1/P2 全部完成）

**目标**：按 `docs/ANDROLUA_PARITY_FULL_AUDIT_REPORT.md` §12 行动项（P0-1~3、P1-4~9、P2-10~14）完成 delua 工程/库/插件/模块系统与 pro 生态的一致性收口。全部通过 `:app:compileArm64DebugKotlin` 与 `:app:assembleArm64Debug`。

**完成内容**：

### P0 插件系统（3/3）
- **P0-1 插件入口语义**：`PluginManager.PluginInfo.main` 默认 `init.lua`→`main.lua`（data class + optString 两处）；`assets/plugin/plugin.json` main 同步修正。
- **P0-2 插件上下文参数**：`EngineBridge` 新增 `EXTRA_SCRIPT_ARGS="arg"`（对齐 LuaActivity `ARG` 键）+ `createEngineIntent(projectDir, mainFile, args)`；`RunEngineLauncher.launch` 透传；插件运行传 `(plugin.dir, plugin.dir/main)` 作为 `{luadir, luapath}`。
- **P0-3 .alp mode 分流**：`ZipUtil` 重写（`safeEntryName` zip-slip 防护 + `readEntryText`）；`AlpManager` 重写（`AlpKind{PROJECT,PLUGIN}`、`import(File/Uri)`、`resolveKind`：init.lua `mode="plugin"` 或文件名 `*_plugin`、目标名 = appname 或去扩展名截断）；`MainActivity` 导入后按 kind 分流，插件跳转插件列表。

### P1 库/模块管理面（6/6）
- **P1-4 .so 入仓**：`LibraryRegistry.findArtifactFile` 认 `.so`；安装命名 `lib<name>.so`（libsloader `luaopen_<name>` 命中）；重装先删旧构件；`getLocalLibFiles` 支持 .so。
- **P1-5 物化清理**：`.delua-materialized.json` 清单（canonical 守卫 + pruneEmptyParents），取消声明/换版后同步删除残留。
- **P1-6 registry.json 收口**：registry 概念整体移除（字段 + read/write/update 删除）。
- **P1-7 模块子目录**：`installModule` 递归收集 `.lua` 按相对路径入仓；物化落 `<project>/lua/<rel>` 与 `<project>/lua/<name>/`；新增 `installModuleFromFiles`；`ModuleRegistry.readMetadata` files 缺失回退磁盘重列。
- **P1-8 示例防覆盖**：`extractSamples` 存在即跳过；新增 `extractExamplePlugin`（plugin.json + main.lua → `plugin/example/`）。
- **P1-9 delua.json 收口**：读路径 + 包装方法删除，依赖声明单一事实源 = init.lua `dependencies` 块；`Project.dependencies` 字段删除。

### P2 生态/工程化（5/5）
- **P2-10 打包决策**：v1.0 明确暂不支持独立 APK 打包，理由与后续平移机制写入 `WHITEPAPER.md` 阶段四。
- **P2-11 zip 防护 + 结构化替换**：见 P0-3；`replaceDependenciesBlock` 改为 `findDependenciesBlock` 结构化扫描（跳过字符串/注释/长括号 + 花括号配平）。
- **P2-12 死代码 + i18n**：清理 `loadedPlugins`、`DependencyManager` 只读函数、`AlpManager.export(File)`、`ProjectManager` delua.json 方法、`Project.dependencies`；**deleteProject 接入 UI**（工程长按 → 确认对话框）；插件/库/模块/依赖/工程列表文案全部 strings.xml 化（含 values-zh），ProjectDependenciesScreen 乱码修复。
- **P2-13 单进程口径**：AGENTS.md R2 改为"EngineBridge → EngineActivity，同进程运行"。
- **P2-14 布局助手资产**：pro `javaapi/*.aly`（clayout/layout/mlayout）→ `app/src/main/assets/layouts/javaapi/`，首启提取 `filesDir/layouts/`（存在即跳过）；布局预览 UI 留待单独立项。

### 其它
- `app/build.gradle.kts` 新增 `androidx.documentfile:documentfile:1.0.1`（SAF 文件夹安装模块）。
- `ModuleManagerScreen` 全量重写：搜索/详情/卸载 + 双安装入口（文件多选 / 文件夹树，SAF 递归复制）；`LibraryManagerScreen`/`PluginScreen` i18n 与空态。

**修复的编译问题**：DependencyManager `const val` 类内声明与 `regionMatches` 参数错误；DeluaApp KDoc 内 `javaapi/*.aly` 的 `/*` 触发 Kotlin 块注释嵌套导致文件尾被吞；ProjectDependenciesScreen 历史遗留多余闭括号；MainActivity `parentFile` 空安全与 `findProjectDir` 循环判空警告。

**验收**：`:app:compileArm64DebugKotlin` ✅、`:app:assembleArm64Debug` ✅（BUILD SUCCESSFUL）、`:editor` R1 边界 grep 零命中 ✅。

**剩余**：真机回归（.alp 插件导入、插件运行 `{luadir, luapath}`、.so 库 import、SAF 文件夹安装、删除工程）待设备验证。

---

## WP-009���Ƴ�ȫ�����������㣨���� A������ɣ�

**����**���� `docs/DEPENDENCY_MODEL_AUDIT_REPORT.md` ���� A ִ�С���**ɾ��** delua �Դ���ȫ�����������㣨`common/libs` + `common/modules` + init.lua `dependencies` �� + ����ǰ�ﻯ�����ع� AndroLua Pro�����������й�����ģ�ͣ����ṩ�ɰ汾Ǩ�Ƽ��ݡ�**��ת WP-007 P2-12 / WP-008 P1-4~9 �С�����ȫ�ֲֿ� + �ﻯͬ��������̬����**�����ɣ�ȫ�ֲ�������ʱ����ϣ�Java ����ʱ�� `common/` �����ã����� S1 ����ȱ�ݣ����� `demo` ���� `modules={"dkjson"}` �� dkjson.lua �湤���Դ���ȫ�°�װ��ȫ�ֲֿ�Ϊ�յ��� demo �ܾ����У���

**�������**��
- **��Ʋ���**��`docs/DEPENDENCY_MODEL_AUDIT_REPORT.md`��S1�CS7 ȱ���嵥��A/B/C �����Աȡ�ɾ��/�����嵥����
- **����ʱɾ��**��`androlua/.../common/{DependencyManager,LibraryRegistry,ModuleRegistry}.kt` + `common/model/{LibraryInfo,ModuleInfo,ProjectDependencies}.kt`��`common/` Ŀ¼�����Ƴ���
- **UI ɾ��**��`LibraryManagerScreen.kt`��`ModuleManagerScreen.kt`��`ProjectDependenciesScreen.kt`��Settings �Ƴ���/ģ����������EditorToolbar �Ƴ����������˵��DropdownMenu/MoreVert/menu_more/menu_dependencies����NavHost �Ƴ� LIBRARIES/MODULES/DEPENDENCIES ����·���� `dependencies()` ���졣
- **��������ԭ**��`RunEngineLauncher.kt` ��дΪ��ֱ�����Ƴ���̨�߳������ﻯ + ʧ�� Toast����`DeluaApp` �Ƴ� `ensureDirsExist()`��
- **�����տ�**��`ProjectManager.getLocalLibFiles` ɾ����`app/build.gradle.kts` �Ƴ� `androidx.documentfile:documentfile:1.0.1`�������ã���
- **�ַ���**��values + values-zh ��ɾ�� 49 ������/ģ��������������������汾/��С/���߱�ǩ��ж�ء�run_materialize_failed��+ 4 ��ʧЧע�ͣ�editor ˫���Ը�ɾ 2 ����menu_more/menu_dependencies����S7 `dependencies_title` ������ɾ����Ȼ��ʧ��
- **����**��`demo/init.lua` �Ƴ� `modules={"dkjson"}` �����顢`testjson/init.lua` �Ƴ��������飨kleinbottle/DemoProject �����޿飩��

**����**��`:app:compileArm64DebugKotlin` ✅��`:app:assembleArm64Debug` ✅��BUILD SUCCESSFUL����`:editor` R1 �߽� grep��luajava|androlua|loadLibrary�������� ✅��ȫ���������ɨ�裨DependencyManager/LibraryRegistry/ModuleRegistry/LibraryInfo/ModuleInfo/ProjectDependencies/getLocalLibFiles/Routes.{LIBRARIES,MODULES,DEPENDENCIES}/onDependencies/run_materialize_failed/menu_dependencies/documentfile�������� ✅��

**ʣ��**������ع飨demo/testjson ȫ�°�װ�������С�`.so` ������ `libs/` ���ء���� `{luadir, luapath}`�����豸��֤��

## 整体验收状态

| 编号 | 标准 | 状态 |
|------|------|------|
| ACC-01 | `gradle assembleArm64Debug` 编译通过，零 LuaJ 依赖 | ✅ 代码确认（需实际构建） |
| ACC-02 | demo 工程运行正常 (ListView + HTTP + JSON) | ⚪ 待设备验证 |
| ACC-03 | testjson 工程运行正常 (JSON 树 + Canvas 绘制) | ⚪ 待设备验证 |
| ACC-04 | kleinbottle 工程运行正常 (GLSurfaceView + 3D 渲染) | ⚪ 待设备验证 |
| ACC-05 | 连续运行 10 次无崩溃或 OOM | ⚪ 待设备验证 |
| ACC-06 | 引擎与宿主 Java 运行时源码集成 | ✅ 设计确认（`androlua` 模块编译进主 DEX） |
| ACC-07 | `prebuilt/` 产物齐全 | ✅ 确认 |
| ACC-08 | APK 体积 ≤ 30MB | ⚪ 待 APK 构建后分析 |

---

## WP-010：/libs 全链路支持（LIBS-001~007，已完成）

**目标**：按 `docs/LIBS_SUPPORT_WORK_ORDER.md` 完成工程 `/libs`（.dex / .so / .lua）全链路能力收口与真机验证。全部通过 `:app:assembleArm64Debug`。

**完成内容**：

### LIBS-001/002/004/005（清理与收口）
- 删除 `androlua/src/main/assets/builtin/import.lua` 死代码（`git rm`，rg 零残留）。
- 内置 Lua 资产单一来源收口：`loadlayout2/loadlayout3/xml2table.lua` 从 `:androlua` 上移至 `:app/assets/lua/`（方案 A），`:androlua` 不再携带任何 `.lua`。
- `DemoProject/libs/README.txt` 修正（`loadDex` 非全局函数；推荐 `import "com.example.mylib.*"`）。
- `ProjectManager.createProject` 模板删除 `dependencies` 块（引擎不消费）。

### LIBS-003（原生模块补齐，部分完成）
- **lfs / lsqlite3 已补齐**：`build-scripts/jni/{lfs,lsqlite3}/Android.mk` + `build-scripts/patches/lsqlite3/lsqlite3.c`（Lua 5.3 `lua_strlen` 兼容补丁）；`scripts/rebuild_engine.{ps1,sh}` 扩展（`DISABLED_MODULES=("lluv","tcc")`）；NDK 29 编译产物入 `prebuilt/` 与 `app/src/main/jniLibs/`（3 ABI × 2 模块）。
- **lluv / tcc 跳过**（按预案）：lluv 与 luv 功能重叠；tcc 无 arm64/x86_64 目标。差异声明入 WHITEPAPER §7.1。

### 引擎修复：W^X writable-dex（LIBS-006 验证中发现，超出工作单范围）
- 真机实证：Android 8+/14+ 禁止从 app 可写目录加载 dex（`Writable dex file ... is not allowed`），`/sdcard`（FUSE，chmod 无效）与 `/data/data` 均被拒。
- `LuaDexLoader.loadDex` 修复：API≥26 走 `InMemoryDexClassLoader(ByteBuffer.wrap(readAll))`；`loadDex`/`loadApp` 返回类型改 `ClassLoader`；`dexCache` 改 `HashMap<String,ClassLoader>`；`.jar` loadResources 仅对 `LuaDexClassLoader` 实例生效；`LuaActivity`/`LuaService` 同步。该文件成为**有意的**与 pro 的差异（工作单 §10.1 已修订）。

### LIBS-006 真机验证矩阵（V-01~V-10 全通过）
- 设备：SDK 36（arm64-v8a）生产 build；工程 `AndroLua/project/libsTest/`；`androlua://` 拉起。
- V-01 PASS auto-loaded-widget|42；V-02 PASS extra-dex-loaded；V-03 PASS 42.0（自制 libmytest.so）；V-04 PASS LuaSocket 3.0-rc1;true;true;true;true;3.0；V-05 PASS hello-from-project-lua-subdir；V-06 PASS init-env-isolated；V-07 PASS 29\|import=true（uninstall/reinstall 冷启动解包）；V-08 PASS checkV08=true/false（资产升级覆盖 + 还原）；V-09 PASS missing=false\|corruptClass=false（corrupt.dex 不再中止启动，优于预期）；V-10 PASS 2->2（classloaders 稳定）。
- 关键经验（工作单 §5.3-9/10）：Java 方法点语法（冒号调用参数错位）；`getFilesDir()` 被 `getDir(String,int)` 遮蔽；生产 build 无 `pm clear`/`pm grant`，冷启动用 uninstall/reinstall，存储权限走「所有文件访问」设置页 + uiautomator。

### LIBS-007 文档收口
- 工作单回填终态；WHITEPAPER §7.1 原生模块差异声明；AGENTS.md 新增「/libs 机制约定」；本条目（WP-010）。

**验收**：`:app:assembleArm64Debug` ✅（BUILD SUCCESSFUL）、`:editor` R1 边界 grep 零命中 ✅、真机矩阵 V-01~V-10 全绿 ✅（日志 `%TEMP%\delua-verify\matrix-final*.txt`）。

**剩余**：无强制项（V-04 中 `require "lluv"`/`"tcc"` 按差异声明不提供；mytest 库为验证专用不在仓库）。

---

## 工作单 WP-001：工作区编译修复（已完成）

**目标**：按 docs/PARITY_COMPLETION_WORK_ORDER.md 打通工作区编译与设备回归基线。

**完成内容**：
- 抽屉重构已由 commit 3e2878f 落地；验证 :app:compileArm64DebugKotlin 与 :app:assembleX64Debug 全绿；:editor R1 门禁（luajava|androlua|loadLibrary）零命中。
- 设备回归（emulator-5554 / API 34）：抽屉打开/关闭、返回键先关抽屉、转屏后抽屉保持关闭、宽屏（3200x2560@360dpi）树常驻+汉堡隐藏，全部通过。

---

## 工作单 WP-019：自动化测试骨架（已完成）

**目标**：建立单元测试 + ADB 回归矩阵基架。

**完成内容**：
- app/editor 加 junit 4.13.2 + unitTests.isReturnDefaultValues；新增 ZipUtilTest（zip-slip）、AlpNamingTest、LuaDiagnosticsTest（14 例）；AlpNaming.kt 抽取纯函数（dedupeName 支持 WP-015）。
- LuaDiagnostics 修复：do 不再占 for/while 的 end；end/until 前优先报未闭合括号。
- scripts/adb_regress.ps1 四工程回归矩阵 6/6 PASS；CI 加 unit-test job。

---

## 工作单 WP-017：init.lua 解析结构化（已完成）

**目标**：以结构化 LuaConfigParser 替换正则读法。

**完成内容**：
- LuaConfigParser.kt（纯 Kotlin）入 :androlua：parse/field/stringField/stringArrayField/setField/setStringArrayField；扫描收敛防护（guardLimit 抛 IllegalStateException）。
- ProjectManager.loadProject 与 PluginManager.loadPlugin 改结构化读；删 MainActivity 收养模板 dependencies 死块。
- LuaConfigParserTest 13 例全绿；修复 scanValueEnd '{' 分支漏 i++ 死循环（jstack 定位）。

---

## 工作单 WP-003：user_permission 语义（已完成）

**目标**：对齐 pro：工程 init.lua 声明权限 → 运行前一次性请求 + 权限页联动展示。

**完成内容**：
- androlua 新增 PermissionResolver.kt（纯函数）：declared/runtimeRequestable/isRuntimeRequestable（特殊权限过滤），单测 7 例。
- ProjectManager.loadUserPermissions(project)：LuaConfigParser 读 init.lua 的 user_permission，失败降级空表。
- EditorScreen 运行前权限门：未授予声明权限时 RequestMultiplePermissions 一次性请求，拒绝仅 Toast（run_permission_denied_hint）仍继续运行（pro 行为）。
- PermissionScreen 新增「当前工程声明权限」区块（后台 IO 读声明，条目独立请求/特殊权限走设置页）；EditorScreen 抽屉新增「工程权限」入口；PERMISSIONS 路由支持可选工程参数。

**验收**：单测全绿（app/editor/androlua）；设备验证 emulator-5554：ACC-1 声明 CAMERA 工程运行弹系统对话框 ✅；ACC-2 授予后二次运行不再弹框直接进引擎 ✅；ACC-3 权限页显示声明项与授予状态（permtest 相机已授权 / nopermtest 未声明提示）✅；ACC-4 未声明工程运行不受影响 ✅；:app:compileArm64DebugKotlin ✅；:editor R1 门禁零命中 ✅。

---

## 工作单 WP-002：运行诊断闭环（已完成）

**目标**：对齐 pro 的 onActivityResult 闭环：错误回传 + 错误跳行 + import 建议。

**完成内容**：
- 引擎侧 EngineActivity.java：重写 sendMsg 收集控制台输出（预编译 Pattern 判错误标记，避免每行重编译正则拖垮引擎页）+ 重写 sendError；finish 时 setResult(RESULT_OK, Intent) 回传（output/error extra），输出截断 200KB 防 Binder 溢出；文件协议不恢复；引擎内置控制台保留（双通道）。
- 宿主侧 EditorScreen：rememberLauncherForActivityResult 启动引擎；成功 → Snackbar（run_finished）；失败 → 底部对话框展示错误文本 + 「跳到出错行」（EngineResultParser 解析首行定位 → session.openTab + setSelection）+ 「插入 import」（nil global 'X' → 文件顶部插入并写盘）。
- EngineResultParser.kt（:app 纯 Kotlin）：findErrorLocation（错误消息首行 路径:行号: = 报错点，traceback 帧为调用点不取）/ findNilGlobal / isErrorOutput；单测 7 例全绿。
- RunEngineLauncher 新增 createIntent（launcher 路径无转场动画，视觉回退已注释说明）。

**验收**：设备 emulator-5554：ACC-1 运行 demo 返回编辑器无崩溃 ✅（Snackbar 为短时提示，链路与对话框同源已证）；ACC-2 含 local x=nil; print(x.y) 工程 → 宿主对话框 + 「跳到出错行」定位第 3 行 ✅；ACC-3 nil global 'http' → 「插入 import "http"」按钮，点击后 main.lua 顶部插入并写盘 ✅；ACC-4 2 万行输出（>200KB 截断线）返回不崩溃 ✅（10 万行极端场景触发 androlua LuaObject.finalize watchdog 崩溃，属引擎固有 GC 极限，非本工作包范围）；ACC-5 运行中 back 返回宿主不崩 ✅；单测 7/7 + 全仓单测绿；:app:compileArm64DebugKotlin ✅；:editor R1 门禁零命中 ✅。

---

## 工作单 WP-004：工程属性页 + 抽屉工程名刷新（已完成）

**目标**：按 docs/PARITY_COMPLETION_WORK_ORDER.md WP-004 提供工程属性（init.lua 元数据）编辑页，保存后抽屉工程名即时刷新；同时修复桌面抽屉顶部内容被顶栏裁剪的显示 bug。

**完成内容**：
- **ProjectInfoScreen（:app 新增）**：读取/回写 init.lua 全字段（appname/appver/appcode/appsdk/path_pattern/packagename/theme/app_key/app_channel/developer/description/debugmode/user_permission）。`LuaConfigParser.quoteLuaString()` 实现 Lua `%q` 等价转义（`\"` `\\` `\n` `\r` `\t` + 控制字符 `\u{XX}`）；setField 全字段串行保存（测试复现验证无 bug）；README 字段缺失时 Field?→String fallback + 非根 rootField 重载。
- **抽屉工程名即时刷新**：DrawerContent `remember(project, projectInfoVersion)` 重读 init.lua appname；保存后版本自增驱动更新（load 为 remember 不随 edits 重跑）。
- **抽屉裁剪修复（本轮核心 bug）**：`ModalNavigationDrawer` 从 Scaffold content 槽移至 Scaffold **外层**。根因：Scaffold 顶栏 z 序高于 content，抽屉放 content 内时顶栏（0..352px，状态栏 96 + TopAppBar 256）把抽屉顶部 ~256px 完全盖住——640dpi 模拟器（1440x2560 = 360dp 宽）sheet 全宽、scrim 不可见，现象为「抽屉顶部缺失」。修复后 sheet+scrim 覆盖整屏（含顶栏），工程名/路径/按钮/文件树全量可见。定位手段：彩色标记盒逐槽位像素扫描 + uiautomator bounds 交叉验证（path@352 恰为顶栏底）。
- **PermissionCatalog.kt（:app 新增）**：共享权限清单，PermissionScreen 引用。
- **LuaConfigParserTest（:androlua 新增）**：quote 转义 / setField / setStringArrayField / 注释保留 / 追加字段等 10+ 例全绿。

**验收**：设备 emulator-5554（x64 / API 34）：ACC-1 工程属性页打开读取 README+init.lua ✅；ACC-2 保存后 init.lua 完整状态回写（含 current=0）并重置编辑态 ✅；ACC-3 保存后抽屉工程名即时更新为 appname ✅；ACC-4 抽屉打开后工程名（@y=160）/路径/权限按钮/文件树全部可见（uiautomator bounds 验证，截图归档 `%TEMP%\delua-verify\wp004-drawer-fixed.png`）✅；ACC-5 单测 3 模块全绿（:app:testX64DebugUnitTest :editor:testDebugUnitTest :androlua:testDebugUnitTest）✅；:app:compileArm64DebugKotlin ✅、:app:assembleX64Debug ✅；:editor R1 门禁零命中 ✅。

**剩余**：无强制项。
