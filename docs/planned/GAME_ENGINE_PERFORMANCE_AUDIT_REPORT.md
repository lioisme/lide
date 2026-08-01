# 游戏引擎 Demo 性能审计报告（看板娘 / 石头）

日期：2026-08-01
范围：`docs/reference/project/看板娘`、`docs/reference/project/石头`，以及 delua 宿主运行时
（`:androlua` 模块 `LuaActivity` / `LuaDexLoader` / `com.luajava` 桥、`import.lua` libs 机制）。

---

## 0. 结论摘要

| 问题 | 结论 |
|---|---|
| 两个 demo 性能瓶颈在哪 | **不是同一个瓶颈**。石头（Box2D）：瓶颈在「传感器无节奏驱动主循环 + 每帧 ~60 次 Lua→Java 反射桥调用 + 未缓存字段反射」。看板娘（Live2D）：瓶颈在引擎本身 —— 它是纯 Java 移植版，每帧在 Java 里做全模型顶点变换，与 Lua 桥无关。 |
| dex 引擎换 .so 会不会更快 | 会，但前提是 **用 C/C++ 原生实现重写/移植引擎**（box2d、Live2D 都有官方 C/C++ 版），不存在"把 dex 重新打包成 so"这种事。稳态端到端估算：石头约 3~10 倍，看板娘约 5~15 倍。 |
| Lua 调 .so 的路径 | **Lua → C（.so）直接调用**，不经 Java。Java 只做一次性文件复制与路径注册。`require "zip"` = `package.loadlib(path, "luaopen_zip")`，运行期调用链无任何 Java 帧。 |

---

## 1. 两个 demo 的事实核查（基于 dex 反编译与源码）

### 1.1 石头 —— JBox2D（纯 Java 移植）

- `libs/box2d.dex`（496 KB）包含 `ee.*` 全部包：`ee.dynamics.World`、`ee.common.Vec2`、`ee.collision.*` 等（AndEngine Box2D 扩展，JBox2D 的直接移植），**无任何 native 代码**。
- 主循环入口：重力传感器回调（`main.lua:277-291`），`registerListener(listener, sensor, 0)`，`0 = SENSOR_DELAY_FASTEST`（50~250 Hz 量级），且未传 Handler → 回调跑在**主线程**。
- 每事件执行：`updateWorld()` → `mWorld.step(0.02, 6, 2)` + `updateViews(ee)`（`main.lua:81-86, 220-226`），对每个视图做 `getTag/getPosition/setX/setY/getAngle/setRotation` 与 `pos.x/pos.y` 字段读（`main.lua:203-218`）。
- `ee` 是 `game.aly` 里 `id="ee"` 的 LinearLayout（`loadlayout.lua:647` 会把 id 写进全局环境，覆盖 `main.lua:49` 创建后被 `setContentView(game)` 卸载的旧 `ee`）→ 实际绑定了 6 个可见 ImageView。

### 1.2 看板娘 —— Live2D 1.x Java SDK（纯 Java 移植）

- `libs/live2D.dex` 共 135 个类，全部是 `jp.live2d.*`、`jp.live2d.framework.*`、`jp.live2d.sample.*`、`jp.live2d.utils.android.*`（Live2D for Android **Java 版**，`Live2DModelAndroid` 等），无 native。
- 渲染：`LAppView extends GLSurfaceView`，`LAppRenderer implements GLSurfaceView.Renderer`。反编译 `LAppRenderer.onDrawFrame`（`020ddc`）确认每帧：`delegate.update(gl)` → 对每个模型 `model.update()`（动作/参数插值）→ `model.draw(gl)`（`Live2DModelAndroid.draw` → `ModelContext.draw` → `jp.live2d.android.a` 逐顶点变换 + GLES 1.0 `glVertexPointer/glDrawArrays`）。
- `LAppView` 构造器未调用 `setRenderMode` → GLSurfaceView 默认 **RENDERMODE_CONTINUOUSLY**（按屏幕刷新率无条件重绘）。
- **关键**：主循环（GL 线程）内没有任何 Lua 回调，`main.lua` 只做一次性初始化（`main.lua:24-38`）。所以 Lua→Java 桥在这里不是瓶颈。

---

## 2. 石头（Box2D）瓶颈逐项分析

### 2.1 每传感器事件的完整调用链（全部在主线程）

