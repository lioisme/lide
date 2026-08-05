# 已知问题与规避（Known Issues & Mitigations）

> 每条格式：现象 → 根因 → 规避/判定 → 验证命令。已双端实证的标 `✅双端一致`。

## 1. demo 工程自身 md5 越界（工程自身 bug，双端必须同样报错）✅双端一致

- 现象：`I lua: Runtime error: length=16; index=16`，`main.lua:83 in function 'md5'` 与 `main.lua:103 in main chunk`。
- 根因：工程自身 bug（3 位毫秒时间戳配合 `md5` 实现越界），非运行时差异。
- 判定：pro 与 lide 报错文本、`stack traceback` 逐字一致即为正常；**不要**因报错"相同"去修 lide。
- 验证：双端启动 demo（命令见 ENVIRONMENT.md §4），`adb logcat -d -s lua:*` 对比。

## 2. libs/ 内 dex 包名解析失败 → NameNotFoundException 降级 ✅双端一致

- 现象：启动打印 `android.content.pm.PackageManager$NameNotFoundException: .../<name>/libs/okhtttp3.dex` 栈（含 activity + newActivity 各一次），随后仍以字节加载继续运行。
- 根因：`LuaDexLoader.loadApp/loadDex/loadLibs` 的包名解析失败→降级路径，pro 原版本就有该打印。
- 注意：lide 版该方法比 pro 版 **+4 行**（注释差），比对栈时行号有固定偏移属正常（SKILL 白名单 §6.2）。
- 判定：栈的类名+消息逐字一致（仅方法行号允许 +4 偏移）即一致。

## 3. 裸 file:// VIEW intent 会进 ResolverActivity

- 现象：不带 `-n` 时 `am start -a VIEW -d file://...` 弹出应用选择器（多个 app 都能处理 .lua）。
- 规避：双端启动一律带 `-n com.androlua/com.androlua.LuaActivity` 或 `-n com.lioisme.lide/com.androlua.LuaActivity` 指定组件。
- 验证：`adb shell "dumpsys activity activities" | Select-String topResumedActivity` 应直接是目标 Activity。

## 4. `androlua://` scheme 只进主程序 UI

- 现象：`am start -a VIEW -d androlua://...` 进 MainActivity 主页，不自动运行引擎。
- 规避：自动化验证用 file:// + `-n`；UI 路径（点工程 → 点「运行」）仅作抽查。

## 5. 高 targetSdk 默认禁明文 HTTP（lide 历史问题，已修复）

- 现象（修复前）：Lua 中 `Http` 请求 http:// 明文地址失败。
- 根因：lide targetSdk=36 默认禁止 cleartext，pro targetSdk=23 默认放行。
- 修复：`<application android:usesCleartextTraffic="true">`（已落地）。回归时若再遇 HTTP 失败，先查此条。

## 6. `com.androlua.LuaActivity` 未注册 → ActivityNotFoundException（lide 历史问题，已修复）

- 现象（修复前）：运行任意工程崩溃 `ActivityNotFoundException: com.androlua.LuaActivity`。
- 根因：`LuaActivity.newActivity()` 显式 `setClassName(..., LuaActivity.class.getName())`，但 lide manifest 只注册了空子类。
- 修复：manifest 注册 LuaActivity + 5 个 .lua intent-filter（file/content）。**不要让 Lua 工程用 `import "com.androlua.LuaActivity"` 绕过**。

## 7. 勿用 `math.tointeger`（不存在）

- 现象：`math.tointeger` 为 `nil`，调用报错。
- 根因：pro 魔改引擎把 `tointeger` 提为全局函数（`jni/lua/lbaselib.c`）。
- 规避：用全局 `tointeger(x)`。
- 验证：探针工程 `reference/project/probe/`（`PROBE tointeger=1` / `PROBE math.tointeger=nil` 双端逐字一致）。

## 8. 字节码不兼容标准 Lua

- 事实：引擎是 Lua 5.3.3 深度魔改（9 项语法扩展），**由 luac/标准 5.3 编译的字节码不可直接加载**；lide 的 `LuaCompiler` 用引擎自带 loadbuffer+dump 生成 `.luac`。
- 规避：交付 `.luac` 必须用与运行时同源的引擎编译；跨版本一律走源码。

## 9. 图标退化（无 res 工程）

