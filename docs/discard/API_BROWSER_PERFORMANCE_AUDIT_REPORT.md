# API 浏览器性能优化 —— 审计报告与任务执行单

> 立项：2026-08-03 ｜ 基线：`HEAD 9de85f38`
> 现象（用户实测）：API 浏览器界面性能极差——进入后长时间白屏转圈、滚动卡顿、打字掉帧。
> 前置结论：上一轮冻结事故（`docs/planned/API_BROWSER_FREEZE_FIX_PLAN.md`，根因 A/B/C）已修复
> 并真机验证通过（HEAD `9de85f38` 全部落地）。**本次是性能质量专项**，聚焦「体验级」问题：
> 冷启动时长、滚动徽章风暴、过滤击键开销、详情页反射语义、健壮性缺口。
> 本文档是唯一任务来源：每完成一项任务须在此文档标注「已完成」并简述工作内容。

---

## 一、审计范围与方法

- 范围：`ApiBrowserScreen.kt`（列表/搜索/过滤/行渲染）、`ApiClassDetailScreen.kt`（详情/成员过滤）、
  `apibrowser/` 包全部文件（`ApiIndex` / `ApiReflection` / `ApiCategoryClassifier` / `ApiUsageClassifier` / `ApiCategory` / `ApiUsage`）。
- 方法：全量源码走查（行号级证据）+ 数据规模实测（静态表解析计数）+ 历史文档核对
  （冻结审计/修复计划/重构计划，确认前置修复已落地且无重复）+ 用户决策对齐。
- 红线复核（AGENTS.md 契约）：
  - 反射查询接口（`rowInfo`/`isLoadable`/`classKind`/`isViewSubclass`）禁止 UI 线程直接调用 ✅（当前仅 `ApiBrowserScreen.kt:457` 经 `withContext(IO)` 调用）
  - 过滤禁止 UI 线程同步 O(n)（搜索路径已异步化，正文路径残留同步过滤，见 R3）
  - 搜索语义红线（动画唯一驱动 + pendingNavigation + 显式 clearFocus）已落地，**本次禁止触碰** ✅

## 二、数据规模基线（实测）

| 数据源 | 条目数 | 说明 |
|---|---|---|
| 静态类表 `assets/javaapi/android.lua` | 4196 | 4199 行，pro 类表平移 + 扩展 |
| 宿主 dex 动态枚举（androidx./material 前缀） | ~15988 | 一次 `DexFile.entries()` 全量枚举后前缀过滤 |
| R 资源类（android.R + 宿主 R 内部类） | ~106 | 反射枚举 |
| 工程 libs 动态枚举 | 0（当前工程） | 按工程增减 |
| **目录总规模** | **~20,629** | 全部行级反射/过滤的数据面 |

---

## 三、根因分析（R1–R5，全量证据链）

### R1（主因）：冷启动索引构建串行 3.5~5.6s，期间 UI 白屏转圈

- 证据链：
  - `ApiBrowserScreen.kt:78-84`：`produceState` 内 `withContext(Dispatchers.IO) { ApiIndex.build(...) }`，
    **快照发布前 UI 无列表可显示**（`ApiBrowserScreen.kt:178` 转圈 + `api_loading`）。
  - `ApiIndex.kt:120-188`：`build()` 单方法串行执行五个阶段：
    1. `parseClassTable` 静态表解析（实测约 1.1s，20K 级正则匹配）；
    2. 宿主 dex 全量枚举（~16K 条目分类）；
    3. 工程 libs 枚举；4. R 类反射补齐；5. `assignUsages()` 反射规则链（≤ `USAGE_BUDGET_MS = 2000ms`，`ApiIndex.kt:26`）；6. `warmUpRowInfo()` 行信息预热（≤ `ROW_INFO_WARMUP_BUDGET_MS = 2500ms`，`ApiIndex.kt:30`）。
  - **两个时间预算都挂在「首个可用列表」之前**：进入界面 → 全部反射预算耗完 → 才发布第一个快照。
    冷启动可交互时间 = 1.1s + 2000ms + 2500ms ≈ 3.5~5.6s 白屏。
  - `ApiIndex.kt:123-125`：缓存命中路径只对「完整构建过」的进程有效；**热重启（进程被杀）每次重复全量串行**。
