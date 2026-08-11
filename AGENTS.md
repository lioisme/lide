# AGENTS.md

本项目为「编辑器包 + 宿主运行时」单一 `:app` 模块架构（2026-08-08 源码组织审计后 `:editor` 模块并单，
`com.lioisme.lide.editor` 包）。以下规则是评审硬门槛，违反视为严重缺陷。

## 架构边界（R1–R5，模块边界 → 包边界语义）

- **R1** 编辑器代码（包 `com.lioisme.lide.editor`）禁止出现 `com.androlua`、`com.luajava`、
  `System.loadLibrary`、jniLibs；`app/build.gradle.kts` 对 `:androlua` 为 `implementation` +
  `testImplementation`（2026-08-09 引擎 dex 单份化：引擎类并入宿主根 `classes*.dex` 恰一份，
  见「运行时基座契约」）。
  编辑器的一切 Lint / 诊断 / 补全 / 高亮逻辑必须是纯 Kotlin/Java，禁止触碰 `libluajava.so`。
- **R2** Lua 编译与运行能力属于宿主（`EngineBridge` → `EngineActivity`，同进程运行）。
  编辑器只做文本级能力（行级词法诊断、补全、高亮），不做运行时语义。
- **R3** 新增编辑器功能默认放 `com.lioisme.lide.editor` 包；宿主专属逻辑（文件、权限、运行时、设置持久化）
  放 `com.lioisme.lide` 其他包（engine / build / project / ui 等）。
- **R4** `com.lioisme.lide.editor` 包不得读取宿主业务状态或类型（engine / build / project / ui 包）；
  宿主依赖一律通过参数 / 接口注入。
- **R5** 仓库禁止任何二进制原生产物入库：`.so`/`.dex`/`.jar`（`gradle/wrapper/gradle-wrapper.jar` 引导例外）
  /`.zip` 一律不得 `git add`；原生引擎唯一允许形态是源码（统一归档于 `app/src/main/cpp/`，
  顶层 `Android.mk` + `Application.mk` 为 ndk-build 定义，`live2d/` 为独立模块），
  由 AGP `externalNativeBuild`（ndk-build）在 `assemble` 时直接从源码构建并打包进 APK，
  不设任何 jniLibs 缓存目录，也无 rebuild 脚本（`scripts/rebuild_engine.*` 已废弃删除）。
  NDK 版本固定为 `29.0.14206865`（`app/build.gradle.kts` `ndkVersion`）。
  例外：`.alp` 作为工程包可直接存在（构建时由 `:app:generateExamples` 从 `scripts/examples-src/` 生成，
  默认零内置，由 `lide.examples` 属性控制，见「精简治理」）。
  例外（2026-08-11 用户拍板，Squircle CE 字体组合）：`app/src/main/assets/fonts/*.ttf` 内置
  6 款等宽字体（jetbrains_mono / droid_sans_mono / fira_code / source_code_pro /
  anonymous_pro / dejavu_sans_mono，OFL 许可，来自 reference/squircle-ce-master/
  feature-fonts），宿主打包进 assets；`git ls-files '*.ttf'` 白名单仅允许该目录 6 款，
  其余二进制（.so/.dex/.jar/.zip 等）一律禁止。

验证命令（以下必须全部成立）：

```
rg -n "luajava|androlua|loadLibrary" app/src/main/kotlin/com/lioisme/lide/editor
rg -n "com\.lioisme" app/vendor/androlua/src app/vendor/sora-editor/src
git ls-files '*.so' '*.dex' '*.zip'
git ls-files '*.jar' | grep -v 'gradle/wrapper/gradle-wrapper.jar'
```

## 构建与验证

