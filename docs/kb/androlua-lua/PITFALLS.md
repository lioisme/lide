# 已知问题与规避（Known Issues & Mitigations）

> 每条格式：现象 → 根因 → 规避/判定 → 验证命令。已双端实证的标 `✅双端一致`。

## 1. demo 工程自身 md5 越界（工程自身 bug，双端必须同样报错）✅双端一致

- 现象：`I lua: Runtime error: length=16; index=16`，`main.lua:83 in function 'md5'` 与 `main.lua:103 in main chunk`。
- 根因：工程自身 bug（3 位毫秒时间戳配合 `md5` 实现越界），非运行时差异。
- 判定：pro 与 delua 报错文本、`stack traceback` 逐字一致即为正常；**不要**因报错"相同"去修 delua。
- 验证：双端启动 demo（命令见 ENVIRONMENT.md §4），`adb logcat -d -s lua:*` 对比。

## 2. libs/ 内 dex 包名解析失败 → NameNotFoundException 降级 ✅双端一致

- 现象：启动打印 `android.content.pm.PackageManager$NameNotFoundException: .../<name>/libs/okhtttp3.dex` 栈（含 activity + newActivity 各一次），随后仍以字节加载继续运行。
- 根因：`LuaDexLoader.loadApp/loadDex/loadLibs` 的包名解析失败→降级路径，pro 原版本就有该打印。
- 注意：delua 版该方法比 pro 版 **+4 行**（注释差），比对栈时行号有固定偏移属正常（SKILL 白名单 §6.2）。
- 判定：栈的类名+消息逐字一致（仅方法行号允许 +4 偏移）即一致。

## 3. 裸 file:// VIEW intent 会进 ResolverActivity

- 现象：不带 `-n` 时 `am start -a VIEW -d file://...` 弹出应用选择器（多个 app 都能处理 .lua）。
- 规避：双端启动一律带 `-n com.androlua/com.androlua.LuaActivity` 或 `-n com.lioisme.delua/com.androlua.LuaActivity` 指定组件。
- 验证：`adb shell "dumpsys activity activities" | Select-String topResumedActivity` 应直接是目标 Activity。

## 4. `androlua://` scheme 只进主程序 UI

- 现象：`am start -a VIEW -d androlua://...` 进 MainActivity 主页，不自动运行引擎。
- 规避：自动化验证用 file:// + `-n`；UI 路径（点工程 → 点「运行」）仅作抽查。

## 5. 高 targetSdk 默认禁明文 HTTP（delua 历史问题，已修复）

- 现象（修复前）：Lua 中 `Http` 请求 http:// 明文地址失败。
- 根因：delua targetSdk=36 默认禁止 cleartext，pro targetSdk=23 默认放行。
- 修复：`<application android:usesCleartextTraffic="true">`（已落地）。回归时若再遇 HTTP 失败，先查此条。

## 6. `com.androlua.LuaActivity` 未注册 → ActivityNotFoundException（delua 历史问题，已修复）

- 现象（修复前）：运行任意工程崩溃 `ActivityNotFoundException: com.androlua.LuaActivity`。
- 根因：`LuaActivity.newActivity()` 显式 `setClassName(..., LuaActivity.class.getName())`，但 delua manifest 只注册了空子类。
- 修复：manifest 注册 LuaActivity + 5 个 .lua intent-filter（file/content）。**不要让 Lua 工程用 `import "com.androlua.LuaActivity"` 绕过**。

## 7. 勿用 `math.tointeger`（不存在）

- 现象：`math.tointeger` 为 `nil`，调用报错。
- 根因：pro 魔改引擎把 `tointeger` 提为全局函数（`jni/lua/lbaselib.c`）。
- 规避：用全局 `tointeger(x)`。
- 验证：探针工程 `docs/reference/project/probe/`（`PROBE tointeger=1` / `PROBE math.tointeger=nil` 双端逐字一致）。

## 8. 字节码不兼容标准 Lua

