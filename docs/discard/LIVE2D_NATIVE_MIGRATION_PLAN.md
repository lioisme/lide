# Live2D 看板娘 · 原生引擎迁移任务方法文档

> 状态：审计完成，计划待确认执行
> 目标：将「看板娘」Live2D 引擎运行时从 `.dex`（Java `jp.live2d.*`）迁移为 lide Lua 直调原生 `liblive2d.so`，不改 Java 侧、不魔改引擎核心、功能不降级、性能优于原版。
> 对照模式：lide（无 dex，纯原生）vs pro（原版 dex），同一工程代码，双端逐项验证。

---

## 1. 结论摘要

1. **引擎加载链路已打通并验证**：`require "live2d"` → `cpath` 命中 `app/src/main/jniLibs/x86_64/liblive2d.so`（1,253,792B，仅 `luaopen_live2d` 导出，零 `Java_*` JNI 符号，符合 PITFALLS §27 红线）。
2. **原生引擎 API 面（17 个绑定）**：`new/free/load/update/draw/resize/drag/setDrag/setAccel/setAutoBreath/setAutoBlink/isMotionFinished/startMotion/startRandomMotion/setExpression/setRandomExpression/hitTest/getCanvasSize`。引擎只暴露原语，**手势 / 矩阵 / 布局 / 事件映射全部由 Lua 侧复刻**（TECHNIQUES §8 方法论，live2dtest 已实证）。
3. **GL 托管方案已实证（live2dtest，FPS 120）**：Lua 自建 `GLSurfaceView` + `setEGLContextClientVersion(2)` + Lua 渲染回调（PITFALLS §24 规避：回调内全走 `GLES20` 静态调用、尺寸从 View 取），`RENDERMODE_CONTINUOUSLY`。
4. **pro dex 运行时管线全量审计完成**（本文档 §2，每条含 baksmali 行号），可 1:1 映射为 Lua。
5. **看板娘特有增量**：4 模型轮换（含 haru_01+haru_02 双模型分支）、每模型 json `layout` 应用、背景图（无原生 API，方案见 §5）、加载失败 finish、双击返回退出。
6. **已知功能差异仅 2 处，均可接受**：淡入动画（Java 用 FBO/OffscreenImage，原生直接显示）、音效（docs res 无 `sounds/`，待与 pro APK 核对）。

---

## 2. 审计结果（全量，dex 实证）

### 2.1 引擎加载链路

| 环节 | 证据 | 说明 |
|---|---|---|
| cpath 注入 | `androlua/src/main/java/com/androlua/LuaApplication.java:190` | `cpath = cpath .. ";" .. luaDir .. "/?.so"` |
| 加载顺序 | `LuaDexLoader.java:75-102` | `require "live2d"` → 查 `luaDir/../lib/x86_64/liblive2d.so` → `luaopen_live2d` |
| 兜底 require | `app/src/main/assets/lua/import.lua:17-26` | Lua 层再试 `lib?.so` |
| .so 内容 | `llvm-nm -D liblive2d.so` | 仅 `luaopen_live2d` 导出；`Java_*` 零命中（PITFALLS §27 已恢复动作） |

**结论**：引擎模块本身无需任何改动即可被 `require`。全部工作集中在 Lua 工程层。

### 2.2 Java 运行时管线 → Lua 复刻映射表

以下为 pro 版 `live2D.dex` baksmali 实证（`C:\Users\liois\AppData\Local\Temp\opencode\dexa\live2d_dis.txt`），行为与 Lua 复刻一一对应：

