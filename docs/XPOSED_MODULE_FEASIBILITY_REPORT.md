# delua 开发 Xposed 模块可行性审计报告

| 项目 | 内容 |
|------|------|
| 报告编号 | DELUA-AUDIT-2026-001 |
| 审计对象 | delua 工程（`/`）+ AndroLua Pro 源码（`docs/AndroLua_pro-master/`） |
| 审计日期 | 2026-07-31 |
| 审计方式 | 源码阅读 + 全量检索 + 生态事实核验（Xposed/LSPosed 官方文档与社区资料） |
| 结论 | **技术可行**，但需要为 delua 新增"Xposed 模块构建模式"（含 API 桥、骨架 DEX、签名升级、清单注入），并对现有打包器设计做 4 项增强 |

---

## 1. 结论摘要（TL;DR）

1. **Xposed 模块的本质是一个普通 APK + 3 条 `<meta-data>` 声明 + `assets/xposed_init` 入口清单 + 一个实现 `IXposedHookLoadPackage` 的 Java 入口类。** 它没有超出 delua 打包器设计蓝图（WHITEPAPER 阶段四/五）的能力范围。
2. **Lua 作为模块逻辑载体在技术上是成立的**：delua 的引擎是 AndroLua C 原生引擎（Lua 5.3.3），`libluajava.so` **静态链接 Lua、只依赖系统库**（`build-scripts/jni/luajava/Android.mk`），可在任意 Android 进程中自提取加载；`com.luajava` 反射层已具备 `setAccessible(true)` 与 `Class.forName` 按调用者类加载器解析的能力，恰好匹配 Xposed 注入到目标进程的类加载器结构。
3. **当前状态不是"直接支持"**：delua 的打包器（阶段四）尚未实现；且现有运行链路是"IDE 内解释执行"（Intent + EngineActivity），与 Xposed 模块"目标进程内、无 UI、`Application.onCreate` 之前启动"的运行模型完全不同，必须新增一条**无头（headless）引导链路**。
4. **必须做的支持（详见第 6 章）**：
   - 打包期：新增"Xposed 模块"构建模式 —— 骨架 DEX（`com.luajava` 精简集 + 桥类）、manifest 注入 `xposedmodule` 等元数据、`assets/xposed_init`、Lua 编译为 `.luac` 进 assets、ABI 化 `lib/`、**v1+v2 双签名**（v2 必须，AndroLua Pro 的 v1-only 签名在现代设备不可安装）。
   - 运行期：入口类在目标进程内自提取 `.so` → 初始化 LuaState → 以"轻量 LuaContext"替代 `activity` 全局（无 Activity）→ 执行 `main.luac`；提供一个固定 Java 桥类 `XposedLuaHook extends XC_MethodHook`，把 Lua 函数回调到 hook 事件上。
   - Lua 侧：新增 `xposed.lua` 运行库（`log` / `findAndHookMethod` / `findClass` / 字段与方法读写 / `setResult` 等）。
5. **生态约束**（决定了签名与 targetSdk 策略）：Android 7.0+ 要求 `targetSdk≥24` 的 APK 必须带 v2 签名；Android 9 要求 `targetSdk≥28` 必须 v2+；Android 11 对 `targetSdk≥30` 完全忽略 v1。LSPosed 支持 Android 8.1–14（Vector fork 至 17），模块按应用粒度设置作用域。**v1-only 签名在现代 LSPosed 设备上直接无法安装，这是 AndroLua Pro 老打包器不可复用的核心原因之一。**
6. 好消息：AndroLua Pro 原版内置的**反 Xposed 对抗代码**（`LuaActivity.check1/check2/check3`，禁用 `XposedBridge.disableHooks`、清空缓存并弹窗"环境不安全"）在 delua fork 中**已被彻底清除**（全仓库检索零命中），不存在引擎自带的障碍。

---

## 2. 审计对象与方法

### 2.1 审计对象

| 对象 | 路径 | 说明 |
|------|------|------|
| delua 主工程 | `/`（`app/`、`androlua/`、`build-scripts/`、`scripts/`、`prebuilt/`） | Kotlin/Java 混合，Compose IDE + AndroLua 运行时 |
| delua 规划文档 | `WHITEPAPER.md`（阶段四/五：构建系统与签名） | 打包器设计蓝图 |
| AndroLua Pro | `docs/AndroLua_pro-master/` | delua 运行环境的来源，含设备端打包器 `bin.lua` |

