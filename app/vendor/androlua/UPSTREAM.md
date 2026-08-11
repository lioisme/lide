# UPSTREAM.md — app/vendor/androlua 上游台账

> 本文件是 `app/vendor/androlua` 与上游 AndroLua Pro 之间**唯一改动登记**（源码组织审计 Phase 2，
> V2-2 / V3-2 / V3-3 闭环；2026-08-09 全树 diff 审计收口）。「vendor 只读」声明以此台账为准：
> 白名单外任何新增改动必须在下方登记，否则门禁视作漂移。台账的机器可读副本为
> `scripts/upstream-pristine.json`（两者必须一致，脚本自检清单录入错误）。

## 1. 上游版本信息

| 项 | 值 |
|---|---|
| 上游仓库 | https://github.com/nirenr/AndroLua_pro |
| 上游版本 | AndroLua Pro v4.4.2（master 分支，2021 年停更） |
| 上游 tree sha | `abe5c25148a60ee44ddcccc212db6b96e836c793`（GitHub API 递归全树提取比对基准） |
| 导入日期 | 2026-08-05（基线迁入 `third_party/` 起；2026-08-08 引擎 dex 单份化时收编 Java 运行时；2026-08-09 随源码组织三需求迁入 `app/vendor/`） |
| 导入方式 | Java 运行时 → `:androlua` 模块（`src/main/java` 全量 330 文件，逐文件比对结论见 `docs/kb/ENGINE_RUNTIME.md` 与本文档删除/改动登记）；JNI 源码 → `app/src/main/cpp/`；Lua 资产 → `app/src/main/assets/` |
| 验证基线 | 运行时 Java 面 100% 完整，删除项全部有据（历史审计 JAVA_LIBRARIES_AUDIT_REPORT 2026-08-08，结论已蒸馏于本文档） |

## 2. 删除项登记（相对上游树，42 个文件，全量清单）

| 目录（上游路径） | 文件数 | 理由 |
|---|---|---|
| `android/support/multidex/` | 3 | MultiDex 引导已被系统 native multidex 取代，无运行期引用 |
| `android/support/v4/app/Fragment.java` | 1 | support-v4 桩，宿主用 androidx Fragment，无引用 |
| `com/androlua/LuaEditor.java` / `LuaLexer.java` / `LuaTokenTypes.java` | 3 | 上游自带编辑器被 sora-editor 取代 |
| `com/androlua/Main.java` / `Welcome.java` | 2 | IDE 壳 Activity，宿主 UI 已 Compose 重写 |
| `com/myopicmobile/textwarrior/android/*` | 9 | 上游文本编辑器（textwarrior），被 sora-editor 取代 |
| `com/myopicmobile/textwarrior/common/*` | 23 | 同上（词法/文档/搜索内核） |
| `com/nirenr/SplitEditView.java` | 1 | 上游分屏编辑视图，宿主无引用 |

- **合计 42 个**，逐文件清单以 `scripts/upstream-pristine.json` `deleted` 为准（上游独有即
  vendor 已删的 .java 必须 ⊆ 该清单，CI 全树 diff 门禁断言）。
- 依据：历史审计（SOURCE_AUDIT_REPORT_2026-08-05 §1.4 / JAVA_LIBRARIES_AUDIT_REPORT 删除清单，结论蒸馏于本文档登记）。

> 注：`android/support/annotation/*`（IntRange / RequiresApi）曾于 2026-08-08 引擎 dex
> 单份化时删除，**2026-08-09 已按上游原样还原**（用户红线：禁止魔改上游；注解为元数据，
> 无运行期语义，还原后不再属于改动）。`verifyVendorIsolation` 断言其存在（IntRange /
> RequiresApi 两个文件）。

## 3. 改动登记（24 个修改文件，逐项分类）

> 用户红线（2026-08-09）：禁止魔改或强耦合依赖上游。androlua 特殊基线允许**非逻辑适配**
> （运行环境兼容性修正 / 构建机制差异 / 上游技术落后时的现代化替代）与**有意逻辑修复**
> （内存泄漏等缺陷修复，全部登记于下）。CI 以全树 diff 白名单校验（修改文件 ⊆ 本台账
> 3.x 全部条目）。sora-editor 保持 pristine（零改动）。
>
> 2026-08-09 全树 diff 审计：以 `abe5c251` 上游树逐文件比对（忽略行尾/空白），24 个文件
> 存在实质差异，全部登记如下；**本地独有 .java 为 0**（残留 `LuaConfigParser.java` 死文件
> 已删除——其 Lua/Kotlin 等价实现早已归位 `app/src/main/kotlin/com/lioisme/lide/{build,project}/`）。