- 现象：lide 运行无资源工程时快捷方式图标为系统默认图标（`android.R.drawable.sym_def_app_icon`），任务卡片仍读工程 `icon.png`。
- 定性：白名单差异（SKILL §6.1/§6.5），非缺陷。

## 10. 大文件与编辑器限制（sora 侧）

- `> 200 KB` 文件：lide 编辑器关闭括号高亮/当前块/自动换行并跳过行级诊断（AGENTS.md 编辑器约定）。
- 定性：编辑器能力边界，与 Lua 运行时无关；`LuaEditor` 类不存在于 lide（见 FEATURES §8）。

## 11. `tostring(0/0)` 的 NaN 符号位：`nan` vs `-nan`（工具链噪声）✅双端一致

- 现象：探针 `0/0` 项，pro 输出 `nan`，lide 输出 `-nan`，原始字符串比较失败。
- 根因：引擎源码同源（lide 基于 `AndroLuaPro v4.4.2 上游源码（移植后唯一真源 `third_party/androlua/` + `app/src/main/cpp/`）` 参考树 + NDK r29 重建），`luaO_tostring` 均走 `%.14g` 的 NaN 特判（`lobject.c:371-387`）；`0/0` 结果是 NaN，**符号位**在不同 ABI/翻译层下不同，bionic `sprintf` 按符号位输出 `-nan`。Lua 规范不保证 NaN 的字符串形式。
- 定性：**非 lide 缺陷**；探针在 `val()` 中归一化 `-nan→nan`（engine.lua:91-96），其余 281 项仍逐字一致。
- 验证：双端重跑后 `0/0` 为 `[OK]`，报告总分 pro 283/284、lide 280/284。

## 12. 老式外部存储权限随 targetSdk 变化（探针观测点）✅双端一致

- 现象：`activity.checkSelfPermission(WRITE/READ_EXTERNAL_STORAGE)` pro（targetSdk 23）返回已授予，lide（targetSdk 36）返回未授予；`INTERNET` 双端均已授予。
- 根因：Android 14 对 targetSdk≥24 的应用默认无旧式外部存储权限（`READ_MEDIA_*` 取代），是**预期的系统级差异**，非 lide 缺陷。
- 判定：按 SKILL 白名单 §6.1（环境差异）定性；探针 `Android 环境` 组总分随之差 2 分属正常。
- 问题：Java 方法引用（如 `activity.checkSelfPermission`）在探针中**不可直接 `pcall` 多次调用**（报 `can not call the function ...@checkSelfPermission`，句柄疑似单次消费）；必须用 `pcall(function() return activity.checkSelfPermission(x) end)`。

## 13. `Build.SUPPORTED_ABIS` 数组不能用 `.length`

- 现象：pro 中 `Build.SUPPORTED_ABIS.length` 报 `[Ljava.lang.String;@length is not a field or mothod`（pro 拼写如此）。
- 根因：pro 中 Java 数组反射只暴露 `[i]` 下标访问，字段/方法访问（含 `.length`）不可用。
- 规避：探针用 `while`+`pcall(arr[i])` 逐元素取到失败为止；lide 侧同代码实测输出一致（`x86_64,arm64-v8a,x86,armeabi-v7a,armeabi`）。

## 14. `lib?.so` require 失败与库缺失（zip 案例）✅双端一致

- 现象：`require("zip")` 双端均失败，报错路径为 `/data/app/~~...==/com.androlua-...==/lib/...`（应用安装路径前缀，末尾被截断）。
- 排查：pro APK 与设备 `/data/app/~~*/com.androlua-*/lib/arm/` 均确认存在 17 个 `.so`（含 libzip.so）；ELF 头正常，`NEEDED` 为 libluajava/libz/libc/libm/libdl。
- 定性：双端**报错行为一致**（同模式、仅路径前缀白名单差异），符"一致即通过"；根因留待物理设备更多 dlopen 日志深挖（疑为 require 的绝对路径拼接问题而非 .so 本体缺失）。

## 15. 探针报告卡在 pcall 静默失败：必须留错误出口

