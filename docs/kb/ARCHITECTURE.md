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
| 权限 | 运行前按 `user_permission` 一次性 `RequestMultiplePermissions`（拒绝仅 Toast 继续运行，pro 行为）；`PermissionResolver` 纯函数。工程权限页（`ProjectPermissionScreen`）勾选即生效：`ProjectPermState.selected` 不可变 Set + toggle 重赋（快照写入 → 重组）+ `persistSelected(Set)` 参数化写盘；`PermissionCheckRow` 整行 `Modifier.toggleable`（Checkbox `onCheckedChange=null` 仅视觉，单点消费防双触发）。**禁止对 `MutableSet` 原地增删**（无快照写入 → 无重组 → 勾选框视觉不动，2026-08-13 修复根因） |
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

## 9. UI 交互与启动界面契约（2026-08-12 UI_AUDIT_SIX_REQ 完工蒸馏）

### 9.1 底部输入框（R1/R2，BottomInputSheet）

- 共享组件 `ui/screen/BottomInputSheet.kt`（2026-08-12 提取）：
  `rememberModalBottomSheetState(skipPartiallyExpanded)` + `sheetState.show()` **动画完成
  后再 requestFocus**（焦点请求在 sheet 入场动画期间执行会落空——Dialog 窗口焦点树
  未就绪，表现为「先弹框后弹键盘」）；label/placeholder/keyboardType 参数化；
  全项目无 `LocalSoftwareKeyboardController` 兜底需求（动画完成后聚焦即可）。
- 「跳转到行」复用该组件（数字键盘 + 自动聚焦 + IME Done 提交），0 基转换与
  pendingJump 管线不变；**不再使用居中 AlertDialog 形态**。

### 9.2 编辑器格式化视口保持（R3）

- `EditorController.format` / `replaceAll`：替换前 `getFirstVisibleLine()` 快照；
  替换后 `setSelection(restoredLine, restoredCol, makeItVisible=false)`（第三参 true
  会触发 sora `ensurePositionVisible` 强制滚动，光标在首/末行时等价跳顶/跳底）+
  `ensurePositionVisible(restoredFirst, 0, true)` 恢复视口。光标行列恢复
  （L3 双夹取）与视口恢复（U6）分离，互不干扰。

### 9.3 工程权限界面（R4）

- `SearchablePermissionPicker.kt` 分层：`rememberPickerItems`（预索引：名称/中文名/
  别名合并小写串，池 = 目录 + 工程已声明未知权限）→ `filterPickerItems`（纯函数，
  后台线程）→ `permissionGroupItems`（LazyListScope 扩展：REGULAR → SPECIAL →
  其他 固定分组序 + 行，页级列表与弹窗共用）→ `PermissionCheckRow` /
  `PermissionGroupHeader` 共享渲染；`PermissionPickerList` 保留给工程属性页弹窗
  （常驻搜索框 + `heightIn` 由调用方约束，弹窗内唯一滚动容器）。
- **页面形态对齐 API 浏览器搜索红线**：折叠自绘条
  `PermissionCollapsedSearchInput`（`detectTapGestures` 区分单击展开/长按无副作用，
  规避 SearchBarDefaults.InputField 折叠态长按文本选择崩坏）+ `ExpandedFullScreenSearchBar`
  全屏展开（`rememberSearchBarState()` 无参 + 独立 `searchActive` 意图 +
  `LaunchedEffect(searchActive)` 唯一驱动 + `Dispatchers.Default` 过滤 +
  BackHandler 清焦收起）；正文与全屏搜索各用独立 LazyColumn（互不嵌套）。
- **无嵌套滚动容器**：页面为单一 LazyColumn 直接渲染分组行（页面不再包裹弹窗组件）；
  工程属性页弹窗单列唯一滚动容器。无「已选 x/y」计数、无说明文案（host 页 3 处
  引导文案 + 工程页 section/hint 已删，死资源 6 条随同清理）。

### 9.4 启动界面（R5）

- **splash 图标**：`drawable-v26/ic_splash_icon.xml` adaptive-icon（背景 =
  `lide_surface_background` 同色融合仅显图形；前景 = `ic_splash_foreground.png`
  原图标图形居中缩至安全区 ≈58% 画布，圆形遮罩下完整无裁切）——legacy 位图会被
  AOSP `shrinkNonAdaptiveIcons` 缩小 + 圆形遮罩裁切致显示不完整。
