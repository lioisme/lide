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
  /`.zip` 一律不得 `git add`；原生引擎唯一允许形态是源码（vendor 于 `build-scripts/jni/`），
  `app/src/main/jniLibs/` 是 `scripts/rebuild_engine.*` 的构建输出（gitignore），CI 在 `assemble` 前必须先重建。
  例外：`.alp` 作为工程包可直接存在（构建时由 `:app:generateExamples` 从 `build-scripts/examples-src/` 生成）。

验证命令（以下必须全部成立）：

```
rg -n "luajava|androlua|loadLibrary" editor/src
git ls-files '*.so' '*.dex' '*.zip'
git ls-files '*.jar' | grep -v 'gradle/wrapper/gradle-wrapper.jar'
```

## 构建与验证

- Kotlin 编译检查：`.\gradlew.bat :app:compileArm64DebugKotlin --console=plain`
- 完整构建：`.\gradlew.bat :app:assembleArm64Debug --console=plain`

## 编辑器关键约定

- 初始化顺序（官方 demo 顺序，勿改）：`colorScheme` → `setEditorLanguage` → `setText`。
- TextMate 运行时只经 `TextMateInitializer`（进程级一次性）初始化，且不得在 UI 线程调用。
- 文本快照可在后台线程取（`Content.toString` 内部持有 `ReadWriteLock`），
  但编辑器 API 调用（`setText` / `diagnostics` 赋值 / `subscribeEvent` 回调）保持主线程。
- 自动保存：快照与写盘均在 `Dispatchers.IO`；离开编辑器时经 `onRelease` 兜底保存。
- 大文件（> 200 KB）：关闭括号高亮 / 当前块 / 自动换行，并跳过行级诊断。
- 补全、诊断、事件回调必须 `try/catch(Throwable)` 降级，不得让异常逃逸进 sora 主循环。

## API 浏览器反射线程契约

- `ApiIndex` 的反射查询接口（`rowInfo` / `isLoadable` / `classKind` / `isViewSubclass`）
  禁止在 UI 线程直接调用——`Class.forName` 负查找（类不存在）需扫描全部 dex，单次可达
  数十毫秒，列表场景（数千条目）会造成 UI 冻结。
- 列表行反射信息一律经 `produceState` + `Dispatchers.IO` 获取，UI 只读惰性缓存结果；
  全部反射路径必须 `try/catch(Throwable)` 降级（含负缓存），且带时间预算上界。
- **两阶段构建**：`buildBase()` 只做非反射阶段（静态表解析 + dex 枚举 + 预索引），快速发布
  基础快照；`completeBuild()` 后台补全反射阶段（`assignUsages` + `warmUpRowInfo`，预算上界
  2s+2.5s）并原子替换完整快照。两者均须在 IO 线程调用；buildBase/completeBuild/invalidate
  内部互斥（`buildLock`），禁止绕开锁直接读写快照字段（性能审计 F1/R5a）。
- **行级单飞**：列表行必须经 `rowInfoAsync`（suspend）获取——快路径同步读缓存，
  慢路径 `NonCancellable + IO` 内 CHM `getOrPut` 单飞 compute；禁止在行 `LaunchedEffect`
  里直接 `rowInfo`（计算随行生命周期取消 → fling 反复全 dex 扫描，性能审计 R2）。
- **不可用行呈现（用户决策，2026-08 反转）**：不可用 API（loadable == false）的 API 文本
  用 Compose 原生 `TextDecoration.LineThrough` 中划线表示；禁止往文本内容里插入
  `-`/`~~` 等字符的「伪中划线」技巧。不再使用 error 圆点等附加指示。

## API 浏览器索引快照契约

- `Snapshot` 的 `categories`（分类 → 索引，build 期一次预分组）与 `categoryCounts`（eager）
  为内部优化面，过滤必须走预索引（O(选中子集)），禁止在 UI 线程对全量 entries 做 O(n) 过滤
  （性能审计 R3）；`usageCounts` 已删除（用途仅作徽章，不做筛选维度）。
- 详情页三态：`ApiReflection.reflect` 必须经 `ApiIndex.isLoadable` 共享行级缓存判定（R5c）；
  详情显示层必须用 `DetailUiState`（Loading/Loaded/Failed），反射失败显示
  `api_detail_failed` + 重试，禁止误用「无结果」文案或无限转圈（R5d）。

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

## /libs 机制约定（工程 /libs 支持，详见 docs/completed/LIBS_SUPPORT_WORK_ORDER.md