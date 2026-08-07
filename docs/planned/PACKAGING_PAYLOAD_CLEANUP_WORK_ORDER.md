# 打包载荷精化工作单（仅含 androlua 工程所需内容）

> 日期：2026-08-07
> 状态：**已完成**
> 触发：打包构建 APK 时宿主 IDE 专属内容（内置 `.alp` 示例工程、API 类表等）被打入产物。
> 目标：产物仅包含「androlua 工程真正所需内容」，从架构上正确识别，而非简单文件黑名单过滤。
> 验收证据见 §7。

---

## 1. 审计结论（问题定位）

### 1.1 根因

- `ApkBuilder` 装配循环对宿主条目采用**黑名单前缀剔除**策略（旧 `HOST_ASSET_EXCLUDED`
  仅列 `assets/textmate/`、`assets/keys/`、`assets/layouts/` 三项），其余一律原样拷贝
  （旧 `ApkBuilder.kt:236-242`）。
- 宿主 IDE 专属资产 `assets/examples/*.alp`（7 个内置示例，共约 8.99 MiB，其中
  `live2d.alp` 8.74 MiB）与 `assets/javaapi/android.lua`（约 159 KiB）未被列入 → 全量进入产物。
- 黑名单策略**结构上不可持续**：每次宿主新增 IDE 资产（`examples/`、`javaapi/` 均由
  `generateExamples` / IDE API 浏览器消费）都必须手工补条目，漏配必然发生且无告警。

### 1.2 上游基线（AndroLua Pro）对照

- pro 打包脚本 `bin.lua:300-301` 对宿主 `^assets/`、`^lua/` 整树默认剔除，需要的内容经
  `checklib` 依赖分析按需召回——即 **「默认拒绝 + 白名单召回」**。
- lide 的 `assets/lua/**` 已实现白名单召回（`NativeDependencyAnalyzer` + `ENGINE_LUA_WHITELIST`），
  方向正确但未覆盖宿主其他 assets，方向与 pro 相反。

### 1.3 产物运行期真实消费面（判定依据）

- `LuaApplication.extractLuaScripts` 仅解包 `assets/lua/**`；
- 产物 launcher 已重定向 `EngineActivity`（运行 filesDir/lua 工程），IDE 屏幕不可达；
- `assets/examples/`（ExamplesScreen 用）、`assets/javaapi/`（ApiIndex 用）、
  `assets/layouts/`（IDE 布局助手用）均为 IDE 专属，产物运行期零消费。

---

## 2. 方案（优雅解决）

以 **「产物 APP 运行期是否消费该能力」** 为唯一依据，将宿主条目显式分类；
**默认拒绝（default-deny）**，而非黑名单剔除：

| 领域（`HostEntryDomain`） | 判定 | 产物处理 |
|---|---|---|
| MANIFEST | `AndroidManifest.xml` | 替换为 patched 版 |
| RUNTIME_CORE | `classes*.dex` / `resources.arsc` / `res/**` / `META-INF/**` | 原样保留 |
| ENGINE_LUA | `assets/lua/**` | 白名单 + 依赖闭包召回（现有逻辑） |
| NATIVE_LIB | `lib/**` | ABI × 依赖二维过滤（现有逻辑） |
| HOST_ONLY（**兜底**） | 其余任何 `assets/**` | **默认剔除** |

> 新增宿主 IDE 资产 → 天然不被打入，无需改打包代码（漂移/漏配免疫），对齐 pro `^assets/` 语义。

---

## 3. 实施明细

### 3.1 新增 `app/src/main/kotlin/com/lioisme/lide/build/HostPolicy.kt`

- `enum class HostEntryDomain`（MANIFEST / RUNTIME_CORE / ENGINE_LUA / NATIVE_LIB / HOST_ONLY）；
- `fun classifyHostEntry(entryName): HostEntryDomain`（纯函数）：manifest 精确命中 →
  `assets/lua/` → `lib/` → `assets/`（HOST_ONLY 兜底）→ 根级核心面。