- 事实：引擎是 Lua 5.3.3 深度魔改（9 项语法扩展），**由 luac/标准 5.3 编译的字节码不可直接加载**；delua 的 `LuaCompiler` 用引擎自带 loadbuffer+dump 生成 `.luac`。
- 规避：交付 `.luac` 必须用与运行时同源的引擎编译；跨版本一律走源码。

## 9. 图标退化（无 res 工程）

- 现象：delua 运行无资源工程时快捷方式图标为系统默认图标（`android.R.drawable.sym_def_app_icon`），任务卡片仍读工程 `icon.png`。
- 定性：白名单差异（SKILL §6.1/§6.5），非缺陷。

## 10. 大文件与编辑器限制（sora 侧）

- `> 200 KB` 文件：delua 编辑器关闭括号高亮/当前块/自动换行并跳过行级诊断（AGENTS.md 编辑器约定）。
- 定性：编辑器能力边界，与 Lua 运行时无关；`LuaEditor` 类不存在于 delua（见 FEATURES §8）。

## 11. `tostring(0/0)` 的 NaN 符号位：`nan` vs `-nan`（工具链噪声）✅双端一致

- 现象：探针 `0/0` 项，pro 输出 `nan`，delua 输出 `-nan`，原始字符串比较失败。
- 根因：引擎源码同源（delua 基于 `docs/reference/androlua/AndroLua_pro-master` 参考树 + NDK r29 重建），`luaO_tostring` 均走 `%.14g` 的 NaN 特判（`lobject.c:371-387`）；`0/0` 结果是 NaN，**符号位**在不同 ABI/翻译层下不同，bionic `sprintf` 按符号位输出 `-nan`。Lua 规范不保证 NaN 的字符串形式。
- 定性：**非 delua 缺陷**；探针在 `val()` 中归一化 `-nan→nan`（engine.lua:91-96），其余 281 项仍逐字一致。
- 验证：双端重跑后 `0/0` 为 `[OK]`，报告总分 pro 283/284、delua 280/284。

## 12. 老式外部存储权限随 targetSdk 变化（探针观测点）✅双端一致

- 现象：`activity.checkSelfPermission(WRITE/READ_EXTERNAL_STORAGE)` pro（targetSdk 23）返回已授予，delua（targetSdk 36）返回未授予；`INTERNET` 双端均已授予。
- 根因：Android 14 对 targetSdk≥24 的应用默认无旧式外部存储权限（`READ_MEDIA_*` 取代），是**预期的系统级差异**，非 delua 缺陷。
- 判定：按 SKILL 白名单 §6.1（环境差异）定性；探针 `Android 环境` 组总分随之差 2 分属正常。
- 问题：Java 方法引用（如 `activity.checkSelfPermission`）在探针中**不可直接 `pcall` 多次调用**（报 `can not call the function ...@checkSelfPermission`，句柄疑似单次消费）；必须用 `pcall(function() return activity.checkSelfPermission(x) end)`。

## 13. `Build.SUPPORTED_ABIS` 数组不能用 `.length`

- 现象：pro 中 `Build.SUPPORTED_ABIS.length` 报 `[Ljava.lang.String;@length is not a field or mothod`（pro 拼写如此）。
- 根因：pro 中 Java 数组反射只暴露 `[i]` 下标访问，字段/方法访问（含 `.length`）不可用。
- 规避：探针用 `while`+`pcall(arr[i])` 逐元素取到失败为止；delua 侧同代码实测输出一致（`x86_64,arm64-v8a,x86,armeabi-v7a,armeabi`）。

## 14. `lib?.so` require 失败与库缺失（zip 案例）✅双端一致

- 现象：`require("zip")` 双端均失败，报错路径为 `/data/app/~~...==/com.androlua-...==/lib/...`（应用安装路径前缀，末尾被截断）。
- 排查：pro APK 与设备 `/data/app/~~*/com.androlua-*/lib/arm/` 均确认存在 17 个 `.so`（含 libzip.so）；ELF 头正常，`NEEDED` 为 libluajava/libz/libc/libm/libdl。
- 定性：双端**报错行为一致**（同模式、仅路径前缀白名单差异），符"一致即通过"；根因留待物理设备更多 dlopen 日志深挖（疑为 require 的绝对路径拼接问题而非 .so 本体缺失）。