### 3.1 W^X 修复：`com/androlua/LuaDexLoader.java`（非逻辑适配 + 返回值放宽）

- **改动**：API ≥ 26 走 `InMemoryDexClassLoader`（`ByteBuffer.wrap` 内存加载），替代原磁盘路径加载；
  伴随 `loadApp` / `loadDex` 返回类型 `LuaDexClassLoader` → `ClassLoader`（缓存类型同步放宽）
- **理由**：Android 8.0+ W^X 执行段保护禁止从可写目录直接加载 dex（工作单 §10.1 有意的、记录在案的与 pro 差异；属运行环境适配，非业务逻辑改动）
- **日期**：2026-08-05（LIBS_SUPPORT_WORK_ORDER §10.1）
- **禁令**：不得回退为磁盘路径加载（验证命令 `rg -n "InMemoryDexClassLoader" app/vendor/androlua/src` 须命中）

### 3.2 构建机制适配（注解剥离 / 资源引用替换）

- **注解剥离**：`androidx.annotation` 导入剥离（`LuaResources.java` @NonNull/@Nullable、
  `util/GlobalActionAutomator.java` @RequiresApi、`LuaActivity.getType` @NonNull 等；
  实证 0 命中）；注解即元数据、无运行期行为差异。
- **资源引用替换**：`R.drawable.icon` → `android.R.drawable.sym_def_app_icon`
  （`android/widget/FloatButton.java`、`android/widget/ToolBar.java`、`com/androlua/LuaWebView.java`）——
  上游 res 未 vendor（R5 精简），原引用无法编译，属强制构建适配，非逻辑。
- **命名空间注入（2026-08-11 解耦审计 A-1 补登）**：`build.gradle.kts` `namespace =
  "com.lioisme.lide.androlua"`——AGP 要求模块唯一命名空间；本模块无 res（R5 精简）→
  不生成 R 类、运行类名保持上游 `com.androlua.*` 不变，纯构建期元数据、零逻辑影响。
  `verifyVendorIsolation` 断言该形态（漂移即失败），沿用后禁止改为其他值。
- **理由**：引擎类并入宿主单一 dex 集（2026-08-09 源码组织三需求：打包产物不需要专门的 dex 拆分，
  引擎类经 `implementation` 随宿主全量 dex 直用）；不 vendor 上游 res（精简治理）
- **日期**：2026-08-08（剥离）→ 2026-08-09（annotation 还原 + 并入单 dex 集 + 台账补登）

### 3.3 引擎原生扩展（LIDE 功能增量，宿主侧资产，不属上游改动）

| 路径 | 内容 | 登记 |
|---|---|---|
| `app/src/main/cpp/lua/lide_des.c/.h` | DES 加解密扩展（打包加密链使用，工作单 §4 密钥约定） | 编入 `cpp/lua/Android.mk` |
| `app/src/main/cpp/live2d/` | live2d 独立原生模块 | 顶层 `Android.mk` 独立模块声明 |

- **理由**：LIDE 打包加密 / 运行期扩展需求，属宿主功能增量，与上游基线无关
- **日期**：2026-08-05 起持续

### 3.4 有意逻辑修复/增强（13 个文件，2026-08-09 台账补登）

