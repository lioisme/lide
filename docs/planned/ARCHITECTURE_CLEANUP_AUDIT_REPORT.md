# lide 架构审计与「最优解」重构方案报告

> **状态**：计划中（审计定稿，实施未排期） · **日期**：2026-08-06
> **目标**：清理补丁式 / 妥协性 / 重复性代码与设计，从根因消除防御性复杂度，产出目标架构与分阶段迁移计划
> **验收标准**：见 §8（代码量、防御性 catch 计数、R1 门禁、编译与测试命令）
> **关联代码路径**：`app/src/main/kotlin/`、`editor/src/main/kotlin/`、`third_party/androlua/src/main/kotlin/`、`third_party/patches/`
> **审计方法**：三路并行深度审计（`:app` 48 文件 / `:editor` 18 文件 / `:androlua` Kotlin 层）+ 关键结论逐条人工核实，全部证据以 `file:line` 引用，可复现。本次审计未修改任何代码。

---

## 1. 执行摘要

lide 当前处于「功能已通、架构负债显著」阶段：R1~R5 边界遵守良好，运行时与编辑器契约清晰；但存在 **~59 处防御性兜底（45 处 `:app` + 14 处 `:editor`）、3 套负缓存/时间预算机制、4 个为对抗第三方组件缺陷而构建的补丁子系统**，以及文档与代码的明显漂移。

**大量复杂度不是来自业务需求，而是来自对少数底层缺陷的层层围栏。** 删掉围栏后，代码量与认知负担预计下降 30~40%，同时消除 3 个真实潜伏缺陷（详见 §4.1）。

### 核心结论（三项根因驱动约 80% 的问题）

1. **`ApiIndex` 基于「丢弃 dex 类名集合」的负缓存/预算/单飞机制过度设计** —— 保留枚举结果即可把「类存在性」判定从「O(全 dex 扫描)」降为「O(1) 查表」，两阶段构建可降为一阶段。
2. **语言数据双源 + 扩展名双表** —— `LanguageCatalog.kt` 硬编码 39 条与 `languages.json` 运行时零读取重复并存（且 `embeddedLanguages` 字段在拷贝中被静默丢弃）；`app` 端 `DOCUMENT_EXTENSIONS` 白名单与 `:editor` 的 `LanguageDetector` 各维护一份「扩展名→行为」决策，导致未知文件被错误地以 Lua 语法打开并施加 Lua 词法诊断。
3. **navigation-compose 2.8.x / material3 SearchBar 的第三方缺陷由自研补丁子系统承载** —— `NavigationGate`（203 行，含为压测工具埋的 `wasSettled` 日志）与搜索红线状态机（`searchActive`/`pendingNavigation`/`snapTo(0f)`）整体是版本升级后可删除的围栏。

---

## 2. 审计方法与范围

| 模块 | 范围 | 方法 |
|------|------|------|
| `:app` | `app/src/main/kotlin/` 全部 48 文件（~12,000 行） | 逐文件精读 + 跨屏模式归并 |
| `:editor` | `editor/src/main/kotlin/` 18 文件 + `editor/build.gradle.kts` | 逐文件精读 + 与 `:app` 交叉核对 |
| `:androlua` | `third_party/androlua/src/main/kotlin/`（lide 自有 Kotlin 层 9 文件）+ 对上游 Java 类的 lide 改动点 + `third_party/patches/` | git 双提交 diff（基线 `344b495` vs 最新 `63d0015`）+ 契约交叉核对 |
| 人工核实 | `ApiIndex`、`NavigationGate`、`LanguageCatalog`+`languages.json`、`TextMateInitializer`、`LuaEditor` 诊断收集器 | 源码直读复核子报告结论 |

豁免范围：`com/androlua/*.java`（上游 AndroLua Pro 4.4.2 vendor 基线）与 `com/android/cglib/*`（dexmaker 上游）内部质量不审计，只审计 lide 对它们的使用方式。

---

## 3. 现状评估：保留资产（设计中的正确内核）

以下为**应当原样保留、禁止动刀**的既定内核：

| 资产 | 位置 | 评价 |
|------|------|------|
| `LuaDiagnostics.Incremental` 增量词法扫描 | `editor/.../LuaDiagnostics.kt` | 单次语义、设计扎实，非补丁式 |
| R1~R5 模块边界 | `:editor` 零 luajava/androlua 泄漏 | 门禁通过 |
| 编辑器初始化时序 `colorScheme → setEditorLanguage → setText` | `LuaEditor.kt` | 官方 demo 顺序，勿改 |
| 文本快照 IO 线程 + 明白锁 | 会话层 | 正确方案 |
| `PermissionResolver` / `ApiUsageClassifier` 纯函数 + 单测 | `:app` / `:androlua` | 测试守护，保留 |
| `AXmlManifest` 字节级 AXML 解析 | `:androlua` | 必要 hack，有真机夹具覆盖 |
| `LuaDexLoader` W^X 内存加载 | `third_party/androlua` | **有意的**与 pro 的差异（工作单 §10.1），禁止回退 |
| `/libs` 三层模型 | 运行时 | 机制正确，仅需收敛重复入口（见 §4.3） |

---

## 4. 问题发现

### 4.1 P0 —— 会静默产出错误结果的正确性缺陷（优先修复）

