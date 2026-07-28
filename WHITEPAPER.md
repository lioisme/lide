# delua — 基于 LuaJ 的 Android Lua 手机编程平台

**白皮书 v1.0**

---

## 1. 摘要

delua 是一个面向 **Android 手机编程** 场景的 Lua 脚本编程环境。用户在手机上编写、运行、调试 Lua 脚本，并可一键构建为独立 APK。

与传统方案（如 AndroLua+）使用 C 语言 Lua 引擎 + JNI 桥接不同，delua 采用纯 Java 实现的 Lua 虚拟机 **LuaJ** 作为底层运行时，宿主层使用 **Kotlin** 实现 IDE、项目管理、构建及调试工具链。

delua **不**对 AndroLua+ 或其衍生项目的代码做任何复用，仅参考其 `.alp` 工程结构规范以实现生态兼容。

---

## 2. 背景与动机

### 2.1 现有方案的局限性

AndroLua+ 是目前 Android 平台上最具影响力的 Lua 脚本编程工具。其架构如下：

```
AndroLua+ App
  ├── Java 层: LuaActivity（薄壳，负责初始化）
  ├── JNI 层: com.luajava.*（C 桥接库）
  ├── C 层:   Lua 5.3 引擎 + 原生模块（.so）
  └── Lua 层: main.lua / init.lua（宿主逻辑实现在 Lua 中）
```

这一架构存在若干根本性问题：

| 问题 | 影响 |
|------|------|
| **JNI 桥接复杂** | 新增一个跨语言 API 需同时修改 C/Java/Lua 三层 |
| **引擎依赖 NDK** | 构建需要 C 工具链，ABI 碎片化问题突出 |
| **宿主用 Lua 编写** | IDE 的 UI 事件处理、文件管理等均在 Lua 中实现，难以维护和调试 |
| **C 模块生态封闭** | 大量功能依赖闭源或难以移植的 `.so` 文件 |
| **多架构支持困难** | 每个 `.so` 都需为 arm64-v8a / armeabi-v7a / x86 分别编译 |
| **JNI 错误难排查** | C 层崩溃导致 native crash，堆栈信息不可读 |

### 2.2 设计目标

delua 的核心设计原则：

1. **纯 Java 运行时** —— Lua 引擎使用 LuaJ（纯 Java），彻底消除 NDK 依赖
2. **Kotlin 宿主** —— 所有 IDE 功能、构建工具链、项目管理均使用 Kotlin / Java 实现
3. **Lua 仅作为脚本语言** —— Lua 仅用于运行用户代码，不承担宿主框架职责
4. **生态兼容** —— 工程格式兼容 AndroLua 的 `.alp` 规范，降低用户迁移成本
5. **极简 IDE** —— 适应手机屏幕限制，编辑器页面仅包含 Appbar + Statusbar + Editor

---

## 3. 技术架构

### 3.1 整体结构

