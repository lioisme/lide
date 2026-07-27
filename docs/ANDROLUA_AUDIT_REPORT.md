# AndroLuaX 全量审计报告

> 审计基准：AndroLuaX v3.4(5.0.18)(Material3)
> 审计日期：2026-07-27
> JAR 反编译来源：`AndroLua+5.0.18_AndroidX.jar`（961 个 class 文件）

---

## 一、项目架构总览

### 1.1 模块结构（两模块 Gradle 项目）

```
AndroLuaX-main/
├── androlua/                   # Android Library 模块 (com.androlua)
│   ├── libs/AndroLua+5.0.18_AndroidX.jar  # 核心业务逻辑 (961 classes)
│   ├── src/main/java/
│   │   ├── com/androlua/LuaDialog.java     # 对话框 (源码已提供)
│   │   └── androidx/appcompat/app/AlertControllerBridge.java  # 桥接 (源码)
│   └── src/main/resources/lua/
│       ├── import.lua          # 导入系统 (核心)
│       ├── loadlayout.lua      # 布局系统 (核心)
│       ├── loadbitmap.lua      # 图片加载
│       └── loadmenu.lua        # 菜单加载
│
├── app/                        # Android Application 模块
│   ├── src/main/java/com/jesse205/androluax/LuaMaterialDialog.java
│   ├── src/main/resources/lua/
│   │   ├── bin.lua             # APK 打包系统
│   │   ├── console.lua         # Lua 编译器/格式化器
│   │   └── ... (约 25 个 Lua 模块)
│   └── src/main/jniLibs/       # 原生 .so 库 (16 个)
│       ├── armeabi-v7a/libluajava.so  # Lua VM + JNI 桥
│       ├── armeabi-v7a/liblua.so      # Lua C 运行时
│       └── ... (cjson, zlib, socket, sensor, xml 等)
```

### 1.2 三层架构模型

```
┌──────────────────────────────────────────┐
│  UI/IDE 层 (Kotlin待翻译目标)              │
│  Main, Welcome, LuaEditor, LuaDialog,     │
│  LuaWebView, LuaAdapter, LuaService       │
├──────────────────────────────────────────┤
│  Lua 运行时引擎层                          │
│  LuaActivity, LuaApplication, LuaContext, │
│  LuaThread, LuaAsyncTask, LuaTimer,       │
│  LuaDexLoader, LuaEnhancer                │
├──────────────────────────────────────────┤
│  Lua-Java 绑定层 (JNI 桥)                 │
│  LuaState, LuaObject, LuaTable, LuaStack, │
│  LuaJavaAPI, LuaInvocationHandler         │
├──────────────────────────────────────────┤
│  原生 C 层 (不变)                         │
│  libluajava.so, liblua.so, libcjson.so   │
└──────────────────────────────────────────┘
```

---

## 二、com.luajava.* 绑定层审计（18 个类）

### 2.1 JNI 核心桥

#### LuaState (核心类)
- **职责**：Lua VM 状态机的 Java 封装，通过 `long nativePtr` 持有 C 层 `lua_State*`
- **关键字段**：`a(long)` = native pointer；`b(LuaContext)` 运行时上下文；`c/d/e` 对象引用管理
- **关键方法**：
  - `openLibs()` / `openBase()` / `openMath()` / `openString()` / `openTable()` / `openOs()` / `openDebug()` / `openIo()` / `openPackage()` / `openLuajava()` — 加载 Lua 标准库
  - `L newTable()`, `pushGlobalTable()`, `getGlobal(String)`, `setGlobal(String)` — 全局表操作
  - `pushJavaObject(Object)`, `toJavaObject(int)`, `pushObjectValue(Object)` — Java ↔ Lua 类型转换
  - `pcall(int, int, int)`, `resume(LuaState, int)`, `yield(int)` — Lua 执行调用
  - `getLuaObject(int/str)`, `getLuaObject(LuaObject, LuaObject/Number/String)` — Lua 对象获取
  - `isFunction/Table/String/Number/Boolean/Nil/Userdata(int)` — 类型检查
  - `pushContext(LuaContext)` — 将 `_LuaContext` 注入 Lua 全局环境
  - `pushPrimitive()` — 向 Lua 注册 Java 原始类型名（boolean/byte/char/short/int/long/float/double）
  - **~100 个 native 方法**，全部通过 `_xxx(long, ...)` 调用 C 层实现