**P0-1　类名枚举结果被丢弃，负查找成本被「躲开」而非消除**
- `ApiIndex.kt:172-176`：`enumerateDexFile` 已把宿主全部类名扫出，却只筛 `androidx.` / `com.google.android.material.` 前缀，其余类名**全部丢弃**。
- `ApiIndex.kt:403-419` `computeRowInfo`：对表内每类名执行 `Class.forName`；类不存在时 `Class.forName` 需全 dex 扫描，单次 10~100ms，数千条目即为秒级。这正是「两阶段构建（`buildBase`/`completeBuild`）+ 三个时间预算（`ApiIndex.kt:29-38`）+ `warmUpRowInfo` + `rowInfoAsync` CHM 单飞（`:396-401`）+ 非空包装负缓存（`:117-124`）」五大机制的**全部存在理由**——它们只是「缩小打击面」，不是根因阻断。
- **改正**：build 时把枚举出的类名存为 `Set<String>`，「可加载性」判定改为 `dexClassNames.contains(name)`（O(1) 查表），只对存在类才 `Class.forName`。由此可删除：`warmUpRowInfo`、`ROW_INFO_WARMUP_BUDGET_MS`、`assignUsages` 的 `checked % 64` 节流、非空包装负缓存注释契约、`rowInfoAsync` 的 `NonCancellable` 单飞（缓存命中即 O(1)）。
- 连带：同一类名最多被 `Class.forName` 三次、三套独立缓存（`rowInfoCache` / `viewAssignCache` / `ApiUsage.kt:41-42,60-95` 的 `FAILED` 哨兵）合并为一张 `Class<?>?` 缓存。
- 连带：AGENTS.md「API 浏览器反射线程契约」整节大幅简写。

**P0-2　语言目录双源真相（`:editor`）**
- `LanguageCatalog.kt:18-58` 硬编码 39 条「与 languages.json 完全一致」；`editor/src/main/assets/textmate/languages.json`（11,524 B）**运行时代码从未读取**（全仓 grep 仅命中注释与 proguard 规则），sora 原生 `GrammarRegistry.loadGrammars(jsonPath)` 未被使用，`TextMateInitializer.kt:89-103` 改为逐条 `loadGrammar` 硬编码循环。
- 更严重：JSON 中 `html`/`julia`/`markdown`/`php` 带 `embeddedLanguages` 映射，而 Kotlin 副本的 `LanguageSpec`（4 字段）无该字段——**嵌入语言映射在拷贝过程中被静默丢弃**。「完全一致」是注释承诺，实际已不一致。
- `find()` / `findByName()`（`LanguageCatalog.kt:67-69`）全仓零调用（死代码）。

**P0-3　扩展名双表 + `DEFAULT_SCOPE` 把未知文件当 Lua（跨模块契约破裂）**
- `app` 白名单 `DOCUMENT_EXTENSIONS`（`EditorComponents.kt:567-615`）允许打开 `csv`/`log`/`ps1`/`svg`/`markdown` 等；`:editor` 的 `LanguageDetector` 对它们无映射 → `LuaEditor.kt:156` `scope ?: DEFAULT_SCOPE` 兜底为 `"source.lua"` → **这些文件以 Lua 语法高亮打开，且 Lua 词法诊断作用于其上**。
- 同一「扩展名→行为」决策在 `app` 与 `editor` 各维护一份，无共享、无交叉校验。
- **改正**：`:editor` 提供唯一 API「扩展名 → `LanguageSpec?`」，未识别文件显式映射为 plain-text scope；`app` 的「可打开性」判定消费同一份表。

**P0-4　诊断收集器 stale closure（`:editor` 真实潜在缺陷）**
- `LuaEditor.kt:370` `config` 用了 `rememberUpdatedState` 防 stale；`:373-376` `resolvedScope` 只是 `remember(fileName)`，未包 `rememberUpdatedState`；`:431` `LaunchedEffect(Unit)` 闭包捕获首帧 `resolvedScope`，`:437` 的 Lua 判定永远读首帧值。
- 首帧若为 Lua 文件，切到 `.py` 后 `applyConfig` 重建语言并 `diagTick++`，收集器仍按旧 scope 判断 → **对非 Lua 文件执行 Lua 括号/块诊断，产生伪报错**。同一函数内两处同源风险，一处打了补丁一处漏掉，属典型的补丁式不一致。
- **改正**：`val currentScope by rememberUpdatedState(resolvedScope)` 并在收集器内读取。

**P0-5　`LuaConfigParser` 顶层 `[` 深度污染（`:androlua`）**
- `LuaConfigParser.kt:103-116`：`[` 在非长括号分支无条件 `depth++`，`]` 无配对递减；字段识别要求 `depth == 0`（`:44`）。init.lua 任一字段前出现 `t[1] = 2` 等索引表达式后，其后全部顶层字段（appname/packagename/**user_permission**/appver）静默解析失败，无任何报错。
- 下游：打包 APK 权限缺失（`PermissionResolver.declared`）、插件名回退默认（`PluginManager.kt:90`）。
- **改正**：`[` 不参与表嵌套计数（depth 只跟踪 `{}`），或 `]` 对称递减；补「前置索引表达式后字段仍可解析」单测。

### 4.2 补丁子系统（为第三方 bug 而建的整块围栏）

