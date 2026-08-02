# AndroLua 运行环境全量盘点报告（Runtime Inventory）

> 审计日期：2026-08-02
> 审计基线：`docs/reference/androlua/AndroLua_pro-master/`（nirenr/AndroLuaPro **v4.4.2** 官方源码，gitignored）
> 审计范围：Java 层（`:androlua`）、原生库层（`prebuilt/` + `jniLibs/`）、Lua 脚本层（`assets/lua/`）、构建配置、host 耦合面、版本滚动机制
> 方法：全量逐字节（MD5）对比 + 源码 diff 审读 + 依赖引用面 grep + 构建脚本/补丁核查

---

## 1. 结论摘要

| 层 | 与 pro 4.4.2 一致率 | 说明 |
|---|---|---|
| Java | 96.3%（387/402 逐字节一致） | 15 个文件修改（多为注释剥离/等值适配，5 个行为修改），0 个 delua 新增 Java 类，41 个 pro 文件未移植（IDE 层，判定合理） |
| 原生 | 17 个 .so/ABI 与官方 APK 对齐 | 仅 2 个最小补丁 + 2 个模块禁用（lluv→luv 取代、tcc 无 arm64/x86_64）；`prebuilt/` 与 `jniLibs/` 双向 MD5 一致 |
| Lua 脚本 | 28/28 逐字节一致 | 另 3 个 delua 新增（layouthelper 家族扩展） |
| 解耦 | ✅ | `:androlua` 对 host 零引用；`:editor` 对 `:androlua` 零引用（R1 零命中）；`loadLibrary` 单点（`LuaState.java:106`） |
| 版本滚动 | ⚠️ 部分可行 | C 层可重建（ps1 全流程），Java 层无自动化对比/合入机制，pro 版本无 pin |

**核心结论**：delua 的 AndroLua 运行环境是 pro 4.4.2 的**纯继承体**（Java 侧零新增类、Lua 侧逐字节、原生侧最小补丁），宿主隔离通过「独立模块 + 命名空间 + 单一桥接面」实现，满足「与 pro 保持一致、与其他代码解耦」的目标；版本滚动机制存在缺口，见 §5。

---

## 2. 运行环境全量清单

### 2.1 模块与目录布局

| 路径 | 内容 | 归属 |
|---|---|---|
| `androlua/src/main/java/` | 402 个 Java 文件（pro 继承体） | 运行时 |
| `androlua/src/main/kotlin/com/lioisme/delua/androlua/` | 9 个 Kotlin 文件（delua 管理层） | 运行时 |
| `androlua/src/test/kotlin/` | 4 个 JVM 单测（AXmlManifestTest、LuaCompilerTest、LuaConfigParserTest、PermissionResolverTest） | 运行时 |
| `androlua/libs/` | 空目录（仅 .gitkeep，`fileTree("libs")` 依赖预留位） | 运行时 |
| `androlua/src/main/assets/lua/` | 空目录（AAR assets 合并占位，Lua 脚本实际放 host 侧） | 运行时 |
| `prebuilt/{arm64-v8a,armeabi-v7a,x86_64}/` | 17 个 .so（二进制真源，md5 与 jniLibs 一致） | 原生 |
| `app/src/main/jniLibs/` | 同 prebuilt（打包入 APK；x86_64 另有未提交的 liblive2d.so） | 原生 |
| `app/src/main/assets/lua/` | 31 个 Lua 脚本（28 pro + 3 delua） | Lua |
| `app/src/main/assets/luadoc/`、`layouts/` | pro 语法手册、.aly 布局模板 | Lua |
| `build-scripts/jni/` | 17 个模块目录的 Android.mk + Application.mk（NDK overlay） | 原生 |
| `build-scripts/patches/` | 2 个 C 补丁（lsqlite3.c、zip/mkstemp.c） | 原生 |
| `build-scripts/live2d/` | live2d 实验模块（未提交、仅 x86_64） | 实验 |
| `scripts/rebuild_engine.ps1/.sh` | 原生库重建脚本（3 ABI） | 工具 |
| `docs/reference/androlua/` | pro 4.4.2 全量参考源码（gitignored，永不提交） | 基线 |