- 现象：pro 首跑 UI 未渲染，uiautomator 只见 pro 自带 ListView 调试控制台（标题 "test"）——`build_ui` 内任何异常被 `pcall` 吞掉，且引擎中 print 不进 UI。
- 规避：工程入口 print `UI_ERROR: <err>`、每区块 `RENDER_ERROR: <err>` 兜底（main.lua），logcat 可见即根因直达；本次实际为 `M.dp` 三处笔误（模块局部变量是 `ui`）→ 全绿。
- 验证：`adb shell uiautomator dump` + pull 解析 `text=` 节点确认 MD3 仪表盘为前台（模型读不了截图）。

## 16. 组件级 MD3 实证：uiautomator 显示 a11y 类名，须用 dumpsys activity top

- 现象：lide 已渲染 `MaterialCardView`，但 `uiautomator dump` 的 `class=` 是 `androidx.cardview.widget.CardView`（MaterialCardView 继承链上的 a11y 类名）；MaterialButton 显示为 `android.widget.Button`。
- 规避：真实类名用 `adb shell dumpsys activity top | Select-String "material"`（本次实证：MaterialCardView×38 / LinearProgressIndicator×38 / MaterialButton×3 / MaterialTextView×N）。
- 验证：`dumpsys activity top` 出现 `com.google.android.material.*` 类名即 MD3 组件落地。

## 17. AndroLua 反射对 `int...`/`ColorStateList` 参数匹配受限

- 现象：lide 上 `LinearProgressIndicator.setIndicatorColor({int})` / `setIndicatorColor(ColorStateList)` 报 `Invalid method call. Invalid Parameters.`；`setShowTrack(true)` 报 `not a field or mothod`；同类的 `setTrackColor(int)` / `setTrackThickness(float)` / `setIndeterminate(boolean)` / `setProgressCompat(int,boolean)` 正常。
- 规避：组件配置整体 `pcall` 包裹（失败即降级手搓实现）；进度条颜色不定制（默认即 M3 主题 primary）。
- 定性：AndroLua luajava 反射的方法参数匹配固有限制，pro/lide 同实现（lide 走 MDC 分支才会暴露）。

## 18. `pcall(loadstring(code))` 立即执行编译结果（不报错但语义错）

- 现象：探针扩展库全览 34 项全部 `N/A`——`local ok,r=pcall(loadstring(p[3]))` 中 `pcall` 直接执行了编译函数（参数即函数），`r` 是 chunk 返回值（boolean），随后 `pcall(r)` 抛 `attempt to call a boolean` 被吞。
- 规避：先编译后执行——`local ok,fn=pcall(loadstring, code)`，再 `pcall(fn)`。

## 19. pro 的 md5 1.2 简化版：`sum()` 只返回 16 字节 raw

- 现象：`md5.sum("hello")` 返回 16 字节二进制；`md5.sum("hex","hello")` 忽略参数仍返回 raw；`sumhexa` 不存在（`_VERSION="MD5 1.2"`，模块仅 `decrypt/sum/crypt/exor`）。
- 规避：探针自转 hex（`%02x` 拼接）再比对 `5d41402abc4b2a76b9719d911017c592`；lide 与 pro 的 md5.lua 逐字节一致（PARITY 报告），行为同。
- 验证：hash 章节 `md5("hello")` 双端 `[OK]`。

## 20. pro 的 utf8 库非标准语义（offset 偏移异常 / len 不校验）

- 现象：`utf8.offset("你好",2)` 返回 `nil`（标准应 4）；`utf8.offset("你好",1)` 返回 4（标准应 1）；`utf8.len("a\255b")` 返回 3（标准返回 nil+错误位置，不校验非法序列）。
- 规避：判定对齐原版 v12 口径——utf8.offset 只测存在性（`type(utf8.offset)=="function"`），非法序列改为 `pcall(utf8.len, ...)` 不抛错断言；具体行为值降为 level 3 观测。
- 验证：`utf8:offset`/`utf8:非法序列` 双端 `[OK]`，观测项值按平台显示。

## 21. pro 的 luajava.newInstance 实际构造不可用；newArray 需 bindClass 类对象

- 现象：`luajava.newInstance("java.lang.String","hi")`（含无参 StringBuilder）报 `Invalid method call. Invalid Parameters.`；`luajava.newArray("java.lang.Integer",3)` 报 `bad argument #1 to 'newArray' (JavaObject expected, got string)`。
- 规避：newInstance 对齐原版只测存在性（`type(luajava.newInstance)=="function"`），实际构造降为 level 3 观测；newArray 先用 `luajava.bindClass` 取类对象再传（`newArray(cls,3)` 可用，`astable` 正常）。
- 定性：pro 旧实现限制；lide 行为待同口径比对（双端均以存在性判定即一致）。