- Kotlin 编译检查：`.\gradlew.bat :app:compileArm64DebugKotlin --console=plain`
- 完整构建：`.\gradlew.bat :app:assembleArm64Debug --console=plain`
- 精简门禁（去重校验）：`.\gradlew.bat :app:verifyMinimalism`（依赖 assembleArm64Release）
- 跨 dex 断言（debug 直出）：`.\gradlew.bat :app:verifyMinimalismDebug`
- 引擎并入单 dex 集断言（引擎面类跨根 dex 恰一份 + 旧形态回归）随
  `:app:verifyMinimalism`（checkEngineDexMerge）与 `:app:verifyMinimalismDebug` 执行，
  `verifyRuntimeSurface` / `verifyReleaseLuaSurface` 任务已随 2026-08-09 引擎 dex 单份化删除
  （历史引用一律不得回退，CI 亦已移除）
- 上游隔离：`.\gradlew.bat :app:verifyVendorIsolation`
- sora 门面隔离（editor 包外零 `io.github.rosemoe` 引用，含 FQN 形态）：
  `.\gradlew.bat :app:verifySoraIsolation`（CI verify-sora-isolation job 挂接）
- 上游全树差异门禁（禁止未登记改动）：`.\scripts\verify-upstream-pristine.ps1`（Windows，
  缺省自动下载上游 tarball；CI 用 `scripts/verify-upstream-pristine.sh`，verify-vendor job 挂接）
  ——白名单机器副本 `scripts/upstream-pristine.json` 必须与
  `app/vendor/androlua/UPSTREAM.md` 台账一致（24 modified + 42 deleted，本地独有 = 0）

## 编辑器关键约定

- 初始化顺序（官方 demo 顺序，勿改）：`colorScheme` → `setEditorLanguage` → `setText`。
- TextMate 运行时只经 `TextMateRuntime`（进程级一次性，`preload` 幂等触发；状态机
  Loading/Ready/Failed，失败展示错误态 + 重试）初始化，且不得在 UI 线程调用。
- 文本快照可在后台线程取（`Content.toString` 内部持有 `ReadWriteLock`），
  但编辑器 API 调用（`setText` / `diagnostics` 赋值 / `subscribeEvent` 回调）保持主线程。
- 自动保存：快照与写盘均在 `Dispatchers.IO`；离开编辑器时经 `onRelease` 兜底保存。
- 编辑器会话（`EditorSession`）经 `EditorSessionViewModel` 持有（2026-08-09 Q8）：会话随
  导航回退栈条目存活，旋转/配置变更不重建（tab 列表/撤销历史/选中文件保留）；出栈时
  `onCleared` 委托 `session.release()`（兜底保存 + 清 Content 缓存）。`initialize()` 幂等
  （`initialized` 标志），重组后重复调用不得重置状态；文本写入一律走会话门面
  `insertText` / `replaceText`（统一脏标记 + 自动保存节流），禁止直接改 `selection.content`。
- 大文件（> 200 KB）：关闭括号高亮 / 当前块 / 自动换行，并跳过行级诊断。
- 补全、诊断、事件回调必须 `try/catch(Throwable)` 降级，不得让异常逃逸进 sora 主循环。
- **编辑器外观单一跟随宿主（2026-08-12 治理红线，替代 T17/T18/T19 三态方案）**：
  编辑器外观不是独立主题，是宿主外观（`LocalIsDarkTheme`）的派生——设置页**禁止**
  出现任何编辑器外观/主题选项（「编辑器主题」手选、`EditorThemeSheet`、三态
  `EditorThemeMode` 已删除），`editor_theme` 偏好键与 `projectDarkOverride`
  （工程 init.lua theme 驱动宿主）一并删除，历史引用一律不得回退。
  - **颜色单一事实源**：`EditorPalette`（editor 包）持有 3 套亮/暗双模主题
    （LIDE Classic = quietlight/darcula、Solarized Dual、Tomorrow Dual），
    激活主题名随宿主明暗切换（`EditorPalette.themeNameFor(isDark)`），
    经 `TextMateRuntime`（`themeRegistry.setTheme` + 进程级单例
    `LideTextMateColorScheme` 广播，`darcula`/`quietlight`/`solarized_*`/
    `tomorrow*` 为 THEMES 加载白名单）装配；宿主切换浅/深色时须触发编辑器重组。
  - **对比度门禁（G4）**：全部文本承载色 token 对主题背景 WCAG AA 严格档
    ≥ 4.5:1，由 `EditorPaletteContrastTest` 守护（解析 assets/textmate/ 全部
    激活主题逐 token 断言）——主题 JSON 任何改动必须过门禁。
  - **颜色分层契约（T19 延续）**：AppBar / 标签行 / 分隔线 / 空态一律宿主
    Material3（AppBar = surface + onSurface，与主界面/设置界面同款）；编辑器
    文本区独立着色（亮侧 quietlight `#F5F5F5` / 暗侧 darcula `#242424` 等
    双模主题色，sora 原生 `editor.background`/`background` 映射
    WHOLE_BACKGROUND）；补全窗 / tooltip 浮层 = `surfaceContainerHighest`
    （浮层 > 画布 > chrome 三层递进）。`EditorChromeColors.kt` 色带已随 T19
    删除。历史记录「DARK = RGB(26,28,30)」为宿主 surface 误记，以契约为准。
