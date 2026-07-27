# delua 项目：工程规划与实施白皮书

> 基于 AndroLuaX v3.4(5.0.18)(Material3) 的全量 Kotlin 迁移方案
>
> 文档版本：v1.0
> 最后更新：2026-07-27

---

## 目录

1. [执行摘要](#1-执行摘要)
2. [架构总览与模块划分](#2-架构总览与模块划分)
3. [里程碑与工作分解](#3-里程碑与工作分解)
4. [模块级翻译清单](#4-模块级翻译清单)
5. [技术风险与缓解策略](#5-技术风险与缓解策略)
6. [JNI / 原生层处理规范](#6-jni--原生层处理规范)
7. [代码质量与风格指南](#7-代码质量与风格指南)
8. [测试策略](#8-测试策略)
9. [构建系统迁移](#9-构建系统迁移)
10. [验收标准](#10-验收标准)
11. [附录：类映射表](#11-附录类映射表)

---

## 1. 执行摘要

### 1.1 项目目标

将 AndroLuaX 项目从 Java + Lua 混合架构迁移为 **Kotlin 原生宿主 + Lua 运行时** 架构。具体要求：

1. **Java → Kotlin 全量翻译**：宿主应用全部 Kotlin 实现，零 Java 源文件残留
2. **宿主原生化**：IDE/编辑器/UI/构建系统等宿主代码全部以 Kotlin 原生运行
3. **Lua 业务层不变**：仅被宿主构建打包的 APK 内部以 Lua 为主力语言

### 1.2 范围

| 维度 | 范围 |
|------|------|
| Java 源文件翻译 | 3 个现有 `.java` 文件 → `.kt` |
| JAR 反编译翻译 | `AndroLua+5.0.18_AndroidX.jar`（961 个 class 文件）中的业务逻辑 |
| Lua 源文件 | **保持不变**（约 25 个核心 Lua 模块 + 用户脚本） |
| 原生 `.so` 库 | **保持不变**（`libluajava.so` 等 C/C++ 实现） |
| 第三方 Java 依赖 | 选择性保留或替换（BeanShell、DexMaker、百度统计） |
| Android 资源 | 保持不变（XML 布局、主题、字符串等） |

### 1.3 核心原则

- **行为等价**：翻译后的 Kotlin 代码必须产生与原始 Java 完全相同的运行时行为
- **渐进迁移**：按模块优先级逐步翻译，每个阶段均可编译和测试
- **Lua 零侵入**：不修改任何 `.lua` 源文件，保持完整的 Lua API 兼容性
- **JNI 桥接透明**：确保 Kotlin ↔ JNI ↔ C(Lua VM) 调用链畅通无阻

---

## 2. 架构总览与模块划分

### 2.1 系统架构（三层模型）

```
┌─────────────────────────────────────────────────────┐
│                   delua 宿主应用                      │
│  (Kotlin: Main/Welecome/LuaEditor/LuaDialog/...)     │
├─────────────────────────────────────────────────────┤
│               Lua 运行时引擎层                         │
│  (Kotlin: LuaActivity/LuaService/LuaApplication/...)  │
├─────────────────────────────────────────────────────┤
│            Lua-Java 绑定层 (com.luajava.*)            │
│  (Kotlin JNI 桥 → libluajava.so → Lua C VM)          │
├─────────────────────────────────────────────────────┤
│              原生层 (C/C++, 不变)                     │
│  libluajava.so | liblua.so | libcjson.so | ...        │
└─────────────────────────────────────────────────────┘
```

### 2.2 模块划分

#### P0 - 关键路径（必须优先翻译，阻塞所有下游）

| 模块 | JAR 类 | 说明 |
|------|--------|------|
| **LuaJava 绑定** | `com.luajava.*` | JNI 桥接层，所有 Lua 调用的入口 |
| **Lua 运行时** | `LuaActivity`, `LuaActivityX`, `LuaApplication`, `LuaService`, `LuaContext` | 应用生命周期与 Lua 环境管理 |
| **崩溃处理** | `CrashHandler` | 全局异常捕获，必须第一个集成 |

#### P1 - 宿主核心功能（IDE/编辑器主界面）

| 模块 | JAR 类 | 说明 |
|------|--------|------|
| **主 Activity** | `Main`, `Welcome` | 应用入口与启动屏 |
| **代码编辑器** | `LuaEditor`, `LuaLexer`, `LuaTokenTypes` | 语法高亮、自动补全、代码编辑 |
| **辅助服务** | `LuaAccessibilityService` | 无障碍功能 |
| **对话框** | `LuaDialog` (已有源文件), `LuaMaterialDialog` (已有源文件) | UI 对话框 |

#### P1 - 工具与适配器层

| 模块 | JAR 类 | 说明 |
|------|--------|------|
| **工具库** | `LuaUtil`, `ZipUtil`, `LuaStringUtil`, `LuaPrint` | 通用工具函数 |
| **适配器** | `LuaAdapter`, `LuaArrayAdapter`, `LuaMultiAdapter`, `LuaExpandableListAdapter` | ListView/RecyclerView 数据适配器 |
| **并发基元** | `LuaAsyncTask`, `LuaThread`, `LuaTimer`, `LuaTimerTask`, `LuaRunnable`, `Ticker` | 线程与定时器 |
| **并发工具** | `AsyncTaskX`, `TimerX`, `TimerTaskX`, `ClickRunnable` | 并发框架扩展 |
| **Dex 加载** | `LuaDexLoader`, `LuaDexClassLoader`, `LuaAssetLoader`, `LuaEnhancer` | 动态类加载与代理 |
| **内置类** | `LuaGcable`, `LuaView` | GC 管理与 View 封装 |

#### P1 - 网络与 I/O

| 模块 | JAR 类 | 说明 |
|------|--------|------|
| **HTTP** | `Http`, `Download` | HTTP 请求与文件下载 |
| **Socket** | `LuaServer`, `LuaClient` | TCP 网络服务 |
| **系统观察者** | `LuaBroadcastReceiver`, `LuaContentObserver`, `LuaFileObserver` | 系统事件监听 |
| **实用工具** | `GlobalActionAutomator`, `RootUtil`, `RSASecurity`, `ScreenMetrics`, `VolatileBox`, `VolatileDispose` | 自动化、安全、屏幕工具 |

#### P2 - 自定义小部件（`android.widget.*`、`android.app.*` 等）

| 模块 | 类 | 说明 |
|------|-----|------|
| **布局** | `DrawerLayout`, `SlidingLayout`, `PullingLayout` | 抽屉/滑动/下拉刷新布局 |
| **列表** | `HorizontalListView`, `ExListView`, `PageView`, `PageLayout` | 水平列表、扩展列表、分页视图 |
| **UI 组件** | `ToolBar`, `FloatButton`, `FloatWindow`, `CardView`, `CircleImageView`, `RippleLayout`, `RippleHelper` | 工具栏、浮动按钮、窗口、卡片等 |
| **对话框** | `AlertDialogBuilder` | 对话框构建器 |
| **适配器** | `ArrayListAdapter`, `ArrayPageAdapter`, `ArrayExpandableListAdapter`, `BasePageAdapter` | 数据适配器 |
| **文件提供者** | `FileProvider` | 文件 URL 共享 |

#### P2 - 图形与绘制

| 模块 | JAR 类 | 说明 |
|------|--------|------|
| **可绘制对象** | `LuaDrawable`, `LuaBitmapDrawable`, `NineBitmapDrawable`, `LoadingDrawable` | 自定义绘制 |
| **位图** | `LuaBitmap`, `GifDecoder` | 图片加载与 GIF 解码 |
| **动画** | `LuaAnimation` | 动画支持 |

#### P2 - Fragment 与偏好设置

| 模块 | JAR 类 | 说明 |
|------|--------|------|
| **Fragment** | `LuaFragment`, `LuaPreferenceFragment` | Fragment 生命周期管理 |
| **资源** | `LuaResources` | 自定义资源加载 |

#### P2 - WebView

| 模块 | JAR 类 | 说明 |
|------|--------|------|
| **WebView** | `LuaWebView` (+ 15 个内部类) | WebView + JS ↔ Lua 双向桥接 |

#### P3 - 可选/可替换的第三方依赖

| 依赖 | 说明 | 处置策略 |
|------|------|----------|
| **BeanShell** (`bsh.*`) | Java 脚本引擎 | 保留为 Java 依赖；或替换为 `kotlin.script` |
| **DexMaker** (`com.android.cglib.*`) | 运行时字节码生成 | 保留为 Java 依赖（JVM 级，无需翻译） |
| **百度统计** (`com.baidu.mobstat.*`) | 统计分析 SDK | 移除或替换为 Firebase/自建统计 |
| **混淆类** (`a/a/*`, `b/a/*`, `c/a/*`) | 未识别依赖 | 反编译分析后决定 |

---

## 3. 里程碑与工作分解

### Phase 0：基础架构搭建（预估 2 周）

```
[ ] 反编译 JAR → Java 源码（使用 CFR / Jadx）
[ ] 建立 Kotlin 项目骨架（Gradle KTS）
[ ] 配置 Kotlin 编译器插件（kotlin-parcelize, kotlinx-serialization 等）
[ ] 移植 LuaJava 绑定层（com.luajava.*）
    - LuaState / LuaStateFactory → Kotlin JNI 声明
    - LuaObject / LuaTable / LuaList / LuaStack → Kotlin 数据类
    - LuaJavaAPI → Kotlin external 函数声明
    - LuaInvocationHandler / LuaMethodInterceptor → Kotlin 代理
[ ] 集成原生 .so 库（保持不变）
[ ] 验证 JNI 调用链：Kotlin → LuaJavaAPI → libluajava.so → Lua C VM
[ ] 建立 CI 构建流水线（GitHub Actions）
```

### Phase 1：运行时引擎翻译（预估 4 周）

```
[ ] LuaContext（接口）→ Kotlin interface
[ ] LuaApplication → Kotlin Application 子类
[ ] LuaActivity / LuaActivityX → Kotlin Activity 子类
[ ] LuaService → Kotlin Service 子类
[ ] LuaAccessibilityService → Kotlin AccessibilityService 子类
[ ] CrashHandler → Kotlin 全局异常处理
[ ] Main（宿主主 Activity）→ Kotlin
[ ] Welcome（启动屏）→ Kotlin
[ ] LuaAsyncTask / LuaThread / LuaTimer / LuaRunnable / Ticker → Kotlin 协程桥接
[ ] LuaDexLoader / LuaDexClassLoader / LuaEnhancer → Kotlin
[ ] LuaAssetLoader → Kotlin
```

### Phase 2：宿主 IDE 与 UI 翻译（预估 4 周）

```
[ ] LuaEditor（代码编辑器）→ Kotlin
[ ] LuaLexer / LuaTokenTypes（语法解析器）→ Kotlin
[ ] LuaDialog / AlertControllerBridge → Kotlin（已有源文件）
[ ] LuaMaterialDialog → Kotlin（已有源文件）
[ ] LuaAdapter / LuaArrayAdapter / LuaMultiAdapter / LuaExpandableListAdapter → Kotlin
[ ] LuaDrawable / LuaBitmapDrawable / NineBitmapDrawable / LoadingDrawable → Kotlin
[ ] LuaAnimation → Kotlin
[ ] LuaFragment / LuaPreferenceFragment → Kotlin
[ ] LuaResources → Kotlin
```

### Phase 3：工具与网络翻译（预估 3 周）

```
[ ] LuaUtil / ZipUtil / LuaStringUtil / LuaPrint → Kotlin
[ ] Http / Download → Kotlin（建议改用 OkHttp/Ktor）
[ ] LuaServer / LuaClient → Kotlin
[ ] LuaBroadcastReceiver / LuaContentObserver / LuaFileObserver → Kotlin
[ ] LuaBitmap / GifDecoder → Kotlin
[ ] GlobalActionAutomator / RootUtil / RSASecurity / ScreenMetrics → Kotlin
[ ] VolatileBox / VolatileDispose → Kotlin
[ ] AsyncTaskX / TimerX / TimerTaskX / ClickRunnable → Kotlin
```

### Phase 4：自定义小部件翻译（预估 4 周）

```
[ ] AlertDialogBuilder → Kotlin
[ ] FloatWindow → Kotlin
[ ] ArrayListAdapter / ArrayPageAdapter / ArrayExpandableListAdapter → Kotlin
[ ] CardView / CircleImageView → Kotlin
[ ] DrawerLayout / SlidingLayout / PullingLayout → Kotlin
[ ] ExListView / HorizontalListView / PageView / PageLayout → Kotlin
[ ] FloatButton / ToolBar → Kotlin
[ ] RippleLayout / RippleHelper → Kotlin
[ ] FileProvider → Kotlin
[ ] LuaWebView（+15 内部类）→ Kotlin
[ ] LuaView / LuaCameraView → Kotlin
[ ] 混淆类分析（a/a/*, b/a/*, c/a/*）
```

### Phase 5：集成测试与发布（预估 3 周）

```
[ ] 全模块集成编译
[ ] 功能等价性测试（对照 AndroLuaX 原始 APK）
[ ] Lua API 兼容性测试
[ ] APK 构建/打包流水线测试
[ ] 性能基准测试（启动速度、内存占用、Lua 执行效率）
[ ] 回退测试（确保卸载 delua 可重新安装 AndroLuaX 不受影响）
[ ] 发布 v0.1 alpha
```

---

## 4. 模块级翻译清单

### 4.1 Lua-Java 绑定层（`com.luajava.*`）- 最高风险模块

这是整个系统的基石。Kotlin 通过 JNI 调用 C 语言实现的 Lua VM。

#### 类清单与翻译要点

| Java 类 | Kotlin 映射 | 关键注意事项 |
|---------|-------------|-------------|
| `LuaState` | `LuaState.kt` | JNI `external` 函数声明；所有 native 方法保留 `external` 修饰符 |
| `LuaStateFactory` | `LuaStateFactory.kt` | 工厂方法；与 `LuaState` 配合创建 Lua VM 实例 |
| `LuaObject` | `LuaObject.kt` | 封装 Lua 栈上的引用；注意 Kotlin `Any?` 与 Java `Object` 的差异 |
| `LuaTable` | `LuaTable.kt` | 实现 `Map<Any?, Any?>` 接口；迭代器转 Kotlin 序列 |
| `LuaList` | `LuaList.kt` | 实现 `List<Any?>` 接口 |
| `LuaStack` | `LuaStack.kt` | Lua 栈操作封装 |
| `LuaString` | `LuaString.kt` | 字节数组 vs 字符串的转换 |
| `LuaJavaAPI` | `LuaJavaAPI.kt` | **核心 JNI 入口**；所有 `native` 方法必须在 `companion object` 中声明 |
| `JavaFunction` | `JavaFunction.kt` | Kotlin 函数 → Lua 函数适配器 |
| `LuaFunction` | `LuaFunction.kt` | Lua 函数 → Kotlin 调用包装 |
| `LuaInvocationHandler` | `LuaInvocationHandler.kt` | `InvocationHandler` 实现；Kotlin 代理对象 |
| `LuaMethodInterceptor` | `LuaMethodInterceptor.kt` | 方法拦截器（虚类支持） |
| `LuaAbstractMethodInterceptor` | `LuaAbstractMethodInterceptor.kt` | 抽象方法拦截器 |
| `LuaMetaTable` | `LuaMetaTable.kt` | Lua 元表桥接 |
| `LuaException` | `LuaException.kt` | 异常类（简单移植） |
| `Console` | `Console.kt` | Lua 控制台 I/O |

#### JNI 签名映射示例

```kotlin
// Java 原始代码
class LuaJavaAPI {
    public static native LuaState createLuaState();
    public static native void pushJavaObject(LuaState L, Object obj);
    public static native Object toJavaObject(LuaState L, int idx);
}

// Kotlin 翻译
class LuaJavaAPI {
    companion object {
        @JvmStatic external fun createLuaState(): LuaState
        @JvmStatic external fun pushJavaObject(L: LuaState, obj: Any?)
        @JvmStatic external fun toJavaObject(L: LuaState, idx: Int): Any?
    }
}
```

#### 风险点

1. **`external` 函数必须与 `.so` 中的 JNI 导出符号精确匹配**。Kotlin 默认生成不同的符号名称（`Java_...` 路径不同），必须精确控制包名和类名。
2. **对象引用管理**：`LuaState` 等 JNI 全局引用需使用 `GC健壮引用` 或手动管理，避免泄漏。
3. **`LuaInvocationHandler`** 使用 `java.lang.reflect.Proxy`，这在 Kotlin 中仍然可用，不需要替换。

### 4.2 运行时引擎（`com.androlua.*` 核心类）

#### LuaActivity

这是用户 Lua 代码的执行容器。它是 `Activity` 的子类，维护 Lua 状态机。

```
关键方法翻译范围：
- onCreate() → Lua 环境初始化
- onResume()/onPause() → Lua 生命周期回调
- runLua() → 加载并执行 Lua 脚本
- sendMsg() → Lua print 输出（通过 Handler 到 UI 线程）
- loadDex() → 动态加载 DEX
- getLuaDir()/setLuaDir() → Lua 工作目录管理
```

**Kotlin 注意事项**：
- `Handler` → 使用 `Handler(Looper.getMainLooper())` 或 Kotlin `CoroutineScope(Dispatchers.Main)`
- `onActivityResult` → 迁移到 `ActivityResultContracts`（AndroidX Activity 1.8+）
- 匿名内部类 → Kotlin lambda/SAM 转换

#### LuaEditor

这是 AndroLuaX 的核心 IDE 组件，包含代码高亮、自动缩进、自动补全等功能。

```
关键子组件：
- LuaLexer：词法分析器（Lua 关键字/运算符/字符串标记化）
- LuaTokenTypes：Token 类型定义（接口/常量）
- 行号显示、滚动同步、搜索替换
- 语法错误标记、自动补全弹窗

翻译难度：★★★★★（高）
```

**Kotlin 注意事项**：
- `LuaLexer` 内部使用 `CharSeqReader`（自定义 Reader）→ 可改用 Kotlin `CharSequence` 遍历
- `LuaTokenTypes` 是接口 + 常量 → Kotlin `interface` + `companion object const`
- 大量 `ArrayList` 和数组操作 → Kotlin `List`/`MutableList` + 集合操作符

#### LuaWebView

WebView + JavaScript ↔ Lua 双向通信桥。

```
关键子组件：
- LnJavaScriptLocalObj：JS 回调本地对象
- JsInterface / JsObject：JS 接口暴露
- LuaWebChromeClient：自定义 Chrome 客户端
- LuaWebViewClient：页面加载拦截
- Download：下载管理
- 6 个内部类：各种回调监听器

翻译难度：★★★★☆（高）
```

**Kotlin 注意事项**：
- `@JavascriptInterface` 仍可使用
- `WebViewClient`/`WebChromeClient` 方法覆盖 → Kotlin `override`
- `DownloadBroadcastReceiver` → Kotlin `BroadcastReceiver` 对象表达式

### 4.3 自定义小部件（`android.widget.*`）

这些类**不是**标准的 Android SDK 类，而是 AndroLua+ 在 `android.widget` 包名下自行实现的 Widget。

#### 翻译策略

这些类大多不涉及 JNI，是纯 Java UI 逻辑，翻译难度相对较低。

| 类 | 说明 | Kotlin 优势 |
|----|------|------------|
| `DrawerLayout` | 自定义抽屉布局 | 可改用 AndroidX `DrawerLayout`+ Kotlin 扩展 |
| `PullingLayout` | 下拉刷新/上拉加载 | 可改用 `SwipeRefreshLayout` + Kotlin 协程 |
| `HorizontalListView` | 水平滚动列表 | 可改用 `RecyclerView` + `LinearLayoutManager(HORIZONTAL)` |
| `PageView` | 左右滑动页面 | 可改用 `ViewPager2` |
| `ToolBar` | 自定义工具栏 | 可改用 Material `Toolbar` + Kotlin DSL |
| `RippleLayout` | 水波纹效果 | 可改用 `RippleDrawable` + Kotlin 扩展 |

**决策点**：对于 Widget 类，有两种策略：
1. **精确翻译**：保持包名 `android.widget.*`，行为完全一致（推荐初期采用）
2. **现代化重构**：迁移到 AndroidX/Material 标准组件（推荐后期采用）

### 4.4 工具与网络类

| Java 类 | 建议的 Kotlin 实现 |
|---------|-------------------|
| `LuaUtil` | Kotlin 顶层扩展函数 + `object` 工具类 |
| `ZipUtil` | Kotlin 扩展 + `java.util.zip.ZipFile`/`ZipOutputStream` |
| `LuaStringUtil` | Kotlin `String` 扩展函数 |
| `Http` | **强烈建议** 改用 OkHttp/Ktor Client，保持 API 兼容包装 |
| `Download` | 改用 `DownloadManager` 或 OkHttp 流式下载 |

---

## 5. 技术风险与缓解策略

### 风险矩阵

| # | 风险 | 级别 | 影响 | 缓解策略 |
|---|------|------|------|----------|
| R1 | **JNI 符号不匹配** | **关键** | Lua VM 完全不可用 | 在 CI 中添加 JNI 符号验证；编写 JNI 测试套件 |
| R2 | **LuaInvocationHandler 行为差异** | **高** | 虚拟类/接口回调失效 | 编写专用测试用例覆盖 `luajava.override` 场景 |
| R3 | **DexMaker/Enhancer 兼容性** | **高** | 运行时类生成失败 | 保持 `com.android.cglib.*` 为 Java 依赖不翻译 |
| R4 | **LuaEditor 词法分析行为漂移** | **高** | 语法高亮/自动补全异常 | 对 LuaLexer 编写基于快照的黄金测试 |
| R5 | **Kotlin 空安全导致 NPE 位置变化** | **中** | 运行时崩溃 | 严格使用 `?=` 和 `?:`，翻译时标注 `@Nullable`/`@NonNull` |
| R6 | **泛型类型擦除差异** | **中** | 运行时类型转换异常 | `LuaAdapter` 等类使用 `reified` 类型参数或 `inline` 函数 |
| R7 | **协程替换 AsyncTask 时序问题** | **中** | 异步任务执行顺序不一致 | `LuaAsyncTask` 保持 API 兼容，内部用 `CoroutineScope` 实现 |
| R8 | **匿名内部类 → Lambda 行为差异** | **低** | `this` 引用/序列化异常 | 在需要引用自身的场景使用匿名对象而非 lambda |
| R9 | **资源 ID / R 类差异** | **低** | 布局/资源引用失败 | 使用 `compileSdk 34`、对齐 resource 命名 |
| R10 | **百度和混淆类分析** | **低** | 未知功能缺失 | 反编译后逐个分析，无法识别时逐步调试定位 |

### R1 详细缓解方案：JNI 符号验证

```kotlin
// 在 CI 中运行以下验证
// 1. 检查 .so 导出的 JNI 函数符号
// $ nm -D libluajava.so | grep Java_com_androlua_
// 2. 对比 Kotlin 编译后的 JNI 符号
// $ javap -s -p com/androlua/LuaJavaAPIKt.class
// 3. 确保完全匹配，尤其是包名中的下划线转义
```

### R2 详细缓解方案：LuaInvocationHandler 测试

```kotlin
// 必须通过以下场景测试：
// 1. luajava.override(interface, impl) → 代理对象调用
// 2. Lua 表自动转换为接口
// 3. 带构造参数的虚类
// 4. 接口默认方法（Java 8+）
// 5. Kotlin 函数类型 → Lua 函数回调
```

---

## 6. JNI / 原生层处理规范

### 6.1 不可变资产

以下文件**不得**以任何方式修改或翻译：

```
androlua/src/main/jniLibs/
├── armeabi-v7a/
│   ├── libluajava.so          # Lua VM + JNI 桥（C 语言）
│   └── ... (其他 .so)
└── arm64-v8a/
    ├── libluajava.so
    └── ... (其他 .so)
```

### 6.2 Kotlin JNI 声明规范

```kotlin
// 规则 1：所有 native 方法放在 companion object 中
// 规则 2：保持 @JvmStatic 注解以确保 JNI 符号正确
// 规则 3：不要使用 Kotlin 默认参数（会生成重载，改变 JNI 符号）
// 规则 4：包名必须与 .so 编译时一致（com.androlua 或 com.luajava）

class LuaJavaAPI {
    companion object {
        @JvmStatic
        external fun createLuaState(): LuaState

        @JvmStatic
        external fun pushJavaObject(L: LuaState, obj: Any?)

        @JvmStatic
        external fun toJavaObject(L: LuaState, idx: Int): Any?
    }
}
```

### 6.3 全局引用管理

```kotlin
// LuaState 持有 JNI 全局引用，需要显式释放
// Java 版依赖 finalize()，Kotlin 应改用：fun close() / AutoCloseable / Cleaner

class LuaState private constructor(private var nativePtr: Long) : AutoCloseable {
    private val cleaner = Cleaner.create()

    init {
        cleaner.register(this, NativeStateCleaner(nativePtr))
    }

    override fun close() {
        nativePtr = 0
        // 触发 Cleaner 释放 JNI 全局引用
    }

    private class NativeStateCleaner(private val ptr: Long) : Runnable {
        override fun run() {
            if (ptr != 0L) {
                destroyLuaState(ptr)
            }
        }
        private external fun destroyLuaState(ptr: Long)
    }
}
```

---

## 7. 代码质量与风格指南

### 7.1 代码风格

遵循 [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)，附加 delua 项目特有规则：

```kotlin
// 1. 包名：保持与原始 Java 一致（避免破坏 JNI 符号）
package com.androlua  // 保持原有包名

// 2. 可见性：对 Lua 通过反射调用的 API 保持 public
class LuaActivity : Activity() {
    fun getLuaDir(): String = luaDir  // 被 Lua require 调用，必须 public
}

// 3. 命名：Java getter/setter → Kotlin 属性
// Java: getLuaDir() / setLuaDir(String)
// Kotlin: var luaDir: String

// 4. 伴生对象中的常量：const val
companion object {
    const val MAX_THREADS = 1024
}

// 5. 接口 SAM 转换：明确标注 fun interface 以便 Lua 隐式转换
fun interface OnLuaCallback {
    fun onResult(vararg args: Any?)
}
```

### 7.2 文件组织

```
com/androlua/
├── runtime/                    # P0: 运行时引擎
│   ├── LuaApplication.kt
│   ├── LuaActivity.kt
│   ├── LuaActivityX.kt
│   ├── LuaService.kt
│   ├── LuaAccessibilityService.kt
│   ├── LuaContext.kt (interface)
│   └── LuaFragment.kt
├── bindings/                   # P0: LuaJava 绑定
│   ├── LuaState.kt
│   ├── LuaStateFactory.kt
│   ├── LuaObject.kt
│   ├── LuaTable.kt
│   ├── LuaJavaAPI.kt
│   └── ...
├── editor/                     # P1: 编辑器
│   ├── LuaEditor.kt
│   ├── LuaLexer.kt
│   └── LuaTokenTypes.kt
├── ui/                         # P1-P2: UI 组件
│   ├── Main.kt
│   ├── Welcome.kt
│   ├── LuaDialog.kt
│   ├── LuaMaterialDialog.kt
│   └── widgets/                # 自定义小部件
├── adapter/                    # P1: 适配器
│   ├── LuaAdapter.kt
│   ├── LuaArrayAdapter.kt
│   └── ...
├── concurrent/                 # P1: 并发
│   ├── LuaAsyncTask.kt
│   ├── LuaThread.kt
│   └── ...
├── net/                        # P2: 网络
│   ├── Http.kt
│   ├── Download.kt
│   ├── LuaServer.kt
│   └── LuaClient.kt
├── util/                       # P1: 工具
│   ├── LuaUtil.kt
│   ├── ZipUtil.kt
│   └── ...
└── drawable/                   # P2: 绘制
    ├── LuaDrawable.kt
    ├── LuaBitmapDrawable.kt
    └── ...
```

### 7.3 审查清单

每个 Pull Request 必须通过以下检查：

```
[ ] 与原始 Java 行为等价（通过测试验证）
[ ] 无可见的 API 破坏（Lua 调用方不受影响）
[ ] 无 JNI 符号破坏
[ ] Kotlin 编译器无警告
[ ] detekt / ktlint 通过
[ ] 代码覆盖率不低于原始 Java 版本
[ ] 无硬编码中文（字符串统一放在 strings.xml）
[ ] 无已废弃的 API 调用（如 AsyncTask、Deprecated 标记类）
```

---

## 8. 测试策略

### 8.1 测试金字塔

```
         /\
        /  \          E2E: 安装APK → 打开编辑器 → 编写Lua → 构建APK
       /    \
      /      \        Integration: Activity生命周期 + Lua VM 交互
     /        \
    /          \      Unit: 每个 Kotlin 类与原始 Java 行为对比
   /____________\
```

### 8.2 测试分类

| 测试类型 | 框架 | 覆盖范围 |
|----------|------|----------|
| **单元测试** | JUnit 5 + Kotest | 工具类、LuaLexer、数据适配器 |
| **JNI 测试** | JUnit 5 + 真实 `.so` | LuaJavaAPI、LuaState |
| **集成测试** | AndroidX Test + Robolectric | Activity/Service/Fragment 生命周期 |
| **UI 测试** | Compose UI Test / Espresso | 编辑器交互、对话框 |
| **Lua 兼容性测试** | Lua 脚本 + 断言 | `import`、`loadlayout`、`luajava.override` |
| **E2E 测试** | Firebase Test Lab | 完整的打包→安装→运行流程 |

### 8.3 Lua 兼容性测试套件

```
lua-test/
├── test_import.lua            # import 系统测试
├── test_loadlayout.lua        # 布局表测试
├── test_luajava_override.lua  # 虚类/接口覆盖测试
├── test_thread.lua            # 多线程测试
├── test_http.lua              # HTTP 请求测试
├── test_luajava_api.lua       # LuaJava API 全覆盖
└── test_performance.lua       # 性能基准
```

### 8.4 回归测试流程

```
1. 在 AndroLuaX（原始 Java）上运行 Lua 测试套件 → 记录结果
2. 在 delua（Kotlin）上运行相同测试套件 → 对比结果
3. 差异必须为零
4. CI 中自动执行此对比
```

---

## 9. 构建系统迁移

### 9.1 Gradle 迁移

```groovy
// build.gradle (Project) → build.gradle.kts
plugins {
    id("com.android.application") version "8.1.1" apply false
    kotlin("android") version "1.9.22" apply false  // 新增
}

// app/build.gradle → app/build.gradle.kts
plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.parcelize")  // 可选
}

android {
    namespace = "com.jesse205.androlua.androidX.material3"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jesse205.androlua.androidX.material3"
        minSdk = 21
        targetSdk = 29
    }

    buildFeatures {
        compose = false  // 如使用 Compose 则设为 true
        prefab = true
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    // AndroidX 依赖（从 libs.versions.toml 引用）
}
```

### 9.2 Kotlin 编译器配置

```kotlin
// gradle.properties
kotlin.code.style=official
kotlin.incremental=true
kotlin.daemon.jvmargs=-Xmx2048m

// 模块级 build.gradle.kts
kotlinOptions {
    jvmTarget = "1.8"
    freeCompilerArgs = listOf(
        "-Xjvm-default=all",           // 允许接口默认方法
        "-opt-in=kotlin.RequiresOptIn" // 可选实验性 API
    )
}
```

### 9.3 版本目录（libs.versions.toml）

```toml
[versions]
kotlin = "1.9.22"
agp = "8.1.1"

[libraries]
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
kotlin-reflect = { module = "org.jetbrains.kotlin:kotlin-reflect", version.ref = "kotlin" }

[plugins]
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
android-application = { id = "com.android.application", version.ref = "agp" }
```

---

## 10. 验收标准

### 10.1 功能等价性检查清单

```
[ ] 宿主应用正常启动（Welcome → Main）
[ ] 新建/打开/编辑 Lua 工程
[ ] 语法高亮与自动缩进正确
[ ] 下划线`import` 系统正常工作（含包自动解析）
[ ] `loadlayout` 布局表正确解析
[ ] `loadbitmap` 图片加载正常
[ ] `thread` / `task` / `timer` 并发 API 正常
[ ] `luajava.override` 虚类接口正常工作
[ ] 打包 APK 功能正常（运行 bin.lua）
[ ] WebView + JS ↔ Lua 桥接正常
[ ] 无障碍服务正常
[ ] 对话框（LuaDialog / LuaMaterialDialog）正常
[ ] HTTP 请求与文件下载正常
[ ] 所有原生 Lua C 模块（cjson、zlib、socket 等）正常加载
[ ] 构建的 APK 安装后正常运行
```

### 10.2 性能基准

```
指标                    AndroLuaX(Java)   delua(Kotlin)  允许偏差
应用程序启动时间            < 2.0s           < 2.0s         ±10%
Lua 脚本加载时间 (1MB)      < 500ms          < 500ms        ±15%
代码编辑器打开延迟           < 200ms          < 200ms        ±10%
APK 构建时间               < 60s            < 60s          ±20%
APK 大小                  ~8MB             ~8MB           +1MB max
内存占用（空闲）            ~50MB            ~50MB          ±15%
```

### 10.3 代码质量指标

```
[ ] Kotlin 编译器零警告
[ ] detekt 通过（默认规则集）
[ ] 代码覆盖率 ≥ 原始 Java 版本的覆盖率
[ ] 所有 public API 有 KDoc 注释
[ ] 所有 JNI 函数有文档说明预期行为
[ ] 所有第三方依赖有许可证兼容性审查
```

---

## 11. 附录：类映射表

### 11.1 `com.luajava.*`（18 个外部类 + 内部类）

| # | 类名 | 内部类 | 优先级 | 依赖 |
|---|------|--------|--------|------|
| 1 | LuaState | — | P0 | JNI |
| 2 | LuaStateFactory | — | P0 | LuaState |
| 3 | LuaObject | — | P0 | LuaState |
| 4 | LuaTable | LuaEntry | P1 | LuaObject |
| 5 | LuaList | — | P1 | LuaObject |
| 6 | LuaStack | — | P1 | LuaState |
| 7 | LuaString | — | P1 | — |
| 8 | LuaJavaAPI | JavaObject; 2 个匿名类 | P0 | JNI |
| 9 | JavaFunction | — | P1 | LuaState |
| 10 | LuaFunction | — | P1 | LuaObject |
| 11 | LuaInvocationHandler | — | P1 | java.lang.reflect.Proxy |
| 12 | LuaMethodInterceptor | SuperCall | P1 | LuaInvocationHandler |
| 13 | LuaAbstractMethodInterceptor | — | P2 | LuaMethodInterceptor |
| 14 | LuaMetaTable | — | P2 | LuaState |
| 15 | LuaException | — | P0 | — |
| 16 | Console | — | P2 | LuaState |

### 11.2 `com.androlua.*`（核心类，~60 个外部类 + 内部类）

| # | 类名 | 角色 | 优先级 | 翻译难度 |
|---|------|------|--------|----------|
| 1 | LuaApplication | Application 入口 | P0 | ★★☆ |
| 2 | Main | 主 Activity（IDE 入口） | P0 | ★★★★ |
| 3 | Welcome | 启动屏 Activity | P0 | ★★☆ |
| 4 | LuaActivity | Lua 运行时 Activity | P0 | ★★★★ |
| 5 | LuaActivityX | 扩展 Lua Activity | P0 | ★★★★ |
| 6 | LuaService | Lua 后台服务 | P0 | ★★★☆ |
| 7 | LuaAccessibilityService | 无障碍服务 | P0 | ★★★☆ |
| 8 | LuaContext | 运行时接口 | P0 | ★☆☆ |
| 9 | CrashHandler | 崩溃处理 | P0 | ★☆☆ |
| 10 | LuaAsyncTask | 异步任务 | P1 | ★★★☆ |
| 11 | LuaThread | 线程封装 | P1 | ★★★☆ |
| 12 | LuaTimer | 定时器 | P1 | ★★☆ |
| 13 | LuaTimerTask | 定时任务 | P1 | ★☆☆ |
| 14 | LuaRunnable | 可运行封装 | P1 | ★★★ |
| 15 | Ticker | UI 主线程定时器 | P1 | ★★☆ |
| 16 | LuaDexLoader | DEX 加载器 | P1 | ★★★ |
| 17 | LuaDexClassLoader | DEX 类加载器 | P1 | ★★☆ |
| 18 | LuaEnhancer | 增强/代理 | P1 | ★★★★ |
| 19 | LuaAssetLoader | Asset 资源加载器 | P1 | ★★☆ |
| 20 | LuaEditor | 代码编辑器视图 | P1 | ★★★★★ |
| 21 | LuaLexer | Lua 词法分析器 | P1 | ★★★★ |
| 22 | LuaTokenTypes | Token 类型定义 | P1 | ★☆☆ |
| 23 | LuaDialog | 对话框（已有源码） | P1 | ★★☆ |
| 24 | LuaAdapter | 数据适配器 | P1 | ★★★☆ |
| 25 | LuaArrayAdapter | 数组适配器 | P1 | ★★★☆ |
| 26 | LuaMultiAdapter | 多布局适配器 | P1 | ★★★☆ |
| 27 | LuaExpandableListAdapter | 可展开列表适配器 | P1 | ★★★☆ |
| 28 | LuaDrawable | 自定义 Drawable | P2 | ★★★ |
| 29 | LuaBitmapDrawable | 位图 Drawable | P2 | ★★★ |
| 30 | NineBitmapDrawable | 9-patch Drawable | P2 | ★★☆ |
| 31 | LoadingDrawable | 加载动画 Drawable | P2 | ★★☆ |
| 32 | LuaAnimation | 动画封装 | P2 | ★★★ |
| 33 | LuaBitmap | 位图操作 | P2 | ★★☆ |
| 34 | GifDecoder | GIF 解码器 | P2 | ★★★☆ |
| 35 | LuaFragment | Fragment 封装 | P2 | ★★★ |
| 36 | LuaPreferenceFragment | 偏好设置 Fragment | P2 | ★★★ |
| 37 | LuaResources | 资源加载 | P2 | ★★★ |
| 38 | LuaWebView | WebView 封装 | P2 | ★★★★★ |
| 39 | LuaCameraView | 相机预览 | P2 | ★★★ |
| 40 | LuaView | View 封装 | P2 | ★★☆ |
| 41 | LuaUtil | 通用工具 | P1 | ★★☆ |
| 42 | ZipUtil | ZIP 压缩工具 | P1 | ★★☆ |
| 43 | LuaStringUtil | 字符串工具 | P1 | ★★☆ |
| 44 | LuaPrint | 打印输出 | P1 | ★☆☆ |
| 45 | Http | HTTP 请求 | P2 | ★★★★ |
| 46 | Download | 文件下载 | P2 | ★★★ |
| 47 | LuaServer | TCP 服务端 | P2 | ★★★ |
| 48 | LuaClient | TCP 客户端 | P2 | ★★★ |
| 49 | LuaBroadcastReceiver | 广播接收器 | P2 | ★★☆ |
| 50 | LuaContentObserver | 内容观察者 | P2 | ★★☆ |
| 51 | LuaFileObserver | 文件观察者 | P2 | ★★☆ |
| 52 | LuaGcable | GC 管理 | P2 | ★☆☆ |
| 53 | LuaExAdapter | 扩展适配器 | P2 | ★★☆ |
| 54 | LuaView | — | P2 | ★☆☆ |
| 55 | Main 内部 Handler | — | P0 | ★☆☆ |

### 11.3 工具类（`com.androlua.util.*`）

| # | 类名 | 说明 | 优先级 |
|---|------|------|--------|
| 1 | AsyncTaskX | 并发任务框架 | P1 |
| 2 | TimerX | 定时器框架 | P1 |
| 3 | TimerTaskX | 定时任务 | P1 |
| 4 | ClickRunnable | 点击防抖 | P1 |
| 5 | GlobalActionAutomator | 自动化操作 | P2 |
| 6 | RootUtil | Root 权限工具 | P2 |
| 7 | RSASecurity | RSA 加密 | P2 |
| 8 | ScreenMetrics | 屏幕尺寸工具 | P1 |
| 9 | VolatileBox | 易变值包装 | P1 |
| 10 | VolatileDispose | 易变资源管理 | P1 |
| 11 | ZipUtil | ZIP 工具 | P1 |

### 11.4 自定义小部件（`android.widget.*` 等）

| # | 类 | 说明 | 优先级 |
|---|-----|------|--------|
| 1 | AlertDialogBuilder | 对话框构建器 | P2 |
| 2 | FloatWindow | 悬浮窗 | P2 |
| 3 | ArrayListAdapter | 列表适配器 | P2 |
| 4 | ArrayPageAdapter | 页面适配器 | P2 |
| 5 | ArrayExpandableListAdapter | 可展开适配器 | P2 |
| 6 | BasePageAdapter | 基础页面适配器 | P2 |
| 7 | CardView | 卡片视图 | P2 |
| 8 | CircleImageView | 圆形图片 | P2 |
| 9 | DrawerLayout | 抽屉布局 | P2 |
| 10 | ExListView | 扩展列表 | P2 |
| 11 | FloatButton | 浮动按钮 | P2 |
| 12 | HorizontalListView | 水平列表 | P2 |
| 13 | PageView | 页面滑动 | P2 |
| 14 | PageLayout | 页面布局 | P2 |
| 15 | PullingLayout | 下拉刷新 | P2 |
| 16 | RippleLayout | 水波纹 | P2 |
| 17 | RippleHelper | 水波纹辅助 | P2 |
| 18 | SlidingLayout | 滑动侧边栏 | P2 |
| 19 | ToolBar | 工具栏 | P2 |
| 20 | ViewDragHelper | 拖拽辅助 | P2 |
| 21 | RoundRectDrawable | 圆角矩形 | P2 |
| 22 | RoundRectDrawableWithShadow | 带阴影圆角 | P2 |
| 23 | FileProvider | 文件提供者 | P2 |

### 11.5 nirenr（`com.nirenr.*`）

| # | 类 | 说明 | 优先级 |
|---|-----|------|--------|
| 1 | Color | 颜色工具 | P2 |
| 2 | ColorFinder | 颜色查找 | P2 |
| 3 | ColorPoint | 颜色点 | P2 |
| 4 | Point | 坐标点 | P2 |
| 5 | SplitEditView | 分屏编辑视图 | P2 |
| 6 | ScreenCaptureActivity | 截屏 Activity | P2 |
| 7 | ScreenCaptureListener | 截屏监听 | P2 |
| 8 | ScreenShot | 截屏工具 | P2 |
| 9 | FileUtil | 文件工具 | P2 |

---

## 附录 A：依赖决策矩阵

| 原始依赖 | 处置 | 原因 |
|----------|------|------|
| `AndroLua+5.0.18_AndroidX.jar`（961 类） | **翻译为 Kotlin 源文件** | 这是项目的核心 |
| `libluajava.so` (armeabi-v7a / arm64-v8a) | **保留不变** | C 语言实现，不可翻译 |
| `bsh.*` (BeanShell) | **保留为 Java 依赖** | ~100 类，纯 Java 实现，无需翻译 |
| `com.android.cglib.*` (DexMaker) | **保留为 Java 依赖** | ~300 类，字节码操作，无需翻译 |
| `com.baidu.mobstat.*` | **移除** | 百度统计，可替换为自建分析 |
| `a/a/*`, `b/a/*`, `c/a/*` (混淆) | **反编译分析后决定** | 可能是核心功能混淆保护 |
| `glide-full-4.7.0` | **保留为 Java 依赖** | 图片加载库 |
| `LuaCustRecyclerAdapter.jar` | **翻译为 Kotlin** | 自定义适配器 |
| 混淆的 `a/a/a/a/a.class` | **反编译后评估** | 可能是许可证/签名校验 |

---

## 附录 B：关键 API 兼容性保证

以下 Lua API 必须保持 100% 向后兼容：

```lua
-- 核心运行时
require "import"                   -- import 系统
import "android.*"                 -- 包导入
activity / service                 -- 全局上下文
luajava.bindClass()                -- 类绑定
luajava.new()                      -- 对象创建
luajava.override()                 -- 接口/虚类覆盖
luajava.astable()                  -- Java 对象转 Lua table
luajava.createProxy()              -- 代理创建
luajava.getContext()               -- 获取上下文

-- 布局
loadlayout(layout_table)           -- 布局表加载
loadbitmap(path)                   -- 位图加载
loadmenu(menu, items)              -- 菜单加载

-- 并发
thread(src, ...)                   -- 线程
task(src, ..., callback)           -- 异步任务
timer(func, delay, period, ...)    -- 定时器

-- 构建
require "bin"                      -- APK 打包
bin(luapath)                       -- 打包入口

-- 工具
print(...)                         -- 输出到 logcat
dump(obj)                          -- 对象转储
each(obj)                          -- 迭代器
enum(e)                            -- 枚举迭代
```

---

## 附录 C：文件变更清单

### 新增文件

```
delua/
├── app/src/main/java/com/androlua/    # Kotlin 源文件
│   ├── runtime/
│   ├── bindings/
│   ├── editor/
│   ├── ui/
│   ├── adapter/
│   ├── concurrent/
│   ├── net/
│   ├── util/
│   └── drawable/
├── app/src/main/java/com/luajava/     # Kotlin JNI 绑定
│   └── *.kt
├── app/src/main/java/com/nirenr/      # nirenr 工具
│   └── *.kt
├── app/build.gradle.kts               # Gradle KTS 迁移
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/libs.versions.toml          # 版本目录更新
```

### 删除文件

```
delua/
└── androlua/libs/AndroLua+5.0.18_AndroidX.jar  # 翻译后移除
```

### 保持不变的文件

```
delua/
├── app/libs/*.jar                      # 第三方 JAR（Glide 等）
├── app/src/main/jniLibs/               # 原生 .so 库
├── app/src/main/assets/                # Lua 资产文件
├── app/src/main/resources/lua/         # Lua 库文件
├── app/src/main/res/                   # Android 资源
├── app/src/main/AndroidManifest.xml    # Manifest（可能需微调）
├── androlua/src/main/jniLibs/          # 原生 .so 库
├── androlua/src/main/resources/lua/    # 核心 Lua 库
└── androlua/src/main/res/              # 核心资源
```

---

> **文档结束**
>
> 本文件应作为 delua 项目的技术决策参考和工程执行手册，
> 所有重大技术决策应首先在此文档中记录变更后再实施。