## 22. logcat 单条日志上限截断大报告

- 现象：37 章/865 项报告 `print(整串)` 后 logcat 只保留约 285 行（单条超限被截断，如 39KB 报告剩 4KB），对比清单缺失大半。
- 规避：`print` 逐行输出——`for line in (report.."\n"):gmatch("(.-)\n") do print(line) end`。
- 验证：`(Get-Content log).Count` ≥ 1000（双端 1028 行完整）。

## 23. 模拟器 x86 构建的 pro 为 double 模式（整数精度 2^53+1 丢失）

- 现象：`math.maxinteger==2^63-1` 在模拟器 pro 为 `false`（double 模式），而原版 v12 在真机（CPH2415 arm64）为 `true`（int64 模式）。
- 规避：探针该项降为 level 3 观测（不计分），双端以同值/同口径判定；若需 int64 断言，以真机验证为准。
- 定性：同一 APK 的 Lua 整数模式与 ABI/构建产物相关，模拟器 x86 变体与真机 arm64 可不同；属环境差异非缺陷。

## 24. GLSurfaceView 渲染器回调的 gl 参数在 ES2 下损坏（Live2D 实测）

- 现象：`GLSurfaceView.Renderer` 代理回调中 `gl` 参数类型异常——`onSurfaceChanged` 里 `gl` 是 number、`onDrawFrame` 里 `gl` 是 nil；`gl.glViewport(...)` 直接报错。
- 根因：AndroLua luajava 代理对 GLES2 接口的返回绑定不可靠；pro 的 `GLSurfaceView` 包了 `LuaGLSurfaceView`（回调内取 `GLES20` 静态引用），工程自建 Renderer 没这层封装。
- 规避：回调内**全部走 `android.opengl.GLES20` 静态调用**（`GLES20.glViewport/glClear/glGetError`），不碰 `gl` 参数；尺寸从 `View.getWidth()/getHeight()` 取（回调的 wd/ht 参数不可信，异常时回退 1080x1920）。
- 验证：`FPS 120 glErr=0` 持续输出即链路健康。

## 25. 触摸坐标一律按 TouchManager 语义：Y 负存、toView 直吃屏幕像素（Live2D 实测）

- 现象：tap 命中 miss——`tapEvent at 0.000,3.244` 超出视图范围（±aspect）判定失败；flick 起点出现双重变换。
- 根因：pro `LAppView$1.onSingleTapConfirmed` 用 `TouchManager.getX()/getY()`，touchMgr 内部 **Y 负存**（`touchBegan` 存 `-y`）；且 `transformDeviceToViewX/Y` 直接吃 touchMgr 的**屏幕像素**（含负存 Y），**先转 deviceToScreen 再 toView 是双重变换**。
- 规避：`toViewY` 传参用 `-event.getY()`；`dragMgr:set` / flick 起点的输入一律用 touchMgr.lastX/lastY/startX/startY（已负存），不要在中间多转一次视图空间。
- 验证：屏幕中心 tap 命中 body、上部 tap 命中 head（`tapEvent HIT head -> setRandomExpression`）。

## 26. `flickAvailable` 需在每次 touchBegan 重置（Live2D 实测）

- 现象：首次 flick 触发后，后续大位移 swipe 不再触发 flickEvent。
- 根因：`LAppView.onTouchEvent` ACTION_DOWN 时置 `mFlickAvailable=true`；在 touchEvent 入口（DOWN 分支）重置即可。
- 验证：连续两次 swipe 均输出 `flickEvent`。

## 27. 架构红线：引擎 .so 内禁止携带 Java JNI 桥（Lua 直调原则）

