# delua × AndroLua Pro 工程 / 库 / 插件 / 模块系统全面一致性审计报告

> 审计对象：`delua`（`:androlua` + `:app` + `:editor` 三模块）vs `docs/reference/androlua/AndroLua_pro-master/`（AndroLua Pro 4.4.2 源码）
> 审计方法：全量源码逐文件阅读 + 全仓库调用链 grep 验证 + 关键文件逐字节比对（hash/diff）
> 审计日期：2026-07-31
> 审计人：opencode（独立复核，非复用既有审计文档结论；既有文档 `ANDROLUA_FEATURE_PARITY_AUDIT.md` 的部分结论已随代码演进过期，本报告以当前代码为准）

---

## 1. 结论速览

- **引擎运行时层已 100% 对齐**：`com.luajava` 全部 16 类、`com.android.cglib.*` 全链、`com.androlua` 主体 42+ 类逐字节一致；28 个 Lua 运行时脚本逐字节一致（另增 3 个 layouthelper 族脚本）；15 个原生 `.so` 与 pro 主 `Android.mk` 构建集完全一致（delua 还多出 x86_64 ABI）；`package.path` / `package.cpath` / `libsloader` / `dexCache(MD5)` / `libCache(实例级)` / `unApk(MD5 增量)` 等机制全部同构。
- **"工程内自行管理"的 pro 核心模型在 delua 中完整保留**：`<project>/libs/` 自动加载（`LuaDexLoader.loadLibs`）、`require` 链、工程根探测（`prjCache` 上溯）、`.alp` 导入导出、`newActivity` 页面机制均原样可用。
- **delua 的"统一管理"是叠加层**：`common/libs` + `common/modules` + `registry.json` + `delua.json` + `plugin.json`。其与运行时的唯一桥梁是**依赖物化**（`materializeProjectDependencies`），该链路 8 个环节当前全部连通（历史断链已修复），但存在 6 处"写-读不对称"半成品与 10 项工程级遗留，另有 4 处与 pro 生态的行为偏差需要决策收口。

---

## 2. AndroLua Pro 基准模型（一切皆"目录 + Lua 脚本"）

Pro 没有独立的工程/插件管理 Java 类。**工程、插件、IDE 页面是同一套机制**：磁盘上的一个目录 + `init.lua`（元数据）+ `main.lua`（入口），统一由 `LuaActivity` 经 URI `file://...` 启动，每个页面独立 `LuaState`。唯一 Java 侧管理类是 `LuaDexLoader`（管 dex/so 库），且无全局注册表（grep `plugin|PluginsManager|loadPlugin|setPackagePath` 于 pro Java 源码零命中）。

### 2.1 工程系统（pro）

- 位置：`/sdcard/AndroLua/project/<name>/`（`luajava.luaextdir .. "/project/"`，`assets/main.lua:755`）。
- 结构：`init.lua`（appname/appver/packagename/user_permission）+ `main.lua` + 可选 `icon.png`/`welcome.png`/`layout.aly`/`libs/`。
- 打开/运行：路径经 `Intent.setData(Uri.parse("file://" + path))` 传递（`LuaActivity.java:1203,1256`），`onCreate → getLuaPath()` 从 data URI 取路径并**上溯查找工程根**（main.lua+init.lua 同时存在的目录，静态缓存 `prjCache`，`LuaActivity.java:470-507`）。
- 运行：`activity.newActivity(luaproject .. "main.lua")`（`assets/main.lua:1268-1278`）；子页面走 `startActivityForResult`，脚本 `onResult` 回调。
- 导入/导出：`.alp` = zip；`getalpinfo` 用 `LuaUtil.readZip(path,"init.lua")` 读元数据（`main.lua:1104-1121`），`importx` 解压（`main.lua:1135-1214`），`export` 打包（`main.lua:1015-1102`）；`.alp` 内 `mode="plugin"` 或文件名含 `_plugin` → 导入到 `plugin/` 目录（`main.lua:1127-1131`）。

### 2.2 库系统（pro，工程内自行管理）

- **无全局/集中式库注册表**（grep 证实）。库 = `<工程>/libs/` 目录约定 + 每 Activity 实例一份 `LuaDexLoader`。
- `loadLibs()`（`LuaDexLoader.java:70-82`）：扫 `<luaDir>/libs/`，`.so` → `loadLib`（复制到私有目录 `getDir(fn)`，记入**实例级** `libCache`，`LuaDexLoader.java:84-102`）；其余 → `loadDex`。
- `loadDex()`（`LuaDexLoader.java:109-142`）：①路径字面查 **static** `dexCache` → ②按包名 `loadApp`（`PackageManager`）→ ③补 `.dex`/`.jar` 后缀 → ④以文件内容 **MD5 为缓存键** → ⑤`new LuaDexClassLoader(path, odexDir, nativeLibraryDir, 宿主classLoader)`。jar 资源经反射 `AssetManager.addAssetPath` 并入（`loadResources`，:144-161）。
- Lua 侧消费（`resources/lua/import.lua`，503 行）：`libsloader` 注入 `package.searchers` 尾部，用 `package.loadlib(绝对路径, "luaopen_"..名)` 加载工程 so（:17-24，先于 asset loader——`jni/socket/Android.mk:3-5` 注释印证）；`import "dex名:类名"` → `luacontext.loadDex(dexname).loadClass(...)`（:104-114）；`import_dex_class` 遍历 `getClassLoaders()`（dexList）（:49-64）；全局 `__index` 自动补包（`env_import`，:139-208）。
- **"安装"= 把 jar/dex/so 放进工程 `libs/`**，`.alp` 导出即携带，导入即生效。