### 2.2 审计方法

- 结构探索：两个并行探索代理分别审计 delua 与 AndroLua Pro 源码树；
- 关键点人工核验：反 Xposed 代码存留、`LuaJavaAPI` 反射机制、`LuaStateFactory` 初始化、`libluajava.so` 链接关系、运行时脚本 `import.lua`/`check.lua`、manifest 与 Gradle 配置、WHITEPAPER 打包管线；
- 生态事实核验：Xposed 官方开发教程（rovo89 wiki）、Xposed API 参考、LSPosed 兼容性文档、签名方案版本规则。

---

## 3. 现状审计

### 3.1 delua 当前状态

**3.1.1 架构事实**

| 项 | 事实 | 证据 |
|----|------|------|
| 引擎 | AndroLua C 原生引擎，Lua 5.3.3，`libluajava.so` 静态链接 liblua，仅依赖 `-llog -ldl` | `build-scripts/jni/luajava/Android.mk`、`build-scripts/jni/lua/Android.mk` |
| ABI | 已为 arm64-v8a / armeabi-v7a / x86_64 预编译 16 个 `.so`（含 cjson、socket、crypt、md5、regex、xml、zip、zlib、luv 等） | `app/src/main/jniLibs/*/` |
| 运行链路 | EditorScreen "运行" → `EngineBridge.createEngineIntent()` → `EngineActivity`（继承 `LuaActivity`）→ `initLua()` → `doFile(main.lua)` 解释执行；结果经 `cacheDir/engine_result.json` 回传 | `androlua/.../engine/EngineBridge.kt`、`app/src/main/java/com/lioisme/delua/engine/EngineActivity.java` |
| 引擎初始化 | `LuaStateFactory.newLuaState()` 为纯 Java + JNI，**不依赖任何 Android Context**；`initLua()` 注册 `activity`/`this`/`print`/`set`/`call` 全局并配置 `luajava.luadir/luaextdir/luapath` | `LuaStateFactory.java:59`、`LuaActivity.java:1333` |
| 反射层 | `LuaJavaAPI.javaBindClass` 用 `Class.forName(name)`（**按调用者类加载器解析**）；方法/字段均 `setAccessible(true)` | `LuaJavaAPI.java:574/185/231/343` |
| 工程模型 | 目录式：`main.lua` + `init.lua`（Lua 形式的清单：appname/appver/packagename/icon/pages/user_permission/dependencies）+ 可选 `libs/`（jar/dex/so 运行时加载）+ `.alp` ZIP 交换格式 | `ProjectManager.kt`、`AlpManager.kt` |
| 打包器 | **未实现**。WHITEPAPER 阶段四/五（规划中）：luac 编译进 assets → 从零组装 ZIP 容器（二进制 manifest、classes.dex 注入、zipalign）→ v1/v2/v3 签名（BouncyCastle + APK Signing Block） | `WHITEPAPER.md:351-364` |
| 宿主清单 | 仅注册 `MainActivity` + `EngineActivity`，无 Service/Receiver | `app/src/main/AndroidManifest.xml` |
| SDK | minSdk 24 / targetSdk 36 / compileSdk 36；ABI 变体 x64 / arm64 | `app/build.gradle.kts:20-40` |
| 反 Xposed 代码 | **不存在**（对比原版，见 3.2.4） | 全仓库 grep `de.robv.android.xposed` 零命中 |
| 运行时 Lua 库 | 25 个脚本（`import.lua`/`console.lua`/`bin.lua`/`json.lua` 等）位于 IDE assets | `app/src/main/assets/lua/` |

**3.1.2 与 Xposed 场景相关的三个关键缺口**

1. **没有打包器**：用户工程目前只能在 IDE 进程内运行，无法产出独立 APK（普通应用或模块）。
2. **运行模型不匹配**：模块必须在"目标进程 + 无 Activity + 早于 Application 初始化"的环境下运行，而 delua 现有运行链路强依赖 `LuaActivity`（UI 宿主）与 IDE 的文件目录。
3. **清单工具链未落地**：二进制 XML 编解码（AndroLua Pro 用外部 `mao.dex` 实现，delua 规划自研）是 manifest 注入（含 xposed 元数据）的前提。

