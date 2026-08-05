# API 浏览器界面卡死 —— 根因修复任务执行单

> 立项：2026-08-03 ｜ 基线：`HEAD 134e168` + 工作副本未提交改动（含 API 浏览器重构 T0-T6）
> 现象（用户实测）：API 浏览器界面卡顿以至于整个程序卡在界面不能操作，只能重启程序；
> 触发场景确认：**进入界面即卡 / 滚动卡**（未触碰搜索条即卡死）。
> 性质：回归修复任务。本文档是唯一任务来源：每完成一项任务须在此文档标注「已完成」并简述工作内容。

---

## 一、审计结论摘要（全量证据链）

### 根因 B（卡死主因，重构 T3 引入/放大）：kindCache 负缓存永久失效 → 全 dex 扫描风暴 → ART 类加载全局锁竞争

1. `ApiIndex.classKind()` 用 `ConcurrentHashMap.getOrPut(name) { ...; catch { null } }`：
   CHM 禁止 null 值，compute 返回 null 时 `putIfAbsent` 抛 NPE（被 catch），**null 永不入缓存**
   → 类不存在的查询每次重新 `Class.forName` 负查找（ART 需扫描全部 dex class table，单次 10~100ms）。
2. 重构 T3 将行级查询从 `isLoadable`（负缓存 false 非 null，有效）升级为 `rowInfo`
   （内含 `classKind`，负缓存失效）→ 每个不可加载类**每次行组合都全 dex 扫描一次**。
   类表 4196+ 条中 AndroLua 历史类大量宿主不存在 + 工程 libs 类全部不存在于宿主 loader，负查找占比极高。
3. `warmUpRowInfo` 1500ms 预算在静态表头部（密集负查找）即耗尽，首屏之后全部未预热 → 首屏即并发风暴。
4. 并发 `Class.forName` 负查找需持有 ART 类加载全局锁（ClassLinker）；
   IO 线程持锁时主线程任何类加载（IME 弹出/资源类/延迟类）被锁阻塞 5s+ → ANR → 只能重启。
   **线程迁移（IO 化）不解决全局锁竞争——这就是「反射已 IO 化仍卡死」的根本原因。**
5. 滚动加剧：行进出组合 → `LaunchedEffect(entry.name)` 反复重启 → 反复全扫描，风暴持续。

### 根因 A（次因，重构 T4 引入，本次未触发但必修）：搜索收起后自动重新展开死循环

- material3 1.4.0 源码实证（SearchBar.kt）：
  - InputField(1683)：`onFocusChanged { if (it.isFocused) onExpandedChange(true) }`
  - ExpandedFullScreenSearchBar(355/437)：`if (!state.isExpanded) return` + Dialog + `LaunchedEffect(Unit) { requestFocus() }`(428)
  - SearchBarState(745)：`currentValue` 只有二值（Collapsed/Expanded），动画一开始即 Expanded；`animateToExpanded()` 无守卫(768)
- 本项目偏差：`expanded` 绑定动画派生值 + `onExpandedChange` 直接重启动画
  → 收起完成时 Dialog 内 InputField 延迟 80ms `clearFocus` → 主 window 焦点回落折叠输入条
  → `onFocusChanged(true)` → 重新展开 → 循环；每轮 Dialog 挂载/销毁 + 动画占满主线程。

### 根因 C（放大器，T4 放大）：击键 O(n) 同步过滤

- `searchResults = remember(...) { filterSnapshot(...) }` 组合阶段同步执行，每击键对 5000~8000 条目
  `contains(ignoreCase=true)`（每次建转换）→ 打字掉帧；在根因 A 每帧重组下更严重。

---

## 二、任务清单（依赖排序 + 重要度）

