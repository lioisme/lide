# lide 架构知识库

> 来源：`AGENTS.md`、`WHITEPAPER.md`、`ANDROLUA_INTEGRATION_PLAN.md`、`ENGINE_MIGRATION_WORK_ORDER.md`、
> 来源：历史审计报告（`EDITOR_CRASH_ANALYSIS_REPORT.md`、`LIBS_SUPPORT_WORK_ORDER.md`、`DEPENDENCY_MODEL_AUDIT_REPORT.md`，均已删除，结论蒸馏于此）、
> `DECOUPLING_AUDIT_REPORT.md`（2026-08-11 解耦审计工作单，全项完工后已删除，结论见 §3.5）。
> 权威边界规则以 `AGENTS.md`（实时）为准，本文档为解释性索引。

---

## 1. 总体拓扑（单进程多层）

```
settings.gradle.kts: :app + :androlua + :sora-editor（:sora-editor:editor + :sora-editor:language-textmate，vendored 于 app/vendor/sora-editor）
（2026-08-08 源码组织审计 :editor 模块并单：编辑器代码并入 :app，包 com.lioisme.lide.editor）
```

| 层 | 模块 | 包 | 职责 |
|----|------|----|------|
| IDE 宿主 | `:app` | `com.lioisme.lide` + `com.lioisme.lide.editor`（编辑器包并入） | Compose UI、项目管理、运行拉起、权限、设置持久化、打包器、纯文本级编辑器能力 |
| 引擎运行时 | `:androlua` | `com.androlua` / `com.luajava` / `com.android.cglib`（纯 Java 源码）+ 少量纯 Kotlin 工具 | Lua 运行时（继承自 AndroLua_pro 4.4.2 源码，`implementation` 并入宿主根 `classes*.dex` 恰一份） |
| 编辑器底座 | `:sora-editor` | `io.github.rosemoe.sora`（vendored 0.24.6，LGPL-2.1） | 编辑器核心（editor 模块 + TextMate 语言支持） |

运行时模型：**单进程**（`EngineBridge` → `EngineActivity` 同进程 Activity 栈直启），无独立进程、无 engine.dex 动态加载（2026-08-09 引擎 dex 单份化：引擎类直接并入宿主根 classes*.dex，无任何内嵌/标记资产形态）。

---

## 2. 架构边界 R1~R5（评审硬门槛）

| 规则 | 内容 |
|------|------|
| **R1** | 编辑器代码（包 `com.lioisme.lide.editor`）禁止出现 `com.androlua` / `com.luajava` / `System.loadLibrary` / jniLibs（`:editor` 模块已并单，2026-08-08）。验证：`rg -n "luajava|androlua|loadLibrary" app/src/main/kotlin/com/lioisme/lide/editor` 必须零命中 |
| **R2** | Lua 编译与运行能力属于 `:app` 宿主（`EngineBridge` → `EngineActivity`，同进程）。编辑器只做文本级能力，不做运行时语义 |
| **R3** | 新增编辑器功能默认放 `com.lioisme.lide.editor` 包；宿主专属逻辑（文件、权限、运行时、设置）放 `com.lioisme.lide` 其他包 |
| **R4** | `com.lioisme.lide.editor` 包不得读取宿主业务状态/类型；宿主一律通过参数/接口注入 |
| **R5** | 仓库禁止任何二进制原生产物入库（`.so`/`.dex`/`.jar`/`.zip`）；原生引擎唯一形态是源码（`app/src/main/cpp/`，AGP `externalNativeBuild`/ndk-build 构建，NDK 29.0.14206865）；无 jniLibs 缓存、无 rebuild 脚本 |

历史教训（`EDITOR_CRASH_ANALYSIS_REPORT.md`）：早期 `LuaDiagnostics` 从宿主导入 `com.luajava.LuaStateFactory` 进打字热路径，
导致 `lua_close(NULL)` SIGSEGV / GBK `NewStringUTF` SIGABRT 双崩溃向量，属**边界违规**而非引擎缺陷。

---

## 3. 关键机制