### 2.2 Java 层

- **包分布**（pro 原包名全保留）：`com.androlua`（52 类）、`com.luajava`（16 类）、`com.android.cglib.*`、`android.widget.*` / `android.content.*` / `android.annotation.*`（pro 自带垫片）、`com.nirenr.screencapture`。
- **delua 管理层（9 Kotlin，`com.lioisme.delua.androlua`）**：`AlpManager`（.alp 打包/解析）、`AlpNaming`、`AXmlManifest`（字节级 AXML 补丁）、`LuaCompiler`（luac 字节码化，复用 luajava `LuaState`）、`LuaConfigParser`、`PermissionResolver`、`PluginManager`、`ZipUtil`、`engine/EngineBridge`（117 个 JNI 符号声明，host 无关）。
- **manifest 注册面**（app 侧）：`com.androlua.LuaActivity`、`LuaActivityX`、`LuaService`、`LuaAccessibilityService`、`com.nirenr.screencapture.ScreenCaptureActivity`、`android.content.FileProvider`（meta-data 用 androidx 标准 `android.support.FILE_PROVIDER_PATHS`）+ host 的 `.engine.EngineActivity`。

### 2.3 原生库层（17 个 .so / ABI）

| .so | 模块来源 | 说明 |
|---|---|---|
| libluajava.so | `jni/luajava` + `jni/lua`（Lua 5.3.3 核心编译其中） | 唯一 JNI 桥，`LuaState.java:106` 单点加载 |
| libcjson / libmd5 / libcrypt / libzip / libxml / libregex / libzlib / libcanvas / libbson / liblfs / liblsqlite3 | 同名 jni 模块 | 与 pro 逐一对应 |
| libluv.so | `jni/luv` | 取代 pro 的 lluv（行为差异，见 §3.2） |
| libsocket.so + libmime.so | `jni/socket`（luasocket） | 同 pro |
| libsensor.so | `jni/sensor` | 同 pro |
| libgl.so | `jni/luagl` | 同 pro |

- **ABI**：`arm64-v8a` / `armeabi-v7a` / `x86_64`。pro 官方 APK 仅 arm 双 ABI；**x86_64 为 delua 补充**（模拟器验证用）。
- `prebuilt/` 与 `app/src/main/jniLibs/` 对应文件 MD5 全部一致，无缺失、无多余（x86_64 的 liblive2d.so 属未提交实验，除外）。
- pro 参考树**不含预编译 .so**（仅 C 源码），二进制一致性以「官方 APK 17 个 .so + 双端探针」实测锚定（见 `docs/kb/androlua-lua/PITFALLS.md`）。

### 2.4 Lua 脚本层（`app/src/main/assets/lua/`，31 个）

- **pro 28 个**逐字节一致：autotheme、base64、bin、bmob、check、console、ftp、hex、http、import、json、loadbitmap、loadlayout、loadmenu、logcat、ltn12、mbox、mime、options、permission、smtp、socket、su、test、xml 等。
- **delua 新增 3 个**：`loadlayout2.lua`、`loadlayout3.lua`、`xml2table.lua`（layouthelper 家族扩展，pro 无对应物）。
- ⚠️ `prebuilt/lua/` 是**过期快照**（仅 28 个，缺新增 3 个），且未被任何构建消费，见 §5.2。

### 2.5 构建配置