```
┌─────────────────────────────────────────────────────────────┐
│                    delua App (Android)                       │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                   IDE Layer (Kotlin)                   │  │
│  │                                                       │  │
│  │  ┌─────────────────────────────────────────────────┐  │  │
│  │  │             EditorActivity                       │  │  │
│  │  │  ┌──────────┐ ┌──────────────────────────────┐  │  │  │
│  │  │  │  Appbar   │ │  Editor (语法高亮 + 报错标注) │  │  │  │
│  │  │  └──────────┘ └──────────────────────────────┘  │  │  │
│  │  │  ┌──────────┐                                   │  │  │
│  │  │  │ Statusbar│ （行号/语言/编码/光标位置）        │  │  │
│  │  │  └──────────┘                                   │  │  │
│  │  └─────────────────────────────────────────────────┘  │  │
│  │                                                       │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ │  │
│  │  │ 项目管理  │ │ 文件浏览 │ │ 权限管理  │ │ 构建发布 │ │  │
│  │  │（抽屉/页) │ │（侧栏）  │ │（独立页）  │ │（独立页） │ │  │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘ │  │
│  └───────────────────────────┬───────────────────────────┘  │
│                              │                               │
│  ┌───────────────────────────┴───────────────────────────┐  │
│  │                 Engine Layer (Kotlin/Java)              │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │  │
│  │  │ LuaRunner     │  │ BuildEngine  │  │ DebugService│ │  │
│  │  │（LuaJ 封装）  │  │（APK 构建）  │  │（语法检查） │ │  │
│  │  └──────┬───────┘  └──────────────┘  └──────────────┘ │  │
│  └─────────┼──────────────────────────────────────────────┘  │
│            │                                                  │
│  ┌─────────┴──────────────────────────────────────────────┐  │
│  │              LuaJ Runtime (Pure Java)                   │  │
│  │  ┌──────────┐ ┌──────────┐ ┌────────────────────────┐ │  │
│  │  │ Globals   │ │ Luajava  │ │ Standard Libraries     │ │  │
│  │  │（环境）   │ │ Lib（反射）│ │（base/string/table/…）│ │  │
│  │  └──────────┘ └──────────┘ └────────────────────────┘ │  │
│  └────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 技术栈

| 层次 | 技术 | 选型理由 |
|------|------|---------|
| UI 框架 | Jetpack Compose + Material Design 3 | 声明式 UI，Kotlin 原生支持，适应手机屏幕 |
| Lua 引擎 | LuaJ 3.0.2（自行 fork 维护） | 纯 Java Lua 5.2，零原生依赖 |
| 构建工具 | Kotlin 原生实现 | 基于 `java.util.zip`、`android.util.jar` 等标准库 |
| 签名 | BouncyCastle / Android `apksigner` | 标准 JCA 体系，支持 v1/v2/v3 签名方案 |
| 代码编辑器 | 基于 `EditText` + 自绘语法高亮 | 轻量，无需引入重量级编辑器库 |

### 3.3 LuaJ 适配层

LuaJ 提供了 `JsePlatform.standardGlobals()` 开箱即用，包含以下标准库：

- `base`（print、type、pairs、ipairs 等）
- `table`、`string`、`math`、`io`、`os`
- `coroutine`、`bit32`、`package`
- **`luajava`**（bindClass、newInstance、createProxy 等）

delua 在此之上封装 AndroLua 兼容层：

```kotlin
class AndroLuaCompat(private val globals: Globals) {
    fun setup(context: Context, projectDir: File) {
        // 设置 activity / this 全局变量
        globals.set("activity", CoerceJavaToLua.coerce(context))
        globals.set("this", CoerceJavaToLua.coerce(context))

        // 注册 import 函数（Lua 模块加载 + Java 包导入）
        globals.load(ImportFunction(context))

        // 注册 loadlayout 函数（布局表解析）
        globals.load(LoadLayoutFunction(context))

        // 设置 package.path
        val pkg = globals.get("package")
        pkg.set("path", "$projectDir/?.lua;$projectDir/lua/?.lua;...")
    }
}
```

关键兼容函数清单：

| Lua 函数 | 实现方式 |
|----------|---------|
| `import "android.widget.*"` | Kotlin 反射扫描包名，批量绑定类到全局表 |
| `loadlayout({...})` | Kotlin 解析 Lua table 树，递归创建 Android View |
| `luajava.bindClass(...)` | 直接使用 LuaJ 内建的 LuajavaLib |
| `luajava.createProxy(...)` | 直接使用 LuaJ 内建的 Proxy 创建 |
| `luajava.astable(...)` | Kotlin 遍历 Collection/Map/Array 转为 Lua table |
| `activity.getLuaDir()` | Kotlin 实现，映射到 Context 文件 API |

---

## 4. IDE 界面设计

### 4.1 设计理念

手机屏幕有限，delua 的 IDE 遵循 **极简原则** —— 编辑器页面仅保留核心编辑区域，不设控制台、日志面板等辅助视图。运行输出、构建日志等通过 **Toast / Snackbar / 独立结果页面** 呈现。

### 4.2 编辑器页面布局

```
┌──────────────────────────────┐
│  Appbar                      │  ← 标题（文件名）、菜单（运行/构建/更多）
├──────────────────────────────┤
│  Editor                      │  ← Lua 代码编辑区
│  ┌────────────────────────┐  │    · 语法高亮（关键字/字符串/注释/数字）
│  │                        │  │    · 行号显示
│  │  Lua code here...      │  │    · 语法错误行标注（红色波浪线/图标）
│  │                        │  │    · 错误提示 tooltip
│  │  ☰ syntax error at     │  │
│  │  line 5: 'end' expected│  │
│  └────────────────────────┘  │
├──────────────────────────────┤
│  Statusbar                   │  ← 行号:列号 | Lua 5.2 | UTF-8 | 项目名
└──────────────────────────────┘
```

**Appbar**：
- 左侧：导航菜单（项目文件浏览、设置等）
- 中间：当前文件名
- 右侧操作：运行 ▶、构建 APK ⚡、更多 ⋮（新建/保存/导出/设置）

**Editor**：
- 全屏编辑区域，无分割视图
- Lua 语法高亮（关键字 `function/end/if/then/for/while/local/return/nil/true/false`、字符串、注释、数字常量）
- 语法错误标注：通过预编译检查，在出错行左侧显示红色标记，点击可查看错误信息
- 行号显示

**Statusbar**：
- 当前光标位置（行:列）
- Lua 版本标识
- 文件编码
- 当前项目名称

### 4.3 其他页面

编辑器为默认首页，其他功能以独立页面或抽屉菜单呈现：

| 功能 | 呈现方式 |
|------|---------|
| 项目文件浏览 | Drawer 侧栏（文件树） |
| 新建/打开/导入/导出工程 | Appbar 菜单 + 文件选择器 |
| 权限管理 | 独立 Activity/页面 |
| 构建发布（签名/ABI 选择） | 独立 Activity/页面 |
| 运行输出 | Snackbar 快速反馈 + 可选的结果弹窗 |
| 语法错误 | Editor 内联标注 |

---

## 5. 核心功能规格

### 5.1 项目管理 (.alp)

**`.alp` 文件格式**：标准 ZIP 归档，内部结构：

```
project.alp
├── main.lua          # 入口脚本
├── init.lua          # 项目配置（可选）
│   ├── appname = "MyApp"
│   ├── appver = "1.0"
│   └── packagename = "com.example.myapp"
├── *.lua             # 其他 Lua 脚本
├── icon.png          # 应用图标（可选）
├── welcome.png       # 启动图（可选）
└── libs/             # 附加资源（可选）
    ├── *.so
    └── *.jar / *.dex