### 3.1 运行链路（结果回传闭环）

```
EditorScreen (rememberLauncherForActivityResult)
  → RunEngineLauncher.createIntent(projectDir, mainFile, args)
  → startActivityForResult → EngineActivity (extends LuaActivity)
       ├─ LuaActivity.onCreate：System.loadLibrary("luajava") → LuaStateFactory → pushJavaObject(this)=activity
       ├─ setPackagePath(projectDir/?.lua + luaDir/?.lua)
       ├─ doFile(init.lua) → doFile(main.lua)
       ├─ sendMsg 收集 print 输出 / sendError 收集错误
       └─ finish() 时 setResult(RESULT_OK, Intent)（output/error extra，截断 200KB 防 Binder 溢出）
  ← 结果解析：EngineResultParser（findErrorLocation / findNilGlobal / isErrorOutput，纯 Kotlin，7 单测）
  ← 失败 → 底部对话框 + 「跳到出错行」 + nil global 'X' → 「插入 import」
```

要点：
- 物化依赖（历史 P0-1 全局仓库方案）已随 WP-009 删除，`RunEngineLauncher` 为纯直启。
- 引擎内置控制台保留（双通道：引擎页可见 + 返回后宿主可查）。
- 引擎入口支持可选变长脚本参数（EXTRA_SCRIPT_ARGS="arg"，对齐 LuaActivity `ARG` 键，`RunEngineLauncher.launch` 第三参透传）——pro 对等能力保留，插件调用已于 2026-08-07 随插件功能移除，当前宿主无调用方。

### 3.2 引擎初始化时序（官方 demo 顺序，勿改）

```
colorScheme → setEditorLanguage → setText
```
- TextMate 运行时只经 `TextMateRuntime`（进程级一次性，`preload` 幂等触发；状态机 Loading/Ready/Failed，失败展示错误态 + 重试），**不得在 UI 线程调用**。
- 编辑器 API 调用（setText / diagnostics 赋值 / subscribeEvent 回调）保持主线程；文本快照可在后台线程取（`Content.toString` 内部 ReadWriteLock）。
- 自动保存：快照与写盘均在 `Dispatchers.IO`；`onRelease` 兜底保存（修过 dispose 顺序丢改动）。
- 大文件（>200 KB）：关闭括号高亮 / 当前块 / 自动换行，跳过行级诊断。
- 补全、诊断、事件回调必须 `try/catch(Throwable)` 降级，不得让异常逃逸进 sora 主循环。

### 3.3 /libs 机制（工程 libs 目录，机制约定见 AGENTS.md「/libs 机制约定」）

- 初始化时序不可变：`luaDir` 由 `EngineActivity.getLuaPath()` 设为 `projectDir` 后，`new LuaDexLoader(this)` + `loadLibs()`（LuaActivity.onCreate 顺序）。乱序导致 dex/so 静默不加载。
- `activity` / `service` 全局是 `import.lua` 的生命线（`local luacontext = activity or service`）。
- **dex 必须内存加载**：Android 8+/14+ W^X 拒绝从 app 可写目录加载 dex；`LuaDexLoader.loadDex` 在 API≥26 走 `InMemoryDexClassLoader` 字节加载，**禁止回退为文件路径加载**（V-01/V-02 真机回归兜底）。
- 脚本调 Java 方法用**点语法**（`activity.loadDex(path)`、`obj.mul(6,7)`）；冒号调用会把 self 推进栈导致参数错位。`getFilesDir()` 被 `getDir(String,int)` 遮蔽，需用 `getDir(...).getParentFile()`。
- 工程 `libs/lib*.so` 经 `libsloader` / `getLibrarys()` / `package.loadlib` 加载；宿主 APK 内置 .so 由 `nativeLibraryDir` 提供，两者入口不同，混淆会 `UnsatisfiedLinkError`。
- `libs/` 内容按不可信输入对待：dex/so 加载失败必须降级不崩溃，错误信息可读。

### 3.4 打包器（现状，见 WHITEPAPER §5 与 AGENTS.md 打包构建约定）

