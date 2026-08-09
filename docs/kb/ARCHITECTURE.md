# lide 架构知识库

> 来源：`AGENTS.md`、`WHITEPAPER.md`、`ANDROLUA_INTEGRATION_PLAN.md`、`ENGINE_MIGRATION_WORK_ORDER.md`、
> 来源：历史审计报告（`EDITOR_CRASH_ANALYSIS_REPORT.md`、`LIBS_SUPPORT_WORK_ORDER.md`、`DEPENDENCY_MODEL_AUDIT_REPORT.md`，均已删除，结论蒸馏于此）。
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
BuildPackagingDialog（打包 UI 唯一入口，编辑器 ⋮ 菜单触发）
  → LuaCompiler（LuaState loadbuffer + dump → .luac，去 BOM，错误带行号）
  → AXmlManifest（纯字节级 AXML 解析/补丁：包名/label/versionCode/uses-permission 白名单，无 Android 依赖）
  → ApkBuilder + ZipUtils（zip 重打包/过滤/对齐）
  → ApkSigner + V2Signer（v1 JAR + v2 Signing Block；v1-only 在 targetSdk≥30 不可安装）
```

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
  signing_meta_line / cd_search_match_count）。
- **具名类型替代 Tuple**：跨模块传值禁止无语义元组（Q16 L1/L14 收口：
  `ZipUtils.ZipEntryData` 替代 `Triple<String,Int,ByteArray>`；`model.PendingOpenFile`
  替代 `Pair<String,String>`）。
- **字节偏移常量**：手写二进制解析（ZIP/arsc/dex）魔法偏移提命名常量（Q16 L4：
  `ZipUtils` EOCD/CEN 全部偏移与签名）。**谓词单一事实源**：扩展名判定等重复出现的
  内联条件提私有谓词（Q16 L8：`ApkBuilder.isLuaFile`）。
- 延迟项登记 backlog：`ZipUtils` CEN 游标访问（整包 readBytes 优化，P1-11）待打包链
  提交后端到端验证。
