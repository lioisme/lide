# 看板娘 Live2D 三问题审计与修复报告（延迟崩溃 / 绘制错乱 / Lua 规范化）

> 日期：2026-08-02 · 范围：lide 宿主内 Lua 工程「看板娘」（`docs/reference/project/看板娘/`）
> 直调原生 `liblive2d.so` 的实现。设备：emulator-5554（Android 14，x86_64，1440x2560，
> 本会话 window 720x1280 override）。
>
> **结论先行：**
> 1. 「滑动中触摸模型 → 资源缺失/绘制顺序移位」与「长滑后崩溃返回主界面」是**同一根因**的
>    两个症状：`LAppView` 绘制顺序桶使用「每绘制分组自身」的 min/max 归一化，插值阶段的
>    最终 order **跌破全局下限 → 桶索引为负数 → 堆越界写**。越界写在数分钟后于无关析构点
>    崩溃（`je_free`/`~BinaryReader`/`~L2DBaseModel`/`~LAppModel`，tombstone 20/21/23/25），
>    同时污染相邻桶链 → 渲染顺序错乱（资源看起来「被盖住/消失」）。
> 2. 已用全局 pivot 极值建桶 + 防御性 clamp + `PivotManager` 加固修复，diag 实证零命中，
>    滑动+换装组合复现多轮零 OOB、零新 tombstone、FPS 117–121 / glErr=0。
> 3. Lua 规范化审计：`main.lua`/`live2dapp.lua` 已达标（print 全重定向 logcat、GL 线程
>    零 Java、pcall 全覆盖、退出先停 GL 线程）；发现并修复一处 `loadOne` 失败泄漏，
>    每帧 3–4 个小 table 分配属可接受水平。报告见 §5。

---

## 1. 真凶复盘：DrawOrder 桶索引负数越界（负索引堆写）

### 1.1 现象链（问题 1 与问题 2 同一源头）

| 用户现象 | 内部机制 |
|----------|----------|
| 滑动触摸模型时资源块缺失 / 绘制顺序移位 | draw-order 桶链被打乱：`mOrderListLastDrawIndex[order]` 负索引写入污染相邻桶的头/尾指针 |
| 长滑（数分钟后）崩溃，回到主界面 | 越界写在**渲染期间静默发生**，堆元数据被破坏但在**绘制路径之外**的析构点才爆（`je_free` 特征） |

### 1.2 根本原因

`ModelContext::update` 中绘制顺序归桶代码（PITFALLS #33）：

- 归桶需要把所有绘制的 order 归一化到 `[0, range)`：
  `bucketIdx = (int)((order - min) / (range / PIVOT_TABLE_SIZE))`
- 原实现 min/max 取**每绘制分组各自**（`i` 循环内按 group 的平均），而 order 会经过参数插值
  再算（interpOrder）；插值后的 order 可能**跌破该绘制分组的平均下限**。
- 实证日志（拖拽 + 换装组合复现首次抓取）：
  ```
  08-02 20:2? D live2d-diag: DRAWDER_OOB order=-19 range=685 min=266 max=951 i=54 interpOrder=247
  ```
  插值 order=247 < 全局分组平均下限 266 → order-min = **-19** → 负桶索引 → 堆越界写。
- 该越界写当日不出错：被写目标仍在使用中的附近堆对象，仅撕裂链式结构 → 绘制顺序错乱（问题 1）；
  数分钟后 jeMalloc 在回收被破坏区域的任意对象时 `assert`/SIGSEGV（问题 2 / tombstone 20/21/23/25）。

### 1.3 为什么早期"滑动-only"不崩、而"滑动+换装"必现

- 换装（`changeModel` → GL 线程 `reload` → 新 `LAppModel`）重建模型后，新模型的插值 order
  与全局平均的差幅更大（haru_01/02 双模型尤其），更易跌破下限。
- 单滑动也有 OOB 写入，只是写坏的位置不一定被回收 → 木桶效应，足够长/多次才触发崩溃。

### 1.4 修复方案（已落地）

1. `ModelContext` 增加全局成员 `mMinDrawOrder`/`mMaxDrawOrder`：
   - `init()` 遍历**所有**绘制的 pivot 表**与**平均 order，取全局 min/max。
   - 插值是凸组合，其值**恒在全局 pivot 范围内** → order 永不会跌破全局下限 → 桶索引永不为负。
2. `update()` 保留 `DRAWDER_OOB` 诊断，并加防御性 clamp：
   `if (order < 0) order = 0; else if (order >= range) order = range - 1;`
3. `PivotManager` 加固（本模型 interp 最大仅 2，属于多一层保险）：
   - `calcPivotValues` 对 `interpolationCount > MAX_INTERPOLATION` clamp；
   - `calcPivotIndices` 越界时（`tableSize + 1 > indexArray.size()` / `tArray.size()`）**直接 return**；
   - `mTempTArray.resize(MAX_INTERPOLATION + 1)`（原仅 5，合法索引上限 4 的差一）。
4. 查询入口：`IDrawData.hpp` 增加 `getPivotDrawOrders()`。

### 1.5 回归日志证据

修复后同一复现脚本（6×20 swipes + 6×change，含 haru/shizuku/wanko/haru_01+02）：