- `build-scripts/jni/Application.mk`：`APP_ABI` 三 ABI、`APP_PLATFORM android-24`、`APP_STL c++_static`、release。
- Android.mk 集合 17 模块；pro 的 19 个 jni 目录中禁用 **lluv**（被 luv 取代）与 **tcc**（无 arm64/x86_64 目标）。
- 补丁 2 个：`lsqlite3.c`（`lua_strlen`→`lua_rawlen`，Lua 5.3 等价修复，UTF-8 无 BOM）、`zip/mkstemp.c`（补 `#include <unistd.h>`，NDK r29/C99 适配）。
- R8：`app/proguard-rules.pro` keep `com.luajava.**`、`com.androlua.**`、`com.android.cglib.**`、`com.nirenr.**`、`com.lioisme.delua.androlua.**`、`android.widget.**` 等；`app/build.gradle.kts` 的 `luaApiSurface` 在 release 构建后用 mapping 文件校验 Lua 可见 API 未被 R8 移除。
- 编译验证命令（AGENTS.md）：`.\gradlew.bat :app:compileArm64DebugKotlin` / `:app:assembleArm64Debug`。

---

## 3. 与 pro 4.4.2 一致性结论

### 3.1 Java 层（402 个文件）

- **387 个逐字节一致（96.3%）**；0 个 delua 新增 Java 类（证明 Java 侧是纯继承体）。
- **15 个修改文件**，分两类：

**A. 等值适配（注释/文档剥离 + androidx 注解替换，逻辑等值调用）**：

| 文件 | 修改内容 |
|---|---|
| `android/content/FileProvider.java` | androidx 注解替换 + 注释剥离 |
| `android/widget/ArrayPageAdapter.java`、`BasePageAdapter.java` | 同上 |
| `android/widget/PageView.java` | 删除 572 行注释，逻辑等值 |
| `android/widget/FloatButton.java`、`ToolBar.java` | 同上 |
| `com/androlua/LuaResources.java` | 注释/文档剥离 |
| `com/androlua/LuaWebView.java`、`util/GlobalActionAutomator.java` | 同上 |

**B. 行为修改（需在滚动时重点复核）**：

| 文件 | 修改内容 | 风险 |
|---|---|---|
| `LuaApplication.java` | +40 行 `extractLuaScripts`（资产提取叠加层，R4 收口） | 中：内嵌在 pro 文件内，滚动需手工合并 |
| `LuaDexLoader.java` | +22 行 InMemoryDexClassLoader 链（LIBS-001，writable-dex 加载） | 中：同上 |
| `LuaActivity.java` | 删除 pro 的 onHook/Xposed 检测（check1/check2/check3 等 117 行）；`luaDir` 改 protected（host EngineActivity 依赖）；新增 mScriptError 回传；loadApp/loadDex 签名整理 | **高**：删除安全检测是**有意的行为差异**，需文档化；protected 字段是 host 耦合点 |
| `LuaService.java` | dexCache 类型随 LuaDexLoader 链调整 | 中 |
| `LuaAccessibilityService.java` | `onDestroy` 追加 `stopScreenshot()`（资源泄漏修复，尚未提交） | 低 |
| `LuaState.java` | +1 行：117 个 JNI 符号声明 | 低 |

- **41 个未移植文件**，分组与理由：

| 分组 | 文件 | 排除理由 |
|---|---|---|
| textwarrior 编辑器族（28） | `com.myopicmobile.textwarrior.{android,common}.*` | 由 `:editor`（sora-editor）取代 |
| IDE 专属（5） | `Main.java`、`Welcome.java`、`LuaEditor.java`、`LuaLexer.java`、`LuaTokenTypes.java` | 由 `:app`/`:editor` 取代 |
| 分屏视图（1） | `SplitEditView.java` | 同上 |
| android.support 兼容垫片（3） | `support/annotation/{IntRange,RequiresApi}.java`、`support/v4/app/Fragment.java` | androidx 原生提供，无需垫片 |

### 3.2 原生层