| 补丁 | 载体规模 | 对抗目标 | 根治方向 |
|------|---------|---------|---------|
| **NavigationGate**（`NavigationGate.kt` 全文 203 行） | 入队栈 + 16ms 轮询 + 5s 超时 + `WeakHashMap` + 双重 synchronized + 常驻 `drainNavigationQueue` + 相邻 navigate 合并；`wasSettled` 边沿检测（`:117-141`）**只为压测工具打印日志**；每次导航打 `Log.d` | navigation-compose 2.8.x 转场失步（NavHost$33$1 回调丢失） | 升级 navigation-compose 至含修复的版本（D-17 曾评估 2.9.x「方案 B 留待评估」），或自管单 Activity 栈；先移除压测埋点 |
| **SearchBar 红线状态机**（`ApiBrowserScreen.kt:105-138`、`ApiBrowserComponents.kt:125-187`） | `searchActive` 独立意图 state + `pendingNavigation` 挂起消费 + `LaunchedEffect(searchActive)` 唯一动画驱动 + `snapTo(0f)` 半程防御 + 「`rememberSearchBarState()` 必须无参调用」引用比较契约 + `ApiCollapsedSearchInput` 自绘折叠条绕开长按展开缺陷 | material3 `SearchBar`/`SearchBarState` 动画死循环、协程互斥取消 | 接受官方组件行为边界（简化交互），或整体自绘搜索视图；不再对抗组件动画 |
| **EditorTabs 手势逆向**（`EditorTabs.kt:155-214`，注释 `:68-73` 自证「已反编译二进制核验 API」） | ~40 行只读指针观察协程（`awaitEachGesture` + `dragMoved`）区分「长按松手=关闭菜单」与「长按拖动=排序」 | reorderable 3.0.0 拖拽库内部时序 | 简化交互契约（短按切换、长按拖动排序、关闭移入 `DropdownMenu` 常驻入口），删除位移观察协程 |
| **TextMate 单例三件套 + 双预热入口 + 失败也 markReady**（`LuaEditor.kt:84-88,100-324,332-352`、`TextMateInitializer.kt:24-60`、`LideApp.kt:27-34`） | `TextMateColorSchemeCache` + `EditorViewPool` + `EditorInitGate` 分散三处进程级状态；双预热入口（`LideApp.preloadTextMate` 与 `rememberTextMateInitializer` 各起线程）；`LuaEditor.kt:349` `markReady()` 无条件执行，**初始化失败被伪装成就绪** | 初始化耗时数百 ms / 失败降级 | 收敛为单一 `TextMateRuntime` 服务：`ready: StateFlow<Boolean>` + 初始化结果；app 预热仅提前触发，editor 只消费状态；失败显示错误态而非假装就绪 |
| **material3_searchbar_patch**（`third_party/patches/`，~127KB AOSP 副本） | 无任何构建引用，纯死代码 | 搜索组件缺陷（历史） | `git mv` 至 `docs/discard/` |

### 4.3 重复代码（去重即减数千行）

| 模式 | 出现点 | 收敛方向 |
|------|--------|---------|
| TopAppBar + 返回键 + colors 样板 | ~13 处（`LideNavHost.kt:193-364` 各 destination + 各 Screen） | 共享 `LideScaffold` / `LideTopBar` 包装组件 |
| `catch(Throwable){降级}` 防御兜底 | 45 处（`:app`）+ 14 处（`:editor`），其中 `ProjectEntryOps.kt` 8 函数全 catch、`LuaDiagnostics.kt:163-179` 三层语义（正常/降级/双降级） | 只保留系统边界单层 catch + 打日志；算法内部删除冗余防线 |
| `isRefreshing + LaunchedEffect(isRefreshing)` 键控刷新 | 4 屏（ProjectList/Examples/Permission/Plugin） | 共享刷新组件/工具 |
| init.lua 字段读取/改写 | 5 处（`ProjectManager.kt:121-131`、`BuildApkScreen.kt:83-96,350-361`、`ProjectInfoScreen.kt:77-97`） | `ProjectManager` 收敛为 init.lua 域服务，UI 不自摸文件 |
| 宿主 manifest 权限查询 + 权限池构建 | 4 处（`EditorScreen.kt:224-238`、`PermissionScreen.kt:241-243,599-605`、`ProjectInfoScreen.kt:223-224`） | 域服务收敛 |
| `readZip` 双份 | `ApkBuilder.kt:187-199` / `ApkSigner.kt:63-73` | 并入 `ZipUtils` |
| 剪贴板复制 `"lide-api"` | `ApiBrowserComponents.kt:409-418` / `ApiClassDetailScreen.kt:330-338` | 工具函数 |
| `alp_${ts}_$raw` 临时文件 + 冲突确认状态机 | 3 处（`ProjectListScreen.kt:102-113`、`ExamplesScreen.kt:116-128`、`PluginScreen.kt:80-87`）+ 双份状态类（`ConflictPending` vs `PendingImport`） | `AlpImportCoordinator` 单一入口 |
| 工程 .lua 文件扫描 | 2 处（`ProjectManager.kt:46-54` / `:108-115`） | 工具函数 |
| 导航参数块（navArgument×2 + dec + null 回退 pop） | 3 处（`LideNavHost.kt:279-289/320-337/351-364`） | `projectRouteComposable(route)` 辅助 |
| `SettingsManager` 21 键三元组（常量/读/写） | 3 份机械代码 63 行（`SettingsManager.kt:19-41/77-103/105-135`） | 声明式键表（`SettingsRegistry`），加减键一处改 |
| ZIP 实现四份 | 上游 `ZipUtil.java` + `util/ZipUtil` + `LuaUtil.zip-unZip` + lide `ZipUtil.kt` | lide 侧统一 `ZipUtil.kt`（已做），上游暴露面差异记录声明 |
| `"source.lua"` scope / 主题名散落 | 7 处（`EditorLanguage.kt:93`、`LuaEditor.kt:75`、`LanguageDetector.kt:108`、`LanguageCatalog.kt:40`、`LanguageKeywordRegistry.kt:26`、`TextMateInitializer.kt:83,108`、`LuaEditor.kt:383`） | 单一 `Languages` 常量对象 |
| 大文件阈值内联判断 | 5 处（`LuaEditor.kt:298,305,411,421,454`） | 常量 + 单一判定函数 |