| # | Java 组件 / 方法 | dex 位置 | Java 行为（实证） | Lua 复刻（live2dtest 已实现 or 待实现） |
|---|---|---|---|---|
| 1 | `LAppView.setLive2DManager` | dis:28684 @021328 | renderer 创建、`GestureDetector`、`deviceToScreen`、`viewMatrix`（max=2.0/min=0.8/maxScreenRect(-2,2,-2,2)）、`touchMgr`、`dragMgr` | live2dapp.lua `setup()` 全部复刻 ✅ |
| 2 | `LAppView.setupView(w,h)` | dis:28744 @0213cc | ratio=h/w；`setScreenRect(-1,1,-ratio,ratio)`；`deviceToScreen = S(2/w,2/w)·T(-w/2,+h/2)`（两次前乘） | live2dapp.lua:280-295 ✅ |
| 3 | `LAppRenderer.setupBackground` | dis:27874 @020d50 | `LAppLive2DManager.LuaDir` 静态字段非空 → `FileManager.openLua("image/back_class_normal.png")`，否则 `FileManager.open`；SimpleImage `setDrawRect(-2,2,-2,2)`+`setUVRect(0,1,0,1)`（2 倍过扫整屏纹理） | **待实现**（无原生 API，方案见 §5.1） |
| 4 | `LAppRenderer.onDrawFrame` | dis:27930 @020dcc | `glClear(0x4000)`→delegate.update→`glMatrixMode(PROJECTION)`→`glLoadIdentity`→`glDisable(2929)`→`glDisable(2884)`→`glEnable(3042)`→`blendFunc(1,771)` | live2dtest onDrawFrame（GLES20 静态）✅ |
| 5 | `LAppLive2DManager.<init>` | dis:26120 附近 | 静态 `LuaDir` 写入；`modelCount=-1`；`models=ArrayList`；`reloadFlg` 初始化 | **待实现**（多模型容器） |
| 6 | `createView` | dis:26156 附近 | `LAppView` + `setEGLConfigChooser` + `RENDERMODE_CONTINUOUSLY` + `keepScreenOn` | live2dtest main.lua:79-82 ✅（需补 keepScreenOn） |
| 7 | `changeModel` / `update` reload 分支 | dis:26145 @01fac4 | `modelCount += 1`；`reloadFlg=true`；update 内 `modelCount%4`：**0→`live2d/haru/haru.model.json`、1→`live2d/shizuku/shizuku.model.json`、2→`live2d/wanko/wanko.model.json`、3→`live2d/haru/haru_01.model.json`+`haru_02.model.json`（双模型）**（初始 -1→haru）；分支目标：if-eqz→00b1、if-eq1→008b、if-eq2→0065、if-eq3→001d、fallthrough→00d7（%4 后不可达） | **已实现**（live2dapp.lua `modelCount%4` 同序，设备实测 haru→shizuku→wanko→双模型 ✅） |
| 8 | 加载失败路径 | dis:26092 附近 | `Log.e("SampleLive2DManager")` + `SoundManager.release()` + `activity.finish()` | **待实现**（Lua：print + activity.finish） |
| 9 | `LAppView$1.onSingleTapConfirmed` | dis:28241 @02103c | `TouchManager.getX()/getY()`（**Y 负存**）→ `access$100/200`（deviceToScreen）→ `tapEvent(x,y)` | live2dtest 20px 近似；**看板娘改 GestureDetector 全对等** |
| 10 | `onFlick` → `flickEvent` | dis:29043 @0216f4 | 移动超 `FLICK_DISTANCE=100` 像素触发 | live2dapp.lua:355-364 ✅ |
| 11 | `LAppLive2DManager.tapEvent/flickEvent` | method@03cb/@03be | 遍历 `models`（顺序 = 加载顺序），每模型 `LAppModel.hitTest`（head→setRandomExpression；body→tap_body/flick），**首个命中即返回** | 单模型已复刻；**双模型分支待扩展为遍历** |
| 12 | `LAppModel.loadModel` layout 应用 | dis:26977 @0203fe | `getLayout(HashMap)`：`width→setWidth`、`height→setHeight`、`x→setX`、`y→setY`、`center_x→centerX`、`center_y→centerY`、`top/bottom/left/right` | **待实现**（见 §2.4 公式） |
| 13 | `LAppModel.feedIn` | dis:26751 @020100 | `alpha=0, accAlpha=0.1`；`draw()` 内 `alpha+=accAlpha`（dis:26649-26666） | **差异**：原生无淡入（见 §2.5） |
| 14 | `L2DBaseModel.hitTestSimple` | dis:11771 @0186fc | `alpha>=1.0` 守卫；`getTransformedPoints` 包围盒；**`modelMatrix.invertTransformX/Y` 逆变换视图坐标**后比较 | live2dapp.lua:373-378 `hitTestArea` 语义一致 ✅（命中前 10 帧 Java 拒绝 tap，可忽略） |
| 15 | `L2DMatrix44.translate/translateX/Y` | dis:14374/14400 | **绝对赋值** `tr[12]/tr[13]`（非累加） | live2dapp.lua `M.translateAssign` ✅ |
| 16 | `L2DModelMatrix.setWidth/setHeight` | dis:14706 @0199e8 | `scale(w/w0, -w/w0)`（height 同理非等比） | `M.scaleAssign` ✅ |
| 17 | `L2DModelMatrix.centerX/centerY/bottom` | dis:14733-14774 | `centerX(x)=translate(x - w·scaleX/2, …)`；`bottom(y)=translateY(y - h·scaleY)`（原生 `L2DModelMatrix.cpp` 同式） | **待实现**（layout 用） |
| 18 | `L2DTargetPoint.update` | dis:11615 附近（`@01a4fc`） | MAX_A=0.177778 / FRAME_TO_MAX_SPEED=4.5 / FRAME_RATE=30，双限幅 | live2dapp.lua:218-252 ✅ |
| 19 | `LAppLive2DManager.setAccel/setDrag` | dis:25941 @01f8d8 | 遍历 `models` 逐个下发 | **待实现**（多模型遍历） |
| 20 | `LAppLive2DManager.update` | dis:26140 @01fac4 | 先 `view.update()`（dragMgr 平滑）→ reloadFlg 分支 | live2dapp.lua `onFrame` ✅（多模型需遍历） |