#### LuaJavaAPI (核心 JNI 入口)
- **职责**：静态工具类，Java ↔ Lua 反射调用的核心
- **关键方法**：
  - `javaBindClass(long, String)` — 绑定 Java 类到 Lua
  - `javaNew(long, int)` / `javaCreate(long, int)` — 创建 Java 对象
  - `javaGetter(LuaState, Object, String)` / `javaSetter(LuaState, Object, String)` — 属性访问
  - `callMethod(long, int, String)` — 调用 Java 方法
  - `objectIndex(long, int, String, int)` / `objectNewIndex(long, int, String, int)` — Lua 索引操作
  - `javaOverride(long, int)` — 创建 Java 接口/虚类的 Lua 代理
  - `createProxy(long, String)` — 创建动态代理
  - `createArray(long, String)` — 创建 Java 数组
  - `newArray(long, int/str)` — 数组操作
  - `asTable(long, int)` — Java 对象转 Lua table
  - `checkClass/Method/Field(LuaState, Object, String)` — 反射检查
  - 内部维护大量 `HashMap<Class, Method[]>` 反射缓存
  - 类型转换：`e/LuaState, Class, int)` 将 Lua 值转为 Java 对象

### 2.2 Lua 对象封装

#### LuaObject
- 实现 `Serializable`，是 Lua 值的 Java 封装
- 持有 `LuaState` 引用和栈索引 `a(int)`
- 支持多构造函数：`(LuaState)`, `(LuaState, int)`, `(LuaState, String)`, `(LuaObject, LuaObject/Number/String)`
- 方法：`call(...)`, `_call(...)`, `getField/setField`, `getI/setI`, `push/pop`, `createProxy`, `dump`
- 类型查询：`isFunction/isTable/isString/isNumber/isBoolean/isNil/isUserdata/isJavaObject`

#### LuaTable<K, V>
- 继承 `LuaObject`，实现 `Map<K, V>`
- 支持 `put/get/containsKey/containsValue/entrySet/keySet/values/clear/remove/size/isEmpty/isList/length`
- 迭代器支持 Lua 表 ↔ Java Map 互操作

#### LuaFunction<T>
- 继承 `LuaObject`，实现 `LuaMetaTable` 接口
- 支持 `__call/__index/__newIndex` 元方法
- `call(Object...)` 调用 Lua 函数并返回结果

#### LuaList
- Lua 表的列表封装（方法未完整展示但类似 LuaTable 的 List 接口）

#### LuaStack
- Lua 栈操作的封装

#### LuaString
- Lua 字符串封装（字节数组 vs 字符串转换）

#### LuaMetaTable (接口)
- 定义 `__call/__index/__newIndex` 元方法协议

### 2.3 函数与代理

#### JavaFunction (abstract)
- Lua C 函数的 Java 适配器
- 子类需实现 `execute(): int`（返回结果数量）
- `getParam(int)`: `LuaObject` / `register(String)`: 注册为 Lua 全局

#### LuaInvocationHandler
- 实现 `java.lang.reflect.InvocationHandler`
- 通过 `LuaObject` 桥接 Java 接口 → Lua 函数调用
- 内部持有 `LuaContext` 和 `LuaObject`

#### LuaMethodInterceptor
- 含内部类 `SuperCall`，支持虚类方法拦截

#### LuaAbstractMethodInterceptor
- 继承 `LuaMethodInterceptor`，支持抽象方法拦截

### 2.4 辅助类

- **LuaStateFactory**：单例工厂，管理所有 `LuaState` 实例（`Map<Long, LuaState>`），提供 `newLuaState()`, `insertLuaState()`, `getExistingState()`, `removeLuaState()`
- **LuaException**：Lua 异常类
- **Console**：Lua 控制台 I/O

---

## 三、com.androlua.* 运行时引擎审计