### 4.4 设计妥协与死代码

| 项 | 位置 | 问题 | 方向 |
|----|------|------|------|
| EditorSession god object | `EditorSession.kt:38-361` | 一个类承担标签/Content LRU/脏标记/undo/自动保存/关闭策略/重命名；`TabState`（`:46-53`）与 `:editor` 的 `EditorTab` 双份相似状态类，靠 `EditorScreen.kt:368` 每帧 `map{it.tab}` 适配 | 拆 TabRepository + SessionLifecycle；状态类合并 |
| `Project` 死字段 | `model/Project.kt:3-12` | `appName = name` 恒等、`appVersion = "1.0"`、`files` 仅透传——全仓无读者 | 删除 |
| 打包模式包名比对 hack | `MainActivity.kt:46-50` | 打包产物与宿主共用同一 Activity，靠 `packageName != BuildConfig.APPLICATION_ID` 运行时跳引擎页 | 构建期 variant/BuildConfig 表达产物模式 |
| 错误定位丢目录 | `EngineResultParser.kt:28` + `EditorScreen.kt:182-193` | `substringAfterLast('/')` 只留 basename，子目录文件错误跳行静默失效（**现存功能缺口**） | 保留工程相对路径 |
| 死状态 `pendingImportGlobal` | `EditorScreen.kt:136` | 声明后零读写 | 删除 |
| 组合期副作用 | `MainActivity.kt:61-66` | `setContent` 组合体读 `pendingImportPaths` 并立即置空（组合期写 state 反模式）；`LideNavHost.kt:93-104` 只消费多文件意图的第一个 | `LaunchedEffect` + 一次性消费 Channel |
| 组合期 O(n) 过滤 | `ApiBrowserScreen.kt:142-145` | `filterSnapshot` 在组合期同步执行，与自身「过滤必须在后台」契约相悖 | `produceState + Dispatchers.Default` |
| 转场时长被压测基准绑架 | `NavTransitions.kt:26-33` | 「300→200→150ms 修订，验收 10 秒内 ≥20 次进出」——生产动画迁就测试 | 验收标准放宽或转场与交互解耦 |
| 反射参数不一致 | `ApiReflection.kt:38` vs `ApiIndex.kt:406,431` | 详情页 `Class.forName(className)`（默认 loader、`initialize=true`）vs 索引 `Class.forName(name,false,hostClassLoader)`；可能触发 static 初始化 | 统一走 `ApiIndex` 加载封装 |
| 指纹缓存不感知文件增删 | `ProjectManager.kt:68-75` | 指纹只含目录 mtime + init.lua，文件树增删后 `Project.files` 快照过期 | 指纹纳入文件清单 |
| 文本哨兵 | `ProjectManager.kt:133-143` | 读失败返回 `"-- error reading file"`、不存在返回 `"-- empty"`，用户可能把错误文本保存回盘 | `Result<String>` / 可空 + 显式处理 |
| 测试钥随包分发 | `BuildApkScreen.kt:273-274` | 从 assets 读 `testkey.pk8`（签名私钥打进每个 APK 资产） | 首次启动生成或用户导入 |
| BuildApk 内存放大 | `BuildApkScreen.kt:271` + `ApkBuilder.kt:75` + `V2Signer.kt:48-111` | 峰值约 5× APK 大小 | 流式处理（可接受约束下，需文档化） |

### 4.5 文档与代码漂移（治理层面）

| 漂移 | 证据 |
|------|------|
| 白皮书阶段四仍「规划中；v1.0 暂不支持」，打包器已落地 | `WHITEPAPER.md:344-352` vs 决策 D-10（DECISIONS.md:38 已自注「待更新」） |
| 白皮书仍写「ClassLoader 隔离 + DexClassLoader」与「Intent + 文件级结果协议」 | `WHITEPAPER.md:13,30,293` vs D-02/D-08 单进程直启 + ActivityResult Extra 回传 |
| 白皮书 §7.1 引用已删除的 `third_party/prebuilt/`、`app/src/main/jniLibs/` 与 rebuild 脚本 | `WHITEPAPER.md:369-377,315` vs R5（ENGINE_RUNTIME.md:38 已纠正） |
| PROGRESS.md WP-003 仍记录已删除的 RunScreen / 文件协议 / EngineResult | `PROGRESS.md:56-99` vs D-05 |
| `AlpNaming` 与 `LuaConfigParser` 两套 init.lua 解析行为不一致 | `AlpNaming.kt`（裸正则，带尾注释时与结构化解析结果不同）vs `PluginManager.kt:90-95` 在用后者 |
| `PluginManager.installFromAlp` 与 `AlpManager.import` 重复且冲突 | `PluginManager.kt:36-55`（无条件 `deleteRecursively` 覆盖）vs `AlpManager.kt:68-81`（OVERWRITE/RENAME 双模式） |
| 契约字符串跨模块字面量漂移 | `:androlua` `EngineBridge.kt` 常量 vs `:app` `EngineActivity.java` 裸字面量、`LuaActivity.java:83` `ARG="arg"` | 契约常量收敛至 `:app` 内 `EngineContract` |
| vendor 只读声明与事实不符 | `third_party/androlua` 13 文件被改动（含 `LuaDexLoader.java:134` W^X 修复等），基线已从 `reference/` 去重归档，本地无 diff 基准 | 补 `third_party/androlua/UPSTREAM.md`（版本/导入日期/全部改动登记） |
| AXmlManifest 与 aapt2 输出耦合 | `AXmlManifest.kt:406-418`：`readPool` 只认 UTF-8/16（0x100），styled pool（0x400）静默解析错；夹具无生成命令注释 | `flags & 0x400` 显式抛 `AXmlException`；夹具来源写注释 |