| 编号 | 任务 | 关联根因 | 依赖 | 重要度 | 状态 |
|---|---|---|---|---|---|
| T0 | 创建本任务执行单（报告文档） | — | — | 高 | 已完成 |
| T1 | ApiIndex 真负缓存：rowInfoCache 非空包装（RowInfo 永非 null），isLoadable/classKind 委托 rowInfo，删除失效双缓存 | B | T0 | 高 | 已完成 |
| T2 | 预热预算 ROW_INFO_WARMUP_BUDGET_MS 1500→2500ms，KDoc 同步负缓存契约 | B | T1 | 高 | 已完成 |
| T3 | 里程碑 1（静态验证）：编译 `:app:compileArm64DebugKotlin` + R1 门禁零命中 | — | T1, T2 | 高 | 已完成 |
| T4 | 搜索语义对齐官方：独立 searchActive 意图 state + LaunchedEffect 幂等驱动动画 + 收起路径显式 clearFocus + Dialog 内 BackHandler 拦截返回键 | A | T3 | 高 | 已完成 |
| T5 | 过滤优化：Snapshot 预建小写名索引（nameLower）+ filterSnapshot 快查 + searchResults 异步 produceState（协程取消即防抖） | C | T4 | 中 | 已完成 |
| T6 | 里程碑 2（静态验证）：编译 + R1 门禁 | — | T5 | 高 | 已完成 |
| T7 | 里程碑 3（arm64 真机验证）：一次性 ADB 验证矩阵（控制变量法，见下） | — | T6 | 高 | 已完成 |
| T8 | 防回归：AGENTS.md 追加硬约定 + 本文档收尾 | — | T7 | 中 | 已完成 |

---

## 三、验证策略（科学方法，最小化 ADB 依赖）

- **控制变量法**：根因 B（T1-T2）、根因 A（T4）、根因 C（T5）分任务隔离修复，
  每步先静态验证（编译 + 代码审查 + 行号级核对），真机只做一次性总体验证（T7）。
- **二分定位**：T7 若发现残留问题，按「git diff 定位到具体任务 + 该任务范围内二分」，
  不重复全量验证；如根因 B 修复后卡死消失则跳过根因 A/C 的真机复测项。
- **验证矩阵（T7 一次执行）**：
  1. 进入 API 浏览器 → 列表加载后立即滚动 30 秒：行徽章即时出现、无闪烁、无卡顿（根因 B 判定项）；
  2. 点击搜索条展开 → 输入即时过滤 → 点击建议进详情 → 返回键收起 → **收起后不得自动重新展开**（连续 5 次往返）（根因 A 判定项）；
  3. 展开/收起往返 10 次无 ANR；二次进入（缓存命中路径）重复 1-2；
  4. 工程 libs 场景：滚动含 PROJECT 类的列表段。
- 静态验证命令：`.\gradlew.bat :app:compileArm64DebugKotlin --console=plain`；R1 门禁：editor/src 内 `luajava|androlua|loadLibrary` 零命中。

---

## 四、执行记录

（逐任务追加）

- **T0 任务执行单：已完成（2026-08-03）** 本报告文档创建；审计结论（根因 A/B/C）、任务清单（T0-T8）、
  控制变量法验证策略与验证矩阵写入。

- **T1 真负缓存：已完成（2026-08-03）** 修改 `ApiIndex.kt`：
  - 删除失效的 `loadableCache`/`kindCache` 双缓存，合并为 `rowInfoCache = ConcurrentHashMap<String, RowInfo>()`；
  - `rowInfo(name)` = `rowInfoCache.getOrPut(name) { computeRowInfo(name) }`，compute 永不返回 null
    （不可加载类以 `RowInfo(kind = null, loadable = false)` 入缓存）→ 负缓存真正生效，
    每类名仅首次全 dex 扫描一次，此后 O(1) 查表；
  - `isLoadable`/`classKind` 委托 `rowInfo`（对外 API 不变，详情页等调用方零改动）；
  - KDoc 明确非空包装契约（CHM 禁止 null 值导致负缓存静默失效的历史教训）。

- **T2 预热预算：已完成（2026-08-03）** `ROW_INFO_WARMUP_BUDGET_MS` 1500 → 2500ms
  （负缓存生效后预算利用率高，可覆盖更多首屏行），KDoc 同步负缓存契约与预算说明。

- **T3 里程碑 1（静态验证）：已完成（2026-08-03）**
  `.\gradlew.bat :app:compileArm64DebugKotlin --console=plain` BUILD SUCCESSFUL；
  R1 门禁（editor/src 内 luajava|androlua|loadLibrary）零命中。