- 影响：用户每次冷进入 API 浏览器先看 3.5~5.6s 转圈；叠加 R2 未预热风暴后体验雪崩。

### R2：行级反射「首扫风暴」——fling 时负查找反复重启 + ART 全局锁

- 证据链：
  - `ApiBrowserScreen.kt:455-464`：`ClassRow` 用 `remember(entry.name) { mutableStateOf }` + `LaunchedEffect(entry.name) { withContext(IO) { rowInfo } }`。
    **行离开组合 → LaunchedEffect 协程被取消**；fling 时行快速进出组合，同一类名反复「启动→取消→重启」。
  - 取消的协程即使 compute 已执行大半也**不落缓存**（结果被丢弃），下次启动重新 `Class.forName` 全扫描。
  - 预热预算只覆盖列表头部：`warmUpRowInfo`（`ApiIndex.kt:212-219`）按列表顺序串行，
    2500ms 只够 ~1K 行；**~16K 条 androidx 条目从未预热**，首次滚动全走负查找/冷加载。
  - 负查找代价：类不存在需扫描全部 dex class table，单次 10~100ms（冻结审计实测）；
    并发 `Class.forName` 持有 ART ClassLinker 全局锁，IO 池并发放大锁竞争。
  - 结论：负缓存机制（`ApiIndex.kt:100` 非空包装）本身已正确，**问题是行级调用点把「计算」与「行生命周期」绑定**。
- 影响：首滚/快速 fling 时徽章逐个闪现、卡顿、偶发 ANR 前兆。

### R3：正文过滤仍为 UI 线程同步 O(n)，分类计数 lazy 于组合阶段

- 证据链：
  - `ApiBrowserScreen.kt:123-125`：`bodyList = remember(snapshot, selectedCategory) { filterSnapshot(...) }`
    —— **组合阶段同步执行**，对 ~20K 条目做 indices 序列过滤（大小写敏感 contains，`ApiBrowserScreen.kt:582-590`），
    单次实测 2~6ms；每帧组合若 key 变化（snapshot 替换）重复执行。
  - `ApiIndex.kt:73-75`：`categoryCounts`/`usageCounts` 为 `by lazy`，首次读取发生在
    `ApiBrowserScreen.kt:134`（组合阶段），20K 条目 `groupingBy` 分组 ~1-3ms 一次性抖动。
  - 搜索路径（`ApiBrowserScreen.kt:129-133`）已正确异步化（Default + 协程取消即防抖）——**正文路径是对称的遗漏**。
- 影响：分类切换一帧掉帧；`usageCounts`（`ApiIndex.kt:76-78`）全项目零引用（死代码，见 R5b）。

### R4：详情页每击键 O(n) 成员过滤，每成员解析两次 memberName + ignoreCase 重建转换

- 证据链：
  - `ApiClassDetailScreen.kt:130`：`rows = remember(detail, q, titles) { buildRows(...) }`——**每击键全量重建**。
  - `ApiClassDetailScreen.kt:181-201`：`buildRows` 内对每个成员 `memberNameOf(item)` **调用两次**
    （188 行过滤条件一次 + 189 行构造 Member 一次），`memberNameOf`（204-214 行）含 `indexOf('(')` + `lastIndexOf('.')` + `split(' ')` 字符串解析。
  - `contains(q, ignoreCase = true)`（188 行）：**每击键对每个成员重建小写转换**（Android 的 `toLowerCase` 每次分配新串）。
  - 大类的 `methods` 可达数百条（如 Activity/Context），每击键总开销 3~10ms 落 UI 线程。
- 影响：详情页打字过滤掉帧；输入法联想每键触发一次重组放大开销。

### R5：健壮性缺口（a–e）

- **R5a**：`build()` 无并发守卫——两个 Screen 实例（或 build 与 invalidate 交错）可并发执行全量构建，
  互踩 `snapshot`/`builtProjectRoots`/`hostClassLoader`，且重复构建浪费预算（`ApiIndex.kt:120-125` 无锁 check-then-act）。