---

## 5. 根因分析（为什么代码会变成这样）

1. **第三方组件缺陷被默认在业务层补偿**：六个补丁均有 WP/D# 决策号注释，说明每次都是从「解决问题」出发且已注记；但缺少「问题归因档案」——属于代码库自身还是第三方库。Kotlin/Compose 生态版本敏感，底层 bug 等不到修复时，围栏默认打在业务层，而围栏本身又成为新复杂度与不一致的来源（P0-4 是最典型例证：同函数一处补丁一处漏）。
2. **契约文档比代码更复杂**：「性能契约」演进成了复杂机制本身（两阶段构建、时间预算、导航门控队列、搜索红线状态机均有 AGENTS.md 专节），机制反过来要求更多文档——复杂度螺旋。
3. **缺少共享层与域服务**：`readZip`/剪贴板/权限池/alp 导入/init.lua 解析等重复，均因缺少共享 util 或域服务，「一物一处」原则未落实。
4. **功能先于重构的推进节奏**：D-03~D-17 决策密集反转（全局仓库→删除、打包暂缓→开工、示例资产化→.alp 化），修正性决策本身正确，但旧的文档与分支代码（死状态、哨兵、过时段落）未同步回收。

**结论**：不是开发者能力问题，而是流程缺少「每打一层补丁，必须同时登记根因消除路径」的制度约定。本报告的目标架构即为该约定的落地载体。

---

## 6. 目标架构设计（最优解）

### 6.1 设计原则（拟写入 AGENTS.md 新层）

1. **单一事实源（SSOT）**：任何数据（语言目录、扩展名→语言映射、init.lua 字段、Settings 键、契约常量）只存在一份，构建期或单测校验派生副本的一致性，禁止运行时二次硬编码。
2. **根因优先于补丁**：遇第三方缺陷，首选升级/换库/消费官方行为边界；围栏必须落入隔离适配层并登记「根因消除条件」（如：navigation 升级至 X 后可删 NavigationGate）。
3. **降级边界单层**：try/catch 只出现在系统边界（sora 回调、补全/诊断入口、反射出口），算法内部与对象内部不再叠加；每个 catch 必须打日志（带类名/方法名），禁止空 catch。
4. **复杂度预算**：任何新增机制必须能回答「删掉它会发生什么」；答不上来的机制不引入。

### 6.2 目标模块拓扑（物理模块不变，职责内聚）

```
:app（宿主）
 ├─ ui/            Compose 界面；共享 LideScaffold/TopBar/刷新组件
 ├─ navigation/    NavGraph 直连 + 顶层 shell（无 NavigationGate）
 ├─ domain/        ProjectDomain（init.lua 域服务）/ AlpImportCoordinator / SettingsRegistry
 ├─ engine/        EngineContract（契约常量）/ ActivityResult 直连
 └─ build/         打包管线（ZipUtils 收敛）
:editor（纯文本层）
 ├─ lang/          SSOT：LanguageRegistry（三表派生自单一数据源）
 ├─ theme/         TextMateRuntime 单例服务（ready 状态 + 失败态）
 └─ ide/           EditorCore（装配/诊断/补全入口，边界单层 catch）
:androlua（引擎运行时）
 ├─ AlpManager      导入唯一入口（PluginScreen 复用）
 └─ LuaConfigParser 权威解析（AlpNaming 委托）
third_party/patches/  删除（material3_searchbar_patch → docs/discard）
```

### 6.3 关键设计决策

| 主题 | 现状 | 目标 | 成效 |
|------|------|------|------|
| 语言目录 | 双源（Kotlin 副本 + JSON 死资产） | 运行时直接消费 `textmate/languages.json`（sora 原生 `loadGrammars` 路径，天然保留 `embeddedLanguages`）；或构建期一致性校验生成 Kotlin 常量 | 删 39 条镜像；恢复嵌入语言映射；消灭 find/findByName 死代码 |
| 扩展名契约 | app/editor 双表 + `DEFAULT_SCOPE`=Lua 兜底 | `:editor` 唯一 API `detect(fileName): LanguageSpec?`，未知 → 显式 plain-text scope；app 消费同一表 | 消除「csv/log 以 Lua 打开」；消除 P0-4 连带 |
| API 索引 | 两阶段 + 5 预算 + 单飞 + 负缓存包装 | `dexClassNames: Set<String>` 一次构建；`isLoadable/classKind` 查表 O(1)；反射只针对存在的类 | 删 warmUp/预算/单飞/非空包装；AGENTS.md 契约节大幅简写 |
| 导航 | NavigationGate 补丁子系统 | 升级 navigation-compose；或单 Activity 自管栈 | 删 203 行 + 压测埋点 + 16ms 轮询 |
| 设置 | 21 键三份手写 | 声明式 `SettingsRegistry`（key → realm/type/default） | 加减键一处改 |
| 导入 | 3 条重复冲突路径 | `AlpImportCoordinator`（内部复用 AlpManager 双模式） | 冲突状态机合并；PluginManager 只留 scan/load |
| 标签状态 | `TabState`/`EditorTab` 双份 + 每帧适配 | 单一状态类型 | 删每帧 `map{it.tab}` |
| 产物模式 | 包名比对 hack | BuildConfig variant 表达 | 运行时决策消除 |
| 契约字符串 | 跨模块字面量 | `EngineContract` 单点 + JVM 测试断言两侧相等 | 编译期可校验 |