- 与 pro 官方 APK 17 个 .so 清单对齐（`docs/kb/androlua-lua/PITFALLS.md` 记录过 delua 曾因源码树误判移除 lfs/lsqlite3、后按 APK 实测恢复的教训）。
- 差异仅 2 个：**lluv→luv 取代**（luv 为 lluv 继任者，能力等价）、**tcc 禁用**（pro 的「编译 Lua」能力由 Java 侧 `LuaCompiler` 用 luajava `LloadBuffer`+`dump` 实现，行为等价）。
- 补丁为最小等价修复/编译适配，无功能改动。

### 3.3 Lua 脚本层

- 28/28 逐字节一致；3 个 delua 新增为 loadlayout 家族扩展，不触碰 pro 文件。
- 双端（pro 官方 APK vs delua）探针结果一致（`ANDROLUA_PARITY_DUAL_END_FINAL_VERIFICATION.md`）。

---

## 4. 耦合面与边界（解耦审计）

```
:app (com.lioisme.delua)  ──►  :androlua (com.androlua / com.luajava / ...)
     │                            │ (零依赖 app / editor)
     └──►  :editor (sora-editor)  ◄── 零依赖（R1：luajava/androlua/loadLibrary 零命中）
```

- **`:androlua` → host 零引用**：402 个 Java + 9 个 Kotlin 中 `import com.lioisme.delua`（非 androlua 子包）零命中。
- **R1 边界**：`rg -n "luajava|androlua|loadLibrary" editor/src` 零命中（AGENTS.md 验证命令）。
- **host 桥接面（唯一允许耦合点）**：
  - `DeluaApp : LuaApplication`：onCreate 追加插件/布局/luadoc 提取与 TextMate 预载。
  - `EngineActivity : LuaActivity`：`getLuaPath` 覆写 + `luaDir`（protected）依赖 + `sendMsg/sendError` 回传 `EXTRA_OUTPUT/EXTRA_ERROR`。
  - `RunEngineLauncher`、`build/ApkBuilder` 管线（host 侧，仅调用 androlua 的工具层）。
  - `EngineBridge`（androlua 侧）为纯 JNI 声明，无 host 类型。

---

## 5. 版本滚动（与 pro 升级对齐）

### 5.1 现状机制

- **C 层**：`rebuild_engine.ps1` 全流程 = 从 reference 复制 C 源码 → 叠加 `build-scripts/jni` 的 Android.mk → 应用 `build-scripts/patches` → ndk-build → 输出 `prebuilt/` + `jniLibs/`。**注意：`rebuild_engine.sh` 未实现补丁应用**（与 ps1 不一致）。
- **Java 层**：无机制。唯一的差异检测手段就是本次审计使用的全量 MD5 对比，需人工移植 + 回归。
- **Lua 层**：reference `assets/lua` 手工复制。

### 5.2 缺口清单

1. **pro 版本无 pin**：参考树仅靠目录名（v4.4.2），无 URL/commit/校验和记录。
2. **`docs/reference/` gitignored**：CI/换机后无法重建、无法跑对比。
3. **`prebuilt/lua/` 快照过期**（缺 3 个 delua 脚本），且无消费方——要么删除、要么纳入构建产物。
4. **二进制双真相源**：`prebuilt/` 与 `jniLibs/` 两份 .so，需一致性校验（当前靠人工 MD5）。
5. **Java 内嵌耦合点**：`LuaApplication`/`LuaDexLoader`/`LuaActivity` 中的 delua 代码在 pro 升级时需手工合并（§3.1 B 类）。
6. **配置同步面**：R8 keep 清单、`luaApiSurface`、manifest 注册面需随 pro 演进同步更新；`LuaCompiler` 依赖的 Lua 版本与 .so 绑定。
7. **无回归脚本**：升级后应跑双端探针（`docs/kb/androlua-lua/` 体系）+ `:app:assembleArm64Debug`。

### 5.3 建议（按优先级）

