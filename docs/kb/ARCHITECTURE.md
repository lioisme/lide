# delua 架构知识库

> 来源：`AGENTS.md`、`WHITEPAPER.md`、`ANDROLUA_INTEGRATION_PLAN.md`、`ENGINE_MIGRATION_WORK_ORDER.md`、
> `EDITOR_CRASH_ANALYSIS_REPORT.md`、`LIBS_SUPPORT_WORK_ORDER.md`、`DEPENDENCY_MODEL_AUDIT_REPORT.md`。
> 权威边界规则以 `AGENTS.md`（实时）为准，本文档为解释性索引。

---

## 1. 总体拓扑（单进程三层）

```
settings.gradle.kts: :app + :androlua + :editor
```

| 层 | 模块 | 包 | 职责 |
|----|------|----|------|
| IDE 宿主 | `:app` | `com.lioisme.delua` | Compose UI、项目管理、运行拉起、权限、设置持久化、打包器 |
| 引擎运行时 | `:androlua` | `com.androlua` / `com.luajava` / `com.android.cglib`（Java 源码）+ 少量纯 Kotlin 工具 | Lua 运行时（继承自 AndroLua_pro 4.4.2 源码，编译进主 DEX） |
| 编辑器 | `:editor` | `com.lioisme.delua.editor` | 纯文本级能力：行级词法诊断、补全、高亮、搜索、标签页、文件树 |

运行时模型：**单进程**（`EngineBridge` → `EngineActivity` 同进程 Activity 栈直启），无独立进程、无 engine.dex 动态加载。

---

## 2. 架构边界 R1~R4（评审硬门槛）

| 规则 | 内容 |
|------|------|
| **R1** | `:editor` 禁止出现 `com.androlua` / `com.luajava` / `System.loadLibrary` / jniLibs；`editor/build.gradle.kts` 禁止依赖 `:androlua`。验证：`rg -n "luajava|androlua|loadLibrary" editor/src` 必须零命中 |
| **R2** | Lua 编译与运行能力属于 `:app`（`EngineBridge` → `EngineActivity`，同进程）。编辑器只做文本级能力，不做运行时语义 |
| **R3** | 新增编辑器功能默认放 `:editor`；宿主专属逻辑（文件、权限、运行时、设置）放 `:app` |
| **R4** | `:editor` 不得读取 `:app` 状态/类型；宿主一律通过参数/接口注入 |

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
- 插件运行传 `{luadir, luapath}`（EXTRA_SCRIPT_ARGS="arg"，对齐 LuaActivity `ARG` 键）。

### 3.2 引擎初始化时序（官方 demo 顺序，勿改）

```
colorScheme → setEditorLanguage → setText
```
- TextMate 运行时只经 `TextMateInitializer`（进程级一次性，`DeluaApp` + `CountDownLatch` 预热），**不得在 UI 线程调用**。
- 编辑器 API 调用（setText / diagnostics 赋值 / subscribeEvent 回调）保持主线程；文本快照可在后台线程取（`Content.toString` 内部 ReadWriteLock）。
- 自动保存：快照与写盘均在 `Dispatchers.IO`；`onRelease` 兜底保存（修过 dispose 顺序丢改动）。
- 大文件（>200 KB）：关闭括号高亮 / 当前块 / 自动换行，跳过行级诊断。
- 补全、诊断、事件回调必须 `try/catch(Throwable)` 降级，不得让异常逃逸进 sora 主循环。

### 3.3 /libs 机制（工程 libs 目录，详见 `LIBS_SUPPORT_WORK_ORDER.md`）

- 初始化时序不可变：`luaDir` 由 `EngineActivity.getLuaPath()` 设为 `projectDir` 后，`new LuaDexLoader(this)` + `loadLibs()`（LuaActivity.onCreate 顺序）。乱序导致 dex/so 静默不加载。
- `activity` / `service` 全局是 `import.lua` 的生命线（`local luacontext = activity or service`）。
- **dex 必须内存加载**：Android 8+/14+ W^X 拒绝从 app 可写目录加载 dex；`LuaDexLoader.loadDex` 在 API≥26 走 `InMemoryDexClassLoader` 字节加载，**禁止回退为文件路径加载**（V-01/V-02 真机回归兜底）。
- 脚本调 Java 方法用**点语法**（`activity.loadDex(path)`、`obj.mul(6,7)`）；冒号调用会把 self 推进栈导致参数错位。`getFilesDir()` 被 `getDir(String,int)` 遮蔽，需用 `getDir(...).getParentFile()`。
- 工程 `libs/lib*.so` 经 `libsloader` / `getLibrarys()` / `package.loadlib` 加载；宿主 APK 内置 .so 由 `nativeLibraryDir` 提供，两者入口不同，混淆会 `UnsatisfiedLinkError`。
- `libs/` 内容按不可信输入对待：dex/so 加载失败必须降级不崩溃，错误信息可读。

### 3.4 打包器（进行中，见 ROADMAP §1）

```
BuildApkScreen
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
单测：            .\gradlew.bat :app:testX64DebugUnitTest :editor:testDebugUnitTest :androlua:testDebugUnitTest
R1 门禁：         rg -n "luajava|androlua|loadLibrary" editor/src
```