### 2.3 插件系统（pro）

- 插件 = `/sdcard/AndroLua/plugin/<name>/`：`init.lua`（appname/appver/packagename/theme/developer/description/debugmode/user_permission）+ `main.lua` + 可选 `icon.png`。
- 列表页 `assets/plugin/main.lua`（54 行）：`File(plugindir).list()` 列子目录 → 逐个 `loadfile(dir/init.lua,"bt",app)()` **以空表为环境**读元数据（pcall 失败即跳过）→ `LuaAdapter + item.aly` 渲染。
- 运行：`activity.newActivity(plugindir.."/"..name.."/main.lua", {luadir, luapath})`（`plugin/main.lua:51-53`）——插件入口**恒为 main.lua**，并接收当前工程上下文 `{luadir, luapath}` 作为 `...` 参数。
- 无安装/卸载/版本解析概念；`mode="plugin"` 的 .alp 导入到 `plugin/`。

### 2.4 模块系统（pro）

- 目录：`luaMdDir = getDir("lua")`（`LuaApplication.java:186`）。
- `package.path` 精确串 = `工程/?.lua;工程/lua/?.lua;工程/?/init.lua;` + `files/lua/?.lua;files/lua/lua/?.lua;files/lua/?/init.lua;`（`LuaApplication.java:189` + `LuaActivity.java:238` 前缀，`initLua` 用 `setField("path")` 注入，:1463-1465；**无 `setPackagePath` JNI**）。
- `package.cpath` = `nativeLibraryDir/lib?.so;app_lib/lib?.so`（`LuaApplication.java:187`）。
- 模块提取：`Welcome.unApk("lua", luaMdDir)` 从自身 APK 解包，**MD5 + 长度比对增量跳过**（`Welcome.java:199-201,212-254`）。
- 扩展加载器：`LuaAssetLoader`（assets/x/y.lua，`LuaAssetLoader.java:25-26`）+ `alyloader`（`.aly` 布局表，`loadlayout.lua:41-60`）。

### 2.5 IDE 与首次启动（pro）

- 首次启动：`Welcome` → 权限批量请求 → `unApk("assets", files/)`（IDE 全部 Lua 页面）+ `unApk("lua", luaMdDir)` → 启动 `Main`（singleTask）→ 以普通 `LuaActivity` 运行 `files/main.lua`——**IDE 与用户工程使用完全相同运行时**。
- 打包（`resources/lua/bin.lua`，398 行）：以自身 APK 为模板重写 manifest（`mao.res.AXmlDecoder`）、`.lua` 编译为 luac、按 `require/import` 正则分析打入哪些 `.so` 与 lua 模块（`checklib` :131-174）、外部 `mao/sign` jar（**不在仓库中**）签名；`assets/keys/`（AOSP keystore/testkey/platform）供 debug 签名。
- Manifest：36 项权限、`largeHeap` + `persistent`、`Main` 5 组 .alp VIEW/EDIT filter、`LuaActivity` 4 组 `.lua` filter + `androlua://com.androlua` scheme、`LuaActivityX`（intoExisting 多窗口）、`LuaService`、`LuaAccessibilityService`、`FileProvider`（自定义 `android.content.FileProvider`，authority=包名）。

---

## 3. delua 工程结构审计

### 3.1 模块拓扑

| 模块 | 内容 | 与 pro 的关系 |
|---|---|---|
| `:androlua`（库模块） | `com.androlua`（42+）、`com.luajava`（16）、`android.*`/`com.nirenr.*`/cglib（~300）、Kotlin 管理层（12 文件）、assets（loadlayout2/3、xml2table） | pro `app/src/main/java` 的运行时部分 + delua 自创管理层 |
| `:app`（宿主 APK） | Compose IDE（20 个 Kotlin 屏/管理器）、`EngineActivity`、assets（lua×28、luadoc、plugin.json、samples×4、textmate）、jniLibs（15×3 ABI） | pro 的 IDE（Lua 页面）被 Compose 宿主替换 |
| `:editor`（库模块） | sora-editor + TextMate Lua 高亮/诊断/补全 | pro 的 textwarrior 编辑链被替换（`LuaEditor` 类不再存在） |

- pro 的"IDE = Lua 工程"模型与 delua 的"纯编辑器模块 + 宿主运行时"同构：pro 的 `assets/` 对应宿主资源，`files/` 运行时目录对应宿主数据目录。
- R1 边界：`:editor` 对 `luajava|androlua|loadLibrary` 零命中（含注释），符合 AGENTS.md。

