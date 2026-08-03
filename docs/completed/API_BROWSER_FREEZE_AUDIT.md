# API 浏览器界面无响应问题 —— 全量审计报告

> 审计对象：`:app` 模块 API 浏览器（ApiBrowserScreen + apibrowser 包）
> 审计日期：2026-08-03 ｜ 编译基线：`:app:compileX64DebugKotlin` 通过
> 审计方式：源码级静态审计（材料 3.1.4.0 反编译验证 + 项目代码逐路径追踪）

## 1. 摘要

API 浏览器界面进入后点击无反应、系统返回键失效，根因是**列表渲染路径存在 UI 线程同步反射**：
`ClassRow` 每行组合时在 UI 线程调用 `ApiIndex.isLoadable()`（`Class.forName`），而类表规模达
**4196+ 条**，其中大量类在宿主 dex 中不存在——ART 上类名"负查找"需扫描全部 dex class table，
单次 10~100ms，滚动时持续触发，主线程被周期性占满，表现为整个界面（含返回键）无响应。
列表加载前界面可返回，是因为索引构建在 IO 线程，UI 线程此时空闲。

另有历史根因（上一轮已修复）：`rememberSearchBarState` 显式传入的 `spring(...)` 每次重组创建
新对象，被 `rememberSaveable` 按引用比较判为 key 变化，导致 SearchBarState 每次重组重建、
无限重组冻结主线程。

## 2. 现象与时间线

| 时间点 | 现象 | 关键信息 |
|---|---|---|
| 列表加载前（loading） | 立即按系统返回键可返回设置界面 | **UI 线程空闲**（build 在 IO） |
| 列表加载后 | 点击任何位置无反应，返回键/返回按钮失效 | **列表渲染路径阻塞 UI** |
| 二次进入 | 更卡顿 | build 缓存命中 → 列表立即渲染，无 loading 缓冲 |

## 3. 审计范围与方法

- 逐行审查：`ApiBrowserScreen.kt`（474 行）、`ApiIndex.kt`、`ApiUsage.kt`、`ApiCategory.kt`、
  `ApiReflection.kt`、`ApiClassDetailScreen.kt`
- 反编译 material3 1.4.0 AAR 验证 SearchBar 交互链路（展开由 InputField 内部焦点/按压事件驱动）
- 统计类表规模：`assets/javaapi/android.lua` = **4196 个类名**（另有宿主 dex 枚举 androidx/material 类）
- 线程模型核验：逐处标注 `Dispatchers` / 同步调用点

## 4. 架构与线程模型事实

| 路径 | 线程 | 结论 |
|---|---|---|
| `ApiIndex.build()`（含 parseClassTable / enumerateDexFile / R 枚举 / assignUsages） | IO（produceState + withContext） | ✓ 合规 |
| `ApiIndex.classKind()`（行签名/接口判定） | IO（ClassRow 的 produceState） | ✓ 合规 |
| **`ApiIndex.isLoadable()`（行灰显判定）** | **UI（ClassRow 同步调用）** | **✗ 违规** |
| `ApiReflection.reflect()`（详情界面） | IO（produceState） | ✓ 合规 |
| `filterByQuery` / `filterByCategoryAndUsage` | UI，O(n) 纯字符串过滤，ms 级 | ✓ 可接受 |
| `Snapshot.categoryCounts / usageCounts`（lazy） | UI，O(n) 分组，ms 级 | ✓ 可接受 |
| `ApiUsageClassifier.classify`（缓存为 HashMap，注释限定单线程） | 仅 build 期 IO | ✓ 合规 |

## 5. 根因分析

### 根因 1（主因）：UI 线程同步反射风暴

`ApiBrowserScreen.kt:367`：

```kotlin
val unavailable = !ApiIndex.isLoadable(entry.name)
```

- 该行位于 `ClassRow` 组合体顶层，**每次组合都执行**（无 remember 包裹、无 IO 切换）；
- `isLoadable` → `ApiIndex.kt:224` `Class.forName(name, false, hostClassLoader)`：
  - 类存在：首次加载 1~10ms，之后 ClassLoader 命中微秒级；
  - **类不存在：ART 需扫描全部 dex 的 class table 才能确认 ClassNotFoundException，
    单次 10~100ms**（宿主 dex 数量越多越慢）；