- 文件 → 语言 scope 唯一事实源为 `LanguageDetector`（未识别扩展名显式回落
  `text.plain`，绝不回退为 Lua）；宿主「可打开性」判定经 `LanguageDetector.isKnownFile`
  消费同一表，不另维护扩展名白名单。
- **sora 门面契约（2026-08-11 解耦审计 B 级收口）**：sora 类型（`io.github.rosemoe.*`）只允许
  出现在 `editor` 包（文本级能力）与 vendor 源码；宿主层（session / ui / project / engine 等）
  一律经 `EditorContent`（不透明文本句柄：`of` 构造 / `snapshot` 后台快照 / `isSameAs` 身份
  判定）与 `EditorController`（命令门面：`bind`/`unbind`/`isShowing`/`setSelection`/`undo`/
  `redo`/`canUndo`/`canRedo`，主线程 + try/catch 降级）访问——禁止 import 或 FQN 引用 sora
  类型，`:app:verifySoraIsolation` 门禁守护（CI verify-sora-isolation job）。

## 宿主路径与引擎前缀约定（2026-08-11 解耦审计 C/D 级收口）

- 引擎外部存储根（`luaExtDir`）唯一宿主入口为 `engine/RuntimePaths`（`luaExtDir()` /
  `projectsDir()` / `defaultApkDir()`），一律走 public getter `getLuaExtDir()`；
  UI / project / 根 Activity 禁止直接触碰 `com.androlua.LuaApplication`（含 Kotlin 对 Java
  protected 字段的越权直读）；唯一豁免 = `LideApp`（继承点）与 `RuntimePaths` 本体。
- API 浏览器引擎归属判定消费 `apibrowser/EnginePackagePrefixes` 单一事实源
  （`ANDROLUA_PACKAGES` 全量引擎包 / `JNI_BRIDGE_PACKAGES` 桥接子集），分类器禁止自建引擎
  前缀表；新增引擎包只改该文件。

## API 浏览器反射线程契约（2026-08 简化，架构审计 A-1）

- build 期把枚举出的全部类名存为 `Snapshot.dexNames` + `Snapshot.entryNames`（宿主 dex
  枚举按进程缓存于 `ApiIndex.hostDexNames`，切工程不重扫）；「可加载性」判定为 O(1) 查表
  （`Snapshot.isKnown`），**只有已知类才 `Class.forName`**——不存在 `Class.forName` 全 dex
  负查找（`loadClassRef` 门控落地，2026-08-09 D1）。
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

## API 浏览器搜索语义红线（冻结审计结论）

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