- **背景跟随外观系统**：浅色档 `values/colors.xml` `#FDFCFF` / 深色档
  `values-night/colors.xml` `#141218`（M3 暗 background 标准值，HCT tone 6 中性色）；
  API 24-30 无系统 splash，`values[-night]/themes.xml` 补 `android:windowBackground`
  使冷启动帧跟随系统 uiMode；API 31+ `windowSplashScreenBackground` 按 night 限定符
  解析。**平台限制**：应用 AppearanceMode 与系统相反时系统 splash 色无法动态覆盖
  （splash 在 app 代码运行前由系统绘制），随后 `applyWindowBackground()`
  （MainActivity，HCT 同源 surface）接管窗口背景。
- 图标资源位图（icon.png 例外族）不受 R5 二进制禁令约束（禁令限 .so/.dex/.jar/.zip）。

### 9.5 主题色标记（R6）

- `selectedMarkColor(color, isDark)` 双因素（SettingsScreen）：日间偏好黑 / 夜间
  偏好白为首选候选，黑白候选依序取首个满足 WCAG AA 4.5:1 严格档的；双候选均不满足
  时取对比度更高者（黑白为极端候选，兜底恒生效）。替代 0.5 luminance 阈值
  （中灰块判色出错）与单因素亮度判色（夜间模式选中亮色块仍显黑色对号）。

验证（2026-08-12 全绿）：compileArm64DebugKotlin + testArm64DebugUnitTest +
verifySoraIsolation + verifyMinimalismDebug + verifyMinimalism（release 链路）。

## 10. 日志猫（全局 Logcat 查看器）（2026-08-14 LOGCAT 完工蒸馏）

- **定位**：宿主级全局日志查看器（非 androlua 专用）；UI 与 API 浏览器同构
  （折叠搜索条 + 全屏 SearchView + 列表 + 详情），风格与 LIDE 一致。
- **范围契约**：仅 LIDE 进程日志——现代 Android（7.0+）未授予 READ_LOGS 时
  logd 按 UID 过滤；宿主 + 引擎**同进程**（AGENTS R2），故「整个软件」=
  宿主 + 引擎 + Lua print 全部可见，其他 App/系统日志不可见（无 root 不另议）。
- **采集时机**：`LideApp.onCreate` 启动即采集（进程级常驻，环形缓冲上限 3000 条），
  打开日志页可见本次运行全过程，无丢失窗口；打包产物不运行 LideApp（manifest
  回落引擎入口 Activity），天然不含此功能。
- **数据层**（`com.lioisme.lide.log`，宿主能力，R1/R3/R4 合规，纯 Kotlin 零 sora/
  引擎依赖）：
  - `LogcatParser`：`logcat -v threadtime` 行解析（正则前缀 + 首 `": "` tag/消息
    切分——含冒号 tag 按标准首切分语义，消息以 `" : "` 开头；多行 continuation
    由 store 归并；`----- beginning of` 头丢弃；乱行兜底）。
  - `LogBuffer`：环形缓冲（容量 3000，溢出丢最旧）+ continuation 归并 + 按 id 查找
    ——JVM 可测。
  - `LogcatReader`：`ProcessBuilder("logcat","-v","threadtime")` 流式读取，
    `Dispatchers.IO` 阻塞循环；断线自动重启（5s 退避）；全路径 `try/catch(Throwable)`。
  - `LogcatStore`：进程级单例（ApiIndex 同款模式），`StateFlow<List<LogEntry>>`
    （采集器线程写、原子发布不可变快照）+ `StateFlow<LogcatStatus>`
    （Streaming/Unavailable）+ id 单调分配 + `clear()`（内存清空 + best-effort
    `logcat -c`）+ `entryById(id)` + `ensureStarted()` 幂等 + `resetForTest()`。
    单条消息上限 16384（超长截断，详情展示截断后全文）。