### 3.2 AndroLua Pro 打包机制审计（delua 运行环境的来源）

**3.2.1 打包器是 Lua 脚本，不是 Java 类**

- 入口：IDE 菜单 → `assets/main.lua` 的 `func.build` → `bin.lua` 的 `bin(path)` → `activity.newTask(binapk, ...)`。
- 核心流程 `binapk(luapath, apkpath)`（`docs/AndroLua_pro-master/app/src/main/resources/lua/bin.lua:53`，约 330 行）：

```
1. 以"宿主 AndroLua Pro APK"为模板：ZipInputStream 读模板 → ZipOutputStream 写新 APK
2. 编译 Lua：递归遍历工程目录，.lua → console.build → string.dump(...,true) 生成 .luac → assets/
3. 依赖分析 checklib()：正则扫描 require/import → 自动附带 lua/*.lua 模块与 lib*.so C 模块
4. 资源替换：icon.png → res/drawable/icon.png、welcome.png
5. 二进制 manifest 补丁：
   - compile "mao" → 外部二进制模块 mao.dex（含 AXmlDecoder）解码宿主二进制 XML
   - 字符串替换表：包名 / appname / 版本 / .alp 路径匹配 / user_permission 过滤（未勾选权限字符串替换为空）
   - xml.write 回写二进制 XML；versionCode / SDK 号小端 uint32 直接补丁
6. 其余文件原样复制（含宿主全套 classes.dex、res、META-INF）
7. 签名：compile "sign" → 外部二进制模块 sign.dex（apksigner.Signer），用内置固定密钥
   （assets/keys/：platform.pk8/x509.pem、testkey.pk8/x509.pem、keystore.ks）做 v1 JAR 签名
8. ZIP 注释写入全部文件 MD5（防篡改自校验）
9. LuaActivity.installApk() → FileProvider + ACTION_VIEW 安装
```

**3.2.2 对 delua 有继承价值的机制**：luac 字节码化、`checklib` 依赖自动收集、manifest 字符串级补丁思路、图标/资源替换。这些可直接平移进 delua 阶段四打包器。

**3.2.3 对 Xposed 场景致命的老打包器缺陷**

| 缺陷 | 后果 |
|------|------|
| **v1-only 签名 + 固定内置密钥** | ① `targetSdk≥24` 时 Android 7.0+ 拒绝安装（无 v2）；② 所有产物共享同一证书，无分发隔离 |
| **模板 = 宿主全套运行时** | 产物携带整个 IDE 运行时（LuaActivity/UI/编辑器类），方法数超限风险、体积大、且在目标进程中引入无意义的类 |
| **manifest 由"宿主模板 + 补丁"得到** | 无法从零注入新 `<meta-data>` 节点（AXmlDecoder 是解码-字符串替换-回写，不能增删结构）——而 Xposed 需要的是**新增节点** |
| `mao.dex`/`sign.dex` 是闭源外部二进制 | delua 必须自研对应能力（WHITEPAPER 已规划） |

**3.2.4 AndroLua Pro 原版的 Xposed 敌意（delua 已清除）**

原版 `docs/AndroLua_pro-master/app/src/main/java/com/androlua/LuaActivity.java` 约 L314-374：

- `check2()`：反射加载 `de.robv.android.xposed.XposedBridge`，置 `disableHooks=true`；
- `check3()`：实例化 `XposedHelpers` 并清空其 `fieldCache/methodCache/constructorCache`，弹窗"环境不安全"；
- `check1()` 及 onCreate 堆栈扫描：检测调用栈含 xposed 帧。

delua 的 `androlua` 模块中这些代码**全部不存在**（grep 零命中），且运行时脚本 `check.lua` 只是元表包装工具（与 Xposed 无关）。**无残留障碍。**

---

## 4. Xposed 模块技术要件（事实核查）

### 4.1 模块的构成（官方开发教程与 API 参考）

