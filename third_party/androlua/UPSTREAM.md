# UPSTREAM.md — third_party/androlua 上游台账

> 本文件是 `third_party/androlua` 与上游 AndroLua Pro 之间**唯一改动登记**（源码组织审计 Phase 2，
> V2-2 / V3-2 / V3-3 闭环）。「vendor 只读」声明以此台账为准：白名单外任何新增改动
> 必须在下方登记，否则 `verifyVendorIsolation` 门禁视作漂移。

## 1. 上游版本信息

| 项 | 值 |
|---|---|
| 上游仓库 | https://github.com/nirenr/AndroLua_pro |
| 上游版本 | AndroLua Pro v4.4.2（master 分支，2021 年停更） |
| 上游 tree sha | `abe5c25148a60ee44ddcccc212db6b96e836c793`（GitHub API 递归全树提取比对基准） |
| 导入日期 | 2026-08-05（基线迁入 `third_party/` 起；2026-08-08 引擎 dex 单份化时收编 Java 运行时） |
| 导入方式 | Java 运行时 → `:androlua` 模块（`src/main/java` 全量 330 文件，逐文件比对结论见 `docs/planned/JAVA_LIBRARIES_AUDIT_REPORT.md`）；JNI 源码 → `app/src/main/cpp/`；Lua 资产 → `app/src/main/assets/` |
| 验证基线 | `docs/planned/JAVA_LIBRARIES_AUDIT_REPORT.md`（2026-08-08）：运行时 Java 面 100% 完整，删除项全部有据 |

## 2. 删除项登记（相对上游树）

| 路径（上游） | 理由 | 依据 |
|---|---|---|
| `com/myopicmobile/textwarrior/*`（32 文件） | 已被 sora-editor 编辑器取代 | `docs/discard/SOURCE_AUDIT_REPORT_2026-08-05.md` §1.4 |
| IDE 壳 / SopEditView / multidex / support 桩 | 宿主 UI 已 Compose 重写，运行时无引用 | `JAVA_LIBRARIES_AUDIT_REPORT.md` 删除清单 |
| `android/support/annotation/*`（IntRange / RequiresApi） | 2026-08-08 引擎 dex 单份化时以纯 Java 常量等价剥离（annotation 依赖不得进引擎 dex） | `third_party/androlua/build.gradle.kts` 注释 |

## 3. 改动登记（白名单，逐项：改动 / 理由 / 日期）

### 3.1 W^X 修复：`LuaDexLoader.java`

- **改动**：API ≥ 26 走 `InMemoryDexClassLoader`（`ByteBuffer.wrap` 内存加载），替代原磁盘路径加载
- **理由**：Android 8.0+ W^X 执行段保护禁止从可写目录直接加载 dex（工作单 §10.1 有意的、记录在案的与 pro 差异）
- **日期**：2026-08-05（LIBS_SUPPORT_WORK_ORDER §10.1）
- **禁令**：不得回退为磁盘路径加载（验证命令 `rg -n "InMemoryDexClassLoader" third_party/androlua/src` 须命中）

### 3.2 引擎 dex 单份化收编（2026-08-08，P0 契约联动）

- **改动**：`androidx.annotation` 全剥离（0 命中实证）；`:androlua` 构建脚本零第三方依赖（`build.gradle.kts` 仅 `com.android.library` 插件）
- **理由**：引擎类唯一一份经 `injectEngineDex<Variant>` 注入宿主 APK 根目录（用户红线：dex 只在安装包根目录、无重复副本）
- **日期**：2026-08-08

### 3.3 引擎原生扩展（LIDE 功能增量）

| 路径 | 内容 | 登记 |
|---|---|---|
| `app/src/main/cpp/lua/lide_des.c/.h` | DES 加解密扩展（打包加密链使用，工作单 §4 密钥约定） | 编入 `cpp/lua/Android.mk` |
| `app/src/main/cpp/live2d/` | live2d 独立原生模块 | 顶层 `Android.mk` 独立模块声明 |

- **理由**：LIDE 打包加密 / 运行期扩展需求，属宿主功能增量，与上游基线无关
- **日期**：2026-08-05 起持续

### 3.4 上游改写位置（有 diff 基线，逐项登记）

| 文件 | 改动 | 理由 | 日期 |
|---|---|---|---|
| `android/content/FileProvider.java` | 路径策略保持 `android.support.FILE_PROVIDER_PATHS` 元数据名，宿主在 `app/src/main/res/xml/androlua_filepaths.xml` 白名单化 | 应用内自签名安装 / 文件共享只放行工程目录 | 2026-08-05 |
| `cpp/lsqlite3/Android.mk` | 编译开关与平台适配调整 | lsqlite3 原生模块随引擎源码构建（WHITEPAPER §7.1） | 2026-08-05 |
| `LuaDexLoader.java` | 见 3.1 | W^X | 2026-08-05 |
| CrashHandler 偏离 | 宿主异常上报走 Compose UI（`EngineActivity` / `EngineContract` 归位 app 后无独立 CrashHandler 类） | 与 pro 的已知差异 | 2026-08-08 |

> 注：`LuaActivity` / `LuaResources` / `GlobalActionAutomator` 的 annotation 剥离见 3.2，一并覆盖
> 「annotation 剥离 ×4」审计项。

## 4. 维护约定（门禁联动）

- 白名单 = 本台账 §3 全部条目；`verifyVendorIsolation` 任务断言：
  1. `third_party/androlua/src` 与 `third_party/sora-editor/src` 下不存在 `com/lioisme/**`（LIDE 代码零入驻上游目录）；
  2. 本台账存在且 §3 改动登记与实际源码一致（W^X 命中 / annotation 零命中 / 构建脚本零第三方依赖）；
  3. 仓库无任何二进制原生产物入库（R5，另由 `verify-no-binaries` 守护）。
- 新增改动必须先改本台账再改代码；台账与源码不一致 = 构建失败。