### 3.2 `app/.../build/ApkBuilder.kt`

- `ASSEMBLE` 装配循环接入 `HostPolicy.classifyHostEntry`：HOST_ONLY 一律剔除；
  ENGINE_LUA / NATIVE_LIB 沿用既有白名单 / ABI 过滤分支；
- 删除旧黑名单常量 `HOST_ASSET_EXCLUDED`；
- 类 KDoc 更新为「默认拒绝 + 领域分类」描述。

### 3.3 `app/.../ui/screen/BuildApkScreen.kt`

- `HostOnlyStats(entries, bytes)` 载荷摘要：预览阶段经同一
  `HostPolicy.classifyHostEntry` 统计「宿主 IDE 内容剔除 N 个 / 约 X」；
- 新增字符串 `build_dep_host_only`（values / values-zh）。

### 3.4 测试 `app/src/test/.../build/ApkBuilderTest.kt`

- 合成宿主 APK 增补 `assets/examples/{demo,live2d}.alp`、`assets/javaapi/android.lua`、
  `assets/layouts/javaapi/layout.aly`；
- 新增用例：`host ide-only assets like builtin examples and api class table excluded`；
- 新增用例：`host policy classifies entries with default-deny for unknown assets`
  （含未来未知资产兜底断言）。

---

## 4. 验收标准

1. 宿主 IDE 资产不进入产物：产物 ZIP 中 `assets/examples/`、`assets/javaapi/`、
   `assets/keys/`、`assets/textmate/`、`assets/layouts/` 零条目。
2. 引擎 Lua 白名单召回不受影响：`assets/lua/import.lua` 等按需保留。
3. UI 载荷摘要正确展示剔除条目数 / 体积。
4. 旧宿主 APK（含历史资产如 `help.txt`、`luadoc/`）也能正确剔除（default-deny 击败漂移）。
5. R1 边界：`:editor` 无改动；打包逻辑全部在 `:app` 宿主域。

---

## 5. 回归范围

- build 单测：`:app:testArm64DebugUnitTest --tests "com.lioisme.lide.build.*"`；
- 完整构建：`:app:assembleArm64Debug`；
- 打包/签名/加密链回归：`:app:testArm64DebugUnitTest --tests "com.lioisme.lide.build.*"`（覆盖 ApkBuilder/ApkSigner/LuaCrypto）。
- R1 门禁：`rg -n "luajava|androlua|loadLibrary" editor/src` 零命中（未触碰 editor）。

---

## 6. 遗留与边界（如实声明）

- 产物 dex 仍为宿主整体合并单 dex（IDE 类与引擎运行时共生），pro 亦然；dex 级剥离
  （R8 tree-shake 产出独立 engine 包）属更大工程，不在本次范围，`ApkBuilder.kt` KDoc 已注明。
- 设备端真机/模拟器产物运行回归（EngineActivity 直达 + live2d 示例）待补验（见 §7 状态）。

---

## 7. 完成记录与验收证据（2026-08-07）

| 项 | 证据 |
|---|---|
| HostPolicy.kt 落地 | `app/src/main/kotlin/com/lioisme/lide/build/HostPolicy.kt`（新增） |
| ApkBuilder 接入 + 删黑名单 | `ApkBuilder.kt` ASSEMBLE 循环改领域分类，`HOST_ASSET_EXCLUDED` 已删除 |
| UI 载荷摘要 | `BuildApkScreen.kt`（HostOnlyStats + `build_dep_host_only` 中英双语） |
| 单测 | `ApkBuilderTest` build 包 **17/17 通过**（原 15 + 新 2）`testArm64DebugUnitTest`（2026-08-07 实跑） |
| 完整构建 | `:app:assembleArm64Debug` **BUILD SUCCESSFUL**（2026-08-07 实跑，125 tasks） |
| R1 门禁 | `editor/src` grep `luajava\|androlua\|loadLibrary` **零命中**（2026-08-07 实跑）；`git ls-files '*.so' '*.dex' '*.zip'` 空、`*.jar` 仅 wrapper 引导例外 |