```
module.apk（本质 = 普通可安装 APK）
├── AndroidManifest.xml
│   └── <application>
│       ├── <meta-data android:name="xposedmodule"       android:value="true" />
│       ├── <meta-data android:name="xposeddescription"  android:value="..." />
│       ├── <meta-data android:name="xposedminversion"   android:value="93" />
│       └── <meta-data android:name="xposedscope" android:resource="@array/xposedscope"/>  （可选）
├── assets/xposed_init          ← 每行一个入口类全名（框架扫描此文件）
└── classes.dex / classes2.dex  ← 含实现 IXposedHookLoadPackage 的入口类
```

### 4.2 框架加载模型（决定运行期设计）

| 事实 | 说明 |
|------|------|
| 加载时机与位置 | `handleLoadPackage(lpparam)` 在**目标进程**内、目标 `Application.onCreate()` **之前**被调用 |
| 只加载 dex | 框架仅把模块的 dex 纳入一个以目标应用类加载器为父的模块类加载器；**模块 APK 里的 native 库不会被自动加载**，必须由模块自己从 APK 提取并 `System.load` |
| API 注入 | 框架把 `de.robv.android.xposed.*` 注入目标进程 App ClassLoader → 模块 ClassLoader 链可见 → **LuaJavaAPI 的 `Class.forName`（调用者加载器）天然可解析 Xposed API 类** |
| 目标类 | `lpparam.classLoader`（目标应用类加载器）用于加载/查找被 hook 的类 |
| 编译期约束 | API 必须 **compileOnly**，**严禁把 `de.robv.android.xposed.*` 类打进模块 dex**（否则 `IllegalAccessError`） |
| 上下文 | 模块无自己的 Context；必要时用 API 提供的 `AndroidAppHelper.currentApplication()`，但早期调用可能为 null |

### 4.3 生态与签名约束（决定打包策略）

| 约束 | 事实 |
|------|------|
| 框架选型 | 经典 Xposed 停止在 Android 8.1；**LSPosed**（Magisk+Zygisk/Riru）支持 Android 8.1–14，Vector fork 至 Android 17 beta；模块在管理器内按应用粒度勾选作用域（scope） |
| 无 root 替代 | **LSPatch**：把模块内嵌进目标 APK（PC 端重打包），使用同一套 `de.robv` API（或 `org.lsposed.lspatch` API） |
| 签名规则 | Android 7.0+：`targetSdk≥24` 必须 v2；Android 9：`targetSdk≥28` 必须 v2+；Android 11：`targetSdk≥30` 忽略 v1。**模块须 `targetSdk≥24` 且 v1+v2 双签名** |
| API 版本 | `de.robv.android.xposed:api:82`（兼容面最广）；新标准 libxposed（api:100/101）为 LSPosed 2.x/Vector 演进方向，存在破坏性变更 |

---

## 5. 可行性分析（逐项对照）

| # | Xposed 模块必需能力 | delua 现状 | 差距 | 工作量等级 |
|---|---------------------|-----------|------|-----------|
| 1 | 产出独立 APK | 打包器未实现（阶段四规划中） | **先实现基础打包器** | 高（已有蓝图） |
| 2 | manifest 新增 `xposedmodule` 等节点 | 二进制 XML 编解码规划中；AndroLua 的字符串替换法不能新增节点 | 需要**结构化解码-修改-编码**或节点级注入 | 中 |
| 3 | 模块 dex 内含入口类 + 桥类 | 无骨架 dex 概念；现成 `com.luajava`（约 16 文件）可直接裁剪进模块 | 编译一个**精简模板 dex**（一次编译，反复使用） | 低 |
| 4 | 目标进程内加载 Lua 引擎 | `libluajava.so` 静态链 Lua、仅依赖系统库、三 ABI 已就绪 | 仅需**自提取 + `System.load`** 的引导代码 | 低 |
| 5 | 无 Activity 的 headless 运行 | `initLua` 强绑定 Activity；`import.lua:13` 要求 `activity or service` 全局 | 轻量 `LuaContext` 实现 + 引导脚本 | 中 |
| 6 | hook 回调桥（Java↔Lua） | AndroLua 有 `JavaFunction`/`LuaInvocationHandler` 回调机制可复用 | 固定桥类 `XposedLuaHook extends XC_MethodHook` | 中 |
| 7 | 编译 Lua → luac 进 assets | AndroLua 的 `string.dump` 方案可直接复用 | 无（打包器实现时自带） | — |
| 8 | v1+v2 签名 | WHITEPAPER 规划 v1/v2/v3（BouncyCastle + APK Signing Block） | 按规划实现即可 | 高 |
| 9 | 类加载器语义匹配 | `Class.forName`（调用者加载器）+ `setAccessible(true)` **天然匹配** | 无差距 | — |
| 10 | 反 Xposed 障碍 | 原版 check1/2/3 已清除 | 无差距 | — |

