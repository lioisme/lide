# lide 引擎与运行时知识库

> 来源：历史审计报告（ANDROLUA_SOURCE_CODE_AUDIT_REPORT / ENGINE_LEVEL_COMPARISON_REPORT / NATIVE_LIB_AUDIT /
> LIBS_SUPPORT_WORK_ORDER / EMULATOR_RUNTIME_AUDIT_REPORT / GAME_ENGINE_PERFORMANCE_AUDIT_REPORT，
> 均已删除，结论蒸馏于此）、`WHITEPAPER.md §7.1`。

---

## 1. 引擎选型结论（已定案）

| 候选 | 结论 | 原因 |
|------|------|------|
| **AndroLua Pro（nirenr）** | ✅ 采用 | Lua 5.3.3、21 原生模块、MIT 全源码、持续维护 |
| LuaJ 3.0.1 | ❌ 已废弃 | 纯 Java 慢 3~8×，兼容层 6 个 P0 缺陷；已随 WP-005 全量删除（~2300 行） |
| AndroLua（mkottman） | ❌ | Lua 5.1.5、2011 年归档 |
| AndroLuaX（Jesse205） | ❌ | 核心为反编译预编译 JAR + 18 个无符号表 .so，无法审计（SEC-01~05） |

参考源：`reference/androlua/AndroLua_pro-master/`（v4.4.2，权威纯净源，逐字节比对基准）。

**引擎魔改事实**：pro 对 Lua 5.3.3 深度魔改——至少 10 个核心 C 文件（llex/lparser/lcode/lvm/lapi 等）含 "//mod by nirenr"，
新增 9 个自定义语法（continue/switch/lambda/defer/when/toclose/table.const/`[]`/省略 then do），且 `LUA_VERSION_NUM` 未改。
→ **魔改版字节码与标准 Lua 5.3.3 不兼容**（版本号欺骗风险），外部 luac 工具链不可混用。

---

## 2. 原生模块与资产清单

### 2.1 原生模块（lide 内置 17 个 × 3 ABI；pro 15 个 × 1 ABI）

| 类别 | 模块 |
|------|------|
| 核心 | lua（5.3.3）、luajava（JNI 桥，117 个导出符号）、luagl（→ lide `gl`） |
| 与 pro 一致 | cjson、md5、crypt、zip、xml、regex、zlib、canvas、bson、luv、socket、mime、sensor |
| 已补齐 | **lfs、lsqlite3**（LIBS-003，NDK 29 预编译 + Lua 5.3 `lua_strlen` 兼容补丁） |
| 有意不提供 | **lluv**（与 luv 功能重叠）、**tcc**（NDK 仅 armeabi-v7a 有目标） |
| lide 超集 | `mime`（pro 未内置） |

- 产物：引擎由 AGP `externalNativeBuild`（ndk-build）在 `assemble` 时直接从源码构建并打包进 APK（NDK 固定 `29.0.14206865`）；源码与模块定义统一归档于 `app/src/main/cpp/`（顶层 `Android.mk` + `Application.mk`，`live2d/` 独立模块），无 `jniLibs` 缓存目录、无 `third_party/prebuilt/`（已删除，R5 红线：禁止二进制原生产物入库）。
- 构建：AGP `externalNativeBuild`（ndk-build）自动执行，无手动重建脚本（`scripts/rebuild_engine.*` 已废弃删除）；lfs/lsqlite3 随引擎源码构建。
- 缺失/第三方 .so 补偿：工程 `libs/` 自备 → `libsloader` 优先命中，否则 `package.loadlib("./libs/lib…so", "luaopen_…")`。
- 加载路径：`luaCpath = nativeLibraryDir + "/lib?.so" + libDir + "/lib?.so"`，无需宿主代码配合。

### 2.2 Lua 资产

- 内置 Lua 库 **31 个（pro 28 个的超集）**：28 个与 pro 逐字节一致 + 3 个 layouthelper 族（loadlayout2/loadlayout3/xml2table）。
- `import.lua` 与 pro MD5 完全一致（`2FE5B94139239C3B2B48698F9A3A9EC6`）。
- 单一来源：`:app/assets/lua/`（LIBS-002 收口，`:androlua` 不再携带 .lua）；`extractLuaScripts` 按 MD5 版本化覆盖（存在即跳过 → 升级自动覆盖）。

### 2.3 Java 运行时（androlua 模块，编译进主 DEX）

- `com.luajava` 16 类、`com.androlua` 主体 42+ 类、`com.android.cglib`（dx ~270 + proxy 9）逐字节同源。
- 有意差异：`LuaDexLoader`（W^X 内存加载，见下）；删除 pro 的 Xposed 反检测（chcek1/2/3/a + onHook）；移除 FileProvider 依赖方法。
- 缺失面：pro 的 80 个 UI 类（widget/textwarrior/com.nirenr 等）由 Compose + sora-editor 替代，Lua API 面相应收窄（SplitEditView 等未移植）。

---

## 3. 兼容性验证证据链