```
BuildApkDialog（打包 UI 唯一入口，编辑器 ⋮ 菜单触发；P5 打开即构建，无配置 UI）
  → LuaCompiler（LuaState loadbuffer + dump → .luac，去 BOM，错误带行号）
  → AXmlManifest（纯字节级 AXML 解析/补丁：包名/label/versionCode/uses-permission 白名单，无 Android 依赖）
  → ApkBuilder + ZipUtils（zip 重打包/过滤/对齐）
  → ApkSigner + V2Signer（v1 JAR + v2 Signing Block；v1-only 在 targetSdk≥30 不可安装）
```

打包配置唯一事实源（P5，2026-08-11）：构建时无任何可配置项——ABI（`preferAbi` 或宿主全部，
`ZipUtils.listAbis` 实测）、签名方案/档案、安装方式、stripNativeLibs、编译/加密、afterBuild、
产物目录/命名一律来自设置页持久化 `BuildOpts`；工程侧字段来自 init.lua（packagename/appver/appcode/
`appsdk`→targetSdk、`minsdk`→minSdk）；预检失败（init.lua 缺失/包名空）Toast 后关闭。

### 3.5 门面契约（2026-08-11 解耦审计 A/B/C/D 级收口，DECOUPLING_AUDIT_REPORT 完工蒸馏）

依赖矩阵（审计后现状，sora 泄漏面已归零）：

```
vendor sora (io.github.rosemoe.sora)   → 仅 editor 包（LuaEditor/EditorLanguage/LuaDiagnostics…）
vendor androlua (com.androlua/luajava) → engine/RuntimePaths（存储路径门面）、LideApp（继承点）、
                                         build/LuaCompiler（打包期）、apibrowser（反射/字符串）、
                                         EngineActivity（继承点）
editor 包                             → sora、android、compose、R（零宿主依赖，R4）
session 包                            → editor（EditorTab/EditorContent）、project、model
ui 包                                 → editor/session/engine/build/project/apibrowser/install/i18n
```

- **sora 门面（B 级）**：sora 类型只允许出现在 `editor` 包与 vendor 源码；宿主层经
  `EditorContent`（不透明文本句柄：`of`/`snapshot`/`isSameAs`）与 `EditorController`
  （命令门面：`bind`/`isShowing`/`setSelection`/`undo`/`redo`/`canUndo`/`canRedo`）访问。
  `:app:verifySoraIsolation` 守护（editor 包外零 `io.github.rosemoe`，import 与 FQN 双形态）。
  详细契约见 `docs/kb/EDITOR.md` §11。
- **引擎存储路径门面（C 级）**：`engine/RuntimePaths`（`luaExtDir()`/`projectsDir()`/
  `defaultApkDir()`）为 `LuaApplication` 存储状态唯一宿主入口，一律走 public getter
  `getLuaExtDir()`；UI / project / 根 Activity 禁止直触 `com.androlua.LuaApplication`
  （含 Kotlin 对 Java protected 字段的越权直读）；唯一豁免 = `LideApp`（继承点）与
  `RuntimePaths` 本体。改造前 8 处散落调用点（MainActivity×2 / AlpManager / LideNavHost /
  ExamplesScreen / ApiBrowserScreen / ProjectListScreen / BuildApkDialog FQN）已全部收拢。
- **引擎包前缀单一事实源（D 级）**：`apibrowser/EnginePackagePrefixes`
  （`ANDROLUA_PACKAGES` 全量引擎包 / `JNI_BRIDGE_PACKAGES` JNI 桥接子集）；
  `ApiCategoryClassifier`（ANDROLUA 归属）与 `ApiUsageClassifier`（UTILITY 归属）消费同表，
  新增引擎包只改该文件。

---

## 4. 数据/权限模型