```

**功能实现**：

| 操作 | Kotlin 实现方式 |
|------|---------------|
| 新建工程 | 生成模板目录 + `init.lua` + `main.lua` 骨架 |
| 打开工程 | `ZipInputStream` 解压到临时目录 |
| 导入工程 | 解析 `.alp` 文件并解压 |
| 导出工程 | `ZipOutputStream` 打包为 `.alp` |

### 5.2 权限管理

可视化权限清单编辑器。用户勾选所需权限，生成 `user_permission` 表写入 `init.lua`，构建时注入 AndroidManifest。

权限数据来源：通过 `PackageManager.getSystemAvailableFeatures()` 获取系统已知权限列表并分类展示。

### 5.3 运行与调试

**执行流程**：

```kotlin
class LuaRunner(private val context: Context) {
    private val globals: Globals = JsePlatform.standardGlobals()

    fun runScript(filePath: String, args: Array<Any?>): Any? {
        // 1. 设置 AndroLua 兼容环境
        setupCompat(context, File(filePath).parentFile)

        // 2. 加载并编译 Lua 源码
        val chunk = globals.loadfile(filePath)

        // 3. 执行
        return chunk.call(*(args.map { CoerceJavaToLua.coerce(it) }.toTypedArray()))
    }
}
```

**语法检查**（编辑器触发）：

```kotlin
fun checkSyntax(source: String): List<SyntaxError> {
    return try {
        globals.load(source, "check.lua")
        emptyList()  // 编译通过
    } catch (e: LuaError) {
        listOf(SyntaxError(line = extractLine(e.message), message = e.message))
    }
}
```

错误结果通过 Editor 内联标注展示，不弹出控制台。

### 5.4 APK 构建引擎

构建引擎是 delua 最核心的底层基础设施，完全在 Kotlin 中实现，不依赖任何外部构建工具。

**构建流水线**：

```
项目目录
    │
    ▼
