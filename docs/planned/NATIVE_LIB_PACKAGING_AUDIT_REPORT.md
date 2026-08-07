# 原生引擎「全架构构建 + 按需打包」机制审计报告（pro checklib vs lide ApkBuilder）

> 日期：2026-08-07
> 审计对象：
> - 上游基线：AndroLua Pro v4.4.2（`nirenr/AndroLua_pro`，master = commit `abe5c25`；本仓库已去重归档，恢复配方见 `reference/README.md`）
> - lide 现状：`app/src/main/cpp/`（19 个 ndk-build 模块）、`app/build.gradle.kts`（ABI flavor）、
>   `app/src/main/kotlin/com/lioisme/lide/build/ApkBuilder.kt`（打包装配）、
>   `app/src/main/kotlin/com/lioisme/lide/ui/screen/BuildApkScreen.kt`（依赖分析）、
>   `app/src/main/assets/lua/bin.lua`（pro 打包脚本原样副本）
> 结论性质：**研究已毕（事实核实 + 落地方案），实施未排期** → 归 `docs/planned/`

---

## 0. 结论先行

1. **pro 的「用哪个原生库才集成哪个原生库」是真实存在的机制**，实现位于 pro IDE 的设备端打包脚本
   `bin.lua` 的 `checklib` 依赖分析（递归 `require/import` 正则扫描，按库文件基名精确过滤
   `lib/<abi>/*.so`），且 `libluajava.so` 无条件保留。**该机制与 ABI 无关，在宿主 APK 的每个 ABI
   目录上自动生效**。
2. **pro 的宿主 APK 本身只有一个 ABI**（`abiFilters 'armeabi-v7a'` + `APP_ABI = armeabi-v7a`，
   仓库内无任何 jniLibs 预编译目录）。「包含所有架构」在 pro 源码基线上并不成立——这是用户对
   lide 提出的**高于上游的新要求**。
3. **lide 现状两处缺口**：
   - **打包产物全量携带引擎原生库**：`ApkBuilder` 对 `lib/<abi>/*.so` 零过滤（每包 18 个引擎库 +
     androidx 图形库，约 3.5 MB/ABI 未压缩），与 pro 的按需集成相反；
   - **Lua 侧依赖分析不完整**：`engineLuaLibs` 无传递闭包、不推导 `.so` 需求 → 工程
     `require "http"` 时产物缺 `socket.lua / ltn12.lua / mime.lua / socket/{url,headers}.lua`
     且缺 `libsocket.so / libmime.so`，**打包产物运行时直接 require 失败**（pro 无此问题）。
4. **落地方案**（详见 §4/§5）：引擎扩展为多 ABI（arm64-v8a / armeabi-v7a / x86_64，x86 可选）随
   宿主发布；打包器新增「ABI 多选 × 原生库按需过滤」二维能力，以 Kotlin 传递闭包完整平移
   pro `checklib` 语义，并补齐 pro 缺失的「未命中诊断」与「强制包含」能力——在需求 2 上做到
   **pro 的超集**，而非简单复刻。

---

## 1. 审计范围与方法

| 项 | 内容 |
|---|---|
| 上游基线 | `github.com/nirenr/AndroLuaPro`（master=4.4.2，commit `abe5c25`）：`app/build.gradle`、`app/src/main/jni/{Android.mk,Application.mk,19 模块目录}`、`app/src/main/assets/lua/bin.lua`（与 lide 副本比对口径为「pro 资产逐字节一致」，见 `docs/discard/ANDROLUA_PARITY_FULL_AUDIT_REPORT.md`） |
| lide 现状 | `app/build.gradle.kts:35-49`（flavor/ABI）、`app/src/main/cpp/**/Android.mk`（模块清单）、`ApkBuilder.kt`（装配）、`BuildApkScreen.kt:382-453`（依赖分析）、实际产物 `app/build/outputs/apk/*/app-*-debug.apk` 的 `lib/` 实测 |
| 方法 | 上游源码直读（GitHub 原始文件）+ lide 源码逐行对照 + 已构建 APK 的 ZIP 条目实测（19 个 .so 清单与体积） |

---

## 2. 上游基线机制审计（AndroLua Pro 4.4.2）

### 2.1 宿主构建与 ABI 事实