| 事实源 | 说明 |
|--------|------|
| 工程元数据 | `init.lua`（appname/appver/packagename/user_permission/pages...）由 `LuaConfigParser`（结构化，非正则）读写 |
| 依赖声明 | **无全局仓库**：`common/` 已删除（WP-009 方案 A）；工程 = 自治单元，`libs/` + `lua/` 自管 |
| 权限 | 运行前按 `user_permission` 一次性 `RequestMultiplePermissions`（拒绝仅 Toast 继续运行，pro 行为）；`PermissionResolver` 纯函数 |
| 设置 | `SettingsManager`（SharedPreferences）+ `LocaleManager`（中英 i18n） |

---

## 5. 构建与验证速查

```
Kotlin 编译检查：  .\gradlew.bat :app:compileArm64DebugKotlin --console=plain
完整构建：        .\gradlew.bat :app:assembleArm64Debug --console=plain
模拟器包：        .\gradlew.bat :app:assembleX64Debug --console=plain
单测：            .\gradlew.bat :app:testX64DebugUnitTest :androlua:testDebugUnitTest
精简门禁：        .\gradlew.bat :app:verifyMinimalism（+ :app:verifyMinimalismDebug）
上游隔离：        .\gradlew.bat :app:verifyVendorIsolation
sora 门面隔离：   .\gradlew.bat :app:verifySoraIsolation
R1 门禁：         rg -n "luajava|androlua|loadLibrary" app/src/main/kotlin/com/lioisme/lide/editor
```

---

## 6. 代码质量收口约定（2026-08-09 CODE_QUALITY Q12–Q16 蒸馏）

新增/改动以下面时必读（硬约定，违反视为规范缺陷）：

- **反射缓存契约**：`ApiIndex.loadClassRef`（单张 `Class<?>?` 合并缓存，负缓存非空包装）与
  `ApiReflection.detailCache`（`ConcurrentHashMap` + `computeIfAbsent`，`CacheEntry` 非空包装）为
  唯一反射路径；**禁止自行 `Class.forName`、禁止返回 null 的 `getOrPut`**（CHM 拒绝 null，
  负缓存永久失效 → 全 dex 扫描 → ART 全局锁）。JVM 单测注意：mockable android.jar 含
  `android.widget.*` stub，负缓存用例必须用动态唯一类名（如 `...Nonexistent${System.nanoTime()}`）。
- **单例/日志约定**：单例可变状态字段加 `@Volatile`（如 `LocaleManager.currentLanguageTag`）；
  Log TAG 一律 companion 常量（`private const val TAG`，Q13 全量收口）；`Log.d` 调试路径加
  `Log.isLoggable(TAG, Log.DEBUG)` 守卫；**Log 消息统一英文**（内部日志，Q16 L12 全量统一）。
- **文案资源模板**：UI 显示文案禁止手工拼接（`"$a · $b"` / `"$c/$d"` 等）——整句进 strings.xml
  模板（`%1$s`/`%1$d`），values + values-zh 同步（380 键校验脚本）；Q14 落地 6 组模板
  （api_copied_format / api_category_count / setting_value_title / signing_cert_detail /
  signing_meta_line / cd_search_match_count）。注：`cd_search_match_count` 模板已随
  2026-08-09 编辑器搜索功能移除而删除（见 docs/kb/EDITOR.md §4.5），本行保留为 Q14 历史记录。
- **具名类型替代 Tuple**：跨模块传值禁止无语义元组（Q16 L1/L14 收口：
  `ZipUtils.ZipEntryData` 替代 `Triple<String,Int,ByteArray>`；`model.PendingOpenFile`
  替代 `Pair<String,String>`）。
- **字节偏移常量**：手写二进制解析（ZIP/arsc/dex）魔法偏移提命名常量（Q16 L4：
  `ZipUtils` EOCD/CEN 全部偏移与签名）。**谓词单一事实源**：扩展名判定等重复出现的
  内联条件提私有谓词（Q16 L8：`ApkBuilder.isLuaFile`）。
- 延迟项登记 backlog：`ZipUtils` CEN 游标访问（整包 readBytes 优化，P1-11）待打包链
  提交后端到端验证。

## 7. 动态主题与国际化契约（2026-08-12 THEME_I18N_SPEC_WORK_ORDER 完工蒸馏）

### 7.1 宿主色板 = Google material-color-utilities（HCT）