- **R5b**：`usageCounts`（`ApiIndex.kt:76-78`）零引用死代码（重构时已删除用途筛选，未删计数）→ 必须删除（用户决策）。
- **R5c**：`ApiReflection.reflect`（`ApiReflection.kt:30-53`）与 `ApiIndex.rowInfo` 缓存**不共享**——
  列表行已缓存的可加载结论，详情页再次 `Class.forName`（负查找重复一次）；
  反向：详情页反射成功后也不回填行缓存。
- **R5d**：详情反射失败（类不存在/初始化异常）时 `ApiClassDetailScreen.kt:106-118` 显示
  **无限转圈 + `api_no_result` 文案**——语义错误（不是「无结果」而是「反射失败」），且无重试路径。
- **R5e**：混淆 keep 规则缺失——release 构建下宿主自类名被混淆后，动态枚举的字符串类名
  （`com.lioisme.lide.R` 及宿主自类）`Class.forName` 失配，API 浏览器功能退化（当前仅 debug 验证，属隐患）。

---

## 四、修复方案（F1–F5 + 呈现决策）

### F1（P0）：两阶段快照发布 —— 先可用后完善（R1）

- `ApiIndex` 拆分：
  - `buildBase(context, roots): Snapshot`：只做**非反射阶段**（静态表解析 + dex 枚举 + R 类 + 分类预索引 + 计数预计算 + 并发互斥守卫），预算 ~1.1~1.5s，发布 Base 快照。
  - `completeBuild()`：后台补全反射阶段（`assignUsages` 预算内 + `warmUpRowInfo` 预算内），完成后**原子替换**进程级快照。幂等（已完整则直接返回）。
- `ApiBrowserScreen`：`produceState` 只等 Base（快速可交互）；另起 `LaunchedEffect(Unit)` 后台执行 `completeBuild()`，不阻塞 UI。
- 阶段二完成前列表已可交互：未预热条目由行级惰性补齐（F2 单飞机制），徽章延迟为**渐进完善**而非白屏。
- 快照替换时 `nameLower`/`categories`/`categoryCounts` 与 `classes` 同步重建（O(n) 一次，毫秒级）。
- 缓存命中路径保留：完整快照直接复用，二次进入即时。

### F2（P0）：行级反射单飞（in-flight 永不取消）+ 后台低并发预热（R2）

- `ApiIndex.rowInfoAsync(name): RowInfo`（挂起）：
  - **快路径**：`rowInfoCache[name]` 命中直接返回（UI 只读缓存，契约允许，零协程开销）；
  - **慢路径**：`withContext(Dispatchers.IO + NonCancellable) { rowInfoCache.getOrPut(name) { computeRowInfo(name) } }`——
    CHM `getOrPut` 天然单飞（同一 name 仅一个 compute），`NonCancellable` 保证**计算不随行生命周期取消**，
    结果必落缓存；下次命中即 O(1)。fling 风暴从「反复重启全扫描」降为「首次一次扫描」。
- `ClassRow` 改用 `produceState(entry.name)` 读取 `rowInfoAsync`——行销毁不取消计算，徽章最终必达。
- 预热保持**串行 + 预算上界**（并发 `Class.forName` 放大 ART 全局锁竞争，串行最优）。
- 线程契约不变：UI 不直接调 `rowInfo`（计算路径始终 IO）。

### F3（P1）：分类预索引 + 计数 eager 预计算 + 死代码清理（R3、R5b）

- `Snapshot` 新增 `categories: Map<ApiCategory, List<ApiClassEntry>>`（buildBase 期一次分组）；
  `filterSnapshot` 分类过滤直接取子集再按查询过滤 → 分类切换 O(选中子集) 而非 O(全部)。
- `categoryCounts` 改为 build 期 eager 字段（去掉 `by lazy`）；删除零引用 `usageCounts`（R5b）。
- 正文 `bodyList` 保持同步（组合阶段）但基于预索引后单次开销降到亚毫秒级；
  若审计后仍掉帧，再切换异步（本次以预索引为准，控制变量避免过度工程）。

### F4（P1）：详情页成员预索引 —— 每击键仅一次 contains（R4）

- `remember(detail)` 预计算成员元数据（`Member(text, name, nameLower)`），`memberNameOf` **每成员仅解析一次**。
- 过滤改 `nameLower.contains(qLower)`（q 每击键 lowercased 一次）——保留原 ignoreCase 语义（pro 对齐），
  但零每击键逐成员转换。