- 现象：lide 原生引擎模块 `liblive2d.so` 曾被编译进 `jp_live2d_sample_Live2DNative.cpp`（12 个 `Java_jp_live2d_sample_Live2DNative_*` JNI 导出符号），而工程内**不存在对应 Java 类**（`jp.live2d.sample.Live2DNative` 零引用）——纯死代码，且违反「Lua 直接调用原生库，Java 不参与运行时」的架构要求。
- 根因：实现初期同时保留了两条入口（Lua→C 与 Java→C 桥）；Java 桥的配套类从未落地，残留进 .so。
- 恢复动作（2026-08-02 已执行）：删除 `build-scripts/live2d/src/jni/`；`Android.mk` 移除该编译项；清理桥专用的 `LAppModel::mOwnDrag` 字段（Lua 路径恒 false，`update()` 内部 L2DTargetPoint 平滑拖拽不受影响）；重建 x86_64 `.so`。
- 验证：`llvm-nm -D liblive2d.so` 确认 `Java_*` 零命中、`luaopen_live2d` 保留；APK 内 `.so` 同检；设备回归（`LIVE2D_PATH native`、load ok、FPS 120、tap body/head、flick 全部通过）。
- 红线：**lide 引擎模块 .so 只允许 Lua→C 入口**（`luaopen_*`）；任何 Java 侧 JNI 绑定（`Java_*` 符号）必须独立成模块或直接禁止，除非有配套 Java 类并走宿主约定。

## 28. 源码树 ≠ 官方 APK 打包事实（lfs/lsqlite3 误移除教训）✅双端一致

- 现象：lide 曾按源码树 `Android.mk2`（lluv/lsqlite3 标记禁用）误判 lfs/lsqlite3 不在 pro 打包内，将其从 jniLibs/third_party/prebuilt/build-scripts 移除；实测设备上 pro 官方 APK（4.4.2）**实际包含** `liblfs.so`、`liblsqlite3.so`（共 17 个 .so），lide 缺库后探针 `lfs`/`lsqlite3` 会变 `[!!]`。
- 根因：源码树构建配置（`Android.mk2` 的 DISABLED 标记）与官方发布 APK 的实际打包不完全一致；判定库清单以**设备上官方 APK** 为准。
- 规避：核对原生库清单时先 `adb shell pm path com.androlua` 取 APK 路径 → 解包 `lib/*/` 列全，再对照 lide APK 同名列表（本次 lide 19 = pro 17 + liblive2d + libandroidx.graphics.path）。
- 验证：探针 `原生库` 组 lfs/lsqlite3 双端 `[OK]`；`git status` 确认已恢复文件（jniLibs/third_party/prebuilt/build-scripts/rebuild_engine.ps1）。

## 29. `LuaAccessibilityService.onDestroy` 必须调用 `stopScreenshot()`（lide 修复记录）

- 现象（修复前）：lide 的 `LuaAccessibilityService.onDestroy()` 缺少 `stopScreenshot()`，与 pro 源码不一致（pro 在 `sAccessibilityServiceCallbacks.onDestroy` 之后、`super.onDestroy()` 之前调用）。
- 修复（2026-08-02 已落地）：`androlua/src/main/java/com/androlua/LuaAccessibilityService.java` onDestroy 补齐 `stopScreenshot()`。
- 验证：该文件与 pro 源逐行 diff 仅剩注释/import 中立差异。

## 30. 引擎内 PLT 符号的 GOT 槽可能加载期固化为坏值（glBlendFunc 崩溃，Live2D 实测）

- 现象：新 `liblive2d.so` 部署后 GLThread 数秒内 SIGSEGV，tombstone 定位 `lua_drawBg`（`lua_live2d.cpp:407`）`glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)`；崩溃点 `callq glBlendFunc@plt`，其 GOT 槽（.got.plt，GNU_RELRO 只读段，RELRO 下加载期写死）指向从未映射的地址 `0x7a16003634a0`。
- 根因：该 GOT 槽的值在库加载解析时（默认 lazy）被固化；调用点存在某次占到不可执行/未映射区域直接 SIGSEGV。同一 .so 里模型路径每帧调用的 `glBlendFuncSeparate` 从未崩，而 `glBlendFunc` 一次即崩——疑似该符号的绑定在本机环境损坏（模拟器 codec 对绑定做特殊处理）。
- 规避：改用**语义等价的稳定符号**——`glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)`（对背景铺全屏是严格等价）；并加注释说明为何不用 glBlendFunc。
- 验证：替换后 8+ 分钟不崩、glErr=0；反汇编确认调用点、GOT 槽地址与崩溃 PC 逐一对应。

## 31. GL 对象必须在该对象的 GL 线程内销毁：禁止 __gc 回收模型（Live2D 实测）