### 3.1 LuaContext (interface) — 核心运行时契约
- **21 个方法**定义 Lua 运行时的完整 API
- `getLuaState()` / `getContext()` / `getClassLoaders()` — 核心访问
- `getLuaDir/LuaPath/LuaExtDir/LuaExtPath/LuaLpath/LuaCpath` — 文件路径管理（各 2-3 个重载）
- `getWidth/Height` / `getGlobalData/getSharedData/setSharedData` — 数据和尺寸
- `doFile/call/sendMsg/sendError/set/get` — Lua 执行与通信
- `regGc` — GC 注册

### 3.2 LuaApplication
- 继承 `android.app.Application`，实现 `LuaContext`
- 单例模式：`getInstance()`
- 管理全局路径：`luaDir/luaExtDir/localDir/libDir/odexDir/mdDir`
- 生命周期 `onCreate()`：初始化基础目录和配置
- 持 LuaState，通过 `Application` 全局共享

### 3.3 LuaActivity (核心类)
- 继承 `androidx.appcompat.app.AppCompatActivity`，实现 `LuaContext` 和 `BroadcastReceiver`
- **~100 个方法**，是 Lua 脚本的主执行容器
- 关键流程：
  1. `onCreate()` → 初始化 Lua 环境、加载脚本
  2. `onResume()` / `onPause()` → Lua 生命周期回调
  3. `onDestroy()` → 清理 Lua 状态、GC
  4. `onKeyDown/Up/LongPress` → 键盘事件 → Lua
  5. `onActivityResult` → Activity 结果回调
- **Lua 执行**：
  - `doFile(path, ...)` / `doString(code, ...)` / `doAsset(path, ...)` — 执行 Lua 代码
  - `runFunc(name, ...)` / `call(name, ...)` — 调用 Lua 函数
  - `setContentView(LuaObject/LuaObject+LuaObject/String)` — 加载布局
  - `newTask/Thread/Timer` — 并发工厂
- **路径管理**：`getLuaDir/getLuaPath/getLuaExtDir/getLuaExtPath/loadDex/loadApp/loadLib`
- **UI 工具**：`showToast/sendMsg/showLogs/setTitle`
- **Intent 管理**：`newActivity/startService/bindService`
- 持有 `MainHandler` 用于主线程通信
- 静态 Map `H(HashMap<String, LuaActivity>)` 管理所有 Activity 实例

### 3.4 LuaActivityX
- 继承 `LuaActivity`，扩展功能（具体在编译代码中）

### 3.5 LuaService
- 继承 `android.app.Service`，实现 `LuaContext` 和 `BroadcastReceiver`
- 与 `LuaActivity` 类似的生命周期管理
- 支持绑定模式：`LuaBinder` / `onBind/unbind`
- 有 `MainHandler`、日志管理、Toast
- 静态 `getService()` 获取单例

### 3.6 LuaAccessibilityService
- 继承 `AccessibilityService`，实现无障碍服务
- 8 个内部类处理各种无障碍回调
- 接口 `AccessibilityServiceCallbacks` 供 Lua 调用

### 3.7 CrashHandler
- 实现 `Thread.UncaughtExceptionHandler`
- 单例模式
- `collectDeviceInfo(Context)` — 收集设备信息
- `uncaughtException(Thread, Throwable)` — 保存崩溃日志到文件

### 3.8 LuaDexLoader
- 管理 DEX 类加载器
- `loadDex/loadApp/loadLib` — 加载外部 DEX/库
- `getClassLoaders/getAssets/getResources/getTheme` — 资源访问
- 内建 `LuaDexClassLoader`（继承 `DexClassLoader`）
- 管理 `LuaAssetLoader`、`LuaResources`

### 3.9 LuaEnhancer
- 封装 `com.android.cglib.proxy.Enhancer` (DexMaker)
- 运行时创建 Java 类的代理子类
- `create()` / `create(MethodFilter)` — 创建增强类
- `setInterceptor(EnhancerInterface, MethodInterceptor)` — 设置方法拦截器

---

## 四、并发模块审计