- 击键路径剩余工作量 = 纯 contains 一趟 O(成员数)，无字符串解析无分配。

### F5（P2）：健壮性收敛（R5a、R5c、R5d、R5e）+ 不可用行优雅呈现（用户决策）

- **R5a**：buildBase 互斥锁（synchronized check-then-act），杜绝并发构建互踩。
- **R5c**：`reflect` 前先走 `rowInfo` 缓存判定（命中即复用扫描结论，未命中单次扫描同时服务两处）；
  反射成功后结论已入 rowInfoCache，两缓存语义一致。
- **R5d**：详情页三态（Loading / Loaded / Failed）；Failed 显示 `api_detail_failed` 文案 + 重试按钮
  （重试 = 递增生产 key 重跑反射），删除「无结果」误用。
- **R5e**：app 模块 R8 keep 规则：`-keepnames class com.lioisme.lide.**`（动态枚举按字符串类名定位的宿主类）；
  核对 `build.gradle.kts` minify 开关后落地。
- **不可用行呈现（用户决策，P1）**：
  - **删除** `api_unavailable` 文本标签（`ApiBrowserScreen.kt:489-495`）与两处 `strings.xml` 资源条目；
  - **禁止**对 API 文本灰显/中划线等任何文本样式改动（`ApiBrowserScreen.kt:479-487` 恢复统一 `onSurface` 色）；
  - 非文本视觉指示：徽章行尾增补 **error 色调 6dp 圆点**（无障碍 contentDescription 由徽章语义承担，圆点纯装饰）。

### 实施优先级与依赖

| 优先级 | 方案 | 依赖 | 理由 |
|---|---|---|---|
| P0 | F1 两阶段快照 | — | 主因，冷启动体验量级改善 |
| P0 | F2 行级单飞 | — | 主因，滚动风暴消除（与 F1 互补） |
| P1 | F3 预索引 + 死代码 | F1（预索引入 buildBase） | 对称异步化的替代方案 |
| P1 | 不可用行优雅呈现 | — | 用户显式决策 |
| P1 | F4 详情预索引 | — | 独立低风险 |
| P2 | F5 健壮性 | F1-F4 主体 | 收尾 |

---

## 五、风险与注意事项（安全红线）

1. **CHM 空值陷阱**：`ConcurrentHashMap.getOrPut` 的 compute 禁止返回 null（NPE 且负缓存永久失效），
   负缓存必须非空包装——既有契约，F2 沿用 `RowInfo(kind=null, loadable=false)`。
2. **NonCancellable 只包反射计算**：`rowInfoAsync` 中 NonCancellable 限于 compute 块本身，不得包裹 UI 逻辑或列表构建。
3. **快照一致性**：Base → Complete 替换时 `classes` 引用变化（usage 补全），`nameLower`/`categories`/`categoryCounts` 必须同源重建；
   LazyColumn `key = name` 不变，行状态保留，替换无闪烁。
4. **时间预算上界**：`assignUsages`/`warmUpRowInfo` 保持 2000/2500ms 硬预算，completeBuild 有上界，绝不悬挂。
5. **线程契约**：UI 线程只读缓存（快路径），计算路径恒 IO；`buildBase`/`completeBuild` 恒 IO 调用。
6. **搜索语义红线不可触碰**：`searchActive` 意图 state、动画唯一驱动协程、pendingNavigation、
   `rememberSearchBarState()` 无参、Dialog 内 BackHandler、收起显式 clearFocus——本报告所有改动不得影响这些既有机制。
7. **行为等价**：F4 保留 ignoreCase 子串语义；过滤结果集顺序（快照顺序）不变；详情分组顺序（pro 对齐）不变。
8. **资源清理**：删除 `api_unavailable`、`usageCounts` 时同步清理引用与资源条目，禁止遗留半吊子引用。
9. **验证纪律**：静态验证（编译 + R1 门禁）先行，模拟器验证一次性集中执行（控制变量/二分定位），禁止频繁 ADB。

---

## 六、验证策略（科学方法，最小化 ADB 依赖）