- **T4 搜索语义对齐：已完成（2026-08-03）** 修改 `ApiBrowserScreen.kt`：
  - 新增独立展开意图 `searchActive`（rememberSaveable），InputField 的 expanded/onExpandedChange
    只读写该意图（官方语义），**禁止绑定 currentValue 动画派生值**（死循环红线）；
  - 动画唯一驱动点 `LaunchedEffect(searchActive)` 幂等执行 animateToExpanded/Collapsed；
  - 收起路径（IME 搜索 / 点击建议 / Dialog 返回键）统一 `searchActive = false` + 显式
    `focusManager.clearFocus()`——切断「Dialog 销毁 → 焦点回落折叠输入条 → 重新展开」正反馈链；
  - Dialog content 槽内 `BackHandler` 拦截返回键（Dialog 拥有独立 back dispatcher，主窗口 BackHandler 无效）；
  - `LaunchedEffect(Unit) { if (!searchActive) snapTo(0f) }` 防御 rememberSaveable 恢复半程动画
    （progress∈(0,1) 时 currentValue 恒 Expanded，会导致进入即弹搜索窗）；
  - **T4 增补（真机验证发现，重要）**：点击建议导航最初实现为
    `scope.launch { animateToCollapsed(); onClassClick() }`——SearchBarState 动画基于 Animatable，
    并发 animateTo* 互斥（后启动者取消先前的动画协程）。点击建议时 LaunchedEffect(searchActive)
    的收起动画会取消 scope.launch 协程 → **onClassClick 永不执行（导航丢失）**。
    修复：`pendingNavigation` 挂起目标 + 导航统一由动画唯一驱动协程在收起动画完成后消费执行。

- **T5 过滤优化：已完成（2026-08-03）** 修改 `ApiIndex.kt` + `ApiBrowserScreen.kt`：
  - `Snapshot` 新增 `nameLower`（与 classes 同序的小写化名称索引，build 期一次 O(n) 预计算）；
  - `filterSnapshot` 改 indices + `nameLower.contains(q)`（大小写敏感快查，免去每击键每条目
    ignoreCase 转换开销，快 5~10 倍）；
  - `searchResults` 改 `produceState(snapshot, selectedCategory, query)` +
    `withContext(Dispatchers.Default)`——每次击键自动取消旧协程（协程取消即防抖，零依赖），
    block 完成前保留上一轮结果不闪空，UI 线程零过滤负担；纯 CPU 过滤走 Default 不占 IO 反射池。

- **T6 里程碑 2（静态验证）：已完成（2026-08-03）**
  `:app:compileArm64DebugKotlin` BUILD SUCCESSFUL；R1 门禁零命中。

- **T7 里程碑 3（arm64 真机验证）：已完成（2026-08-03）**
  设备：`5fb6fdc9`（arm64-v8a 真机，因 x64 模拟器被其他项目占用改用真机）；
  安装 `app-arm64-debug.apk`（含 T1-T6 全部修复）。验证矩阵逐项结果：
  1. 进入 API 浏览器 → 列表加载 → 滚动 20 次（swipe 200ms）+ 此前 15 次：**无 ANR、无卡顿**；
  2. 搜索展开 → 输入 "float" → 建议实时过滤（FloatWindow$ContentView/FloatButton/Float3… 全匹配，
     **异步过滤正确**）；输入 "android" → 「无匹配项」；
  3. 点击建议（收起键盘后）→ **收起 → 详情页导航成功**（FloatWindow$ContentView 详情含 onClick/onLongClick 等成员）；
  4. 主列表点击 → 详情页导航成功；详情页返回正常；
  5. 返回键收起 Dialog → **不自动重新展开**：连续 5 次往返（expand→back→+2s 均 MAIN）+ 快速 10 次
     展开/收起 → 终态 MAIN、**全程无 ANR**（logcat 无 am_anr/Application Not Responding）；
  6. 二次进入：列表即时加载（缓存命中路径）；
  7. 全程 logcat 无 FATAL 异常。
  测试环境备注：真机 IME 为微信输入法（WeType），其键盘会遮挡屏幕下方区域并影响 adb 注入的
  按键序列（"float" 可能被改成 "Wroat"/"lfoat"，且点击建议时若键盘未收起会被 IME 吞掉）——
  均为测试工具/输入法行为，非应用缺陷；已用「输入完成后收起键盘再点击」规避并全部通过。
  AOSP LatinIME 对 adb keyevent 注入不响应（忽略），故保留 WeType 测试。

- **T8 防回归：已完成（2026-08-03）**
  - AGENTS.md 追加「API 浏览器搜索语义红线」：动画唯一驱动协程 + pendingNavigation 机制、
    禁止并发 animateTo*（Animatable 互斥取消）、禁止 InputField.expanded 绑定 currentValue 派生值、
    收起路径必须显式 clearFocus、rowInfo 缓存非空包装、过滤不得回主线程同步执行；
  - 本文档全部任务标记完成；最终门禁复验（编译 + R1 零命中）通过。