┌─────────────────┐
│  1. 读取配置     │  ← 解析 init.lua 获取 appname / packagename / user_permission
└────────┬────────┘
         ▼
┌─────────────────┐
│  2. 资源准备     │  ← icon.png → res/drawable/，welcome.png → 启动图
└────────┬────────┘
         ▼
┌─────────────────┐
│  3. Lua 编译     │  ← LuaJ 编译 .lua → .luac 字节码，放入 assets/
└────────┬────────┘
         ▼
┌─────────────────┐
│  4. 创建 APK     │  ← 从零构建 APK（ZIP 格式容器）
│  ├── AndroidManifest.xml  （二进制 XML）
│  ├── classes.dex          （模板 DEX + 用户资源）
│  ├── resources.arsc       （资源索引表，可选）
│  ├── assets/              （Lua 编译产物 + 用户文件）
│  ├── res/                 （图标等）
│  ├── lib/                 （按 ABI 分目录放置原生库）
│  └── META-INF/            （签名信息）
└────────┬────────┘
         ▼
┌─────────────────┐
│  5. 对齐          │  ← 4 字节对齐（ZIP 优化）
└────────┬────────┘
         ▼
┌─────────────────┐
│  6. 签名          │  ← v1 / v2 / v3 签名方案
└────────┬────────┘
         ▼
    输出 APK → 安装 / 分享
```

### 5.5 签名子系统

- **调试签名**：delua 自带预生成的 debug.keystore（与 Android SDK 标准 debug 证书一致）
- **自定义签名**：用户导入自己的 `.keystore` 或 `.jks` 文件，指定别名和密码
- **v1 签名（JAR 签名）**：使用 `java.util.jar.JarFile` + `java.security.KeyStore` 实现
- **v2 / v3 签名（APK 签名方案）**：使用 BouncyCastle 库 + APK Signing Block 格式实现

---

## 6. LuaJ 选型依据

### 6.1 为什么选择 LuaJ

| 对比项 | LuaJ（纯 Java） | C Lua via JNI（AndroLua 方案） |
|--------|----------------|-------------------------------|
| NDK | ❌ 不需要 | ✅ 需要 NDK 工具链 |
| ABI 兼容 | 全 ABI 自动兼容 | 每个 `.so` 需为各 ABI 分别编译 |
| Crash 排查 | Java 堆栈可读 | native crash 难定位 |
| 构建集成 | Maven/Gradle 直接引用 | 需要 ndkBuild / CMake |
| 扩展方式 | `LibFunction` 继承（纯 Java） | JNI 桥接（C+Java 两层） |
| 线程安全 | 内置 Globals 隔离 | 需自行管理 |
| JSR-223 支持 | ✅ 标准 `javax.script` 接口 | ❌ 不支持 |

### 6.2 LuaJ 审计要点

| 指标 | 状态 |
|------|------|
| 最新版本 | v3.0.2（2019） |
| 维护状态 | 官方停更，社区活跃（1K+ Stars / 184 Forks） |
| Lua 版本 | 5.2（缺失 `goto`、`//`、`utf8` 等 5.3+ 特性） |
| 活跃 Fork | `FiguraMC/luaj`、`wagyourtail/luaj`、`binx6/luaj`（5.2→5.3 升级） |
| Maven Central | 仅 v3.0.1（v3.0.2 因作者失联未发布） |

**结论**：LuaJ 核心 VM 成熟稳定，采用自行 fork 转为 Gradle 构建 + 选择性升级 5.3 特性的策略，风险可控。