- **控制变量法**：F1/F2/F3/F4/F5 分任务隔离落地，每任务静态验证先行
  （编译 + 代码审查 + 行号级核对），模拟器只做总体验证；验证失败时按「git diff 定位任务 + 任务内二分」。
- **二分定位**：若某判定项失败，先定位到具体任务改动点，再在该改动范围内二分开关，不重复全量验证。
- **验证矩阵（里程碑 T8 一次执行）**：
  1. 冷启动进入 API 浏览器：列表可交互 ≤ 1.5s（目标 0.5s 级）；白屏不再等 3.5~5.6s 反射预算；
  2. 首滚 30 秒：无卡顿、行徽章即时出现（不再逐个闪现）；
  3. AndroidX 分类（~16K 子集）：首滚徽章 2s 内补齐，无负查找风暴（logcat 无异常、无 ANR）；
  4. 详情页（Activity 级大类）：过滤每击键流畅（单帧完成）；
  5. fling 反复 10 次 + 快速滚动：无 ANR、徽章最终全部正确（单飞验证项）；
  6. 二次进入：即时（缓存命中路径）；
  7. 搜索展开/收起往返 10 次：无死循环、无 ANR、导航正常（防回归）；
  8. 不可用行（如 AndroLua 历史类）：error 圆点显示、无「当前设备不可用」文本、API 文本不灰显。
- 静态门禁：`.\gradlew.bat :app:compileArm64DebugKotlin --console=plain`；
  R1 门禁：`rg -n "luajava|androlua|loadLibrary" editor/src` 零命中。

---

## 七、任务执行单（依赖排序 + 重要度）

| 编号 | 任务 | 关联 | 依赖 | 重要度 | 状态 |
|---|---|---|---|---|---|
| T1 | 本审计报告（任务执行单） | — | — | 高 | 已完成 |
| T2 | F1 两阶段快照：`buildBase`/`completeBuild` + 互斥守卫 + Screen 两段发布 + 快照原子替换 | R1, R5a | T1 | 高 | 已完成 |
| T3 | F2 行级单飞：`rowInfoAsync`（快路径 + NonCancellable 计算）+ `ClassRow` 改 `produceState` | R2 | T1 | 高 | 已完成 |
| T4 | F3 分类预索引 + 计数 eager + 删 `usageCounts` + 正文过滤快查 + 不可用行优雅呈现（删文本标签/圆点指示/资源清理） | R3, R5b, 用户决策 | T2 | 高 | 已完成 |
| T5 | F4 详情页成员预索引（元数据一次解析 + 预小写 contains） | R4 | T1 | 中 | 已完成 |
| T6 | F5 健壮性：reflect 缓存共享、详情三态 + 失败文案 + 重试、R8 keep 规则 | R5c, R5d, R5e | T2-T5 | 中 | 已完成 |
| T7 | 里程碑静态验证：编译门禁 + R1 零命中 + 全量代码审查 | — | T2-T6 | 高 | 已完成 |
| T8 | 里程碑模拟器验证矩阵（控制变量/二分）+ AGENTS.md 契约同步 + 本文档收尾 | — | T7 | 高 | 已完成 |

## 八、执行记录

（逐任务追加）

- **T1 审计报告：已完成（2026-08-03）** 本文档创建。全量证据链 R1-R5（行号级）、
  数据规模基线（4196 静态 + ~16K dex = ~20,629 总目录）、修复方案 F1-F5（P0-P2 优先级与依赖）、
  风险注意事项（CHM 空值陷阱 / NonCancellable 边界 / 快照一致性 / 预算上界 / 线程契约 / 搜索红线 /
  行为等价 / 资源清理 / 验证纪律）、验证策略（控制变量 + 二分 + 8 项矩阵）、任务执行单（T1-T8）写入。