- **筛选语义**（`LogFilter`，2026-08-14 修订对齐 pro `logcat.lua` 菜单与语义）：
  All/Lua/Test/Tcc/Error/Warning/Info/Debug/Verbose（[LogFilter.entries] 遍历即菜单
  序）；Lua = tag `lua`（`lua:* *:S`，Lua print + 引擎 Lua 业务日志）；Test = tag
  `test`（`test:* *:S`，工程测试约定通道，现无来源恒空态）；Tcc = tag `tcc`
  （LIDE 不构建 tcc 恒空态）；Error≥E、Warning≥W、Info≥I、Debug≥D、Verbose 恒真。
  `filterEntries` 纯函数：类型 ∩ 查询词（tag+message 小写子串），
  `Dispatchers.Default` + produceState。
- **UI 层**（`ui/screen`，搜索状态机红线照抄 ApiBrowserScreen）：
  - `LogcatScreen`：CenterAlignedTopAppBar + 折叠搜索条（复用 `ApiCollapsedSearchInput`）
    + 类型筛选菜单（9 项单选 + 菜单底部「清空日志」）；搜索状态机红线
    （`rememberSearchBarState()` 无参 + `searchActive` 独立意图 +
    `LaunchedEffect(searchActive)` 唯一驱动 + 收起路径三件套 + BackHandler）；空态
    三文案（未开始/不可用/无匹配）；列表自动滚动（底部跟随，userHasScrolledUp 时
    暂停）。
  - **清除/筛选布局（2026-08-14 界面重构）**：「清空日志」并入**类型筛选菜单**
    （9 项筛选后 `HorizontalDivider` + DropdownMenuItem：Delete 图标 +
    `colorScheme.error`，对齐宿主危险操作惯例）；折叠/全屏两态共享组件自动同步；
    直接清除（内存必清 + `logcat -c` best-effort，无二次确认）。搜索条 trailing
    仅余筛选按钮；`LogClearButton` 组件删除；共享组件 `ApiSearchInputField` /
    `ApiCollapsedSearchInput` 的 `clearSlot` 死参数删除（历史 R2 布局已废止）。
  - **搜索占位符（2026-08-14 界面重构）**：共享搜索组件占位符参数化
    （`placeholder: String` 必传）——LogcatScreen 传 `logcat_search_hint`
    （「搜索日志…」），ApiBrowserScreen 传 `api_search_hint`（修复日志猫显示
    「搜索类名…」文案错位）。
  - `LogcatDetailScreen`：路由 `logcat/{entryId}`（Long id 经 enc）；三态
    Loading/Loaded/Failed（id 被溢出清除 → `logcat_detail_missing` + 重试，对齐
    API 详情契约）；完整消息 SelectionContainer + 元数据（时间/级别/tag/pid/tid）
    + 复制按钮（snackbar 提示）。顶栏保持手写 TopAppBar（动态 tag 标题需省略
    控制，LideTopBar 无此参数，配色已一致）。