1. 在 `docs/reference/README.md`（不入 git 的目录里建）记录 pro 版本 pin：下载 URL、目录校验和、比对基准日期。
2. 把本次 MD5 对比命令固化为 `scripts/compare_engine.ps1`（输出 modified / new / excluded 三清单），并登记 AGENTS.md。
3. 补齐 `rebuild_engine.sh` 的补丁应用逻辑，与 ps1 行为对齐。
4. 明确 `prebuilt/lua` 归属（删除或纳入发布清单）。
5. 升级 SOP：拉新树 → 跑对比 → 审阅 B 类修改是否可吸收/归并 → 更新 patches → 重建 .so → 双端探针 → 更新 R8/luaApiSurface → 全量构建。

---

## 6. 遗留风险

| 风险 | 说明 |
|---|---|
| 未提交工作区改动 | `LuaAccessibilityService`（stopScreenshot）、`check.lua`（String.len 断言修复）、`docs/kb/androlua-lua/*`、示例 .alp、ExamplesScreen/strings 更新——建议尽快提交收口 |
| live2d 实验未收口 | `build-scripts/live2d/` + 仅 x86_64 的 `liblive2d.so`（prebuilt 与 jniLibs 各一份）均未提交；混在二进制目录中，需明确「实验/正式」归属 |
| 反 Xposed 检测移除 | pro 的 `LuaActivity` 安全检测被有意删除（-117 行），属于**安全性行为差异**，需在文档中明示 |
| 内嵌业务代码 | `LuaApplication`/`LuaDexLoader` 中 delua 代码与 pro 源码同文件，是滚动合并冲突高发区（§5.2-5） |
| android.support 字符串残留 | `FileProvider` 的 `FILE_PROVIDER_PATHS`、CardView 注释等，均系源码原本内容，与 androidx FileProvider 兼容，无害 |
| reference 编码 | pro README 为 GBK，批处理脚本按编码注意处理 |
| 语义差异锚点 | 一致性以「逐字节 + 官方 APK 实测」双锚定；pro 若发布新版（无官方版本号体系），需重新跑双端验证 |

---

## 7. 附录

### 7.1 验证命令

```powershell
# R1 边界（AGENTS.md 硬门槛，须零命中）
rg -n "luajava|androlua|loadLibrary" editor/src

# Java 层 MD5 全量对比（pro vs delua）
# 以 docs/reference/androlua/AndroLua_pro-master/app/src/main/java 为基准，
# 相对路径求并集 → identical / modified / pro-only excluded 三清单

# 原生层双向对比
Get-ChildItem prebuilt -Recurse -Filter *.so | ForEach-Object { (Get-FileHash $_.FullName -Algorithm MD5).Hash }
Get-ChildItem app/src/main/jniLibs -Recurse -Filter *.so | ForEach-Object { (Get-FileHash $_.FullName -Algorithm MD5).Hash }

# 构建验证
.\gradlew.bat :app:compileArm64DebugKotlin --console=plain
.\gradlew.bat :app:assembleArm64Debug --console=plain
```

### 7.2 相关文档

- 前序审计：`ANDROLUA_SOURCE_CODE_AUDIT_REPORT.md`、`ANDROLUA_PARITY_FULL_AUDIT_REPORT.md`、`ANDROLUA_FEATURE_PARITY_AUDIT.md`、`ANDROLUA_PARITY_DUAL_END_FINAL_VERIFICATION.md`、`NATIVE_LIB_AUDIT.md`、`DEPENDENCY_MODEL_AUDIT_REPORT.md`、`ENGINE_LEVEL_COMPARISON_REPORT.md`、`FULL_AUDIT_REPORT_2026-08-01.md`
- 实施：`ANDROLUA_INTEGRATION_PLAN.md`、`ENGINE_MIGRATION_WORK_ORDER.md`、`LIBS_SUPPORT_WORK_ORDER.md`
- 知识库：`docs/kb/ARCHITECTURE.md`、`docs/kb/ENGINE_RUNTIME.md`、`docs/kb/androlua-lua/`（双端探针、PITFALLS）