- 类表 4196+ 条为 AndroLua 时代平移（含大量宿主不存在的第三方/历史类），**负查找占比高**；
- `LazyColumn` 只渲染可见行（~20 行），但**滚动持续组合新行** → 每滚几行触发一批反射，
  主线程周期性被占满 → 点击事件、返回键事件排队，表现为"无反应"。

**证据链**：loading 阶段返回键有效（UI 空闲）↔ 列表渲染后无响应（UI 反射阻塞）——
与线程模型完全吻合；二次进入更卡是因为 `ApiIndex.kt:97` 缓存命中后列表无缓冲直接渲染。

### 根因 2（放大因素）：条目规模

- `android.lua` 4196 条 + 宿主 dex 枚举（androidx./material，Compose 全家桶可达数千条）
  + libs dex|jar + R 内部类 → 总条目约 5000~8000+；
- 列表过滤与统计均为 O(n)（ms 级，合规），但**每一行"存在性反射"都乘以该规模**。

### 根因 3（已修复，历史回归）：SearchBarState 无限重建

上一轮审计已修复：`rememberSearchBarState` 内部 `rememberSaveable(initialValue,
animationSpecForExpand, animationSpecForCollapse)`，key 按**引用比较**；显式传入
`spring(...)` 每次重组创建新对象 → key 永不相等 → SearchBarState 每次重组重建 →
Animatable 重置 → **无限重组 → 主线程冻结 → 全界面无响应**。已恢复无参调用（默认参数为
MotionScheme 构造时缓存的稳定 spec 实例），并留注释防护。

## 6. 次要发现（非本次冻结根因，记录备查）

1. `SearchBar` 容器挂 `clickable` 兜底展开（不依赖库内部焦点机制）：合法，嵌套 clickable
   子级消费事件不受影响，保留；
2. 筛选按钮 + DropdownMenu 已移出 InputField 装饰器（独立 Row/Box）：标准组合，合规；
3. `ClassRow` 的 `produceState`（key1 = entry.name）在快速滚动时会频繁启停 IO 协程：
   ApiIndex 缓存命中后开销极小，非根因，但可通过行级 memo 优化（P1）；
4. `ApiClassDetailScreen` 的 `ApiReflection.reflect` 已在 IO 线程（`produceState` + 
   `withContext(Dispatchers.IO)`），无同类问题；
5. `ApiIndex` 三个反射缓存（loadableCache / kindCache / viewAssignCache）均为
   ConcurrentHashMap + 负缓存，并发安全，无泄漏。

## 7. 修复方案（分级）

### P0 —— 消除 UI 线程反射（本次无响应的直接根因）

- **方案 A（推荐，必做）**：`ClassRow` 中 `unavailable` 判定并入现有 IO 协程——
  扩展 `produceState` 产物为 `RowInfo(kind: ClassKind?, loadable: Boolean)`，
  IO 线程一次取 `classKind(name)` + `isLoadable(name)`；UI 首帧默认
  `loadable = true`（灰显状态随后修正），**UI 线程零反射**；
- **方案 B（建议，加速首帧）**：`ApiIndex.build()` 完成时在 IO 线程以预算
  （约 1.5s，参照 `USAGE_BUDGET_MS` 模式）批量预热 `loadableCache`，未覆盖条目默认可加载；
- 效果：列表渲染/滚动只做纯 Compose 布局，主线程无阻塞，返回键/点击即时响应。

### P1 —— 渲染稳定性

- `ClassRow` 固定行高（两行文本 + divider 为固定尺寸），减少 LazyColumn 测量抖动；
- 滚动时避免行级协程风暴（方案 A 已天然缓解，可不额外处理）。

### P2 —— 防御性约定

- 在 `ApiIndex` 反射接口（`isLoadable` / `classKind` / `isViewSubclass`）Kdoc 中标注
  **"禁止 UI 线程直接调用"**，并在 AGENTS.md 线程模型一节补充该约定；
- 代码评审检查单增加：`apibrowser` 包内任何 `Class.forName` 路径必须处于 IO 线程。

## 8. 验证计划

1. `.\gradlew.bat :app:compileX64DebugKotlin --console=plain` 编译通过；
2. 真机：进入 API 浏览器 → 列表加载后滚动列表、点击行、点返回按钮、按系统返回键，
   均须即时响应；连续滚动 30 秒无明显卡顿；
3. 二次进入（缓存命中路径）重复上述操作；
4. 打开筛选菜单选择分类/用途，列表过滤即时生效。
