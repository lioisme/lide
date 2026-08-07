# AGENTS.md

本项目为「纯编辑器模块 + 宿主运行时」双层架构。以下规则是评审硬门槛，违反视为严重缺陷。

## 架构边界（R1–R5）

- **R1** `:editor`（包 `com.lioisme.lide.editor`）禁止出现 `com.androlua`、`com.luajava`、
  `System.loadLibrary`、jniLibs；其 `build.gradle.kts` 禁止依赖 `:androlua`。
  编辑器进程内的一切 Lint / 诊断 / 补全 / 高亮逻辑必须是纯 Kotlin/Java，禁止触碰 `libluajava.so`。
- **R2** Lua 编译与运行能力属于宿主 `:app`（`EngineBridge` → `EngineActivity`，同进程运行）。
  编辑器只做文本级能力（行级词法诊断、补全、高亮、搜索），不做运行时语义。
- **R3** 新增编辑器功能默认放 `:editor`；宿主专属逻辑（文件、权限、运行时、设置持久化）放 `:app`。
- **R4** `:editor` 不得读取 `:app` 的任何状态或类型；宿主依赖一律通过参数 / 接口注入。
- **R5** 仓库禁止任何二进制原生产物入库：`.so`/`.dex`/`.jar`（`gradle/wrapper/gradle-wrapper.jar` 引导例外）
  /`.zip` 一律不得 `git add`；原生引擎唯一允许形态是源码（统一归档于 `app/src/main/cpp/`，
  顶层 `Android.mk` + `Application.mk` 为 ndk-build 定义，`live2d/` 为独立模块），
  由 AGP `externalNativeBuild`（ndk-build）在 `assemble` 时直接从源码构建并打包进 APK，
  不设任何 jniLibs 缓存目录，也无 rebuild 脚本（`scripts/rebuild_engine.*` 已废弃删除）。
  NDK 版本固定为 `29.0.14206865`（`app/build.gradle.kts` `ndkVersion`）。
  例外：`.alp` 作为工程包可直接存在（构建时由 `:app:generateExamples` 从 `scripts/examples-src/` 生成，
  默认零内置，由 `lide.examples` 属性控制，见「精简治理」）。

验证命令（以下必须全部成立）：

```
rg -n "luajava|androlua|loadLibrary" editor/src
git ls-files '*.so' '*.dex' '*.zip'
git ls-files '*.jar' | grep -v 'gradle/wrapper/gradle-wrapper.jar'
```

## 构建与验证

- Kotlin 编译检查：`.\gradlew.bat :app:compileArm64DebugKotlin --console=plain`
- 完整构建：`.\gradlew.bat :app:assembleArm64Debug --console=plain`
- 精简门禁（去重校验）：`.\gradlew.bat :app:verifyMinimalism`（依赖 assembleArm64Release）

## 编辑器关键约定

- 初始化顺序（官方 demo 顺序，勿改）：`colorScheme` → `setEditorLanguage` → `setText`。
- TextMate 运行时只经 `TextMateRuntime`（进程级一次性，`preload` 幂等触发；状态机
  Loading/Ready/Failed，失败展示错误态 + 重试）初始化，且不得在 UI 线程调用。
- 文本快照可在后台线程取（`Content.toString` 内部持有 `ReadWriteLock`），
  但编辑器 API 调用（`setText` / `diagnostics` 赋值 / `subscribeEvent` 回调）保持主线程。
- 自动保存：快照与写盘均在 `Dispatchers.IO`；离开编辑器时经 `onRelease` 兜底保存。
- 大文件（> 200 KB）：关闭括号高亮 / 当前块 / 自动换行，并跳过行级诊断。
- 补全、诊断、事件回调必须 `try/catch(Throwable)` 降级，不得让异常逃逸进 sora 主循环。
- 文件 → 语言 scope 唯一事实源为 `LanguageDetector`（未识别扩展名显式回落
  `text.plain`，绝不回退为 Lua）；宿主「可打开性」判定经 `LanguageDetector.isKnownFile`
  消费同一表，不另维护扩展名白名单。