- **日志行设计（2026-08-15 LOGCAT_LIST_REVISION8 九次重构：徽章固定列锚点，
     行业列式规范收口）**：REVISION7（AS 前缀式）验收失败——消息列随 tag 长度
     逐行漂移成锯齿、前缀与消息混排一行（用户反馈「排版混乱」）。用户拍板重构为
     **固定列锚点**结构（LogFox 同款）：每行 = **`[级别徽章固定宽列] + 固定 12dp
     间距 + [右侧内容列]`**，右侧消息/元数据左缘全列表恒定 x 对齐（行业列式规范：
     perfetto Android Logs / MAUI.Sherpa / Android Studio Logcat 同构）：
     **左列（固定 LEVEL_BADGE_WIDTH 28dp）**：级别徽章——圆角 6dp、级别色 15%
     alpha 淡底 + 级别色字母（labelMedium 等宽居中，top 对齐消息首行；级别一眼
     扫描 + 列锚点恒定，私有 `LevelBadge`）；**主体 Column（weight 1f）**：**行1
     消息 bodySmall Monospace onSurface `maxLines = 3`（独占整行区零混排，续行
     自然对齐消息列）** + **行2 元数据小字一行（labelSmall onSurfaceVariant）：
     `MM-DD HH:MM:SS（tnum）· PID-TID（等宽）· tag（等宽 widthIn(max = 120dp)
     省略）`**（PID-TID = AS Standard View 同款 `12345-12345`；毫秒降级详情页）；
     行 `minHeight 56dp` + `padding(horizontal = 16.dp, vertical = 10.dp)`；行末
     `HorizontalDivider(outlineVariant.copy(alpha = 0.4f))`。**常量**：新增
     `LEVEL_BADGE_WIDTH(28dp)` / `BADGE_TO_CONTENT_GAP(12dp)` / `LEVEL_BADGE_CORNER`
     (6dp) / `LEVEL_BADGE_ALPHA(0.15f)`；`TAG_MAX_WIDTH`(120dp) / `LOG_ROW_MIN_HEIGHT`
     (56dp) / `MAX_MESSAGE_LINES(3)` / `TIME_TEXT_LENGTH(14)` / `timeForList` 保留；
     新增 imports（background / RoundedCornerShape）。长按复制 = `LogEntry.formatted()`
     标准整行（`MM-DD HH:MM:SS.mmm PID TID I/tag: message`）。历史形态（REVISION7
     前缀流式 `I/tag: message` / REVISION6 级别字母固定列 + 元数据行内 tag /
     REVISION5 元数据行内嵌级别字母 / 单行标准前缀 / 单行分组式 / 纵向两行式 /
     单行横排三区块 / 2w 两行式 tag 主行 / 2v 时间固定列 + tag 灰块徽章）全部废止。
     列表时间 `MM-DD HH:MM:SS`（`timeForList` = `timeText.take(14)`，毫秒降级
     详情页——详情页展示完整 `timeText`）；级别语义色固定（V 灰 / D 蓝 / I 绿 /
     W 琥珀 / E 红，经典 logcat 色，双模 Material 300/800 配对）；其余全部 M3
     token；i18n 中英全量（本重构零新增资源）。
  - **列表全宽（2026-08-14 界面重构 R3 延续）**：LazyColumn `contentPadding =
    0`——无相对外部间距，边距由行内 padding（horizontal 16 / vertical 8）承担
    （对齐 ProjectList 主界面写法，波纹全宽）。
- **入口（2026-08-14 修订 R3，全局定位）**：**主设置页（SettingsScreen）新增
  「工具」分区**（Article 图标 + `logcat` + `logcat_subtitle`），AndroLua 设置页
  工具区仅余 API 浏览器/示例；Routes（LOGCAT / LOG_DETAIL）+ slideComposable 注册。
- **验证**（2026-08-14 重构后全绿）：compileArm64DebugKotlin 0 error；全量
  testArm64DebugUnitTest 全绿；ktlintCheck 对日志猫/API 浏览器相关文件 0 违规
  （log 包与 LogcatDetailScreen 的既有违规登记 backlog R11 ktlint 全量专项）；
  红线自检三连 grep 全零输出（log 包零 sora/引擎引用、editor 包零 log 触碰）。
- **验证**（2026-08-14 LOGCAT_LIST_REDESIGN 收口）：ktlintCheck **全量 PASS**
  （顺手修复既有违规：LogLevel 1 处 + LogcatDetailScreen 2 处 +
  SearchablePermissionPicker 文件头 KDoc 转块注释 1 处 + log 包 5 文件
  ktlintFormat 16 处）；compileArm64DebugKotlin 0 error / 0 warning；
   全量 testArm64DebugUnitTest = **357 tests / 0 failures**；红线三连 grep
   全零（editor 包零 luajava/androlua/loadLibrary、vendor 源码零 com.lioisme、
   git ls-files 二进制零）。
- **验证**（2026-08-14 LOGCAT_LIST_REVISION 二次重构收口）：ktlintCheck **全量
   PASS**（文件整体重建后 UTF-8/LF 校验通过）；compileArm64DebugKotlin
   0 error / 0 warning；全量 testArm64DebugUnitTest = **357 tests / 0 failures**。
- **验证**（2026-08-14 LOGCAT_LIST_REVISION2 三次重构收口）：ktlintCheck
   PASS；compileArm64DebugKotlin 0 error / 0 warning；全量 testArm64DebugUnitTest
   = **357 tests / 0 failures**。
- **实机验收**（2026-08-15，x64Debug / Android 14 模拟器，ADB）：列表纵向
   两行式实机生效（消息主行 + 副行 tag/时间，级别字母语义色小字，无行首色
   点，细分隔线行高均匀）；详情页毫秒时间/PID/TID 完整展示；级别筛选
   「错误」生效（列表只剩 E/Error）；长按复制 Snackbar 提示正常。