| 文件 | 改动 | 理由 |
|---|---|---|
| `android/content/FileProvider.java` | 路径策略保持 `android.support.FILE_PROVIDER_PATHS` 元数据名，宿主在 `app/src/main/res/xml/androlua_filepaths.xml` 白名单化 | 应用内自签名安装 / 文件共享只放行工程目录（部署路径适配） |
| `com/androlua/LuaActivity.java` | `luaDir` → protected；移除 anti-xposed 环境检测（xposed 检测链已删除）；`R.drawable.icon` → 系统图标；`onDestroy` 后台线程 `lua_close` + 延迟回收（泄漏修复）；新增 `onRequestPermissionsResult` 动态权限路由（ENGINE_MIGRATION P-3.6）；`getType` @NonNull 剥离 | 宿主 EngineActivity 直启需要 protected luaDir；权限路由与泄漏修复属有意修复（设备实测） |
| `com/androlua/LuaApplication.java` | 新增 `extractLuaScripts`（assets/lua 解包 + MD5 版本化） | 打包产物 Application 机制（P0-7：LuaApplication.extractLuaScripts 解包 assets/lua） |
| `com/androlua/CrashHandler.java` | 完整重写：崩溃日志落 `filesDir/crash/` 私有目录 + 默认处理器终止进程 | 原 pro 实现落 SD 卡公开目录 + 吞异常；重写为宿主异常上报前置（compose UI 展示） |
| `com/androlua/LuaAccessibilityService.java` | 新增 `startScreenshot(VirtualDisplay.Callback)` 重载 | 截图回调能力增强（脚本端截图 API） |
| `com/androlua/LuaAsyncTask.java` | `closeLua()` 后台线程收口（onPostExecute 全量 GC → 显式 close） | 内存泄漏修复系列：每任务泄漏原生 lua_State（2026-08 实测） |
| `com/androlua/LuaRunnable.java` | 线程退出 finally 中显式 close | 同上 |
| `com/androlua/LuaThread.java` | quit 路径补 `regGc`（loop 模式线程 Looper 挂起、L 永不关闭）| 同上 |
| `com/androlua/LuaService.java` | `loadDex` 返回 `ClassLoader`；onDestroy 对 gclist 全量 gc | 同上 + 与 LuaDexLoader 3.1 返回值对齐 |
| `com/androlua/LuaTimer.java` | cancel 前在 Timer 线程排一次性收口任务（`LuaTimerTask` 独立 LuaState 关闭 + Timer 线程退出） | 同上 |
| `com/androlua/LuaTimerTask.java` | 原 tick 全量 GC → cancel 后 tick 结束 finally 关闭 | 同上 |
| `com/luajava/LuaJavaAPI.java` | `pushJavaObject` 计数器超限抛异常 → 周期告警 | 泄漏探测降级（看板娘 120fps 场景 10s 崩溃实证） |
| `com/luajava/LuaObject.java` | `finalize` 前检查状态未关闭（use-after-free SIGSEGV 崩溃修复） | 同上系列（FinalizerDaemon 回收竞态） |
| `com/luajava/LuaState.java` | `close()` 幂等化（`luaState == 0` 提前返回，防双重 _close 悬空） | 同上系列 |
| `com/luajava/LuaStateFactory.java` | `states.put(idx, null)` → `states.remove(idx)` | 同上系列（避免表内空槽残留） |

### 3.5 非逻辑净化 / 技术现代化替代（5 个文件）

> 用户裁定（2026-08-09）：上游技术落后（2021 年停更）可用新技术/净化替代，登记即可。

| 文件 | 改动 | 类别 |
|---|---|---|
| `android/widget/PageView.java` | 注释/许可证头清除 + 变量重命名（`ii`→`info`）+ 行为等价微调 | support-v4 ViewPager 拷贝的本地净化（上游实现已由 androidx ViewPager 替代） |
| `android/widget/BasePageAdapter.java` | 注释/许可证头清除 | 同上 |
| `android/widget/ArrayPageAdapter.java` | 注释/许可证头清除 | 同上 |
| `android/widget/ToolBar.java` | `R.drawable.icon` → 系统图标（3.2）+ 空白风格净化 | 构建适配 + 净化 |

### 3.6 上游改写位置（历史登记，已被 3.4/3.5 全量覆盖）

- `cpp/lsqlite3/Android.mk`：编译开关与平台适配调整（lsqlite3 原生模块随引擎源码构建，WHITEPAPER §7.1），2026-08-05
- `android/content/FileProvider.java`、`com/androlua/LuaDexLoader.java`：见 3.4 / 3.1

## 4. 维护约定（门禁联动）

- **白名单双源必须一致**：`app/vendor/androlua/UPSTREAM.md`（人工台账）与
  `scripts/upstream-pristine.json`（机器副本：24 modified + 42 deleted）。改台账必须同步改 JSON。
- **全树 diff 门禁**：`scripts/verify-upstream-pristine.ps1`（Windows 本地）/
  `scripts/verify-upstream-pristine.sh`（CI ubuntu，`verify-vendor` job 挂接）。以上游
  `abe5c251` 树（缺省自动从 GitHub codeload 下载，不落地仓库，符合 R5）逐文件比对：
  1. vendor 本地独有 .java → 必须 0（白名单无新增类）；
  2. 双侧共有文件实质差异（忽略行尾/空白）→ 必须 ⊆ 24 文件白名单；
  3. 上游独有（已删）→ 必须 ⊆ 42 文件白名单。
- **verifyVendorIsolation（gradle）**：上游目录零 `com/lioisme/**`（R-2）、UPSTREAM.md 存在、
  台账登记项与源码实证一致（W^X 命中 / androidx.annotation 零命中 / support annotation 还原 /
  :androlua 构建脚本零第三方依赖 / sora 构建脚本无外部 URL）。
- 新增改动必须先改台账 + JSON 再改代码；台账/JSON/源码不一致 = 门禁失败。
