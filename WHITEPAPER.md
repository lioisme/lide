# delua — Android Lua 手机编程平台

**白皮书 v2.0**

---

## 1. 摘要

delua 是一个面向 **Android 手机编程** 场景的 Lua 脚本编程环境。用户在手机上编写、运行、调试 Lua 脚本，并可一键构建为独立 APK。

delua 采用 **AndroLua C 原生引擎**（`libluajava.so` + 全量 Java 运行时源码）作为底层运行时，从 AndroLua_pro 源码编译集成。宿主层使用 **Kotlin + Jetpack Compose** 实现 IDE、项目管理及调试工具链。

引擎 Java 层编译为宿主 APK 主 DEX（`androlua` 模块），原生 `.so` 通过 NDK 从 AndroLua_pro C 源码构建。引擎与宿主通过 **Intent 通信 + 文件级结果协议**解耦，实现零兼容层代码的纯正 AndroLua 运行体验。

---

## 2. 背景与动机

### 2.1 方案选择

delua v1 原型采用 LuaJ（纯 Java Lua 5.2）作为脚本引擎，并在 `androlua/` 模块中维护了 ~2000 行 Kotlin 兼容层代码（`ImportFunction`、`LoadLayoutFunction`、`ViewProxy`、`GLModule` 等）来模拟 AndroLua 的 API 行为。经审计发现，这套兼容层存在 6 个 P0 级缺陷，维护成本高于预期。

**决策**：迁移到 AndroLua C 原生引擎，移除所有兼容层代码。

### 2.2 设计目标

delua 的核心设计原则：

1. **Kotlin 宿主** —— 所有 IDE 功能、构建工具链、项目管理均使用 Kotlin / Java 实现
2. **Lua 仅作为脚本语言** —— Lua 仅用于运行用户代码，不承担宿主框架职责
3. **引擎与宿主解耦** —— ClassLoader 隔离 + Intent 通信，互不污染
4. **零兼容层** —— 所有 AndroLua API 由原生引擎直接提供
5. **预编译集成** —— .so + .dex 预编译，宿主构建零 NDK 依赖
6. **生态兼容** —— 工程格式兼容 AndroLua 的 `.alp` 规范
7. **极简 IDE** —— 适应手机屏幕限制

---

## 3. 技术架构

### 3.1 整体结构