## /libs 机制约定（工程 /libs 支持）

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
  编译开 = 全部工程 `.lua`/`.aly`（含 main.lua）编译为字节码——`.aly` 先包装
  `local layout=…\nreturn layout` 再编译并**改名 `.lua`** 入库（pro `gsub("aly$","lua")`
  同款，运行端 alyloader 凭 `LIDE` 魔数走 `loadfile`）；关 = 源码文本入库（双关时 `.aly`
  保留原名裸表文本）。加密开 = **仅对入库 `.lua`/`.aly`** DES-ECB 加密（编译开加密字节码、
  编译关加密源码文本），其余工程文件（图片/字体/数据）一律原样入库，关 = 原文。偏好键
  `compile_lua` / `encrypt_lua`（`SettingsManager` build 域），旧键 `compile_all` / `encrypt`
  不得回退读写。
- **打包图标（2026-08-09 P9 用户红线反转：LIDE 宿主自带图标）**：宿主 manifest **声明
  `android:icon="@drawable/icon"`**（`res/drawable/icon.png`，512×512，矢量设计渲染 PNG，
  宿主桌面图标 = 打包模板默认图标，同一资源）。产物图标机制不变：`ArscLookup`（解析宿主
  arsc 的 drawable/icon 资源 ID，纯字节级、不写死 ID）经 `AXmlManifest.applicationIconId`
  注入 `android:icon`（TYPE_REFERENCE）**仅对模板无 icon 属性时兜底**；模板自带 icon
  属性时原样保留、不重复注入（AXmlManifestTest 双路径用例）。工程根 `icon.png` 覆盖产物
  `res/drawable/icon.png` 条目数据即桌面图标生效（无工程图标显示宿主自带图标，pro bin.lua
  同款双份语义：`assets/lua/icon.png` 同时注入）。arsc 缺失/解析失败安全降级不注入。
- 签名档案三类：默认内置（AOSP testkey）、自定义导入（.jks/.p12）、应用内自生成
  （`SigningCrypto.generate` 零依赖手写 X.509 v3 自签 DER）。自签证书 version 字段必须
  `[0] EXPLICIT`（`A0 03 02 01 02`）包装——裸 INTEGER 会被 OpenJDK 误读为 serialNumber，
  平台回环解析报 `algid parse error`。
- 加密格式唯一事实源：工作单 §4（MAGIC `LIDE\x01\0\0\0` + DES/ECB/NoPadding + PKCS7，
  密钥 `{0xFF,0xDD,0xBB,0x99,0x77,0x55,0x33,0x11}`）；宿主引擎 Lua 库与宿主资源不加密
  （工程 `.aly` 属工程文件，按打包双开关参与编译/加密，见上）。
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
  实测结果）；`preferAbi` 残留为未携带架构时构建侧回落「宿主全部」。设置页 ABI 下拉
  仅列出宿主实际携带项（未携带不显示）。
- 打包 UI 唯一入口为构建打包对话框（编辑器 ⋮ 菜单「构建 APK」触发；`BuildApkDialog`，
  2026-08-11 P5 起**打开即构建**，无构建中可配置项）；签名方案 v1/v2/v3、默认 ABI、
  安装方式、「优化打包」（`stripNativeLibs`）等一律在 AndroLua 设置页持久化（`BuildOpts`），
  构建时对话框直接消费，产物装配仍以宿主实际携带 + `ZipUtils.listAbis` 实测为准。
- 打包多架构验证走 `scripts/build-apks.ps1`（矩阵参数化：`-Abis`/`-Types`/`-Variants`，
  默认 6 变体；产物签名逐包校验）。

## 运行时基座契约（打包 dex 直用宿主，2026-08-09 引擎 dex 单份化收口，P0-7 冻结结论）

- **用户红线（2026-08-08）**：所有 dex 必须位于 LIDE 本体安装包 APK 根目录，禁止
  其他任何目录，禁止任何重复副本（引擎类在宿主安装包中唯一一份）。
- `:app` 对 `:androlua` 的依赖形态：`implementation` + `testImplementation`——引擎类
  （androlua/luajava/cglib/vendor Java 全量）直接并入宿主根 `classes*.dex` 恰一份，
  无 runtime buildType、无 `engineDexExtract`/`injectEngineDex`、无
  `assembleUniversalRuntime` 发布链（2026-08-09 全部删除，历史引用一律不得回退）。