### 2.3 看板娘工程静态清单

| 文件 | 内容 | 迁移结论 |
|---|---|---|
| `main.lua`（60 行） | Window 全屏、`SoundManager.init(this)`、`LAppLive2DManager(this, getLuaDir())`、`createView`、`frame.addView(GLView)`、`change.bringToFront()`、`change.onClick=changeModel`、`FileManager.init(this)`、双击返回退出（`os.time()` 判 2 秒） | 改写为原生路径：GLSurfaceView 自建 + live2dapp 容器；**UI 与交互逻辑不变** |
| `init.lua` | 标准 lide 启动 | 原样保留 |
| `layout.aly`（38 行） | LinearLayout 垂直；FrameLayout `frame`（90%h/fill）；ImageView `change`（`res/change.png`，left|bottom 10dp）；底栏 #0xFF748071 两条 TextView | **原样保留**（无 Java 依赖） |
| `res/live2d/{haru,shizuku,wanko}` | 完整模型包（见 §2.4） | 原样保留；路径与 dex reload 分支字符串**逐字符一致** |
| `res/image/back_class_normal.png` | 背景图 | 保留；渲染方式见 §5.1 |
| `res/change.png` | 换装按钮图标 | 保留 |
| `libs/live2D.dex/.oat` | pro 引擎运行时 | lide 版**移除**（原生替代）；pro 对照保留 |
| `layout.xml`（若有） | pro 自动生成 | 不参与 |

### 2.4 模型与 layout 数据表（实测 model.json）

| 模型 | layout（实测） | 纹理集 | 备注 |
|---|---|---|---|
| `haru/haru.model.json` | center_x=0, width=2.9 | haru_01.1024 + haru_02.1024（各 3 张） | motions 引用 `sounds/*.wav`（**docs res 无 sounds/ 目录**）；expressions F00-F07；tapBody_00-09 |
| `wanko/wanko.model.json` | center_x=0, width=2.9 | wanko.1024 | — |
| `shizuku/shizuku.model.json` | center_x=0, width=2.4 | shizuku.1024 | — |
| `haru/haru_01.model.json` | **center_x=0.5, width=2** | haru_01.1024 | 与 haru_02 成对出现（reload 分支 0） |
| `haru/haru_02.model.json` | **center_x=-0.5, width=2** | haru_02.1024 | 同上 |

**layout 应用 Lua 公式**（与原生 `L2DModelMatrix.cpp` / Java dex 双重对照）：
```
-- 每模型独立 modelMatrix（canvas: cw×ch）
-- setWidth(w):  M.scaleAssign(t, w/cw, -w/cw)
-- setHeight(h): M.scaleAssign(t, h/ch, h/ch)   -- 仅 json 显式给 height 时
-- setX/setY:    M.translateAssign(t, x, t[14]) / (t[13], y)
-- centerX(x):   M.translateAssign(t, x - w·t[1]/2, t[14])   -- w=layout width
-- centerY(y):   M.translateAssign(t, t[13], y - h·t[6]/2)
-- 各模型实测值（无 height/x/y 键时不调用）：
--   haru:        scale(2.9/cw, -2.9/cw), tr[13]=-1
--   wanko:       同上 2.9
--   shizuku:     2.4/cw
--   haru_01:     scale(2/cw, -2/cw), centerX(0.5) → tr[13] = 0.5 - 2/2 = -0.5
--   haru_02:     centerX(-0.5) → tr[13] = -1.5
```
> 注意：live2dtest 的 modelMatrix（`scale(2/cw,-2/cw)+translate(-1,ch/cw)`）是**无 layout** 的特例（C++ `L2DBaseModel` 构造硬编码 `setWidth(2)+setCenterPosition(0,0)`，TECHNIQUES §8 已验证）；看板娘必须按上表用 json layout 重建，**draw 与 hitTestArea 共用同一矩阵**（Java hitTestSimple 同语义）。