- **实机验收**（2026-08-15，LOGCAT_LIST_REVISION3 单行紧凑分组式收口，
   x64Debug / Android 14）：一屏 21 条（行高 32dp 级，密度 +75%）；消息
   全名完整显示不省略；tag 全名完整（`om.lioisme.lide` /
   `CompatibilityChangeReporter` / `GraphicsEnvironment`，UI 树文本核验）；
   消息左对齐 / 时间右缘对齐 / 列对齐整齐；筛选与详情交互无回归。
- **实机验收**（2026-08-15，LOGCAT_LIST_REVISION5 纵向展开收口，x64Debug /
   Android 14）：每条日志上下两段实机生效——消息多行完整可读（如
   `Currently set values for: angle_gl_driver_selection_pkgs=[]` 两行完整
   显示，不再单行截断），元数据小字行（`08-15 02:09:25 I/om.lioisme.lide`）
   位于消息下方；日志阅读器观感；单测 358 tests / 0 failures。

## 11. 日志架构：kotlin-logging 迁移（2026-08-15 KOTLIN_LOGGING_WORK_ORDER 完工蒸馏）

- **拍板**（用户红线）：日志猫完全使用 kotlin-logging 组件库实现，禁止自己手搓；
  读取面取舍拍板 = 选项 A（logcat 子进程保留唯一传输，领域模型对齐组件库语义）。
- **写入面（生产）**：宿主全部日志经 `io.github.oshai:kotlin-logging:8.0.4`
  （pin 精确版本，`ideDeps` `implementation`，D9）；工厂 =
  `AndroidNativeLoggerFactory`（D2）：`LideApp.companion.init` 设
  `kotlin-logging-to-android-native=true`（类加载期最早执行点，先于一切 logger
  调用）；直连 `android.util.Log`，tag = logger 名，`isLoggable` 门控内建 +
  消息 lambda 懒求值。**零 SLF4J 运行时**（D3；库侧 slf4j-api 为 compileOnly）。
  - **命名（D4/D5）**：顶层 `private val logger = KotlinLogging.logger("Xxx")`，
    显式短名 = 原 TAG 值（含 `ShizukuService`/`DhizukuService` 非类名 tag 原样
    保留）；级映射 `Log.d→debug`、`i→info`、`w→warn`、`e→error`、`v→trace`；
    `Log.w(TAG,"…",e)` → `logger.warn(e) { "…" }`（throwable 第一参数）。
  - **迁移量**：22 文件 / 140 调用点 / 21 `const val TAG` + 1 `TAG_SHARE` /
    ApiIndex 2 处手写 `isLoggable` 守卫全部清除；引擎 vendor 与 cpp 零改动
    （R5；引擎 Java 与 Lua print 仍直呼 `android.util.Log`，tag `lua` 等，
    日志猫 LUA/TEST 筛选语义不变）。
- **读取面（消费）**：logcat 子进程唯一传输保留（kotlin-logging 无读取能力，
  v8 已删 MessageBufferLogger，Appender 仅捕获经本库发出的日志——引擎/Lua
  日志必经 logd，任何日志库无法替代）。**自搓 `LogLevel` 枚举删除**，级别统一
  = kotlin-logging `Level`（TRACE(0)/DEBUG(10)/INFO(20)/WARN(30)/ERROR(40)/OFF(50)）：
  - `log/LevelMappings.kt`（新，唯一映射点）：`logcatSymbolToLevel(Char)`（V→TRACE、
    D→DEBUG、I→INFO、W→WARN、E/F/未知→ERROR）+ `Level.symbolChar()`（回映；OFF→'?'
    防御兜底）。
  - 类型迁移：`LogEntry.level`、`LogcatParser.Result.Entry.level`、`LogFilter.minLevel`
    均改 `Level`；过滤比较用 `Level.toInt()`；`LogFilter.VERBOSE` 阈值 = `Level.TRACE`
    （kotlin-logging 无 verbose，V 为最细粒度）；UI 消费点（`levelColor`/`LevelBadge`
    /`levelLabel`）随动，色表 VERBOSE 顶替 TRACE，`symbol` → `symbolChar()`。
  - 新增 `LevelMappingsTest`（字符→级别 / F 与未知回落 / 回映 / 数值单调序 /
    formatted 一致性）。