**判定**：可行性成立的四个支柱（引擎可注入性、反射语义、API 兼容性、无框架级障碍）**全部为真**；剩余差距集中在 delua 尚未落地的打包器与两个新增组件（headless 引导 + 桥类）上。

---

## 6. 需要的支持：完整链路设计

### 6.1 总览

```
┌─────────────────────────── 打包期（delua 构建系统新增"Xposed 模块模式"） ───────────────────────────┐
│                                                                                                      │
│  Lua 工程（main.lua / init.lua / libs/）                                                              │
│    │ 1. 工程类型标志：init.lua 增加 xposed = { description=..., minversion=... } 或独立工程模板        │
│    ▼                                                                                                 │
│  2. Lua 编译：console.build（string.dump）→ .luac → assets/scripts/                                   │
│    │   依赖收集：checklib 式扫描 → 附带 lua 运行时精简集（import.lua/xposed.lua 等）                   │
│    ▼                                                                                                 │
│  3. 骨架 dex：精简模块模板（一次预编译，打包时直接注入）                                                │
│    ├── com.luajava.*            （LuaState/LuaStateFactory/LuaJavaAPI/LuaObject/LuaFunction/          │
│    │                              LuaTable/LuaException 等，约 16 文件，由 androlua 模块裁剪）         │
│    ├── com.delua.xposed.Entry   （实现 IXposedHookLoadPackage，compileOnly 编译）                     │
│    ├── com.delua.xposed.LuaXposedBridge （引导 + 桥）                                                 │
│    ├── com.delua.xposed.XposedLuaHook   （extends XC_MethodHook 固定桥类）                            │
│    └── com.delua.xposed.HeadlessLuaContext（轻量 LuaContext：getClassLoaders/getLibrarys）            │
│    ▼                                                                                                 │
│  4. AndroidManifest（结构化二进制 XML 编解码器）                                                       │
│    ├── package = 模块包名；targetSdk ≥ 24（建议 28+）；minSdk 24                                      │
│    ├── <application> 内新增 meta-data：xposedmodule / xposeddescription / xposedminversion            │
│    └── 无 Activity 亦可（headless）；如需配置页可带一个原生 Activity                                  │
│    ▼                                                                                                 │
│  5. assets/xposed_init  ← 写入 com.delua.xposed.Entry                                               │
│    ▼                                                                                                 │
│  6. lib/{arm64-v8a,armeabi-v7a,x86_64}/libluajava.so（+ 用户用到的 C 模块如 libcjson.so）             │
│    ▼                                                                                                 │
│  7. 对齐（zipalign）+ 签名 v1 + v2（用户密钥 / 自动生成）→ module.apk                                 │
│    ▼                                                                                                 │
│  8. 安装 → LSPosed 管理器激活 → 勾选目标应用 scope → 重启/重开目标应用                                 │
│                                                                                                      │
└──────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────── 运行期（目标进程内，headless） ───────────────────────────┐
│                                                                                       │
│  1. 框架实例化 Entry → handleLoadPackage(lpparam)                                      │
│  2. 定位模块 APK 路径：(PathClassLoader) Entry.class.getClassLoader() → getPath()      │
│     （注意不是 lpparam.classLoader —— 那是目标应用的加载器）                            │
│  3. 从模块 APK 按 Build.SUPPORTED_ABIS 提取 libluajava.so → 目标应用 filesDir/cacheDir → System.load │
│  4. 延迟初始化（首次 hook 回调触发时再建引擎，规避 Application 未创建、                    │
│     AndroidAppHelper.currentApplication() 为 null 的问题）：                             │
│     LuaStateFactory.newLuaState() → openLibs()                                          │
│     → 全局：pushJavaObject(HeadlessLuaContext) 为 activity/service                      │
│     → luajava.luadir/luaextdir/luapath → LuaPrint（Context 用 currentApplication 兜底）  │
│  5. 读模块 APK assets/scripts/main.luac → LloadBuffer → pcall                          │
│  6. main.lua 里 require "xposed" → 注册 hook（XposedHelpers.findAndHookMethod(...,       │
│     XposedLuaHook.new(callback))）；hook 触发时任意线程 → Lua 互斥锁 → 回调 Lua 函数      │
│                                                                                       │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

### 6.2 打包期支持清单（delua 需新增/落地的能力）

| # | 能力 | 说明 | 依赖 |
|---|------|------|------|
| A | **基础打包器落地**（WHITEPAPER 阶段四/五） | luac 编译、ZIP 容器组装、zipalign | 先决条件，所有模式共用 |
| B | **结构化二进制 manifest 编解码** | 从 AndroLua 的"字符串替换"升级为可增删节点（解码→修改→编码），Xposed 元数据注入的前提 | 自研（AXml 格式公开文档） |
| C | **Xposed 模块构建模式** | 工程类型选项；产物布局切换（无启动 Activity、`assets/xposed_init`、meta-data 注入、`lib/` ABI 目录） | A+B |
| D | **精简模块骨架 dex 工程** | 独立 Gradle 子工程（如 `xposed-stub/`），一次编译产出注入用 dex；`com.luajava` 裁剪 + 桥类；API 以 `compileOnly` 引入 | 新增 |
| E | **v1+v2 签名** | 按 WHITEPAPER 规划（BouncyCastle + APK Signing Block）；v2 强制校验 | 打包器 |
| F | **过渡期 PC 工具链**（可选，见 6.6） | 不等打包器，先把链路跑通 | 新增 |

### 6.3 运行期支持清单（新增组件）

**6.3.1 入口类 `com.delua.xposed.Entry implements IXposedHookLoadPackage`**（Java，编译期 `compileOnly de.robv.android.xposed:api:82`）

```java
public class Entry implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        // 1) 保存 lpparam；按 scope 过滤（包名匹配）
        // 2) 解析模块 APK 路径：((PathClassLoader) getClass().getClassLoader()).getPath()
        // 3) 提取 libluajava.so（按 ABI）→ System.load（幂等，延迟到首次回调亦可）
        // 4) 注册首个 hook：Application.attach → 引擎就绪后执行 main.luac
        //    （延迟策略规避 handleLoadPackage 阶段无 Context 的问题）
    }
}
```

**6.3.2 桥类 `XposedLuaHook extends XC_MethodHook`**（固定类，避免在目标进程用 DexMaker 生成子类）

```java
public class XposedLuaHook extends XC_MethodHook {
    private final LuaFunction before;   // Lua 函数，可空
    private final LuaFunction after;
    @Override protected void beforeHookedMethod(MethodHookParam param) {
        // 构造 Lua 参数表（thisObject/args/result/returnEarly 等）→ 互斥锁内 pcall(before)
    }
    @Override protected void afterHookedMethod(MethodHookParam param) { /* 同上 */ }
    // 静态工厂：public static XposedLuaHook create(LuaState L, int beforeIdx, int afterIdx)
    // Lua 侧经 luajava 调用（AndroLua 既有 LuaFunction 传递机制可直接复用）
}
```

**6.3.3 headless 环境**：轻量 `LuaContext` 实现（提供 `getClassLoaders()/getLibrarys()/getLuaState()/doFile()` 等 `import.lua` 依赖的方法），作为 `activity` 全局注入；UI 相关全局（`setContentView`/`loadlayout`）在模块模式下不注册。

**6.3.4 线程模型**：hook 回调可能在任何线程触发。方案：单 LuaState + 互斥串行（LuaJavaAPI 已对 L 加 synchronized，但脚本回调需要外层锁保证可重入安全），文档明示限制：回调内不要阻塞、不要嵌套调用同一 hook。

### 6.4 Lua 侧 API（新增 `assets/lua/xposed.lua`）

```lua
local xp = require "xposed"