- **打包 dex 直用宿主（P0-7，2026-08-08）**：不再为打包单独提供 dex、无 dex 替换模式——
  产物直接复用宿主根目录**全量 classes*.dex**（IDE 类 + 引擎类原样保留，接受工程用不上
  的多余内容）；无 `ENGINE_DEX_MARKER` 标记资产（旧 `assets/engine.dex.idx` 出现即
  `verifyMinimalism` 失败）；打包器以「根 dex 存在 `Lcom/androlua/LuaApplication;`」作
  **引擎存在性判定**，命中时 manifest 回落 `com.androlua.LuaApplication` + 移除 IDE
  MainActivity（`ManifestEdit.applicationName` / `removeActivities`），未命中保持宿主
  manifest；ABI 枚举/权限仍以宿主条目为准（模板 = 宿主，D-1 冻结结论）。
- 新增引擎侧 Lua 反射类：登记 `engineSurfaceDescriptors`（`app/build.gradle.kts`，与
  `src/runtime` 时代 `luaApiSurface` 同源演进；R8 keep 面见 proguard-rules.pro「引擎
  keep 面」段）——`checkEngineDexMerge`（verifyMinimalism 内）断言引擎面类跨根 dex
  恰一份且全部存在，遗漏即失败。
- **IDE 依赖形态（2026-08-09 单 dex 集）**：`ideDeps` 列表（compose ui/graphics/material3/
  icons-extended/activity-compose/navigation-compose/core-ktx/lifecycle-runtime-ktx/
  lifecycle-viewmodel-compose + lifecycle-viewmodel-ktx（2.8.7，Q8 会话 ViewModel）/
  reorderable/cascade/profileinstaller/apksig + sora-editor project 依赖双模块）一律
  `implementation`（随 :androlua 同集合并，无引擎/IDE 双 dex 拆分）；
  `constraints { implementation("org.jetbrains:annotations:23.0.0") }` 防 consistent
  resolution 冲突；新增 IDE 依赖时维持该形态（无 debugImplementation /
  releaseImplementation / compileOnly 拆分；`ui-tooling-preview` 已随 2026-08-09
  全量审计删除——无 @Preview 使用）。
- 引擎 dex 与全部构建产物在 `app/build/` 下，不入库。
- P0 全链验证：`.\gradlew.bat :app:verifyMinimalism`（release：载荷体检 + 引擎面类
  跨根 dex 恰一份 + 无旧 assets/runtime 形态）+ `:app:verifyMinimalismDebug`（debug
  直出同断言）+ 打包单测（ApkBuilderTest 全量根 dex 保留 / 无引擎兜底用例）。

## 引擎/IDE dex 断言语义（2026-08-09，源码组织五需求审计收口）

- `scanDexClassNames()` 解析类名必须走**二次间接**：`class_defs[].class_idx` 是
  **type_ids 索引**（`string_ids` 之外的另一张表），须 `type_ids[class_idx].descriptor_idx`
  → `string_ids[descriptor_idx]` 取描述符（偏移常量 `typeIdsSize/typeIdsOff = 0x40/0x44`）。
  任何「class_idx 直读 string_ids」的实现都是错的（历史曾致跨 dex 断言全部错位）。
- `checkEngineDexMerge`（verifyMinimalism 内，引擎并入单 dex 集后的唯一 dex 断言）：
  ①旧形态回归失败——`assets/engine.dex.idx` 标记 / `assets/runtime/` 内嵌 dex 出现即失败
  ②引擎面类全集（`engineSurfaceDescriptors`）跨根 dex 恰一处且全部存在（R8 keep 遗漏或
  :androlua 未并入即失败）③`Lcom/androlua/LuaActivity;` + `Lcom/androlua/LuaApplication;`
  根 dex 存在（引擎核心不缺）。2026-08-08 时代的 `checkDexDuplication` 四类断言（引擎 dex
  无 LIDE 类 / IDE dex 无引擎前缀 / 引擎 dex 无 IDE 树等）随双 dex 拆分机制一并删除——
  单 dex 集下不存在「引擎 dex vs IDE dex」之分，历史引用一律不得回退。