### 4.1 LuaAsyncTask
- 继承 `AsyncTaskX`（自定义 AsyncTask），实现 `LuaGcable`
- 构造函数多态：接收 `LuaContext + (LuaObject/String) + LuaObject(callback/update)`
- 内部运行在独立 LuaState 中的后台任务
- `execute()` / `update(int/str/Object)` / `gc()` / `isGc()`

### 4.2 LuaThread
- 继承 `Thread`，实现 `Runnable + LuaMetaTable + LuaGcable`
- 构造函数：`LuaContext + (LuaObject/String) + boolean(独立state?) + Object[](args)`
- 内部创建独立 `LuaState` 运行 Lua 脚本
- 支持 `__call/__index/__newIndex` 元方法（从 Lua 侧调用 Java 方法）
- `call/doAsset/push/set/get/quit`

### 4.3 LuaRunnable
- 与 `LuaThread` 结构几乎相同（继承 `Thread`）
- 似乎是 LuaThread 的变体（或者早期版本）

### 4.4 LuaTimer
- 继承 `TimerX`（自定义 Timer），实现 `LuaGcable`
- 持有 `LuaTimerTask` 内部类
- `start(delay)` / `start(delay, period)` / `stop()` / `setEnabled`
- 支持 Lua 函数回调作为定时器任务

### 4.5 LuaTimerTask
- 定时任务封装（含 Lua 函数回调）

### 4.6 Ticker
- UI 主线程帧定时器
- `OnTickListener` 接口

### 4.7 AsyncTaskX (util 包)
- 自定义 AsyncTask 实现
- `Status` 枚举（PENDING/RUNNING/FINISHED）
- `SerialExecutor` / `InternalHandler` / `WorkerRunnable`

### 4.8 TimerX / TimerTaskX (util 包)
- 自定义 Timer 实现
- `TimerImpl` / `TimerHeap` / `FinalizerHelper`

### 4.9 ClickRunnable
- 点击防抖处理
- `ClickCallback` 接口

### 4.10 VolatileBox / VolatileDispose
- 易变值/资源包装器

---

## 五、编辑器模块审计

### 5.1 LuaEditor
- 继承自混淆类 `b.b.a.a.c`（代码编辑器视图基类，继承 `View`）
- 编辑器基类 `b.b.a.a.c` 是功能完整的代码编辑器控件：
  - 行号、语法高亮、自动换行、滚动
  - 剪切板管理、触摸手势、光标控制
  - Paint 渲染、行距、字体控制
- LuaEditor 在基类基础上增加：
  - Lua 语法高亮配置（关键字/注释/字符串/基础词颜色）
  - `open(String)` — 打开文件
  - `save()` / `save(String)` — 保存文件
  - `findNext(String)` / `search()` / `startFindMode()` — 搜索
  - `gotoLine()` / `gotoLine(int)` — 跳转
  - `undo()` / `redo()` — 撤销/重做
  - `insert(int, String)` — 插入文本
  - `addPackage/removePackage/addNames` — 自动补全包管理
  - `setWordWrap/setDark` — 视图配置

### 5.2 LuaLexer
- Lua 词法分析器（JFlex 风格）
- 构造函数接受 `Reader` 或 `CharSequence`
- 状态机：`YYINITIAL`, `xCOMMENT`, `xBLOCK_COMMENT`, `xBLOCK_STRING`, `xDOUBLE_QUOTED_STRING`, `xSINGLE_QUOTED_STRING`, `xSHEBANG`
- `advance()` 返回 `LuaTokenTypes`（下一个 Token）

### 5.3 LuaTokenTypes
- `Enum` 定义了 **82 种 Token 类型**
- 关键词：`AND/BREAK/CASE/CONTINUE/DO/ELSE/ELSEIF/END/FALSE/FOR/FUNCTION/GOTO/IF/IN/LOCAL/NIL/NOT/OR/REPEAT/RETURN/SWITCH/THEN/TRUE/UNTIL/WHEN/WHILE/LOCAL`
- 运算符：`PLUS/MINUS/MULT/DIV/MOD/EXP/CONCAT/LT/GT/LE/GE/EQ/NE/ASSIGN/BIT_AND/BIT_OR/BIT_TILDE/BIT_LTLT/BIT_RTRT/DOUBLE_DIV`
- Lua 特有：`ELLIPSIS/DOT/COLON/DOUBLE_COLON/SEMI/COMMA`
- 注释：`SHORT_COMMENT/BLOCK_COMMENT/DOC_COMMENT/SHEBANG/SHEBANG_CONTENT`
- 值：`NUMBER/STRING/NAME/NIL/TRUE/FALSE`