-- 注册一个方法 hook（参数类型自动探测，失败可回退 findAndHookMethod 显式类型）
xp.hookMethod("com.example.Target", xp.getTargetClassLoader(), "methodName",
    function(param)
        -- param.thisObject / param.args / param.result
        param.args[1] = 0
        param:setResult("modified")
    end)

xp.findClass("com.example.Hidden")            -- XposedHelpers.findClass
xp.getField("com.example.Target", cl, "fieldName", instance)
xp.setField(...) / xp.callMethod(...)
xp.log("loaded in " .. xp.getPackageName())   -- XposedBridge.log → LSPosed 日志
```

实现要点：`findAndHookMethod` 的静态调用经 `luajava.bindClass("de.robv.android.xposed.XposedHelpers")` + `class.static:findAndHookMethod(...)`（luajava 既有的静态方法反射路径），`XposedLuaHook` 经 `luajava.new` 构造。`de.robv` 类在目标进程对模块 ClassLoader 可见（4.2 节），故 `bindClass` 可直接解析。

### 6.5 需要引入的外部依赖

| 依赖 | 用途 | 方式 | 注意 |
|------|------|------|------|
| XposedBridge API（`de.robv.android.xposed:api:82` 或同源 jar） | 入口类/桥类编译 | **compileOnly**（Bintray 官方仓库已下线，用本地 jar 或镜像） | **禁止打进模块 dex**；`libs/` 目录会被自动打包，须用 `compileOnly files(...)` |
| （可选演进）libxposed api 100/101 | LSPosed 2.x/Vector 新标准 | compileOnly | 破坏性变更，一期不做 |
| （可选）LSPatch api | 无 root 分发 | 编译期 | 若走 LSPatch 路线需按 LSPatch 模板输出 |
| 用户侧 | 目标设备 Magisk + Zygisk + LSPosed | 运行环境 | Android 8.1–14（Vector 至 17） |

### 6.6 过渡方案（不等打包器，立刻可用的半自动链路）

delua 打包器未完成前，可先提供 **PC 端 Gradle 模板工程**（`tools/xposed-module-template/`）：

```
delua 导出 .alp ──► 解包 ──► PC 脚本：
  1) .lua → luac（用 delua 同版引擎工具或 luac 5.3.3）
  2) 拷贝至模板 assets/scripts/
  3) 生成 AndroidManifest（模板内固定结构 + meta-data）
  4) 拷贝 libluajava.so × 3 ABI
  5) ./gradlew assembleRelease（模板内含 XposedBridgeApi.jar compileOnly）
  6) apksigner v1+v2 签名
  7) adb install / 分享