- **T2 两阶段快照（F1）：已完成（2026-08-03）** 修改 `ApiIndex.kt` + `ApiBrowserScreen.kt`：
  - `ApiIndex.build()` 拆分为 `buildBase()`（非反射阶段：静态表解析 + dex 枚举 + R 类 + 模块扫描，
    约 1s 级，快速发布基础快照）与 `completeBuild()`（反射补全：`assignUsages` + `warmUpRowInfo`，
    预算上界 2s+2.5s 不变，完成后原子替换完整快照，幂等）；
  - 新增 `buildLock` 互斥 + `completePhaseDone` 幂等标志（R5a 一并落地），
    `invalidate()` 同步复位；快照缓存路径（工程根未变复用）保留；
  - `ApiBrowserScreen` 改两段发布：单一 `LaunchedEffect(Unit)` 顺序执行
    buildBase → 发布列表（立即可交互）→ completeBuild → 刷新进程级完整快照，
    白屏只等非反射阶段，徽章由行级惰性补齐渐进完善；
  - 门禁：`:app:compileArm64DebugKotlin` BUILD SUCCESSFUL（仅存 DexFile 既有 deprecation 警告）；
    R1 门禁零命中。

- **T3 行级单飞反射（F2）：已完成（2026-08-03）** 修改 `ApiIndex.kt` + `ApiBrowserScreen.kt`：
  - `ApiIndex.rowInfoAsync(name)`（挂起）：快路径缓存命中直接返回（UI 同步读缓存零开销）；
    慢路径 `withContext(Dispatchers.IO + NonCancellable)` 内 CHM `getOrPut` 单飞 compute——
    同一类名仅一次全 dex 扫描，计算不随行生命周期取消，结果必落缓存，fling 不重启负查找风暴；
  - `ClassRow` 由「`remember` + `LaunchedEffect` + 同步 `rowInfo`」改为
    `produceState(entry.name) { rowInfoAsync }`，行销毁不取消计算（行重建命中 O(1) 快路径）；
  - 线程契约不变（计算恒 IO，UI 只走缓存快路径）；
  - 门禁：编译 BUILD SUCCESSFUL；R1 门禁零命中。

- **T4 分类预索引 + 不可用行呈现（F3 + 用户决策）：已完成（2026-08-03）** 修改 `ApiIndex.kt` + `ApiBrowserScreen.kt` + `strings.xml`：
  - `Snapshot` 新增 `categories: Map<ApiCategory, List<Int>>`（build 期一次预分组，索引对齐 classes/nameLower，
    分类过滤 O(选中子集)）与 `categoryCounts`（build 期 eager 预计算）；删除零引用 `usageCounts` 死代码（R5b）；
  - `filterSnapshot` 走预索引：全量 + 空查询直接返回原表（零拷贝），分类 + 查询仅扫选中子集；
  - 不可用行优雅呈现（用户决策）：删除 `api_unavailable` 文本标签与两处 `strings.xml` 资源条目；
    API 文本统一 `onSurface` 色（撤销灰显）；徽章照常显示，追加 6dp error 色调圆点（非文本指示）；
  - 门禁：编译 BUILD SUCCESSFUL；`usageCounts`/`api_unavailable` 全仓零残留。

- **T5 详情页成员预索引（F4）：已完成（2026-08-03）** 修改 `ApiClassDetailScreen.kt`：
  - 新增 `MemberMeta(text, name, nameLower)` 元数据与 `buildMemberGroups`：`memberNameOf` 每成员仅解析一次 + 预小写；
  - 击键路径 `buildRows` 仅做一趟 `nameLower.contains(q)`（q 每击键 lowercase 一次）——
    保留原 ignoreCase 子串语义（pro medit 对齐）与「头部恒显 + 空组跳过 + 分组顺序」行为；
  - 门禁：编译 BUILD SUCCESSFUL。

- **T6 健壮性收敛（F5）：已完成（2026-08-03）** 修改 `ApiReflection.kt` + `ApiClassDetailScreen.kt` + `strings.xml`：
  - **R5c**：`ApiReflection.reflect` 先经 `ApiIndex.isLoadable`（rowInfoCache 共享）判定，
    不可加载直接降级（消除详情页对同一类名的重复全 dex 扫描），可加载则继续完整反射；
  - **R5d**：详情页三态 `DetailUiState`（Loading / Loaded / Failed）：Loading 转圈 + `api_loading` 文案
    （修正原先 spinner 下误挂 `api_no_result` 的错误）；Failed 显示新增 `api_detail_failed` 文案 + 重试按钮
    （`retryTick` 递增触发 produceState 重启），删除「无限转圈 + 无结果」误用；
  - **R5e**：核对 `app/proguard-rules.pro` —— 既有规则 `-keep class com.lioisme.lide.** { *; }`（第 15 行）
    已覆盖宿主自类与 R 内部类按字符串类名反射的场景，androidx/material 由依赖 consumer 规则保护、
    framework 类不在 APK 内 —— **无需新增规则**（避免宽泛 keep 导致 APK 膨胀），仅记录结论；
  - 新增字符串资源 `api_detail_failed` / `api_retry`（en/zh）；
  - 门禁：编译 BUILD SUCCESSFUL。