- `app/build.gradle`：`abiFilters 'armeabi-v7a'`、`externalNativeBuild.ndkBuild path 'src/main/jni/android.mk'`（注意文件名小写）。
- `app/src/main/jni/Android.mk`：`APP_ABI = armeabi-v7a` + `include $(call all-subdir-makefiles)`。
- `app/src/main/jni/` 模块目录共 19 个：`bson canvas cjson crypt lfs lluv lsqlite3 lua luagl luajava luv md5 regex sensor socket tcc xml zip zlib`（+ 顶层 2 个 mk 文件）。
- `app/src/main/` 下**无 jniLibs 预编译目录**：宿主全部原生库均来自 jni/ 源码 ndk-build 产物。

**结论：pro 官方源码产物的宿主 APK = 单一 ABI（armeabi-v7a）× 19 模块。** lide 仓库文档
（`WHITEPAPER.md §7.1`、`docs/discard/ANDROLUA_PARITY_FULL_AUDIT_REPORT.md`）记载的
「pro 仅 armeabi-v7a、lide 另补 x86_64/arm64-v8a」与此一致。

### 2.2 设备端打包链（`bin.lua`，398 行，模板复制模型）

1. 以**正在运行的 pro 宿主 APK**（`publicSourceDir`）为模板，逐条目重写为产物 APK（`bin.lua:53-103`）。
2. 仅替换：manifest（包名/label/版本/权限白名单）、图标/欢迎图、`assets/` 工程注入（Lua 编译为
   `.luac`、`.aly` 编译为 `.lua`）、剔除宿主专属资产。
3. v1 签名（`Signer.sign`，`bin.lua:363`）→ 自动安装（`activity.installApk`，`bin.lua:365`）。
4. **原生库条目**：模板 APK 的 `lib/<abi>/*.so` 按基名过滤（见 2.3），过滤逻辑天然逐 ABI 生效。

### 2.3 `checklib` 按需原生库机制（`bin.lua:108-176` + 复制循环 `294-305`）逐行语义

| 步骤 | 代码位置 | 语义 |
|---|---|---|
| 初始标记 | `bin.lua:108-113`：`File(nativeLibraryDir).list()` 全部宿主 .so 基名 → `replace[lib]=true` | 默认「剔除」 |
| 恒保留 | `bin.lua:176`：`replace["libluajava.so"]=false` | JNI 核心无条件保留 |
| 静态命中 | `bin.lua:140-155`（require）/ `157-173`（import）：`require/import "m(.n)?"` → `cp="lib<m>.so"`，若宿主存在该库则 `replace[cp]=false` | **按需保留**：命中即保留 |
| 传递递归 | 命中宿主 Lua 模块（`lua/<m>[/<n>].lua` 存在）→ `checklib(宿主模块路径)` 递归扫描其自身 require/import | 闭包覆盖宿主库内部依赖 |
| 复制过滤 | `bin.lua:297-299`：条目 `name:match("([^/]+%.so)$")` 取基名，`replace[基名]==true` 则跳过 | 每个 ABI 目录同样规则 |

语义要点：

- **`require "socket.core"` → `libsocket.so`（首段归组）**；`require "mime"` → `libmime.so`。
  子模块名按第一段 `m` 归属到原生库，这是 LuaSocket 系（socket.core/mime.core 同 .so）能正确
  工作的关键。
- **纯 Lua 库**：命中 `lua/<m>.lua`（宿主资产）则编译为 `.luac` 注入产物（`lualib` 表，
  `bin.lua:154/170/271-282`），同时递归扫其源码 → 静态白名单不存在，一切由闭包推导。
- 正则盲区（pro 固有）：`require(var)` 动态 require 无法静态命中 → 运行时才炸；pro 无任何补偿。

### 2.4 pro 机制的固有限制（作为 lide 超越基准）

1. **无诊断输出**：未命中的 require（宿主既无 lua 模块也无 .so）静默缺失，产物装到设备上才报错。
2. **无 ABI 概念**：宿主单 ABI，产物天然单 ABI，无组合能力（用户对 lide 的要求在 pro 源码上
   不存在对应实现）。
3. 正则只能识别字符串字面量，注释/字符串中的假命中产生少量误保留（无害但膨胀）。

---

## 3. lide 现状审计

### 3.1 引擎构建清单（`app/src/main/cpp/`，19 个模块目录）