```

该模板同时是 6.2-D 骨架 dex 工程的雏形，后续可被 delua 打包器直接复用（Lua 开发与打包一次自动化）。

---

## 7. 风险与限制

| # | 风险 | 说明 | 缓解 |
|---|------|------|------|
| 1 | **检测/对抗** | 目标进程内存与 `/proc/self/maps` 会出现 `libluajava.so`；Lua 堆字符串可被扫描 | 不适用于安全对抗类目标；`dlopen` 后 `unmap` 复杂度高，不建议 |
| 2 | **时机约束** | `handleLoadPackage` 阶段无 Context、目标 Application 未创建 | 延迟初始化 + `Application.attach` 钩子后再建引擎 |
| 3 | **线程/重入** | hook 回调多线程触发，Lua state 非线程安全 | 全局互斥串行；文档限制回调内阻塞与嵌套 |
| 4 | **签名** | v1-only 或固定密钥 → 现代设备不可装 / 无分发隔离 | v1+v2 双签名；每模块独立密钥（delua 规划已有） |
| 5 | **64K 方法数** | 若把整个 androlua 运行时打进模块 dex | 用精简骨架 dex（com.luajava + 桥类） |
| 6 | **C 模块依赖** | 用户 `require` cjson/socket 等 → 对应 .so 须一并提取加载 | 打包期依赖收集（checklib 机制）+ 运行期统一提取器 |
| 7 | **Android 14+ JNI 约束** | `luajava.c` 受新版 ART JNI 限制影响 | 已在 WHITEPAPER 风险表登记，需在 API 34+ 设备验证 |
| 8 | **引擎版本兼容** | 模块场景跨应用进程，需与目标应用 32/64 位进程匹配 | 三 ABI 齐备 + 按 `Build.SUPPORTED_ABIS` 提取 |
| 9 | **UI 能力丧失** | headless 模式下 `activity` 全局无 UI；loadlayout 类脚本 API 不可用 | 明确模块模式的 API 子集；配置界面用原生 Activity 模板 |
| 10 | **classloader 陷阱** | 模块 APK 路径须从模块自身类加载器获取，而非 `lpparam.classLoader` | 引导代码固化此逻辑并注释 |
| 11 | **libxposed 演进** | 未来框架可能弃用 de.robv API | 桥类只依赖最稳定子集，预留抽象层 |

---

## 8. 实施路线图

| 里程碑 | 内容 | 产出 | 相对工作量 |
|--------|------|------|-----------|
| **M1**（前置） | 基础打包器落地（WHITEPAPER 阶段四/五）：luac → assets、ZIP 组装、二进制 manifest 编解码、zipalign、v1+v2 签名 | 普通应用打包可用 | 高（已有蓝图） |
| **M2** | `tools/xposed-module-template` PC 模板 + 骨架 dex（com.luajava 裁剪 + Entry + XposedLuaHook + HeadlessLuaContext） | 手工链路跑通首个 Lua hook 模块 | 中 |
| **M3** | delua 打包器"Xposed 模块模式"：工程类型、meta-data 注入、xposed_init、ABI lib/、scope 配置 UI | IDE 一键打包模块 | 中 |
| **M4** | `xposed.lua` 运行库完善 + 线程模型/延迟初始化加固 + 日志与诊断（模块内 logcat、LSPosed 日志引导） | 开发体验完善 | 中 |
| **M5** | 测试矩阵：LSPosed 1.9.x（8.1–14）、Vector（15–17）、LSPatch、三 ABI、目标 32/64 位进程、Android 14+ JNI 验证 | 兼容性证据 | 低-中 |

## 9. 最终结论

**能，但有前提条件。** delua 的工程开发（Lua 脚本 + init.lua 清单 + libs 依赖）+ 按 WHITEPAPER 蓝图实现的打包器，在新增"Xposed 模块构建模式"（骨架 dex、manifest 元数据注入、headless 引导、v1+v2 签名）后，可完整支撑 Xposed/LSPosed 模块的「开发 → 打包 → 激活 → 运行」全链路。核心有利因素（引擎单 `.so` 可注入、luajava 反射语义与 Xposed 类加载器结构天然匹配、原版反 Xposed 代码已被清除、三 ABI 预编译就绪）均已核实成立；主要工作量不在"能否"，而在 delua 尚未实现的打包器本身与两个新增桥接组件。

---

## 附录 A：关键文件索引

| 文件 | 作用 |
|------|------|
| `androlua/src/main/java/com/androlua/LuaActivity.java:1333` | `initLua()`：引擎全局注册（模块 headless 引导的参照） |
| `androlua/src/main/java/com/luajava/LuaStateFactory.java:59` | `newLuaState()`：纯 Java 初始化，无 Context 依赖 |
| `androlua/src/main/java/com/luajava/LuaJavaAPI.java:574/185/343` | `Class.forName`（调用者加载器）+ `setAccessible(true)` |
| `build-scripts/jni/luajava/Android.mk` | `LOCAL_STATIC_LIBRARIES := lua`：单 `.so` 可注入的根基 |
| `app/src/main/assets/lua/import.lua:13` | `local luacontext = activity or service`：headless 需提供轻量替代 |
| `docs/AndroLua_pro-master/app/src/main/resources/lua/bin.lua:53` | `binapk`：AndroLua Pro 设备端打包器（机制参照） |
| `docs/AndroLua_pro-master/app/src/main/java/com/androlua/LuaActivity.java:314-374` | 原版反 Xposed 代码（delua 已清除） |
| `WHITEPAPER.md:351-364` | 阶段四/五：delua 打包器规划（本项目的前置条件） |
| `app/build.gradle.kts:20-40` | minSdk 24 / targetSdk 36 / ABI 变体 |

## 附录 B：术语

| 术语 | 含义 |
|------|------|
| headless | 无 UI 组件（Activity/View）的进程内运行模式 |
| luac | Lua 字节码（`string.dump` 产物） |
| compileOnly | 编译期可见、不打包进产物的依赖 |
| scope | LSPosed 中模块生效的目标应用集合 |
| Zygisk | Magisk 提供的 Zygote 注入机制，LSPosed 的运行底座 |