### 6.4 保持不动的回归基线

- 编辑器初始化时序（`colorScheme → setEditorLanguage → setText`）；`/libs` 三层模型与 `LuaDexLoader` W^X 内存加载；`LuaActivity` 全局注入与 `import.lua` 首行依赖；R1~R5 边界；引擎源码构建（AGP externalNativeBuild，无 jniLibs/rebuild 脚本）；打包链的 AXML/签名方案本身（仅做显式拒绝与契约收敛）。

---

## 7. 分阶段实施计划

### Phase A：真实缺陷修复（低风险，单测守护，优先执行）

| # | 任务 | 证据 | 验收 |
|---|------|------|------|
| A-1 | ApiIndex 保留 dex 类名集合 → `loadable` 查表 O(1)；删 warmUp/预算/单飞；合并三套缓存 | §4.1 P0-1 | API 浏览器列表滚动/详情页无全 dex 扫描；`:app:testX64DebugUnitTest` 绿 |
| A-2 | 语言目录单一事实源（消费 JSON 或构建期校验）+ 一致性单测 | §4.1 P0-2 | `embeddedLanguages` 不再丢失；新增语言只需改一处 |
| A-3 | `rememberUpdatedState(resolvedScope)` + 未知扩展名 → plain-text scope | §4.1 P0-3/P0-4 | 打开 `.csv`/`.py` 不再出现 Lua 伪报错；单测覆盖切文件场景 |
| A-4 | `LuaConfigParser` 深度计数修复 + 前置索引表达式用例 | §4.1 P0-5 | 单测：索引表达式后字段仍可解析 |
| A-5 | 错误定位保留工程相对路径（子目录跳行） | §4.4 | 子目录文件错误 → 「跳到出错行」命中 |

**完成记录**：

- **A-1（已完成 2026-08-06）**：`ApiIndex` 重构为「全量 dex 类名集合 + 合并反射缓存」——
  `Snapshot.dexNames: Set<String>` 提供 O(1) `loadable` 判定（无 Class.forName 负查找）；
  删除 warmUp / 2s+2.5s 预算 / 行级单飞 / 节流逻辑；`ApiUsageClassifier`、`ApiReflection.reflect`
  改走 `ApiIndex.loadClassRef` 共享缓存；删除死代码 `hostLoader()`。
  验收：`:app:compileArm64DebugKotlin` + `:app:testX64DebugUnitTest` 全绿。
- **A-2（已完成 2026-08-06）**：语言目录改为单一事实源——`textmate/languages.json`
   转成 sora 原生 `{"languages":[...]}` 结构（语法路径补 `textmate/` 前缀），`TextMateInitializer`
   直接经 `LanguageDefinitionReader.read` 批量 `loadGrammars`，删除硬编码 `LanguageCatalog.kt` /
   `LanguageSpec.kt`（git rm）；新增 `LanguageCatalogConsistencyTest`（39 语言解析、嵌入映射
   html=4/julia=5/php=7/markdown≥40、资产文件存在性、Detector/KeywordRegistry scope 一致性）。
   验收：`:editor:testDebugUnitTest` 28 通过 + `:app:compileArm64DebugKotlin` 全绿。
- **A-3（已完成 2026-08-06）**：P0-3/P0-4 修复——`LanguageDetector` 改为单一事实源 API
   （`detect(): String` 永不返回 null，未识别扩展名显式回落 `text.plain`，不再以 Lua 兜底；
   `isKnownFile()` 供宿主「可打开性」判定消费）；新增纯文本文法资产并登记入语言目录
   （`plaintext/`，`text.plain` scope）；`LuaEditor` 诊断收集器经 `rememberUpdatedState` 读取
   最新 scope（修复 stale closure）；顺带修复扩展名查表 bug（原代码查 `".lua"` 带点键，
   映射表实际为无点键 → 所有扩展名文件全部误落 Lua）；app 删除 43 项重复扩展名双表，
   `isDocumentFile` 改为消费 `:editor` 表 + 纯文本附加集。
   验收：`:editor:testDebugUnitTest` 29 通过（新增未知扩展名回落用例）+ `:app:compileArm64DebugKotlin` 全绿。
- **A-4（已完成 2026-08-06）**：`LuaConfigParser` 深度计数修复（P0-5）——`[` 索引括号不再
   参与表嵌套计数（depth 只跟踪 `{}`），顶层 `t[1] = 2` 等索引表达式不再毒化其后全部字段；
   新增 4 个回归用例（索引表达式后字段、表内 `[key]` 不泄漏、长字符串/注释共存）。
   验收：`:androlua:testDebugUnitTest` 35 通过。
- **A-5（已完成 2026-08-06）**：错误定位保留工程相对路径（§4.4）——`findErrorLocation` 增加
   `projectPath` 参数，路径在工程内时裁剪为相对路径（含子目录），`EditorScreen` 传入
   `project.path`；「跳到出错行」对子目录文件命中会话标签。
   验收：`:app:testX64DebugUnitTest` 通过（新增子目录/工程外回退用例）。