- 上游隔离门禁：`.\gradlew.bat :app:verifyVendorIsolation`（app/vendor 两目录零
  `com/lioisme/**` + UPSTREAM.md 台账字面量 + :androlua 构建脚本零外部依赖 + sora 构建脚本
  无外部 URL；CI verify-vendor job）；`app/vendor/androlua/UPSTREAM.md` 为改动台账唯一事实源
  （全树 diff 白名单 = `scripts/upstream-pristine.json`，见「构建与验证」）。

## 精简治理（去重门禁，2026-08-07）

- **内置示例**：`-Plide.examples=all|名单` 控制生成哪些 `.alp` 进 `assets/examples/`；
  默认不设置 = 零内置（APK 最瘦）。`scripts/examples-src/` 保留工程模板作为选样池，
  UI（ExamplesScreen）按 assets 实际存在过滤，空态展示 `examples_empty`。
- **引擎 dex 并入单 dex 集（2026-08-09）**：引擎类经 `implementation` 并入宿主根
  `classes*.dex` 恰一份，**无剔除开关**（`-Plide.embedRuntimeDex` 与
  `assets/runtime/classes.dex` 内嵌形态均已废弃，`verifyMinimalism` 发现即失败）；
  打包器按根 dex 引擎存在性判定装配（见「运行时基座契约」）。
- **死代码禁令**：`:app` / `:androlua` 禁止把空 `libs/` 目录声明为依赖（`fileTree("libs")`
  空声明与 `.gitkeep` 已清理）；各模块 compileSdk 保持一致（当前 36）。
- **去重门禁**：`:app:verifyMinimalism` 校验 release APK 无 jniLibs 预编译回包、白名单外
  `.so`、同 ABI 重复原生库、默认场景内置示例为 0，已接入 CI（verify-release job）。
- 改动打包 / 原生 / 示例链路必须重跑：`:app:verifyMinimalism` + `:app:testArm64DebugUnitTest --tests "com.lioisme.lide.build.*"`。

## 文档治理约定（opencode Agent 必守，2026-08-09 断舍离收口）

- **源码位置索引（禁止云端拉取）**：所有上游/参考源码（AndroLua Pro 4.4.2、sora-editor、
  Squircle CE、参考工程、社区语料）都在本仓库工作副本内，位置统一记录于
  `docs/SOURCE_LOCATION.md`（含 reference/、app/vendor/、本方实现地图三区）。
  改动相关功能必须先查该索引读本地文件，**禁止从 GitHub 等云端拉取替代**；
  reference/ 内 URL 仅溯源备注，不作为获取途径。

- **文档全集（无 discard 区）**：`docs/README.md`（入口）、`docs/WHITEPAPER.md`（白皮书）、
  `docs/kb/`（知识库，唯一事实来源：README / ARCHITECTURE / ENGINE_RUNTIME / EDITOR +
  androlua-community 语料库）、`docs/planned/backlog.md`（任务与技术债登记处）、
  `docs/in-progress/`（进行中工作单）。历史工作单/报告已全部删除，结论蒸馏在知识库中。
- **事实来源唯一**：当前事实一律以 `docs/kb/` 与 `docs/WHITEPAPER.md` 为准；当前工作以
  `docs/in-progress/`、`docs/planned/backlog.md` 为准。
- **任务文档生命周期**：新任务登记于 `docs/planned/backlog.md`；进行中的工作单放
  `docs/in-progress/`；**每完成一项任务，必须在工作单对应条目标记「（已完成）」
  并备注工作内容 + 日期 + 验收证据**（构建命令 / 设备验证 / 测试结果）。
- **完成后删除**：整份工作单全部完成后，把结论蒸馏进 `docs/kb/` 对应文档，
  然后删除该工作单（不再有 discard 归档区；git 历史保留原文）。