| 模块 | 产物 | 模块 | 产物 |
|---|---|---|---|
| lua | `liblua.a`（静态） | luajava | `libluajava.so` |
| cjson | `libcjson.so` | socket | `libsocket.so` + `libmime.so` |
| lfs | `liblfs.so` | lsqlite3 | `liblsqlite3.so` |
| luv | `libluv.so` | live2d | `liblive2d.so` |
| canvas | `libcanvas.so` | zlib | `libzlib.so` |
| luagl | `libgl.so` | bson | `libbson.so` |
| crypt | `libcrypt.so` | md5 | `libmd5.so` |
| regex | `libregex.so` | sensor | `libsensor.so` |
| xml | `libxml.so` | zip | `libzip.so`（`LOCAL_MODULE := libzip`，产物自动去重前缀） |

顶层 `Android.mk` = `include $(call all-subdir-makefiles)`；`Application.mk` 不设 `APP_ABI`
（由 AGP 按 flavor `abiFilters` 注入，与 pro 的 mk 内写死形成对照）。

实测 `app-x64-debug.apk`（x86_64）`lib/` 共 19 个 .so = 18 引擎库 + `libandroidx.graphics.path.so`
（Compose 依赖），未压缩合计 **3.47 MB**；体积大头：live2d 1.26 MB / lsqlite3 856 KB /
luajava 347 KB / luv 284 KB / sensor 267 KB。

### 3.2 宿主 ABI 策略（`app/build.gradle.kts:35-49`）

- `flavorDimensions = "arch"`；`x64` → `abiFilters x86_64`；`arm64` → `abiFilters arm64-v8a`。
- **无 armeabi-v7a、无 x86**；每个宿主 APK 仅单 ABI。
- 历史上有过 3 ABI（arm64-v8a / armeabi-v7a / x86_64）的 jniLibs 记录（`PROGRESS.md`、`XPOSED_MODULE_FEASIBILITY_REPORT.md`），
  现 flavor 化后仅剩 2 个 ABI 且互斥。

### 3.3 打包装配（`ApkBuilder.kt:125-165`）——原生库零过滤

复制宿主条目时仅过滤：`META-INF/`（重签名）、`assets/lua/`（按 `engineLuaLibs` 白名单）、
`HOST_ASSET_EXCLUDED`（textmate/keys/luadoc/plugin/layouts）。**`lib/<abi>/*.so` 全量透传**——
产物 APK 恒携带宿主全部引擎库（× 宿主单一 ABI）。

### 3.4 Lua 侧依赖分析（`BuildApkScreen.kt:382-453` `engineLuaLibs`）——平移度分析

| pro `checklib` | lide `engineLuaLibs` | 差距 |
|---|---|---|
| require + import 正则命中 | 同款正则（`BuildApkScreen.kt:441`） | ✅ 口径一致 |
| 命中宿主 Lua 模块 → 递归扫描（传递闭包） | **不递归**，仅扫工程源码 + `.using` | ❌ **非传递** |
| 由模块名推导 `lib<m>.so` 并保留 | **无 .so 推导** | ❌ **无原生侧分析** |
| 无白名单，全闭包 | 静态白名单 `ENGINE_LUA_WHITELIST`（import/loadlayout×3/loadbitmap/loadmenu/permission） | ⚠️ 白名单是 lide 补偿非传递缺陷的补丁 |
| `libluajava.so` 恒保留 | —（ApkBuilder 不过滤 .so，等价于全保留） | ✅ 无功能缺口但方向相反 |

**实例验证（缺依赖会炸）**：工程仅 `require "http"` 时——

- lide 产物 `assets/lua/` 只含白名单 + `http.lua`；
- 但 `http.lua` 运行时 `require("socket") / require("socket.url") / require("ltn12") / require("mime") / require("socket.headers")`
  （`app/src/main/assets/lua/http.lua:10-15`）——这些模块**不在**产物资产中；
- 原生侧虽全量携带，但 `require "socket"` 需 `lua/socket.lua`（C 侧只导出 `luaopen_socket_core`，无 `luaopen_socket`）
  → **产物运行即失败**。pro 经传递闭包无此问题。

### 3.5 缺口量化

| 缺口 | 影响 | 量化 |
|---|---|---|
| 原生库全量集成 | 体积膨胀 | 每包 18 引擎库 ≈ 3.47 MB（x86_64 未压缩）；多数工程仅用 cjson/socket/zip 等 2-3 个 |
| 无 ABI 组合 | 产物覆盖受限 | 宿主单 ABI → 产物单 ABI；arm64 宿主产物无法跑在 x86_64 模拟器/旧 32 位设备 |
| Lua 依赖非传递 | **功能缺陷** | `require "http"/"ftp"/"smtp"/"socket"/"mime"` 类工程打包产物直接断模块加载 |