- **依赖**：`com.materialkolor:material-color-utilities:4.1.1`（官方 Java 版仅内嵌于
  MDC-Android AAR 无法单独取用，Kotlin 移植；JVM 单测直跑）。手工 HSL 派生
  （lighten/darken/rotateHue/contrastingColor）已全量删除——对比度无门禁风险。
- **生成**：`SchemeTonalSpot(sourceColorHct = Hct.fromInt(seed), isDark,
  contrastLevel = 0.0)`（M3 默认 TONAL_SPOT，与 Compose `dynamicColorScheme`
  同算法）→ 全量 50+ token 映射 Compose `ColorScheme`（亮/暗各一份）。
- **预设 seed 单一事实源**：`ui/theme/ThemeSeeds.OPTIONS`（20 预设色），
  SettingsScreen 色板页与 `ThemeContrastTest` 共用同一个表，禁止散落色值。
- **对比度门禁**：`ThemeContrastTest` 守护全部文本承载 token（HCT 生成 token +
  chrome 槽）× 20 seed × 亮/暗。**核心教训（2026-08-12）**：手工 HSL 在
  `container`/`onContainer` 上的对比度不可预测（曾产生 1.44:1 的近白容器色，
  视觉几乎不可见）——一切手动颜色派生皆为已删除的方向，新增色调一律经
  material-color-utilities 生成后过门禁。

### 7.2 国际化（默认英文基线 + per-app language）

- 默认资源（values/）全英文；中文走 values-zh；`locales_config.xml`（en/zh）
  声明系统语言支持（`android:localeConfig`）。
- **复数**：英文必须 plurals one/other 双档（`ResourceConsistencyTest` 守护），
  中文仅 other；`getQuantityString` 取文案（不做 int 拼接）。
- **字符串模板**：UI 一律模板（`%1$s`/`%2$d`），中英同名键参数集合必须一致
  （`ResourceConsistencyTest` 守护）；版本号 `%1$s v%2$s` + `BuildConfig.VERSION_NAME`。
- **错误文案**：`BuildException` 携带 `BuildErrorCode`（8 码 + GENERAL），UI 经
  `buildErrorRes` 映射 `build_error_*` 本地化文案；GENERAL/LUA_COMPILE 保留原始
  消息（编译器行号诊断价值）；Toast 不直出异常消息（Log.w 保留细节）。
- **per-app language（API 33+ / T21）**：写入 = 平台 `android.app.LocaleManager`
  `.setApplicationLocales`（**core 1.15.0 的 `LocaleManagerCompat` 无写入 API**，
  javap 实锤仅 getSystemLocales/getApplicationLocales）；读取 =
  `LocaleManagerCompat.getApplicationLocales`（`systemAppLocalesTag`）。
  `TAG_SYSTEM` → 空列表 = 清空 per-app locale 跟随系统。系统「语言偏好」面板
  与 App 内选择**双向**生效；启动态 `resolveTag(prefTag, systemTag)` 系统优先。
  API 33+ 框架自动重建 Activity（调用方**不得**再手动 recreate——双重重建）；
  API < 33 保持 wrap（`attachBaseContext` 注入 createConfigurationContext）+
  调用方 recreate。
- **历史 bug 修复（T21 顺带）**：MainActivity / LuaActivity 此前均未 override
  `attachBaseContext` → wrap 从未注入，`<33` 语言切换实际无效；已修复。
  `LocaleManager.currentLanguageTag` 以 `@Volatile` 跨线程可见（§6 约定）。

### 7.3 工程质量门禁

- **lint（T23）**：`lint { abortOnError; warningsAsErrors; checkReleaseBuilds=false;
  baseline = lint-baseline.xml }`——存量（81 errors + 3 hints，含第三方
  WorkManager 源码目录与历史代码）冻结于 baseline，**新增零容忍**（lint 链接
  CI 门禁，`lintArm64Debug`）。业务代码禁止新增 lint 告警；改动 lint 相关
  配置必须跑 lint 任务。