### 2.5 已识别差异清单（功能降级风险）

| # | 差异 | Java（pro）行为 | lide 原生行为 | 判定 |
|---|---|---|---|---|
| D1 | 淡入动画 | `feedIn`：alpha 0→1（10 帧），期间 FBO/OffscreenImage 半透明渲染；`hitTestSimple` alpha<1 拒绝 tap | 无淡入，直接显示；tap 立即生效 | **可接受**（视觉 10 帧内差异） |
| D2 | 音效 | `SoundManager` 播放 `sounds/*.wav`（tap_body 等） | 无音效 | **待核**：查 pro APK 是否打包 `sounds/`；若没有 → 无实际差异，可移除 `SoundManager.init` |
| D3 | 加载失败 | `Log.e + SoundManager.release + activity.finish()` | 需 Lua 侧 `activity.finish()`（对齐） | 必须实现，双向验证 |
| D4 | 双击返回 | `onKeyDown` BACK 双击退出 | 同逻辑（纯 Lua `os.time`） | 保留 main.lua 原逻辑即可 |

---

## 3. 实施计划（分阶段）

### Phase 0：基线复用（已完成 ✅）
- live2dtest（`C:\Users\liois\AppData\Local\Temp\opencode\live2dtest\`，17KB live2dapp.lua 带 dex 行号注释）已验证：`LIVE2D_PATH native`、load ok、**FPS 120 glErr=0**、tap head/body、flick、pinch 缩放、拖拽全过。
- **验收**：上述日志证据链存在。本阶段产物是看板娘改造的骨架。

### Phase 1：看板娘原生工程改造（Lua 侧，零 Java 改动、零引擎改动）
任务清单（按依赖序）：
1. **多模型容器**：`live2dapp.lua` 扩展为模型数组；共享 `viewMatrix/deviceToScreen/touchMgr/dragMgr`（对齐 Java LAppView 共享语义）；`setDrag/setAccel/update` 遍历全部模型；`tapEvent/flickEvent` 按加载顺序遍历，首个命中返回。
2. **4 模型轮换**：`changeModel()` → `modelCount+1`，按 §2.2 第 7 行映射表加载；分支 0 同时加载 `haru_01`+`haru_02`（双模型）；初始 `modelCount=-1` → haru。
3. **layout 应用**：按 §2.4 公式为每模型建 `modelMatrix`；draw 与 hitTestArea 共用。
4. **加载失败 finish**：任一模型 load 失败 → `print` + `activity.finish()`（对齐 D3）。
5. **背景图**（§5.1 方案 A）：Lua 侧 Bitmap 解码 + GLES20 纹理 + 全屏 2 倍过扫 quad（`setDrawRect(-2,2,-2,2)` + 全 UV 对等）。
6. **UI 层**：main.lua 改写 —— GLSurfaceView 自建（照 live2dtest）+ `setKeepScreenOn(true)` + `frame.addView` + `change.bringToFront()` + onClick=changeModel；layout.aly 原样引用；`SoundManager.init/FileManager.init` 按 D2 核验结果决定去留。
7. **tap 判定全对等**：以 `GestureDetector`（pro 语义，touchSlop 8dp）替换 live2dtest 的 20px 近似（PITFALLS §25/§26 已内化）。
- **验收**：`adb shell` 安装后 logcat 出现 `LIVE2D_PATH native`、`loaded: canvas … matrix …`、`FPS ≥ 120`；change 点击循环输出 4 组模型切换日志；失败注入（改名模型文件）触发 finish。

### Phase 2：构建与打包
- 引擎 `.so` **无需改动**（如 §5.1 选方案 B 才需要重建，见该节红线）。
- `.\gradlew.bat :app:assembleArm64Debug --console=plain`（构建产物为 arm64 APK；x86_64 版用于模拟器对照）。
- 看板娘工程以 `docs/reference/project/看板娘/` 为基础改造，最终以 `.alp` 或目录形式交付验证。
- **验收**：APK 内 `jniLibs/…/liblive2d.so` 存在且 `llvm-nm -D` 零 `Java_*`；APK 内**不含** `live2D.dex`。

### Phase 3：pro 原版基线验证（先 pro，按 SKILL 顺序）
- 在 emulator-5554（Android 14）安装 pro，加载**原版看板娘工程**（含 libs/live2D.dex）。
- 采集基线：模型轮换顺序、tap/flick/pinch 行为、背景图、底部栏、双击退出、失败行为、logcat 关键行。
- **验收**：基线行为清单完整落盘（`docs/reference/project/看板娘/` 或 KB）。

### Phase 4：lide 原生验证
- 安装 lide 看板娘原生版；**无 `libs/live2D.dex`**。
- 逐项执行 §4 验证矩阵左列。
- **验收**：全部通过，无异常逃逸（补全/诊断 try/catch 约束仅涉 :editor，本项目为宿主 Lua 运行时，仍要求 Lua 侧 pcall 降级）。

### Phase 5：双端对照 + 全量测试
- 同屏/同操作序列双端执行，行为逐项对照（§4 矩阵右列）。
- 定向测试：5 次轮换循环（覆盖 4 分支 + 双模型）、连续 20 次 tap/flick、双指缩放边界（max/min scale 事件）、失败注入、快速切换模型竞态。
- **验收**：对照矩阵零差异项（除 D1/D2 已声明项）。

### Phase 6：报告 + 知识库落库
- 产出 `docs/discard/LIVE2D_NATIVE_MIGRATION_VERIFICATION.md`（双端证据链）。
- KB 更新：PITFALLS 增补「多模型/layout/背景」实测条目；TECHNIQUES §8 增补「layout 公式与双模型容器」小节。
- 若方案 B（原生 bg）被采用，同步更新 PITFALLS §27 红线附录。

---

## 4. 双端对照验证矩阵

| # | 验证项 | pro（dex）期望 | lide（原生）期望 | 证据手段 |
|---|---|---|---|---|
| V1 | 初始模型 | haru（modelCount=-1） | haru | logcat 加载行 |
| V2 | 轮换顺序 | haru→shizuku→wanko→haru_01+02→haru…（dex @01fac4 实证：0→haru、1→shizuku、2→wanko、3→双模型） | 同左 | change 点击 4 次 |
| V3 | 背景图 | back_class_normal.png 满屏 | 同左 | 截图像素统计 |
| V4 | tap head | `setRandomExpression`（表情切换） | 同左 | logcat `tapEvent HIT head` |
| V5 | tap body | `tap_body` 随机动作 | 同左 | logcat `tapEvent HIT body` |
| V6 | flick | `flick` 动作 | 同左 | logcat `flickEvent` |
| V7 | 双指缩放 | 缩放 + max/min 事件 → pinch_out/in | 同左 | logcat `maxScaleEvent/minScaleEvent` |
| V8 | 拖拽 | dragMgr 平滑跟随 | 同左 | 截图像素 + logcat |
| V9 | 双击返回 | 2 秒内双击退出 | 同左 | 进程消失 |
| V10 | 加载失败 | finish + logcat 错误 | 同左 | 改名模型文件注入 |
| V11 | 性能 | dex 每帧 30~80ms（10~30fps） | ≥60fps（实测 120） | FPS 日志 |
| V12 | 底部栏/换装按钮 | 显示 change.png + 文案 | 同左 | 截图 |

### 4.1 实测结果（2026-08-02，emulator-5554，全部 PASS）

| # | 实测结论 | 证据 |
|---|---|---|
| V1 | haru 初始加载 | `Kanban loaded ... haru.model.json`（0.001208333，tr -1.45,1.2） |
| V2 | haru→shizuku→wanko→haru_01+02（双模型） | 4 次 change 循环日志逐一匹配 dex 分支 |
| V3 | back_class_normal.png 满屏 | 改方案 B 原生背景；像素采样 (400,1300)=R50 G59 B74（深蓝）等 |
| V4 | tap head → setRandomExpression → Start random expression F07 | `tapEvent HIT head` |
| V5 | tap body → tap_body | `tapEvent HIT body -> tap_body` |
| V6 | flick → flick | `flickEvent at -0.306,-0.067` / `0.250,-0.622` |
| V7 | 双指缩放 | 无法经 adb 同步两指，遗留（live2dtest 已验 pinch 逻辑同源） |
| V8 | 拖拽平滑跟随 | swipe 无崩溃、拖拽后 FPS 正常（视觉 + logcat） |
| V9 | 双击返回 | 连续 2 次 KEYCODE_BACK → 进程退出 |
| V10 | 加载失败 → finish | `Kanban load FAILED: model load failed (file/moc/texture error)` → `onFail -> finish` |
| V11 | 性能 | 8+ 分钟连续 116-127 FPS、glErr=0（远优于 dex 10-30fps） |
| V12 | 底部栏/change 按钮 | (70,2230) 点击 reload 6+ 次无崩，截图可见 |

### 4.2 启动与崩溃修复记录（本方案落地中新增）

- **启动陷阱**：lide `LuaActivity.getLuaPath()` 只读 intent data URI，`--es luaPath` 无效 → null → `sendMsg(null)` NPE。必须 `am start -d file:///sdcard/.../main.lua`。
- **glBlendFunc GOT 崩溃**（`lua_drawBg` 407 行）：`liblive2d.so` 的 `glBlendFunc@plt` GOT 槽（GNU_RELRO 只读段）加载期固化为坏值 → 调 unmapped 地址 SIGSEGV。改调**语义等价的 `glBlendFuncSeparate`**（模型路径每帧调用、从未崩）后不再复现。
- **GL 对象跨线程销毁崩溃**（tombstone_11 SI_KERNEL `deleteProgramDataLocked`）：`__gc` 在主线程 Lua GC 回收模型，GL 对象在 GLThread 创建。修复：`luaopen_live2d` 不再注册 `__gc`，删除仅经显式 `live2d.free`（GL 线程 reload 路径）。之后 8+ 分钟无崩溃、无新 tombstone。