## 15. 探针报告卡在 pcall 静默失败：必须留错误出口

- 现象：pro 首跑 UI 未渲染，uiautomator 只见 pro 自带 ListView 调试控制台（标题 "test"）——`build_ui` 内任何异常被 `pcall` 吞掉，且引擎中 print 不进 UI。
- 规避：工程入口 print `UI_ERROR: <err>`、每区块 `RENDER_ERROR: <err>` 兜底（main.lua），logcat 可见即根因直达；本次实际为 `M.dp` 三处笔误（模块局部变量是 `ui`）→ 全绿。
- 验证：`adb shell uiautomator dump` + pull 解析 `text=` 节点确认 MD3 仪表盘为前台（模型读不了截图）。

## 16. 组件级 MD3 实证：uiautomator 显示 a11y 类名，须用 dumpsys activity top

- 现象：delua 已渲染 `MaterialCardView`，但 `uiautomator dump` 的 `class=` 是 `androidx.cardview.widget.CardView`（MaterialCardView 继承链上的 a11y 类名）；MaterialButton 显示为 `android.widget.Button`。
- 规避：真实类名用 `adb shell dumpsys activity top | Select-String "material"`（本次实证：MaterialCardView×38 / LinearProgressIndicator×38 / MaterialButton×3 / MaterialTextView×N）。
- 验证：`dumpsys activity top` 出现 `com.google.android.material.*` 类名即 MD3 组件落地。

## 17. AndroLua 反射对 `int...`/`ColorStateList` 参数匹配受限

- 现象：delua 上 `LinearProgressIndicator.setIndicatorColor({int})` / `setIndicatorColor(ColorStateList)` 报 `Invalid method call. Invalid Parameters.`；`setShowTrack(true)` 报 `not a field or mothod`；同类的 `setTrackColor(int)` / `setTrackThickness(float)` / `setIndeterminate(boolean)` / `setProgressCompat(int,boolean)` 正常。
- 规避：组件配置整体 `pcall` 包裹（失败即降级手搓实现）；进度条颜色不定制（默认即 M3 主题 primary）。
- 定性：AndroLua luajava 反射的方法参数匹配固有限制，pro/delua 同实现（delua 走 MDC 分支才会暴露）。

## 18. `pcall(loadstring(code))` 立即执行编译结果（不报错但语义错）

- 现象：探针扩展库全览 34 项全部 `N/A`——`local ok,r=pcall(loadstring(p[3]))` 中 `pcall` 直接执行了编译函数（参数即函数），`r` 是 chunk 返回值（boolean），随后 `pcall(r)` 抛 `attempt to call a boolean` 被吞。
- 规避：先编译后执行——`local ok,fn=pcall(loadstring, code)`，再 `pcall(fn)`。

## 19. pro 的 md5 1.2 简化版：`sum()` 只返回 16 字节 raw

- 现象：`md5.sum("hello")` 返回 16 字节二进制；`md5.sum("hex","hello")` 忽略参数仍返回 raw；`sumhexa` 不存在（`_VERSION="MD5 1.2"`，模块仅 `decrypt/sum/crypt/exor`）。
- 规避：探针自转 hex（`%02x` 拼接）再比对 `5d41402abc4b2a76b9719d911017c592`；delua 与 pro 的 md5.lua 逐字节一致（PARITY 报告），行为同。
- 验证：hash 章节 `md5("hello")` 双端 `[OK]`。

## 20. pro 的 utf8 库非标准语义（offset 偏移异常 / len 不校验）

- 现象：`utf8.offset("你好",2)` 返回 `nil`（标准应 4）；`utf8.offset("你好",1)` 返回 4（标准应 1）；`utf8.len("a\255b")` 返回 3（标准返回 nil+错误位置，不校验非法序列）。
- 规避：判定对齐原版 v12 口径——utf8.offset 只测存在性（`type(utf8.offset)=="function"`），非法序列改为 `pcall(utf8.len, ...)` 不抛错断言；具体行为值降为 level 3 观测。
- 验证：`utf8:offset`/`utf8:非法序列` 双端 `[OK]`，观测项值按平台显示。