| 证据 | 结果 | 出处 |
|------|------|------|
| Universal Lua Probe（956 项） | lide **878/956** vs pro **879/956**（91% 优秀），唯一差异 socket 已修复 | 历史审计报告（已删） |
| 探针差异根因 | `extractLuaScripts()` 遇子目录 `socket` 抛 IOException，try-catch 在循环外 → 后续脚本全未落盘；已改为递归遍历 + 单文件 try-catch | 历史审计报告（已删） |
| 模拟器实测 | 12 个参考工程 lide vs pro 日志**逐字一致**（含非致命 bug 日志）；残余差异仅 LuaDexLoader +4 行号、宿主包装帧一行 | 历史审计报告（已删） |
| 真机 /libs 矩阵 | V-01~V-10 全 PASS（SDK 36 arm64）：自动加载、额外 dex、自制 .so、LuaSocket、init 隔离、冷启动解包、资产升级覆盖、损坏 dex 降级、classloader 稳定 | 历史审计报告（已删） |
| Java 桥 | bindClass/newInstance/createProxy/loadLib 12/12 全可用；危险操作（os.execute/io.popen/loadfile/dex）全部可用 | 历史审计（956 项 probe 双端日志基线） |
| 引擎缺陷修复 | ① socket 解包缺陷；② **W^X writable-dex**：Android 8+/14+ 拒绝 app 可写目录加载 dex → `InMemoryDexClassLoader` 内存字节加载（API≥26，不回退）；③ LuaActivity 未注册 → ActivityNotFoundException；④ targetSdk 36 明文 HTTP 默认禁 → `usesCleartextTraffic="true"` | AGENTS.md / 历史审计报告（已删） |

---

## 4. 已知行为差异与风险（排障备忘）

| 项 | 说明 |
|----|------|
| `InMemoryDexClassLoader` 无 AOT | 无 dex2oat 产物，冷启动解释执行（慢 10~50 倍）；游戏 demo 启动「卡」的共同来源 |
| 递归性能异常 | lide 探针递归 0.0116s vs pro 0.0002s（~58 倍慢），原因未定案，需关注 |
| `LuaObject.finalize` watchdog | 10 万行 print 极端场景触发引擎固有 GC 极限崩溃（WP-002 实测），非本工程范围 |
| 魔改字节码不兼容 | 外部 Lua 5.3.3 工具编译产物不可直接运行；打包链用引擎同款 LuaState（LuaCompiler） |
| GLES 1.0 | `gl` 模块依赖 GLESv1_CM，部分设备不可用（仅 kleinbottle 使用） |
| 探针已知缺陷 | `math.huge`/`math.pi` 报「无」、`status(运行)` bad argument 为探针自身问题，非运行时缺陷 |
| Xposed 反检测 | pro 原版 check1/2/3 已在 lide fork 彻底清除（grep 零命中），对 Xposed 生态友好 |
| 打包体积 | 全量原生库合计 ~1.3 MB，APK 22.79 MB（历史基线）；体积预算 WP-001 ACC-004 增量 ≤6 MB |

---

## 5. 性能审计结论（游戏 Demo）

| Demo | 瓶颈 | 原生化收益（估算） |
|------|------|-------------------|
| 石头（Box2D） | 传感器无节奏驱动主循环 + 每帧 ~60 次 Lua→Java 反射桥调用（每次 150~400µs）+ 未缓存字段反射 | 端到端 3~10×（桥 20~100×、step 1.5~3×）；只优化 Lua 侧不换引擎也有 2~4× |
| 看板娘（Live2D） | 纯 Java 移植引擎每帧全模型顶点变换 30~80ms + GLSurfaceView CONTINUOUSLY 无条件重绘 | 换 Cubism 原生 SDK 5~15×（30~80ms → 3~10ms） |

关键澄清：**不存在「dex 重新打包成 so」**——必须用 C/C++ 重写引擎（box2d/Live2D 均有官方 C/C++ 版）。
Lua → C（.so）调用为纯 C 路径不经 Java（`require "zip"` = `package.loadlib(..., "luaopen_zip")`，V-03 已验证）。

---

## 6. 快速索引：相关源码位置

| 主题 | 位置 |
|------|------|
| 引擎初始化/运行 | `app/src/main/java/.../engine/EngineActivity.java`（继承 `androlua` 的 `LuaActivity`） |
| 运行拉起 | `app/src/main/kotlin/.../engine/RunEngineLauncher.kt`、`androlua/.../engine/EngineBridge.kt` |
| dex/so 加载 | `androlua/src/main/java/com/androlua/LuaDexLoader.java`（W^X 内存加载） |
| 打包链 | `androlua/.../LuaCompiler.kt`、`AXmlManifest.kt`、`app/.../build/{ApkBuilder,ApkSigner,V2Signer,ZipUtils}.kt`、`BuildPackagingDialog.kt` |
| 打包 dex 直用宿主（P0-7 冻结） | 产物直接复用宿主根目录**全量 classes*.dex**（IDE 类 + 引擎类原样保留，接受多余内容）；无 dex 替换模式、无 `ENGINE_DEX_MARKER` 标记资产；打包器以根 dex 存在 `Lcom/androlua/LuaApplication;` 作引擎存在性判定，命中时 manifest 回落 `com.androlua.LuaApplication` + 移除 IDE MainActivity（`ManifestEdit.applicationName`/`removeActivities`），未命中保持宿主 manifest；ABI/权限以宿主条目为准 |
| 资产提取 | `LuaApplication.extractLuaScripts()`（递归 + MD5 版本化） |
| 原生模块构建 | `app/src/main/cpp/`（顶层 `Android.mk` + `Application.mk` 为 ndk-build 定义，`live2d/` 独立模块），AGP `externalNativeBuild` 自动构建（NDK 29.0.14206865） |