---

## 六、UI 组件审计

### 6.1 对话框系统

#### LuaDialog (已有源码)
- 继承 `AlertDialog`，实现 `DialogInterface.OnClickListener`
- 自定义 `ListView` 集成（列表项、单选、多选）
- `setButton/Button1/2/3/PosButton/NegButton/NeuButton/OkButton/CancelButton`
- `setItems/SingleChoiceItems/MultiChoiceItems`
- `setAdapter`
- 自定义 `OnClickListener` 回调（`onClick(LuaDialog, Button)`）

#### LuaMaterialDialog (已有源码)
- 继承 `LuaDialog`，Material3 风格
- 使用 `MaterialAlertDialogBuilder` 的样式
- MaterialShapeDrawable 背景、圆角、内边距

#### AlertControllerBridge (已有源码)
- 桥接类，访问 `AlertController.RecycleListView` 的内部 `setHasDecor` 方法

### 6.2 适配器

#### LuaAdapter
- 继承 `BaseAdapter`，实现 `Filterable`
- 基于 `LuaTable` 数据源（Lua table 驱动）
- `getView/getDropDownView` — 使用 Lua 函数渲染视图
- `add/addAll/clear/insert/remove/filter`
- `setAnimation/setAnimationUtil/setFilter/setStyle`
- 内部类：`ArrayFilter/AsyncLoader`

#### LuaArrayAdapter
- 数组适配器，带 `AsyncLoader`

#### LuaMultiAdapter
- 多布局适配器，带 `AsyncLoader`

#### LuaExpandableListAdapter
- 可展开列表适配器
- 内部类：`GroupItem/AsyncLoader`

#### LuaExAdapter
- 扩展适配器

### 6.3 其他 UI 类

#### LuaWebView
- 继承 `WebView`，实现 `LuaGcable`
- **16 个内部类**：`Download`, `DownloadBroadcastReceiver`, `InJavaScriptLocalObj`, `JsInterface`, `JsObject`, `LuaJavaScriptInterface`, `LuaWebChromeClient`(含6个匿名类), `LuaWebViewClient`, `OnDownloadCompleteListener`, `OnDownloadStartListener`, `OnReceivedIconListener`, `OnReceivedTitleListener`, `SimpleLuaWebViewClient`
- JS ↔ Lua 双向桥接
- `addJSInterface/JsInterface` — 注册 JS 接口
- `setCookie/getCookie`
- `openFile/download` — 文件下载管理
- 广告过滤支持

#### LuaCameraView
- 相机预览 View

#### LuaView
- View 封装

#### LuaDrawable
- 自定义 Drawable（函数驱动）

#### LuaBitmapDrawable
- 位图 Drawable

#### NineBitmapDrawable
- 9-patch Drawable

#### LoadingDrawable
- 加载动画 Drawable

#### LuaAnimation
- 动画封装

#### LuaBitmap
- 位图加载（本地/HTTP）

#### GifDecoder
- GIF 解码器
- `GifAction` 接口, `GifFrame` 数据类

#### LuaResources
- 自定义资源加载

#### LuaFragment
- Fragment 生命周期管理

#### LuaPreferenceFragment
- 偏好设置 Fragment

---

## 七、android.widget.* 自定义小部件审计（~60 个类）

> 注意：这些类**不是** Android SDK 标准类，而是 AndroLua+ 自建实现，放在 `android.widget` 和 `android.app` 包名下。

### 布局类
- **DrawerLayout** (`android.widget.DrawerLayout`) — 自定义抽屉布局，~15 内部类（含 `ViewDragCallback`, `SavedState`）
- **SlidingLayout** — 滑动侧边栏
- **PullingLayout** — 下拉刷新/上拉加载，~20 内部类（HeadView/FootView/动画 Drawable 等）
- **PageView** — 页面滑动容器，~10 内部类（`ItemInfo`, `PageObserver`, `ViewPositionComparator`）
- **PageLayout** — 页面布局
- **ViewDragHelper** — 拖拽辅助类