- **JVM 单测工厂（D11）**：宿主单测类路径携带 kotlin-logging **Android 变体**，
  其 `detectLogger()` 仅识别 `kotlin-logging-to-android-native`（否则回落 Slf4j 缺类 →
  `NoClassDefFoundError: org/slf4j/LoggerFactory`）；`testOptions.unitTests.all` 置该
  属性 → 测试与运行时同工厂，`unitTests.isReturnDefaultValues=true` 下
  `Log.isLoggable` 桩 false 即 no-op。`kotlin-logging-to-jul` 对 Android 变体无效（实测）。
- **打包链零改动（D10）**：产物复用宿主根 dex 全量（kotlin-logging 类随包，
  P0-7 契约已接受多余内容）；打包产物不运行 LideApp → 无工厂配置需求。
- **验证**（2026-08-15 收口）：`compileArm64DebugKotlin` 0 error；全量单测
  **363 tests / 0 failures**（原 358 + 新增 5）；`git grep` 宿主树
  `android.util.Log`/`Log\.[vdiew]\(`/`const val TAG = "` 仅剩 LogFilter.kt:13
  文档注释（pro 历史语义说明，非代码，保留；SigningCrypto `TAG_*` 为 ASN.1 DER
  常量、LocaleManager `TAG_SYSTEM/EN/ZH` 为 locale 键，非日志 tag）；
  `verifyMinimalismDebug` / `verifyVendorIsolation` / `verifySoraIsolation` /
  `assembleArm64Debug` 全绿；`arm64DebugRuntimeClasspath` 依赖树零 slf4j
  （kotlin-logging → kotlin-logging-android-debug）。
- **设备验收**（2026-08-15 补验，x64Debug / Android 14 模拟器 x86_64，ADB 实测）：
  - **工厂生效**：两次独立进程启动均见 `kotlin-logging: initializing... active
    logger factory: AndroidNativeLoggerFactory`（`I System.out`，引擎/D2 证据）。
  - **F1 宿主 tag**：新建工程触发 `D ProjectManager: createProject: name=adbprobe` +
    `D ProjectManager: Created project adbprobe`（kotlin-logging 短名 tag、级别/消息
    正确，logcat 直读可见）。
  - **F2 筛选**：日志猫九项菜单顺序 = pro logcat.lua（全部/Lua/Test/Tcc/错误/警告/
    信息/调试/详细 + 清空日志）；LUA 筛选后仅剩 tag `lua` 条目（print + 引擎语法错）；
    ERROR 筛选后仅剩 E 级（libEGL）；搜索词过滤仅剩命中行。
  - **F3 Lua print**：运行 hello 工程 → `I lua: hello: layout loaded`（引擎
    `Log.i("lua",…)` 通道，tag `lua` 可见）。
  - **F4 引擎日志**：demo 语法错误 `I lua: Syntax error: …`、BitmapFactory ENOENT
    等引擎/系统日志在 logcat 与日志猫均可见。
  - **F5 交互**：清空日志 → 空态文案；详情页（毫秒时间戳、级别标签 W·警告、tag、
    PID/TID、全文）+ 复制按钮 → snackbar「已复制到剪贴板」；列表长按复制与详情复制
    同走 `copyToClipboard` + snackbar（adb 长按手势未触发 snackbar 属输入模拟局限，
    非功能缺陷）。
  - **无回归**：应用多次冷启无崩溃；编辑器打开、工程运行、API 浏览器 dex 扫描、
    日志猫采集（05:13 进程全量）均正常。性能（F6/P1-P3）无迁移前基线对比，机制由
    KLoggerAndroid `isLoggable` 门控 + lambda 懒求值保证。
  - **R8（2026-08-15 补齐）**：迁移后 release 首次构建 R8 报 missing classes——
    kotlin-logging Android 变体编译期引用 org.slf4j 接口（compileOnly，运行时零
    slf4j），须在 `app/proguard-rules.pro` 追加 `-dontwarn org.slf4j.**`（kotlin-logging
    官方 R8 约定）；x64/arm64 × Debug/Release 四变体独立任务构建 + apksigner v2 验签
    通过，release 装机冒烟（R8 后工厂行仍现、进程无崩溃）。