### 3.2 磁盘布局（delua 实况）

```
/storage/emulated/0/AndroLua/            ← luaExtDir（LuaApplication.java:165-179，与 pro 同）
├── project/<name>/                      ← 工程（init.lua + main.lua + libs/ + lua/）
├── plugin/<name>/                       ← 插件（plugin.json 或 init.lua + main.lua）
└── common/
    ├── libs/<name>/{metadata.json, <name>-<version>.jar|.dex}    ← 全局库仓库
    ├── modules/<name>/{metadata.json, *.lua}                      ← 全局模块仓库
    └── registry.json                     ← 全局索引（只写不读）
data/data/<pkg>/files/lua/                ← luaMdDir（内置 28+3 脚本）
data/data/<pkg>/files/odex|lib|lua/       ← pro 同款私有目录
```

---

## 4. 工程系统比对

| 维度 | pro | delua | 判定 |
|---|---|---|---|
| 工程位置/结构 | `project/<name>/` init.lua+main.lua+icon+libs | 同；模板额外写 `pages/user_permission/dependencies` | ✅ 兼容 |
| 工程根探测 | `prjCache` 上溯（LuaActivity.java:470-507） | 同源码保留（LuaActivity.java:361-398）；EngineActivity 场景直接 `getLuaPath()` 覆写 | ✅ |
| 运行入口 | `newActivity(project/main.lua)` | `RunEngineLauncher` → `EngineBridge`（extra: projectDir/mainFile）→ `EngineActivity` | ✅ 实现路径不同，语义等价 |
| 子页面 | `newActivity(path, arg)` + `onResult` 回调 + `LuaActivityX` 多窗口 | 同源码保留（LuaActivity.java:1043-1165）；`LuaActivityX` 已注册 manifest | ✅ |
| .alp 导入 | zip → project/ 或 plugin/（按 mode） | `AlpManager.import` → **一律 project/**（AlpManager.kt:76-84）；插件型 .alp 走 PluginScreen "+" 手动装 | ⚠️ 无 mode 分流 |
| .alp 导出 | `export` 可编译 .luac + `.using` 清单 | `AlpManager.export` 原始 zip（ZipUtil.kt:24-44），无编译/清单 | ⚠️ 简化 |
| 元数据读取 | `loadfile(init.lua,"bt",app)()` 环境块执行 | 正则解析 appname/packagename/icon（ProjectManager.kt:46-51）+ initENV 环境块执行（LuaActivity.java:1401-1440，同 pro） | ✅ 双通道 |
| 工程列表 | Lua 页 `loadfile` pcall 扫描 | Compose `ProjectManager.listProjects`，**要求 main.lua+init.lua 齐备**（ProjectManager.kt:28） | ✅ 语义等价 |
| 独立 .lua 打开 | 文件管理器直接拉起 `LuaActivity`（file://*.lua filter） | MainActivity 收养为单文件工程（MainActivity.kt:123-157）再编辑 | ⚠️ 行为差异（见 §9-4） |
| 删除工程 | 有（Lua 页） | `deleteProject` 存在但 **UI 零调用**（ProjectManager.kt:211-217） | ❌ 死代码 |
| delua.json 声明 | 无此概念 | 读路径存在（DependencyManager.kt:153-156），**全仓无写入端** | ❌ 半成品 |

**工程系统结论：核心机制（结构、探测、运行、页面、alp 容器）与 pro 一致；差异集中在"插件型 .alp 分流"与"导出编译"两个 pro 生态能力未接入。**

---

## 5. 库系统比对

### 5.1 运行时（逐字节一致 ✅）

| 组件 | pro | delua | 判定 |
|---|---|---|---|
| `LuaDexLoader` / `LuaDexClassLoader` | 172/31 行 | 同源（172/31 行） | ✅ |
| `loadLibs`（libs/ 自动加载） | :70-82 | 同 | ✅ |
| `dexCache` static MD5 键 / `libCache` 实例级 | :19-21 | 同 | ✅ |
| `loadResources` AssetManager 合并 | :144-161 | 同 | ✅ |
| `import "dex:类"`、`import_dex_class`、`libsloader`、`import.compile` | import.lua:17-64,104-114,211-213 | 同（逐行） | ✅ |
| cpath（nativeLibDir/lib?.so） | LuaApplication.java:187 | 同（:190） | ✅ |
| `LuaActivity.loadLib(name)` | :733-747 | 同（:624-638） | ✅ |

### 5.2 管理面（delua 自创层）

- 仓库：`common/libs/<name>/{metadata.json, <name>-<version>.jar|.dex}`；安装（DependencyManager.kt:28-53）、卸载带引用拦截（:55-64,231-238）、列表（LibraryRegistry.kt:9-15）。
- **物化链路（8 环全通，核心决策"全局仓库 = 安装面 / 工程目录 = 运行时面"）**：

```
ProjectDependenciesScreen 勾选(172-179) → saveDependencies(67-87)
→ DependencyManager.setProjectDependencies(160-166) → init.lua 追加 dependencies={libs={"x"},modules={"y"}}(327-351)
→ getProjectDependencies(150-158) → validateDependencies(168-180)
→ RunEngineLauncher(23-25) 运行前物化 → materializeProjectDependencies(189-223)
   libs → <project>/libs/*.jar|.dex（findJarFile:201, copyTo:203）
   modules → <project>/lua/*.lua（+ 含 init.lua 时 <project>/lua/<name>/，:206-222）
→ EngineBridge extras{projectDir,mainFile} → EngineActivity.getLuaPath(22-25)
→ LuaActivity.loadLibs()(233-234) + package.path 前缀(230) → import/require 命中
```

### 5.3 库系统差距（❌/⚠️）

| # | 差距 | 证据 | 影响 |
|---|---|---|---|
| L1 | **common 仓库不支持 `.so`**：`findJarFile` 只认 .jar/.dex，安装 .so 会被校验判缺失并阻止运行 | LibraryRegistry.kt:22-24,57-62；DependencyManager.kt:170-173,191-195 | pro 的 libs/ 可直接放 .so（gl/cjson 等），delua 全局库做不到 |
| L2 | **物化无清理**：取消声明/换版本后 `<project>/libs` 旧 jar 永久残留，`loadLibs` 继续加载 | DependencyManager.kt:197-205 | 隐性行为不一致（运行了已卸载的库） |
| L3 | 同库多版本孤儿 jar：重装生成 `<name>-<newver>.jar`，旧文件残留，`findJarFile` 取目录第一个（顺序不定） | LibraryRegistry.kt:22-24 | 版本语义不可靠 |
| L4 | `registry.json` 只写不读（`readRegistry` 零调用） | DependencyManager.kt:249-256,258-285 | 索引形同虚设 |
| L5 | `validateDependencies` 只查目录/文件存在性，不验 jar 有效性/ABI | DependencyManager.kt:168-180 | 坏包运行时才炸 |
| L6 | 库元数据只读 `metadata.json`；`getLibraryInfo` 零调用 | DependencyManager.kt:68-71 | 死代码 |
| L7 | jar 放 `libs/` 时 `import "jar名:类"` 冒号语法不认（只解析工程根或已缓存字面名）—— **pro 同款行为** | LuaDexLoader.java:109-123（两侧一致） | 非差距，但需文档化（`libs/` 的 jar 用 `import "com.foo.Bar"` 或 `loadDex` 显式加载） |

---

## 6. 插件系统比对

| 维度 | pro | delua | 判定 |
|---|---|---|---|
| 插件结构 | `plugin/<name>/` init.lua 元数据 + main.lua + icon | 同；元数据优先 `plugin.json`（name/version/description/main/author），无 json 时 fallback init.lua（appname/appver/description/author，正则 `fieldOf`） | ✅ 超集（兼容 pro 生态插件） |
| 扫描 | `loadfile(init.lua,"bt",app)()` pcall 跳过 | `PluginManager.scan` 一级子目录 + `loadPlugin`（PluginManager.kt:20-34,59-78） | ✅ |
| 运行入口 | `newActivity(dir/main.lua, {luadir, luapath})`，**恒 main.lua** | `PluginScreen` 卡片 → `RunEngineLauncher.launch(plugin.dir, plugin.main)`；**plugin.json 默认/示例 main="init.lua"**（PluginManager.kt:12,69；assets/plugin/plugin.json "main":"init.lua"） | ❌ 语义偏差（见 P0-1） |
| 运行上下文 | 插件收到 `{luadir, luapath}` 变长参数 | EngineBridge 仅 projectDir/mainFile 两个 extra，`...` 为空 | ❌ 缺失 |
| 安装 | .alp `mode="plugin"` → plugin/；无 UI | `installFromAlp`（PluginManager.kt:38-57）+ PluginScreen "+"（GetContent → install）✅ 已接线 | ✅（入口不同） |
| 依赖物化 | 无此概念（插件=工程） | 插件目录同样走 `materializeProjectDependencies` | ✅ 增强 |
| 版本/卸载 | 无 | 卸载=删目录；无版本解析 | ✅ |

**插件系统结论：pro 生态插件（init.lua 风格）可被扫描、可运行；但入口语义与上下文参数两处不兼容，`.alp(mode=plugin)` 导入分流缺失。**

---

## 7. 模块系统比对

| 维度 | pro | delua | 判定 |
|---|---|---|---|
| package.path | 工程前缀 + files/lua 三模式 | 同（LuaActivity.java:230 + LuaApplication.java:192 逐字符同构） | ✅ |
| package.cpath | nativeLibDir + app_lib | 同 | ✅ |
| 内置脚本提取 | `unApk("lua")` MD5+长度增量 | `extractLuaScripts` **MD5 比对覆盖**（LuaApplication.java:196-232） | ✅（已修复历史"存在即跳过"） |
| 内置脚本集合 | resources/lua 28 文件 | app assets/lua 28 文件**逐字节一致** + androlua assets 另加 loadlayout2/3、xml2table（layouthelper 族） | ✅ 超集 |
| 工程内模块 | 工程目录任意 .lua 即可 require | 同 + 物化模块到 `<project>/lua/` | ✅ |
| 全局模块仓库 | 无 | `common/modules/<name>/` 安装/卸载/引用拦截（DependencyManager.kt:103-141） | ➕ 增强 |
| 子目录模块 | 无此概念（模块=文件） | **安装与物化只处理顶层 .lua**（:114-116,212-219）——socket 式目录模块无法入仓 | ⚠️ |
| 模块元数据 | 无 | metadata.json（entryPoint/files[]），`installModule` 目录版零调用 | ⚠️ 半成品 |
| 物化清理 | 无此概念 | 卸载后 `<project>/lua`、`<project>/lua/<name>` 残留 | ⚠️ |

**模块系统结论：运行时解析链完全一致；全局模块仓库可用但被"只支持平面文件"限制，且物化残留不清理。**

---

## 8. 引擎运行时层逐字节一致性清单（复核通过）

| 类别 | 结果 |
|---|---|
| `com.luajava` 16 类（LuaState 1277L / LuaJavaAPI 1823L / LuaObject / LuaTable / LuaFunction / LuaInvocationHandler / LuaStack / LuaString / LuaList / JavaFunction / LuaException / LuaStateFactory / Console / LuaMetaTable / LuaMethodInterceptor / LuaAbstractMethodInterceptor） | ✅ 逐字节 |
| `com.android.cglib` dx(~270) + proxy(9) | ✅ 完整一致 |
| `com.androlua` 主体 42+（含 LuaContext / LuaActivity / LuaApplication / LuaService / LuaDexLoader / LuaDexClassLoader / LuaEnhancer / Http / Download / 全部 Adapter / LuaThread / LuaTimer / LuaAsyncTask / 控件族 / util/*） | ✅ 逐字节（仅 4 文件有意修改，见下） |
| `android.content.FileProvider`（自定义版） | ✅ 两侧同为自定义类，authority=包名 |
| Lua 运行时脚本 28 个（app assets/lua == pro resources/lua，含 socket/{headers,tp,url}） | ✅ 逐字节 |
| 原生库 15 个 × 3 ABI（bson/canvas/cjson/crypt/gl/luajava/luv/md5/regex/sensor/socket/mime/libzip/zlib + 静态 lua） | ✅ 与 pro 主 Android.mk 构建集一致（pro 的 lluv/lsqlite3/tcc 为 Android.mk2 禁用态，delua 正确未引入）；delua 另补 x86_64（pro 仅 armeabi-v7a） |
| `initENV` 空环境块执行 init.lua（无 package.path/print 上下文） | ✅ 同款 |
| print 双重覆盖（Java LuaPrint → import.lua `_M.print`） | ✅ 同款 |
| `L.close()` 注释态、`MultiDex.installLibs` 注释态、`LuaAssetLoader` 未注册 | ✅ 同款（与 pro 行为一致） |
| `import "dex:类"` 冒号语法仅认工程根/包名（不认 libs/ 子目录） | ✅ 同款 |

**有意修改（行为差异需知晓）**：
1. `LuaActivity.java`（-115/+17）：删除 Xposed 反检测 `chcek1/2/3/a` + `runFunc("onHook")`（合理，属恶意痕迹清除）；`R.drawable.icon` → `android.R.drawable.sym_def_app_icon`（×3，无 res 工程下的图标退化）；`luaDir` private→protected（EngineActivity 需要）；新增 scriptError 存取器（宿主回传错误用）；annotation 换 androidx。
2. `LuaApplication.java`（+37）：`extractLuaScripts()`（MD5 版本化）替代 pro 的 `unApk("lua", luaMdDir)`；无 `unApk("assets", localDir)`（IDE 页面由 Compose 替代，无需解包）。
3. `LuaAccessibilityService.java`：曾删截图 API（-64），后按 P1-5 **已恢复** nirenr/截图全 API（startScreenshot/getScreenshot/click(Point) 等）。
4. `PageView/BasePageAdapter/ArrayPageAdapter`：仅删 Javadoc。

---

## 9. Manifest / 组件 / 权限比对（当前实况）

| 组件 | pro | delua | 判定 |
|---|---|---|---|
| IDE 入口 | `Welcome`(LAUNCHER) → `Main`(singleTask, 5 组 .alp VIEW/EDIT) | `MainActivity`(MAIN/LAUNCHER) + `.alp`/`.lua`(VIEW+EDIT, content+file)/`androlua://{com.lioisme.delua, com.androlua}` 5 组 filter | ✅ 超集 |
| LuaActivity 直开 .lua | file://*.lua + content + text/* | .lua 由 MainActivity 收养后进编辑器（EngineActivity 不直接暴露） | ⚠️ 行为差异 |
| LuaService | ✅ | ✅ 已注册（含 luaDir/luaPath extra 消费链路 LuaService.java:292-317） | ✅ 修复 |
| LuaAccessibilityService | ✅ + config xml | ✅ 已注册 + `res/xml/accessibility_service_config.xml` + 截图 API | ✅ 修复 |
| FileProvider | ✅ | ✅ 自定义类 + authorities=${applicationId} + `androlua_filepaths.xml`（files/、external AndroLua/、external /） | ✅ 修复 |
| LuaActivityX / ScreenCaptureActivity | ✅ | ✅ 已注册 | ✅ 修复 |
| 权限 | 36 项 | 50 项（原 12 项补齐 + MANAGE_EXTERNAL_STORAGE/REQUEST_INSTALL_PACKAGES 等宿主所需） | ✅ 超集 |
| largeHeap / persistent | largeHeap+persistent | largeHeap（persistent 未沿用，合理） | ✅ |
| 多进程 | 单进程 | **单进程**（AGENTS.md 所述"EngineBridge 独立进程"仅为架构意图，引擎实跑主进程） | ⚠️ 文档与实现偏差 |

---

## 10. 统一管理层遗留问题清单（当前代码复核）

### 10.1 写-读不对称（半成品，按影响排序）

| # | 问题 | 证据 |
|---|---|---|
| G1 | `delua.json` 无写入端：文档宣称"声明即同步入口"，实际 `setProjectDependencies` 只写 init.lua；且 `ProjectManager.parseDependencies`（列表显示）只认 init.lua——手工建 delua.json 时两视图不一致 | DependencyManager.kt:153-156,287-306,319-325；ProjectManager.kt:127-141 |
| G2 | `registry.json` 只写不读（索引无消费者） | DependencyManager.kt:249-256 |
| G3 | `Project.dependencies` 字段只写无读 | Project.kt:14；ProjectManager.kt:52,61 |
| G4 | `assets/plugin/plugin.json` 无提取逻辑，永不参与扫描（惰性示例） | DeluaApp.kt 无相关代码 |
| G5 | `extractSamples` **每次启动无条件覆盖**示例工程（用户修改丢失）；对比 `extractLuadoc` 有 `exists && length>0` 跳过 | DeluaApp.kt:35-60 vs :73-86 |

### 10.2 死代码（grep 验证零调用）

`PluginManager.loadedPlugins`（:36）、`DependencyManager.readRegistry/getLibraryInfo/getModuleInfo/installModule(目录版)`、`AlpManager.export(file版)` + `ZipUtil.zip`、`ProjectManager.deleteProject`、`Project.dependencies` 字段。

### 10.3 其它工程风险

- `replaceDependenciesInInitLua` 正则脆弱（要求 `dependencies` 前有换行、`[^}]*` 不嵌套，文件头无换行会重复追加）（DependencyManager.kt:327-351）。
- `ZipUtil.unzipFromStream` 无路径穿越防护（ZipUtil.kt:56-78）。
- 物化/安装全链路无清理（§5.3 L2/L3、§7）。
- 部分 UI 文案硬编码英文未走 strings.xml。

---

## 11. 特性一致性矩阵（总表）

| 特性 | pro 机制 | delua 现状 | 一致性 |
|---|---|---|---|
| 工程结构（init.lua 元数据 + main.lua + icon + libs/） | project/<name>/ | 同结构；模板含 dependencies 块 | ✅ |
| 工程根探测 / newActivity 页面链 / LuaActivityX | prjCache 上溯 | 同源码 | ✅ |
| .alp 导入导出 | zip + mode 分流 + 编译 | zip；**无 mode 分流**、无编译 | ⚠️ |
| 工程内库自动加载（libs/*.jar/.dex/.so） | LuaDexLoader.loadLibs | 逐字节同源码 | ✅ |
| 全局库仓库 | 无 | common/libs + 物化到工程 libs/（链路全通） | ➕ 增强（L1-L6 缺口） |
| 全局模块仓库 | 无 | common/modules + 物化到工程 lua/ | ➕ 增强（仅平面文件） |
| Lua 模块 require 链 | package.path/cpath | 逐字符同构 | ✅ |
| 内置脚本（28）提取 | unApk MD5 增量 | extractLuaScripts MD5 比对 | ✅ |
| 原生库（15×3 ABI） | 15（仅 armeabi-v7a） | 15×3（+x86_64） | ✅ 超集 |
| 插件结构/扫描 | plugin/<name>/ + init.lua | plugin.json 优先 + init.lua fallback | ✅ 兼容 |
| 插件运行 | newActivity(main.lua, {luadir,luapath}) | 直启引擎；**main 默认 init.lua（偏差）+ 无上下文参数** | ❌ 两处不兼容 |
| 插件导入 | .alp mode=plugin 分流 | 仅 PluginScreen 手动 "+" | ⚠️ |
| 无障碍/截图/找色 | LuaAccessibilityService + com.nirenr | 类+注册+API 全齐 | ✅ |
| 后台服务 / 分享 URI | LuaService / FileProvider | 已注册 | ✅ |
| IDE 编辑器 | textwarrior + LuaEditor + javaapi 补全 | sora-editor + TextMate | ✅ 合理替换（pro 脚本 import LuaEditor 会失败，已知） |
| 布局助手 | layouthelper/ + javaapi/*.aly | loadlayout2/3、xml2table 脚本可用；**.aly 模板缺失（编辑器侧无布局助手）** | ⚠️ |
| 离线文档 | luadoc/ | ✅ 已补齐（WebView 渲染） | ✅ |
| 独立 APK 打包 | bin.lua + 外部 mao/sign | **无**（delua 无打包功能；白皮书阶段四/五规划中） | ❌ 缺失（最大单项） |
| .lua 关联 / androlua:// | ✅ | ✅（含双 host 兼容） | ✅ |
| 运行输出 | 引擎内控制台 | 引擎内控制台（结果页已按决策移除） | ✅ |

---

## 12. 差距分级与行动项（按 pro 一致性要求）

### P0 —— 必须修复（插件系统不兼容）

| # | 行动 | 验收 |
|---|---|---|
| 1 | **插件入口语义对齐**：plugin.json 风格插件默认入口改为 `main.lua`（现默认 "init.lua"，运行的是元数据文件）；示例 plugin.json 同步修正 | 安装的 plugin.json 插件点击后运行 main.lua |
| 2 | **插件上下文参数**：EngineBridge/EngineActivity 支持可选 `{luadir, luapath}` 参数并作为 `...` 传给插件 main.lua（pro 生态插件依赖此入参） | pro 生态插件 `local luadir, luapath = ...` 可取到当前工程上下文 |
| 3 | **.alp mode 分流**：`AlpManager.import` 读取 zip 内 init.lua 的 `mode` 字段（`LuaUtil.readZip` 式读取），`mode=="plugin"` 导入到 `plugin/` | pro 的 .alp 插件从文件管理器直接导入后出现在插件列表 |

### P1 —— 近期补齐（库/模块管理面收口）

| # | 行动 | 验收 |
|---|---|---|
| 4 | **common 库支持 .so**：安装/校验/物化扩展 `lib<name>.so`（物化到 `<project>/libs/lib<name>.so`，libsloader 自动生效） | 全局库安装 .so 后 `import "gl"` 等价可用 |
| 5 | **物化清理**：运行前比对声明清单与 `<project>/libs`、`<project>/lua` 实际内容，移除未声明文件（或版本化命名 + 清除旧文件） | 卸载/取消声明后引擎不再加载残留 |
| 6 | **registry.json 接入或删除**：让索引被 LibraryManager/ModuleManager 消费，或移除该概念 | 二选一收口 |
| 7 | **模块子目录支持**：安装/物化保留相对路径（socket 式目录模块可入仓） | 目录型模块安装后 `require "m/子"` 可用 |
| 8 | **extractSamples 防覆盖**：加存在性/版本标记跳过（对齐 extractLuadoc），或改为仅首启提取 | 重启不丢用户修改 |
| 9 | **delua.json 收口**：要么接通写入端（统一两套解析器），要么删除读路径 | 两套解析结果恒一致 |

### P2 —— 可选（生态/工程化）

| # | 行动 |
|---|---|
| 10 | 补齐独立 APK 打包（pro `bin.lua` 等价能力；当前完全缺失，是 pro 最大的功能缺口）——或明确暂不支持并写入产品文档 |
| 11 | `ZipUtil` 路径穿越防护；`replaceDependenciesInInitLua` 改用结构化解法 |
| 12 | 清理 §10.2 死代码清单；UI 文案国际化收口 |
| 13 | 消除"EngineBridge 独立进程"文档与单进程实现的偏差（AGENTS.md / 白皮书口径统一） |
| 14 | 编辑器侧布局助手（javaapi/*.aly 模板 + 布局预览）——pro 的高频创作特性，工作量较大，建议单独立项 |

### 12.1 行动项执行状态（2026-07-31 全部收口）

> 每项均标注完成情况与工作简述；构建验收：`:app:compileArm64DebugKotlin` ✅、`:app:assembleArm64Debug` ✅、`:editor` R1 边界 grep 零命中 ✅。

- **P0-1 ✅ 已完成**：`PluginManager.kt` `PluginInfo.main` 默认值 `init.lua`→`main.lua`（data class 默认值 + `optString("main", ...)` 两处）；`app/src/main/assets/plugin/plugin.json` main 字段同步修正。安装的 plugin.json 插件点击后运行 main.lua。
- **P0-2 ✅ 已完成**：`EngineBridge.kt` 重写——新增 `EXTRA_SCRIPT_ARGS="arg"` 常量（对齐 LuaActivity `ARG` 键）、`createEngineIntent(projectDir, mainFile, args: Array<Any?>?)`；`RunEngineLauncher.launch` 增 `args` 参数透传；`DeluaNavHost` 插件运行传 `arrayOf(plugin.dir, plugin.dir/main)` 作为 `{luadir, luapath}`。
- **P0-3 ✅ 已完成**：`ZipUtil.kt` 重写（`safeEntryName` zip-slip 防护 + `readEntryText`）；`AlpManager.kt` 重写——`AlpKind{PROJECT,PLUGIN}`、`resolveKind`（init.lua `mode="plugin"` 或文件名 `*_plugin`）、`import(File/Uri): AlpImportResult?`；`MainActivity` `.alp` 导入后按 kind 分流，插件跳转插件列表；工程列表导入结果按类型计数提示。
- **P1-4 ✅ 已完成**：`LibraryRegistry.findArtifactFile` 扩展认 `.so`；`DependencyManager.installLibrary` 对 .so 命名 `lib<name>.so`（libsloader `luaopen_<name>` 可命中），jar/dex 仍 `<name>-<version>.<ext>`，重装先删旧构件（单构件约束）；`ProjectManager.getLocalLibFiles` 支持 `.so`。
- **P1-5 ✅ 已完成**：物化清单 `.delua-materialized.json`（`MaterializationManifest` 私有嵌套类）——运行前同步，`syncMaterializedFiles` 删除未声明文件（canonical 前缀守卫 + `pruneEmptyParents`），卸载/换版本后引擎不再加载残留。
- **P1-6 ✅ 已完成**：`registry.json` 概念整体移除（`registryFile` 字段与 read/write/update 函数删除）；`getLibraryInfo/getModuleInfo` 死代码同步清理。
- **P1-7 ✅ 已完成**：`installModule(sourceDir, ...)` 递归收集 `.lua` 按相对路径入仓（socket 式目录模块）；物化同时落 `<project>/lua/<rel>` 与（含 init.lua 时）`<project>/lua/<name>/`；新增 `installModuleFromFiles`（文件多选安装）；`ModuleRegistry.readMetadata` files 缺失时回退磁盘重列。
- **P1-8 ✅ 已完成**：`DeluaApp.extractSamples` 目录存在即跳过（重启不丢用户修改）；新增 `extractExamplePlugin`（G4 收口：`plugin/example/` 从 assets 提取 plugin.json + 生成 main.lua）、`extractLayoutTemplates`（P2-14 配套）。
- **P1-9 ✅ 已完成**：`delua.json` 读路径与 `ProjectManager.parseDependencies/getProjectDependencies/setProjectDependencies` 删除；依赖声明单一事实源 = init.lua `dependencies` 块；`Project.dependencies` 字段删除。
- **P2-10 ✅ 已决策**：v1.0 明确暂不支持设备端独立 APK 打包，决策理由（mao/sign 缺失、v1-only 签名不可装、模板携带全套 IDE 运行时、优先级低）写入 `WHITEPAPER.md` 阶段四；后续平移 pro `bin.lua` 的 luac/checklib/manifest 补丁三机制。
- **P2-11 ✅ 已完成**：`ZipUtil` 路径穿越防护（见 P0-3）；`replaceDependenciesBlock` 改为结构化扫描（`findDependenciesBlock` 跳过字符串/注释/长括号 + 花括号配平 `skipBalancedBraces`），不再依赖换行/非嵌套假设。
- **P2-12 ✅ 已完成**：死代码清理（`PluginManager.loadedPlugins`、`DependencyManager` 三项只读函数、`AlpManager.export(File)`、`ProjectManager` delua.json 依赖方法、`Project.dependencies`）；`deleteProject` 接入 UI（工程长按 → 删除确认对话框）；UI 文案国际化收口（插件/库/模块/依赖/工程列表全部走 strings.xml，含 values-zh；ProjectDependenciesScreen 乱码清理）。
- **P2-13 ✅ 已完成**：AGENTS.md R2 改为"EngineBridge → EngineActivity，同进程运行"单进程口径；白皮书 3.3 独立 Activity 栈表述与实现一致（历史报告保留原貌）。
- **P2-14 ✅ 部分完成（资产侧）**：pro `assets/javaapi/{clayout,layout,mlayout}.aly` 复制入 `app/src/main/assets/layouts/javaapi/`，`DeluaApp` 首启提取到 `filesDir/layouts/`（存在即跳过）；布局预览/编辑器侧布局助手 UI 仍待单独立项（审计原结论不变）。

---

## 13. 总结

1. **运行时语义已 100% 对齐**：28 个 Lua 脚本、15 个原生库（三 ABI）、`com.luajava` 16 类、cglib 全链、`com.androlua` 主体逐字节一致；`libs/` 工程内自动加载、`require` 链、工程根探测、页面链、MD5 增量提取等 pro 核心机制原样保留——"工程内自行管理"模型在 delua 中依然成立，且为 pro 超集。
2. **统一管理层已从"断链"走到"通链"**：声明 → 校验 → 物化 → 引擎消费 8 环全通，`materializeProjectDependencies` 是全局仓库与 pro 式工程模型的唯一桥；但管理面自身存在 6 处写-读不对称（delua.json、registry.json、Project.dependencies、plugin.json 资产、示例覆盖、.so 不支持）与 2 处无清理风险。
3. **与 pro 生态的一致性缺口收敛为 3 个 P0**（插件入口默认值、插件上下文参数、.alp mode 分流）、**6 个 P1**（so 入仓、物化清理、索引收口、子目录模块、示例防覆盖、delua.json 收口）与 **1 个大项 P2**（独立 APK 打包缺失）。
4. 执行完 P0+P1 后，delua 的工程/库/插件/模块系统将达成"pro 全部特性 + 全局仓库增强"的最终形态；建议按 §12 顺序推进并在 `PROGRESS.md` 登记。