```
┌─────────────────────────────────────────────────────────────────────┐
│  PROCESS BOUNDARY ———— Android Application                         │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  delua Host Process (Kotlin / ART)                             │  │
│  │  ┌─────────────────────────────────────────────────────────┐  │  │
│  │  │  IDE Layer (Jetpack Compose + Material 3)                │  │  │
│  │  │  ├── ProjectListScreen   项目管理                          │  │  │
│  │  │  ├── EditorScreen        代码编辑器 + 语法高亮              │  │  │
│  │  │  ├── SettingsScreen      设置/主题/权限                    │  │  │
│  │  │  └── PermissionScreen    运行时权限管理                     │  │  │
│  │  └─────────────────────────────────────────────────────────┘  │  │
│  │                                                               │  │
│  │  ┌─────────────────────────────────────────────────────────┐  │  │
│  │  │  EngineBridge (Kotlin, ~20 lines)                       │  │  │
│  │  │  └── createEngineIntent() 构建 → EngineActivity           │  │  │
│  │  └─────────────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────┬────────────────────────────────┘  │
│                                 │                                    │
│  ┌──────────────────────────────┴────────────────────────────────┐  │
│  │  AndroLua Engine (Native + Java Runtime)                      │  │
│  │                                                              │  │
│  │  ┌──────────────┐  ┌─────────────────────┐  ┌────────────┐  │  │
│  │  │ libluajava.so │  │ androlua 模块        │  │ libgl.so   │  │  │
│  │  │ (C Lua + JNI) │  │ (Java 运行时源码编译 │  │ (OpenGL ES │  │  │
│  │  │               │  │  → 宿主主 DEX)      │  │  1.0 绑定) │  │  │
│  │  └──────────────┘  └─────────────────────┘  └────────────┘  │  │
│  │                                                              │  │
│  │  ┌──────────────────────────────────────────────────────┐   │  │
│  │  │  Lua 运行时资源                                       │   │  │
│  │  │  ├── import.lua     类加载/导入机制核心                │   │  │
│  │  │  ├── loadlayout.lua 布局表解析器                      │   │  │
│  │  │  ├── loadbitmap.lua 位图加载                         │   │  │
│  │  │  └── *.lua          工具库                           │   │  │
│  │  └──────────────────────────────────────────────────────┘   │  │
│  └──────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 技术栈

| 层次 | 技术 | 选型理由 |
|------|------|---------|
| UI 框架 | Jetpack Compose + Material Design 3 | 声明式 UI，Kotlin 原生支持，适应手机屏幕 |
| Lua 引擎 | AndroLua C 引擎（Lua 5.3.3 + JNI） | 原生性能，100% AndroLua API 兼容 |
| 引擎集成 | NDK 编译 .so + Java 源码编译（androlua 模块） | 从 AndroLua_pro 源码构建，100% API 兼容 |
| 通信协议 | Intent + 文件系统（JSON） | 松耦合，引擎运行在独立 Activity 栈 |
| Java 运行时 | `androlua` 模块（Kotlin 库）编译所有 AndroLua_pro Java 源码 | 全量运行时类编译进宿主主 DEX |
| 代码编辑器 | sora-editor + TextMate 语法 | 专业级编辑器，支持 Lua 语法高亮 |

### 3.3 解耦设计：EngineBridge

引擎与宿主通过 EngineBridge 交互，运行在独立 Activity 栈：

```
RunEngineLauncher → 物化依赖(后台) → EngineBridge.createEngineIntent(projectDir, mainFile) → EngineActivity
```

引擎输出/错误由引擎自带控制台（LuaActivity 默认布局）直接展示，宿主不做结果回传页；物化失败仅 Toast 提示。

Java 运行时类（`com.luajava.*`、`com.androlua.*`）通过 `androlua` 模块编译进宿主 APK 主 DEX，`EngineActivity` 直接继承 `LuaActivity`，无需反射或 ClassLoader 隔离。

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

```
用户点击"运行"
    │
    ▼
RunEngineLauncher → 物化依赖(后台线程)
    │
    ▼
EngineBridge.createEngineIntent(projectDir, mainFile)
    ▼
startActivity(intent)  │ Intent Extra: projectDir, mainFile
    ▼
EngineActivity (extends LuaActivity, 独立 Activity 栈)
    │ 继承自 androlua 模块的 LuaActivity
    │ System.loadLibrary("luajava")
    │ LuaStateFactory → LuaState
    │ pushJavaObject(this) → setGlobal("activity")
    │ setPackagePath(projectDir/?.lua + cacheDir/lua/?.lua)
    │ doFile(init.lua) → doFile(main.lua)
    │ 脚本执行（输出/错误在引擎控制台直接展示）