### 列表类
- **HorizontalListView** — 水平滚动列表，~6 内部类
- **ExListView** — 扩展列表

### 组件类
- **ToolBar** — 自定义工具栏，4 内部类
- **FloatButton** — 浮动按钮
- **FloatWindow** (`android.app.FloatWindow`) — 悬浮窗，~3 内部类
- **CardView** — 卡片视图，~4 内部类（兼容不同 API 级别）
- **CircleImageView** — 圆形图片
- **RippleLayout** / **RippleHelper** — 水波纹效果
- **RoundRectDrawable** / **RoundRectDrawableWithShadow** — 圆角矩形绘制

### 对话框
- **AlertDialogBuilder** (`android.app.AlertDialogBuilder`)

### 适配器
- **ArrayListAdapter** — 列表适配器
- **ArrayPageAdapter** / **BasePageAdapter** — 页面适配器
- **ArrayExpandableListAdapter** — 可展开列表适配器

### 文件
- **FileProvider** (`android.content.FileProvider`) — 文件 URL 共享

---

## 八、网络/IO 审计

### 8.1 Http
- 基于 `HttpURLConnection` 的 HTTP 请求（非 OkHttp）
- `get/post/put/delete/download` — 全部返回 `HttpTask`
- 每个方法有 5-10 个重载（参数组合：url/header/body/callback）
- 支持自定义 `Header/Cookie/UserAgent/Referer`
- 内部类 `HttpTask`（AsyncTask 子类）

### 8.2 Download
- 文件下载任务

### 8.3 LuaServer / LuaClient
- TCP 服务器/客户端
- 支持 `OnReadLineListener` 回调

### 8.4 观察者类
- **LuaBroadcastReceiver** — 广播接收器 (`OnReceiveListener` 接口)
- **LuaContentObserver** — 内容观察者 (`OnChangeListener` 接口)
- **LuaFileObserver** — 文件观察者 (`OnEventListener` 接口)

---

## 九、Lua 核心脚本审计

### 9.1 import.lua (505行) — 导入系统
- **核心机制**：Lua 的 `import` 函数、自动 Java 类绑定、包管理
- 搜索路径：`java.lang.*` → `java.util.*` → `com.androlua.*`
- `import "android.widget.*"` — 通配符包导入（元表驱动延迟加载）
- `import "android.widget.Button"` — 单类导入
- `import "dexname:com.example.Class"` — DEX 中的类导入
- `import_class` → `bindClass` → `LuaJavaAPI.javaBindClass`
- `import_dex_class` → `dex.loadClass` → DEX 类加载器
- 自动注册 `loadlayout/loadbitmap/loadmenu` 到全局环境
- 提供 `dump/printstack/thread/task/timer/each/enum/print` 全局函数
- Lua 全局环境设置 `__index` 元方法实现自动类绑定
- `luajava.xxx.yyy` — 链式属性访问（通过元表自动调用 `bindClass`）

### 9.2 loadlayout.lua (676行) — 布局系统
- **核心机制**：Lua 表 → Android View 的声明式布局
- 支持 `.aly` 布局文件加载（新的 searcher）
- 布局表格式：`{Class, id="name", layout_width="match", ...}`
- 属性处理器：
  - `layout_xxx` → `RelativeLayout.LayoutParams.addRule()`
  - `textSize/ellipsize/textAppearance` → View setter
  - `background/src` → 支持颜色/图片/URL/函数/LuaDrawable
  - `onClick` → View.OnClickListener（函数/字符串/对象）
  - `items/pages` → ListView/PageView 数据绑定
  - `style` → 样式表继承
  - `id` → 创建全局变量引用
- `checkNumber` — 字符串值处理（颜色#HEX、dp/sp/pt、百分比）
- `toint` 表 — 40+ Android 常量映射