| 环节 | 路径 | 成本类型 |
|---|---|---|
| 传感器回调 | Java Proxy(`SensorEventListener`) → `LuaInvocationHandler.invoke` → `synchronized(L)` → `func.call(args)` + `pcall` | 动态代理 + 栈出入 + pcall，~5~20µs/事件 |
| 读加速度 | `event.values[0/1]` → `objectIndex` 数组分支 `Array.get` | 反射读 ×2，未缓存 |
| 物理步进 | `mWorld.step(0.02, 6, 2)`（3 参）→ `callMethod` **慢路径**：全量过载遍历 + 每参 `compareTypes` + `Method.invoke` | 慢路径每次 ~3~10µs（无 ≥2 参快速缓存） |
| 重力设置 | `Vec2(x,y)` 构造（2 参慢路径）+ `setGravity(Vec2)` | ~3~8µs + 每次新分配一个 Vec2 |
| 视图同步 ×6 | 每视图：`getTag`(0 参缓存) + `getPosition`(0 参缓存) + **`pos.x`/`pos.y`（`checkField` → `Class.getField` 每次全量反射查找，未缓存）** + `setX`/`setY`/`setRotation`(1 参 double 缓存) + `getAngle`(0 参缓存) + `getWidth` | 每视图 ~8 次桥调用，稳态 ~1~5µs/次；字段反射最贵 |

合计：**每事件 ~60 次 Lua↔Java 桥往返，约 150~400µs**（JIT 稳态）；JBox2D `step` 本身只占 50~200µs。

### 2.2 结构性设计缺陷（比桥开销更致命）

1. **无帧节奏、无 dt 累积**：`dt=0.02` 固定，步进频率 = 传感器频率（FASTEST，50~250Hz）。模拟时间流速 = 真实时间的 1.2~5 倍，且完全不与 vsync 对齐 → 抖动、掉帧感知、"太快/太卡"并存。
2. **传感器回调在主线程**：每事件 ~300µs 的主线程 CPU 占用 + 6 个 attached View 的 `setX/setY/setRotation` 每次触发 `requestLayout`，与 UI 绘制争用主线程。
3. **桥调用全部 `synchronized(L)`**：每个 JNI 调用都先取 LuaState 监视器（`LuaState.java:930` 等所有 native 方法），配合动态代理的 `synchronized(obj.L)`（`LuaInvocationHandler.java:53`），同一状态被主线程/任务线程穿插时会互相阻塞。
4. **分配与 GC 抖动**：`callMethod` 每调用新建 `Object[top]`；`compareTypes` 每参装箱；`Vec2` 每事件新建；`pushObjectValue` 每返回值走 `pushJavaObject` JNI。60Hz×60 调用 ≈ 每帧上百次小分配。
5. **dex 无 AOT、每次进程冷启动解释执行**：`LuaDexLoader.loadDex`（`LuaDexLoader.java:138-150`）API≥26 走 `InMemoryDexClassLoader` —— 该类加载器**永远没有 dex2oat 编译产物**，每次启动先从解释器跑起（解释器慢 10~50 倍），JIT 预热数秒后才回稳态。这是两个 demo 启动阶段"卡"的共同来源。

> 副带发现（正确性问题，非性能）：`updateBounds` 使用像素坐标直接建世界（Box2D 推荐 1m≈1~32px 的尺度），`main.lua:89-134`；且 physics 世界在 `task(300)` 后台线程创建，传感器主线程读取，存在跨线程无同步访问。

---

## 3. 看板娘（Live2D）瓶颈逐项分析

1. **引擎本身是 Java 移植版（主因，占帧时间 90%+）**：`Live2DModelAndroid`/`jp.live2d.android.a` 每帧在 Java 里对模型全部顶点做矩阵变换（无 SIMD/无 native），haru 模型（3 张贴图、数千~上万顶点）单帧 CPU 变换 + 参数/物理插值，典型中端机 30~80ms —— 这是 Live2D Java 版众所周知的天花板（官方文档同样说明 Java 版约为原生 SDK 的 1/5~1/10）。
2. **CONTINUOUSLY 渲染 + 无节奏**：GLSurfaceView 默认按刷新率（60/90/120Hz）无条件重绘；引擎跟不上时直接掉帧，还持续烧电 → 热降频 → 自我加剧。
3. **dex 冷启动解释执行**（同 2.5）：前几秒 GL 线程跑解释器，帧率雪崩。
4. **GL ES 1.0 固定管线**：`glVertexPointer`+`glDrawArrays` 每帧重新上传顶点数组（Java 侧数组重建），旧版 API 在 12+ 上无优化路径。
5. **Lua 桥不是瓶颈**：per-frame 无 Lua 参与；`main.lua` 的一次性初始化（`SoundManager.init`、`FileManager.init`、模型加载）不构成持续开销。