```

**语法检查**：由 sora-editor 的 CodeEditor 内置实现，无需宿主干预。

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

## 6. 引擎选型依据

### 6.1 为什么选择 AndroLua C 引擎

| 对比项 | v1 LuaJ（纯 Java） | v2 AndroLua C 引擎（当前） |
|--------|-------------------|--------------------------|
| Lua 版本 | 5.2 | 5.3.3 |
| 兼容性 | ~2000 行 Kotlin 兼容层代码 | 零兼容层，100% 原生 AndroLua API |
| 维护来源 | 社区停更（2019） | AndroLua_pro 活跃项目 |
| 性能 | 解释模式 ≈ C Lua 40-70% | 原生 C 速度 |
| 引擎集成 | Gradle 依赖 | 预编译 .so + .dex，一次编译长期使用 |
| 宿主耦合 | 强耦合（共享 Globals） | 完全解耦（Intent + 文件协议） |
| ClassLoader 隔离 | ❌ 不隔离 | ✅ DexClassLoader 隔离 |
| build.gradle 复杂度 | 低（仅 Maven 坐标） | 中（资产复制 + jniLibs） |

### 6.2 迁移收益

| 指标 | LuaJ 时代 | 原生引擎 | 改善 |
|------|----------|---------|------|
| 兼容层代码 | ~2000 行 Kotlin | **0 行** | 完全消除维护负担 |
| demo 工程 | ❌ 兼容层 bug | ✅ 零修改原生运行 | 兼容性达到 100% |
| testjson 工程 | ❌ ViewProxy 缺陷 | ✅ 零修改原生运行 | 复杂 UI 场景覆盖 |
| kleinbottle 工程 | ❌ GL 兼容问题 | ✅ 零修改原生运行 | OpenGL 场景覆盖 |
| 性能 | 解释模式 | JNI + 原生 C | 10-50x 提升 |

### 6.3 成本

| 成本项 | 说明 | 频次 |
|--------|------|------|
| NDK 环境搭建 | 首次配置 NDK r28+ | 一次性 |
| .so 编译 | `ndk-build` 编译 C 源码 | 仅源码变更时 |
| .dex 编译 | `javac + d8` 编译 Java 源码 | 仅源码变更时 |
| ClassLoader 隔离验证 | 安全审查 | 每次引擎更新 |

引擎重建非日常操作。通过 `scripts/rebuild_engine.sh` / `.ps1` 一键完成。

---

## 7. 实现路线图

### 阶段一：引擎迁移（已完成）

- NDK 编译 `libluajava.so` + `libgl.so`（arm64-v8a / armeabi-v7a / x86_64）
- AndroLua_pro Java 全量源码编译（`androlua` 模块，~80 源文件，集成入主 DEX）
- EngineBridge + EngineActivity 桥接实现
- 三个测试工程（demo / testjson / kleinbottle）适配
- LuaJ 兼容层代码清理（~2300 行移除）

### 阶段二：编辑器完善（进行中）

- sora-editor 集成 + TextMate Lua 语法
- Lua 语法高亮（关键字/字符串/注释/数字）
- 搜索与替换
- 文件浏览侧栏
- 多文件支持

### 阶段三：运行时（已完成）

- AndroLua C 原生引擎（`import` / `loadlayout` / 所有 API）
- EngineActivity 执行引擎（Intent 驱动）
- 运行入口（RunEngineLauncher：物化依赖 → 直启引擎，输出由引擎控制台展示）
- Lua 运行时脚本（25 个 `.lua` 文件）

### 阶段四：构建系统（规划中；v1.0 明确暂不支持，见下方决策说明）

- AndroidManifest 二进制 XML 编码/解码
- APK 容器组装（ZIP 格式）
- `classes.dex` 注入
- 资源打包（图标、启动图）
- 对齐（zipalign）

> **v1.0 决策（2026-07-31）**：设备端独立 APK 打包暂不支持。原因：① pro 打包器 `bin.lua` 依赖外部 `mao/sign` 签名 jar（不在仓库中），且 v1-only 签名在现代 Android（targetSdk≥30）不可安装；② 宿主 APK 模板携带全套 IDE 运行时，产物方法数/体积不可控；③ 打包器属于设备端构建链，与 IDE 编辑/运行主链路正交，优先级低。后续立项时平移 pro 的 luac 字节码化、`checklib` 依赖自动收集、manifest 字符串级补丁三个机制即可（参考 `docs/XPOSED_MODULE_FEASIBILITY_REPORT.md` §3.2 的继承价值分析）。

### 阶段五：签名与发布（规划中）

- 调试 / 自定义签名管理
- v1 / v2 / v3 签名方案支持
- APK 安装与分享
- 多 ABI 构建支持

---

## 7.1 原生模块差异声明（2026-08-01）

> 对应 `docs/LIBS_SUPPORT_WORK_ORDER.md` LIBS-003 的最终结论。

与 AndroLua_pro（`jni/` 19 个 C 模块源码随 APK 打包）相比，delua 内置原生模块为 **17 个 × 3 ABI**（15 个原有 + lfs + lsqlite3 已补齐）：

- ✅ **lfs / lsqlite3**：已按 WP-001 流程预编译（`build-scripts/jni/{lfs,lsqlite3}/Android.mk` + `build-scripts/patches/lsqlite3/lsqlite3.c` 的 Lua 5.3 兼容补丁），产物入 `prebuilt/` 与 `app/src/main/jniLibs/`，真机 `require "lfs"` / `require "lsqlite3"` 验证通过。
- ⏭️ **lluv**：与已内置的 luv 功能重叠（同源于 libuv），pro 生态中极少直接使用，不预编译。
- ⏭️ **tcc**：脚本内嵌 C 编译器；NDK 29 仅 armeabi-v7a 有目标（无 arm64-v8a / x86_64），对现代设备无实际可用性，不预编译。
- **补偿机制**：缺失模块（含任意第三方 .so）由工程 `libs/` 自备 —— 引擎 `loadLibs` 自动登记 `getLibrarys()`，`libsloader` 优先命中表，否则 `package.loadlib("./libs/lib…so", "luaopen_…")`。用法见 `app/src/main/assets/samples/DemoProject/libs/README.txt`。

另注：delua 内置模块含 `gl`（OpenGL 绑定，对应 pro 的 luagl）与 `mime`（pro 未内置）等；`socket` 等常用模块与 pro 完全一致。内置 Lua 库为 pro 超集（31 文件 vs pro 28）。

---

## 8. 风险与缓解

| 风险 | 说明 | 缓解措施 |
|------|------|---------|
| NDK 版本兼容性 | NDK 版本更新可能导致 C 编译失败 | 锁定 NDK 26.x；scripts/rebuild_engine 记录精确环境 |
| Support Library 依赖 | AndroLua_pro Java 源码有 Support Library 引用 | 使用 AndroidX 重写受影响类（参考 AndroLuaX） |
| Android 14+ JNI 限制 | `luajava.c` 在 Android 14+ 的新 JNI 约束下崩溃 | 在 API 34+ 设备验证；必要时适配新 JNI API |
| DEX 方法数超 64K | androlua 模块 Java 源码过多导致主 DEX 方法数超限 | 分割模块或启用 multidex |
| GLES 1.0 可用性 | `libgl.so` 依赖 GLESv1_CM 在部分设备不可用 | 仅 kleinbottle 工程使用，对用户提示 |
| Lua 5.1/5.3 语法差异 | 脚本使用了 Lua 5.1 专有语法 | 三个测试工程使用基础语法，兼容性好 |
| 通配符导入受限 | Android 14+ 限制 DexFile API | 改用 PathClassLoader + 反射遍历 DEX 路径 |
| 代理创建 ClassLoader 隔离 | createProxy 跨 ClassLoader 异常 | 限制代理 Android SDK 接口（Boot ClassLoader） |
| 结果文件写入丢失 | Activity 异常退出时结果未写入 | onSaveInstanceState 兜底写入 |

---

## 附录 A：术语表

| 术语 | 定义 |
|------|------|
| .alp | AndroLua Project 的缩写，标准的 ZIP 格式工程归档 |
| ABI | Application Binary Interface，如 arm64-v8a、armeabi-v7a |
| AXML | Android Binary XML，编译后的 XML 格式 |
| LuaJ | 纯 Java 实现的 Lua 5.2 解释器（v1 原型使用，已废弃） |
| androlua 模块 | Kotlin Android Library，编译 AndroLua_pro Java 运行时源码 |
| EngineBridge | Kotlin 宿主 ↔ AndroLua 引擎的唯一桥接类 |
| EngineActivity | 引擎侧 Activity，继承 LuaActivity，运行 Lua 脚本 |
| NDK 编译 | 通过 ndk-build 从 AndroLua_pro C 源码编译 .so 原生库 |
| `loadlayout` | AndroLua 的布局表函数，将 Lua table 递归解析为 Android View 树 |
| DEX | Dalvik Executable，Android 的可执行文件格式 |
| Appbar | Android 顶部应用栏 |
| Statusbar | Android 底部状态栏（行号/编码等） |

---

*本文档为 delua v1.0 白皮书，内容可能随项目进度调整。*