### Phase B：收敛重复（减载约 70%）

- **B-1（已完成 2026-08-06）**：域服务收敛——
  - `AlpImportCoordinator`（`app/.../import/`）：三屏（工程列表/示例/插件）共用的
    「URI→临时文件 + planImport + 冲突挂起状态机 + 按模式导入」单一入口；删除双份
    同构冲突状态类（`ConflictPending`/`PendingImport`）与三处 `alp_${ts}_$raw`
    临时文件样板；`PluginScreen` 改走 `AlpManager` 导入（§4.5：删除 `installFromAlp`
    无条件覆盖路径）；
  - `ProjectManager` 收敛为 init.lua 域服务：新增 `readInitContent` / `initField` /
    `initArrayField` / `writeInitFields`；`BuildApkScreen`、`ProjectInfoScreen` 不再
    自摸文件；删除 `BuildApkScreen.declaredPermissions` 重复实现（改用
    `loadUserPermissions`）。
  验收：`:app:compileArm64DebugKotlin` + 全模块单测绿。
- **B-2（已完成 2026-08-06）**：共享 UI 层——新增 `LideTopBar`（返回键 + 标题 +
  可选图标 + actions + 标准配色），替换 9 处逐屏复制样板（Settings / EditorSettings /
  Help / LuaManual / Examples / Plugin / Permission×2 / ProjectInfo / BuildApk /
  ProjectList 首页保留自定义标题与溢出菜单）。`ApiBrowserScreen` 为搜索条定制顶栏
  （C-2 范围），`ApiClassDetailScreen` 保留 titleLarge 定制样式。
  验收：`:app:compileArm64DebugKotlin` 绿。
- **B-3（已完成 2026-08-06）**：工具收敛——`ZipUtils.readEntries`（合并 ApkBuilder /
  ApkSigner 双份 readZip，带 skipBlankNames 语义）；剪贴板复制收敛到
  `ApiBrowserComponents.copyToClipboard`（删除 `ApiClassDetailScreen.copy` 重复）。
  验收：`:app:compileArm64DebugKotlin` + `:app:testX64DebugUnitTest` 绿。
- **B-4（已完成 2026-08-06）**：新增 `SettingsRegistry` 声明式键表（21 个 EditorConfig
  键的「常量 + 读取 + 写入」三元组 → 单一 BoolKey/IntKey 表驱动 load/applyTo）；
  `SettingsManager.initEditor/updateEditor` 改为表驱动，加减键一处改。
  验收：`:app:compileArm64DebugKotlin` 绿。
- **B-5（已完成 2026-08-06）**：状态合并——`EditorTab`（:editor）扩展
  `canUndo/canRedo` 成为单一标签状态类，删除 `EditorSession.TabState` 与
  `EditorScreen` 每帧 `map{it.tab}` 适配；删除 `Project` 死字段
  （`appName`/`appVersion`/`files`）；删除死状态 `pendingImportGlobal`。
  验收：`:app:compileArm64DebugKotlin` + `:app:testX64DebugUnitTest` 绿。

### Phase C：组件替换（升级释放围栏）

- **C-1（部分完成 2026-08-06）**：删除 NavigationGate 压测埋点（`wasSettled` 边沿检测 +
   逐导航 `Log.d`，原 `:117-141` 仅为压测工具打印日志）；`NavTransitions` 时长注释解除
   压测基准绑架（150ms 仅为产品手感）。navigation-compose 2.9.x 升级属根因消除路径
   （D-17 方案 B），按审计 §9 风险缓解「迁移分支验证，失败则保留 NavigationGate 并登记
   根因消除条件」——未经真机压测验证前保留门控（本环境无法设备验证）。
- **C-2（部分完成 2026-08-06）**：`material3_searchbar_patch`（~127KB 死代码，无构建引用）
   已 `git mv` 至 `docs/discard/`。SearchBar 红线状态机（searchActive/pendingNavigation/
   动画互斥）为 AGENTS.md 冻结契约，官方组件行为边界未在真机验证前不拆除。
- **C-3（已完成 2026-08-06）**：`EditorTabs` 交互简化——删除位移观察协程
   （`awaitEachGesture` + touchSlop 判定，~40 行）；关闭菜单改为每标签常驻 ⋮
   `IconButton` + `DropdownMenu` 入口；长按手势仅保留拖拽排序语义。
   验收：`:app:compileArm64DebugKotlin` + `:editor:testDebugUnitTest` 绿。
- **C-4（已完成 2026-08-06）**：新增 `TextMateRuntime` 单一服务——三件套进程级状态
   （`TextMateColorSchemeCache` / `EditorInitGate` / 双预热线程）合并为一个
   `ready: StateFlow<RuntimeState>`（Loading/Ready/Failed）状态机；`LideApp.preloadTextMate`
   收敛为 `TextMateRuntime.preload`（幂等）；编辑器消费状态而非自起线程；初始化失败
   展示错误态 + 重试（不再 `markReady()` 伪装就绪）。`MainActivity` 产物模式改 BuildConfig
   variant 属遗留（§4.4，另立任务）。
   验收：`:app:compileArm64DebugKotlin` + 全模块单测绿。

### Phase D：治理收口