## API 浏览器反射线程契约（2026-08 简化，架构审计 A-1）

- build 期把枚举出的 dex 类名存为 `Snapshot.dexNames: Set<String>`，「可加载性」判定
  为 O(1) 查表，**只有存在类才 `Class.forName`**——不存在 `Class.forName` 全 dex 负查找。
- 反射加载统一经 `ApiIndex.loadClassRef`（合并后的单张 `Class<?>?` 缓存），
  `ApiReflection.reflect` / `ApiUsageClassifier` 一律走该封装，禁止自行
  `Class.forName`（防 static 初始化差异与三套缓存并存）。
- 列表行反射信息经 `produceState` + `Dispatchers.IO` 获取，UI 只读惰性缓存；
  全部反射路径必须 `try/catch(Throwable)` 降级（含负缓存）。
- 过滤必须走预索引（`categories`/`categoryCounts`，O(选中子集)），禁止在 UI 线程对
  全量 entries 做 O(n) 过滤；过滤计算经 `produceState + Dispatchers.Default`。
- 详情页三态：显示层必须用 `DetailUiState`（Loading/Loaded/Failed），反射失败显示
  `api_detail_failed` + 重试，禁止误用「无结果」文案或无限转圈。
- 不可用 API（loadable == false）用 Compose 原生 `TextDecoration.LineThrough` 中划线
  表示；禁止往文本内容里插入 `-`/`~~` 等「伪中划线」技巧。

## API 浏览器搜索语义红线（冻结审计结论，详见 docs/planned/API_BROWSER_FREEZE_FIX_PLAN.md）

- 搜索展开意图必须用独立 state（`searchActive`），InputField 的 `expanded`/`onExpandedChange`
  只读写该意图；**禁止绑定 `searchBarState.currentValue` 动画派生值**（progress∈(0,1) 时
  currentValue 恒 Expanded，会构成「收起→焦点回落→重新展开」死循环）。
- `SearchBarState` 动画（`animateToExpanded`/`animateToCollapsed`）基于 Animatable，
  并发调用互斥（后启动者取消先前的动画协程）：**全屏内同一时刻只允许一个协程驱动动画**
  （`LaunchedEffect(searchActive)` 幂等驱动）；点击建议导航必须经「pendingNavigation 挂起 +
  动画完成后消费」机制，**禁止**在点击回调里 `scope.launch { animateToCollapsed(); ... }`
  （协程会被动画互斥取消，导航丢失）。
- 收起路径（IME 搜索 / 点击建议 / Dialog 返回键）必须同时：置 `searchActive = false` +
  显式 `focusManager.clearFocus()`；Dialog content 槽内自行 `BackHandler` 拦截返回键
  （Dialog 拥有独立 back dispatcher，主窗口 BackHandler 无效）。
- `rememberSearchBarState()` 必须无参调用（rememberSaveable 按引用比较 key，
  每次重组新建 spring 实例会导致状态无限重建冻结）。
- 过滤（`filterSnapshot`）必须在后台（`Dispatchers.Default`）执行 + 预建小写名索引，
  禁止在 UI 线程同步 O(n) 过滤；行级反射信息缓存（`rowInfoCache`）必须是**非空包装**，
  禁止用返回 null 的 `getOrPut`（CHM 拒绝 null，负缓存永久失效 → 全 dex 扫描 → ART 全局锁）。

## /libs 机制约定（工程 /libs 支持，详见 docs/discard/LIBS_SUPPORT_WORK_ORDER.md）

- 工程 `<project>/libs/` 三层模型（pro 原生机制，勿改动）：`*.dex|jar|apk|zip` 由
  `LuaDexLoader.loadLibs()` 启动时自动扫描加载（`import "xxx:com.foo"` 可用）；`lib<name>.so`
  复制到 libDir 并由 `libsloader` 按 `package.loadlib(…, "luaopen_<name>")` 加载；`lua/*.lua`
  走 `luaDir/?.lua + luaDir/lua/?.lua + luaDir/?/init.lua` 搜索路径。
- `import.lua` 首行 `local luacontext = activity or service` 依赖 `LuaActivity.initLua()`
  注入的 `activity` 全局 —— 任何宿主改动不得破坏该全局注入。