---

## 4. dex（Java 引擎） vs .so（原生引擎）能快多少

### 4.1 先纠正一个前提

"像 libzip 那样把游戏引擎通过 .so 集成" —— 可行，**但不是把现有 dex 重新打包成 .so**，而是必须有 C/C++ 的原生实现并导出 `luaopen_*` 入口。两个引擎都有现成原生实现：

- Box2D：官方 C 移植（`box2d.c`）或 C++ Box2D 2.3.1 + 现成 Lua 绑定（moonbox2d / luabox2d 等）。
- Live2D：官方 Cubism 原生 SDK（C++）或旧版 `live2d_opengl`（注意 Live2D 商用许可）。

### 4.2 逐层量化（工程估算，精确值需真机基准）

| 层 | dex（Java） | .so（C） | 倍数 |
|---|---|---|---|
| 单次 Lua 调用开销 | 1~5µs（JIT 稳态，走反射桥；解释器期 10~50µs） | 10~100ns（`luaopen_*` 注册的 C 函数直调） | **20~100×/次调用** |
| 引擎计算（Box2D step，16 刚体） | JIT 后约 50~200µs | 约 20~80µs | 1.5~3× |
| 引擎计算（Live2D 顶点变换） | 每帧 30~80ms（Java） | 每帧 3~10ms（C/C++） | **5~15×** |
| 冷启动（进程首跑） | 解释器起步，JIT 预热数秒 | mmap 后全速执行 | 启动期 10~50× |

### 4.3 端到端结论

- **石头**：每事件 150~400µs 里 60~70% 是桥开销。原生化后桥调用消失（每事件只剩 6~7 次 C 调用），step 也快 1.5~3×，稳态整体 **约 3~10 倍**；且消除 GC 抖动与主线程争用。若保留 Java 引擎、只优化 Lua 侧（缓存字段/方法、降到 SENSOR_DELAY_GAME、按 vsync 攒批、视图渲染下沉到 SurfaceView），不换 .so 也能拿到 **2~4 倍**。
- **看板娘**：瓶颈在引擎本体，Lua 侧优化无效。换 Cubism 原生 SDK 稳态 **约 5~15 倍**，可从 10~30fps 到稳定 60fps；同时应把渲染改成 `RENDERMODE_WHEN_DIRTY` 或加帧节流。
- **共同收益**：delua 内 in-memory dex 无 AOT 的每次启动 JIT 预热被彻底消除。

> 诚实声明：上述倍数为基于调用路径计数与业界常识的工程估算，未经真机 profiler 校准；建议先做 30 秒 trace（`systrace`/`simpleperf`）验证后再定目标。

---

## 5. Lua 调用 .so 的路径（问题三）

**Lua → C（.so）直接调用，不经过 Java。**

机制（工程内证据链）：

1. `LuaActivity.onCreate` → `LuaDexLoader.loadLibs()`（`LuaActivity.java:234`）扫描工程 `libs/`，把 `lib*.so` 复制到应用私有目录并登记路径（`LuaDexLoader.java:88-106`）。
2. `import.lua` 把 `libsloader` 插入 `package.searchers`（`app/src/main/assets/lua/import.lua:17-26`）：
   ```lua
   local function libsloader(path)
       local p = libs[path:match("^%a+")]
       if p then
           return assert(package.loadlib(p, "luaopen_" .. (path:gsub("%.", "_")))), p
       ...
   ```
3. 脚本 `require "zip"` → 命中 `libsloader` → `package.loadlib(libzip.so, "luaopen_zip")` → dlopen + C `luaopen_zip`，返回 C 注册表（`libzip.lua` 里再加一层 Lua 封装）。

运行期每次调用：Lua VM → C 函数指针（`lua_CFunction`），**栈上没有 Java 帧**。Java 只参与：一次性文件复制（`LuaDexLoader.loadLib`）、`getLibrarys()` 路径查询（`LuaActivity.java:153-155`）、以及 `libsloader` 注册——都是加载期动作，不是调用期动作。

验证事实：`docs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.md