### 9.3 bin.lua (408行) — APK 打包系统
- **核心机制**：从原始 APK 复制文件 + 替换 assets + 签名
- `console.build(path)` → Lua 源码编译为字节码
- 读取原始 APK 的 `ZipInputStream`，写入新 APK 的 `ZipOutputStream`
- 处理：assets/文件/lib/META-INF/AndroidManifest.xml
- 替换 Manifest 中的包名、版本号、权限
- 使用 `Signer.sign()` 签名
- 调用 `activity.installApk()` 安装

### 9.4 loadbitmap.lua (16行) — 图片加载
- 本地路径/HTTP URL → `LuaBitmap.getLocalBitmap/getHttpBitmap`

### 9.5 loadmenu.lua (43行) — 菜单加载
- Lua 表 → Android Menu 的声明式映射
- 支持 `MenuItem` / `SubMenu`

### 9.6 console.lua (87行) — Lua 编译器
- `build(path)` — 加载 Lua 文件并 `string.dump` 为字节码
- `format(Text)` — 自动缩进格式化
- 用于 APK 打包时的编译步骤

---

## 十、混淆包审计 (a/*, b/*, c/*)

### a.a.a.a.a
- 空类（无字段、无方法）— 可能是标记类或占位符

### b.b.a.* — 代码编辑器包
- `b.b.a.a.c` — 代码编辑器视图基类（`extends View`，~60+ 字段/方法）
  - 文本渲染、行号、滚动、触摸事件处理
  - 持有 `b.b.a.a.f`（文档模型）
  - 持有 `b.b.a.b.e` 回调接口
- `b.b.a.b.*` — 词法分析/语法高亮引擎（~20+ 类）
  - 包含语法高亮计算、词法状态管理
- 总结：这是 **CodeEditor** 核心，LuaEditor 继承自它

### b.b.a.a.c$* — 编辑器内部类
- 多种内部监听器、状态管理

### c.a.a.* — 其他混淆类（~35 个）
- 可能是序列化、代理或其他工具

---

## 十一、工具类审计

### com.androlua.util.*
- **AsyncTaskX** — 自定义 AsyncTask（Status 枚举、SerialExecutor、WorkerRunnable、InternalHandler）
- **TimerX** — 自定义 Timer（TimerImpl、TimerHeap、FinalizerHelper）
- **TimerTaskX** — Timer 任务
- **ClickRunnable** — 点击防抖
- **GlobalActionAutomator** — 全局操作自动化
- **RootUtil** — Root 权限
- **RSASecurity** — RSA 加密
- **ScreenMetrics** — 屏幕尺寸
- **VolatileBox/VolatileDispose** — 易变值包装
- **ZipUtil** — ZIP 压缩

### 其他工具类
- **LuaUtil** — 文件 MD5/SHA1、copyFile/copyDir/rmDir/zip/unzip、readAll、captureScreen
- **LuaStringUtil** — 字符串模式匹配（Lua 风格）
- **LuaPrint** — 继承 `JavaFunction`，实现 Lua `print()` 函数
- **LuaGcable** — GC 接口

---

## 十二、com.nirenr.* 审计

- **Color** / **ColorFinder** / **ColorPoint** / **Point** — 颜色和坐标工具
- **SplitEditView** — 分屏编辑视图（`EditDialog`, `OnSaveListener`）
- **ScreenCaptureActivity** (`screencapture`) — 截屏 Activity
- **ScreenCaptureListener** — 截屏监听
- **ScreenShot** — 截图实现（3 个内部类 + `SaveTask`）
- **FileUtil** — 文件工具

---

## 十三、AndroLua 核心原理总结

### 13.1 数据流

```
Lua 脚本
  ↓ require "import"
  ↓ import "android.widget.Button"  →  LuaJavaAPI.javaBindClass()
  ↓ activity/LuaObject.call()       →  LuaJavaAPI.callMethod()
  ↓ luaState.pushJavaObject()       →  JNI → C → lua_pushlightuserdata()
  ↓ luaState.toJavaObject()         →  JNI → C → lua_touserdata()
  ↓                                →  LuaJavaAPI 反射调度引擎
```

### 13.2 Lua ↔ Java 互操作