- `LuaDexLoader` 的 W^X 修复（API≥26 走 `InMemoryDexClassLoader`）为**有意的、记录在案的**
  与 pro 的差异（工作单 §10.1），不得回退为磁盘路径加载。
- lfs / lsqlite3 原生模块已随引擎源码构建；lluv / tcc 按差异声明不提供（WHITEPAPER §7.1）。

## 打包构建约定（:app，签名 / Lua 编译加密）

- 打包双开关（`BuildOpts.compileLua` / `encryptBytecode`，**默认均开、互相独立、不得互相隐含**）：
  编译开 = 全部工程 `.lua`（含 main.lua）编译为字节码，关 = 源码文本入库；加密开 = 对入库每个
  Lua 文件 DES-ECB 加密（编译开加密字节码、编译关加密源码文本），关 = 原文。偏好键
  `compile_lua` / `encrypt_lua`（`SettingsManager` build 域），旧键 `compile_all` / `encrypt`
  不得回退读写。
- 签名档案三类：默认内置（AOSP testkey）、自定义导入（.jks/.p12）、应用内自生成
  （`SigningCrypto.generate` 零依赖手写 X.509 v3 自签 DER）。自签证书 version 字段必须
  `[0] EXPLICIT`（`A0 03 02 01 02`）包装——裸 INTEGER 会被 OpenJDK 误读为 serialNumber，
  平台回环解析报 `algid parse error`。
- 加密格式唯一事实源：工作单 §4（MAGIC `LIDE\x01\0\0\0` + DES/ECB/NoPadding + PKCS7，
  密钥 `{0xFF,0xDD,0xBB,0x99,0x77,0x55,0x33,0x11}`）；宿主引擎 Lua 库与 `.aly`/资源不加密。
- 改动打包/签名/加密链必须跑 `:app:testArm64DebugUnitTest --tests "com.lioisme.lide.build.*"`。

## ABI 架构契约（打包多架构，D-1 冻结结论）

- **打包 ABI 集合由宿主实际携带驱动**（唯一事实源 `ZipUtils.listAbis`，解析宿主 APK
  `lib/<abi>/` 目录，无白名单、对任意未来新 ABI 名天然通用）：宿主仅含 arm64 → 打包器仅支持
  打包 arm64 产物；宿主含 4 ABI → 自动支持 4 架构任意组合。**禁止在打包侧按已知架构名
  硬编码可用性判定**（如「已知 4 架构必然可用」）。
- `ApkBuilder` 装配侧 `effectiveAbis = 用户选择 ∩ 宿主 ABI`，空交集抛 `BuildException`
  （禁止静默回落、禁止错误产物）。
- 新增架构 = 宿主构建期加 flavor `abiFilters`（NDK 支持为前置，32 位需 NDK 27 线，当前
  固定 29 不含 32 位工具链）→ 宿主携带 → 打包器自动可用，**打包侧与 UI 零改动**。
- 设置页默认 ABI（`BuildOpts.preferAbi`）选项由宿主实际携带驱动（展示候选 = 宿主 ABI ∪
  标准架构名）：宿主未携带的架构**置灰禁用**（仅展示不可选，可用性判定只依据 `ZipUtils.listAbis`
  实测结果）；`preferAbi` 残留为未携带架构时构建侧回落「宿主全部」。构建打包对话框 ABI 多选
  仅列出宿主实际携带项（未携带不显示）。
- 打包 UI 唯一入口为构建打包对话框（编辑器 ⋮ 菜单触发；`BuildPackagingDialog`），
  签名方案 v1/v2/v3 与 ABI 均用经典 Checkbox 控件；「优化打包」（`stripNativeLibs`）
  为开关控件，仅针对原生库。
- 打包多架构验证走 `scripts/build-apks.ps1`（矩阵参数化：`-Abis`/`-Types`/`-Variants`，
  默认 6 变体；产物签名逐包校验）。

## 运行时基座契约（打包 dex 解耦，P0 冻结结论）