---

## 4. 需求一方案：运行环境底层库覆盖所有架构 + 打包自由组合

### 4.1 引擎侧（构建期）

在 `app/build.gradle.kts` 增加 ABI 组合：

| 方案 | 内容 | 评价 |
|---|---|---|
| A（推荐） | 新增 `universal` flavor（不设 abiFilters，四 ABI 全含）或令 release 变体为多 ABI fat APK：arm64-v8a + armeabi-v7a + x86_64（x86 32 位默认不构建，见 §7 风险） | 宿主一次发布即可覆盖全部目标设备；打包器可任意组合 ABI |
| B（最小改动） | 保持 flavor 单 ABI，打包器 ABI 集合 = 宿主 APK 实测存在的 ABI | 满足「按需」但不满足「所有架构」诉求 |

体积账：每 ABI 引擎库 ≈ 3.47 MB 未压缩（zip 压缩后约 1.2-1.5 MB/ABI）；三 ABI 全含约
+2.5-3 MB 压缩体积（宿主 release 现 17.3 MB）。`Application.mk` 无需改动（`APP_ABI` 由 AGP 注入）。

### 4.2 打包侧（产物期）

- 打包器新增 **ABI 多选**（默认全选宿主可用 ABI；宿主无的 ABI 不可选并置灰）；
- 装配时按 `lib/<abi>/` 目录过滤：`abi ∉ 选中` 的条目整目录跳过（见 §5 的二维过滤）。

---

## 5. 需求二方案：按需集成原生库（更优雅实现，pro 超集）

### 5.1 完整平移：Kotlin 传递闭包依赖分析

新增/扩展 `engineLuaLibs` 为双集合输出（`luaModules: Set<String>` + `nativeLibs: Set<String>`），
算法（与 pro `checklib` 语义等价，收敛到不动点）：

```
工作集 = 工程全部 .lua/.aly 文本 ∪ .using ∪ 静态白名单(import/loadlayout/loadbitmap/loadmenu/permission)
循环：
  for src in 工作集:
    for require/import "m(.n)?" in src:
      luaModules += "m[.n]→lua/m[/n].lua"（宿主存在才收，工程同名优先）
      nativeLibs  += "lib<m>.so"（宿主存在才收）
  if 命中宿主 Lua 模块且未扫描过 → 追加其源码到工作集
直至无新增
nativeLibs += "libluajava.so"（恒保留）
```

与 pro 的差异点（提升）：