### 6.3 与 AndroLua 能力对比

| 能力 | LuaJ | AndroLua+ | delua 补齐方式 |
|------|------|-----------|---------------|
| Lua 执行 | ✅ Globals.load() | ✅ L.LdoString() | 直接使用 LuaJ |
| Java 互调 | ✅ LuajavaLib（反射） | ✅ JNI 桥接 | 直接使用 LuaJ |
| `loadlayout` | ❌ | ✅ | Kotlin 兼容层实现 |
| `import` 导包 | ❌ | ✅ | Kotlin 反射实现 |
| `astable` | ❌ | ✅ | Kotlin 工具函数 |
| `loadDex` | ❌ | ✅ | Kotlin DexClassLoader |
| C 模块 (.so) | ❌ 不需要 | ✅ 需要 | **无需**（纯 Java 方案优势） |
| 语法解析/AST | ✅ JavaCC 生成 | ❌ | 用于编辑器语法高亮和报错 |
| 性能（解释） | C Lua 的 40-70% | 原生 C 速度 | 手机编程场景足够 |
| 性能（JIT） | 可超 C Lua（luajc） | 无 | 可选启用 |

---

## 7. 实现路线图

### 阶段一：基础框架

- Kotlin + Jetpack Compose 项目搭建
- LuaJ 集成 + Gradle 构建适配
- `.alp` 工程管理（新建 / 打开 / 保存 / 导出 / 导入）
- 基础编辑器（Appbar + Statusbar + 编辑区）

### 阶段二：编辑器完善

- Lua 语法高亮（词法分析器）
- 语法检查与错误行标注（预编译 + 错误提取）
- 文件浏览侧栏
- 多文件支持

### 阶段三：运行时

- AndroLua 兼容层（`import` / `loadlayout` / 常用 API）
- LuaRunner 执行引擎
- 运行结果反馈（Snackbar / 弹窗）
- Lua 标准库补齐

### 阶段四：构建系统

- AndroidManifest 二进制 XML 编码/解码
- APK 容器组装（ZIP 格式）
- `classes.dex` 注入
- 资源打包（图标、启动图）
- 对齐（zipalign）

### 阶段五：签名与发布

- 调试 / 自定义签名管理
- v1 / v2 / v3 签名方案支持
- APK 安装与分享
- 多 ABI 构建支持

---

## 8. 风险与缓解

| 风险 | 说明 | 缓解措施 |
|------|------|---------|
| LuaJ 版本为 5.2 | 部分脚本可能使用 5.3+ 语法 | 兼容层中提供 polyfill；可基于 `binx6/luaj` fork 升级 |
| AnroidManifest 二进制格式 | 未公开的二进制布局，随 API 等级变化 | 使用成熟的开源 AXML 解析库，保持更新 |
| DEX 注入 | Android 编译工具链的 API 可能变化 | 预编译最小模板 DEX + 替换式注入 |
| 性能 | LuaJ 解释模式较 C Lua 慢 2-3x | 手机编程场景以编辑/调试为主，执行性能非瓶颈；可启用 luajc |
| 权限模型 | Android 13+ 细粒度权限变化 | 跟随 Android API 版本更新适配 |

---

## 附录 A：术语表

| 术语 | 定义 |
|------|------|
| .alp | AndroLua Project 的缩写，标准的 ZIP 格式工程归档 |
| ABI | Application Binary Interface，如 arm64-v8a、armeabi-v7a |
| AXML | Android Binary XML，编译后的 XML 格式 |
| LuaJ | 纯 Java 实现的 Lua 5.2 解释器 |
| `loadlayout` | AndroLua 的布局表函数，将 Lua table 递归解析为 Android View 树 |
| DEX | Dalvik Executable，Android 的可执行文件格式 |
| Appbar | Android 顶部应用栏 |
| Statusbar | Android 底部状态栏（行号/编码等） |

---

*本文档为 delua v1.0 白皮书，内容可能随项目进度调整。*