## 21. pro 的 luajava.newInstance 实际构造不可用；newArray 需 bindClass 类对象

- 现象：`luajava.newInstance("java.lang.String","hi")`（含无参 StringBuilder）报 `Invalid method call. Invalid Parameters.`；`luajava.newArray("java.lang.Integer",3)` 报 `bad argument #1 to 'newArray' (JavaObject expected, got string)`。
- 规避：newInstance 对齐原版只测存在性（`type(luajava.newInstance)=="function"`），实际构造降为 level 3 观测；newArray 先用 `luajava.bindClass` 取类对象再传（`newArray(cls,3)` 可用，`astable` 正常）。
- 定性：pro 旧实现限制；delua 行为待同口径比对（双端均以存在性判定即一致）。

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

- 现象：delua 原生引擎模块 `liblive2d.so` 曾被编译进 `jp_live2d_sample_Live2DNative.cpp`（12 个 `Java_jp_live2d_sample_Live2DNative_*` JNI 导出符号），而工程内**不存在对应 Java 类**（`jp.live2d.sample.Live2DNative` 零引用）——纯死代码，且违反「Lua 直接调用原生库，Java 不参与运行时」的架构要求。
- 根因：实现初期同时保留了两条入口（Lua→C 与 Java→C 桥）；Java 桥的配套类从未落地，残留进 .so。
- 恢复动作（2026-08-02 已执行）：删除 `build-scripts/live2d/src/jni/`；`Android.mk` 移除该编译项；清理桥专用的 `LAppModel::mOwnDrag` 字段（Lua 路径恒 false，`update()` 内部 L2DTargetPoint 平滑拖拽不受影响）；重建 x86_64 `.so`。
- 验证：`llvm-nm -D liblive2d.so` 确认 `Java_*` 零命中、`luaopen_live2d` 保留；APK 内 `.so` 同检；设备回归（`LIVE2D_PATH native`、load ok、FPS 120、tap body/head、flick 全部通过）。
- 红线：**delua 引擎模块 .so 只允许 Lua→C 入口**（`luaopen_*`）；任何 Java 侧 JNI 绑定（`Java_*` 符号）必须独立成模块或直接禁止，除非有配套 Java 类并走宿主约定。

## 28. 源码树 ≠ 官方 APK 打包事实（lfs/lsqlite3 误移除教训）✅双端一致

- 现象：delua 曾按源码树 `Android.mk2`（lluv/lsqlite3 标记禁用）误判 lfs/lsqlite3 不在 pro 打包内，将其从 jniLibs/prebuilt/build-scripts 移除；实测设备上 pro 官方 APK（4.4.2）**实际包含** `liblfs.so`、`liblsqlite3.so`（共 17 个 .so），delua 缺库后探针 `lfs`/`lsqlite3` 会变 `[!!]`。
- 根因：源码树构建配置（`Android.mk2` 的 DISABLED 标记）与官方发布 APK 的实际打包不完全一致；判定库清单以**设备上官方 APK** 为准。
- 规避：核对原生库清单时先 `adb shell pm path com.androlua` 取 APK 路径 → 解包 `lib/*/` 列全，再对照 delua APK 同名列表（本次 delua 19 = pro 17 + liblive2d + libandroidx.graphics.path）。
- 验证：探针 `原生库` 组 lfs/lsqlite3 双端 `[OK]`；`git status` 确认已恢复文件（jniLibs/prebuilt/build-scripts/rebuild_engine.ps1）。

## 29. `LuaAccessibilityService.onDestroy` 必须调用 `stopScreenshot()`（delua 修复记录）

- 现象（修复前）：delua 的 `LuaAccessibilityService.onDestroy()` 缺少 `stopScreenshot()`，与 pro 源码不一致（pro 在 `sAccessibilityServiceCallbacks.onDestroy` 之后、`super.onDestroy()` 之前调用）。
- 修复（2026-08-02 已落地）：`androlua/src/main/java/com/androlua/LuaAccessibilityService.java` onDestroy 补齐 `stopScreenshot()`。
- 验证：该文件与 pro 源逐行 diff 仅剩注释/import 中立差异。