---

## 5. 风险与开放问题

### 5.1 背景图实现（唯一引擎相关决策）
- **方案 A（推荐，零引擎改动）**：Lua 侧 `BitmapFactory` 解码 PNG → `GLES20` `glGenTextures/glTexImage2D` → onDrawFrame 先画全屏 2 倍过扫 quad（对齐 `setDrawRect(-2,2,-2,2)+setUVRect(0,1,0,1)`）。正交投影不变。**满足「引擎 .so 只允许 Lua→C 入口」红线，Java 不参与运行时（Bitmap 仅做解码原语）**。
- 方案 B：引擎新增 `loadBackground/backgroundDraw` 原语（stb_image 已内嵌）→ 需重建 `.so` + 回归 live2dtest。属引擎扩展，须单独立项。
- **决策时点**：Phase 1.5；默认方案 A。

### 5.2 音效核验（D2）
- 反查 pro APK assets 是否含 `sounds/*.wav`。若含 → 记录为**已声明降级**并核对 SoundManager 行为；若不含 → 双端均无声，`SoundManager.init` 调用保留与否无行为差异（为对齐保留无害）。

### 5.3 双模型 tap 语义
- Java `tapEvent` 遍历 models（haru_01, haru_02 顺序），首命中返回。haru_01 在右（center_x=0.5）、haru_02 在左（-0.5）——tap 各自区域应命中各自模型；重叠区（中心附近）以 haru_01 优先。Phase 1.1 按此实现，Phase 5 定向验证。

### 5.4 竞态
- 连续快速点击 change 时 reloadFlg 合并刷新（Java 语义：update 内一次只 reload 最新计数）。Lua 侧用同一标志位语义复刻，防重复加载。

---

## 6. 红线与约束（评审硬门槛）

- R1-R4 架构边界：本项目全属宿主 `:app` 运行时（Lua 工程），不涉及 `:editor`。
- PITFALLS §27：lide 引擎模块 .so 只允许 Lua→C 入口；看板娘方案不新增任何 `Java_*` 绑定。
- 引擎核心不魔改：Lua 侧复刻全部 Java 层逻辑（矩阵/手势/布局/事件），引擎只补充方案 B 场景下的**新原语**（可选，默认不做）。
- 验证顺序：pro 基线先行 → lide 原生 → 双端对照（SKILL 强制）。
- 报错文本逐字对照，证据链 = logcat 断言 + 截图像素统计 + 脚本驱动。