- **T7 里程碑静态验证：已完成（2026-08-03）**
  - 全量代码审查（行号级）：`ApiIndex.kt`（两阶段构建/互斥/幂等/预索引/单飞快慢路径）、
    `ApiBrowserScreen.kt`（两段发布顺序/搜索红线未触碰/不可用行呈现）、
    `ApiClassDetailScreen.kt`（三态/重试/成员预索引）逐段核对通过；
  - 修正审查发现：`completeBuild` 未用局部变量清理、`RowInfo` KDoc「灰显」措辞更新（现为 error 圆点）、
    `hostLoader` KDoc `build()` → `buildBase()`、正文列表注释同步预索引语义；
  - 门禁：`:app:compileArm64DebugKotlin` BUILD SUCCESSFUL；R1 门禁（editor/src 内
    `luajava|androlua|loadLibrary`）零命中。

- **T8 里程碑模拟器验证：已完成（2026-08-03）**
  - 设备：`emulator-5554`（x86_64 API 34；备选 `127.0.0.1:5555` 同镜像）；产物
    `app-x64-debug.apk`（`:app:assembleX64Debug`）。控制变量：同一 APK 冷启动（force-stop 后
    am start）计时，logcat 打点为准；
  - **性能矩阵**：优化前 base 阶段 3355ms（超矩阵目标）→ 应用两项 buildBase 优化后
    `built base 21085 classes, 18 modules in 1149ms`（warm ART；冷进程实测 1065ms）、
    complete 阶段 711-803ms。优化内容（归入 T7 审查修正之外追加）：`parseClassTable`
    Regex.findAll → 引号 indexOf 手工扫描（毫秒级）；dex 前缀判定由
    `hostDexPrefixes.any{}` 内联为两个 `startsWith`（androidx. / com.google.android.material.），
    删除死列表 `hostDexPrefixes`。达成矩阵「白屏 ≤1.5s」目标（该目标由 base 阶段决定，
    完整阶段后台预算不变）；
  - **交互矩阵**：20 次连续 fling 无 ANR/FATAL，API 浏览器内 Choreographer 零掉帧
    （仅冷启动期 1 次与进程启动相关的 skip）；搜索建议徽章/点击进入详情正常
    （`com.nirenr.Color`：分组 + 构造器 + getter + 字段全渲染）；成员过滤 "get" 仅剩 get*
    成员、空分组收敛（R4 ✓）；`android.support.annotation.Keep`（真实不可用样例，
    androlua 源码无此包）显示「该类反射失败」+ 重试，重试再次失败正常（R5c/R5d ✓）；
    二次进入（同进程）零构建日志 = 进程级缓存命中 ✓；3 轮快速展开/收拢无 ANR/死循环 ✓；
  - **二分定位**：搜索条点击后仅聚焦、偶发不弹全屏搜索（AndroidX 模拟器 + pinyin IME 环境）——
    同一环境基线 APK（git stash 恢复 HEAD 9de85f38 构建）行为**完全一致**（点击聚焦不弹窗），
    确认非本次改动回归；与冻结审计 T7 记录的真机（arm64 + 搜狗输入法）一次性验证通过一致，
    属测试环境 IME 行为差异，非应用缺陷；
  - **收尾**：`AGENTS.md` 追加/更新契约（两阶段构建与互斥、`rowInfoAsync` 单飞非取消、
    快照预索引内部面、不可用行非文本指示决策、详情三态与 isLoadable 共享、R5c/R5d）；
    门禁最终复跑 `:app:assembleX64Debug` BUILD SUCCESSFUL；
  - **遗留说明**：修改全部保留在工作副本未提交（与既往约定一致，由用户决定整体提交）；
    androidx/ 目录为基线遗留未跟踪文件，不在本次改动范围。