- 现象：稳定运行 ~3 分钟后模拟器 SIGSEGV（tombstone `SI_KERNEL code 128`），栈为 `GLSharedGroup::deleteProgramDataLocked` ← `~DrawParamOpenGL` ← `~Live2DModelOpenGL`，调用者来自 **主线程 Lua lua_gc**。
- 根因：Lua GC 的可重入（`__gc`）在主线程回收 Live2D 模型 userdata → C++ 析构调用 `glDeleteProgram`，而程序/纹理的 GL 上下文是 GLSurfaceView 的 GLThread 创建——跨线程删 GL 对象，模拟器（`libOpenglCodecCommon.so`）崩溃。
- 规避：**`luaopen_live2d` 不注册 `__gc`**；模型删除只走显式 `live2d.free`（Lua 侧仅 GL 线程内 reload 路径调用）；`live2d.gc` 文档注明「GC 回收不安全，须显式 free」。
- 验证：去掉 `__gc` 后连续 8+ 分钟稳定（中途含 6+ 次 reload、双模型分支反复加载卸载），无新 tombstone 生成。

## 32. LuaActivity 启动必须走 `-d file://` data URI；`--es luaPath` 无效

- 现象：`am start --es luaPath /sdcard/.../main.lua` 启动后进程立刻崩溃；`/storage/emulated/0/androlua/crash/` 有 `sendMsg(null)` NPE。
- 根因：`LuaActivity.getLuaPath()` 只读 intent **data URI**，不读 `luaPath` extra；无 data 时返回 null，后续 `com.android.internal.util` Android 14 下 `sendMsg(null)` → `Log.i("lua", null)` NPE。
- 规避：`am start -n <pkg>/com.androlua.LuaActivity -d "file:///sdcard/AndroLua/project/<name>/main.lua"`（data 必须是 file:// 绝对路径）。

## 33. DrawOrder 桶索引用「单绘制平均」极值会越界写堆（Live2D 视图顺序错乱 + 延迟崩溃）

- 现象：滑动中触摸模型 → 出现**资源缺失/绘制顺序移位**；叠加模型切换后长滑累积崩溃，且崩溃发生在**离开绘制路径之后**（GLThread 的 `je_free` ← `~BinaryReader`/`~L2DBaseModel`/`~LAppModel`，栈见 tombstone 20/21/23/25）——症状程远离根因。
- 根因：`LAppView.draw` 复刻的 draw-order 桶需要每绘制单元先算平均、再用全局平均的 min/max 归一化成桶索引。原实现按**每绘制分组各自的平均 min/max** 建桶，而插值阶段的**最终 order** 会跌破全局平均下限 → `bucketIdx` 为**负数** → `mOrderListLastDrawIndex[负数]` 堆越界写。堆腐败当日不崩、数分钟后在无关析构点崩（模拟器 `je_free` 特征），同时污染相邻桶链 → 渲染顺序错乱。
- 实证：诊断日志 `DRAWDER_OOB order=-19 range=685 min=266 max=951 i=54 interpOrder=247`（插值 order 247 < 全局平均下限 266）；修复后 12 轮 × 20 滑 + 12 次换装零命中、零 tombstone。
- 规避：桶极值必须取自**全局**（对所有绘制（含 pivot 极值的插值界：插值是凸组合，永不出 pivot 范围）；保留防御性 clamp。相关代码见 `build-scripts/live2d/src/Model/ModelContext.cpp /* 桶 */`。
- 验证：拖拽+换装组合复现脚本（每轮 15 步 swipe + 换装键 tap），`adb logcat -d -s live2d-diag:*` 零命中。

## 真机 GLSL #version 120 被 Adreno 拒绝 → 角色不渲染（⚠仅真机，模拟器不可见）