```
Java → Lua:
  LuaState.pushJavaObject(obj) → 注册到内部 ID Map → JNI 传指针
  LuaState.pcall()              → 执行 Lua 函数

Lua → Java:
  luajava.bindClass("android.widget.Button")
    → LuaJavaAPI.javaBindClass() → Class.forName() → 注册类型元表
  Button() → JavaFunction 调用 → 反射 Constructor.newInstance()
  button.setText("Hello")
    → LuaObject.__index → 方法查找 → LuaJavaAPI.callMethod()
    → 反射 Method.invoke()
```

### 13.3 import 系统原理

```lua
-- 通过 Lua 元表 __index 实现延迟绑定
local globalMT = {
    __index = function(T, classname)
        -- 依次尝试: loaded cache → bindClass → dex.loadClass
        local class = loaded[classname] or import_class(classname) or import_dex_class(classname)
        T[classname] = class  -- 缓存
        return class
    end
}
setmetatable(_G, globalMT)
-- 之后 Button = _G["Button"] 自动触发类绑定
```

### 13.4 loadlayout 原理

```lua
-- {LinearLayout, orientation="vertical", {TextView, text="Hello"}}
function loadlayout(t, root, group)
    local view = t[1](context)  -- 调用构造函数: LinearLayout(context)
    -- 遍历所有 key-value, 通过 setattribute() 处理
    for k, v in pairs(t) do
        if tonumber(k) then     -- 子 View
            view.addView(loadlayout(v, root, t[1]))
        elseif k == "id" then   -- 变量注册
            rawset(root, v, view)
        else                    -- 属性设置
            view["set"..k](checkValue(v))
        end
    end
end
```

### 13.5 并发模型

- **LuaThread**: 每个线程创建独立 LuaState，`LuaMetaTable.__call` 桥接到 Java 方法调用
- **LuaAsyncTask**: 基于 AsyncTaskX 的后台任务 + UI 更新回调
- **LuaTimer**: TimerX 封装的定时器，回调 Lua 函数
- 所有并发类都实现 `LuaGcable` 接口

### 13.6 构建系统

- **APK 打包**: 读取原 APK → 替换 assets（编译后的 Lua 字节码）→ 修改 Manifest → 签名 → 安装
- 使用 `console.build()` 编译 Lua → `string.dump` 字节码
- 使用 `mao` 和 `sign` 本地库进行 AXML 解析和 APK 签名

---

## 十四、关键风险点总结

### JNI 层（最高风险）
- `LuaJavaAPI` 中的所有 `static native` 方法必须在 Kotlin `companion object` 中正确映射
- `LuaState` 的 `_xxx(long, ...)` native 方法的 JNI 符号必须与 `.so` 完全匹配
- `LuaInvocationHandler` 的 Proxy 机制依赖 `java.lang.reflect.Proxy`（Kotlin 兼容）

### 代码编辑器（高风险）
- `LuaEditor` 继承自混淆类 `b.b.a.a.c` — 必须反向分析该基类的完整 API
- `b.b.a.b.*` 语法高亮引擎 — 词法分析状态机逻辑复杂
- `LuaLexer` 是 JFlex 生成的 Tokenizer — 保持行为等价需要精确翻译

### 自定义小部件（中风险）
- `android.widget.*` 中的自定义类与 SDK 标准类同名 — 翻译时要注意命名冲突
- `PullingLayout`（~20 个内部类）、`PageView`（~10 个内部类）结构复杂

### 并发（中风险）
- `LuaThread` / `LuaRunnable` 各自管理独立 LuaState — 需要注意状态隔离
- `LuaAsyncTask` 与 `AsyncTaskX` 的关系 — 需要理解自定义 AsyncTask 的实现
- `TimerX` / `TimerTaskX` 替代标准 `java.util.Timer` — 行为差异需测试

### 构建系统（中风险）
- `bin.lua` 依赖 `mao` 和 `sign` 本地库 — 需确认 Kotlin 下的 AXML 解析和签名方案
- 签名流程中的 `Signer.sign()` — 可能是 Java 实现或 JNI 调用

### 测试策略
- 需要为每个模块编写行为等价性测试
- Lua 测试套件：`test_import/test_loadlayout/test_thread/test_http/test_luajava_api`
- 关键验证：启动时间、Lua 执行效率、APK 打包功能