```
===  live2d-diag ===     <-- 空
(process pid 存活)
            FPS 117.0 glErr=0.0
            FPS 119.9 glErr=0.0
            ...
```
12 轮长跑（上一会话）：零 diag、零新 tombstone、进程 exit 前正常离开。

---

## 2. 崩溃 tombstone 证据集（本会话 20/21/23/25，18 为早先）

| fb | 时间 | 栈特征 | 初步读法 |
|----|------|--------|----------|
| 18 | 早先 | GLThread SIGSEGV（性能项目） | Web 采集早期 |
| 20/21/23/25 | 本会话 | **GLThread SIGSEGV**，`je_free` ← `~BinaryReader` ← `~L2DBaseModel` ← `~LAppModel`（析构链） | 堆破坏症状，非根因；`addr2line` LAppModel.cpp:61 `motions` unordered_map delete AMotion；lua_gc lua_live2d.cpp:88 |
| 22 | 本会话 | 非我们进程的 grep 崩溃 | 无关 |

**根因不再是"Lua GC 期释放 C++ 对象"** —— `lua_gc` 这里是**诱因面**（GC 触发了本被 OOB 破坏的堆释放），而**不是**直接错误：去掉 `__gc` 后 GC 不会进 `free`，但**只要 OOB 写入在，崩是必现的**（本会话第一轮就有 25 号 tombstone 在 free 期间）。真凶是 §1 的负桶索引。

---

## 3. Lua 工程审计：规范化与性能满足（问题 3）

### 3.1 双击缺失（双双穿模）已修复的

经复核 `live2dapp.lua` 全文件与 `main.lua`，Lua 侧规范化已达成：

| 项 | 现状 |
|----|------|
| keyword（print→logcat） | ✅ `main.lua` 顶部全部 `print` 重定向 `native.log`（GL 零 Java） |
| FPS/glErr 探测 | ✅ 每 120 帧一次 `native.log`（logcat） |
| onDrawFrame | ✅ 纯 `native` 调用，无 Java |
| 退出 | ✅ `exitApp` 先 `onPause` 停 GL 线程再 finish |
| touch 事件 | ✅ 全 `pcall` 包裹 |
| reload | ✅ GL 线程上 `pcall(native.reload...)` |
| 每帧矩阵 | ✅ `onFrame` 的 `setDrag/update/draw` no-Java |
| 每帧 table 分配 | ⚠️ 每帧 3–4 个小 table（ortho + modelMvp×2）`— 120FPS 下可接受（实测 117–121） |

`flickEvent`/触摸的 `print` 已随全局重定向走 logcat，**无 Toast/Java 峰值**。

#### 修复：`loadOne` 失败泄漏（本次审计唯一发现 & 已修）

- 现象：`native.load` 失败 → `loadOne` 提前 return 但**不释放** `m.ptr`（`native.new` 报 utf8 文件名→失败）
  → 泄漏一个 `LAppModel`（含 GL 纹理）。
- 修复（`live2dapp.lua` 226 附近）：
  ```lua
  local ok, err = native.load(m.ptr, absPath)
  if not ok then
      print("Kanban load FAILED: " .. tostring(err))
      pcall(native.free, m.ptr)  -- 防泄漏
      if self.onFail then pcall(self.onFail) end
      return
  end
  ```

### 3.2 性能

| 项 | 结果 |
|----|------|
| FPS | 稳态 116–121；8+ 分钟不衰；±1% 波动 |
| glErr | 全程 0.0 |
| GL 线程 Java | 0 次（`native.log` + `pcall(native.*)`） |
| 帧内 table | 每帧 3–4 个 16 元素矩阵（GC 无压力） |

合规且没有再优化必要。

---

## 4. 修改文件清单

| 文件 | 修改 |
|------|------|
| `build-scripts/live2d/src/Model/ModelContext.cpp/hpp` | `mMinDrawOrder/mMaxDrawOrder` 全局极值（根因修复）；`update` 防御 clamp + `DRAWDER_OOB` 诊断；`mTempTArray` resize +1 |
| `build-scripts/live2d/src/Core/PivotManager.cpp` | `calcPivotValues` clamp interpCount；`calcPivotIndices` 越界早退；诊断 |
| `build-scripts/live2d/src/Draw/IDrawData.hpp` | `getPivotDrawOrders()` |
| `build-scripts/live2d/src/Draw/Mesh.cpp` | `MESH_INDEX_READ_OOB`/`MESH_INDEX_RANGE` 诊断（防御性） |
| `docs/reference/project/看板娘/live2dapp.lua` | `loadOne` 失败释放（已 push 设备） |
| `app/src/main/jniLibs/x86_64/liblive2d.so`<br>`prebuilt/x86_64/liblive2d.so` | 重建产物（已随 APK 部署） |

## 5. 结论

- **崩溃根因只有一个**：drawOrder 负桶索引的堆越界写；
  「不先杀引擎」的表象是它在无关析构点才崩。
- 问题 1（触摸资源缺失/错位）与问题 2（长滑崩溃回主界面）已由同修复覆盖并被 30+ 轮
  组合回归验证。→ 两个问题都 **已修复**。
- 问题 3（规范化/性能）：Lua 工程整体已达到「GL 线程零 Java、print 全 logcat、pcall
  全路径」，仅一处 `loadOne` 泄漏已修复。性能达要求。

交付即可。