## 8. 全量规范审计整改（2026-08-12 FULL_AUDIT_SPEC 完工蒸馏）

> 28 项全部落地（工作单 docs/in-progress/FULL_AUDIT_SPEC_WORK_ORDER.md 已收口，
> backlog §2n 全部标记）。改动 42 文件，验收全绿：compile + 全量单测
> （含新增 A5/B6/C5/C7/C8/D1 用例）+ lint 0 新问题 + verifyMinimalismDebug。

### 8.1 打包链单遍解析（D1，M-B1）

- `AXmlManifest.patch` 复用单次解析结果（`packageNameFromParsed`，`readPackageName`
  委托同 helper）；`ArscLookup.drawableIcon(arsc): IconEntry?` 单次解析同时产出
  **资源 ID + 条目路径**，`ApkBuilder` 装配时 iconEntry 一处引用两用（`resourceId`
  兜底注入 + `path` 覆盖桌面图标）。原「id/路径各全量解析一次 arsc」已消除。

### 8.2 编辑器补全/诊断契约（A2/A5/C2/C4/D2/D4）

- 补全窗滚动 while 上限 100 次（上游 DefaultCompletionLayout 同款防 ANR）；
- replaceAll 光标恢复 = 替换前记录 + 双夹取 setSelection（sora afterInsert 语义）；
- snippet 描述本地化：`LuaSnippet.descriptionRes` + `toCompletionItems(context)`
  ——**lint WrongThread 教训**：分析线程禁止 `CodeEditor.getContext()`（推断
  worker），context 须 attach 时（主线程）快照注入；
- LuaFonts 缓存键 = asset 文件名（id 0 恒走内置 jetbrains_mono）；
- 200KB 大文件阈值/格式化上限单一事实源 `EditorLimits`；补全前缀复用 sora
  `CompletionHelper.computePrefix`。

### 8.3 M3 token 合规基线（E1/E2/E3/E4）

- 卡片容器一律 `surfaceContainerLow`（内容卡）/`surfaceContainerHigh`（搜索条浮层），
  `surfaceVariant` alpha 叠卡已清零；禁用态文本/图标 = `onSurface@38%`；
- `AppTypography` headline 系回归官方 Normal 权重 + displayLarge -0.25sp 字距；
  硬编码圆角 → `MaterialTheme.shapes`；
- 死资源 7 对字符串 + assets-zh 空目录已清理；裸魔法数提升常量（含
  `RSA_KEY_BITS=2048`、`UTC_TIME_MAX_YEAR=2050`（X.509 UTCTime/GeneralizedTime
  分界，RFC 5280 §4.1.2.5）、ZipBomb 三上限 512MB/10k 条目/64MB 单文件）。

### 8.4 线程/安全治理（A3/A4/B1-B6）

- androlua:// 与 ProjectEntryOps containment 一律纯词法归一化（去 `File.toPath()`
  API 26 依赖，minSdk 24 安全）；content:// 解析拷贝整体 IO 线程；
- Shizuku 合并流读（redirectErrorStream）防管道死锁；Dhizuku setInstallerPackageName
  守卫 `SDK_INT >= UPSIDE_DOWN_CAKE`（34）+ 全部后端路径 `catch(Throwable)`；
- EngineResultParser 错误定位锚定 ERROR_PREFIX 行；EngineContract 收敛全部
  extra 键（含 output/error 回传键）；RuntimePaths 子目录名常量（project/apk）。

验证（2026-08-12 全绿）：compileArm64DebugKotlin + testArm64DebugUnitTest +
lintArm64Debug（0 新问题，baseline 同步删 9 条已修复条目）+
verifyMinimalismDebug。

验证（2026-08-12 全绿）：compileArm64DebugKotlin + testArm64DebugUnitTest
（含 i18n.ResourceConsistencyTest 5 用例 / LocaleManagerTest 5 用例 /
build.* 8 用例）+ lintArm64Debug（0 新问题）+ assembleArm64Debug +
verifyMinimalism + verifyMinimalismDebug；设备截图与 per-app locale 真机
双向验证待 P7 统一执行。