| 项 | pro | lide 目标 |
|---|---|---|
| 扫描源 | 仅 `.lua` + `.using` | 追加 `.aly` 文本（布局文件内嵌 require/import 场景） |
| 诊断 | 无 | 未命中宿主且工程未自备的模块 → 打包警告清单（UI 展示） |
| 强制包含 | 无 | 高级选项「force-include/force-exclude」覆盖自动集（开发者自由组合） |
| 工程 libs/*.so | 原样进 assets（运行时 libsloader 表） | 保持现状 + ABI 一致性提示 |

### 5.2 `ApkBuilder` 二维过滤

```
for entry in hostEntries:
  if entry 以 lib/<abi>/ 开头:
    abi  ∉ 选中集合        → 跳过
    basename == libluajava.so → 保留
    basename ∉ nativeLibs   → 跳过
  ...（其余规则不变）
```

- 保持 `useLegacyPackaging = true` 的条目压缩方式透传；`ZipUtils.writeEntry` 已保证 STORED
  条目 4 字节对齐（`ZipUtils.kt:64-97`），多 ABI 多库不影响安装校验。
- 产物 `lib/` 只含：选中 ABI ×（libluajava.so + 闭包命中库 + 强制包含库）。

### 5.3 打包 UI（`BuildApkScreen`）

1. ABI 复选组（默认全选）；
2. 依赖分析结果摘要（命中 Lua 库数 / 原生库数 / 警告数），可展开详情；
3. 高级：原生库手动开关（默认跟随自动集）。

### 5.4 自动化保障

- 扩展打包单测（`BuildPackagingTest` 系）：demo 工程打包断言 `lib/<abi>/` 仅含
  `libluajava.so + 命中库`；`require "http"` 工程断言闭包包含 socket/ltn12/mime 等。
- 对 `scripts/examples-src/` 全量工程打包 + 安装冒烟（现 ACC-1~4 验收基线沿用）。

---

## 6. 三方对比总表

| 维度 | pro 4.4.2 | lide 现状 | lide 目标（本报告） |
|---|---|---|---|
| 引擎 ABI | armeabi-v7a 单 ABI | arm64-v8a / x86_64（flavor 互斥，单 ABI） | arm64-v8a + armeabi-v7a + x86_64（universal 变体） |
| 原生库按需集成 | ✅ checklib 传递闭包 | ❌ 全量携带 | ✅ 传递闭包 + 诊断 + 强制包含 |
| 纯 Lua 库闭包 | ✅ 递归 | ❌ 白名单 + 非传递（require http 断裂） | ✅ 递归 + .aly 扫描 |
| ABI 自由组合 | ❌ 无 | ❌ 无 | ✅ 打包 UI 多选 |
| 未命中诊断 | ❌ 无 | ❌ 无 | ✅ 警告清单 |
| 体积 | 单 ABI 按需 | 单 ABI 全量 | 多 ABI 按需（组合可控） |

---

## 7. 实施任务分解（供排期）

| # | 任务 | 涉及文件 | 验收 | 状态 |
|---|---|---|---|---|
| 1 | 引擎扩展 ABI（universal flavor / fat release + x86 可选验证） | `app/build.gradle.kts` | `assembleUniversalRelease` 产出 3 ABI 宿主 APK；`lib/` 实测三目录 | **（已完成 2026-08-07）** |
| 2 | `NativeDependencyAnalyzer`（传递闭包 + .so 推导 + 警告） | 新建 `build/DependencyAnalyzer.kt`；`BuildApkScreen.kt` 改造 | 单测：http/socket/ftp 闭包断言；demo 工程零警告 | **（已完成 2026-08-07）** |
| 3 | `ApkBuilder` 二维过滤（ABI × lib） | `ApkBuilder.kt` | 打包单测：lib/ 条目精确匹配自动集 | **（已完成 2026-08-07）** |
| 4 | 打包 UI（ABI 复选 + 摘要 + 高级开关） | `BuildApkScreen.kt` + 字符串资源 | 真机：arm64 宿主打包产物在 arm64 设备安装运行；x86_64 模拟器验证多 ABI 产物 | **（已完成 2026-08-07）** |
| 5 | 全量工程打包冒烟（ACC 基线） | `scripts/adb_regress.ps1` 扩展 | `scripts/examples-src/` 全部工程打包 → 安装 → 运行 | **（已完成 2026-08-07）** |

### 实施记录（2026-08-07）

- **T1 多 ABI**：`app/build.gradle.kts` 新增 `universal` flavor（abiFilters x86_64 + arm64-v8a，NDK 29 已移除 32 位工具链，3 ABI 目标收敛为双 ABI）；`scripts/build-apks.ps1` 扩为 6 变体（x64/arm64/universal × debug/release）。验收：`:app:assembleUniversalRelease` 成功；universal debug APK 37,066 KB、38 个 lib 条目（19 × 2 ABI）、x86_64 模拟器安装成功。
- **T2 依赖分析**：新建 `NativeDependencyAnalyzer.kt`（纯 Kotlin，class + companion）。语义对齐 pro `checklib` 并补两处超集：命中宿主 Lua 模块后递归扫描其 require/import（传递闭包，防环）；未命中时按模块首段推导 `lib<name>.so` 候选，装配阶段与宿主 `lib/<abi>/` 实际条目求交集。Lua 标准库（table/string/os/io/math/coroutine/debug/utf8/package）跳过；缺失 = Lua 模块与 .so 候选均不在宿主。`libluajava.so` 恒保留、`libandroidx.graphics.path.so` 默认剔除（可关）。`BuildApkScreen.kt` 的 `engineLuaLibs` 兼容包装改为委托 `analyzeDependencies`（工程源码 + .using + hostLua + hostNativeLibs 单入口）。
- **T3 二维过滤**：`ApkBuilder.kt` `ProjectInput` 新增 `requiredNativeLibs` / `abiSelection?` / `stripNativeLibs` / `launcherActivity?`（默认 `com.lioisme.lide.engine.EngineActivity`）；READ_HOST 阶段枚举宿主 ABI，effectiveAbis = 选择 ∩ 宿主，空交集抛 BuildException；lib/ 条目按 ABI × `nativeLibKept()`（纯函数，装配与 UI 同口径）过滤；`ZipUtils.listEntryNames`（CEN 直读）支撑宿主枚举。launcher 重定：`AXmlManifest.ManifestEdit.launcherActivity` 剥离旧入口 MAIN intent-filter 子树并给引擎 Activity 注入 MAIN/LAUNCHER + `exported=true`（API 31+ 必需），目标缺失安全降级。
- **T4 打包 UI**：`BuildApkScreen.kt` 重写——依赖摘要卡（Lua 库数 / 原生库数与体积 / ABI join / 缺失模块红字）、高级折叠卡（ABI 多选 + 原生库保留开关）、进度 Dialog 经 Channel 主线程桥；新增中英 strings（build_need_abi 等 9 条）。设备验证：以真实 universal debug 宿主 APK 打包最小 hello 工程产物（31,405 KB）→ `adb install` Success → `am start` 直达 EngineActivity（exported=true 生效）→ 系统从产物 `lib/x86_64/` 装载 luajava（nativeloader library_path 实测）→ Lua 引擎实际执行 main.lua（报错为预期：单测 fake 编译器字节码），进程存活无 FATAL、无 UnsatisfiedLinkError。
- **T5 冒烟**：`NativeDependencyAnalyzerTest.kt`（9 测试：闭包/推导/工程优先/缺失/环/标准库等）+ `ApkBuilderTest.kt` +5（ABI 过滤/子集/非法 ABI/strip 全量/launcher 重定）；真实宿主冒烟临时测试跑完即删。全量 `:app:testArm64DebugUnitTest` 95 测试绿；`assembleUniversalRelease` 绿；R1 门禁零命中。ktlint：本任务新增文件全绿（剩余违规均为仓库预存文件：ApiIndex/AlpImportCoordinator/NavigationGate/SettingsRegistry/ApkSigner 等，未触碰）。

> 注：T1 验收原述「3 ABI」，因 NDK 29.0.14206865 移除 32 位工具链，实际交付为双 ABI（x86_64 + arm64-v8a），与 §5 现状表一致；32 位生态已死（§8 风险表同口径），如需 x86 需降级 NDK，属另行决策。

## 8. 风险与边界

| 项 | 说明 |
|---|---|
| R5 合规 | 多 ABI 全部从 `app/src/main/cpp/` 源码构建，仓库零二进制新增，合规 |
| x86（32 位） | NDK 29 支持但生态已死；默认不构建，`x86` 仅在 universal flavor 验证后按需启用 |
| armeabi-v7a | 本项目历史上已成功构建（3 ABI 时代），风险低；live2d/luv 为 C++ 模块，需各 ABI 全量编译验证 |
| 产物安装 | 多 ABI 条目沿用宿主压缩方式（legacy packaging）+ `ZipUtils` 4 对齐，不影响 v1/v2 签名与安装校验 |
| 与 `:editor` 边界 | 全部改动位于 `:app` build 域与打包 UI，R1/R2/R4 不受影响 |
| `libandroidx.graphics.path.so` | Compose 依赖随宿主全量进入产物；可列入默认剔除清单（产物运行不触达 Compose UI 路径） |
| pro 一致性 | `bin.lua` 保持原样（只读资产），不反向修改；lide 以 Kotlin 实现为准 |

## 9. 参考文件

- 上游：`github.com/nirenr/AndroLua_pro` master（4.4.2）：`app/build.gradle`、`app/src/main/jni/Android.mk`、`app/src/main/jni/Application.mk`、`app/src/main/assets/lua/bin.lua`
- lide：`app/build.gradle.kts`、`app/src/main/cpp/**/Android.mk`、`app/src/main/kotlin/com/lioisme/lide/build/{ApkBuilder,ZipUtils}.kt`、`app/src/main/kotlin/com/lioisme/lide/ui/screen/BuildApkScreen.kt`、`app/src/main/assets/lua/{bin,http,socket,mime,import}.lua`
- 知识库：`docs/kb/ENGINE_RUNTIME.md`、`docs/kb/ARCHITECTURE.md`、`docs/WHITEPAPER.md §7.1`、`docs/in-progress/PARITY_COMPLETION_WORK_ORDER.md`（WP-014）、`docs/discard/LIBS_SUPPORT_WORK_ORDER.md`