- **D-1（已完成 2026-08-06）**：文档漂移修复——
  - WHITEPAPER：阶段四/五由「规划中；暂不支持」更新为「已完成」（打包器 + v1/v2 签名
    已落地）；§1/§2/§3/§6 的「ClassLoader 隔离 + Intent + 文件级结果协议」更正为
    「同进程直启 + ActivityResult Extra 回传」；§6.3/§7.1 的 `third_party/prebuilt/`、
    `jniLibs/`、`scripts/rebuild_engine.*` 引用更正为 R5 源码构建模型（ndk-build +
    AGP externalNativeBuild，NDK 29.0.14206865）；风险表 NDK 26.x 更正。
  - PROGRESS.md：WP-003 历史段落加「2026-08 更正」标注（文件协议与 RunScreen 已删，
    指向 kb/ARCHITECTURE.md 当前事实），保留存档价值。
  - `AlpNaming` 与 `LuaConfigParser` 两套 init.lua 解析行为收敛（§4.5）——
    `AlpNaming.resolveKind/resolveName` 改走 `LuaConfigParser` 结构化解析（防带尾注释
    时与 PluginManager 结果不一致）。
  - 治理矩阵同步：`docs/TASKS.md`、`docs/kb/README.md` §2 状态更新为实施中。
- **D-2（未实施 2026-08-06）**：`third_party/androlua/UPSTREAM.md` 改动登记表、
  AXmlManifest styled-pool 显式拒绝、`EngineContract` 收敛——遗留（属 :androlua 上游
  治理，另行排期）。
- **D-3（未实施 2026-08-06）**：防御性 catch 清理（~59 处边界收敛）——遗留（改动面广，
  需逐点审计语义，另行排期）。
- **D-4（已完成 2026-08-06）**：AGENTS.md 契约简化——API 浏览器反射线程契约整节大幅
  简写（A-1 后 O(1) 查表，删除两阶段构建/预算/单飞/非空包装长篇契约）；编辑器约定
  更新（`TextMateInitializer` → `TextMateRuntime` 三态服务；`LanguageDetector` 单一
  事实源 + 未知扩展名回落 `text.plain`；宿主「可打开性」经 `isKnownFile` 消费同一表）；
  搜索红线节保留（C-2 冻结契约，未真机验证不拆除）。

每阶段完成后：`.\gradlew.bat :app:compileArm64DebugKotlin --console=plain` + 对应单测 + R1 门禁（`rg -n "luajava|androlua|loadLibrary" editor/src` 零命中），任务文档对应条目标记「（已完成）」+ 日期 + 验收证据。

**遗留项（另立任务，不在本单收口）**：C-1 navigation-compose 2.9.x 升级与 NavigationGate
拆除（需真机压测验证）；C-2 SearchBar 官方行为边界验证后拆除红线状态机；D-2 上游治理；
D-3 防御 catch 清理；§4.4 的 MainActivity 产物模式 BuildConfig variant、指纹感知文件增删、
文本哨兵 Result 化、测试钥随包分发、组合期副作用 Channel 化。

---

## 8. 验收标准与度量

| 指标 | 当前基线 | 目标 |
|------|---------|------|
| 防御性 catch 合计 | ~59（45+14） | 降 50%+，且全部落系统边界并带日志（D-3 遗留，另立任务） |
| 双源/镜像表 | 语言目录 2 份、扩展名表 2 份、ZIP 实现 4 份、init.lua 解析 2 套 | 各剩 1 份（语言目录/扩展名/ZIP/init.lua 解析已收敛；ZIP 上游暴露面差异声明遗留） |
| 补丁子系统 | 5 个（NavigationGate/搜索红线/EditorTabs 手势/TextMate 三件套/searchbar patch） | 0 个（或全部带「根因消除条件」登记）——EditorTabs 手势、TextMate 三件套、searchbar patch 已消除；NavigationGate 与搜索红线保留并登记根因消除条件（C-1/C-2 待真机验证） |
| 死代码/死字段 | ~6 处 | 0 处（pendingImportGlobal/Project 死字段/installFromAlp/双份冲突状态类/剪贴板重复/readZip 重复已清除） |
| 文档漂移 | 6 处 | 0 处（WHITEPAPER 4 处 + PROGRESS 1 处 + AlpNaming 解析双套已修复） |

**工程门禁（每个 Phase 收口必须全绿）**：

```
.\gradlew.bat :app:compileArm64DebugKotlin --console=plain
.\gradlew.bat :app:testX64DebugUnitTest :editor:testDebugUnitTest :androlua:testDebugUnitTest
rg -n "luajava|androlua|loadLibrary" editor/src        # 必须零命中
git ls-files '*.so' '*.dex' '*.zip'                    # 必须为空
```

---

## 9. 风险与回滚

| 风险 | 缓解 |
|------|------|
| A-1 改动 API 浏览器热路径 | 单测 + 真机列表滚动验收；回滚点 = 单文件（ApiIndex + ApiUsage） |
| C-1 升级 navigation 引入新行为 | 先做迁移分支验证 D-17 方案 B 结论；失败则保留 NavigationGate 并登记根因消除条件 |
| 域服务收敛改动 UI 面广 | 每屏独立提交 + 截图对比；回归以 TASKS.md 现有验收流程为准 |
| 防御性 catch 删除后暴露上游异常 | 每个 Phase 保留边界 catch；异常一律落日志便于定位，不回退 |

---

*本报告为审计结论 + 目标架构定稿，实施排期与任务拆解待登记 `docs/planned/backlog.md` 后逐项执行；执行状态按文档治理约定在本文档对应条目标记「（已完成）」并同步 `docs/kb/README.md` §2 / `docs/TASKS.md`。*