- 现象：真机（CPH2415, arm64, Adreno）跑看板娘 live2d 引擎，只有背景图、无角色；背景可缩放露出黑边；logcat 出现 8 次 AdrenoGLES-0: ERROR: Invalid #version（恰好 4 个 shader × 2 次编译）。模拟器（SwiftShader 软渲染）正常显示角色。
- 根因：liblive2d.so 的模型/蒙版 shader 以桌面 GLSL #version 120 开头（uild-scripts/live2d/src/Graphics/DrawParamOpenGL.cpp），而 EGL 上下文是 ES 2.0（CONTEXT_CLIENT_VERSION=2）。ES 驱动只接受 #version 100；SwiftShader 宽容接受 120，真机 Adreno 严格报错 → 模型 shader 编译失败 → 角色不绘制。背景 shader（lua_live2d.cpp）无 #version 指令，ES 默认按 100 编译 → 背景正常，造成"只有背景"假象。
- 规避/修复：#version 120 → #version 100（已修复，三 ABI 重编）。教训：**GLSL 版本指令必须与 EGL 上下文匹配**；模拟器（SwiftShader/ANGLE）对非法版本宽容，真机 GPU 驱动严格——GL 相关 bug 必须在真机回归。
- 判定：真机 db logcat -d | grep "Invalid #version" 零命中 + 截图像素含角色色（75697a 紫/436271 蓝/肤色）即修复。
- 验证命令：真机 arm64 装 pp-arm64-debug.apk → m start -a VIEW -d file:///storage/emulated/0/AndroLua/project/live2d/main.lua -n com.lioisme.lide/com.androlua.LuaActivity → 12s → 截图 + 像素统计（角色色簇 > 1000）。

## 真机环境事实（⚠仅真机）

- 真机 CPH2415（OPPO，Android 15/API 35，arm64-v8a）通过 USB 连接时 db devices 见 5fb6fdc9；模拟器 emulator-5554（x86_64）仍在线，操作必须带 -s 指定设备。
- 真机 lide 的 EngineActivity **未导出**（Permission Denial ... not exported from uid），只能经导出的 com.androlua.LuaActivity（file:// VIEW + -n）启动；模拟器上 EngineActivity 可直接 m start。
- 真机安装的 APK 需 arm64 版：.\gradlew.bat :app:assembleArm64Debug（模拟器用 X64）。
- 真机工程目录 /storage/emulated/0/AndroLua/project/live2d/（由 alp 解包或 db push 同步，务必与 uild-scripts/examples-src/live2d/ 一致后再测）。

## 看板娘工程已去 pro 化（⚠仅 lide）

- reference/project/看板娘/ 与示例 uild-scripts/examples-src/live2d/：已删除 pro 回退代码（import "jp.live2d.*"、SoundManager/FileManager/LAppLive2DManager 分支）与资产（libs/live2D.dex），仅支持 lide 原生库 liblive2d.so（equire "live2dapp" 失败即明确报错退出）。
- 真机回归全 PASS：haru→shizuku→wanko→haru_01+02 四分支轮换、tap/flick 交互、FPS 60 glErr=0、无 Runtime error。
- live2dapp.lua 内 dex @xxx 实证 注释为行为来源标注（官方 v2 引擎语义），非 pro 兼容代码，保留。

## 34. Kotlin KDoc 里写 `libs/*.dex` / `libs/*.so` 会开启嵌套块注释（⚠仅 lide 源码编写）

- 现象：文件从 KDoc 中 `libs/*.dex` 处开始被注释"吞噬"，编译器报 `Unclosed comment`（错误行号指向文件 EOF 之外，极具迷惑性），该文件后续所有声明全部消失 → 其他文件连锁 `Unresolved reference`。
- 根因：Kotlin 块注释可嵌套，`/*` 字节序列在注释内会开启嵌套注释；`libs/*.dex` 中的 `/*`（`s/` + `*`）恰好构成开启符，而文本内无对应 `*/` 闭合 → 外层注释永不闭合。
- 规避：KDoc 中引用 glob 模式一律写成 `libs/ 下 dex|jar`（或转义），禁止出现 `/*` 字节序列；已修复于 `ApiIndex.kt`（2026-08-02）。
- 验证：编译报错消失；排查手段 = 对源码文件做字节级 `/*` 与 `*/` 计数（不相等即存在嵌套开口），`rg` 等文本工具看不见此问题。

## 35. 宿主 API 浏览器对工程 libs 的识别边界（⚠仅 lide，与 §7 互补）

- 事实：`dex|jar` → 类名级全量识别（入「工程库」分类）；`lib*.so` → 仅模块名级（入「原生库」分类）；函数级不可静态获取（C 模块函数表只存在于 Lua 运行时内存，枚举需执行不可信代码——已否决，见 `docs/planned/API_BROWSER_AUDIT_REPORT.md` §4）。
- 判定：浏览器「工程库/原生库」分类计数随 `<project>/libs/` 内容变化；`.so` 只展示 `require "模块名"` 与说明文案，不承诺函数列表。