- 宿主存在 `runtime` buildType（R8 白名单 keep 引擎 + luaApiSurface，剥离 IDE 类），
  其产物 `classes.dex` 由 `:app:embedRuntimeDex` 解出为宿主
  `assets/runtime/classes.dex`（单 dex 资产，非 APK；挂 preBuild，源缺失自动跳过）。
- **构建顺序**：发布链 = `:app:assembleUniversalRuntime`（生成 runtime 变体）→
  `:app:embedRuntimeDex`（解出引擎 dex 资产）→ `:app:assemble*`（宿主携带 classes.dex）。
  宿主缺失该资产时打包器**兜底宿主 APK 自身**（pro 模板语义），不阻断运行期打包。
- `ApkBuilder` dex 替换模式（`RUNTIME_DEX_ENTRY = assets/runtime/classes.dex`）：宿主
  classes*.dex 条目全部替换为引擎 dex（产物唯一 classes.dex），manifest 同步回落
  `com.androlua.LuaApplication` + 移除 IDE MainActivity（`ManifestEdit.applicationName` /
  `removeActivities`）；ABI 枚举/权限仍以宿主条目为准（模板 = 宿主，D-1 冻结结论）。
- 新增引擎侧 Lua 反射类：同时登记 `luaApiSurface`（三处同步）+ `src/runtime/runtime-rules.pro`
  （白名单 keep）——`verifyRuntimeSurface` 门禁捕获遗漏。
- `ui-tooling-preview` 恒为 `debugImplementation`（IDE tooling，release/runtime 不得携带）。
- `app/src/main/assets/runtime/classes.dex` 为构建产物，不入库（.gitignore）。
- P0 全链验证：`.\gradlew.bat :app:verifyRuntimeSurface`（mapping 断言剥离）+ 打包单测。

## 精简治理（去重门禁，2026-08-07，详见 docs/planned/MINIMALISM_AUDIT_REPORT.md）

- **内置示例**：`-Plide.examples=all|名单` 控制生成哪些 `.alp` 进 `assets/examples/`；
  默认不设置 = 零内置（APK 最瘦）。`scripts/examples-src/` 保留工程模板作为选样池，
  UI（ExamplesScreen）按 assets 实际存在过滤，空态展示 `examples_empty`。
- **运行时引擎 dex**：`-Plide.embedRuntimeDex=false` 剔除 `assets/runtime/classes.dex`
  （受控冗余，默认开启保持 P0 契约；缺失时打包器兜底宿主 dex，见上一节）。
- **死代码禁令**：`:app` / `:androlua` 禁止把空 `libs/` 目录声明为依赖（`fileTree("libs")`
  空声明与 `.gitkeep` 已清理）；各模块 compileSdk 保持一致（当前 36）。
- **去重门禁**：`:app:verifyMinimalism` 校验 release APK 无 jniLibs 预编译回包、白名单外
  `.so`、同 ABI 重复原生库、默认场景内置示例为 0，已接入 CI（verify-release job）。
- 改动打包 / 原生 / 示例链路必须重跑：`:app:verifyMinimalism` + `:app:testArm64DebugUnitTest --tests "com.lioisme.lide.build.*"`。

## 文档治理约定（opencode Agent 必守）

- **事实来源唯一**：当前事实一律以 `docs/kb/` 为准；当前工作以 `docs/in-progress/`、
  `docs/planned/` 为准。**禁止把 `docs/discard/` 当作日常任务输入或事实来源**
  （仅追溯历史结论时按需查阅）。
- **任务文档生命周期**：新任务文档登记于 `docs/planned/`（或 `docs/in-progress/`）并登记
  `docs/kb/README.md` §2 矩阵；**每完成一项任务，必须在任务文档对应条目标记「（已完成）」
  并备注工作内容 + 日期 + 验收证据**（构建命令 / 设备验证 / 测试结果）。
- **完成后归档**：整份任务文档全部完成后 `git mv` 到 `docs/discard/`（唯一丢弃出口，
  不再使用 `completed/`、`archive/`），并同步更新 `docs/kb/README.md` §2、
  `docs/TASKS.md`、`docs/discard/README.md` §3 清单。